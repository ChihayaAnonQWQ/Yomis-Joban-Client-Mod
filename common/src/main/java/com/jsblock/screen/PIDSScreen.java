package com.jsblock.screen;

import com.jsblock.amberui.gui.widget.IntegerTextField;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import mtr.screen.WidgetBetterCheckbox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * A PIDS configuration screen with the YJCM-only automatic preset switching.
 *
 * <p>Two independent rules are kept: one for an arriving train and one for a departing one. Each
 * has an enable switch, a preset, a countdown (seconds before the event) and a duration; the
 * departure rule additionally has "until the doors close", which disables its duration field.
 * These are YJCM features that JCM 2.3 does not have, so they are added on top of the shared
 * {@link PIDSBlockConfigScreen} list rather than being folded into it.</p>
 */
public abstract class PIDSScreen extends PIDSBlockConfigScreen {
	protected boolean autoSwitchEnabled;
	protected String autoSwitchPreset;
	protected int autoSwitchCountdown;
	protected int autoSwitchDuration;
	protected boolean depAutoSwitchEnabled;
	protected String depAutoSwitchPreset;
	protected int depAutoSwitchCountdown;
	protected int depAutoSwitchDuration;
	protected boolean depAutoSwitchUntilClose;

	protected WidgetBetterCheckbox autoSwitchCheckbox;
	protected Button autoSwitchPresetButton;
	protected IntegerTextField autoSwitchCountdownField;
	protected IntegerTextField autoSwitchDurationField;
	protected WidgetBetterCheckbox depAutoSwitchCheckbox;
	protected Button depAutoSwitchPresetButton;
	protected IntegerTextField depAutoSwitchCountdownField;
	protected IntegerTextField depAutoSwitchDurationField;
	protected WidgetBetterCheckbox depAutoSwitchUntilCloseCheckbox;

