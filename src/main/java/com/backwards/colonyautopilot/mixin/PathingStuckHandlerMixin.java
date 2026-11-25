// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.ProblemLedger;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathingStuckHandler.class)
public class PathingStuckHandlerMixin
{

    @Unique
    private static final int COLONYAUTOPILOT$CHECK_TICKS = 12;

    @Unique
    private static final int COLONYAUTOPILOT$STALLED_TICKS = 400;

    @Unique
    private int colonyautopilot$stalled;

    @Unique
    private static final int COLONYAUTOPILOT$SAME_PLACE_TICKS = 6000;

    @Unique
    private BlockPos colonyautopilot$rescuedTo;
    @Unique
    private long colonyautopilot$rescuedAt;

    @Shadow
    private void completeStuckAction(final PathNavigation navigator)
    {
    }

    @Inject(method = "checkStuck(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;)V", at = @At("HEAD"), require = 0)
    private void colonyautopilot$rescueSooner(final PathNavigation navigator, final CallbackInfo ci)
    {

        if (!(navigator instanceof IMinecoloniesNavigator stalled) || stalled.getStuckHandler().getStuckLevel() < 1
              || !(stalled.getOurEntity() instanceof AbstractEntityCitizen citizen) || citizen.getCitizenData() == null
              || stalled.getPathResult() == null || !AutopilotConfig.SPEC.isLoaded()
              || !AutopilotConfig.live(citizen.getCitizenData().getColony(), AutopilotConfig.JOB_WATCH)
              || !(stalled.getSafeDestination() instanceof final BlockPos destination) || BlockPos.ZERO.equals(destination)
              || (destination.equals(colonyautopilot$rescuedTo) && citizen.level().getGameTime() - colonyautopilot$rescuedAt < COLONYAUTOPILOT$SAME_PLACE_TICKS)
              || BlockPosUtil.dist(citizen.blockPosition(), destination) <= 4
              || citizen.isInWater() || citizen.getCitizenSleepHandler().isAsleep() || !citizen.canBeStuck()
              || !(citizen.level() instanceof final ServerLevel level) || !level.isPositionEntityTicking(destination))
        {
            colonyautopilot$stalled = 0;
            return;
        }
        if (navigator.getPath() != null && !navigator.getPath().isDone())
        {
            return;
        }
        colonyautopilot$stalled += COLONYAUTOPILOT$CHECK_TICKS;
        if (colonyautopilot$stalled >= COLONYAUTOPILOT$STALLED_TICKS)
        {
            colonyautopilot$stalled = 0;
            colonyautopilot$rescuedTo = destination.immutable();
            colonyautopilot$rescuedAt = level.getGameTime();
            ColonyAutopilot.LOGGER.debug("[{}] {} was getting nowhere at {} on the way to {} for {} seconds — MineColonies' own rescue run now",
              citizen.getCitizenData().getColony().getName(), citizen.getCitizenData().getName(), citizen.blockPosition().toShortString(),
              destination.toShortString(), COLONYAUTOPILOT$STALLED_TICKS / 20);
            completeStuckAction(navigator);
        }
    }

    @Inject(method = "completeStuckAction", at = @At("HEAD"))
    private void colonyautopilot$noteTheRescue(final PathNavigation navigator, final CallbackInfo ci)
    {

        if (navigator instanceof IMinecoloniesNavigator rescued && rescued.getOurEntity() instanceof AbstractEntityCitizen citizen)
        {
            ProblemLedger.stuck(citizen, rescued.getSafeDestination());
        }
    }
}
