package com.jsblock.script;

import com.jsblock.screen.IDrawingJoban;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The drawing builders a PIDS script uses: {@code Text}, {@code Texture} and
 * {@code Rectangle}.
 *
 * <p>Ports {@code TextWrapper}, {@code TextureWrapper} and {@code RectangleWrapper} from
 * JCM 2.x's {@code com.lx862.jcm.mod.scripting.pids}. The classes are nested so Rhino can be
 * handed each one as a {@code NativeJavaClass} under the name the scripts expect.</p>
 *
 * <p>JCM 2.x schedules its drawing through MTR 4's {@code MainRenderer} and
 * {@code QueuedRenderLayer}; on MTR 3 the panel is already being drawn inside the
 * block-entity pass, so each call draws straight away through {@code mtr.client.IDrawing}
 * and YJCM's {@link IDrawingJoban}.</p>
 */
public final class ScriptDrawCalls {

	/** Fallback font, matching JCM 2.x's {@code Init.MOD_ID + ":mtr"}. */
	private static final String DEFAULT_FONT = "mtr:mtr";

	/** Texture used by {@code Rectangle}, matching JCM 2.x. */
	private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation("mtr", "textures/block/white.png");

	private ScriptDrawCalls() {
	}

	// ==================================================================
	// Rectangle
	// ==================================================================

	/** {@code Rectangle.create("name").color(0xFFFFFF).pos(x, y).size(w, h).draw(ctx)} */
	public static class Rectangle extends ScriptDrawCall<Rectangle> {

		protected int color = IGui.ARGB_WHITE;
		protected boolean naturalLight = false;

		protected Rectangle() {
			super(20, 20);
		}

		public static Rectangle create() {
			return new Rectangle();
		}

		/** JCM 2.x signatures take a comment string that is ignored. */
		public static Rectangle create(String comment) {
			return create();
		}

		public Rectangle color(int color) {
			this.color = opaque(color);
			return this;
		}

		public Rectangle naturalLight() {
			this.naturalLight = true;
			return this;
		}

		/**
		 * Accepted for JCM 2.x compatibility and ignored.
		 *
		 * <p>JCM 2.x selects one of MTR 4's {@code QueuedRenderLayer} values here. MTR 3 has no
		 * equivalent enum, and PIDS panels are drawn on MTR's emissive "light" layer anyway, so
		 * honouring the request would only change how the panel reacts to world lighting.</p>
		 */
		public Rectangle renderType(String renderType) {
			return this;
		}

		@Override
		public String describe() {
			return String.format("Rectangle(z=%s pos=%s,%s size=%sx%s color=%08X)", z,
					x, y, w, h, color);
		}

		@Override
		void draw(ScriptRenderContext ctx, float z) {
			pushTransform(ctx, z);
			final VertexConsumer vertexConsumer = ctx.vertexConsumers.getBuffer(
					MoreRenderLayers.getLight(WHITE_TEXTURE, false));
			IDrawing.drawTexture(ctx.matrices, vertexConsumer,
					0, 0, 0, (float) w, (float) h, 0,
					0, 0, 1, 1, ctx.facing,
					color, naturalLight ? ctx.light : IGui.MAX_LIGHT_GLOWING);
			popTransform(ctx);
		}
	}

	// ==================================================================
	// Texture
	// ==================================================================

	/** {@code Texture.create("name").texture("ns:path.png").pos(x, y).size(w, h).draw(ctx)} */
	public static class Texture extends ScriptDrawCall<Texture> {

		protected ResourceLocation textureId;
		protected int color = IGui.ARGB_WHITE;
		protected boolean naturalLight = false;
		protected float u1 = 0;
		protected float v1 = 0;
		protected float u2 = 1;
		protected float v2 = 1;

		protected Texture() {
			super(20, 20);
		}

		public static Texture create() {
			return new Texture();
		}

		public static Texture create(String comment) {
			return create();
		}

		public Texture texture(String id) {
			/* Scripts write paths without the "textures/" prefix, e.g.
			   "nanbin:pids/image/crt_pids_1.png"; ResourceLocation keeps it as-is and the
			   texture manager resolves it under assets/<ns>/textures/. */
			this.textureId = new ResourceLocation(id);
			return this;
		}

