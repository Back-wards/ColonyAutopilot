// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.util.ShapeUtil;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.core.entity.pathfinding.MNode;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import com.minecolonies.core.entity.pathfinding.world.CachingBlockLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractPathJob.class)
public abstract class AbstractPathJobMixin
{
    @Shadow
    private PathingOptions pathingOptions;

    @Shadow
    protected CachingBlockLookup cachedBlockLookup;

    @ModifyReturnValue(method = "isPassable(Lnet/minecraft/world/level/block/state/BlockState;IIILcom/minecolonies/core/entity/pathfinding/MNode;Z)Z",
      at = @At("RETURN"), require = 0)
    private boolean colonyautopilot$dangerIsInTheWay(final boolean passable, final BlockState block, final int x, final int y, final int z,
      final MNode parent, final boolean head)
    {

        return passable && (block.isAir() || pathingOptions.canPassDanger() || !PathfindingUtils.isDangerous(block));
    }

    @ModifyReturnValue(method = "checkHeadBlock(Lcom/minecolonies/core/entity/pathfinding/MNode;III)Z", at = @At("RETURN"), require = 0)
    private boolean colonyautopilot$dangerOverhead(final boolean noHeadRoom, final MNode parent, final int x, final int y, final int z)
    {

        if (noHeadRoom || pathingOptions.canPassDanger())
        {
            return noHeadRoom;
        }
        final BlockState overhead = cachedBlockLookup.getBlockState(x, y + 1, z);
        return !overhead.isAir() && overhead.is(ModTags.dangerousBlocks);
    }

    @ModifyReturnValue(method = "handleTargetNotPassable(Lcom/minecolonies/core/entity/pathfinding/MNode;IIILnet/minecraft/world/level/block/state/BlockState;)I",
      at = @At("RETURN"), require = 0)
    private int colonyautopilot$dangerOverTheJump(final int groundY, final MNode parent, final int x, final int y, final int z, final BlockState target)
    {

        if (groundY == Integer.MIN_VALUE || parent == null || pathingOptions.canPassDanger())
        {
            return groundY;
        }

        final BlockState overTheJump = cachedBlockLookup.getBlockState(parent.x, parent.y + 2, parent.z);
        return !overTheJump.isAir() && overTheJump.is(ModTags.dangerousBlocks)
                 && y + ShapeUtil.max(target.getCollisionShape(cachedBlockLookup, new BlockPos(x, y, z)), Direction.Axis.Y)
                      + CitizenConstants.CITIZEN_HEIGHT > parent.y + 2
                 ? Integer.MIN_VALUE : groundY;
    }
}
