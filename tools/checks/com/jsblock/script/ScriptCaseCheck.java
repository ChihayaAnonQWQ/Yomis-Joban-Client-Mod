package com.jsblock.script;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks how a script reference with capitals in it is resolved, headlessly.
 *
 * <p>Minecraft resource names are lower case. {@code ResourceLocation}'s constructor does not
 * return {@code null} for a capital letter — it throws — so a preset whose {@code scriptFiles}
 * entry reads {@code jsblock:scripts/Digital_Rail.js} used to fail before any file was looked at.
 * met transit ships exactly that file, named exactly that way, which is why this exists: the pack
 * predates the rule in its own naming and works in v2 because the read is folded first.</p>
 *
 * <p>Two halves are asserted, because they fail differently:</p>
 * <ul>
 *   <li>{@link ScriptPaths#resourceOrNull(String)} and {@link ScriptPaths#foldToResourceLocation(String)}
 *       decide whether the reference can be turned into a location at all. The order matters —
 *       an exact reference must not be folded, or a pack that ships both
 *       {@code scripts/a.js} and {@code scripts/A.js} would silently get the wrong one.</li>
 *   <li>{@link ScriptEngine#caseFoldedCandidates(String)} decides what to try, in order, and must
 *       still refuse a reference that walks out of its pack: tolerance for spelling is not
 *       tolerance for {@code ..}.</li>
 * </ul>
 *
 * <p>The disk half — finding {@code Digital_Rail.js} when the name it is asked for is
 * {@code digital_rail.js} — is {@link ScriptApiCheck#caseInsensitiveFile(Path, String)}, exercised
 * here against a temporary tree.</p>
 *
 * <p><b>This is tolerance for packs, not Minecraft's rule.</b> The assertions below deliberately
 * include the cases that stay refused, so that the tolerance cannot quietly grow into something
 * that resolves a name the game itself would not.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class ScriptCaseCheck {

	private static int failures;

	private ScriptCaseCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== script reference spelling (case folding) ==");

		checkExactIsNotFolded();
		checkFolding();
		checkEscapesAreStillRefused();
		checkCandidates();
		checkDiskLookup();
		checkPackFallback();

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: CASE OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/** An ordinary reference is used as written, so nothing about the normal path changed. */
	private static void checkExactIsNotFolded() {
		final ResourceLocation location = ScriptPaths.resourceOrNull("jsblock:scripts/pids_util.js");
		expect(location != null, "a lower-case reference is built as written");
		expect(location != null && location.getPath().equals("scripts/pids_util.js"),
				"and its path is unchanged");
		expect(location != null && location.getNamespace().equals("jsblock"),
				"and its namespace is unchanged");
		expect(ScriptPaths.resourceOrNull("pids_util.js") != null,
				"a reference with no namespace is still the minecraft namespace");
	}

	/** The tolerance itself, and the shape of what it produces. */
	private static void checkFolding() {
		expect(ScriptPaths.resourceOrNull("jsblock:scripts/Digital_Rail.js") == null,
				"a capital in the path is refused by ResourceLocation, which is why folding exists");

		final ResourceLocation folded = ScriptPaths.foldToResourceLocation("jsblock:scripts/Digital_Rail.js");
		expect(folded.getPath().equals("scripts/digital_rail.js"),
				"folding lower-cases the path: " + folded.getPath());
		expect(folded.getNamespace().equals("jsblock"), "folding leaves the namespace alone here");

		/* A capital in the namespace is folded too: the whole string is, which is what v2 does. */
		expect(ScriptPaths.foldToResourceLocation("JSBlock:scripts/x.js").getNamespace().equals("jsblock"),
				"a capitalised namespace folds as well");

		/* met transit's other scripts have no capitals, so they must not be folded. */
		expect(ScriptPaths.resourceOrNull("jsblock:scripts/met_running_board.js") != null,
				"the pack's other scripts need no folding at all");
	}

	/** The independence that has to survive the tolerance: a spelling problem is not a path problem. */
	private static void checkEscapesAreStillRefused() {
		final String[] escapes = {
				"jsblock:scripts/../../../../etc/hosts",
				"jsblock:scripts/Digital_Rail/../../../../x.js",
				"jsblock:/etc/hosts",
				"jsblock:scripts\\Digital_Rail.js",
				"jsblock:",
				"",
		};
		for (String escape : escapes) {
			expectRefused(escape, () -> ScriptPaths.resourceOrNull(escape));
			expectRefused(escape, () -> ScriptPaths.foldToResourceLocation(escape));
		}
	}

	/** What the engine tries, in order. */
	private static void checkCandidates() {
		final java.util.List<ResourceLocation> exact =
				ScriptEngine.caseFoldedCandidates("jsblock:scripts/met_running_board.js");
		expect(exact.size() == 1,
				"an ordinary reference gets exactly one candidate, so the walk never runs for it: " + exact);
		expect(exact.get(0).getPath().equals("scripts/met_running_board.js"),
				"and that candidate is the reference as written");

		/* No client runs in this check, so the resource-manager walk behind the folded name cannot
		   answer and adds nothing. The assertion that matters is the one that does not need a
		   client: the folded name is present and first, so the game tries it before giving up. */
		final java.util.List<ResourceLocation> folded =
				ScriptEngine.caseFoldedCandidates("jsblock:scripts/Digital_Rail.js");
		expect(!folded.isEmpty() && folded.get(0).getPath().equals("scripts/digital_rail.js"),
				"a capitalised reference folds into a usable candidate: " + folded);

		expectRefused("jsblock:scripts/../../x.js",
				() -> ScriptEngine.caseFoldedCandidates("jsblock:scripts/../../x.js"));
	}

	/**
	 * The disk half, against a tree shaped like a pack.
	 *
	 * <p>Named the way met transit names things: a directory with a capital and a file with
	 * capitals. The assertions are written so that they hold whether or not the filesystem is
	 * case sensitive — on this project's own Windows volume it is not, and
	 * {@code Files.isRegularFile("scripts/digital_rail.js")} answers {@code true} against
	 * {@code Scripts/Digital_Rail.js} before the fallback is ever consulted. Asserting the
	 * returned name would therefore be asserting the filesystem. What is asserted instead is the
	 * two things that hold either way: the lookup finds the file, and an exact spelling reaches
	 * the exact file rather than a near miss.</p>
	 */
	private static void checkDiskLookup() {
		Path root = null;
		try {
			root = Files.createTempDirectory("jcm-case-check");
			/* The directory carries the capital, so even the descent to it is an inexact match:
			   "Script_Scripts" and "Script_Scripts" do not contain each other's spelling. */
			final Path scripts = Files.createDirectories(root.resolve("Script_Scripts"));
			Files.write(scripts.resolve("Digital_Rail.js"), "// met transit".getBytes(StandardCharsets.UTF_8));

			final Path found = ScriptApiCheck.caseInsensitiveFile(root, "script_scripts/digital_rail.js");
			expect(found != null && found.getFileName().toString().equals("Digital_Rail.js"),
					"a lower-case reference finds the capitalised file on disk: " + found);
			expect(found != null && java.util.Objects.equals(
							Files.readString(found).trim(), "// met transit"),
					"and the file the lookup found is the one the pack shipped");

			/* An exact hit is the exact file. This is the assertion the ambiguous case rests on:
			   the fallback must never be what answers a reference that was already correct. */
			final Path exact = ScriptApiCheck.caseInsensitiveFile(root, "Script_Scripts/Digital_Rail.js");
			expect(exact != null && exact.equals(scripts.resolve("Digital_Rail.js")),
					"an exactly-spelled reference resolves to itself");

			expect(ScriptApiCheck.caseInsensitiveFile(root, "script_scripts/no_such_file.js") == null,
					"a name that matches nothing answers null rather than guessing");

			/* A second script in the same directory. Both must keep resolving to themselves, which
			   is what says the lookup is per-name rather than "the first file in the directory". */
			Files.write(scripts.resolve("Met_Glass.js"), "// decoy".getBytes(StandardCharsets.UTF_8));
			final Path first = ScriptApiCheck.caseInsensitiveFile(root, "script_scripts/digital_rail.js");
			final Path second = ScriptApiCheck.caseInsensitiveFile(root, "script_scripts/met_glass.js");
			expect(first != null && first.getFileName().toString().equals("Digital_Rail.js"),
					"the first script still resolves to itself with a second one present: "
							+ (first == null ? "null" : first.getFileName()));
			expect(second != null && second.getFileName().toString().equals("Met_Glass.js"),
					"and the second resolves to itself: " + (second == null ? "null" : second.getFileName()));

			/* And the walk cannot leave the tree it was given. */
			expect(ScriptApiCheck.caseInsensitiveFile(root, "script_scripts/../../outside.js") == null,
					"the lookup refuses to climb out of the root it was given");
		} catch (IOException e) {
			fail("building the temporary pack threw: " + e);
		} finally {
			if (root != null) {
				deleteTree(root);
			}
		}
	}

	// ------------------------------------------------------------------

	// ------------------------------------------------------------------

	/**
	 * The pack reader's own directory walk — the half of {@code ScriptPackFiles} that can be run
	 * without a client.
	 *
	 * <p>This is the code that answers for {@code met transit}: the pack stores
	 * {@code assets/jsblock/scripts/Digital_Rail.js} and the reference reads exactly that, so the
	 * exact attempt is what should hit, and it must return the file's contents. The folded attempt
	 * is then exercised by asking for the lower-case spelling, which is what a pack that named its
	 * file one way and its {@code scriptFiles} entry another would produce.</p>
	 *
	 * <p>Without this, {@code ScriptPackFiles} would only ever be reasoned about: the pack
	 * enumeration and the reflection around this method need a running client, so the assertions
	 * here are the ones that keep the readable half honest.</p>
	 */
	private static void checkPackFallback() {
		Path root = null;
		try {
			root = Files.createTempDirectory("jcm-pack-check");
			final Path scripts = Files.createDirectories(
					root.resolve("assets").resolve("jsblock").resolve("Scripts"));
			Files.write(scripts.resolve("Digital_Rail.js"),
					"// Met Transit Digital Rail\n".getBytes(StandardCharsets.UTF_8));

			/* The exact reference met transit writes, character for character. */
			final String[] exact = {"assets", "jsblock", "Scripts", "Digital_Rail.js"};
			final String text = ScriptPackFiles.readFromDirectoryRoot(root, exact);
			expect(text != null && text.contains("Met Transit Digital Rail"),
					"the exact asset path a capitalised pack uses is read straight off the pack root");

			/* The same file asked for in lower case, which is what the resource manager's index
			   would have offered: every segment has to be matched loosely. */
			final String[] folded = {"assets", "jsblock", "scripts", "digital_rail.js"};
			final String foldedText = ScriptPackFiles.readFromDirectoryRoot(root, folded);
			expect(foldedText != null && foldedText.equals(text),
					"the lower-case spelling of the same path finds it too");

			/* A name that is genuinely absent stays absent: the tolerance is per segment, not a
			   wildcard over the whole path. */
			expect(ScriptPackFiles.readFromDirectoryRoot(root,
							new String[]{"assets", "jsblock", "Scripts", "Not_A_Script.js"}) == null,
					"a name that matches nothing answers null rather than the nearest file");
			expect(ScriptPackFiles.readFromDirectoryRoot(root,
							new String[]{"assets", "nosuchnamespace", "Scripts", "Digital_Rail.js"}) == null,
					"a namespace the pack does not have answers null");
		} catch (IOException e) {
			fail("building the temporary pack root threw: " + e);
		} finally {
			if (root != null) {
				deleteTree(root);
			}
		}
	}

	private interface Call {
		Object run();
	}

	private static void expectRefused(String reference, Call call) {
		try {
			final Object value = call.run();
			fail("\"" + reference + "\" was accepted and produced " + value);
		} catch (ScriptPaths.RejectedPathException expected) {
			pass("refuses \"" + reference + "\": " + reason(expected));
		} catch (Exception other) {
			fail("\"" + reference + "\" was refused as " + other.getClass().getSimpleName()
					+ " instead of a path refusal: " + other.getMessage());
		}
	}

	private static String reason(ScriptPaths.RejectedPathException rejected) {
		final String message = rejected.getMessage();
		final int colon = message.indexOf(": ");
		return colon < 0 ? message : message.substring(colon + 2);
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
