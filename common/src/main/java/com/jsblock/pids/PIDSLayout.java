package com.jsblock.pids;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mtr.data.IGui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A complete PIDS screen layout: a list of positioned {@link PIDSComponent}s plus the design
 * canvas they were authored against.
 *
 * <p>This is the MTR 3 port of JCM 2.x's JSON-driven PIDS presets
 * ({@code JsonPIDSPreset} + {@code PIDSPresetBase}). YJCM's built-in renderers hard-code the
 * position of every element (destination, platform circle, ETA, car count, clock); a layout
 * preset instead describes them declaratively, so a resource pack can restyle a PIDS without
 * touching Java.</p>
 *
 * <h2>Preset JSON</h2>
 * <pre>{@code
 * {
 *   "id": "example",
 *   "background": "jsblock:textures/block/pids_rv_screen.png",
 *   "size": [100, 50],
 *   "color": "FFFFFF",
 *   "components": [
 *     { "component": "station_name",        "x": 2,  "y": 2,  "width": 40, "height": 8 },
 *     { "component": "clock",               "x": 80, "y": 2,  "width": 18, "height": 8 },
 *     { "component": "arrival_destination", "x": 2,  "y": 14, "width": 60, "height": 8, "row": 0 },
 *     { "component": "arrival_eta",         "x": 80, "y": 14, "width": 18, "height": 8, "row": 0 }
 *   ]
 * }
 * }</pre>
 *
 * <p>Coordinates are in <em>design units</em>; {@code size} declares the canvas. At render time
 * the whole canvas is scaled uniformly onto the panel, so one preset behaves identically on a
 * small track-side PIDS and on a large projector.</p>
 */
public class PIDSLayout implements IGui {

	/** Design canvas width used when a preset omits {@code size}. */
	public static final double DEFAULT_WIDTH = 100;
	/** Design canvas height used when a preset omits {@code size}. */
	public static final double DEFAULT_HEIGHT = 50;

	private final double designWidth;
	private final double designHeight;
	private final List<PIDSComponent> components;

	private PIDSLayout(double designWidth, double designHeight, List<PIDSComponent> components) {
		this.designWidth = designWidth;
		this.designHeight = designHeight;
		this.components = components;
	}

	/**
	 * Parses the layout part of a preset object.
	 *
	 * @return the layout, or {@code null} when the preset declares no usable components —
	 * callers then fall back to the built-in renderer.
	 */
	public static PIDSLayout fromJson(JsonObject json) {
		if (json == null || !json.has("components") || !json.get("components").isJsonArray()) {
			return null;
		}

		double designWidth = DEFAULT_WIDTH;
		double designHeight = DEFAULT_HEIGHT;
		if (json.has("size") && json.get("size").isJsonArray()) {
			final JsonArray size = json.getAsJsonArray("size");
			if (size.size() >= 1 && size.get(0).isJsonPrimitive()) {
				designWidth = size.get(0).getAsDouble();
			}
			if (size.size() >= 2 && size.get(1).isJsonPrimitive()) {
				designHeight = size.get(1).getAsDouble();
			}
		}
		if (designWidth <= 0) {
			designWidth = DEFAULT_WIDTH;
		}
		if (designHeight <= 0) {
			designHeight = DEFAULT_HEIGHT;
		}

		final List<PIDSComponent> components = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("components")) {
			if (!element.isJsonObject()) {
				continue;
			}
			final PIDSComponent component = PIDSComponent.parse(element.getAsJsonObject());
			if (component != null) {
				components.add(component);
			}
		}
		return components.isEmpty() ? null : new PIDSLayout(designWidth, designHeight, components);
	}

	public boolean isEmpty() {
		return components.isEmpty();
	}

	public double getDesignWidth() {
		return designWidth;
	}

	public double getDesignHeight() {
		return designHeight;
	}

	/** @return an unmodifiable view of the parsed components, for diagnostics. */
	public List<PIDSComponent> getComponents() {
		return Collections.unmodifiableList(components);
	}

	/** @return the component type names used by this layout, in declaration order. */
	public List<String> describeComponents() {
		final List<String> names = new ArrayList<>();
		for (PIDSComponent component : components) {
			names.add(component.getClass().getSimpleName());
		}
		return names;
	}

	/**
	 * Draws the whole layout.
	 *
	 * @param context     current PIDS data
	 * @param graphics    render state; {@code matrices} must already be transformed into the
	 *                    space the panel rectangle is expressed in
	 * @param originX     left edge of the panel in that space
	 * @param originY     top edge of the panel in that space
	 * @param panelWidth  panel width in that space
	 * @param panelHeight panel height in that space (used only for aspect checks)
	 */
	public void render(PIDSContext context, PIDSGraphics graphics,
					   float originX, float originY, float panelWidth, float panelHeight) {
		if (components.isEmpty() || panelWidth <= 0) {
			return;
		}

		// Uniform scale: one design unit maps to the same length on both axes, so presets
		// keep their proportions on any panel size.
		final float unit = (float) (panelWidth / designWidth);

		for (PIDSComponent component : components) {
			if (!component.canRender(context)) {
				continue;
			}
			component.render(context, graphics,
					(float) (originX + component.getX() * unit),
					(float) (originY + component.getY() * unit),
					(float) (component.getWidth() * unit),
					(float) (component.getHeight() * unit));
		}
	}
}
