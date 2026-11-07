package com.jsblock.amberui.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of nested GUI scissors.
 *
 * <p>Use {@link #push} to start clipping, {@link #add} to clip inside the current rectangle, and
 * {@link #pop} when done. {@link GuiGraphics} owns the actual scissor stack: {@code enableScissor}
 * pushes and {@code disableScissor} pops back to the previous rectangle, so the two must stay
 * balanced. This class mirrors that stack only to clamp {@link #add} and to let a screen drop a
 * stale stack on {@code init}.</p>
 *
 * <p>MTR 4's original drove {@code RenderSystem} directly and re-applied the previous rectangle on
 * every pop; doing that with {@code GuiGraphics} would push an extra entry each time instead of
 * restoring one. The implementation is therefore simpler here, not more complex.</p>
 */
public class ClipStack {
	private static final List<Rectangle> STACKS = new ArrayList<>();

	private ClipStack() {
	}

	/** Clears the Java-side mirror. The {@link GuiGraphics} stack is per-frame, so this is only
	 *  for a screen that is being rebuilt. */
	public static void reset() {
		STACKS.clear();
	}

	public static void push(GuiGraphics guiGraphics, int x, int y, int width, int height) {
		STACKS.add(new Rectangle(x, y, width, height));
		guiGraphics.enableScissor(x, y, x + Math.max(0, width), y + Math.max(0, height));
	}

	/**
	 * Pushes a clip area clamped inside the previous one. If nothing is on the stack this behaves
	 * like {@link #push}.
	 */
	public static void add(GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (STACKS.isEmpty()) {
			push(guiGraphics, x, y, width, height);
			return;
		}

		final Rectangle old = STACKS.get(STACKS.size() - 1);
		final int clampedX = Math.max(old.x, x);
		final int clampedY = Math.max(old.y, y);
		final int oldEndX = old.x + old.width;
		final int oldEndY = old.y + old.height;
		final int clampedWidth = Math.max(0, Math.min(width, oldEndX - clampedX));
		final int clampedHeight = Math.max(0, oldEndY - clampedY);
		push(guiGraphics, clampedX, clampedY, clampedWidth, clampedHeight);
	}

	public static void pop(GuiGraphics guiGraphics) {
		if (STACKS.isEmpty()) {
			return;
		}
		STACKS.remove(STACKS.size() - 1);
		guiGraphics.disableScissor();
	}

	/** Kept for source compatibility with the MTR 4 port; {@link GuiGraphics} already restores
	 *  the previous rectangle on {@code disableScissor}, so there is nothing to repair. */
	public static void ensureStateCorrect(GuiGraphics guiGraphics) {
	}
}
