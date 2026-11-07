package com.jsblock.screen;

import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.amberui.gui.widget.WidgetSet;
import com.jsblock.block.PIDSProjector;
import com.jsblock.packet.PacketClient;
import mtr.mappings.Text;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

/**
 * Configuration of the PIDS Projector, on the same AmberUI list as the other PIDS screens.
 *
 * <p>A projector has no companion block, so it cannot reuse the two-block PIDS screen; it shares
 * everything that screen offers that a projector can use -- the preset, the platform filter, the
 * custom messages and the hidden rows -- and adds its own placement entries (offset, rotation,
 * scale) above them. Everything is sent to the server when the screen is saved.</p>
 *
 * <p>Unlike the other PIDS screens, typing here moves the real panel on this client as it is typed,
 * so a placement can be judged before it is saved; discarding puts the panel back.</p>
 */
public class PIDSProjectorScreen extends PIDSBlockConfigScreen {
	/** Offset X, offset Y, offset Z, rotation X, rotation Y, rotation Z, scale. */
	private static final double[] DEFAULTS = {0, 0, 0, 0, 0, 0, 1};
	/** Wide enough for three values side by side with a little room to type. */
	private static final int FIELD_SET_WIDTH = 140;
	private static final int FIELD_MAX_LENGTH = 16;
	/** The scale is the only field on its row, so it is sized on its own. */
	private static final int SCALE_INDEX = 6;
	private static final int SCALE_FIELD_WIDTH = 60;

	private final WidgetBetterTextField[] transformFields = new WidgetBetterTextField[DEFAULTS.length];
	private final WidgetSet positionWidgetSet = new WidgetSet(20, 0);
	private final WidgetSet rotationWidgetSet = new WidgetSet(20, 0);
	/** What the screen opened with, so an unreadable field falls back to the block's own value. */
	private final double[] initialValues = DEFAULTS.clone();
	/** What the client's panel is showing right now, so a half-typed box does not snap it around. */
	private final double[] previewed = DEFAULTS.clone();

	public PIDSProjectorScreen(BlockPos pos, String presetID, String[] messages, boolean[] rowHidden,
							   boolean hidePlatformNumber, Set<Long> platformIds,
							   double offsetX, double offsetY, double offsetZ,
							   double rotateX, double rotateY, double rotateZ, double scale) {
		super(pos, PIDSProjector.TileEntityBlockPIDSProjector.MAX_ARRIVALS, true, presetID, hidePlatformNumber,
				messages, rowHidden, platformIds);

		initialValues[0] = offsetX;
		initialValues[1] = offsetY;
		initialValues[2] = offsetZ;
		initialValues[3] = rotateX;
		initialValues[4] = rotateY;
		initialValues[5] = rotateZ;
		initialValues[SCALE_INDEX] = scale <= 0 ? DEFAULTS[SCALE_INDEX] : scale;

		for (int i = 0; i < transformFields.length; i++) {
			transformFields[i] = TextFields.setValue(new WidgetBetterTextField("", FIELD_MAX_LENGTH), format(initialValues[i]));
		}

		/* The scale sits on its own, so it needs a width of its own: a text field is built zero wide. */
		transformFields[SCALE_INDEX].setWidth(SCALE_FIELD_WIDTH);

		/* Filled here rather than in addConfigEntries, which runs again whenever the screen is
		   resized and would otherwise stack the fields up in the same row twice. */
		positionWidgetSet.addWidget(transformFields[0]);
		positionWidgetSet.addWidget(transformFields[1]);
		positionWidgetSet.addWidget(transformFields[2]);

		rotationWidgetSet.addWidget(transformFields[3]);
		rotationWidgetSet.addWidget(transformFields[4]);
		rotationWidgetSet.addWidget(transformFields[5]);

		System.arraycopy(initialValues, 0, previewed, 0, previewed.length);

		/* Attached after the boxes are seeded, because setting a value notifies the responder. */
		for (WidgetBetterTextField transformField : transformFields) {
			transformField.setResponder(value -> previewFromFields());
		}
	}

