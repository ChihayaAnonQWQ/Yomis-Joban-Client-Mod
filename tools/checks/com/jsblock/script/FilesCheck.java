package com.jsblock.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the {@code Files} global, headlessly.
 *
 * <p>{@link FilesUtil} is the one global in this port that writes to the disk, and the only one
 * whose whole reason for existing is that a preset's own variables do not survive a frame. So the
 * two things worth proving without a game are the pair that makes a running board work — a value
 * saved is the value read back, and a name that was never written answers {@code null} rather than
 * throwing, because v2's own example is
 * {@code if (Files.hasData(...)) { JSON.parse(Files.readData(...)) } } and a throw there would put
 * the panel on the error path for the ordinary case of a first run.</p>
 *
 * <p>The third is the guard. A PIDS preset arrives inside a resource pack, and a resource pack
 * arrives with a server or a modpack, so {@code Files.saveData(text, "..", "..", "options.txt")}
 * has to be refused — and refused as an {@link IOException} with v2's own wording, because that is
 * what the pack author will have seen before. Every shape of escape is exercised: {@code ..} in
 * each position, an absolute path, a Windows path, and a nested {@code a/../../..} that only looks
 * contained before normalization.</p>
 *
 * <p>The roots are redirected to a temporary directory for the run, which is the whole reason
 * {@link FilesUtil#useRootForTesting(Path)} exists: with a real game directory this check would be
 * writing into a player's {@code .minecraft}.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class FilesCheck {

	private static int failures;

	private FilesCheck() {
	}

	public static void main(String[] args) throws Exception {
		System.out.println("== script file storage (Files) ==");

		final Path sandbox = Files.createTempDirectory("jcm-files-check");
		FilesCheckSupport.redirectTo(sandbox);
		try {
			System.out.println("   sandbox: " + sandbox);
			checkDirectories();
			checkRoundTrip();
			checkMissingFileIsNull();
			checkQuotedValuesSurvive();
			checkEscapesAreRefused();
			checkThroughAScript();
		} finally {
			FilesCheckSupport.reset();
			deleteTree(sandbox);
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: FILES OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/** The two roots: the game directory for {@code read}, and v2's data area for the rest. */
	private static void checkDirectories() {
		final Path root = FilesCheckSupport.readDirectory();
		final Path data = FilesCheckSupport.dataDirectory();
		expect(root != null && Files.isDirectory(root), "read() resolves against the game directory");
		expect(data != null && data.startsWith(root),
				"the data area sits under the game directory, as v2 has it: " + data);
		expect(data.endsWith(Path.of("data", "mtrscripting")),
				"the data area is data/mtrscripting, the directory v2's own packs write to: " + data);
	}

	/** What a running board does every tick it notices a change. */
	private static void checkRoundTrip() throws Exception {
		final String json = "{\"log\":[1,2,3],\"lastDay\":42}";
		FilesUtil.saveData(json, "met_running_board", "station-abc.json");
		expect(Files.isRegularFile(FilesCheckSupport.dataDirectory()
						.resolve("met_running_board").resolve("station-abc.json")),
				"saveData() creates the directories above the file, as v2's FileUtils does");
		expect(json.equals(FilesUtil.readData("met_running_board", "station-abc.json")),
				"readData() returns exactly what saveData() wrote");
		expect(FilesUtil.hasData("met_running_board", "station-abc.json"),
				"hasData(parent, name) is true for a file that was written");
		expect(!FilesUtil.hasData("met_running_board", "station-zzz.json"),
				"hasData(parent, name) is false for one that was not");

		/* The same content written somewhere else must not be visible under the first name. */
		FilesUtil.saveData("different", "met_running_board", "station-def.json");
		expect(json.equals(FilesUtil.readData("met_running_board", "station-abc.json")),
				"a second file in the same directory does not change the first");

		/* deleteData is v2's clear, and saying so twice is not an error. */
		FilesUtil.deleteData("met_running_board", "station-def.json");
		expect(!FilesUtil.hasData("met_running_board", "station-def.json"), "deleteData() removes it");
		FilesUtil.deleteData("met_running_board", "station-def.json");
		pass("deleteData() of a file that is already gone is not an error");
	}

	/** The first-run case v2's own example guards with hasData. */
	private static void checkMissingFileIsNull() throws Exception {
		expect(FilesUtil.readData("nothing_here", "nope.json") == null,
				"readData() of a file that was never written returns null, not a throw");
		expect(FilesUtil.read("nothing_here_either.json") == null,
				"read() of a missing file returns null too");
	}

	/** Text in and text out: UTF-8 and multi-line, because a board saves JSON and Chinese. */
	private static void checkQuotedValuesSurvive() throws Exception {
		final String text = "{\"station\":\"重庆轨道交通\",\n\"note\":\"line 2\"}";
		FilesUtil.saveData(text, "utf8.json");
		expect(text.equals(FilesUtil.readData("utf8.json")),
				"a multi-line UTF-8 value round-trips unchanged");
		expect(new String(Files.readAllBytes(FilesCheckSupport.dataDirectory().resolve("utf8.json")),
						StandardCharsets.UTF_8).equals(text),
				"the file on disk is UTF-8, which is what v2 writes");
		expect(FilesUtil.hasData("utf8.json"), "hasData(name) answers with one argument too");
	}

	/**
	 * Every shape of escape, against both roots.
	 *
	 * <p>The nested case is the one worth naming: {@code a/../../../x} contains no {@code ..} that
	 * a caller could notice by looking at the first segment, and only the walk back up to the root
	 * catches it. That walk is {@link ScriptPaths#ensureWithin}, so getting these right is the same
	 * claim as getting the path guard right.</p>
	 */
	private static void checkEscapesAreRefused() {
		final String[][] escapes = {
				{".."},
				{"..", ".."},
				{"..", "..", "options.txt"},
				{"a", "..", "..", "..", "options.txt"},
				{"..", "..", "..", "..", "..", "..", "..", "..", "Windows", "System32", "drivers", "etc", "hosts"},
				{"/etc/hosts"},
				{"C:\\Windows\\System32\\drivers\\etc\\hosts"},
		};
		for (String[] escape : escapes) {
			expectRefused(() -> FilesUtil.saveData("owned", escape), "saveData " + String.join("/", escape));
			expectRefused(() -> FilesUtil.readData(escape), "readData " + String.join("/", escape));
			expectRefused(() -> FilesUtil.deleteData(escape), "deleteData " + String.join("/", escape));
			expectRefused(() -> FilesUtil.hasData(escape), "hasData " + String.join("/", escape));
		}
		/* The read root is the game directory, one level above the data area, so a path that climbs
		   out of the data area but stays in the game directory is legal there and must be refused
		   for readData only. This is that boundary. */
		expectRefused(() -> FilesUtil.readData("..", "..", "options.txt"),
				"readData from the game directory root (the data area is two levels down)");
		expect(hasNoFileOutside(sandboxProbe()), "nothing was written outside the data area");
	}

	/** @return a path the escape attempts would have hit if the guard had let them through. */
	private static Path sandboxProbe() {
		return FilesCheckSupport.readDirectory().getParent().resolve("owned");
	}

	private static boolean hasNoFileOutside(Path path) {
		return !Files.exists(path);
	}

	/**
	 * The global as a script sees it: {@code Files.saveData} from JavaScript, read back by
	 * JavaScript, through the real scope and the real sandbox.
	 *
	 * <p>This is the half a Java-side test cannot cover. Rhino resolves JavaScript arguments onto
	 * Java parameters by its own rules, and v2's API is varargs
	 * ({@code readData(String...)}, {@code saveData(String, String...)}) — so whether
	 * {@code Files.hasData("a", "b")} lands in the two-argument form is a property of the engine,
	 * not of this port. It is asserted here rather than assumed.</p>
	 */
	private static void checkThroughAScript() {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);

			final Object global = scope.get("Files", scope);
			if (global instanceof NativeJavaClass
					&& ((NativeJavaClass) global).getClassObject() == FilesUtil.class) {
				pass("Files is on the scope and points at the storage helper");
			} else {
				fail("Files is " + global + " instead of the storage helper");
			}

			/* The exact shape met transit's running board uses. */
			evaluate(cx, scope, "Files.saveData(JSON.stringify({log:[1,2]}), 'met_running_board', 'probe.json');");
			expect("true".equals(String.valueOf(evaluate(cx, scope,
							"String(Files.hasData('met_running_board', 'probe.json'))"))),
					"a script's Files.hasData(parent, name) reaches the two-argument Java form");
			expect("{\"log\":[1,2]}".equals(String.valueOf(evaluate(cx, scope,
							"JSON.stringify(JSON.parse(Files.readData('met_running_board', 'probe.json')))"))),
					"a script round-trips JSON through saveData/readData, as a running board does");
			expect("null".equals(String.valueOf(evaluate(cx, scope,
							"String(Files.readData('met_running_board', 'never-written.json'))"))),
					"a script gets null for a file that was never written");

			/* And the refusal, from the script side: it must be an error the renderer can catch,
			   not a silent success. */
			try {
				cx.evaluateString(scope, "Files.saveData('owned', '..', '..', 'options.txt');", "probe", 1, null);
				fail("a script's path escape was accepted");
			} catch (Exception expected) {
				final String message = String.valueOf(expected.getMessage());
				if (message.contains("Path must be within")) {
					pass("a script's path escape is refused with v2's own wording");
				} else {
					fail("a script's path escape was refused without v2's wording: " + message);
				}
			}

			/* What a script can reach beyond the four documented methods. Rhino does not answer
			   "undefined" for a Java member that is not there -- it throws, which is why the probe
			   below has to catch rather than stringify. So the assertion is the refusal itself: a
			   public setter for the roots would let a preset choose where its own files are
			   written, and it is package-private for exactly that reason. */
			expectJavaMemberHidden(cx, scope, "Files.useRootForTesting",
					"the root override is package-private, so a script cannot move its own storage");
			expectJavaMemberHidden(cx, scope, "Files.rootOverride",
					"and the field behind it is not readable either");
			expect("function".equals(String.valueOf(evaluate(cx, scope, "typeof Files.saveData"))),
					"the probe is meaningful: a public static does report \"function\"");
		} catch (Exception e) {
			fail("running a script against Files threw: " + e);
			e.printStackTrace(System.out);
		} finally {
			Context.exit();
		}
	}

	private static Object evaluate(Context cx, Scriptable scope, String expression) {
		return cx.evaluateString(scope, expression, "files-probe", 1, null);
	}

	/**
	 * Asserts that a Java member is not reachable from a script.
	 *
	 * <p>Not a {@code typeof} probe: Rhino resolves a member of a {@code NativeJavaClass} eagerly
	 * and throws {@code "Java class ... has no public instance field or method named ..."} rather
	 * than answering {@code undefined} the way a plain JavaScript object would. That was
	 * established by running this check with the override still public, and it is worth knowing
	 * independently of the override — a script's own feature-detection idiom does not work
	 * against a Java class.</p>
	 */
	private static void expectJavaMemberHidden(Context cx, Scriptable scope, String expression, String what) {
		try {
			final Object value = evaluate(cx, scope, expression);
			fail(what + " -- but it was reachable and returned " + value);
		} catch (Exception expected) {
			pass(what + " (reading it throws: " + firstLine(expected.getMessage()) + ")");
		}
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "no message";
		}
		final int newline = message.indexOf('\n');
		return newline < 0 ? message : message.substring(0, newline);
	}

	// ------------------------------------------------------------------

	private interface Attempt {
		void run() throws IOException;
	}

	private static void expectRefused(Attempt attempt, String what) {
		try {
			attempt.run();
			fail(what + " was accepted");
		} catch (IOException expected) {
			if (String.valueOf(expected.getMessage()).contains("Path must be within")) {
				pass("refuses " + what);
			} else {
				fail(what + " was refused without v2's wording: " + expected.getMessage());
			}
		} catch (Exception other) {
			fail(what + " was refused as " + other.getClass().getSimpleName()
					+ " instead of an IOException: " + other.getMessage());
		}
	}

	private static void deleteTree(Path root) {
		try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					/* A temporary directory that will not delete is not a check failure. */
				}
			});
		} catch (IOException ignored) {
			/* Same. */
		}
	}

	private static void expect(boolean condition, String what) {
		if (condition) {
			pass(what);
		} else {
			fail(what);
		}
	}

	private static void pass(String what) {
		System.out.println("OK   " + what);
	}

	private static void fail(String what) {
		System.out.println("FAIL " + what);
		failures++;
	}
}
