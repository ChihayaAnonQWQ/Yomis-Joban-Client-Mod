package com.jsblock.item;

import com.jsblock.block.JobanPIDSBase;
import com.jsblock.packet.PacketClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Right-click a PIDS to paste the configuration copied from another one; left-click copies.
 *
 * <p>Left-click is handled by the loader hooks, because breaking a block never reaches an item's
 * {@code useOn} and in creative mode it would otherwise remove the PIDS outright. The copy itself
 * is taken server-side from the block entity's saved tag, so a configuration copied in one world
 * can be pasted into another.</p>
 */
public class ItemPIDSConfigTool extends Item {

	public ItemPIDSConfigTool() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.jsblock.pids_config_tool.desc"));
		tooltip.add(Component.translatable("item.jsblock.pids_config_tool.desc2"));
		tooltip.add(Component.translatable("item.jsblock.pids_config_tool.desc3"));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		final Level level = context.getLevel();
		final BlockPos pos = context.getClickedPos();
		final BlockEntity entity = level.getBlockEntity(pos);

		if (!(entity instanceof JobanPIDSBase.TileEntityBlockJobanPIDS)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			PacketClient.sendPidsToolPaste(pos);
		}

		return InteractionResult.SUCCESS;
	}
}
