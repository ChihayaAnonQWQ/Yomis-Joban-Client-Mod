package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

/**
 * A text field for decimal numbers, with the same range checks and scroll wheel nudging as
 * AmberUI's original. Shift makes the step finer, Ctrl+Shift finer still.
 */
public class DoubleTextField extends AlwaysRenderedTextField implements GuiHelper {
	private final double min;
	private final double max;
	private final double defaultValue;
	private final String prefix;

	public DoubleTextField(int x, int y, int width, int height, double min, double max, double defaultValue) {
		this(x, y, width, height, min, max, defaultValue, null);
	}

	public DoubleTextField(int x, int y, int width, int height, double min, double max, double defaultValue, Component prefix) {
		super(x, y, width, height, 16, format(defaultValue));
		this.min = min;
		this.max = max;
		this.defaultValue = defaultValue;
		this.prefix = prefix == null ? null : prefix.getString();
	}

	private static String format(double value) {
		return value == Math.floor(value) && !Double.isInfinite(value)
				? String.valueOf((long) value)
				: String.valueOf(value);
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		final String previousValue = getValue();
		final boolean accepted = super.charTyped(character, modifiers);
		final String newValue = getValue();

		if (newValue.isEmpty() || newValue.equals("-") || newValue.equals(".") || newValue.equals("-.")) {
			return accepted;
		}

		try {
			final double value = Double.parseDouble(newValue);
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

	public double getNumber() {
		try {
			return Double.parseDouble(getValue());
		} catch (Exception e) {
			return defaultValue;
		}
	}

	public void setValue(double value) {
		if (value < min || value > max) {
			return;
		}
		setValue(format(value));
	}

	private void increment() {
		setValue(BigDecimal.valueOf(getNumber()).add(new BigDecimal(getStep())).doubleValue());
	}

	private void decrement() {
		setValue(BigDecimal.valueOf(getNumber()).subtract(new BigDecimal(getStep())).doubleValue());
	}

	private static String getStep() {
		if (Screen.hasShiftDown()) {
			return Screen.hasControlDown() ? "0.025" : "0.05";
		}
		return "0.1";
	}
}
