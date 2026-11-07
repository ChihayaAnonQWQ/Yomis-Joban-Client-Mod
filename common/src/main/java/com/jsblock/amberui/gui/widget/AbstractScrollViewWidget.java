package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.ClipStack;
import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * A scrollable viewport.
 *
 * <p>AmberUI's version extended MTR 4's {@code ClickableWidgetExtension} and called
 * {@code super.mouseClicked2(...)}. Here it extends {@link AbstractWidget} and implements the
 * vanilla event methods directly. The scrollbar arithmetic is unchanged.</p>
 */
public abstract class AbstractScrollViewWidget extends AbstractWidget {
	public static final int SCROLLBAR_WIDTH = 6;
	protected double currentScroll = 0;
	private boolean scrollbarDragging = false;

	protected AbstractScrollViewWidget(int x, int y, int width, int height) {
		super(x, y, width, height, Component.empty());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
			scrollbarDragging = true;
			return true;
		}
		scrollbarDragging = false;
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			scrollbarDragging = false;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (active && visible && scrollbarDragging) {
			final double remainingHeight = getContentHeight() - getHeight();
			final int s1 = (getHeight() * getHeight()) / Math.max(1, getContentHeight());
			final double scrollScale = remainingHeight / ((double) getHeight() - s1);
			setScroll(currentScroll + (deltaY * scrollScale));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!visible || !isMouseOver(mouseX, mouseY)) {
			return false;
		}
		amount *= 15;
		final double oldScroll = currentScroll;
		setScroll(currentScroll - amount);
		return oldScroll != currentScroll;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		final boolean up = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
		final boolean down = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
		if (up || down) {
			final double oldScroll = currentScroll;
			setScroll(currentScroll + (up ? -15 : 15));
			if (oldScroll != currentScroll) {
				return true;
			}
		}
		return false;
	}

	protected boolean contentOverflowed() {
		return getContentHeight() > getHeight();
	}

	protected int getScrollbarOffset() {
		return contentOverflowed() ? SCROLLBAR_WIDTH : 0;
	}

	public void setScroll(double scroll) {
		if (scroll < 0 || !contentOverflowed()) {
			currentScroll = 0;
		} else if (scroll > getContentHeight() - getHeight()) {
			currentScroll = getContentHeight() - getHeight();
		} else {
			currentScroll = scroll;
		}
	}

	private void renderScrollbar(GuiGraphics guiGraphics, boolean mouseOverScrollbar) {
		if (!contentOverflowed()) {
			return;
		}

		final int fullHeight = getContentHeight();
		final int visibleHeight = getHeight();
		final double scrollbarHeight = visibleHeight * ((double) visibleHeight / fullHeight);
		final double bottomOffset = currentScroll / (fullHeight - visibleHeight);
		final double yOffset = bottomOffset * (visibleHeight - scrollbarHeight);
		GuiHelper.drawRectangle(guiGraphics, getX() + getWidth() - SCROLLBAR_WIDTH, getY() + yOffset,
				SCROLLBAR_WIDTH, scrollbarHeight, mouseOverScrollbar ? GuiHelper.ARGB_WHITE : 0xFFC0C0C0);

		GuiHelper.drawRectangle(guiGraphics, getX() + getWidth() - 1, getY() + yOffset, 1, scrollbarHeight, 0xFF808080);
		GuiHelper.drawRectangle(guiGraphics, getX() + getWidth() - SCROLLBAR_WIDTH,
				getY() + yOffset + scrollbarHeight - 1, SCROLLBAR_WIDTH, 1, 0xFF808080);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		ClipStack.push(guiGraphics, getX(), getY(), getWidth() - getScrollbarOffset(), getHeight());
		renderContent(guiGraphics, mouseX, mouseY, tickDelta);
		ClipStack.pop(guiGraphics);
		renderScrollbar(guiGraphics, isOverScrollbar(mouseX, mouseY));
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		final int scrollbarEndX = getX() + getWidth();
		final int scrollbarStartX = scrollbarEndX - getScrollbarOffset();
		return mouseX >= scrollbarStartX && mouseX <= scrollbarEndX
				&& mouseY >= getY() && mouseY <= getY() + getHeight();
	}

	public abstract void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta);

	protected abstract int getContentHeight();

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
	}
}
