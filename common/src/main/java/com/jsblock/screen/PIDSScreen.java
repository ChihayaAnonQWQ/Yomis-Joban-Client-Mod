package com.jsblock.screen;

import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.ContentItem;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.client.JobanCustomResources;
import mtr.mappings.Text;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.Set;

// 基于 AmberUI 和 JCM 2.3 的屏幕移植
public abstract class PIDSScreen extends PIDSBlockConfigScreen {
	protected static final int AUTO_SWITCH_PRESET_WIDTH = 120;
	/** The same width as the main preset row's Choose button. */
	protected static final int CHOOSE_BUTTON_WIDTH = 60;
	protected static final int AUTO_SWITCH_NUMBER_WIDTH = 52;

	protected final BlockPos pos2;

	protected final WidgetBetterCheckbox autoSwitchCheckbox;
	protected final Button autoSwitchPresetButton;
	protected String autoSwitchPreset;
	protected final WidgetBetterTextField autoSwitchCountdownTextField;
	protected final WidgetBetterTextField autoSwitchDurationTextField;
	protected final WidgetBetterCheckbox depAutoSwitchCheckbox;
	protected final Button depAutoSwitchPresetButton;
	protected String depAutoSwitchPreset;
	protected final WidgetBetterTextField depAutoSwitchCountdownTextField;
	protected final WidgetBetterTextField depAutoSwitchDurationTextField;
	protected final WidgetBetterCheckbox depAutoSwitchUntilCloseCheckbox;

	private final int autoSwitchCountdown;
	private final int autoSwitchDuration;
	private final int depAutoSwitchCountdown;
	private final int depAutoSwitchDuration;

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
		this.autoSwitchCountdown = autoSwitchCountdown > 0 ? autoSwitchCountdown : 10;
		this.autoSwitchDuration = autoSwitchDuration > 0 ? autoSwitchDuration : 10;
		this.depAutoSwitchCountdown = depAutoSwitchCountdown > 0 ? depAutoSwitchCountdown : 10;
		this.depAutoSwitchDuration = depAutoSwitchDuration > 0 ? depAutoSwitchDuration : 10;

		this.autoSwitchCheckbox = new WidgetBetterCheckbox(0, 0, SQUARE_SIZE, SQUARE_SIZE, Text.translatable("gui.jsblock.auto_switch_enabled"), checked -> {
		});
		this.autoSwitchCheckbox.setChecked(autoSwitchEnabled);

		this.autoSwitchPreset = autoSwitchPreset == null ? "" : autoSwitchPreset;
		this.autoSwitchPresetButton = Button.builder(Text.translatable("gui.jsblock.pids.listview.widget.choose_preset"),
				button -> openPresetPicker(false)).bounds(0, 0, CHOOSE_BUTTON_WIDTH, 20).build();

		this.autoSwitchCountdownTextField = new WidgetBetterTextField("", 4);
		TextFields.setValue(this.autoSwitchCountdownTextField, String.valueOf(this.autoSwitchCountdown));
		this.autoSwitchDurationTextField = new WidgetBetterTextField("", 4);
		TextFields.setValue(this.autoSwitchDurationTextField, String.valueOf(this.autoSwitchDuration));

		this.depAutoSwitchCheckbox = new WidgetBetterCheckbox(0, 0, SQUARE_SIZE, SQUARE_SIZE, Text.translatable("gui.jsblock.dep_auto_switch_enabled"), checked -> {
		});
		this.depAutoSwitchCheckbox.setChecked(depAutoSwitchEnabled);

		this.depAutoSwitchPreset = depAutoSwitchPreset == null ? "" : depAutoSwitchPreset;
		this.depAutoSwitchPresetButton = Button.builder(Text.translatable("gui.jsblock.pids.listview.widget.choose_preset"),
				button -> openPresetPicker(true)).bounds(0, 0, CHOOSE_BUTTON_WIDTH, 20).build();

		this.depAutoSwitchCountdownTextField = new WidgetBetterTextField("", 4);
		TextFields.setValue(this.depAutoSwitchCountdownTextField, String.valueOf(this.depAutoSwitchCountdown));
		this.depAutoSwitchDurationTextField = new WidgetBetterTextField("", 4);
		TextFields.setValue(this.depAutoSwitchDurationTextField, String.valueOf(this.depAutoSwitchDuration));
		this.depAutoSwitchDurationTextField.setEditable(!depAutoSwitchUntilClose);

