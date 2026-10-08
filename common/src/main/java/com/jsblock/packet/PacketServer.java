package com.jsblock.packet;

import com.jsblock.Joban;
import com.jsblock.block.ButterflyLight;
import com.jsblock.block.FareSaver1;
import com.jsblock.block.JobanPIDSBase;
import com.jsblock.block.PIDSRVBase;
import com.jsblock.block.SoundLooper;
import com.jsblock.block.SubsidyMachine1;
import io.netty.buffer.Unpooled;
import mtr.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;

import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_BUTTERFLY_CONFIG_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_FARESAVER_CONFIG_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_JOBAN_PIDS_CONFIG_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_RV_PIDS_CONFIG_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_PIDS_PROJECTOR_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_SOUND_LOOPER_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_OPEN_SUBSIDY_CONFIG_SCREEN;
import static com.jsblock.packet.IPacketJoban.PACKET_PLAY_NETWORK_SOUND;
import static com.jsblock.packet.IPacketJoban.PACKET_VERSION_CHECK;
import static mtr.data.SerializedDataBase.PACKET_STRING_READ_LENGTH;

/**
 * This class is responsible for handling packets, that should be executed on the SERVER side, e.g.
 * Method for sending packets from the Server to Client (S2C)
 * Method for handling packets sent from the Client to Server (C2S)
 */
public class PacketServer {

