package com.jsblock.render;

import com.jsblock.block.BlockPIDSBaseHorizontal;
import com.jsblock.block.JobanPIDSBase;
import com.jsblock.block.PIDSRVBase;
import com.jsblock.client.ClientConfig;
import com.jsblock.client.JobanCustomResources;
import com.jsblock.data.PIDSPreset;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGeometry;
import com.jsblock.pids.PIDSGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.MTRClient;
import mtr.block.IBlock;
import mtr.client.ClientData;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.data.Platform;
import mtr.data.RailwayData;
import mtr.data.ScheduleEntry;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.BlockEntityRendererMapper;
import mtr.mappings.UtilitiesClient;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Abstract class to handle all the preprocessing of Joban PIDS.<br>
 * (Variables, PIDS Preset etc.)
 * @author LX86
 */
public abstract class RenderPIDSBase<T extends BlockEntityMapper> extends BlockEntityRendererMapper<T> implements IGui {
    private final int maxArrivals;

    public static final int SWITCH_LANGUAGE_TICKS = 80;
    public static final int MAX_VIEW_DISTANCE = 16;
    public static final boolean[] SHOW_ALL_ROWS = new boolean[]{false, false, false, false};

    public RenderPIDSBase(BlockEntityRenderDispatcher dispatcher, int maxArrivals) {
        super(dispatcher);
        this.maxArrivals = maxArrivals;
    }

    @Override
    public void render(T entity, float delta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null || ClientConfig.getRenderDisabled()) {
            return;
        }

        if(!(entity instanceof JobanPIDSBase.TileEntityBlockJobanPIDS)) {
            return;
        }

        final String[] customMessages = new String[maxArrivals];
        final List<Long> platformIds = new ArrayList<>(((BlockPIDSBaseHorizontal.TileEntityBlockPIDSBaseHorizontal) entity).getPlatformIds());
        final boolean[] hideArrivals = new boolean[maxArrivals];
        final boolean hidePlatforms;
        final String presetID = ((JobanPIDSBase.TileEntityBlockJobanPIDS)entity).getPresetID();
        PIDSPreset preset = JobanCustomResources.PIDSPresets.getOrDefault(presetID, null);

        /* Auto-switch presets based on train arrival/departure time */
        final JobanPIDSBase.TileEntityBlockJobanPIDS jobanEntity = (JobanPIDSBase.TileEntityBlockJobanPIDS) entity;
        if (jobanEntity.getDepAutoSwitchEnabled() || jobanEntity.getAutoSwitchEnabled()) {
            final BlockPos pos = entity.getBlockPos();
            final List<ScheduleEntry> scheduleList = new ArrayList<>();
            long primaryPlatformId = 0;

            if (!platformIds.isEmpty()) {
                for (long platformId : platformIds) {
                    final Set<ScheduleEntry> schedules = ClientData.SCHEDULES_FOR_PLATFORM.get(platformId);
                    if (schedules != null) {
                        scheduleList.addAll(schedules);
                    }
                }
                primaryPlatformId = platformIds.get(0);
            } else {
                final long closestPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
                primaryPlatformId = closestPlatformId;
                final Set<ScheduleEntry> schedules = ClientData.SCHEDULES_FOR_PLATFORM.get(closestPlatformId);
                if (schedules != null) {
                    scheduleList.addAll(schedules);
                }
            }

            if (!scheduleList.isEmpty()) {
                Collections.sort(scheduleList);
                final ScheduleEntry firstEntry = scheduleList.get(0);
                final long diff = firstEntry.arrivalMillis - System.currentTimeMillis();

                /* Priority 1: Departure auto-switch (only when train is at platform) */
                if (jobanEntity.getDepAutoSwitchEnabled() && diff <= 0) {
                    final int timeSinceArrivalSec = (int)(-diff / 1000);
                    final Platform platform = ClientData.DATA_CACHE.platformIdMap.get(primaryPlatformId);
                    final int depCountdownSec = platform != null ? (platform.getDwellTime() / 2) - timeSinceArrivalSec : 0;
                    final boolean inWindow;
                    if (jobanEntity.getDepAutoSwitchUntilClose()) {
                        inWindow = depCountdownSec > 0 && depCountdownSec <= jobanEntity.getDepAutoSwitchCountdown();
                    } else {
                        inWindow = (depCountdownSec > 0 && depCountdownSec <= jobanEntity.getDepAutoSwitchCountdown()) ||
                                   (depCountdownSec <= 0 && depCountdownSec > -jobanEntity.getDepAutoSwitchDuration());
                    }
                    if (inWindow) {
                        final PIDSPreset depPreset = JobanCustomResources.PIDSPresets.getOrDefault(jobanEntity.getDepAutoSwitchPreset(), null);
                        if (depPreset != null) {
                            preset = depPreset;
                        }
                    }
                }
                /* Priority 2: Arrival auto-switch (lower priority than departure) */
                /* Check !(departure switch active) to avoid overriding departure preset */
                if (preset == JobanCustomResources.PIDSPresets.getOrDefault(presetID, null) && jobanEntity.getAutoSwitchEnabled()) {
                    if ((diff > 0 && diff <= jobanEntity.getAutoSwitchCountdown() * 1000L) ||
                        (diff <= 0 && diff > -jobanEntity.getAutoSwitchDuration() * 1000L)) {
                        final PIDSPreset arrPreset = JobanCustomResources.PIDSPresets.getOrDefault(jobanEntity.getAutoSwitchPreset(), null);
                        if (arrPreset != null) {
                            preset = arrPreset;
                        }
                    }
                }
            }
        }

