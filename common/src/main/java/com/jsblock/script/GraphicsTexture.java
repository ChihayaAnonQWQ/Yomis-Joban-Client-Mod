package com.jsblock.script;

import com.jsblock.Joban;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A texture a PIDS script draws into at runtime: {@code new GraphicsTexture(width, height)}.
 *
 * <p>Port of JCM 2.x's {@code com.lx862.mtrscripting.util.GraphicsTexture} — same name, same
 * constructor, same public fields ({@code identifier}, {@code bufferedImage}, {@code graphics},
 * {@code width}, {@code height}), same {@code upload()} and {@code close()}. A pack that builds a
 * departure board out of a canvas it paints itself, which is how the vehicle and eye-candy scripting
 * in JCM 2.x draws its signage, therefore needs no changes to its script; what changes is where the
 * bytes live.</p>
 *
 * <h2>Drawing on it</h2>
 * <p>{@code bufferedImage} and {@code graphics} are Java2D, exactly as in JCM 2.x, and
 * {@code java.awt.*} is on {@link ScriptClassShutter}'s allow-list, so a script can set a colour and
 * a font itself. The helpers below ({@code fillRect}, {@code drawText}, {@code drawTexture},
 * {@code clear}) exist because they are what a board actually does, and because the common ones
 * would otherwise be four lines of AWT each — but nothing stops a script from using the raw
 * {@code graphics} for anything else. The origin is the canvas's top-left, y downwards, as
 * everywhere else in a PIDS script; the size unit is the canvas's own pixels, not script units.</p>
 *
 * <pre>{@code
 * const canvas = new GraphicsTexture(128, 32);
 * canvas.fillRect(0, 0, 128, 32, 0x101010);
 * canvas.drawText("06:00", 4, 4, 0xFC9700, 20);
 * canvas.drawTexture("jsblock:textures/block/pids/plat_circle.png", 100, 4, 24, 24);
 * canvas.upload();
 * Texture.create("board").texture(canvas.identifier).pos(0, 0).size(128, 32).draw(ctx);
 * // ... and in dispose(): canvas.close();
 * }</pre>
 *
 * <h2>When the GPU side is built, and when it is not</h2>
 * <p>JCM 2.x allocates its {@code DynamicTexture} in the constructor. Here the image and its
 * {@code Graphics2D} are Java2D and are made immediately, while the texture — the one thing that
 * needs a GL context — is created on the first {@link #upload()}. That ordering is what lets the
 * canvas be created, drawn on and closed with no game running at all, which is how the headless
 * check covers this path; it also means a script that builds a canvas and never uploads it costs
 * nothing but the image.</p>
 *
 * <p>Every GL touch goes through the client thread: called from the render thread (where a PIDS
 * script normally runs) the work happens inline, and from a {@code BackgroundWorker} thread it is
 * queued, because creating a texture off that thread is not allowed and
 * {@code NativeImage}/{@code TextureManager} are not thread-safe.</p>
 *
 * <h2>Failure, and not leaking</h2>
 * <p>A canvas that cannot be uploaded — no client, no GL context, a driver refusal — logs once,
 * marks itself dead and keeps its image: the script carries on, the drawing on the canvas is not
 * lost, and the texture it names is simply Minecraft's missing-texture placeholder. Nothing throws
 * out of a renderer.</p>
 *
 * <p>Textures are not garbage collected: a {@code DynamicTexture} holds a GL texture name and a
 * native image until it is released. So {@link #close()} is required, and there are two backstops
 * for a pack that forgets. A script can only hold {@link #MAX_LIVE} unreleased canvases before the
 * next one is refused with a message naming {@code close()} — a leak becomes one visible error
 * rather than a slow GPU memory exhaustion — and a resource reload drops every compiled program, so
 * {@link #releaseAll()} releases the canvases that belonged to them at the same time.</p>
 */
public final class GraphicsTexture implements java.io.Closeable {

	/**
	 * How many canvases a script may hold unreleased at once.
	 *
	 * <p>JCM 2.x has no limit and no cleanup: a script that makes a canvas per frame leaks a GL
	 * texture per frame until the game dies. This is the smallest change that turns that into a
	 * diagnosable failure — generous enough for a preset that keeps one canvas per panel row and
	 * swaps between them, small enough that the memory behind it (a 256x256 canvas is 256 KB of
	 * image on each side) stays bounded.</p>
	 */
	public static final int MAX_LIVE = 64;

	/** Largest edge a canvas may have, in pixels. */
	public static final int MAX_SIZE = 2048;

	/** Canvases that exist and have not been closed, so a leak can be bounded and reported. */
	private static final Set<GraphicsTexture> LIVE = ConcurrentHashMap.newKeySet();

	/** Canvases whose release should happen when the resource reloads. */
	public final int width;
	public final int height;
	/** The pixels, ARGB. Java2D, so a script can draw on them with no GPU present. */
	public final BufferedImage bufferedImage;
	/** A {@code Graphics2D} over {@link #bufferedImage}; disposed by {@link #close()}. */
	public final Graphics2D graphics;
	/**
	 * The identifier this canvas is registered under, and the one a script hands to
	 * {@code Texture.create(...).texture(canvas.identifier)}. Unique per canvas, as in JCM 2.x.
	 */
	public final ResourceLocation identifier;

	/** The GPU-side texture; {@code null} until the first {@link #upload()}. */
	private volatile DynamicTexture dynamicTexture;
	/** Set once, so a failure is reported once and every later upload is a no-op. */
	private volatile boolean failed;
	/** Set by {@link #close()}, so a late upload cannot resurrect a released texture. */
	private volatile boolean closed;

	public GraphicsTexture(int width, int height) {
		if (width < 1 || height < 1) {
			throw new IllegalArgumentException("GraphicsTexture size must be at least 1x1, got "
					+ width + "x" + height);
		}
		if (width > MAX_SIZE || height > MAX_SIZE) {
			throw new IllegalArgumentException("GraphicsTexture size must be at most " + MAX_SIZE
					+ "x" + MAX_SIZE + ", got " + width + "x" + height);
		}
		if (LIVE.size() >= MAX_LIVE) {
			throw new IllegalStateException("GraphicsTexture: " + MAX_LIVE + " canvases are already"
					+ " open. A script must call close() on a canvas it is done with, normally from"
					+ " dispose(); the resource reload releases any that are left.");
		}

		this.width = width;
		this.height = height;
		this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		this.graphics = this.bufferedImage.createGraphics();
		/* Antialiased text and unquantised strokes: a PIDS canvas is normally magnified by the panel
		   -- and again by the pixelation pass -- so a hint that looks fine at 1:1 here is visible as
		   ragged edges there. Both are what JCM 2.x sets. */
		this.graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		this.graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		this.identifier = new ResourceLocation(Joban.MOD_ID,
				"dynamic/graphics/" + UUID.randomUUID().toString().toLowerCase(java.util.Locale.ROOT));
		LIVE.add(this);
		/* Remembered as already registered even before it is: the identifier names a texture this mod
		   makes, so the script texture loader must not go looking for a file behind it and report the
		   pack as broken. */
		ScriptTextures.markExternal(this.identifier);
	}

	// ==================================================================
	// Drawing, in the canvas's own pixels
	// ==================================================================

	/**
	 * {@code canvas.clear(color)} — fills the whole canvas.
	 *
	 * <p>Colours are ARGB ({@code 0xFFRRGGBB}), with the scripting surface's shorthand: a value with
	 * no alpha channel at all ({@code 0xRRGGBB}) is taken as opaque, exactly as
	 * {@code Text.color(0xFC9700)} is. {@code 0} is the one value that means transparent rather than
	 * black, so a canvas can be emptied.</p>
	 *
	 * <p>Taken as a {@code long} rather than an {@code int}, and the same for the three helpers below,
	 * because a JavaScript number is a double: Rhino refuses to narrow
	 * {@code 0xFF102030} (4279246896) into an {@code int} and throws "Cannot convert ... to
	 * java.lang.Integer" instead. Every full-ARGB literal a script writes has the high bit set, so an
	 * {@code int} parameter would make the whole notation unusable.</p>
	 */
	public void clear(long color) {
		fillRect(0, 0, width, height, color);
	}

	/** {@code canvas.fillRect(x, y, w, h, color)} — see {@link #clear} for the colour rule. */
	public void fillRect(double x, double y, double w, double h, long color) {
		graphics.setColor(new Color(argb((int) color), true));
		graphics.fillRect((int) Math.floor(x), (int) Math.floor(y),
				(int) Math.ceil(w), (int) Math.ceil(h));
	}

	/**
	 * {@code canvas.drawText(text, x, y, color, size)} — draws text with its baseline at {@code y}.
	 *
	 * <p>{@code x} is the left edge and the font is the JVM's default sans-serif at {@code size}
	 * pixels: the pack's own Minecraft fonts are not reachable from Java2D, and a canvas is normally
	 * used for lettering a texture rather than for a board's live text — that is what
	 * {@code Text.create(...)} is for.</p>
	 */
	public void drawText(String text, double x, double y, long color, double size) {
		if (text == null || text.isEmpty()) {
			return;
		}
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(1, (int) Math.round(size))));
		graphics.setColor(new Color(argb((int) color), true));
		graphics.drawString(text, (float) x, (float) y);
	}

	/**
	 * {@code canvas.measureText(text, size)} — the width {@link #drawText} would use, in pixels, so a
	 * script can centre or right-align it.
	 */
	public int measureText(String text, double size) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(1, (int) Math.round(size))));
		return graphics.getFontMetrics().stringWidth(text);
	}

	/**
	 * {@code canvas.drawTexture("ns:path.png", x, y, w, h)} — pastes a resource-pack texture into the
	 * canvas, scaled to the rectangle.
	 *
	 * <p>The other half of "a canvas can be composed": a pack's own artwork goes in, the script's own
	 * drawing goes on top, and the result is one texture the panel can use. Read through the same path
	 * guard as every other script read, and a missing or unreadable image is one warning and nothing
	 * drawn — see {@link ScriptTextures#readImage}.</p>
	 */
	public void drawTexture(Object location, double x, double y, double w, double h) {
		final ResourceLocation id;
		try {
			if (location instanceof ResourceLocation) {
				id = ScriptPaths.checkLocation((ResourceLocation) location);
			} else if (location != null) {
				id = ScriptPaths.resource(location.toString());
			} else {
				return;
			}
		} catch (ScriptPaths.RejectedPathException refused) {
			ScriptPaths.report("GraphicsTexture.drawTexture()", refused);
			return;
		}
		final BufferedImage image = ScriptTextures.readImage(id);
		if (image == null) {
			return;
		}
		graphics.drawImage(image, (int) Math.floor(x), (int) Math.floor(y),
				(int) Math.ceil(w), (int) Math.ceil(h), null);
	}

	// ==================================================================
	// The GPU side
	// ==================================================================

	/**
	 * {@code canvas.upload()} — sends the pixels drawn so far to the texture Minecraft will sample.
	 *
	 * <p>Call it after drawing and before the {@code Texture} call that uses the canvas. Cheap enough
	 * to call per frame — it copies {@code width * height} pixels and re-uploads the texture — and it
	 * is the only way the drawn image reaches the screen.</p>
	 */
	public void upload() {
		if (closed || failed) {
			return;
		}
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			/* No client at all: the headless check's case, and the only one where the caller cannot
			   fix anything. The image is still usable, so this is not an error -- but say it once, so
			   a script that expected a texture on screen is not left guessing. */
			degrade("there is no client running");
			return;
		}
		if (RenderSystem.isOnRenderThread()) {
			applyUpload();
		} else {
			minecraft.execute(this::applyUpload);
		}
	}

	/** The GL half of {@link #upload()}; must run on the client thread. */
	private void applyUpload() {
		if (closed || failed) {
			return;
		}
		try {
			DynamicTexture texture = this.dynamicTexture;
			if (texture == null) {
				/* The one allocation that needs a GL context, and it happens on first use rather than
				   in the constructor so a canvas nobody uploads never costs a texture name. */
				texture = new DynamicTexture(new NativeImage(width, height, false));
				this.dynamicTexture = texture;
				Minecraft.getInstance().getTextureManager().register(identifier, texture);
			}
			final NativeImage pixels = texture.getPixels();
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					pixels.setPixelRGBA(x, y, abgr(bufferedImage.getRGB(x, y)));
				}
			}
			texture.upload();
		} catch (Throwable t) {
			degrade(t.toString());
		}
	}

	/**
	 * Releases the texture and the graphics context. Safe to call twice, and safe to call from a
	 * script's {@code dispose()}.
	 *
	 * <p>The texture manager's release is what closes the {@code NativeImage}, which is native memory
	 * that the garbage collector does not see; skipping it is the leak this class exists to avoid.</p>
	 */
	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		LIVE.remove(this);
		ScriptTextures.forget(identifier);
		graphics.dispose();
		final DynamicTexture texture = this.dynamicTexture;
		this.dynamicTexture = null;
		if (texture == null) {
			return;
		}
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			/* Nothing to release it from: the context that made it is gone, and with it the texture. */
			return;
		}
		final Runnable release = () -> minecraft.getTextureManager().release(identifier);
		if (RenderSystem.isOnRenderThread()) {
			release.run();
		} else {
			minecraft.execute(release);
		}
	}

	/** @return whether this canvas has released its resources. */
	public boolean isClosed() {
		return closed;
	}

	/** @return how many canvases are open right now, for the headless check and diagnostics. */
	public static int liveCount() {
		return LIVE.size();
	}

	/**
	 * Releases every canvas that is still open; called when the resource manager reloads, which is
	 * also when every compiled program is dropped.
	 *
	 * <p>A canvas outlives the script that made it — nothing in the engine hands a script a "you are
	 * being reloaded" event — so without this a pack that leaves one open leaks it for the rest of the
	 * session, on every reload.</p>
	 */
	public static void releaseAll() {
		for (GraphicsTexture texture : LIVE.toArray(new GraphicsTexture[0])) {
			try {
				texture.close();
			} catch (Throwable t) {
				Joban.LOGGER.warn("[Joban Client] PIDS script canvas could not be released: {}", t.toString());
			}
		}
		LIVE.clear();
	}

	/**
	 * Reports the first failure and stands the canvas down.
	 *
	 * <p>Once, not per frame: an upload happens every frame a script draws, and a failure that is
	 * structural (no GL, an unsupported size) would otherwise fill the log. The drawing on the canvas
	 * is untouched, so the script keeps running and the panel shows the missing-texture placeholder
	 * rather than nothing at all.</p>
	 */
	private void degrade(String reason) {
		if (failed) {
			return;
		}
		failed = true;
		Joban.LOGGER.warn("[Joban Client] A PIDS script canvas ({}x{}) cannot be uploaded to the GPU: {}."
				+ " It is drawn on but never appears; the script continues.", width, height, reason);
	}

	// ==================================================================
	// Colour and pixel conversion
	// ==================================================================

	/**
	 * The scripting surface's colour shorthand, applied to canvas colours.
	 *
	 * <p>{@code 0xRRGGBB} becomes opaque, exactly as {@code Text.color(0xFC9700)} does in
	 * {@link ScriptDrawCall#opaque}; a value that already carries an alpha channel is used as it
	 * stands. {@code 0} means transparent, because that is the only way to say "nothing" and a black
	 * fully-opaque fill is what {@code 0xFF000000} is for.</p>
	 */
	static int argb(int color) {
		if (color == 0) {
			return 0;
		}
		return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
	}

	/**
	 * AWT's ARGB to the ABGR {@code NativeImage} stores.
	 *
	 * <p>JCM 2.x's own conversion, kept because it is the one that makes red and blue come out the
	 * right way round on this platform's native image format. A canvas whose colours came out swapped
	 * is the kind of bug that looks like a resource pack problem for a week.</p>
	 */
	private static int abgr(int argb) {
		final int alpha = (argb >> 24) & 0xFF;
		final int red = (argb >> 16) & 0xFF;
		final int green = (argb >> 8) & 0xFF;
		final int blue = argb & 0xFF;
		return (alpha << 24) | (blue << 16) | (green << 8) | red;
	}

	/**
	 * The ARGB copy JCM 2.x's {@code Resources.readBufferedImage} returns.
	 *
	 * <p>Kept as a public static because a v2 script's own reading path may hand back an image in
	 * whatever format {@code ImageIO} chose, and drawing one of those onto a canvas without this
	 * loses the alpha channel.</p>
	 */
	public static BufferedImage createArgbBufferedImage(BufferedImage source) {
		final BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		final Graphics2D copyGraphics = copy.createGraphics();
		copyGraphics.drawImage(source, 0, 0, null);
		copyGraphics.dispose();
		return copy;
	}
}
