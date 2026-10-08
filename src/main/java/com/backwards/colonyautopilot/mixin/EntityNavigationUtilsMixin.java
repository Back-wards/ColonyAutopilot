// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.other.AbstractFastMinecoloniesEntity;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNavigationUtils.class)
public class EntityNavigationUtilsMixin
{
    @Inject(method = "walkToBuilding", at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$reachedAtAnyHeight(final AbstractFastMinecoloniesEntity entity, final IBuilding building,
      final CallbackInfoReturnable<Boolean> cir)
    {
        if (building == null || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.live(building.getColony(), AutopilotConfig.REACH_HUT_ANY_HEIGHT)
              || !(entity.getNavigation() instanceof MinecoloniesAdvancedPathNavigate nav))
        {
            return;
        }
        final boolean walked = nav.getPathResult() != null;
        if (walked && !nav.isDone())
        {
            return;
        }
        final BlockPos at = entity.blockPosition();
        if (building.isInBuilding(at)
              || (colonyautopilot$jobWalk(entity) && walked && Math.abs(at.getY() - building.getPosition().getY()) >= 2
                    && colonyautopilot$underOrOver(at, building, EntityNavigationUtils.BUILDING_REACH_DIST)))
        {
            nav.stop();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "walkToPos(Lcom/minecolonies/api/entity/other/AbstractFastMinecoloniesEntity;Lnet/minecraft/core/BlockPos;IZ)Z",
      at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$reachedHutBlockAtAnyHeight(final AbstractFastMinecoloniesEntity entity, final BlockPos pos,
      final int reach, final boolean safe, final CallbackInfoReturnable<Boolean> cir)
    {

        if (pos == null || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn() || !colonyautopilot$jobWalk(entity))
        {
            return;
        }

        final BlockPos at = entity.blockPosition();
        if (Math.abs(at.getY() - pos.getY()) < 2 || Math.abs(at.getX() - pos.getX()) > 32 || Math.abs(at.getZ() - pos.getZ()) > 32
              || !(entity.getNavigation() instanceof MinecoloniesAdvancedPathNavigate nav) || nav.getPathResult() == null || !nav.isDone())
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(entity.level(), pos);
        final IBuilding building = colony == null ? null : colony.getServerBuildingManager().getBuilding(pos);
        if (building != null && AutopilotConfig.get(colony, AutopilotConfig.REACH_HUT_ANY_HEIGHT) && colonyautopilot$underOrOver(at, building, reach))
        {
            nav.stop();
            cir.setReturnValue(true);
        }
    }

    private static boolean colonyautopilot$jobWalk(final AbstractFastMinecoloniesEntity entity)
    {
        return entity instanceof EntityCitizen citizen && citizen.getCitizenAI().getState() == CitizenAIState.WORKING;
    }

    private static boolean colonyautopilot$underOrOver(final BlockPos at, final IBuilding building, final int reach)
    {
        final BlockPos hut = building.getPosition();
        if (Math.abs(at.getY() - hut.getY()) > 24)
        {
            return false;
        }
        final long dx = at.getX() - hut.getX();
        final long dz = at.getZ() - hut.getZ();
        if (dx * dx + dz * dz <= (long) reach * reach)
        {
            return true;
        }
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        if (corners == null || corners.getA() == null || corners.getB() == null)
        {
            return false;
        }
        return at.getX() >= Math.min(corners.getA().getX(), corners.getB().getX())
                 && at.getX() <= Math.max(corners.getA().getX(), corners.getB().getX())
                 && at.getZ() >= Math.min(corners.getA().getZ(), corners.getB().getZ())
                 && at.getZ() <= Math.max(corners.getA().getZ(), corners.getB().getZ());
    }
}
