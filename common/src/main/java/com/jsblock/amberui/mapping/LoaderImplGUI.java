package com.jsblock.amberui.mapping;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Loader-neutral GUI helpers.
 *
 * <p>AmberUI's file was guarded with {@code #if MC_VERSION} preprocessor blocks and used
 * {@code OrderedText}/{@code MutableText}. This port targets one Minecraft version (1.20.1) and
 * Mojang mappings, so the version branches collapse to the single surviving implementation:
 * {@link ConfirmLinkScreen} for links and {@link FormattedCharSequence} for tooltips.</p>
 */
public class LoaderImplGUI {
	private LoaderImplGUI() {
	}

	public static void openURLScreen(Screen parentScreen, String url) {
		final Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
			if (confirmed) {
				Util.getPlatform().openUri(url);
			}
			minecraft.setScreen(parentScreen);
		}, url, true));
	}

	public static void setTooltip(Screen screen, FormattedCharSequence orderedText) {
		screen.setTooltipForNextRenderPass(List.of(orderedText));
	}

	public static FormattedCharSequence asOrderedText(Component text) {
		return text.getVisualOrderText();
	}
}
