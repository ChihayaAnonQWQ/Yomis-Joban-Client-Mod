package com.jsblock.pids;

import com.google.gson.JsonObject;

/**
 * Factory for one PIDS component type, registered in
 * {@link PIDSComponent#COMPONENTS}.
 *
 * <p>Mirrors {@code com.lx862.jcm.mod.data.pids.preset.components.base.ComponentParser}
 * from JCM 2.x, minus the MTR 4 mapping types.</p>
 */
@FunctionalInterface
public interface ComponentParser {
	/**
	 * @param x      left edge in design units
	 * @param y      top edge in design units
	 * @param width  width in design units
	 * @param height height in design units
	 * @param json   the raw component object, for type-specific options
	 * @return the parsed component, or {@code null} when the options are unusable
	 */
	PIDSComponent parse(double x, double y, double width, double height, JsonObject json);
}
