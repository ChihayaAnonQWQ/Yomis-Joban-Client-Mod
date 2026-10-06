package com.jsblock.render;

import com.jsblock.Joban;
import com.jsblock.data.PIDSPreset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.MTRClient;
import mtr.block.IBlock;
import mtr.client.ClientData;
import mtr.data.IGui;
import mtr.data.Platform;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jsblock.screen.IDrawingJoban.renderTextWithOffset;

/**
 * Class for rendering RV PIDS
 * @author LX86
 * @see com.jsblock.block.PIDSRV
 * @see com.jsblock.block.PIDSRVSIL1
 * @see com.jsblock.block.PIDSRVSIL2
 */
public class RenderRVPIDS<T extends BlockEntityMapper> extends RenderPIDSBase<T> implements IGui {

    private static final float BACKGROUND_WIDTH = 119F;
    private static final float BACKGROUND_HEIGHT = 65.8F;
    private static final float BACKGROUND_Y = -9.5F;
    private static final double OVERLAY_DISTANCE = 0.1;
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
    private final int defaultTextColor;
    private final float rotation;
    private final static String defaultFont = "mtr:mtr";
    private final PIDSPreset DEFAULT_PRESET = new PIDSPreset(new ResourceLocation(Joban.MOD_ID, "textures/block/pids_rv_screen.png"), true, true, false, SHOW_ALL_ROWS, 0, null, null);

    public RenderRVPIDS(BlockEntityRenderDispatcher dispatcher, int maxArrivals, float startX, float startY, float startZ, float maxHeight, float maxWidth, boolean rotate90, boolean renderArrivalNumber, int textColor, float rotation) {
        super(dispatcher, maxArrivals);
        scale = 230 * maxArrivals / maxHeight;
        totalScaledWidth = scale * maxWidth / 16;
        destinationStart = renderArrivalNumber ? scale * 2 / 16 : 0;
        destinationMaxWidth = totalScaledWidth * 0.3F;
        platformMaxWidth = scale * 2F / 16;
        arrivalMaxWidth = totalScaledWidth - destinationStart - destinationMaxWidth - platformMaxWidth;
        this.maxArrivals = maxArrivals;
        this.maxHeight = maxHeight;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.rotate90 = rotate90;
        this.defaultTextColor = textColor;
        this.rotation = rotation;
    }

    /**
     * Exposes this renderer's panel geometry so {@link RenderPIDSBase} can draw
     * component-based layout presets onto exactly the same panel rectangle as the
     * built-in elements.
     *
     * <p>{@code panelOffsetY} is the background quad's top edge in the local space:
     * this renderer translates by {@code BACKGROUND_Y} and then draws the quad at
     * {@code -1.5F}, hence {@code BACKGROUND_Y - 1.5F}.</p>
     */
    @Override
    protected com.jsblock.pids.PIDSGeometry getLayoutGeometry() {
        return new com.jsblock.pids.PIDSGeometry(startX, startY, startZ, scale,
                BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_Y - 1.5F,
                rotate90, rotation, defaultFont, defaultTextColor);
    }

    @Override
    public void render(T entity, Level world, String[] customMessages, boolean[] hideArrivals, boolean hidePlatforms, PIDSPreset preset, List<Long> filteredPlatformIds, float delta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final BlockPos pos = entity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, HorizontalDirectionalBlock.FACING);

