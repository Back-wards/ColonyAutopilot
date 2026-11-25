// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.CitizenWall;
import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ClipContext.class)
public abstract class ClipContextMixin
{
    @ModifyArg(method = "getBlockShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/ClipContext$Block;get(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
      index = 3, require = 0)
    private CollisionContext colonyautopilot$noWallToARay(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context)
    {
        if (((CitizenWall) state).colonyautopilot$isCitizenWall() && context instanceof final EntityCollisionContext body)
        {

            final Entity who = body.getEntity();
            if (who instanceof AbstractCivilianEntity && who instanceof AbstractEntityCitizen)
            {
                return CollisionContext.empty();
            }
        }
        return context;
    }
}
