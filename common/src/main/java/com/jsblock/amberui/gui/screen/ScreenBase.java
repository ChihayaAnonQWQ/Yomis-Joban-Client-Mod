package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.ClipStack;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen that remembers the screen it was opened from and returns there when closed.
 *
 * <p>AmberUI's MTR 4 version extended {@code ScreenExtension} and hooked {@code onClose2}. MTR 3
 * has only the vanilla {@link Screen}, whose {@code onClose} is the one hook every caller uses, so
 * that is the method overridden here.</p>
 */
public abstract class ScreenBase extends ScreenMapper {
	private Screen previousScreen = null;

	protected ScreenBase() {
		super(Text.literal(""));
	}

	protected ScreenBase(Component title) {
		super(title);
	}

	public ScreenBase withPreviousScreen(Screen screen) {
		this.previousScreen = screen;
		return this;
	}

	public Screen getPreviousScreen() {
		return previousScreen;
	}

	@Override
	protected void init() {
		super.init();
		// A screen rebuilt while a previous render left a clip behind must not inherit it.
		ClipStack.reset();
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(previousScreen);
	}
}
