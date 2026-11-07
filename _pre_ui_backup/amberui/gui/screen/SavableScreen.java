package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.GuiHelper;
import mtr.client.IDrawing;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.gui.components.Button;

/**
 * A titled screen with a Save / Cancel row along the bottom.
 *
 * <p>AmberUI saved on every close unless the Discard button had been pressed. That behaviour is
 * preserved: closing with Escape saves, which is what every other configuration screen in this
 * mod does. The button captions use this mod's own {@code gui.jsblock.*} keys rather than JCM's
 * {@code block_config.*} namespace.</p>
 */
public abstract class SavableScreen extends TitledScreen implements GuiHelper {
	protected Button saveButton;
	protected Button discardButton;
	protected boolean discardConfig = false;

	protected SavableScreen(boolean animatable) {
		super(animatable);
	}

	protected void addBottomRowEntry(int x, int y, int width, int height) {
		final int buttonHeight = 20;
		final int buttonWidth = Math.max(40, (width - 6) / 2);

		saveButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.save"), button -> onClose());
		discardButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.cancel"), button -> {
			discardConfig = true;
			onClose();
		});

		IDrawing.setPositionAndWidth(saveButton, x, y, buttonWidth);
		IDrawing.setPositionAndWidth(discardButton, x + width - buttonWidth, y, buttonWidth);
		addDrawableChild(saveButton);
		addDrawableChild(discardButton);
	}

	@Override
	public void onClose() {
		if (!discardConfig) {
			onSave();
		} else {
			onDiscard();
		}
		super.onClose();
	}

	protected abstract void onSave();

	protected void onDiscard() {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
