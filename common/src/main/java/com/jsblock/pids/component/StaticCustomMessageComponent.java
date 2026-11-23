package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;
import com.jsblock.render.RenderPIDSBase;
import mtr.data.IGui;

/**
 * Draws a fixed piece of text, with the same {@code {time}} / {@code {day}} / {@code {weather}}
 * / {@code {time_period}} / {@code {weatherChin}} / {@code {worldPlayer}} placeholders that
 * YJCM's per-row custom messages already support.
 *
 * <p>Ported from JCM 2.x's {@code StaticCustomMessageComponent}. Substitution is delegated to
 * {@link RenderPIDSBase#parseVariable} so a layout's static text and a row's custom message
 * can never disagree.</p>
 *
 * <p>Options: {@code text} (required), {@code use_variables} (default {@code true}),
 * {@code halign}, {@code valign}, {@code color}, {@code scale}.</p>
 */
public class StaticCustomMessageComponent extends PIDSComponent {

	private final String text;
	private final boolean useVariables;
	private final IGui.HorizontalAlignment horizontalAlignment;
	private final IGui.VerticalAlignment verticalAlignment;
	private final int color;
	private final float scale;

	private StaticCustomMessageComponent(double x, double y, double width, double height, String text,
										 boolean useVariables, IGui.HorizontalAlignment horizontalAlignment,
										 IGui.VerticalAlignment verticalAlignment, int color, float scale) {
		super(x, y, width, height);
		this.text = text;
		this.useVariables = useVariables;
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.color = color;
		this.scale = scale;
	}

	public static StaticCustomMessageComponent parse(double x, double y, double width, double height, JsonObject json) {
		return new StaticCustomMessageComponent(
				x, y, width, height,
				optString(json, "text", ""),
				optBoolean(json, "use_variables", true),
				PIDSAlign.horizontal(json, "halign", IGui.HorizontalAlignment.LEFT),
				PIDSAlign.vertical(json, "valign", IGui.VerticalAlignment.CENTER),
				PIDSAlign.color(json, "color", IGui.ARGB_WHITE),
				(float) optDouble(json, "scale", 1)
		);
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return !text.isEmpty();
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		final String resolved = useVariables && context.world != null
				? RenderPIDSBase.parseVariable(text, context.world)
				: text;
		drawText(graphics, resolved, x, y, width, height,
				horizontalAlignment, verticalAlignment, graphics.scale * scale, color);
	}
}
