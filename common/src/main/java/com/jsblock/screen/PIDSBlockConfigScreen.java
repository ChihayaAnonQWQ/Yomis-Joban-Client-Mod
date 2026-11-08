package com.jsblock.screen;

import com.jsblock.amberui.gui.screen.BlockConfigListScreen;
import com.jsblock.amberui.gui.widget.ContentItem;
import com.jsblock.amberui.gui.widget.HorizontalWidgetSet;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.block.BlockPIDSBaseHorizontal;
import com.jsblock.block.JobanPIDSBase;
import com.jsblock.client.JobanCustomResources;
import mtr.client.ClientData;
import mtr.data.Platform;
import mtr.mappings.Text;
import mtr.screen.PIDSConfigScreen;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The configuration every PIDS block shares: the preset it draws, the platforms it draws for, one
 * custom message and one hidden row per arrival, and the hide-platform-number option.<br>
 * Subclasses add their own entries on top of these.
 */
public abstract class PIDSBlockConfigScreen extends BlockConfigListScreen {
	protected static final int MAX_MESSAGE_LENGTH = 100;
	protected static final int ROW_HIDDEN_WIDTH = 95;
	protected static final int ROW_WIDGET_GAP = 18;

	protected final BlockPos pos1;
	protected final boolean withPlatformNumberOption;

	protected final WidgetBetterTextField[] customMessagesWidgets;
	protected final WidgetBetterCheckbox[] rowHiddenWidgets;
	protected final WidgetBetterCheckbox selectAllCheckbox;
	protected final WidgetBetterCheckbox hidePlatformNumberWidget;
	protected final Button choosePresetButton;
	protected final Button choosePlatformButton;

	/** The messages and hidden rows the screen was opened with, still unchecked by the player. */
	protected final String[] customMessages;
	protected final boolean[] rowHidden;
	protected final Set<Long> filteredPlatforms;
	protected String presetId;

	/**
	 * @param messages    the custom messages the screen starts with, or {@code null} to read them off the block entity
	 * @param rowHidden   the hidden rows the screen starts with, or {@code null} to read them off the block entity
	 * @param platformIds the platforms the screen starts with, or {@code null} to read them off the block entity
	 */
	protected PIDSBlockConfigScreen(BlockPos pos1, int maxArrivals, boolean withPlatformNumberOption, String presetId,
									boolean hidePlatformNumber, String[] messages, boolean[] rowHidden, Set<Long> platformIds) {
		super(pos1);
		this.pos1 = pos1;
		this.withPlatformNumberOption = withPlatformNumberOption;
		this.presetId = presetId == null ? "" : presetId;

		final int arrivalCount = Math.max(1, maxArrivals);
		final Level level = Minecraft.getInstance().level;
		final BlockEntity blockEntity = level == null ? null : level.getBlockEntity(pos1);
		final JobanPIDSBase.TileEntityBlockJobanPIDS pidsEntity = blockEntity instanceof JobanPIDSBase.TileEntityBlockJobanPIDS
				? (JobanPIDSBase.TileEntityBlockJobanPIDS) blockEntity
				: null;

		if (messages == null) {
			messages = new String[arrivalCount];

			for (int i = 0; i < arrivalCount; i++) {
				messages[i] = pidsEntity != null && i < pidsEntity.getMaxArrivals() ? pidsEntity.getMessage(i) : "";
			}
		}

		if (rowHidden == null) {
			rowHidden = new boolean[arrivalCount];

			for (int i = 0; i < arrivalCount; i++) {
				rowHidden[i] = pidsEntity != null && i < pidsEntity.getMaxArrivals() && pidsEntity.getHideArrival(i);
			}
		}

		if (platformIds == null) {
			platformIds = blockEntity instanceof BlockPIDSBaseHorizontal.TileEntityBlockPIDSBaseHorizontal
					? ((BlockPIDSBaseHorizontal.TileEntityBlockPIDSBaseHorizontal) blockEntity).getPlatformIds()
					: new HashSet<>();
		}

		this.customMessages = Arrays.copyOf(messages, arrivalCount);
		this.rowHidden = Arrays.copyOf(rowHidden, arrivalCount);
		this.filteredPlatforms = new HashSet<>(platformIds);

		this.customMessagesWidgets = new WidgetBetterTextField[arrivalCount];
		this.rowHiddenWidgets = new WidgetBetterCheckbox[arrivalCount];

		for (int i = 0; i < arrivalCount; i++) {
			final WidgetBetterTextField messageField = new WidgetBetterTextField("", MAX_MESSAGE_LENGTH);
			messageField.setHint(Text.translatable("gui.jsblock.pids.listview.widget.custom_message"));
			TextFields.setValue(messageField, this.customMessages[i]);
			this.customMessagesWidgets[i] = messageField;

			final WidgetBetterCheckbox rowHiddenCheckbox = new WidgetBetterCheckbox(0, 0, ROW_HIDDEN_WIDTH, SQUARE_SIZE, Text.translatable("gui.jsblock.pids.listview.widget.row_hidden"), checked -> {
			});
			rowHiddenCheckbox.setChecked(this.rowHidden[i]);
			this.rowHiddenWidgets[i] = rowHiddenCheckbox;
		}

		this.hidePlatformNumberWidget = new WidgetBetterCheckbox(0, 0, SQUARE_SIZE, SQUARE_SIZE, Component.empty(), checked -> {
		});
		this.hidePlatformNumberWidget.setChecked(hidePlatformNumber);

		this.selectAllCheckbox = new WidgetBetterCheckbox(0, 0, SQUARE_SIZE, SQUARE_SIZE, Text.translatable("gui.mtr.automatically_detect_nearby_platform"), checked -> {
		});
		this.selectAllCheckbox.setChecked(this.filteredPlatforms.isEmpty());

		this.choosePresetButton = Button.builder(Text.translatable("gui.jsblock.pids.listview.widget.choose_preset"), btn -> openPresetPicker())
				.bounds(0, 0, 60, 20)
				.build();

		this.choosePlatformButton = PIDSConfigScreen.getPlatformFilterButton(pos1, selectAllCheckbox, this.filteredPlatforms, this);
		this.choosePlatformButton.setMessage(Text.translatable("gui.jsblock.pids.listview.widget.change_platform"));
		this.choosePlatformButton.setWidth(60);
	}

