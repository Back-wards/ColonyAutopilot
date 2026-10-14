// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractEntityAIGuard.class)
public abstract class GuardTargetMixin
{
    @Inject(method = "isAttackableTarget", at = @At("HEAD"), cancellable = true)
    private static void colonyautopilot$spareTheIgnored(final AbstractEntityCitizen user, final LivingEntity target,
      final CallbackInfoReturnable<Boolean> cir)
    {
        if (user != null && target == user.getLastHurtByMob())
        {
            return;
        }
        if (target.getType().is(ColonyAutopilot.GUARDS_IGNORE))
        {
            cir.setReturnValue(false);
        }
    }
}
