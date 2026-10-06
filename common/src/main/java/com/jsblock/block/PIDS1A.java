package com.jsblock.block;

import com.jsblock.BlockEntityTypes;
import mtr.block.IBlock;
import mtr.mappings.BlockEntityMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * PIDS 1A (Larger Version of PIDS_1)
 * @author LX86
 * @see com.jsblock.block.BlockPIDSBaseHorizontal
 */
public class PIDS1A extends JobanPIDSBase {

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext collisionContext) {
        VoxelShape shape1 = IBlock.getVoxelShapeByDirection(6, 0, 0, 10, 11, 16, IBlock.getStatePropertySafe(state, FACING));
        VoxelShape shape2 = IBlock.getVoxelShapeByDirection(7.5, 11, 12.5, 8.5, 16, 13.5, IBlock.getStatePropertySafe(state, FACING));
        return Shapes.or(shape1, shape2);
    }

    @Override
    public BlockEntityMapper createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityBlockPIDS1A(pos, state);
    }

    public static class TileEntityBlockPIDS1A extends JobanPIDSBase.TileEntityBlockJobanPIDS {

        public static final int MAX_ARRIVALS = 3;
        public static final int LINES_PER_ARRIVAL = 1;

        public TileEntityBlockPIDS1A(BlockPos pos, BlockState state) {
            super(BlockEntityTypes.PIDS_1A_TILE_ENTITY.get(), pos, state);
        }

        @Override
        public int getMaxArrivals() {
            return MAX_ARRIVALS;
        }
    }
}