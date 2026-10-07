package com.jsblock.screen;

import com.jsblock.client.JobanCustomResources;
import com.jsblock.packet.PacketClient;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import mtr.screen.PIDSConfigScreen;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PIDS Projector configuration: the preset it draws, the platform it draws for, and where it draws.
 *
 * <p>Ported from JCM 2.x's {@code PIDSProjectorScreen}. Its fields are the projector's own — the
 * offset, rotation and scale that place the panel in the air — plus the two settings the ordinary
 * PIDS screen would have offered that a projector can use: the preset, and MTR's platform filter,
 * which is reused wholesale so both screens behave the same way. Saved on close, as the mod's other
 * configuration screens are.</p>
 */
public class PIDSProjectorScreen extends ScreenMapper implements IGui {

	private final BlockPos pos;
	private final WidgetSuggestionTextField textBoxPreset;
	/** offset x/y/z, rotation x/y/z, scale — the projector's whole placement. */
	private final WidgetBetterTextField[] fields = new WidgetBetterTextField[7];
	/** The same pair the ordinary PIDS screen uses: "detect nearby" plus MTR's platform picker. */
	private final WidgetBetterCheckbox selectAllCheckbox;
	private final Button filterButton;
	private final Set<Long> filterPlatformIds = new HashSet<>();
	/** The count currently written on the filter button, so it is only rewritten when it changes. */
	private int lastFilteredCount = -1;

	/**
	 * The values the screen opened with.
	 *
	 * <p>They are applied in {@link #init()} rather than here: a field's text only appears once the
	 * screen has been initialised, which is the order every other configuration screen in the mod
	 * uses. Setting them in the constructor leaves every box blank until it is clicked, which is
	 * what this screen did at first.</p>
	 */
	private final String initialPresetID;
	private final double[] initialValues;

	private static final double[] DEFAULTS = {0, 0, 0, 0, 0, 0, 1};
	/** One column of an x/y/z row. */
	private static final int COLUMN_WIDTH = 58;
	private static final int COLUMN_GAP = 4;
	private static final int WIDE_FIELD_WIDTH = COLUMN_WIDTH * 3 + COLUMN_GAP * 2;
	private static final int TEXT_PADDING = 16;
	private static final int FINAL_TEXT_HEIGHT = TEXT_HEIGHT + TEXT_PADDING;

