package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSData;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import mtr.data.Platform;

/**
 * Draws the name of the platform this PIDS watches.
 *
 * <p>Ported from JCM 2.x's {@code PlatformComponent}. On MTR 3 the name comes from
 * {@link Platform#name} rather than MTR 4's {@code Platform.getName()}.</p>
 *
 * <p>Options: {@code prefix} (default empty), {@code fallback} (shown when the platform has
 * no name, default empty), {@code halign}, {@code valign}, {@code color}, {@code scale}.</p>
 */
public class PlatformComponent extends PIDSComponent {

	private final String prefix;
	private final String fallback;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private PlatformComponent(double x, double y, double width, double height, String prefix, String fallback,
							  IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment,
							  int color, float scale) {
		super(x, y, width, height);
		this.prefix = prefix;
		this.fallback = fallback;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static PlatformComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new PlatformComponent(
				x, y, width, height,
				optString(json, "prefix", ""),
				optString(json, "fallback", ""),
				PIDSAlign.horizontal(json, "halign", IGui.HorizontalAlignment.LEFT),
				PIDSAlign.vertical(json, "valign", IGui.VerticalAlignment.CENTER),
				PIDSAlign.color(json, "color", IGui.ARGB_WHITE),
				(float) optDouble(json, "scale", 1)
		);
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return !describe(context).isEmpty();
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		drawText(graphics, describe(context), x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}

	private String describe(PIDSContext context) {
		final long platformId = context.primaryPlatformId();
		final Platform platform = PIDSData.platform(platformId);
		final String name = platform == null || platform.name == null ? "" : platform.name;
		if (name.isEmpty()) {
			return prefix + fallback;
		}
		return prefix + name;
	}
}
