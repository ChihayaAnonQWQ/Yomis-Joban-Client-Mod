package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSData;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import mtr.data.ScheduleEntry;

/**
 * Draws the destination of one arrival row.
 *
 * <p>Ported from JCM 2.x's {@code DestinationMessageComponent}. This is the clearest example
 * of the MTR 4 → MTR 3 data gap: MTR 4 hands the component a ready-made
 * {@code ArrivalResponse}, whereas here the destination has to be resolved from the
 * entry's {@code routeId} and {@code currentStationIndex} through {@link PIDSData#destination}.</p>
 *
 * <p>Options: {@code row} (default {@code 0}), {@code show_route_name} (prepends the route
 * name, default {@code false}), {@code halign}, {@code valign}, {@code color},
 * {@code scale}.</p>
 */
public class ArrivalDestinationComponent extends PIDSComponent {

	private final int row;
	private final boolean showRouteName;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private ArrivalDestinationComponent(double x, double y, double width, double height, int row,
										boolean showRouteName, IGui.HorizontalAlignment horizontalAlignment,
										IGui.VerticalAlignment verticalAlignment, int color, float scale) {
		super(x, y, width, height);
		this.row = row;
		this.showRouteName = showRouteName;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static ArrivalDestinationComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new ArrivalDestinationComponent(
				x, y, width, height,
				(int) optDouble(json, "row", 0),
				optBoolean(json, "show_route_name", false),
				PIDSAlign.horizontal(json, "halign", IGui.HorizontalAlignment.LEFT),
				PIDSAlign.vertical(json, "valign", IGui.VerticalAlignment.CENTER),
				PIDSAlign.color(json, "color", IGui.ARGB_WHITE),
				(float) optDouble(json, "scale", 1)
		);
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return entry(context) != null;
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		final ScheduleEntry entry = entry(context);
		if (entry == null) {
			return;
		}
		final String destination = PIDSData.destination(entry);
		final String text = showRouteName
				? PIDSData.routeName(entry) + " " + destination
				: destination;
		drawText(graphics, text, x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}

	private ScheduleEntry entry(PIDSContext context) {
		return context.isRowHidden(row) ? null : context.arrival(row);
	}
}
