// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForesterGrove
{

    private static final int GROVE_MIN_TREES = 4;

    private static final int SAPLING_SPACING = 6;

    private static final int GROVE_RADIUS = 16;

    private static final int GROVE_SAPLINGS = 12;

    private static final int TREE_LEAVES = 3;

    private static final Set<GlobalPos> NO_GROUND_WARNED = new HashSet<>();

    private ForesterGrove()
    {
    }

    static void clearGroundWarnings()
    {
        NO_GROUND_WARNED.clear();
    }

    static void ensure(final ServerLevel level, final IColony colony, final BlockPos anchor,
      final SiteSelector.Footprint plot, final List<SiteSelector.Footprint> obstacles)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            return;
        }
        final int[] stand = countStand(level, colony, anchor, 2);
        if (stand[0] + stand[1] >= GROVE_MIN_TREES)
        {
            return;
        }

        final List<SiteSelector.Footprint> guarded = new ArrayList<>(obstacles);
        guarded.addAll(SiteSelector.foreignClaims(level, colony, anchor.getX() - GROVE_RADIUS - 1, anchor.getZ() - GROVE_RADIUS - 1,
          anchor.getX() + GROVE_RADIUS + 1, anchor.getZ() + GROVE_RADIUS + 1));
        int planted = 0;
        boolean unread = false;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = anchor.getX() - GROVE_RADIUS; x <= anchor.getX() + GROVE_RADIUS && planted < GROVE_SAPLINGS; x += SAPLING_SPACING)
        {
            for (int z = anchor.getZ() - GROVE_RADIUS; z <= anchor.getZ() + GROVE_RADIUS && planted < GROVE_SAPLINGS; z += SAPLING_SPACING)
            {
                if (plot.intersects(x - 2, z - 2, x + 2, z + 2))
                {
                    continue;
                }
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    unread = true;
                    continue;
                }
                boolean blocked = false;
                for (final SiteSelector.Footprint box : guarded)
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

                final BlockState soil = level.getBlockState(probe.set(x, ground, z));
                final boolean barren = soil.is(Blocks.SAND) || soil.is(Blocks.RED_SAND) || soil.is(BlockTags.BADLANDS_TERRACOTTA) || soil.is(Blocks.SNOW_BLOCK);

                if ((!barren && !soil.is(BlockTags.DIRT)) || !level.getBlockState(probe.set(x, ground + 1, z)).canBeReplaced())
                {
                    continue;
                }
                if (barren)
                {
                    WorldUtil.setBlockState(level, probe.set(x, ground, z), Blocks.DIRT.defaultBlockState());
                }
                WorldUtil.setBlockState(level, probe.set(x, ground + 1, z), Blocks.OAK_SAPLING.defaultBlockState());
                planted++;
            }
        }

        final GlobalPos hutKey = GlobalPos.of(level.dimension(), anchor.atY(0));
        if (planted > 0)
        {
            NO_GROUND_WARNED.remove(hutKey);
            ColonyAutopilot.LOGGER.info("[{}] planted a starter grove — {} oak saplings around the forester's hut ({} trees stood within reach)",
              colony.getName(), planted, stand[0]);
        }

        else if (stand[0] + stand[1] == 0 && !unread && !NO_GROUND_WARNED.contains(hutKey))
        {

            final int[] every = countStand(level, colony, anchor, 1);
            if (every[0] + every[1] + every[2] == 0 && NO_GROUND_WARNED.add(hutKey))
            {
                ColonyAutopilot.LOGGER.warn("[{}] no ground for a starter grove round the forester's hut at {} — no tree stands within {} blocks of it and no spot within {} could take a sapling (rock, gravel or water, or all of it built over): the forester has nothing to fell until trees are planted near him by hand (needs eyes)",
                  colony.getName(), anchor.toShortString(), SiteSelector.TREE_REACH, GROVE_RADIUS);
            }
        }
    }

    static boolean waitingOnGrowth(final ServerLevel level, final IColony colony, final BlockPos hut)
    {
        final int[] stand = countStand(level, colony, hut, 2);
        return stand[0] == 0 && stand[1] > 0;
    }

    private static int[] countStand(final ServerLevel level, final IColony colony, final BlockPos hut, final int step)
    {
        int trees = 0;
        int saplings = 0;
        int unread = 0;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = hut.getX() - SiteSelector.TREE_REACH; x <= hut.getX() + SiteSelector.TREE_REACH; x += step)
        {
            for (int z = hut.getZ() - SiteSelector.TREE_REACH; z <= hut.getZ() + SiteSelector.TREE_REACH; z += step)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    unread++;
                    continue;
                }
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (level.getBlockState(probe.set(x, top, z)).is(BlockTags.LOGS))
                {
                    if (isTree(level, colony, x, top, z) && ++trees >= GROVE_MIN_TREES)
                    {

                        return new int[] {trees, saplings, unread};
                    }
                }

                else if (level.getBlockState(probe.set(x, top + 1, z)).is(BlockTags.SAPLINGS))
                {
                    saplings++;
                }
            }
        }
        return new int[] {trees, saplings, unread};
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
