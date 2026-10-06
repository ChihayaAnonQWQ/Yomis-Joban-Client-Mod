package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Draws a weather icon chosen by the world's current weather.
 *
 * <p>Ported from JCM 2.x's {@code WeatherIconComponent}. Unlike JCM, MTR 3 ships no
 * weather artwork that YJCM may reuse, so all three textures must be supplied by the
 * preset. A missing texture simply skips that state instead of drawing garbage.</p>
 *
 * <p>Options: {@code sunny_texture}, {@code raining_texture}, {@code thundering_texture},
 * {@code tint} (hex, default opaque white), {@code translucent} (boolean).</p>
 */
public class WeatherIconComponent extends PIDSComponent {

	private final ResourceLocation sunnyTexture;
	private final ResourceLocation rainingTexture;
	private final ResourceLocation thunderingTexture;
	private final int tint;
	private final boolean translucent;

	private WeatherIconComponent(double x, double y, double width, double height,
								 ResourceLocation sunnyTexture, ResourceLocation rainingTexture,
								 ResourceLocation thunderingTexture, int tint, boolean translucent) {
		super(x, y, width, height);
		this.sunnyTexture = sunnyTexture;
		this.rainingTexture = rainingTexture;
		this.thunderingTexture = thunderingTexture;
		this.tint = tint;
		this.translucent = translucent;
	}

	public static WeatherIconComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new WeatherIconComponent(
				x, y, width, height,
				texture(json, "sunny_texture"),
				texture(json, "raining_texture"),
				texture(json, "thundering_texture"),
				PIDSAlign.color(json, "tint", IGui.ARGB_WHITE),
				optBoolean(json, "translucent", true)
		);
	}

	private static ResourceLocation texture(JsonObject json, String key) {
		final String raw = optString(json, key, "");
		if (raw.isEmpty()) {
			return null;
		}
		try {
			return new ResourceLocation(raw);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return context.world != null && currentTexture(context.world) != null;
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		final ResourceLocation texture = currentTexture(context.world);
		if (texture != null) {
			drawTexture(graphics, texture, x, y, width, height, tint, translucent);
		}
	}

	private ResourceLocation currentTexture(Level world) {
		if (world.isThundering()) {
			return thunderingTexture;
		}
		return world.isRaining() ? rainingTexture : sunnyTexture;
	}
}
