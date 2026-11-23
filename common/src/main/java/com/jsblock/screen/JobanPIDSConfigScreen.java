package com.jsblock.screen;

import com.jsblock.packet.PacketClient;
import net.minecraft.core.BlockPos;

/**
 * Config screen of the Joban PIDS blocks (non-Railway-Vision variants).<br>
 * The entry layout comes from JCM 2.3's {@code PIDSScreen}, the auto-switch categories are
 * exclusive to this fork.
 */
public class JobanPIDSConfigScreen extends PIDSScreen {

	public JobanPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals, String presetID,
								 boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
								 boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, pos2, maxArrivals, false, false, presetID == null ? "" : presetID,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}

	/**
	 * The overload the server packet uses: it already carries the panel's text, hidden rows and
	 * platforms, so the screen opens with those rather than reading a possibly stale client copy of
	 * the block entity.
	 */
	public JobanPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
			String[] messages, boolean[] rowHidden, java.util.Set<Long> platformIds,
			String presetID, boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
			boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, pos2, maxArrivals, false, false, presetID == null ? "" : presetID,
				messages, rowHidden, platformIds,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}

	@Override
	protected void onSave() {
		PacketClient.sendJobanPIDSConfigC2S(pos1, pos2, collectCustomMessages(), collectRowHidden(), filteredPlatforms,
				presetId == null ? "" : presetId,
				isAutoSwitchEnabled(),
				getAutoSwitchPreset() == null ? "" : getAutoSwitchPreset(),
				getAutoSwitchCountdown(),
				getAutoSwitchDuration(),
				isDepAutoSwitchEnabled(),
				getDepAutoSwitchPreset() == null ? "" : getDepAutoSwitchPreset(),
				getDepAutoSwitchCountdown(),
				getDepAutoSwitchDuration(),
				isDepAutoSwitchUntilClose());
	}
}
