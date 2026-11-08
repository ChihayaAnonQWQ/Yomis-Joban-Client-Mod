package com.jsblock;

import com.jsblock.client.JobanCustomResources;
import com.jsblock.mappings.ForgeConfig;
import com.jsblock.mappings.ForgeUtilities;
import com.jsblock.packet.PacketClient;
import mtr.RegistryObject;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.DeferredRegisterHolder;
import mtr.mappings.RegistryUtilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.reflect.Field;
import java.util.*;

@Mod(Joban.MOD_ID)
public class JobanForge {

	private static final DeferredRegisterHolder<Item> ITEMS = new DeferredRegisterHolder<>(Joban.MOD_ID, ForgeUtilities.registryGetItem());
	private static final DeferredRegisterHolder<Block> BLOCKS = new DeferredRegisterHolder<>(Joban.MOD_ID, ForgeUtilities.registryGetBlock());
	private static final DeferredRegisterHolder<BlockEntityType<?>> BLOCK_ENTITY_TYPES = new DeferredRegisterHolder<>(Joban.MOD_ID, ForgeUtilities.registryGetBlockEntityType());
	private static final DeferredRegisterHolder<ParticleType<?>> PARTICLE_TYPES = new DeferredRegisterHolder<>(Joban.MOD_ID, ForgeUtilities.registryGetParticleType());
	private static final DeferredRegisterHolder<CreativeModeTab> CREATIVE_MODE_TABS = new DeferredRegisterHolder<>(Joban.MOD_ID, net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB);

	private static final Map<ResourceLocation, List<Item>> TAB_ITEMS = new LinkedHashMap<>();

	static {
		CREATIVE_MODE_TABS.register("core", () -> CreativeModeTab.builder()
				.icon(() -> new ItemStack(Blocks.HELPLINE_3.get()))
				.title(Component.translatable("itemGroup.jsblock.core"))
				.build());
		CREATIVE_MODE_TABS.register("pids", () -> CreativeModeTab.builder()
				.icon(() -> new ItemStack(Blocks.PIDS_RV_TCL.get()))
				.title(Component.translatable("itemGroup.jsblock.pids"))
				.build());
		CREATIVE_MODE_TABS.register("ceiling", () -> CreativeModeTab.builder()
				.icon(() -> new ItemStack(Blocks.STATION_CEILING_1.get()))
				.title(Component.translatable("itemGroup.jsblock.ceiling"))
				.build());

		Joban.init(JobanForge::registerBlock, JobanForge::registerItem, JobanForge::registerTabItem, JobanForge::registerBlockAndItems, JobanForge::registerBlockEntityType, JobanForge::registerParticle);

		// ItemGroups instantiation triggers MTR's creative tab registration
		// (via CreativeModeTabs.Wrapper → Registry.getCreativeModeTab()),
		// which adds JCM entries to MTR's static CREATIVE_TAB_ORDER.
		// Since JCM now registers its own creative tabs independently, we
		// must remove our entries from MTR's maps before MTR's constructor
		// creates duplicate empty tabs that cause gaps in the tab bar.
		// This static block runs before any @Mod constructors.
		removeJcmEntriesFromMtrStaticMaps();
	}

	/**
	 * Cleans up JCM entries that were inadvertently added to MTR's static
	 * creative tab maps via the CreativeModeTabs.Wrapper constructor path.
	 * Without this cleanup, MTR's registerCreativeModeTabsToDeferredRegistry
	 * would create empty duplicate tabs (mtr:core, etc.) alongside JCM's
	 * own tabs (jsblock:core, etc.), causing gaps in the creative tab bar.
	 */
	@SuppressWarnings("unchecked")
	private static void removeJcmEntriesFromMtrStaticMaps() {
		try {
			Class<?> mtrForgeUtils = Class.forName("mtr.forge.mappings.ForgeUtilities");

			Field orderField = mtrForgeUtils.getDeclaredField("CREATIVE_TAB_ORDER");
			orderField.setAccessible(true);
			List<ResourceLocation> orderList = (List<ResourceLocation>) orderField.get(null);
			if (orderList != null) {
				orderList.removeIf(rl -> Joban.MOD_ID.equals(rl.getNamespace()));
			}

			Field tabsField = mtrForgeUtils.getDeclaredField("CREATIVE_TABS");
			tabsField.setAccessible(true);
			Map<ResourceLocation, ?> tabsMap = (Map<ResourceLocation, ?>) tabsField.get(null);
			if (tabsMap != null) {
				tabsMap.keySet().removeIf(rl -> Joban.MOD_ID.equals(rl.getNamespace()));
			}
		} catch (Exception e) {
			Joban.LOGGER.warn("[Joban Client] Could not clean up MTR creative tab entries: {}", e.toString());
		}
	}

