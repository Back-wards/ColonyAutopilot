// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.tileentities.AbstractTileEntityBarrel;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule;
import com.minecolonies.core.colony.buildings.modules.MinerLevelManagementModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingComposter;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.util.MinerLevel;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JobWatch
{

    private static final int STALL_DAYS = 3;

    private static final int MAX_STRIKES = 3;

    private static Set<Block> watched;

    private static Set<Block> watched()
    {
        if (watched == null)
        {
            watched = Set.of(
              ModBlocks.blockHutMiner, ModBlocks.blockHutLumberjack, ModBlocks.blockHutFarmer,
              ModBlocks.blockHutFisherman, ModBlocks.blockHutComposter, ModBlocks.blockHutBeekeeper,
              ModBlocks.blockHutFlorist, ModBlocks.blockHutDeliveryman);
        }
        return watched;
    }

    private static final class Pulse
    {
        long total = Long.MIN_VALUE;
        int lastMoveDay;
        int strikes;
    }

    private final Map<ColonyId, Map<BlockPos, Pulse>> pulses = new HashMap<>();

    private final Set<GlobalPos> recallWarned = new HashSet<>();

    void clear()
    {
        pulses.clear();
        recallWarned.clear();
    }

    Map<ColonyId, ?> state()
    {
        return pulses;
    }

    void tend(final IColony colony, final IBuilding building)
    {
        final Block hut = building.getBuildingType().getBuildingBlock();
        if (hut == ModBlocks.blockHutWareHouse)
        {
            assertCourierStaffing(colony, building);
            return;
        }
        if (hut == ModBlocks.blockHutCook)
        {
            assertCookStaffing(colony, building);
            return;
        }
        if (!watched().contains(hut))
        {
            return;
        }
        final BuildingStatisticsModule stats = building.getModule(BuildingStatisticsModule.class);
        long total = 0;
        if (stats != null)
        {
            for (final String id : stats.getBuildingStatisticsManager().getStatTypes())
            {
                total += stats.getBuildingStatisticsManager().getStatTotal(id);
            }
        }

        int unpaid = 0;
        if (AutopilotConfig.progressionMode(colony))
        {
            final Set<IToken<?>> own = new HashSet<>();
            for (final var open : building.getOpenRequestsByRequestableType().values())
            {
                own.addAll(open);
            }
            unpaid = Treasury.waitingUnder(colony, own);
        }
        final Map<BlockPos, Pulse> seen = pulses.get(ColonyAutopilot.colonyKey(colony));
        final Pulse last = seen == null ? null : seen.get(building.getID());
        final String still = last == null || last.total == Long.MIN_VALUE ? "first look" : (colony.getDay() - last.lastMoveDay) + " game-day(s) since its last move";
        final String branch = watch(colony, building, hut, stats != null, total, unpaid);

        ColonyAutopilot.LOGGER.debug("[{}] job watch: the {} at {} — statistics total {}, {}, {} request(s) waiting for funds: {}", colony.getName(),
          SiteSelector.plainName(building), building.getPosition().toShortString(), stats == null ? "none" : total, still, unpaid, branch);
    }

    private String watch(final IColony colony, final IBuilding building, final Block hut, final boolean hasStats, final long total, final int unpaid)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final int today = colony.getDay();
        if (building.getAllAssignedCitizen().isEmpty())
        {

            final Map<BlockPos, Pulse> colonyPulses = pulses.get(key);
            final Pulse waiting = colonyPulses == null ? null : colonyPulses.get(building.getID());
            if (waiting != null)
            {
                waiting.lastMoveDay = today;
            }
            return "no worker, the clock waits";
        }
        if (!hasStats)
        {
            return "no statistics to read";
        }
        final Pulse pulse = pulses.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(building.getID(), k -> new Pulse());
        if (hut == ModBlocks.blockHutDeliveryman && !hasQueuedWork(building))
        {

            pulse.total = total;
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            return "a courier at rest, no work queued";
        }
        if (total != pulse.total)
        {
            pulse.total = total;
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            return "moved";
        }
        if (pulse.strikes >= MAX_STRIKES)
        {
            return "parked after " + (MAX_STRIKES - 1) + " remedies";
        }
        if (today - pulse.lastMoveDay < STALL_DAYS)
        {
            return "still, a strike at " + STALL_DAYS + " game-days";
        }

        for (final ICitizenData citizen : building.getAllAssignedCitizen())
        {
            if (citizen.getSaturation() < CitizenConstants.LOW_SATURATION)
            {
                pulse.lastMoveDay = today;
                ColonyAutopilot.LOGGER.info("[{}] the {} has produced nothing for {} game-days — but its worker {} is starving, not shirking (last seen at {}; strike held; the village owes him a meal)",
                  colony.getName(), building.getBuildingDisplayName(), STALL_DAYS, citizen.getName(), citizen.getLastPosition().toShortString());
                return "held: its worker is starving";
            }
        }

        if (unpaid > 0)
        {
            pulse.lastMoveDay = today;
            ColonyAutopilot.LOGGER.debug("[{}] the {} produces nothing while {} of its requests wait for funds — the watch waits",
              colony.getName(), building.getBuildingDisplayName(), unpaid);
            return "held: waiting for funds";
        }

        if (hut == ModBlocks.blockHutFarmer && colony.getWorld() instanceof ServerLevel farmland
              && FarmFields.hasPlantedCrops(farmland, building))
        {
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            ColonyAutopilot.LOGGER.info("[{}] the {}'s counters sit still but a field of it is sown or growing — crops are the harvest on its way, the watch waits",
              colony.getName(), building.getBuildingDisplayName());
            return "held: a field is sown or growing";
        }

        if (hut == ModBlocks.blockHutMiner && ownsOpenShaft(colony, building))
        {
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            ColonyAutopilot.LOGGER.info("[{}] the {}'s counters sit still but its shaft's work order is open — the mine is being dug, the watch waits",
              colony.getName(), building.getBuildingDisplayName());
            return "held: its shaft's work order is open";
        }

        if (hut == ModBlocks.blockHutLumberjack && colony.getWorld() instanceof ServerLevel woods
              && ForesterGrove.waitingOnGrowth(woods, colony, building.getPosition()))
        {
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            ColonyAutopilot.LOGGER.info("[{}] the {} waits on its grove — saplings stand where trees will; the watch waits with him",
              colony.getName(), building.getBuildingDisplayName());
            return "held: its grove is growing";
        }

        if (hut == ModBlocks.blockHutBeekeeper && building instanceof BuildingBeekeeper apiary
              && colony.getWorld() instanceof ServerLevel hiveLevel && honeyBuilding(hiveLevel, apiary))
        {
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            ColonyAutopilot.LOGGER.info("[{}] the {}'s counters sit still but its hives are filling — honey is the harvest on its way, the watch waits",
              colony.getName(), building.getBuildingDisplayName());
            return "held: its hives are filling";
        }

        if (hut == ModBlocks.blockHutComposter && building instanceof BuildingComposter composter
              && colony.getWorld() instanceof ServerLevel barrelLevel && composting(barrelLevel, composter))
        {
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            ColonyAutopilot.LOGGER.info("[{}] the {}'s counters sit still but its barrels are composting — the batch is on its way, the watch waits",
              colony.getName(), building.getBuildingDisplayName());
            return "held: its barrels are composting";
        }

        if (hut == ModBlocks.blockHutFisherman && colony.getWorld() instanceof ServerLevel fishery
              && FishingPond.waitsForWater(fishery, colony, building.getPosition()))
        {
            pulse.lastMoveDay = today;
            pulse.strikes = 0;
            ColonyAutopilot.LOGGER.info("[{}] the {}'s counters sit still but it has no water to fish — it waits for its pond, and the watch waits with it",
              colony.getName(), building.getBuildingDisplayName());
            return "held: no water to fish";
        }

        pulse.lastMoveDay = today;
        pulse.strikes++;

        if (pulse.strikes == 1)
        {
            if (hut == ModBlocks.blockHutDeliveryman && walkCouriersHome(colony, building))
            {
                return "strike 1: the couriers recalled";
            }
            if (hut == ModBlocks.blockHutMiner)
            {

                final MinerLevelManagementModule levels = building.getModule(MinerLevelManagementModule.class);
                final MinerLevel current = levels == null ? null : levels.getCurrentLevel();
                if (current != null)
                {
                    levels.repairLevel(levels.getLevelId(current));
                    ColonyAutopilot.LOGGER.warn("[{}] the {} has produced nothing for {} game-days — repairing the mine's current level",
                      colony.getName(), SiteSelector.plainName(building), STALL_DAYS);
                    return "strike 1: the mine's level repaired";
                }
            }
            ColonyAutopilot.LOGGER.warn("[{}] the {} has produced nothing for {} game-days — under watch (a fresh hire follows if the freeze holds)",
              colony.getName(), SiteSelector.plainName(building), STALL_DAYS);
            return "strike 1: under watch";
        }
        if (pulse.strikes < MAX_STRIKES)
        {

            final WorkerBuildingModule crew = building.getModule(WorkerBuildingModule.class);
            if (crew != null)
            {
                for (final var citizenData : List.copyOf(building.getAllAssignedCitizen()))
                {
                    crew.removeCitizen(citizenData);
                }
                ColonyAutopilot.LOGGER.warn("[{}] the {} has produced nothing for {} game-days — its worker was dismissed for a fresh hire (strike {})",
                  colony.getName(), SiteSelector.plainName(building), STALL_DAYS, pulse.strikes);
            }
            return "strike " + pulse.strikes + (crew == null ? ": no crew to dismiss" : ": its worker dismissed");
        }
        ColonyAutopilot.LOGGER.warn("[{}] the {} STILL produces nothing after {} remedies — something is mechanically wrong there (watch parked; needs eyes)",
          colony.getName(), SiteSelector.plainName(building), MAX_STRIKES - 1);
        if (building instanceof BuildingBeekeeper beekeeper && colony.getWorld() instanceof ServerLevel level)
        {
            final StringBuilder census = new StringBuilder();
            for (final BlockPos hive : beekeeper.getHives())
            {
                if (!WorldUtil.isChunkLoaded(level, hive.getX() >> 4, hive.getZ() >> 4))
                {
                    census.append(" [").append(hive.toShortString()).append(" unloaded]");
                    continue;
                }
                final BlockState state = level.getBlockState(hive);
                final int honey = state.hasProperty(BeehiveBlock.HONEY_LEVEL) ? state.getValue(BeehiveBlock.HONEY_LEVEL) : -1;
                final int inside = level.getBlockEntity(hive) instanceof BeehiveBlockEntity nest ? nest.getOccupantCount() : -1;
                final int nearby = level.getEntitiesOfClass(Bee.class, new AABB(hive).inflate(24, 8, 24), Bee::isAlive).size();
                census.append(" [").append(state.getBlock().getName().getString()).append(" at ").append(hive.toShortString())
                  .append(": honey ").append(honey).append(", bees inside ").append(inside).append(", bees about ").append(nearby).append(']');
            }
            ColonyAutopilot.LOGGER.info("[{}] apiary post-mortem — {} registered hive(s):{}",
              colony.getName(), beekeeper.getHives().size(), census.length() == 0 ? " none" : census);
        }
        return "strike " + pulse.strikes + ": the watch parked";
    }

    private static boolean ownsOpenShaft(final IColony colony, final IBuilding mine)
    {
        for (final WorkOrderMiner order : colony.getWorkManager().getWorkOrdersOfType(WorkOrderMiner.class))
        {
            if (order.canBuild(mine))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQueuedWork(final IBuilding building)
    {
        for (final ICitizenData citizen : building.getAllAssignedCitizen())
        {
            if (citizen.getJob() instanceof JobDeliveryman courier && !courier.getTaskQueue().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean honeyBuilding(final ServerLevel level, final BuildingBeekeeper apiary)
    {
        for (final BlockPos hive : apiary.getHives())
        {
            if (!WorldUtil.isChunkLoaded(level, hive.getX() >> 4, hive.getZ() >> 4))
            {
                continue;
            }
            final BlockState state = level.getBlockState(hive);
            if (state.hasProperty(BeehiveBlock.HONEY_LEVEL))
            {
                final int honey = state.getValue(BeehiveBlock.HONEY_LEVEL);
                if (honey > 0 && honey < BeehiveBlock.MAX_HONEY_LEVELS)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean composting(final ServerLevel level, final BuildingComposter composter)
    {
        for (final BlockPos barrel : composter.getBarrels())
        {
            if (!WorldUtil.isChunkLoaded(level, barrel.getX() >> 4, barrel.getZ() >> 4))
            {
                continue;
            }
            if (level.getBlockEntity(barrel) instanceof AbstractTileEntityBarrel bin && bin.checkIfWorking())
            {
                return true;
            }
        }
        return false;
    }

    private boolean walkCouriersHome(final IColony colony, final IBuilding courierHut)
    {
        boolean walked = false;
        for (final ICitizenData citizen : List.copyOf(courierHut.getAllAssignedCitizen()))
        {
            if (!(citizen.getJob() instanceof JobDeliveryman courier))
            {
                continue;
            }
            final IWareHouse warehouse = courier.findWareHouse();
            final var entity = citizen.getEntity();
            if (warehouse == null || entity.isEmpty()
                  || !WorldUtil.isChunkLoaded(colony.getWorld(), warehouse.getPosition().getX() >> 4, warehouse.getPosition().getZ() >> 4)
                  || warehouse.getTileEntity() == null)
            {
                continue;
            }
            TeleportHelper.teleportCitizen(entity.get(), colony.getWorld(), warehouse.getPosition());
            warehouse.getTileEntity().dumpInventoryIntoWareHouse(entity.get().getInventoryCitizen());
            for (final IToken<?> token : courier.getTaskQueue())
            {

                colony.getRequestManager().updateRequestState(token, RequestState.FAILED);
                courier.onTaskDeletion(token);
            }
            citizen.setWorking(false);
            walked = true;
        }
        if (walked && recallWarned.add(GlobalPos.of(colony.getDimension(), courierHut.getPosition())))
        {
            ColonyAutopilot.LOGGER.warn("[{}] the {} at {} moved nothing for {} game-days with work queued — its couriers were recalled to the warehouse, packs emptied, tasks re-filed (its later recalls this session are logged at debug)",
              colony.getName(), SiteSelector.plainName(courierHut), courierHut.getPosition().toShortString(), STALL_DAYS);
        }
        else if (walked)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the {} at {} moved nothing for {} game-days again — its couriers were recalled once more",
              colony.getName(), SiteSelector.plainName(courierHut), courierHut.getPosition().toShortString(), STALL_DAYS);
        }
        return walked;
    }

    private void assertCookStaffing(final IColony colony, final IBuilding restaurant)
    {
        if (restaurant.getBuildingLevel() <= 0 || !restaurant.getAllAssignedCitizen().isEmpty())
        {
            return;
        }
        final WorkerBuildingModule crew = restaurant.getModule(WorkerBuildingModule.class);
        if (crew == null || !com.minecolonies.core.util.BuildingUtils.canAutoHire(restaurant, crew.getHiringMode(), crew.getJobEntry()))
        {
            return;
        }
        final ICitizenData jobless = colony.getCitizenManager().getJoblessCitizen();
        if (jobless != null && crew.assignCitizen(jobless))
        {
            ColonyAutopilot.LOGGER.info("[{}] assigned {} to the {} — the village must never stand cookless",
              colony.getName(), jobless.getName(), restaurant.getBuildingDisplayName());
        }
    }

    private void assertCourierStaffing(final IColony colony, final IBuilding warehouse)
    {
        if (warehouse.getBuildingLevel() <= 0)
        {
            return;
        }
        final var buildings = colony.getServerBuildingManager().getBuildings().values();
        for (final IBuilding building : buildings)
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutDeliveryman
                  && !building.getAllAssignedCitizen().isEmpty())
            {
                return;
            }
        }
        for (final IBuilding building : buildings)
        {
            if (building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutDeliveryman
                  || building.getBuildingLevel() <= 0)
            {
                continue;
            }
            final WorkerBuildingModule crew = building.getModule(WorkerBuildingModule.class);
            if (crew == null || !com.minecolonies.core.util.BuildingUtils.canAutoHire(building, crew.getHiringMode(), crew.getJobEntry()))
            {
                continue;
            }
            final ICitizenData jobless = colony.getCitizenManager().getJoblessCitizen();
            if (jobless != null && crew.assignCitizen(jobless))
            {
                ColonyAutopilot.LOGGER.info("[{}] assigned {} to the {} — the warehouse was resolving requests with no courier alive to carry them",
                  colony.getName(), jobless.getName(), building.getBuildingDisplayName());
                return;
            }
        }
    }
}
