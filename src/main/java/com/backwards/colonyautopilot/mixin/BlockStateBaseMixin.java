// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.CitizenWall;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements CitizenWall
{
    @Unique
    private boolean colonyautopilot$citizenWall;

    @Override
    public void colonyautopilot$setCitizenWall(final boolean wall)
    {
        colonyautopilot$citizenWall = wall;
    }

    @Override
    public boolean colonyautopilot$isCitizenWall()
    {
        return colonyautopilot$citizenWall;
    }

    @ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
      at = @At("RETURN"), require = 0)
    private VoxelShape colonyautopilot$wallForCitizens(final VoxelShape shape, final BlockGetter level, final BlockPos pos, final CollisionContext context)
    {

        if (colonyautopilot$citizenWall && context instanceof final EntityCollisionContext body)
        {

            final Entity who = body.getEntity();

            if (who instanceof AbstractCivilianEntity && who instanceof AbstractEntityCitizen && !colonyautopilot$inTrap(level, who))
            {
                return Shapes.block();
            }
        }
        return shape;
    }

    @Unique
    private static boolean colonyautopilot$inTrap(final BlockGetter level, final Entity who)
    {
        return level.getBlockState(who.blockPosition()).is(ModTags.dangerousBlocks)
                 || level.getBlockState(BlockPos.containing(who.getX(), who.getEyeY(), who.getZ())).is(ModTags.dangerousBlocks);
    }
}
