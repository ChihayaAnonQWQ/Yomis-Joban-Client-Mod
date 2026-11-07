package com.jsblock.screen;

import com.jsblock.amberui.gui.ClipStack;
import com.jsblock.amberui.gui.GuiHelper;
import com.jsblock.amberui.gui.screen.ScreenBase;
import com.jsblock.client.JobanCustomResources;
import com.jsblock.data.PIDSPreset;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Preset picker: a scrolling grid of preview tiles.
 *
 * <p>Each tile shows the preset's {@code thumbnail} when the pack supplies one, its
 * {@code background} otherwise, and a flat placeholder if it has neither. Clicking a tile hands
 * the preset id back to the PIDS screen and returns there; the grid is used because a preview is
 * the one thing a name alone cannot tell a player.</p>
 */
public class PIDSPresetScreen extends ScreenBase implements GuiHelper {
	private static final int TILE_WIDTH = 108;
	private static final int TILE_HEIGHT = 76;
	private static final int TILE_GAP = 8;
	private static final int THUMBNAIL_WIDTH = 96;
	private static final int THUMBNAIL_HEIGHT = 54;
	private static final int HEADER_HEIGHT = 40;
	private static final int FOOTER_HEIGHT = 40;

	private final Screen previousScreen;
	private final Consumer<String> onSelected;
	private final String selectedPreset;
	private final List<String> presetIds;

	private int columns = 1;
	private int left;
	private int top;
	private int scroll;

	public PIDSPresetScreen(Screen previousScreen, String selectedPreset, Consumer<String> onSelected) {
		super(Text.translatable("gui.jsblock.pids_select_preset"));
		this.previousScreen = previousScreen;
		this.selectedPreset = selectedPreset == null ? "" : selectedPreset;
		this.onSelected = onSelected;
		this.presetIds = new ArrayList<>(JobanCustomResources.PIDSPresets.keySet());
		Collections.sort(this.presetIds);
		withPreviousScreen(previousScreen);
	}

	@Override
	protected void init() {
		super.init();
		columns = Math.max(1, (width - 40) / (TILE_WIDTH + TILE_GAP));
		left = (width - (columns * TILE_WIDTH + (columns - 1) * TILE_GAP)) / 2;
		top = HEADER_HEIGHT;
		scroll = 0;

		final int buttonY = height - 30;
		final Button noneButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.pids_preset_none"), button -> {
			if (onSelected != null) {
				onSelected.accept("");
			}
			onClose();
		});
		noneButton.setX((width - 210) / 2);
		noneButton.setY(buttonY);
		noneButton.setWidth(100);
		addDrawableChild(noneButton);

		final Button cancelButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.cancel"), button -> onClose());
		cancelButton.setX((width - 210) / 2 + 110);
		cancelButton.setY(buttonY);
		cancelButton.setWidth(100);
		addDrawableChild(cancelButton);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		renderBackground(guiGraphics);
		guiGraphics.drawCenteredString(font, Text.translatable("gui.jsblock.pids_select_preset"),
				width / 2, 16, ARGB_WHITE);

		final int bottom = height - FOOTER_HEIGHT;
		final int viewportHeight = Math.max(1, bottom - top);
		final int maxScroll = Math.max(0, contentHeight() - viewportHeight);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		ClipStack.push(guiGraphics, 0, top, width, viewportHeight);
		for (int i = 0; i < presetIds.size(); i++) {
			final int column = i % columns;
			final int row = i / columns;
			final int tileX = left + column * (TILE_WIDTH + TILE_GAP);
			final int tileY = top + row * (TILE_HEIGHT + TILE_GAP) - scroll;
			if (tileY + TILE_HEIGHT < top || tileY > bottom) {
				continue;
			}
			drawTile(guiGraphics, presetIds.get(i), tileX, tileY, mouseX, mouseY);
		}
		ClipStack.pop(guiGraphics);

		if (presetIds.isEmpty()) {
			guiGraphics.drawCenteredString(font, Text.translatable("gui.jsblock.pids_preset_none"),
					width / 2, top + 20, ARGB_WHITE);
		}

		super.render(guiGraphics, mouseX, mouseY, tickDelta);
	}

	private void drawTile(GuiGraphics guiGraphics, String presetId, int tileX, int tileY, int mouseX, int mouseY) {
		final PIDSPreset preset = JobanCustomResources.PIDSPresets.get(presetId);
		final boolean hovered = mouseX >= tileX && mouseX <= tileX + TILE_WIDTH
				&& mouseY >= tileY && mouseY <= tileY + TILE_HEIGHT;
		final boolean selected = presetId.equals(selectedPreset);

		guiGraphics.fill(tileX, tileY, tileX + TILE_WIDTH, tileY + TILE_HEIGHT, hovered ? 0xFF555555 : 0xFF333333);
		GuiHelper.drawBorder(guiGraphics, tileX, tileY, TILE_WIDTH, TILE_HEIGHT, selected ? 0xFFFFFF00 : 0xFF808080);

		final int thumbnailX = tileX + (TILE_WIDTH - THUMBNAIL_WIDTH) / 2;
		final int thumbnailY = tileY + 4;
		final ResourceLocation thumbnail = preset == null ? null : preset.thumbnail;
		final ResourceLocation fallback = preset == null ? null : preset.image;
		if (thumbnail != null) {
			GuiHelper.drawTexture(guiGraphics, thumbnail, thumbnailX, thumbnailY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
		} else if (fallback != null) {
			GuiHelper.drawTexture(guiGraphics, fallback, thumbnailX, thumbnailY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
		} else {
			guiGraphics.fill(thumbnailX, thumbnailY, thumbnailX + THUMBNAIL_WIDTH, thumbnailY + THUMBNAIL_HEIGHT, 0xFF202020);
			GuiHelper.drawBorder(guiGraphics, thumbnailX, thumbnailY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, 0xFF606060);
		}

		final Component name = Text.literal(preset == null ? presetId : preset.displayName());
		final String clipped = font.plainSubstrByWidth(name.getString(), TILE_WIDTH - 8);
		guiGraphics.drawString(font, clipped, tileX + 4, tileY + THUMBNAIL_HEIGHT + 8, ARGB_WHITE, false);
	}

	private int contentHeight() {
		final int rows = (presetIds.size() + columns - 1) / Math.max(1, columns);
		return rows <= 0 ? 0 : rows * TILE_HEIGHT + (rows - 1) * TILE_GAP;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		final int bottom = height - FOOTER_HEIGHT;
		if (button == 0 && mouseY >= top && mouseY <= bottom) {
			for (int i = 0; i < presetIds.size(); i++) {
				final int column = i % columns;
				final int row = i / columns;
				final int tileX = left + column * (TILE_WIDTH + TILE_GAP);
				final int tileY = top + row * (TILE_HEIGHT + TILE_GAP) - scroll;
				if (mouseX >= tileX && mouseX <= tileX + TILE_WIDTH
						&& mouseY >= tileY && mouseY <= tileY + TILE_HEIGHT) {
					if (onSelected != null) {
						onSelected.accept(presetIds.get(i));
					}
					onClose();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		final int viewportHeight = Math.max(1, height - FOOTER_HEIGHT - top);
		final int maxScroll = Math.max(0, contentHeight() - viewportHeight);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (amount * 24)));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public Screen getPreviousScreen() {
		return previousScreen;
	}
}
