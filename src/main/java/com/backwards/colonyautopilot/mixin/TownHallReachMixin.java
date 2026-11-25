// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBuildingServerMessage.class)
public abstract class TownHallReachMixin
{
    @Redirect(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;Lcom/minecolonies/api/colony/IColony;)V",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/network/messages/server/AbstractBuildingServerMessage;isInRange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"),
      require = 0)
    private boolean colonyautopilot$townHallFromAfar(final AbstractBuildingServerMessage<?> message, final BlockPos from, final BlockPos to,
      final IPayloadContext context, final ServerPlayer player, final IColony colony)
    {
        if (message.isInRange(from, to))
        {
            return true;
        }
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        return hall != null && hall.getID().equals(to) && colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
    }
}
