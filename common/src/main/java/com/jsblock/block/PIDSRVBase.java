package com.jsblock.block;

import com.jsblock.packet.PacketServer;
import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Abstract class for Railway Vision type PIDS
 * @author LX86
 * @since 1.1.0
 */
public abstract class PIDSRVBase extends JobanPIDSBase {

    /* If return InteractionResult.FAIL, the hand won't swing */
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return IBlock.checkHoldingBrush(world, player, () -> {
            final Direction facing = IBlock.getStatePropertySafe(state, FACING);
            BlockPos otherPos = pos.relative(facing);
            BlockEntity entity1 = world.getBlockEntity(pos);
            BlockEntity entity2 = world.getBlockEntity(otherPos);
            /* A PIDS is two blocks long along its facing axis, and the panel is addressed from
               whichever half is clicked. Only the leading half has its partner at pos + facing;
               from the trailing half that points one block past the structure, the check below
               failed, and the brush did nothing at all. Look the other way before giving up, so
               either half opens the screen. */
            if (!(entity2 instanceof TileEntityBlockRVPIDS)) {
                final BlockPos behind = pos.relative(facing.getOpposite());
                if (world.getBlockEntity(behind) instanceof TileEntityBlockRVPIDS) {
                    otherPos = behind;
                    entity2 = world.getBlockEntity(behind);
                }
            }
            if (entity1 instanceof TileEntityBlockRVPIDS && entity2 instanceof TileEntityBlockRVPIDS) {
                ((TileEntityBlockRVPIDS) entity1).syncData();
                ((TileEntityBlockRVPIDS) entity2).syncData();
                /* The screen opens showing the board's own text, not a blank it would send back. */
                final String[] messages = new String[((TileEntityBlockRVPIDS) entity1).getMaxArrivals()];
                final boolean[] rowHidden = new boolean[messages.length];
                for (int i = 0; i < messages.length; i++) {
                    messages[i] = ((TileEntityBlockRVPIDS) entity1).getMessage(i);
                    rowHidden[i] = ((TileEntityBlockRVPIDS) entity1).getHideArrival(i);
                }
                PacketServer.sendRVPIDSConfigScreenS2C((ServerPlayer) player, pos, otherPos, ((TileEntityBlockRVPIDS) entity1).getMaxArrivals(),
                        messages, rowHidden, ((TileEntityBlockRVPIDS) entity1).getPlatformIds(),
                        ((TileEntityBlockRVPIDS) entity1).getHidePlatformNumber(), ((TileEntityBlockRVPIDS) entity1).getPresetID(),
                        ((TileEntityBlockRVPIDS) entity1).getAutoSwitchEnabled(), ((TileEntityBlockRVPIDS) entity1).getAutoSwitchPreset(), ((TileEntityBlockRVPIDS) entity1).getAutoSwitchCountdown(), ((TileEntityBlockRVPIDS) entity1).getAutoSwitchDuration(),
                        ((TileEntityBlockRVPIDS) entity1).getDepAutoSwitchEnabled(), ((TileEntityBlockRVPIDS) entity1).getDepAutoSwitchPreset(), ((TileEntityBlockRVPIDS) entity1).getDepAutoSwitchCountdown(), ((TileEntityBlockRVPIDS) entity1).getDepAutoSwitchDuration(), ((TileEntityBlockRVPIDS) entity1).getDepAutoSwitchUntilClose());
            }
        });
    }

    public abstract static class TileEntityBlockRVPIDS extends JobanPIDSBase.TileEntityBlockJobanPIDS {
        private boolean hidePlatformNumber;
        private static final String KEY_HIDE_PLATFORM_NUMBER = "hide_platform_number";

        public TileEntityBlockRVPIDS(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            this.hidePlatformNumber = compoundTag.getBoolean(KEY_HIDE_PLATFORM_NUMBER);
            super.readCompoundTag(compoundTag);
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            compoundTag.putBoolean(KEY_HIDE_PLATFORM_NUMBER, this.hidePlatformNumber);
            super.writeCompoundTag(compoundTag);
        }

        public void setData(String[] messages, boolean[] hideArrival, Set<Long> platformIds, int displayPage, boolean hidePlatformNumber, String presetID,
                            boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
                            boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
            super.setData(messages, hideArrival, platformIds, displayPage, presetID, autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
                    depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
            this.hidePlatformNumber = hidePlatformNumber;
            this.setChanged();
            this.syncData();
        }

        @Override
        public abstract int getMaxArrivals();

        public boolean getHidePlatformNumber() {
            return hidePlatformNumber;
        }
    }
}