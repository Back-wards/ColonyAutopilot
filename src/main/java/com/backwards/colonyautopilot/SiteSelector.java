// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SiteSelector
{

    static final double BUILDER_RANGE_SQ = 100 * 100;

    private static final int MIN_RADIUS = 12;

    private static final int FACING_WINDOW = 12;

    private static final int SCORE_FACING_TOLERANCE = 120;

    private static final int SPREAD_RING = 45;

    static final int KEYSTONE_RADIUS = 50;

    private static final int GRADE_REACH = 5;

    static final int RAMP_CUT = 24;

    private static final int RAMP_MAX = 40;

    private static final int LAND_RUN = 3;

    private static final int MAX_PROMINENCE = 4;

    static final int FORCED_MAX_CUT = 512;
    private static final int FORCED_RAMP_MAX = 400;
    private static final int FORCED_LEVEL_PAD = 8;

    record Placement(int sizeX, int sizeZ, int offsetX, int offsetZ, int doorX, int doorZ)
    {
    }

    record Result(BlockPos anchor, Direction facing)
    {
    }

    record TerraformSite(Result site, int minX, int minZ, int maxX, int maxZ, int targetY, List<int[]> ramp, boolean forced)
    {
    }

    record Footprint(int minX, int minZ, int maxX, int maxZ)
    {
        boolean intersects(final int oMinX, final int oMinZ, final int oMaxX, final int oMaxZ)
        {
            return oMinX <= maxX && oMaxX >= minX && oMinZ <= maxZ && oMaxZ >= minZ;
        }
    }

    private SiteSelector()
    {
    }

    private record Prep(List<Footprint> occupied, List<BlockPos> staffedBuilders)
    {
    }

    private static final int FIELD_MARGIN = 7;

    private static Prep prep(final IColony colony, final Collection<Footprint> reserved)
    {
        final List<Footprint> occupied = new ArrayList<>(reserved);
        final List<BlockPos> staffedBuilders = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            occupied.add(boxOf(building));
            if (building instanceof BuildingBuilder && !building.getAllAssignedCitizen().isEmpty())
            {
                staffedBuilders.add(building.getPosition());
            }
        }

        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext -> true))
        {
            final BlockPos field = extension.getPosition();
            occupied.add(new Footprint(field.getX() - FIELD_MARGIN, field.getZ() - FIELD_MARGIN,
              field.getX() + FIELD_MARGIN, field.getZ() + FIELD_MARGIN));
        }

        occupied.addAll(ProtectedZones.footprintsFor(colony));
        return new Prep(occupied, staffedBuilders);
    }

    static String plainName(final IBuilding building)
    {
        final String name = building.getBuildingDisplayName();
        final int cut = name.lastIndexOf('.');
        return name.startsWith("com.") && name.contains(".building.") && cut >= 0 ? name.substring(cut + 1) : name;
    }

    static Footprint boxOf(final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        int minX = corners.getA().getX();
        int minZ = corners.getA().getZ();
        int maxX = corners.getB().getX();
        int maxZ = corners.getB().getZ();
        if (maxX - minX < 2 && maxZ - minZ < 2)
        {
            final int guess = building.getBuildingType().getBuildingBlock() == com.minecolonies.api.blocks.ModBlocks.blockHutTownHall ? 20 : 8;
            final BlockPos pos = building.getPosition();
            minX = pos.getX() - guess;
            minZ = pos.getZ() - guess;
            maxX = pos.getX() + guess;
            maxZ = pos.getZ() + guess;
        }
        return new Footprint(minX, minZ, maxX, maxZ);
    }

    static String occupantOverlapping(final IColony colony, final Footprint box)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Footprint bb = boxOf(building);
            if (box.intersects(bb.minX(), bb.minZ(), bb.maxX(), bb.maxZ()))
            {
                final BlockPos p = building.getPosition();

                return "the " + Component.translatable(building.getBuildingDisplayName()).getString() + " at " + p.getX() + ", " + p.getY() + ", " + p.getZ();
            }
        }
        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext -> true))
        {
            final BlockPos f = extension.getPosition();
            if (box.intersects(f.getX() - FIELD_MARGIN, f.getZ() - FIELD_MARGIN, f.getX() + FIELD_MARGIN, f.getZ() + FIELD_MARGIN))
            {

                return "a field at " + f.getX() + ", " + f.getY() + ", " + f.getZ();
            }
        }
        return null;
    }

    static List<Footprint> occupiedGround(final IColony colony)
    {
        final List<Footprint> ground = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            ground.add(boxOf(building));
        }
        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext -> true))
        {
            final BlockPos f = extension.getPosition();
            ground.add(new Footprint(f.getX() - FIELD_MARGIN, f.getZ() - FIELD_MARGIN, f.getX() + FIELD_MARGIN, f.getZ() + FIELD_MARGIN));
        }
        return ground;
    }

    static List<Footprint> foreignClaims(final ServerLevel level, final IColony colony, final int minX, final int minZ, final int maxX, final int maxZ)
    {
        final List<Footprint> claims = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++)
        {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++)
            {
                if (!WorldUtil.isChunkLoaded(level, chunkX, chunkZ))
                {
                    continue;
                }
                final IColony owner = IColonyManager.getInstance().getColonyByPosFromWorld(level, new BlockPos(chunkX << 4, 0, chunkZ << 4));
                if (owner != null && owner != colony)
                {
                    claims.add(new Footprint(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15));
                }
            }
        }
        return claims;
    }

    static Direction facingToward(final BlockPos center, final int x, final int z)
    {
        final int dx = center.getX() - x;
        final int dz = center.getZ() - z;
        if (dx == 0 && dz == 0)
        {
            return Direction.NORTH;
        }
        return Math.abs(dx) > Math.abs(dz)
                 ? (dx > 0 ? Direction.EAST : Direction.WEST)
                 : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    static Result findSite(
      final ServerLevel level,
      final IColony colony,
      final Map<Direction, Placement> options,
      final Collection<Footprint> reserved,
      final Collection<Footprint> doorsteps,
      final List<BlockPos> spreadFrom,
      final List<BlockPos> coverFrom,
      final boolean wooded,
      final BlockPos frontier,
      final int searchRadius,
      final boolean keystone,
      final boolean rangeExempt,
      final boolean fisher,
      final boolean farm,
      final Set<Long> roadBand)
    {
        final BlockPos center = colony.getCenter();
        final int padding = AutopilotConfig.get(colony, AutopilotConfig.SITE_PADDING_BLOCKS);
        final int maxVariance = AutopilotConfig.get(colony, AutopilotConfig.MAX_SURFACE_VARIANCE);
        final int maxRadius = searchRadius;

        final Prep prep = prep(colony, reserved);
        final List<Footprint> occupied = prep.occupied();
        final List<BlockPos> staffedBuilders = prep.staffedBuilders();

        final List<Footprint> clearance = new ArrayList<>(occupied);
        clearance.addAll(doorsteps);

        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        final Footprint hallBox = hall != null ? boxOf(hall) : null;

        final int hallTop = yardTop(level, ColonyGrounds.get(level), colony.getID(), center, hallBox);

        final int villageY = hallTop + 1;

        final RoadRouter.Survey siteSurvey = new RoadRouter.Survey(RoadRouter.SITE_POOL);

        final boolean scored = !spreadFrom.isEmpty() || !coverFrom.isEmpty() || wooded || frontier != null;
        final int maxElevation = AutopilotConfig.get(colony, AutopilotConfig.MAX_ELEVATION_DELTA);
        final Map<Long, List<int[]>> trunkGrid = wooded ? binTrunks(scanTrunks(level, center, maxRadius + TREE_REACH)) : Map.of();
        final Map<Long, Integer> heights = new HashMap<>();

        final Set<Long> frontierRing = AutopilotConfig.live(colony, AutopilotConfig.FRONTIER_TICKETS) ? new HashSet<>() : null;

        Result offLevelFallback = null;
        Result bestScored = null;
        long bestScore = Long.MIN_VALUE;
        Result bestScoredOffLevel = null;
        long bestScoreOffLevel = Long.MIN_VALUE;

        Result sideways = null;
        int sidewaysRadius = 0;

        Result secondChoice = null;
        Result bestFacing = null;
        long bestFacingScore = Long.MIN_VALUE;
        for (int radius = MIN_RADIUS; radius <= maxRadius; radius += 4)
        {
            if (sideways != null && radius > sidewaysRadius + FACING_WINDOW)
            {
                return sideways;
            }
            for (final int[] xz : squareRing(radius))
            {
                final int x = center.getX() + xz[0];
                final int z = center.getZ() + xz[1];

                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    if (frontierRing != null)
                    {
                        noteFrontier(level, x >> 4, z >> 4, frontierRing);
                    }
                    continue;
                }
                final BlockPos candidate = new BlockPos(x, groundHeight(level, x, z, heights), z);

                final boolean surelyOffLevel = Math.abs(candidate.getY() - villageY) > maxElevation + maxVariance;
                if (surelyOffLevel && keystone)
                {
                    continue;
                }
                if (surelyOffLevel && (scored ? bestScored != null : (offLevelFallback != null || sideways != null || secondChoice != null)))
                {
                    continue;
                }

                if (IColonyManager.getInstance().getColonyByPosFromWorld(level, candidate) != colony)
                {
                    continue;
                }

                final Direction preferred = facingToward(center, x, z);

                Result result = null;

                final boolean[] routed = {false};
                for (final Direction facing : sideways != null ? new Direction[] {preferred}
                                                : new Direction[] {preferred, preferred.getClockWise(), preferred.getCounterClockWise(), preferred.getOpposite()})
                {
                    final Placement placement = options.get(facing);
                    if (placement == null
                          || !isAcceptable(level, colony, candidate, placement, facing, padding, maxVariance, staffedBuilders, clearance, rangeExempt, heights))
                    {
                        continue;
                    }

                    final int surface = plotSurface(level, candidate, placement, heights);
                    if (Math.abs(surface - candidate.getY()) > maxVariance)
                    {
                        continue;
                    }
                    final Result fit = new Result(new BlockPos(x, surface, z), facing);

                    if (Math.abs(surface - villageY) > maxElevation
                          && (keystone || (scored ? bestScored != null : (offLevelFallback != null || sideways != null || secondChoice != null))))
                    {
                        continue;
                    }

                    if (localProminence(level, candidate, placement, surface, heights) > MAX_PROMINENCE)
                    {
                        continue;
                    }

                    if (fisher && !pondFits(level, colony, candidate, placement, facing, surface, clearance, roadBand, null, heights))
                    {
                        continue;
                    }
                    if (approachWalkable(level, colony, colony.getCenter(), hallTop, hallBox, fit, placement, occupied, heights, siteSurvey, routed))
                    {
                        result = fit;
                        break;
                    }
                }
                if (result == null)
                {
                    continue;
                }
                final boolean offLevel = Math.abs(result.anchor().getY() - villageY) > maxElevation;
                if (!scored)
                {
                    if (!offLevel)
                    {

                        if ((fisher && FishingPond.findTrustedWater(level, result.anchor()) == null)
                              || (farm && !fieldReady(level, colony, result, options.get(result.facing()), occupied, roadBand, heights)))
                        {
                            if (secondChoice == null)
                            {
                                secondChoice = result;
                            }
                            continue;
                        }
                        if (result.facing() == preferred)
                        {
                            return result;
                        }
                        if (sideways == null)
                        {
                            sideways = result;
                            sidewaysRadius = radius;
                        }
                        continue;
                    }
                    if (offLevelFallback == null)
                    {
                        offLevelFallback = result;
                    }
                    continue;
                }

                final long score;
                if (wooded)
                {
                    score = treeScore(trunkGrid, candidate);
                }
                else if (frontier != null)
                {
                    score = frontierScore(level, frontier, candidate, options.get(result.facing()), result.anchor().getY(), heights);
                }
                else if (coverFrom.isEmpty())
                {
                    score = spreadScore(level, spreadFrom, candidate, center, options.get(result.facing()), result.anchor().getY(), heights);
                }
                else
                {
                    score = coverScore(coverFrom, candidate);
                }
                if (offLevel)
                {
                    if (score > bestScoreOffLevel)
                    {
                        bestScoreOffLevel = score;
                        bestScoredOffLevel = result;
                    }
                }
                else
                {
                    if (score > bestScore)
                    {
                        bestScore = score;
                        bestScored = result;
                    }
                    if (result.facing() == preferred && score > bestFacingScore
                          && (frontier == null || result.anchor().distSqr(frontier) <= BUILDER_RANGE_SQ))
                    {
                        bestFacingScore = score;
                        bestFacing = result;
                    }
                }
            }
        }
        if (sideways != null)
        {
            return sideways;
        }
        if (secondChoice != null)
        {
            return secondChoice;
        }
        if (scored && bestScored != null)
        {
            final long tolerance = wooded ? Math.max(0, bestScore / 10) : !coverFrom.isEmpty() && frontier == null ? 0 : SCORE_FACING_TOLERANCE;
            return bestFacing != null && bestScore - bestFacingScore <= tolerance ? bestFacing : bestScored;
        }
        final Result fallback = scored ? bestScoredOffLevel : offLevelFallback;
        if (fallback != null)
        {
            ColonyAutopilot.LOGGER.info("[{}] no plot within {} blocks of town-hall level — accepting off-level terrain",
              colony.getName(), maxElevation);
        }
        else if (frontierRing != null && !frontierRing.isEmpty())
        {

            ChunkKeeper.requestFrontier(level, frontierRing);
        }
        return fallback;
    }

    private static long spreadScore(final ServerLevel level, final List<BlockPos> siblings, final BlockPos candidate, final BlockPos center,
      final Placement placement, final int baseY, final Map<Long, Integer> heights)
    {
        final double siblingDist = Math.sqrt(distToNearest(siblings, candidate));
        final long dx = center.getX() - candidate.getX();
        final long dz = center.getZ() - candidate.getZ();
        final double centerDist = Math.sqrt(dx * dx + dz * dz);
        return Math.round(10.0 * (siblingDist - 2.0 * Math.abs(centerDist - SPREAD_RING)
                                    - 3.0 * localProminence(level, candidate, placement, baseY, heights)));
    }

    private static long frontierScore(final ServerLevel level, final BlockPos frontier, final BlockPos candidate,
      final Placement placement, final int baseY, final Map<Long, Integer> heights)
    {
        final long dx = frontier.getX() - candidate.getX();
        final long dz = frontier.getZ() - candidate.getZ();
        return -Math.round(10.0 * (Math.sqrt(dx * dx + dz * dz) + 3.0 * localProminence(level, candidate, placement, baseY, heights)));
    }

    private static final int[][] PROMINENCE_RING = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {6, -6}, {-6, 6}, {-6, -6}};

    private static final int PROMINENCE_CLEAR = 4;

    private static int localProminence(final ServerLevel level, final BlockPos candidate, final Placement placement, final int baseY,
      final Map<Long, Integer> heights)
    {
        final int footMinX = candidate.getX() - placement.offsetX();
        final int footMinZ = candidate.getZ() - placement.offsetZ();
        final int footMaxX = footMinX + placement.sizeX() - 1;
        final int footMaxZ = footMinZ + placement.sizeZ() - 1;
        final int[] samples = new int[PROMINENCE_RING.length];
        int sampled = 0;
        for (final int[] d : PROMINENCE_RING)
        {
            int sx = candidate.getX() + d[0];
            int sz = candidate.getZ() + d[1];
            if (sx >= footMinX && sx <= footMaxX && sz >= footMinZ && sz <= footMaxZ)
            {

                final int stepX = Integer.signum(d[0]);
                final int stepZ = Integer.signum(d[1]);
                final int alongX = stepX > 0 ? footMaxX + PROMINENCE_CLEAR - sx : stepX < 0 ? sx - footMinX + PROMINENCE_CLEAR : Integer.MAX_VALUE;
                final int alongZ = stepZ > 0 ? footMaxZ + PROMINENCE_CLEAR - sz : stepZ < 0 ? sz - footMinZ + PROMINENCE_CLEAR : Integer.MAX_VALUE;
                final int slide = Math.min(alongX, alongZ);
                sx += stepX * slide;
                sz += stepZ * slide;
            }
            if (!WorldUtil.isChunkLoaded(level, sx >> 4, sz >> 4))
            {
                continue;
            }
            samples[sampled++] = groundHeight(level, sx, sz, heights);
        }
        if (sampled == 0)
        {
            return 0;
        }
        Arrays.sort(samples, 0, sampled);
        return Math.max(0, baseY - samples[sampled / 2]);
    }

    static final int TREE_REACH = 32;

    private static List<int[]> scanTrunks(final ServerLevel level, final BlockPos center, final int reach)
    {
        final List<int[]> trunks = new ArrayList<>();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - reach; x <= center.getX() + reach; x += 2)
        {
            for (int z = center.getZ() - reach; z <= center.getZ() + reach; z += 2)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (level.getBlockState(probe.set(x, top, z)).is(BlockTags.LOGS))
                {
                    trunks.add(new int[] {x, z});
                }
            }
        }
        return trunks;
    }

    private static Map<Long, List<int[]>> binTrunks(final List<int[]> trunks)
    {
        final Map<Long, List<int[]>> grid = new HashMap<>();
        for (final int[] trunk : trunks)
        {
            grid.computeIfAbsent(cellKey(Math.floorDiv(trunk[0], TREE_REACH), Math.floorDiv(trunk[1], TREE_REACH)),
              k -> new ArrayList<>()).add(trunk);
        }
        return grid;
    }

    private static long cellKey(final int cellX, final int cellZ)
    {
        return (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private static long treeScore(final Map<Long, List<int[]>> trunkGrid, final BlockPos candidate)
    {
        final int cellX = Math.floorDiv(candidate.getX(), TREE_REACH);
        final int cellZ = Math.floorDiv(candidate.getZ(), TREE_REACH);
        long count = 0;
        for (int gx = cellX - 1; gx <= cellX + 1; gx++)
        {
            for (int gz = cellZ - 1; gz <= cellZ + 1; gz++)
            {
                final List<int[]> cell = trunkGrid.get(cellKey(gx, gz));
                if (cell == null)
                {
                    continue;
                }
                for (final int[] trunk : cell)
                {
                    final long dx = trunk[0] - candidate.getX();
                    final long dz = trunk[1] - candidate.getZ();
                    if (dx * dx + dz * dz <= (long) TREE_REACH * TREE_REACH)
                    {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static long distToNearest(final List<BlockPos> anchors, final BlockPos candidate)
    {
        long best = Long.MAX_VALUE;
        for (final BlockPos anchor : anchors)
        {
            final long dx = anchor.getX() - candidate.getX();
            final long dz = anchor.getZ() - candidate.getZ();
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static long coverScore(final List<BlockPos> uncovered, final BlockPos candidate)
    {
        long covered = 0;
        for (final BlockPos pos : uncovered)
        {
            if (withinGuardClaim(candidate, pos))
            {
                covered++;
            }
        }
        return covered;
    }

    static boolean withinGuardClaim(final BlockPos post, final BlockPos pos)
    {
        return Math.abs((pos.getX() >> 4) - (post.getX() >> 4)) <= 2
                 && Math.abs((pos.getZ() >> 4) - (post.getZ() >> 4)) <= 2;
    }

    private static boolean isAcceptable(
      final ServerLevel level,
      final IColony colony,
      final BlockPos candidate,
      final Placement placement,
      final Direction facing,
      final int padding,
      final int maxVariance,
      final List<BlockPos> staffedBuilders,
      final List<Footprint> occupied,
      final boolean rangeExempt,
      final Map<Long, Integer> heights)
    {
        if (!isPlaceableIgnoringTerrain(level, colony, candidate, placement, facing, padding, staffedBuilders, occupied, rangeExempt))
        {
            return false;
        }

        final int footMinX = candidate.getX() - placement.offsetX();
        final int footMinZ = candidate.getZ() - placement.offsetZ();
        final int footMaxX = footMinX + placement.sizeX() - 1;
        final int footMaxZ = footMinZ + placement.sizeZ() - 1;

        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (final int sx : sampleCoords(footMinX, footMaxX, Math.max(2, placement.sizeX() / 3)))
        {
            for (final int sz : sampleCoords(footMinZ, footMaxZ, Math.max(2, placement.sizeZ() / 3)))
            {

                if (!WorldUtil.isChunkLoaded(level, sx >> 4, sz >> 4))
                {
                    return false;
                }
                final int h = groundHeight(level, sx, sz, heights);
                minH = Math.min(minH, h);
                maxH = Math.max(maxH, h);
                if (maxH - minH > maxVariance)
                {
                    return false;
                }
                if (!level.getBlockState(new BlockPos(sx, h - 1, sz)).getFluidState().isEmpty())
                {
                    return false;
                }
            }
        }

        return true;
    }

    private static final int FILL_REACH = 3;

    private static final int DOORSTEP_RUN = 4;

    static final int DOORSTEP_STRIP = DOORSTEP_RUN + 4;

    private static boolean approachWalkable(final ServerLevel level, final IColony colony, final BlockPos center, final int villageTop,
      final Footprint hallBox, final Result fit, final Placement placement, final List<Footprint> occupied,
      final Map<Long, Integer> heights, final RoadRouter.Survey survey, final boolean[] routed)
    {

        final Direction facing = fit.facing();
        final BlockPos anchor = fit.anchor();
        final int minX = anchor.getX() - placement.offsetX(), minZ = anchor.getZ() - placement.offsetZ();
        final Footprint plot = new Footprint(minX, minZ, minX + placement.sizeX() - 1, minZ + placement.sizeZ() - 1);
        final BlockPos door = anchor.offset(placement.doorX(), 0, placement.doorZ());
        final int toEdge = switch (facing)
        {
            case NORTH -> door.getZ() - minZ;
            case SOUTH -> plot.maxZ() - door.getZ();
            case WEST -> door.getX() - minX;
            default -> plot.maxX() - door.getX();
        };
        final BlockPos.MutableBlockPos skyProbe = new BlockPos.MutableBlockPos();
        int grade = anchor.getY();
        BlockPos elbow = door;
        for (int out = 1; out <= toEdge + 1 + DOORSTEP_RUN; out++)
        {
            elbow = door.relative(facing, out);
            if (out <= toEdge || !WorldUtil.isChunkLoaded(level, elbow.getX() >> 4, elbow.getZ() >> 4) || insideAny(occupied, elbow.getX(), elbow.getZ()))
            {
                continue;
            }
            final int h = groundHeight(level, elbow.getX(), elbow.getZ(), heights);
            grade = Mth.clamp(h, grade - 1, grade + 1);
            if (h - grade > GRADE_REACH || grade - h > FILL_REACH)
            {
                return false;
            }
        }
        final BlockPos from = new BlockPos(elbow.getX(), grade, elbow.getZ());
        final int dx = center.getX() - from.getX();
        final int dz = center.getZ() - from.getZ();
        final int steps = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));

        final int skyCeiling = Math.max(center.getY(), from.getY()) + 8;
        for (int i = 1; i < steps; i++)
        {
            final int sx = from.getX() + Math.round((float) dx * i / steps);
            final int sz = from.getZ() + Math.round((float) dz * i / steps);
            if (!WorldUtil.isChunkLoaded(level, sx >> 4, sz >> 4) || insideAny(occupied, sx, sz))
            {
                continue;
            }
            final int h = groundHeight(level, sx, sz, heights);
            if (h - 1 > skyCeiling && !solidBeneath(level, sx, sz, h - 1, skyProbe))
            {
                continue;
            }
            grade = Mth.clamp(h, grade - 1, grade + 1);
            if (h - grade > GRADE_REACH || grade - h > FILL_REACH)
            {
                if (routed[0])
                {
                    return false;
                }
                routed[0] = true;
                final List<Footprint> walls = new ArrayList<>(occupied);
                walls.add(plot);
                final List<int[]> route = RoadRouter.route(level, colony, from, center, villageTop, hallBox, VillagePaths.ARRIVAL, walls, Map.of(), null,
                  survey, RoadRouter.SITE_BUDGET);
                return route != null;
            }
        }
        return true;
    }

    private static boolean insideAny(final List<Footprint> occupied, final int x, final int z)
    {
        for (final Footprint box : occupied)
        {
            if (box.intersects(x, z, x, z))
            {
                return true;
            }
        }
        return false;
    }

    static final Set<net.minecraft.world.level.block.Block> STANDING_CLUTTER = Set.of(
      net.minecraft.world.level.block.Blocks.BAMBOO,
      net.minecraft.world.level.block.Blocks.CACTUS,
      net.minecraft.world.level.block.Blocks.MELON,
      net.minecraft.world.level.block.Blocks.PUMPKIN,
      net.minecraft.world.level.block.Blocks.RED_MUSHROOM_BLOCK,
      net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM_BLOCK,
      net.minecraft.world.level.block.Blocks.MUSHROOM_STEM);

    static int groundHeight(final ServerLevel level, final int x, final int z)
    {
        return groundHeightBelow(level, x, z, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
    }

    static int groundHeightBelow(final ServerLevel level, final int x, final int z, final int from)
    {
        int y = from;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        while (y > level.getMinBuildHeight() + 1)
        {
            final var below = level.getBlockState(probe.set(x, y - 1, z));
            if (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES)
                  || STANDING_CLUTTER.contains(below.getBlock())
                  || (below.getFluidState().isEmpty()
                        && !below.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                        && (below.canBeReplaced() || !below.blocksMotion())))
            {
                y--;
                continue;
            }
            break;
        }
        return y;
    }

    private static int groundHeight(final ServerLevel level, final int x, final int z, final Map<Long, Integer> cache)
    {
        final long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
        final Integer hit = cache.get(key);
        if (hit != null)
        {
            return hit;
        }
        final int h = groundHeight(level, x, z);
        cache.put(key, h);
        return h;
    }

    static final int SKY_PROBE_DEPTH = 8;

    static boolean solidBeneath(final ServerLevel level, final int x, final int z, final int surfaceTop, final BlockPos.MutableBlockPos pos)
    {
        for (int y = surfaceTop - 1; y >= surfaceTop - SKY_PROBE_DEPTH; y--)
        {
            final var state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir() || state.canBeReplaced())
            {
                return false;
            }
        }
        return true;
    }

    static boolean solidBeneathOrCaveRoof(final ServerLevel level, final int x, final int z, final int surfaceTop, final BlockPos.MutableBlockPos pos)
    {
        int y = surfaceTop - 1;
        int roof = 0;
        while (y > level.getMinBuildHeight() && roof < SKY_PROBE_DEPTH && probeSolid(level, x, y, z, pos))
        {
            roof++;
            y--;
        }
        if (roof >= SKY_PROBE_DEPTH)
        {
            return true;
        }
        int gap = 0;
        while (y > level.getMinBuildHeight() && gap <= SKY_PROBE_DEPTH && !probeSolid(level, x, y, z, pos))
        {
            gap++;
            y--;
        }
        if (gap > SKY_PROBE_DEPTH)
        {
            return false;
        }
        int floor = 0;
        while (y > level.getMinBuildHeight() && floor < SKY_PROBE_DEPTH && probeSolid(level, x, y, z, pos))
        {
            floor++;
            y--;
        }
        return floor >= SKY_PROBE_DEPTH;
    }

    private static boolean probeSolid(final ServerLevel level, final int x, final int y, final int z, final BlockPos.MutableBlockPos pos)
    {
        final var state = level.getBlockState(pos.set(x, y, z));
        return !state.isAir() && !state.canBeReplaced();
    }

    static TerraformSite findTerraformSite(final ServerLevel level, final IColony colony, final Map<Direction, Placement> options, final Collection<Footprint> reserved,
      final Collection<Footprint> doorsteps, final List<BlockPos> spreadFrom, final BlockPos frontier, final int searchRadius, final boolean rangeExempt,
      final boolean forced, final boolean fisher, final Set<Long> roadBand)
    {
        final BlockPos center = colony.getCenter();
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        final Footprint hallBox = hall != null ? boxOf(hall) : null;

        final int villageY = yardTop(level, ColonyGrounds.get(level), colony.getID(), center, hallBox) + 1;
        final int padding = AutopilotConfig.get(colony, AutopilotConfig.SITE_PADDING_BLOCKS);

        final int effectivePad = forced ? Math.max(padding, FORCED_LEVEL_PAD) : Math.max(padding, 3);
        final int maxRadius = searchRadius;
        final int maxElevation = AutopilotConfig.get(colony, AutopilotConfig.MAX_ELEVATION_DELTA);
        final Prep prep = prep(colony, reserved);

        final List<Footprint> clearance = new ArrayList<>(prep.occupied());
        clearance.addAll(doorsteps);
        final Map<Long, Integer> heights = new HashMap<>();

        TerraformSite best = null;
        int bestCost = Integer.MAX_VALUE;
        int examined = 0;

        for (int radius = MIN_RADIUS; radius <= maxRadius && examined < 40; radius += 4)
        {
            for (final int[] xz : squareRing(radius))
            {
                final int x = center.getX() + xz[0];
                final int z = center.getZ() + xz[1];
                final Direction facing = facingToward(center, x, z);
                final Placement placement = options.get(facing);
                if (placement == null || !WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                final BlockPos candidate = new BlockPos(x, groundHeight(level, x, z, heights), z);
                if (!isPlaceableIgnoringTerrain(level, colony, candidate, placement, facing, effectivePad, prep.staffedBuilders(), clearance, rangeExempt)
                      || (!forced && localProminence(level, candidate, placement, candidate.getY(), heights) > MAX_PROMINENCE))
                {
                    continue;
                }

                if (frontier != null && candidate.distSqr(frontier) > BUILDER_RANGE_SQ)
                {
                    continue;
                }

                final int footMinX = candidate.getX() - placement.offsetX();
                final int footMinZ = candidate.getZ() - placement.offsetZ();
                final int footMaxX = footMinX + placement.sizeX() - 1;
                final int footMaxZ = footMinZ + placement.sizeZ() - 1;

                final List<Integer> groundHeights = new ArrayList<>();
                int wet = 0;
                boolean fullyLoaded = true;
                for (final int sx : sampleCoords(footMinX, footMaxX, Math.max(2, placement.sizeX() / 3)))
                {
                    for (final int sz : sampleCoords(footMinZ, footMaxZ, Math.max(2, placement.sizeZ() / 3)))
                    {
                        if (!WorldUtil.isChunkLoaded(level, sx >> 4, sz >> 4))
                        {
                            fullyLoaded = false;
                            break;
                        }
                        final int ground = groundHeight(level, sx, sz, heights) - 1;
                        groundHeights.add(ground);
                        if (!level.getBlockState(new BlockPos(sx, ground, sz)).getFluidState().isEmpty())
                        {
                            wet++;
                        }
                    }
                    if (!fullyLoaded)
                    {
                        break;
                    }
                }
                if (!fullyLoaded || groundHeights.isEmpty())
                {
                    continue;
                }

                int waterTop = Integer.MIN_VALUE;
                for (final int sx : sampleCoords(footMinX - effectivePad, footMaxX + effectivePad, Math.max(2, placement.sizeX() / 3)))
                {
                    for (final int sz : sampleCoords(footMinZ - effectivePad, footMaxZ + effectivePad, Math.max(2, placement.sizeZ() / 3)))
                    {
                        if (!WorldUtil.isChunkLoaded(level, sx >> 4, sz >> 4))
                        {
                            fullyLoaded = false;
                            break;
                        }
                        final int ground = groundHeight(level, sx, sz, heights) - 1;
                        if (!level.getBlockState(new BlockPos(sx, ground, sz)).getFluidState().isEmpty())
                        {
                            waterTop = Math.max(waterTop, ground);
                        }
                    }
                    if (!fullyLoaded)
                    {
                        break;
                    }
                }
                final int ringMinX = footMinX - effectivePad - 1, ringMaxX = footMaxX + effectivePad + 1;
                final int ringMinZ = footMinZ - effectivePad - 1, ringMaxZ = footMaxZ + effectivePad + 1;
                for (int rx = ringMinX; fullyLoaded && rx <= ringMaxX; rx++)
                {
                    for (final int rz : new int[] {ringMinZ, ringMaxZ})
                    {
                        final Integer top = waterTopAt(level, rx, rz, heights);
                        if (top == null)
                        {
                            fullyLoaded = false;
                            break;
                        }
                        waterTop = Math.max(waterTop, top);
                    }
                }
                for (int rz = ringMinZ + 1; fullyLoaded && rz < ringMaxZ; rz++)
                {
                    for (final int rx : new int[] {ringMinX, ringMaxX})
                    {
                        final Integer top = waterTopAt(level, rx, rz, heights);
                        if (top == null)
                        {
                            fullyLoaded = false;
                            break;
                        }
                        waterTop = Math.max(waterTop, top);
                    }
                }
                if (!fullyLoaded)
                {
                    continue;
                }

                groundHeights.sort(null);
                final int median = groundHeights.get(groundHeights.size() / 2);

                final int upperRoom = forced ? Math.max(0, maxElevation - 1) : maxElevation;

                int targetY = Mth.clamp(median, villageY - 1 - maxElevation, villageY - 1 + upperRoom);

                if (waterTop > targetY)
                {
                    if (waterTop > villageY - 1 + upperRoom)
                    {
                        continue;
                    }
                    targetY = waterTop;
                }

                int cost = wet * 8;
                boolean tooSteep = false;
                for (final int ground : groundHeights)
                {
                    final int delta = Math.abs(ground - targetY);
                    if (!forced && delta > 8)
                    {
                        tooSteep = true;
                        break;
                    }
                    cost += delta;
                }

                if (!spreadFrom.isEmpty())
                {
                    cost += 4 * Math.max(0, SPREAD_RING - (int) Math.sqrt(distToNearest(spreadFrom, candidate)));
                }
                if (tooSteep || cost >= bestCost)
                {
                    continue;
                }
                final Footprint plot = new Footprint(footMinX - effectivePad, footMinZ - effectivePad, footMaxX + effectivePad, footMaxZ + effectivePad);
                if (fisher && !forced && !pondFits(level, colony, candidate, placement, facing, targetY + 1, clearance, roadBand, plot, heights))
                {
                    continue;
                }

                examined++;

                final List<int[]> ramp = rampCorridor(level, center, villageY, hallBox,
                  new BlockPos(candidate.getX(), targetY + 1, candidate.getZ()), plot, prep.occupied(), forced, heights);
                if (ramp == null)
                {
                    continue;
                }
                cost += ramp.size() * 2;

                if (cost < bestCost)
                {
                    bestCost = cost;
                    best = new TerraformSite(new Result(new BlockPos(candidate.getX(), targetY + 1, candidate.getZ()), facing),
                      plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ(), targetY, ramp, forced);
                }
            }
        }
        return best;
    }

    private static boolean pondFits(final ServerLevel level, final IColony colony, final BlockPos candidate, final Placement placement, final Direction facing,
      final int surface, final List<Footprint> obstacles, final Set<Long> roadBand, final Footprint pad, final Map<Long, Integer> heights)
    {
        final int minX = candidate.getX() - placement.offsetX(), minZ = candidate.getZ() - placement.offsetZ();
        final Footprint plot = new Footprint(minX, minZ, minX + placement.sizeX() - 1, minZ + placement.sizeZ() - 1);
        final int groundY = surface - 1;
        if (FishingPond.onCrest(level, plot, groundY, (x, z) -> groundHeight(level, x, z, heights) - 1))
        {
            return false;
        }
        return !AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED)
                 || FishingPond.hasRoom(level, colony, new BlockPos(candidate.getX(), surface, candidate.getZ()), plot, facing, obstacles, roadBand,
                      (x, z) -> gradedTop(level, x, z, plot, groundY, pad, heights));
    }

    private static boolean fieldReady(final ServerLevel level, final IColony colony, final Result fit, final Placement placement,
      final List<Footprint> occupied, final Set<Long> roadBand, final Map<Long, Integer> heights)
    {
        final int minX = fit.anchor().getX() - placement.offsetX(), minZ = fit.anchor().getZ() - placement.offsetZ();
        final Footprint plot = new Footprint(minX, minZ, minX + placement.sizeX() - 1, minZ + placement.sizeZ() - 1);
        if (FishingPond.onCrest(level, plot, fit.anchor().getY() - 1, (x, z) -> groundHeight(level, x, z, heights) - 1))
        {
            return false;
        }
        final List<Footprint> obstacles = new ArrayList<>(occupied);
        obstacles.add(plot);
        return FarmFields.naturalFieldPlot(level, colony, fit.anchor(), obstacles, roadBand) != null;
    }

    private static int gradedTop(final ServerLevel level, final int x, final int z, final Footprint plot, final int groundY, final Footprint pad,
      final Map<Long, Integer> heights)
    {
        if (pad != null && pad.intersects(x, z, x, z))
        {
            return groundY;
        }
        final int top = groundHeight(level, x, z, heights) - 1;
        final int ring = Math.max(Math.max(plot.minX() - x, x - plot.maxX()), Math.max(plot.minZ() - z, z - plot.maxZ()));
        return ring <= VillageGrounds.SKIRT_RINGS ? Mth.clamp(top, groundY - ring, groundY + ring) : top;
    }

    private static Integer waterTopAt(final ServerLevel level, final int x, final int z, final Map<Long, Integer> heights)
    {
        if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
        {
            return null;
        }
        final int ground = groundHeight(level, x, z, heights) - 1;
        return level.getBlockState(new BlockPos(x, ground, z)).getFluidState().isEmpty() ? Integer.MIN_VALUE : ground;
    }

    private static List<int[]> rampCorridor(final ServerLevel level, final BlockPos center, final int villageY, final Footprint hallBox,
      final BlockPos anchor, final Footprint plot, final List<Footprint> occupied, final boolean forced, final Map<Long, Integer> heights)
    {
        final int dx = center.getX() - anchor.getX();
        final int dz = center.getZ() - anchor.getZ();
        final int steps = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        final boolean xDominant = Math.abs(dx) > Math.abs(dz);

        final int rampCut = forced ? FORCED_MAX_CUT : RAMP_CUT;
        final int rampMax = forced ? FORCED_RAMP_MAX : RAMP_MAX;
        final List<int[]> ramp = new ArrayList<>();
        int tread = anchor.getY();
        int treadColumns = 0;
        int landed = 0;

        final int skyCeiling = center.getY() + 32;
        final BlockPos.MutableBlockPos skyProbe = new BlockPos.MutableBlockPos();

        final List<int[]> line = new ArrayList<>(steps);
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i < steps; i++)
        {
            final int sx = anchor.getX() + Math.round((float) dx * i / steps);
            final int sz = anchor.getZ() + Math.round((float) dz * i / steps);
            if (sx != lastX || sz != lastZ)
            {
                line.add(new int[] {sx, sz});
            }
            lastX = sx;
            lastZ = sz;
        }

        int boxAt = line.size();
        for (int c = 0; hallBox != null && c < line.size(); c++)
        {
            if (hallBox.intersects(line.get(c)[0], line.get(c)[1], line.get(c)[0], line.get(c)[1]))
            {
                boxAt = c;
                break;
            }
        }
        for (int c = 0; c < line.size(); c++)
        {
            final int sx = line.get(c)[0];
            final int sz = line.get(c)[1];
            if (hallBox != null && hallBox.intersects(sx, sz, sx, sz))
            {

                return Math.abs(tread - villageY) <= 1 || forced ? ramp : null;
            }
            if (plot.intersects(sx, sz, sx, sz)
                  || !WorldUtil.isChunkLoaded(level, sx >> 4, sz >> 4) || insideAny(occupied, sx, sz))
            {
                continue;
            }
            final int h = groundHeight(level, sx, sz, heights);

            if (h - 1 > skyCeiling && !solidBeneathOrCaveRoof(level, sx, sz, h - 1, skyProbe))
            {
                continue;
            }

            if (level.getBlockState(skyProbe.set(sx, h - 1, sz)).is(Blocks.DIRT_PATH))
            {
                if (Math.abs(h - tread) > 1)
                {
                    if (!forced)
                    {
                        return null;
                    }
                    tread = Mth.clamp(h, tread - 1, tread + 1);
                    landed = 0;
                    continue;
                }
                tread = h;
                if (++landed >= LAND_RUN)
                {
                    return ramp;
                }
                continue;
            }

            final boolean wet = !level.getFluidState(skyProbe.set(sx, h - 1, sz)).isEmpty();
            final int needed = villageY - tread;
            final boolean climbing = hallBox != null && boxAt - c <= Math.abs(needed) + LAND_RUN;
            if (climbing)
            {

                tread += Integer.signum(needed);
            }
            else
            {
                tread = Mth.clamp(h, wet ? tread : tread - 1, tread + 1);
            }

            if (!forced && wet && tread < h)
            {
                return null;
            }
            if (h - tread > rampCut)
            {
                return forced ? ramp : null;
            }

            final boolean leadsOn = Math.abs(tread - villageY) <= 1;
            if (!wet && Math.abs(h - tread) <= 1 && leadsOn)
            {
                if (++landed >= LAND_RUN)
                {
                    return ramp;
                }
            }
            else
            {
                landed = 0;
            }

            if ((wet || h != tread || climbing) && ++treadColumns > rampMax)
            {
                return forced ? ramp : null;
            }
            ramp.add(new int[] {sx, sz, tread});
            for (final int[] side : xDominant ? new int[][] {{sx, sz - 1}, {sx, sz + 1}} : new int[][] {{sx - 1, sz}, {sx + 1, sz}})
            {
                if (!plot.intersects(side[0], side[1], side[0], side[1]) && !insideAny(occupied, side[0], side[1]))
                {

                    final Integer sideWater = forced ? null : waterTopAt(level, side[0], side[1], heights);
                    if (sideWater != null && sideWater != Integer.MIN_VALUE && tread <= sideWater)
                    {
                        return null;
                    }
                    ramp.add(new int[] {side[0], side[1], tread});
                }
            }
        }
        return ramp;
    }

    private static boolean isPlaceableIgnoringTerrain(
      final ServerLevel level,
      final IColony colony,
      final BlockPos candidate,
      final Placement placement,
      final Direction facing,
      final int padding,
      final List<BlockPos> staffedBuilders,
      final List<Footprint> occupied,
      final boolean rangeExempt)
    {

        if (!rangeExempt && !staffedBuilders.isEmpty())
        {
            boolean inRange = false;
            for (final BlockPos builderPos : staffedBuilders)
            {
                if (builderPos.distSqr(candidate) <= BUILDER_RANGE_SQ)
                {
                    inRange = true;
                    break;
                }
            }
            if (!inRange)
            {
                return false;
            }
        }

        final int padMinX = candidate.getX() - placement.offsetX() - padding - (facing == Direction.WEST ? DOORSTEP_STRIP : 0);
        final int padMinZ = candidate.getZ() - placement.offsetZ() - padding - (facing == Direction.NORTH ? DOORSTEP_STRIP : 0);
        final int padMaxX = candidate.getX() - placement.offsetX() + placement.sizeX() - 1 + padding + (facing == Direction.EAST ? DOORSTEP_STRIP : 0);
        final int padMaxZ = candidate.getZ() - placement.offsetZ() + placement.sizeZ() - 1 + padding + (facing == Direction.SOUTH ? DOORSTEP_STRIP : 0);

        for (int cx = padMinX >> 4; cx <= padMaxX >> 4; cx++)
        {
            for (int cz = padMinZ >> 4; cz <= padMaxZ >> 4; cz++)
            {
                if (!WorldUtil.isChunkLoaded(level, cx, cz)
                      || IColonyManager.getInstance().getColonyByPosFromWorld(level, new BlockPos(cx << 4, candidate.getY(), cz << 4)) != colony)
                {
                    return false;
                }
            }
        }

        for (final Footprint box : occupied)
        {
            if (box.intersects(padMinX, padMinZ, padMaxX, padMaxZ))
            {
                return false;
            }
        }
        return true;
    }

    static int yardTop(final ServerLevel level, final ColonyGrounds grounds, final int colonyId, final BlockPos target, final Footprint box)
    {
        if (grounds.hasAnchorOffset(colonyId, target) || box == null)
        {
            return target.getY() - grounds.anchorOffset(colonyId, target, 1);
        }
        final int midX = (box.minX() + box.maxX()) / 2, midZ = (box.minZ() + box.maxZ()) / 2;
        final int[][] rim = {{midX, box.minZ() - 1}, {midX, box.maxZ() + 1}, {box.minX() - 1, midZ}, {box.maxX() + 1, midZ}};
        final List<Integer> tops = new ArrayList<>();
        for (final int[] at : rim)
        {
            if (WorldUtil.isChunkLoaded(level, at[0] >> 4, at[1] >> 4))
            {
                tops.add(VillagePaths.surfaceY(level, at[0], at[1]));
            }
        }
        if (tops.isEmpty())
        {
            return target.getY() - 1;
        }
        tops.sort(null);
        return tops.get(tops.size() / 2);
    }

    private static int plotSurface(final ServerLevel level, final BlockPos candidate, final Placement placement, final Map<Long, Integer> heights)
    {
        final int footMinX = candidate.getX() - placement.offsetX();
        final int footMinZ = candidate.getZ() - placement.offsetZ();
        final int footMaxX = footMinX + placement.sizeX() - 1;
        final int footMaxZ = footMinZ + placement.sizeZ() - 1;
        final List<Integer> surface = new ArrayList<>();
        for (final int sx : sampleCoords(footMinX, footMaxX, Math.max(2, placement.sizeX() / 3)))
        {
            for (final int sz : sampleCoords(footMinZ, footMaxZ, Math.max(2, placement.sizeZ() / 3)))
            {
                surface.add(groundHeight(level, sx, sz, heights));
            }
        }
        surface.add(groundHeight(level, candidate.getX(), candidate.getZ(), heights));
        surface.sort(null);
        return surface.get(surface.size() / 2);
    }

    private static List<Integer> sampleCoords(final int min, final int max, final int step)
    {
        final List<Integer> coords = new ArrayList<>();
        for (int c = min; c < max; c += step)
        {
            coords.add(c);
        }
        coords.add(max);
        return coords;
    }

    private static final int[][] FRONTIER_NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static void noteFrontier(final ServerLevel level, final int cx, final int cz, final Set<Long> ring)
    {
        if (ring.size() >= ChunkKeeper.FRONTIER_MAX_CHUNKS || ring.contains(ChunkPos.asLong(cx, cz)))
        {
            return;
        }
        for (final int[] d : FRONTIER_NEIGHBOURS)
        {
            if (WorldUtil.isChunkLoaded(level, cx + d[0], cz + d[1]))
            {
                ring.add(ChunkPos.asLong(cx, cz));
                return;
            }
        }
    }

    private static List<int[]> squareRing(final int radius)
    {
        final List<int[]> ring = new ArrayList<>();
        for (int x = -radius; x <= radius; x += 4)
        {
            ring.add(new int[] {x, -radius});
            ring.add(new int[] {x, radius});
        }
        for (int z = -radius + 4; z <= radius - 4; z += 4)
        {
            ring.add(new int[] {-radius, z});
            ring.add(new int[] {radius, z});
        }

        ring.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return ring;
    }
}
