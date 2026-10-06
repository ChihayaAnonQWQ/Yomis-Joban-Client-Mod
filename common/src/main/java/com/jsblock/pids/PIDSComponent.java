package com.jsblock.pids;

import com.google.gson.JsonObject;
import com.jsblock.pids.component.ArrivalCarComponent;
import com.jsblock.pids.component.ArrivalDestinationComponent;
import com.jsblock.pids.component.ArrivalETAComponent;
import com.jsblock.pids.component.ClockComponent;
import com.jsblock.pids.component.CustomTextureComponent;
import com.jsblock.pids.component.CycleComponent;
import com.jsblock.pids.component.PlatformComponent;
import com.jsblock.pids.component.StaticCustomMessageComponent;
import com.jsblock.pids.component.StationNameComponent;
import com.jsblock.pids.component.WeatherIconComponent;
import com.jsblock.pids.component.WeatherTextComponent;
import com.jsblock.screen.IDrawingJoban;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for one positioned element of a PIDS layout.
 *
 * <p>This is the MTR 3 port of JCM 2.x's
 * {@code com.lx862.jcm.mod.data.pids.preset.components.base.PIDSComponent}. JCM's version
 * draws through MTR 4's {@code GraphicsHolder}/{@code GuiDrawing} abstraction; here
 * components draw through MTR 3's {@code mtr.client.IDrawing} helpers and the raw
 * {@link PoseStack} / {@link net.minecraft.client.renderer.MultiBufferSource} pair, which
 * is what YJCM's existing PIDS renderers already use.</p>
 *
 * <p>Coordinates are expressed in <em>design units</em> declared by the preset's
 * {@code size}; {@link PIDSLayout} converts them to whatever units the caller draws in.
 * Components receive the converted rectangle in {@link #render}.</p>
 */
public abstract class PIDSComponent implements IGui {

	/**
	 * Registry of component type name to factory. Third-party code may add entries
	 * during mod init, exactly as JCM allows.
	 */
	public static final Map<String, ComponentParser> COMPONENTS = new LinkedHashMap<>();

	static {
		COMPONENTS.put("arrival_destination", ArrivalDestinationComponent::parse);
		COMPONENTS.put("arrival_eta", ArrivalETAComponent::parse);
		COMPONENTS.put("arrival_car", ArrivalCarComponent::parse);
		COMPONENTS.put("clock", ClockComponent::parse);
		COMPONENTS.put("cycle", CycleComponent::parse);
		COMPONENTS.put("custom_text", StaticCustomMessageComponent::parse);
		COMPONENTS.put("custom_texture", CustomTextureComponent::parse);
		COMPONENTS.put("platform_text", PlatformComponent::parse);
		COMPONENTS.put("station_name", StationNameComponent::parse);
		COMPONENTS.put("weather_text", WeatherTextComponent::parse);
		COMPONENTS.put("weather_icon", WeatherIconComponent::parse);
	}

	/** Left edge in design units. */
	protected final double x;
	/** Top edge in design units. */
	protected final double y;
	/** Width in design units. */
	protected final double width;
	/** Height in design units. */
	protected final double height;

	protected PIDSComponent(double x, double y, double width, double height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getWidth() {
		return width;
	}

	public double getHeight() {
		return height;
	}

	/**
	 * @return {@code false} to skip drawing this component for the given frame, e.g. when
	 * there is no arrival to show.
	 */
	public boolean canRender(PIDSContext context) {
		return true;
	}

	/**
	 * Draws the component.
	 *
	 * @param context current PIDS data
	 * @param graphics render state bundle
	 * @param x       left edge in the caller's draw units
	 * @param y       top edge in the caller's draw units
	 * @param width   width in the caller's draw units
	 * @param height  height in the caller's draw units
	 */
	public abstract void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height);

	/**
	 * Parses one entry of a preset's {@code components} array.
	 *
	 * @return the component, or {@code null} when the type is unknown or required fields
	 * are missing (the caller skips nulls rather than failing the whole preset).
	 */
	public static PIDSComponent parse(JsonObject json) {
		if (json == null || !json.has("component")) {
			return null;
		}
		final String name = json.get("component").getAsString();
		final ComponentParser parser = COMPONENTS.get(name);
		if (parser == null) {
			return null;
		}
		final double x = optDouble(json, "x", 0);
		final double y = optDouble(json, "y", 0);
		final double width = optDouble(json, "width", 0);
		final double height = optDouble(json, "height", 0);
		try {
			return parser.parse(x, y, width, height, json);
		} catch (Exception e) {
			return null;
		}
	}

	protected static double optDouble(JsonObject json, String key, double fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
	}

	protected static String optString(JsonObject json, String key, String fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
	}

	protected static boolean optBoolean(JsonObject json, String key, boolean fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
	}

	// ------------------------------------------------------------------
	// Shared drawing helpers
	// ------------------------------------------------------------------

	/**
	 * Draws left/centre/right aligned text inside the given rectangle.
	 *
	 * @param scale text scale multiplier relative to {@link PIDSGraphics#scale}
	 */
	protected static void drawText(PIDSGraphics graphics, String text, float x, float y, float width, float height,
								   IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment,
								   float scale, int color) {
		if (text == null || text.isEmpty() || width <= 0 || height <= 0) {
			return;
		}
		IDrawingJoban.drawStringWithFont(
				graphics.matrices,
				Minecraft.getInstance().font,
				graphics.immediate,
				text,
				horizontalAlignment,
				verticalAlignment,
				x, y, width, height,
				scale,
				true,
				color,
				false,
				graphics.light,
				graphics.font == null ? "mtr:mtr" : graphics.font
		);
	}

	/** Draws a flat coloured quad filling the rectangle, using the PIDS "light" render layer. */
	protected static void drawQuad(PIDSGraphics graphics, float x, float y, float width, float height, int color) {
		final VertexConsumer vertexConsumer = graphics.vertexConsumers.getBuffer(
				MoreRenderLayers.getLight(new ResourceLocation("mtr:textures/block/white.png"), false));
		IDrawing.drawTexture(graphics.matrices, vertexConsumer, x, y, 0, x + width, y + height, 0,
				0, 0, 1, 1, graphics.facing, color, graphics.light);
	}

	/** Draws a texture spanning the rectangle. */
	protected static void drawTexture(PIDSGraphics graphics, ResourceLocation texture, float x, float y,
									  float width, float height, int color, boolean translucent) {
		final VertexConsumer vertexConsumer = graphics.vertexConsumers.getBuffer(MoreRenderLayers.getLight(texture, translucent));
		IDrawing.drawTexture(graphics.matrices, vertexConsumer, x, y, 0, x + width, y + height, 0,
				0, 0, 1, 1, graphics.facing, color, graphics.light);
	}

	/** Pushes a translate/scale pair so nested drawing can use a local coordinate space. */
	protected static void pushLocalSpace(PoseStack matrices, float x, float y, float scale) {
		matrices.pushPose();
		matrices.translate(x, y, 0);
		matrices.scale(scale, scale, 1);
	}

	protected static void popLocalSpace(PoseStack matrices) {
		matrices.popPose();
	}
}