        if(preset != null && preset.visibility != null) {
            System.arraycopy(preset.visibility, 0, hideArrivals, 0, hideArrivals.length);
        }

        for (int i = 0; i < maxArrivals; i++) {
            customMessages[i] = parseVariable(((BlockPIDSBaseHorizontal.TileEntityBlockPIDSBaseHorizontal) entity).getMessage(i), world);
            boolean hideArrival = ((BlockPIDSBaseHorizontal.TileEntityBlockPIDSBaseHorizontal) entity).getHideArrival(i);
            if(hideArrival) {
                hideArrivals[i] = true;
            }
        }

        /* Hide Platform Circles (RV PIDS Only) */
        if (entity instanceof PIDSRVBase.TileEntityBlockRVPIDS) {
            hidePlatforms = ((PIDSRVBase.TileEntityBlockRVPIDS) entity).getHidePlatformNumber();
        } else {
            hidePlatforms = false;
        }

        /* A JCM 2.x preset is a JavaScript file rather than a component list. The script
           draws the whole panel itself, background included, so the built-in renderer is
           skipped entirely. Guarded like the other paths: a broken script must not throw
           out of the block-entity renderer once per frame. */
        if (preset != null && preset.isScripted()) {
            try {
                renderScripted(entity, world, preset, customMessages, hideArrivals, platformIds, hidePlatforms, delta, matrices, vertexConsumers);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        /* A blank panel is ambiguous: no preset may be selected on the block, or a preset may
           be selected that draws nothing. Say which, once per block position, so a grey panel
           can be told apart from a script that ran and produced no visible geometry. */
        reportPanelOnce(entity.getBlockPos(), "using the built-in renderer, preset="
                + (preset == null ? "<none selected>" : preset.id + " (no script, no components)"));

        /* A preset that declares a "components" array drives the whole panel; the built-in
           hard-coded element positions are skipped entirely (see com.jsblock.pids).
           Guarded exactly like the built-in path below: a bad preset must not be able to
           throw out of the block-entity renderer once per frame. */
        if (preset != null && preset.layout != null) {
            try {
                renderLayout(entity, world, preset, customMessages, hideArrivals, platformIds, delta, matrices, vertexConsumers);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            render(entity, world, customMessages, hideArrivals, hidePlatforms, preset, platformIds, delta, matrices, vertexConsumers, light, overlay);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Renders a component-based layout preset, bypassing the renderer's hard-coded element
     * positions.
     *
     * <p>The matrix setup deliberately mirrors the block the concrete renderers use for their
     * background artwork, so a layout lands exactly on the panel: translate to the block
     * centre, apply the facing/90-degree rotations, move to the panel origin, then divide by
     * {@link PIDSGeometry#scale}.</p>
     */
    protected void renderLayout(T entity, Level world, PIDSPreset preset, String[] customMessages,
                                boolean[] hideArrivals, List<Long> platformIds, float delta,
                                PoseStack matrices, MultiBufferSource vertexConsumers) {
        final PIDSGeometry geometry = getLayoutGeometry();
        if (geometry == null) {
            return;
        }

        final BlockPos pos = entity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, HorizontalDirectionalBlock.FACING);

        final List<ScheduleEntry> scheduleList = new ArrayList<>();
        if (!platformIds.isEmpty()) {
            for (long platformId : platformIds) {
                scheduleList.addAll(ClientData.SCHEDULES_FOR_PLATFORM.getOrDefault(platformId, Collections.emptySet()));
            }
        } else {
            final long closestPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
            scheduleList.addAll(ClientData.SCHEDULES_FOR_PLATFORM.getOrDefault(closestPlatformId, Collections.emptySet()));
        }
        Collections.sort(scheduleList);

        final PIDSContext context = new PIDSContext(world, pos, facing, customMessages, scheduleList,
                platformIds, hideArrivals, delta, (long) Math.floor(MTRClient.getGameTick()));
        final int textColor = preset.color == null ? geometry.defaultTextColor : preset.color;
        final String font = preset.font == null ? geometry.defaultFont : preset.font;

        matrices.pushPose();
        matrices.translate(0.5, 0, 0.5);
        UtilitiesClient.rotateYDegrees(matrices, (geometry.rotate90 ? 90 : 0) - facing.toYRot());
        UtilitiesClient.rotateZDegrees(matrices, 180);
        UtilitiesClient.rotateXDegrees(matrices, geometry.rotation);
        matrices.translate((geometry.startX - 8) / 16, -geometry.startY / 16, (geometry.startZ - 8) / 16 - SMALL_OFFSET * 2);
        matrices.scale(1F / geometry.scale, 1F / geometry.scale, 1F / geometry.scale);

        final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        /* The built-in renderers draw the preset's background image themselves, but a layout
           preset takes over the whole panel, so the background has to be drawn here instead
           or every layout preset would be missing its artwork. */
        drawPresetBackground(preset, geometry, facing, matrices, vertexConsumers);

        final PIDSGraphics graphics = new PIDSGraphics(matrices, vertexConsumers, immediate, facing,
                MAX_LIGHT_GLOWING, textColor, font, 1F);

        preset.layout.render(context, graphics,
                geometry.panelLeft(), geometry.panelOffsetY, geometry.panelWidth, geometry.panelHeight);

        immediate.endBatch();
        matrices.popPose();
    }

    /**
     * @return the panel geometry for this renderer, or {@code null} when component layouts
     * are not supported for this PIDS variant.
     */
    protected PIDSGeometry getLayoutGeometry() {
        return null;
    }

    /**
     * Finds the key block of the panel this block is part of.
     *
     * <p>JCM 2.x decides which half is the key one by direction alone:
     * {@code PIDSBlockEntity.isKeyBlock()} is {@code FACING == NORTH || FACING == EAST}. MTR's
     * own block code agrees — {@code BlockPIDSBaseHorizontal.playerWillDestroy} reaches a
     * {@code SOUTH}/{@code WEST} half's companion with {@code pos.relative(FACING)}, and
     * {@code setPlacedBy} is what put it there and gave it the opposite FACING.</p>
     *
     * <p>The answer is used for one thing: keying the compiled script program, so that both
     * halves of a panel share a single script state instead of running two. It does
     * <b>not</b> decide what gets drawn — see {@link #renderScripted} — because JCM 2.x's
     * {@code PIDSRenderer.renderCurated} draws from whichever half it was called for.</p>
     *
     * @return the key block's position, or {@code pos} when this already is the key half
     */
    private static BlockPos keyBlock(Level world, BlockPos pos, Direction facing) {
        if (isKeyFacing(facing)) {
            return pos;
        }
        final BlockPos companion = pos.relative(facing);
        if (world.getBlockState(companion).getBlock() == world.getBlockState(pos).getBlock()) {
            return companion;
        }
        return pos;
    }

    /**
     * @return whether a block facing this way is the key half of its panel, which is JCM 2.x's
     * {@code isKeyBlock()} rule applied to the block state.
     */
    private static boolean isKeyFacing(Direction facing) {
        return facing == Direction.NORTH || facing == Direction.EAST;
    }

    /**
     * @return the other half of this panel, or {@code null} when this is a single-block one.
     *
     * <p>Only used for the once-per-block diagnostic: it says whether a second renderer call
     * belongs to the same panel, which is what makes "the panel is drawn twice" and "the panel
     * is drawn once and one side is blank" distinguishable at all.</p>
     */
    private static BlockPos companionOf(Level world, BlockPos pos, Direction facing) {
        final BlockPos candidate = pos.relative(facing);
        final net.minecraft.world.level.block.Block block = world.getBlockState(pos).getBlock();
        if (world.getBlockState(candidate).getBlock() != block) {
            return null;
        }
        final Direction candidateFacing =
                IBlock.getStatePropertySafe(world.getBlockState(candidate), HorizontalDirectionalBlock.FACING);
        return candidateFacing == facing.getOpposite() ? candidate : null;
    }

    /** Panel shapes already reported, so the diagnostic does not repeat every frame. */
    private static final java.util.Set<String> REPORTED_SHAPES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Reports once per block which half of a panel it is, and what its companion is.
     *
     * <p>Two renderer calls for one panel and one renderer call for a two-sided panel look the
     * same from outside as soon as the preset decides for itself which half paints — the
     * 1A "station display" preset does exactly that with {@code pids.isKeyBlock()}. This
     * records the block's own facing, its key/non-key role and the companion it found, so a
     * panel that is silent can be traced without a screenshot.</p>
     */
    private static void reportPanelShapeOnce(BlockPos pos, Direction facing, BlockPos companion) {
        final String key = pos.asLong() + "|" + facing + "|" + (companion == null ? "-" : companion.asLong());
        if (REPORTED_SHAPES.add(key)) {
            com.jsblock.Joban.LOGGER.info("[Joban Client] [PIDS] panel block at {}, {}, {} facing {} ({} half): companion {}",
                    pos.getX(), pos.getY(), pos.getZ(), facing,
                    isKeyFacing(facing) ? "key" : "other",
                    companion == null ? "<none, single block>"
                            : companion.getX() + ", " + companion.getY() + ", " + companion.getZ());
        }
    }

    /** Drops the once-per-block diagnostics; called when resources reload. */
    public static void forgetReportedPanels() {
        REPORTED_PANELS.clear();
        REPORTED_SHAPES.clear();
    }

    /**
     * How far the finished scripted panel is lifted out of its block, in blocks.
     *
     * <p>JCM 2.x uses {@code translate(0, 0, -0.005)} in ScriptPIDSPreset.render. That value is
     * not enough on MTR 3: the panel still sat inside the block and overlapped it, so the
     * reporter asked for it to be pulled further out. 0.02 is four times JCM 2.x's value and
     * still well under the 0.05 that was tried earlier, which visibly floated the panel off
     * the block.</p>
     */
    private static final float SCRIPT_PANEL_OUTWARD = 0.02F;

    /**
     * JCM 2.x's hard-coded panel translate for this PIDS type, in block space.
     *
     * <p>Each of JCM 2.x's renderers carries its own literal — RVPIDSRenderer uses
     * {@code (-0.21, -0.14, -0.128)}, LCDPIDSRenderer {@code (-0.19, -0.125, -0.130)},
     * PIDS1ARenderer {@code (-0.47, -0.155, -0.130)}. Subclasses override these with their
     * own values; the default is the RV set.</p>
     */
    /* Mutable so a renderer can adopt another PIDS shape's JCM 2.x literals at registration
       time, without a subclass per shape. Defaults are RVPIDSRenderer's. */
    private float panelTranslateX = -0.21F;
    private float panelTranslateY = -0.14F;
    private float panelTranslateZ = -0.128F;
    private int canvasWidth = 136;
    private int canvasHeight = 76;

    /**
     * Adopts another PIDS shape's JCM 2.x panel transform and canvas.
     *
     * <p>JCM 2.x gives each renderer its own literals -- RVPIDSRenderer
     * {@code (-0.21, -0.14, -0.128)} with a 136x76 canvas, PIDS1ARenderer
     * {@code (-0.47, -0.155, -0.130)} with 186x60, LCDPIDSRenderer {@code (-0.19, -0.125,
     * -0.130)} with 133x72. Using the wrong set puts the panel roughly a quarter block off its
     * screen, which is what happened when the 1A PIDS borrowed the RV ones.</p>
     */
    public RenderPIDSBase<T> setScriptPanelProfile(float x, float y, float z, int canvasW, int canvasH) {
        this.panelTranslateX = x;
        this.panelTranslateY = y;
        this.panelTranslateZ = z;
        this.canvasWidth = canvasW;
        this.canvasHeight = canvasH;
        return this;
    }
    protected float scriptPanelTranslateX() {
        return panelTranslateX;
    }

    /** @see #scriptPanelTranslateX() */
    protected float scriptPanelTranslateY() {
        return panelTranslateY;
    }

    /** @see #scriptPanelTranslateX() */
    protected float scriptPanelTranslateZ() {
        return panelTranslateZ;
    }

    /**
     * The canvas size JCM 2.x hands this PIDS type's scripts, in script units.
     *
     * <p>Also a literal there — 136x76 for RV, 133x72 for LCD, 186x60 for 1A — rather than
     * something derived, so scripts that lay out against {@code pids.width} see exactly the
     * numbers their author wrote against.</p>
     */
    protected int scriptCanvasWidth() {
        return canvasWidth;
    }

    /** @see #scriptCanvasWidth() */
    protected int scriptCanvasHeight() {
        return canvasHeight;
    }

    /** Block positions already reported, so the panel diagnostics do not repeat every frame. */
    private static final java.util.Set<String> REPORTED_PANELS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * How far the scripted panel is lifted out of its block, in blocks.
     *
     * <p>The per-draw-call depth step only separates the panel's own layers; the first of them
     * still sits exactly on the block's surface and traded places with the block model's screen
     * face, which showed up as the panel flashing between drawn and black. Lifting the whole
     * panel clear of the surface removes that fight, and 0.05 blocks (under one texture pixel)
     * is too small to see as a gap.</p>
     */
    private static final float SCRIPT_PANEL_OUTWARD_OFFSET = 0.05F;

    /**
     * Reports once per block which path is drawing its panel, and what it was handed.
     *
     * <p>A grey PIDS is ambiguous from the outside: the block may have no preset selected at
     * all, or a script may have run and drawn nothing visible. This separates the two, and
     * also records the canvas the script was given so a size mismatch can be spotted without
     * guessing.</p>
     */
    private static void reportPanelOnce(BlockPos pos, String message) {
        final String key = pos.asLong() + "|" + message;
        if (REPORTED_PANELS.add(key)) {
            com.jsblock.Joban.LOGGER.info("[Joban Client] [PIDS] {} at {}, {}, {}", message, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * Runs a JCM 2.x JavaScript preset.
     *
     * <p>Unlike the JSON-component path this does <b>not</b> draw {@code preset.image}: a
     * scripted preset paints its own background with
     * {@code Texture.create("Background").texture(...).size(pids.width, pids.height)}, so
     * drawing the preset image here as well would double it up.</p>
     *
     * <h2>Canvas size</h2>
     * <p>Script coordinates are in JCM 2.x's unit of {@code 1/96} block, so the canvas the
     * script sees is the panel measured in those units:
     * {@code panelWidth * 96 / geometry.scale}. Computing it rather than hard-coding 128x72
     * keeps a preset's background exactly covering whatever PIDS block it is placed on.</p>
     */
    protected void renderScripted(T entity, Level world, PIDSPreset preset, String[] customMessages,
                                  boolean[] hideArrivals, List<Long> platformIds, boolean hidePlatforms, float delta,
                                  PoseStack matrices, MultiBufferSource vertexConsumers) {
        final PIDSGeometry geometry = getLayoutGeometry();
        if (geometry == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, HorizontalDirectionalBlock.FACING);

        /* Every half paints. That is what JCM 2.x does -- PIDSRenderer.renderCurated has no
           key-block check, it renders from whichever block entity it was called for, anchored
           at that block -- and it is what a two-block PIDS is: the halves sit back to back and
           each draws its own copy on its own face.
           
           An earlier version of this method skipped every half that was not the "head", which
           left each two-block panel showing on one side only. Which half paints, when a preset
           cares, is the preset's business: it asks through pids.isKeyBlock().
           
           Both halves still share one compiled program, keyed by the key block, so a preset
           that plays a sound or keeps a frame counter does it once per panel rather than once
           per half -- and so that the two halves cannot drift apart. */
        final BlockPos panelKey = keyBlock(world, pos, facing);
        reportPanelShapeOnce(pos, facing, companionOf(world, pos, facing));

        final com.jsblock.script.ScriptEngine.Program program = com.jsblock.script.ScriptEngine.programFor(preset, panelKey);
        if (program == null) {
            return;
        }

        final List<ScheduleEntry> scheduleList = new ArrayList<>();
        if (!platformIds.isEmpty()) {
            for (long platformId : platformIds) {
                scheduleList.addAll(ClientData.SCHEDULES_FOR_PLATFORM.getOrDefault(platformId, Collections.emptySet()));
            }
        } else {
            final long closestPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
            scheduleList.addAll(ClientData.SCHEDULES_FOR_PLATFORM.getOrDefault(closestPlatformId, Collections.emptySet()));
        }
        Collections.sort(scheduleList);

        /* JCM 2.x hands each PIDS type a fixed canvas size rather than deriving one, so use its
           literals: 136x76 for RV, 133x72 for LCD, 186x60 for 1A. Scripts position themselves
           against pids.width/pids.height, and those numbers are what their authors wrote to. */
        final int canvasWidth = scriptCanvasWidth();
        final int canvasHeight = scriptCanvasHeight();
        /* The matrix is already JCM 2.x's block space with its 1/96 base scale applied, so a
           script unit is one unit of that space: draw calls must not scale again. Previously
           this was geometry.scale / 96 to compensate for the caller's own 1/geometry.scale,
           which no longer exists in the chain. */
        final float scriptScale = 1F;

        reportPanelOnce(pos, "running script preset=" + preset.id + " canvas=" + canvasWidth + "x" + canvasHeight
                + " scriptScale=" + scriptScale + " outward=" + SCRIPT_PANEL_OUTWARD
                + " canvasArrivals=" + scheduleList.size()
                + " rows=" + (hideArrivals == null ? 0 : hideArrivals.length));

        final com.jsblock.script.PIDSWrapper wrapper = new com.jsblock.script.PIDSWrapper(
                preset.id, hideArrivals == null ? 0 : hideArrivals.length,
                canvasWidth, canvasHeight, pos, platformIds, customMessages, hideArrivals, scheduleList,
                isKeyFacing(facing), hidePlatforms);

        matrices.pushPose();
        /* Verbatim from JCM 2.x's RVPIDSRenderer: origin at the block centre
           (StoredMatrixTransformations starts at 0.5 + the block position), the same two
           rotations, then the panel's own hard-coded translate, JCM 2.x's 0.005-block lift,
           and a flat 1/96 base scale.
           Every number here is JCM 2.x's. The previous version derived equivalents from
           panelLeft()/panelOffsetY()/geometry.scale, and that derivation is what kept moving
           the panel. RenderLCDPIDS and RenderPIDS1A supply their own literals. */
        matrices.translate(0.5, 0.5, 0.5);
        UtilitiesClient.rotateYDegrees(matrices, (geometry.rotate90 ? 90 : 0) - facing.toYRot());
        UtilitiesClient.rotateZDegrees(matrices, 180);
        matrices.translate(scriptPanelTranslateX(), scriptPanelTranslateY(), scriptPanelTranslateZ());
        matrices.translate(0F, 0F, -SCRIPT_PANEL_OUTWARD);
        matrices.scale(1F / 96F, 1F / 96F, 1F / 96F);

        final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final com.jsblock.script.ScriptRenderContext ctx = new com.jsblock.script.ScriptRenderContext(
                matrices, vertexConsumers, immediate, facing, MAX_LIGHT_GLOWING,
                canvasWidth, canvasHeight, scriptScale);

        if (!program.renderOrFail(ctx, program.usesLenientArrivals() ? wrapper.withLenientArrivals() : wrapper)) {
            /* The script threw, very likely before it reached its own background call, which is
               why a script error used to leave the panel completely black.
               
               The most common cause by far is a preset that reads arrivals().get(i) past the end
               without checking, which JCM 2.x answers with null and then breaks on as well -- the
               CRT pack does it for the second train. The pack cannot be edited from here, so the
               frame is retried once with a placeholder there instead: a board showing a
               placeholder row beats a board showing nothing. The choice sticks for this program,
               so the retry happens once and not every frame. A preset that guards, as HKR's
               board does, never gets here and keeps its empty rows empty.
               
               Only a throw that mentions null is retried: that is what an unguarded
               arrivals().get(i) produces ("Cannot call method ... of null"), and a failure with
               some other cause should not be re-run under a different arrivals contract. */
            if (mentionsNull(program.getLastError()) && program.adoptLenientArrivals()) {
                ctx.restartDrawCalls();
                if (program.renderOrFail(ctx, wrapper.withLenientArrivals())) {
                    /* Drawn after all, so the player does not need the red line. The throw stays
                       in the log for the preset author, and one line explains what was done. */
                    program.discardFailureNotice();
                    program.reportLenientFallback();
                } else {
                    /* The null was not an arrival -- sound_transit.js dies on a null station
                       instead -- so the panel really is broken and says so. */
                    program.flushFailureNotice();
                    drawPresetBackground(preset, geometry, facing, matrices, vertexConsumers);
                }
            } else {
                program.flushFailureNotice();
                drawPresetBackground(preset, geometry, facing, matrices, vertexConsumers);
            }
        }

        immediate.endBatch();
        matrices.popPose();
    }

    /**
     * @return whether a script error reads like an access on a null arrival, which is what
     * {@code pids.arrivals().get(i)} past the end produces ("Cannot call method ... of null").
     */
    private static boolean mentionsNull(String error) {
        return error != null && error.toLowerCase(java.util.Locale.ROOT).contains("null");
    }

    /**
     * Draws the preset's background image across the panel.
     *
     * <p>The built-in renderers do this themselves, but both the JSON-layout and scripted paths
     * take over the whole panel. A scripted preset normally paints its own background first, so
     * this is also the fallback used when a script throws before reaching that call — otherwise
     * a single bad script line leaves the player staring at a black screen.</p>
     */
    protected void drawPresetBackground(PIDSPreset preset, PIDSGeometry geometry, Direction facing,
                                        PoseStack matrices, MultiBufferSource vertexConsumers) {
        if (preset == null || preset.image == null) {
            return;
        }
        final VertexConsumer backgroundConsumer = vertexConsumers.getBuffer(MoreRenderLayers.getLight(preset.image, false));
        final float left = geometry.panelLeft();
        IDrawing.drawTexture(matrices, backgroundConsumer,
                left, geometry.panelOffsetY, 0F,
                left + geometry.panelWidth, geometry.panelOffsetY + geometry.panelHeight, 0F,
                0, 0, 1, 1, facing, ARGB_WHITE, MAX_LIGHT_GLOWING);
    }

    public abstract void render(T entity, Level world, String[] customMessages, boolean[] hideArrivals, boolean hidePlatforms, PIDSPreset preset, List<Long> platformId, float delta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay);

    public static String parseVariable(String str, Level world) {
        long time = world.getDayTime() + 6000;
        long hours = time / 1000;
        long minutes = Math.round((time - (hours * 1000)) / 16.8);
        String timeString = String.format("%02d:%02d", hours % 24, minutes % 60);
        String weatherString = world.isRaining() ? "Raining" : world.isThundering() ? "Thundering" : "Sunny";
        String weatherChinString = world.isRaining() ? "下雨" : world.isThundering() ? "雷暴" : "晴天";
        int worldDay = (int) (world.getDayTime() / 24000L);
        int worldPlayer = world.players().size();
        String timeGreetings;

        if (time >= 6000 & time <= 12000) {
            timeGreetings = "Morning";
        } else if (time >= 12000 & time <= 18000) {
            timeGreetings = "Afternoon";
        } else {
            timeGreetings = "Night";
        }

        return str.replace("{time}", timeString)
                .replace("{day}", String.valueOf(worldDay))
                .replace("{weather}", weatherString)
                .replace("{time_period}", timeGreetings)
                .replace("{weatherChin}", weatherChinString)
                .replace("{worldPlayer}", String.valueOf(worldPlayer));
    }

    static void drawTexture(PoseStack matrices, VertexConsumer vertexConsumer, float x, float y, float width, float height, Direction facing, int color, int light) {
        IDrawing.drawTexture(matrices, vertexConsumer, x, y, 0, x + width, y + height, 0, 0, 0, 1, 1, facing, color, light);
    }
}
