// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

final class RoadRouter
{

    private static final int MARGIN = 48;

    static final int CARVE_BUDGET = 60_000;

    static final int SITE_BUDGET = 6_000;

    static final int SITE_POOL = 30_000;

    private static final int CLIFF = 2;

    static final int YARD_STEP = 1;

    private static final double STEP = 0.6;

    private static final double WATER = 1.5;

    private static final double OFF_ROAD = 0.2;

    private static final double OWN_SHOULDER = OFF_ROAD;

    private static final double DIAGONAL = 1.4142;

    private record Column(int surface, boolean water, boolean roadbed, boolean own)
    {
    }

    private static final Column UNSEEN = new Column(Integer.MIN_VALUE, false, false, false);

    private record Open(double f, double g, long key)
    {
    }

    static final class Survey
    {
        private final Map<Long, Column> columns = new HashMap<>();
        private final Map<Long, Boolean> claimed = new HashMap<>();

        private final Map<Long, Boolean> solid = new HashMap<>();
        private int budget;

        Set<Long> own;
        private Set<Long> ownColumns;

        Survey(final int budget)
        {
            this.budget = budget;
        }

        int budgetLeft()
        {
            return budget;
        }

        boolean ownColumn(final int x, final int z)
        {
            if (own == null)
            {
                return false;
            }
            if (ownColumns == null)
            {
                ownColumns = new HashSet<>();
                for (final long packed : own)
                {
                    ownColumns.add(BlockPos.asLong(BlockPos.getX(packed), 0, BlockPos.getZ(packed)));
                }
            }
            return ownColumns.contains(BlockPos.asLong(x, 0, z));
        }

