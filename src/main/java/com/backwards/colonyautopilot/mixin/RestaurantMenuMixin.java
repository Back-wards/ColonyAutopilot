// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.ProvidenceSweep;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.network.messages.server.colony.building.AlterRestaurantMenuItemMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AlterRestaurantMenuItemMessage.class)
public class RestaurantMenuMixin
{
    @Inject(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;Lcom/minecolonies/api/colony/IColony;Lcom/minecolonies/api/colony/buildings/IBuilding;)V",
      at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$locked(final IPayloadContext context, final ServerPlayer player, final IColony colony, final IBuilding building, final CallbackInfo ci)
    {
        if (ProvidenceSweep.postboxLocked(colony))
        {
            player.sendSystemMessage(Component.literal(ProvidenceSweep.MENU_LOCKED).withStyle(ChatFormatting.RED));

            building.markDirty();
            ColonyAutopilot.LOGGER.debug("[{}] the menu is the colony's in Progression Mode — {}'s edit refused", colony.getName(), player.getName().getString());
            ci.cancel();
        }
    }
}