        try {
            final Font textRenderer = Minecraft.getInstance().font;
            final PIDSPreset pidsPreset = preset == null ? DEFAULT_PRESET : preset;
            final String textFont = pidsPreset.font == null ? defaultFont : pidsPreset.font;
            final int textColor = pidsPreset.color == null ? defaultTextColor : pidsPreset.color;
            final int languageTicks = (int) Math.floor(MTRClient.getGameTick()) / SWITCH_LANGUAGE_TICKS;
            final List<ScheduleEntry> scheduleList = new ArrayList<>();

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

            final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            Collections.sort(scheduleList);

            final boolean showCarLength;
            int maxCars = 0;
            int minCars = Integer.MAX_VALUE;

            /* Find the maximum and minimum cars of a train out of the schedule list */
            for (final ScheduleEntry scheduleEntry : scheduleList) {
                final int trainCars = scheduleEntry.trainCars;
                if (trainCars > maxCars) {
                    maxCars = trainCars;
                }
                if (trainCars < minCars) {
                    minCars = trainCars;
                }
            }

            /* True if there's different cars running on the schedule list */
            showCarLength = minCars != maxCars;

            matrices.pushPose();
            matrices.translate(0.5, 0, 0.5);
            UtilitiesClient.rotateYDegrees(matrices, (rotate90 ? 90 : 0) - facing.toYRot());
            UtilitiesClient.rotateZDegrees(matrices, 180);
            UtilitiesClient.rotateXDegrees(matrices, rotation);
            matrices.translate((startX - 8) / 16, -startY / 16, (startZ - 8) / 16 - SMALL_OFFSET * 2);
            matrices.scale(1F / scale, 1F / scale, 1F / scale);

            /* Render Background */
            final VertexConsumer vertexConsumerBackground = vertexConsumers.getBuffer(MoreRenderLayers.getLight(pidsPreset.image, false));
            matrices.translate(0, BACKGROUND_Y, 0.01);
            drawTexture(matrices, vertexConsumerBackground, startX - 26F / 2, -1.5F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, facing, ARGB_WHITE, MAX_LIGHT_GLOWING);

            /* If the player is too far away from the PIDS that not even the train renders */
            if (RenderTrains.shouldNotRender(pos, Math.min(MAX_VIEW_DISTANCE, RenderTrains.maxTrainRenderDistance), rotate90 ? null : facing)) {
                matrices.popPose();
                return;
            }

            /* Render Clock */
            if(pidsPreset.showClock) {
                renderClock(matrices, textRenderer, immediate, world, textFont);
            }

            /* Render Weather icon */
            if(pidsPreset.showWeather) {
                renderWeather(matrices, vertexConsumers, world, facing);
            }

            matrices.popPose();

            /* Loop through each arrival */
            int entryIndex = 0;

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
                UtilitiesClient.rotateXDegrees(matrices, rotation);
                matrices.translate((startX - 8) / 16, -startY / 16 + i * maxHeight / maxArrivals / 16, (startZ - 8) / 16 - SMALL_OFFSET * 4);
                matrices.scale(1F / (scale / 2), 1F / (scale / 2), 1F / (scale / 2));

                if (useCustomMessage) {
                    renderTextWithOffset(matrices, textRenderer, immediate, destinationString, 0, 0, arrivalMaxWidth - platformMaxWidth, 4, textColor, MAX_LIGHT_GLOWING, HorizontalAlignment.LEFT, VerticalAlignment.TOP, false, textFont);
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

                    /* PLATFORM */
                    if (!hidePlatforms) {
                        final VertexConsumer vertexConsumerStationCircle = vertexConsumers.getBuffer(MoreRenderLayers.getLight(new ResourceLocation("mtr:textures/block/sign/circle.png"), true));
                        final long platformId = currentSchedule.currentStationIndex < route.platformIds.size() ? route.platformIds.get(currentSchedule.currentStationIndex).platformId : 0;
                        final Platform platform = ClientData.DATA_CACHE.platformIdMap.get(platformId);
                        if (platform != null) {
                            final float x = destinationStart + destinationMaxWidth;

                            /* PLATFORM CIRCLE */
                            drawTexture(matrices, vertexConsumerStationCircle, x, 0, 4, 4, facing, route.color + ARGB_BLACK, MAX_LIGHT_GLOWING);
                            matrices.pushPose();
                            matrices.translate(x + 1.95F, 2.2F, -0.05);
                            matrices.scale(0.7F, 0.7F, 0.7F);
                            renderTextWithOffset(matrices, textRenderer, immediate, platform.name, 0, 0, 4, 3, ARGB_WHITE, MAX_LIGHT_GLOWING, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, textFont);
                            matrices.popPose();
                        }
                    }

                    matrices.pushPose();
                    matrices.translate(destinationStart, 0, 0);

                    renderTextWithOffset(matrices, textRenderer, immediate, destinationString, 0, 0, 30, 5, textColor, MAX_LIGHT_GLOWING, HorizontalAlignment.LEFT, VerticalAlignment.TOP, false, textFont);
                    matrices.popPose();

                    if (arrivalText != null) {
                        final boolean isShowCar = showCarLength && (languageTicks % 6 == 0 || languageTicks % 6 == 1);
                        matrices.pushPose();
                        matrices.translate(arrivalMaxWidth - platformMaxWidth, 0, 0);

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

                /* Don't skip the current entry if the current row is hidden */
                if(hideArrivals[i]) continue;
                if(useCustomMessage && pidsPreset.customTextPushArrival) continue;
                entryIndex++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderWeather(PoseStack matrices, MultiBufferSource vertexConsumers, Level world, Direction blockFacing) {
        ResourceLocation weatherTexture = world.isThundering() ? new ResourceLocation("jsblock:textures/block/weather_thunder.png") : world.isRaining() ? new ResourceLocation("jsblock:textures/block/weather_rainy.png") : new ResourceLocation("jsblock:textures/block/weather_sunny.png");
        final VertexConsumer vertexConsumerWeather = vertexConsumers.getBuffer(MoreRenderLayers.getLight(weatherTexture, false));

        matrices.pushPose();
        matrices.translate(startX - 9F, -startY / 16, -OVERLAY_DISTANCE);
        drawTexture(matrices, vertexConsumerWeather, 0, -0.5F, 8F, 8F, blockFacing, ARGB_WHITE, MAX_LIGHT_GLOWING);
        matrices.popPose();
    }

    private void renderClock(PoseStack matrices, Font textRenderer, MultiBufferSource.BufferSource immediate, Level world, String font) {
        long time = world.getDayTime() + 6000;
        long hours = time / 1000;
        long minutes = Math.round((time - (hours * 1000)) / 16.8);
        String timeString = String.format("%02d:%02d", hours % 24, minutes % 60);
        matrices.pushPose();
        matrices.translate(BACKGROUND_WIDTH, 0, -OVERLAY_DISTANCE);
        matrices.translate(((startX - 26F) / 2), -startY / 16, 0);
        matrices.scale(1.6F, 1.6F, 1.6F);
        renderTextWithOffset(matrices, textRenderer, immediate, timeString, 0, 0, 12, 2, ARGB_WHITE, MAX_LIGHT_GLOWING, HorizontalAlignment.RIGHT, VerticalAlignment.TOP, false, font);
        matrices.popPose();
    }
}