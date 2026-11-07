package com.jsblock.screen;

import com.jsblock.amberui.gui.screen.BlockConfigListScreen;
import com.jsblock.amberui.gui.widget.HorizontalWidgetSet;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.client.JobanCustomResources;
import com.jsblock.data.PIDSPreset;
import mtr.data.IGui;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import mtr.screen.PIDSConfigScreen;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * The configuration shared by every PIDS block: the preset, the platform filter, the optional
 * platform-number switch, and one custom message plus one hidden-row box per arrival slot.
 *
 * <p>This is the AmberUI list-screen equivalent of what {@code JobanPIDSConfigScreen} and
 * {@code RVPIDSConfigScreen} used to lay out by hand. The screen itself owns the values; the
 * subclass supplies the packet it is saved with. Re-entering the screen (after MTR's platform
 * picker or the preset picker returns) re-initialises the widgets, so {@link #init()} copies the
 * current control values back into the fields first -- otherwise a half-typed message would be
 * lost every time a child screen was opened.</p>
 */
public abstract class PIDSBlockConfigScreen extends BlockConfigListScreen implements IGui {
	protected static final int MESSAGE_MAX_LENGTH = 2048;
	protected static final int WIDGET_COLUMN_WIDTH = 240;
	protected static final int WIDE_FIELD_WIDTH = 170;

	protected final BlockPos pos1;
	protected final BlockPos pos2;
	protected final int maxArrivals;
	protected final String[] messages;
	protected final boolean[] hideArrival;
	protected final Set<Long> filterPlatformIds = new HashSet<>();
	protected final boolean hadPacketMessages;
	protected final boolean supportsHidePlatformNumber;
	private final Component screenTitle;

	protected String presetID;
	protected boolean hidePlatformNumber;

	protected Button presetButton;
	protected WidgetBetterCheckbox selectAllCheckbox;
	protected Button filterButton;
	protected WidgetBetterCheckbox hidePlatformNumberCheckbox;
	protected WidgetBetterTextField[] messageFields;
	protected WidgetBetterCheckbox[] rowHiddenBoxes;

	private boolean initialisedOnce = false;
	private boolean loadedFromBlockEntity = false;

	protected PIDSBlockConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
									String[] packetMessages, boolean[] packetRowHidden, Set<Long> packetPlatformIds,
									String presetID, boolean hidePlatformNumber, boolean supportsHidePlatformNumber,
									Component screenTitle) {
		super(pos1);
		this.pos1 = pos1;
		this.pos2 = pos2 == null ? pos1 : pos2;
		this.maxArrivals = Math.max(1, maxArrivals);
		this.hadPacketMessages = packetMessages != null;
		this.supportsHidePlatformNumber = supportsHidePlatformNumber;
		this.screenTitle = screenTitle;

		this.messages = new String[this.maxArrivals];
		this.hideArrival = new boolean[this.maxArrivals];
		this.presetID = presetID == null ? "" : presetID;
		this.hidePlatformNumber = hidePlatformNumber;

		for (int i = 0; i < this.maxArrivals; i++) {
			this.messages[i] = "";
		}
		if (packetMessages != null) {
			for (int i = 0; i < this.maxArrivals && i < packetMessages.length; i++) {
				this.messages[i] = packetMessages[i] == null ? "" : packetMessages[i];
				if (packetRowHidden != null && i < packetRowHidden.length) {
					this.hideArrival[i] = packetRowHidden[i];
				}
			}
		}
		if (packetPlatformIds != null) {
			this.filterPlatformIds.addAll(packetPlatformIds);
		}
	}

	@Override
	public Component getScreenTitle() {
		return screenTitle;
	}

	@Override
	protected void init() {
		if (initialisedOnce) {
			// Returning from MTR's platform picker or our preset picker re-runs init(); keep what
			// the player has typed instead of resetting every field to the last saved values.
			captureAll();
		} else if (!hadPacketMessages && !loadedFromBlockEntity) {
			loadBlockEntityFallback();
			loadedFromBlockEntity = true;
		}
		super.init();
		initialisedOnce = true;
	}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		addGeneralEntries(listViewWidget);
		addDisplayEntries(listViewWidget);
		addMessageEntries(listViewWidget);
	}

	protected void addGeneralEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.pids_section.general"));

		presetButton = UtilitiesClient.newButton(presetText(), button -> openPresetSelector());
		presetButton.setMessage(presetText());
		presetButton.setWidth(150);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_preset"), new MappedWidget(presetButton));

		selectAllCheckbox = new WidgetBetterCheckbox(0, 0, 150, SQUARE_SIZE,
				Text.translatable("gui.mtr.automatically_detect_nearby_platform"), checked -> {
		});
		selectAllCheckbox.setChecked(filterPlatformIds.isEmpty());
		selectAllCheckbox.setWidth(150);

		filterButton = PIDSConfigScreen.getPlatformFilterButton(pos1, selectAllCheckbox, filterPlatformIds, this);
		filterButton.setMessage(Text.translatable("selectWorld.edit"));
		filterButton.setWidth(80);

		final HorizontalWidgetSet filterRow = new HorizontalWidgetSet(4);
		filterRow.addWidget(new MappedWidget(selectAllCheckbox));
		filterRow.addWidget(new MappedWidget(filterButton));
		filterRow.setXYSize(0, 0, WIDGET_COLUMN_WIDTH, 20);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_filter_platforms"), new MappedWidget(filterRow));
	}

	protected void addDisplayEntries(ListViewWidget listViewWidget) {
		if (!supportsHidePlatformNumber) {
			return;
		}

		hidePlatformNumberCheckbox = new WidgetBetterCheckbox(0, 0, WIDGET_COLUMN_WIDTH, SQUARE_SIZE,
				Text.translatable("gui.jsblock.hide_platform_number"), checked -> {
		});
		hidePlatformNumberCheckbox.setChecked(hidePlatformNumber);
		hidePlatformNumberCheckbox.setWidth(WIDGET_COLUMN_WIDTH);
		listViewWidget.add(null, new MappedWidget(hidePlatformNumberCheckbox));
	}

	protected void addMessageEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.pids_section.messages"));

		messageFields = new WidgetBetterTextField[maxArrivals];
		rowHiddenBoxes = new WidgetBetterCheckbox[maxArrivals];

		final Component hideRowText = Text.translatable("gui.jsblock.pids_row_hidden");
		final int hideWidth = Math.max(60, font.width(hideRowText) + SQUARE_SIZE + IGui.TEXT_PADDING);

		for (int i = 0; i < maxArrivals; i++) {
			final WidgetBetterTextField messageField = new WidgetBetterTextField("", MESSAGE_MAX_LENGTH);
			messageField.setMaxLength(MESSAGE_MAX_LENGTH);
			messageField.setValue(messages[i]);
			messageField.setWidth(WIDE_FIELD_WIDTH);
			messageFields[i] = messageField;

			final WidgetBetterCheckbox hideBox = new WidgetBetterCheckbox(0, 0, hideWidth, SQUARE_SIZE, hideRowText,
					checked -> {
					});
			hideBox.setChecked(hideArrival[i]);
			hideBox.setWidth(hideWidth);
			rowHiddenBoxes[i] = hideBox;

			final HorizontalWidgetSet row = new HorizontalWidgetSet(4);
			row.addWidget(new MappedWidget(messageField));
			row.addWidget(new MappedWidget(hideBox));
			row.setXYSize(0, 0, WIDE_FIELD_WIDTH + 4 + hideWidth, 20);
			listViewWidget.add(Text.translatable("gui.jsblock.pids_custom_message", i + 1), new MappedWidget(row));
		}
	}

	/** Reads the block entity when the screen was opened without a packet payload. */
	protected void loadBlockEntityFallback() {
	}

	protected void captureAll() {
		captureMessages();
		capturePlatforms();
		if (hidePlatformNumberCheckbox != null) {
			hidePlatformNumber = hidePlatformNumberCheckbox.selected();
		}
	}

	protected void captureMessages() {
		if (messageFields == null || rowHiddenBoxes == null) {
			return;
		}
		for (int i = 0; i < maxArrivals && i < messageFields.length; i++) {
			messages[i] = messageFields[i].getValue();
			hideArrival[i] = rowHiddenBoxes[i].selected();
		}
	}

	protected void capturePlatforms() {
		if (selectAllCheckbox != null && selectAllCheckbox.selected()) {
			filterPlatformIds.clear();
		}
	}

	protected Component presetText() {
		return presetText(presetID);
	}

	protected Component presetText(String id) {
		if (id == null || id.isEmpty()) {
			return Text.translatable("gui.jsblock.pids_preset_none");
		}
		final PIDSPreset preset = JobanCustomResources.PIDSPresets.get(id);
		return Text.literal(preset == null ? id : preset.displayName());
	}

	protected void openPresetSelector() {
		openPresetSelector(presetID, selected -> presetID = selected);
	}

	protected void openPresetSelector(String currentPreset, java.util.function.Consumer<String> onSelected) {
		Minecraft.getInstance().setScreen(new PIDSPresetScreen(this, currentPreset, onSelected));
	}

	protected static int parseInteger(WidgetBetterTextField field, int fallback) {
		try {
			return Integer.parseInt(field.getValue().trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	protected String[] copyMessages() {
		final String[] copy = new String[maxArrivals];
		System.arraycopy(messages, 0, copy, 0, maxArrivals);
		return copy;
	}

	protected boolean[] copyHiddenRows() {
		final boolean[] copy = new boolean[maxArrivals];
		System.arraycopy(hideArrival, 0, copy, 0, maxArrivals);
		return copy;
	}

	protected Set<Long> copyPlatformIds() {
		return new HashSet<>(filterPlatformIds);
	}
}