	protected PIDSScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
						 String[] packetMessages, boolean[] packetRowHidden, Set<Long> packetPlatformIds,
						 String presetID, boolean hidePlatformNumber, boolean supportsHidePlatformNumber,
						 boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
						 boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration,
						 boolean depAutoSwitchUntilClose,
						 net.minecraft.network.chat.Component screenTitle) {
		super(pos1, pos2, maxArrivals, packetMessages, packetRowHidden, packetPlatformIds,
				presetID, hidePlatformNumber, supportsHidePlatformNumber, screenTitle);

		this.autoSwitchEnabled = autoSwitchEnabled;
		this.autoSwitchPreset = autoSwitchPreset == null ? "" : autoSwitchPreset;
		this.autoSwitchCountdown = autoSwitchCountdown > 0 ? autoSwitchCountdown : 10;
		this.autoSwitchDuration = autoSwitchDuration > 0 ? autoSwitchDuration : 10;
		this.depAutoSwitchEnabled = depAutoSwitchEnabled;
		this.depAutoSwitchPreset = depAutoSwitchPreset == null ? "" : depAutoSwitchPreset;
		this.depAutoSwitchCountdown = depAutoSwitchCountdown > 0 ? depAutoSwitchCountdown : 10;
		this.depAutoSwitchDuration = depAutoSwitchDuration > 0 ? depAutoSwitchDuration : 10;
		this.depAutoSwitchUntilClose = depAutoSwitchUntilClose;
	}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		super.addConfigEntries(listViewWidget);
		addAutoSwitchEntries(listViewWidget);
	}

	protected void addAutoSwitchEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.arrival_switch_section"));

		autoSwitchCheckbox = new WidgetBetterCheckbox(0, 0, WIDGET_COLUMN_WIDTH, SQUARE_SIZE,
				Text.translatable("gui.jsblock.auto_switch_enabled"), checked -> updateAutoSwitchEnabledStates());
		autoSwitchCheckbox.setChecked(autoSwitchEnabled);
		autoSwitchCheckbox.setWidth(WIDGET_COLUMN_WIDTH);
		listViewWidget.add(null, new MappedWidget(autoSwitchCheckbox));

		autoSwitchPresetButton = UtilitiesClient.newButton(presetText(autoSwitchPreset),
				button -> openPresetSelector(autoSwitchPreset, selected -> autoSwitchPreset = selected));
		autoSwitchPresetButton.setWidth(150);
		listViewWidget.add(Text.translatable("gui.jsblock.auto_switch_preset"), new MappedWidget(autoSwitchPresetButton));

		autoSwitchCountdownField = new IntegerTextField(0, 0, 80, 20, 0, 3600, autoSwitchCountdown);
		listViewWidget.add(Text.translatable("gui.jsblock.auto_switch_countdown"), new MappedWidget(autoSwitchCountdownField));

		autoSwitchDurationField = new IntegerTextField(0, 0, 80, 20, 0, 3600, autoSwitchDuration);
		listViewWidget.add(Text.translatable("gui.jsblock.auto_switch_duration"), new MappedWidget(autoSwitchDurationField));

		listViewWidget.addCategory(Text.translatable("gui.jsblock.departure_switch_section"));

		depAutoSwitchCheckbox = new WidgetBetterCheckbox(0, 0, WIDGET_COLUMN_WIDTH, SQUARE_SIZE,
				Text.translatable("gui.jsblock.dep_auto_switch_enabled"), checked -> updateAutoSwitchEnabledStates());
		depAutoSwitchCheckbox.setChecked(depAutoSwitchEnabled);
		depAutoSwitchCheckbox.setWidth(WIDGET_COLUMN_WIDTH);
		listViewWidget.add(null, new MappedWidget(depAutoSwitchCheckbox));

		depAutoSwitchPresetButton = UtilitiesClient.newButton(presetText(depAutoSwitchPreset),
				button -> openPresetSelector(depAutoSwitchPreset, selected -> depAutoSwitchPreset = selected));
		depAutoSwitchPresetButton.setWidth(150);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_preset"), new MappedWidget(depAutoSwitchPresetButton));

		depAutoSwitchCountdownField = new IntegerTextField(0, 0, 80, 20, 0, 3600, depAutoSwitchCountdown);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_countdown"), new MappedWidget(depAutoSwitchCountdownField));

		depAutoSwitchDurationField = new IntegerTextField(0, 0, 80, 20, 0, 3600, depAutoSwitchDuration);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_duration"), new MappedWidget(depAutoSwitchDurationField));

		depAutoSwitchUntilCloseCheckbox = new WidgetBetterCheckbox(0, 0, WIDGET_COLUMN_WIDTH, SQUARE_SIZE,
				Text.translatable("gui.jsblock.dep_auto_switch_until_close"), checked -> updateAutoSwitchEnabledStates());
		depAutoSwitchUntilCloseCheckbox.setChecked(depAutoSwitchUntilClose);
		depAutoSwitchUntilCloseCheckbox.setWidth(WIDGET_COLUMN_WIDTH);
		listViewWidget.add(null, new MappedWidget(depAutoSwitchUntilCloseCheckbox));

		updateAutoSwitchEnabledStates();
	}

	private void updateAutoSwitchEnabledStates() {
		if (depAutoSwitchUntilCloseCheckbox == null || depAutoSwitchDurationField == null) {
			return;
		}
		final boolean untilClose = depAutoSwitchUntilCloseCheckbox.selected();
		depAutoSwitchDurationField.active = !untilClose;
		depAutoSwitchDurationField.setEditable(!untilClose);
	}

	@Override
	protected void captureAll() {
		super.captureAll();
		if (autoSwitchCheckbox == null) {
			return;
		}

		autoSwitchEnabled = autoSwitchCheckbox.selected();
		autoSwitchCountdown = parseInteger(autoSwitchCountdownField, 10);
		autoSwitchDuration = parseInteger(autoSwitchDurationField, 10);
		depAutoSwitchEnabled = depAutoSwitchCheckbox.selected();
		depAutoSwitchCountdown = parseInteger(depAutoSwitchCountdownField, 10);
		depAutoSwitchDuration = parseInteger(depAutoSwitchDurationField, 10);
		depAutoSwitchUntilClose = depAutoSwitchUntilCloseCheckbox.selected();
	}
}
