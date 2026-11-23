package com.jsblock.amberui.gui.screen;

import mtr.mappings.ScreenMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen that can return to a previous screen when closed.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.screen.ScreenBase}.
 */
public class ScreenBase extends ScreenMapper {
	private Screen previousScreen;

	public ScreenBase(Component title) {
		super(title);
	}

	public ScreenBase withPreviousScreen(Screen screen) {
		previousScreen = screen;
		return this;
	}

	@Override
	protected void init() {
		super.init();
		init2();
	}

	/**
	 * Called after the screen has been (re-)initialised.
	 */
	protected void init2() {
	}

	/**
	 * Called when the screen is "closed", i.e. the escape key is pressed or the screen is replaced.
	 */
	public void onClose2() {
		Minecraft.getInstance().setScreen(previousScreen);
	}
	@Override
	public void onClose() {
		onClose2();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
