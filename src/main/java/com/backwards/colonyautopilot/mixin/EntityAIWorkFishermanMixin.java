// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.FishingPond;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFisherman;
import com.minecolonies.core.colony.jobs.JobFisherman;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAISkill;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFisherman;
import com.minecolonies.core.entity.pathfinding.pathresults.WaterPathResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityAIWorkFisherman.class)
public abstract class EntityAIWorkFishermanMixin extends AbstractEntityAISkill<JobFisherman, BuildingFisherman>
{
    @Shadow
    private int executedRotations;

    @Shadow
    private WaterPathResult lastPathResult;

    private EntityAIWorkFishermanMixin(final JobFisherman job)
    {
        super(job);
    }

    @Inject(method = "findWater", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$keepToTheHomePond(final CallbackInfoReturnable<IAIState> cir)
    {
        if (FishingPond.sendToHomePond(job))
        {
            executedRotations = 0;

            lastPathResult = null;
            cir.setReturnValue(AIWorkerState.FISHERMAN_CHECK_WATER);
        }
    }
}
