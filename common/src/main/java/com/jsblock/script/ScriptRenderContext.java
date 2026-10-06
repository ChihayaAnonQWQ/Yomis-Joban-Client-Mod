package com.jsblock.script;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

/**
 * Per-frame render state handed to a PIDS script as its {@code ctx} argument.
 *
 * <p>Ported from JCM 2.x's {@code PIDSScriptContext}, reduced to what PIDS scripts actually
 * touch. JCM 2.x queues draw calls and replays them afterwards so it can sort them by
 * z-order and cache panels for distant blocks; on MTR 3 the panel is drawn during the
 * block-entity pass anyway, so calls execute immediately and layering is reproduced by
 * giving each successive call a slightly larger depth offset — the same {@code 0.0002}
 * step JCM 2.x uses.</p>
 *
 * <h2>Coordinate space</h2>
 * <p>Scripts position everything in JCM 2.x's unit: {@code 1/96} of a block
 * ({@code PIDSPresetBase.BASE_SCALE}). {@code pos(0, 0)} is the panel's top-left corner and
 * {@code pids.width} x {@code pids.height} is the panel's size in those units, so a preset
 * authored for a 128x72 panel keeps its proportions.</p>
 *
 * <p>JCM 2.x can hard-code {@code 1/96} because its matrix stack is already in block space.
 * MTR 3's block-entity renderers hand us a stack scaled by {@code 1/geometry.scale} instead,
 * so the multiplier is supplied as {@link #scriptScale} and is computed by the caller as
 * {@code geometry.scale / 96}. Drawing at script coordinate {@code s} therefore lands at
 * world offset {@code s/96} blocks, exactly as in JCM 2.x.</p>
 */
public class ScriptRenderContext {

	/** Depth step between successive draw calls, matching JCM 2.x. */
	public static final float Z_ORDER_STEP = 0.0002F;

	public final PoseStack matrices;
	public final MultiBufferSource vertexConsumers;
	public final MultiBufferSource.BufferSource immediate;
	public final Direction facing;
	/** Packed light value; a PIDS panel is emissive so callers pass {@code MAX_LIGHT_GLOWING}. */
	public final int light;
	/** Panel width in script units, exposed to scripts as {@code pids.width}. */
	public final int panelWidth;
	/** Panel height in script units, exposed to scripts as {@code pids.height}. */
	public final int panelHeight;
	/** Multiplier converting script units into the caller's local drawing units. */
	public final float scriptScale;

	/** Index of the next draw call, used to derive its depth offset. */
	private int drawCallIndex = 0;
	/** Set from {@code ctx.setAutoZOrdering(false)}; draw calls then share one depth. */
	private boolean autoZOrdering = true;
	private double zOrderStep = Z_ORDER_STEP;

	public ScriptRenderContext(PoseStack matrices, MultiBufferSource vertexConsumers,
							   MultiBufferSource.BufferSource immediate, Direction facing, int light,
							   int panelWidth, int panelHeight, float scriptScale) {
		this.matrices = matrices;
		this.vertexConsumers = vertexConsumers;
		this.immediate = immediate;
		this.facing = facing;
		this.light = light;
		this.panelWidth = panelWidth;
		this.panelHeight = panelHeight;
		this.scriptScale = scriptScale;
	}

	// ------------------------------------------------------------------
	// API exposed to scripts
	// ------------------------------------------------------------------

	/** {@code ctx.setAutoZOrdering(false)} — stop giving each draw call its own depth. */
	public void setAutoZOrdering(boolean autoZOrdering) {
		this.autoZOrdering = autoZOrdering;
	}

	/** {@code ctx.setZOrderStep(f)} — change the depth step between draw calls. */
	public void setZOrderStep(double distance) {
		this.zOrderStep = distance;
	}

	/** {@code ctx.draw(call)} — draws a {@code Text}/{@code Texture}/{@code Rectangle}. */
	public void draw(Object call) {
		if (!(call instanceof ScriptDrawCall)) {
			throw new IllegalArgumentException(
					"ctx.draw() expects a Text/Texture/Rectangle, got "
							+ (call == null ? "null" : call.getClass().getName()));
		}
		final ScriptDrawCall drawCall = (ScriptDrawCall) call;
		drawCall.validate();
		final float z = autoZOrdering ? (float) (drawCallIndex++ * zOrderStep) : 0F;
		drawCall.draw(this, z);
	}
}