		this.depAutoSwitchUntilCloseCheckbox = new WidgetBetterCheckbox(0, 0, SQUARE_SIZE, SQUARE_SIZE, Text.translatable("gui.jsblock.dep_auto_switch_until_close"), checked -> {
		});
		this.depAutoSwitchUntilCloseCheckbox.setChecked(depAutoSwitchUntilClose);
		}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		super.addConfigEntries(listViewWidget);
		addAutoSwitchEntries(listViewWidget);
	}

	/**
	 * Fork-only auto preset switching settings, appended after the 2.3 entries.
	 */
	/**
	 * One row of the auto-switch section: a preset preview, its label, and a Choose button.
	 *
	 * <p>This replaced a free-text field the player had to type a preset id into — the same
	 * information, but picked from the same preview list the main preset uses.</p>
	 */
	private void addPresetRow(ListViewWidget listViewWidget, Component label,
							  Button chooseButton, java.util.function.Supplier<String> currentPreset) {
		final ContentItem row = new ContentItem(label, new MappedWidget(chooseButton), 26);
		row.setIconCallback((guiGraphics, startX, startY, width, height) -> PIDSPresetScreen.drawPIDSPreview(
				JobanCustomResources.getPresetEntry(currentPreset.get()), guiGraphics, startX, startY, width, height, true));
		listViewWidget.add(row);
	}

	/** @return the preset's display name, or the localised None when nothing is selected */
	private static Component presetLabel(String id) {
		final com.jsblock.data.PIDSPresetEntry entry = JobanCustomResources.getPresetEntry(id);
		return entry == null ? Text.translatable("gui.jsblock.pids_preset_none") : Text.literal(entry.getName());
	}

	/** Opens the preview picker for one of the auto-switch presets. */
	private void openPresetPicker(boolean departure) {
		final String current = departure ? depAutoSwitchPreset : autoSwitchPreset;
		Minecraft.getInstance().setScreen(new PIDSPresetScreen(pos1, current, selected -> {
			if (departure) {
				depAutoSwitchPreset = selected == null ? "" : selected;
			} else {
				autoSwitchPreset = selected == null ? "" : selected;
			}
		}).withPreviousScreen(this));
	}

	private void addAutoSwitchEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.arrival_switch_section"));

		addWidget(autoSwitchCheckbox);
		listViewWidget.add(Text.translatable("gui.jsblock.auto_switch_enabled"), new MappedWidget(autoSwitchCheckbox));

		addWidget(autoSwitchPresetButton);
		addPresetRow(listViewWidget, Text.translatable("gui.jsblock.auto_switch_preset"),
				autoSwitchPresetButton, () -> autoSwitchPreset);

		autoSwitchCountdownTextField.setWidth(AUTO_SWITCH_NUMBER_WIDTH);
		addWidget(autoSwitchCountdownTextField);
		listViewWidget.add(Text.translatable("gui.jsblock.auto_switch_countdown"), new MappedWidget(autoSwitchCountdownTextField));

		autoSwitchDurationTextField.setWidth(AUTO_SWITCH_NUMBER_WIDTH);
		addWidget(autoSwitchDurationTextField);
		listViewWidget.add(Text.translatable("gui.jsblock.auto_switch_duration"), new MappedWidget(autoSwitchDurationTextField));

		listViewWidget.addCategory(Text.translatable("gui.jsblock.departure_switch_section"));

		addWidget(depAutoSwitchCheckbox);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_enabled"), new MappedWidget(depAutoSwitchCheckbox));

		addWidget(depAutoSwitchPresetButton);
		addPresetRow(listViewWidget, Text.translatable("gui.jsblock.dep_auto_switch_preset"),
				depAutoSwitchPresetButton, () -> depAutoSwitchPreset);

		depAutoSwitchCountdownTextField.setWidth(AUTO_SWITCH_NUMBER_WIDTH);
		addWidget(depAutoSwitchCountdownTextField);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_countdown"), new MappedWidget(depAutoSwitchCountdownTextField));

		depAutoSwitchDurationTextField.setWidth(AUTO_SWITCH_NUMBER_WIDTH);
		addWidget(depAutoSwitchDurationTextField);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_duration"), new MappedWidget(depAutoSwitchDurationTextField));

		addWidget(depAutoSwitchUntilCloseCheckbox);
		listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_until_close"), new MappedWidget(depAutoSwitchUntilCloseCheckbox));
	}

	@Override
	public void tick() {
		super.tick();

		autoSwitchCountdownTextField.tick();
		autoSwitchDurationTextField.tick();
		depAutoSwitchCountdownTextField.tick();
		depAutoSwitchDurationTextField.tick();
	}

	protected boolean isAutoSwitchEnabled() {
		return autoSwitchCheckbox.selected();
	}

	protected String getAutoSwitchPreset() {
		return autoSwitchPreset;
	}

	protected int getAutoSwitchCountdown() {
		return parseInteger(autoSwitchCountdownTextField, 10);
	}

	protected int getAutoSwitchDuration() {
		return parseInteger(autoSwitchDurationTextField, 10);
	}

	protected boolean isDepAutoSwitchEnabled() {
		return depAutoSwitchCheckbox.selected();
	}

	protected String getDepAutoSwitchPreset() {
		return depAutoSwitchPreset;
	}

	protected int getDepAutoSwitchCountdown() {
		return parseInteger(depAutoSwitchCountdownTextField, 10);
	}

	protected int getDepAutoSwitchDuration() {
		return parseInteger(depAutoSwitchDurationTextField, 10);
	}

	protected boolean isDepAutoSwitchUntilClose() {
		return depAutoSwitchUntilCloseCheckbox.selected();
	}

	private static int parseInteger(WidgetBetterTextField textField, int fallback) {
		try {
			return Integer.parseInt(textField.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
