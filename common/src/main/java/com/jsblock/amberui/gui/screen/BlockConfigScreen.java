package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.GuiHelper;
import mtr.client.ClientData;
import mtr.data.IGui;
import mtr.data.RailwayData;
import mtr.data.Station;
import mtr.mappings.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Base for a block's configuration screen: it knows which block is being configured and draws
 * that block's position (and station, when it is inside one) as the subtitle.
 */
public abstract class BlockConfigScreen extends SavableScreen implements GuiHelper {
	protected final BlockPos blockPos;
	private Component cachedSubtitle;

	protected BlockConfigScreen(BlockPos blockPos) {
		super(false);
		this.blockPos = blockPos;
	}

	protected int getContentWidth() {
		return (int) Math.min(width * 0.75, MAX_CONTENT_WIDTH);
	}

	@Override
	public Component getScreenSubtitle() {
		/* The title is drawn every frame; looking the station up every frame would walk MTR's
		   station set for a value that cannot change while the screen is open. */
		if (cachedSubtitle != null) {
			return cachedSubtitle;
		}

		final Station station = RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, blockPos);

		if (station != null) {
			cachedSubtitle = Text.translatable("gui.jsblock.block_config.subtitle_with_station",
					blockPos.getX(), blockPos.getY(), blockPos.getZ(),
					IGui.formatStationName(station.name));
		} else {
			cachedSubtitle = Text.translatable("gui.jsblock.block_config.subtitle",
					blockPos.getX(), blockPos.getY(), blockPos.getZ());
		}
		return cachedSubtitle;
	}
}
