package com.jsblock.screen;

import com.jsblock.amberui.gui.widget.DoubleTextField;
import com.jsblock.amberui.gui.widget.HorizontalWidgetSet;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.packet.PacketClient;
import mtr.mappings.Text;
import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Configuration screen for the PIDS projector.
 *
 * <p>The projector has the same shared half as every other PIDS board -- preset, platform filter,
 * platform-number switch, custom messages and hidden rows -- plus the seven numbers that place
 * the projection in the air: offset x/y/z, rotation x/y/z and scale. Those seven use AmberUI's
 * {@link DoubleTextField}, which accepts decimals and nudges them with the scroll wheel.</p>
 *
 * <p>The custom-message / hidden-row half is wired to the same fields as the other screens; it is
 * not a leftover: a script reads the text back through {@code pids.getCustomMessage(i)} and the
 * rows through {@code pids.isRowHidden(i)}, so a projector that could not be told what to show
 * would only be announcing the timetable.</p>
 */
public class PIDSProjectorScreen extends PIDSBlockConfigScreen {
	private static final double[] DEFAULTS = {0, 0, 0, 0, 0, 0, 1};
	private static final int COLUMN_WIDTH = 52;
	private static final int COLUMN_GAP = 4;

	private final double[] currentValues = new double[7];
	private final DoubleTextField[] numberFields = new DoubleTextField[7];

	public PIDSProjectorScreen(BlockPos pos, String presetID, String[] messages, boolean[] rowHidden,
							   boolean hidePlatformNumber, Set<Long> platformIds,
							   double offsetX, double offsetY, double offsetZ,
							   double rotateX, double rotateY, double rotateZ, double scale) {
		super(pos, pos,
				messages == null || messages.length == 0 ? 4 : messages.length,
				messages, rowHidden, platformIds, presetID, hidePlatformNumber, true,
				Text.translatable("gui.jsblock.pids_projector"));

		currentValues[0] = offsetX;
		currentValues[1] = offsetY;
		currentValues[2] = offsetZ;
		currentValues[3] = rotateX;
		currentValues[4] = rotateY;
		currentValues[5] = rotateZ;
		currentValues[6] = scale;
	}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		addGeneralEntries(listViewWidget);
		addProjectorEntries(listViewWidget);
		addDisplayEntries(listViewWidget);
		addMessageEntries(listViewWidget);
	}

	private void addProjectorEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.pids_projector"));

		numberFields[0] = newTextField(0, -256, 256);
		numberFields[1] = newTextField(1, -256, 256);
		numberFields[2] = newTextField(2, -256, 256);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_projector.offset"),
				new MappedWidget(row(numberFields[0], numberFields[1], numberFields[2])));

		numberFields[3] = newTextField(3, -360, 360);
		numberFields[4] = newTextField(4, -360, 360);
		numberFields[5] = newTextField(5, -360, 360);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_projector.rotate"),
				new MappedWidget(row(numberFields[3], numberFields[4], numberFields[5])));

		numberFields[6] = newTextField(6, 0.01, 16);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_projector.scale"),
				new MappedWidget(numberFields[6]));
	}

	private DoubleTextField newTextField(int index, double min, double max) {
		final DoubleTextField field = new DoubleTextField(0, 0, COLUMN_WIDTH, 20, min, max, currentValues[index]);
		field.setWidth(COLUMN_WIDTH);
		return field;
	}

	private static HorizontalWidgetSet row(DoubleTextField first, DoubleTextField second, DoubleTextField third) {
		final HorizontalWidgetSet row = new HorizontalWidgetSet(COLUMN_GAP);
		row.addWidget(new MappedWidget(first));
		row.addWidget(new MappedWidget(second));
		row.addWidget(new MappedWidget(third));
		row.setXYSize(0, 0, COLUMN_WIDTH * 3 + COLUMN_GAP * 2, 20);
		return row;
	}

	@Override
	protected void captureAll() {
		super.captureAll();
		for (int i = 0; i < numberFields.length; i++) {
			if (numberFields[i] != null) {
				currentValues[i] = numberFields[i].getNumber();
			}
		}
	}

	@Override
	protected void onSave() {
		captureAll();
		PacketClient.sendPIDSProjectorC2S(pos1, presetID, copyMessages(), copyHiddenRows(), hidePlatformNumber, copyPlatformIds(),
				valueOr(0), valueOr(1), valueOr(2), valueOr(3), valueOr(4), valueOr(5), valueOr(6));
	}

	private double valueOr(int index) {
		return Double.isFinite(currentValues[index]) ? currentValues[index] : DEFAULTS[index];
	}
}
