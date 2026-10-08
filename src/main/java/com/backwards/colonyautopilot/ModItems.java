// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColonyAutopilot.MOD_ID);

    public static final DeferredItem<Item> ZONE_MARKER =
      ITEMS.registerItem("zone_marker", properties -> new ZoneMarkerItem(properties.stacksTo(1)));

    public static final DeferredItem<Item> COLONY_BUCKS =
      ITEMS.registerSimpleItem("colony_bucks", new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON).fireResistant());

    private ModItems()
    {
    }
}
