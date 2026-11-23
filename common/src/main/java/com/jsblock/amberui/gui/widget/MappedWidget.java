package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * A wrapper around a vanilla widget, so that single widgets and {@link WidgetSet} can be used
 * interchangeably by the layout code.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.MappedWidget}.
 */
public class MappedWidget implements WidgetHost {
	private final AbstractWidget widget;

	public MappedWidget(AbstractWidget widget) {
		this.widget = widget;
	}

	public AbstractWidget getWidget() {
		return widget;
	}

	@Override
	public int getX() {
		return widget.getX();
	}

	@Override
	public int getY() {
		return widget.getY();
	}

	@Override
	public int getWidth() {
		return widget.getWidth();
	}

	@Override
	public int getHeight() {
		return widget.getHeight();
	}

	@Override
	public boolean getActive() {
		return widget.active;
	}

	@Override
	public void setX(int newX) {
		widget.setX(newX);
	}

	@Override
	public void setY(int newY) {
		widget.setY(newY);
	}

	@Override
	public void setWidth(int width) {
		widget.setWidth(width);
	}

	@Override
	public void setVisible(boolean visible) {
		// WidgetSet overrides setVisible to propagate the state to its children, vanilla widgets have no such override
		if(widget instanceof WidgetSet) {
			((WidgetSet) widget).setVisible(visible);
		} else {
			widget.visible = visible;
		}
	}

	@Override
	public void setAllX(int newX) {
		widget.setX(newX);
	}

	@Override
	public void setAllY(int newY) {
		widget.setY(newY);
	}

	/**
	 * Draw the wrapped widget. The wrapped widget is not part of the screen's render list,
	 * it is drawn by the list entry that owns it (as in JCM 2.3).
	 */
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		widget.render(guiGraphics, mouseX, mouseY, tickDelta);
	}
}
