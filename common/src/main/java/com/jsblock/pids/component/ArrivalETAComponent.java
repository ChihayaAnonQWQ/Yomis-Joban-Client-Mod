package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSData;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import mtr.data.ScheduleEntry;
import mtr.mappings.Text;

/**
 * Draws the estimated time until one arrival reaches its next stop.
 *
 * <p>Ported from JCM 2.x's {@code ArrivalETAComponent}. The wording reuses the very same
 * MTR / YJCM translation keys that YJCM's hand-written {@code RenderLCDPIDS} and
 * {@code RenderRVPIDS} already use, so a component-based layout is indistinguishable from
 * the built-in renderers.</p>
 *
 * <p>Options: {@code row} (default {@code 0}), {@code halign}, {@code valign},
 * {@code color}, {@code scale}.</p>
 */
public class ArrivalETAComponent extends PIDSComponent {

	private final int row;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private ArrivalETAComponent(double x, double y, double width, double height, int row,
								IGui.HorizontalAlignment horizontalAlignment, IGui.VerticalAlignment verticalAlignment,
								int color, float scale) {
		super(x, y, width, height);
		this.row = row;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static ArrivalETAComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new ArrivalETAComponent(
				x, y, width, height,
				(int) optDouble(json, "row", 0),
				PIDSAlign.horizontal(json, "halign", IGui.HorizontalAlignment.RIGHT),
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
		drawText(graphics, describe(entry, PIDSData.destination(entry)), x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}

	/**
	 * Builds the ETA wording for an arrival. CJK destinations use MTR's CJK-specific keys
	 * so the unit reads naturally in Chinese, matching {@code RenderLCDPIDS}.
	 *
	 * @param entry                    the arrival to describe
	 * @param destinationForLanguage   the destination text shown alongside this ETA; it
	 *                                 decides whether CJK units are used. MTR 3's
	 *                                 {@link ScheduleEntry} carries no destination, so the
	 *                                 caller resolves it through {@link PIDSData}.
	 */
	public static String describe(ScheduleEntry entry, String destinationForLanguage) {
		final int seconds = (int) Math.round((entry.arrivalMillis - System.currentTimeMillis()) / 1000.0D);
		final boolean isCjk = IGui.isCjk(destinationForLanguage == null ? "" : destinationForLanguage);
		if (seconds >= 60) {
			return Text.translatable(isCjk ? "gui.mtr.arrival_min_cjk" : "gui.mtr.arrival_min", seconds / 60).getString();
		}
		if (seconds > 0) {
			return Text.translatable(isCjk ? "gui.mtr.arrival_sec_cjk" : "gui.mtr.arrival_sec", seconds).getString();
		}
		return Text.translatable("gui.jsblock.train_arrived").getString();
	}

	private ScheduleEntry entry(PIDSContext context) {
		return context.isRowHidden(row) ? null : context.arrival(row);
	}
}
