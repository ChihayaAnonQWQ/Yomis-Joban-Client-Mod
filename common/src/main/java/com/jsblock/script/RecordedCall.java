package com.jsblock.script;

import com.jsblock.pids.PIDSComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * One draw call a PIDS script asked for, captured <b>by value</b> so it can be replayed later
 * on the render thread.
 *
 * <p>This is the second half of JCM 2.x/v2's execution model. There, {@code PIDSScriptInstance}
 * runs the preset on a script thread, the context queues {@code PIDSDrawCall}s, and the renderer
 * replays them next frame. Here the same shape is used: {@link ScriptRenderContext} records into
 * a list of these objects while the script thread runs, and {@link #replay(ScriptRenderContext)}
 * reconstructs the original builder and executes it against the live context.</p>
 *
 * <h2>Why a copy, and not the builder itself</h2>
 * <p>A script is free to keep a {@code Text}/{@code Texture}/{@code Rectangle} object in its
 * {@code state} and mutate it between frames. Holding the builder would let the script thread
 * change a call while the render thread is replaying it. Every field that affects drawing is
 * therefore read once, at record time, into this immutable object; the builder is discarded.
 * The same reason is why {@code state} and the {@code pids} wrapper are not referenced here.</p>
 *
 * <h2>Descriptions</h2>
 * <p>{@link #describe()} rebuilds the builder from the captured fields and asks <em>it</em> for
 * its description, so the dry-run strings the headless checks compare are produced by exactly
 * the same code as before. That also makes the description a round-trip test: a field that is
 * captured but not restored shows up as a different string when the record is replayed into a
 * recording context.</p>
 */
public abstract class RecordedCall {

	/** Depth offset this call used when it was recorded, in the caller's local units. */
	public final float z;

	RecordedCall(float z) {
		this.z = z;
	}

	/** @return the same one-line description the original draw call produced. */
	public abstract String describe();

	/**
	 * @return every captured field, including the ones {@link #describe()} does not print
	 * (shadow, bold, natural light, matrices, ...), for the round-trip check
	 */
	public abstract String signature();

	/**
	 * Executes this call against {@code ctx}.
	 *
	 * <p>A live context draws it immediately, in call order, with the recorded depth. A recording
	 * context captures it again, which is what lets the check compare "run the script inline"
	 * against "record on one context, replay on another" without a GPU.</p>
	 */
	public abstract void replay(ScriptRenderContext ctx);

	// ==================================================================
	// Factory
	// ==================================================================

	/** Captures a builder, which must already have been validated. */
	static RecordedCall of(ScriptDrawCall<?> call, float z) {
		if (call instanceof ScriptDrawCalls.Text) {
			return new TextCall((ScriptDrawCalls.Text) call, z);
		}
		if (call instanceof ScriptDrawCalls.Texture) {
			return new TextureCall((ScriptDrawCalls.Texture) call, z);
		}
		if (call instanceof ScriptDrawCalls.Rectangle) {
			return new RectangleCall((ScriptDrawCalls.Rectangle) call, z);
		}
		if (call instanceof ScriptQuadDrawCall) {
			return new QuadCall((ScriptQuadDrawCall) call, z);
		}
		throw new IllegalArgumentException("Unsupported draw call for recording: " + call.getClass().getName());
	}

	/** Captures a parsed component. */
	static RecordedCall ofComponent(ScriptComponent component, float z) {
		return new ComponentCall(component.component(), component.type(), z);
	}

	// ==================================================================
	// Text
	// ==================================================================

	/** A captured {@code Text} call. */
	public static final class TextCall extends RecordedCall {
		private final double x;
		private final double y;
		private final double w;
		private final double h;
		private final String text;
		private final String fontId;
		private final boolean shadow;
		private final boolean italic;
		private final boolean bold;
		private final double scale;
		private final int overflowMode;
		private final int alignment;
		private final int color;
		private final double lineHeight;
		private final double marqueeDurationOverride;
		private final double marqueeProgressOverride;
		private final boolean naturalLight;
		private final Matrix4f matrix;

		TextCall(ScriptDrawCalls.Text call, float z) {
			super(z);
			this.x = call.x;
			this.y = call.y;
			this.w = call.w;
			this.h = call.h;
			this.text = call.textContent;
			this.fontId = call.fontId;
			this.shadow = call.shadow;
			this.italic = call.italic;
			this.bold = call.bold;
			this.scale = call.scale;
			this.overflowMode = call.overflowMode;
			this.alignment = call.alignment;
			this.color = call.color;
			this.lineHeight = call.lineHeight;
			this.marqueeDurationOverride = call.marqueeDurationOverride;
			this.marqueeProgressOverride = call.marqueeProgressOverride;
			this.naturalLight = call.naturalLight;
			this.matrix = call.scriptMatrices == null ? null : new Matrix4f(call.scriptMatrices.last());
		}

		ScriptDrawCalls.Text builder() {
			final ScriptDrawCalls.Text call = new ScriptDrawCalls.Text();
			call.x = x;
			call.y = y;
			call.w = w;
			call.h = h;
			call.z = z;
			call.textContent = text;
			call.fontId = fontId;
			call.shadow = shadow;
			call.italic = italic;
			call.bold = bold;
			call.scale = scale;
			call.overflowMode = overflowMode;
			call.alignment = alignment;
			call.color = color;
			call.lineHeight = lineHeight;
			call.marqueeDurationOverride = marqueeDurationOverride;
			call.marqueeProgressOverride = marqueeProgressOverride;
			call.naturalLight = naturalLight;
			call.scriptMatrices = matrix == null ? null : ScriptMath.Matrices.of(matrix);
			return call;
		}

		@Override
		public String describe() {
			return builder().describe();
		}

		@Override
		public String signature() {
			return String.format(Locale.ROOT,
					"Text(z=%s pos=%s,%s size=%sx%s text=%s font=%s shadow=%s italic=%s bold=%s"
							+ " scale=%s overflow=%s align=%s color=%08X lineHeight=%s marqueeDuration=%s"
							+ " marqueeProgress=%s naturalLight=%s matrix=%s)",
					z, x, y, w, h, text, fontId, shadow, italic, bold, scale, overflowMode, alignment,
					color, lineHeight, marqueeDurationOverride, marqueeProgressOverride, naturalLight,
					matrix == null ? "none" : matrix.toString());
		}

		@Override
		public void replay(ScriptRenderContext ctx) {
			ctx.acceptRecorded(builder(), z);
		}
	}

	// ==================================================================
	// Texture
	// ==================================================================

	/** A captured {@code Texture} call. */
	public static final class TextureCall extends RecordedCall {
		private final double x;
		private final double y;
		private final double w;
		private final double h;
		private final ResourceLocation textureId;
		private final int color;
		private final boolean naturalLight;
		private final float u1;
		private final float v1;
		private final float u2;
		private final float v2;
		private final Matrix4f matrix;

		TextureCall(ScriptDrawCalls.Texture call, float z) {
			super(z);
			this.x = call.x;
			this.y = call.y;
			this.w = call.w;
			this.h = call.h;
			this.textureId = call.textureId;
			this.color = call.color;
			this.naturalLight = call.naturalLight;
			this.u1 = call.u1;
			this.v1 = call.v1;
			this.u2 = call.u2;
			this.v2 = call.v2;
			this.matrix = call.scriptMatrices == null ? null : new Matrix4f(call.scriptMatrices.last());
		}

		ScriptDrawCalls.Texture builder() {
			final ScriptDrawCalls.Texture call = new ScriptDrawCalls.Texture();
			call.x = x;
			call.y = y;
			call.w = w;
			call.h = h;
			call.z = z;
			call.textureId = textureId;
			call.color = color;
			call.naturalLight = naturalLight;
			call.u1 = u1;
			call.v1 = v1;
			call.u2 = u2;
			call.v2 = v2;
			call.scriptMatrices = matrix == null ? null : ScriptMath.Matrices.of(matrix);
			return call;
		}

		@Override
		public String describe() {
			return builder().describe();
		}

		@Override
		public String signature() {
			return String.format(Locale.ROOT,
					"Texture(z=%s pos=%s,%s size=%sx%s texture=%s color=%08X naturalLight=%s"
							+ " uv=%s,%s,%s,%s matrix=%s)",
					z, x, y, w, h, textureId, color, naturalLight, u1, v1, u2, v2,
					matrix == null ? "none" : matrix.toString());
		}

		@Override
		public void replay(ScriptRenderContext ctx) {
			ctx.acceptRecorded(builder(), z);
		}
	}

	// ==================================================================
	// Rectangle
	// ==================================================================

	/** A captured {@code Rectangle} call. */
	public static final class RectangleCall extends RecordedCall {
		private final double x;
		private final double y;
		private final double w;
		private final double h;
		private final int color;
		private final boolean naturalLight;

		RectangleCall(ScriptDrawCalls.Rectangle call, float z) {
			super(z);
			this.x = call.x;
			this.y = call.y;
			this.w = call.w;
			this.h = call.h;
			this.color = call.color;
			this.naturalLight = call.naturalLight;
		}

		ScriptDrawCalls.Rectangle builder() {
			final ScriptDrawCalls.Rectangle call = new ScriptDrawCalls.Rectangle();
			call.x = x;
			call.y = y;
			call.w = w;
			call.h = h;
			call.z = z;
			call.color = color;
			call.naturalLight = naturalLight;
			return call;
		}

		@Override
		public String describe() {
			return builder().describe();
		}

		@Override
		public String signature() {
			return String.format(Locale.ROOT,
					"Rectangle(z=%s pos=%s,%s size=%sx%s color=%08X naturalLight=%s)",
					z, x, y, w, h, color, naturalLight);
		}

		@Override
		public void replay(ScriptRenderContext ctx) {
			ctx.acceptRecorded(builder(), z);
		}
	}

	// ==================================================================
	// Component
	// ==================================================================

	/**
	 * A captured {@code ctx.parseComponent(...)} result.
	 *
	 * <p>The component is a parsed declaration: its geometry and options are immutable, and the
	 * frame data it is drawn against is supplied by the replay context. The script-thread copy of
	 * {@code PIDSContext} is therefore not retained — only the configuration is.</p>
	 */
	public static final class ComponentCall extends RecordedCall {
		private final PIDSComponent component;
		private final String type;

		ComponentCall(PIDSComponent component, String type, float z) {
			super(z);
			this.component = component;
			this.type = type;
		}

		@Override
		public String describe() {
			return String.format(Locale.ROOT, "Component(type=%s pos=%s,%s size=%sx%s)",
					type, component.getX(), component.getY(),
					component.getWidth(), component.getHeight());
		}

		@Override
		public String signature() {
			return String.format(Locale.ROOT,
					"Component(z=%s type=%s class=%s pos=%s,%s size=%sx%s)",
					z, type, component.getClass().getName(),
					component.getX(), component.getY(),
					component.getWidth(), component.getHeight());
		}

		@Override
		public void replay(ScriptRenderContext ctx) {
			ctx.replayComponent(component, type, z);
		}
	}

	// ==================================================================
	// QuadDrawCall (the 3D shell JCM 2.x packs draw around a panel)
	// ==================================================================

	/** A captured {@code QuadDrawCall}. */
	public static final class QuadCall extends RecordedCall {
		private final net.minecraft.resources.ResourceLocation texture;
		private final int color;
		private final float nx;
		private final float ny;
		private final float nz;
		private final double[][] corners;

		QuadCall(ScriptQuadDrawCall call, float z) {
			super(z);
			this.texture = call.textureId();
			this.color = call.color();
			this.nx = call.normal().x();
			this.ny = call.normal().y();
			this.nz = call.normal().z();
			this.corners = new double[4][3];
			for (int i = 0; i < 4; i++) {
				ScriptMath.Vector3f vertex = call.vertex(i);
				this.corners[i] = new double[]{vertex.x(), vertex.y(), vertex.z()};
			}
		}

		@Override
		public void replay(ScriptRenderContext ctx) {
			ScriptQuadDrawCall call = ScriptQuadDrawCall.create()
					.texture(texture.toString())
					.color(color)
					.normal(nx, ny, nz)
					.quad(new ScriptMath.Vector3f(corners[0][0], corners[0][1], corners[0][2]),
							new ScriptMath.Vector3f(corners[1][0], corners[1][1], corners[1][2]),
							new ScriptMath.Vector3f(corners[2][0], corners[2][1], corners[2][2]),
							new ScriptMath.Vector3f(corners[3][0], corners[3][1], corners[3][2]));
			ctx.drawQuad(call, z);
		}

		@Override
		public String signature() {
			final StringBuilder builder = new StringBuilder("Quad|").append(texture).append('|')
					.append(String.format("%08X", color)).append('|')
					.append(nx).append(',').append(ny).append(',').append(nz);
			for (double[] corner : corners) {
				builder.append('|').append(corner[0]).append(',').append(corner[1]).append(',').append(corner[2]);
			}
			return builder.toString();
		}

		@Override
		public String describe() {
			return "Quad(texture=" + texture + " color=" + String.format("%08X", color)
					+ " normal=" + nx + "," + ny + "," + nz + ")";
		}
	}
}
