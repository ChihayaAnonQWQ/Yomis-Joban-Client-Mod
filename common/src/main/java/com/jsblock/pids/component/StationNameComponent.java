package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSData;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import mtr.data.Station;

/**
 * Draws the name of the station the watched platform belongs to.
 *
 * <p>Ported from JCM 2.x's {@code StationNameComponent}. MTR 3 resolves the station through
 * {@code DataCache#platformIdToStation}; MTR 4 uses its own platform objects.</p>
 *
 * <p>Options: {@code prefix}, {@code use_mtr_formatting} (default {@code true}, applies
 * {@link IGui#formatStationName}), {@code halign}, {@code valign}, {@code color},
 * {@code scale}.</p>
 */
public class StationNameComponent extends PIDSComponent {

	private final String prefix;
	private final boolean useMtrFormatting;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private StationNameComponent(double x, double y, double width, double height, String prefix,
								 boolean useMtrFormatting, IGui.HorizontalAlignment horizontalAlignment,
								 IGui.VerticalAlignment verticalAlignment, int color, float scale) {
		super(x, y, width, height);
		this.prefix = prefix;
		this.useMtrFormatting = useMtrFormatting;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static StationNameComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new StationNameComponent(
				x, y, width, height,
				optString(json, "prefix", ""),
				optBoolean(json, "use_mtr_formatting", true),
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
		final Station station = PIDSData.stationOf(context.primaryPlatformId());
		final String name = station == null || station.name == null ? "" : station.name;
		if (name.isEmpty()) {
			return "";
		}
		return prefix + (useMtrFormatting ? IGui.formatStationName(name) : name);
	}
}
