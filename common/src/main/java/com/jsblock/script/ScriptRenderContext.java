package com.jsblock.script;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

	/**
	 * Depth step between successive draw calls, in <b>script units</b>, applied as a
	 * <b>negative</b> offset.
	 *
	 * <p>The panel is drawn inside a space that has been rotated 180 degrees about Z, so
	 * positive Z points <em>into</em> the block — the same reason YJCM's own renderers pull
	 * their geometry out with a negative {@code SMALL_OFFSET}. Offsetting later calls
	 * <em>outward</em> therefore both separates them from the background and keeps them clear
	 * of the block's own screen face, instead of burying them behind it.</p>
	 *
	 * <p>One script unit is 1/96 block, so this is about 0.001 blocks per layer. JCM 2.x's own
	 * 0.0002 gives 2e-6 blocks here, which is too little to stop the background, the advert and
	 * the text from resolving differently frame to frame — that was the flicker.</p>
	 */
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

	/**
	 * When set, {@link #draw(Object)} records what would have been drawn instead of touching
	 * Minecraft's renderer. Used by the headless API check, which runs real preset scripts
	 * through the real wrappers without a game.
	 */
	private final boolean dryRun;
	/** Descriptions of the calls recorded in dry-run mode. */
	private final List<String> recordedCalls;

	public ScriptRenderContext(PoseStack matrices, MultiBufferSource vertexConsumers,
							   MultiBufferSource.BufferSource immediate, Direction facing, int light,
							   int panelWidth, int panelHeight, float scriptScale) {
		this(matrices, vertexConsumers, immediate, facing, light, panelWidth, panelHeight, scriptScale, false);
	}

	private ScriptRenderContext(PoseStack matrices, MultiBufferSource vertexConsumers,
								MultiBufferSource.BufferSource immediate, Direction facing, int light,
								int panelWidth, int panelHeight, float scriptScale, boolean dryRun) {
		this.matrices = matrices;
		this.vertexConsumers = vertexConsumers;
		this.immediate = immediate;
		this.facing = facing;
		this.light = light;
		this.panelWidth = panelWidth;
		this.panelHeight = panelHeight;
		this.scriptScale = scriptScale;
		this.dryRun = dryRun;
		this.recordedCalls = dryRun ? new ArrayList<>() : null;
	}

	/**
	 * Builds a context that records draw calls instead of rendering them.
	 *
	 * <p>Everything except the final draw goes through the real code path, so a script that
	 * calls a wrapper method this port does not implement fails here exactly as it would in
	 * game — which is what makes this usable as a headless check.</p>
	 */
	public static ScriptRenderContext dryRun(int panelWidth, int panelHeight, float scriptScale) {
		return new ScriptRenderContext(null, null, null, null, 0, panelWidth, panelHeight, scriptScale, true);
	}

	/** @return the descriptions recorded in dry-run mode, in call order. */
	public List<String> recordedCalls() {
		return recordedCalls == null ? Collections.emptyList() : Collections.unmodifiableList(recordedCalls);
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
		if (dryRun) {
			recordedCalls.add(drawCall.describe());
			return;
		}
		drawCall.draw(this, z);
	}
}
