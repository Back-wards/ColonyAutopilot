// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.ProvidenceSweep;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.TOOL_PERMISSION_SCEPTER_PERMISSION_DENY;

@Pseudo
@Mixin(targets = "steve_gall.minecolonies_tweaks.core.common.network.message.ResearchCostRequestMessage")
public class ResearchCostRequestMixin
{
    @Inject(method = "handle(Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void colonyautopilot$locked(final IPayloadContext context, final CallbackInfo ci)
    {
        final IBuilding building;
        try
        {
            final Object pos = this.getClass().getMethod("getBuildingPos").invoke(this);
            building = pos == null ? null : (IBuilding) pos.getClass().getMethod("getBuilding").invoke(pos);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return;
        }
        final Player player = context.player();

        if (building != null && player instanceof ServerPlayer sp && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn()
              && !building.getColony().getPermissions().hasPermission(sp, Action.MANAGE_HUTS))
        {
            MessageUtils.format(TOOL_PERMISSION_SCEPTER_PERMISSION_DENY).sendTo(sp);
            ColonyAutopilot.LOGGER.debug("[{}] {} may not manage the colony's huts — the research cost order refused",
              building.getColony().getName(), sp.getName().getString());
            ci.cancel();
            return;
        }
        if (building != null && player != null && ProvidenceSweep.postboxLocked(building.getColony()))
        {
            player.sendSystemMessage(Component.literal(ProvidenceSweep.RESEARCH_COST_LOCKED).withStyle(ChatFormatting.RED));
            ColonyAutopilot.LOGGER.debug("[{}] a research's cost is the colony's to buy in Progression Mode — {}'s order at the university refused",
              building.getColony().getName(), player.getName().getString());
            ci.cancel();
        }
    }
}
