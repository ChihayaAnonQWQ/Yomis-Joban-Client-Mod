package com.jsblock.amberui.gui.screen;

import com.jsblock.amberui.gui.GuiHelper;
import com.jsblock.amberui.gui.widget.WidgetSet;
import mtr.mappings.Text;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A screen with a save/discard bottom row.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.screen.SavableScreen}.
 */
public abstract class SavableScreen extends TitledScreen implements GuiHelper {
	protected Button saveButton;
	protected Button discardButton;
	protected boolean discardConfig = false;

	public SavableScreen(Component title, boolean animatable) {
		super(title, animatable);
	}

	protected void addBottomRowEntry(int x, int y, int width, int height) {
		final WidgetSet bottomEntryWidget = new WidgetSet(20);

		this.saveButton = Button.builder(Text.translatable("gui.jsblock.block_config.save"), btn -> onClose2()).bounds(0, 0, 0, 20).build();
		this.discardButton = Button.builder(Text.translatable("gui.jsblock.block_config.discard"), btn -> {
			discardConfig = true;
			onClose2();
		}).bounds(0, 0, 0, 20).build();

		addWidget(saveButton);
		addWidget(discardButton);
		addRenderableWidget(bottomEntryWidget);

		bottomEntryWidget.addWidget(saveButton);
		bottomEntryWidget.addWidget(discardButton);
		bottomEntryWidget.setXYSize(x, y, width, height);
	}

	@Override
	public void onClose2() {
		// Save config by default, unless explicitly requested not to
		if(!discardConfig) {
			onSave();
		} else {
			onDiscard();
		}

		super.onClose2();
	}

	protected abstract void onSave();

	protected void onDiscard() {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
