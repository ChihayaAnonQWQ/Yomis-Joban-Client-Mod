package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Collections;
import java.util.List;

/**
 * Implemented by widgets that position a group of child widgets themselves.
 *
 * <p>MTR 3 has no {@code ClickableWidgetExtension} / {@code MappedWidget} layer: every widget is
 * a plain {@link AbstractWidget}. This interface is the part of the MTR 4 AmberUI design that is
 * still needed, namely a way for a list row to move or hide a whole group at once. Widgets that
 * are not groups simply do not implement it.</p>
 */
public interface WidgetsWrapper {
	void setAllX(int newX);

	void setAllY(int newY);

	default List<AbstractWidget> getChildWidgets() {
		return Collections.emptyList();
	}

	default void setAllVisible(boolean visible) {
		for (final AbstractWidget widget : getChildWidgets()) {
			widget.visible = visible;
		}
	}
}
