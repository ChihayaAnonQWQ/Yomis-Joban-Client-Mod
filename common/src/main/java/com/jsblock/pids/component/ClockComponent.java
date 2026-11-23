package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import net.minecraft.world.level.Level;

/**
 * Draws the in-game clock.
 *
 * <p>Ported from JCM 2.x's {@code ClockComponent}. The time arithmetic is deliberately the
 * same as YJCM's existing {@code RenderPIDSBase#parseVariable} {@code {time}} substitution,
 * so a layout clock and a {@code {time}} placeholder always agree.</p>
 *
 * <p>Options: {@code format} (default {@code HH:mm}), {@code seconds} (boolean),
 * {@code halign}, {@code valign}, {@code color}, {@code scale}.</p>
 */
public class ClockComponent extends PIDSComponent {

	private final String format;
	private final boolean showSeconds;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private ClockComponent(double x, double y, double width, double height, String format, boolean showSeconds,
						   IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment,
						   int color, float scale) {
		super(x, y, width, height);
		this.format = format;
		this.showSeconds = showSeconds;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static ClockComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new ClockComponent(
				x, y, width, height,
				optString(json, "format", "HH:mm"),
				optBoolean(json, "seconds", false),
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
		drawText(graphics, currentTime(context.world), x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}

	/** @return the formatted in-game time. */
	public String currentTime(Level world) {
		// Minecraft day 0 starts at 06:00, hence the +6000 offset; one in-game hour is
		// 1000 ticks and one in-game minute is 1000/60 ≈ 16.667 ticks.
		final long time = world.getDayTime() + 6000;
		final long hours = time / 1000;
		final long minutes = Math.round((time - (hours * 1000)) / 16.8D);
		final long seconds = Math.round((time - (hours * 1000) - (minutes * 16.8D)) * (60D / 16.8D));

		return format
				.replace("HH", pad(hours % 24))
				.replace("mm", pad(minutes % 60))
				.replace("ss", pad(showSeconds ? Math.floorMod(seconds, 60) : 0));
	}

	private static String pad(long value) {
		return String.format("%02d", value);
	}
}
