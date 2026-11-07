package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * A category header in a {@link ListViewWidget}.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.CategoryItem}.
 */
public class CategoryItem extends AbstractListItem {
	public final Component title;
	private final int height;

	public CategoryItem(Component title, int height) {
		this.height = height;
		this.title = title;
	}

	public CategoryItem(Component title) {
		this(title, 22);
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int entryX, int entryY, int width, int height, int mouseX, int mouseY, boolean widgetVisible, boolean rowInSight, double elapsed, float tickDelta) {
		if(rowInSight) {
			GuiHelper.drawRectangle(guiGraphics, entryX, entryY, width, this.height, 0x99999999);
			guiGraphics.drawCenteredString(GuiHelper.getFont(), title, (entryX + width / 2), entryY - (8 / 2) + (this.height / 2), ARGB_WHITE);
		}
	}

	@Override
	public boolean matchQuery(String searchTerm) {
		return Objects.equals(searchTerm, "") || title.getString().contains(searchTerm);
	}

	@Override
	public void positionChanged(int entryX, int entryY) {
	}

	@Override
	public void hidden() {
	}

	@Override
	public void shown() {
	}

	@Override
	public int getHeight() {
		return this.height;
	}
}