        boolean centred(final int x, final int z)
        {
            int round = 0;
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if ((dx != 0 || dz != 0) && ownColumn(x + dx, z + dz))
                    {
                        round++;
                    }
                }
            }
            return round >= 6;
        }
    }

    private RoadRouter()
    {
    }

    static List<int[]> route(final ServerLevel level, final IColony colony, final BlockPos start, final BlockPos target, final int arrivalTop,
      final SiteSelector.Footprint targetBox, final int reach, final List<SiteSelector.Footprint> obstacles, final Map<Long, Integer> junctions,
      final Set<Long> fresh, final Survey survey, final int callBudget)
    {

        final List<SiteSelector.Footprint> walls = new ArrayList<>(obstacles);
        if (targetBox != null && !walls.contains(targetBox))
        {
            walls.add(targetBox);
        }
        final int minX = Math.min(start.getX(), target.getX()) - MARGIN, maxX = Math.max(start.getX(), target.getX()) + MARGIN;
        final int minZ = Math.min(start.getZ(), target.getZ()) - MARGIN, maxZ = Math.max(start.getZ(), target.getZ()) + MARGIN;

        final int skyCeiling = Math.max(start.getY(), target.getY()) + 8;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        final long startKey = key(start.getX(), start.getZ());
        final List<SiteSelector.Footprint> junctionCells = junctionCells(junctions);
        final Column first = column(level, colony, start.getX(), start.getZ(), skyCeiling, fresh, survey, probe);
        if (first == UNSEEN)
        {
            ColonyAutopilot.LOGGER.debug("router: nothing to route from {} — the start is unreadable", start.toShortString());
            return null;
        }
        if (arrived(start.getX(), start.getZ(), first.surface(), target, arrivalTop, targetBox, reach, junctions, startKey, startKey))
        {
            ColonyAutopilot.LOGGER.debug("router: nothing to route from {} — it has already arrived", start.toShortString());
            return Collections.emptyList();
        }

        final PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        final Map<Long, Double> best = new HashMap<>();
        final Map<Long, Long> from = new HashMap<>();
        best.put(startKey, 0.0D);
        open.add(new Open(estimate(start.getX(), start.getZ(), target, reach, targetBox, junctionCells), 0.0D, startKey));
        int expanded = 0;
        final int allowed = Math.min(callBudget, survey.budget);
        while (!open.isEmpty())
        {
            final Open node = open.poll();
            if (node.g() > best.getOrDefault(node.key(), Double.MAX_VALUE))
            {
                continue;
            }
            if (++expanded > allowed)
            {
                survey.budget -= expanded - 1;
                ColonyAutopilot.LOGGER.debug("router: no route from {} to {} within {} expansions", start.toShortString(), target.toShortString(), allowed);
                return null;
            }
            final int x = x(node.key()), z = z(node.key());
            final Column here = survey.columns.get(node.key());
            if (node.key() != startKey && arrived(x, z, here.surface(), target, arrivalTop, targetBox, reach, junctions, node.key(), startKey))
            {
                survey.budget -= expanded;
                final List<int[]> route = unwind(from, node.key(), startKey);
                if (ColonyAutopilot.LOGGER.isDebugEnabled())
                {

                    int centre = 0, shoulder = 0, other = 0, plain = 0;
                    for (final int[] column : route == null ? List.<int[]>of() : route)
                    {
                        final Column walked = survey.columns.get(key(column[0], column[1]));
                        if (walked != null && walked.own())
                        {
                            if (survey.centred(column[0], column[1]))
                            {
                                centre++;
                            }
                            else
                            {
                                shoulder++;
                            }
                        }
                        else if (walked != null && walked.roadbed())
                        {
                            other++;
                        }
                        else
                        {
                            plain++;
                        }
                    }
                    ColonyAutopilot.LOGGER.debug("router: {} to {} — {} after {} expansions (own centre {}, own shoulder {}, other road {}, plain {})",
                      start.toShortString(), target.toShortString(), route == null ? "a route longer than the carver lays" : route.size() + " columns",
                      expanded, centre, shoulder, other, plain);
                }
                return route;
            }
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0)
                    {
                        continue;
                    }
                    final int nx = x + dx, nz = z + dz;
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ)
                    {
                        continue;
                    }
                    final long next = key(nx, nz);
                    final Column there = column(level, colony, nx, nz, skyCeiling, fresh, survey, probe);
                    if (there == UNSEEN)
                    {
                        continue;
                    }
                    final int rise = Math.abs(there.surface() - here.surface());
                    if (rise >= CLIFF)
                    {
                        continue;
                    }

                    final boolean goal = arrived(nx, nz, there.surface(), target, arrivalTop, targetBox, reach, junctions, next, startKey);
                    if (!goal && insideWalls(walls, nx, nz))
                    {
                        continue;
                    }
                    double cost = (dx != 0 && dz != 0 ? DIAGONAL : 1.0D) + (rise == 1 ? STEP : 0.0D);
                    if (there.water())
                    {
                        cost += WATER;
                    }

                    if (there.own())
                    {
                        if (!survey.centred(nx, nz))
                        {
                            cost += OWN_SHOULDER;
                        }
                    }
                    else if (!there.roadbed())
                    {
                        cost += OFF_ROAD;
                    }
                    final double g = node.g() + cost;
                    if (g < best.getOrDefault(next, Double.MAX_VALUE))
                    {
                        best.put(next, g);
                        from.put(next, node.key());

                        open.add(new Open(goal ? g : g + estimate(nx, nz, target, reach, targetBox, junctionCells), g, next));
                    }
                }
            }
        }
        survey.budget -= expanded;
        ColonyAutopilot.LOGGER.debug("router: no route from {} to {} — every reachable column tried ({} expansions)", start.toShortString(), target.toShortString(), expanded);
        return null;
    }

    private static boolean arrived(final int x, final int z, final int surface, final BlockPos target, final int arrivalTop,
      final SiteSelector.Footprint targetBox, final int reach, final Map<Long, Integer> junctions, final long key, final long startKey)
    {
        if (Math.abs(surface - arrivalTop) <= YARD_STEP)
        {
            if (Math.abs(target.getX() - x) <= reach && Math.abs(target.getZ() - z) <= reach)
            {
                return true;
            }
            if (targetBox != null && targetBox.intersects(x - 1, z - 1, x + 1, z + 1))
            {
                return true;
            }
        }
        if (key == startKey)
        {
            return false;
        }
        final Integer roadY = junctions.get(BlockPos.asLong(x, 0, z));
        return roadY != null && Math.abs(roadY - surface) <= 1;
    }

    private static boolean insideWalls(final List<SiteSelector.Footprint> walls, final int x, final int z)
    {
        for (final SiteSelector.Footprint box : walls)
        {
            if (box.intersects(x - 1, z - 1, x + 1, z + 1))
            {
                return true;
            }
        }
        return false;
    }

    private static double estimate(final int x, final int z, final BlockPos target, final int reach,
      final SiteSelector.Footprint targetBox, final List<SiteSelector.Footprint> junctionCells)
    {
        double h = Math.max(0, Math.max(Math.abs(target.getX() - x), Math.abs(target.getZ() - z)) - reach);
        if (targetBox != null)
        {
            h = Math.min(h, Math.max(0, toRect(x, z, targetBox) - 1));
        }
        for (final SiteSelector.Footprint cell : junctionCells)
        {
            h = Math.min(h, toRect(x, z, cell));
        }
        return h;
    }

    private static int toRect(final int x, final int z, final SiteSelector.Footprint box)
    {
        final int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
        final int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
        return Math.max(dx, dz);
    }

    private static Column column(final ServerLevel level, final IColony colony, final int x, final int z, final int skyCeiling,
      final Set<Long> fresh, final Survey survey, final BlockPos.MutableBlockPos probe)
    {
        final long key = key(x, z);
        Column read = survey.columns.get(key);
        if (read == null)
        {
            read = read(level, colony, x, z, fresh, survey, probe);
            survey.columns.put(key, read);
        }
        final Column column = read;

        if (column != UNSEEN && !column.water() && column.surface() > skyCeiling
              && !survey.solid.computeIfAbsent(key, k -> SiteSelector.solidBeneath(level, x, z, column.surface(), probe)))
        {
            return UNSEEN;
        }
        return column;
    }

    private static Column read(final ServerLevel level, final IColony colony, final int x, final int z,
      final Set<Long> fresh, final Survey survey, final BlockPos.MutableBlockPos probe)
    {
        if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
        {
            return UNSEEN;
        }
        final long chunk = ChunkPos.asLong(x >> 4, z >> 4);
        Boolean ours = survey.claimed.get(chunk);
        if (ours == null)
        {
            ours = IColonyManager.getInstance().getColonyByPosFromWorld(level, probe.set(x, 64, z)) == colony;
            survey.claimed.put(chunk, ours);
        }
        if (!ours)
        {
            return UNSEEN;
        }
        final int top = groundUnderDecks(level, x, z, probe, fresh);
        final BlockState crown = level.getBlockState(probe.set(x, top, z));
        final FluidState fluid = crown.getFluidState();

        if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER))
        {
            return UNSEEN;
        }
        if (fluid.isSource())
        {
            return new Column(top + 1, true, false, false);
        }
        final boolean roadbed = crown.is(Blocks.DIRT_PATH);
        return new Column(top, false, roadbed, survey.ownColumn(x, z));
    }

    static int groundUnderDecks(final ServerLevel level, final int x, final int z, final BlockPos.MutableBlockPos probe, final Set<Long> fresh)
    {
        int top = VillagePaths.surfaceY(level, x, z);
        if (fresh != null)
        {
            for (int y = top; y >= top - 8 && y > level.getMinBuildHeight(); y--)
            {
                if (fresh.contains(BlockPos.asLong(x, y, z)))
                {
                    return y;
                }
            }
        }
        BlockState crown = level.getBlockState(probe.set(x, top, z));
        while ((crown.is(Blocks.DIRT_PATH) || crown.is(Blocks.GLOWSTONE)) && top > level.getMinBuildHeight() + 1
                 && (fresh == null || !fresh.contains(BlockPos.asLong(x, top, z))))
        {
            final BlockState under = level.getBlockState(probe.set(x, top - 1, z));
            if (!under.getFluidState().isEmpty() || !(under.isAir() || under.canBeReplaced()))
            {
                break;
            }
            top = SiteSelector.groundHeightBelow(level, x, z, top - 1) - 1;
            crown = level.getBlockState(probe.set(x, top, z));
        }
        return top;
    }

    private static List<int[]> unwind(final Map<Long, Long> from, final long goal, final long startKey)
    {
        final List<int[]> route = new ArrayList<>();
        long at = goal;
        while (true)
        {
            route.add(new int[] {x(at), z(at)});
            if (at == startKey)
            {
                break;
            }
            at = from.get(at);
        }
        Collections.reverse(route);
        return route.size() - 1 > VillagePaths.MAX_LENGTH ? null : route;
    }

    private static List<SiteSelector.Footprint> junctionCells(final Map<Long, Integer> junctions)
    {
        final Map<Long, int[]> cells = new HashMap<>();
        for (final long packed : junctions.keySet())
        {
            final int jx = BlockPos.getX(packed), jz = BlockPos.getZ(packed);
            final int[] bounds = cells.computeIfAbsent(ChunkPos.asLong(jx >> 4, jz >> 4), k -> new int[] {jx, jz, jx, jz});
            bounds[0] = Math.min(bounds[0], jx);
            bounds[1] = Math.min(bounds[1], jz);
            bounds[2] = Math.max(bounds[2], jx);
            bounds[3] = Math.max(bounds[3], jz);
        }
        final List<SiteSelector.Footprint> boxes = new ArrayList<>(cells.size());
        for (final int[] b : cells.values())
        {
            boxes.add(new SiteSelector.Footprint(b[0], b[1], b[2], b[3]));
        }
        return boxes;
    }

    static final int STAIR_FILL = 5;

    static final int STAIR_CUT = 5;

    private static final double EARTH = 1.0;

    private static final double TURN = 0.3;

    private static final int[][] WAYS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    private static final int NO_WAY = 8;

    static List<int[]> routeStair(final ServerLevel level, final IColony colony, final BlockPos start, final BlockPos target, final int arrivalTop,
      final SiteSelector.Footprint targetBox, final int reach, final List<SiteSelector.Footprint> obstacles, final Map<Long, Integer> junctions,
      final Set<Long> fresh, final Survey survey, final int callBudget)
    {
        final List<SiteSelector.Footprint> walls = new ArrayList<>(obstacles);
        if (targetBox != null && !walls.contains(targetBox))
        {
            walls.add(targetBox);
        }
        final int minX = Math.min(start.getX(), target.getX()) - MARGIN, maxX = Math.max(start.getX(), target.getX()) + MARGIN;
        final int minZ = Math.min(start.getZ(), target.getZ()) - MARGIN, maxZ = Math.max(start.getZ(), target.getZ()) + MARGIN;
        final int skyCeiling = Math.max(start.getY(), target.getY()) + 8;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        final long startKey = key(start.getX(), start.getZ());
        final List<SiteSelector.Footprint> junctionCells = junctionCells(junctions);
        final Column first = column(level, colony, start.getX(), start.getZ(), skyCeiling, fresh, survey, probe);
        if (first == UNSEEN)
        {
            return null;
        }
        if (arrived(start.getX(), start.getZ(), first.surface(), target, arrivalTop, targetBox, reach, junctions, startKey, startKey))
        {
            return Collections.emptyList();
        }

        final PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        final Map<Long, Double> best = new HashMap<>();
        final Map<Long, Long> from = new HashMap<>();
        final long startState = stairKey(start.getX() - minX, start.getZ() - minZ, first.surface());
        best.put(startState, 0.0D);
        open.add(new Open(estimate(start.getX(), start.getZ(), target, reach, targetBox, junctionCells), 0.0D, startState));
        int expanded = 0;
        final int allowed = Math.min(callBudget, survey.budget);
        while (!open.isEmpty())
        {
            final Open node = open.poll();
            if (node.g() > best.getOrDefault(node.key(), Double.MAX_VALUE))
            {
                continue;
            }
            if (++expanded > allowed)
            {
                survey.budget -= expanded - 1;
                ColonyAutopilot.LOGGER.debug("router: no stair from {} to {} within {} expansions", start.toShortString(), target.toShortString(), allowed);
                return null;
            }
            final int x = minX + stairX(node.key()), z = minZ + stairZ(node.key()), tread = stairTread(node.key());

            final Long parent = from.get(node.key());
            final int way = parent == null ? NO_WAY : wayBetween(parent, node.key());
            if (node.key() != startState && arrived(x, z, tread, target, arrivalTop, targetBox, reach, junctions, key(x, z), startKey))
            {
                survey.budget -= expanded;
                final List<int[]> stair = unwindStair(from, node.key(), startState, minX, minZ);
                ColonyAutopilot.LOGGER.debug("router: stair {} to {} — {} after {} expansions", start.toShortString(), target.toShortString(),
                  stair == null ? "refused (too long, or laid over itself)" : stair.size() + " columns", expanded);
                return stair;
            }
            for (int d = 0; d < 8; d++)
            {
                if (way != NO_WAY && d == (way + 4) % 8)
                {
                    continue;
                }
                final int nx = x + WAYS[d][0], nz = z + WAYS[d][1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ)
                {
                    continue;
                }
                final Column there = column(level, colony, nx, nz, skyCeiling, fresh, survey, probe);
                if (there == UNSEEN)
                {
                    continue;
                }

                final boolean walled = insideWalls(walls, nx, nz);
                final double bound = estimate(nx, nz, target, reach, targetBox, junctionCells);
                for (int dt = -1; dt <= 1; dt++)
                {
                    final int nt = tread + dt;
                    final int earth;
                    if (there.water() || (there.roadbed() && !there.own()))
                    {

                        if (nt != there.surface())
                        {
                            continue;
                        }
                        earth = 0;
                    }
                    else
                    {
                        earth = nt - there.surface();
                        if (earth > STAIR_FILL || -earth > STAIR_CUT)
                        {
                            continue;
                        }
                    }
                    final boolean goal = arrived(nx, nz, nt, target, arrivalTop, targetBox, reach, junctions, key(nx, nz), startKey);
                    if (!goal && walled)
                    {
                        continue;
                    }
                    double cost = (WAYS[d][0] != 0 && WAYS[d][1] != 0 ? DIAGONAL : 1.0D) + (dt != 0 ? STEP : 0.0D) + EARTH * Math.abs(earth)
                                    + (way != NO_WAY && d != way ? TURN : 0.0D);
                    if (there.water())
                    {
                        cost += WATER;
                    }
                    if (!there.roadbed())
                    {
                        cost += OFF_ROAD;
                    }
                    final double g = node.g() + cost;
                    final long next = stairKey(nx - minX, nz - minZ, nt);
                    if (g < best.getOrDefault(next, Double.MAX_VALUE))
                    {
                        best.put(next, g);
                        from.put(next, node.key());
                        open.add(new Open(goal ? g : g + bound, g, next));
                    }
                }
            }
        }
        survey.budget -= expanded;
        ColonyAutopilot.LOGGER.debug("router: no stair from {} to {} — every reachable column and height tried ({} expansions)",
          start.toShortString(), target.toShortString(), expanded);
        return null;
    }

    private static List<int[]> unwindStair(final Map<Long, Long> from, final long goal, final long startState, final int minX, final int minZ)
    {
        final List<int[]> stair = new ArrayList<>();
        long at = goal;
        while (true)
        {
            stair.add(new int[] {minX + stairX(at), minZ + stairZ(at), stairTread(at)});
            if (at == startState)
            {
                break;
            }
            at = from.get(at);
        }
        Collections.reverse(stair);
        if (stair.size() - 1 > VillagePaths.MAX_LENGTH)
        {
            return null;
        }
        final int last = stair.size() - 1;
        final long[][] lanes = new long[stair.size()][3];
        for (int i = 0; i <= last; i++)
        {

            final int[] back = stair.get(Math.max(0, i - 1)), ahead = stair.get(Math.min(last, i + 1));
            final boolean alongX = Math.abs(ahead[0] - back[0]) > Math.abs(ahead[1] - back[1]);
            for (int w = -1; w <= 1; w++)
            {
                lanes[i][w + 1] = BlockPos.asLong(stair.get(i)[0] + (alongX ? 0 : w), 0, stair.get(i)[1] + (alongX ? w : 0));
            }
        }
        for (int i = 0; i <= last; i++)
        {
            for (int j = i + 3; j <= last; j++)
            {
                if (Math.abs(stair.get(i)[2] - stair.get(j)[2]) < 2)
                {
                    continue;
                }
                for (final long mine : lanes[i])
                {
                    for (final long theirs : lanes[j])
                    {
                        if (mine == theirs)
                        {
                            return null;
                        }
                    }
                }
            }
        }
        return stair;
    }

    private static int wayBetween(final long fromKey, final long toKey)
    {
        final int dx = stairX(toKey) - stairX(fromKey), dz = stairZ(toKey) - stairZ(fromKey);
        for (int d = 0; d < 8; d++)
        {
            if (WAYS[d][0] == dx && WAYS[d][1] == dz)
            {
                return d;
            }
        }
        return NO_WAY;
    }

    private static long stairKey(final int rx, final int rz, final int tread)
    {
        return ((long) rx << 33) | ((long) rz << 17) | ((long) (tread + 4096) << 4);
    }

    private static int stairX(final long key)
    {
        return (int) ((key >>> 33) & 0xFFFF);
    }

    private static int stairZ(final long key)
    {
        return (int) ((key >>> 17) & 0xFFFF);
    }

    private static int stairTread(final long key)
    {
        return (int) ((key >>> 4) & 0x1FFF) - 4096;
    }

    private static long key(final int x, final int z)
    {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static int x(final long key)
    {
        return (int) (key >> 32);
    }

    private static int z(final long key)
    {
        return (int) key;
    }
}
