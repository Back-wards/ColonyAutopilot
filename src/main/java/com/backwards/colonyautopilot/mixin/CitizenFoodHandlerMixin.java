// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenFoodHandler;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenFoodHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CitizenFoodHandler.class)
public class CitizenFoodHandlerMixin
{
    @Redirect(method = "addLastEaten",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/api/colony/ICitizenData;triggerInteraction(Lcom/minecolonies/api/colony/interactionhandling/IInteractionResponseHandler;)V"))
    private void colonyautopilot$swallowFoodComplaint(final ICitizenData citizen, final IInteractionResponseHandler complaint)
    {

        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.live(AutopilotConfig.IGNORE_FOOD_QUALITY))
        {
            citizen.triggerInteraction(complaint);
        }
    }

    @Inject(method = "getFoodHappinessStats", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$satisfiedDiet(final CallbackInfoReturnable<ICitizenFoodHandler.CitizenFoodStats> cir)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(AutopilotConfig.IGNORE_FOOD_QUALITY))
        {

            cir.setReturnValue(new ICitizenFoodHandler.CitizenFoodStats(10, 10));
        }
    }
}
