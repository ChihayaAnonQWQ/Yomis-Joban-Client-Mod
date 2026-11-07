package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.ClipStack;
import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A vertical list of {@link AbstractListItem}s.
 *
 * <p>Row controls are not drawn by the list. They are real screen children registered by
 * {@code BlockConfigListScreen}, which keeps Minecraft's own focus, keyboard and narration
 * handling intact; the list positions them and sets their visibility from the scroll position.
 * That is the one structural difference from AmberUI's MTR 4 implementation, where the controls
 * lived inside the list's own {@code ClickableWidgetExtension}.</p>
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
		this.width = width;
		this.height = height;
		setScroll(currentScroll);
		positionWidgets(currentScroll);
		refreshDisplayedList();
	}

	public void add(Component text, MappedWidget widget) {
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
		this.searchTerm = searchTerm == null ? "" : searchTerm;
		refreshDisplayedList();
	}

	/** All controls owned by the rows, in display order, ready for {@code addDrawableChild}. */
	public List<AbstractWidget> getWidgets() {
		final List<AbstractWidget> widgets = new ArrayList<>();
		for (final AbstractListItem item : entryList) {
			if (item instanceof ContentItem) {
				final MappedWidget widget = ((ContentItem) item).widget;
				if (widget != null) {
					widget.collectWidgets(widgets);
				}
			}
		}
		return widgets;
	}

	private void refreshDisplayedList() {
		displayedEntryList.clear();
		for (final AbstractListItem entry : entryList) {
			if (entry.matchQuery(searchTerm)) {
				entry.shown();
				displayedEntryList.add(entry);
			} else {
				entry.hidden();
			}
		}
		positionWidgets();
		setScroll(currentScroll);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		GuiHelper.drawRectangle(guiGraphics, getX(), getY(), width, height, 0x4F4C4C4C);
		super.renderWidget(guiGraphics, mouseX, mouseY, tickDelta);
	}

	@Override
	public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		elapsed += tickDelta / 20.0;

		int incY = 0;
		for (final AbstractListItem item : displayedEntryList) {
			ClipStack.ensureStateCorrect(guiGraphics);

			final int entryX = getX();
			final int entryY = getY() + incY - (int) currentScroll;
			final int rowHeight = item.getHeight();
			final boolean rowInSight = entryY + rowHeight > getY() && entryY < getY() + getHeight();

			boolean widgetVisible = false;
			if (item instanceof ContentItem) {
				final ContentItem contentItem = (ContentItem) item;
				if (contentItem.widget != null) {
					widgetVisible = contentItem.widget.getY() >= getY()
							&& contentItem.widget.getY() + contentItem.widget.getHeight() <= getY() + getHeight();
					contentItem.widget.setVisible(widgetVisible);
				}
			}

			item.draw(guiGraphics, entryX, entryY, width - getScrollbarOffset(), height,
					mouseX, mouseY, widgetVisible, rowInSight, elapsed, tickDelta);
			incY += rowHeight;
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
		for (final AbstractListItem entry : displayedEntryList) {
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
		for (final AbstractListItem listItem : displayedEntryList) {
			final int entryY = startY + incY;
			final int x = (startX + width - getScrollbarOffset()) - ENTRY_PADDING;
			listItem.positionChanged(x, (int) -scroll + entryY);
			incY += listItem.getHeight();
		}
	}

	/** Exposed for screens that want to lay out rows against the same width the list uses. */
	public List<AbstractListItem> getEntries() {
		return Collections.unmodifiableList(entryList);
	}
}
