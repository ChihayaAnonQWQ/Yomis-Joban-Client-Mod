package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A screen with an animated title and subtitle at the top of the screen.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.screen.TitledScreen}.
 */
public abstract class TitledScreen extends AnimatedScreen implements GuiHelper {
	public static final int TEXT_PADDING = 10;
	public static final int TITLE_SCALE = 2;
	protected double elapsed = 0;

	public TitledScreen(Component title, boolean animatable) {
		super(title, animatable);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		drawBackground(guiGraphics, mouseX, mouseY, tickDelta);
		drawTitle(guiGraphics);
		drawSubtitle(guiGraphics);
		elapsed += tickDelta / 20;
		super.render(guiGraphics, mouseX, mouseY, tickDelta);
	}

	public void drawBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		super.renderBackground(guiGraphics);
	}

	private void drawTitle(GuiGraphics guiGraphics) {
		final int titleHeight = (LINE_HEIGHT * TITLE_SCALE);
		final Component titleText = getScreenTitle();

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(width / 2.0, TEXT_PADDING, 0);
		guiGraphics.pose().translate(0, -((titleHeight + TEXT_PADDING) * (1 - animationProgress)), 0);
		guiGraphics.pose().scale(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE);
		GuiHelper.scaleToFit(guiGraphics, GuiHelper.getFont().width(titleText), width / (float) TITLE_SCALE, true);
		guiGraphics.drawCenteredString(GuiHelper.getFont(), titleText, 0, 0, 0xFFFFFFFF);
		guiGraphics.pose().popPose();
	}

	private void drawSubtitle(GuiGraphics guiGraphics) {
		final double titleHeight = (LINE_HEIGHT * TITLE_SCALE);
		final Component subtitleText = getScreenSubtitle();

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(width / 2.0, titleHeight * animationProgress, 0);
		guiGraphics.pose().translate(0, TEXT_PADDING * 1.5, 0);
		GuiHelper.scaleToFit(guiGraphics, GuiHelper.getFont().width(subtitleText), width, true);
		guiGraphics.drawCenteredString(GuiHelper.getFont(), subtitleText, 0, 0, 0xFFFFFFFF);
		guiGraphics.pose().popPose();
	}

	public abstract Component getScreenTitle();

	public abstract Component getScreenSubtitle();
}
