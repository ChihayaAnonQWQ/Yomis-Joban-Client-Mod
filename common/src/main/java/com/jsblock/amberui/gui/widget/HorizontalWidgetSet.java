package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a set of widgets in one row, tiled horizontally with a fixed margin.
 *
 * <p>Unlike {@link WidgetSet}, the width of each child is not redistributed: the caller (or
 * {@link #computeWidth()}) decides how wide the row is, and children keep their own widths.</p>
 */
public class HorizontalWidgetSet extends AbstractWidget implements WidgetsWrapper {
	public final int widgetXMargin;
	private final List<MappedWidget> widgets = new ArrayList<>();

	public HorizontalWidgetSet(int widgetXMargin) {
		super(0, 0, 0, 20, Component.empty());
		this.widgetXMargin = widgetXMargin;
	}

	public HorizontalWidgetSet() {
		this(5);
	}

	public void setXYSize(int x, int y, int width, int height) {
		setX(x);
		setY(y);
		this.width = width <= 0 ? computeWidth() : width;
		this.height = height;
		positionWidgets();
	}

	public int computeWidth() {
		int total = 0;
		for (final MappedWidget widget : widgets) {
			total += widget.getWidth() + widgetXMargin;
		}
		return total > widgetXMargin ? total - widgetXMargin : total;
	}

	public void addWidget(MappedWidget widget) {
		widgets.add(widget);
	}

	public void positionWidgets() {
		int accX = 0;
		for (final MappedWidget widget : widgets) {
			widget.setX(getX() + accX);
			widget.setY(getY());
			final int clampedWidth = Math.min(getWidth() - accX, widget.getWidth());
			widget.setWidth(Math.max(0, clampedWidth));
			accX += widget.getWidth() + widgetXMargin;
		}
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		for (final MappedWidget widget : widgets) {
			widget.render(guiGraphics, mouseX, mouseY, delta);
		}
	}

	@Override
	public void setAllX(int newX) {
		setX(newX);
		positionWidgets();
	}

	@Override
	public void setAllY(int newY) {
		setY(newY);
		positionWidgets();
	}

	@Override
	public List<AbstractWidget> getChildWidgets() {
		final List<AbstractWidget> children = new ArrayList<>();
		for (final MappedWidget widget : widgets) {
			widget.collectWidgets(children);
		}
		return children;
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
	}
}
