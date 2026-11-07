package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.ClipStack;
import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import org.lwjgl.glfw.GLFW;

/**
 * A scrollable viewport with a scrollbar.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.AbstractScrollViewWidget}.
 */
public abstract class AbstractScrollViewWidget extends AbstractWidget implements GuiHelper {
	public static final int SCROLLBAR_WIDTH = 6;
	protected double currentScroll = 0;
	private boolean scrollbarDragging = false;

	public AbstractScrollViewWidget(int x, int y, int width, int height) {
		super(x, y, width, height, CommonComponents.EMPTY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		scrollbarDragging = button == 0 && isOverScrollbar(mouseX, mouseY);
		// Only consume the click when the scrollbar is targeted, otherwise the widgets
		// placed within the list would never receive any input.
		return scrollbarDragging;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0) {
			scrollbarDragging = false;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if(visible && isFocused() && scrollbarDragging) {
			final double remainingHeight = getContentHeight() - getHeight();

			final int s1 = (getHeight() * getHeight()) / getContentHeight();
			final double scrollScale = remainingHeight / ((double) getHeight() - s1);
			setScroll(currentScroll + (deltaY * scrollScale));
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if(!visible || !contentOverflowed() || !isMouseOver(mouseX, mouseY)) return false;

		amount *= 15;
		final double oldScroll = currentScroll;
		setScroll(currentScroll - amount);
		return oldScroll != currentScroll;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(!visible || !isFocused()) return false;

		final boolean bl = keyCode == GLFW.GLFW_KEY_UP;
		final boolean bl2 = keyCode == GLFW.GLFW_KEY_DOWN;

		if(bl || bl2) {
			final double oldScroll = currentScroll;
			setScroll(bl ? currentScroll - 15 : currentScroll + 15);
			if(oldScroll != currentScroll) return true;
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
		if(scroll < 0 || !contentOverflowed()) {
			currentScroll = 0;
		} else if(scroll > getContentHeight() - getHeight()) {
			currentScroll = getContentHeight() - getHeight();
		} else {
			currentScroll = scroll;
		}
	}

	public double getScroll() {
		return currentScroll;
	}

	private void renderScrollbar(GuiGraphics guiGraphics, boolean isMouseOverScrollbar) {
		if(!contentOverflowed()) return;

		final int fullHeight = getContentHeight();
		final int visibleHeight = getHeight();
		final double scrollbarHeight = visibleHeight * ((double) visibleHeight / fullHeight);
		final double bottomOffset = currentScroll / (fullHeight - visibleHeight);
		final double yOffset = bottomOffset * (visibleHeight - scrollbarHeight);
		GuiHelper.drawRectangle(guiGraphics, getX() + getWidth() - SCROLLBAR_WIDTH, getY() + yOffset, SCROLLBAR_WIDTH, scrollbarHeight, isMouseOverScrollbar ? ARGB_WHITE : 0xFFC0C0C0);

		// Border
		GuiHelper.drawRectangle(guiGraphics, getX() + getWidth() - 1, getY() + yOffset, 1, scrollbarHeight, 0xFF808080);
		GuiHelper.drawRectangle(guiGraphics, getX() + getWidth() - SCROLLBAR_WIDTH, getY() + yOffset + scrollbarHeight - 1, SCROLLBAR_WIDTH, 1, 0xFF808080);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		ClipStack.push(getX(), getY(), getWidth(), getHeight());

		ClipStack.push(getX(), getY(), getWidth() - getScrollbarOffset(), getHeight());
		renderContent(guiGraphics, mouseX, mouseY, tickDelta);
		ClipStack.pop();

		renderScrollbar(guiGraphics, isOverScrollbar(mouseX, mouseY));
		ClipStack.pop();
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		final int scrollbarEndX = getX() + getWidth();
		final int scrollbarStartX = scrollbarEndX - getScrollbarOffset();
		return mouseX >= scrollbarStartX && mouseX <= scrollbarEndX && mouseY >= getY() && mouseY <= getY() + getHeight();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= getX() && mouseX <= getX() + getWidth() && mouseY >= getY() && mouseY <= getY() + getHeight();
	}

	public abstract void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta);

	protected abstract int getContentHeight();

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
}
