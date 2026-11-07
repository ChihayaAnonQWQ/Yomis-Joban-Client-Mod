package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.GuiHelper;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import net.minecraft.core.BlockPos;

/**
 * Block Config Screen with a GUI list, override {@link BlockConfigListScreen#addConfigEntries(ListViewWidget)} to add items to the list.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.screen.BlockConfigListScreen}.
 */
public abstract class BlockConfigListScreen extends BlockConfigScreen implements GuiHelper {
	public BlockConfigListScreen(BlockPos blockPos) {
		super(blockPos);
	}

	@Override
	protected void init2() {
		super.init2();
		final int contentWidth = getContentWidth();
		final int startX = (width - contentWidth) / 2;
		final int startY = TEXT_PADDING * 5;
		/* JCM 2.3 gave the list 75% of the screen height, which suited its shorter config lists.
		   The YJCM screens add the arrival and departure auto-switch sections on top of them, and at
		   75% their last rows were below the fold with no way to reach them. The list now takes
		   whatever the title and the bottom row leave. */
		final int bottomEntryHeight = Math.max(24, Math.min(40, height / 12));
		final int listViewHeight = Math.max(120,
				height - startY - bottomEntryHeight - (BOTTOM_ROW_MARGIN * 2));

		final ListViewWidget listViewWidget = new ListViewWidget();
		listViewWidget.setXYSize(startX, startY, contentWidth, listViewHeight);
		addConfigEntries(listViewWidget);
		addBottomRowEntry(startX, startY + listViewHeight + BOTTOM_ROW_MARGIN, contentWidth, bottomEntryHeight);
		listViewWidget.positionWidgets();
		addRenderableWidget(listViewWidget);
	}

	public abstract void addConfigEntries(ListViewWidget listViewWidget);
}
