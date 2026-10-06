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
                renderScripted(entity, world, preset, customMessages, hideArrivals, platformIds, delta, matrices, vertexConsumers);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

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
        if (preset.image != null) {
            final VertexConsumer backgroundConsumer = vertexConsumers.getBuffer(MoreRenderLayers.getLight(preset.image, false));
            final float left = geometry.panelLeft();
            IDrawing.drawTexture(matrices, backgroundConsumer,
                    left, geometry.panelOffsetY, 0F,
                    left + geometry.panelWidth, geometry.panelOffsetY + geometry.panelHeight, 0F,
                    0, 0, 1, 1, facing, ARGB_WHITE, MAX_LIGHT_GLOWING);
        }

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
                                  boolean[] hideArrivals, List<Long> platformIds, float delta,
                                  PoseStack matrices, MultiBufferSource vertexConsumers) {
        final PIDSGeometry geometry = getLayoutGeometry();
        if (geometry == null) {
            return;
        }
        final com.jsblock.script.ScriptEngine.Program program = com.jsblock.script.ScriptEngine.programFor(preset);
        if (program == null) {
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

        /* One script unit is 1/96 block, so the caller's local space (which is scaled by
           1/geometry.scale) needs geometry.scale / 96 local units per script unit. */
        final float scriptScale = geometry.scale / 96F;
        if (scriptScale <= 0F) {
            return;
        }
        final int canvasHeight = Math.round(geometry.panelHeight / scriptScale);
        final int canvasWidth = Math.round(geometry.panelWidth / scriptScale);

        final com.jsblock.script.PIDSWrapper wrapper = new com.jsblock.script.PIDSWrapper(
                preset.id, hideArrivals == null ? 0 : hideArrivals.length,
                canvasWidth, canvasHeight, pos, platformIds, customMessages, hideArrivals, scheduleList);

        matrices.pushPose();
        matrices.translate(0.5, 0, 0.5);
        UtilitiesClient.rotateYDegrees(matrices, (geometry.rotate90 ? 90 : 0) - facing.toYRot());
        UtilitiesClient.rotateZDegrees(matrices, 180);
        UtilitiesClient.rotateXDegrees(matrices, geometry.rotation);
        matrices.translate((geometry.startX - 8) / 16, -geometry.startY / 16, (geometry.startZ - 8) / 16 - SMALL_OFFSET * 2);
        matrices.scale(1F / geometry.scale, 1F / geometry.scale, 1F / geometry.scale);
        /* Move to the panel's top-left corner: scripts position everything from there.
           panelLeft()/panelOffsetY are already expressed in this post-scale space, which is
           the same space renderLayout() draws its background quad in. */
        matrices.translate(geometry.panelLeft(), geometry.panelOffsetY, 0F);

        final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final com.jsblock.script.ScriptRenderContext ctx = new com.jsblock.script.ScriptRenderContext(
                matrices, vertexConsumers, immediate, facing, MAX_LIGHT_GLOWING,
                canvasWidth, canvasHeight, scriptScale);

        program.render(ctx, wrapper);

        immediate.endBatch();
        matrices.popPose();
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