package com.jsblock.render;

import com.jsblock.data.PIDSPreset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.MTRClient;
import mtr.block.IBlock;
import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.data.IGui;
import mtr.data.RailwayData;
import mtr.data.Route;
import mtr.data.ScheduleEntry;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import mtr.render.MoreRenderLayers;
import mtr.render.RenderTrains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jsblock.screen.IDrawingJoban.renderTextWithOffset;

/**
 * Class for rendering LCD PIDS
 * @author LX86
 * @see com.jsblock.block.PIDSLCD
 */
public class RenderLCDPIDS<T extends BlockEntityMapper> extends RenderPIDSBase<T> implements IGui {

    private static final float BACKGROUND_WIDTH = 111F;
    private static final float BACKGROUND_HEIGHT = 60F;
    private final float scale;
    private final float totalScaledWidth;
    private final float destinationStart;
    private final float destinationMaxWidth;
    private final float platformMaxWidth;
    private final float arrivalMaxWidth;
    private final int maxArrivals;
    private final float maxHeight;
    private final float startX;
    private final float startY;
    private final float startZ;
    private final boolean rotate90;
    private final boolean showPlatforms;
    private final float screenWidth;
    private final float rotation;
    private final int defaultTextColor;
    private final static String defaultFont = "jsblock:pids_lcd";

    private final PIDSPreset DEFAULT_PRESET = new PIDSPreset(null, false, false, false, SHOW_ALL_ROWS, null, null, null);
    private List<ClientCache.PlatformRouteDetails> routeData;

    public RenderLCDPIDS(BlockEntityRenderDispatcher dispatcher, int maxArrivals, float startX, float startY, float startZ, float maxHeight, int maxWidth, boolean rotate90, boolean renderArrivalNumber, boolean showPlatforms, int defaultTextColor, float rotation) {
        super(dispatcher, maxArrivals);
        scale = 230 * maxArrivals / maxHeight;
        totalScaledWidth = scale * maxWidth / 16;
        destinationStart = renderArrivalNumber ? scale * 2 / 16 : 0;
        destinationMaxWidth = totalScaledWidth * 0.33F;
        platformMaxWidth = showPlatforms ? scale * 2F / 16 : 0;
        arrivalMaxWidth = totalScaledWidth - destinationStart - destinationMaxWidth - platformMaxWidth;
        screenWidth = arrivalMaxWidth / 1.35F;
        this.maxArrivals = maxArrivals;
        this.maxHeight = maxHeight;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.rotate90 = rotate90;
        this.showPlatforms = showPlatforms;
        this.rotation = rotation;
        this.defaultTextColor = defaultTextColor;
    }

    /**
     * Exposes this renderer's panel geometry so {@link RenderPIDSBase} can draw
     * component-based layout presets onto exactly the same panel rectangle as the
     * built-in background.
     *
     * <p>The offsets are copied verbatim from the background draw call below:
     * the quad is placed at {@code startX - 21F / 2} and {@code -1.5F}, inside a
     * {@code translate(0, -1F, 0.01)}. Note that {@code 21F / 2} is <em>not</em>
     * {@code BACKGROUND_WIDTH / 2}, so the origin cannot be derived from the size.</p>
     */
    @Override
    protected com.jsblock.pids.PIDSGeometry getLayoutGeometry() {
        return new com.jsblock.pids.PIDSGeometry(startX, startY, startZ, scale,
                BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                -21F / 2F, -1F - 1.5F,
                rotate90, rotation, defaultFont, defaultTextColor);
    }

    @Override
    public void render(T entity, Level world, String[] customMessages, boolean[] hideArrivals, boolean hidePlatforms, PIDSPreset preset, List<Long> filteredPlatformIds, float delta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final BlockPos pos = entity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, HorizontalDirectionalBlock.FACING);

