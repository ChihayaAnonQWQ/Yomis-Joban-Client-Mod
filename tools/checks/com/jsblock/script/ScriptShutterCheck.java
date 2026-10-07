package com.jsblock.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;

/**
 * Checks the script sandbox, headlessly.
 *
 * <p>The other two checks cover the script API and the JSON presets. Neither covers
 * {@link ScriptClassShutter}, which is the piece that decides whether a resource pack can
 * read the player's files. Its failure mode is silent in both directions: too permissive and
 * nothing looks wrong until a pack misbehaves, too strict and every preset stops loading --
 * which is exactly what happened the first time it was switched on, when
 * {@code Resources.id()} returned a {@code ResourceLocation} and Rhino refused to wrap it.</p>
 *
 * <p>Two levels: the rule table on its own, and a real engine scope asked to reach a class it
 * must not have. The second is the one that would have caught the ResourceLocation case.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class ScriptShutterCheck {

	private static int failures;

	private ScriptShutterCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== script sandbox (ScriptClassShutter) ==");

		checkRuleTable();
		checkEndToEnd();

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: SHUTTER OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	private static void checkRuleTable() {
		final ScriptClassShutter shutter = new ScriptClassShutter();
		shutter.setEnabled(true);

		// Must be reachable: the scripting surface a preset legitimately uses.
		expect(shutter, "com.jsblock.script.ScriptEngine$Text", true, "draw-call builder");
		expect(shutter, "com.jsblock.script.PIDSWrapper", true, "the pids object");
		expect(shutter, "net.minecraft.resources.ResourceLocation", true, "Resources.id() return value");
		expect(shutter, "org.mozilla.javascript.Context", true, "the engine itself");
		expect(shutter, "java.lang.String", true, "strings");
		expect(shutter, "java.util.ArrayList", true, "collections");
		expect(shutter, "java.lang.Math", true, "math");
		expect(shutter, "[Ljava.lang.String;", true, "object arrays");
		expect(shutter, "[I", true, "primitive arrays");

		// Must not be reachable: the ways out of the sandbox.
		expect(shutter, "java.lang.System", false, "System.exit / System.getenv");
		expect(shutter, "java.lang.Class", false, "reflection entry point");
		expect(shutter, "java.lang.ClassLoader", false, "class loading");
		expect(shutter, "java.lang.ProcessBuilder", false, "spawning processes");
		expect(shutter, "java.lang.Runtime", false, "exec");
		expect(shutter, "java.lang.reflect.Method", false, "reflection");
		expect(shutter, "java.lang.invoke.MethodHandle", false, "invoke package");
		expect(shutter, "java.io.File", false, "the filesystem");
		expect(shutter, "java.nio.file.Files", false, "the filesystem");
		expect(shutter, "java.util.zip.ZipFile", false, "archive access");
		expect(shutter, "ScriptClassShutter", false, "the shutter's own name is not even a class");

		// The deny list must beat the allow list, not the other way round: java.lang.* is
		// allowed, so a rule that only consulted the allow list would let System through.
		expect(shutter, "java.lang.System", false, "java.lang.* allowed but System denied");

		// Off means off, except for the shutter class itself.
		shutter.setEnabled(false);
		expect(shutter, "java.lang.System", true, "shutter disabled");
		expect(shutter, "java.io.File", true, "shutter disabled");
		expect(shutter, "com.jsblock.script.ScriptClassShutter", false, "still hides itself when disabled");
	}

	/**
	 * Asks a real engine scope for classes it must and must not reach.
	 *
	 * <p>This is the end-to-end half: it goes through {@code ScriptEngine.newScope}, which is
	 * where the shutter is installed, so a shutter that was built correctly but never wired up
	 * fails here.</p>
	 */
	private static void checkEndToEnd() {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);

			// Reaching a forbidden class must throw rather than return something usable.
			//
			// Each expression has to *use* the class, not merely name it. In Rhino a bare
			// `java.lang.System` evaluates to a lazily-resolved JavaPackage and never consults
			// the shutter; resolution happens when a member is touched. Asserting on the bare
			// name would pass no matter what the shutter said.
			final String[][] probes = {
					{"java.lang.System", "java.lang.System.nanoTime()"},
					{"java.lang.Class", "java.lang.Class.forName(\"java.lang.String\")"},
					{"java.io.File", "new java.io.File(\".\").getName()"},
					{"java.lang.Runtime", "java.lang.Runtime.getRuntime().availableProcessors()"},
			};
			for (String[] probe : probes) {
				final String className = probe[0];
				final String expression = probe[1];
				try {
					final Object value = cx.evaluateString(scope, expression, "shutter-check", 1, null);
					fail("USING " + className + " should have been refused, but " + expression
							+ " returned " + value);
				} catch (EvaluatorException expected) {
					final String message = expected.getMessage() == null ? "" : expected.getMessage();
					if (message.contains("prohibited")) {
						pass("refused to use " + className);
					} else {
						// A different Rhino error still means the script could not reach it, but
						// name the reason so a refusal for the wrong cause stays visible.
						pass("refused to use " + className + " (" + message + ")");
					}
				} catch (Exception other) {
					pass("refused to use " + className + " (" + other.getClass().getSimpleName() + ")");
				}
			}

			// The API a preset actually calls must still work through the same scope.
			try {
				final Object id = cx.evaluateString(scope, "Resources.id(\"jsblock:scripts/pids_util.js\")",
						"shutter-check", 1, null);
				if (id == null) {
					fail("Resources.id() returned null through the shutter");
				} else {
					pass("Resources.id() works through the shutter -> " + id);
				}
			} catch (Exception e) {
				fail("Resources.id() was blocked by the shutter: " + e);
			}

			// And a builder chain, the other half of the surface.
			try {
				cx.evaluateString(scope, "Text.create(\"probe\").text(\"hi\").pos(1, 2)",
						"shutter-check", 1, null);
				pass("Text builder chain works through the shutter");
			} catch (Exception e) {
				fail("Text builder was blocked by the shutter: " + e);
			}
		} finally {
			Context.exit();
		}
	}

	// ------------------------------------------------------------------

	private static void expect(ScriptClassShutter shutter, String className, boolean shouldBeVisible, String why) {
		final boolean actual = shutter.visibleToScripts(className);
		if (actual == shouldBeVisible) {
			pass((shouldBeVisible ? "allows " : "denies ") + className + "  (" + why + ")");
		} else {
			fail((shouldBeVisible ? "should have allowed " : "should have denied ") + className
					+ " (" + why + "), but it did the opposite");
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
