// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(BuildingStable.class)
public class StableStallsMixin
{
    @Inject(method = "stallPositions()Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void colonyautopilot$stallsInsideOnly(final CallbackInfoReturnable<List<BlockPos>> cir)
    {
        final IBuilding stable = (IBuilding) (Object) this;
        final List<BlockPos> inside = new ArrayList<>();
        for (final BlockPos stall : cir.getReturnValue())
        {
            if (stable.isInBuilding(stall))
            {
                inside.add(stall);
            }
        }
        if (inside.size() != cir.getReturnValue().size())
        {
            cir.setReturnValue(inside);
        }
    }
}
