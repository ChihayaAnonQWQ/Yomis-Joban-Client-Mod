package com.jsblock.screen;

import com.jsblock.amberui.gui.GuiHelper;
import com.jsblock.amberui.gui.screen.TitledScreen;
import com.jsblock.amberui.gui.widget.*;
import com.jsblock.block.JobanPIDSBase;
import com.jsblock.block.PIDSRVBase;
import com.jsblock.client.JobanCustomResources;
import com.jsblock.data.PIDSPresetEntry;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * The PIDS preset picker.<br>
 * Ported from JCM 2.3's {@code com.lx862.jcm.mod.gui.screen.PIDSPresetScreen}.
 */
public class PIDSPresetScreen extends TitledScreen implements GuiHelper {
	private static final ResourceLocation PIDS_PREVIEW_BASE = new ResourceLocation("jsblock:textures/gui/pids_preview.png");
	private final Consumer<String> callback;
	private final String selectedPreset;
	private final String pidsType;

	public PIDSPresetScreen(BlockPos pos, String selectedPreset, Consumer<String> callback) {
		super(Component.empty(), false);
		this.selectedPreset = selectedPreset;
		this.callback = callback;
		this.pidsType = getPIDSType(pos);
	}

	private static String getPIDSType(BlockPos pos) {
		if (Minecraft.getInstance().level == null) return "";

		final net.minecraft.world.level.block.Block block = Minecraft.getInstance().level.getBlockState(pos).getBlock();

		if (block instanceof com.jsblock.block.PIDSRVSIL1) {
			return "rv_pids_sil_1";
		} else if (block instanceof com.jsblock.block.PIDSRVSIL2) {
			return "rv_pids_sil_2";
		} else if (block instanceof PIDSRVBase) {
			return "rv_pids";
		} else if (block instanceof com.jsblock.block.PIDSLCD) {
			return "lcd_pids";
		} else if (block instanceof com.jsblock.block.PIDS1A) {
			return "pids_1a";
		} else if (block instanceof JobanPIDSBase) {
			return "pids";
		}

		return "";
	}

	@Override
	protected void init2() {
		super.init2();
		final int contentWidth = (int) Math.min((width * 0.75), MAX_CONTENT_WIDTH);
		final int startX = (width - contentWidth) / 2;
		final int searchStartY = TEXT_PADDING * 5;
		final int startY = searchStartY + (TEXT_PADDING * 3);
		final int listViewHeight = Math.max(140, height - startY - (TEXT_PADDING * 2));

		final ListViewWidget listViewWidget = new ListViewWidget();
		addConfigEntries(listViewWidget);

		final AlwaysRenderedTextField searchBox = new AlwaysRenderedTextField(startX, searchStartY, contentWidth, 22, 60, Text.translatable("gui.jsblock.widget.search").getString());
		searchBox.setResponder(listViewWidget::setSearchTerm);
		searchBox.setValue(searchBox.getValue());

		listViewWidget.setXYSize(startX, startY, contentWidth, listViewHeight);
		addRenderableWidget(listViewWidget);
		addRenderableWidget(searchBox);
	}

	@Override
	public Component getScreenTitle() {
		return Text.translatable("gui.jsblock.pids_preset.title");
	}

	@Override
	public Component getScreenSubtitle() {
		/* The subtitle names the selected preset, not its id: the id is what the block stores, but a
		   player reading "已选择 › door_cls_psd" learns nothing about which board that is. */
		final PIDSPresetEntry selected = JobanCustomResources.getPresetEntry(selectedPreset);
		return Text.translatable("gui.jsblock.pids_preset.subtitle",
				selected == null ? selectedPreset : selected.getName());
	}

	private void addConfigEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.pids_preset.listview.category.builtin"));

		for (PIDSPresetEntry preset : JobanCustomResources.getBuiltInPresets()) {
			addPreset(listViewWidget, preset);
		}

		final java.util.List<PIDSPresetEntry> customPresets = JobanCustomResources.getCustomPresets();

		if (!customPresets.isEmpty()) {
			listViewWidget.addCategory(Text.translatable("gui.jsblock.pids_preset.listview.category.custom"));

			for (PIDSPresetEntry preset : customPresets) {
				addPreset(listViewWidget, preset);
			}
		}
	}

	private void addPreset(ListViewWidget listViewWidget, PIDSPresetEntry preset) {
		if (!preset.typeAllowed(pidsType)) return;

		final Button selectBtn = Button.builder(Text.translatable("gui.jsblock.pids_preset.listview.widget.choose"), btn -> choose(preset.getId()))
				.bounds(0, 0, 60, 20)
				.build();

		if (preset.getId().equals(selectedPreset)) {
			selectBtn.setMessage(Text.translatable("gui.jsblock.pids_preset.listview.widget.selected"));
			selectBtn.active = false;
		}

		final HorizontalWidgetSet widgetSet = new HorizontalWidgetSet();
		widgetSet.addWidget(new MappedWidget(selectBtn));
		widgetSet.setXYSize(0, 0, 100, 20);

		addWidget(selectBtn);

		final ContentItem contentItem = new ContentItem(Text.literal(preset.getName()), new MappedWidget(widgetSet), 26);
		contentItem.setIconCallback((guiGraphics, startX, startY, width, height) -> drawPIDSPreview(preset, guiGraphics, startX, startY, width, height, false));
		listViewWidget.add(contentItem);
	}

	public static void drawPIDSPreview(PIDSPresetEntry preset, GuiGraphics guiGraphics, int startX, int startY, int width, int height, boolean backgroundOnly) {
		final int offset = 6;

		// Background
		GuiHelper.drawTexture(guiGraphics, PIDS_PREVIEW_BASE, startX, startY, width, height);
		if (preset == null) return;

		GuiHelper.drawTexture(guiGraphics, preset.getThumbnail(), startX + 0.5, startY + offset + 0.5, width - 1, height - offset - 4);


	}

	private void choose(String id) {
		callback.accept(id);
		onClose2();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
