package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import mtr.data.ScheduleEntry;
import mtr.mappings.Text;

/**
 * Draws how many cars the arriving train has.
 *
 * <p>Ported from JCM 2.x's {@code ArrivalCarComponent}. MTR 3's {@link ScheduleEntry}
 * exposes the car count directly as {@code trainCars}, so no extra lookup is needed.</p>
 *
 * <p>Options: {@code row} (default {@code 0}), {@code only_when_varying} (suppress the text
 * unless the upcoming arrivals differ in length, default {@code false}), {@code halign},
 * {@code valign}, {@code color}, {@code scale}.</p>
 */
public class ArrivalCarComponent extends PIDSComponent {

	private final int row;
	private final boolean onlyWhenVarying;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private ArrivalCarComponent(double x, double y, double width, double height, int row, boolean onlyWhenVarying,
								IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment,
								int color, float scale) {
		super(x, y, width, height);
		this.row = row;
		this.onlyWhenVarying = onlyWhenVarying;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static ArrivalCarComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new ArrivalCarComponent(
				x, y, width, height,
				(int) optDouble(json, "row", 0),
				optBoolean(json, "only_when_varying", false),
				PIDSAlign.horizontal(json, "halign", IGui.HorizontalAlignment.RIGHT),
				PIDSAlign.vertical(json, "valign", IGui.VerticalAlignment.CENTER),
				PIDSAlign.color(json, "color", IGui.ARGB_WHITE),
				(float) optDouble(json, "scale", 1)
		);
	}

	@Override
	public boolean canRender(PIDSContext context) {
		final ScheduleEntry entry = entry(context);
		if (entry == null) {
			return false;
		}
		return !onlyWhenVarying || carCountsVary(context);
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		final ScheduleEntry entry = entry(context);
		if (entry == null) {
			return;
		}
		drawText(graphics, describe(entry.trainCars), x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}

	/** @return the localised "N cars" wording for a train length. */
	public static String describe(int trainCars) {
		return Text.translatable("gui.mtr.arrival_car", trainCars).getString();
	}

	private static boolean carCountsVary(PIDSContext context) {
		int first = -1;
		for (ScheduleEntry entry : context.scheduleList) {
			if (first < 0) {
				first = entry.trainCars;
			} else if (entry.trainCars != first) {
				return true;
			}
		}
		return false;
	}

	private ScheduleEntry entry(PIDSContext context) {
		return context.isRowHidden(row) ? null : context.arrival(row);
	}
}
