package com.jsblock.amberui.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of applied OpenGL scissors, this should be the primary way to apply clipping to the GUI.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.ClipStack}.
 */
public class ClipStack {
	private static final List<Rect> stacks = new ArrayList<>();

	/**
	 * Push and apply a new clipping area.<br>
	 * GUI scaling is already taken account for, there is no need to multiply the scale by yourself.
	 */
	public static void push(int x, int y, int width, int height) {
		stacks.add(new Rect(x, y, width, height));
		enableClip(x, y, width, height);
	}

	/**
	 * Push and apply a new clipping area.
	 * The clipping area will be clamped <i>within</i> the previous clipped stack.
	 */
	public static void add(int x, int y, int width, int height) {
		if(stacks.isEmpty()) {
			push(x, y, width, height);
			return;
		}

		final Rect oldStack = stacks.get(stacks.size() - 1);
		final int clampedX = Math.max(oldStack.x, x);
		final int clampedY = Math.max(oldStack.y, y);
		final int oldEndY = oldStack.y + oldStack.height;
		final int clampedHeight = oldEndY - clampedY;
		final Rect rect = new Rect(clampedX, clampedY, width, clampedHeight);
		stacks.add(rect);
		enableClip(rect.x, rect.y, rect.width, rect.height);
	}

	public static void pop() {
		if(stacks.isEmpty()) return;

		stacks.remove(stacks.size() - 1);
		if(stacks.isEmpty()) {
			disableClip();
		} else {
			final Rect remainingLast = stacks.get(stacks.size() - 1);
			enableClip(remainingLast.x, remainingLast.y, remainingLast.width, remainingLast.height);
		}
	}

	/**
	 * Ensure the clip is applied appropriately (in case something else changed the GL state).
	 */
	public static void ensureStateCorrect() {
		if(stacks.isEmpty()) {
			disableClip();
		} else {
			final Rect latestStack = stacks.get(stacks.size() - 1);
			enableClip(latestStack.x, latestStack.y, latestStack.width, latestStack.height);
		}
	}

	/**
	 * Drop every clip that may have been left over from the previous frame.
	 */
	public static void reset() {
		stacks.clear();
		disableClip();
	}

	private static void enableClip(int x, int y, int width, int height) {
		final Minecraft minecraft = Minecraft.getInstance();
		final double scale = minecraft.getWindow().getGuiScale();
		final int windowHeight = minecraft.getWindow().getHeight();
		RenderSystem.enableScissor(
				(int) (x * scale),
				(int) (windowHeight - (y + height) * scale),
				Math.max(0, (int) (width * scale)),
				Math.max(0, (int) (height * scale))
		);
	}

	private static void disableClip() {
		RenderSystem.disableScissor();
	}

	private static class Rect {
		private final int x;
		private final int y;
		private final int width;
		private final int height;

		private Rect(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
}
