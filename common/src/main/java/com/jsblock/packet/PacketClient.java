package com.jsblock.packet;

import com.jsblock.Joban;
import com.jsblock.audio.NetworkAudioPlayer;
import com.jsblock.block.ButterflyLight;
import com.jsblock.block.FareSaver1;
import com.jsblock.block.JobanPIDSBase;
import com.jsblock.block.PIDSRVBase;
import com.jsblock.block.SoundLooper;
import com.jsblock.block.SubsidyMachine1;
import com.jsblock.client.ClientConfig;
import com.jsblock.screen.ButterflyLightScreen;
import com.jsblock.screen.FareSaverScreen;
import com.jsblock.screen.JobanPIDSConfigScreen;
import com.jsblock.screen.PIDSProjectorScreen;
import com.jsblock.screen.RVPIDSConfigScreen;
import com.jsblock.screen.SoundLooperScreen;
import com.jsblock.screen.SubsidyMachineScreen;
import io.netty.buffer.Unpooled;
import mtr.RegistryClient;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

import static com.jsblock.packet.IPacketJoban.PACKET_UPDATE_SOUND_LOOPER_CONFIG;

/**
 * This class is responsible for handling packets, that should be executed on the CLIENT side, e.g.
 * Method for sending packets from the Client to Server (C2S)
 * Method for handling packets sent from the Server to Client (S2C)
 */
public class PacketClient {
    public static void sendRVPIDSConfigC2S(BlockPos pos1, BlockPos pos2, String[] messages, boolean[] hideArrival, Set<Long> filterPlatformIds, boolean hidePlatformNumber, String presetID,
                                           boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
                                           boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos1);
        packet.writeBlockPos(pos2);
        packet.writeInt(messages.length);
        for (int i = 0; i < messages.length; ++i) {
            packet.writeUtf(messages[i]);
            packet.writeBoolean(hideArrival[i]);
        }
        packet.writeInt(filterPlatformIds.size());
        filterPlatformIds.forEach(packet::writeLong);

