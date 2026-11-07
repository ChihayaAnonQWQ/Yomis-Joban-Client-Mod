package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * A thin handle around a Minecraft widget, used by the list rows to place the control that
 * belongs to a row on the right-hand side.
 *
 * <p>AmberUI's original class accepted MTR 4's {@code ButtonWidgetExtension},
 * {@code TextFieldWidgetExtension}, {@code CheckboxWidgetExtension}, {@code SliderWidgetExtension}
 * and {@code ClickableWidgetExtension}. MTR 3 has none of those; every one of them is an
 * {@link AbstractWidget} here (MTR 3's own {@code WidgetBetterTextField} extends {@code EditBox},
 * {@code WidgetBetterCheckbox} extends {@code Checkbox}, and buttons are plain {@code Button}s).
 * Collapsing the type switch into one base class is the structural change the port needed; the
 * behaviour the list depends on -- x/y/width/height/active/visible -- is unchanged.</p>
 */
public class MappedWidget {
	private final AbstractWidget widget;

	public MappedWidget(Object widget) {
		if (!(widget instanceof AbstractWidget)) {
			throw new IllegalArgumentException("Widget is not an AbstractWidget: "
					+ (widget == null ? "null" : widget.getClass().getName()));
		}
		this.widget = (AbstractWidget) widget;
	}

	public AbstractWidget getWidget() {
		return widget;
	}

	public int getX() {
		return widget.getX();
	}

	public int getY() {
		return widget.getY();
	}

	public int getWidth() {
		return widget.getWidth();
	}

	public int getHeight() {
		return widget.getHeight();
	}

	public boolean getActive() {
		return widget.active;
	}

	public void setWidth(int width) {
		widget.setWidth(width);
	}

	public void setX(int newX) {
		if (widget instanceof WidgetsWrapper) {
			((WidgetsWrapper) widget).setAllX(newX);
		} else {
			widget.setX(newX);
		}
	}

	public void setY(int newY) {
		if (widget instanceof WidgetsWrapper) {
			((WidgetsWrapper) widget).setAllY(newY);
		} else {
			widget.setY(newY);
		}
	}

	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		widget.render(guiGraphics, mouseX, mouseY, tickDelta);
	}

	public void setVisible(boolean visible) {
		if (widget instanceof WidgetsWrapper) {
			((WidgetsWrapper) widget).setAllVisible(visible);
		}
		widget.visible = visible;
	}

	public void setAlpha(float alpha) {
		widget.setAlpha(alpha);
	}

	/**
	 * Adds the real screen children a row owns. A composite widget contributes its children and
	 * not itself: the children are registered with the screen so that Minecraft's own focus and
	 * keyboard routing work, while the composite is only a layout box.
	 */
	public void collectWidgets(List<AbstractWidget> out) {
		if (widget instanceof WidgetsWrapper) {
			final List<AbstractWidget> children = ((WidgetsWrapper) widget).getChildWidgets();
			if (!children.isEmpty()) {
				out.addAll(children);
				return;
			}
		}
		out.add(widget);
	}

	public static List<AbstractWidget> collect(MappedWidget widget) {
		final List<AbstractWidget> widgets = new ArrayList<>();
		if (widget != null) {
			widget.collectWidgets(widgets);
		}
		return widgets;
	}
}
