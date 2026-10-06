package com.jsblock.pids.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotates between several sub-components on a fixed tick interval.
 *
 * <p>Ported from JCM 2.x's {@code CycleComponent}. This is what lets one PIDS alternate
 * between, say, an arrivals board and a service notice without needing two blocks.</p>
 *
 * <p>Options: {@code interval} (ticks per child, default {@code 100}), {@code components}
 * (array of child components, positioned relative to this component).</p>
 */
public class CycleComponent extends PIDSComponent {

	private final List<PIDSComponent> children;
	private final int intervalTicks;

	private CycleComponent(double x, double y, double width, double height,
						   List<PIDSComponent> children, int intervalTicks) {
		super(x, y, width, height);
		this.children = children;
		this.intervalTicks = Math.max(1, intervalTicks);
	}

	public static CycleComponent parse(double x, double y, double width, double height, JsonObject json) {
		final List<PIDSComponent> children = new ArrayList<>();
		if (json.has("components") && json.get("components").isJsonArray()) {
			final JsonArray array = json.getAsJsonArray("components");
			for (JsonElement element : array) {
				if (element.isJsonObject()) {
					final PIDSComponent child = PIDSComponent.parse(element.getAsJsonObject());
					if (child != null) {
						children.add(child);
					}
				}
			}
		}
		return new CycleComponent(x, y, width, height, children, (int) optDouble(json, "interval", 100));
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return !children.isEmpty();
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		if (children.isEmpty()) {
			return;
		}
		final int index = (int) ((context.gameTick / intervalTicks) % children.size());
		final PIDSComponent child = children.get(index);
		if (!child.canRender(context)) {
			return;
		}

		// Children are positioned relative to this component's design rectangle, so map
		// design units to the caller's draw units using this component's own ratio.
		final double unitX = this.width == 0 ? 0 : width / this.width;
		final double unitY = this.height == 0 ? 0 : height / this.height;

		child.render(context, graphics,
				(float) (x + child.getX() * unitX),
				(float) (y + child.getY() * unitY),
				(float) (child.getWidth() * unitX),
				(float) (child.getHeight() * unitY));
	}
}
