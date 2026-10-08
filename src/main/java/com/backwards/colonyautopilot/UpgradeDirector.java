// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingAddedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.util.Tuple;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.util.TeleportHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UpgradeDirector
{
    private int tickCounter = 26;

    private final ColonyRota rota = new ColonyRota();

    private final ResearchDirector research;

    UpgradeDirector(final ResearchDirector research)
    {
        this.research = research;
    }

    private GrowthDirector growth;

    void setGrowthDirector(final GrowthDirector growth)
    {
        this.growth = growth;
    }

    void queueOutfit(final IBuilding building)
    {
        if (growth != null)
        {
            growth.queueOutfit(building);
        }
    }

    private final Set<IColony> pending = new HashSet<>();

    private final Map<ColonyId, Long> pendingEvaluatedAt = new HashMap<>();

    private static final long PENDING_COALESCE_TICKS = 20L;

    private final Map<ColonyId, Map<Integer, OrderPulse>> orderPulse = new HashMap<>();

    private record OrderPulse(BlockPos progressPos, String stage, int available, int needed, BlockPos builderPos,
      long since, long coreSince, long cursorSince, boolean nudged)
    {
    }

    private final Map<ColonyId, Map<BlockPos, Integer>> builderJamStrikes = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Integer>> recycleStrikes = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> buildParkedUntil = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Integer>> parkCount = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, BlockPos>> failedClaimers = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, List<ItemStack>>> paidAtSite = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> crewlessSince = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Integer>> heldBelowHall = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> unbuiltSince = new HashMap<>();

    private static final long UNBUILT_PATIENCE_TICKS = 24000L;

    private final Map<ColonyId, Set<BlockPos>> gateWarned = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> refusedUntil = new HashMap<>();

    private static final long REFUSAL_RETRY_TICKS = 24000L;

    private final Map<ColonyId, Set<BlockPos>> recycledUpgrades = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> recycledAt = new HashMap<>();

    private static final long RECYCLE_HOLD_CAP_TICKS = 24000L;

    private final Map<ColonyId, Map<BlockPos, Long>> addedAt = new HashMap<>();

    private static final long ADDED_GRACE_TICKS = 2400L;

    public void subscribeToMineColonies()
    {
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(BuildingConstructionModEvent.class, this::onConstructionCompleted);
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(BuildingAddedModEvent.class, this::onBuildingAdded);
        ColonyAutopilot.LOGGER.info("UpgradeDirector subscribed to MineColonies events");
    }

    private void onConstructionCompleted(final BuildingConstructionModEvent event)
    {
        final IColony colony = event.getBuilding().getColony();

        if (colony != null && event.getWorkOrder() != null && colony.getWorld() instanceof ServerLevel level)
        {
            ColonyGrounds.get(level).setTornDown(colony.getID(), event.getBuilding().getPosition(),
              event.getWorkOrder().getWorkOrderType() == WorkOrderType.REMOVE);

            ColonyGrounds.get(level).settleRepair(colony.getID(), event.getBuilding().getPosition());
        }
        if (colony != null)
        {
            pending.add(colony);

            final Map<BlockPos, Integer> strikes = recycleStrikes.get(ColonyAutopilot.colonyKey(colony));
            if (strikes != null)
            {
                strikes.remove(event.getBuilding().getID());
            }
            final Map<BlockPos, Long> parked = buildParkedUntil.get(ColonyAutopilot.colonyKey(colony));
            if (parked != null)
            {
                parked.remove(event.getBuilding().getID());
            }
            final Map<BlockPos, Integer> rounds = parkCount.get(ColonyAutopilot.colonyKey(colony));
            if (rounds != null)
            {
                rounds.remove(event.getBuilding().getID());
            }
            final Map<BlockPos, BlockPos> avoid = failedClaimers.get(ColonyAutopilot.colonyKey(colony));
            if (avoid != null)
            {
                avoid.remove(event.getBuilding().getID());
            }
            final Map<BlockPos, List<ItemStack>> paid = paidAtSite.get(ColonyAutopilot.colonyKey(colony));
            if (paid != null)
            {
                paid.remove(event.getBuilding().getID());
            }
            final Set<BlockPos> recycled = recycledUpgrades.get(ColonyAutopilot.colonyKey(colony));
            if (recycled != null)
            {
                recycled.remove(event.getBuilding().getID());
            }
            final Map<BlockPos, Long> recycledTimes = recycledAt.get(ColonyAutopilot.colonyKey(colony));
            if (recycledTimes != null)
            {
                recycledTimes.remove(event.getBuilding().getID());
            }
            final Map<BlockPos, Long> refused = refusedUntil.get(ColonyAutopilot.colonyKey(colony));
            if (refused != null)
            {
                refused.remove(event.getBuilding().getID());
            }

            final Map<BlockPos, Integer> crewStrikes = builderJamStrikes.get(ColonyAutopilot.colonyKey(colony));
            if (crewStrikes != null && event.getWorkOrder() != null && event.getWorkOrder().getClaimedBy() != null)
            {
                crewStrikes.remove(event.getWorkOrder().getClaimedBy());
            }
        }
    }

    private void onBuildingAdded(final BuildingAddedModEvent event)
    {
        final IColony colony = event.getBuilding().getColony();
        if (colony != null && colony.getWorld() instanceof ServerLevel level)
        {
            addedAt.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>()).put(event.getBuilding().getID(), level.getGameTime());
        }
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        pending.clear();
        pendingEvaluatedAt.clear();
        orderPulse.clear();
        recycleStrikes.clear();
        buildParkedUntil.clear();
        parkCount.clear();
        failedClaimers.clear();
        paidAtSite.clear();
        builderJamStrikes.clear();
        crewlessSince.clear();
        heldBelowHall.clear();
        unbuiltSince.clear();
        gateWarned.clear();
        recycledUpgrades.clear();
        recycledAt.clear();
        refusedUntil.clear();
        addedAt.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {

        pending.clear();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {
            pending.clear();
            return;
        }

        final List<IColony> toEvaluate = new ArrayList<>();
        if (++tickCounter >= AutopilotConfig.UPGRADE_CHECK_INTERVAL_SECONDS.get() * 20)
        {
            tickCounter = 0;
            toEvaluate.addAll(IColonyManager.getInstance().getAllColonies());
            pending.clear();

            final Set<ColonyId> live = new HashSet<>();
            for (final IColony colony : toEvaluate)
            {
                live.add(ColonyAutopilot.colonyKey(colony));
            }
            ColonyAutopilot.retainColonies(live, orderPulse, recycleStrikes, buildParkedUntil, parkCount, failedClaimers, paidAtSite, builderJamStrikes, crewlessSince, heldBelowHall, unbuiltSince, pendingEvaluatedAt, gateWarned, recycledUpgrades, recycledAt, refusedUntil, addedAt);
        }
        else if (!pending.isEmpty())
        {
            for (final Iterator<IColony> it = pending.iterator(); it.hasNext(); )
            {
                final IColony colony = it.next();
                if (!(colony.getWorld() instanceof ServerLevel level))
                {
                    it.remove();
                    toEvaluate.add(colony);
                    continue;
                }
                final Long last = pendingEvaluatedAt.get(ColonyAutopilot.colonyKey(colony));
                if (last != null && level.getGameTime() - last < PENDING_COALESCE_TICKS)
                {
                    continue;
                }
                pendingEvaluatedAt.put(ColonyAutopilot.colonyKey(colony), level.getGameTime());
                it.remove();
                toEvaluate.add(colony);
            }
        }

        for (final IColony colony : toEvaluate)
        {
            rota.add(event.getServer(), colony);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {
            try
            {
                evaluate(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Autopilot upgrade evaluation failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void evaluate(final IColony colony)
    {
        if (!AutopilotConfig.get(colony, AutopilotConfig.AUTO_UPGRADE_ENABLED))
        {
            return;
        }
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }

        final ColonyId key = ColonyAutopilot.colonyKey(colony);

        watchStuckOrders(colony, level, key);

        final Set<BlockPos> recycled = recycledUpgrades.get(key);
        if (recycled != null)
        {
            recycled.removeIf(site -> colony.getServerBuildingManager().getBuilding(site) == null);
        }

        List<WorkOrderBuilding> openOrders = colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);

        final List<IBuilding> staffedHuts = new ArrayList<>();
        final List<IBuilding> crewlessHuts = new ArrayList<>();
        int townHallLevel = 0;
        final List<IBuilding> candidates = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingBuilder)
            {
                if (!building.getAllAssignedCitizen().isEmpty())
                {
                    staffedHuts.add(building);
                }
                else
                {

                    crewlessHuts.add(building);
                }
            }

            else if (building.getBuildingLevel() > 0 && building.getAllAssignedCitizen().isEmpty()
                       && building.getModule(WorkerBuildingModule.class) != null
                       && !(building instanceof AbstractBuildingGuards))
            {
                crewlessHuts.add(building);
            }
            if (building instanceof BuildingTownHall)
            {
                townHallLevel = building.getBuildingLevel();
            }
            if (building.getBuildingLevel() < building.getMaxBuildingLevel()

                  && !tornDown(building)

                  && !(AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES)
                        && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGraveyard))
            {
                candidates.add(building);
            }
        }

        staffCrewlessHuts(colony, level, key, crewlessHuts);

        if (openOrders.stream().noneMatch(order -> order.getWorkOrderType() == WorkOrderType.REPAIR)
              && drainOneRepair(colony, level, openOrders))
        {

            openOrders = colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);
        }

        final Map<Block, Integer> researchWants = research.researchBlockedLevels(colony);
        final int townHall = townHallLevel;
        final Integer wantedUniversity = researchWants.get(ModBlocks.blockHutUniversity);

        if (AutopilotConfig.get(colony, AutopilotConfig.INSTANT_UNIVERSITY))
        {

            if (wantedUniversity != null)
            {
                for (final IBuilding building : candidates)
                {

                    if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutUniversity
                          && building.getBuildingLevel() < Math.min(wantedUniversity, building.getMaxBuildingLevel())
                          && instantOpen(colony, building, wantedUniversity, townHall))
                    {
                        return;
                    }
                }
            }

            final Integer wantedHome = researchWants.get(ModBlocks.blockHutHome);
            if (wantedHome != null)
            {
                IBuilding bestHome = null;
                for (final IBuilding home : colony.getServerBuildingManager().getBuildings().values())
                {
                    if (home.getBuildingType().getBuildingBlock() == ModBlocks.blockHutHome
                          && home.getBuildingLevel() > 0
                          && (bestHome == null || home.getBuildingLevel() > bestHome.getBuildingLevel()))
                    {
                        bestHome = home;
                    }
                }
                if (bestHome != null && instantOpen(colony, bestHome, wantedHome, townHall))
                {
                    return;
                }
            }
        }

        final List<IBuilding> freeHuts = freeCrews(staffedHuts, openOrders);
        if (staffedHuts.isEmpty() ? !openOrders.isEmpty() : freeHuts.isEmpty())
        {
            return;
        }

        final var census = colony.getCitizenManager();

        final boolean housingCapped = census.getCurrentCitizenCount() >= census.getMaxCitizens()
              && census.getMaxCitizens() < census.maxCitizensFromResearch();

        for (final Iterator<IBuilding> overCap = candidates.iterator(); overCap.hasNext(); )
        {
            final IBuilding building = overCap.next();
            if (building instanceof BuildingTownHall || building.getBuildingLevel() <= townHall)
            {
                continue;
            }
            overCap.remove();
            final Integer lastHeldAt = heldBelowHall.computeIfAbsent(key, k -> new HashMap<>())
                                         .put(building.getID(), building.getBuildingLevel());
            if (lastHeldAt == null || lastHeldAt != building.getBuildingLevel())
            {
                ColonyAutopilot.LOGGER.info("[{}] {} holds at level {} until the Town Hall (level {}) catches up",
                  colony.getName(), building.getBuildingDisplayName(), building.getBuildingLevel(), townHall);
            }
        }

        final Map<BlockPos, Long> recycledSites = recycledAt.getOrDefault(key, Map.of());
        for (final IBuilding building : candidates)
        {
            if (recycledSites.containsKey(building.getID()) && (staffedHuts.isEmpty() || servableBy(freeHuts, building))
                  && readyForOrder(colony, building, openOrders) && request(colony, level, building))
            {
                return;
            }
        }

        final IBuilding hallFirst = hallRuleSite(colony, candidates, freeHuts, openOrders, townHall);
        if (hallFirst != null && (staffedHuts.isEmpty() || servableBy(freeHuts, hallFirst)) && readyForOrder(colony, hallFirst, openOrders)
              && request(colony, level, hallFirst, true))
        {
            return;
        }

        final Map<BlockPos, Long> waiting = unbuiltSince.computeIfAbsent(key, k -> new HashMap<>());
        waiting.keySet().removeIf(site ->
        {
            final IBuilding placed = colony.getServerBuildingManager().getBuilding(site);
            return placed == null || placed.getBuildingLevel() > 0;
        });
        final List<IBuilding> overdue = new ArrayList<>();
        for (final IBuilding building : candidates)
        {
            if (building.getBuildingLevel() == 0)
            {
                final long since = waiting.computeIfAbsent(building.getID(), site -> level.getGameTime());
                if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBarracks
                      || level.getGameTime() - since >= UNBUILT_PATIENCE_TICKS)
                {
                    overdue.add(building);
                }
            }
        }
        overdue.sort(Comparator
          .comparingInt((IBuilding b) -> upgradeClass(b, townHall, researchWants, housingCapped))
          .thenComparingInt(b -> growth == null ? Integer.MAX_VALUE : growth.planRank(b.getBuildingType().getBuildingBlock()))
          .thenComparing(IBuilding::getID));
        for (final IBuilding building : overdue)
        {
            if ((staffedHuts.isEmpty() || servableBy(freeHuts, building)) && readyForOrder(colony, building, openOrders))
            {
                if (request(colony, level, building))
                {
                    return;
                }
            }
        }

        if (housingCapped)
        {
            final List<IBuilding> homes = new ArrayList<>();
            for (final IBuilding building : candidates)
            {
                if (building.getBuildingLevel() > 0
                      && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutHome)
                {
                    homes.add(building);
                }
            }
            homes.sort(Comparator.comparingInt(IBuilding::getBuildingLevel).thenComparing(IBuilding::getID));
            for (final IBuilding home : homes)
            {
                if (!staffedHuts.isEmpty() && !servableBy(freeHuts, home))
                {
                    continue;
                }
                if (readyForOrder(colony, home, openOrders))
                {
                    if (request(colony, level, home))
                    {
                        return;
                    }
                }
            }
        }

        for (final IBuilding farm : candidates)
        {
            if (farm.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFarmer
                  && farm.getBuildingLevel() > 0 && farm.getBuildingLevel() < Math.min(3, townHall)
                  && (staffedHuts.isEmpty() || servableBy(freeHuts, farm))
                  && readyForOrder(colony, farm, openOrders))
            {
                if (request(colony, level, farm))
                {
                    return;
                }
            }
        }

        final List<IBuilding> laggingTowers = new ArrayList<>();
        for (final IBuilding building : candidates)
        {
            final Block towerBlock = building.getBuildingType().getBuildingBlock();
            if ((towerBlock == ModBlocks.blockHutGuardTower || towerBlock == ModBlocks.blockHutBarracksTower)
                  && building.getBuildingLevel() > 0 && building.getBuildingLevel() < townHall)
            {
                laggingTowers.add(building);
            }
        }
        laggingTowers.sort(Comparator.comparingInt(IBuilding::getBuildingLevel).thenComparing(IBuilding::getID));
        for (final IBuilding tower : laggingTowers)
        {
            if (!staffedHuts.isEmpty() && !servableBy(freeHuts, tower))
            {
                continue;
            }
            if (readyForOrder(colony, tower, openOrders))
            {
                if (request(colony, level, tower))
                {
                    return;
                }
            }
        }

        if (wantedUniversity != null)
        {
            for (final IBuilding building : candidates)
            {
                if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutUniversity
                      && building.getBuildingLevel() < Math.min(wantedUniversity, building.getMaxBuildingLevel())
                      && (staffedHuts.isEmpty() || servableBy(freeHuts, building))
                      && readyForOrder(colony, building, openOrders))
                {
                    if (request(colony, level, building))
                    {
                        return;
                    }
                }
            }
        }
        for (final IBuilding building : candidates)
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBarracks
                  && building.getBuildingLevel() > 0
                  && (staffedHuts.isEmpty() || servableBy(freeHuts, building))
                  && readyForOrder(colony, building, openOrders))
            {
                if (request(colony, level, building))
                {
                    return;
                }
            }
        }

        final List<IBuilding> spine = new ArrayList<>();
        for (final IBuilding building : candidates)
        {
            final Block spineBlock = building.getBuildingType().getBuildingBlock();
            if (building.getBuildingLevel() > 0 && building.getBuildingLevel() < townHall
                  && (spineBlock == ModBlocks.blockHutWareHouse || spineBlock == ModBlocks.blockHutMiner
                        || spineBlock == ModBlocks.blockHutSmeltery || spineBlock == ModBlocks.blockHutBlacksmith
                        || spineBlock == ModBlocks.blockHutEnchanter))
            {
                spine.add(building);
            }
        }
        spine.sort(Comparator.comparingInt(IBuilding::getBuildingLevel).thenComparing(IBuilding::getID));
        for (final IBuilding hut : spine)
        {
            if (!staffedHuts.isEmpty() && !servableBy(freeHuts, hut))
            {
                continue;
            }
            if (readyForOrder(colony, hut, openOrders))
            {
                if (request(colony, level, hut))
                {
                    return;
                }
            }
        }

        final List<IBuilding> fresh = new ArrayList<>();
        for (final IBuilding building : candidates)
        {
            if (building.getBuildingLevel() == 0)
            {
                fresh.add(building);
            }
        }

        fresh.sort(Comparator
          .comparingInt((IBuilding b) -> upgradeClass(b, townHall, researchWants, housingCapped))
          .thenComparingInt(b -> growth == null ? Integer.MAX_VALUE : growth.planRank(b.getBuildingType().getBuildingBlock()))
          .thenComparing(IBuilding::getID));
        for (final IBuilding building : fresh)
        {
            if (!staffedHuts.isEmpty() && !servableBy(freeHuts, building))
            {
                continue;
            }
            if (readyForOrder(colony, building, openOrders))
            {
                if (request(colony, level, building))
                {
                    return;
                }
            }
        }

        candidates.sort(Comparator
          .comparingInt((IBuilding b) -> upgradeClass(b, townHall, researchWants, housingCapped))
          .thenComparingInt(IBuilding::getBuildingLevel)
          .thenComparingInt(b -> growth == null ? Integer.MAX_VALUE : growth.planRank(b.getBuildingType().getBuildingBlock()))
          .thenComparing(IBuilding::getID));

        for (final IBuilding building : candidates)
        {
            if (!staffedHuts.isEmpty() && !servableBy(freeHuts, building))
            {
                continue;

            }
            if (readyForOrder(colony, building, openOrders))
            {
                if (request(colony, level, building))
                {
                    return;
                }
            }
        }
    }

    boolean driveTownHall(final IColony colony, final ServerLevel level)
    {

        if (!AutopilotConfig.live(colony, AutopilotConfig.AUTO_UPGRADE_ENABLED))
        {
            return false;
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!(building instanceof BuildingTownHall) || building.getBuildingLevel() >= building.getMaxBuildingLevel())
            {
                continue;
            }
            final List<WorkOrderBuilding> openOrders = colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);
            if (hasOpenOrder(building, openOrders))
            {
                return true;
            }

            return readyForOrder(colony, building, openOrders) && request(colony, level, building);
        }
        return false;
    }

    boolean upgradeDue(final IColony colony)
    {

        if (!AutopilotConfig.live(colony, AutopilotConfig.AUTO_UPGRADE_ENABLED))
        {
            return false;
        }
        final List<WorkOrderBuilding> openOrders = colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);
        final List<IBuilding> staffedHuts = new ArrayList<>();
        int townHall = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingBuilder && !building.getAllAssignedCitizen().isEmpty())
            {
                staffedHuts.add(building);
            }
            if (building instanceof BuildingTownHall)
            {
                townHall = building.getBuildingLevel();
            }
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() >= 1 && building.getBuildingLevel() < building.getMaxBuildingLevel()
                  && (building instanceof BuildingTownHall || building.getBuildingLevel() <= townHall)
                  && !(AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES)
                        && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGraveyard)
                  && servableBy(staffedHuts, building)
                  && readyForOrder(colony, building, openOrders))
            {
                return true;
            }
        }
        return false;
    }

    private boolean request(final IColony colony, final ServerLevel level, final IBuilding building)
    {
        return request(colony, level, building, false);
    }

    private boolean request(final IColony colony, final ServerLevel level, final IBuilding building, final boolean hallRule)
    {
        final boolean upgrade = building.getBuildingLevel() > 0;
        final Set<BlockPos> recycled = recycledUpgrades.get(ColonyAutopilot.colonyKey(colony));
        final boolean refile = upgrade && recycled != null && recycled.contains(building.getID());
        if (upgrade && !BuilderCap.mayUpgrade(colony, level, refile || hallRule))
        {
            return false;
        }
        final FakePlayer owner = FakePlayerFactory.get(level,
          new GameProfile(colony.getPermissions().getOwner(), colony.getPermissions().getOwnerName()));
        ColonyAutopilot.LOGGER.info("[{}] requesting {} {} (level {} -> {})",
          colony.getName(),
          upgrade ? "upgrade of" : "construction of",
          building.getBuildingDisplayName(),
          building.getBuildingLevel(),
          building.getBuildingLevel() + 1);
        building.requestUpgrade(owner, BlockPos.ZERO);

        final boolean filed = hasOpenOrder(building, colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class));
        if (filed)
        {
            endRecycleHold(colony, building.getID());
        }
        else
        {
            refuse(colony, level, building, upgrade ? "upgrade" : "construction");
        }

        if (!AutopilotConfig.progressionMode(colony))
        {
            return true;
        }
        if (!filed)
        {
            ColonyAutopilot.LOGGER.debug("[{}] MineColonies refused the order for {} at {} — the cap records nothing",
              colony.getName(), building.getBuildingDisplayName(), building.getID().toShortString());
            return false;
        }
        if (refile)
        {

            recycled.remove(building.getID());
        }
        else if (upgrade)
        {
            BuilderCap.started(colony, level, BuilderCap.Kind.UPGRADE);
        }
        return true;
    }

    private void refuse(final IColony colony, final ServerLevel level, final IBuilding building, final String what)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        refusedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(building.getID(), level.getGameTime() + REFUSAL_RETRY_TICKS);
        if (gateWarned.computeIfAbsent(key, k -> new HashSet<>()).add(building.getID()))
        {
            ColonyAutopilot.LOGGER.warn("[{}] MineColonies turned down the {} of the {} at {} (its blueprint may reach past the colony's claimed chunks) — asked again in a game day",
              colony.getName(), what, SiteSelector.plainName(building), building.getID().toShortString());
        }
        else
        {
            ColonyAutopilot.LOGGER.debug("[{}] MineColonies turned down the {} of the {} at {} again — asked again in a game day",
              colony.getName(), what, SiteSelector.plainName(building), building.getID().toShortString());
        }
    }

    private boolean stillRefused(final IColony colony, final BlockPos buildingId)
    {
        final Map<BlockPos, Long> refused = refusedUntil.get(ColonyAutopilot.colonyKey(colony));
        final Long until = refused == null ? null : refused.get(buildingId);
        if (until == null)
        {
            return false;
        }
        if (colony.getWorld() != null && colony.getWorld().getGameTime() < until)
        {
            return true;
        }
        refused.remove(buildingId);
        return false;
    }

    private boolean instantOpen(final IColony colony, final IBuilding building, final int wantedLevel, final int townHall)
    {
        if (tornDown(building))
        {
            return false;
        }

        int target = Math.min(Math.min(wantedLevel, building.getMaxBuildingLevel()), townHall + 1);
        while (target > building.getBuildingLevel()
                 && !building.canBeBuiltByBuilder(target) && !resolvableByBuilder(colony, target, building.getPosition()))
        {
            target--;
        }
        if (target <= building.getBuildingLevel())
        {
            return false;
        }

        building.setBuildingLevel(target);
        building.onUpgradeComplete(null, target);

        for (final WorkOrderBuilding order : List.copyOf(colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class)))
        {
            if (order.getLocation().equals(building.getID()))
            {
                colony.getWorkManager().removeWorkOrder(order.getID());
            }
        }
        scheduleRepair(colony, building.getID());
        ColonyAutopilot.LOGGER.info("[{}] {} opened early to level {} — the research it gates unlocks now, and the builders raise its structure to match",
          colony.getName(), building.getBuildingDisplayName(), target);
        return true;
    }

    private IBuilding hallRuleSite(final IColony colony, final List<IBuilding> candidates, final List<IBuilding> freeHuts,
      final List<WorkOrderBuilding> openOrders, final int townHall)
    {
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        if (hall == null || townHall < 1 || growth == null || !candidates.contains(hall) || !growth.hallRungDue(colony, townHall))
        {
            return null;
        }
        if (resolvableByBuilder(colony, townHall + 1, hall.getPosition()))
        {
            return hall;
        }
        IBuilding climber = null;
        for (final IBuilding hut : candidates)
        {
            if (!(hut instanceof BuildingBuilder) || hut.getAllAssignedCitizen().isEmpty()
                  || hut.getPosition().distSqr(hall.getPosition()) > SiteSelector.BUILDER_RANGE_SQ)
            {
                continue;
            }

            if (hasOpenOrder(hut, openOrders))
            {
                return null;
            }
            if (servableBy(freeHuts, hut) && (climber == null || hut.getBuildingLevel() > climber.getBuildingLevel()))
            {
                climber = hut;
            }
        }
        return climber;
    }

    private static boolean resolvableByBuilder(final IColony colony, final int level, final BlockPos site)
    {
        for (final IBuilding hut : colony.getServerBuildingManager().getBuildings().values())
        {
            if (hut instanceof BuildingBuilder && !hut.getAllAssignedCitizen().isEmpty() && hut.getBuildingLevel() >= level
                  && hut.getPosition().distSqr(site) <= SiteSelector.BUILDER_RANGE_SQ)
            {
                return true;
            }
        }
        return false;
    }

    private void staffCrewlessHuts(final IColony colony, final ServerLevel level, final ColonyId key, final List<IBuilding> crewlessHuts)
    {
        final Map<BlockPos, Long> since = crewlessSince.computeIfAbsent(key, k -> new HashMap<>());
        since.entrySet().removeIf(entry -> crewlessHuts.stream().noneMatch(hut -> hut.getID().equals(entry.getKey())));
        final long now = level.getGameTime();
        for (final IBuilding hut : crewlessHuts)
        {
            final WorkerBuildingModule crew = hut.getModule(WorkerBuildingModule.class);

            if (crew != null && !com.minecolonies.core.util.BuildingUtils.canAutoHire(hut, crew.getHiringMode(), crew.getJobEntry()))
            {
                since.remove(hut.getID());
                continue;
            }
            final long first = since.computeIfAbsent(hut.getID(), k -> now);

            final boolean warned = first < 0;
            if (!warned && now - first < CREWLESS_GRACE_TICKS)
            {
                continue;
            }
            final ICitizenData jobless = colony.getCitizenManager().getJoblessCitizen();
            if (crew != null && jobless != null && crew.assignCitizen(jobless))
            {
                since.remove(hut.getID());
                ColonyAutopilot.LOGGER.info("[{}] the crew at {} stood empty — {} hired on the spot",
                  colony.getName(), hut.getID().toShortString(), jobless.getName());
            }
            else if (!warned && now - first >= CREWLESS_WARN_TICKS)
            {

                since.put(hut.getID(), -Math.max(1L, now));
                if (hut instanceof BuildingBuilder)
                {
                    ColonyAutopilot.LOGGER.warn("[{}] the crew at {} stands empty and no jobless citizen can fill it",
                      colony.getName(), hut.getID().toShortString());
                }
                else
                {
                    ColonyAutopilot.LOGGER.info("[{}] the crew at {} stands empty and no jobless citizen can fill it",
                      colony.getName(), hut.getID().toShortString());
                }
            }
        }
    }

    private static final long CREWLESS_GRACE_TICKS = 2400L;

    private static final long CREWLESS_WARN_TICKS = 24000L;

    private static List<IBuilding> freeCrews(final List<IBuilding> staffedHuts, final List<WorkOrderBuilding> openOrders)
    {
        final Set<BlockPos> busy = new HashSet<>();
        for (final WorkOrderBuilding order : openOrders)
        {
            if (order.isClaimed())
            {
                busy.add(order.getClaimedBy());
            }
        }
        for (final WorkOrderBuilding order : openOrders)
        {
            if (order.isClaimed())
            {
                continue;
            }
            for (final IBuilding hut : staffedHuts)
            {
                if (!busy.contains(hut.getID()) && order.canBuild(hut))
                {
                    busy.add(hut.getID());
                    break;
                }
            }
        }
        final List<IBuilding> free = new ArrayList<>();
        for (final IBuilding hut : staffedHuts)
        {
            if (!busy.contains(hut.getID()))
            {
                free.add(hut);
            }
        }
        return free;
    }

    private static boolean servableBy(final List<IBuilding> freeHuts, final IBuilding building)
    {
        final int target = building.getBuildingLevel() + 1;
        for (final IBuilding hut : freeHuts)
        {
            if ((hut.getBuildingLevel() >= target
                   || hut.getBuildingLevel() == BuildingBuilder.MAX_BUILDING_LEVEL
                   || hut.getID().equals(building.getID()))

                  && hut.getPosition().distSqr(building.getPosition()) <= SiteSelector.BUILDER_RANGE_SQ)
            {
                return true;
            }
        }
        return false;
    }

    private static int upgradeClass(final IBuilding building, final int townHallLevel, final Map<Block, Integer> researchWants, final boolean housingCapped)
    {
        if (building instanceof BuildingTownHall)
        {

            return 0;
        }
        final Block block = building.getBuildingType().getBuildingBlock();
        if (building.getBuildingLevel() == 0
              && (block == ModBlocks.blockHutCook || block == ModBlocks.blockHutHospital
                    || block == ModBlocks.blockHutTavern || block == ModBlocks.blockHutWareHouse))
        {
            return 1;
        }
        if (housingCapped && block == ModBlocks.blockHutHome)
        {
            return 2;
        }

        if (block == ModBlocks.blockHutTavern && building.getBuildingLevel() < townHallLevel)
        {
            return 2;
        }

        if (block == ModBlocks.blockHutFarmer && building.getBuildingLevel() < Math.min(3, townHallLevel))
        {
            return 2;
        }
        if (building instanceof BuildingBuilder)
        {

            return building.getBuildingLevel() <= townHallLevel ? 3 : 8;
        }

        if (block == ModBlocks.blockHutBarracks)
        {
            return 1;
        }
        if (block == ModBlocks.blockHutBarracksTower)
        {
            return building.getBuildingLevel() == 0 ? 5 : 1;
        }
        if (block == ModBlocks.blockHutGuardTower)
        {
            return building.getBuildingLevel() == 0 ? 6 : 1;
        }
        final Integer wantedLevel = researchWants.get(block);
        if (wantedLevel != null && building.getBuildingLevel() < wantedLevel)
        {
            return 4;
        }
        return 7;
    }

    private void watchStuckOrders(final IColony colony, final ServerLevel level, final ColonyId key)
    {
        final int staleMinutes = AutopilotConfig.get(colony, AutopilotConfig.STUCK_ORDER_GAME_MINUTES);
        if (staleMinutes <= 0)
        {
            return;
        }
        final Map<Integer, OrderPulse> pulses = orderPulse.computeIfAbsent(key, k -> new HashMap<>());
        pulses.keySet().retainAll(colony.getWorkManager().getWorkOrders().keySet());

        final Map<BlockPos, BlockPos> avoid = failedClaimers.get(key);
        if (avoid != null && !avoid.isEmpty())
        {
            for (final IWorkOrder order : colony.getWorkManager().getWorkOrders().values())
            {
                final BlockPos failed = avoid.get(order.getLocation());
                if (failed == null || !(order instanceof WorkOrderBuilding)
                      || (order.isClaimed() && !failed.equals(order.getClaimedBy())))
                {
                    continue;
                }

                final List<ItemStack> paid = paidAtSite.getOrDefault(key, Map.of()).get(order.getLocation());
                BuildingBuilder receiver = null;
                for (final IBuilding hut : colony.getServerBuildingManager().getBuildings().values())
                {

                    if (hut instanceof BuildingBuilder capable && hut.getBuildingLevel() > 0
                          && hut.getBuildingLevel() >= ((WorkOrderBuilding) order).getTargetLevel()
                          && !hut.getID().equals(failed) && !hut.getAllAssignedCitizen().isEmpty())
                    {
                        if (receiver == null)
                        {
                            receiver = capable;
                        }
                        if (paid == null)
                        {
                            break;
                        }
                        if (capable.getWorkOrder() == null)
                        {
                            receiver = capable;
                            break;
                        }
                    }
                }
                if (receiver != null)
                {
                    final IBuilding failedBuilding = colony.getServerBuildingManager().getBuilding(failed);

                    if (failedBuilding instanceof AbstractBuildingStructureBuilder failedHut)
                    {
                        failedHut.onWorkOrderCancellation(order);
                    }

                    order.setClaimedBy(null);
                    order.setClaimedBy(receiver.getID());
                    avoid.remove(order.getLocation());
                    ColonyAutopilot.LOGGER.info("[{}] '{}' at {} goes to the crew at {} — the last builder never reached it",
                      colony.getName(), order.getDisplayName().getString(), order.getLocation().toShortString(), receiver.getID().toShortString());
                    if (paid != null)
                    {
                        paidAtSite.get(key).remove(order.getLocation());
                        movePaidSet(colony, order, paid, failedBuilding, receiver);
                    }
                }
            }
        }

        final long now = level.getGameTime();
        final long staleTicks = staleMinutes * 1200L;
        for (final IWorkOrder order : List.copyOf(colony.getWorkManager().getWorkOrders().values()))
        {

            if (order.getWorkOrderType() == WorkOrderType.REMOVE || order instanceof WorkOrderMiner)
            {
                pulses.remove(order.getID());
                continue;
            }
            if (!order.isClaimed())
            {

                if (!(order instanceof WorkOrderBuilding wob) || claimableBySomeCrew(colony, wob))
                {
                    pulses.remove(order.getID());
                    continue;
                }
                final OrderPulse waiting = pulses.get(order.getID());
                if (waiting == null)
                {
                    pulses.put(order.getID(), new OrderPulse(null, "", 0, 0, null, now, now, now, false));
                    continue;
                }
                if (now - waiting.since() < staleTicks)
                {
                    continue;
                }
                pulses.remove(order.getID());
                colony.getWorkManager().removeWorkOrder(order.getID());
                final int rounds = parkCount.computeIfAbsent(key, k -> new HashMap<>()).merge(order.getLocation(), 1, Integer::sum);
                final long days = parkSite(colony, key, order.getLocation(), rounds, now);
                ColonyAutopilot.LOGGER.warn("[{}] '{}' at {} waits for a crew that cannot come — no staffed builder may legally claim it; parked for {} game-days",
                  colony.getName(), order.getDisplayName().getString(), order.getLocation().toShortString(), days);
                continue;
            }
            if (!(colony.getServerBuildingManager().getBuilding(order.getClaimedBy()) instanceof AbstractBuildingStructureBuilder builder))
            {
                pulses.remove(order.getID());
                continue;
            }

            final IBuilderWorkOrder active = builder.getWorkOrder();
            if (!((active != null && active.getID() == order.getID()) || builder.getAllAssignedCitizen().isEmpty()))
            {
                pulses.remove(order.getID());
                continue;
            }

            int available = 0;
            int needed = 0;
            final var ledger = builder.getNeededResources().values();
            if (!ledger.isEmpty())
            {
                final Map<Item, List<ItemStack>> onHand = MaterialTrickle.onHand(builder);
                for (final var resource : ledger)
                {
                    available += Math.min(MaterialTrickle.held(onHand, resource.getItemStack()), resource.getAmount());
                    needed += resource.getAmount();
                }
            }
            final var progress = builder.getProgress();
            final BlockPos progressPos = progress == null ? null : progress.getA();
            final String stage = progress == null || progress.getB() == null ? "" : progress.getB().toString();

            BlockPos builderPos = null;
            boolean asleep = false;
            for (final var citizenData : builder.getAllAssignedCitizen())
            {
                builderPos = citizenData.getEntity()
                               .map(e -> new BlockPos(e.getBlockX() >> 2, e.getBlockY() >> 2, e.getBlockZ() >> 2))
                               .orElse(null);
                asleep = citizenData.getEntity().map(e -> e.isSleeping()).orElse(false);
                break;
            }
            final OrderPulse last = pulses.get(order.getID());
            if (last == null)
            {
                pulses.put(order.getID(), new OrderPulse(progressPos, stage, available, needed, builderPos, now, now, now, false));
                continue;
            }

            final boolean cursorMoved = !Objects.equals(last.progressPos(), progressPos) || !last.stage().equals(stage);

            final boolean suppliesMoved = available > last.available() || needed < last.needed();

            final boolean builderMoved = asleep || !Objects.equals(last.builderPos(), builderPos);
            final long sinceAny = (cursorMoved || suppliesMoved || builderMoved) ? now : last.since();
            final long sinceCore = (cursorMoved || suppliesMoved) ? now : last.coreSince();
            final long sinceCursor = cursorMoved ? now : last.cursorSince();

            final boolean nudged = last.nudged() && !cursorMoved;
            if (cursorMoved || suppliesMoved || builderMoved)
            {
                pulses.put(order.getID(), new OrderPulse(progressPos, stage, available, needed, builderPos, sinceAny, sinceCore, sinceCursor, nudged));
            }

            if (asleep || (now - sinceAny < staleTicks && now - sinceCore < 3L * staleTicks && now - sinceCursor < 6L * staleTicks))
            {
                continue;
            }
            final boolean materialShort = needed > available;

            int ownWaiting = 0;
            if (!builder.getAllAssignedCitizen().isEmpty() && AutopilotConfig.progressionMode(colony))
            {
                final Set<IToken<?>> own = new HashSet<>();
                for (final var open : builder.getOpenRequestsByRequestableType().values())
                {
                    own.addAll(open);
                }
                ownWaiting = Treasury.waitingUnder(colony, own);
            }
            if (ownWaiting > 0)
            {
                pulses.put(order.getID(), new OrderPulse(progressPos, stage, available, needed, builderPos, now, now, now, nudged));
                ColonyAutopilot.LOGGER.debug("[{}] the watchdog holds off at {}: {} of its requests (or their crafting children) wait for funds",
                  colony.getName(), order.getLocation().toShortString(), ownWaiting);
                continue;
            }
            final String why = materialShort
                                 ? "short " + (needed - available) + " of " + needed + " materials"
                                 : progressPos == null
                                     ? "materials in hand but no block ever placed"
                                     : "materials in hand, blueprint cursor frozen at " + progressPos.toShortString();

            boolean sentHome = false;
            if (!materialShort && !nudged)
            {
                for (final ICitizenData citizen : builder.getAllAssignedCitizen())
                {
                    if (citizen.getEntity().isPresent() && TeleportHelper.teleportCitizen(citizen.getEntity().get(), level, builder.getPosition()))
                    {
                        sentHome = true;
                        ColonyAutopilot.LOGGER.info("[{}] '{}' at {} has made no construction progress for at least {} game-minutes — nudged {} home to re-path; the order stands, and a second stall recycles it ({})",
                          colony.getName(), order.getDisplayName().getString(), order.getLocation().toShortString(), staleMinutes,
                          citizen.getName(), why);
                        break;
                    }
                }
            }
            if (sentHome)
            {
                pulses.put(order.getID(), new OrderPulse(progressPos, stage, available, needed, builderPos, now, now, now, true));
                continue;
            }
            pulses.remove(order.getID());
            String crewName = "unstaffed";
            for (final var citizenData : builder.getAllAssignedCitizen())
            {
                crewName = citizenData.getName();
                break;
            }
            ColonyAutopilot.LOGGER.info("[{}] '{}' at {} has made no construction progress for at least {} game-minutes — recycling the jammed order (claimed by {} at {}; {})",
              colony.getName(), order.getDisplayName().getString(), order.getLocation().toShortString(), staleMinutes,
              crewName, builder.getID().toShortString(), why);

            if (order instanceof WorkOrderBuilding && !materialShort && AutopilotConfig.progressionMode(colony))
            {
                final List<ItemStack> paid = paidSet(builder);
                if (!paid.isEmpty())
                {
                    paidAtSite.computeIfAbsent(key, k -> new HashMap<>()).put(order.getLocation(), paid);
                }
            }
            colony.getWorkManager().removeWorkOrder(order.getID());
            if (order.getWorkOrderType() == WorkOrderType.UPGRADE && AutopilotConfig.progressionMode(colony))
            {
                recycledUpgrades.computeIfAbsent(key, k -> new HashSet<>()).add(order.getLocation());
            }

            if (order instanceof WorkOrderBuilding && order.getWorkOrderType() != WorkOrderType.REPAIR)
            {
                recycledAt.computeIfAbsent(key, k -> new HashMap<>()).put(order.getLocation(), now);
            }

            builder.resetNeededResources();
            builder.setProgressPos(null, null);

            for (final var citizenData : builder.getAllAssignedCitizen())
            {
                citizenData.getEntity().ifPresent(entity -> TeleportHelper.teleportCitizen(entity, level, builder.getPosition()));
            }

            if (order instanceof WorkOrderBuilding && !materialShort)
            {
                failedClaimers.computeIfAbsent(key, k -> new HashMap<>()).put(order.getLocation(), builder.getID());
            }

            final boolean staffed = !builder.getAllAssignedCitizen().isEmpty();
            final boolean siteSuspect = recycleStrikes.getOrDefault(key, Map.of()).getOrDefault(order.getLocation(), 0) >= 1
                                          || parkCount.getOrDefault(key, Map.of()).getOrDefault(order.getLocation(), 0) >= 1;

            final int crewStrikes = (siteSuspect || !staffed || materialShort || !(builder instanceof BuildingBuilder))
                                      ? 0
                                      : builderJamStrikes.computeIfAbsent(key, k -> new HashMap<>()).merge(builder.getID(), 1, Integer::sum);
            if (crewStrikes >= 2)
            {
                int staffedCrews = 0;
                for (final IBuilding hut : colony.getServerBuildingManager().getBuildings().values())
                {
                    if (hut instanceof BuildingBuilder && !hut.getAllAssignedCitizen().isEmpty())
                    {
                        staffedCrews++;
                    }
                }
                if (staffedCrews <= 1)
                {

                    ColonyAutopilot.LOGGER.warn("[{}] the crew at {} ({}) keeps jamming but is the colony's last — dismissal waits until another crew stands",
                      colony.getName(), builder.getID().toShortString(), crewName);
                }
                else
                {
                    builderJamStrikes.get(key).remove(builder.getID());
                    final WorkerBuildingModule crew = builder.getModule(WorkerBuildingModule.class);
                    if (crew != null)
                    {
                        for (final var citizenData : List.copyOf(builder.getAllAssignedCitizen()))
                        {
                            crew.removeCitizen(citizenData);
                        }
                        ColonyAutopilot.LOGGER.warn("[{}] the crew at {} ({}) jammed {} orders across the colony — the builder was dismissed for a fresh hire",
                          colony.getName(), builder.getID().toShortString(), crewName, crewStrikes);
                        final ICitizenData jobless = colony.getCitizenManager().getJoblessCitizen();
                        if (jobless != null && crew.assignCitizen(jobless))
                        {
                            ColonyAutopilot.LOGGER.info("[{}] {} takes over the crew at {} — hired on the spot",
                              colony.getName(), jobless.getName(), builder.getID().toShortString());
                        }
                    }
                }
            }

            if (order instanceof WorkOrderBuilding && staffed && !materialShort)
            {
                final int struck = recycleStrikes.computeIfAbsent(key, k -> new HashMap<>()).merge(order.getLocation(), 1, Integer::sum);
                if (struck >= 3)
                {
                    recycleStrikes.get(key).remove(order.getLocation());
                    endRecycleHold(colony, order.getLocation());
                    final int rounds = parkCount.computeIfAbsent(key, k -> new HashMap<>()).merge(order.getLocation(), 1, Integer::sum);
                    final long days = parkSite(colony, key, order.getLocation(), rounds, now);
                    ColonyAutopilot.LOGGER.warn("[{}] '{}' at {} jammed {} orders in a row — parked for {} game-days (strike-out #{}); the site may be unreachable for the builders",
                      colony.getName(), order.getDisplayName().getString(), order.getLocation().toShortString(), struck, days, rounds);
                }
                else if (struck == 2)
                {

                    for (final ICitizenData citizen : builder.getAllAssignedCitizen())
                    {
                        if (citizen.getEntity().isPresent()
                              && TeleportHelper.teleportCitizen(citizen.getEntity().get(), level, order.getLocation()))
                        {
                            ColonyAutopilot.LOGGER.info("[{}] the crew at {} jammed twice on '{}' — teleported {} to the site before the watchdog parks it",
                              colony.getName(), builder.getID().toShortString(), order.getDisplayName().getString(), citizen.getName());
                            break;
                        }
                    }
                }
            }
        }
    }

    private long parkSite(final IColony colony, final ColonyId key, final BlockPos site, final int rounds, final long now)
    {
        final IBuilding parkedHut = colony.getServerBuildingManager().getBuilding(site);
        final boolean townHall = parkedHut instanceof BuildingTownHall;
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();

        boolean hallWaits = parkedHut instanceof BuildingBuilder && hall != null
                              && hall.getBuildingLevel() < hall.getMaxBuildingLevel()
                              && parkedHut.getBuildingLevel() >= hall.getBuildingLevel()
                              && !resolvableByBuilder(colony, hall.getBuildingLevel() + 1, hall.getPosition());

        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (hallWaits && other != parkedHut && other instanceof BuildingBuilder && other.getBuildingLevel() >= hall.getBuildingLevel()
                  && !other.getAllAssignedCitizen().isEmpty() && !tornDown(other) && !parked(colony, other.getID())
                  && !stillRefused(colony, other.getID())
                  && other.getPosition().distSqr(hall.getPosition()) <= SiteSelector.BUILDER_RANGE_SQ)
            {
                hallWaits = false;
            }
        }
        final long days = (townHall || hallWaits) ? 1L : 7L << Math.min(rounds - 1, 2);
        buildParkedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(site, now + days * 24000L);
        if (townHall && rounds >= 2)
        {
            ColonyAutopilot.LOGGER.warn("[{}] the Town Hall at {} has parked {} times running — the growth plan's single gate is stuck and needs a human; held to a 1-day re-park so the village keeps moving meanwhile",
              colony.getName(), site.toShortString(), rounds);
        }
        else if (hallWaits && rounds >= 2)
        {
            ColonyAutopilot.LOGGER.warn("[{}] the builder's hut at {}, the only one the Town Hall's next level can wait on, has parked {} times running — it needs a human; held to a 1-day re-park so the hall is not frozen for weeks",
              colony.getName(), site.toShortString(), rounds);
        }
        return days;
    }

    private static List<ItemStack> paidSet(final AbstractBuildingStructureBuilder builder)
    {
        final List<ItemStack> paid = new ArrayList<>();
        final var ledger = builder.getNeededResources().values();
        if (ledger.isEmpty())
        {
            return paid;
        }
        final Map<Item, List<ItemStack>> onHand = MaterialTrickle.onHand(builder);
        for (final var resource : ledger)
        {
            final int held = Math.min(MaterialTrickle.held(onHand, resource.getItemStack()), resource.getAmount());
            if (held > 0)
            {
                paid.add(resource.getItemStack().copyWithCount(held));
            }
        }
        return paid;
    }

    private static void movePaidSet(final IColony colony, final IWorkOrder order, final List<ItemStack> paid,
      final IBuilding failedBuilding, final BuildingBuilder receiver)
    {
        if (!(failedBuilding instanceof AbstractBuildingStructureBuilder failedHut) || failedHut.getWorkOrder() != null
              || receiver.getWorkOrder() != null || failedHut.getTileEntity() == null || receiver.getTileEntity() == null)
        {
            return;
        }
        final IItemHandler from = failedHut.getItemHandlerCap();
        final IItemHandler to = receiver.getItemHandlerCap();
        if (from == null || to == null)
        {
            return;
        }
        int moved = 0;
        for (final ItemStack wanted : paid)
        {
            final int left = InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandlerWithResult(from,
              stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, wanted, true, true), wanted.getCount(), to);
            moved += wanted.getCount() - left;
        }
        if (moved > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] {} paid item(s) for '{}' at {} move with it from the crew at {} to the crew at {}",
              colony.getName(), moved, order.getDisplayName().getString(), order.getLocation().toShortString(),
              failedHut.getID().toShortString(), receiver.getID().toShortString());
        }
    }

    private static boolean claimableBySomeCrew(final IColony colony, final WorkOrderBuilding order)
    {
        for (final IBuilding hut : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!(hut instanceof BuildingBuilder) || !order.canBuild(hut))
            {
                continue;
            }
            if (!hut.getAllAssignedCitizen().isEmpty())
            {
                return true;
            }
            final WorkerBuildingModule crew = hut.getModule(WorkerBuildingModule.class);
            if (!tornDown(hut) && crew != null
                  && com.minecolonies.core.util.BuildingUtils.canAutoHire(hut, crew.getHiringMode(), crew.getJobEntry()))
            {
                return true;
            }
        }
        return false;
    }

    void scheduleRepair(final IColony colony, final BlockPos buildingId)
    {
        if (colony.getWorld() instanceof ServerLevel level)
        {
            ColonyGrounds.get(level).oweRepair(colony.getID(), buildingId);
        }
    }

    boolean struggling(final IColony colony, final BlockPos buildingId)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<BlockPos, Long> parked = buildParkedUntil.get(key);
        if (parked != null)
        {
            final Long until = parked.get(buildingId);
            if (until != null && colony.getWorld() != null && colony.getWorld().getGameTime() < until)
            {
                return true;
            }
        }
        return recycleStrikes.getOrDefault(key, Map.of()).getOrDefault(buildingId, 0) >= 2;
    }

    boolean parked(final IColony colony, final BlockPos buildingId)
    {
        final Long until = buildParkedUntil.getOrDefault(ColonyAutopilot.colonyKey(colony), Map.of()).get(buildingId);
        return until != null && colony.getWorld() != null && colony.getWorld().getGameTime() < until;
    }

    long recycleGraceUntil(final IColony colony, final BlockPos buildingId)
    {
        final Long at = recycledAt.getOrDefault(ColonyAutopilot.colonyKey(colony), Map.of()).get(buildingId);
        return at == null ? 0L : at + RECYCLE_HOLD_CAP_TICKS;
    }

    long addedGraceUntil(final IColony colony, final BlockPos buildingId)
    {
        final Map<BlockPos, Long> added = addedAt.get(ColonyAutopilot.colonyKey(colony));
        final Long at = added == null ? null : added.get(buildingId);
        if (at == null)
        {
            return 0L;
        }
        if (colony.getWorld() != null && colony.getWorld().getGameTime() >= at + ADDED_GRACE_TICKS)
        {
            added.remove(buildingId);
            return 0L;
        }
        return at + ADDED_GRACE_TICKS;
    }

    private void endRecycleHold(final IColony colony, final BlockPos buildingId)
    {
        final Map<BlockPos, Long> held = recycledAt.get(ColonyAutopilot.colonyKey(colony));
        if (held != null)
        {
            held.remove(buildingId);
        }
    }

    boolean repairPending(final IColony colony, final BlockPos buildingId)
    {
        return colony.getWorld() instanceof ServerLevel level && ColonyGrounds.get(level).owesRepair(colony.getID(), buildingId);
    }

    boolean anyRepairPending(final IColony colony)
    {
        for (final WorkOrderBuilding order : colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class))
        {
            if (order.getWorkOrderType() == WorkOrderType.REPAIR)
            {
                return true;
            }
        }
        if (!AutopilotConfig.live(colony, AutopilotConfig.AUTO_UPGRADE_ENABLED) || !(colony.getWorld() instanceof ServerLevel level))
        {
            return false;
        }
        for (final long owed : ColonyGrounds.get(level).owedRepairs(colony.getID()))
        {
            final BlockPos site = BlockPos.of(owed);

            final IBuilding owedBuilding = colony.getServerBuildingManager().getBuilding(site);
            if (!parked(colony, site) && !stillRefused(colony, site)
                  && (owedBuilding == null || resolvableByBuilder(colony, owedBuilding.getBuildingLevel(), site)))
            {
                return true;
            }
        }
        return false;
    }

    private boolean drainOneRepair(final IColony colony, final ServerLevel level, final List<WorkOrderBuilding> openOrders)
    {
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        for (final long owed : grounds.owedRepairs(colony.getID()))
        {
            final BlockPos site = BlockPos.of(owed);
            final IBuilding building = colony.getServerBuildingManager().getBuilding(site);
            if (building == null || building.getBuildingLevel() <= 0 || tornDown(building))
            {
                grounds.settleRepair(colony.getID(), site);
                continue;
            }

            if (hasOpenOrder(building, openOrders) || parked(colony, site) || stillRefused(colony, site))
            {
                continue;
            }
            try
            {
                WorkOrderBuilding.create(WorkOrderType.REPAIR, building);
            }
            catch (final Exception e)
            {

                grounds.settleRepair(colony.getID(), site);
                ColonyAutopilot.LOGGER.warn("[{}] the structure owed at the {} at {} cannot be ordered — MineColonies cannot create its repair order ({}); the debt is dropped",
                  colony.getName(), SiteSelector.plainName(building), site.toShortString(), e.toString());
                continue;
            }

            if (!passesWorkOrderGates(colony, building, WorkOrderType.REPAIR)
                  || !resolvableByBuilder(colony, building.getBuildingLevel(), site))
            {
                continue;
            }
            ColonyAutopilot.LOGGER.info("[{}] requesting repair of {}", colony.getName(), building.getBuildingDisplayName());
            building.requestRepair(BlockPos.ZERO);

            if (hasOpenOrder(building, colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class)))
            {
                endRecycleHold(colony, site);
                return true;
            }

            refuse(colony, level, building, "repair");
        }
        return false;
    }

    static boolean tornDown(final IBuilding building)
    {
        return building.isDeconstructed() && building.getColony() != null
                 && building.getColony().getWorld() instanceof ServerLevel level
                 && ColonyGrounds.get(level).isTornDown(building.getColony().getID(), building.getPosition());
    }

    boolean orderable(final IColony colony, final IBuilding building)
    {

        final IBuilding parent = colony.getServerBuildingManager().getBuilding(building.getParent());
        if (building.getBuildingLevel() == 0 && parent != null && parent != building && parent.getBuildingLevel() == 0)
        {
            return orderable(colony, parent);
        }
        if (stillRefused(colony, building.getID()) || !researchAllows(colony, building) || !withinHeightBounds(colony, building)
              || !withinClaim(colony, building))
        {
            return false;
        }

        try
        {
            WorkOrderBuilding.create(building.getBuildingLevel() == 0 ? WorkOrderType.BUILD : WorkOrderType.UPGRADE, building);
        }
        catch (final Exception e)
        {
            return false;
        }

        for (final IBuilding hut : colony.getServerBuildingManager().getBuildings().values())
        {
            if (hut instanceof BuildingBuilder && hut.getBuildingLevel() > 0 && !tornDown(hut)
                  && hut.getPosition().distSqr(building.getPosition()) <= SiteSelector.BUILDER_RANGE_SQ)
            {
                return true;
            }
        }
        return false;
    }

    private boolean readyForOrder(final IColony colony, final IBuilding building, final List<WorkOrderBuilding> openOrders)
    {
        if (hasOpenOrder(building, openOrders) || tornDown(building))
        {
            return false;
        }

        final Map<BlockPos, Long> parked = buildParkedUntil.get(ColonyAutopilot.colonyKey(colony));
        if (parked != null)
        {
            final Long until = parked.get(building.getID());
            if (until != null)
            {
                if (colony.getWorld() != null && colony.getWorld().getGameTime() < until)
                {
                    return false;
                }
                parked.remove(building.getID());
            }
        }

        if (stillRefused(colony, building.getID()))
        {
            return false;
        }

        if (!researchAllows(colony, building))
        {
            return false;
        }

        final IBuilding parent = colony.getServerBuildingManager().getBuilding(building.getParent());
        if (building.getBuildingLevel() == 0)
        {
            if (parent != null && parent.getBuildingLevel() == 0)
            {
                return false;
            }
        }
        else if (parent != null
                   && building.getBuildingLevel() >= parent.getBuildingLevel()
                   && parent.getBuildingLevel() < parent.getMaxBuildingLevel())
        {
            return false;
        }

        if (building instanceof BuildingBarracks barracks && building.getBuildingLevel() >= 1)
        {
            for (final BlockPos towerPos : barracks.getTowers())
            {
                final IBuilding tower = colony.getServerBuildingManager().getBuilding(towerPos);
                if (tower != null && tower.getBuildingLevel() < building.getBuildingLevel())
                {
                    return false;
                }
            }
        }

        final WorkOrderType type = building.getBuildingLevel() == 0 ? WorkOrderType.BUILD : WorkOrderType.UPGRADE;
        return passesWorkOrderGates(colony, building, type);
    }

    private static boolean researchAllows(final IColony colony, final IBuilding building)
    {
        final ResourceLocation hutResearch = colony.getResearchManager().getResearchEffectIdFrom(building.getBuildingType().getBuildingBlock());
        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(hutResearch))
        {
            final double strength = colony.getResearchManager().getResearchEffects().getEffectStrength(hutResearch);
            if (strength < 1 || strength <= building.getBuildingLevel())
            {
                return false;
            }
        }
        return true;
    }

    static boolean hasOpenOrder(final IBuilding building, final List<WorkOrderBuilding> openOrders)
    {
        for (final WorkOrderBuilding order : openOrders)
        {
            if (order.getLocation().equals(building.getID()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean passesWorkOrderGates(final IColony colony, final IBuilding building, final WorkOrderType type)
    {
        try
        {

            final WorkOrderBuilding order = WorkOrderBuilding.create(type, building);
            final int target = order.getTargetLevel();

            if (!building.canBeBuiltByBuilder(target) && !order.canBeResolved(colony, target))
            {
                return false;
            }
            if (order.tooFarFromAnyBuilder(colony, target))
            {
                return false;
            }
        }
        catch (final Exception e)
        {

            if (gateWarned.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashSet<>()).add(building.getID()))
            {
                ColonyAutopilot.LOGGER.warn("[{}] cannot create a work order for the {} at {} — it will never build until this resolves: {}",
                  colony.getName(), SiteSelector.plainName(building), building.getID().toShortString(), e.toString());
            }
            else
            {
                ColonyAutopilot.LOGGER.debug("[{}] cannot create work order for {}: {}", colony.getName(), building.getID(), e.toString());
            }
            return false;
        }

        if (!withinHeightBounds(colony, building))
        {
            return false;
        }

        if (!withinClaim(colony, building))
        {
            if (gateWarned.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashSet<>()).add(building.getID()))
            {
                ColonyAutopilot.LOGGER.warn("[{}] the {} at {} reaches outside the colony's claimed chunks — MineColonies refuses its work orders until the claim covers it (a higher town hall widens the claim)",
                  colony.getName(), SiteSelector.plainName(building), building.getID().toShortString());
            }
            return false;
        }

        return true;
    }

    private static boolean withinClaim(final IColony colony, final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final int minX = Math.min(corners.getA().getX(), corners.getB().getX()) + 1;
        final int maxX = Math.max(corners.getA().getX(), corners.getB().getX());
        final int minZ = Math.min(corners.getA().getZ(), corners.getB().getZ()) + 1;
        final int maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ());
        for (int x = minX; x < maxX; x += 16)
        {
            for (int z = minZ; z < maxZ; z += 16)
            {
                final com.minecolonies.api.colony.claim.IChunkClaimData claim =
                  IColonyManager.getInstance().getClaimData(colony.getDimension(), new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4));
                if ((claim == null ? 0 : claim.getOwningColony()) != colony.getID())
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean withinHeightBounds(final IColony colony, final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final int maxHeight = colony.getWorld().getMaxBuildHeight();
        return corners.getA().getY() < maxHeight && corners.getB().getY() < maxHeight
                 && building.getPosition().getY() > colony.getWorld().getMinBuildHeight();
    }
}