		/** Rhino passes back whatever {@code Resources.id(...)} produced. */
		public Texture texture(Object id) {
			if (id instanceof ResourceLocation) {
				this.textureId = (ResourceLocation) id;
			} else if (id != null) {
				this.textureId = new ResourceLocation(id.toString());
			}
			return this;
		}

		/**
		 * {@code .color(0xRRGGBB)} — a preset writes the colour without an alpha channel, so
		 * it is made opaque here; see {@link ScriptDrawCall#opaque}.
		 *
		 * <p>Normalised on the way in rather than at draw time, so that the value the debug
		 * trace and the headless check print is the value that reaches the renderer.</p>
		 */
		public Texture color(int color) {
			this.color = opaque(color);
			return this;
		}

		public Texture uv(float u1, float v1, float u2, float v2) {
			this.u1 = u1;
			this.v1 = v1;
			this.u2 = u2;
			this.v2 = v2;
			return this;
		}

		public Texture uv(float u2, float v2) {
			this.u1 = 0;
			this.v1 = 0;
			this.u2 = u2;
			this.v2 = v2;
			return this;
		}

		public Texture naturalLight() {
			this.naturalLight = true;
			return this;
		}

		/** Accepted for JCM 2.x compatibility and ignored; see {@link Rectangle#renderType}. */
		public Texture renderType(String renderType) {
			return this;
		}

		@Override
		public String describe() {
			return String.format("Texture(z=%s pos=%s,%s size=%sx%s texture=%s uv=%s,%s,%s,%s color=%08X)", z,
					x, y, w, h, textureId, u1, v1, u2, v2, color);
		}

		@Override
		public void validate() {
			if (textureId == null) {
				throw new IllegalArgumentException("texture must be filled");
			}
		}

		@Override
		void draw(ScriptRenderContext ctx, float z) {
			pushTransform(ctx, z);
			/* Script paths are relative to assets/<namespace>/, not assets/<namespace>/textures/;
			   see ScriptTextures for why the render layer alone would miss. */
			/* translucent = true. The second argument of MoreRenderLayers.getLight is the alpha
			   switch, and a script texture is routinely mostly transparent -- JCM 2.x's weather
			   icons are 64x64 with roughly ninety percent of their pixels at alpha 0. Drawn
			   through the opaque layer that transparent area comes out as a solid white square,
			   which is what the weather icon looked like in game. JCM 2.x defaults a Texture to
			   QueuedRenderLayer.LIGHT_2 for the same reason. */
			final RenderType layer = MoreRenderLayers.getLight(ScriptTextures.resolve(textureId), true);
			final VertexConsumer vertexConsumer = ctx.vertexConsumers.getBuffer(layer);
			IDrawing.drawTexture(ctx.matrices, vertexConsumer,
					0, 0, 0, (float) w, (float) h, 0,
					u1, v1, u2, v2, ctx.facing,
					color, naturalLight ? ctx.light : IGui.MAX_LIGHT_GLOWING);
			popTransform(ctx);
			/* End the batch here, so this quad cannot be reordered behind one the script drew
			   before it; see ScriptRenderContext.flushLayer. */
			ctx.flushLayer(layer);
		}
	}

	// ==================================================================
	// Text
	// ==================================================================

	/**
	 * {@code Text.create("name").text("...").scale(0.8).pos(x, y).size(w, h).rightAlign().draw(ctx)}
	 *
	 * <p>Overflow modes follow JCM 2.x: {@code 0} none, {@code 1} stretch XY,
	 * {@code 2} scale XY, {@code 3} wrap, {@code 4} marquee.</p>
	 */
	public static class Text extends ScriptDrawCall<Text> {

		public static final int OVERFLOW_NONE = 0;
		public static final int OVERFLOW_STRETCH_XY = 1;
		public static final int OVERFLOW_SCALE_XY = 2;
		public static final int OVERFLOW_WRAP = 3;
		public static final int OVERFLOW_MARQUEE = 4;

		/** JCM 2.x uses -1 for left, 0 for centre and 1 for right. */
		private static final int ALIGN_LEFT = -1;
		private static final int ALIGN_CENTER = 0;
		private static final int ALIGN_RIGHT = 1;

