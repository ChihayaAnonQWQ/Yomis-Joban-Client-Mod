package com.jsblock.amberui.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws a large centred title and a smaller subtitle above the screen's widgets.
 *
 * <p>{@code GraphicsHolder}'s push/translate/scale/drawCenteredText combination maps one-to-one
 * onto a {@link GuiGraphics} pose stack; the only change is that text is drawn with the screen's
 * own {@code Font} instead of through MTR 4's render helper.</p>
 */
public abstract class TitledScreen extends AnimatedScreen {
	public static final int TEXT_PADDING = 10;
	public static final int TITLE_SCALE = 2;
	protected double elapsed = 0;

	protected TitledScreen(boolean animatable) {
		super(animatable);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		drawBackground(guiGraphics, mouseX, mouseY, tickDelta);
		drawTitle(guiGraphics);
		drawSubtitle(guiGraphics);
		elapsed += tickDelta / 20.0;
		super.render(guiGraphics, mouseX, mouseY, tickDelta);
	}

	public void drawBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		super.renderBackground(guiGraphics);
	}

	private void drawTitle(GuiGraphics guiGraphics) {
		final int titleHeight = 9 * TITLE_SCALE;
		final Component titleText = getScreenTitle();

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(width / 2.0, TEXT_PADDING, 0);
		guiGraphics.pose().translate(0, -((titleHeight + TEXT_PADDING) * (1 - animationProgress)), 0);
		guiGraphics.pose().scale(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE);
		scaleToFit(guiGraphics, font.width(titleText), width / (float) TITLE_SCALE);
		guiGraphics.drawCenteredString(font, titleText, 0, 0, 0xFFFFFFFF);
		guiGraphics.pose().popPose();
	}

	private void drawSubtitle(GuiGraphics guiGraphics) {
		final double titleHeight = 9 * TITLE_SCALE;
		final Component subtitleText = getScreenSubtitle();

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(width / 2.0, titleHeight * animationProgress, 0);
		guiGraphics.pose().translate(0, TEXT_PADDING * 1.5, 0);
		scaleToFit(guiGraphics, font.width(subtitleText), width);
		guiGraphics.drawCenteredString(font, subtitleText, 0, 0, 0xFFFFFFFF);
		guiGraphics.pose().popPose();
	}

	private static void scaleToFit(GuiGraphics guiGraphics, int textWidth, float availableWidth) {
		if (textWidth <= 0 || availableWidth <= 0 || textWidth <= availableWidth) {
			return;
		}
		final float scale = availableWidth / textWidth;
		guiGraphics.pose().scale(scale, scale, 1);
	}

	public abstract Component getScreenTitle();

	public abstract Component getScreenSubtitle();
}
