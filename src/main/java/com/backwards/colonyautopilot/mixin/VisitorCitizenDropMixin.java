// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisitorCitizen.class)
public class VisitorCitizenDropMixin
{

    private com.minecolonies.api.colony.IColony colonyautopilot$colony()
    {
        final com.minecolonies.api.colony.ICitizenData data = ((VisitorCitizen) (Object) this).getCitizenData();
        return data == null ? null : data.getColony();
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$noVisitorDrop(final CallbackInfo ci)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(colonyautopilot$colony(), AutopilotConfig.NO_GRAVES))
        {
            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void colonyautopilot$noVisitorEquipmentDrop(final DamageSource cause, final CallbackInfo ci)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(colonyautopilot$colony(), AutopilotConfig.NO_GRAVES))
        {
            for (final EquipmentSlot slot : EquipmentSlot.values())
            {
                ((Mob) (Object) this).setDropChance(slot, 0.0F);
            }
        }
    }
}
