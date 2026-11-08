package com.jsblock;

import com.jsblock.item.ItemPIDSConfigTool;
import com.jsblock.item.ItemPSDAPGBase;
import mtr.RegistryObject;
import net.minecraft.world.item.Item;

public interface Items {
    RegistryObject<Item> PIDS_CONFIG_TOOL = new RegistryObject<>(ItemPIDSConfigTool::new);
    RegistryObject<Item> APG_DOOR_DRL = new RegistryObject<>(() -> new ItemPSDAPGBase(ItemPSDAPGBase.EnumPSDAPGItem.PSD_APG_DOOR, ItemPSDAPGBase.EnumPSDAPGType.DRL_APG));
    RegistryObject<Item> APG_GLASS_DRL = new RegistryObject<>(() -> new ItemPSDAPGBase(ItemPSDAPGBase.EnumPSDAPGItem.PSD_APG_GLASS, ItemPSDAPGBase.EnumPSDAPGType.DRL_APG));
    RegistryObject<Item> APG_GLASS_END_DRL = new RegistryObject<>(() -> new ItemPSDAPGBase(ItemPSDAPGBase.EnumPSDAPGItem.PSD_APG_GLASS_END, ItemPSDAPGBase.EnumPSDAPGType.DRL_APG));
}