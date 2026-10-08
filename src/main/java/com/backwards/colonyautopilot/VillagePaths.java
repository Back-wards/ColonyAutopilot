// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class VillagePaths
{

    private static final int WIDTH = 3;

    static final int MAX_LENGTH = 200;

    static final int ARRIVAL = 8;

    private static final int LAMP_OFFSET = 2;

    static final int LAMP_REACH = 13;

    static final int LAMP_SAME_LEVEL = 4;

    private static final int CLEAR_HEIGHT = 5;

    private static final int GRADE_REACH = 3;

    private VillagePaths()
    {
    }

    static List<BlockPos> connect(final ServerLevel level, final BlockPos start, final boolean fromDoor, final BlockPos center,
      final List<SiteSelector.Footprint> obstacles, final List<SiteSelector.Footprint> shields, final Set<Long> liveRoads,
      final Map<Long, Integer> junctions, final Set<Long> previous, final List<int[]> route, final Set<Long> fresh, final Set<Long> blind,
      final int zone, final int lampSpacing)
    {
        final List<BlockPos> painted = new ArrayList<>();
        final int dx = center.getX() - start.getX();
        final int dz = center.getZ() - start.getZ();
        final List<int[]> columns = route != null ? route : straightLine(start, center);
        if (columns.size() < 2)
        {
            return painted;
        }
        final int steps = columns.size() - 1;

        final Direction[] sides = new Direction[steps + 1];
        final Direction lineSide = sideOf(dx, dz);
        for (int i = 0; i <= steps; i++)
        {
            final int[] back = columns.get(Math.max(0, i - 1));
            final int[] ahead = columns.get(Math.min(steps, i + 1));
            sides[i] = route != null ? sideOf(ahead[0] - back[0], ahead[1] - back[1]) : lineSide;
        }
        final int half = WIDTH / 2;

        final Set<Long> foreign = foreignChunks(level, start, fromDoor, center, columns, sides, half);

        final int[] surface = new int[steps + 1];

        final int skyCeiling = Math.max(start.getY(), center.getY()) + 8;
        final BlockPos.MutableBlockPos skyProbe = new BlockPos.MutableBlockPos();

        final boolean[] skyBlind = new boolean[steps + 1];

        final boolean[] roadbed = new boolean[steps + 1];

        final boolean[] cutThrough = new boolean[steps + 1];
        for (int i = 0; i <= steps; i++)
        {
            final int x = columns.get(i)[0];
            final int z = columns.get(i)[1];

            if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
            {
                surface[i] = Integer.MIN_VALUE;
                continue;
            }
            if (foreign.contains(ChunkPos.asLong(x >> 4, z >> 4)))
            {
                surface[i] = Integer.MIN_VALUE;
                skyBlind[i] = true;
                continue;
            }
            boolean roofed = false;
            for (final SiteSelector.Footprint box : obstacles)
            {
                if (box.intersects(x, z, x, z))
                {
                    roofed = true;
                    break;
                }
            }
            if (roofed)
            {
                surface[i] = Integer.MIN_VALUE;
                continue;
            }

            final int top = RoadRouter.groundUnderDecks(level, x, z, skyProbe, fresh);
            final BlockState crown = level.getBlockState(new BlockPos(x, top, z));

            final FluidState crownFluid = crown.getFluidState();
            if (!crownFluid.isEmpty() && !crownFluid.is(FluidTags.WATER))
            {
                surface[i] = Integer.MIN_VALUE;
                skyBlind[i] = true;
                continue;
            }
            if (crownFluid.isSource())
            {
                surface[i] = top + 1;
                continue;
            }
            if (top > skyCeiling && !SiteSelector.solidBeneath(level, x, z, top, skyProbe))
            {
                surface[i] = Integer.MIN_VALUE;
                skyBlind[i] = true;
                continue;
            }
            surface[i] = top;

            roadbed[i] = crown.is(Blocks.DIRT_PATH) || crown.is(Blocks.GLOWSTONE);
        }

        int previousY = Integer.MIN_VALUE;
        for (final int measured : surface)
        {
            if (measured != Integer.MIN_VALUE)
            {
                previousY = measured;
                break;
            }
        }
        if (previousY == Integer.MIN_VALUE)
        {

            if (blind != null)
            {
                for (int i = 0; i <= steps; i++)
                {
                    if (skyBlind[i] || !WorldUtil.isChunkLoaded(level, columns.get(i)[0] >> 4, columns.get(i)[1] >> 4))
                    {
                        markBlind(blind, columns.get(i), sides[i], half);
                    }
                }
            }
            ColonyAutopilot.LOGGER.debug("carve from {} toward {}: every column lies in a building's grounds, protected ground, an unloaded chunk, "
                                           + "a floating build's shadow, lava or another colony's claim — nothing laid",
              start.toShortString(), center.toShortString());
            return painted;
        }
        if (fromDoor)
        {

            previousY = start.getY() - 1;

            final boolean alongX = Math.abs(dx) > Math.abs(dz);
            final int yardX = start.getX() - (alongX ? Integer.signum(dx) : 0);
            final int yardZ = start.getZ() - (alongX ? 0 : Integer.signum(dz));
            if (WorldUtil.isChunkLoaded(level, yardX >> 4, yardZ >> 4))
            {
                final int yard = surfaceY(level, yardX, yardZ);
                if (Math.abs(yard - previousY) <= 1)
                {
                    previousY = yard;
                }
            }
        }

        final int[] planned = new int[steps + 1];
        int end = steps;
        String stop = "the end";
        for (int i = 0; i <= steps; i++)
        {
            final int x = columns.get(i)[0];
            final int z = columns.get(i)[1];

            if (route == null && zone > 0 && i > zone && Math.abs(center.getX() - x) <= zone && Math.abs(center.getZ() - z) <= zone)
            {
                end = i - 1;
                stop = "the target's zone";
                break;
            }

            if (route == null && i > 0 && entersBox(obstacles, x, z, sides[i], half))
            {
                end = i - 1;
                stop = "a building's grounds";
                break;
            }

            final Integer roadY = junctions.get(BlockPos.asLong(x, 0, z));
            if (i > 0 && roadY != null && Math.abs(roadY - previousY) <= 1)
            {
                end = i - 1;
                stop = "a junction";
                break;
            }

            final boolean crossing = i > 0 && roadbed[i] && liveRoads.contains(BlockPos.asLong(x, 0, z));
            final boolean climbing = crossing && fromDoor && route == null
                                       && surface[i] - previousY >= 2 && surface[i] - previousY <= CUT_THROUGH + 1;
            if (crossing && !climbing && Math.abs(surface[i] - previousY) >= 2)
            {
                end = i - 1;
                stop = "another road " + Math.abs(surface[i] - previousY) + " off the grade";
                break;
            }

            if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4) || skyBlind[i])
            {
                planned[i] = Integer.MIN_VALUE;
                continue;
            }

            if (columns.get(i).length > 2)
            {
                planned[i] = columns.get(i)[2];
                previousY = planned[i];
                continue;
            }

            final int reach = Math.min(GRADE_REACH, Math.min(i, steps - i));
            final int[] window = new int[2 * reach + 1];
            int measured = 0;
            for (int j = i - reach; j <= i + reach; j++)
            {
                if (surface[j] != Integer.MIN_VALUE)
                {
                    window[measured++] = surface[j];
                }
            }
            final int median;
            if (measured == 0)
            {
                median = previousY;
            }
            else
            {
                Arrays.sort(window, 0, measured);

                final int low = window[(measured - 1) / 2];
                median = surface[i] == Integer.MIN_VALUE ? low : Mth.clamp(surface[i], low, window[measured / 2]);
            }

            final boolean standing = roadbed[i] && Math.abs(surface[i] - previousY) <= 1;
            planned[i] = Mth.clamp(standing ? surface[i] : median, previousY - 1, previousY + 1);
            if (climbing)
            {
                planned[i] = previousY + 1;
            }

            if (fromDoor && route == null && i == 0)
            {
                planned[i] = Mth.clamp(planned[i], start.getY() - 2, start.getY());
            }

            cutThrough[i] = fromDoor && route == null && roadbed[i] && liveRoads.contains(BlockPos.asLong(x, 0, z))
                              && surface[i] - planned[i] >= 1 && surface[i] - planned[i] <= CUT_THROUGH;

            if (fromDoor && route == null && i > 0 && surface[i] != Integer.MIN_VALUE && planned[i] - surface[i] > FILL_DEPTH)
            {
                end = i - 1;
                stop = "a drop the fill cannot reach";
                break;
            }
            previousY = planned[i];
        }

        if (ColonyAutopilot.LOGGER.isDebugEnabled())
        {
            final StringBuilder plan = new StringBuilder();
            for (int i = 0; i <= end; i++)
            {
                plan.append(i == 0 ? "" : " ").append(columns.get(i)[0]).append(',').append(columns.get(i)[1]).append('@')
                  .append(planned[i] == Integer.MIN_VALUE ? "-" : String.valueOf(planned[i]));
            }
            ColonyAutopilot.LOGGER.debug("carve from {} toward {}: {} of {} columns laid, stopped by {}: {}",
              start.toShortString(), center.toShortString(), end + 1, steps + 1, stop, plan);
        }

        final Map<Long, Integer> lanes = new HashMap<>();
        for (int i = 0; i <= end; i++)
        {
            if (planned[i] == Integer.MIN_VALUE)
            {
                continue;
            }
            for (int w = -half; w <= half; w++)
            {
                lanes.merge(BlockPos.asLong(columns.get(i)[0] + sides[i].getStepX() * w, 0, columns.get(i)[1] + sides[i].getStepZ() * w),
                  planned[i], Math::max);
            }
        }

        final Map<Long, Integer> bandLow = new HashMap<>();
        final Map<Long, Integer> bandHigh = new HashMap<>();

        final Set<Long> centres = new HashSet<>();
        for (int i = 0; i <= end; i++)
        {
            if (planned[i] == Integer.MIN_VALUE)
            {
                continue;
            }
            centres.add(BlockPos.asLong(columns.get(i)[0], 0, columns.get(i)[1]));
            for (int w = -half - 1; w <= half + 1; w++)
            {
                final long cell = BlockPos.asLong(columns.get(i)[0] + sides[i].getStepX() * w, 0, columns.get(i)[1] + sides[i].getStepZ() * w);
                bandLow.merge(cell, planned[i], Math::min);
                bandHigh.merge(cell, planned[i], Math::max);
            }
        }

        final Map<Long, Integer> paintedColumns = new HashMap<>();

        final Set<Long> refusedTrunks = new HashSet<>();

        final int[] laneEnd = new int[end + 1];
        for (int i = 0; i <= end; i++)
        {
            if (planned[i] == Integer.MIN_VALUE)
            {

                if (blind != null)
                {
                    markBlind(blind, columns.get(i), sides[i], half);
                }
                continue;
            }
            final int x = columns.get(i)[0];
            final int z = columns.get(i)[1];
            final int y = planned[i];

            for (int w = -half; w <= half; w++)
            {
                final int rx = x + sides[i].getStepX() * w;
                final int rz = z + sides[i].getStepZ() * w;
                if (!WorldUtil.isChunkLoaded(level, rx >> 4, rz >> 4) || foreign.contains(ChunkPos.asLong(rx >> 4, rz >> 4)))
                {
                    continue;
                }

                final long column = BlockPos.asLong(rx, 0, rz);
                if ((w != 0 && ((liveRoads.contains(column) && !cutThrough[i]) || centres.contains(column))) || paintedColumns.containsKey(column)
                      || insideShields(obstacles, rx, rz))
                {
                    continue;
                }
                final BlockPos ground = new BlockPos(rx, y, rz);

                if (cutThrough[i])
                {
                    for (int up = 1; up <= CUT_THROUGH + 1; up++)
                    {
                        final BlockPos over = ground.above(up);
                        final BlockState crossing = level.getBlockState(over);
                        if ((crossing.is(Blocks.DIRT_PATH) || crossing.is(Blocks.GLOWSTONE)) && (previous == null || !previous.contains(over.asLong())))
                        {
                            WorldUtil.setBlockState(level, over, Blocks.AIR.defaultBlockState());
                        }
                    }
                }

                for (int up = 0; up <= 3; up++)
                {
                    final BlockPos cap = ground.above(up);
                    if (dismantleLamp(level, cap, true)
                          || (level.getBlockState(cap.below(3)).is(Blocks.DIRT_PATH) && dismantleLamp(level, cap, false)))
                    {
                        break;
                    }
                }

                final Paved paved = pave(level, ground, previous, columns.get(i).length > 2 ? RoadRouter.STAIR_FILL : FILL_DEPTH);
                if (paved != Paved.NONE)
                {
                    painted.add(ground);
                    paintedColumns.put(column, y);
                }
            }
            laneEnd[i] = painted.size();

            for (int w = -half - 1; w <= half + 1; w++)
            {
                final int cx = x + sides[i].getStepX() * w;
                final int cz = z + sides[i].getStepZ() * w;
                if (!WorldUtil.isChunkLoaded(level, cx >> 4, cz >> 4) || foreign.contains(ChunkPos.asLong(cx >> 4, cz >> 4)) || insideShields(shields, cx, cz))
                {
                    continue;
                }
                final Integer roadAbove = lanes.get(BlockPos.asLong(cx, 0, cz));
                clearColumn(level, new BlockPos(cx, y, cz), shields, roadAbove != null && roadAbove > y ? roadAbove : null, previous, refusedTrunks);
            }
        }

        final BlockPos[][] spots = new BlockPos[end + 1][];
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = LAMP_OFFSET; i <= end; i++)
        {
            if (planned[i] == Integer.MIN_VALUE)
            {
                continue;
            }
            final int x = columns.get(i)[0];
            final int z = columns.get(i)[1];
            final int y = planned[i];
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
            spots[i] = new BlockPos[2];
            for (int k = 0; k < 2; k++)
            {
                final int shoulder = k == 0 ? half + 1 : -(half + 1);
                final BlockPos base = new BlockPos(x + sides[i].getStepX() * shoulder, y, z + sides[i].getStepZ() * shoulder);
                final long cell = BlockPos.asLong(base.getX(), 0, base.getZ());
                final Integer low = bandLow.get(cell);
                if (!lanes.containsKey(cell) && (low == null || y < low + 1 || y > bandHigh.get(cell) + CLEAR_HEIGHT + 1)
                      && WorldUtil.isChunkLoaded(level, base.getX() >> 4, base.getZ() >> 4) && !foreign.contains(ChunkPos.asLong(base.getX() >> 4, base.getZ() >> 4))
                      && !insideShields(obstacles, base.getX(), base.getZ()))
                {
                    spots[i][k] = base;
                }
            }
        }
        if (minX == Integer.MAX_VALUE)
        {
            return painted;
        }

        final int around = Math.max(lampSpacing, LAMP_REACH + 1) + half + 1;
        final List<BlockPos> lights = postsIn(level, minX - around, minZ - around, maxX + around, maxZ + around);
        final BlockPos[] lampAt = new BlockPos[end + 1];

        final Set<Long> entered = new HashSet<>(fresh != null ? fresh : Set.of());
        int first = 0;
        for (int pass = 0; pass < 2; pass++)
        {
            for (int i = LAMP_OFFSET; i <= end; i++)
            {
                if (spots[i] == null || lampAt[i] != null || (pass == 1 && lanesLit(columns.get(i), sides[i], half, paintedColumns, lights)))
                {
                    continue;
                }
                for (int k = 0; k < 2 && pass == 0 && lampAt[i] == null; k++)
                {
                    final BlockPos base = spots[i][(first + k) % 2];
                    if (base != null && !entered.contains(base.asLong())
                          && (previous != null ? previous.contains(base.asLong()) : !liveRoads.contains(BlockPos.asLong(base.getX(), 0, base.getZ())))
                          && standingPost(level, base))
                    {
                        lampAt[i] = base;
                        entered.add(base.asLong());
                        first = 1 - (first + k) % 2;
                    }
                }
                for (int k = 0; k < 2 && lampAt[i] == null; k++)
                {
                    final BlockPos base = spots[i][(first + k) % 2];
                    if (base != null && (pass == 1 || !crowded(base, lights, lampSpacing)) && lamp(level, base))
                    {
                        lampAt[i] = base;
                        lights.add(base);
                        first = 1 - (first + k) % 2;
                    }
                }
                if (pass == 0 && lampAt[i] == null)
                {

                    final BlockPos deck = new BlockPos(columns.get(i)[0], planned[i], columns.get(i)[1]);
                    final BlockState belowDeck = level.getBlockState(deck.below());
                    if (level.getBlockState(deck).is(Blocks.DIRT_PATH) && (belowDeck.canBeReplaced() || !belowDeck.getFluidState().isEmpty())
                          && !crowded(deck, lights, lampSpacing))
                    {
                        WorldUtil.setBlockState(level, deck, Blocks.GLOWSTONE.defaultBlockState());
                        lights.add(deck);
                    }
                }
            }
        }

        final List<BlockPos> ordered = new ArrayList<>(painted.size() + end + 1);
        int from = 0;
        for (int i = 0; i <= end; i++)
        {
            if (planned[i] != Integer.MIN_VALUE)
            {
                ordered.addAll(painted.subList(from, laneEnd[i]));
                from = laneEnd[i];
            }
            if (lampAt[i] != null)
            {
                ordered.add(lampAt[i]);
            }
        }
        ordered.addAll(painted.subList(from, painted.size()));
        return ordered;
    }

    private static boolean lanesLit(final int[] column, final Direction side, final int half, final Map<Long, Integer> paintedColumns,
      final List<BlockPos> posts)
    {
        for (int w = -half; w <= half; w++)
        {
            final int x = column[0] + side.getStepX() * w;
            final int z = column[1] + side.getStepZ() * w;
            final Integer y = paintedColumns.get(BlockPos.asLong(x, 0, z));
            if (y != null && !lit(new BlockPos(x, y + 1, z), posts, null))
            {
                return false;
            }
        }
        return true;
    }

    private static void markBlind(final Set<Long> blind, final int[] column, final Direction side, final int half)
    {
        for (int w = -half - 1; w <= half + 1; w++)
        {
            blind.add(BlockPos.asLong(column[0] + side.getStepX() * w, 0, column[1] + side.getStepZ() * w));
        }
    }

    private static Set<Long> foreignChunks(final ServerLevel level, final BlockPos start, final boolean fromDoor, final BlockPos center,
      final List<int[]> columns, final Direction[] sides, final int half)
    {
        IColony own = null;
        for (final BlockPos at : fromDoor ? new BlockPos[] {start, center} : new BlockPos[] {center, start})
        {
            if (own == null && WorldUtil.isChunkLoaded(level, at.getX() >> 4, at.getZ() >> 4))
            {
                own = IColonyManager.getInstance().getColonyByPosFromWorld(level, at);
            }
        }
        if (own == null)
        {
            return Set.of();
        }
        final Set<Long> touched = new HashSet<>();
        for (int i = 0; i < columns.size(); i++)
        {
            for (int w = -half - 1; w <= half + 1; w++)
            {
                touched.add(ChunkPos.asLong((columns.get(i)[0] + sides[i].getStepX() * w) >> 4, (columns.get(i)[1] + sides[i].getStepZ() * w) >> 4));
            }
        }
        final Set<Long> foreign = new HashSet<>();
        for (final long chunk : touched)
        {
            final int minX = ChunkPos.getX(chunk) << 4;
            final int minZ = ChunkPos.getZ(chunk) << 4;
            if (!SiteSelector.foreignClaims(level, own, minX, minZ, minX, minZ).isEmpty())
            {
                foreign.add(chunk);
            }
        }
        return foreign;
    }

    private static boolean entersBox(final List<SiteSelector.Footprint> obstacles, final int x, final int z, final Direction side, final int half)
    {
        for (final SiteSelector.Footprint box : obstacles)
        {
            for (int w = -half; w <= half; w++)
            {
                final int rx = x + side.getStepX() * w;
                final int rz = z + side.getStepZ() * w;
                if (box.intersects(rx, rz, rx, rz))
                {
                    return true;
                }
            }
        }
        return false;
    }

    static List<int[]> straightLine(final BlockPos start, final BlockPos end)
    {
        final int dx = end.getX() - start.getX();
        final int dz = end.getZ() - start.getZ();
        final int distance = Math.max(Math.abs(dx), Math.abs(dz));
        final int steps = Math.min(MAX_LENGTH, distance);
        final List<int[]> columns = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++)
        {
            columns.add(distance == 0 ? new int[] {start.getX(), start.getZ()}
                          : new int[] {start.getX() + Math.round((float) dx * i / distance), start.getZ() + Math.round((float) dz * i / distance)});
        }
        return columns;
    }

    private static Direction sideOf(final int dx, final int dz)
    {
        return Math.abs(dx) > Math.abs(dz)
                 ? (dz >= 0 ? Direction.SOUTH : Direction.NORTH)
                 : (dx >= 0 ? Direction.EAST : Direction.WEST);
    }

    static List<BlockPos> erase(final ServerLevel level, final List<BlockPos> blocks, final Set<Long> spare, final Set<Long> blind)
    {
        final List<BlockPos> kept = new ArrayList<>();

        final Set<Long> old = new HashSet<>();
        for (final BlockPos pos : blocks)
        {
            old.add(pos.asLong());
        }
        for (final BlockPos pos : blocks)
        {
            if (spare.contains(pos.asLong()))
            {
                continue;
            }
            if (!WorldUtil.isChunkLoaded(level, pos.getX() >> 4, pos.getZ() >> 4))
            {
                kept.add(pos);
                continue;
            }

            if (blind.contains(BlockPos.asLong(pos.getX(), 0, pos.getZ())))
            {
                kept.add(pos);
                continue;
            }
            final BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.GLOWSTONE) && level.getBlockState(pos.below()).is(Blocks.OAK_FENCE))
            {
                continue;
            }
            if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.GLOWSTONE))
            {
                final BlockState below = level.getBlockState(pos.below());
                final BlockState replacement;
                if (below.is(Blocks.DIRT_PATH))
                {

                    replacement = Blocks.AIR.defaultBlockState();
                }
                else if (below.canBeReplaced() || !below.getFluidState().isEmpty())
                {

                    final BlockState carried = level.getBlockState(pos.above());
                    replacement = supportsRoad(level, pos, old) || !(carried.isAir() || carried.canBeReplaced() || carried.is(Blocks.OAK_FENCE) || carried.is(Blocks.GLOWSTONE))
                                    ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
                }
                else
                {
                    replacement = Blocks.DIRT.defaultBlockState();
                }
                WorldUtil.setBlockState(level, pos, replacement);
            }
            else if (state.is(Blocks.COBBLESTONE) && level.getBlockState(pos.above()).is(Blocks.OAK_FENCE)
                       && (level.getBlockState(pos.above(2)).is(Blocks.OAK_FENCE) || level.getBlockState(pos.above(2)).is(Blocks.GLOWSTONE)))
            {

                for (int up = 1; up <= 3; up++)
                {
                    final BlockState part = level.getBlockState(pos.above(up));
                    if (!part.is(Blocks.OAK_FENCE) && !part.is(Blocks.GLOWSTONE))
                    {
                        break;
                    }
                    WorldUtil.setBlockState(level, pos.above(up), Blocks.AIR.defaultBlockState());
                }
                WorldUtil.setBlockState(level, pos, Blocks.DIRT.defaultBlockState());
            }
        }
        return kept;
    }

    static boolean dismantleLamp(final ServerLevel level, final BlockPos cap, final boolean requireVillageFooting)
    {

        if (!level.getBlockState(cap).is(Blocks.GLOWSTONE)
              || !level.getBlockState(cap.below()).is(Blocks.OAK_FENCE)
              || !level.getBlockState(cap.below(2)).is(Blocks.OAK_FENCE)
              || (requireVillageFooting && !level.getBlockState(cap.below(3)).is(Blocks.COBBLESTONE)))
        {
            return false;
        }
        WorldUtil.setBlockState(level, cap, Blocks.AIR.defaultBlockState());
        WorldUtil.setBlockState(level, cap.below(), Blocks.AIR.defaultBlockState());
        WorldUtil.setBlockState(level, cap.below(2), Blocks.AIR.defaultBlockState());
        if (level.getBlockState(cap.below(3)).is(Blocks.COBBLESTONE))
        {

            WorldUtil.setBlockState(level, cap.below(3),
              level.getBlockState(cap.below(4)).is(Blocks.DIRT_PATH) ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        }
        return true;
    }

    static int clearLampsIn(final ServerLevel level, final IColony colony, final SiteSelector.Footprint box)
    {
        final List<SiteSelector.Footprint> foreign = SiteSelector.foreignClaims(level, colony, box.minX(), box.minZ(), box.maxX(), box.maxZ());
        int cleared = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4) || insideShields(foreign, x, z))
                {
                    continue;
                }
                final BlockPos cap = new BlockPos(x, SiteSelector.groundHeight(level, x, z) - 1, z);
                if (dismantleLamp(level, cap, true))
                {
                    cleared++;
                    continue;
                }

                if (level.getBlockState(cap).is(Blocks.GLOWSTONE)
                      && (level.getBlockState(cap.north()).is(Blocks.DIRT_PATH) || level.getBlockState(cap.south()).is(Blocks.DIRT_PATH)
                            || level.getBlockState(cap.east()).is(Blocks.DIRT_PATH) || level.getBlockState(cap.west()).is(Blocks.DIRT_PATH)))
                {
                    final BlockState below = level.getBlockState(cap.below());
                    final boolean floating = below.canBeReplaced() || !below.getFluidState().isEmpty();
                    if (!floating)
                    {

                        continue;
                    }
                    WorldUtil.setBlockState(level, cap, Blocks.DIRT_PATH.defaultBlockState());
                    cleared++;
                }
            }
        }
        return cleared;
    }

    static boolean lamp(final ServerLevel level, final BlockPos ground)
    {
        final BlockState existing = level.getBlockState(ground);
        final BlockState under = level.getBlockState(ground.below());
        if (existing.hasBlockEntity() || existing.is(Blocks.DIRT_PATH) || existing.is(Blocks.FARMLAND)
              || !existing.getFluidState().isEmpty()
              || under.canBeReplaced())
        {
            return false;
        }

        if (under.is(Blocks.DIRT_PATH) || under.is(Blocks.GLOWSTONE))
        {
            return false;
        }
        for (int i = 1; i <= 3; i++)
        {
            final BlockState above = level.getBlockState(ground.above(i));
            if (!(above.isAir() || above.canBeReplaced()))
            {
                return false;
            }
        }
        WorldUtil.setBlockState(level,ground, Blocks.COBBLESTONE.defaultBlockState());
        WorldUtil.setBlockState(level,ground.above(), Blocks.OAK_FENCE.defaultBlockState());
        WorldUtil.setBlockState(level,ground.above(2), Blocks.OAK_FENCE.defaultBlockState());
        WorldUtil.setBlockState(level,ground.above(3), Blocks.GLOWSTONE.defaultBlockState());
        return true;
    }

    static int surfaceY(final ServerLevel level, final int x, final int z)
    {
        final int top = SiteSelector.groundHeight(level, x, z) - 1;
        return level.getBlockState(new BlockPos(x, top, z)).is(Blocks.GLOWSTONE)
                 && level.getBlockState(new BlockPos(x, top - 1, z)).is(Blocks.OAK_FENCE)
                 && level.getBlockState(new BlockPos(x, top - 2, z)).is(Blocks.OAK_FENCE) ? top - 3 : top;
    }

    static boolean standingPost(final ServerLevel level, final BlockPos base)
    {
        return level.getBlockState(base).is(Blocks.COBBLESTONE)
                 && level.getBlockState(base.above()).is(Blocks.OAK_FENCE)
                 && level.getBlockState(base.above(2)).is(Blocks.OAK_FENCE)
                 && level.getBlockState(base.above(3)).is(Blocks.GLOWSTONE);
    }

    static BlockPos postAt(final ServerLevel level, final int x, final int z)
    {
        final BlockPos cap = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        return level.getBlockState(cap).is(Blocks.GLOWSTONE) && level.getBlockState(cap.below()).is(Blocks.OAK_FENCE)
                 && level.getBlockState(cap.below(2)).is(Blocks.OAK_FENCE) ? cap.below(3) : null;
    }

    static List<BlockPos> postsIn(final ServerLevel level, final int minX, final int minZ, final int maxX, final int maxZ)
    {
        final List<BlockPos> posts = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++)
        {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++)
            {
                if (!WorldUtil.isChunkLoaded(level, chunkX, chunkZ))
                {
                    continue;
                }
                for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++)
                {
                    for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++)
                    {
                        final BlockPos post = postAt(level, x, z);
                        if (post != null)
                        {
                            posts.add(post);
                        }
                    }
                }
            }
        }
        return posts;
    }

    static boolean crowded(final BlockPos spot, final List<BlockPos> posts, final int spacing)
    {
        for (final BlockPos post : posts)
        {
            final long dx = spot.getX() - post.getX();
            final long dz = spot.getZ() - post.getZ();
            if (!post.equals(spot) && dx * dx + dz * dz < (long) spacing * spacing && Math.abs(spot.getY() - post.getY()) <= LAMP_SAME_LEVEL)
            {
                return true;
            }
        }
        return false;
    }

    static boolean lit(final BlockPos stand, final List<BlockPos> posts, final BlockPos except)
    {
        for (final BlockPos post : posts)
        {
            if (!post.equals(except)
                  && Math.abs(post.getX() - stand.getX()) + Math.abs(post.getY() + 3 - stand.getY()) + Math.abs(post.getZ() - stand.getZ()) < 15)
            {
                return true;
            }
        }
        return false;
    }

    static final int FILL_DEPTH = 3;

    private static final int CUT_THROUGH = 2;

    static String breakIn(final ServerLevel level, final List<BlockPos> blocks, final int end, final BlockPos door)
    {
        final Map<Long, List<Integer>> columns = new HashMap<>();
        final List<BlockPos> line = new ArrayList<>();
        for (int i = 0; i < Math.min(end, blocks.size()); i++)
        {
            final BlockPos block = blocks.get(i);
            if (!WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4))
            {
                return null;
            }
            final BlockState state = level.getBlockState(block);
            if (!state.is(Blocks.DIRT_PATH) && !state.is(Blocks.GLOWSTONE))
            {
                continue;
            }
            columns.computeIfAbsent(BlockPos.asLong(block.getX(), 0, block.getZ()), k -> new ArrayList<>()).add(block.getY());
            line.add(block);
        }
        if (line.size() < 2)
        {
            return null;
        }

        BlockPos first = null;
        for (final BlockPos block : line)
        {
            if (Math.abs(block.getX() - door.getX()) <= 1 && Math.abs(block.getZ() - door.getZ()) <= 1 && Math.abs(block.getY() - (door.getY() - 1)) <= 1)
            {
                first = block;
                break;
            }
        }
        if (first == null)
        {
            final BlockPos nearest = line.get(0);
            return "no road at the door — the road starts " + Math.max(Math.abs(nearest.getX() - door.getX()), Math.abs(nearest.getZ() - door.getZ()))
                     + " columns out at " + nearest.toShortString();
        }
        final BlockPos last = line.get(line.size() - 1);
        final long start = BlockPos.asLong(first.getX(), 0, first.getZ());
        final long goal = BlockPos.asLong(last.getX(), 0, last.getZ());
        final Map<Long, Integer> reached = new HashMap<>();
        final Deque<Long> queue = new ArrayDeque<>();
        reached.put(start, first.getY());
        queue.add(start);
        while (!queue.isEmpty())
        {
            final long at = queue.poll();
            final int x = BlockPos.getX(at), z = BlockPos.getZ(at);
            for (final int height : columns.get(at))
            {
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        final long next = BlockPos.asLong(x + dx, 0, z + dz);
                        final List<Integer> heights = columns.get(next);
                        if ((dx == 0 && dz == 0) || heights == null || reached.containsKey(next))
                        {
                            continue;
                        }
                        for (final int there : heights)
                        {
                            if (Math.abs(there - height) <= 1 && headroom(level, x + dx, there, z + dz))
                            {
                                reached.put(next, there);
                                queue.add(next);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (reached.containsKey(goal))
        {
            return null;
        }

        BlockPos at = first;
        for (int i = line.size() - 1; i >= 0; i--)
        {
            if (reached.containsKey(BlockPos.asLong(line.get(i).getX(), 0, line.get(i).getZ())))
            {
                at = line.get(i);
                break;
            }
        }
        final int nx = at.getX(), nz = at.getZ(), ny = reached.get(BlockPos.asLong(nx, 0, nz));
        String beyond = null;
        int step = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                final long column = BlockPos.asLong(nx + dx, 0, nz + dz);
                final List<Integer> heights = columns.get(column);
                if ((dx == 0 && dz == 0) || heights == null || reached.containsKey(column))
                {
                    continue;
                }
                for (final int there : heights)
                {
                    final int rise = Math.abs(there - ny);
                    if (rise < step)
                    {
                        step = rise;
                        beyond = rise <= 1 ? "no headroom over " + (nx + dx) + ", " + there + ", " + (nz + dz)
                                   : "a step of " + rise + " to " + (nx + dx) + ", " + there + ", " + (nz + dz);
                    }
                }
            }
        }
        final int short_ = Math.max(Math.abs(last.getX() - nx), Math.abs(last.getZ() - nz));
        return "it breaks at " + nx + ", " + ny + ", " + nz + (beyond != null ? " — " + beyond : " — the road stops " + short_ + " columns short of its end");
    }

    private static boolean headroom(final ServerLevel level, final int x, final int y, final int z)
    {
        for (int up = 1; up <= 2; up++)
        {
            final BlockPos pos = new BlockPos(x, y + up, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    static boolean walkable(final ServerLevel level, final BlockPos from, final BlockPos to)
    {
        int last = Integer.MIN_VALUE;
        for (final int[] column : straightLine(from, to))
        {
            if (!WorldUtil.isChunkLoaded(level, column[0] >> 4, column[1] >> 4))
            {
                return false;
            }
            final int top = surfaceY(level, column[0], column[1]);
            if (last != Integer.MIN_VALUE && Math.abs(top - last) > 1)
            {
                return false;
            }
            last = top;
        }
        return true;
    }

    private enum Paved
    {
        NONE, GROUND, DECK
    }

    private static Paved pave(final ServerLevel level, final BlockPos ground, final Set<Long> previous, final int fillReach)
    {
        final BlockState existing = level.getBlockState(ground);

        if (existing.hasBlockEntity())
        {
            return Paved.NONE;
        }

        if (existing.is(BlockTags.DOORS) || existing.getBlock() instanceof FenceGateBlock)
        {
            return Paved.NONE;
        }
        final BlockPos under = ground.below();
        if (level.getBlockState(under).is(Blocks.DIRT_PATH))
        {

            if (previous == null || !previous.contains(under.asLong()))
            {
                return Paved.NONE;
            }
            WorldUtil.setBlockState(level, under, Blocks.DIRT.defaultBlockState());
        }

        for (int up = 1; up <= 4; up++)
        {
            final BlockPos over = ground.above(up);
            if (level.getBlockState(over).is(Blocks.DIRT_PATH) && (previous == null || !previous.contains(over.asLong())))
            {
                return Paved.NONE;
            }
        }

        final FluidState here = existing.getFluidState();
        final FluidState beneath = level.getBlockState(under).getFluidState();
        if ((!here.isEmpty() && !here.is(FluidTags.WATER)) || (!beneath.isEmpty() && !beneath.is(FluidTags.WATER)))
        {
            return Paved.NONE;
        }
        final boolean overWater = here.isSource() || beneath.isSource();

        int fillDepth = -1;
        if (!overWater)
        {
            for (int below = 1; below <= fillReach; below++)
            {
                final BlockPos at = ground.below(below);
                final BlockState support = level.getBlockState(at);
                if (!(support.canBeReplaced() || support.getFluidState().isSource()))
                {
                    if (support.is(Blocks.DIRT_PATH) && (previous == null || !previous.contains(at.asLong())))
                    {
                        return Paved.NONE;
                    }
                    fillDepth = below - 1;
                    break;
                }
            }
            for (int below = 1; below <= fillDepth; below++)
            {
                WorldUtil.setBlockState(level, ground.below(below), Blocks.DIRT.defaultBlockState());
            }
        }

        final boolean grounded = fillDepth >= 0;
        WorldUtil.setBlockState(level, ground, Blocks.DIRT_PATH.defaultBlockState());
        return grounded || overWater ? Paved.GROUND : Paved.DECK;
    }

    private static void clearColumn(final ServerLevel level, final BlockPos ground, final List<SiteSelector.Footprint> shields,
      final Integer roadAbove, final Set<Long> previous, final Set<Long> refusedTrunks)
    {
        boolean topCleared = false;
        for (int y = 1; y <= CLEAR_HEIGHT; y++)
        {
            final BlockPos pos = ground.above(y);
            if (roadAbove != null && pos.getY() < roadAbove)
            {
                continue;
            }

            if (supportsRoad(level, pos, previous))
            {
                continue;
            }

            if (standingPost(level, pos))
            {
                dismantleLamp(level, pos.above(3), true);
            }
            final BlockState state = level.getBlockState(pos);

            if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                  || state.is(Blocks.TORCH) || state.is(Blocks.OAK_FENCE) || state.is(Blocks.GLOWSTONE)
                  || state.is(Blocks.DIRT_PATH))
            {
                continue;
            }
            if (state.is(BlockTags.LOGS))
            {

                BlockPos base = pos;
                while (base.getY() > level.getMinBuildHeight() && level.getBlockState(base.below()).is(BlockTags.LOGS))
                {
                    base = base.below();
                }
                if (refusedTrunks.contains(BlockPos.asLong(base.getX(), 0, base.getZ())) || !fellTree(level, base, shields, false))
                {
                    refuseTrunk(refusedTrunks, base);
                    break;
                }
            }
            else
            {

                if (holdsFluid(level, pos) || fallingRunHoldsFluid(level, pos.above()))
                {
                    continue;
                }
                WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                topCleared = y == CLEAR_HEIGHT;
            }
        }
        if (!topCleared)
        {
            return;
        }

        final BlockPos over = ground.above(CLEAR_HEIGHT + 1);
        final BlockState onTop = level.getBlockState(over);
        if (onTop.is(BlockTags.LOGS))
        {
            if (!refusedTrunks.contains(BlockPos.asLong(over.getX(), 0, over.getZ())) && !fellTree(level, over, shields, false))
            {
                refuseTrunk(refusedTrunks, over);
            }
            return;
        }

        int runTop = CLEAR_HEIGHT;
        for (int y = CLEAR_HEIGHT + 1; y <= CLEAR_HEIGHT + 16; y++)
        {
            final BlockPos pos = ground.above(y);
            final BlockState state = level.getBlockState(pos);
            if (!(state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) || supportsRoad(level, pos, previous))
            {
                break;
            }
            if (holdsFluid(level, pos))
            {
                return;
            }
            runTop = y;
        }
        for (int y = CLEAR_HEIGHT + 1; y <= runTop; y++)
        {
            WorldUtil.setBlockState(level, ground.above(y), Blocks.AIR.defaultBlockState());
        }
    }

    private static boolean holdsFluid(final ServerLevel level, final BlockPos pos)
    {
        if (!level.getBlockState(pos.above()).getFluidState().isEmpty())
        {
            return true;
        }
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            final BlockPos beside = pos.relative(side);
            if (!WorldUtil.isChunkLoaded(level, beside.getX() >> 4, beside.getZ() >> 4) || !level.getBlockState(beside).getFluidState().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean fallingRunHoldsFluid(final ServerLevel level, final BlockPos from)
    {
        for (BlockPos pos = from; pos.getY() < level.getMaxBuildHeight(); pos = pos.above())
        {
            if (!(level.getBlockState(pos).getBlock() instanceof Fallable))
            {
                return false;
            }
            if (holdsFluid(level, pos))
            {
                return true;
            }
        }
        return false;
    }

    static void refuseTrunk(final Set<Long> refusedTrunks, final BlockPos base)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                refusedTrunks.add(BlockPos.asLong(base.getX() + dx, 0, base.getZ() + dz));
            }
        }
    }

    private static boolean supportsRoad(final ServerLevel level, final BlockPos pos, final Set<Long> ignored)
    {
        for (int up = 1; up <= 4; up++)
        {
            final BlockPos above = pos.above(up);
            final BlockState state = level.getBlockState(above);
            if (state.is(Blocks.DIRT_PATH) && (ignored == null || !ignored.contains(above.asLong())))
            {
                return true;
            }
            if (state.isAir() || state.canBeReplaced())
            {
                return false;
            }
        }
        return false;
    }

    static boolean fellTree(final ServerLevel level, final BlockPos seed, final List<SiteSelector.Footprint> shields, final boolean bareTrunkOk)
    {
        if (insideShields(shields, seed.getX(), seed.getZ()))
        {
            return refused(seed, "the seed stands in a shielded footprint — somebody's wall", 0, 0);
        }

        final Deque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> mass = new HashSet<>();
        queue.add(seed);
        mass.add(seed);
        while (!queue.isEmpty())
        {
            if (mass.size() >= MASS_LOGS)
            {
                TickBudget.spend(mass.size());
                return refused(seed, "a chained forest or a giant: the log mass passed MASS_LOGS", mass.size(), 0);
            }
            final BlockPos log = queue.poll();
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        final BlockPos next = log.offset(dx, dy, dz);
                        if (mass.contains(next) || !WorldUtil.isChunkLoaded(level, next.getX() >> 4, next.getZ() >> 4)
                              || !level.getBlockState(next).is(BlockTags.LOGS) || insideShields(shields, next.getX(), next.getZ())
                              || Math.abs(next.getX() - seed.getX()) > MASS_REACH || Math.abs(next.getZ() - seed.getZ()) > MASS_REACH
                              || next.getY() < seed.getY() - MASS_FLOOR || next.getY() > seed.getY() + 40)
                        {
                            continue;
                        }

                        if (next.getY() == seed.getY() - MASS_FLOOR)
                        {
                            TickBudget.spend(mass.size());
                            return refused(seed, "a log on the flood's floor, " + MASS_FLOOR + " under the seed: the tree goes on below", mass.size(), 0);
                        }
                        mass.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        TickBudget.spend(mass.size());

        final Set<BlockPos> logs = ownShare(level, mass, seed);
        if (logs == null)
        {
            return refused(seed, "no share of the mass fits one tree (over 256 logs, 16 wide or 35 tall)", mass.size(), 0);
        }

        for (final BlockPos log : logs)
        {
            final String name = BuiltInRegistries.BLOCK.getKey(level.getBlockState(log).getBlock()).getPath();
            if (name.startsWith("stripped_") || name.endsWith("_wood") || name.endsWith("_hyphae"))
            {
                return refused(seed, "a stripped log or bark block in the share (" + name + "): a build", mass.size(), logs.size());
            }
        }

        if (!trunkFootprint(level, logs))
        {
            return refused(seed, "the grounded logs do not fit a 2x2: a row of posts or a frame", mass.size(), logs.size());
        }

        final Map<Long, Boolean> shielded = new HashMap<>();
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, seed);
        final List<SiteSelector.Footprint> zones = colony == null ? List.of() : ProtectedZones.footprintsFor(colony);
        if (touchesBuild(level, logs, shields, zones, shielded))
        {
            return refused(seed, "a built block on a log's face", mass.size(), logs.size());
        }

        final boolean split = logs.size() != mass.size();
        final int hunt = !split && logs.size() <= BARE_TRUNK_LOGS ? 3 : 1;
        boolean canopy = false;
        final Set<Long> hunted = new HashSet<>();
        canopyHunt:
        for (final BlockPos log : logs)
        {
            for (int dx = -hunt; dx <= hunt; dx++)
            {
                for (int dz = -hunt; dz <= hunt; dz++)
                {
                    final int cx = log.getX() + dx, cz = log.getZ() + dz;
                    if (shielded.computeIfAbsent(BlockPos.asLong(cx, 0, cz), c -> insideShields(shields, cx, cz)))
                    {
                        continue;
                    }
                    for (int dy = -hunt; dy <= hunt; dy++)
                    {
                        final BlockPos leaf = log.offset(dx, dy, dz);
                        if (!hunted.add(leaf.asLong()) || !WorldUtil.isChunkLoaded(level, leaf.getX() >> 4, leaf.getZ() >> 4))
                        {
                            continue;
                        }
                        final BlockState state = level.getBlockState(leaf);
                        if (state.is(BlockTags.LEAVES)
                              && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)))
                        {
                            canopy = true;
                            break canopyHunt;
                        }
                    }
                }
            }
        }
        if (!canopy && !(bareTrunkOk && logs.size() <= BARE_TRUNK_LOGS && freeStanding(level, logs)))
        {
            return refused(seed, "no grown leaf within " + hunt + " of a log" + (split ? " (a share of a touching mass)" : ""), mass.size(), logs.size());
        }

        for (final BlockPos log : logs)
        {
            WorldUtil.setBlockState(level, log, Blocks.AIR.defaultBlockState());
        }

        final Set<Long> neighbours = new HashSet<>();
        if (split)
        {
            for (final BlockPos other : mass)
            {
                if (logs.contains(other))
                {
                    continue;
                }
                boolean near = false;
                for (final BlockPos log : logs)
                {
                    if (Math.abs(other.getX() - log.getX()) <= 5 && Math.abs(other.getZ() - log.getZ()) <= 5 && Math.abs(other.getY() - log.getY()) <= 5)
                    {
                        near = true;
                        break;
                    }
                }
                if (!near)
                {
                    continue;
                }
                for (int dx = -2; dx <= 2; dx++)
                {
                    for (int dy = -2; dy <= 2; dy++)
                    {
                        for (int dz = -2; dz <= 2; dz++)
                        {
                            neighbours.add(other.offset(dx, dy, dz).asLong());
                        }
                    }
                }
            }
        }

        final Set<Long> swept = new HashSet<>();
        for (final BlockPos log : logs)
        {
            for (int dx = -3; dx <= 3; dx++)
            {
                for (int dz = -3; dz <= 3; dz++)
                {
                    final int cx = log.getX() + dx, cz = log.getZ() + dz;
                    if (shielded.computeIfAbsent(BlockPos.asLong(cx, 0, cz), c -> insideShields(shields, cx, cz)) || insideShields(zones, cx, cz))
                    {
                        continue;
                    }
                    for (int dy = -3; dy <= 3; dy++)
                    {
                        final BlockPos leaf = log.offset(dx, dy, dz);
                        if (!swept.add(leaf.asLong()) || neighbours.contains(leaf.asLong())
                              || !WorldUtil.isChunkLoaded(level, leaf.getX() >> 4, leaf.getZ() >> 4))
                        {
                            continue;
                        }
                        final BlockState state = level.getBlockState(leaf);
                        if (state.is(BlockTags.LEAVES)
                              && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)))
                        {
                            WorldUtil.setBlockState(level, leaf, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean refused(final BlockPos seed, final String reason, final int mass, final int share)
    {
        ColonyAutopilot.LOGGER.debug("fell refused at {}: {} (mass {} logs, share {})", seed.toShortString(), reason, mass, share);
        return false;
    }

    private static final int BARE_TRUNK_LOGS = 32;

    private static final int MASS_LOGS = 1024;

    static final int MASS_REACH = 24;

    private static final int MASS_FLOOR = 24;

    private static Set<BlockPos> ownShare(final ServerLevel level, final Set<BlockPos> mass, final BlockPos seed)
    {
        final List<BlockPos> grounded = new ArrayList<>();
        for (final BlockPos log : mass)
        {
            if (!mass.contains(log.below()) && restsOnGround(level, log.below()))
            {
                grounded.add(log);
            }
        }
        final Map<BlockPos, Integer> owner = new HashMap<>();
        int trunks = 0;
        for (final BlockPos base : grounded)
        {
            if (owner.containsKey(base))
            {
                continue;
            }
            final Deque<BlockPos> group = new ArrayDeque<>();
            group.add(base);
            owner.put(base, trunks);
            while (!group.isEmpty())
            {
                final BlockPos at = group.poll();
                for (final BlockPos other : grounded)
                {
                    if (!owner.containsKey(other) && Math.abs(other.getX() - at.getX()) <= 1
                          && Math.abs(other.getZ() - at.getZ()) <= 1 && Math.abs(other.getY() - at.getY()) <= 1)
                    {
                        owner.put(other, trunks);
                        group.add(other);
                    }
                }
            }
            trunks++;
        }
        final Set<BlockPos> share;
        if (trunks <= 1)
        {
            share = mass;
        }
        else
        {

            final Deque<BlockPos> wave = new ArrayDeque<>(grounded);
            while (!wave.isEmpty())
            {
                final BlockPos log = wave.poll();
                final int trunk = owner.get(log);
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            final BlockPos next = log.offset(dx, dy, dz);
                            if (mass.contains(next) && !owner.containsKey(next))
                            {
                                owner.put(next, trunk);
                                wave.add(next);
                            }
                        }
                    }
                }
            }
            final Integer mine = owner.get(seed);
            if (mine == null)
            {
                return null;
            }
            share = new HashSet<>();
            for (final Map.Entry<BlockPos, Integer> entry : owner.entrySet())
            {
                if (entry.getValue().equals(mine))
                {
                    share.add(entry.getKey());
                }
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (final BlockPos log : share)
        {
            minX = Math.min(minX, log.getX());
            maxX = Math.max(maxX, log.getX());
            minZ = Math.min(minZ, log.getZ());
            maxZ = Math.max(maxZ, log.getZ());
            minY = Math.min(minY, log.getY());
            maxY = Math.max(maxY, log.getY());
        }
        return share.size() > 256 || maxX - minX > 16 || maxZ - minZ > 16 || maxY - minY > 35 ? null : share;
    }

    private static boolean restsOnGround(final ServerLevel level, final BlockPos under)
    {
        if (!WorldUtil.isChunkLoaded(level, under.getX() >> 4, under.getZ() >> 4))
        {
            return false;
        }
        final BlockState state = level.getBlockState(under);
        return VillageGrounds.isEarth(state.getBlock()) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
                 || state.is(Blocks.GRAVEL) || state.is(BlockTags.NYLIUM) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                 || state.is(Blocks.DIRT_PATH);
    }

    private static boolean trunkFootprint(final ServerLevel level, final Set<BlockPos> logs)
    {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos log : logs)
        {
            final BlockPos under = log.below();
            final BlockState below = level.getBlockState(under);

            if (below.is(BlockTags.LOGS) || below.isAir() || below.canBeReplaced() || below.is(BlockTags.LEAVES)
                  || below.is(Blocks.VINE) || below.is(Blocks.SNOW) || below.is(Blocks.COCOA) || below.is(Blocks.BEE_NEST))
            {
                continue;
            }
            minX = Math.min(minX, log.getX());
            maxX = Math.max(maxX, log.getX());
            minZ = Math.min(minZ, log.getZ());
            maxZ = Math.max(maxZ, log.getZ());
        }
        return minX == Integer.MAX_VALUE || (maxX - minX <= 1 && maxZ - minZ <= 1);
    }

    private static boolean touchesBuild(final ServerLevel level, final Set<BlockPos> logs, final List<SiteSelector.Footprint> shields,
      final List<SiteSelector.Footprint> zones, final Map<Long, Boolean> shielded)
    {
        for (final BlockPos log : logs)
        {
            for (final Direction side : Direction.values())
            {
                final BlockPos beside = log.relative(side);
                if (logs.contains(beside) || !WorldUtil.isChunkLoaded(level, beside.getX() >> 4, beside.getZ() >> 4))
                {
                    continue;
                }
                final int bx = beside.getX(), bz = beside.getZ();
                if (shielded.computeIfAbsent(BlockPos.asLong(bx, 0, bz), c -> insideShields(shields, bx, bz)) && !insideShields(zones, bx, bz))
                {
                    continue;
                }
                final BlockState state = level.getBlockState(beside);
                if (lampPart(level, beside, state))
                {
                    continue;
                }
                if (state.is(BlockTags.PLANKS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.FENCES)
                      || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.WALLS)
                      || state.is(BlockTags.WOOL) || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.BANNERS) || state.is(BlockTags.IMPERMEABLE)
                      || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.LADDER)
                      || state.is(Blocks.CHAIN) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.SOUL_LANTERN)
                      || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(BlockTags.CANDLES)
                      || state.is(BlockTags.BUTTONS) || state.getBlock() instanceof IronBarsBlock || state.is(Blocks.END_ROD)
                      || state.is(Blocks.LIGHTNING_ROD) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.FLOWER_POTS)
                      || (state.hasBlockEntity() && !state.is(Blocks.BEE_NEST)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean lampPart(final ServerLevel level, final BlockPos pos, final BlockState state)
    {
        if (!state.is(Blocks.COBBLESTONE) && !state.is(Blocks.OAK_FENCE) && !state.is(Blocks.GLOWSTONE))
        {
            return false;
        }
        for (int down = 0; down <= 3; down++)
        {
            if (standingPost(level, pos.below(down)))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean freeStanding(final ServerLevel level, final Set<BlockPos> logs)
    {
        for (final BlockPos log : logs)
        {
            final BlockState state = level.getBlockState(log);
            if (!state.hasProperty(RotatedPillarBlock.AXIS) || state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y
                  || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")
                  || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_wood"))
            {
                return false;
            }
            for (final Direction side : Direction.Plane.HORIZONTAL)
            {
                if (!natural(level, log.relative(side), logs))
                {
                    return false;
                }
            }
            if (!logs.contains(log.above()) && !natural(level, log.above(), logs))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean natural(final ServerLevel level, final BlockPos pos, final Set<BlockPos> logs)
    {
        if (logs.contains(pos) || !WorldUtil.isChunkLoaded(level, pos.getX() >> 4, pos.getZ() >> 4))
        {
            return true;
        }
        final BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()
                 || VillageGrounds.isEarth(state.getBlock()) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                 || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE);
    }

    private static boolean insideShields(final List<SiteSelector.Footprint> shields, final int x, final int z)
    {
        for (final SiteSelector.Footprint box : shields)
        {
            if (box.intersects(x, z, x, z))
            {
                return true;
            }
        }
        return false;
    }
}
