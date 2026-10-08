// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.Pond;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.jobs.JobFisherman;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

public final class FishingPond
{

    private static final int TRUSTED_WATER_RADIUS = 24;

    private static final int TRUSTED_WATER_DEPTH = 4;

    private static final int THAW_REACH = 48;

    private static final int POND_SIZE = 7;

    private static final int GAP = 2;

    private static final int[][] PROBE_OFFSETS = {
      {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

    private static final Map<GlobalPos, Long> nextThawScan = new HashMap<>();

    private static final long THAW_BACKOFF_TICKS = 6000L;

    private static final Map<GlobalPos, String> reported = new HashMap<>();

    private FishingPond()
    {
    }

    static void clearThawScans()
    {
        nextThawScan.clear();
        trustedWater.clear();
        reported.clear();
    }

    private static boolean firstReport(final ServerLevel level, final BlockPos anchor, final String outcome)
    {
        return !outcome.equals(reported.put(GlobalPos.of(level.dimension(), anchor), outcome));
    }

    static void pruneDeadPonds(final ServerLevel level, final IColony colony, final IBuilding hut)
    {
        for (final ICitizenData citizen : hut.getAllAssignedCitizen())
        {
            if (!(citizen.getJob() instanceof JobFisherman fisher))
            {
                continue;
            }
            for (final Tuple<BlockPos, BlockPos> pond : fisher.getPonds())
            {
                final BlockPos water = pond.getA();
                final BlockPos stand = pond.getB();
                if (!WorldUtil.isChunkLoaded(level, water.getX() >> 4, water.getZ() >> 4)
                      || !WorldUtil.isChunkLoaded(level, stand.getX() >> 4, stand.getZ() >> 4))
                {
                    continue;
                }

                final BlockPos surface = level.getFluidState(water).isEmpty() ? water.below() : water;
                final boolean waterDead = !level.getFluidState(surface).isSource();
                final boolean shoreDead = !BlockUtils.isAnySolid(level.getBlockState(stand.below()))
                                            || level.getBlockState(stand).isSolid()
                                            || level.getBlockState(stand.above()).isSolid();
                if (!waterDead && !shoreDead)
                {
                    continue;
                }

                if (fisher.getWater() != null && water.equals(fisher.getWater().getA()))
                {
                    continue;
                }
                fisher.removeFromPonds(pond);
                ColonyAutopilot.LOGGER.info("[{}] pruned a dead pond at {} from the {}'s memory — {}",
                  colony.getName(), water.toShortString(), hut.getBuildingDisplayName(),
                  waterDead ? "its water is gone" : "its shore is buried");
            }
        }
    }

    private static BlockPos ownPond(final ServerLevel level, final IColony colony, final BlockPos ground)
    {
        for (final long packed : ColonyGrounds.get(level).ponds(colony.getID()))
        {
            final BlockPos middle = BlockPos.of(packed).offset(POND_SIZE / 2, 0, POND_SIZE / 2);
            if (onHomeTerrace(ground, middle)
                  && (!WorldUtil.isChunkLoaded(level, middle.getX() >> 4, middle.getZ() >> 4) || level.getFluidState(middle).isSource()))
            {
                return BlockPos.of(packed);
            }
        }
        return null;
    }

    static List<SiteSelector.Footprint> dugPondShields(final ServerLevel level, final IColony colony, final IBuilding hut)
    {
        final BlockPos ground = groundAnchor(level, colony, hut.getPosition());
        final List<SiteSelector.Footprint> shields = new ArrayList<>();
        for (final long packed : ColonyGrounds.get(level).ponds(colony.getID()))
        {
            final BlockPos corner = BlockPos.of(packed);
            final BlockPos middle = corner.offset(POND_SIZE / 2, 0, POND_SIZE / 2);
            if (onHomeTerrace(ground, middle)
                  && (!WorldUtil.isChunkLoaded(level, middle.getX() >> 4, middle.getZ() >> 4) || level.getFluidState(middle).isSource()))
            {
                shields.add(new SiteSelector.Footprint(corner.getX() - 1, corner.getZ() - 1, corner.getX() + POND_SIZE, corner.getZ() + POND_SIZE));
            }
        }
        return shields;
    }

    public static boolean sendToHomePond(final JobFisherman fisher)
    {
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return false;
        }
        final ICitizenData citizen = fisher.getCitizen();
        final IBuilding hut = citizen == null ? null : citizen.getWorkBuilding();
        if (hut == null || hut.getColony() == null || !(hut.getColony().getWorld() instanceof ServerLevel level)
              || !AutopilotConfig.get(hut.getColony(), AutopilotConfig.FISHER_HOME_POND))
        {
            return false;
        }
        final BlockPos anchor = groundAnchor(level, hut.getColony(), hut.getPosition());
        final BlockPos dug = ownPond(level, hut.getColony(), anchor);
        final List<Tuple<BlockPos, BlockPos>> home = new ArrayList<>();
        for (final Tuple<BlockPos, BlockPos> pond : fisher.getPonds())
        {
            final BlockPos water = pond.getA();
            if (!onHomeTerrace(anchor, water) || !onHomeTerrace(anchor, pond.getB()))
            {
                continue;
            }

            if (dug == null || (water.getX() >= dug.getX() && water.getX() < dug.getX() + POND_SIZE
                                  && water.getZ() >= dug.getZ() && water.getZ() < dug.getZ() + POND_SIZE
                                  && Math.abs(water.getY() - dug.getY()) <= 1))
            {
                home.add(pond);
            }
        }
        if (home.isEmpty())
        {
            return false;
        }
        fisher.setWater(home.get(level.random.nextInt(home.size())));
        return true;
    }

    private static boolean onHomeTerrace(final BlockPos anchor, final BlockPos pos)
    {
        return Math.abs(pos.getX() - anchor.getX()) <= TRUSTED_WATER_RADIUS
                 && Math.abs(pos.getZ() - anchor.getZ()) <= TRUSTED_WATER_RADIUS
                 && Math.abs(pos.getY() - anchor.getY()) <= TRUSTED_WATER_DEPTH;
    }

    static List<BlockPos> memorizedWaters(final IBuilding hut)
    {
        final List<BlockPos> waters = new ArrayList<>();
        for (final ICitizenData citizen : hut.getAllAssignedCitizen())
        {
            if (citizen.getJob() instanceof JobFisherman fisher)
            {
                for (final Tuple<BlockPos, BlockPos> pond : fisher.getPonds())
                {
                    waters.add(pond.getA());
                }
            }
        }
        return waters;
    }

    static void reseal(final ServerLevel level, final IColony colony, final IBuilding hut, final List<SiteSelector.Footprint> shields)
    {
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final int colonyId = colony.getID();
        for (final BlockPos water : memorizedWaters(hut))
        {
            adoptDugPond(level, colony, grounds, hut, water);
        }
        for (final long packed : grounds.ponds(colonyId))
        {
            final BlockPos corner = BlockPos.of(packed);
            final int y = corner.getY();
            final int minX = corner.getX();
            final int minZ = corner.getZ();
            final int maxX = minX + POND_SIZE - 1;
            final int maxZ = minZ + POND_SIZE - 1;
            if (!cornersLoaded(level, minX - 1, minZ - 1, maxX + 1, maxZ + 1))
            {
                continue;
            }

            int sources = 0, taken = 0;
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    final BlockState top = level.getBlockState(new BlockPos(x, y, z));
                    if (top.getFluidState().is(FluidTags.WATER))
                    {
                        sources++;
                    }
                    else if (SporeCompat.isInfection(top.getBlock()) || top.is(BlockTags.ICE))
                    {
                        taken++;
                    }
                }
            }
            if (sources + taken == 0)
            {

                grounds.removePond(colonyId, corner);
                continue;
            }
            int waterRestored = 0, rimClosed = 0, debris = 0, flushed = 0;
            for (int x = minX - 1; x <= maxX + 1; x++)
            {
                for (int z = minZ - 1; z <= maxZ + 1; z++)
                {
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4) || shielded(x, z, shields))
                    {
                        continue;
                    }
                    final boolean ring = x < minX || x > maxX || z < minZ || z > maxZ;
                    for (int dy = 0; dy >= -1; dy--)
                    {
                        final BlockPos cell = new BlockPos(x, y + dy, z);
                        final FluidState fluid = level.getFluidState(cell);
                        final BlockState state = level.getBlockState(cell);
                        final boolean creep = SporeCompat.isInfection(state.getBlock());

                        final boolean frozen = !ring && state.is(BlockTags.ICE);
                        if (!ring && ((!fluid.isEmpty() && !fluid.isSource()) || state.isAir() || creep || frozen))
                        {
                            WorldUtil.setBlockState(level, cell, Blocks.WATER.defaultBlockState());
                            if (creep || frozen)
                            {
                                flushed++;
                            }
                            else
                            {
                                waterRestored++;
                            }
                        }
                        else if (ring && creep && fluid.isSource())
                        {

                            WorldUtil.setBlockState(level, cell, Blocks.WATER.defaultBlockState());
                            flushed++;
                        }
                        else if (ring && ((!fluid.isEmpty() && !fluid.isSource()) || creep
                                            || (fluid.isEmpty() && (state.isAir() || state.canBeReplaced()))))
                        {

                            WorldUtil.setBlockState(level, cell, Blocks.DIRT.defaultBlockState());
                            rimClosed++;
                        }
                    }
                    final BlockPos cell = new BlockPos(x, y, z);

                    for (int dy = 1; dy <= 3; dy++)
                    {
                        final BlockPos over = cell.above(dy);
                        final BlockState overState = level.getBlockState(over);
                        if (!overState.isAir() && !overState.hasBlockEntity() && overState.getFluidState().isEmpty() && !overState.canBeReplaced()
                              && !(ring && overState.getBlock() instanceof FallingBlock))
                        {
                            WorldUtil.setBlockState(level, over, Blocks.AIR.defaultBlockState());
                            debris++;
                        }
                    }
                }
            }
            if (waterRestored + rimClosed + debris + flushed > 0)
            {
                ColonyAutopilot.LOGGER.info("[{}] resealed the fisher's pond at {} — {} water restored, {} rim closed, {} debris cleared, {} creep or ice flushed",
                  colony.getName(), corner.toShortString(), waterRestored, rimClosed, debris, flushed);
            }
        }
    }

    private static void adoptDugPond(final ServerLevel level, final IColony colony, final ColonyGrounds grounds, final IBuilding hut, final BlockPos water)
    {
        if (!WorldUtil.isChunkLoaded(level, water.getX() >> 4, water.getZ() >> 4))
        {
            return;
        }

        final BlockPos surface = level.getFluidState(water).is(FluidTags.WATER) ? water : water.below();
        if (!level.getFluidState(surface).is(FluidTags.WATER))
        {
            return;
        }
        final int y = surface.getY();
        for (final long packed : grounds.ponds(colony.getID()))
        {
            final BlockPos corner = BlockPos.of(packed);
            if (corner.getY() == y && water.getX() >= corner.getX() && water.getX() < corner.getX() + POND_SIZE
                  && water.getZ() >= corner.getZ() && water.getZ() < corner.getZ() + POND_SIZE)
            {
                return;
            }
        }
        final SiteSelector.Footprint footprint = SiteSelector.boxOf(hut);
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            final BlockPos corner = pondCorner(footprint, side);
            final int cx = corner.getX();
            final int cz = corner.getZ();
            if (water.getX() < cx || water.getX() >= cx + POND_SIZE || water.getZ() < cz || water.getZ() >= cz + POND_SIZE
                  || !cornersLoaded(level, cx, cz, cx + POND_SIZE - 1, cz + POND_SIZE - 1))
            {
                continue;
            }
            int floor = 0;
            int wet = 0;
            for (int x = cx; x < cx + POND_SIZE; x++)
            {
                for (int z = cz; z < cz + POND_SIZE; z++)
                {

                    final BlockState bed = level.getBlockState(probe.set(x, y - 2, z));
                    if (bed.isSolidRender(level, probe) || bed.is(Blocks.GLOWSTONE))
                    {
                        floor++;
                    }
                    if (level.getFluidState(probe.set(x, y - 1, z)).is(FluidTags.WATER))
                    {
                        wet++;
                    }
                }
            }
            if (floor == POND_SIZE * POND_SIZE && wet >= POND_SIZE * POND_SIZE - 9)
            {
                final BlockPos dug = new BlockPos(cx, y, cz);
                grounds.addPond(colony.getID(), dug);
                ColonyAutopilot.LOGGER.info("[{}] adopted the village's own pond at {} into the grounds record (dug before the record existed)",
                  colony.getName(), dug.toShortString());
                return;
            }
        }
    }

    private static boolean shielded(final int x, final int z, final List<SiteSelector.Footprint> shields)
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

    static void ensure(
      final ServerLevel level,
      final IColony colony,
      final BlockPos anchor,
      final SiteSelector.Footprint footprint,
      final Direction doorSide,
      final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand)
    {
        ensure(level, colony, anchor, footprint, doorSide, obstacles, roadBand, false);
    }

    static void tend(
      final ServerLevel level,
      final IColony colony,
      final BlockPos anchor,
      final SiteSelector.Footprint footprint,
      final Direction doorSide,
      final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand)
    {
        ensure(level, colony, anchor, footprint, doorSide, obstacles, roadBand, true);
    }

    static BlockPos groundAnchor(final ServerLevel level, final IColony colony, final BlockPos hut)
    {
        final int offset = ColonyGrounds.get(level).anchorOffset(colony.getID(), hut, 1);
        return offset == 1 ? hut : hut.below(offset - 1);
    }

    private static void ensure(
      final ServerLevel level,
      final IColony colony,
      final BlockPos anchor,
      final SiteSelector.Footprint footprint,
      final Direction doorSide,
      final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand,
      final boolean quiet)
    {

        final BlockPos ground = groundAnchor(level, colony, anchor);
        if (ownPond(level, colony, ground) != null)
        {
            reported.remove(GlobalPos.of(level.dimension(), ground));
            return;
        }
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            final boolean natural = findTrustedWater(level, ground) != null;
            if (!quiet && firstReport(level, ground, natural ? "natural, no terraforming" : "dry, no terraforming"))
            {
                ColonyAutopilot.LOGGER.info(natural
                    ? "[{}] terraforming is disabled, so no pond is dug for the fisher's hut — he keeps to the natural water nearby"
                    : "[{}] no fishable water near the fisher's hut and terraforming is disabled — the fisher will idle",
                  colony.getName());
            }
            return;
        }

        final List<SiteSelector.Footprint> guarded = new ArrayList<>(obstacles);
        final int reach = THAW_REACH + POND_SIZE + 1;
        guarded.addAll(SiteSelector.foreignClaims(level, colony, anchor.getX() - reach, anchor.getZ() - reach, anchor.getX() + reach, anchor.getZ() + reach));

        final IBuilding hut = colony.getServerBuildingManager().getBuilding(anchor);
        if (hut == null)
        {
            digPreferring(null, level, colony, anchor, ground, footprint, doorSide, guarded, roadBand, quiet);
            return;
        }
        waterSide(level, hut, MAX_HUT_LEVEL,
          side -> digPreferring(side, level, colony, anchor, ground, footprint, doorSide, guarded, roadBand, quiet));
    }

    private static final int MAX_HUT_LEVEL = 5;

    private static void digPreferring(
      final Direction waterfront,
      final ServerLevel level,
      final IColony colony,
      final BlockPos hut,
      final BlockPos anchor,
      final SiteSelector.Footprint footprint,
      final Direction doorSide,
      final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand,
      final boolean quiet)
    {
        if (ownPond(level, colony, anchor) != null)
        {
            return;
        }

        final List<Direction> sides = new ArrayList<>(List.of(doorSide.getOpposite(), doorSide.getClockWise(), doorSide.getCounterClockWise(), doorSide));
        if (waterfront != null && waterfront != doorSide)
        {
            sides.remove(waterfront);
            sides.add(0, waterfront);
        }
        for (final Direction side : sides)
        {
            final BlockPos corner = pondCorner(footprint, side);

            if (beyondTerrace(anchor, corner))
            {
                continue;
            }
            if (digPond(level, colony, anchor, corner, obstacles, roadBand, quiet, true))
            {
                reported.remove(GlobalPos.of(level.dimension(), anchor));
                return;
            }
        }

        if (findTrustedWater(level, anchor) != null)
        {
            if (!quiet && firstReport(level, anchor, "natural"))
            {
                ColonyAutopilot.LOGGER.info("[{}] no room for a fishing pond beside the fisher's hut — he keeps to the natural water within {} blocks",
                  colony.getName(), TRUSTED_WATER_RADIUS);
            }
            return;
        }

        if (thawNearestFrozenWater(level, colony, anchor, obstacles, roadBand, quiet))
        {
            reported.remove(GlobalPos.of(level.dimension(), anchor));
            return;
        }

        if (firstReport(level, anchor, "dry"))
        {
            ColonyAutopilot.LOGGER.warn("[{}] the Fisher's Hut at {} has no room for a pond and no water within {} blocks — he cannot fish; move the hut or give him water nearby",
              colony.getName(), hut.toShortString(), TRUSTED_WATER_RADIUS);
        }
    }

    private static void waterSide(final ServerLevel level, final IBuilding hut, final int tryLevel, final Consumer<Direction> callback)
    {
        final String rawPath = hut.getBlueprintPath();
        if (tryLevel < 1 || !rawPath.endsWith(".blueprint"))
        {
            callback.accept(null);
            return;
        }
        final String stem = rawPath.substring(0, rawPath.length() - ".blueprint".length());
        final String path = stem.substring(0, stem.length() - 1) + tryLevel + ".blueprint";

        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(hut.getStructurePack(), path, true, level.registryAccess()), level, blueprint -> {
            Direction side = null;
            boolean probeFailed = false;
            try
            {
                side = blueprint == null ? null : waterSideOf(level, hut, blueprint);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.debug("fisher blueprint water probe failed at level {}", tryLevel, e);
                probeFailed = true;
            }

            try
            {
                if (probeFailed)
                {
                    callback.accept(null);
                }
                else if (side != null)
                {
                    callback.accept(side);
                }
                else
                {
                    waterSide(level, hut, tryLevel - 1, callback);
                }
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the fisher's pond work after the water probe failed", hut.getColony().getName(), e);
            }
        }));
    }

    private static Direction waterSideOf(final ServerLevel level, final IBuilding hut, final Blueprint blueprint)
    {
        blueprint.setRotationMirror(RotationMirror.NONE, level);
        final BlockState anchorState = blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        final BlockState worldHut = level.getBlockState(hut.getPosition());
        RotationMirror rotationMirror = hut.getRotationMirror();
        if (anchorState != null && anchorState.hasProperty(AbstractBlockHut.FACING) && worldHut.hasProperty(AbstractBlockHut.FACING))
        {
            final int steps = (4 + worldHut.getValue(AbstractBlockHut.FACING).get2DDataValue()
                                 - anchorState.getValue(AbstractBlockHut.FACING).get2DDataValue()) % 4;
            rotationMirror = RotationMirror.of(Rotation.values()[steps], hut.getRotationMirror().mirror());
        }
        blueprint.setRotationMirror(rotationMirror, level);

        long sumX = 0, sumZ = 0;
        int water = 0;
        final BlockPos.MutableBlockPos local = new BlockPos.MutableBlockPos();
        for (int y = 0; y < blueprint.getSizeY(); y++)
        {
            for (int x = 0; x < blueprint.getSizeX(); x++)
            {
                for (int z = 0; z < blueprint.getSizeZ(); z++)
                {
                    final BlockState state = blueprint.getBlockState(local.set(x, y, z));
                    if (state != null && state.getFluidState().is(FluidTags.WATER))
                    {
                        sumX += x;
                        sumZ += z;
                        water++;
                    }
                }
            }
        }
        if (water == 0)
        {
            return null;
        }
        final double dx = sumX / (double) water - (blueprint.getSizeX() - 1) / 2.0;
        final double dz = sumZ / (double) water - (blueprint.getSizeZ() - 1) / 2.0;
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5)
        {
            return null;
        }
        return Math.abs(dx) > Math.abs(dz)
                 ? (dx > 0 ? Direction.EAST : Direction.WEST)
                 : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static boolean thawNearestFrozenWater(final ServerLevel level, final IColony colony, final BlockPos anchor,
      final List<SiteSelector.Footprint> obstacles, final Set<Long> roadBand, final boolean quiet)
    {
        final long now = level.getGameTime();
        final GlobalPos here = GlobalPos.of(level.dimension(), anchor);
        final Long retryAt = nextThawScan.get(here);
        if (retryAt != null && now < retryAt)
        {
            return false;
        }

        if (dugPondInReach(level, colony, anchor))
        {
            return true;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = anchor.getX() - THAW_REACH; x <= anchor.getX() + THAW_REACH; x += 4)
        {
            for (int z = anchor.getZ() - THAW_REACH; z <= anchor.getZ() + THAW_REACH; z += 4)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                final BlockPos surface = new BlockPos(x, SiteSelector.groundHeight(level, x, z) - 1, z);
                if (!level.getBlockState(surface).is(Blocks.ICE))
                {
                    continue;
                }
                if (surface.getY() > anchor.getY() + TRUSTED_WATER_DEPTH)
                {
                    continue;

                }

                boolean blocked = crossesRoad(x - POND_SIZE / 2 - 1, z - POND_SIZE / 2 - 1, x - POND_SIZE / 2 + POND_SIZE, z - POND_SIZE / 2 + POND_SIZE, roadBand);
                for (final SiteSelector.Footprint box : obstacles)
                {
                    if (box.intersects(x - POND_SIZE / 2 - 1, z - POND_SIZE / 2 - 1, x - POND_SIZE / 2 + POND_SIZE, z - POND_SIZE / 2 + POND_SIZE))
                    {
                        blocked = true;
                        break;
                    }
                }
                if (blocked)
                {
                    continue;
                }
                final double dist = surface.distSqr(anchor);
                if (dist < bestDist)
                {
                    bestDist = dist;
                    best = surface;
                }
            }
        }
        if (best != null
              && digPond(level, colony, anchor, new BlockPos(best.getX() - POND_SIZE / 2, 0, best.getZ() - POND_SIZE / 2), obstacles, roadBand, quiet, false))
        {
            nextThawScan.remove(here);
            return true;
        }
        nextThawScan.put(here, now + THAW_BACKOFF_TICKS);
        return false;
    }

    private static boolean dugPondInReach(final ServerLevel level, final IColony colony, final BlockPos anchor)
    {
        for (final long packed : ColonyGrounds.get(level).ponds(colony.getID()))
        {
            final BlockPos middle = BlockPos.of(packed).offset(POND_SIZE / 2, 0, POND_SIZE / 2);
            if (Math.abs(middle.getX() - anchor.getX()) <= THAW_REACH && Math.abs(middle.getZ() - anchor.getZ()) <= THAW_REACH
                  && middle.getY() <= anchor.getY() + TRUSTED_WATER_DEPTH
                  && (!WorldUtil.isChunkLoaded(level, middle.getX() >> 4, middle.getZ() >> 4) || level.getFluidState(middle).is(FluidTags.WATER)))
            {
                return true;
            }
        }
        return false;
    }

    private static BlockPos pondCorner(final SiteSelector.Footprint footprint, final Direction side)
    {
        final int midX = (footprint.minX() + footprint.maxX()) / 2;
        final int midZ = (footprint.minZ() + footprint.maxZ()) / 2;
        return switch (side)
        {
            case NORTH -> new BlockPos(midX - POND_SIZE / 2, 0, footprint.minZ() - GAP - POND_SIZE);
            case SOUTH -> new BlockPos(midX - POND_SIZE / 2, 0, footprint.maxZ() + GAP + 1);
            case WEST -> new BlockPos(footprint.minX() - GAP - POND_SIZE, 0, midZ - POND_SIZE / 2);
            default -> new BlockPos(footprint.maxX() + GAP + 1, 0, midZ - POND_SIZE / 2);
        };
    }

    static BlockPos findTrustedWater(final ServerLevel level, final BlockPos anchor)
    {

        final GlobalPos key = GlobalPos.of(level.dimension(), anchor);
        final long now = level.getGameTime();
        final TrustedWater cached = trustedWater.get(key);
        if (cached != null && now - cached.tick() <= TRUSTED_WATER_MEMO_TICKS && now >= cached.tick())
        {
            return cached.water();
        }
        final BlockPos found = scanTrustedWater(level, anchor);
        trustedWater.put(key, new TrustedWater(now, found));
        return found;
    }

    private record TrustedWater(long tick, BlockPos water) {}

    private static final long TRUSTED_WATER_MEMO_TICKS = 20L;

    private static final Map<GlobalPos, TrustedWater> trustedWater = new HashMap<>();

    private static BlockPos scanTrustedWater(final ServerLevel level, final BlockPos anchor)
    {
        for (int x = anchor.getX() - TRUSTED_WATER_RADIUS; x <= anchor.getX() + TRUSTED_WATER_RADIUS; x += 4)
        {
            for (int z = anchor.getZ() - TRUSTED_WATER_RADIUS; z <= anchor.getZ() + TRUSTED_WATER_RADIUS; z += 4)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }

                final BlockPos surface = new BlockPos(x, SiteSelector.groundHeight(level, x, z) - 1, z);
                if (!level.getFluidState(surface).isSource()
                      || Math.abs(surface.getY() - anchor.getY()) > TRUSTED_WATER_DEPTH)
                {
                    continue;
                }

                for (final int[] shift : PROBE_OFFSETS)
                {
                    final int px = x + shift[0];
                    final int pz = z + shift[1];

                    if (!cornersLoaded(level, px - 2, pz - 2, px + 2, pz + 2))
                    {
                        continue;
                    }
                    final BlockPos probe = shift[0] == 0 && shift[1] == 0
                                             ? surface
                                             : new BlockPos(px, SiteSelector.groundHeight(level, px, pz) - 1, pz);
                    if (level.getFluidState(probe).isSource() && Pond.checkPond(level, probe, null) == Pond.PondState.VALID)
                    {
                        return probe;
                    }
                }
            }
        }
        return null;
    }

    static boolean hasRoom(final ServerLevel level, final IColony colony, final BlockPos anchor, final SiteSelector.Footprint footprint,
      final Direction doorSide, final List<SiteSelector.Footprint> obstacles, final Set<Long> roadBand, final IntBinaryOperator gradedTop)
    {

        final List<SiteSelector.Footprint> guarded = new ArrayList<>(obstacles);
        final int reach = GAP + POND_SIZE + 1;
        guarded.addAll(SiteSelector.foreignClaims(level, colony, footprint.minX() - reach, footprint.minZ() - reach, footprint.maxX() + reach, footprint.maxZ() + reach));
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            final BlockPos corner = pondCorner(footprint, side);
            if (side != doorSide && !beyondTerrace(anchor, corner) && pondLevel(level, anchor, corner, guarded, roadBand, true, gradedTop) != null)
            {
                return true;
            }
        }
        return false;
    }

    static boolean onCrest(final ServerLevel level, final SiteSelector.Footprint footprint, final int groundY, final IntBinaryOperator top)
    {
        return fallsAway(level, footprint, Direction.NORTH, groundY, top) && fallsAway(level, footprint, Direction.SOUTH, groundY, top)
                 || fallsAway(level, footprint, Direction.WEST, groundY, top) && fallsAway(level, footprint, Direction.EAST, groundY, top);
    }

    private static boolean fallsAway(final ServerLevel level, final SiteSelector.Footprint footprint, final Direction side, final int groundY,
      final IntBinaryOperator top)
    {
        final BlockPos corner = pondCorner(footprint, side);
        if (!cornersLoaded(level, corner.getX(), corner.getZ(), corner.getX() + POND_SIZE - 1, corner.getZ() + POND_SIZE - 1))
        {
            return false;
        }
        final List<Integer> tops = sortedTops(corner, top);
        return tops.get(tops.size() / 2) < groundY;
    }

    static boolean waitsForWater(final ServerLevel level, final IColony colony, final BlockPos hut)
    {
        final BlockPos ground = groundAnchor(level, colony, hut);
        return !dugPondInReach(level, colony, ground) && findTrustedWater(level, ground) == null;
    }

    private static boolean beyondTerrace(final BlockPos anchor, final BlockPos corner)
    {
        return Math.abs(corner.getX() + POND_SIZE / 2 - anchor.getX()) > TRUSTED_WATER_RADIUS
                 || Math.abs(corner.getZ() + POND_SIZE / 2 - anchor.getZ()) > TRUSTED_WATER_RADIUS;
    }

    private static List<Integer> sortedTops(final BlockPos corner, final IntBinaryOperator top)
    {
        final List<Integer> tops = new ArrayList<>();
        for (int x = corner.getX(); x < corner.getX() + POND_SIZE; x++)
        {
            for (int z = corner.getZ(); z < corner.getZ() + POND_SIZE; z++)
            {
                tops.add(top.applyAsInt(x, z));
            }
        }
        tops.sort(null);
        return tops;
    }

    private static Integer pondLevel(final ServerLevel level, final BlockPos anchor, final BlockPos corner, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand, final boolean onTerrace, final IntBinaryOperator top)
    {
        final int maxX = corner.getX() + POND_SIZE - 1;
        final int maxZ = corner.getZ() + POND_SIZE - 1;
        for (final SiteSelector.Footprint box : obstacles)
        {
            if (box.intersects(corner.getX() - 1, corner.getZ() - 1, maxX + 1, maxZ + 1))
            {
                return null;
            }
        }

        if (crossesRoad(corner.getX() - 1, corner.getZ() - 1, maxX + 1, maxZ + 1, roadBand))
        {
            return null;
        }

        if (!cornersLoaded(level, corner.getX() - 1, corner.getZ() - 1, maxX + 1, maxZ + 1))
        {
            return null;
        }

        final List<Integer> heights = sortedTops(corner, top);
        final int waterY = heights.get(heights.size() / 2);
        if (heights.get(0) < waterY - 2 || heights.get(heights.size() - 1) > waterY + 2)
        {
            return null;
        }
        if (waterY > anchor.getY() + TRUSTED_WATER_DEPTH)
        {
            return null;

        }
        if (onTerrace && waterY < anchor.getY() - TRUSTED_WATER_DEPTH)
        {
            return null;
        }
        for (int x = corner.getX(); x <= maxX; x++)
        {
            for (int z = corner.getZ(); z <= maxZ; z++)
            {
                for (int y = waterY - 2; y <= waterY + 3; y++)
                {
                    if (level.getBlockEntity(new BlockPos(x, y, z)) != null)
                    {
                        return null;
                    }
                }
            }
        }
        return waterY;
    }

    private static boolean crossesRoad(final int minX, final int minZ, final int maxX, final int maxZ, final Set<Long> roadBand)
    {
        for (int x = minX; x <= maxX && !roadBand.isEmpty(); x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                if (roadBand.contains(BlockPos.asLong(x, 0, z)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean digPond(final ServerLevel level, final IColony colony, final BlockPos anchor, final BlockPos corner, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand, final boolean quiet, final boolean onTerrace)
    {
        final Integer waterLevel = pondLevel(level, anchor, corner, obstacles, roadBand, onTerrace, (x, z) -> SiteSelector.groundHeight(level, x, z) - 1);
        if (waterLevel == null)
        {
            return false;
        }
        final int waterY = waterLevel;
        final int maxX = corner.getX() + POND_SIZE - 1;
        final int maxZ = corner.getZ() + POND_SIZE - 1;

        final BlockPos center = new BlockPos(corner.getX() + POND_SIZE / 2, waterY, corner.getZ() + POND_SIZE / 2);
        final boolean freezing = level.getBiome(center).value().coldEnoughToSnow(center);

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = corner.getX() - 1; x <= maxX + 1; x++)
        {
            for (int z = corner.getZ() - 1; z <= maxZ + 1; z++)
            {
                final boolean rim = x < corner.getX() || x > maxX || z < corner.getZ() || z > maxZ;
                if (rim)
                {
                    for (int y = waterY - 2; y <= waterY; y++)
                    {
                        final BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.canBeReplaced() || !state.getFluidState().isEmpty())
                        {
                            WorldUtil.setBlockState(level,pos, Blocks.DIRT.defaultBlockState());
                        }
                    }

                    for (int y = waterY + 1; y <= waterY + 3; y++)
                    {
                        final BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && !state.canBeReplaced())
                        {
                            WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                        }
                    }
                    continue;
                }
                if (freezing && (x - corner.getX()) % 2 == 1 && (z - corner.getZ()) % 2 == 1)
                {
                    WorldUtil.setBlockState(level,pos.set(x, waterY - 2, z), Blocks.GLOWSTONE.defaultBlockState());
                }
                else if (!level.getBlockState(pos.set(x, waterY - 2, z)).isSolidRender(level, pos))
                {
                    WorldUtil.setBlockState(level,pos, Blocks.DIRT.defaultBlockState());
                }
                WorldUtil.setBlockState(level,pos.set(x, waterY - 1, z), Blocks.WATER.defaultBlockState());
                WorldUtil.setBlockState(level,pos.set(x, waterY, z), Blocks.WATER.defaultBlockState());

                for (int y = waterY + 1; y <= waterY + 3; y++)
                {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir())
                    {
                        WorldUtil.setBlockState(level,pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        final BlockPos pond = new BlockPos(corner.getX(), waterY, corner.getZ());
        ColonyGrounds.get(level).addPond(colony.getID(), pond);

        trustedWater.remove(GlobalPos.of(level.dimension(), anchor));
        ColonyAutopilot.LOGGER.info("[{}] dug a {}x{} fishing pond at {} for the fisher's hut{}",
          colony.getName(), POND_SIZE, POND_SIZE, pond.toShortString(),
          freezing ? " (glowstone-floored against the frost)" : "");
        if (!quiet)
        {

            Milestones.say(colony, "colonyautopilot.milestone.pond");
        }
        return true;
    }

    private static boolean cornersLoaded(final ServerLevel level, final int minX, final int minZ, final int maxX, final int maxZ)
    {
        return WorldUtil.isChunkLoaded(level, minX >> 4, minZ >> 4)
                 && WorldUtil.isChunkLoaded(level, maxX >> 4, minZ >> 4)
                 && WorldUtil.isChunkLoaded(level, minX >> 4, maxZ >> 4)
                 && WorldUtil.isChunkLoaded(level, maxX >> 4, maxZ >> 4);
    }
}
