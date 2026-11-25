// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.plantation.IPlantationModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class VillageGrounds
{

    static boolean isEarth(final Block block)
    {
        return block.defaultBlockState().is(BlockTags.DIRT)
                 || block == Blocks.SAND || block == Blocks.RED_SAND
                 || SporeCompat.infestedEarth().contains(block);
    }

    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static SkirtResult smoothSkirt(final ServerLevel level, final SiteSelector.Footprint plot, final int plotGroundY,
      final List<SiteSelector.Footprint> boxes, final Set<Long> roadBand)
    {

        final List<SiteSelector.Footprint> obstacles = new ArrayList<>(boxes.size());
        for (final SiteSelector.Footprint box : boxes)
        {
            obstacles.add(new SiteSelector.Footprint(box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1));
        }

        final List<SiteSelector.Footprint> shields = new ArrayList<>(obstacles);
        shields.add(plot);

        final Set<Long> refusedTrunks = new HashSet<>();
        int cleared = 0;
        int graded = 0;
        int recapped = 0;
        int skirtRead = 0;
        int skirtCold = 0;
        int cold = 0;
        for (int ring = 1; ring <= RAMP_RINGS; ring++)
        {
            final int minX = plot.minX() - ring, maxX = plot.maxX() + ring;
            final int minZ = plot.minZ() - ring, maxZ = plot.maxZ() + ring;
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    if (x != minX && x != maxX && z != minZ && z != maxZ)
                    {
                        continue;
                    }
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                    {
                        if (ring <= SKIRT_RINGS)
                        {
                            skirtCold++;
                        }
                        else
                        {
                            cold++;
                        }
                        continue;
                    }
                    if (ring <= SKIRT_RINGS)
                    {
                        skirtRead++;
                    }

                    if (roadBand.contains(BlockPos.asLong(x, 0, z)))
                    {
                        continue;
                    }
                    if (ring > SKIRT_RINGS)
                    {

                        if (!SiteSelector.insideAny(obstacles, x, z) && !floatingAbove(level, x, z, plotGroundY) && fellTreeAt(level, x, z, shields, refusedTrunks))
                        {
                            cleared++;
                        }
                        continue;
                    }
                    final int[] done = smoothSkirtColumn(level, x, z, plotGroundY, ring, obstacles, shields, refusedTrunks);
                    cleared += done[0];
                    graded += done[1];
                    recapped += done[2];
                }
            }
        }
        final int[] ramped = rampDown(level, plot, plotGroundY, obstacles, roadBand);
        cold += ramped[2];
        if (skirtCold + cold > 0)
        {
            ColonyAutopilot.LOGGER.debug("the apron around the plot at {},{}..{},{} skipped {} column(s) of its skirt and {} beyond it in unloaded chunks",
              plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ(), skirtCold, cold);
        }
        return new SkirtResult(cleared, graded + ramped[0], recapped + ramped[1], skirtRead > 0);
    }

    static final int SKIRT_RINGS = 3;

    private static final int RAMP_RINGS = 8;

    private static final int APRON_REACH = 2 * RAMP_RINGS + 1;

    private static boolean floatingAbove(final ServerLevel level, final int x, final int z, final int plotGroundY)
    {
        final int surfaceY = SiteSelector.groundHeight(level, x, z) - 1;
        return surfaceY > plotGroundY + 12 && !SiteSelector.solidBeneath(level, x, z, surfaceY, new BlockPos.MutableBlockPos());
    }

    static boolean fellTreeAt(final ServerLevel level, final int x, final int z, final List<SiteSelector.Footprint> shields,
      final Set<Long> refusedTrunks)
    {
        if (refusedTrunks.contains(BlockPos.asLong(x, 0, z)))
        {
            return false;
        }

        final int ground = SiteSelector.groundHeight(level, x, z) - 1;
        for (int y = ground + 1; y <= ground + 12; y++)
        {
            final BlockPos log = new BlockPos(x, y, z);
            if (level.getBlockState(log).is(BlockTags.LOGS))
            {
                if (!rooted(level, log.below()))
                {
                    return false;
                }
                if (VillagePaths.fellTree(level, log, shields, true))
                {
                    return true;
                }
                VillagePaths.refuseTrunk(refusedTrunks, log);
                return false;
            }
        }
        return false;
    }

    private static boolean rooted(final ServerLevel level, final BlockPos under)
    {
        final BlockState state = level.getBlockState(under);

        return state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    private static int[] rampDown(final ServerLevel level, final SiteSelector.Footprint plot, final int grade,
      final List<SiteSelector.Footprint> obstacles, final Set<Long> roadBand)
    {
        int laid = 0;
        int recapped = 0;
        int cold = 0;
        for (int ring = 1; ring <= 2 * RAMP_RINGS; ring++)
        {
            final int minX = plot.minX() - ring, maxX = plot.maxX() + ring;
            final int minZ = plot.minZ() - ring, maxZ = plot.maxZ() + ring;
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    if (x != minX && x != maxX && z != minZ && z != maxZ)
                    {
                        continue;
                    }
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                    {
                        cold++;
                        continue;
                    }
                    if (roadBand.contains(BlockPos.asLong(x, 0, z)))
                    {
                        continue;
                    }
                    final int stepX = x < plot.minX() ? -1 : x > plot.maxX() ? 1 : 0;
                    final int stepZ = z < plot.minZ() ? -1 : z > plot.maxZ() ? 1 : 0;

                    final int target = Math.min(grade - 1, lipTop(level, x - stepX, z - stepZ, ring, grade, obstacles, roadBand) - 1);
                    final int surfaceY = SiteSelector.groundHeight(level, x, z) - 1;
                    if (surfaceY >= target || !rampable(level, x, surfaceY, z, obstacles))
                    {
                        continue;
                    }

                    boolean lands = false;
                    for (int further = ring + 1; further <= 2 * RAMP_RINGS + 1; further++)
                    {

                        final int line = target - (further - ring);
                        if (line < grade - RAMP_RINGS - 1)
                        {
                            break;
                        }
                        final int fx = x + stepX * (further - ring);
                        final int fz = z + stepZ * (further - ring);
                        if (!WorldUtil.isChunkLoaded(level, fx >> 4, fz >> 4))
                        {
                            cold++;
                            break;
                        }
                        if (SiteSelector.insideAny(obstacles, fx, fz) || roadBand.contains(BlockPos.asLong(fx, 0, fz)))
                        {
                            break;
                        }
                        final int fs = SiteSelector.groundHeight(level, fx, fz) - 1;
                        if (fs >= line)
                        {
                            lands = true;
                            break;
                        }
                        if (!rampable(level, fx, fs, fz, obstacles))
                        {
                            break;
                        }
                    }
                    if (!lands)
                    {
                        continue;
                    }
                    final boolean wasGrass = level.getBlockState(new BlockPos(x, surfaceY, z)).is(Blocks.GRASS_BLOCK);
                    int column = 0;
                    for (int y = surfaceY + 1; y <= target; y++)
                    {
                        final BlockPos pos = new BlockPos(x, y, z);
                        final BlockState state = level.getBlockState(pos);
                        if (!(state.isAir() || state.canBeReplaced()) || !state.getFluidState().isEmpty())
                        {
                            break;
                        }
                        WorldUtil.setBlockState(level, pos, Blocks.DIRT.defaultBlockState());
                        column++;
                    }
                    laid += column;
                    recapped += column > 0 ? recap(level, x, z, wasGrass) : 0;
                }
            }
        }
        return new int[] {laid, recapped, cold};
    }

    private static int lipTop(final ServerLevel level, final int x, final int z, final int ring, final int grade,
      final List<SiteSelector.Footprint> obstacles, final Set<Long> roadBand)
    {
        final int edgeLine = grade - ring + 1;
        if (ring == 1)
        {
            return grade;
        }
        if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4) || SiteSelector.insideAny(obstacles, x, z) || roadBand.contains(BlockPos.asLong(x, 0, z)))
        {
            return edgeLine;
        }
        final int top = SiteSelector.groundHeight(level, x, z) - 1;
        final BlockState surface = level.getBlockState(new BlockPos(x, top, z));
        return isEarth(surface.getBlock()) || surface.is(BlockTags.BASE_STONE_OVERWORLD) || surface.is(Blocks.GRAVEL) ? top : edgeLine;
    }

    private static boolean rampable(final ServerLevel level, final int x, final int surfaceY, final int z, final List<SiteSelector.Footprint> obstacles)
    {
        if (SiteSelector.insideAny(obstacles, x, z))
        {
            return false;
        }
        final BlockState surface = level.getBlockState(new BlockPos(x, surfaceY, z));
        if (!isEarth(surface.getBlock()) && !surface.is(BlockTags.BASE_STONE_OVERWORLD) && !surface.is(Blocks.GRAVEL))
        {
            return false;
        }
        for (int y = surfaceY + 1; y <= surfaceY + 12; y++)
        {
            if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.LOGS))
            {
                return false;
            }
        }
        return true;
    }

    record SkirtResult(int cleared, int graded, int recapped, boolean complete)
    {
        int total()
        {
            return cleared + graded + recapped;
        }
    }

    private static int recap(final ServerLevel level, final int x, final int z, final boolean wasGrass)
    {
        if (!wasGrass)
        {
            return 0;
        }
        final BlockPos top = new BlockPos(x, SiteSelector.groundHeight(level, x, z) - 1, z);
        if (level.getBlockState(top).is(Blocks.DIRT))
        {
            WorldUtil.setBlockState(level, top, Blocks.GRASS_BLOCK.defaultBlockState());
            return 1;
        }
        return 0;
    }

    private static int[] smoothSkirtColumn(final ServerLevel level, final int x, final int z, final int grade,
      final int ring, final List<SiteSelector.Footprint> obstacles, final List<SiteSelector.Footprint> shields, final Set<Long> refusedTrunks)
    {
        if (SiteSelector.insideAny(obstacles, x, z) || floatingAbove(level, x, z, grade))
        {
            return new int[] {0, 0, 0};
        }

        int cleared = 0;
        final int rawTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int y = rawTop; y > level.getMinBuildHeight() && cleared < 32; y--)
        {
            final BlockPos pos = new BlockPos(x, y, z);
            if (!SiteSelector.STANDING_CLUTTER.contains(level.getBlockState(pos).getBlock()))
            {
                break;
            }
            WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
            cleared++;
        }

        if (fellTreeAt(level, x, z, shields, refusedTrunks))
        {
            cleared++;
        }
        final int surfaceY = SiteSelector.groundHeight(level, x, z) - 1;
        final BlockState surface = level.getBlockState(new BlockPos(x, surfaceY, z));

        if (!isEarth(surface.getBlock()) && !surface.is(BlockTags.BASE_STONE_OVERWORLD) && !surface.is(Blocks.GRAVEL))
        {
            return new int[] {cleared, 0, 0};
        }
        final boolean wasGrass = surface.is(Blocks.GRASS_BLOCK);

        for (int y = surfaceY + 1; y <= surfaceY + 12; y++)
        {
            if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.LOGS))
            {
                return new int[] {cleared, 0, 0};
            }
        }
        int edits = 0;
        if (surfaceY > grade + ring)
        {

            final int floor = Math.max(grade + ring, waterBeside(level, x, z, shields));
            for (int y = surfaceY; y > floor && edits < MAX_EDIT_PER_COLUMN; y--)
            {
                final BlockPos pos = new BlockPos(x, y, z);
                if (!isEarth(level.getBlockState(pos).getBlock()))
                {
                    break;
                }
                WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                edits++;
            }
        }
        else if (surfaceY < grade - ring)
        {
            for (int y = surfaceY + 1; y <= grade - ring && edits < MAX_EDIT_PER_COLUMN; y++)
            {
                final BlockPos pos = new BlockPos(x, y, z);
                final BlockState state = level.getBlockState(pos);
                if (!(state.isAir() || state.canBeReplaced()) || !state.getFluidState().isEmpty())
                {
                    break;
                }
                WorldUtil.setBlockState(level, pos, Blocks.DIRT.defaultBlockState());
                edits++;
            }
        }
        return new int[] {cleared, edits, edits > 0 ? recap(level, x, z, wasGrass) : 0};
    }

    private static int waterBeside(final ServerLevel level, final int x, final int z, final List<SiteSelector.Footprint> shields)
    {
        int top = Integer.MIN_VALUE;
        for (final int[] step : NEIGHBORS)
        {
            final int nx = x + step[0];
            final int nz = z + step[1];
            if (!WorldUtil.isChunkLoaded(level, nx >> 4, nz >> 4) || SiteSelector.insideAny(shields, nx, nz))
            {
                continue;
            }
            final int neighborY = SiteSelector.groundHeight(level, nx, nz) - 1;
            if (!level.getBlockState(new BlockPos(nx, neighborY, nz)).getFluidState().isEmpty())
            {
                top = Math.max(top, neighborY);
            }
        }
        return top;
    }

    private static void notchBank(final ServerLevel level, final Sweep sweep, final int x, final int z, final List<SiteSelector.Footprint> shields)
    {

        if (sweep.roadBand.contains(BlockPos.asLong(x, 0, z)))
        {
            return;
        }
        final int top = SiteSelector.groundHeight(level, x, z) - 1;
        if (!isEarth(level.getBlockState(new BlockPos(x, top, z)).getBlock()) || floatingAbove(level, x, z, sweep.villageTop))
        {
            return;
        }

        int waterTop = Integer.MIN_VALUE;
        int[] toWater = null;
        for (final int[] step : NEIGHBORS)
        {
            final int nx = x + step[0];
            final int nz = z + step[1];
            if (!WorldUtil.isChunkLoaded(level, nx >> 4, nz >> 4))
            {
                return;
            }

            if (SiteSelector.insideAny(shields, nx, nz))
            {
                continue;
            }
            final int water = openWaterTopAt(level, nx, nz);
            if (water > waterTop)
            {
                waterTop = water;
                toWater = step;
            }
        }
        if (toWater == null || top - waterTop < 1 || top - waterTop > NOTCH_MAX_RISE)
        {
            return;
        }

        toWater = null;
        for (final int[] step : NEIGHBORS)
        {
            if (SiteSelector.insideAny(shields, x + step[0], z + step[1]) || openWaterTopAt(level, x + step[0], z + step[1]) != waterTop)
            {
                continue;
            }
            final Boolean wayOut = wayOutNear(level, x, z, x + step[0], z + step[1], waterTop);
            if (wayOut == null)
            {
                return;
            }
            if (!wayOut)
            {
                toWater = step;
                break;
            }
        }
        if (toWater == null)
        {
            return;
        }

        final List<int[]> stair = new ArrayList<>();
        for (int k = 0; ; k++)
        {
            if (k > NOTCH_MAX_RISE)
            {
                return;
            }
            final int cx = x - toWater[0] * k;
            final int cz = z - toWater[1] * k;
            final int line = waterTop + k;
            int columnTop = top;
            if (k > 0)
            {
                if (!WorldUtil.isChunkLoaded(level, cx >> 4, cz >> 4))
                {
                    return;
                }
                columnTop = SiteSelector.groundHeight(level, cx, cz) - 1;

                if (!groundVote(level.getBlockState(new BlockPos(cx, Math.min(columnTop, line), cz))))
                {
                    return;
                }
                if (columnTop <= line)
                {
                    break;
                }
                if (cx < sweep.minX || cx >= sweep.minX + sweep.width || cz < sweep.minZ || cz >= sweep.minZ + sweep.depth
                      || SiteSelector.insideAny(shields, cx, cz) || sweep.roadBand.contains(BlockPos.asLong(cx, 0, cz))
                      || ColonyGrounds.get(level).isCausewayColumn(sweep.colony.getID(), BlockPos.asLong(cx, 0, cz)))
                {
                    return;
                }
            }
            if (!cuttable(level, cx, cz, columnTop, line))
            {
                return;
            }
            stair.add(new int[] {cx, cz, columnTop, line});
        }
        int cut = 0;
        for (final int[] column : stair)
        {
            final boolean wasGrass = level.getBlockState(new BlockPos(column[0], column[2], column[1])).is(Blocks.GRASS_BLOCK);
            for (int y = column[2]; y > column[3]; y--)
            {
                WorldUtil.setBlockState(level, new BlockPos(column[0], y, column[1]), Blocks.AIR.defaultBlockState());
                cut++;
            }
            recap(level, column[0], column[1], wasGrass);
        }
        TickBudget.spend(cut);
        final String notch = new BlockPos(x, waterTop, z).toShortString();
        sweep.notches.add(notch);
        ColonyAutopilot.LOGGER.debug("[{}] cut a way out of the water at {} — its bank stood {} over it, with none within {} blocks ({} block(s) taken down)",
          sweep.colony.getName(), notch, top - waterTop, NOTCH_REACH, cut);
    }

    private static boolean cuttable(final ServerLevel level, final int x, final int z, final int top, final int line)
    {
        if (top - line > MAX_EDIT_PER_COLUMN)
        {
            return false;
        }
        for (int y = top + 1; y <= top + 12; y++)
        {
            final BlockPos over = new BlockPos(x, y, z);

            if (level.getBlockState(over).is(BlockTags.LOGS) || (y <= line + 2 && !WaterRescue.clear(level, over)))
            {
                return false;
            }
        }
        final BlockState floor = level.getBlockState(new BlockPos(x, line, z));
        if ((!isEarth(floor.getBlock()) && !floor.is(BlockTags.BASE_STONE_OVERWORLD) && !floor.is(Blocks.GRAVEL))
              || (floor.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(new BlockPos(x, line - 1, z)))))
        {
            return false;
        }
        for (int y = top; y > line; y--)
        {
            final BlockPos pos = new BlockPos(x, y, z);
            if (!isEarth(level.getBlockState(pos).getBlock()) || VillagePaths.holdsFluid(level, pos))
            {
                return false;
            }
        }
        return true;
    }

    private static Boolean wayOutNear(final ServerLevel level, final int bx, final int bz, final int wx, final int wz, final int waterTop)
    {
        final Set<Long> seen = new HashSet<>();
        final ArrayDeque<int[]> open = new ArrayDeque<>();
        seen.add(BlockPos.asLong(wx, 0, wz));
        open.add(new int[] {wx, wz});
        int read = 0;
        try
        {
            while (!open.isEmpty())
            {
                final int[] at = open.poll();
                for (final int[] step : NEIGHBORS)
                {
                    final int nx = at[0] + step[0];
                    final int nz = at[1] + step[1];
                    if (Math.abs(nx - bx) > NOTCH_REACH || Math.abs(nz - bz) > NOTCH_REACH || (nx == bx && nz == bz)
                          || !seen.add(BlockPos.asLong(nx, 0, nz)))
                    {
                        continue;
                    }
                    if (!WorldUtil.isChunkLoaded(level, nx >> 4, nz >> 4))
                    {
                        return null;
                    }
                    read += 2;
                    final BlockPos over = new BlockPos(nx, waterTop + 1, nz);
                    if (openWaterTopAt(level, nx, nz) == waterTop && level.getBlockState(over).getCollisionShape(level, over).isEmpty())
                    {
                        open.add(new int[] {nx, nz});
                        continue;
                    }
                    read += 3;
                    if (WaterRescue.climbableBank(level, new BlockPos(nx, waterTop, nz)))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        finally
        {
            TickBudget.spend(read);
        }
    }

    private static int openWaterTopAt(final ServerLevel level, final int x, final int z)
    {
        final int y = SiteSelector.groundHeight(level, x, z) - 1;
        return WaterRescue.openWater(level.getBlockState(new BlockPos(x, y, z))) ? y : Integer.MIN_VALUE;
    }

    private static boolean groundVote(final BlockState state)
    {
        return state.getFluidState().isEmpty()
                 && (isEarth(state.getBlock()) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.FARMLAND));
    }

    private static final int COLUMNS_PER_TICK = 32;

    private static final int MAX_EDIT_PER_COLUMN = 4;

    private static final int NOTCH_REACH = 4;

    private static final int NOTCH_MAX_RISE = 3;

    private static final int MARGIN = 8;

    private static final int LUMBERJACK_GROVE = 40;

    static final class Sweep
    {
        final IColony colony;
        private final int minX;
        private final int minZ;
        private final int width;
        private final int depth;
        private final List<SiteSelector.Footprint> keepOut;
        private final List<BlockPos> groves;

        private final int villageTop;
        private int cursor = 0;
        private int smoothed = 0;
        private int felled = 0;

        private final Set<Long> refusedTrunks = new HashSet<>();

        private final Set<Long> roadBand;

        private final List<String> notches = new ArrayList<>();

        private Sweep(final IColony colony, final int minX, final int minZ, final int width, final int depth,
          final List<SiteSelector.Footprint> keepOut, final List<BlockPos> groves, final int villageTop, final Set<Long> roadBand)
        {
            this.colony = colony;
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            this.keepOut = keepOut;
            this.groves = groves;
            this.villageTop = villageTop;
            this.roadBand = roadBand;
        }
    }

    private VillageGrounds()
    {
    }

    static Sweep plan(final IColony colony, final List<SiteSelector.Footprint> reserved, final Set<Long> roadBand)
    {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        final List<SiteSelector.Footprint> keepOut = new ArrayList<>(reserved);
        final List<BlockPos> groves = new ArrayList<>();
        int villageTop = colony.getCenter().getY();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            if (building.getBuildingLevel() <= 0)
            {

                if (corners.getB().getX() - corners.getA().getX() >= 2 || corners.getB().getZ() - corners.getA().getZ() >= 2)
                {
                    keepOut.add(new SiteSelector.Footprint(corners.getA().getX() - 1, corners.getA().getZ() - 1,
                      corners.getB().getX() + 1, corners.getB().getZ() + 1));
                }
                continue;
            }
            villageTop = Math.max(villageTop, building.getPosition().getY());
            minX = Math.min(minX, corners.getA().getX());
            minZ = Math.min(minZ, corners.getA().getZ());
            maxX = Math.max(maxX, corners.getB().getX());
            maxZ = Math.max(maxZ, corners.getB().getZ());

            keepOut.add(new SiteSelector.Footprint(corners.getA().getX() - 1, corners.getA().getZ() - 1,
              corners.getB().getX() + 1, corners.getB().getZ() + 1));
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutLumberjack)
            {
                groves.add(building.getPosition());
            }
        }
        if (minX == Integer.MAX_VALUE)
        {
            return null;
        }

        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext ->
          ext.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) || ext.hasModule(IPlantationModule.class)))
        {
            final BlockPos pos = extension.getPosition();
            final int shield = extension instanceof FarmField field
                                 ? Math.max(Math.max(field.getRadius(Direction.SOUTH), field.getRadius(Direction.WEST)),
                                     Math.max(field.getRadius(Direction.NORTH), field.getRadius(Direction.EAST))) + 1
                                 : 9;
            keepOut.add(new SiteSelector.Footprint(pos.getX() - shield, pos.getZ() - shield, pos.getX() + shield, pos.getZ() + shield));
        }
        minX -= MARGIN;
        minZ -= MARGIN;
        maxX += MARGIN;
        maxZ += MARGIN;

        if (colony.getWorld() instanceof ServerLevel level)
        {
            keepOut.addAll(SiteSelector.foreignClaims(level, colony, minX - VillagePaths.MASS_REACH, minZ - VillagePaths.MASS_REACH,
              maxX + VillagePaths.MASS_REACH, maxZ + VillagePaths.MASS_REACH));

            final IBuilding hall = colony.getServerBuildingManager().getTownHall();
            final ColonyGrounds grounds = ColonyGrounds.get(level);
            if (hall != null && hall.getBuildingLevel() > 0 && grounds.hasAnchorOffset(colony.getID(), hall.getPosition())
                  && !grounds.isApronDone(colony.getID(), hall.getPosition()))
            {
                final SiteSelector.Footprint box = SiteSelector.boxOf(hall);
                keepOut.add(new SiteSelector.Footprint(box.minX() - APRON_REACH, box.minZ() - APRON_REACH,
                  box.maxX() + APRON_REACH, box.maxZ() + APRON_REACH));
            }
        }

        final int width = maxX - minX + 1;
        final int depth = maxZ - minZ + 1;
        return new Sweep(colony, minX, minZ, width, depth, keepOut, groves, villageTop, roadBand);
    }

    static void reportNotches(final Sweep sweep)
    {
        if (!sweep.notches.isEmpty())
        {
            ColonyAutopilot.LOGGER.info("[{}] the village grounds sweep cut {} way(s) out of the water, where its banks stood over it with none within {} blocks: at {}",
              sweep.colony.getName(), sweep.notches.size(), NOTCH_REACH, String.join("; ", sweep.notches));
            sweep.notches.clear();
        }
    }

    static boolean drain(final Sweep sweep)
    {
        if (!(sweep.colony.getWorld() instanceof ServerLevel level))
        {
            return true;
        }

        final List<SiteSelector.Footprint> fellShields = new ArrayList<>(sweep.keepOut);
        fellShields.addAll(ProtectedZones.footprintsFor(sweep.colony));
        for (final IBuilding building : sweep.colony.getServerBuildingManager().getBuildings().values())
        {
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            if (corners.getB().getX() - corners.getA().getX() >= 2 || corners.getB().getZ() - corners.getA().getZ() >= 2)
            {
                fellShields.add(new SiteSelector.Footprint(corners.getA().getX() - 1, corners.getA().getZ() - 1,
                  corners.getB().getX() + 1, corners.getB().getZ() + 1));
            }
        }
        int refusedFells = 0;
        for (int visited = 0; visited < COLUMNS_PER_TICK && TickBudget.has(); visited++)
        {
            TickBudget.spend(1);
            if (sweep.cursor >= sweep.width * sweep.depth)
            {
                if (sweep.smoothed > 0 || sweep.felled > 0)
                {
                    ColonyAutopilot.LOGGER.debug("[{}] the village grounds were tended — {} blocks smoothed toward the local grade{}",
                      sweep.colony.getName(), sweep.smoothed, sweep.felled > 0 ? ", " + sweep.felled + " stray trees felled" : "");
                }
                reportNotches(sweep);
                return true;
            }
            final int x = sweep.minX + sweep.cursor % sweep.width;
            final int z = sweep.minZ + sweep.cursor / sweep.width;
            sweep.cursor++;

            if (SiteSelector.insideAny(fellShields, x, z) || !WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
            {
                continue;
            }
            boolean fellAllowed = true;
            for (final BlockPos grove : sweep.groves)
            {
                if (Math.abs(grove.getX() - x) <= LUMBERJACK_GROVE && Math.abs(grove.getZ() - z) <= LUMBERJACK_GROVE)
                {
                    fellAllowed = false;
                    break;
                }
            }
            final boolean causeway = ColonyGrounds.get(level).isCausewayColumn(sweep.colony.getID(), BlockPos.asLong(x, 0, z));
            final int result = smoothColumn(level, x, z, fellAllowed, fellShields, sweep.villageTop, causeway, sweep.refusedTrunks);
            if (result == -1)
            {
                sweep.felled++;
                return false;
            }
            if (result == -2)
            {

                if (++refusedFells >= 2)
                {
                    return false;
                }
                continue;
            }
            sweep.smoothed += result;

            if (!causeway)
            {
                notchBank(level, sweep, x, z, fellShields);
            }
        }
        return false;
    }

    private static int smoothColumn(final ServerLevel level, final int x, final int z, final boolean fellAllowed,
      final List<SiteSelector.Footprint> fellShields, final int villageTop, final boolean earthLocked, final Set<Long> refusedTrunks)
    {
        final int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;

        if (surfaceY > villageTop + 12 && !SiteSelector.solidBeneath(level, x, z, surfaceY, new BlockPos.MutableBlockPos()))
        {
            return 0;
        }
        final BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        final BlockState surface = level.getBlockState(surfacePos);

        if (surface.is(BlockTags.LOGS))
        {
            if (fellAllowed)
            {

                int rooted = surfaceY;
                while (rooted > level.getMinBuildHeight() && level.getBlockState(new BlockPos(x, rooted - 1, z)).is(BlockTags.LOGS))
                {
                    rooted--;
                }
                if (!rooted(level, new BlockPos(x, rooted - 1, z)))
                {
                    return 0;
                }

                if (refusedTrunks.contains(BlockPos.asLong(x, 0, z)))
                {
                    return 0;
                }
                final BlockPos base = new BlockPos(x, rooted, z);
                if (VillagePaths.fellTree(level, base, fellShields, false))
                {
                    return -1;
                }
                VillagePaths.refuseTrunk(refusedTrunks, base);
                return -2;
            }
            return 0;
        }
        if (SiteSelector.STANDING_CLUTTER.contains(surface.getBlock()))
        {

            int cleared = 0;
            for (int y = surfaceY; y > level.getMinBuildHeight() && cleared < 32; y--)
            {
                final BlockPos pos = new BlockPos(x, y, z);
                if (!SiteSelector.STANDING_CLUTTER.contains(level.getBlockState(pos).getBlock()))
                {
                    break;
                }
                WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                cleared++;
            }
            return cleared;
        }
        if (!isEarth(surface.getBlock()) || earthLocked)
        {

            return 0;
        }
        final boolean wasGrass = surface.is(Blocks.GRASS_BLOCK);

        int low = Integer.MAX_VALUE;
        int groundVotes = 0;

        int fluidTop = Integer.MIN_VALUE;
        for (final int[] step : NEIGHBORS)
        {
            final int nx = x + step[0];
            final int nz = z + step[1];
            if (!WorldUtil.isChunkLoaded(level, nx >> 4, nz >> 4))
            {
                return 0;
            }
            final int neighborY = SiteSelector.groundHeight(level, nx, nz) - 1;

            final BlockState neighborSurface = level.getBlockState(new BlockPos(nx, neighborY, nz));
            if (!neighborSurface.getFluidState().isEmpty())
            {
                fluidTop = Math.max(fluidTop, neighborY);
                continue;
            }
            if (!groundVote(neighborSurface))
            {
                continue;
            }
            groundVotes++;
            low = Math.min(low, neighborY);
        }
        if (groundVotes == 0)
        {
            return 0;
        }

        int edits = 0;
        if (surfaceY > low + 1)
        {
            for (int y = surfaceY; y > Math.max(low + 1, fluidTop) && edits < MAX_EDIT_PER_COLUMN; y--)
            {
                final BlockPos pos = new BlockPos(x, y, z);
                if (!isEarth(level.getBlockState(pos).getBlock()))
                {
                    break;
                }
                WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                edits++;
            }
        }
        else if (surfaceY < low - 1)
        {

            for (int y = surfaceY + 1; y <= low - 1 && edits < MAX_EDIT_PER_COLUMN; y++)
            {
                final BlockPos pos = new BlockPos(x, y, z);
                final BlockState state = level.getBlockState(pos);
                if (!(state.isAir() || state.canBeReplaced()) || !state.getFluidState().isEmpty())
                {
                    break;
                }
                WorldUtil.setBlockState(level, pos, Blocks.DIRT.defaultBlockState());
                edits++;
            }
        }
        if (edits > 0)
        {
            recap(level, x, z, wasGrass);
        }
        return edits;
    }
}
