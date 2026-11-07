package com.jsblock.screen;

import com.jsblock.packet.PacketClient;
import net.minecraft.core.BlockPos;

/**
 * Config screen of the Railway Vision PIDS blocks.<br>
 * Same 2.3 layout as {@link JobanPIDSConfigScreen}, plus the platform number toggle.
 */
public class RVPIDSConfigScreen extends PIDSScreen {

	public RVPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals, boolean hidePlatformNumber, String presetID,
							  boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
							  boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, pos2, maxArrivals, true, hidePlatformNumber, presetID == null ? "" : presetID,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}

	/**
	 * The overload the server packet uses: it already carries the panel's text, hidden rows and
	 * platforms, so the screen opens with those rather than reading a possibly stale client copy of
	 * the block entity.
	 */
	public RVPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
			String[] messages, boolean[] rowHidden, java.util.Set<Long> platformIds, boolean hidePlatformNumber,
			String presetID, boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
			boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, pos2, maxArrivals, true, hidePlatformNumber, presetID == null ? "" : presetID,
				messages, rowHidden, platformIds,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}

	@Override
	protected void onSave() {
		PacketClient.sendRVPIDSConfigC2S(pos1, pos2, collectCustomMessages(), collectRowHidden(), filteredPlatforms,
				isHidePlatformNumberSelected(),
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
