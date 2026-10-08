// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

final class ForesterGrove
{

    private static final int GROVE_MIN_TREES = 4;

    private static final int SAPLING_SPACING = 6;

    private static final int GROVE_RADIUS = 16;

    private static final int GROVE_SAPLINGS = 12;

    private static final int TREE_LEAVES = 3;

    private ForesterGrove()
    {
    }

    static void ensure(final ServerLevel level, final IColony colony, final BlockPos anchor,
      final SiteSelector.Footprint plot, final List<SiteSelector.Footprint> obstacles)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            return;
        }
        final int[] stand = countStand(level, colony, anchor);
        if (stand[0] + stand[1] >= GROVE_MIN_TREES)
        {
            return;
        }
        int planted = 0;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = anchor.getX() - GROVE_RADIUS; x <= anchor.getX() + GROVE_RADIUS && planted < GROVE_SAPLINGS; x += SAPLING_SPACING)
        {
            for (int z = anchor.getZ() - GROVE_RADIUS; z <= anchor.getZ() + GROVE_RADIUS && planted < GROVE_SAPLINGS; z += SAPLING_SPACING)
            {
                if (plot.intersects(x - 2, z - 2, x + 2, z + 2) || !WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                boolean blocked = false;
                for (final SiteSelector.Footprint box : obstacles)
                {
                    if (box.intersects(x - 1, z - 1, x + 1, z + 1))
                    {
                        blocked = true;
                        break;
                    }
                }
                if (blocked)
                {
                    continue;
                }
                final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (ground > anchor.getY() + 12)
                {
                    continue;

                }

                if (!level.getBlockState(probe.set(x, ground, z)).is(BlockTags.DIRT)
                      || !level.getBlockState(probe.set(x, ground + 1, z)).isAir())
                {
                    continue;
                }
                WorldUtil.setBlockState(level, probe, Blocks.OAK_SAPLING.defaultBlockState());
                planted++;
            }
        }
        if (planted > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] planted a starter grove — {} oak saplings around the forester's hut ({} trees stood within reach)",
              colony.getName(), planted, stand[0]);
        }
    }

    static boolean waitingOnGrowth(final ServerLevel level, final IColony colony, final BlockPos hut)
    {
        final int[] stand = countStand(level, colony, hut);
        return stand[0] == 0 && stand[1] > 0;
    }

    private static int[] countStand(final ServerLevel level, final IColony colony, final BlockPos hut)
    {
        int trees = 0;
        int saplings = 0;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = hut.getX() - SiteSelector.TREE_REACH; x <= hut.getX() + SiteSelector.TREE_REACH; x += 2)
        {
            for (int z = hut.getZ() - SiteSelector.TREE_REACH; z <= hut.getZ() + SiteSelector.TREE_REACH; z += 2)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (level.getBlockState(probe.set(x, top, z)).is(BlockTags.LOGS))
                {
                    if (isTree(level, colony, x, top, z) && ++trees >= GROVE_MIN_TREES)
                    {

                        return new int[] {trees, saplings};
                    }
                }

                else if (level.getBlockState(probe.set(x, top + 1, z)).is(BlockTags.SAPLINGS))
                {
                    saplings++;
                }
            }
        }
        return new int[] {trees, saplings};
    }

    private static boolean isTree(final ServerLevel level, final IColony colony, final int x, final int top, final int z)
    {
        final BlockPos log = new BlockPos(x, top, z);
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.isInBuilding(log))
            {
                return false;
            }
        }
        int leaves = 0;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {

                if (!WorldUtil.isChunkLoaded(level, (x + dx) >> 4, (z + dz) >> 4))
                {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++)
                {
                    final BlockState state = level.getBlockState(probe.set(x + dx, top + dy, z + dz));
                    if (state.is(BlockTags.LEAVES) && !state.getOptionalValue(LeavesBlock.PERSISTENT).orElse(false) && ++leaves >= TREE_LEAVES)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
