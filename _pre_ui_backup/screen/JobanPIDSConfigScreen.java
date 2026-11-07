package com.jsblock.screen;

import com.jsblock.block.JobanPIDSBase;
import com.jsblock.packet.PacketClient;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

/**
 * Configuration screen for the 1A / LCD PIDS blocks.
 *
 * <p>All of the layout and behaviour lives in {@link PIDSScreen}; this class only chooses the
 * screen title, reads the block entity when the screen was opened without packet data, and sends
 * the packet this block type expects. The constructor signature is the one
 * {@code PacketClient} has always used.</p>
 */
public class JobanPIDSConfigScreen extends PIDSScreen {
	public JobanPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
								 String[] packetMessages, boolean[] packetRowHidden, Set<Long> packetPlatformIds,
								 String presetID,
								 boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
								 boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, pos2, maxArrivals, packetMessages, packetRowHidden, packetPlatformIds,
				presetID, false, false,
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
		if (!(entity instanceof JobanPIDSBase.TileEntityBlockJobanPIDS)) {
			return;
		}

		final JobanPIDSBase.TileEntityBlockJobanPIDS pids = (JobanPIDSBase.TileEntityBlockJobanPIDS) entity;
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
		PacketClient.sendJobanPIDSConfigC2S(pos1, pos2, copyMessages(), copyHiddenRows(), copyPlatformIds(), presetID,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}
}
