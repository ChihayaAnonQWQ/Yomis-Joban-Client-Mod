package com.jsblock.screen;

import com.jsblock.block.PIDSRVBase;
import com.jsblock.packet.PacketClient;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

/**
 * Configuration screen for the Railway Vision PIDS blocks.
 *
 * <p>Same list as {@link JobanPIDSConfigScreen} plus the platform-number switch that only the RV
 * and projector boards draw. All layout comes from {@link PIDSScreen}.</p>
 */
public class RVPIDSConfigScreen extends PIDSScreen {
	public RVPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
							  String[] packetMessages, boolean[] packetRowHidden, Set<Long> packetPlatformIds,
							  boolean hidePlatformNumber, String presetID,
							  boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
							  boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, pos2, maxArrivals, packetMessages, packetRowHidden, packetPlatformIds,
				presetID, hidePlatformNumber, true,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose,
				Text.translatable("gui.jsblock.pids_config.title"));
	}

	@Override
	protected void loadBlockEntityFallback() {
		final Level world = Minecraft.getInstance().level;
		if (world == null) {
			return;
		}
		final BlockEntity entity = world.getBlockEntity(pos1);
		if (!(entity instanceof PIDSRVBase.TileEntityBlockRVPIDS)) {
			return;
		}

		final PIDSRVBase.TileEntityBlockRVPIDS pids = (PIDSRVBase.TileEntityBlockRVPIDS) entity;
		filterPlatformIds.clear();
		filterPlatformIds.addAll(pids.getPlatformIds());
		for (int i = 0; i < maxArrivals; i++) {
			messages[i] = pids.getMessage(i);
			hideArrival[i] = pids.getHideArrival(i);
		}
	}

	@Override
	protected void onSave() {
		captureAll();
		PacketClient.sendRVPIDSConfigC2S(pos1, pos2, copyMessages(), copyHiddenRows(), copyPlatformIds(), hidePlatformNumber, presetID,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}
}
