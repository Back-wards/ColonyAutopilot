// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Tuple;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GroundsWarden
{

    private static final int PERIOD_TICKS = 600;

    private static final int CREEP_CHUNKS_PER_PATROL = 2;

    private static final int CREEP_DEEP_WRITES = 800;

    private static final int CREEP_CLEAR_HEIGHT = 5;

    private static final int CREEP_CLEAR_DEPTH = 48;

    private static final int CLEAN_RECHECK_TICKS = 24000;

    private static final int LAVA_CHUNKS_PER_PATROL = 4;

    private static final int BUSH_CHUNKS_PER_PATROL = 4;

    private static final int BUSH_DEATH_REACH = 2;

    private static final int LAVA_SCAN_DEPTH = 4;

    private static final int REACT_PERIOD_TICKS = 40;

    private static final int REACT_BUDGET = 800;

    private static final int REACT_CHUNKS_PER_PASS = 8;

    private static final int HOT_TTL_TICKS = 600;

    private static final int SIEGE_PATROLS = 3;

    private static final int SIEGE_HALO_BLOCKS = 32;

    private final GrowthDirector growth;

    private final Map<ColonyId, Integer> creepCursor = new HashMap<>();

    private final Map<ColonyId, Map<Long, Long>> creepScanned = new HashMap<>();

    private final Map<ColonyId, Integer> lavaCursor = new HashMap<>();

    private final Map<ColonyId, Integer> bushCursor = new HashMap<>();

    private int tickCounter = 29;

    private int reactCounter = 7;

    private final ColonyRota rota = new ColonyRota();

    private final Map<ColonyId, Map<Long, Long>> hotChunks = new HashMap<>();

    private final Map<ColonyId, Integer> reactCursor = new HashMap<>();

    private final Map<ColonyId, Set<Long>> warZone = new HashMap<>();

    private final Map<ColonyId, Map<Long, Long>> hotDug = new HashMap<>();

    private final Map<ColonyId, Map<Long, Integer>> borderPressure = new HashMap<>();

    GroundsWarden(final GrowthDirector growth)
    {
        this.growth = growth;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        creepCursor.clear();
        creepScanned.clear();
        lavaCursor.clear();
        bushCursor.clear();
        hotChunks.clear();
        reactCursor.clear();
        warZone.clear();
        hotDug.clear();
        borderPressure.clear();
        FarmFields.clearDeadGroundWarnings();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {

            return;
        }
        final boolean react = ++reactCounter >= REACT_PERIOD_TICKS;
        if (react)
        {
            reactCounter = 0;
        }
        if (++tickCounter >= PERIOD_TICKS)
        {
            tickCounter = 0;
            ColonyAutopilot.retainColonies(rota.fill(event.getServer()), creepCursor, creepScanned, lavaCursor, bushCursor, hotChunks, reactCursor, warZone, hotDug, borderPressure);
        }

        final IColony patrolling = rota.next(event.getServer());
        if (!react && patrolling == null)
        {
            return;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (!(colony.getWorld() instanceof ServerLevel level))
            {
                continue;
            }
            if (react)
            {
                try
                {
                    reactivePass(level, colony);
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("[{}] the reactive creep guard failed", colony.getName(), e);
                }
            }
            if (colony != patrolling)
            {
                continue;
            }
            List<SiteSelector.Footprint> shields = null;
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                if (building.getBuildingLevel() <= 0
                      || !WorldUtil.isChunkLoaded(level, building.getPosition().getX() >> 4, building.getPosition().getZ() >> 4))
                {
                    continue;
                }
                try
                {
                    if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFisherman)
                    {

                        FishingPond.pruneDeadPonds(level, colony, building);

                        if (AutopilotConfig.get(colony, AutopilotConfig.GROWTH_POND_FIELD_UPKEEP))
                        {
                            if (shields == null)
                            {
                                shields = buildingBoxes(colony);
                            }
                            FishingPond.reseal(level, colony, building, shields);
                            final BlockState hutState = level.getBlockState(building.getPosition());
                            if (hutState.hasProperty(AbstractBlockHut.FACING))
                            {
                                FishingPond.tend(level, colony, building.getPosition(), SiteSelector.boxOf(building),
                                  ColonyGrounds.get(level).front(colony.getID(), building.getPosition(), hutState.getValue(AbstractBlockHut.FACING)),
                                  growth.obstaclesAround(building), growth.roadBand(colony, level));
                            }
                        }
                    }
                    else if (AutopilotConfig.live(colony, AutopilotConfig.FARM_FIELDS) && AutopilotConfig.get(colony, AutopilotConfig.GROWTH_POND_FIELD_UPKEEP) && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFarmer)
                    {

                        FarmFields.tend(level, colony, building);
                    }
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("[{}] grounds patrol failed at the {}", colony.getName(), SiteSelector.plainName(building), e);
                }
            }
            try
            {
                scrubCreep(level, colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the creep guard's patrol failed", colony.getName(), e);
            }
            try
            {
                killSporeSpreaders(level, colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the spore warden's patrol failed", colony.getName(), e);
            }
            try
            {

                breakBorderSiege(level, colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the border siege-breaker's patrol failed", colony.getName(), e);
            }
            try
            {
                sealClaimLava(level, colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the lava warden's patrol failed", colony.getName(), e);
            }
            try
            {
                clearClaimBushes(level, colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the bush warden's patrol failed", colony.getName(), e);
            }
        }
    }

    private void scrubCreep(final ServerLevel level, final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.SPORES_CREEP_GUARD) || SporeCompat.creepCure().isEmpty())
        {
            return;
        }
        final LinkedHashSet<Long> chunks = new LinkedHashSet<>(colony.getLoadedChunks());
        if (chunks.isEmpty())
        {

            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                final Tuple<BlockPos, BlockPos> corners = building.getCorners();
                for (int cx = (corners.getA().getX() >> 4) - 1; cx <= (corners.getB().getX() >> 4) + 1; cx++)
                {
                    for (int cz = (corners.getA().getZ() >> 4) - 1; cz <= (corners.getB().getZ() >> 4) + 1; cz++)
                    {
                        chunks.add(ChunkPos.asLong(cx, cz));
                    }
                }
            }
        }
        if (chunks.isEmpty())
        {
            return;
        }
        final Long[] order = chunks.toArray(new Long[0]);
        java.util.Arrays.sort(order);
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final int cursor = creepCursor.getOrDefault(key, 0) % order.length;
        final Map<Block, Block> cure = SporeCompat.creepCure();
        final List<Shelter> footprints = shelters(colony);
        final Set<Long> roads = growth.roadBlocks(colony, level);
        final long now = level.getGameTime();
        final Map<Long, Long> hot = hotChunks.get(key);
        final boolean atPeace = hot == null || hot.isEmpty();
        final Map<Long, Long> scanned = creepScanned.computeIfAbsent(key, k -> new HashMap<>());
        final Relaid relaid = new Relaid();
        int scrubbed = 0;

        int remaining = CREEP_DEEP_WRITES;

        final Set<Long> zone = warZone.computeIfAbsent(key, k -> new HashSet<>());
        zone.retainAll(chunks);
        final Map<Long, Long> dug = hotDug.computeIfAbsent(key, k -> new HashMap<>());
        dug.values().removeIf(at -> now - at >= CLEAN_RECHECK_TICKS);
        final List<Long> visits = new ArrayList<>();
        final Long[] heat = zone.toArray(new Long[0]);
        java.util.Arrays.sort(heat);
        for (int h = 0; h < heat.length && visits.size() < CREEP_CHUNKS_PER_PATROL; h++)
        {

            if (!dug.containsKey(heat[h]) && WorldUtil.isChunkLoaded(level, ChunkPos.getX(heat[h]), ChunkPos.getZ(heat[h]))
                  && !(atPeace && now < scanned.getOrDefault(heat[h], 0L)))
            {
                visits.add(heat[h]);
            }
        }
        final int fromHot = visits.size();
        for (int slice = 0; visits.size() < CREEP_CHUNKS_PER_PATROL && slice < order.length; slice++)
        {
            visits.add(order[(cursor + slice) % order.length]);
        }
        int next = cursor + visits.size() - fromHot;
        for (int visit = 0; visit < visits.size(); visit++)
        {
            final long chunkLong = visits.get(visit);
            if (visit >= fromHot && visits.indexOf(chunkLong) < visit)
            {
                continue;
            }
            final ChunkPos chunk = new ChunkPos(chunkLong);
            if (!WorldUtil.isChunkLoaded(level, chunk.x, chunk.z))
            {
                continue;
            }
            if (atPeace && now < scanned.getOrDefault(chunkLong, 0L))
            {
                continue;
            }

            final boolean creepHere = visit < fromHot || (hot != null && now < hot.getOrDefault(chunkLong, 0L));
            final int did = scrubChunk(level, colony, cure, chunk, remaining, footprints, roads, true, creepHere, relaid);
            remaining -= did;
            scrubbed += did;
            if (did > 0)
            {
                markHot(key, chunkLong, now);
                scanned.remove(chunkLong);
            }
            else
            {

                scanned.put(chunkLong, now + CLEAN_RECHECK_TICKS);
            }
            if (remaining <= 0)
            {

                next = cursor + Math.max(0, visit - fromHot);
                break;
            }

            zone.remove(chunkLong);
            if (visit < fromHot)
            {
                dug.put(chunkLong, now);
            }
        }
        creepCursor.put(key, next % order.length);

        if (scrubbed > relaid.ground)
        {
            ColonyAutopilot.LOGGER.info("[{}] the creep guard cleared {} spore blocks from the village grounds — the infection stops at the border",
              colony.getName(), scrubbed - relaid.ground);
            Milestones.alert(colony, Milestones.ALERT_INFECTED, "Your village is infected!");
        }
        if (relaid.ground > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the creep guard laid {} block(s) of earth back where the ground had been eaten away ({} under buildings)",
              colony.getName(), relaid.ground, relaid.foundation);
        }
        if (relaid.roads > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the creep guard laid {} block(s) of the village's roads back as it cured them", colony.getName(), relaid.roads);
        }
        if (relaid.remembered > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the creep guard remembers the clean ground of {} more column(s)", colony.getName(), relaid.remembered);
        }
    }

    private int scrubChunk(final ServerLevel level, final IColony colony, final Map<Block, Block> cure, final ChunkPos chunk,
      final int budget, final List<Shelter> footprints, final Set<Long> roads, final boolean deep, final boolean creepHere, final Relaid relaid)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final List<Shelter> local = new ArrayList<>();
        final List<SiteSelector.Footprint> near = new ArrayList<>();
        for (final Shelter shelter : footprints)
        {
            final SiteSelector.Footprint box = shelter.box();
            if (box.intersects(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ()))
            {
                local.add(shelter);
            }
            if (box.intersects(chunk.getMinBlockX() - FillGrade.REACH, chunk.getMinBlockZ() - FillGrade.REACH,
              chunk.getMaxBlockX() + FillGrade.REACH, chunk.getMaxBlockZ() + FillGrade.REACH))
            {
                near.add(box);
            }
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final short[] remembered = grounds.groundMemory(colony.getID(), chunk.toLong());
        final FillGrade grade = new FillGrade(level, cure, near, remembered);
        int scrubbed = 0;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX() && scrubbed < budget; x++)
        {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ() && scrubbed < budget; z++)
            {
                final int floor = shelterFloor(local, x, z);
                if (floor == ZONE)
                {
                    continue;
                }
                final int surface = SiteSelector.groundHeight(level, x, z);
                final boolean sheltered = floor != Integer.MAX_VALUE;
                final int lowY = deep ? Math.max(level.getMinBuildHeight(), surface - CREEP_CLEAR_DEPTH) : surface - 1;
                final int highY = surface + CREEP_CLEAR_HEIGHT;
                final int column = (z & 15) << 4 | (x & 15);

                final int ground = sheltered || remembered == null ? ColonyGrounds.NO_GROUND : remembered[column];
                boolean curedUnder = false;
                int y = lowY;
                for (; y <= highY && scrubbed < budget; y++)
                {
                    final BlockState state = level.getBlockState(pos.set(x, y, z));
                    final Block block = state.getBlock();
                    final Block clean = cure.get(block);
                    if (clean != null)
                    {
                        final BlockState road = sheltered ? null : roadBlock(level, pos.immutable(), roads);
                        WorldUtil.setBlockState(level, pos.immutable(), road != null ? road : clean.defaultBlockState());
                        scrubbed++;
                        relaid.roads += road != null ? 1 : 0;
                        curedUnder |= y < ground;
                    }

                    else if (SporeCompat.isInfection(block))
                    {
                        final BlockState road = sheltered ? null : roadBlock(level, pos.immutable(), roads);
                        final BlockState fill;
                        if (state.getFluidState().getType().isSame(Fluids.WATER))
                        {

                            fill = state.getFluidState().createLegacyBlock();
                        }
                        else if (road != null)
                        {
                            fill = road;
                            relaid.roads++;
                        }

                        else if (sheltered ? y >= floor : y >= grade.fillLine(x, z))
                        {
                            fill = Blocks.AIR.defaultBlockState();
                        }
                        else
                        {
                            fill = Blocks.DIRT.defaultBlockState();
                        }
                        WorldUtil.setBlockState(level, pos.immutable(), fill);
                        scrubbed++;
                        curedUnder |= y < ground;
                    }
                }
                if (curedUnder && scrubbed < budget)
                {
                    scrubbed += fillHole(level, x, z, ground, budget - scrubbed, relaid);
                }

                if (deep && !sheltered && y > highY)
                {
                    final int clean = cleanGround(level, x, z, pos);
                    if (clean != ColonyGrounds.NO_GROUND && grounds.rememberGround(colony.getID(), chunk.toLong(), column, clean))
                    {
                        relaid.remembered++;
                    }
                }
            }
        }
        if (deep && (creepHere || scrubbed > 0))
        {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX() && scrubbed < budget && TickBudget.has(); x++)
            {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ() && scrubbed < budget && TickBudget.has(); z++)
                {
                    final int floor = shelterFloor(local, x, z);
                    if (floor != Integer.MAX_VALUE && floor != ZONE && floor != EVERY_HEIGHT)
                    {
                        scrubbed += fillFoundation(level, cure, x, z, floor,
                          remembered == null ? ColonyGrounds.NO_GROUND : remembered[(z & 15) << 4 | (x & 15)], budget - scrubbed, relaid);
                    }
                }
            }
        }
        return scrubbed;
    }

    private static final int FOUNDATION_REACH = 24;

    private static int fillFoundation(final ServerLevel level, final Map<Block, Block> cure, final int x, final int z, final int floor,
      final int ground, final int budget, final Relaid relaid)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int end = floor - 1;
        BlockState found = level.getBlockState(pos.set(x, end, z));
        while (end >= floor - FOUNDATION_REACH && hollow(found))
        {
            found = level.getBlockState(pos.set(x, --end, z));
        }
        TickBudget.spend(floor - end);
        final boolean reached = end >= floor - FOUNDATION_REACH;
        if (reached && (found.hasBlockEntity() || !found.isFaceSturdy(level, pos, Direction.UP)))
        {
            return 0;
        }
        final int bottom;
        if (ground != ColonyGrounds.NO_GROUND && ground <= floor)
        {
            bottom = Math.max(ground - 1, floor - FOUNDATION_REACH);
        }
        else if (reached)
        {
            bottom = end + 1;
        }
        else
        {
            return 0;
        }
        int filled = 0;
        for (int y = floor - 1; y >= bottom && filled < budget; y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.hasBlockEntity())
            {
                break;
            }
            if (hollow(state))
            {
                final Block clean = cure.get(state.getBlock());
                WorldUtil.setBlockState(level, pos.immutable(), clean != null ? clean.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                filled++;
            }
        }
        TickBudget.spend(filled);
        relaid.ground += filled;
        relaid.foundation += filled;
        return filled;
    }

    private static boolean hollow(final BlockState state)
    {
        return (state.canBeReplaced() && !state.hasBlockEntity()) || SporeCompat.isInfection(state.getBlock());
    }

    private static int fillHole(final ServerLevel level, final int x, final int z, final int ground, final int budget, final Relaid relaid)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int filled = 0;
        for (int y = ground - 1; y >= ground - CREEP_CLEAR_DEPTH && filled < budget && TickBudget.has(); y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.getFluidState().is(FluidTags.WATER) && state.canBeReplaced())
            {
                WorldUtil.setBlockState(level, pos.immutable(), Blocks.DIRT.defaultBlockState());
                TickBudget.spend(1);
                filled++;
            }
            else if (!state.isAir())
            {
                break;
            }
        }
        relaid.ground += filled;
        return filled;
    }

    private static int cleanGround(final ServerLevel level, final int x, final int z, final BlockPos.MutableBlockPos probe)
    {
        int y = VillagePaths.surfaceY(level, x, z) + 1;
        while (y > level.getMinBuildHeight() + 1 && level.getBlockState(probe.set(x, y - 1, z)).getFluidState().is(FluidTags.WATER))
        {
            y = SiteSelector.groundHeightBelow(level, x, z, y - 1);
        }
        return SporeCompat.isInfection(level.getBlockState(probe.set(x, y - 1, z)).getBlock()) ? ColonyGrounds.NO_GROUND : y;
    }

    private static BlockState roadBlock(final ServerLevel level, final BlockPos at, final Set<Long> roads)
    {
        if (roads.isEmpty())
        {
            return null;
        }
        if (roads.contains(at.asLong()))
        {
            if (level.getBlockState(at.above()).is(Blocks.OAK_FENCE))
            {
                return Blocks.COBBLESTONE.defaultBlockState();
            }
            final BlockPos under = at.below();
            final BlockState ground = level.getBlockState(under);
            return ground.isFaceSturdy(level, under, Direction.UP) && !SporeCompat.isInfection(ground.getBlock()) ? Blocks.DIRT_PATH.defaultBlockState() : null;
        }

        for (int down = 1; down <= 3; down++)
        {
            final BlockPos below = at.below(down);
            final BlockState state = level.getBlockState(below);
            if (roads.contains(below.asLong()) && state.is(Blocks.COBBLESTONE))
            {
                return down < 3 ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState();
            }
            if (!state.is(Blocks.OAK_FENCE))
            {
                return null;
            }
        }
        return null;
    }

    private static final class Relaid
    {
        int ground;

        int foundation;

        int roads;
        int remembered;
    }

    private static final class FillGrade
    {

        static final int REACH = 8;

        private static final int MAX_RISE = 3;

        private final ServerLevel level;
        private final Map<Block, Block> cure;
        private final List<SiteSelector.Footprint> sheltered;

        private final short[] remembered;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        private final Map<Long, int[]> columns = new HashMap<>();
        private final Map<Long, Integer> lines = new HashMap<>();

        FillGrade(final ServerLevel level, final Map<Block, Block> cure, final List<SiteSelector.Footprint> sheltered, final short[] remembered)
        {
            this.level = level;
            this.cure = cure;
            this.sheltered = sheltered;
            this.remembered = remembered;
        }

        int fillLine(final int x, final int z)
        {
            final long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
            final Integer known = lines.get(key);
            if (known != null)
            {
                return known;
            }
            final int[] own = column(x, z);
            int line = own[0];
            if (own[1] != 0)
            {
                int lowest = Integer.MAX_VALUE;
                for (final int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                {
                    for (int d = 1; d <= REACH; d++)
                    {
                        final int cx = x + step[0] * d;
                        final int cz = z + step[1] * d;
                        if (!WorldUtil.isChunkLoaded(level, cx >> 4, cz >> 4) || inFootprint(sheltered, cx, cz))
                        {
                            break;
                        }
                        final int[] other = column(cx, cz);
                        if (other[1] == 0)
                        {
                            lowest = Math.min(lowest, other[0]);
                            break;
                        }
                    }
                }
                if (lowest != Integer.MAX_VALUE)
                {
                    line = Math.max(own[0], Math.min(lowest, own[0] + MAX_RISE));
                }
            }
            if (remembered != null)
            {
                line = Math.max(line, remembered[(z & 15) << 4 | (x & 15)]);
            }
            lines.put(key, line);
            return line;
        }

        private int[] column(final int x, final int z)
        {
            final long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
            final int[] known = columns.get(key);
            if (known != null)
            {
                return known;
            }
            int y = SiteSelector.groundHeight(level, x, z);
            boolean infected = false;
            while (y > level.getMinBuildHeight() + 1)
            {
                final BlockState below = level.getBlockState(probe.set(x, y - 1, z));
                if (!below.getFluidState().isEmpty() || !SporeCompat.isInfection(below.getBlock()) || cure.containsKey(below.getBlock()))
                {
                    break;
                }
                infected = true;
                y = SiteSelector.groundHeightBelow(level, x, z, y - 1);
            }
            final int[] column = {y, infected ? 1 : 0};
            columns.put(key, column);
            return column;
        }
    }

    private static boolean inFootprint(final List<SiteSelector.Footprint> footprints, final int x, final int z)
    {
        for (final SiteSelector.Footprint fp : footprints)
        {
            if (fp.intersects(x, z, x, z))
            {
                return true;
            }
        }
        return false;
    }

    private void sealClaimLava(final ServerLevel level, final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            return;
        }
        final LinkedHashSet<Long> chunks = new LinkedHashSet<>(colony.getLoadedChunks());
        if (chunks.isEmpty())
        {
            return;
        }
        final Long[] order = chunks.toArray(new Long[0]);
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        java.util.Arrays.sort(order);
        final int cursor = lavaCursor.getOrDefault(key, 0) % order.length;

        final List<SiteSelector.Footprint> sheltered = new ArrayList<>(ProtectedZones.footprintsFor(colony));
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() > 0)
            {
                sheltered.add(SiteSelector.boxOf(building));
            }
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int capped = 0;
        for (int slice = 0; slice < LAVA_CHUNKS_PER_PATROL && slice < order.length; slice++)
        {
            final ChunkPos chunk = new ChunkPos(order[(cursor + slice) % order.length]);
            if (!WorldUtil.isChunkLoaded(level, chunk.x, chunk.z))
            {
                continue;
            }
            final List<SiteSelector.Footprint> local = new ArrayList<>();
            for (final SiteSelector.Footprint box : sheltered)
            {
                if (box.intersects(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ()))
                {
                    local.add(box);
                }
            }
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++)
                {
                    if (inFootprint(local, x, z))
                    {
                        continue;
                    }

                    final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                    for (int y = top; y >= top - LAVA_SCAN_DEPTH; y--)
                    {
                        if (!level.getBlockState(pos.set(x, y, z)).getFluidState().is(FluidTags.LAVA))
                        {
                            continue;
                        }
                        final BlockState above = level.getBlockState(pos.set(x, y + 1, z));

                        if (above.getFluidState().isEmpty() && (above.isAir() || above.canBeReplaced()))
                        {
                            WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.COBBLESTONE.defaultBlockState());
                            capped++;
                        }
                    }
                }
            }
        }
        lavaCursor.put(key, (cursor + LAVA_CHUNKS_PER_PATROL) % order.length);
        if (capped > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the lava warden capped {} exposed lava block(s) in the claim — that ground takes nobody now",
              colony.getName(), capped);
        }
    }

    private void clearClaimBushes(final ServerLevel level, final IColony colony)
    {
        final Set<Block> bushes = hazardBushes();
        if (bushes.isEmpty() || !AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            return;
        }
        final LinkedHashSet<Long> chunks = new LinkedHashSet<>(colony.getLoadedChunks());
        if (chunks.isEmpty())
        {
            return;
        }
        final Long[] order = chunks.toArray(new Long[0]);
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        java.util.Arrays.sort(order);
        final int cursor = bushCursor.getOrDefault(key, 0) % order.length;
        final List<SiteSelector.Footprint> sheltered = new ArrayList<>(ProtectedZones.footprintsFor(colony));
        int villageTop = colony.getCenter().getY();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            sheltered.add(SiteSelector.boxOf(building));
            if (building.getBuildingLevel() > 0)
            {
                villageTop = Math.max(villageTop, building.getPosition().getY());
            }
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final Map<Block, Integer> cleared = new LinkedHashMap<>();
        int swept = 0;
        for (; swept < BUSH_CHUNKS_PER_PATROL && swept < order.length && TickBudget.has(); swept++)
        {
            final ChunkPos chunk = new ChunkPos(order[(cursor + swept) % order.length]);
            if (!WorldUtil.isChunkLoaded(level, chunk.x, chunk.z))
            {
                continue;
            }
            TickBudget.spend(256);
            final List<SiteSelector.Footprint> local = new ArrayList<>();
            for (final SiteSelector.Footprint box : sheltered)
            {
                if (box.intersects(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ()))
                {
                    local.add(box);
                }
            }
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++)
                {
                    if (inFootprint(local, x, z))
                    {
                        continue;
                    }

                    final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (top > villageTop + 12 && !SiteSelector.solidBeneath(level, x, z, top, pos))
                    {
                        continue;
                    }
                    for (int y = top; y <= top + 1; y++)
                    {
                        removeBush(level, pos.set(x, y, z), bushes, cleared);
                    }
                }
            }
        }
        bushCursor.put(key, (cursor + swept) % order.length);
        if (!cleared.isEmpty())
        {
            ColonyAutopilot.LOGGER.info("[{}] the bush warden cleared {} off the village's land — they wound whoever walks through them",
              colony.getName(), describeBushes(cleared));
        }
    }

    @SubscribeEvent
    public void onCitizenDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof AbstractEntityCitizen victim) || !(victim.level() instanceof ServerLevel level) || !AutopilotConfig.masterOn())
        {
            return;
        }
        try
        {
            final Set<Block> bushes = hazardBushes();
            final ResourceLocation cause = event.getSource().typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
            boolean byBush = false;
            for (final Block bush : bushes)
            {
                byBush |= BuiltInRegistries.BLOCK.getKey(bush).equals(cause);
            }
            final IColony colony = victim.getCitizenColonyHandler().getColony();
            if (!byBush || colony == null || !AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
            {
                return;
            }
            final BlockPos spot = victim.blockPosition();
            final IColony landlord = IColonyManager.getInstance().getColonyByPosFromWorld(level, spot);
            if (landlord != null && landlord != colony)
            {
                return;
            }
            final List<SiteSelector.Footprint> zones = ProtectedZones.footprintsFor(colony);
            final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            final Map<Block, Integer> cleared = new LinkedHashMap<>();
            for (int dx = -BUSH_DEATH_REACH; dx <= BUSH_DEATH_REACH; dx++)
            {
                for (int dz = -BUSH_DEATH_REACH; dz <= BUSH_DEATH_REACH; dz++)
                {
                    final int x = spot.getX() + dx;
                    final int z = spot.getZ() + dz;
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4) || inFootprint(zones, x, z))
                    {
                        continue;
                    }
                    for (int dy = -BUSH_DEATH_REACH; dy <= BUSH_DEATH_REACH; dy++)
                    {
                        removeBush(level, pos.set(x, spot.getY() + dy, z), bushes, cleared);
                    }
                }
            }
            if (!cleared.isEmpty())
            {
                ColonyAutopilot.LOGGER.info("[{}] {} died to a bush at {} — cleared {} within {} blocks of the spot",
                  colony.getName(), victim.getName().getString(), spot.toShortString(), describeBushes(cleared), BUSH_DEATH_REACH);
            }
        }
        catch (final RuntimeException e)
        {

            ColonyAutopilot.LOGGER.warn("clearing the bushes at a death spot failed", e);
        }
    }

    private static Set<Block> hazardBushes()
    {
        final Set<Block> bushes = new HashSet<>();
        for (final String id : AutopilotConfig.HAZARD_BUSHES.get())
        {
            final ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null)
            {
                BuiltInRegistries.BLOCK.getOptional(key).ifPresent(bushes::add);
            }
        }
        return bushes;
    }

    private static void removeBush(final ServerLevel level, final BlockPos pos, final Set<Block> bushes, final Map<Block, Integer> cleared)
    {
        final Block block = level.getBlockState(pos).getBlock();
        if (bushes.contains(block))
        {
            WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
            cleared.merge(block, 1, Integer::sum);
        }
    }

    private static String describeBushes(final Map<Block, Integer> cleared)
    {
        final List<String> kinds = new ArrayList<>();
        for (final Map.Entry<Block, Integer> kind : cleared.entrySet())
        {
            kinds.add(kind.getValue() + " " + BuiltInRegistries.BLOCK.getKey(kind.getKey()).getPath().replace('_', ' ') + "(es)");
        }
        return String.join(", ", kinds);
    }

    private void killSporeSpreaders(final ServerLevel level, final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.SPORES_CREEP_GUARD) || !ModList.get().isLoaded(SporeCompat.MOD_ID))
        {
            return;
        }
        final Set<Long> claimed = colony.getLoadedChunks();
        if (claimed.isEmpty())
        {
            return;
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final long chunk : claimed)
        {
            final ChunkPos pos = new ChunkPos(chunk);
            minX = Math.min(minX, pos.getMinBlockX());
            minZ = Math.min(minZ, pos.getMinBlockZ());
            maxX = Math.max(maxX, pos.getMaxBlockX());
            maxZ = Math.max(maxZ, pos.getMaxBlockZ());
        }
        final AABB box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        int cleared = 0;
        for (final Entity spreader : level.getEntitiesOfClass(Entity.class, box, SporeCompat::isSporeSpreader))
        {
            final BlockPos at = spreader.blockPosition();

            if (IColonyManager.getInstance().getColonyByPosFromWorld(level, at) == colony)
            {
                spreader.discard();
                cleared++;
                markHotAround(key, at.getX() >> 4, at.getZ() >> 4, level.getGameTime());
            }
        }
        if (cleared > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the spore warden cleared {} spore spreader(s) from the town — the infection cannot take root inside the walls",
              colony.getName(), cleared);
            Milestones.alert(colony, Milestones.ALERT_INFECTED, "Your village is infected!");
        }
    }

    private void reactivePass(final ServerLevel level, final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.SPORES_CREEP_GUARD))
        {
            return;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<Long, Long> hot = hotChunks.get(key);
        if (hot == null || hot.isEmpty())
        {
            return;
        }
        final long now = level.getGameTime();
        hot.values().removeIf(coolAt -> now >= coolAt);
        if (hot.isEmpty())
        {
            return;
        }

        killSpreadersInHot(level, colony, key, hot, now);

        final Map<Block, Block> cure = SporeCompat.creepCure();
        if (cure.isEmpty())
        {
            return;
        }
        final List<Shelter> footprints = shelters(colony);
        final Set<Long> roads = growth.roadBlocks(colony, level);

        final Set<Long> claimed = colony.getLoadedChunks();
        final Long[] order = hot.keySet().toArray(new Long[0]);
        final int cursor = reactCursor.getOrDefault(key, 0) % order.length;
        final Relaid relaid = new Relaid();
        int remaining = REACT_BUDGET;
        int reverted = 0;
        int visited = 0;
        for (; visited < order.length && visited < REACT_CHUNKS_PER_PASS && remaining > 0; visited++)
        {
            final long chunkLong = order[(cursor + visited) % order.length];
            if (!claimed.isEmpty() && !claimed.contains(chunkLong))
            {
                hot.remove(chunkLong);
                continue;
            }
            final ChunkPos chunk = new ChunkPos(chunkLong);
            if (!WorldUtil.isChunkLoaded(level, chunk.x, chunk.z))
            {
                continue;
            }
            final int did = scrubChunk(level, colony, cure, chunk, remaining, footprints, roads, false, false, relaid);
            remaining -= did;
            reverted += did;
            if (did > 0)
            {
                markHot(key, chunkLong, now);
            }
            else
            {

                hot.remove(chunkLong);
            }
        }
        reactCursor.put(key, (cursor + Math.max(1, visited)) % order.length);

        if (reverted > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the creep guard drove the infection back — {} block(s) reverted along the front ({} laid back as road)",
              colony.getName(), reverted, relaid.roads);
            Milestones.alert(colony, Milestones.ALERT_INFECTED, "Your village is infected!");
        }
    }

    private void killSpreadersInHot(final ServerLevel level, final IColony colony, final ColonyId key, final Map<Long, Long> hot, final long now)
    {
        if (!ModList.get().isLoaded(SporeCompat.MOD_ID))
        {
            return;
        }

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final long chunkLong : hot.keySet())
        {
            final ChunkPos chunk = new ChunkPos(chunkLong);
            minX = Math.min(minX, chunk.getMinBlockX());
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxX = Math.max(maxX, chunk.getMaxBlockX());
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ());
        }
        final AABB box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
        int cleared = 0;
        for (final Entity spreader : level.getEntitiesOfClass(Entity.class, box, SporeCompat::isSporeSpreader))
        {
            final BlockPos at = spreader.blockPosition();
            if (!hot.containsKey(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4)))
            {
                continue;
            }
            if (IColonyManager.getInstance().getColonyByPosFromWorld(level, at) == colony)
            {
                spreader.discard();
                cleared++;
                markHotAround(key, at.getX() >> 4, at.getZ() >> 4, now);
            }
        }
        if (cleared > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the spore warden cut {} spore spreader(s) at the front",
              colony.getName(), cleared);
            Milestones.alert(colony, Milestones.ALERT_INFECTED, "Your village is infected!");
        }
    }

    private void breakBorderSiege(final ServerLevel level, final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.BORDER_SIEGE_BREAKER) || !ModList.get().isLoaded(SporeCompat.MOD_ID))
        {
            return;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<Long, Long> hot = hotChunks.get(key);
        final Set<Long> claimed = colony.getLoadedChunks();
        final Map<Long, Integer> pressure = borderPressure.computeIfAbsent(key, k -> new HashMap<>());
        if (claimed.isEmpty() || hot == null || hot.isEmpty())
        {
            pressure.clear();
            return;
        }
        final long now = level.getGameTime();
        final List<Long> pressured = new ArrayList<>();
        final Set<Long> edges = new HashSet<>();
        for (final long chunkLong : claimed)
        {
            final ChunkPos chunk = new ChunkPos(chunkLong);
            if (!isClaimEdge(claimed, chunk))
            {
                continue;
            }
            edges.add(chunkLong);
            final Long coolAt = hot.get(chunkLong);
            if (coolAt == null || now >= coolAt)
            {
                pressure.remove(chunkLong);
                continue;
            }
            final int streak = pressure.getOrDefault(chunkLong, 0) + 1;
            pressure.put(chunkLong, streak);
            if (streak >= SIEGE_PATROLS)
            {
                pressured.add(chunkLong);
            }
        }

        pressure.keySet().retainAll(edges);
        if (pressured.isEmpty())
        {
            return;
        }
        int cut = 0;
        for (final long chunkLong : pressured)
        {
            cut += cullHalo(level, claimed, new ChunkPos(chunkLong));
        }
        if (cut > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the siege-breaker cut {} spore spreader(s) in the wild past a besieged border — the siege engine dies, not just the creep it throws",
              colony.getName(), cut);
            Milestones.alert(colony, Milestones.ALERT_INFECTED, "Your village is infected!");
        }
    }

    private static boolean isClaimEdge(final Set<Long> claimed, final ChunkPos chunk)
    {
        return !claimed.contains(ChunkPos.asLong(chunk.x - 1, chunk.z))
                 || !claimed.contains(ChunkPos.asLong(chunk.x + 1, chunk.z))
                 || !claimed.contains(ChunkPos.asLong(chunk.x, chunk.z - 1))
                 || !claimed.contains(ChunkPos.asLong(chunk.x, chunk.z + 1));
    }

    private int cullHalo(final ServerLevel level, final Set<Long> claimed, final ChunkPos edge)
    {
        final int westPad = claimed.contains(ChunkPos.asLong(edge.x - 1, edge.z)) ? 0 : SIEGE_HALO_BLOCKS;
        final int eastPad = claimed.contains(ChunkPos.asLong(edge.x + 1, edge.z)) ? 0 : SIEGE_HALO_BLOCKS;
        final int northPad = claimed.contains(ChunkPos.asLong(edge.x, edge.z - 1)) ? 0 : SIEGE_HALO_BLOCKS;
        final int southPad = claimed.contains(ChunkPos.asLong(edge.x, edge.z + 1)) ? 0 : SIEGE_HALO_BLOCKS;
        final AABB halo = new AABB(
          edge.getMinBlockX() - westPad, level.getMinBuildHeight(), edge.getMinBlockZ() - northPad,
          edge.getMaxBlockX() + 1 + eastPad, level.getMaxBuildHeight(), edge.getMaxBlockZ() + 1 + southPad);
        int cut = 0;
        for (final Entity spreader : level.getEntitiesOfClass(Entity.class, halo, SporeCompat::isSporeSpreader))
        {
            if (IColonyManager.getInstance().getColonyByPosFromWorld(level, spreader.blockPosition()) != null)
            {
                continue;
            }
            spreader.discard();
            cut++;
        }
        return cut;
    }

    private void markHot(final ColonyId key, final long chunkLong, final long now)
    {
        hotChunks.computeIfAbsent(key, k -> new HashMap<>()).put(chunkLong, now + HOT_TTL_TICKS);
        warZone.computeIfAbsent(key, k -> new HashSet<>()).add(chunkLong);
    }

    private void markHotAround(final ColonyId key, final int chunkX, final int chunkZ, final long now)
    {
        final Map<Long, Long> hot = hotChunks.computeIfAbsent(key, k -> new HashMap<>());
        final Set<Long> zone = warZone.computeIfAbsent(key, k -> new HashSet<>());
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                hot.put(ChunkPos.asLong(chunkX + dx, chunkZ + dz), now + HOT_TTL_TICKS);
                zone.add(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
            }
        }
    }

    private record Shelter(SiteSelector.Footprint box, int floor)
    {
    }

    private static final int ZONE = Integer.MIN_VALUE;

    private static final int EVERY_HEIGHT = Integer.MIN_VALUE + 1;

    private static List<Shelter> shelters(final IColony colony)
    {
        final List<WorkOrderBuilding> orders = colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);
        final List<Shelter> shelters = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final boolean standing = building.getBuildingLevel() > 0 && !building.isDeconstructed()
                                       && building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutMiner
                                       && !UpgradeDirector.hasOpenOrder(building, orders);
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            shelters.add(new Shelter(SiteSelector.boxOf(building),
              standing ? Math.min(corners.getA().getY(), corners.getB().getY()) : EVERY_HEIGHT));
        }
        for (final SiteSelector.Footprint zone : ProtectedZones.footprintsFor(colony))
        {
            shelters.add(new Shelter(zone, ZONE));
        }
        return shelters;
    }

    private static int shelterFloor(final List<Shelter> shelters, final int x, final int z)
    {
        int floor = Integer.MAX_VALUE;
        for (final Shelter shelter : shelters)
        {
            if (shelter.box().intersects(x, z, x, z))
            {
                floor = Math.min(floor, shelter.floor());
            }
        }
        return floor;
    }

    private static List<SiteSelector.Footprint> buildingBoxes(final IColony colony)
    {
        final List<SiteSelector.Footprint> boxes = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            boxes.add(SiteSelector.boxOf(building));
        }

        boxes.addAll(ProtectedZones.footprintsFor(colony));
        return boxes;
    }
}
