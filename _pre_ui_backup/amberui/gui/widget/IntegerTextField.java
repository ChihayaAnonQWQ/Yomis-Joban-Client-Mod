package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A text field that only accepts whole numbers within a range.
 */
public class IntegerTextField extends AlwaysRenderedTextField implements GuiHelper {
	private final long min;
	private final long max;
	private final long defaultValue;
	private final String prefix;

	public IntegerTextField(int x, int y, int width, int height, long min, long max, long defaultValue) {
		this(x, y, width, height, min, max, defaultValue, null);
	}

	public IntegerTextField(int x, int y, int width, int height, long min, long max, long defaultValue, Component prefix) {
		super(x, y, width, height, 16, String.valueOf(defaultValue));
		this.min = min;
		this.max = max;
		this.defaultValue = defaultValue;
		this.prefix = prefix == null ? null : prefix.getString();
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		final String previousValue = getValue();
		final boolean accepted = super.charTyped(character, modifiers);
		final String newValue = getValue();

		if (newValue.isEmpty() || newValue.equals("-")) {
			return accepted;
		}

		try {
			final long value = Long.parseLong(newValue);
			if (value < min || value > max) {
				setValue(previousValue);
				return false;
			}
		} catch (NumberFormatException e) {
			setValue(previousValue);
			return false;
		}
		return accepted;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		super.render(guiGraphics, mouseX, mouseY, tickDelta);

		if (prefix != null) {
			final int prefixWidth = Minecraft.getInstance().font.width(prefix);
			final int prefixX = getX() - prefixWidth;
			final int prefixY = getY() + (getHeight() / 2) - (9 / 2);
			guiGraphics.drawString(Minecraft.getInstance().font, prefix, prefixX, prefixY, 0xFFFFFFFF, true);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (visible && active && isFocused()) {
			if (amount > 0) {
				increment();
			} else {
				decrement();
			}
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	public long getNumber() {
		try {
			return Long.parseLong(getValue());
		} catch (Exception e) {
			return defaultValue;
		}
	}

	public void setValue(long value) {
		if (value < min || value > max) {
			return;
		}
		setValue(String.valueOf(value));
	}

	private void increment() {
		setValue(getNumber() + 1);
	}

	private void decrement() {
		setValue(getNumber() - 1);
	}
}
