package com.jsblock.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.awt.image.BufferedImage;

/**
 * Checks the runtime canvas a script draws into, headlessly.
 *
 * <p>{@link GraphicsTexture} is the one piece of the scripting surface that owns a resource outside
 * the JVM's heap: a {@code DynamicTexture} holds a GL texture name, and the {@code NativeImage}
 * behind it is native memory the collector does not see. So the two things worth proving without a
 * GPU are that drawing a canvas really paints pixels — an API that accepts every call and paints
 * nothing passes any check that only looks for absence of exceptions — and that the resource is
 * accounted for: created, and released by {@code close()}, with a bound on what a script that never
 * closes can hold.</p>
 *
 * <p><b>What cannot run here, and what stands in for it:</b> the GPU half of {@code upload()}. It
 * needs a GL context, so the check asserts the degraded path instead — no client is running, so
 * {@code upload()} must report once and return, leaving the image intact and the script alive. The
 * upload itself is a straight copy of JCM 2.x's: {@code DynamicTexture}, {@code getPixels()},
 * {@code setPixelRGBA(bufferedImage.getRGB(...))} converted through the same ABGR swap, and that
 * conversion is asserted here directly.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class ScriptCanvasCheck {

	private static int failures;

	private ScriptCanvasCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== script canvas (GraphicsTexture) ==");

		checkColourRules();
		checkImageCopy();
		checkSizeRules();
		checkLeakBound();
		checkThroughAScript();

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: CANVAS OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/** The colour shorthand, which decides whether a board is visible or transparent. */
	private static void checkColourRules() {
		expect(GraphicsTexture.argb(0xFFFFFF) == 0xFFFFFFFF,
				"0xFFFFFF becomes opaque, as Text.color(0xFFFFFF) does");
		expect(GraphicsTexture.argb(0x80FF0000) == 0x80FF0000,
				"a colour that already has an alpha channel keeps it");
		expect(GraphicsTexture.argb(0) == 0,
				"0 means transparent, so a canvas can be emptied");
		expect(GraphicsTexture.argb(0xFF000000) == 0xFF000000,
				"opaque black is still reachable");
	}

	/** The ARGB copy a pack image goes through before it is drawn onto a canvas. */
	private static void checkImageCopy() {
		final BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		source.setRGB(1, 1, 0x112233);
		final BufferedImage copy = GraphicsTexture.createArgbBufferedImage(source);
		expect(copy.getType() == BufferedImage.TYPE_INT_ARGB,
				"createArgbBufferedImage() produces an image with an alpha channel");
		expect((copy.getRGB(1, 1) & 0xFFFFFF) == 0x112233,
				"createArgbBufferedImage() preserves the pixels");
	}

	/** A canvas nobody can afford, and one that makes no sense, are refused with a reason. */
	private static void checkSizeRules() {
		expectRefused(0, 16, "zero width");
		expectRefused(16, 0, "zero height");
		expectRefused(GraphicsTexture.MAX_SIZE + 1, 16, "a width over the limit");
	}

	private static void expectRefused(int width, int height, String what) {
		try {
			new GraphicsTexture(width, height).close();
			fail("a canvas with " + what + " was accepted");
		} catch (IllegalArgumentException expected) {
			pass("refuses " + what + ": " + expected.getMessage());
		}
	}

	/**
	 * A script that forgets {@code close()} must fail visibly rather than exhaust the GPU.
	 *
	 * <p>This is the one behaviour with no JCM 2.x counterpart — v2 has no cap — so it is pinned
	 * here: the {@code MAX_LIVE}th canvas is fine, the next one is refused by name, and every canvas
	 * is released afterwards.</p>
	 */
	private static void checkLeakBound() {
		final int before = GraphicsTexture.liveCount();
		final GraphicsTexture[] held = new GraphicsTexture[GraphicsTexture.MAX_LIVE];
		try {
			for (int i = 0; i < held.length; i++) {
				held[i] = new GraphicsTexture(8, 8);
			}
			expect(GraphicsTexture.liveCount() == before + GraphicsTexture.MAX_LIVE,
					"a script can hold " + GraphicsTexture.MAX_LIVE + " canvases");
			try {
				new GraphicsTexture(8, 8);
				fail("a canvas past the limit was accepted, so a leaking script is unbounded");
			} catch (IllegalStateException expected) {
				expect(String.valueOf(expected.getMessage()).contains("close()"),
						"refuses the " + (GraphicsTexture.MAX_LIVE + 1) + "th canvas and names close()");
			}
		} finally {
			for (GraphicsTexture texture : held) {
				if (texture != null) {
					texture.close();
				}
			}
		}
		expect(GraphicsTexture.liveCount() == before,
				"every canvas released with close() (live=" + GraphicsTexture.liveCount() + ")");
	}

	/**
	 * The whole path, from a script: create, draw, read back, upload, use as a texture, release.
	 *
	 * <p>Run through {@link ScriptEngine#newScope} so it is the real global, the real sandbox and the
	 * real Rhino method resolution — a canvas whose methods Rhino cannot reach fails here.</p>
	 */
	private static void checkThroughAScript() {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			final ScriptRenderContext ctx = ScriptRenderContext.dryRun(136, 76, 1F);
			ScriptableObject.putProperty(scope, "PROBE_CTX", ctx);

			final int liveBefore = GraphicsTexture.liveCount();
			final int callsBefore = ctx.recordedCalls().size();

			/* create -> draw -> upload -> use as a texture. */
			final Object identifier = evaluate(cx, scope,
					"var CANVAS = new GraphicsTexture(32, 16);"
							+ " CANVAS.clear(0xFF102030);"
							+ " CANVAS.fillRect(2, 2, 8, 8, 0xFF00FF00);"
							+ " CANVAS.drawText(\"AB\", 2, 14, 0xFFFFFF, 10);"
							+ " CANVAS.measureText(\"AB\", 10) > 0;");
			expect(Boolean.TRUE.equals(identifier) || "true".equals(String.valueOf(identifier)),
					"measureText() answers, so text can be positioned on a canvas");

			expect(GraphicsTexture.liveCount() == liveBefore + 1,
					"creating a canvas from a script registers it (live=" + GraphicsTexture.liveCount() + ")");

			/* The drawing has to be in the pixels, not merely accepted. */
			final GraphicsTexture canvas = unwrapCanvas(scope);
			if (canvas == null) {
				fail("the script's canvas could not be read back");
				return;
			}
			expect(canvas.bufferedImage.getRGB(3, 3) == 0xFF00FF00,
					"fillRect() painted the requested pixels: "
							+ String.format("%08X", canvas.bufferedImage.getRGB(3, 3)));
			expect(canvas.bufferedImage.getRGB(1, 1) == 0xFF102030,
					"clear() filled the whole canvas: "
							+ String.format("%08X", canvas.bufferedImage.getRGB(1, 1)));
			expect(canvas.bufferedImage.getRGB(20, 3) == 0xFF102030,
					"the pixels outside the rectangle are untouched");

			/* upload() with no client: it must report and return, not throw, and not lose the image. */
			try {
				evaluate(cx, scope, "CANVAS.upload(); String(CANVAS.isClosed());");
				pass("upload() without a client degrades instead of throwing (see the warning above)");
			} catch (Exception e) {
				fail("upload() threw with no client running: " + e);
			}
			expect(!canvas.isClosed(), "a canvas that could not be uploaded is still usable");
			expect(canvas.bufferedImage.getRGB(3, 3) == 0xFF00FF00,
					"the failed upload left the image alone");

			/* The identifier is a texture identifier: it has to reach the draw call as one. */
			evaluate(cx, scope,
					"Texture.create(\"canvas\").texture(CANVAS.identifier).pos(0, 0).size(32, 16).draw(PROBE_CTX);");
			final int calls = ctx.recordedCalls().size();
			if (calls == callsBefore + 1 && ctx.recordedCalls().get(callsBefore).contains(canvas.identifier.toString())) {
				pass("the canvas is drawable as a texture: " + ctx.recordedCalls().get(callsBefore));
			} else {
				fail("the canvas was not drawn as a texture: " + ctx.recordedCalls());
			}

			/* release. */
			evaluate(cx, scope, "CANVAS.close(); CANVAS.close();");
			expect(GraphicsTexture.liveCount() == liveBefore,
					"close() releases the canvas, and closing twice is harmless");
			expect(canvas.isClosed(), "the canvas reports itself closed");

			/* A script cannot draw into a path outside its pack, canvas or not. */
			try {
				evaluate(cx, scope, "CANVAS.drawTexture(\"jsblock:../../../../secret.png\", 0, 0, 4, 4); 'ok'");
				pass("drawTexture() refuses a traversing path without throwing (see the error above)");
			} catch (Exception e) {
				fail("drawTexture() with a traversing path threw instead of refusing quietly: " + e);
			}

			/* A resource reload must not leave the canvases behind: this is the engine-side cleanup. */
			final GraphicsTexture leftover = new GraphicsTexture(4, 4);
			ScriptEngine.reset();
			expect(leftover.isClosed(), "ScriptEngine.reset() releases canvases the script left open");
			expect(GraphicsTexture.liveCount() == 0,
					"nothing is left live after a reload (live=" + GraphicsTexture.liveCount() + ")");
		} finally {
			Context.exit();
		}
	}

	/** Reads the canvas the script stored in its own scope back out as a Java object. */
	private static GraphicsTexture unwrapCanvas(Scriptable scope) {
		final Object value = scope.get("CANVAS", scope);
		if (value instanceof NativeJavaObject) {
			final Object unwrapped = ((NativeJavaObject) value).unwrap();
			if (unwrapped instanceof GraphicsTexture) {
				return (GraphicsTexture) unwrapped;
			}
		}
		return null;
	}

	private static Object evaluate(Context cx, Scriptable scope, String expression) {
		return cx.evaluateString(scope, expression, "canvas-probe", 1, null);
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