	public PIDSProjectorScreen(BlockPos pos, String presetID, Set<Long> platformIds,
							   double offsetX, double offsetY, double offsetZ,
							   double rotateX, double rotateY, double rotateZ, double scale) {
		super(Text.literal(""));
		this.pos = pos;

		final List<String> presetIds = new ArrayList<>(JobanCustomResources.PIDSPresets.keySet());
		presetIds.sort(String::compareTo);
		textBoxPreset = new WidgetSuggestionTextField(presetID == null ? "" : presetID, presetIds, 128, false);
		this.initialPresetID = presetID == null ? "" : presetID;

		this.initialValues = new double[] {offsetX, offsetY, offsetZ, rotateX, rotateY, rotateZ, scale};
		for (int i = 0; i < fields.length; i++) {
			/* The value goes in as the constructor's first argument.
			   
			   That argument is what this widget shows as its default, and setValue alone does not
			   make the text appear -- the fields opened blank and only filled in once clicked. Every
			   other screen in the mod passes its default the same way. */
			fields[i] = new WidgetBetterTextField(format(initialValues[i]), 16);
		}

		filterPlatformIds.addAll(platformIds);
		selectAllCheckbox = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE,
				Text.translatable("gui.mtr.automatically_detect_nearby_platform"), checked -> {
		});
		selectAllCheckbox.setChecked(filterPlatformIds.isEmpty());
		filterButton = PIDSConfigScreen.getPlatformFilterButton(pos, selectAllCheckbox, filterPlatformIds, this);
	}

	private static String format(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private static double parse(WidgetBetterTextField field, double fallback) {
		try {
			return Double.parseDouble(field.getValue().trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	/** The left edge of column {@code index} of a three-column row, counted from the right. */
	private int columnX(int index) {
		return width - SQUARE_SIZE - WIDE_FIELD_WIDTH + index * (COLUMN_WIDTH + COLUMN_GAP);
	}

	@Override
	protected void init() {
		super.init();
		int row = 1;

		/* Values first, then position and add: the order every other configuration screen uses, and
		   the one that makes the text appear without a click. */
		textBoxPreset.setValue(initialPresetID);
		for (int i = 0; i < fields.length; i++) {
			fields[i].setValue(format(initialValues[i]));
		}

		IDrawing.setPositionAndWidth(textBoxPreset, width - SQUARE_SIZE - WIDE_FIELD_WIDTH,
				FINAL_TEXT_HEIGHT * row + SQUARE_SIZE, WIDE_FIELD_WIDTH);
		addDrawableChild(textBoxPreset);
		row++;

		/* Offset and rotation are three numbers each, so they get three columns; the preset and the
		   scale are single values and get the whole width. Getting each field its own column is the
		   point: the first version put all three at one x and they drew on top of each other. */
		for (int i = 0; i < fields.length; i++) {
			final int column = i % 3;
			IDrawing.setPositionAndWidth(fields[i], columnX(column), FINAL_TEXT_HEIGHT * row + SQUARE_SIZE, COLUMN_WIDTH);
			addDrawableChild(fields[i]);
			/* scale is the seventh field: a row of its own, in the first column. */
			if (column == 2) {
				row++;
			}
		}

		/* Checkbox on one row, then the filter button on the next with its count beside it -- the
		   arrangement the ordinary PIDS screen uses, and the reason the count is drawn at this row
		   in render(). */
		row += 2;
		IDrawing.setPositionAndWidth(selectAllCheckbox, SQUARE_SIZE, FINAL_TEXT_HEIGHT * row + SQUARE_SIZE, 220);
		addDrawableChild(selectAllCheckbox);
		row++;
		IDrawing.setPositionAndWidth(filterButton, columnX(0), FINAL_TEXT_HEIGHT * row + SQUARE_SIZE, WIDE_FIELD_WIDTH);
		addDrawableChild(filterButton);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		try {
			renderBackground(guiGraphics);
			guiGraphics.drawCenteredString(font, Text.translatable("gui.jsblock.pids_projector"), width / 2, TEXT_PADDING, ARGB_WHITE);

			/* One label per row, at the rows init() placed: preset, offset, rotation, scale. */
			int row = 1;
			drawRowLabel(guiGraphics, "preset", row++);
			drawRowLabel(guiGraphics, "offset", row++);
			drawRowLabel(guiGraphics, "rotate", row++);
			drawRowLabel(guiGraphics, "scale", row);
			/* The count goes on the button itself rather than on a label beside it: the button is
			   what the count is about, and MTR's own caption already says "filtered platforms".
			   Only written when it changes, so a screen that is merely being looked at does not
			   rebuild a Component every frame. */
			final int filteredCount = selectAllCheckbox.selected() ? 0 : filterPlatformIds.size();
			if (filteredCount != lastFilteredCount) {
				lastFilteredCount = filteredCount;
				filterButton.setMessage(Text.translatable("gui.mtr.filtered_platforms", filteredCount));
			}

			super.render(guiGraphics, mouseX, mouseY, delta);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void drawRowLabel(GuiGraphics guiGraphics, String key, int row) {
		/* Same offset as init() gives the fields, so a label sits on its own field's line rather than
		   one row below it -- which is where a copied SQUARE_SIZE * 2 from another screen put it. */
		guiGraphics.drawString(font, Text.translatable("gui.jsblock.pids_projector." + key),
				SQUARE_SIZE, FINAL_TEXT_HEIGHT * row + SQUARE_SIZE, ARGB_WHITE);
	}

	@Override
	public void onClose() {
		final double[] values = new double[fields.length];
		for (int i = 0; i < fields.length; i++) {
			values[i] = parse(fields[i], DEFAULTS[i]);
		}
		if (selectAllCheckbox.selected()) {
			filterPlatformIds.clear();
		}
		PacketClient.sendPIDSProjectorC2S(pos, textBoxPreset.getValue(), filterPlatformIds,
				values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
