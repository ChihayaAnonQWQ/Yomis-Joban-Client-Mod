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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

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

            /* The panel's own platform is the one nearest the block -- MTR's rule, see
               IPIDS.TileEntityPIDS#getPlatformId. platformIds is a Set<Long>, so taking its
               first element picked an arbitrary platform, often a station at the other end of
               the line; it is used here only to gather schedules and as the fallback. */
            primaryPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
            if (!platformIds.isEmpty()) {
                for (long platformId : platformIds) {
                    final Set<ScheduleEntry> schedules = ClientData.SCHEDULES_FOR_PLATFORM.get(platformId);
                    if (schedules != null) {
                        scheduleList.addAll(schedules);
                    }
                }
                if (primaryPlatformId == 0) {
                    primaryPlatformId = platformIds.get(0);
                }
            } else {
                final Set<ScheduleEntry> schedules = ClientData.SCHEDULES_FOR_PLATFORM.get(primaryPlatformId);
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
        } else if (entity instanceof com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) {
            /* JCM 2.x's projector inherits the hide-platform setting with the rest of the PIDS data. */
            hidePlatforms = ((com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) entity).getHidePlatformNumber();
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
        if (projectorMode && entity instanceof com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) {
            /* The chain below puts a panel *on its block* -- those are JCM 2.x's RV panel literals. A
               projector's panel is not on the block, so a components-array preset used to be drawn
               inside it and looked like nothing had rendered at all. The scripted chain is the one that
               knows where a projector's panel goes, so a component preset uses it too; JCM 2.x has one
               renderer per projector for the same reason, and its preset API does not care whether a
               preset came from a script or from a components array. */
            applyScriptPanelTransform(matrices, entity, world, pos, facing, geometry);
        } else {
            matrices.translate(0.5, 0, 0.5);
            UtilitiesClient.rotateYDegrees(matrices, (geometry.rotate90 ? 90 : 0) - facing.toYRot());
            UtilitiesClient.rotateZDegrees(matrices, 180);
            UtilitiesClient.rotateXDegrees(matrices, geometry.rotation);
            matrices.translate((geometry.startX - 8) / 16, -geometry.startY / 16, (geometry.startZ - 8) / 16 - SMALL_OFFSET * 2);
            matrices.scale(1F / geometry.scale, 1F / geometry.scale, 1F / geometry.scale);
        }

        final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        /* The built-in renderers draw the preset's background image themselves, but a layout
           preset takes over the whole panel, so the background has to be drawn here instead
           or every layout preset would be missing its artwork. */
        drawPresetBackground(preset, geometry, facing, matrices, vertexConsumers);

        /* JCM 2.x draws this from its renderer, so every kind of preset gets it -- a components-array
           preset used to be framed by nothing here, because only the scripted path asked for it. */
        drawProjectorFrameIfAiming(world, facing, matrices, vertexConsumers,
                Math.round(geometry.panelWidth), Math.round(geometry.panelHeight));

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
       time, without a subclass per shape. Defaults are RVPIDSRenderer's in X and Y; Z is
       pulled in from JCM 2.x's -0.128 to suit MTR 3's block model, see below. */
    private float panelTranslateX = -0.21F;
    private float panelTranslateY = -0.14F;
    /**
     * Depth of the panel from the block centre, before {@link #SCRIPT_PANEL_OUTWARD}.
     *
     * <p>JCM 2.x uses {@code -0.128}, tuned against MTR 4's model, and this fork added
     * {@code 0.02} on top of it — 0.148 blocks from the centre. MTR 3's own RV model
     * ({@code pids_rv.json}) is a mount rather than a screen: a post, two side plates that run
     * the block's full depth along the centre line, and a pole. The panel has to clear the
     * plates, so the usable range is narrow and was measured in game rather than derived:
     * 0.120 puts the panel inside the mount and the plates cut through it, 0.148 is clear but
     * leaves a gap that reads as a floating board. This sits between the two.</p>
     */
    private float panelTranslateZ = -0.114F;
    private int canvasWidth = 136;
    private int canvasHeight = 76;

    /**
     * Lean of the panel about the local X axis, in degrees, applied before the translate.
     *
     * <p>YJCM's own RV renderer carries the same term — {@code mulPose(XP.rotationDegrees(
     * rotation))}, straight after the two facing rotations and before the panel translate —
     * and it is what puts a panel on the slanted signs. The two SIL shapes are a V: their
     * halves face opposite ways, so one 22.5 degree lean comes out mirrored and forms it. A
     * panel drawn flat is simply wrong on those blocks.</p>
     */
    private float panelRotateXDegrees = 0F;

    /**
     * Whether this panel belongs to a PIDS Projector, which is not attached to its block at all.
     *
     * <p>A projector's panel is placed by its own offset, rotation and scale, so it replaces the
     * whole transform rather than adjusting the profile. It lives here, as a switch, rather than in
     * a subclass, because a subclass referenced from {@code JobanClient}'s renderer registration
     * made that class impossible to load on Forge -- every reference resolved and the class was
     * present in the jar, and the loader still refused it. A flag on an existing renderer has no
     * such problem and the same behaviour.</p>
     */
    protected boolean projectorMode = false;

    /**
     * The block type this renderer draws, as the scripting docs name it -- {@code rv_pids},
     * {@code rv_pids_sil_1}, {@code rv_pids_sil_2}, {@code lcd_pids}, {@code pids_projector},
     * {@code pids_1a}.
     *
     * <p>This is what {@code pids.type} reports. It used to report the preset id, so a script
     * branching on {@code pids.type == "pids_projector"} never took that branch, and a preset's
     * {@code blacklist} -- a list of type names -- never matched anything.</p>
     */
    private String scriptType;


    /** @see #projectorMode */
    /** Sets the block type a script sees as {@code pids.type}. */
    public RenderPIDSBase<T> setScriptType(String scriptType) {
    	this.scriptType = scriptType;
    	return this;
    }

    /**
     * @return the configured type, or one derived from the renderer when it did not name it.
     * The two SIL shapes share a tile entity class, so those renderers must say which one they
     * are; everything else is unambiguous.
     */
    private String scriptType() {
    	if (scriptType != null) {
    		return scriptType;
    	}
    	return projectorMode ? "pids_projector" : "rv_pids";
    }

    public RenderPIDSBase<T> setProjectorMode(boolean projector) {
        this.projectorMode = projector;
        return this;
    }

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

    /**
     * Adopts a shape's lean as well; see {@link #panelRotateXDegrees}.
     *
     * <p>Derive it from the same constructor argument YJCM's renderer passes as {@code rotation}
     * (22.5 for both SIL shapes, 0 for everything else), and derive that shape's translate from
     * its own {@code (startX, startY, startZ)} rather than reusing RV's — the SIL pair sits
     * 0.216 blocks lower and 0.222 further out than the plain board.</p>
     */
    public RenderPIDSBase<T> setScriptPanelRotation(float degreesX) {
        this.panelRotateXDegrees = degreesX;
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
        /* The platform this panel is standing at, resolved the way MTR resolves it -- the one
           nearest the block (IPIDS.TileEntityPIDS#getPlatformId) -- and handed to the wrapper so
           that pids.station() names the station these arrivals came from.

           It is only resolved when the block carries no platform filter of its own: then there is
           nothing else to answer pids.station() with, and it used to answer null. HKR's terminus
           rule (i >= platforms.size() - 1 over route().getPlatforms()) finds the panel's own i by
           matching pids.station().name against the stops, so with no name it never matched and a
           terminus panel drew a real departure where the "not in service" wording belongs. A
           filtered panel hands 0 and keeps its previous resolution: nearest platform first, the
           filter's first id as the fallback. */
        final long panelPlatformId;
        if (!platformIds.isEmpty()) {
            for (long platformId : platformIds) {
                scheduleList.addAll(ClientData.SCHEDULES_FOR_PLATFORM.getOrDefault(platformId, Collections.emptySet()));
            }
            panelPlatformId = 0L;
        } else {
            panelPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
            scheduleList.addAll(ClientData.SCHEDULES_FOR_PLATFORM.getOrDefault(panelPlatformId, Collections.emptySet()));
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
                scriptType(), hideArrivals == null ? 0 : hideArrivals.length,
                canvasWidth, canvasHeight, pos, platformIds, customMessages, hideArrivals, scheduleList,
                isKeyFacing(facing), hidePlatforms, panelPlatformId);

        matrices.pushPose();
        applyScriptPanelTransform(matrices, entity, world, pos, facing, geometry);
        drawProjectorFrameIfAiming(world, facing, matrices, vertexConsumers, canvasWidth, canvasHeight);

        /* The depth test, said outright.
           
           A panel never writes depth -- that is what the light layers are -- and the layers never
           set the depth *test* either, so whatever state is current applies to it. The block entity
           pass normally has it on, but the pixelation pass binds a framebuffer with no depth buffer
           attached, and nothing guarantees the state afterwards: the result is a panel drawn over
           the blocks in front of it, which is what a projector's panel does. Turning it on here is
           cheap and cannot make a correctly occluded panel wrong. */
        RenderSystem.enableDepthTest();

        /* Whole-screen pixelation, when this preset's scale says so. The resource pack declares a
           scale for presets drawn for a low-resolution screen; the client config overrides it,
           including back to 1. The offscreen pass needs the world transform only for the final
           magnified quad, so it is tried here, with the chain built; anything it cannot do falls
           through to the ordinary path below, which is left exactly as it was -- including the
           lenient-arrivals retry. */
        final int pixelScale = com.jsblock.client.ClientConfig.effectivePixelScale(
                preset.id, com.jsblock.data.PackPixelation.packScaleFor(preset.pixelScale));
        final com.jsblock.data.PixelShape declaredShape = com.jsblock.client.ClientConfig.effectivePixelShape(
                preset.id, com.jsblock.data.PackPixelation.packShapeFor(preset.pixelShape));
        /* A pack may name the grid in dots rather than as a divisor, and the more precise one wins.
           A player who has named a scale for this preset overrides both -- including when their entry
           says 1, which is them refusing the pack's grid outright. */
        final int[] pixelResolution = com.jsblock.client.ClientConfig.hasPixelResolutionEntry(preset.id)
                ? com.jsblock.client.ClientConfig.getPixelResolution(preset.id)
                : (com.jsblock.client.ClientConfig.hasPixelScaleEntry(preset.id)
                ? null
                : com.jsblock.data.PackPixelation.packResolutionFor(preset.pixelResolution));
        final int[] pixelTarget = com.jsblock.data.PackPixelation.targetSize(
                canvasWidth, canvasHeight, pixelScale, pixelResolution);
        final com.jsblock.data.PixelShape pixelShape = declaredShape;
        /* The lamps, which are not the same question as how finely the panel is drawn. Left unset
           there is one lamp per rendered pixel, which is what this feature started as; set, they are
           what the player actually sees, and each carries the average of everything behind it. */
        final int[] pixelDots = com.jsblock.client.ClientConfig.hasPixelDotsEntry(preset.id)
                ? com.jsblock.client.ClientConfig.getPixelDots(preset.id)
                : com.jsblock.data.PackPixelation.packDotsFor(preset.pixelDots);

        /* The frame's panel data, which ctx.parseComponent() hands to a component so it can be asked
           the same questions the components-array path asks -- and which both the direct and the
           pixelated path need, since the offscreen pass draws the same panel. Built before the
           pixelation attempt for that reason; it carries no transform, so it is valid in either. */
        final com.jsblock.pids.PIDSContext frameContext = new com.jsblock.pids.PIDSContext(
                world, pos, facing, customMessages, scheduleList, platformIds, hideArrivals, delta,
                (long) Math.floor(MTRClient.getGameTick()));

        /* create(ctx, state, pids) runs here, on the first frame that has a pids to hand it.
           
           It used to run while the program was compiled, with a literal null as the third
           argument, because at that point the panel's arrivals, messages and platform filter do
           not exist yet -- they are read from the world and the client's schedule cache every
           frame. A script that reads that argument in create therefore threw on its first
           statement on a real client, and the throw was caught and logged like any other, so
           nothing downstream noticed: met transit's met_running_board.js:10 does
           `let pos = pids.blockPos();` to name its saved-log file, and instead of naming it after
           the block it wrote met_running_board/undefined_departed.json.
           
           JCM 2.x hands the real wrapper to create as well -- PIDSScriptInstance.<init> calls
           setWrapperObject(wrapper) before any lifecycle function runs, and
           ParsedScript.invokeFunction passes getWrapperObject() as the third argument to create and
           render alike -- so this is the contract, not a convenience. Placed before the pixelation
           attempt so both render paths share one create(). */
        program.start(wrapper);

        if (pixelTarget != null && drawPixelatedPanel(preset, wrapper, program, facing, matrices, vertexConsumers, canvasWidth, canvasHeight, pixelTarget, pixelScale, pixelShape, pixelDots, frameContext)) {
            matrices.popPose();
            return;
        }

        final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final com.jsblock.script.ScriptRenderContext ctx = new com.jsblock.script.ScriptRenderContext(
                matrices, vertexConsumers, immediate, facing, MAX_LIGHT_GLOWING,
                canvasWidth, canvasHeight, scriptScale);

        /* ctx.parseComponent(): the render state, exactly the pair the components-array path builds
           in renderLayout -- same matrices, same vertex source, same immediate source for text -- so
           a component reaches the panel from a script and from a preset by the same route and lands
           in the same place. */
        ctx.setComponentSupport(frameContext,
                new com.jsblock.pids.PIDSGraphics(matrices, vertexConsumers, immediate, facing,
                        MAX_LIGHT_GLOWING,
                        preset.color == null ? geometry.defaultTextColor : preset.color,
                        preset.font == null ? geometry.defaultFont : preset.font,
                        1F));

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
     * Draws the projector's frame, while the player holds a brush.
     *
     * <p>JCM 2.x shows it so a projector can be aimed without guessing where its panel will land:
     * one quad of {@code jsblock:textures/block/light_1.png}, which is a hollow glow, stretched one
     * canvas unit outside the panel on every side — the same texture and the same -1 / +1 outset
     * their renderer uses. Nothing is drawn for any other PIDS.</p>
     */
    protected void drawProjectorFrameIfAiming(Level world, Direction facing, PoseStack matrices,
                                              MultiBufferSource vertexConsumers, int canvasWidth, int canvasHeight) {
        if (!projectorMode) {
            return;
        }
        if (!aimingWithBrush(world)) {
            return;
        }
        /* Four thin strips rather than one quad of a glow texture.
           
           JCM 2.x stretches jsblock:textures/block/light_1.png across the whole panel and relies on
           it being a hollow ring -- but it is a solid 16x16 of flat colour, so the same call paints
           an opaque slab over the panel instead of framing it. Four edges say the same thing and
           cannot cover anything, whatever the texture turns out to be. */
        final VertexConsumer consumer = vertexConsumers.getBuffer(MoreRenderLayers.getLight(
                new net.minecraft.resources.ResourceLocation("jsblock:textures/block/light_1.png"), false));
        final float thickness = 1.5F;
        final float width = canvasWidth;
        final float height = canvasHeight;
        drawFrameStrip(matrices, consumer, facing, 0F, 0F, width, thickness);
        drawFrameStrip(matrices, consumer, facing, 0F, height - thickness, width, thickness);
        drawFrameStrip(matrices, consumer, facing, 0F, thickness, thickness, height - thickness * 2F);
        drawFrameStrip(matrices, consumer, facing, width - thickness, thickness, thickness, height - thickness * 2F);
    }

    /**
     * Whether the player is holding a brush, which is when a projector shows where its panel is.
     *
     * <p>MTR's own test, with a runnable standing in for the answer: it is a held-item check, and
     * running it is how the mod asks the same question elsewhere.</p>
     */
    protected boolean aimingWithBrush(Level world) {
        final net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        final boolean[] aiming = {false};
        IBlock.checkHoldingBrush(world, player, () -> aiming[0] = true);
        return aiming[0];
    }

    /**
     * The four red lines JCM 2.x draws from a projector while a brush is held: the area the panel will
     * cover, 1.785 by 1 blocks per unit of scale, in the projector's own space.
     *
     * <p>Their numbers, their colour and their condition -- see {@code PIDSProjectorRenderer}'s
     * {@code QueuedRenderLayer.LINES} block, which draws the same four edges in the same space before
     * the panel scale is applied.</p>
     */
    private void drawProjectorRangeLines(PoseStack matrices, float panelScale) {
        final MultiBufferSource.BufferSource lines = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final VertexConsumer consumer = lines.getBuffer(RenderType.lines());
        final Matrix4f pose = matrices.last().pose();
        final float width = 1.785F * panelScale;
        final float height = panelScale;
        rangeLine(consumer, pose, 0F, 0F, width, 0F);
        rangeLine(consumer, pose, 0F, 0F, 0F, height);
        rangeLine(consumer, pose, width, 0F, width, height);
        rangeLine(consumer, pose, 0F, height, width, height);
        lines.endBatch();
    }

    /** One edge of {@link #drawProjectorRangeLines}, in the projector's own space. */
    private static void rangeLine(VertexConsumer consumer, Matrix4f pose, float x1, float y1, float x2, float y2) {
        consumer.vertex(pose, x1, y1, 0F).color(1F, 0F, 0F, 1F).normal(0F, 0F, 1F).endVertex();
        consumer.vertex(pose, x2, y2, 0F).color(1F, 0F, 0F, 1F).normal(0F, 0F, 1F).endVertex();
    }

    /**
     * A projector's panel placement, for the built-in renderer too.
     *
     * <p>The built-in chain puts a panel on its block, which is right for RV, SIL, 1A and LCD and wrong
     * for a projector whose panel hangs in the air -- a preset with a texture but no components, which is
     * what a traditional pack contains, was drawn inside the block and looked like nothing had rendered.
     * This is the same chain the scripted path uses, ending in the caller's own units-per-block so the
     * built-in element positions stay the numbers they were written as.</p>
     */
    protected void applyProjectorBuiltInTransform(PoseStack matrices, T entity, Level world,
                                                  Direction facing, boolean rotate90, float scale) {
        final com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector projector =
                (com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) entity;
        matrices.translate(0.5, 0.5, 0.5);
        UtilitiesClient.rotateYDegrees(matrices, (rotate90 ? 90 : 0) - facing.toYRot());
        UtilitiesClient.rotateZDegrees(matrices, 180);

        final float panelScale = (float) projector.getScale();
        UtilitiesClient.rotateYDegrees(matrices, 90);
        matrices.translate(-0.5F + (float) projector.getOffsetX(),
                -0.5F - (float) projector.getOffsetY(),
                0.5F + (float) projector.getOffsetZ());
        matrices.mulPose(Axis.XP.rotationDegrees((float) projector.getRotateX()));
        matrices.mulPose(Axis.YP.rotationDegrees((float) projector.getRotateY()));
        matrices.mulPose(Axis.ZP.rotationDegrees((float) projector.getRotateZ()));
        if (aimingWithBrush(world) && projector.getRotateX() == 0 && projector.getRotateY() == 0
                && projector.getRotateZ() == 0) {
            drawProjectorRangeLines(matrices, panelScale);
        }
        matrices.scale(com.jsblock.block.PIDSProjector.PROJECTOR_PANEL_SCALE * panelScale,
                com.jsblock.block.PIDSProjector.PROJECTOR_PANEL_SCALE * panelScale, 1F);
        matrices.scale(1F / scale, 1F / scale, 1F / scale);
    }

    /** One edge of {@link #drawProjectorFrameIfAiming}, in canvas units. */
    private void drawFrameStrip(PoseStack matrices, VertexConsumer consumer, Direction facing,
                                float x, float y, float stripWidth, float stripHeight) {
        IDrawing.drawTexture(matrices, consumer,
                x, y, 0.1F, x + stripWidth, y + stripHeight, 0.1F,
                0, 0, 1, 1, facing, 0xFFFF0000, MAX_LIGHT_GLOWING);
    }

    /**
     * Puts the pose stack where the script's canvas coordinates apply, for this block's shape.
     *
     * <p>Verbatim from JCM 2.x's RVPIDSRenderer: origin at the block centre
     * (StoredMatrixTransformations starts at 0.5 + the block position), the same two rotations,
     * then the panel's own hard-coded translate, JCM 2.x's 0.005-block lift, and a flat 1/96 base
     * scale. Every number here is JCM 2.x's. The previous version derived equivalents from
     * panelLeft()/panelOffsetY()/geometry.scale, and that derivation is what kept moving the
     * panel. RenderLCDPIDS and RenderPIDS1A supply their own literals through the profile, and
     * RenderProjectorPIDS replaces the whole chain because its panel is not on the block at all.</p>
     */
    protected void applyScriptPanelTransform(PoseStack matrices, T entity, Level world, BlockPos pos,
                                             Direction facing, PIDSGeometry geometry) {
        if (projectorMode && entity instanceof com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) {
            /* JCM 2.x's projector chain, but only after the base transform every PIDS gets.
               
               Their projector renderer extends the ordinary PIDS renderer, so its chain runs on top
               of the block centre, the facing rotation and the 180 degree Z flip -- and that
               half-turn is what puts the panel the right way up. Replacing the whole transform
               instead, which the first version of this did, leaves the panel mirrored: its text
               comes out upside down. */
            matrices.translate(0.5, 0.5, 0.5);
            UtilitiesClient.rotateYDegrees(matrices, (geometry.rotate90 ? 90 : 0) - facing.toYRot());
            UtilitiesClient.rotateZDegrees(matrices, 180);

            final com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector projector =
                    (com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) entity;
            final float panelScale = (float) projector.getScale();
            UtilitiesClient.rotateYDegrees(matrices, 90);
            matrices.translate(-0.5F + (float) projector.getOffsetX(),
                    -0.5F - (float) projector.getOffsetY(),
                    0.5F + (float) projector.getOffsetZ());
            matrices.mulPose(Axis.XP.rotationDegrees((float) projector.getRotateX()));
            matrices.mulPose(Axis.YP.rotationDegrees((float) projector.getRotateY()));
            matrices.mulPose(Axis.ZP.rotationDegrees((float) projector.getRotateZ()));
            /* JCM 2.x's projection rectangle, in the same space and under the same condition: four red
               lines around the area the panel will cover, drawn only while the projector is unrotated.
               Their rectangle is a plain axis-aligned one, so once the panel is turned it stops saying
               anything true -- the frame around the panel itself covers that case. */
            if (aimingWithBrush(world) && projector.getRotateX() == 0 && projector.getRotateY() == 0
                    && projector.getRotateZ() == 0) {
                drawProjectorRangeLines(matrices, panelScale);
            }
            matrices.scale(com.jsblock.block.PIDSProjector.PROJECTOR_PANEL_SCALE * panelScale,
                    com.jsblock.block.PIDSProjector.PROJECTOR_PANEL_SCALE * panelScale, 1F);
            matrices.scale(1F / 96F, 1F / 96F, 1F / 96F);
            return;
        }
        matrices.translate(0.5, 0.5, 0.5);
        UtilitiesClient.rotateYDegrees(matrices, (geometry.rotate90 ? 90 : 0) - facing.toYRot());
        UtilitiesClient.rotateZDegrees(matrices, 180);
        /* YJCM's renderer leans the panel here, after the facing rotations and before the
           translate, so the translate runs in the leaning frame. The SIL shapes need it; see
           panelRotateXDegrees. */
        matrices.mulPose(Axis.XP.rotationDegrees(panelRotateXDegrees));
        matrices.translate(scriptPanelTranslateX(), scriptPanelTranslateY(), scriptPanelTranslateZ());
        matrices.translate(0F, 0F, -SCRIPT_PANEL_OUTWARD);
        matrices.scale(1F / 96F, 1F / 96F, 1F / 96F);
    }

    /**
     * @return whether a script error reads like an access on a null arrival, which is what
     * {@code pids.arrivals().get(i)} past the end produces ("Cannot call method ... of null").
     */
    private static boolean mentionsNull(String error) {
        return error != null && error.toLowerCase(java.util.Locale.ROOT).contains("null");
    }

    /** Set once a pixelation attempt has failed, so the log gets one line and not one per frame. */
    private boolean pixelationFailureReported = false;

    /**
     * Presets whose one-shot pixelation diagnostic has been logged.
     *
     * <p>Static, and keyed by preset and scale alone rather than by the whole message: the first
     * version keyed on the message, which carried the per-frame draw count, so it logged on every
     * frame and read the target back with it.</p>
     */
    private static final java.util.Set<String> pixelationReported = new java.util.HashSet<>();

    /** Presets whose offscreen pass needed the placeholder-arrival retry. See the retry above. */
    private static final java.util.Set<String> pixelationRecovered = new java.util.HashSet<>();

    /**
     * The dot-matrix mask: opaque in the gaps between dots, transparent inside them.
     *
     * <p>One cell of the texture is one pixel of the offscreen target, so the mask tiles once per
     * target pixel when it is drawn over the composite. See the circle branch in
     * {@link #drawPixelatedPanel}.</p>
     */
    private static final ResourceLocation PIXEL_DOT_MASK =
            new ResourceLocation(com.jsblock.Joban.MOD_ID, "textures/pids/pixel_dot.png");

    /**
     * The render state a parsed component draws through during the pixelation pass.
     *
     * <p>The offscreen twin of the bundle {@code renderScripted} builds for the direct path: same
     * panel data, same sources, except that both of them are the offscreen ones. That source keeps a
     * builder per layer and is flushed by the caller, which is what the script's own text already
     * relies on; a component's text and quads reach it the same way.</p>
     *
     * <p>Colours and font come from the preset, as they do on the direct path, so a component looks
     * the same pixelated as not.</p>
     */
    private com.jsblock.pids.PIDSGraphics offscreenComponentGraphics(PIDSPreset preset, PoseStack panelStack,
                                                                     PIDSPixelatedPanel.OffscreenSource source,
                                                                     Direction facing) {
        final PIDSGeometry geometry = getLayoutGeometry();
        return new PIDSGraphics(panelStack, source, source, facing, MAX_LIGHT_GLOWING,
                preset.color == null || geometry == null ? IGui.ARGB_WHITE : preset.color,
                preset.font == null || geometry == null ? "mtr:mtr" : preset.font,
                1F);
    }

    /**
     * Draws this panel by rendering its script into an offscreen target and magnifying that.
     *
     * <p>Called with the world transform already built, because the magnified quad is drawn in it.
     * The script itself is run in the target's own space, so it draws exactly the canvas coordinates
     * it always does — the only thing that changes is where those coordinates land.</p>
     *
     * <p>Deliberately all-or-nothing: if the target cannot be made, or the script throws, this
     * returns {@code false} and the ordinary path draws the panel instead. That keeps the
     * lenient-arrivals retry, the fallback background and every other detail of the verified path in
     * one place, and means a preset set to pixelate on a machine where the pass does not work shows
     * its panel as it always did rather than not at all.</p>
     */
    private boolean drawPixelatedPanel(PIDSPreset preset, com.jsblock.script.PIDSWrapper wrapper,
                                       com.jsblock.script.ScriptEngine.Program program, Direction facing,
                                       PoseStack matrices, MultiBufferSource vertexConsumers,
                                       int canvasWidth, int canvasHeight, int[] pixelTarget, int pixelScale,
                                       com.jsblock.data.PixelShape pixelShape, int[] dotGrid,
                                       com.jsblock.pids.PIDSContext frameContext) {
        final PIDSPixelatedPanel panel;
        try {
            panel = PIDSPixelatedPanel.of(canvasWidth, canvasHeight, pixelTarget[0], pixelTarget[1]);
        } catch (Throwable t) {
            reportPixelationFailure("creating the offscreen target for " + preset.id, t);
            return false;
        }

        /* How many lamps the board has.
           
           Unset means one per rendered pixel, which is this feature's original behaviour. Set, the
           grid is clamped to the target -- there cannot be more lamps than there are pixels to fill
           them -- and its proportions are taken from the canvas like the target's, so a lamp is
           never stretched either. */
        final int[] dots = dotGrid == null || dotGrid[0] < 1
                ? pixelTarget
                : new int[]{
                Math.min(dotGrid[0], pixelTarget[0]),
                dotGrid.length >= 2 && dotGrid[1] >= 1
                        ? Math.min(dotGrid[1], pixelTarget[1])
                        : Math.max(1, Math.round(Math.min(dotGrid[0], pixelTarget[0])
                        * (float) pixelTarget[1] / pixelTarget[0]))};

        /* Dots finer than one canvas unit are dots nobody can see: the panel is a couple of blocks
           wide, so a lamp at a tenth of a unit is a fraction of a screen pixel and the mask averages
           into a flat tint. Square pixels in that case -- the picture is still whatever resolution
           was asked for, there is simply no lamp grid to show. */
        final boolean dotsVisible = dots[0] <= canvasWidth && dots[1] <= canvasHeight;
        final com.jsblock.data.PixelShape shape =
                dotsVisible ? pixelShape : com.jsblock.data.PixelShape.SQUARE;

        final PIDSPixelatedPanel.OffscreenSource offscreenSource = new PIDSPixelatedPanel.OffscreenSource(panel);
        boolean drawn = false;
        com.jsblock.script.ScriptRenderContext ctx = null;
        /* One begin() per attempt: it binds the target and pushes the modelview, so the pose stack it
           returns is the one the offscreen draws -- the script's and a parsed component's alike -- must
           use, and it is not valid to ask for a second one while the first is running. */
        PoseStack panelStack = null;
        try {
            /* The facing is reversed for the offscreen pass.
               
               The pass is mirrored by construction: the script's origin is the panel's top left and
               its y grows downward, so the orthographic projection is y-down, and a y-down mapping
               reverses triangle winding. In the world the quads are wound for the panel's facing, so
               here every one of them lands as a back face and the layers -- which set their own cull
               state on setup -- throw them all away.
               
               Asking MTR's draw helper for the opposite winding cancels the mirror out. */
            panelStack = panel.begin();
            ctx = new com.jsblock.script.ScriptRenderContext(
                    panelStack, offscreenSource, offscreenSource, facing.getOpposite(), MAX_LIGHT_GLOWING,
                    canvasWidth, canvasHeight, 1F);
            /* Uploads go through the panel rather than through RenderType.end, which would bind the
               main framebuffer and drop the whole panel into the world. See PIDSPixelatedPanel.upload. */
            ctx.setQuadUploader(panel::upload);
            /* ctx.parseComponent() in the offscreen pass: the same panel data as the direct path, and
               the offscreen source for both the quads and the text. PIDSPixelatedPanel.OffscreenSource
               exists precisely so that text drawn through it lands in the target instead of the world,
               which is what a component's text needs; the quads accumulate in it per layer and are
               uploaded by the flush() below. The facing is the reversed one for the same reason the
               script's own draws get it -- the pass is mirrored, and the layers cull back faces. */
            ctx.setComponentSupport(frameContext, offscreenComponentGraphics(
                    preset, panelStack, offscreenSource, facing.getOpposite()));
            drawn = program.renderOrFail(ctx, program.usesLenientArrivals() ? wrapper.withLenientArrivals() : wrapper);
            offscreenSource.flush();

            if (!drawn) {
                /* The same second chance the direct path gives, for the same reason and with the
                   same shape: run it again with placeholder arrivals. Without it a preset whose
                   script throws once -- which the CRT preset does, on its own null bug -- never
                   pixelates, because this pass gives up where the direct path recovers, and the
                   panel then falls back to being drawn smoothly. The target is cleared again by
                   begin(), so nothing from the failed attempt survives. */
                try {
                    offscreenSource.flush();
                } catch (Throwable ignored) {
                    /* A partial panel is about to be cleared anyway. */
                }
                panel.end();
                panelStack = panel.begin();
                ctx = new com.jsblock.script.ScriptRenderContext(
                        panelStack, offscreenSource, offscreenSource, facing.getOpposite(), MAX_LIGHT_GLOWING,
                        canvasWidth, canvasHeight, 1F);
                ctx.setQuadUploader(panel::upload);
                /* The retry draws the same frame, so it needs the same component state -- without this
                   a preset that uses ctx.parseComponent would pixelate only until its first throw. */
                ctx.setComponentSupport(frameContext, offscreenComponentGraphics(
                        preset, panelStack, offscreenSource, facing.getOpposite()));
                drawn = program.renderOrFail(ctx, wrapper.withLenientArrivals());
                offscreenSource.flush();
                if (drawn && pixelationRecovered.add(preset.id)) {
                    com.jsblock.Joban.LOGGER.info("[PIDS pixelation] preset=" + preset.id
                            + " threw on its first pass and was redrawn with placeholder arrivals,"
                            + " the same recovery the direct path applies. Reported once.");
                }
            }

            if (drawn && pixelationReported.add(preset.id + "@" + pixelTarget[0] + "x" + pixelTarget[1] + "@" + shape.configName())) {
                /* Read the target back once per preset, never again: "drew nothing" and "drew, but
                   the composite does not sample it" look identical on screen and need opposite
                   fixes, and a readback stalls the pipeline, so one frame is enough. */
                final int[] centre = panel.readCentrePixel();
                com.jsblock.Joban.LOGGER.info("[PIDS pixelation] preset=" + preset.id
                        + " scale=" + pixelScale
                        + (pixelTarget[0] * pixelScale == canvasWidth && pixelTarget[1] * pixelScale == canvasHeight
                        ? "" : " (declared grid " + pixelTarget[0] + "x" + pixelTarget[1] + ")")
                        + " shape=" + shape.configName()
                        + (shape == pixelShape ? "" : " (dots off: a lamp would be under a screen pixel)")
                        + " dots=" + dots[0] + "x" + dots[1]
                        + " target=" + panel.targetWidth() + "x" + panel.targetHeight()
                        + " drawCalls=" + ctx.drawCallCount()
                        + " centrePixel=" + centre[0] + "," + centre[1] + "," + centre[2] + "," + centre[3]
                        + " " + panel.bindingReport());
            }
        } catch (Throwable t) {
            reportPixelationFailure("drawing " + preset.id + " into its offscreen target", t);
        } finally {
            try {
                offscreenSource.flush();
            } catch (Throwable ignored) {
                /* Nothing to flush is not a reason to skip restoring the framebuffer. */
            }
            try {
                panel.end();
            } catch (Throwable t) {
                reportPixelationFailure("restoring the framebuffer after " + preset.id, t);
            }
        }

        if (!drawn) {
            return false;
        }

        /* One quad covering the canvas, in canvas units, in the panel's own plane. Nearest
           filtering turns the target's pixels into the squares the feature is for.

           v is flipped: the target's texture has v=0 at its bottom, while the orthographic pass
           puts canvas y=0 at the top.

           Two immediate sources, flushed one after the other, rather than two quads in one batch.
           A batch is a map keyed by layer, flushed in that map's order, and the light layer sorts
           what it holds by distance to the camera when it uploads -- so two quads in one batch are
           not promised to come out in the order they went in. That is what made the dots flicker:
           the panel alternated between the image on top and the mask on top.

           Both layers are the translucent variant. The opaque one ignores alpha, which for a mask
           that is transparent inside its dots means painting its dots black -- the other half of
           the same flicker. */
        final MultiBufferSource.BufferSource image = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final VertexConsumer consumer = image.getBuffer(MoreRenderLayers.getLight(panel.location(), true));
        IDrawing.drawTexture(matrices, consumer,
                0F, 0F, 0F, panel.canvasWidth(), panel.canvasHeight(), 0F,
                0F, 1F, 1F, 0F, facing, ARGB_WHITE, MAX_LIGHT_GLOWING);
        image.endBatch();

        if (shape == com.jsblock.data.PixelShape.CIRCLE) {
            /* Round dots instead of square pixels. One mask cell per target pixel, tiled by making
               the uv range as many units wide as the target has pixels across.

               No depth offset: the two quads are now in separate batches flushed in order, so which
               one is on top is decided here rather than sorted out later.

               A preset that leaves part of its canvas transparent is the one case this does not
               suit -- the gaps are painted wherever the mask is, and there is no per-pixel alpha to
               test against without reading the target back. Every pack this was built against paints
               a full background. */
            final MultiBufferSource.BufferSource dotPass = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            final VertexConsumer mask = dotPass.getBuffer(MoreRenderLayers.getLight(PIXEL_DOT_MASK, true));
            /* One mask cell per lamp, so the uv range is the lamp count rather than the pixel count:
               every lamp then shows the average of the cell behind it, which is what a real board's
               driver chips do. */
            IDrawing.drawTexture(matrices, mask,
                    0F, 0F, 0F, panel.canvasWidth(), panel.canvasHeight(), 0F,
                    0F, dots[1], dots[0], 0F, facing, ARGB_WHITE, MAX_LIGHT_GLOWING);
            dotPass.endBatch();
        }

        return true;
    }

    /**

    /** Reports a pixelation failure once per renderer, then stops mentioning it. */
    private void reportPixelationFailure(String what, Throwable t) {
        if (pixelationFailureReported) {
            return;
        }
        pixelationFailureReported = true;
        com.jsblock.Joban.LOGGER.warn("[PIDS pixelation] Failed while " + what
                + "; falling back to drawing the panel directly. This is reported once.", t);
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
