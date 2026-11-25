// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.UpgradeDirector;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(WorkOrderBuilding.class)
public abstract class BuilderReachMixin
{
    @Inject(method = "canBuild(Lcom/minecolonies/api/colony/buildings/IBuilding;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void colonyautopilot$nearestFreeCrew(final IBuilding hut, final CallbackInfoReturnable<Boolean> cir)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn())
        {
            final WorkOrderBuilding order = (WorkOrderBuilding) (Object) this;

            if (!(hut instanceof BuildingBuilder) || !order.canBuildIgnoringDistance(hut, hut.getPosition(), hut.getBuildingLevel()))
            {
                cir.setReturnValue(false);
                return;
            }
            final IBuilding nearest = UpgradeDirector.nearestCrew(order, hut.getColony().getServerBuildingManager().getBuildings().values(), Set.of());
            cir.setReturnValue(nearest != null && nearest.getID().equals(hut.getID()));
        }
    }

    @Inject(method = "tooFarFromAnyBuilder(Lcom/minecolonies/api/colony/IColony;I)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void colonyautopilot$noReachLimit(final IColony colony, final int level, final CallbackInfoReturnable<Boolean> cir)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn())
        {
            cir.setReturnValue(!((WorkOrderBuilding) (Object) this).canBeResolved(colony, 0));
        }
    }
}
