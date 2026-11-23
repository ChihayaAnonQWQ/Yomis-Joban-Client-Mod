package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Anything that can be placed, sized and shown/hidden by the list layout, be it a single widget
 * or a group of widgets ({@link WidgetSet}).
 *
 * <p>Introduced by this port rather than ported: JCM 2.3's list code takes {@code MappedWidget} and
 * {@code WidgetSet} directly, with the two used in different places. This interface is the seam that
 * lets a row hold either, and it is what a new widget type implements to become placeable. The rest
 * of the package is JCM 2.3 code under its MIT licence (see LICENSE_JCM).</p>
 */
public interface WidgetHost {
	int getX();

	int getY();

	int getWidth();

	int getHeight();

	void setX(int x);

	void setY(int y);

	void setWidth(int width);

	void setVisible(boolean visible);

	boolean getActive();

	void setAllX(int x);

	void setAllY(int y);

	void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta);
}
