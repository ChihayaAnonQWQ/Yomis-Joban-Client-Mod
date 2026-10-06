package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import net.minecraft.world.level.Level;

/**
 * Draws the current weather as text.
 *
 * <p>Ported from JCM 2.x's {@code WeatherTextComponent}. YJCM already exposes the same
 * three states through {@code RenderPIDSBase#parseVariable}'s {@code {weather}} and
 * {@code {weatherChin}} placeholders; this component keeps the wording consistent and lets
 * a preset override any of the three strings.</p>
 *
 * <p>Options: {@code sunny}, {@code raining}, {@code thundering} (override strings),
 * {@code halign}, {@code valign}, {@code color}, {@code scale}.</p>
 */
public class WeatherTextComponent extends PIDSComponent {

	private final String sunny;
	private final String raining;
	private final String thundering;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private WeatherTextComponent(double x, double y, double width, double height,
								 String sunny, String raining, String thundering,
								 IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment,
								 int color, float scale) {
		super(x, y, width, height);
		this.sunny = sunny;
		this.raining = raining;
		this.thundering = thundering;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static WeatherTextComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new WeatherTextComponent(
				x, y, width, height,
				optString(json, "sunny", "Sunny"),
				optString(json, "raining", "Raining"),
				optString(json, "thundering", "Thundering"),
				PIDSAlign.horizontal(json, "halign", IGui.HorizontalAlignment.CENTER),
				PIDSAlign.vertical(json, "valign", IGui.VerticalAlignment.CENTER),
				PIDSAlign.color(json, "color", IGui.ARGB_WHITE),
				(float) optDouble(json, "scale", 1)
		);
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return context.world != null;
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		drawText(graphics, describe(context.world), x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}

	/** @return the weather wording for the given world. */
	public String describe(Level world) {
		if (world.isThundering()) {
			return thundering;
		}
		return world.isRaining() ? raining : sunny;
	}
}
