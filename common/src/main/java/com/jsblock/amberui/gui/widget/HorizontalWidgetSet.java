package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a set of widget within a defined area.
 * Able to tile the widgets horizontally.<br>
 * Unlike {@link WidgetSet}, this widget does not automatically distribute the widget evenly
 * and you can control the width of each element.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.HorizontalWidgetSet}.
 */
public class HorizontalWidgetSet extends WidgetSet {
	public final int widgetXMargin;
	private final List<MappedWidget> widgets = new ArrayList<>();

	public HorizontalWidgetSet(int widgetXMargin) {
		super(20, widgetXMargin);
		this.widgetXMargin = widgetXMargin;
	}

	public HorizontalWidgetSet() {
		this(5);
	}

	@Override
	public void addWidget(MappedWidget widget) {
		widgets.add(widget);
	}

	@Override
	public void addWidget(AbstractWidget widget) {
		addWidget(new MappedWidget(widget));
	}

	@Override
	public void reset() {
		widgets.clear();
	}

	@Override
	public int getWidth() {
		int total = 0;

		for(MappedWidget mappedWidget : widgets) {
			total += mappedWidget.getWidth();
			total += widgetXMargin;
		}

		if(total > 1) {
			total -= widgetXMargin;
		}

		return total;
	}

	@Override
	protected List<MappedWidget> getAllWidgets() {
		return widgets;
	}

	@Override
	protected void positionWidgets() {
		int accX = 0;

		for(MappedWidget widget : widgets) {
			widget.setX(getX() + accX);
			widget.setY(getY());
			final int clampedWidth = Math.min(getSetWidth() - accX, widget.getWidth());
			widget.setWidth(clampedWidth);
			accX += clampedWidth + widgetXMargin;
		}
	}

	private int getSetWidth() {
		return super.getWidth();
	}
}
