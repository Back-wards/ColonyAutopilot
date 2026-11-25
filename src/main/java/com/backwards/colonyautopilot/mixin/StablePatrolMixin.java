// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.ColonyAutopilot;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(BuildingStable.class)
public class StablePatrolMixin
{

    @Unique
    private static final int COLONY_TICK = 500;

    @Unique
    private static final int RIDE_TICKS = 4800;

    @Unique
    private BlockPos colonyautopilot$namedLast;

    @Unique
    private static final int PAUSE_TICKS = 2 * COLONY_TICK - COLONY_TICK / 5;

    @Unique
    private static final int AT_THE_POINT = 8;

    @Unique
    private long colonyautopilot$pausedSince = -1;

    @Inject(method = "startPatrolNext()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void colonyautopilot$standAtTheBuilding(final CallbackInfo ci)
    {
        final BuildingStable stable = (BuildingStable) (Object) this;
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.live(stable.getColony(), AutopilotConfig.CAVALRY_PATROLS) || stable.shallPatrolManually()
              || stable.minutesSinceLastPatrol() < stable.getSetting(BuildingStable.PATROL_INTERVAL).getValue())
        {
            return;
        }
        final BlockPos point = stable.getNextPatrolTarget(false);
        boolean there = false;
        if (point != null)
        {
            for (final ICitizenData rider : stable.getModule(BuildingModules.CAVALRY_STABLE_WORK).getAssignedCitizen())
            {
                there |= rider.getEntity().isPresent() && BlockPosUtil.getDistance2D(rider.getEntity().get().blockPosition(), point) <= AT_THE_POINT;
            }
        }
        if (!there)
        {
            colonyautopilot$pausedSince = -1;
            return;
        }
        final long now = stable.getColony().getWorld().getGameTime();
        if (colonyautopilot$pausedSince < 0)
        {
            colonyautopilot$pausedSince = now;
        }
        if (now - colonyautopilot$pausedSince < PAUSE_TICKS)
        {

            stable.setPatrolTimer(1);
            ci.cancel();
            return;
        }
        colonyautopilot$pausedSince = -1;
    }

    @Inject(method = "startPatrolNext()V",
      at = @At(value = "INVOKE", target = "Lcom/minecolonies/core/colony/buildings/AbstractBuildingGuards;startPatrolNext()V", shift = At.Shift.AFTER),
      require = 0)
    private void colonyautopilot$nameTheNextBuilding(final CallbackInfo ci)
    {
        final BuildingStable stable = (BuildingStable) (Object) this;
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.live(stable.getColony(), AutopilotConfig.CAVALRY_PATROLS) || stable.shallPatrolManually())
        {
            return;
        }

        stable.setPatrolTimer((stable.getSetting(BuildingStable.PATROL_INTERVAL).getValue() * 1200 + RIDE_TICKS) / COLONY_TICK);
        final BlockPos home = stable.getPosition();
        final int reach = stable.getPatrolDistance();

        final BlockPos last = colonyautopilot$namedLast;
        final BlockPos target = stable.getColony().getServerBuildingManager().getRandomBuilding(
          building -> building.getBuildingLevel() >= 1 && building != stable && !building.getPosition().equals(last)
                        && BlockPosUtil.getDistance(building.getPosition(), home) <= reach);
        final BlockPos point = stable.patrolPointForBuilding(target);
        if (point != null && BlockPosUtil.getDistance(point, home) <= reach)
        {
            colonyautopilot$namedLast = target;
            stable.setTempNextPatrolPoint(point);
            ColonyAutopilot.LOGGER.debug("[{}] the stable at {} names {} for its riders' outing after this one",
              stable.getColony().getName(), home.toShortString(), point.toShortString());
        }
    }

    @Inject(method = "cavalryPatrolFilter()Ljava/util/function/Predicate;", at = @At("RETURN"), cancellable = true, require = 0)
    private static void colonyautopilot$patrolTheTown(final CallbackInfoReturnable<Predicate<IBuilding>> cir)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn())
        {

            final Predicate<IBuilding> own = cir.getReturnValue();
            cir.setReturnValue(building -> AutopilotConfig.get(building.getColony(), AutopilotConfig.CAVALRY_PATROLS)
                                             ? building.getBuildingLevel() >= 1 : own.test(building));
        }
    }
}
