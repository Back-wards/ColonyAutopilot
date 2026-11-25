// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.other.AbstractFastMinecoloniesEntity;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.entity.ai.workers.guard.EntityAICavalry;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityAICavalry.class)
public class CavalryRestMixin
{

    @Unique
    private static final int HOME_STRETCH = 24;

    @Redirect(method = "patrol()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/pathfinding/navigation/EntityNavigationUtils;walkToRandomPosAround(Lcom/minecolonies/api/entity/other/AbstractFastMinecoloniesEntity;Lnet/minecraft/core/BlockPos;ID)Z"),
      require = 0)
    private boolean colonyautopilot$rideHome(final AbstractFastMinecoloniesEntity rider, final BlockPos restSpot, final int range, final double pace)
    {
        if (BlockPosUtil.getDistance2D(rider.blockPosition(), restSpot) > HOME_STRETCH && AutopilotConfig.SPEC.isLoaded()
              && rider instanceof AbstractEntityCitizen citizen && citizen.getCitizenData() != null
              && AutopilotConfig.live(citizen.getCitizenData().getColony(), AutopilotConfig.CAVALRY_PATROLS))
        {
            return EntityNavigationUtils.walkToPos(rider, restSpot, range, true);
        }
        return EntityNavigationUtils.walkToRandomPosAround(rider, restSpot, range, pace);
    }
}
