package com.jsblock.block;

import com.jsblock.packet.PacketServer;
import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Set;

/**
 * Abstract class for Joban PIDS, used for storing presets ID
 * @author LX86
 * @see com.jsblock.block.BlockPIDSBaseHorizontal
 */
public abstract class JobanPIDSBase extends BlockPIDSBaseHorizontal {

    protected JobanPIDSBase() {
        super();
    }

    /** @see BlockPIDSBaseHorizontal#BlockPIDSBaseHorizontal(net.minecraft.world.level.block.state.BlockBehaviour.Properties) */
    protected JobanPIDSBase(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        super(properties);
    }
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return IBlock.checkHoldingBrush(world, player, () -> {
            BlockPos otherPos = pos.relative(IBlock.getStatePropertySafe(state, FACING));
            BlockEntity entity1 = world.getBlockEntity(pos);
            BlockEntity entity2 = world.getBlockEntity(otherPos);
            if (entity1 instanceof TileEntityBlockJobanPIDS && entity2 instanceof TileEntityBlockJobanPIDS) {
                ((TileEntityBlockJobanPIDS) entity1).syncData();
                ((TileEntityBlockJobanPIDS) entity2).syncData();
                PacketServer.sendJobanPIDSConfigScreenS2C((ServerPlayer) player, pos, otherPos, ((TileEntityBlockJobanPIDS)entity1).getMaxArrivals(),
                        ((TileEntityBlockJobanPIDS) entity1).getPresetID(),
                        ((TileEntityBlockJobanPIDS) entity1).getAutoSwitchEnabled(), ((TileEntityBlockJobanPIDS) entity1).getAutoSwitchPreset(), ((TileEntityBlockJobanPIDS) entity1).getAutoSwitchCountdown(), ((TileEntityBlockJobanPIDS) entity1).getAutoSwitchDuration(),
                        ((TileEntityBlockJobanPIDS) entity1).getDepAutoSwitchEnabled(), ((TileEntityBlockJobanPIDS) entity1).getDepAutoSwitchPreset(), ((TileEntityBlockJobanPIDS) entity1).getDepAutoSwitchCountdown(), ((TileEntityBlockJobanPIDS) entity1).getDepAutoSwitchDuration(), ((TileEntityBlockJobanPIDS) entity1).getDepAutoSwitchUntilClose());
            }
        });
    }

    public abstract static class TileEntityBlockJobanPIDS extends TileEntityBlockPIDSBaseHorizontal {
        private String presetID = "";
        private boolean autoSwitchEnabled = false;
        private String autoSwitchPreset = "";
        private int autoSwitchCountdown = 10;
        private int autoSwitchDuration = 10;
        private boolean depAutoSwitchEnabled = false;
        private String depAutoSwitchPreset = "";
        private int depAutoSwitchCountdown = 10;
        private int depAutoSwitchDuration = 10;
        private boolean depAutoSwitchUntilClose = false;

        private static final String KEY_SCREEN_ID = "preset_id";
        private static final String KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled";
        private static final String KEY_AUTO_SWITCH_PRESET = "auto_switch_preset";
        private static final String KEY_AUTO_SWITCH_COUNTDOWN = "auto_switch_countdown";
        private static final String KEY_AUTO_SWITCH_DURATION = "auto_switch_duration";
        private static final String KEY_DEP_AUTO_SWITCH_ENABLED = "dep_auto_switch_enabled";
        private static final String KEY_DEP_AUTO_SWITCH_PRESET = "dep_auto_switch_preset";
        private static final String KEY_DEP_AUTO_SWITCH_COUNTDOWN = "dep_auto_switch_countdown";
        private static final String KEY_DEP_AUTO_SWITCH_DURATION = "dep_auto_switch_duration";
        private static final String KEY_DEP_AUTO_SWITCH_UNTIL_CLOSE = "dep_auto_switch_until_close";

        public TileEntityBlockJobanPIDS(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            this.presetID = compoundTag.getString(KEY_SCREEN_ID);
            this.autoSwitchEnabled = compoundTag.getBoolean(KEY_AUTO_SWITCH_ENABLED);
            this.autoSwitchPreset = compoundTag.getString(KEY_AUTO_SWITCH_PRESET);
            this.autoSwitchCountdown = compoundTag.getInt(KEY_AUTO_SWITCH_COUNTDOWN);
            this.autoSwitchDuration = compoundTag.getInt(KEY_AUTO_SWITCH_DURATION);
            this.depAutoSwitchEnabled = compoundTag.getBoolean(KEY_DEP_AUTO_SWITCH_ENABLED);
            this.depAutoSwitchPreset = compoundTag.getString(KEY_DEP_AUTO_SWITCH_PRESET);
            this.depAutoSwitchCountdown = compoundTag.getInt(KEY_DEP_AUTO_SWITCH_COUNTDOWN);
            this.depAutoSwitchDuration = compoundTag.getInt(KEY_DEP_AUTO_SWITCH_DURATION);
            this.depAutoSwitchUntilClose = compoundTag.getBoolean(KEY_DEP_AUTO_SWITCH_UNTIL_CLOSE);
            super.readCompoundTag(compoundTag);
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            compoundTag.putString(KEY_SCREEN_ID, this.presetID);
            compoundTag.putBoolean(KEY_AUTO_SWITCH_ENABLED, this.autoSwitchEnabled);
            compoundTag.putString(KEY_AUTO_SWITCH_PRESET, this.autoSwitchPreset);
            compoundTag.putInt(KEY_AUTO_SWITCH_COUNTDOWN, this.autoSwitchCountdown);
            compoundTag.putInt(KEY_AUTO_SWITCH_DURATION, this.autoSwitchDuration);
            compoundTag.putBoolean(KEY_DEP_AUTO_SWITCH_ENABLED, this.depAutoSwitchEnabled);
            compoundTag.putString(KEY_DEP_AUTO_SWITCH_PRESET, this.depAutoSwitchPreset);
            compoundTag.putInt(KEY_DEP_AUTO_SWITCH_COUNTDOWN, this.depAutoSwitchCountdown);
            compoundTag.putInt(KEY_DEP_AUTO_SWITCH_DURATION, this.depAutoSwitchDuration);
            compoundTag.putBoolean(KEY_DEP_AUTO_SWITCH_UNTIL_CLOSE, this.depAutoSwitchUntilClose);
            super.writeCompoundTag(compoundTag);
        }

        @Override
        public abstract int getMaxArrivals();

        public void setData(String[] messages, boolean[] hideArrival, Set<Long> platformIds, int displayPage, String screenID,
                            boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
                            boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
            super.setData(messages, hideArrival, platformIds, displayPage);
            this.presetID = screenID;
            this.autoSwitchEnabled = autoSwitchEnabled;
            this.autoSwitchPreset = autoSwitchPreset;
            this.autoSwitchCountdown = autoSwitchCountdown;
            this.autoSwitchDuration = autoSwitchDuration;
            this.depAutoSwitchEnabled = depAutoSwitchEnabled;
            this.depAutoSwitchPreset = depAutoSwitchPreset;
            this.depAutoSwitchCountdown = depAutoSwitchCountdown;
            this.depAutoSwitchDuration = depAutoSwitchDuration;
            this.depAutoSwitchUntilClose = depAutoSwitchUntilClose;
            this.setChanged();
            this.syncData();
        }

        /** Sets the preset alone; the projector has no room for the whole config form. */
        public void setPresetID(String presetID) {
            this.presetID = presetID;
            this.setChanged();
            this.syncData();
        }

        /**
         * Replaces the platform filter; the projector's screen is the only caller.
         *
         * <p>MTR keeps the set private and only exposes {@link #getPlatformIds()}, so this edits it
         * through the getter — the same set the renderer reads and the same one {@code setData}
         * fills.</p>
         */
        public void setPlatformIds(java.util.Set<Long> newPlatformIds) {
            this.getPlatformIds().clear();
            this.getPlatformIds().addAll(newPlatformIds);
            this.setChanged();
            this.syncData();
        }

        public String getPresetID() {
            return presetID;
        }

        public boolean getAutoSwitchEnabled() {
            return autoSwitchEnabled;
        }

        public String getAutoSwitchPreset() {
            return autoSwitchPreset;
        }

        public int getAutoSwitchCountdown() {
            return autoSwitchCountdown;
        }

        public int getAutoSwitchDuration() {
            return autoSwitchDuration;
        }

        public boolean getDepAutoSwitchEnabled() {
            return depAutoSwitchEnabled;
        }

        public String getDepAutoSwitchPreset() {
            return depAutoSwitchPreset;
        }

        public int getDepAutoSwitchCountdown() {
            return depAutoSwitchCountdown;
        }

        public int getDepAutoSwitchDuration() {
            return depAutoSwitchDuration;
        }

        public boolean getDepAutoSwitchUntilClose() {
            return depAutoSwitchUntilClose;
        }
    }
}