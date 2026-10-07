package com.jsblock.script;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
	public static final float Z_ORDER_STEP = -0.1F;

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

	/**
	 * Whether this frame's draw calls should also be described into {@link #traceCalls}.
	 *
	 * <p>Set from {@link #tracingEnabled()} when the context is built. Kept off by default:
	 * building a description per call costs a {@code String.format} each, which a PIDS that
	 * redraws several times a frame should not pay for unasked.</p>
	 */
	private final boolean tracing;
	/** Descriptions of this frame's calls, in call order; {@code null} when not tracing. */
	private final List<String> traceCalls;

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
		this.tracing = !dryRun && tracingEnabled();
		this.traceCalls = tracing ? new ArrayList<>() : null;
	}

	/**
	 * @return whether scripts should have their draw calls described each frame.
	 *
	 * <p>Two switches, either of which is enough: the script debug switch in the config
	 * screen, and the {@code jsblock.pids.trace} system property for a launch that should
	 * trace without touching the player's config.</p>
	 */
	private static boolean tracingEnabled() {
		try {
			return com.jsblock.client.ClientConfig.getScriptDebugMode()
					|| Boolean.getBoolean("jsblock.pids.trace");
		} catch (Throwable t) {
			/* The headless checks build contexts without ever loading the config. */
			return Boolean.getBoolean("jsblock.pids.trace");
		}
	}

	/** @return whether this context is recording the calls handed to it. */
	public boolean isTracing() {
		return tracing;
	}

	/**
	 * @return a one-line description of every call drawn so far this frame, in call order, or
	 * an empty list when tracing is off.
	 */
	public List<String> traceCalls() {
		return traceCalls == null ? Collections.emptyList() : traceCalls;
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

	/**
	 * Rewinds the per-call depth counter and forgets the calls recorded so far.
	 *
	 * <p>Used when a frame is retried — see {@code RenderPIDSBase.renderScripted}. Without it
	 * the second attempt would stack a second copy of everything <em>behind</em> the copy the
	 * first attempt had already drawn: the retry has to land on the same depths, where a
	 * repeated call is the same geometry at the same place rather than a competing layer.</p>
	 */
	public void restartDrawCalls() {
		drawCallIndex = 0;
		if (traceCalls != null) {
			traceCalls.clear();
		}
	}

	/**
	 * Draws whatever is already queued in {@code layer}, now.
	 *
	 * <h2>Why a script has to do this</h2>
	 * <p>Script quads go through MTR's light layer, which is
	 * {@code RenderType.beaconBeam(texture, true)}. That layer is built with
	 * {@code sortOnUpload = true} and a {@code COLOR_WRITE} write-mask — it sorts the quads it
	 * is given by distance from the camera and then draws them without writing depth. So which
	 * quad ends up on top is decided by <em>where its centre is</em>, not by the order the
	 * script drew it in.</p>
	 *
	 * <p>That is fine for a panel whose pieces are the same size, and wrong for one that is a
	 * full-size background plus small overlays: the badge at the left edge of a 136-unit panel
	 * is up to 0.7 blocks off the panel's centre, which is a far bigger term in that distance
	 * than the 0.001-block depth step between two calls. Stand to one side and the badge's
	 * centre is the farther of the two, so the background is drawn last and paints over it —
	 * the badge is then only visible where it sticks out past the panel's silhouette. Walk to
	 * the other side and it comes back.</p>
	 *
	 * <p>JCM 2.x has none of this because it queues its draws and replays them in order. Until
	 * this port has a queue of its own, ending the batch after every quad reproduces the same
	 * thing: a batch holding one quad cannot be reordered, so the paint order is the call
	 * order again.</p>
	 *
	 * @param layer the render layer the caller just drew into, or {@code null} for none
	 */
	public void flushLayer(RenderType layer) {
		if (layer == null) {
			return;
		}
		if (vertexConsumers instanceof MultiBufferSource.BufferSource) {
			((MultiBufferSource.BufferSource) vertexConsumers).endBatch(layer);
		} else if (immediate != null) {
			immediate.endBatch(layer);
		}
	}

	/** {@code ctx.draw(call)} — draws a {@code Text}/{@code Texture}/{@code Rectangle}. */
	public void draw(Object call) {		if (!(call instanceof ScriptDrawCall)) {
			throw new IllegalArgumentException(
					"ctx.draw() expects a Text/Texture/Rectangle, got "
							+ (call == null ? "null" : call.getClass().getName()));
		}
		final ScriptDrawCall drawCall = (ScriptDrawCall) call;
		drawCall.validate();
		final float z = autoZOrdering ? (float) (drawCallIndex++ * zOrderStep) : 0F;
		if (dryRun) {
			/* Record the depth this call would have used. Without this the dry run short-circuits
			   before pushTransform, so every call describes itself as z=0 and the layer
			   separation cannot be checked without launching the game. */
			drawCall.z = z;
			recordedCalls.add(drawCall.describe());
			return;
		}
		if (tracing) {
			drawCall.z = z;
			traceCalls.add(drawCall.describe());
		}
		drawCall.draw(this, z);
	}
}
