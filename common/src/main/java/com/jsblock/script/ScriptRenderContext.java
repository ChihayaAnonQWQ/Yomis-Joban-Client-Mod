package com.jsblock.script;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
	 * <b>negative</b> offset. JCM 2.x's own value ({@code zOrderStep = 0.0002} in
	 * {@code PIDSScriptContext}), in its unit.
	 *
	 * <p>The panel is drawn inside a space that has been rotated 180 degrees about Z, so
	 * positive Z points <em>into</em> the block — the same reason YJCM's own renderers pull
	 * their geometry out with a negative {@code SMALL_OFFSET}. Offsetting later calls
	 * <em>outward</em> therefore separates them from the background without burying them
	 * behind it.</p>
	 *
	 * <h2>Why this went up by 500x and came back</h2>
	 * <p>One script unit is 1/96 block, so JCM 2.x's step is 2e-6 blocks — far below anything
	 * the depth buffer can resolve. That was fine in JCM 2.x, which queues its draws and replays
	 * them in order. This port used to hand its quads to the block entity's buffer instead,
	 * where they were sorted by distance and drawn as one batch, and 2e-6 blocks was too little
	 * to keep the background, the advert and the text from resolving differently from frame to
	 * frame: the panel flickered. The step was raised to {@code -0.1} (about 0.001 blocks) to
	 * force the order.</p>
	 *
	 * <p>That workaround cost more than it bought. Every call moved a little further out, and a
	 * preset that draws a row as several elements — {@code met_bus_stop} and most of the
	 * Japanese-style packs do — accumulated it into a visible staircase, one row per step, with
	 * forty calls reaching 4 cm.</p>
	 *
	 * <p>Quads are no longer batched at all ({@link #beginQuad}), so the order is the call order
	 * and nothing competes for depth. The step is back to JCM 2.x's value: it only has to keep
	 * the calls nominally apart, not to settle a sort.</p>
	 */
	public static final float Z_ORDER_STEP = -0.0002F;

	public final PoseStack matrices;
	public final MultiBufferSource vertexConsumers;
	/**
	 * Where the script's text goes. A caller drawing into its own framebuffer supplies a
	 * {@code BufferSource} subclass whose {@code getBuffer} keeps the text out of the world; the
	 * font renderer's signature requires this type, so the interface alone will not do.
	 */
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

	/** The sound manager handed to scripts by getSoundManager(). */
	private final ScriptSound.SoundManager soundManager = new ScriptSound.SoundManager();

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
	 *
	 * <p>{@code ctx.parseComponent(...)} is part of that path, so a headless context carries an
	 * empty panel's data: no world, no arrivals, no custom messages. A script that parses a
	 * component still gets a component, still gets its rectangle, and still reaches the recording
	 * half — and one that asks {@code canRender()} gets the same answer the JSON path would give on
	 * a panel with no data, which is what the check asserts.</p>
	 */
	public static ScriptRenderContext dryRun(int panelWidth, int panelHeight, float scriptScale) {
		final ScriptRenderContext context =
				new ScriptRenderContext(null, null, null, null, 0, panelWidth, panelHeight, scriptScale, true);
		context.componentContext = new com.jsblock.pids.PIDSContext(null, net.minecraft.core.BlockPos.ZERO,
				Direction.NORTH, new String[0], Collections.emptyList(), Collections.emptyList(),
				new boolean[0], 0D, 0L);
		return context;
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
	/**
	 * {@code PIDSScriptContext.getSoundManager()} -- plays sounds for this script.
	 *
	 * <p>One per context, as the docs describe; the caller does not have to hold on to it.</p>
	 */
	public ScriptSound.SoundManager getSoundManager() {
		return soundManager;
	}

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
	 * How many draw calls this context has been handed since it was made.
	 *
	 * <p>For the pixelation diagnostic: an offscreen pass that reports the right number of calls
	 * and a still-black target has a sampling problem, while one that reports none never ran.</p>
	 */
	public int drawCallCount() {
		return drawCallIndex;
	}

	/**
	 * One quad buffer for the whole script engine, begun and drained per draw call.
	 *
	 * <p>Only ever touched on the render thread, and only between a {@link #beginQuad} and its
	 * matching {@link #endQuad}, so a single shared builder is enough — and it keeps the engine
	 * from allocating one per panel per frame.</p>
	 */
	private static BufferBuilder SCRIPT_QUADS;

	/**
	 * Starts a quad in {@code layer} in the script engine's <b>own</b> buffer and returns the
	 * consumer to write it to. Pair every call with {@link #endQuad}.
	 *
	 * <h2>Why the panel does not use the block-entity buffer source</h2>
	 * <p>Scripts draw a full-panel background and then small overlays on top of it, and expect
	 * each call to land on top of the one before — that is what JCM 2.x's queued renderer
	 * gives them. Neither way of going through the {@code MultiBufferSource} handed to the
	 * block entity does:</p>
	 *
	 * <ul>
	 *   <li>MTR's light layer is {@code RenderType.beaconBeam(texture, true)}, built with
	 *       {@code sortOnUpload = true} and a {@code COLOR_WRITE} write-mask. It sorts the
	 *       quads it is given by distance from the camera and draws them without writing depth,
	 *       so a small overlay whose centre is farther away than the panel's centre is painted
	 *       <em>first</em> and then covered by the background. Which overlays survive depends
	 *       on where the player stands.</li>
	 *   <li>Mods that take over entity rendering are free to group those quads into their own
	 *       batches. <i>Accelerated Rendering</i>, for one, mixes into
	 *       {@code MultiBufferSource.BufferSource.getBuffer} and hands back its own consumer,
	 *       so the quads never reach a buffer that {@code endBatch} could flush in order — on a
	 *       pack with that mod installed, every overlay vanished and only the background was
	 *       left, from every angle.</li>
	 * </ul>
	 *
	 * <p>Going through a buffer of our own and drawing it immediately fixes both: a batch
	 * holding one quad cannot be reordered, and no other mod ever gets a say in it. The text
	 * path already worked this way by accident — it draws through MTR's immediate source, which
	 * is not the one the block-entity pass is given.</p>
	 *
	 * <p>The cost is one draw call per quad instead of one per layer per frame, which for a
	 * handful of overlays per panel is not worth trading the layering for.</p>
	 */
	public VertexConsumer beginQuad(RenderType layer) {
		if (SCRIPT_QUADS == null) {
			SCRIPT_QUADS = new BufferBuilder(1536);
		}
		SCRIPT_QUADS.begin(VertexFormat.Mode.QUADS, layer.format());
		return SCRIPT_QUADS;
	}

	/**
	 * Draws the quad {@link #beginQuad} started, now.
	 *
	 * <p>Normally through {@code RenderType.end}, which sets the layer's state up, uploads and clears
	 * it again in one go. A caller drawing into its own framebuffer supplies an {@link QuadUploader}
	 * instead, because {@code RenderType}'s state setup ends by binding the layer's <em>output</em>
	 * target, and for every layer Minecraft builds that is the main one: a quad drawn through
	 * {@code end} lands in the world no matter which framebuffer was bound beforehand. The uploader
	 * puts the caller's target back between the two steps, which is the only order that works.</p>
	 */
	public void endQuad(RenderType layer) {
		if (SCRIPT_QUADS == null) {
			return;
		}
		if (quadUploader != null) {
			quadUploader.upload(layer, SCRIPT_QUADS);
			return;
		}
		layer.end(SCRIPT_QUADS, RenderSystem.getVertexSorting());
	}

	/**
	 * Uploads one finished layer, in the caller's own framebuffer.
	 *
	 * <p>Implementations must run the layer's state setup, re-bind the target afterwards — the setup
	 * binds the main one — and only then upload.</p>
	 */
	public interface QuadUploader {
		void upload(RenderType layer, BufferBuilder builder);
	}

	/** Set to draw through an {@link QuadUploader} rather than straight into the world. */
	private QuadUploader quadUploader = null;

	/** @see QuadUploader */
	public void setQuadUploader(QuadUploader uploader) {
		this.quadUploader = uploader;
	}

	/** {@code ctx.draw(call)} — draws a {@code Text}/{@code Texture}/{@code Rectangle}. */
	public void draw(Object call) {
		/* A parsed component is a legitimate thing to hand to draw(): JCM 2.x's own draw() takes an
		   Object and accepts the draw-call wrappers, and a script author who has just written
		   ctx.parseComponent(...) reaches for ctx.draw(...) next. Both spellings land in the same
		   place. */
		if (call instanceof ScriptComponent) {
			renderComponent((ScriptComponent) call);
			return;
		}
		if (!(call instanceof ScriptDrawCall)) {
			throw new IllegalArgumentException(
					"ctx.draw() expects a Text/Texture/Rectangle or a ctx.parseComponent(...) result, got "
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

	// ------------------------------------------------------------------
	// ctx.parseComponent(jsonString) -- JCM 2.x's declarative components, from a script
	// ------------------------------------------------------------------

	/**
	 * The frame's panel data, so a parsed component can be asked the same questions a JSON preset
	 * asks it. Supplied by the caller; {@code null} until it is, which only the headless check
	 * sees.
	 */
	private com.jsblock.pids.PIDSContext componentContext;

	/**
	 * The render state a parsed component draws through.
	 *
	 * <p>Deliberately the same bundle the {@code components} array path builds in
	 * {@code RenderPIDSBase.renderLayout}: the same panel matrices, the same vertex source and the
	 * same immediate source for text. A component drawn from a script therefore lands on the panel
	 * exactly where the same JSON would put it, and a preset can be moved between the two spellings
	 * without redrawing itself.</p>
	 */
	private com.jsblock.pids.PIDSGraphics componentGraphics;

	/**
	 * Hands the context the panel data and render state {@code ctx.parseComponent} needs.
	 *
	 * <p>Called by the renderer once per frame, after the panel transform is on the stack, because
	 * {@link com.jsblock.pids.PIDSGraphics} carries the live {@link PoseStack}.</p>
	 */
	public void setComponentSupport(com.jsblock.pids.PIDSContext context,
									com.jsblock.pids.PIDSGraphics graphics) {
		this.componentContext = context;
		this.componentGraphics = graphics;
	}

	/**
	 * {@code ctx.parseComponent(jsonString)} — parses one JCM 2.x component declaration.
	 *
	 * <p>Port of {@code PIDSScriptContext.parseComponent(String)}: the string goes through Gson into
	 * {@code PIDSComponent.parse(JsonObject)}, which reads {@code component}, {@code x}, {@code y},
	 * {@code width} and {@code height} and looks the type up in the registry — so every component
	 * this branch implements, and every one a mod adds to
	 * {@link com.jsblock.pids.PIDSComponent#COMPONENTS}, is reachable from a script.</p>
	 *
	 * <p>A declaration that cannot be used is refused with a message naming the problem rather than
	 * a {@code null}: {@code PIDSComponent.parse} answers an unknown type with {@code null} and a
	 * logged warning, because a JSON <em>preset</em> must keep loading the rest of its components,
	 * but a script that receives {@code null} fails later with "cannot call method render of null"
	 * and the author never sees the word "unknown".</p>
	 *
	 * @throws IllegalArgumentException when the string is not JSON, is not an object, names no
	 *                                  component, or names one this build does not know
	 */
	public ScriptComponent parseComponent(String json) {
		if (json == null || json.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"ctx.parseComponent() needs a JSON object such as {\"component\":\"clock\"}");
		}
		final com.google.gson.JsonObject object;
		try {
			final com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				throw new IllegalArgumentException(
						"ctx.parseComponent() expects a JSON object, got "
								+ (parsed.isJsonArray() ? "an array" : "a " + parsed.getClass().getSimpleName()));
			}
			object = parsed.getAsJsonObject();
		} catch (IllegalArgumentException typeError) {
			throw typeError;
		} catch (Exception syntaxError) {
			throw new IllegalArgumentException(
					"ctx.parseComponent() could not parse the JSON: " + syntaxError.getMessage());
		}
		if (!object.has("component") || !object.get("component").isJsonPrimitive()) {
			throw new IllegalArgumentException(
					"ctx.parseComponent() needs a \"component\" key naming the type, e.g. "
							+ "{\"component\":\"clock\",\"x\":0,\"y\":0,\"width\":40,\"height\":10}");
		}
		final String type = object.get("component").getAsString();
		final com.jsblock.pids.PIDSComponent component = com.jsblock.pids.PIDSComponent.parse(object);
		if (component == null) {
			throw new IllegalArgumentException("ctx.parseComponent(): unknown component \"" + type
					+ "\". Known types: "
					+ String.join(", ", com.jsblock.pids.PIDSComponent.COMPONENTS.keySet()));
		}
		return new ScriptComponent(component, type, componentContext);
	}

	/**
	 * Draws a parsed component, giving it the same depth step a draw call would get.
	 *
	 * <p>Without the step a component would land at the same depth as the call before it — normally
	 * the preset's own background — and the two would resolve in whatever order the layer's sort
	 * produced. The step is the autoz-ordered one {@link #draw} uses, so a component takes its turn
	 * in the script's call order like any other element.</p>
	 *
	 * <p>In dry-run mode nothing is drawn and the component describes itself instead, which is what
	 * makes the whole path — parse, wrap, hand over, and the geometry that would be used — checkable
	 * without a game.</p>
	 */
	public void renderComponent(ScriptComponent component) {
		if (component == null) {
			throw new IllegalArgumentException("ctx.draw() was given no component");
		}
		final float z = autoZOrdering ? (float) (drawCallIndex++ * zOrderStep) : 0F;
		if (dryRun) {
			recordedCalls.add(component.describe());
			return;
		}
		if (tracing) {
			traceCalls.add(component.describe());
		}
		/* A component declares its own rectangle and draws through the panel's own graphics, so the
		   only transform the engine adds is the depth step. */
		if (componentGraphics == null) {
			throw new IllegalStateException(
					"ctx.parseComponent() is not available for this panel; no render state was set");
		}
		componentGraphics.matrices.pushPose();
		componentGraphics.matrices.translate(0F, 0F, z);
		try {
			component.draw(componentContext, componentGraphics);
		} finally {
			componentGraphics.matrices.popPose();
		}
	}
}
