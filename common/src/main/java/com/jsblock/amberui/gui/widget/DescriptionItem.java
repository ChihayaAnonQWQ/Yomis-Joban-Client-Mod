package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

import static com.jsblock.amberui.gui.widget.ListViewWidget.ENTRY_PADDING;

/**
 * A prose row, wrapped to the list's width.
 */
public class DescriptionItem extends AbstractListItem {
	private static final int LINE_HEIGHT = 13;
	private final Component text;
	public final List<FormattedCharSequence> lines;

	public DescriptionItem(Component text, int width) {
		this.text = text;
		this.lines = Minecraft.getInstance().font.split(text, Math.max(1, width - ENTRY_PADDING - ENTRY_PADDING));
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int entryX, int entryY, int width, int height,
					 int mouseX, int mouseY, boolean widgetVisible, boolean rowInSight,
					 double elapsed, float tickDelta) {
		super.draw(guiGraphics, entryX, entryY, width, height, mouseX, mouseY, widgetVisible,
				rowInSight, elapsed, tickDelta);
		if (rowInSight) {
			drawDescription(guiGraphics, entryX, entryY);
		}
	}

	@Override
	protected void drawBackground(GuiGraphics guiGraphics, int entryX, int entryY, int width,
								  int mouseX, int mouseY, double elapsed, float tickDelta) {
	}

	@Override
	public boolean matchQuery(String searchTerm) {
		return searchTerm == null || searchTerm.isEmpty()
				|| (text != null && text.getString().toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT)));
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
		return 10 + (this.lines.size() * LINE_HEIGHT);
	}

	private void drawDescription(GuiGraphics guiGraphics, int entryX, int entryY) {
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(entryX, entryY, 0);
		guiGraphics.pose().translate(ENTRY_PADDING, 0, 0);

		for (int i = 0; i < lines.size(); i++) {
			guiGraphics.drawString(Minecraft.getInstance().font, lines.get(i),
					0, ENTRY_PADDING + (i * LINE_HEIGHT), ARGB_WHITE, true);
		}
		guiGraphics.pose().popPose();
	}
}
