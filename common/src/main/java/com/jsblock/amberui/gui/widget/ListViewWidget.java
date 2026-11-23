package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.ClipStack;
import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A scrollable list of entries.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.ListViewWidget}.
 */
public class ListViewWidget extends AbstractScrollViewWidget implements GuiHelper {
	public static final int ENTRY_PADDING = 5;
	private final List<AbstractListItem> displayedEntryList = new ArrayList<>();
	private final List<AbstractListItem> entryList = new ArrayList<>();
	private float elapsed;
	private String searchTerm = "";

	public ListViewWidget() {
		super(0, 0, 0, 0);
	}

	public void setXYSize(int x, int y, int width, int height) {
		setX(x);
		setY(y);
		setWidth(width);
		this.height = height;
		setScroll(currentScroll);
		positionWidgets(currentScroll);
		refreshDisplayedList();
	}

	public void add(Component text, WidgetHost widget) {
		add(new ContentItem(text, widget));
	}

	public void add(AbstractListItem listItem) {
		entryList.add(listItem);
		refreshDisplayedList();
	}

	public void addCategory(Component text) {
		entryList.add(new CategoryItem(text));
		refreshDisplayedList();
	}

	public void reset() {
		this.entryList.clear();
		refreshDisplayedList();
	}

	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
		refreshDisplayedList();
	}

	private void refreshDisplayedList() {
		displayedEntryList.clear();

		entryList.forEach(e -> {
			if(e.matchQuery(searchTerm)) {
				e.shown();
				displayedEntryList.add(e);
			} else {
				e.hidden();
			}
		});

		positionWidgets();

		// Update scrolling so that it will rubber-band back if scrolled area are beyond viewport
		setScroll(currentScroll);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		GuiHelper.drawRectangle(guiGraphics, getX(), getY(), getWidth(), getHeight(), 0x4F4C4C4C);
		super.renderWidget(guiGraphics, mouseX, mouseY, tickDelta);
	}

	@Override
	public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		elapsed += (tickDelta / 20);

		int incY = 0;

		for(AbstractListItem abstractListItem : displayedEntryList) {
			ClipStack.ensureStateCorrect();
			final int entryX = getX();
			final int entryY = getY() + incY - (int) currentScroll;
			boolean widgetVisible = false;
			final boolean rowInSight = inRectangle(entryX, entryY, getX(), getY(), getWidth(), getHeight()) || inRectangle(entryX, entryY + abstractListItem.getHeight(), getX(), getY(), getWidth(), getHeight());

			if(abstractListItem instanceof ContentItem) {
				final ContentItem contentItem = (ContentItem) abstractListItem;

				if(contentItem.widget != null) {
					final boolean topLeftVisible = inRectangle(contentItem.widget.getX(), contentItem.widget.getY(), getX(), getY(), getWidth(), getHeight());
					final boolean bottomRightVisible = inRectangle(contentItem.widget.getX() + contentItem.widget.getWidth(), contentItem.widget.getY() + contentItem.widget.getHeight(), getX(), getY(), getWidth(), getHeight());
					widgetVisible = topLeftVisible && bottomRightVisible;
				}
			}

			abstractListItem.draw(guiGraphics, entryX, entryY, getWidth() - getScrollbarOffset(), getHeight(), mouseX, mouseY, widgetVisible, rowInSight, elapsed, tickDelta);

			incY += abstractListItem.getHeight();
		}
	}

	@Override
	public void setScroll(double newScroll) {
		super.setScroll(newScroll);
		positionWidgets();
	}

	@Override
	protected int getContentHeight() {
		int entryHeight = 0;

		for(AbstractListItem entry : displayedEntryList) {
			entryHeight += entry.getHeight();
		}

		return entryHeight;
	}

	public void positionWidgets() {
		positionWidgets(currentScroll);
	}

	private void positionWidgets(double scroll) {
		final int startX = getX();
		final int startY = getY();

		int incY = 0;

		for(AbstractListItem listItem : displayedEntryList) {
			final int entryY = startY + incY;
			final int x = (startX + getWidth() - getScrollbarOffset()) - ENTRY_PADDING;
			listItem.positionChanged(x, (int) -scroll + entryY);
			incY += listItem.getHeight();
		}
	}
}
