package com.jsblock.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the guard that keeps a PIDS script inside its own resource pack, headlessly.
 *
 * <p>{@link ScriptShutterCheck} covers what a script can reach in Java and
 * {@link ScriptApiCheck} covers what it can draw. Neither covers the third way out: a script can
 * ask the engine to <em>read a file for it</em> through {@code include()} and
 * {@code Texture.texture(...)}. Those take a resource location, and
 * {@code jsblock:../../../../../../somefile} is a perfectly legal one — dots and slashes are legal
 * path characters — which for a folder resource pack resolves at the filesystem level, where
 * {@code ..} means the parent directory. The failure is invisible from the outside: the script
 * gets the file's contents and the pack looks like it works.</p>
 *
 * <p>So this check leaves a decoy file <b>outside</b> the resource root whose text sets a global,
 * asks a real engine scope to {@code include} it by a traversing path, and asserts the global is
 * still unset afterwards. That is the property that matters: not "the guard threw", but "the file
 * was not read". A positive control right next to it includes a file <em>inside</em> the root and
 * asserts that one did run, so a guard that simply refuses everything fails too.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class ScriptPathCheck {

	private static int failures;

	private ScriptPathCheck() {
	}

	public static void main(String[] args) throws Exception {
		System.out.println("== script file access (ScriptPaths) ==");

		checkRuleTable();
		checkPhysicalGuard();
		checkEngineEntryPoint();
		checkScriptLevelInclude();
		checkTextureBuilder();

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: PATHS OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/** The rules themselves: what a script may name, and what it may not. */
	private static void checkRuleTable() {
		final String[] refused = {
				"jsblock:../../x",
				"../../x",
				"jsblock:a/../../x",
				"jsblock:scripts/builtin/../../../../../../etc/hosts",
				"jsblock:/etc/passwd",
				"jsblock:..\\..\\x",
				"C:\\Windows\\win.ini",
				"C:/Windows/win.ini",
				"\\\\server\\share\\x",
				"jsblock:",
				"",
				"   ",
				"jsblock:scripts/..",
		};
		for (String reference : refused) {
			expectRefused(reference);
		}

		final String[] allowed = {
				"jsblock:scripts/pids_util.js",
				"jsblock:scripts/builtin/pids_1a.js",
				"mtr:textures/block/white.png",
				"scripts/ok.js",
				"jsblock:pids/image/a..b.png",
				"jsblock:pids/image/crt_pids_1.png",
		};
		for (String reference : allowed) {
			expectAllowed(reference);
		}

		/* The same rule for a location that was built first -- Resources.id() returns one, and
		   a script is free to hand that to a texture instead of a string. */
		try {
			ScriptPaths.checkLocation(new net.minecraft.resources.ResourceLocation("jsblock:../../x"));
			fail("checkLocation() accepted jsblock:../../x");
		} catch (ScriptPaths.RejectedPathException expected) {
			pass("refuses a traversing ResourceLocation");
		}
		try {
			ScriptPaths.checkLocation(new net.minecraft.resources.ResourceLocation("jsblock:scripts/x.js"));
			pass("accepts an ordinary ResourceLocation");
		} catch (ScriptPaths.RejectedPathException e) {
			fail("checkLocation() refused jsblock:scripts/x.js: " + e.getMessage());
		}
	}

	/**
	 * The physical half, on a real directory tree.
	 *
	 * <p>Also proves the guard is load-bearing: the same traversal is resolved without it first,
	 * and has to land outside the root — otherwise the check would pass on a platform where
	 * {@code ..} never escapes and would be proving nothing.</p>
	 */
	private static void checkPhysicalGuard() throws Exception {
		final Path workspace = Files.createTempDirectory("pids-path-check");
		final Path root = Files.createDirectories(workspace.resolve("root"));
		final Path assets = Files.createDirectories(root.resolve("assets").resolve("jsblock"));
		Files.createDirectories(assets.resolve("scripts"));
		final Path inside = Files.write(assets.resolve("scripts").resolve("ok.js"),
				"INSIDE = true;".getBytes(StandardCharsets.UTF_8));
		final Path outside = Files.write(workspace.resolve("secret.js"),
				"ESCAPED = true;".getBytes(StandardCharsets.UTF_8));

		try {
			final Path resolved = ScriptPaths.resolveWithin(assets, "scripts", "ok.js");
			if (resolved.equals(inside)) {
				pass("resolveWithin() resolves a path inside the root");
			} else {
				fail("resolveWithin() resolved to " + resolved + " instead of " + inside);
			}

			/* The negative control: what the filesystem does with the same traversal when nobody
			   checks. If this ever stops being true the guard is still correct, but this check
			   would no longer be evidence, so it is asserted rather than assumed. */
			final Path unguarded = assets.resolve("..").resolve("..").resolve("..").resolve("secret.js").normalize();
			if (unguarded.equals(outside.normalize())) {
				pass("without the guard, \"../../../secret.js\" lands outside the root: " + unguarded.getFileName());
			} else {
				fail("the negative control resolved to " + unguarded + ", so this check proves nothing");
			}

			try {
				final Path escaped = ScriptPaths.resolveWithin(assets, "..", "..", "..", "secret.js");
				fail("resolveWithin() allowed an escape to " + escaped.getFileName());
			} catch (IOException expected) {
				if (String.valueOf(expected.getMessage()).contains("Path must be within")) {
					pass("resolveWithin() refuses the escape: " + expected.getMessage());
				} else {
					fail("resolveWithin() refused the escape for the wrong reason: " + expected.getMessage());
				}
			}
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * The engine's own entry point, which is what {@code include()} calls in game.
	 *
	 * <p>It cannot read anything headlessly — there is no client and so no resource manager — but
	 * the refusal happens before that, which is the point: a bad reference is turned away without
	 * reaching the read at all.</p>
	 */
	private static void checkEngineEntryPoint() {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);

			final int before = ScriptPaths.rejectedCount();
			final boolean loaded = ScriptEngine.evaluateResource(cx, scope, "jsblock:../../../../secret.js");
			if (loaded) {
				fail("evaluateResource() reported that it loaded a traversing path");
			} else {
				pass("evaluateResource() refuses the traversing path");
			}
			if (ScriptPaths.rejectedCount() == before + 1) {
				pass("the refusal was reported once (rejectedCount " + before + " -> " + ScriptPaths.rejectedCount() + ")");
			} else {
				fail("the refusal was not reported: rejectedCount " + before + " -> " + ScriptPaths.rejectedCount());
			}

			/* A second try must not report again: the offending call is in a per-frame path. */
			ScriptEngine.evaluateResource(cx, scope, "jsblock:../../../../secret.js");
			if (ScriptPaths.rejectedCount() == before + 1) {
				pass("a repeat of the same refusal is not reported twice");
			} else {
				fail("a repeat of the same refusal was reported again (rejectedCount "
						+ ScriptPaths.rejectedCount() + ")");
			}
		} finally {
			Context.exit();
		}
	}

	/**
	 * A real script, asking in the way a preset would.
	 *
	 * <p>The decoy outside the root is the assertion: it exists, it is readable, and after the
	 * script has run the global it would have set is still not there.</p>
	 */
	private static void checkScriptLevelInclude() throws Exception {
		final Path workspace = Files.createTempDirectory("pids-path-script");
		final Path root = Files.createDirectories(workspace.resolve("root"));
		final Path assets = Files.createDirectories(root.resolve("assets").resolve("jsblock"));
		Files.createDirectories(assets.resolve("scripts"));
		Files.write(assets.resolve("scripts").resolve("util.js"),
				"INSIDE = true;".getBytes(StandardCharsets.UTF_8));
		final Path decoy = Files.write(workspace.resolve("secret.js"),
				"ESCAPED = true;".getBytes(StandardCharsets.UTF_8));
		if (!Files.isReadable(decoy)) {
			fail("the decoy file is not readable, so the check proves nothing");
			return;
		}

		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			installGuardedFileInclude(cx, scope, root);

			/* The script-level case the guard exists for. */
			cx.evaluateString(scope,
					"include(\"jsblock:../../../secret.js\");",
					"traversal.js", 1, null);
			if (isSet(scope, "ESCAPED")) {
				fail("a script's include(\"jsblock:../../../secret.js\") read a file outside the resource root");
			} else {
				pass("a script's include() could not read " + decoy.getFileName() + " outside the resource root");
			}

			/* Positive control: the same include, aimed inside the root, must still work. */
			cx.evaluateString(scope,
					"include(\"jsblock:scripts/util.js\");",
					"include-ok.js", 1, null);
			if (isSet(scope, "INSIDE")) {
				pass("a script's include() still reads a file inside the resource root");
			} else {
				fail("a legitimate include() was refused as well, so the guard is too strict");
			}
		} finally {
			Context.exit();
		}
		deleteRecursively(workspace);
	}

	/** The texture route, which is the other read a script can ask for. */
	private static void checkTextureBuilder() {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);

			try {
				cx.evaluateString(scope,
						"Texture.create(\"escape\").texture(\"jsblock:../../../../../../secret.png\");",
						"texture-escape.js", 1, null);
				fail("Texture.texture() accepted a traversing path");
			} catch (Exception expected) {
				final String message = String.valueOf(expected.getMessage());
				if (message.contains("Refused to read") || message.contains("outside")) {
					pass("Texture.texture() refuses the traversing path at the line the author wrote");
				} else {
					fail("Texture.texture() threw, but not for the path: " + expected);
				}
			}

			/* The same route with a location built by Resources.id(), which never sees the
			   builder's string overload. */
			try {
				cx.evaluateString(scope,
						"Texture.create(\"escape\").texture(Resources.id(\"jsblock:../../../../secret.png\"));",
						"texture-id-escape.js", 1, null);
				fail("Texture.texture(Resources.id(...)) accepted a traversing path");
			} catch (Exception expected) {
				pass("Texture.texture(Resources.id(...)) refuses it too");
			}

			try {
				cx.evaluateString(scope,
						"Texture.create(\"ok\").texture(\"jsblock:pids/image/crt_pids_1.png\");",
						"texture-ok.js", 1, null);
				pass("Texture.texture() still accepts an ordinary path");
			} catch (Exception e) {
				fail("Texture.texture() refused an ordinary path: " + e);
			}
		} finally {
			Context.exit();
		}
	}

	// ------------------------------------------------------------------

	/**
	 * The headless stand-in for the engine's {@code include}.
	 *
	 * <p>In game the read goes through Minecraft's resource manager, which cannot run here. This
	 * mirrors {@link ScriptApiCheck}'s file-backed include and adds the one thing that matters for
	 * this check: the same validation the engine applies, plus the physical guard on the way to
	 * the file.</p>
	 */
	private static void installGuardedFileInclude(final Context cx, final Scriptable scope, final Path root) {
		org.mozilla.javascript.ScriptableObject.putProperty(scope, "include",
				new org.mozilla.javascript.BaseFunction() {
					@Override
					public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
						if (args.length == 0 || args[0] == null) {
							return Undefined.instance;
						}
						final String reference = Context.toString(args[0]);
						try {
							ScriptPaths.checkReference(reference);
						} catch (ScriptPaths.RejectedPathException refused) {
							ScriptPaths.report("include()", refused);
							return Undefined.instance;
						}
						final int colon = reference.indexOf(':');
						final String namespace = colon < 0 ? "minecraft" : reference.substring(0, colon);
						final String path = colon < 0 ? reference : reference.substring(colon + 1);
						try {
							final Path candidate = ScriptPaths.resolveWithin(
									root.resolve("assets").resolve(namespace), path.split("/"));
							if (Files.isRegularFile(candidate)) {
								context.evaluateString(s,
										new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8),
										candidate.toString(), 1, null);
							}
						} catch (IOException refused) {
							ScriptPaths.report("include()",
									new ScriptPaths.RejectedPathException(reference, refused.getMessage()));
						} catch (Exception e) {
							System.out.println("FAIL include " + reference + " threw: " + e);
							failures++;
						}
						return Undefined.instance;
					}
				});
	}

	private static boolean isSet(Scriptable scope, String name) {
		return scope.get(name, scope) != Scriptable.NOT_FOUND;
	}

	private static void expectRefused(String reference) {
		try {
			ScriptPaths.checkReference(reference);
			fail("accepted \"" + reference + "\", which a script must not be able to read");
		} catch (ScriptPaths.RejectedPathException expected) {
			pass("refuses \"" + reference + "\"");
		}
	}

	private static void expectAllowed(String reference) {
		try {
			ScriptPaths.checkReference(reference);
			pass("allows \"" + reference + "\"");
		} catch (ScriptPaths.RejectedPathException e) {
			fail("refused \"" + reference + "\", which is an ordinary path: " + e.getMessage());
		}
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
			for (Path entry : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(entry);
			}
		}
	}

	private static void pass(String message) {
		System.out.println("OK   " + message);
	}

	private static void fail(String message) {
		System.out.println("FAIL " + message);
		failures++;
	}
}
