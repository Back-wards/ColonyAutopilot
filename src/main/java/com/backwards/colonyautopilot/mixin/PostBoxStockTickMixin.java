// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ProvidenceSweep;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.minecolonies.core.colony.buildings.workerbuildings.PostBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinimumStockModule.class)
public class PostBoxStockTickMixin
{
    @Inject(method = "onColonyTick(Lcom/minecolonies/api/colony/IColony;)V", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$locked(final IColony colony, final CallbackInfo ci)
    {
        if (((MinimumStockModule) (Object) this).getBuilding() instanceof PostBox && ProvidenceSweep.postboxLocked(colony))
        {
            ci.cancel();
        }
    }
}