    public static void receiveButterflyC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
        final BlockPos pos = packet.readBlockPos();
        final int countdown = packet.readInt();
        minecraftServer.execute(() -> {
            final BlockEntity entity = player.level().getBlockEntity(pos);
            if (entity instanceof ButterflyLight.TileEntityButterFlyLight) {
                ((ButterflyLight.TileEntityButterFlyLight) entity).setData(countdown);
            }
        });
    }

    public static void receiveFaresaverC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
        final BlockPos pos = packet.readBlockPos();
        final int discount = packet.readInt();
        minecraftServer.execute(() -> {
            final BlockEntity entity = player.level().getBlockEntity(pos);
            if (entity instanceof FareSaver1.TileEntityFareSaver) {
                ((FareSaver1.TileEntityFareSaver) entity).setAllData(discount);
            }
        });
    }

    /* When Server receives the new RV PIDS Config sent by client */
    public static void receiveRVPIDSConfigC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
        final BlockPos pos1 = packet.readBlockPos();
        final BlockPos pos2 = packet.readBlockPos();
        final int maxArrivals = packet.readInt();
        final String[] messages = new String[maxArrivals];
        final boolean[] hideArrivals = new boolean[maxArrivals];
        for (int i = 0; i < maxArrivals; i++) {
            messages[i] = packet.readUtf(PACKET_STRING_READ_LENGTH);
            hideArrivals[i] = packet.readBoolean();
        }
        final int platformIdCount = packet.readInt();
        final Set<Long> platformIds = new HashSet<>();
        for (int i = 0; i < platformIdCount; i++) {
            platformIds.add(packet.readLong());
        }

        final boolean hidePlatformNumber = packet.readBoolean();
        final String presetID = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final boolean autoSwitchEnabled = packet.readBoolean();
        final String autoSwitchPreset = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final int autoSwitchCountdown = packet.readInt();
        final int autoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchEnabled = packet.readBoolean();
        final String depAutoSwitchPreset = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final int depAutoSwitchCountdown = packet.readInt();
        final int depAutoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchUntilClose = packet.readBoolean();
        minecraftServer.execute(() -> {
            final BlockEntity entity1 = player.level().getBlockEntity(pos1);
            if (entity1 instanceof PIDSRVBase.TileEntityBlockRVPIDS) {
                ((PIDSRVBase.TileEntityBlockRVPIDS) entity1).setData(messages, hideArrivals, platformIds, 0, hidePlatformNumber, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration, depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
            }
            final BlockEntity entity2 = player.level().getBlockEntity(pos2);
            if (entity2 instanceof PIDSRVBase.TileEntityBlockRVPIDS) {
                ((PIDSRVBase.TileEntityBlockRVPIDS) entity2).setData(messages, hideArrivals, platformIds, 0, hidePlatformNumber, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration, depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
            }
        });
    }

    /* When Server receives the new Joban PIDS Config sent by client */
    public static void receiveJobanPIDSConfigC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
        final BlockPos pos1 = packet.readBlockPos();
        final BlockPos pos2 = packet.readBlockPos();
        final int maxArrivals = packet.readInt();
        final String[] messages = new String[maxArrivals];
        final boolean[] hideArrivals = new boolean[maxArrivals];
        for (int i = 0; i < maxArrivals; i++) {
            messages[i] = packet.readUtf(PACKET_STRING_READ_LENGTH);
            hideArrivals[i] = packet.readBoolean();
        }
        final int platformIdCount = packet.readInt();
        final Set<Long> platformIds = new HashSet<>();
        for (int i = 0; i < platformIdCount; i++) {
            platformIds.add(packet.readLong());
        }

        /* On 1.16.5, readString() is client-side only */
        /* Have to use the overload for it to work on server as well */
        final String presetID = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final boolean autoSwitchEnabled = packet.readBoolean();
        final String autoSwitchPreset = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final int autoSwitchCountdown = packet.readInt();
        final int autoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchEnabled = packet.readBoolean();
        final String depAutoSwitchPreset = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final int depAutoSwitchCountdown = packet.readInt();
        final int depAutoSwitchDuration = packet.readInt();
        final boolean depAutoSwitchUntilClose = packet.readBoolean();
        minecraftServer.execute(() -> {
            final BlockEntity entity1 = player.level().getBlockEntity(pos1);
            if (entity1 instanceof JobanPIDSBase.TileEntityBlockPIDSBaseHorizontal) {
                ((JobanPIDSBase.TileEntityBlockJobanPIDS) entity1).setData(messages, hideArrivals, platformIds, 1, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration, depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
            }
            final BlockEntity entity2 = player.level().getBlockEntity(pos2);
            if (entity2 instanceof JobanPIDSBase.TileEntityBlockPIDSBaseHorizontal) {
                ((JobanPIDSBase.TileEntityBlockJobanPIDS) entity2).setData(messages, hideArrivals, platformIds, 1, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration, depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
            }
        });
    }

    public static void receiveSoundLooperC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
        final BlockPos pos = packet.readBlockPos();
        final BlockPos pos1 = packet.readBlockPos();
        final BlockPos pos2 = packet.readBlockPos();
        final String soundId = packet.readUtf(PACKET_STRING_READ_LENGTH);
        final int soundCategory = packet.readInt();
        final int interval = packet.readInt();
        final float volume = packet.readFloat();
        final boolean needRedstone = packet.readBoolean();
        final boolean limitRange = packet.readBoolean();

        final boolean useNetworkAudio = packet.readBoolean();
        final String networkAudioUrl = packet.readUtf(PACKET_STRING_READ_LENGTH);

        minecraftServer.execute(() -> {
            final BlockEntity entity = player.level().getBlockEntity(pos);
            if (entity instanceof SoundLooper.TileEntitySoundLooper) {
                ((SoundLooper.TileEntitySoundLooper) entity).setData(
                        soundId, soundCategory, interval, volume, needRedstone, limitRange,
                        pos1, pos2, useNetworkAudio, networkAudioUrl
                );
            }
        });
    }

    public static void receiveSubsidyC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
        final BlockPos pos = packet.readBlockPos();
        final int pricePerClick = packet.readInt();
        final int timeout = packet.readInt();
        minecraftServer.execute(() -> {
            final BlockEntity entity = player.level().getBlockEntity(pos);
            if (entity instanceof SubsidyMachine1.TileEntitySubsidyMachine) {
                ((SubsidyMachine1.TileEntitySubsidyMachine) entity).setData(pricePerClick, timeout);
            }
        });
    }

    /* Version check packet (The client is the one responsible for disconnecting) */
    public static void sendVersionCheckS2C(ServerPlayer player) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeUtf(Joban.getVersion().split("-hotfix-")[0]);
        Registry.sendToPlayer(player, PACKET_VERSION_CHECK, packet);
    }

    /* Packet to open the Butterfly Light Configuration GUI in client */
    public static void sendButterflyConfigScreenS2C(ServerPlayer player, BlockPos pos1, int countdown) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos1);
        packet.writeInt(countdown);
        Registry.sendToPlayer(player, PACKET_OPEN_BUTTERFLY_CONFIG_SCREEN, packet);
    }

    /* Packet to open the Fare Saver Configuration GUI in client */
    public static void sendFaresaverConfigScreenS2C(ServerPlayer player, BlockPos pos1, int discount) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos1);
        packet.writeInt(discount);
        Registry.sendToPlayer(player, PACKET_OPEN_FARESAVER_CONFIG_SCREEN, packet);
    }

    /* Packet to open the Joban PIDS Configuration GUI in client */
    public static void sendJobanPIDSConfigScreenS2C(ServerPlayer player, BlockPos pos1, BlockPos pos2, int maxArrivals, String presetID, boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration, boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos1);
        packet.writeBlockPos(pos2);
        packet.writeInt(maxArrivals);
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
        Registry.sendToPlayer(player, PACKET_OPEN_JOBAN_PIDS_CONFIG_SCREEN, packet);
    }

    /* Packet to open the Railway Vision PIDS Configuration GUI in client */
    public static void sendRVPIDSConfigScreenS2C(ServerPlayer player, BlockPos pos1, BlockPos pos2, int maxArrivals, boolean hidePlatformNumber, String presetID, boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration, boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos1);
        packet.writeBlockPos(pos2);
        packet.writeInt(maxArrivals);
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
        Registry.sendToPlayer(player, PACKET_OPEN_RV_PIDS_CONFIG_SCREEN, packet);
    }

    /* Packet to open the Subsidy Machine Configuration GUI in client */
    public static void sendSubsidyConfigScreenS2C(ServerPlayer player, BlockPos pos, int pricePerClick, int timeout) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos);
        packet.writeInt(pricePerClick);
        packet.writeInt(timeout);
        Registry.sendToPlayer(player, PACKET_OPEN_SUBSIDY_CONFIG_SCREEN, packet);
    }

    /* Packet to open the Sound Looper GUI in client */
    public static void sendSoundLooperScreenS2C(ServerPlayer player, BlockPos pos) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBlockPos(pos);
        Registry.sendToPlayer(player, PACKET_OPEN_SOUND_LOOPER_SCREEN, packet);
    }

    public static void sendPlayNetworkSoundS2C(ServerPlayer player, String url, SoundSource source, BlockPos pos, float volume) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeUtf(url);
        packet.writeInt(source.ordinal());
        packet.writeBlockPos(pos);
        packet.writeFloat(volume);
        Registry.sendToPlayer(player, PACKET_PLAY_NETWORK_SOUND, packet);
    }

	/**
	 * Opens the PIDS Projector's screen, which edits its preset and its placement.
	 *
	 * <p>A packet of its own rather than more fields on the PIDS config packet: a projector is one
	 * block with no companion, and its screen is a different screen. Keeping them apart means the
	 * verified two-block PIDS configuration flow is not touched at all.</p>
	 */
	public static void sendPIDSProjectorScreenS2C(ServerPlayer player, BlockPos pos, String presetID,
												  String[] messages, boolean[] rowHidden, boolean hidePlatformNumber,
												  java.util.Set<Long> platformIds,
												  double offsetX, double offsetY, double offsetZ,
												  double rotateX, double rotateY, double rotateZ, double scale) {
		final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
		packet.writeBlockPos(pos);
		packet.writeInt(messages.length);
		for (int i = 0; i < messages.length; i++) {
			packet.writeUtf(messages[i] == null ? "" : messages[i]);
		}
		packet.writeInt(rowHidden.length);
		for (int i = 0; i < rowHidden.length; i++) {
			packet.writeBoolean(rowHidden[i]);
		}
		packet.writeInt(platformIds.size());
		platformIds.forEach(packet::writeLong);
		packet.writeBoolean(hidePlatformNumber);
		packet.writeUtf(presetID);
		packet.writeDouble(offsetX);
		packet.writeDouble(offsetY);
		packet.writeDouble(offsetZ);
		packet.writeDouble(rotateX);
		packet.writeDouble(rotateY);
		packet.writeDouble(rotateZ);
		packet.writeDouble(scale);
		Registry.sendToPlayer(player, PACKET_OPEN_PIDS_PROJECTOR_SCREEN, packet);
	}

	/** Saves what the projector's screen sent back. */
	public static void receivePIDSProjectorC2S(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
		final BlockPos pos = packet.readBlockPos();
		final int messageCount = packet.readInt();
		final String[] messages = new String[messageCount];
		for (int i = 0; i < messageCount; i++) {
			messages[i] = packet.readUtf(PACKET_STRING_READ_LENGTH);
		}
		final int rowCount = packet.readInt();
		final boolean[] rowHidden = new boolean[rowCount];
		for (int i = 0; i < rowCount; i++) {
			rowHidden[i] = packet.readBoolean();
		}
		final Set<Long> platformIds = new HashSet<>();
		final int platformCount = packet.readInt();
		for (int i = 0; i < platformCount; i++) {
			platformIds.add(packet.readLong());
		}
		final boolean hidePlatformNumber = packet.readBoolean();
		final String presetID = packet.readUtf(PACKET_STRING_READ_LENGTH);
		final double offsetX = packet.readDouble();
		final double offsetY = packet.readDouble();
		final double offsetZ = packet.readDouble();
		final double rotateX = packet.readDouble();
		final double rotateY = packet.readDouble();
		final double rotateZ = packet.readDouble();
		final double scale = packet.readDouble();
		minecraftServer.execute(() -> {
			final BlockEntity entity = player.level().getBlockEntity(pos);
			if (entity instanceof com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) {
				final com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector projector =
						(com.jsblock.block.PIDSProjector.TileEntityBlockPIDSProjector) entity;
				projector.setPresetID(presetID);
				projector.setHidePlatformNumber(hidePlatformNumber);
				/* MTR's own setter for a PIDS panel's text, hidden rows and platforms; the projector
				   inherits it, and it is what pids.getCustomMessage(i) reads back. JCM 2.x applies the
				   same set on the same fields, from the same packet. */
				projector.setData(messages, rowHidden, platformIds, 0);
				projector.setProjectorTransform(offsetX, offsetY, offsetZ, rotateX, rotateY, rotateZ, scale);
			} else {
				Joban.LOGGER.warn("[PIDS projector] server could not apply: block entity at " + pos
						+ " is " + (entity == null ? "null" : entity.getClass().getName()));
			}
		});
	}
}
