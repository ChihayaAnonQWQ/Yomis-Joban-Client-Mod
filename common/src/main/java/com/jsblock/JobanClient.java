package com.jsblock;

import com.jsblock.block.PIDS1A;
import com.jsblock.block.PIDSLCD;
import com.jsblock.block.PIDSRV;
import com.jsblock.block.PIDSRVSIL1;
import com.jsblock.block.PIDSRVSIL2;
import com.jsblock.client.ClientConfig;
import com.jsblock.packet.IPacketJoban;
import com.jsblock.packet.PacketClient;
import com.jsblock.particle.LightBlockParticle;
import com.jsblock.render.RenderConstantSignalLight;
import com.jsblock.render.RenderDepartureTimer;
import com.jsblock.render.RenderFaresaver1;
import com.jsblock.render.RenderJobanPSDAPG;
import com.jsblock.render.RenderKCRStationName;
import com.jsblock.render.RenderLCDPIDS;
import com.jsblock.render.RenderRVPIDS;
import com.jsblock.render.RenderSignalLight;
import com.jsblock.render.RenderStationNameTall;
import dev.architectury.injectables.annotations.ExpectPlatform;
import mtr.RegistryClient;
import mtr.data.PIDSType;
import mtr.render.RenderPIDS;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Main class for the client.
 * @author LX86
 * @since 1.0.0
 */
public class JobanClient {

    public static void init() {
        ClientConfig.loadConfig();
        if (ClientConfig.getRenderDisabled()) {
            Joban.LOGGER.info("[Joban Client] Rendering for all JCM blocks are disabled.");
        }

        /* Allow transparent texture for the block */
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.APG_DOOR_DRL.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.APG_GLASS_DRL.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.APG_GLASS_END_DRL.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.AUTO_IRON_DOOR.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.BUFFERSTOP_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CEILING_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_2.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_3.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_4.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_5.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_6.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.CIRCLE_WALL_7.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.ENQUIRY_MACHINE_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.ENQUIRY_MACHINE_2.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.FARESAVER_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.HELPLINE_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.HELPLINE_2.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.HELPLINE_3.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.HELPLINE_4.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.EMG_STOP_5.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.EMG_STOP_6.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.KCR_EMG_STOP_SIGN.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.KCR_NAME_SIGN.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.KCR_NAME_SIGN_STATION_COLOR.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.LIGHT_2.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.PIDS_RV_SIL_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.PIDS_RV_SIL_2.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.STATION_NAME_TALL_STAND.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.SUBSIDY_MACHINE_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.TICKET_BARRIER_1_ENTRANCE.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.TICKET_BARRIER_1_EXIT.get());
        RegistryClient.registerBlockRenderType(RenderType.translucent(), Blocks.TICKET_BARRIER_1_DECOR.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.TRESPASS_SIGN_1.get());
        RegistryClient.registerBlockRenderType(RenderType.cutout(), Blocks.WATER_MACHINE_1.get());

