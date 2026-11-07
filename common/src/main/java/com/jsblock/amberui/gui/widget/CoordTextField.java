package com.jsblock.amberui.gui.widget;

/**
 * A text field for a whole-number coordinate, clamped to a range. AmberUI used one per axis in
 * {@link BlockPosWidget}; the PIDS screens use {@link DoubleTextField} for their own numbers.
 */
public class CoordTextField extends AlwaysRenderedTextField {
	private final long min;
	private final long max;
	private final int defaultValue;

	public CoordTextField(int x, int y, int width, int height, long min, long max, int defaultValue) {
		super(x, y, width, height, 16, String.valueOf(defaultValue));
		this.min = min;
		this.max = max;
		this.defaultValue = defaultValue;
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

	public int getNumber() {
		try {
			return Integer.parseInt(getValue());
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
		setValue(getNumber() + 1L);
	}

	private void decrement() {
		setValue(getNumber() - 1L);
	}
}
