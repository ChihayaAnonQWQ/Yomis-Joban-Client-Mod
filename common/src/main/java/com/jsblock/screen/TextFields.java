package com.jsblock.screen;

import mtr.screen.WidgetBetterTextField;

/**
 * Helpers for MTR's text fields.
 *
 * <p>{@link WidgetBetterTextField} builds its box with a width of zero, and Minecraft's text box
 * re-clamps its scroll position against that width every time the value or the caret changes. A
 * value assigned while the box is still zero wide therefore leaves the box scrolled past the end
 * of its own text, and it paints blank until it is clicked — the "point at it before it shows"
 * behaviour. Assigning the value through {@link #setValue} gives the box a width to measure
 * against first, so the text shows up as soon as it is drawn.</p>
 */
public final class TextFields {
	/** Wide enough for any value this mod puts in a text box, so nothing is scrolled out of view. */
	private static final int MEASURING_WIDTH = 4096;

	private TextFields() {
	}

	/**
	 * Sets the value of a text field that has not been given its final size yet.
	 *
	 * @return the field, so this can be used inline
	 */
	public static <T extends WidgetBetterTextField> T setValue(T textField, String value) {
		final int width = textField.getWidth();
		textField.setWidth(MEASURING_WIDTH);
		textField.setValue(value == null ? "" : value);
		textField.setWidth(width);
		return textField;
	}
}