		protected String textContent;
		protected String fontId = DEFAULT_FONT;
		protected boolean shadow = false;
		protected boolean italic = false;
		protected boolean bold = false;
		protected double scale = 1;
		protected int overflowMode = OVERFLOW_NONE;
		protected int alignment = ALIGN_LEFT;
		protected int color = IGui.ARGB_WHITE;
		protected double lineHeight = 1;
		protected double marqueeDurationOverride = -1;
		protected double marqueeProgressOverride = -1;
		protected boolean naturalLight = false;

		protected Text() {
			super(100, 25);
		}

		public static Text create() {
			return new Text();
		}

		public static Text create(String comment) {
			return create();
		}

		public Text text(String str) {
			this.textContent = str;
			return this;
		}

		public Text scale(double i) {
			this.scale = i;
			return this;
		}

		public Text leftAlign() {
			this.alignment = ALIGN_LEFT;
			return this;
		}

		public Text centerAlign() {
			this.alignment = ALIGN_CENTER;
			return this;
		}

		public Text rightAlign() {
			this.alignment = ALIGN_RIGHT;
			return this;
		}

		public Text shadowed() {
			this.shadow = true;
			return this;
		}

		public Text stretchXY() {
			this.overflowMode = OVERFLOW_STRETCH_XY;
			return this;
		}

		public Text scaleXY() {
			this.overflowMode = OVERFLOW_SCALE_XY;
			return this;
		}

		public Text wrapText() {
			this.overflowMode = OVERFLOW_WRAP;
			return this;
		}

		public Text marquee() {
			this.overflowMode = OVERFLOW_MARQUEE;
			return this;
		}

		public Text marquee(double durationTickOverride) {
			this.overflowMode = OVERFLOW_MARQUEE;
			this.marqueeDurationOverride = durationTickOverride;
			return this;
		}

		public Text withMarqueeProgress(double marqueeProgressOverride) {
			this.marqueeProgressOverride = marqueeProgressOverride;
			return this;
		}

		public Text lineHeight(double lineHeightFactor) {
			this.lineHeight = lineHeightFactor;
			return this;
		}

		public Text fontMC() {
			this.fontId = DEFAULT_FONT;
			return this;
		}

		public Text font(String font) {
			this.fontId = font;
			return this;
		}

		/**
		 * Accepts whatever {@code Resources.id(...)} or a raw string produced. JCM 2.x takes an
		 * {@code Identifier} here; MTR 3's font renderer wants the {@code "namespace:path"}
		 * form, which both spellings reduce to.
		 */
		public Text font(Object font) {
			if (font instanceof ResourceLocation) {
				this.fontId = font.toString();
			} else if (font != null) {
				this.fontId = font.toString();
			}
			return this;
		}

		/** Accepted for JCM 2.x compatibility and ignored; see {@link Rectangle#renderType}. */
		public Text renderType(String renderType) {
			return this;
		}

		/**
		 * @return the width of the current text in font pixels, matching JCM 2.x's
		 * {@code measureWidth}.
		 */
		public int measureWidth() {
			if (textContent == null) {
				throw new IllegalStateException("Text is not set!");
			}
			return Minecraft.getInstance().font.width(textContent);
		}

		public Text italic() {
			this.italic = true;
			return this;
		}

		public Text bold() {
			this.bold = true;
			return this;
		}

		public Text color(int color) {
			this.color = color;
			return this;
		}

		public Text naturalLight() {
			this.naturalLight = true;
			return this;
		}

		@Override
		public void validate() {
			if (textContent == null) {
				throw new IllegalArgumentException("Text must be filled");
			}
		}

		@Override
		public String describe() {
			return String.format("Text(z=%s pos=%s,%s box=%sx%s scale=%s align=%s overflow=%s color=%08X text=%s)", z,
					x, y, w, h, scale, alignment, overflowMode, color, quote(textContent));
		}

		private static String quote(String value) {
			if (value == null) {
				return "null";
			}
			final String flat = value.replace("\n", "\\n");
			return '"' + (flat.length() > 60 ? flat.substring(0, 57) + "..." : flat) + '"';
		}

