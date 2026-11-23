package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.ClipStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A screen that plays a slide-in animation when opened, and a slide-out animation when closed.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.screen.AnimatedScreen}.
 */
public class AnimatedScreen extends ScreenBase {
	protected double linearAnimationProgress = 0;
	protected double animationProgress;
	protected boolean closing = false;
	private final boolean shouldAnimate;

	public AnimatedScreen(Component title, boolean animatable) {
		super(title);
		this.shouldAnimate = animatable;
		this.animationProgress = animatable ? 0 : 1;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		ClipStack.reset();
		super.render(guiGraphics, mouseX, mouseY, tickDelta);

		final double frameDelta = (tickDelta / 20);

		if(!shouldAnimate) {
			linearAnimationProgress = 1;
		} else {
			linearAnimationProgress = closing ? Math.max(0, linearAnimationProgress - frameDelta) : Math.min(1, linearAnimationProgress + frameDelta);

			if(linearAnimationProgress <= 0 && closing) {
				onClose2();
			}
		}

		animationProgress = getEaseAnimation();
		ClipStack.reset();
	}

	@Override
	public void onClose2() {
		if(closing || !shouldAnimate) {
			super.onClose2();
		} else {
			closing = true;
		}
	}

	private double getEaseAnimation() {
		final double x = linearAnimationProgress;
		return 1 - Math.pow(1 - x, 5);
	}
}
