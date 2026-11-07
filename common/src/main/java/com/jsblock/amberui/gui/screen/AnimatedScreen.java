package com.jsblock.amberui.gui.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A screen whose title and subtitle slide in when it opens.
 *
 * <p>The original also animated the close by delaying {@code onClose2}; MTR 3's save-on-close
 * screens cannot defer closing without swallowing the save, so only the opening animation is
 * kept and closing is immediate.</p>
 */
public abstract class AnimatedScreen extends ScreenBase {
	protected double linearAnimationProgress = 0;
	protected double animationProgress;
	protected boolean closing = false;
	private final boolean shouldAnimate;

	protected AnimatedScreen(boolean animatable) {
		super();
		this.shouldAnimate = animatable;
		this.animationProgress = animatable ? 0 : 1;
		if (!animatable) {
			this.linearAnimationProgress = 1;
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		super.render(guiGraphics, mouseX, mouseY, tickDelta);

		final double frameDelta = tickDelta / 20.0;
		if (!shouldAnimate) {
			linearAnimationProgress = 1;
		} else if (!closing) {
			linearAnimationProgress = Math.min(1, linearAnimationProgress + frameDelta);
		}
		animationProgress = getEaseAnimation();
	}

	private double getEaseAnimation() {
		final double x = linearAnimationProgress;
		return 1 - Math.pow(1 - x, 5);
	}
}
