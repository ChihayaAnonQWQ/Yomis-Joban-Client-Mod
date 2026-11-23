package com.jsblock.screen;

import com.jsblock.amberui.gui.widget.ListViewWidget;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Shared base of the two-block PIDS configuration screens (1A/LCD and RV).
 *
 * <p>Adds YJCM's automatic preset switching on top of {@link PIDSBlockConfigScreen}: one section
 * that switches when a train is about to arrive, one when it is about to leave. The widgets live in
 * {@link AutoSwitchSection} so the projector screen can offer the same rows without a second copy.</p>
 *
 * <p>Port of JCM 2.3's screens, with those two sections being fork-only.</p>
 */
public abstract class PIDSScreen extends PIDSBlockConfigScreen {

	protected final BlockPos pos2;

	protected final AutoSwitchSection arrivalSwitch;
	protected final AutoSwitchSection departureSwitch;

	public PIDSScreen(BlockPos pos1, BlockPos pos2, int maxArrivals, boolean withPlatformNumberOption, boolean hidePlatformNumber, String presetId,
					  boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
					  boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		this(pos1, pos2, maxArrivals, withPlatformNumberOption, hidePlatformNumber, presetId, null, null, null,
				autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
				depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
	}

	public PIDSScreen(BlockPos pos1, BlockPos pos2, int maxArrivals, boolean withPlatformNumberOption, boolean hidePlatformNumber, String presetId,
					  String[] messages, boolean[] rowHidden, Set<Long> platformIds,
					  boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
					  boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
		super(pos1, maxArrivals, withPlatformNumberOption, presetId, hidePlatformNumber, messages, rowHidden, platformIds);
		this.pos2 = pos2;
		this.arrivalSwitch = new AutoSwitchSection(false, autoSwitchEnabled, autoSwitchPreset,
				orDefault(autoSwitchCountdown), orDefault(autoSwitchDuration), false, this::openPresetPicker);
		this.departureSwitch = new AutoSwitchSection(true, depAutoSwitchEnabled, depAutoSwitchPreset,
				orDefault(depAutoSwitchCountdown), orDefault(depAutoSwitchDuration), depAutoSwitchUntilClose, this::openPresetPicker);
	}

	/**
	 * A stored countdown or duration of 0 means "never set", and these two boxes have always opened
	 * on 10 in that case. The same rule applies to the projector, which is why it lives here.
	 */
	static int orDefault(int value) {
		return value > 0 ? value : 10;
	}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		super.addConfigEntries(listViewWidget);

		listViewWidget.addCategory(Text.translatable("gui.jsblock.arrival_switch_section"));
		arrivalSwitch.addRows(listViewWidget, this);

		listViewWidget.addCategory(Text.translatable("gui.jsblock.departure_switch_section"));
		departureSwitch.addRows(listViewWidget, this);
	}

	@Override
	public void tick() {
		super.tick();
		arrivalSwitch.tick();
		departureSwitch.tick();
	}

	/** Opens the thumbnail picker for one of the two sections and writes the choice back into it. */
	private void openPresetPicker(AutoSwitchSection section) {
		Minecraft.getInstance().setScreen(
				new PIDSPresetScreen(pos1, section.preset(), section::setPreset).withPreviousScreen(this));
	}

	protected boolean isAutoSwitchEnabled() {
		return arrivalSwitch.isEnabled();
	}

	protected String getAutoSwitchPreset() {
		return arrivalSwitch.preset();
	}

	protected int getAutoSwitchCountdown() {
		return arrivalSwitch.countdown();
	}

	protected int getAutoSwitchDuration() {
		return arrivalSwitch.duration();
	}

	protected boolean isDepAutoSwitchEnabled() {
		return departureSwitch.isEnabled();
	}

	protected String getDepAutoSwitchPreset() {
		return departureSwitch.preset();
	}

	protected int getDepAutoSwitchCountdown() {
		return departureSwitch.countdown();
	}

	protected int getDepAutoSwitchDuration() {
		return departureSwitch.duration();
	}

	protected boolean isDepAutoSwitchUntilClose() {
		return departureSwitch.untilClose();
	}
}