        try {
            final PIDSPreset pidsPreset = preset == null ? DEFAULT_PRESET : preset;
            final Font textRenderer = Minecraft.getInstance().font;
            final String textFont = pidsPreset.font == null ? defaultFont : pidsPreset.font;
            final int textColor = pidsPreset.color == null ? defaultTextColor : pidsPreset.color;
            final int languageTicks = (int) Math.floor(MTRClient.getGameTick()) / SWITCH_LANGUAGE_TICKS;
            final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            final List<ScheduleEntry> scheduleList = new ArrayList<>();

            matrices.pushPose();
            matrices.translate(0.5, 0, 0.5);
            UtilitiesClient.rotateYDegrees(matrices, (rotate90 ? 90 : 0) - facing.toYRot());
            UtilitiesClient.rotateZDegrees(matrices, 180);
            UtilitiesClient.rotateXDegrees(matrices, rotation);
            matrices.translate((startX - 8) / 16, -startY / 16 + 0 * maxHeight / maxArrivals / 16, (startZ - 8) / 16 - SMALL_OFFSET * 2);
            matrices.scale(1F / scale, 1F / scale, 1F / scale);

            if(pidsPreset.image != null) {
                matrices.pushPose();
                final VertexConsumer vertexConsumerPIDSBG = vertexConsumers.getBuffer(MoreRenderLayers.getLight(pidsPreset.image, false));
                matrices.translate(0, -1F, 0.01);
                drawTexture(matrices, vertexConsumerPIDSBG, startX - 21F / 2, -1.5F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, facing, ARGB_WHITE, MAX_LIGHT_GLOWING);
                matrices.popPose();
            }

            matrices.popPose();

            /* If the player is too far away from the PIDS that not even the train renders */
            if (RenderTrains.shouldNotRender(pos, Math.min(MAX_VIEW_DISTANCE, RenderTrains.maxTrainRenderDistance), rotate90 ? null : facing)) {
                return;
            }

            if (!filteredPlatformIds.isEmpty()) {
                for(long platformId : filteredPlatformIds) {
                    final Set<ScheduleEntry> schedulesForPlatform = ClientData.SCHEDULES_FOR_PLATFORM.get(platformId);
                    if(schedulesForPlatform != null) {
                        scheduleList.addAll(schedulesForPlatform);
                    }
                }
            } else {
                final long closestPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
                final Set<ScheduleEntry> schedulesForPlatform = ClientData.SCHEDULES_FOR_PLATFORM.get(closestPlatformId);
                if(schedulesForPlatform != null) {
                    scheduleList.addAll(schedulesForPlatform);
                }
            }
            Collections.sort(scheduleList);

            final boolean showCarLength;
            int maxCars = 0;
            int minCars = Integer.MAX_VALUE;

            /* Find the maximum and minimum cars out of the schedule list */
            for (final ScheduleEntry scheduleEntry : scheduleList) {
                final int trainCars = scheduleEntry.trainCars;
                if (trainCars > maxCars) {
                    maxCars = trainCars;
                }
                if (trainCars < minCars) {
                    minCars = trainCars;
                }
            }

            showCarLength = minCars != maxCars;
            int entryIndex = 0;

            /* Loop through each arrival */
            for (int i = 0; i < maxArrivals; i++) {
                final String destinationString;
                final boolean useCustomMessage;
                final ScheduleEntry currentSchedule = entryIndex < scheduleList.size() ? scheduleList.get(entryIndex) : null;
                final Route route = currentSchedule == null ? null : ClientData.DATA_CACHE.routeIdMap.get(currentSchedule.routeId);

                if (entryIndex < scheduleList.size() && !hideArrivals[i] && route != null) {
                    final String[] destinationSplit = ClientData.DATA_CACHE.getFormattedRouteDestination(route, currentSchedule.currentStationIndex, "").split("\\|");
                    final boolean isLightRailRoute = route.isLightRailRoute;
                    final String[] routeNumberSplit = route.lightRailRouteNumber.split("\\|");

                    if (customMessages[i].isEmpty()) {
                        destinationString = (isLightRailRoute ? routeNumberSplit[languageTicks % routeNumberSplit.length] + " " : "") + IGui.textOrUntitled(destinationSplit[languageTicks % destinationSplit.length]);
                        useCustomMessage = false;
                    } else {
                        final String[] customMessageSplit = customMessages[i].split("\\|");
                        final int destinationMaxIndex = Math.max(routeNumberSplit.length, destinationSplit.length);
                        final int indexToUse = languageTicks % (destinationMaxIndex + customMessageSplit.length);

                        if (indexToUse < destinationMaxIndex) {
                            destinationString = (isLightRailRoute ? routeNumberSplit[languageTicks % routeNumberSplit.length] + " " : "") + IGui.textOrUntitled(destinationSplit[languageTicks % destinationSplit.length]);
                            useCustomMessage = false;
                        } else {
                            destinationString = customMessageSplit[indexToUse - destinationMaxIndex];
                            useCustomMessage = true;
                        }
                    }
                } else {
                    final String[] destinationSplit = customMessages[i].split("\\|");
                    destinationString = destinationSplit[languageTicks % destinationSplit.length];
                    useCustomMessage = true;
                }

                matrices.pushPose();
                matrices.translate(0.5, 0, 0.5);
                UtilitiesClient.rotateYDegrees(matrices, (rotate90 ? 90 : 0) - facing.toYRot());
                UtilitiesClient.rotateZDegrees(matrices, 180);
                matrices.translate((startX - 8) / 16, -startY / 16 + i * maxHeight / maxArrivals / 16, (startZ - 8) / 16 - SMALL_OFFSET * 2);
                matrices.scale(1F / (scale / 2), 1F / (scale / 2), 1F / (scale / 2));

                if (useCustomMessage) {
                    renderTextWithOffset(matrices, textRenderer, immediate, destinationString, 0, 0, screenWidth, 4, textColor, MAX_LIGHT_GLOWING, HorizontalAlignment.LEFT, VerticalAlignment.TOP, false, textFont);
                } else {
                    final Component arrivalText;
                    final int seconds = (int) Math.round((currentSchedule.arrivalMillis - System.currentTimeMillis()) / 1000.0);
                    final boolean isCJK = IGui.isCjk(destinationString);
                    if (seconds >= 60) {
                        arrivalText = Text.translatable(isCJK ? "gui.mtr.arrival_min_cjk" : "gui.mtr.arrival_min", seconds / 60);
                    } else if (seconds > 0) {
                        arrivalText = Text.translatable(isCJK ? "gui.mtr.arrival_sec_cjk" : "gui.mtr.arrival_sec", seconds);
                    } else {
                        arrivalText = Text.translatable("gui.jsblock.train_arrived");
                    }


                    matrices.pushPose();
                    matrices.translate(destinationStart, 0, 0);

                    renderTextWithOffset(matrices, textRenderer, immediate, destinationString, 0, 0, destinationMaxWidth, 5, textColor, MAX_LIGHT_GLOWING, HorizontalAlignment.LEFT, VerticalAlignment.TOP, false, textFont);
                    matrices.popPose();

                    if (arrivalText != null) {
                        final boolean isShowCar = showCarLength && (languageTicks % 6 == 0 || languageTicks % 6 == 1);
                        matrices.pushPose();
                        matrices.translate(screenWidth, 0, 0);

                        if (isShowCar) {
                            final Component carText = Text.translatable(isCJK ? "gui.mtr.arrival_car_cjk" : "gui.mtr.arrival_car", currentSchedule.trainCars);
                            final Integer carColor = pidsPreset.getCarColor(currentSchedule.trainCars);
                            renderTextWithOffset(matrices, textRenderer, immediate, carText.getString(), 0, -0.025F, 15, 5, carColor == null ? textColor : carColor, MAX_LIGHT_GLOWING, HorizontalAlignment.RIGHT, VerticalAlignment.TOP, false, textFont);
                        } else {
                            renderTextWithOffset(matrices, textRenderer, immediate, arrivalText.getString(), 0, -0.025F, 15, 5, textColor, MAX_LIGHT_GLOWING, HorizontalAlignment.RIGHT, VerticalAlignment.TOP, false, textFont);
                        }

                        matrices.popPose();
                    }
                }
                matrices.popPose();
                if(hideArrivals[i]) continue;
                if(useCustomMessage && pidsPreset.customTextPushArrival) continue;
                entryIndex++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}