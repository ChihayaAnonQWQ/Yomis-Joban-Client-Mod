package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Represent a row in {@link ListViewWidget}.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.AbstractListItem}.
 */
public abstract class AbstractListItem implements GuiHelper {
	private double hoverOpacity = 0;

	public void draw(GuiGraphics guiGraphics, int entryX, int entryY, int width, int height, int mouseX, int mouseY, boolean widgetVisible, boolean rowInSight, double elapsed, float tickDelta) {
		if(rowInSight) {
			drawBackground(guiGraphics, entryX, entryY, width, mouseX, mouseY, elapsed, tickDelta);
		}
	}

	public abstract boolean matchQuery(String searchTerm);

	public abstract void positionChanged(int entryX, int entryY);

	public abstract void hidden();

	public abstract void shown();

	public abstract int getHeight();

	protected void drawBackground(GuiGraphics guiGraphics, int entryX, int entryY, int width, int mouseX, int mouseY, double elapsed, float tickDelta) {
		final double highlightFadeSpeed = (tickDelta / 4);
		final boolean entryHovered = inRectangle(mouseX, mouseY, entryX, entryY, width, getHeight());
		hoverOpacity = entryHovered ? Math.min(1, hoverOpacity + highlightFadeSpeed) : Math.max(0, hoverOpacity - highlightFadeSpeed);

		if(hoverOpacity > 0) {
			drawListEntryHighlight(guiGraphics, entryX, entryY, width, getHeight());
		}
	}

	private void drawListEntryHighlight(GuiGraphics guiGraphics, int x, int y, int width, int height) {
		final int highlightAlpha = (int) (100 * hoverOpacity);
		final int highlightColor = (highlightAlpha << 24) | (150 << 16) | (150 << 8) | 150;

		GuiHelper.drawRectangle(guiGraphics, x, y, width, height, highlightColor);
	}
}
