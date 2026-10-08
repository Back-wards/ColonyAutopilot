// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

final class GuideMeCompat
{
    private static final String MOD_ID = "guideme";

    private GuideMeCompat()
    {
    }

    @SuppressWarnings("unchecked")
    static ItemStack guideStack()
    {
        if (!ModList.get().isLoaded(MOD_ID))
        {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_ID, "guide")).orElse(null);
        final DataComponentType<?> guideId =
          BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_ID, "guide_id")).orElse(null);
        if (item == null || guideId == null)
        {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = new ItemStack(item);
        stack.set((DataComponentType<ResourceLocation>) guideId,
          ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "guide"));
        return stack;
    }
}
