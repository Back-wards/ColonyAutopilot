// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.ProvidenceSweep;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.workerbuildings.PostBox;
import com.minecolonies.core.network.messages.server.colony.building.postbox.PostBoxRequestMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostBoxRequestMessage.class)
public class PostBoxRequestMixin
{

    @Inject(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;Lcom/minecolonies/api/colony/IColony;Lcom/minecolonies/core/colony/buildings/workerbuildings/PostBox;)V",
      at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$locked(final IPayloadContext context, final ServerPlayer player, final IColony colony, final PostBox postBox, final CallbackInfo ci)
    {
        if (ProvidenceSweep.postboxLocked(colony))
        {
            player.sendSystemMessage(Component.literal(ProvidenceSweep.POSTBOX_LOCKED).withStyle(ChatFormatting.RED));
            ColonyAutopilot.LOGGER.debug("[{}] the Postbox is locked in Progression Mode — {}'s order refused", colony.getName(), player.getName().getString());
            ci.cancel();
        }
    }
}