        packet.writeBoolean(hidePlatformNumber);
        packet.writeUtf(presetID);
        packet.writeBoolean(autoSwitchEnabled);
        packet.writeUtf(autoSwitchPreset);
        packet.writeInt(autoSwitchCountdown);
        packet.writeInt(autoSwitchDuration);
        packet.writeBoolean(depAutoSwitchEnabled);
        packet.writeUtf(depAutoSwitchPreset);
        packet.writeInt(depAutoSwitchCountdown);
        packet.writeInt(depAutoSwitchDuration);
        packet.writeBoolean(depAutoSwitchUntilClose);
        RegistryClient.sendToServer(IPacketJoban.PACKET_UPDATE_RV_PIDS_CONFIG, packet);
    }

    public static void sendJobanPIDSConfigC2S(BlockPos pos1, BlockPos pos2, String[] messages, boolean[] hideArrival, Set<Long> filterPlatformIds, String presetID,
                                              boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
                                              boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos1);
        packet.writeBlockPos(pos2);
        packet.writeInt(messages.length);
        for (int i = 0; i < messages.length; ++i) {
            packet.writeUtf(messages[i]);
            packet.writeBoolean(hideArrival[i]);
        }
        packet.writeInt(filterPlatformIds.size());
        filterPlatformIds.forEach(packet::writeLong);

        packet.writeUtf(presetID);
        packet.writeBoolean(autoSwitchEnabled);
        packet.writeUtf(autoSwitchPreset);
        packet.writeInt(autoSwitchCountdown);
        packet.writeInt(autoSwitchDuration);
        packet.writeBoolean(depAutoSwitchEnabled);
        packet.writeUtf(depAutoSwitchPreset);
        packet.writeInt(depAutoSwitchCountdown);
        packet.writeInt(depAutoSwitchDuration);
        packet.writeBoolean(depAutoSwitchUntilClose);
        RegistryClient.sendToServer(IPacketJoban.PACKET_UPDATE_JOBAN_PIDS_CONFIG, packet);
    }

    public static void sendSubsidyConfigC2S(BlockPos pos, int pricePerClick, int timeout) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos);
        packet.writeInt(pricePerClick);
        packet.writeInt(timeout);

        RegistryClient.sendToServer(IPacketJoban.PACKET_UPDATE_SUBSIDY_CONFIG, packet);
    }

    public static void sendFaresaverConfigC2S(BlockPos pos, int discount) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos);
        packet.writeInt(discount);

        RegistryClient.sendToServer(IPacketJoban.PACKET_UPDATE_FARESAVER_CONFIG, packet);
    }

    public static void sendButterflyConfigC2S(BlockPos pos, int countdown) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos);
        packet.writeInt(countdown);

        RegistryClient.sendToServer(IPacketJoban.PACKET_UPDATE_BUTTERFLY_CONFIG, packet);
    }

    public static void versionCheckS2C(FriendlyByteBuf packet) {
        final String version = packet.readUtf();
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if(ClientConfig.getVersionCheckDisabled()) {
                Joban.LOGGER.warn("Version check disabled, please use this for debugging only.");
                return;
            }

            if (!Joban.getVersion().split("-hotfix-")[0].equals(version)) {
                final ClientPacketListener connection = minecraft.getConnection();
                if (connection != null) {
                    final int widthDifference1 = minecraft.font.width(Text.translatable("gui.mtr.mismatched_versions_your_version")) - minecraft.font.width(Text.translatable("gui.mtr.mismatched_versions_server_version"));
                    final int widthDifference2 = minecraft.font.width(Joban.getVersion()) - minecraft.font.width(version);
                    final int spaceWidth = minecraft.font.width(" ");

                    final StringBuilder text = new StringBuilder();
                    for (int i = 0; i < -widthDifference1 / spaceWidth; i++) {
                        text.append(" ");
                    }
                    text.append(Text.translatable("gui.mtr.mismatched_versions_your_version", Joban.getVersion()).getString());
                    for (int i = 0; i < -widthDifference2 / spaceWidth; i++) {
                        text.append(" ");
                    }
                    text.append("\n");
                    for (int i = 0; i < widthDifference1 / spaceWidth; i++) {
                        text.append(" ");
                    }
                    text.append(Text.translatable("gui.mtr.mismatched_versions_server_version", version).getString());
                    for (int i = 0; i < widthDifference2 / spaceWidth; i++) {
                        text.append(" ");
                    }
                    text.append("\n\n");

                    connection.getConnection().disconnect(Text.literal(text.toString()).append(Text.translatable("gui.jsblock.mismatched_versions")));
                }
            }
        });
    }

    public static void openSoundLooperScreenS2C(FriendlyByteBuf packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = packet.readBlockPos();

        minecraft.execute(() -> {
            if (minecraft.level != null) {
                final BlockEntity entity = minecraft.level.getBlockEntity(pos);
                if (entity instanceof SoundLooper.TileEntitySoundLooper) {
                    UtilitiesClient.setScreen(minecraft, new SoundLooperScreen(pos));
                }
            }
        });
    }

    public static void openRVPIDSScreenS2C(FriendlyByteBuf packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) return;

        final BlockPos pos1 = packet.readBlockPos();
        final BlockPos pos2 = packet.readBlockPos();
        final int maxArrivals = packet.readInt();
        final boolean hidePlatformNumber = packet.readBoolean();
        final String presetID = packet.readUtf();
        final boolean autoSwitchEnabled = packet.readBoolean();
        final String autoSwitchPreset = packet.readUtf();
        final int autoSwitchCountdown = packet.readInt();
        final int autoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchEnabled = packet.readBoolean();
        final String depAutoSwitchPreset = packet.readUtf();
        final int depAutoSwitchCountdown = packet.readInt();
        final int depAutoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchUntilClose = packet.readBoolean();

        if (minecraft.level.getBlockEntity(pos1) instanceof PIDSRVBase.TileEntityBlockRVPIDS) {
            minecraft.execute(() -> {
                if (!(minecraft.screen instanceof RVPIDSConfigScreen)) {
                    UtilitiesClient.setScreen(minecraft, new RVPIDSConfigScreen(pos1, pos2, maxArrivals, hidePlatformNumber, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration, depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose));
                }
            });
        }
    }

    public static void openJobanPIDSScreenS2C(FriendlyByteBuf packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) return;

        final BlockPos pos1 = packet.readBlockPos();
        final BlockPos pos2 = packet.readBlockPos();
        final int maxArrivals = packet.readInt();
        final String presetID = packet.readUtf();
        final boolean autoSwitchEnabled = packet.readBoolean();
        final String autoSwitchPreset = packet.readUtf();
        final int autoSwitchCountdown = packet.readInt();
        final int autoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchEnabled = packet.readBoolean();
        final String depAutoSwitchPreset = packet.readUtf();
        final int depAutoSwitchCountdown = packet.readInt();
        final int depAutoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchUntilClose = packet.readBoolean();

        if (minecraft.level.getBlockEntity(pos1) instanceof JobanPIDSBase.TileEntityBlockJobanPIDS) {
            minecraft.execute(() -> {
                if (!(minecraft.screen instanceof JobanPIDSConfigScreen)) {
                    UtilitiesClient.setScreen(minecraft, new JobanPIDSConfigScreen(pos1, pos2, maxArrivals, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration, depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose));
                }
            });
        }
    }

    public static void openFaresaverScreenS2C(FriendlyByteBuf packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) return;

        final BlockPos pos1 = packet.readBlockPos();
        final int discount = packet.readInt();

        if (minecraft.level.getBlockEntity(pos1) instanceof FareSaver1.TileEntityFareSaver) {
            minecraft.execute(() -> {
                if (!(minecraft.screen instanceof FareSaverScreen)) {
                    UtilitiesClient.setScreen(minecraft, new FareSaverScreen(pos1, discount));
                }
            });
        }
    }

    public static void openButterflyScreenS2C(FriendlyByteBuf packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) return;

        final BlockPos pos = packet.readBlockPos();
        final int countdown = packet.readInt();

        if (minecraft.level.getBlockEntity(pos) instanceof ButterflyLight.TileEntityButterFlyLight) {
            minecraft.execute(() -> {
                if (!(minecraft.screen instanceof ButterflyLightScreen)) {
                    UtilitiesClient.setScreen(minecraft, new ButterflyLightScreen(pos, countdown));
                }
            });
        }
    }

    public static void openSubsidyScreenS2C(FriendlyByteBuf packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) return;

        final BlockPos pos = packet.readBlockPos();
        final int pricePerClick = packet.readInt();
        final int timeout = packet.readInt();

        if (minecraft.level.getBlockEntity(pos) instanceof SubsidyMachine1.TileEntitySubsidyMachine) {
            minecraft.execute(() -> {
                if (!(minecraft.screen instanceof SubsidyMachineScreen)) {
                    UtilitiesClient.setScreen(minecraft, new SubsidyMachineScreen(pos, pricePerClick, timeout));
                }
            });
        }
    }

    public static void sendSoundLooperC2S(BlockPos pos, int categoryIndex, String soundId, int loopInterval,
                                          float volume, boolean needRedstone, boolean limitRange,
                                          BlockPos pos1, BlockPos pos2,
                                          boolean useNetworkAudio, String networkAudioUrl) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos);
        packet.writeBlockPos(pos1);
        packet.writeBlockPos(pos2);
        packet.writeUtf(soundId);
        packet.writeInt(categoryIndex);
        packet.writeInt(loopInterval);
        packet.writeFloat(volume);
        packet.writeBoolean(needRedstone);
        packet.writeBoolean(limitRange);

        packet.writeBoolean(useNetworkAudio);
        packet.writeUtf(networkAudioUrl);

        RegistryClient.sendToServer(PACKET_UPDATE_SOUND_LOOPER_CONFIG, packet);
    }

    public static void playNetworkSoundS2C(FriendlyByteBuf packet) {
        String url = packet.readUtf(32767);
        int sourceOrdinal = packet.readInt();
        BlockPos pos = packet.readBlockPos();
        float volume = packet.readFloat();

        Minecraft.getInstance().execute(() -> {
            SoundSource source = SoundSource.values()[sourceOrdinal];
            float gameVolume = Minecraft.getInstance().options.getSoundSourceVolume(source);
            float finalVolume = volume * gameVolume;
            NetworkAudioPlayer.play(url, finalVolume, pos);
        });
    }

	/** Saves the projector's settings back to the server. */
	public static void sendPIDSProjectorC2S(BlockPos pos, String presetID, java.util.Set<Long> platformIds,
											double offsetX, double offsetY, double offsetZ,
											double rotateX, double rotateY, double rotateZ, double scale) {
		final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
		packet.writeBlockPos(pos);
		packet.writeUtf(presetID);
		packet.writeInt(platformIds.size());
		platformIds.forEach(packet::writeLong);
		packet.writeDouble(offsetX);
		packet.writeDouble(offsetY);
		packet.writeDouble(offsetZ);
		packet.writeDouble(rotateX);
		packet.writeDouble(rotateY);
		packet.writeDouble(rotateZ);
		packet.writeDouble(scale);
		RegistryClient.sendToServer(IPacketJoban.PACKET_UPDATE_PIDS_PROJECTOR, packet);
	}

	public static void openPIDSProjectorScreenS2C(FriendlyByteBuf packet) {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		final BlockPos pos = packet.readBlockPos();
		final String presetID = packet.readUtf();
		final java.util.Set<Long> platformIds = new java.util.HashSet<>();
		final int platformCount = packet.readInt();
		for (int i = 0; i < platformCount; i++) {
			platformIds.add(packet.readLong());
		}
		final double offsetX = packet.readDouble();
		final double offsetY = packet.readDouble();
		final double offsetZ = packet.readDouble();
		final double rotateX = packet.readDouble();
		final double rotateY = packet.readDouble();
		final double rotateZ = packet.readDouble();
		final double scale = packet.readDouble();
		if (minecraft.level.getBlockEntity(pos) instanceof com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) {
			minecraft.execute(() -> {
				if (!(minecraft.screen instanceof PIDSProjectorScreen)) {
					UtilitiesClient.setScreen(minecraft, new PIDSProjectorScreen(pos, presetID, platformIds, offsetX, offsetY, offsetZ, rotateX, rotateY, rotateZ, scale));
				}
			});
		}
	}
}
