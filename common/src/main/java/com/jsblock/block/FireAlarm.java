package com.jsblock.block;

import mtr.block.IBlock;
import mtr.mappings.BlockDirectionalMapper;
import mtr.mappings.Utilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fire Alarm — ported from JCM 2.x's {@code FireAlarmWallBlock}.
 *
 * <p>A wall-mounted bell that does two things when it is pressed: it rings, and it puts out a
 * redstone signal of 15 for one second. The signal is the point — it is how a station's alarm
 * circuit knows something happened — and the state is named exactly as JCM 2.x names it, where
 * {@code unpowered} means idle, so a pack written against their block still lines up.</p>
 *
 * <p>JCM 2.x's version is silent; this one rings, because a bell that cannot be heard is not much
 * of a bell. Nothing else about it differs.</p>
 */
public class FireAlarm extends BlockDirectionalMapper {

	/** True while the alarm is idle. JCM 2.x's name and sense, kept deliberately. */
	private static final BooleanProperty UNPOWERED = BooleanProperty.create("unpowered");
	/** How long the alarm rings and signals, in ticks. One second, as JCM 2.x has it. */
	private static final int ALARM_TICKS = 20;

	public FireAlarm(Properties settings) {
		super(settings);
		registerDefaultState(defaultBlockState().setValue(UNPOWERED, true));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext collisionContext) {
		return IBlock.getVoxelShapeByDirection(5, 5, 0, 11, 11, 1, IBlock.getStatePropertySafe(state, FACING));
	}

	/** Only against a wall, and facing away from it — the same rule JCM 2.x applies. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		final Direction facing = ctx.getClickedFace().getOpposite();
		if (facing.getAxis().isVertical()) {
			return null;
		}
		return defaultBlockState()
				.setValue(FACING, facing)
				.setValue(UNPOWERED, true);
	}

	/** Falls off when its wall goes, the way every wall-mounted block does. */
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
								  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
		final Direction facing = IBlock.getStatePropertySafe(state, FACING);
		if (direction == facing.getOpposite() && !neighborState.isFaceSturdy(world, neighborPos, facing)) {
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!world.isClientSide) {
			world.setBlockAndUpdate(pos, state.setValue(UNPOWERED, false));
			updateNearby(world, pos);
			Utilities.scheduleBlockTick(world, pos, this, ALARM_TICKS);
			world.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1F, 1.2F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void tick(BlockState state, ServerLevel world, BlockPos pos) {
		if (world == null || world.isClientSide()) {
			return;
		}
		world.setBlockAndUpdate(pos, state.setValue(UNPOWERED, true));
		updateNearby(world, pos);
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
		return IBlock.getStatePropertySafe(state, UNPOWERED) ? 0 : 15;
	}

	@Override
	public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
		return IBlock.getStatePropertySafe(state, UNPOWERED) ? 0 : 15;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, UNPOWERED);
	}

	/** Wakes the neighbours so the signal is noticed the moment it changes. */
	private void updateNearby(Level world, BlockPos pos) {
		for (final Direction direction : Direction.values()) {
			world.updateNeighborsAt(pos.relative(direction), this);
		}
	}
}