	@Override
	public Component getScreenTitle() {
		final Level level = Minecraft.getInstance().level;

		if (level != null) {
			return Text.translatable(level.getBlockState(pos1).getBlock().getDescriptionId());
		}

		return Text.translatable("block.jsblock.pids_1a");
	}

	private void openPresetPicker() {
		Minecraft.getInstance().setScreen(new PIDSPresetScreen(pos1, presetId, preset -> this.presetId = preset).withPreviousScreen(this));
	}

	/** @return the preset's display name, or a dash when nothing is selected */
	protected static String presetName(String currentId) {
		final com.jsblock.data.PIDSPresetEntry entry = JobanCustomResources.getPresetEntry(currentId);
		return entry == null ? "-" : entry.getName();
	}

	/** Shortens a preset name so a long resource-pack name cannot push the row out of shape. */
	protected static String truncate(String text, int maxChars) {
		if (text == null) {
			return "-";
		}
		return text.length() <= maxChars ? text : text.substring(0, Math.max(1, maxChars - 1)) + "\u2026";
	}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		// Preset button
		addWidget(choosePresetButton);
		final ContentItem presetEntry = new ContentItem(Text.translatable("gui.jsblock.pids.listview.title.pids_preset"), new MappedWidget(choosePresetButton), 26);
		presetEntry.setIconCallback((guiGraphics, startX, startY, width, height) -> PIDSPresetScreen.drawPIDSPreview(JobanCustomResources.getPresetEntry(presetId), guiGraphics, startX, startY, width, height, true));
		listViewWidget.add(presetEntry);

		// Filter Platform button
		final List<String> platformList = new ArrayList<>();

		for (long filteredPlatform : filteredPlatforms) {
			String platformName = null;

			for (Platform platform : ClientData.PLATFORMS) {
				if (platform.id == filteredPlatform) {
					platformName = platform.name;
					break;
				}
			}

			platformList.add(platformName == null ? "?" : platformName);
		}

		final Component filteredPlatformTitle = filteredPlatforms.isEmpty()
				? Text.translatable("gui.jsblock.pids.listview.title.filtered_platform.nearby")
				: Text.translatable("gui.jsblock.pids.listview.title.filtered_platform", String.join(",", platformList));
		listViewWidget.add(filteredPlatformTitle, new MappedWidget(choosePlatformButton));
		addWidget(choosePlatformButton);

		// One row per arrival: custom message + hide arrival
		for (int i = 0; i < customMessagesWidgets.length; i++) {
			addWidget(customMessagesWidgets[i]);
			addWidget(rowHiddenWidgets[i]);

			final int rowWidth = listViewWidget.getWidth();
			rowHiddenWidgets[i].setWidth(ROW_HIDDEN_WIDTH);
			customMessagesWidgets[i].setWidth(rowWidth - rowHiddenWidgets[i].getWidth() - ROW_WIDGET_GAP);

			final HorizontalWidgetSet widgetSet = new HorizontalWidgetSet();
			widgetSet.addWidget(new MappedWidget(customMessagesWidgets[i]));
			widgetSet.addWidget(new MappedWidget(rowHiddenWidgets[i]));
			widgetSet.setXYSize(listViewWidget.getX(), 20, listViewWidget.getWidth(), 20);
			listViewWidget.add((Component) null, new MappedWidget(widgetSet));
		}

		if (withPlatformNumberOption) {
			addWidget(hidePlatformNumberWidget);
			listViewWidget.add(Text.translatable("gui.jsblock.pids.listview.title.hide_platform_number"), new MappedWidget(hidePlatformNumberWidget));
		}
	}

	@Override
	public void tick() {
		super.tick();

		for (WidgetBetterTextField textField : customMessagesWidgets) {
			textField.tick();
		}
	}

	protected String[] collectCustomMessages() {
		final String[] collected = new String[customMessagesWidgets.length];

		for (int i = 0; i < collected.length; i++) {
			collected[i] = customMessagesWidgets[i].getValue();
		}

		return collected;
	}

	protected boolean[] collectRowHidden() {
		final boolean[] collected = new boolean[rowHiddenWidgets.length];

		for (int i = 0; i < collected.length; i++) {
			collected[i] = rowHiddenWidgets[i].selected();
		}

		return collected;
	}

	protected boolean isHidePlatformNumberSelected() {
		return hidePlatformNumberWidget.selected();
	}

	/** The platforms as the player left them: an empty set means "every platform nearby". */
	protected Set<Long> collectFilteredPlatforms() {
		if (selectAllCheckbox.selected()) {
			filteredPlatforms.clear();
		}

		return filteredPlatforms;
	}
}
