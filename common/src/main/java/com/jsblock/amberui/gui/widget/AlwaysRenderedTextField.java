package com.jsblock.amberui.gui.widget;

import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A text field that also draws when its {@code visible} flag is off.
 *
 * <p>AmberUI needed this because list rows toggled visibility while a field was being edited.
 * MTR 4 had {@code TextFieldWidgetExtension}; in MTR 3 the equivalent is
 * {@link WidgetBetterTextField}, which already extends vanilla's {@code EditBox}.</p>
 */
public class AlwaysRenderedTextField extends WidgetBetterTextField {
	public AlwaysRenderedTextField(int x, int y, int width, int height, int maxLength, String text) {
		super("", maxLength);
		initialise(x, y, width, height, text);
	}

	protected AlwaysRenderedTextField(TextFieldFilter filter, int x, int y, int width, int height, int maxLength, String text) {
		super(filter, "", maxLength);
		initialise(x, y, width, height, text);
	}

	private void initialise(int x, int y, int width, int height, String text) {
		setX(x);
		setY(y);
		setWidth(width);
		this.height = height;
		setValue(text == null ? "" : text);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		// MTR 3's screen already skips invisible children, so a hidden list row must stay hidden
		// here as well; the MTR 4 original forced visibility only because its list drew its own
		// children. Keeping the override makes that difference explicit.
		if (visible) {
			super.render(guiGraphics, mouseX, mouseY, tickDelta);
		}
	}
}