        /* Register entity that requires to be rendered, and pointing to the corresponding method */
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.DRL_APG_DOOR_TILE_ENTITY.get(), dispatcher -> new RenderJobanPSDAPG<>(dispatcher, 0));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.DEPARTURE_TIMER_TILE_ENTITY.get(), RenderDepartureTimer::new);
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.FARESAVER_1_TILE_ENTITY.get(), RenderFaresaver1::new);
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.KCR_NAME_SIGN_TILE_ENTITY.get(), RenderKCRStationName::new);
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.KCR_NAME_SIGN_STATION_COLOR_TILE_ENTITY.get(), RenderKCRStationName::new);
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.PIDS_1A_TILE_ENTITY.get(), dispatcher -> new RenderPIDS<>(dispatcher, PIDS1A.TileEntityBlockPIDS1A.MAX_ARRIVALS, PIDS1A.TileEntityBlockPIDS1A.LINES_PER_ARRIVAL, 1, 9.5F, 6, 8.8F, 30, true, false, PIDSType.PIDS, 0xFF9900, 0xFF9900));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.PIDS_LCD_TILE_ENTITY.get(), dispatcher -> new RenderLCDPIDS<>(dispatcher, PIDSLCD.TileEntityBlockPIDS4.MAX_ARRIVALS, 5.7F, 9.5F, 6, 11.5F, 21, true, false, false, 0xEFE29E, 0));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.PIDS_RV_TILE_ENTITY.get(), dispatcher -> new RenderRVPIDS<>(dispatcher, PIDSRV.TileEntityBlockPIDSRV.MAX_ARRIVALS, 6, 8.25F, 6, 11F, 20, true, false, 0x000000, 0));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.PIDS_RV_SIL_TILE_ENTITY_1.get(), dispatcher -> new RenderRVPIDS<>(dispatcher, PIDSRVSIL1.TileEntityBlockPIDSSIL.MAX_ARRIVALS, 6F, 11.7F, 2.45F, 11F, 20.7F, true, false, 0x000000, 22.5F));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.PIDS_RV_SIL_TILE_ENTITY_2.get(), dispatcher -> new RenderRVPIDS<>(dispatcher, PIDSRVSIL2.TileEntityBlockPIDSSIL.MAX_ARRIVALS, 6F, 11.7F, 2.45F, 11F, 20.7F, true, false, 0x000000, 22.5F));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.SIGNAL_LIGHT_RED_ENTITY_1.get(), dispatcher -> new RenderConstantSignalLight<>(dispatcher, true, 0xFFFF0000, false));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.SIGNAL_LIGHT_RED_ENTITY_2.get(), dispatcher -> new RenderConstantSignalLight<>(dispatcher, true, 0xFFFF0000, true));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.SIGNAL_LIGHT_BLUE_ENTITY.get(), dispatcher -> new RenderConstantSignalLight<>(dispatcher, true, 0xFF0000FF, true));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.SIGNAL_LIGHT_GREEN_ENTITY.get(), dispatcher -> new RenderConstantSignalLight<>(dispatcher, true, 0xFF00FF00, false));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.SIGNAL_LIGHT_INVERTED_ENTITY_1.get(), dispatcher -> new RenderSignalLight<>(dispatcher, true, true, true, 0xFF0000FF));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.SIGNAL_LIGHT_INVERTED_ENTITY_2.get(), dispatcher -> new RenderSignalLight<>(dispatcher, true, true, false, 0xFF00FF00));
        RegistryClient.registerTileEntityRenderer(BlockEntityTypes.STATION_NAME_TALL_STAND_TILE_ENTITY.get(), RenderStationNameTall::new);

        /* Registers station color for that block */
        /* This is MTR's implementation of color providers which follows a station's color */
        /* Further Reading: https://fabricmc.net/wiki/tutorial:colorprovider */
        RegistryClient.registerBlockColors(Blocks.KCR_NAME_SIGN_STATION_COLOR.get());
        RegistryClient.registerBlockColors(Blocks.STATION_NAME_TALL_STAND.get());
        RegistryClient.registerBlockColors(Blocks.STATION_CEILING_1_STATION_COLOR.get());

        registerParticle(Particles.LIGHT_BLOCK.get(), new LightBlockParticle.Provider());

        /* Register inbound packets (Client Side) */
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_OPEN_BUTTERFLY_CONFIG_SCREEN, PacketClient::openButterflyScreenS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_OPEN_FARESAVER_CONFIG_SCREEN, PacketClient::openFaresaverScreenS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_OPEN_JOBAN_PIDS_CONFIG_SCREEN, PacketClient::openJobanPIDSScreenS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_OPEN_RV_PIDS_CONFIG_SCREEN, PacketClient::openRVPIDSScreenS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_OPEN_SUBSIDY_CONFIG_SCREEN, PacketClient::openSubsidyScreenS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_PLAY_NETWORK_SOUND, PacketClient::playNetworkSoundS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_OPEN_SOUND_LOOPER_SCREEN, PacketClient::openSoundLooperScreenS2C);
        RegistryClient.registerNetworkReceiver(IPacketJoban.PACKET_VERSION_CHECK, PacketClient::versionCheckS2C);
    }

    @ExpectPlatform
    public static void registerParticle(SimpleParticleType particle, ParticleProvider<SimpleParticleType> provider) {
    }
}

