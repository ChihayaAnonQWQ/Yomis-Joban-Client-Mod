package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.GuiHelper;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;

/**
 * A block configuration screen whose entries live in a scrolling list.
 *
 * <p>Subclasses add {@code ContentItem}s (a label and a control), {@code CategoryItem}s (section
 * headings) and {@code DescriptionItem}s (wrapped prose) from
 * {@link #addConfigEntries(ListViewWidget)}; the Save / Cancel row is placed automatically below
 * the list.</p>
 */
public abstract class BlockConfigListScreen extends BlockConfigScreen implements GuiHelper {
	protected ListViewWidget listView;

	protected BlockConfigListScreen(BlockPos blockPos) {
		super(blockPos);
	}

	@Override
	protected void init() {
		super.init();

		final int contentWidth = getContentWidth();
		final int listViewHeight = Math.max(160, (int) ((height - 60) * 0.75));
		final int startX = (width - contentWidth) / 2;
		final int startY = TEXT_PADDING * 5;
		final int bottomEntryHeight = Math.max(20, height - startY - listViewHeight - (BOTTOM_ROW_MARGIN * 2));

		listView = new ListViewWidget();
		listView.setXYSize(startX, startY, contentWidth, listViewHeight);
		addConfigEntries(listView);

		// The list first, then the controls it positions: the widgets have to draw above the
		// list's background and be the first to receive a click.
		addDrawableChild(listView);
		for (final AbstractWidget widget : listView.getWidgets()) {
			addDrawableChild(widget);
		}

		addBottomRowEntry(startX, startY + listViewHeight + BOTTOM_ROW_MARGIN, contentWidth, bottomEntryHeight);
		listView.positionWidgets();
	}

	public abstract void addConfigEntries(ListViewWidget listViewWidget);
}
