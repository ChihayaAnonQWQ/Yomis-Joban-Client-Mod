package com.jsblock.packet;

import com.jsblock.Joban;
import net.minecraft.resources.ResourceLocation;

/**
 * Interface containing all packet id
 * @author LX86
 */
public interface IPacketJoban {
    ResourceLocation PACKET_VERSION_CHECK = new ResourceLocation(Joban.MOD_ID, "packet_version_check");
    ResourceLocation PACKET_UPDATE_BUTTERFLY_CONFIG = new ResourceLocation(Joban.MOD_ID, "packet_update_butterfly_light");
    ResourceLocation PACKET_UPDATE_JOBAN_PIDS_CONFIG = new ResourceLocation(Joban.MOD_ID, "packet_joban_pids_update");
    ResourceLocation PACKET_UPDATE_RV_PIDS_CONFIG = new ResourceLocation(Joban.MOD_ID, "packet_rv_pids_update");
    ResourceLocation PACKET_UPDATE_SOUND_LOOPER_CONFIG = new ResourceLocation(Joban.MOD_ID, "packet_update_sound_looper");
    ResourceLocation PACKET_UPDATE_SUBSIDY_CONFIG = new ResourceLocation(Joban.MOD_ID, "packet_subsidy_machine_update");
    ResourceLocation PACKET_UPDATE_FARESAVER_CONFIG = new ResourceLocation(Joban.MOD_ID, "packet_faresaver_update");
    ResourceLocation PACKET_OPEN_BUTTERFLY_CONFIG_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_butterfly_config_screen");
    ResourceLocation PACKET_OPEN_FARESAVER_CONFIG_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_faresaver_config_screen");
    ResourceLocation PACKET_OPEN_JOBAN_PIDS_CONFIG_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_joban_pids_config_screen");
    ResourceLocation PACKET_OPEN_RV_PIDS_CONFIG_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_rv_pids_config_screen");
    ResourceLocation PACKET_OPEN_SOUND_LOOPER_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_sound_looper_screen");
    ResourceLocation PACKET_OPEN_SUBSIDY_CONFIG_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_subsidy_config_screen");
    ResourceLocation PACKET_PLAY_NETWORK_SOUND = new ResourceLocation(Joban.MOD_ID, "play_network_sound");
    /* PIDS Projector: its own screen and its own update, so the two-block PIDS configuration flow
       stays untouched. */
    ResourceLocation PACKET_UPDATE_PIDS_PROJECTOR = new ResourceLocation(Joban.MOD_ID, "packet_pids_projector_update");
    ResourceLocation PACKET_OPEN_PIDS_PROJECTOR_SCREEN = new ResourceLocation(Joban.MOD_ID, "packet_open_pids_projector_screen");
}