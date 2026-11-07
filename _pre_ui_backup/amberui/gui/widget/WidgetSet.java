package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds widgets in a grid of rows, sharing the available width equally.
 */
public class WidgetSet extends AbstractWidget implements WidgetsWrapper {
	private final List<List<MappedWidget>> widgetRows = new ArrayList<>();
	private final int maxWidgetHeight;
	public final int widgetXMargin;

	public WidgetSet(int maxWidgetHeight, int widgetXMargin) {
		super(0, 0, 0, 0, Component.empty());
		this.maxWidgetHeight = maxWidgetHeight;
		this.widgetXMargin = widgetXMargin;
	}

	public WidgetSet(int maxWidgetHeight) {
		this(maxWidgetHeight, 10);
	}

	public void setXYSize(int x, int y, int width, int height) {
		setX(x);
		setY(y);
		this.width = width;
		this.height = height;
		positionWidgets();
	}

	public void addWidget(Button widget) {
		addWidget(new MappedWidget(widget));
	}

	public void addWidget(MappedWidget widget) {
		if (widgetRows.isEmpty()) {
			insertRow();
		}
		widgetRows.get(widgetRows.size() - 1).add(widget);
	}

	public void insertRow() {
		widgetRows.add(new ArrayList<>());
	}

	public void reset() {
		widgetRows.clear();
	}

	private void positionWidgets() {
		final int x = getX();
		final int y = getY();
		final int availableWidth = getWidth() + widgetXMargin;
		final int widgetHeight = Math.min(maxWidgetHeight,
				(int) (maxWidgetHeight * ((double) getHeight() / (widgetRows.size() * maxWidgetHeight))));

		for (int i = 0; i < widgetRows.size(); i++) {
			final List<MappedWidget> rowWidgets = widgetRows.get(i);
			if (rowWidgets.isEmpty()) {
				continue;
			}
			final double perWidgetWidth = ((double) availableWidth / rowWidgets.size()) - widgetXMargin;
			final int rowY = y + (i * widgetHeight);

			for (int j = 0; j < rowWidgets.size(); j++) {
				final MappedWidget widget = rowWidgets.get(j);
				final double widgetStartX = x + (j * perWidgetWidth) + (j * widgetXMargin);
				widget.setX((int) Math.round(widgetStartX));
				widget.setY(rowY);
				widget.setWidth((int) Math.round(perWidgetWidth));
			}
		}
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		for (final List<MappedWidget> row : widgetRows) {
			for (final MappedWidget widget : row) {
				widget.render(guiGraphics, mouseX, mouseY, delta);
			}
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
		final List<AbstractWidget> widgets = new ArrayList<>();
		for (final List<MappedWidget> row : widgetRows) {
			for (final MappedWidget widget : row) {
				widget.collectWidgets(widgets);
			}
		}
		return widgets;
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
	}
}