	@Override
	public Component getScreenTitle() {
		return Text.translatable("gui.jsblock.pids_projector");
	}

	@Override
	public void addConfigEntries(ListViewWidget listViewWidget) {
		listViewWidget.addCategory(Text.translatable("gui.jsblock.pids.listview.category.projection"));

		/* Registered with the screen so the boxes can be typed into; the list entry below draws
		   them through their widget set. */
		for (WidgetBetterTextField transformField : transformFields) {
			addWidget(transformField);
		}

		positionWidgetSet.setXYSize(0, 0, FIELD_SET_WIDTH, 20);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_projector.offset"), new MappedWidget(positionWidgetSet));

		rotationWidgetSet.setXYSize(0, 0, FIELD_SET_WIDTH, 20);
		listViewWidget.add(Text.translatable("gui.jsblock.pids_projector.rotate"), new MappedWidget(rotationWidgetSet));

		listViewWidget.add(Text.translatable("gui.jsblock.pids_projector.scale"), new MappedWidget(transformFields[SCALE_INDEX]));

		listViewWidget.addCategory(Text.translatable("gui.jsblock.pids.listview.category.pids"));
		super.addConfigEntries(listViewWidget);
	}

	@Override
	public void tick() {
		super.tick();

		for (WidgetBetterTextField transformField : transformFields) {
			transformField.tick();
		}
	}

	@Override
	protected void onSave() {
		final double[] values = new double[transformFields.length];

		for (int i = 0; i < values.length; i++) {
			values[i] = parse(transformFields[i], initialValues[i]);
		}

		if (values[SCALE_INDEX] <= 0) {
			values[SCALE_INDEX] = DEFAULTS[SCALE_INDEX];
		}

		PacketClient.sendPIDSProjectorC2S(pos1, presetId == null ? "" : presetId, collectCustomMessages(),
				collectRowHidden(), isHidePlatformNumberSelected(), collectFilteredPlatforms(),
				values[0], values[1], values[2], values[3], values[4], values[5], values[6]);

		/* What the server was told is what the panel should show until its sync comes back. */
		System.arraycopy(values, 0, previewed, 0, previewed.length);
		applyPreview();
	}

	@Override
	protected void onDiscard() {
		/* The panel moved while the player typed, so put it back where the screen found it. */
		System.arraycopy(initialValues, 0, previewed, 0, previewed.length);
		applyPreview();
	}

	/** Reads the boxes into {@link #previewed}, keeping the last readable value of a box being typed in. */
	private void previewFromFields() {
		for (int i = 0; i < previewed.length; i++) {
			/* An unreadable box is one being typed in, such as a lone minus sign: keep what it had. */
			previewed[i] = parse(transformFields[i], previewed[i]);
		}

		if (previewed[SCALE_INDEX] <= 0) {
			previewed[SCALE_INDEX] = DEFAULTS[SCALE_INDEX];
		}

		applyPreview();
	}

	/**
	 * Moves the projector's own panel on this client, which its renderer reads every frame.
	 *
	 * <p>The block entity's setter is safe to call from a screen: its data sync only does anything in
	 * a server level, so this redraws and nothing more. The server hears about the placement when the
	 * screen is saved.</p>
	 */
	private void applyPreview() {
		final Level level = Minecraft.getInstance().level;

		if (level == null) {
			return;
		}

		final BlockEntity blockEntity = level.getBlockEntity(pos1);

		if (!(blockEntity instanceof PIDSProjector.TileEntityBlockPIDSProjector)) {
			return;
		}

		((PIDSProjector.TileEntityBlockPIDSProjector) blockEntity).setProjectorTransform(previewed[0], previewed[1],
				previewed[2], previewed[3], previewed[4], previewed[5], previewed[SCALE_INDEX]);
	}

	/** Renders a whole number without a trailing {@code .0}, which is how a player would type it. */
	private static String format(double value) {
		return value == Math.floor(value) && !Double.isInfinite(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private static double parse(WidgetBetterTextField field, double fallback) {
		try {
			return Double.parseDouble(field.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
