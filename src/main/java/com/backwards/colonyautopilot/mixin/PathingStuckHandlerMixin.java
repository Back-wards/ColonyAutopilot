// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ProblemLedger;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathingStuckHandler.class)
public class PathingStuckHandlerMixin
{
    @Inject(method = "completeStuckAction", at = @At("HEAD"))
    private void colonyautopilot$noteTheRescue(final PathNavigation navigator, final CallbackInfo ci)
    {

        if (navigator instanceof IMinecoloniesNavigator rescued && rescued.getOurEntity() instanceof AbstractEntityCitizen citizen)
        {
            ProblemLedger.stuck(citizen, rescued.getSafeDestination());
        }
    }
}
