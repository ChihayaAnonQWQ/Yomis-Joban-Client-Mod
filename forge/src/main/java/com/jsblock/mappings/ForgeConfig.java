package com.jsblock.mappings;

import com.jsblock.screen.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Forge-only bridge that hooks JCM's in-game config screen into the Forge mod list.
 *
 * <p>On Fabric the screen is reached through ModMenu
 * (see {@code com.jsblock.ModMenuConfig}); Forge has no equivalent mod, so the
 * screen has to be published as a Forge config-screen extension point instead.
 * Called from {@code JobanForge.MTRForgeRegistry#onClientSetupEvent}.</p>
 *
 * <p>This class lives in the generated {@code com.jsblock.mappings} package
 * because that is where {@code JobanForge} imports it from, but unlike
 * {@code ForgeUtilities} / {@code FabricRegistryUtilities} it is <b>not</b>
 * produced by the Minecraft-Mappings download, so it is kept in version
 * control. The generated files are moved into this package by the
 * {@code setupFiles} task, which never deletes existing files here.</p>
 *
 * @author reconstructed for the MTR 3 branch
 * @since 1.2.12
 */
public class ForgeConfig {

	/**
	 * Registers the {@link ConfigScreen} factory with Forge. Must run on the
	 * client only; the sole caller is the {@code FMLClientSetupEvent} handler.
	 */
	public static void registerConfig() {
		ModLoadingContext.get().registerExtensionPoint(
				ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen())
		);
	}
}
