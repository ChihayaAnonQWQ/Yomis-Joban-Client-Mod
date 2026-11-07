package com.jsblock.amberui.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;

/**
 * A text field that is always rendered, even when marked as invisible.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.AlwaysRenderedTextField}.
 */
public class AlwaysRenderedTextField extends EditBox {
	public AlwaysRenderedTextField(int x, int y, int width, int height, int maxLength, String suggestion) {
		super(Minecraft.getInstance().font, x, y, width, height, CommonComponents.EMPTY);
		setMaxLength(maxLength);

		if(suggestion != null) {
			setSuggestion(suggestion);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		final boolean wasVisible = visible;
		visible = true;
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		visible = wasVisible;
	}
}
