// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class GunEnchantabilityMixin
{
    @Unique
    private static final ResourceLocation COLONYAUTOPILOT$GUN_ID = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");

    @Unique
    private static final int COLONYAUTOPILOT$GUN_ENCHANTABILITY = 16;

    @Unique
    private static Item colonyautopilot$gun;

    @Unique
    private boolean colonyautopilot$isEnchantableGun()
    {
        if (colonyautopilot$gun == null)
        {

            final Item found = BuiltInRegistries.ITEM.get(COLONYAUTOPILOT$GUN_ID);
            if (found == Items.AIR)
            {
                return false;
            }
            colonyautopilot$gun = found;
        }
        return (Object) this == colonyautopilot$gun
                 && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(AutopilotConfig.GUN_ENCHANTMENTS);
    }

    @Inject(method = "isEnchantable", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$gunsTakeEnchantments(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir)
    {

        if (colonyautopilot$isEnchantableGun()
              && !stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                    .contains(AutopilotConfig.ISSUED_GUN_TAG))
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getEnchantmentValue", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$gunEnchantability(final CallbackInfoReturnable<Integer> cir)
    {
        if (colonyautopilot$isEnchantableGun())
        {
            cir.setReturnValue(COLONYAUTOPILOT$GUN_ENCHANTABILITY);
        }
    }
}
