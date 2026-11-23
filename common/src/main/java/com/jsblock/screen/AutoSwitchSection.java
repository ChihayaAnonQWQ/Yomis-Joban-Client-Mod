package com.jsblock.screen;

import com.jsblock.amberui.gui.widget.ContentItem;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.client.JobanCustomResources;
import mtr.mappings.Text;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * One "automatic preset switch" block of a PIDS configuration screen: enable, the preset to switch
 * to, how long before the event to switch, and how long to stay on it.
 *
 * <p>Two of these make the arrival and the departure section. It used to live inline in
 * {@link PIDSScreen}, which is why the projector could not have one: the code was reachable only
 * through a two-block PIDS screen. Extracting it is what lets {@link PIDSProjectorScreen} offer the
 * same rows without a second copy of them.</p>
 *
 * <p>The preset is chosen from the same thumbnail picker as everywhere else; the row shows the
 * preset's preview and a short Choose button, and keeps the id rather than the name, because the id
 * is what the block entity stores.</p>
 */
final class AutoSwitchSection {

	/** Same width as the main preset row's Choose button, so the rows line up. */
	private static final int PRESET_BUTTON_WIDTH = 60;
	private static final int NUMBER_WIDTH = 52;
	private static final int SQUARE = 20;

	private final boolean departure;
	private final WidgetBetterCheckbox enabledBox;
	private final Button presetButton;
	private final WidgetBetterTextField countdownField;
	private final WidgetBetterTextField durationField;
	/** Departure only; {@code null} on the arrival section. */
	private final WidgetBetterCheckbox untilCloseBox;

	private String preset;

	/**
	 * @param departure   {@code true} for the departure section, which also has "until the doors
	 *                     close" and greys out the duration box when it is on
	 * @param openPicker  called with this section when its Choose button is pressed; the screen
	 *                    opens the preview picker and calls {@link #setPreset(String)}
	 */
	AutoSwitchSection(boolean departure, boolean enabled, String preset, int countdown, int duration,
					  boolean untilClose, Consumer<AutoSwitchSection> openPicker) {
		this.departure = departure;
		this.preset = preset == null ? "" : preset;

		/* Component.empty(), not the row's text: a Checkbox draws its own message to the right of the
		   box, and these rows already have a label on the left -- the message was what showed up as a
		   clipped second copy of it. Same reason the settings screen passes EMPTY. */
		this.enabledBox = new WidgetBetterCheckbox(0, 0, SQUARE, SQUARE, Component.empty(), checked -> {
		});
		this.enabledBox.setChecked(enabled);

		this.presetButton = Button
				.builder(Text.translatable("gui.jsblock.pids.listview.widget.choose_preset"),
						button -> openPicker.accept(this))
				.bounds(0, 0, PRESET_BUTTON_WIDTH, 20)
				.build();

		this.countdownField = TextFields.setValue(new WidgetBetterTextField("", 4), String.valueOf(countdown));
		this.durationField = TextFields.setValue(new WidgetBetterTextField("", 4), String.valueOf(duration));

		if (departure) {
			this.untilCloseBox = new WidgetBetterCheckbox(0, 0, SQUARE, SQUARE, Component.empty(), checked -> {
			});
			this.untilCloseBox.setChecked(untilClose);
			/* With "until the doors close" on there is no duration to type. */
			this.durationField.setEditable(!untilClose);
		} else {
			this.untilCloseBox = null;
		}
	}

	/** Appends this section's rows, registering the widgets with the screen so they can be typed in. */
	void addRows(ListViewWidget listViewWidget, PIDSBlockConfigScreen host) {
		host.registerListWidget(enabledBox);
		listViewWidget.add(key("_enabled"), new MappedWidget(enabledBox));

		host.registerListWidget(presetButton);
		final ContentItem presetRow = new ContentItem(key("_preset"), new MappedWidget(presetButton), 26);
		presetRow.setIconCallback((guiGraphics, startX, startY, width, height) -> PIDSPresetScreen.drawPIDSPreview(
				JobanCustomResources.getPresetEntry(preset), guiGraphics, startX, startY, width, height, true));
		listViewWidget.add(presetRow);

		countdownField.setWidth(NUMBER_WIDTH);
		host.registerListWidget(countdownField);
		listViewWidget.add(key("_countdown"), new MappedWidget(countdownField));

		durationField.setWidth(NUMBER_WIDTH);
		host.registerListWidget(durationField);
		listViewWidget.add(key("_duration"), new MappedWidget(durationField));

		if (untilCloseBox != null) {
			host.registerListWidget(untilCloseBox);
			listViewWidget.add(Text.translatable("gui.jsblock.dep_auto_switch_until_close"), new MappedWidget(untilCloseBox));
		}
	}

	/** The list row's label, from the same keys the two-block PIDS screens have always used. */
	private Component key(String suffix) {
		return Text.translatable((departure ? "gui.jsblock.dep_auto_switch" : "gui.jsblock.auto_switch") + suffix);
	}

	void tick() {
		countdownField.tick();
		durationField.tick();
	}

	/** Called by the screen once the player picked a preset in the picker. */
	void setPreset(String preset) {
		this.preset = preset == null ? "" : preset;
	}

	boolean isEnabled() {
		return enabledBox.selected();
	}

	String preset() {
		return preset;
	}

	int countdown() {
		return parse(countdownField, 10);
	}

	int duration() {
		return parse(durationField, 10);
	}

	boolean untilClose() {
		return untilCloseBox != null && untilCloseBox.selected();
	}

	/** A countdown or duration of 0 means "not set" here, exactly as it always has on these screens. */
	private static int parse(WidgetBetterTextField field, int fallback) {
		try {
			return Integer.parseInt(field.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