		@Override
		void draw(ScriptRenderContext ctx, float z) {
			if (textContent.isEmpty()) {
				return;
			}
			pushTransform(ctx, z);

			/* No transform for the marquee: the scroll is a window onto the string now, so
			   the run never leaves the box. See renderText. */

			final IGui.HorizontalAlignment horizontalAlignment;
			switch (alignment) {
				case ALIGN_CENTER:
					horizontalAlignment = IGui.HorizontalAlignment.CENTER;
					break;
				case ALIGN_RIGHT:
					horizontalAlignment = IGui.HorizontalAlignment.RIGHT;
					break;
				default:
					horizontalAlignment = IGui.HorizontalAlignment.LEFT;
					break;
			}

			/* drawStringWithFont divides the stack by `scale`, so passing 1/textScale makes
			   the glyphs textScale times larger -- the same effect as JCM 2.x calling
			   graphicsHolder.scale(textScale). */
			final float drawScale = scale == 0 ? 1F : (float) (1D / scale);

			final float maxWidth;
			final float maxHeight;
			final boolean keepRatio;
			switch (overflowMode) {
				case OVERFLOW_STRETCH_XY:
					maxWidth = (float) w;
					maxHeight = (float) h;
					keepRatio = false;
					break;
				case OVERFLOW_SCALE_XY:
					maxWidth = (float) w;
					maxHeight = (float) h;
					keepRatio = true;
					break;
				case OVERFLOW_WRAP:
				case OVERFLOW_MARQUEE:
					maxWidth = (float) w;
					maxHeight = -1F;
					keepRatio = true;
					break;
				default:
					maxWidth = -1F;
					maxHeight = -1F;
					keepRatio = true;
					break;
			}

			final int textLight = naturalLight ? ctx.light : IGui.MAX_LIGHT_GLOWING;
			/* sameSize = true: no doubling, which is what the presets are written against.
			 *
			 * A JCM 2.x preset is laid out for MTR 4, where the text it draws did not carry an
			 * extra CJK multiplier. Turning the multiplier on here made the unboxed runs --
			 * HKR's "本班車將會停靠於" and friends, which set no size(...) -- come out at twice
			 * the width their author wrote, straight off the panel.
			 *
			 * The flag also has to agree with IDrawingJoban's layout, which is where the other
			 * half of the problem lived: it reserved 2x for CJK regardless, so boxed runs were
			 * measured for a doubled string and then drawn single size, landing at half the
			 * size of their own box. That is now tied to the same flag. */
			IDrawingJoban.drawStringWithFont(
					ctx.matrices,
					Minecraft.getInstance().font,
					ctx.immediate,
					renderText(ctx),
					horizontalAlignment,
					IGui.VerticalAlignment.TOP,
					horizontalAlignment,
					0, 0,
					maxWidth, maxHeight,
					drawScale,
					keepRatio,
					color,
					shadow,
					textLight,
					fontId == null ? DEFAULT_FONT : fontId,
					true,
					null
			);

			popTransform(ctx);
		}

		/**
		 * @return the part of the string the box can show, for a marquee.
		 *
		 * <p>JCM 2.x scrolls a marquee character by character and clips the run against its
		 * box. This does the same by choosing the window rather than clipping pixels: leading
		 * characters that have scrolled past the left edge are dropped, and characters that
		 * would stick out of the right edge are trimmed. Sliding the whole run through a
		 * transform instead -- which is what this did before -- simply drew it outside the
		 * panel, since nothing in this render path clips to a rectangle.</p>
		 */
		private String renderText(ScriptRenderContext ctx) {
			if (overflowMode != OVERFLOW_MARQUEE) {
				return textContent;
			}
			final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
			final double total = font.width(textContent);
			if (total <= w) {
				return textContent;
			}

			/* Travel far enough for the whole string to pass through the box once. */
			final double scrolled = (total + w) * marqueeProgress();
			int cut = 0;
			while (cut < textContent.length() && font.width(textContent.substring(0, cut + 1)) < scrolled) {
				cut++;
			}

			String visible = textContent.substring(cut);
			while (!visible.isEmpty() && font.width(visible) > w) {
				visible = visible.substring(0, visible.length() - 1);
			}
			return visible;
		}

		/**
		 * The marquee's position in its cycle, from 0 to 1.
		 *
		 * <p>JCM 2.x scrolls character by character; the duration defaults to ten ticks per
		 * character, which is what the length-based default reproduces here.</p>
		 */
		private double marqueeProgress() {
			final double cycleTicks = marqueeDurationOverride > 0
					? marqueeDurationOverride * 20D
					: Math.max(20D, textContent.length() * 10D);
			return marqueeProgressOverride >= 0
					? marqueeProgressOverride
					: (System.currentTimeMillis() / 50D % cycleTicks) / cycleTicks;
		}
	}
}
