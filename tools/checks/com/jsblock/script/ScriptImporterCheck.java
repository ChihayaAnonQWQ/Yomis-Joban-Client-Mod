package com.jsblock.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.awt.image.BufferedImage;

/**
 * Checks the Rhino importer globals a JCM 2.x preset starts with, and the AWT path they open.
 *
 * <h2>The failure this exists for</h2>
 * <p>{@code HKR-Split-Flap-Display}'s {@code next_train.js} opens with</p>
 * <pre>{@code
 * importPackage(java.awt);
 * importPackage(java.awt.geom);
 * }</pre>
 * <p>and its whole screen is an AWT {@code BufferedImage} painted through a
 * {@link GraphicsTexture}. The engine used to build its scope with
 * {@code Context.initStandardObjects()}, which has no importer global at all, so the preset died of
 * a {@code ReferenceError: "importPackage" is not defined} <b>at line 27 while it was being
 * loaded</b> — before {@code create()} existed, and therefore before a single pixel was drawn. A
 * panel whose script never loads is a transparent panel, and nothing in the log said so beyond one
 * error line nobody was reading.</p>
 *
 * <p>Nothing else covered it. {@code ScriptApiCheck} runs whatever script it is given, so it finds
 * the fault only when a pack that uses the importer is passed to it; the built-in
 * {@code pids_1a.js} never calls {@code importPackage}. This check runs the importer itself, so the
 * presence of the globals is asserted on every build rather than only when a pack happens to
 * exercise them.</p>
 *
 * <h2>Three things, in order</h2>
 * <ol>
 *   <li><b>The globals exist</b> and resolve the AWT classes a pack draws with. {@code java.awt.*}
 *       and {@code java.awt.geom.*} are already on {@link ScriptClassShutter}'s allow-list — the
 *       canvas port put them there — so nothing had to be loosened for this.</li>
 *   <li><b>The sandbox still holds through the importer.</b> This is the half worth being paranoid
 *       about: the importer is a second route to a class, and a route that skipped
 *       {@code ClassShutter} would hand every pack the whole JVM. It does not — Rhino consults the
 *       shutter inside {@code NativeJavaPackage.getPkgProperty}, before {@code Kit.classOrNull} —
 *       but that is a property of a library, not of this port, so it is asserted here on every
 *       build. {@link ScriptShutterCheck} asserts the same rules through the direct
 *       {@code java.lang.System} spelling; this one asserts them through
 *       {@code importPackage}, {@code importClass}, {@code Packages} and the bare package roots,
 *       which are the spellings a pack actually writes.</li>
 *   <li><b>The AWT drawing path works end to end</b>, in the shape a real preset uses it: a font
 *       out of the pack scaled with {@code deriveFont(int, float)}, {@code RenderingHints},
 *       {@code new Color(int)}, {@code Graphics2D.fillRect}, and the painted image read back pixel
 *       by pixel. These are the three traps {@code next_train.js}'s own header lists, so they are
 *       the three this asserts.</li>
 * </ol>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class ScriptImporterCheck {

	private static int failures;

	private ScriptImporterCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== script importer (importPackage / importClass / Packages) ==");

		final Context cx = Context.enter();
		try {
			/* The fonts come out of MTR's own assets, which is what the engine reads in game
			   through the resource manager. There is no client here, so the check hands
			   ScriptFonts the reader itself -- the same seam ScriptApiCheck uses for include(),
			   and the only way the Font.createFont half of Resources.getSystemFont can be
			   exercised at all off the game. The bytes are the same bytes: assets/mtr/font/ is on
			   this classpath through libs/mtr-common.jar. */
			ScriptFonts.headlessSource = location -> ScriptImporterCheck.class.getClassLoader()
					.getResourceAsStream("assets/" + location.getNamespace() + "/" + location.getPath());

			final Scriptable scope = ScriptEngine.newScope(cx);

			checkGlobalsPresent(cx, scope);
			checkAllowedClassesResolve(cx, scope);
			checkSystemFonts(cx, scope);
			checkShutterHoldsThroughTheImporter(cx, scope);
			checkAwtCanvasPath(cx, scope);
		} catch (Exception e) {
			System.out.println("FAIL the importer check itself threw: " + e);
			e.printStackTrace(System.out);
			failures++;
		} finally {
			ScriptFonts.headlessSource = null;
			Context.exit();
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: IMPORTER OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/**
	 * The nine names JCM 2.x's scope has and {@code initStandardObjects()} does not.
	 *
	 * <p>Asserted by {@code typeof} rather than by presence in the scope, because these are exactly
	 * the reads a preset makes: a name that is in the scope but resolves to {@code null} still
	 * answers {@code "undefined"} to the script that calls it, and that is the failure being
	 * pinned.</p>
	 */
	private static void checkGlobalsPresent(Context cx, Scriptable scope) {
		final String[][] globals = {
				{"importPackage", "function", "the call next_train.js opens with"},
				{"importClass", "function", "the single-class form of the same"},
				{"Packages", "object", "the package root a script can walk by hand"},
				{"java", "object", "the java package root"},
				{"javax", "object", "the javax package root"},
				{"org", "object", "the org package root"},
				{"com", "object", "the com package root"},
				{"edu", "object", "the edu package root"},
				{"net", "object", "the net package root"},
		};
		for (String[] global : globals) {
			final Object type = evaluate(cx, scope, "typeof " + global[0]);
			expect(global[1].equals(String.valueOf(type)),
					"the scope has " + global[0] + " as a " + global[1] + " (" + global[2]
							+ "); typeof answered " + type);
		}
	}

	/**
	 * The classes a pack's own imports have to resolve, and the calls it makes with them.
	 *
	 * <p>Both import spellings are here because a pack mixes them: {@code importPackage(java.awt)}
	 * puts the package on the scope and every later bare name is resolved against it lazily, while
	 * {@code java.awt.image.BufferedImage} is walked one segment at a time. A port that only made
	 * one of the two work would load a pack and then fail on its first colour.</p>
	 */
	private static void checkAllowedClassesResolve(Context cx, Scriptable scope) {
		final Object terse = evaluate(cx, scope,
				"importPackage(java.awt); importPackage(java.awt.geom);"
						+ " [typeof Graphics2D, typeof Color, typeof Font, typeof RenderingHints,"
						+ "  typeof Ellipse2D, typeof BasicStroke, typeof AlphaComposite].join(\",\")");
		expect("function,function,function,function,function,function,function".equals(String.valueOf(terse)),
				"importPackage() resolves the AWT and Java2D classes a canvas draws with: " + terse);

		final Object qualified = evaluate(cx, scope,
				"typeof java.awt.image.BufferedImage + \",\" + typeof java.awt.geom.Line2D$Double");
		expect("function,function".equals(String.valueOf(qualified)),
				"a fully-qualified class resolves through the package roots too: " + qualified);

		/* The exact colour call next_train.js makes. Its own header warns that
		   new Color(r, g, b) binds to the float overload and throws for 0-255 values, so the
		   one-argument int constructor is the only spelling a pack may use -- and the value has to
		   survive the round trip. */
		final Object rgb = evaluate(cx, scope, "new Color(0x102030).getRGB() & 0xFFFFFF");
		expect(String.valueOf(rgb).equals(String.valueOf(0x102030))
						|| String.valueOf(rgb).equals(String.valueOf((double) 0x102030)),
				"new Color(int) keeps its value through the packed-int constructor: " + asHex(rgb));

		final Object font = evaluate(cx, scope, "new Font(\"SansSerif\", Font.PLAIN, 12).getSize()");
		expect("12".equals(String.valueOf(font)) || "12.0".equals(String.valueOf(font)),
				"new Font(name, style, size) works, which is the fallback sysFont() reaches for: " + font);
	}

	/**
	 * {@link ScriptEngine.Resources#getSystemFont} — the method that put HKR's board on screen.
	 *
	 * <p>Three behaviours, from the reference jar's bytecode rather than from the docs: the two names
	 * that are MTR's own bundled fonts, and the fall-through that starts as a JVM font lookup at
	 * {@code PLAIN} size {@code 1}. That last one is where this port deviates — a name the machine
	 * does not have is answered from MTR's bundled Noto instead of from AWT's silent substitution —
	 * and {@link ScriptFontFallbackCheck} is the check that covers it; what is asserted here is the
	 * contract every preset depends on either way, size 1 and a font rather than a throw.</p>
	 */
	private static void checkSystemFonts(Context cx, Scriptable scope) {
		/* "Noto Sans" is the one name whose branch loads a file. It has to come back as a real
		   Font, and a Font.createFont font is size 1 with one face -- which is why the pack has to
		   scale it with deriveFont and cannot rebuild it from a family name. */
		final Object noto = evaluate(cx, scope, "String(Resources.getSystemFont(\"Noto Sans\") != null)");
		expect("true".equals(String.valueOf(noto)),
				"getSystemFont(\"Noto Sans\") loaded mtr:font/noto-sans-semibold.ttf out of the pack");

		final Object derived = evaluate(cx, scope,
				"String(Resources.getSystemFont(\"Noto Sans\").deriveFont(Font.BOLD, 24.0).getSize())");
		expect("24.0".equals(String.valueOf(derived)) || "24".equals(String.valueOf(derived)),
				"deriveFont(int, float) scales it, the unambiguous overload the pack's header insists on: "
						+ derived);

		/* Everything else starts as new Font(name, PLAIN, 1) — and, since this port's fallback, ends
		   as MTR's bundled Noto when the machine does not have the name. HKR asks for "Noto Sans CJK
		   TC Medium", "Noto Sans SemiBold" and "Monospaced"; the first two are not installed here.
		   Either answer is a size-1 font the caller scales, which is the contract asserted below. */
		final Object fallback = evaluate(cx, scope,
				"String(Resources.getSystemFont(\"Noto Sans CJK TC Medium\").getSize())");
		expect("1".equals(String.valueOf(fallback)) || "1.0".equals(String.valueOf(fallback)),
				"an uninstalled name answers a size-1 font the caller scales with deriveFont: "
						+ fallback);

		final Object mono = evaluate(cx, scope, "String(Resources.getSystemFont(\"Monospaced\").getFamily())");
		expect("Monospaced".equals(String.valueOf(mono)),
				"a logical family name is a real font, so a pack's Monospaced request draws: " + mono);

		final Object missing = evaluate(cx, scope, "String(Resources.getSystemFont(\"No Such Family 4711\") != null)");
		expect("true".equals(String.valueOf(missing)),
				"an unknown family still answers a Font rather than throwing, as JCM 2.x does");

		final Object nullName = evaluate(cx, scope, "Resources.getSystemFont(null) == null");
		expect(Boolean.TRUE.equals(nullName) || "true".equals(String.valueOf(nullName)),
				"getSystemFont(null) answers null instead of throwing inside a renderer");
	}

	/**
	 * The sandbox, asked through every spelling the importer adds.
	 *
	 * <p>Each probe has to <b>use</b> the class: in Rhino a bare {@code java.lang.System} evaluates
	 * to a lazily-resolved package object and never consults the shutter, so asserting on the name
	 * alone would pass no matter what the shutter said. Touching a member is what forces the
	 * resolution — and what has to fail.</p>
	 *
	 * <p>The last two probes are the positive control. Without them this check would also pass if
	 * the importer had simply stopped working, which is the one way to make a sandbox look perfect
	 * while breaking every pack.</p>
	 */
	private static void checkShutterHoldsThroughTheImporter(Context cx, Scriptable scope) {
		final String[][] probes = {
				{"java.lang.System through the package root", "java.lang.System.nanoTime()"},
				{"java.lang.System through Packages", "Packages.java.lang.System.nanoTime()"},
				{"java.lang.System through importClass", "importClass(java.lang.System)"},
				{"java.lang.System.exit", "java.lang.System.exit(0)"},
				{"java.lang.Class", "java.lang.Class.forName(\"java.lang.String\").getName()"},
				{"java.lang.Runtime", "java.lang.Runtime.getRuntime().availableProcessors()"},
				{"java.io.File", "new java.io.File(\".\").getName()"},
				{"java.nio.file.Files", "java.nio.file.Files.readString(java.nio.file.Path.of(\".\"))"},
				{"java.io.File after importPackage(java.io)", "importPackage(java.io); new File(\".\").getName()"},
				{"java.lang.System after importPackage(java.lang)", "importPackage(java.lang); System.nanoTime()"},
				{"java.lang.System.exit after importPackage(java.lang)", "importPackage(java.lang); System.exit(0)"},
				{"java.lang.reflect after importPackage(java.lang.reflect)",
						"importPackage(java.lang.reflect); Method.class.getName()"},
		};
		for (String[] probe : probes) {
			try {
				final Object value = cx.evaluateString(scope, probe[1], "importer-shutter-check", 1, null);
				fail("USING " + probe[0] + " should have been refused, but \"" + probe[1]
						+ "\" returned " + value);
			} catch (Exception expected) {
				final String message = String.valueOf(expected.getMessage());
				pass("refused " + probe[0] + (message.contains("prohibited") ? "" : " (" + firstLine(message) + ")"));
			}
		}

		/* A refused name must not be left behind as a usable binding either. */
		final Object leftover = evaluate(cx, scope,
				"importPackage(java.lang); importPackage(java.lang.reflect);"
						+ " typeof System + \",\" + typeof Class + \",\" + typeof Runtime");
		expect("undefined,undefined,undefined".equals(String.valueOf(leftover)),
				"an import of a denied package leaves nothing usable in the scope: " + leftover);

		/* And the importer is still an importer. */
		final Object control = evaluate(cx, scope,
				"importPackage(java.awt); importPackage(java.awt.geom);"
						+ " String(new Color(0x009944).getRGB() & 0xFFFFFF)");
		expect(String.valueOf(control).equals(String.valueOf(0x009944))
						|| String.valueOf(control).equals(String.valueOf((double) 0x009944)),
				"the allowed half of the same importer still works: " + control);
	}

	/**
	 * The AWT path a real pack draws its whole screen with, run as a script.
	 *
	 * <p>{@code next_train.js} is 1867 lines and belongs to a resource pack, so it cannot live in
	 * this repository — but its <em>shape</em> can, and that shape is what failed. Every step below
	 * is a line of its {@code create()} or its draw helpers:</p>
	 * <ul>
	 *   <li>{@code importPackage(java.awt)}, then bare {@code Color} / {@code Font} /
	 *       {@code RenderingHints};</li>
	 *   <li>a font from {@code Resources.getSystemFont}, scaled with
	 *       {@code deriveFont(int style, float size)};</li>
	 *   <li>{@code canvas.graphics} used directly — {@code setRenderingHint}, {@code setColor},
	 *       {@code fillRect}, {@code drawString}, {@code setFont};</li>
	 *   <li>{@code upload()}, then one {@code Texture} draw call covering the panel.</li>
	 * </ul>
	 *
	 * <p>The pixels are read back out of the {@link GraphicsTexture} afterwards, because an API that
	 * accepts every call and paints nothing looks identical to a working one from the script's side
	 * — and "the panel is transparent" is the exact symptom being fixed.</p>
	 */
	private static void checkAwtCanvasPath(Context cx, Scriptable scope) {
		final ScriptRenderContext ctx = ScriptRenderContext.dryRun(136, 76, 1F);
		ScriptableObject.putProperty(scope, "PROBE_CTX", ctx);
		final int callsBefore = ctx.recordedCalls().size();
		final int liveBefore = GraphicsTexture.liveCount();

		/* The script, in the pack's own idiom. PROBE_STATE mirrors the state object the engine
		   hands create(). */
		final Object ok = evaluate(cx, scope,
				"var PROBE_STATE = {};"
						+ "function awtProbe() {"
						+ "  importPackage(java.awt);"
						+ "  importPackage(java.awt.geom);"
						+ "  var SS = 8;"
						+ "  var W = PROBE_CTX.panelWidth * SS, H = PROBE_CTX.panelHeight * SS;"
						+ "  var tex = new GraphicsTexture(W, H);"
						+ "  var base = Resources.getSystemFont(\"Noto Sans CJK TC Medium\");"
						+ "  if (base == null) base = new Font(\"SansSerif\", Font.PLAIN, 12);"
						+ "  var big = base.deriveFont(Font.BOLD, 40.0);"
						+ "  var g = tex.graphics;"
						+ "  g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);"
						+ "  g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);"
						+ "  g.setColor(new Color(0x181818));"
						+ "  g.fillRect(0, 0, W, H);"
						+ "  g.setColor(new Color(0xED8F90));"
						+ "  g.fillRect(8, 8, 16, 16);"
						+ "  g.setColor(new Color(0xFFFFFF));"
						+ "  g.setFont(big);"
						+ "  g.drawString(\"下班車\", 8, 72);"
						+ "  tex.upload();"
						+ "  Texture.create(\"flap\").texture(tex.identifier).pos(0, 0).size(W / SS, H / SS).draw(PROBE_CTX);"
						+ "  PROBE_STATE.tex = tex;"
						+ "  return true;"
						+ "}"
						+ "String(awtProbe())");
		expect("true".equals(String.valueOf(ok)), "the AWT canvas script ran to the end: " + ok);

		final GraphicsTexture canvas = unwrapCanvas(scope);
		if (canvas == null) {
			fail("the script's canvas could not be read back, so its pixels cannot be checked");
			return;
		}

		final BufferedImage image = canvas.bufferedImage;
		expect(image.getWidth() == 136 * 8 && image.getHeight() == 76 * 8,
				"the canvas is the panel at the pack's supersampling rate: " + image.getWidth() + "x" + image.getHeight());
		expect(image.getRGB(2, 2) == 0xFF181818,
				"fillRect() painted the background: " + String.format("%08X", image.getRGB(2, 2)));
		expect(image.getRGB(10, 10) == 0xFFED8F90,
				"fillRect() painted the accent block: " + String.format("%08X", image.getRGB(10, 10)));

		/* The text has to have marked the canvas. Nothing else in this probe is white. */
		final long white = countColour(image, 0xFFFFFFFF);
		expect(white > 0, "drawString() with a derived font put " + white + " white pixels on the canvas");

		/* The identifier reaches the frame as a texture draw call -- the call that makes the
		   panel show the image instead of nothing. */
		final int calls = ctx.recordedCalls().size();
		if (calls == callsBefore + 1
				&& ctx.recordedCalls().get(callsBefore).contains(canvas.identifier.toString())) {
			pass("the canvas reaches the frame as one texture draw call: " + ctx.recordedCalls().get(callsBefore));
		} else {
			fail("the canvas was not drawn: " + ctx.recordedCalls());
		}

		/* upload() with no client degrades, and it says so exactly once however often it is
		   called -- the second half is what stops a per-frame upload from filling a player's log. */
		evaluate(cx, scope, "PROBE_STATE.tex.upload(); PROBE_STATE.tex.upload();");
		expect(canvas.degradeReports() == 1,
				"a canvas that cannot be uploaded reports it once, not once per frame (reports="
						+ canvas.degradeReports() + ")");
		expect(!canvas.isClosed(), "the canvas is still usable after a refused upload");

		evaluate(cx, scope, "PROBE_STATE.tex.close();");
		expect(GraphicsTexture.liveCount() == liveBefore,
				"the script's canvas was released (live=" + GraphicsTexture.liveCount() + ")");
	}

	// ------------------------------------------------------------------

	/** @return how many pixels of the image are exactly {@code argb}. */
	private static long countColour(BufferedImage image, int argb) {
		long count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (image.getRGB(x, y) == argb) {
					count++;
				}
			}
		}
		return count;
	}

	/** Reads the canvas the probe stored in its own scope back out as a Java object. */
	private static GraphicsTexture unwrapCanvas(Scriptable scope) {
		final Object state = scope.get("PROBE_STATE", scope);
		if (!(state instanceof Scriptable)) {
			return null;
		}
		final Object value = ((Scriptable) state).get("tex", (Scriptable) state);
		if (value instanceof NativeJavaObject) {
			final Object unwrapped = ((NativeJavaObject) value).unwrap();
			if (unwrapped instanceof GraphicsTexture) {
				return (GraphicsTexture) unwrapped;
			}
		}
		return null;
	}

	private static Object evaluate(Context cx, Scriptable scope, String expression) {
		return cx.evaluateString(scope, expression, "importer-probe", 1, null);
	}

	/**
	 * A Rhino number as {@code 0xRRGGBB}, for a message that has to read like the script's own hex.
	 *
	 * <p>Defensive on purpose: a check that throws while formatting a <em>failure</em> message hides
	 * the failure it was reporting, so a value that is not a number is printed as it stands.</p>
	 */
	private static String asHex(Object value) {
		try {
			return String.format("0x%06X", ((long) Double.parseDouble(String.valueOf(value))) & 0xFFFFFFL);
		} catch (RuntimeException notANumber) {
			return String.valueOf(value);
		}
	}

	/** Rhino's refusal messages are multi-line; a check line wants the first one. */
	private static String firstLine(String message) {		final int newline = message.indexOf('\n');
		final String line = newline < 0 ? message : message.substring(0, newline);
		return line.length() <= 90 ? line : line.substring(0, 87) + "...";
	}

	private static void expect(boolean condition, String message) {
		if (condition) {
			pass(message);
		} else {
			fail(message);
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
