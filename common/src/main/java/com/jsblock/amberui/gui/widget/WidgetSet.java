package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a set of widget within a defined area.
 * Able to tile the widgets horizontally and add new rows like a grid/table.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.WidgetSet}.<br><br>
 * This set only does layout: the wrapped widgets are registered with the screen themselves,
 * so they are rendered and receive input like any other widget.
 */
public class WidgetSet extends AbstractWidget implements WidgetHost {
	private final List<List<MappedWidget>> widgetRows = new ArrayList<>();
	private final int maxWidgetHeight;
	public final int widgetXMargin;

	public WidgetSet(int maxWidgetHeight, int widgetXMargin) {
		super(0, 0, 0, 0, CommonComponents.EMPTY);
		this.maxWidgetHeight = maxWidgetHeight;
		this.widgetXMargin = widgetXMargin;
	}

	public WidgetSet(int maxWidgetHeight) {
		this(maxWidgetHeight, 10);
	}

	/**
	 * Set the position and size of the widget and reposition all added widget.
	 * Make sure to call this after all widget has been added.
	 */
	public void setXYSize(int x, int y, int width, int height) {
		// Assign the fields directly: the overridden setX()/setY() re-layout the children, which
		// would clamp their width against the not-yet-updated width of the set.
		super.setX(x);
		super.setY(y);
		super.setWidth(width);
		this.height = height;
		positionWidgets();
	}

	public void addWidget(AbstractWidget widget) {
		addWidget(new MappedWidget(widget));
	}

	public void addWidget(MappedWidget widget) {
		if(widgetRows.isEmpty()) {
			insertRow();
		}

		final int lastRowIndex = widgetRows.size() - 1;
		widgetRows.get(lastRowIndex).add(widget);
	}

	public void insertRow() {
		widgetRows.add(new ArrayList<>());
	}

	public void reset() {
		this.widgetRows.clear();
	}

	protected List<MappedWidget> getAllWidgets() {
		final List<MappedWidget> list = new ArrayList<>();
		for(List<MappedWidget> row : widgetRows) {
			list.addAll(row);
		}
		return list;
	}

	protected void positionWidgets() {
		if(widgetRows.isEmpty()) return;

		final int x = getX();
		final int y = getY();
		final int width = getWidth() + widgetXMargin;
		final int widgetHeight = Math.min(maxWidgetHeight, (int) (maxWidgetHeight * ((double) getHeight() / (widgetRows.size() * maxWidgetHeight))));

		for(int i = 0; i < widgetRows.size(); i++) {
			final List<MappedWidget> rowWidgets = widgetRows.get(i);
			if(rowWidgets.isEmpty()) continue;

			final double perWidgetWidth = ((double) width / rowWidgets.size()) - widgetXMargin;
			final int rowY = y + (i * widgetHeight);

			for(int j = 0; j < rowWidgets.size(); j++) {
				final MappedWidget widget = rowWidgets.get(j);
				final double widgetStartX = x + (j * perWidgetWidth) + (j * widgetXMargin);

				widget.setX((int) Math.round(widgetStartX));
				widget.setY(rowY);
				widget.setWidth((int) Math.round(perWidgetWidth));
			}
		}
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		for(MappedWidget widget : getAllWidgets()) {
			widget.render(guiGraphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// The widgets inside the set are registered with the screen themselves
		return false;
	}

	@Override
	public void setVisible(boolean visible) {
		for(MappedWidget widget : getAllWidgets()) {
			widget.setVisible(visible);
		}
		this.visible = visible;
	}

	@Override
	public boolean getActive() {
		return true;
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
	public void setX(int x) {
		super.setX(x);
		positionWidgets();
	}

	@Override
	public void setY(int y) {
		super.setY(y);
		positionWidgets();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
}