	public JobanForge() {
		final IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ForgeUtilities.registerModEventBus(Joban.MOD_ID, eventBus);
		ITEMS.register();
		BLOCKS.register();
		BLOCK_ENTITY_TYPES.register();
		PARTICLE_TYPES.register();
		CREATIVE_MODE_TABS.register();
		eventBus.register(CreativeTabHandler.class);
		/* LeftClickBlock lives on the Forge bus, not the mod bus the creative-tab handler uses. */
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PidsToolHandler.class);
		eventBus.register(MTRForgeRegistry.class);
		DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> eventBus.register(ForgeUtilities.ClientsideEvents.class));
	}

	private static void registerBlock(String path, RegistryObject<Block> block) {
		BLOCKS.register(path, block::get);
	}

	private static void registerItem(String path, RegistryObject<Item> item) {
		ITEMS.register(path, item::get);
	}

	/**
	 * Registers a plain item and puts it in one of this mod's creative tabs.
	 *
	 * <p>The item is constructed inside the deferred register's supplier, not here: this runs during
	 * the mod class's static initialiser, when Forge's item registry is already frozen, and calling
	 * {@code item.get()} at that point throws "Registry is already frozen".</p>
	 */
	private static void registerTabItem(String path, RegistryObject<Item> item) {
		ITEMS.register(path, () -> {
			final Item built = item.get();
			TAB_ITEMS.computeIfAbsent(ItemGroups.PIDS, k -> new ArrayList<>()).add(built);
			return built;
		});
	}


	private static void registerParticle(String resourceLocation, SimpleParticleType particle) {
		PARTICLE_TYPES.register(resourceLocation, Particles.LIGHT_BLOCK::get);
	}

	private static void registerBlockAndItems(String path, RegistryObject<Block> block, ResourceLocation creativeModeTab) {
		registerBlock(path, block);
		ITEMS.register(path, () -> {
			final BlockItem blockItem = new BlockItem(block.get(), new Item.Properties());
			TAB_ITEMS.computeIfAbsent(creativeModeTab, k -> new ArrayList<>()).add(blockItem);
			return blockItem;
		});
	}

	private static <T extends BlockEntityMapper> void registerBlockEntityType(String path, RegistryObject<? extends BlockEntityType<? extends BlockEntityMapper>> blockEntityType) {
		BLOCK_ENTITY_TYPES.register(path, blockEntityType::get);
	}

	/**
	 * Keeps the PIDS config tool from breaking the block it is pointed at.
	 *
	 * <p>Left-click copies, and creative mode would otherwise remove the PIDS in the same click. The
	 * event fires on both sides, so the packet is sent from the client only and the event is
	 * cancelled on both.</p>
	 */
	public static class PidsToolHandler {
		@SubscribeEvent
		public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
			if (!event.getEntity().getItemInHand(event.getHand()).is(Items.PIDS_CONFIG_TOOL.get())) {
				return;
			}
			if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof com.jsblock.block.JobanPIDSBase.TileEntityBlockJobanPIDS)) {
				return;
			}
			/* The PIDS block's own use() would otherwise consume the click (it opens the config screen
			   when a brush is held), leaving the tool's useOn unreachable. */
			if (event.getSide().isClient()) {
				PacketClient.sendPidsToolPaste(event.getPos());
			}
			event.setCanceled(true);
		}

		@SubscribeEvent
		public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
			if (!event.getEntity().getItemInHand(event.getHand()).is(Items.PIDS_CONFIG_TOOL.get())) {
				return;
			}
			if (event.getSide().isClient()) {
				PacketClient.sendPidsToolCopy(event.getPos());
			}
			event.setCanceled(true);
		}
	}

	public static class CreativeTabHandler {

		@SubscribeEvent
		public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
			ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(event.getTab());
			if (key == null || !key.getNamespace().equals(Joban.MOD_ID)) {
				return;
			}
			List<Item> items = TAB_ITEMS.get(key);
			if (items != null) {
				for (Item item : items) {
					event.getEntries().put(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
				}
			}
		}
	}

	private static class MTRForgeRegistry {

		@SubscribeEvent
		public static void onClientSetupEvent(FMLClientSetupEvent event) {
			JobanClient.init();
			ForgeConfig.registerConfig();

			ForgeUtilities.registerTextureStitchEvent(textureAtlas -> {
				if (((TextureAtlas) textureAtlas).location().getPath().equals("textures/atlas/blocks.png")) {
					JobanCustomResources.reload(Minecraft.getInstance().getResourceManager());
				}
			});
		}
	}
}
