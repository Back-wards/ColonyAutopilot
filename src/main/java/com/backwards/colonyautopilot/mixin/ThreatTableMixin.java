// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.minecolonies.api.entity.ai.combat.threat.ThreatTable;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThreatTable.class)
public abstract class ThreatTableMixin
{
    @Shadow
    @Final
    private LivingEntity owner;

    @Inject(method = "addThreat", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$noCallsAboutTheIgnored(final LivingEntity attacker, final int threat, final CallbackInfo ci)
    {
        if (owner instanceof AbstractEntityCitizen && attacker != owner.getLastHurtByMob()
              && attacker.getType().is(ColonyAutopilot.GUARDS_IGNORE))
        {
            ci.cancel();
        }
    }
}
