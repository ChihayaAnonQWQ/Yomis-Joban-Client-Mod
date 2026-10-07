package com.jsblock.screen;

import mtr.data.IGui;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;

/**
 * The warning shown before PIDS scripts are allowed to reach arbitrary Java classes.
 *
 * <p>A PIDS preset is a file inside a resource pack, and resource packs arrive with servers
 * and modpacks. With the class shutter off, Rhino can hand such a script the whole JVM --
 * reading and writing files, spawning processes. This screen exists so that turning that
 * protection off is a decision the player makes knowingly, which is exactly what JCM 2.x's
 * {@code ScriptRestrictionWarningScreen} does.</p>
 */
public class ScriptRestrictionWarningScreen extends ScreenMapper implements IGui {

	private static final int TEXT_PADDING = 16;
	private static final int LINE_HEIGHT = TEXT_HEIGHT + TEXT_PADDING;
	/** Same button metrics as the config screen, so the two screens look like one mod. */
	private static final int BUTTON_HEIGHT = TEXT_HEIGHT + 12;

	private final Runnable acknowledge;
	private final Runnable cancelled;

	public ScriptRestrictionWarningScreen(Runnable acknowledge, Runnable cancelled) {
		super(Text.literal(""));
		this.acknowledge = acknowledge;
		this.cancelled = cancelled;
	}

	@Override
	protected void init() {
		super.init();

		final int buttonWidth = Mth.clamp(width / 3, 0, 150);
		final int gap = 8;
		final int totalWidth = buttonWidth * 2 + gap;
		final int left = (width - totalWidth) / 2;
		final int y = height - SQUARE_SIZE - BUTTON_HEIGHT;

		final Button yes = UtilitiesClient.newButton(Text.translatable("gui.yes"), button -> {
			if (acknowledge != null) {
				acknowledge.run();
			}
			onClose();
		});
		positionButton(yes, left, buttonWidth, y);

		final Button no = UtilitiesClient.newButton(Text.translatable("gui.no"), button -> {
			if (cancelled != null) {
				cancelled.run();
			}
			onClose();
		});
		positionButton(no, left + buttonWidth + gap, buttonWidth, y);

		addDrawableChild(yes);
		addDrawableChild(no);
	}

	/** Small helper so the two buttons are positioned identically. */
	private void positionButton(Button button, int x, int width, int y) {
		button.setX(x);
		button.setY(y);
		button.setWidth(width);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		try {
			renderBackground(guiGraphics);
			drawCenteredLine(guiGraphics, Text.translatable("gui.jsblock.script_restriction.title").withStyle(ChatFormatting.YELLOW), TEXT_PADDING);
			drawCenteredLine(guiGraphics, Text.translatable("gui.jsblock.script_restriction.about"), LINE_HEIGHT * 2);
			drawCenteredLine(guiGraphics, Text.translatable("gui.jsblock.script_restriction.consequence").withStyle(ChatFormatting.UNDERLINE), LINE_HEIGHT * 3);
			drawCenteredLine(guiGraphics, Text.translatable("gui.jsblock.script_restriction.caution"), LINE_HEIGHT * 4);
			drawCenteredLine(guiGraphics, Text.translatable("gui.jsblock.script_restriction.note"), LINE_HEIGHT * 5);
			super.render(guiGraphics, mouseX, mouseY, delta);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void drawCenteredLine(GuiGraphics guiGraphics, net.minecraft.network.chat.Component text, int y) {
		guiGraphics.drawCenteredString(font, text, width / 2, y, ARGB_WHITE);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
