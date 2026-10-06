package com.jsblock.pids;

import com.google.gson.JsonObject;
import mtr.data.IGui;

import java.util.Locale;

/** JSON helpers for parsing the alignment fields shared by most PIDS components. */
public final class PIDSAlign {

	private PIDSAlign() {
	}

	/** Accepts {@code left}, {@code center}/{@code centre}/{@code middle}, {@code right}. */
	public static IGui.HorizontalAlignment horizontal(JsonObject json, String key, IGui.HorizontalAlignment fallback) {
		switch (PIDSComponent.optString(json, key, "").toLowerCase(Locale.ROOT)) {
			case "left":
				return IGui.HorizontalAlignment.LEFT;
			case "center":
			case "centre":
			case "middle":
				return IGui.HorizontalAlignment.CENTER;
			case "right":
				return IGui.HorizontalAlignment.RIGHT;
			default:
				return fallback;
		}
	}

	/** Accepts {@code top}, {@code center}/{@code centre}/{@code middle}, {@code bottom}. */
	public static IGui.VerticalAlignment vertical(JsonObject json, String key, IGui.VerticalAlignment fallback) {
		switch (PIDSComponent.optString(json, key, "").toLowerCase(Locale.ROOT)) {
			case "top":
				return IGui.VerticalAlignment.TOP;
			case "center":
			case "centre":
			case "middle":
				return IGui.VerticalAlignment.CENTER;
			case "bottom":
				return IGui.VerticalAlignment.BOTTOM;
			default:
				return fallback;
		}
	}

	/** Parses an {@code RRGGBB} or {@code AARRGGBB} hex colour, returning {@code fallback} on failure. */
	public static int color(JsonObject json, String key, int fallback) {
		final String raw = PIDSComponent.optString(json, key, "");
		if (raw.isEmpty()) {
			return fallback;
		}
		try {
			final String cleaned = raw.startsWith("#") ? raw.substring(1) : raw;
			final long value = Long.parseLong(cleaned, 16);
			return cleaned.length() <= 6 ? 0xFF000000 | (int) value : (int) value;
		} catch (Exception e) {
			return fallback;
		}
	}
}
