// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintTagUtils;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.util.IOPool;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.compatibility.newstruct.BlueprintMapping;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.plantation.IPlantationModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenDiedModEvent;
import net.minecraft.server.MinecraftServer;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.constant.StatisticsConstants;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule;
import com.minecolonies.core.colony.buildings.modules.EnchanterStationsModule;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.minecolonies.core.colony.buildings.modules.WorkAtHomeBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.util.BuildingUtils;
import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.colony.workorders.WorkOrderDecoration;
import com.minecolonies.core.colony.workorders.WorkOrderPlantationField;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingAlchemist;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingComposter;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingConcreteMixer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingNetherWorker;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GrowthDirector
{

    private static final int EVALUATION_PERIOD_TICKS = 200;

    private static GrowthDirector instance;

    public GrowthDirector()
    {
        instance = this;
    }

    static String reservedPadOverlapping(final IColony colony, final SiteSelector.Footprint box)
    {
        if (instance == null)
        {
            return null;
        }
        final Map<Block, SiteSelector.Footprint> pads = instance.lastTerraform.get(ColonyAutopilot.colonyKey(colony));
        if (pads != null)
        {
            for (final SiteSelector.Footprint pad : pads.values())
            {
                if (box.intersects(pad.minX(), pad.minZ(), pad.maxX(), pad.maxZ()))
                {
                    return "ground the village is levelling for a building";
                }
            }
        }
        return null;
    }

    List<SiteSelector.Footprint> reservedPads(final IColony colony)
    {
        return List.copyOf(lastTerraform.getOrDefault(ColonyAutopilot.colonyKey(colony), Map.of()).values());
    }

    static boolean shortOfPlots(final IColony colony)
    {
        return instance != null && instance.plotShortage.getOrDefault(ColonyAutopilot.colonyKey(colony), Set.of()).size() >= PLOT_SHORTAGE_HUTS;
    }

    void queueOutfit(final IBuilding building)
    {
        pendingRoadReconnects.add(building);
    }

    record ResolvedStep(Block hut, String name, int minCitizens, int targetCount, int targetLevel)
    {
        ResolvedStep(final Block hut, final String name, final int minCitizens, final int targetCount)
        {
            this(hut, name, minCitizens, targetCount, 0);
        }
    }

    private List<ResolvedStep> plan = List.of();

    private List<ResolvedStep> gravesPlan = List.of();

    private ResolvedStep openingBuilderStep;

    private List<ResolvedStep> planFor(final IColony colony)
    {
        return AutopilotConfig.live(AutopilotConfig.NO_GRAVES) && !AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES) ? gravesPlan : plan;
    }

    private Map<Block, Integer> planRankByHut = Map.of();

    int planRank(final Block hut)
    {
        return planRankByHut.getOrDefault(hut, Integer.MAX_VALUE);
    }

    private int tickCounter = 27;

    private final ColonyRota rota = new ColonyRota();

    private final Map<ColonyId, Long> lastPlacement = new HashMap<>();

    private final Map<ColonyId, Long> pendingSince = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, SiteSelector.Footprint>> reservedPlots = new HashMap<>();

    private final Map<ColonyId, Set<BlockPos>> guardTowersSeen = new HashMap<>();

    private final Map<ColonyId, Set<BlockPos>> graveyardShellWarned = new HashMap<>();

    private final Map<ColonyId, Long> lastGuardBalance = new HashMap<>();
    private final Map<ColonyId, Long> barracksForDruidsAt = new HashMap<>();
    private static final long BARRACKS_FOR_DRUIDS_COOLDOWN = 48000L;

    private boolean guardStaffingLive(final IColony colony)
    {
        return AutopilotConfig.masterOn() && AutopilotConfig.get(colony, AutopilotConfig.GROWTH_ENABLED) && !plan.isEmpty();
    }

    private static final Map<ColonyId, Long> anticipationConversion = new HashMap<>();

    static void grantAnticipationConversion(final IColony colony, final long gameDay)
    {
        anticipationConversion.put(ColonyAutopilot.colonyKey(colony), gameDay);
    }

    private final Map<ColonyId, Long> blockedUntil = new HashMap<>();

    private final Map<ColonyId, String> lastGateLogged = new HashMap<>();

    private final Map<ColonyId, String> housingHoldLogged = new HashMap<>();

    private final Map<ColonyId, Set<Block>> unavailableHuts = new HashMap<>();

    private final Map<ColonyId, String> unavailablePack = new HashMap<>();

    private static final Map<String, Boolean> stableInPack = new HashMap<>();

    private static final Map<String, Long> stableProbedAt = new HashMap<>();

    static Boolean shipsStable(final IColony colony)
    {
        final String packName = colony.getStructurePack();
        final Boolean known = stableInPack.get(packName);
        final StructurePackMeta pack = known == null ? StructurePacks.getStructurePack(packName) : null;
        if (known != null || !(colony.getWorld() instanceof ServerLevel level) || pack == null)
        {
            return known;
        }

        final Long asked = stableProbedAt.get(packName);
        if (asked != null && level.getGameTime() - asked < 2400)
        {
            return null;
        }

        final ColonyGrounds world = ColonyGrounds.get(level.getServer().overworld());
        final String edition = pack.isImmutable()
                                 ? pack.getVersion() + java.util.stream.Stream.of(pack.getOwner(), "structurize", "minecolonies", ColonyAutopilot.MOD_ID).distinct()
                                     .map(mod -> " " + mod + "@" + net.neoforged.fml.ModList.get().getModContainerById(mod)
                                       .map(container -> container.getModInfo().getVersion().toString()).orElse("-"))
                                     .collect(java.util.stream.Collectors.joining())
                                 : null;
        final Boolean saved = edition == null ? null : world.stableInPack(packName, edition);
        if (saved != null)
        {
            stableInPack.put(packName, saved);
            return saved;
        }
        stableProbedAt.put(packName, level.getGameTime());

        final String hutName = ModBlocks.blockHutStable.getBlueprintName();
        final HolderLookup.Provider registries = level.registryAccess();
        final CompletableFuture<Blueprint> future = CompletableFuture.supplyAsync(
          () -> findDesign(packName, ModBlocks.blockHutStable, hutName, null, registries), IOPool.getExecutor());
        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(future, level, found -> {
            stableInPack.put(packName, found != null);
            stableProbedAt.remove(packName);
            if (edition != null)
            {
                world.setStableInPack(packName, edition, found != null);
            }
        }));
        return null;
    }

    private final Map<ColonyId, Map<Block, Long>> plotBlockedUntil = new HashMap<>();

    private final Map<ColonyId, Set<Block>> researchLockLogged = new HashMap<>();

    private final Set<IBuilding> pendingRoadReconnects = new HashSet<>();

    private final Set<IBuilding> apronOwed = new HashSet<>();

    private final Map<ColonyId, Map<BlockPos, List<BlockPos>>> roadLedger = new HashMap<>();

    private final Set<ColonyId> roadsLoaded = new HashSet<>();

    private final Set<ColonyId> bootSwept = new HashSet<>();

    private static final int BOOT_SWEEP_AFTER_EVALUATIONS = 4;

    private static final int BOOT_SWEEP_PASSES = 4;

    private final Map<ColonyId, Integer> bootDelay = new HashMap<>();

    private final Map<ColonyId, Integer> bootPass = new HashMap<>();

    private final Set<ColonyId> bootChanged = new HashSet<>();

    private final Map<ColonyId, BlockPos> townHallAproned = new HashMap<>();

    private final Map<ColonyId, Integer> apronRetries = new HashMap<>();

    private static final int APRON_RETRIES = 5;

    private final Map<GlobalPos, Long> offsetLearning = new HashMap<>();

    private final Map<GlobalPos, Integer> offsetMisses = new HashMap<>();

    private final Map<ColonyId, Long> pondTended = new HashMap<>();

    private final Map<ColonyId, Long> ravineChecked = new HashMap<>();
    private final Map<ColonyId, Set<BlockPos>> roadsAwaitingGrade = new HashMap<>();

    private final Map<ColonyId, VillageGrounds.Sweep> groundsSweeps = new HashMap<>();

    private final Set<ColonyId> sweepOwed = new HashSet<>();

    private final Map<ColonyId, RavineSurvey> ravineSurveys = new HashMap<>();

    private final JobWatch jobWatch = new JobWatch();

    private final Map<ColonyId, List<BlockPos>> lavaDeathSpots = new HashMap<>();

    private final Map<ColonyId, List<BlockPos>> drownDeathSpots = new HashMap<>();

    private final Map<ColonyId, List<BlockPos>> fallDeathSpots = new HashMap<>();

    private final Map<BlockPos, IColony> rescuedHoles = new LinkedHashMap<>();

    private final Set<BlockPos> holesAwaiting = new HashSet<>();

    private final Map<ColonyId, Map<Block, Integer>> plotFailStrikes = new HashMap<>();

    private static final int PLOT_SHORTAGE_HUTS = 3;

    private record FirstStrike(String name, long until, boolean carried) {}

    private final Map<ColonyId, Map<Block, FirstStrike>> firstStrikeRun = new HashMap<>();

    private final Map<ColonyId, Set<Block>> plotShortage = new HashMap<>();

    private final Map<ColonyId, Map<Block, Integer>> fullParks = new HashMap<>();

    private final Map<ColonyId, Set<Block>> structuresYielded = new HashMap<>();

    private record Rescue(String stepName, Block hut, BlockPos frontier, SiteSelector.Footprint land)
    {
    }

    private final Map<ColonyId, Rescue> builderRescue = new HashMap<>();

    private final Map<ColonyId, StagedFrontier> rescueFrontier = new HashMap<>();

    private record StagedFrontier(BlockPos frontier, long until) {}

    private static final int COLD_PAD_TRIES = 10;

    private final Map<ColonyId, Map<Block, Integer>> coldPadTries = new HashMap<>();

    private final Map<String, java.nio.file.Path> designPaths = new HashMap<>();

    private final Map<ColonyId, Boolean> universityWidened = new HashMap<>();

    private final Map<ColonyId, Map<Block, SiteSelector.Footprint>> lastTerraform = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> stairRefused = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> stairLonger = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> walkRefused = new HashMap<>();

    private int lastCarveSpent;

    private static final int HEAVY_CARVE_EXPANSIONS = 10_000;

    private final Map<ColonyId, Set<Block>> rescueRefused = new HashMap<>();

    private final Map<ColonyId, Long> importantCallUnanswered = new HashMap<>();

    private final Map<ColonyId, Long> walkPlacedLast = new HashMap<>();

    private final List<IBuilding> reconnectLater = new ArrayList<>();

    private final Map<ColonyId, Map<List<BlockPos>, Long>> requeuedAt = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, DeadRoad>> deadRoads = new HashMap<>();

    private record DeadRoad(int cells, boolean told)
    {
    }

    private final Map<ColonyId, Map<BlockPos, OrphanRoad>> orphanRoads = new HashMap<>();

    private record OrphanRoad(Set<BlockPos> blocks, int days)
    {
    }

    private final Set<ColonyId> liftedRoads = new HashSet<>();

    private final Map<ColonyId, Long> relaidAt = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, String>> lastBreak = new HashMap<>();

    private final Map<ColonyId, Map<Block, SiteSelector.Result>> terraformAnchors = new HashMap<>();

    private static final int MAX_BUILDER_HUTS = 4;

    private static final int MAX_RESCUE_BUILDER_HUTS = 12;

    private static final int BUILDINGS_PER_CREW = 15;

    private static final int DOORSTEP_DEPTH = 3;

    private record Entrance(BlockPos door, Direction side)
    {
    }

    private Terraformer terraformer;

    void setTerraformer(final Terraformer terraformer)
    {
        this.terraformer = terraformer;
    }

    private UpgradeDirector upgrades;

    void setUpgradeDirector(final UpgradeDirector upgrades)
    {
        this.upgrades = upgrades;
    }

    boolean hallRungDue(final IColony colony, final int hallLevel)
    {
        for (final ResolvedStep step : planFor(colony))
        {
            if (step.hut() == ModBlocks.blockHutTownHall && step.targetLevel() > hallLevel)
            {
                return colony.getCitizenManager().getCurrentCitizenCount() >= step.minCitizens();
            }
        }
        return false;
    }

    boolean planComplete(final IColony colony)
    {
        if (plan.isEmpty())
        {
            return false;
        }
        final Map<Block, Integer> countByHut = new HashMap<>();
        final Map<Block, Integer> maxLevelByHut = new HashMap<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            countByHut.merge(building.getBuildingType().getBuildingBlock(), 1, Integer::sum);
            maxLevelByHut.merge(building.getBuildingType().getBuildingBlock(), building.getBuildingLevel(), Integer::max);
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Set<Block> unavailable = unavailableHuts.getOrDefault(key, Set.of());
        for (final ResolvedStep step : planFor(colony))
        {
            if (unavailable.contains(step.hut()))
            {
                continue;
            }
            if (step.hut() == ModBlocks.blockHutGraveyard && AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES))
            {
                continue;
            }

            final int standing = countByHut.getOrDefault(step.hut(), 0)
                                   + (step.hut() == ModBlocks.blockHutGuardTower ? countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) : 0);
            if (step.targetLevel() > 0
                  ? maxLevelByHut.getOrDefault(step.hut(), 0) < step.targetLevel()
                  : standing < step.targetCount())
            {
                return false;
            }
        }
        return true;
    }

    String growthStatus(final IColony colony)
    {
        if (plan.isEmpty())
        {
            return "growth: plan not loaded";
        }
        final Map<Block, Integer> countByHut = new HashMap<>();
        final Map<Block, Integer> maxLevelByHut = new HashMap<>();

        int lowestHomeLevel = Integer.MAX_VALUE;
        int homeBlueprintMax = 0;
        int homesShallow = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Block block = building.getBuildingType().getBuildingBlock();
            countByHut.merge(block, 1, Integer::sum);
            maxLevelByHut.merge(block, building.getBuildingLevel(), Integer::max);
            if (block == ModBlocks.blockHutHome)
            {
                lowestHomeLevel = Math.min(lowestHomeLevel, building.getBuildingLevel());
                homeBlueprintMax = Math.max(homeBlueprintMax, building.getMaxBuildingLevel());
                if (building.getBuildingLevel() <= 1)
                {
                    homesShallow++;
                }
            }
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Set<Block> unavailable = unavailableHuts.getOrDefault(key, Set.of());
        final Map<Block, Long> plotBlocked = plotBlockedUntil.getOrDefault(key, Map.of());
        final var census = colony.getCitizenManager();
        final int citizens = census.getCurrentCitizenCount();
        final boolean bedCapped = census.getMaxCitizens() > 0 && citizens >= census.getMaxCitizens()
              && census.getMaxCitizens() < census.maxCitizensFromResearch();
        final List<ResolvedStep> walk = planFor(colony);

        final Boolean stableShipped = stableInPack.get(colony.getStructurePack());
        int met = 0;
        int waived = 0;

        ResolvedStep first = null;
        String next = null;
        String why = null;
        String passed = null;
        int passedOver = 0;

        final StringBuilder importantPassed = new StringBuilder();
        int importantNamed = 0;
        for (final ResolvedStep step : walk)
        {
            if (unavailable.contains(step.hut()))
            {
                met++;
                waived++;
            }
            else if (step.hut() == ModBlocks.blockHutGraveyard && AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES))
            {
                met++;
            }
            else if (step.targetLevel() > 0
                  ? maxLevelByHut.getOrDefault(step.hut(), 0) >= step.targetLevel()
                  : countByHut.getOrDefault(step.hut(), 0)
                      + (step.hut() == ModBlocks.blockHutGuardTower ? countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) : 0) >= step.targetCount())
            {
                met++;
            }

            else if (step.targetLevel() == 0 && step.hut() == ModBlocks.blockHutStable && Boolean.FALSE.equals(stableShipped))
            {
                met++;
                waived++;
            }
            else if (next == null)
            {
                if (first == null)
                {
                    first = step;
                }

                final boolean barracks = step.targetLevel() == 0 && step.hut() == ModBlocks.blockHutGuardTower && barracksDue(colony, countByHut, unavailable);
                final Block hut = barracks ? ModBlocks.blockHutBarracks : step.hut();
                final ResourceLocation research = colony.getResearchManager().getResearchEffectIdFrom(hut);
                final String hold = hut == ModBlocks.blockHutHome
                                      ? housingHold(colony, countByHut.getOrDefault(hut, 0), lowestHomeLevel, homeBlueprintMax,
                                          maxLevelByHut.getOrDefault(ModBlocks.blockHutTownHall, 0), bedCapped, homesShallow)
                                      : null;
                final String reason;
                boolean stops = true;
                if (step.targetLevel() > 0 && citizens >= step.minCitizens())
                {
                    final int stands = maxLevelByHut.getOrDefault(hut, 0);

                    final boolean ours = stands == 0 ? AutopilotConfig.buildsPlacedHuts(colony) : AutopilotConfig.upgradesBuildings(colony);
                    reason = (ours ? "raising it" : "yours to raise (switched off under autoupgrade)")
                               + " from level " + stands + " to " + step.targetLevel() + " — the plan waits on the hall";
                }
                else if (step.targetLevel() == 0 && hut == ModBlocks.blockHutTownHall)
                {
                    reason = "no town hall stands — place yours; the plan never places one";
                }

                else if (step.targetLevel() == 0 && importantHut(hut) && terraformer != null && terraformer.cuttingPadFor(colony, hut))
                {
                    reason = "its ground is being levelled";
                }
                else if (step.targetLevel() == 0 && colony.getWorld() != null && plotBlocked.getOrDefault(hut, 0L) > colony.getWorld().getGameTime())
                {

                    final Rescue bridging = builderRescue.get(key);
                    reason = bridging != null && bridging.hut() == hut
                               ? "waiting for a builder's hut to reach its land at " + bridging.frontier().toShortString() + " (parked)"
                               : "no buildable plot yet (parked)";
                    stops = false;
                }

                else if (step.targetLevel() == 0 && hut == ModBlocks.blockHutStable && stableShipped == null)
                {

                    reason = StructurePacks.getStructurePack(colony.getStructurePack()) == null
                               ? "structure pack '" + colony.getStructurePack() + "' is not loaded" : "looking for its blueprint in the style pack";
                    stops = false;
                }
                else if (citizens < step.minCitizens())
                {
                    reason = "waiting for " + step.minCitizens() + " citizens (has " + citizens + ")";
                }

                else if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(research)
                           && colony.getResearchManager().getResearchEffects().getEffectStrength(research) < 1)
                {
                    reason = "awaits university research";
                    stops = false;
                }
                else if (hold != null)
                {
                    reason = hold.startsWith("cap") ? "held at " + hold + ": the town hall must rise before more homes do"
                               : "held: the standing homes " + hold + " first";
                    stops = false;
                }

                else if (hut != ModBlocks.blockHutHome && !importantHut(hut) && citizens >= census.getMaxCitizens() && census.getJoblessCitizen() == null)
                {
                    reason = "nobody is free to staff it";
                    stops = false;
                }
                else
                {
                    reason = StructurePacks.getStructurePack(colony.getStructurePack()) == null
                               ? "structure pack '" + colony.getStructurePack() + "' is not loaded" : "next up";
                }
                if (stops)
                {
                    next = barracks ? "Barracks" : step.name();

                    why = "next up".equals(reason) && importantHut(hut)
                            && countByHut.getOrDefault(ModBlocks.blockHutGuardTower, 0) + countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) > 0
                            ? reason + " — new guard posts and builders' huts wait for it" : reason;
                }
                else if (passedOver++ == 0)
                {
                    passed = (barracks ? "Barracks" : step.name()) + " (" + reason + ")";
                }
                else if (importantHut(hut))
                {

                    importantPassed.append(", ").append(step.name()).append(" (").append(reason).append(")");
                    importantNamed++;
                }
            }
        }

        final String off = !AutopilotConfig.masterOn()
                             ? "the autopilot is OFF in this world — nothing is placed or upgraded until /colonyautopilot on"
                             : !AutopilotConfig.get(colony, AutopilotConfig.GROWTH_ENABLED)
                                 ? "growth is OFF for this colony (growth.enabled) — nothing new is placed; upgrades follow the autoupgrade switches"
                                 : null;
        if (first == null)
        {

            final String upgradesNote = AutopilotConfig.upgradesBuildings(colony) ? "upgrades continue" : "upgrades are yours (switched off under autoupgrade)";
            return "growth: plan complete (" + met + "/" + walk.size() + " steps"
                     + (waived > 0 ? ", " + waived + " waived — no blueprint for them" : "")
                     + (off != null ? ") — " + off
                          : AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED) ? ") — expansion continues, " + upgradesNote
                          : ") — expansion is OFF ('/colonyautopilot expansion on' resumes it), " + upgradesNote);
        }
        final String steps = "growth: " + met + "/" + walk.size() + " steps — next: ";

        if (off != null)
        {
            return steps + first.name() + " (" + off + ")";
        }
        if (!AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED))
        {
            return steps + first.name() + " (expansion is OFF — nothing new is placed; '/colonyautopilot expansion on' resumes it)";
        }
        final int unnamed = passedOver - 1 - importantNamed;
        final String over = passedOver == 0 ? "" : "passed over: " + passed + importantPassed
                                                     + (unnamed > 0 ? " and " + unnamed + " more" : "");

        final String held = AutopilotConfig.progressionMode(colony) ? BuilderCap.holdText(colony) : "";
        return steps + (next == null ? "nothing the plan can place just now; " + over : next + " (" + why + ")" + (over.isEmpty() ? "" : "; " + over))
                 + (held.isEmpty() ? "" : " — " + held);
    }

    static int beds(final IColony colony)
    {
        final int beds = colony.getCitizenManager().getMaxCitizens();
        if (beds > 1)
        {
            return beds;
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() > 0 && (building.hasModule(LivingBuildingModule.class)
                  || (building.hasModule(BuildingModules.BED) && building.hasModule(WorkAtHomeBuildingModule.class))))
            {
                return beds;
            }
        }
        return 0;
    }

    List<SiteSelector.Footprint> reservedFootprints(final IColony colony)
    {
        final Map<BlockPos, SiteSelector.Footprint> reserved = reservedPlots.get(ColonyAutopilot.colonyKey(colony));
        if (reserved == null)
        {
            return List.of();
        }

        pruneReservations(colony, reserved);
        return List.copyOf(reserved.values());
    }

    private static void pruneReservations(final IColony colony, final Map<BlockPos, SiteSelector.Footprint> reserved)
    {
        reserved.entrySet().removeIf(entry -> {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(entry.getKey());
            if (building == null)
            {
                return true;
            }
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            return corners.getB().getX() - corners.getA().getX() >= 2 || corners.getB().getZ() - corners.getA().getZ() >= 2;
        });
    }

    List<SiteSelector.Footprint> sacredGround(final IColony colony)
    {
        final List<SiteSelector.Footprint> sacred = new ArrayList<>(reservedFootprints(colony));
        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext ->
          ext.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) || ext.hasModule(IPlantationModule.class)))
        {
            final BlockPos pos = extension.getPosition();
            final int shield = extension instanceof FarmField field
                                 ? Math.max(Math.max(field.getRadius(Direction.SOUTH), field.getRadius(Direction.WEST)),
                                     Math.max(field.getRadius(Direction.NORTH), field.getRadius(Direction.EAST))) + 1
                                 : 9;
            sacred.add(new SiteSelector.Footprint(pos.getX() - shield, pos.getZ() - shield, pos.getX() + shield, pos.getZ() + shield));
        }

        for (final WorkOrderPlantationField order : colony.getWorkManager().getWorkOrdersOfType(WorkOrderPlantationField.class))
        {
            final BlockPos loc = order.getLocation();
            sacred.add(new SiteSelector.Footprint(loc.getX() - 9, loc.getZ() - 9, loc.getX() + 9, loc.getZ() + 9));
        }

        sacred.addAll(PlantationFields.fieldGround(colony));
        if (colony.getWorld() instanceof ServerLevel level)
        {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFisherman)
                {

                    final List<BlockPos> waters = FishingPond.memorizedWaters(building);
                    final List<SiteSelector.Footprint> dug = FishingPond.dugPondShields(level, colony, building);

                    if (dug.isEmpty())
                    {
                        final BlockPos trusted = FishingPond.findTrustedWater(level, FishingPond.groundAnchor(level, colony, building.getPosition()));
                        if (trusted != null)
                        {
                            waters.add(trusted);
                        }
                    }
                    for (final BlockPos water : waters)
                    {
                        sacred.add(new SiteSelector.Footprint(water.getX() - 6, water.getZ() - 6, water.getX() + 6, water.getZ() + 6));
                    }

                    sacred.addAll(dug);
                }
            }
        }

        if (colony.getWorld() instanceof ServerLevel level)
        {
            final ColonyGrounds grounds = ColonyGrounds.get(level);
            for (final Map.Entry<Long, int[]> ornament : grounds.decorations(colony.getID()).entrySet())
            {
                final BlockPos anchor = BlockPos.of(ornament.getKey());
                final int[] box = ornament.getValue();

                final BlockPos witness = box.length >= 7 ? new BlockPos(box[4], box[5], box[6]) : anchor;
                if (WorldUtil.isChunkLoaded(level, anchor.getX() >> 4, anchor.getZ() >> 4)
                      && WorldUtil.isChunkLoaded(level, witness.getX() >> 4, witness.getZ() >> 4)
                      && !(level.getBlockState(anchor).getBlock() instanceof BlockDecorationController)
                      && level.getBlockState(witness).canBeReplaced()
                      && colony.getWorkManager().getWorkOrdersOfType(WorkOrderDecoration.class).stream()
                           .noneMatch(order -> anchor.equals(order.getLocation())))
                {
                    grounds.removeDecoration(colony.getID(), anchor);
                    continue;
                }
                sacred.add(new SiteSelector.Footprint(box[0], box[1], box[2], box[3]));
            }
        }
        sacred.addAll(ProtectedZones.footprintsFor(colony));

        sacred.addAll(WorldStructures.footprintsFor(colony));
        return sacred;
    }

    private static String housingHold(final IColony colony, final int homes, final int lowestHomeLevel, final int homeBlueprintMax,
      final int townHallLevel, final boolean bedCapped, final int homesShallow)
    {
        if (homes <= 1)
        {
            return null;
        }
        final int cap = 3 + 2 * townHallLevel;

        final int ceiling = AutopilotConfig.upgradesBuildings(colony) ? Math.min(Math.max(1, homeBlueprintMax), townHallLevel + 1) : 1;
        if (lowestHomeLevel < ceiling)
        {

            return bedCapped && homes < cap && homesShallow < 2 ? null : "deepen to level " + ceiling;
        }

        return homes < cap || bedCapped ? null : "cap " + cap;
    }

    private boolean homePlacementHeld(final IColony colony, final ColonyId key, final int homes,
      final int lowestHomeLevel, final int homeBlueprintMax, final int townHallLevel, final boolean bedCapped, final int homesShallow)
    {
        final String hold = housingHold(colony, homes, lowestHomeLevel, homeBlueprintMax, townHallLevel, bedCapped, homesShallow);

        final int cap = 3 + 2 * townHallLevel;
        final int ceiling = AutopilotConfig.upgradesBuildings(colony) ? Math.min(Math.max(1, homeBlueprintMax), townHallLevel + 1) : 1;
        if (hold == null)
        {

            if (homes >= cap)
            {

                if (!("over " + homes).equals(housingHoldLogged.put(key, "over " + homes)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] housing exceeds its cap: {} homes at cap {} (town hall level {}), all at ceiling {}, and beds still bind — placing one more",
                      colony.getName(), homes, cap, townHallLevel, ceiling);
                }
            }
            else
            {
                housingHoldLogged.remove(key);
            }
            return false;
        }
        if (!hold.equals(housingHoldLogged.put(key, hold)))
        {
            ColonyAutopilot.LOGGER.info("[{}] housing holds at {} homes (cap {} at town hall level {}) — {}",
              colony.getName(), homes, cap, townHallLevel,
              lowestHomeLevel < ceiling
                ? "the standing homes upgrade to level " + ceiling + " before any new one is placed"
                : "the town hall must rise before more homes do");
        }
        return true;
    }

    void groundReshaped(final IColony colony, final SiteSelector.Footprint pad, final List<int[]> ramp)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final Map<BlockPos, List<BlockPos>> ledger = roadsOf(colony, level);
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final List<BlockPos> road = ledger.get(building.getPosition());
            if (building.getBuildingLevel() <= 0 || road == null)
            {
                continue;
            }
            boolean touched = false;
            for (final BlockPos block : road)
            {
                if (pad.intersects(block.getX() - 3, block.getZ() - 3, block.getX() + 3, block.getZ() + 3))
                {
                    touched = true;
                    break;
                }
                for (final int[] tread : ramp)
                {
                    if (Math.abs(tread[0] - block.getX()) <= 2 && Math.abs(tread[1] - block.getZ()) <= 2)
                    {
                        touched = true;
                        break;
                    }
                }
                if (touched)
                {
                    break;
                }
            }
            if (touched)
            {
                pendingRoadReconnects.add(building);
            }
        }
    }

    private void continueRoadPasses(final IColony colony, final ColonyId key)
    {
        if (!bootPass.containsKey(key) || pendingRoadReconnects.stream().anyMatch(building -> building.getColony() == colony)
              || reconnectLater.stream().anyMatch(building -> building.getColony() == colony))
        {
            return;
        }

        final int pass = bootPass.get(key);
        if (bootChanged.remove(key) && pass < BOOT_SWEEP_PASSES)
        {
            bootPass.put(key, pass + 1);
            queueRoads(colony);
        }
        else
        {
            bootPass.remove(key);
        }
    }

    int relayRoads(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        liftedRoads.remove(key);
        if (colony.getWorld() instanceof ServerLevel level)
        {
            roadsOf(colony, level);
            relaidAt.put(key, level.getGameTime());
        }

        bootSwept.add(key);
        bootDelay.remove(key);
        bootChanged.remove(key);
        bootPass.put(key, 1);
        return queueRoads(colony);
    }

    public static String roadsRefused(final IColony colony)
    {
        if (!AutopilotConfig.masterOn())
        {
            return "The autopilot is off (/colonyautopilot on) — nothing lays roads until it is on.";
        }
        if (!AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS))
        {
            return "Roads are off for " + colony.getName() + " (growth.buildPaths) — turn them on first.";
        }
        return instance.liftedRoads.contains(ColonyAutopilot.colonyKey(colony))
                 ? "An operator took up the roads of " + colony.getName() + " ('roads lift') — none is laid until an operator's '/colonyautopilot colony roads' or the next restart."
                 : "";
    }

    public static Component relayRoadsPressed(final IColony colony, final String player)
    {
        final String refused = roadsRefused(colony);
        if (!refused.isEmpty())
        {
            return Component.literal(refused).withStyle(ChatFormatting.RED);
        }
        final Long last = instance.relaidAt.get(ColonyAutopilot.colonyKey(colony));
        if (last != null && colony.getWorld().getGameTime() - last < 1200)
        {
            return Component.literal("The roads of " + colony.getName() + " were sent to be laid again less than a game-minute ago — let that re-lay run first.")
                     .withStyle(ChatFormatting.RED);
        }
        final int queued = instance.relayRoads(colony);
        ColonyAutopilot.LOGGER.info("[{}] {} asked for the roads to be laid again (the Exchange page): {} building(s) queued", colony.getName(), player, queued);
        return Component.literal(queued == 0 ? colony.getName() + " has no finished building with a road to lay."
            : "Laying the roads of " + queued + " building" + (queued == 1 ? "" : "s") + " of " + colony.getName() + " again, two a tick, in passes until they settle — the log names each door and any road that does not join up.");
    }

    int[] liftRoads(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return new int[] {0, 0};
        }
        final Map<BlockPos, List<BlockPos>> ledger = roadsOf(colony, level);
        int roads = 0;
        int blocks = 0;
        for (final Map.Entry<BlockPos, List<BlockPos>> entry : new ArrayList<>(ledger.entrySet()))
        {
            final List<BlockPos> left = VillagePaths.erase(level, entry.getValue(), Set.of(), Set.of(), ColonyGrounds.get(level).roadWater(colony.getID()));
            blocks += entry.getValue().size() - left.size();
            roads++;

            ColonyGrounds.get(level).setRoadLine(colony.getID(), entry.getKey(), null);
            if (left.isEmpty())
            {
                ledger.remove(entry.getKey());
            }
            else
            {
                ledger.put(entry.getKey(), left);
            }
        }

        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        bootSwept.add(key);
        bootDelay.remove(key);
        bootPass.remove(key);
        bootChanged.remove(key);
        liftedRoads.add(key);
        saveRoads(colony, level);
        return new int[] {roads, blocks};
    }

    void plotFreed(final ColonyId key, final Block hut)
    {
        final Map<Block, Long> blocked = plotBlockedUntil.get(key);
        if (blocked != null)
        {
            blocked.remove(hut);
        }

        final Map<Block, FirstStrike> run = firstStrikeRun.get(key);
        if (run != null)
        {
            run.remove(hut);
        }
    }

    void terraformAbandoned(final ColonyId key, final Block hut)
    {
        final Map<Block, SiteSelector.Result> anchors = terraformAnchors.get(key);
        if (anchors != null)
        {
            anchors.remove(hut);
        }
        final Map<Block, SiteSelector.Footprint> rects = lastTerraform.get(key);
        if (rects != null)
        {
            rects.remove(hut);
        }
        plotFreed(key, hut);
    }

    public void subscribeToMineColonies()
    {
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(BuildingConstructionModEvent.class, this::onConstructionCompleted);
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(CitizenDiedModEvent.class, this::onCitizenPerished);
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyDeletedModEvent.class, this::onColonyDeleted);
    }

    private void onConstructionCompleted(final BuildingConstructionModEvent event)
    {
        if (event.getWorkOrder() == null
              || event.getWorkOrder().getWorkOrderType() == WorkOrderType.REMOVE
              || event.getWorkOrder().getWorkOrderType() == WorkOrderType.REPAIR)
        {
            return;
        }
        pendingRoadReconnects.add(event.getBuilding());
        final IBuilding built = event.getBuilding();

        if (event.getWorkOrder().getWorkOrderType() == WorkOrderType.BUILD
              && !built.hasParent()
              && built.getBuildingType().getBuildingBlock() != ModBlocks.blockHutTownHall
              && built.getColony() != null && built.getColony().getWorld() instanceof ServerLevel world
              && !ColonyGrounds.get(world).hasAnchorOffset(built.getColony().getID(), built.getPosition()))
        {
            apronOwed.add(built);
        }

        if (built.getBuildingType().getBuildingBlock() == ModBlocks.blockHutTownHall
              && built.getColony() != null && built.getColony().getWorld() instanceof ServerLevel level)
        {
            ColonyGrounds.get(level).clearApronDone(built.getColony().getID());
            townHallAproned.remove(ColonyAutopilot.colonyKey(built.getColony()));
            apronRetries.remove(ColonyAutopilot.colonyKey(built.getColony()));
        }
    }

    private void onCitizenPerished(final CitizenDiedModEvent event)
    {

        if (event.getCitizen() instanceof ICitizenData fallen && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn()
              && !plan.isEmpty() && AutopilotConfig.get(fallen.getColony(), AutopilotConfig.GROWTH_ENABLED))
        {
            try
            {
                staffGuardPosts(fallen.getColony());
            }
            catch (final RuntimeException e)
            {

                ColonyAutopilot.LOGGER.warn("Posting the empty guard towers of colony {} afresh failed", fallen.getColony().getName(), e);
            }
        }
        final DamageSource source = event.getDamageSource();
        final boolean terrain = source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
                                  || source.is(DamageTypeTags.IS_DROWNING);
        if (!terrain || source.getEntity() != null || !(event.getCitizen() instanceof ICitizenData citizen))
        {
            return;
        }
        final BlockPos where = citizen.getLastPosition();
        ColonyAutopilot.LOGGER.info("[{}] {} died to the terrain ({}) at {}",
          citizen.getColony().getName(), citizen.getName(), source.getMsgId(), where.toShortString());

        if (where.getY() > villageTop(citizen.getColony()) + 24)
        {
            return;
        }

        if (citizen.getColony().getWorld() instanceof ServerLevel land && WorldUtil.isChunkLoaded(land, where.getX() >> 4, where.getZ() >> 4))
        {
            final IColony landlord = IColonyManager.getInstance().getColonyByPosFromWorld(land, where);
            if (landlord != null && landlord != citizen.getColony())
            {
                ColonyAutopilot.LOGGER.info("[{}] that spot is {}'s land — it is left as it lies", citizen.getColony().getName(), landlord.getName());
                return;
            }
        }

        if (source.is(DamageTypeTags.IS_FIRE) && citizen.getColony().getWorld() instanceof ServerLevel level)
        {
            if (secondStrike(lavaDeathSpots, citizen.getColony(), where))
            {
                drainFluidBody(level, citizen.getColony(), where, true);
            }
            else
            {
                sealLavaAround(level, citizen.getColony(), where);
            }
        }
        else if (source.is(DamageTypeTags.IS_DROWNING) && citizen.getColony().getWorld() instanceof ServerLevel level)
        {
            if (secondStrike(drownDeathSpots, citizen.getColony(), where))
            {
                if (nearFisherWater(citizen.getColony(), where))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the drowning water at {} is the fisher's — leaving it undrained",
                      citizen.getColony().getName(), where.toShortString());
                }
                else
                {
                    drainFluidBody(level, citizen.getColony(), where, false);
                }
            }
        }
        else if (source.is(DamageTypeTags.IS_FALL) && citizen.getColony().getWorld() instanceof ServerLevel level)
        {
            padLanding(level, citizen.getColony(), where);
            if (secondStrike(fallDeathSpots, citizen.getColony(), where))
            {
                fillDeadlyDrop(level, citizen.getColony(), where, null);
            }
        }

        final ColonyId key = ColonyAutopilot.colonyKey(citizen.getColony());
        if (citizen.getColony().getWorld() instanceof ServerLevel && AutopilotConfig.live(citizen.getColony(), AutopilotConfig.BUILD_PATHS)
              && !liftedRoads.contains(key) && bootSwept.contains(key))
        {
            try
            {
                groundReshaped(citizen.getColony(), new SiteSelector.Footprint(where.getX() - 10, where.getZ() - 10, where.getX() + 10, where.getZ() + 10), List.of());
            }
            catch (final RuntimeException e)
            {

                ColonyAutopilot.LOGGER.warn("[{}] queueing the roads near the death at {} for a re-carve failed", citizen.getColony().getName(), where.toShortString(), e);
            }
        }
    }

    private static List<SiteSelector.Footprint> untouchableGround(final ServerLevel level, final IColony colony,
      final int minX, final int minZ, final int maxX, final int maxZ)
    {
        final List<SiteSelector.Footprint> ground = new ArrayList<>(ProtectedZones.footprintsFor(colony));
        ground.addAll(SiteSelector.foreignClaims(level, colony, minX, minZ, maxX, maxZ));
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {

            if (building.getBuildingLevel() > 0)
            {
                ground.add(SiteSelector.boxOf(building));
            }
        }
        return ground;
    }

    private static void sealLavaAround(final ServerLevel level, final IColony colony, final BlockPos center)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            return;
        }
        final int radius = 12;
        final List<SiteSelector.Footprint> shields = untouchableGround(level, colony,
          center.getX() - radius, center.getZ() - radius, center.getX() + radius, center.getZ() + radius);

        for (int chunkX = (center.getX() - radius) >> 4; chunkX <= (center.getX() + radius) >> 4; chunkX++)
        {
            for (int chunkZ = (center.getZ() - radius) >> 4; chunkZ <= (center.getZ() + radius) >> 4; chunkZ++)
            {
                if (!WorldUtil.isChunkLoaded(level, chunkX, chunkZ))
                {
                    return;
                }
            }
        }
        int sealed = 0;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++)
        {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++)
            {
                if (SiteSelector.insideAny(shields, x, z))
                {
                    continue;
                }
                for (int y = center.getY() - 6; y <= center.getY() + 3; y++)
                {
                    if (!level.getBlockState(pos.set(x, y, z)).getFluidState().is(FluidTags.LAVA))
                    {
                        continue;
                    }

                    final BlockState above = level.getBlockState(pos.set(x, y + 1, z));
                    if (above.getFluidState().isEmpty() && (above.isAir() || above.canBeReplaced()))
                    {
                        WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.COBBLESTONE.defaultBlockState());
                        sealed++;
                    }
                }
            }
        }
        if (sealed > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] capped {} lava blocks around the death spot at {} — that pool takes nobody else",
              colony.getName(), sealed, center.toShortString());
        }
    }

    private static boolean secondStrike(final Map<ColonyId, List<BlockPos>> ledger, final IColony colony, final BlockPos where)
    {
        final List<BlockPos> priors = ledger.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new ArrayList<>());
        final boolean samePool = priors.stream().anyMatch(prior -> prior.distSqr(where) <= 32 * 32);
        priors.add(where.immutable());
        if (priors.size() > 10)
        {
            priors.remove(0);
        }
        return samePool;
    }

    private static boolean nearFisherWater(final IColony colony, final BlockPos where)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFisherman
                  && building.getBuildingLevel() > 0 && building.getPosition().distSqr(where) <= 64 * 64)
            {
                return true;
            }
        }
        return false;
    }

    private static void padLanding(final ServerLevel level, final IColony colony, final BlockPos center)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            ColonyAutopilot.LOGGER.debug("[{}] fall at {} not padded — terraforming is disabled", colony.getName(), center.toShortString());
            return;
        }
        if (!WorldUtil.isChunkLoaded(level, center.getX() >> 4, center.getZ() >> 4))
        {
            ColonyAutopilot.LOGGER.info("[{}] could not pad the fall at {} — its chunk is not loaded", colony.getName(), center.toShortString());
            return;
        }
        final List<SiteSelector.Footprint> shields = untouchableGround(level, colony, center.getX() - 1, center.getZ() - 1, center.getX() + 1, center.getZ() + 1);
        if (SiteSelector.insideAny(shields, center.getX(), center.getZ()))
        {
            ColonyAutopilot.LOGGER.info("[{}] fall at {} not padded — inside a building's grounds or a protected zone", colony.getName(), center.toShortString());
            return;
        }
        int padded = 0;
        int hayed = 0;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= 1; x++)
        {
            for (int z = -1; z <= 1; z++)
            {

                if (SiteSelector.insideAny(shields, center.getX() + x, center.getZ() + z)
                      || !WorldUtil.isChunkLoaded(level, (center.getX() + x) >> 4, (center.getZ() + z) >> 4))
                {
                    continue;
                }

                for (int y = 0; y >= -3; y--)
                {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    final BlockState floor = level.getBlockState(pos);
                    if (!floor.isSolidRender(level, pos))
                    {
                        continue;
                    }
                    if (floor.is(Blocks.HAY_BLOCK))
                    {
                        hayed++;
                        break;
                    }
                    final BlockState above = level.getBlockState(pos.move(Direction.UP));
                    if (above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty()))
                    {
                        WorldUtil.setBlockState(level, pos, Blocks.HAY_BLOCK.defaultBlockState());
                        padded++;
                    }
                    break;
                }
            }
        }
        if (padded == 0 && hayed > 0)
        {

            ColonyAutopilot.LOGGER.info("[{}] a fall death at {} — the landing is padded already", colony.getName(), center.toShortString());
            return;
        }
        if (padded == 0)
        {
            for (int y = 0; y >= -1; y--)
            {
                pos.set(center.getX(), center.getY() + y, center.getZ());
                final BlockState state = level.getBlockState(pos);
                if (state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty()))
                {
                    WorldUtil.setBlockState(level, pos, Blocks.HAY_BLOCK.defaultBlockState());
                    padded++;
                }
            }
            if (padded > 0)
            {
                ColonyAutopilot.LOGGER.info("[{}] a fall death at {} — no floor to pad, so hay plugs the crack itself",
                  colony.getName(), center.toShortString());
            }
        }
        else
        {
            ColonyAutopilot.LOGGER.info("[{}] a fall death — padded the landing at {} with {} hay bales so the next worker survives",
              colony.getName(), center.toShortString(), padded);
        }
        if (padded == 0)
        {

            for (int y = 1; y <= 3 && padded == 0; y++)
            {
                for (int x = -1; x <= 1 && padded == 0; x++)
                {
                    for (int z = -1; z <= 1 && padded == 0; z++)
                    {
                        if (SiteSelector.insideAny(shields, center.getX() + x, center.getZ() + z)
                              || !WorldUtil.isChunkLoaded(level, (center.getX() + x) >> 4, (center.getZ() + z) >> 4))
                        {
                            continue;
                        }
                        final BlockPos cell = new BlockPos(center.getX() + x, center.getY() + y, center.getZ() + z);
                        final BlockState state = level.getBlockState(cell);
                        if ((state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty()))
                              && level.getBlockState(cell.below()).isSolidRender(level, cell.below()))
                        {
                            WorldUtil.setBlockState(level, cell, Blocks.HAY_BLOCK.defaultBlockState());
                            padded++;
                            ColonyAutopilot.LOGGER.info("[{}] a fall death at {} — the body lay embedded, so hay tops the ground above it",
                              colony.getName(), center.toShortString());
                        }
                    }
                }
            }
        }
        if (padded == 0)
        {
            ColonyAutopilot.LOGGER.warn("[{}] could not pad the fall at {} — solid or fluid in every fillable cell around the body",
              colony.getName(), center.toShortString());
        }
    }

    private int fillDeadlyDrop(final ServerLevel level, final IColony colony, final BlockPos center, final Set<Long> hole)
    {
        final String what = hole == null ? "the twice-deadly drop" : "the hole citizens were rescued from twice";
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            ColonyAutopilot.LOGGER.debug("[{}] {} at {} stays — terraforming is disabled", colony.getName(), what, center.toShortString());
            return 0;
        }
        final int radius = 4;
        if (!WorldUtil.isChunkLoaded(level, (center.getX() - radius) >> 4, (center.getZ() - radius) >> 4)
              || !WorldUtil.isChunkLoaded(level, (center.getX() + radius) >> 4, (center.getZ() - radius) >> 4)
              || !WorldUtil.isChunkLoaded(level, (center.getX() - radius) >> 4, (center.getZ() + radius) >> 4)
              || !WorldUtil.isChunkLoaded(level, (center.getX() + radius) >> 4, (center.getZ() + radius) >> 4))
        {
            ColonyAutopilot.LOGGER.info("[{}] could not fill {} at {} — its ground is not fully loaded", colony.getName(), what, center.toShortString());
            return 0;
        }
        final List<SiteSelector.Footprint> plots = untouchableGround(level, colony,
          center.getX() - radius, center.getZ() - radius, center.getX() + radius, center.getZ() + radius);

        Integer rim = null;
        for (final long cell : hole == null ? Set.<Long>of() : hole)
        {
            rim = rim == null ? BlockPos.getY(cell) : Math.max(rim, BlockPos.getY(cell));
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; hole == null && x <= center.getX() + radius; x++)
        {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++)
            {
                if (x != center.getX() - radius && x != center.getX() + radius
                      && z != center.getZ() - radius && z != center.getZ() + radius)
                {
                    continue;
                }
                for (int y = center.getY() + 6; y >= center.getY() - 8; y--)
                {
                    if (level.getBlockState(pos.set(x, y, z)).isSolidRender(level, pos))
                    {
                        rim = rim == null ? y : Math.min(rim, y);
                        break;
                    }
                }
            }
        }
        if (rim == null || rim <= center.getY() - 8)
        {
            ColonyAutopilot.LOGGER.info("[{}] could not fill the twice-deadly drop at {} — no solid rim reads around it",
              colony.getName(), center.toShortString());
            return 0;
        }
        if (hole != null)
        {

            final Set<Long> band = roadBand(colony, level);
            boolean village = false;
            for (final long cell : hole)
            {
                village |= band.contains(BlockPos.asLong(BlockPos.getX(cell), 0, BlockPos.getZ(cell)));
            }
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                if (village || building.getBuildingLevel() <= 0)
                {
                    continue;
                }
                final SiteSelector.Footprint box = SiteSelector.boxOf(building);
                for (final long cell : hole)
                {
                    village |= box.intersects(BlockPos.getX(cell) - RAVINE_SURVEY_MARGIN,
                      BlockPos.getZ(cell) - RAVINE_SURVEY_MARGIN, BlockPos.getX(cell) + RAVINE_SURVEY_MARGIN, BlockPos.getZ(cell) + RAVINE_SURVEY_MARGIN);
                }
            }

            final List<SiteSelector.Footprint> occupied = SiteSelector.occupiedGround(colony);
            boolean shielded = false;
            for (final long cell : hole)
            {
                shielded |= SiteSelector.insideAny(plots, BlockPos.getX(cell), BlockPos.getZ(cell))
                              || SiteSelector.insideAny(occupied, BlockPos.getX(cell), BlockPos.getZ(cell));
            }
            if (!village)
            {
                ColonyAutopilot.LOGGER.info("[{}] the hole at {} where citizens were rescued twice is left as it is — it lies outside the village: no road beside it and no building within {} blocks",
                  colony.getName(), center.toShortString(), RAVINE_SURVEY_MARGIN);
                return 0;
            }
            if (shielded)
            {
                ColonyAutopilot.LOGGER.info("[{}] the hole at {} where citizens were rescued twice is left as it is — it lies in a building's grounds or site, a field, a protected zone or another colony's land",
                  colony.getName(), center.toShortString());
                return 0;
            }
            final int emptied = emptyHole(level, colony, center, hole, rim);
            if (emptied <= 0)
            {
                return emptied;
            }
        }

        final boolean fisherWater = hole == null && nearFisherWater(colony, center);
        int leftForFisher = 0;
        int filled = 0;
        int open = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++)
        {
            columns:
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++)
            {
                if (hole != null && !hole.contains(BlockPos.asLong(x, rim, z)))
                {
                    continue;
                }

                boolean drop = false;
                for (int y = rim; y >= center.getY() - 8 && !drop; y--)
                {
                    final BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isSolidRender(level, pos))
                    {
                        break;
                    }
                    drop = state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
                }
                if (!drop)
                {
                    continue;
                }
                open++;
                for (final SiteSelector.Footprint plot : plots)
                {
                    if (x >= plot.minX() && x <= plot.maxX() && z >= plot.minZ() && z <= plot.maxZ())
                    {
                        continue columns;
                    }
                }
                for (int y = rim; fisherWater && y >= center.getY() - 8; y--)
                {
                    if (level.getBlockState(pos.set(x, y, z)).getFluidState().is(FluidTags.WATER))
                    {
                        leftForFisher++;
                        continue columns;
                    }
                }
                for (int y = rim; y >= center.getY() - 8; y--)
                {
                    final BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isSolidRender(level, pos))
                    {
                        break;
                    }
                    if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty())
                    {
                        WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.DIRT.defaultBlockState());
                        filled++;
                    }
                }
            }
        }
        if (filled > 0)
        {
            ColonyAutopilot.LOGGER.info(hole == null
                ? "[{}] a second fall death at {} — filled the drop with {} blocks up to the surrounding grade; that crack takes nobody else"
                : "[{}] a second rescue from the hole at {} — filled it with {} blocks of earth up to the ground round it; that hole takes nobody else",
              colony.getName(), center.toShortString(), filled);
        }
        else if (open == 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] a second fall death at {} — no drop lies below the surrounding ground there, so there is nothing to fill",
              colony.getName(), center.toShortString());
        }
        else
        {
            ColonyAutopilot.LOGGER.warn(leftForFisher > 0
                ? "[{}] the twice-deadly drop at {} could not be filled — its open columns hold water beside the fisher's hut (left whole for him) or belong to a building's plot, a protected zone or another colony's land"
                : "[{}] the twice-deadly drop at {} could not be filled — every open column there belongs to a building's plot, a protected zone or another colony's land",
              colony.getName(), center.toShortString());
        }
        return filled;
    }

    private static int villageTop(final IColony colony)
    {
        int villageTop = colony.getCenter().getY();
        for (final IBuilding built : colony.getServerBuildingManager().getBuildings().values())
        {
            villageTop = Math.max(villageTop, built.getPosition().getY());
        }
        return villageTop;
    }

    static Set<Long> holeAt(final ServerLevel level, final BlockPos spot)
    {
        final int radius = 4;
        final int depth = 8;
        final Map<Long, Integer> ground = new HashMap<>();
        for (int x = spot.getX() - radius; x <= spot.getX() + radius; x++)
        {
            for (int z = spot.getZ() - radius; z <= spot.getZ() + radius; z++)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    return null;
                }
                ground.put(BlockPos.asLong(x, 0, z), SiteSelector.groundHeightBelow(level, x, z, spot.getY() + depth + 1) - 1);
            }
        }
        final long own = BlockPos.asLong(spot.getX(), 0, spot.getZ());
        final int floor = ground.get(own);
        if (floor >= spot.getY())
        {
            return null;
        }
        Set<Long> under = null;
        int top = floor + 1;
        for (boolean runsOn = false; !runsOn; top++)
        {
            if (top > spot.getY() + depth)
            {
                return null;
            }
            final Set<Long> joined = new HashSet<>();
            final ArrayDeque<Long> open = new ArrayDeque<>();
            joined.add(own);
            open.add(own);
            while (!open.isEmpty() && !runsOn)
            {
                final long column = open.poll();
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final int x = BlockPos.getX(column) + side.getStepX();
                    final int z = BlockPos.getZ(column) + side.getStepZ();
                    final long next = BlockPos.asLong(x, 0, z);
                    if (joined.contains(next) || ground.get(next) >= top)
                    {
                        continue;
                    }
                    if (Math.abs(x - spot.getX()) == radius || Math.abs(z - spot.getZ()) == radius)
                    {
                        runsOn = true;
                        break;
                    }
                    joined.add(next);
                    open.add(next);
                }
            }
            if (!runsOn)
            {
                under = joined;
            }
        }
        top -= 2;
        if (under == null || top - floor < 2)
        {
            return null;
        }
        final Set<Long> cells = new HashSet<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (final long column : under)
        {
            final int x = BlockPos.getX(column);
            final int z = BlockPos.getZ(column);
            final int bottom = ground.get(column);
            final BlockState base = level.getBlockState(pos.set(x, bottom, z));
            if (bottom < spot.getY() - depth - 1 || !base.getFluidState().isEmpty() || !base.isSolidRender(level, pos))
            {
                return null;
            }
            for (int y = bottom + 1; y <= top; y++)
            {
                final BlockState cell = level.getBlockState(pos.set(x, y, z));
                if (!cell.isAir() && !(cell.canBeReplaced() && cell.getFluidState().isEmpty()))
                {
                    return null;
                }
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    if (!under.contains(BlockPos.asLong(x + side.getStepX(), 0, z + side.getStepZ())))
                    {
                        final BlockState wall = level.getBlockState(pos.set(x + side.getStepX(), y, z + side.getStepZ()));
                        if (wall.isAir() || wall.canBeReplaced() || !wall.getFluidState().isEmpty() || wall.getCollisionShape(level, pos).isEmpty()
                              || wall.is(net.minecraft.tags.BlockTags.DOORS) || wall.is(net.minecraft.tags.BlockTags.TRAPDOORS)
                              || wall.is(net.minecraft.tags.BlockTags.FENCE_GATES) || wall.is(net.minecraft.tags.BlockTags.FENCES)
                              || wall.is(net.minecraft.tags.BlockTags.WALLS))
                        {
                            return null;
                        }
                    }
                }
                cells.add(BlockPos.asLong(x, y, z));
            }
        }
        return cells;
    }

    private int emptyHole(final ServerLevel level, final IColony colony, final BlockPos center, final Set<Long> hole, final int top)
    {
        final List<net.minecraft.world.entity.LivingEntity> inside = new ArrayList<>();
        for (final net.minecraft.world.entity.Entity body : level.getEntities((net.minecraft.world.entity.Entity) null,
          new net.minecraft.world.phys.AABB(center).inflate(4, 9, 4)))
        {
            final net.minecraft.world.phys.AABB box = body.getBoundingBox().deflate(1.0E-7);
            boolean touches = false;
            for (final BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            {
                touches |= hole.contains(cell.asLong());
            }
            if (!touches)
            {
                continue;
            }
            if (body instanceof net.minecraft.world.entity.player.Player || body.countPlayerPassengers() > 0)
            {
                if (holesAwaiting.add(center))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the hole at {} where citizens were rescued twice waits to be filled — {} stands in it",
                      colony.getName(), center.toShortString(), body.getName().getString());
                }
                return -1;
            }

            if (!(body instanceof net.minecraft.world.entity.LivingEntity living) || body.getVehicle() instanceof net.minecraft.world.entity.LivingEntity)
            {
                continue;
            }
            inside.add(living);
        }

        final List<net.minecraft.world.entity.LivingEntity> unmoved = new ArrayList<>();
        for (final net.minecraft.world.entity.LivingEntity body : inside)
        {
            final String moved = body instanceof com.minecolonies.api.entity.citizen.AbstractEntityCitizen citizen && citizen.getCitizenData() != null
                                   ? (WaterRescue.recall(level, citizen, citizen.getCitizenData().getHomePosition()) ? "sent home" : null)
                                   : (body instanceof net.minecraft.world.entity.Mob mob && WaterRescue.recallToStall(level, mob) != null ? "sent to a stall of its Stable" : null);
            if (moved == null)
            {
                unmoved.add(body);
                continue;
            }
            ColonyAutopilot.LOGGER.info("[{}] {} stood in the hole at {} — {} before it is filled",
              colony.getName(), body.getName().getString(), center.toShortString(), moved);
        }
        for (final net.minecraft.world.entity.LivingEntity body : unmoved)
        {

            net.minecraft.world.phys.AABB landing = body.getBoundingBox().move(0, top + 1 - body.getY(), 0);
            for (final net.minecraft.world.entity.Entity rider : body.getPassengers())
            {
                landing = landing.minmax(rider.getBoundingBox().move(0, top + 1 - body.getY(), 0));
            }
            if (!level.noCollision(body, landing))
            {
                ColonyAutopilot.LOGGER.info("[{}] the hole at {} where citizens were rescued twice is left as it is — {} stands in it, with no room on its top to set it",
                  colony.getName(), center.toShortString(), body.getName().getString());
                return 0;
            }
        }
        for (final net.minecraft.world.entity.LivingEntity body : unmoved)
        {
            body.stopRiding();
            body.teleportTo(body.getX(), top + 1, body.getZ());
            ColonyAutopilot.LOGGER.info("[{}] {} stood in the hole at {} — set on its top before it is filled",
              colony.getName(), body.getName().getString(), center.toShortString());
        }
        return 1;
    }

    static boolean holeRescued(final IColony colony, final BlockPos where)
    {
        if (instance == null)
        {
            return false;
        }
        instance.rescuedHoles.put(where.immutable(), colony);
        return true;
    }

    private void fillRescuedHoles()
    {
        final Iterator<Map.Entry<BlockPos, IColony>> due = rescuedHoles.entrySet().iterator();
        while (due.hasNext())
        {
            final Map.Entry<BlockPos, IColony> rescue = due.next();
            final BlockPos where = rescue.getKey();
            final IColony colony = rescue.getValue();
            if (!(colony.getWorld() instanceof ServerLevel level))
            {
                due.remove();
                holesAwaiting.remove(where);
                continue;
            }
            if (holesAwaiting.contains(where) && level.getGameTime() % 20 != 0)
            {
                continue;
            }

            if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
            {
                due.remove();
                holesAwaiting.remove(where);
                ProblemLedger.releaseHeld(level, where, false);
                continue;
            }
            try
            {

                final Set<Long> hole = where.getY() > villageTop(colony) + 24 ? null : holeAt(level, where);
                if (hole == null)
                {
                    ColonyAutopilot.LOGGER.debug("[{}] two rescues near {} — no dry hole walled in round that spot to fill (open ground or water runs on, "
                                                   + "it passes the bound, or it lies high over the village); left to the warning", colony.getName(), where.toShortString());
                    ProblemLedger.holeDone(level, where, Set.of(where.asLong()), false);
                    ProblemLedger.releaseHeld(level, where, false);
                    due.remove();
                    holesAwaiting.remove(where);
                    continue;
                }
                if (ProblemLedger.rescuesIn(level, where, hole) < 2)
                {
                    due.remove();
                    holesAwaiting.remove(where);
                    ProblemLedger.releaseHeld(level, where, false);
                    continue;
                }
                final int filled = fillDeadlyDrop(level, colony, where, hole);
                if (filled < 0)
                {
                    continue;
                }
                due.remove();
                holesAwaiting.remove(where);
                ProblemLedger.holeDone(level, where, hole, filled > 0);
                ProblemLedger.releaseHeld(level, where, filled > 0);
                final ColonyId key = ColonyAutopilot.colonyKey(colony);
                if (filled > 0 && AutopilotConfig.live(colony, AutopilotConfig.BUILD_PATHS) && !liftedRoads.contains(key) && bootSwept.contains(key))
                {
                    groundReshaped(colony, new SiteSelector.Footprint(where.getX() - 4, where.getZ() - 4, where.getX() + 4, where.getZ() + 4), List.of());
                }
            }
            catch (final RuntimeException e)
            {
                due.remove();
                holesAwaiting.remove(where);
                ProblemLedger.releaseHeld(level, where, false);
                ColonyAutopilot.LOGGER.warn("[{}] the hole rule at {} failed", colony.getName(), where.toShortString(), e);
            }
        }
    }

    private static void drainFluidBody(final ServerLevel level, final IColony colony, final BlockPos center, final boolean lava)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {
            return;
        }
        final var fluidTag = lava ? FluidTags.LAVA : FluidTags.WATER;

        final int cap = lava ? 4096 : 600;
        final java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<>();
        final Set<BlockPos> body = new HashSet<>();

        boolean unseen = false;

        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = -3; x <= 3; x++)
        {
            for (int y = -3; y <= 3; y++)
            {
                for (int z = -3; z <= 3; z++)
                {
                    probe.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    if (WorldUtil.isChunkLoaded(level, probe.getX() >> 4, probe.getZ() >> 4)
                          && level.getBlockState(probe).getFluidState().is(fluidTag))
                    {
                        final BlockPos seed = probe.immutable();
                        if (body.add(seed))
                        {
                            frontier.add(seed);
                        }
                    }
                }
            }
        }
        if (body.isEmpty())
        {
            if (lava)
            {

                sealLavaAround(level, colony, center);
            }
            return;
        }

        boolean bounded = true;
        while (!frontier.isEmpty())
        {
            if (body.size() > cap)
            {
                bounded = false;
                break;
            }
            final BlockPos pos = frontier.poll();
            for (final Direction direction : Direction.values())
            {
                final BlockPos next = pos.relative(direction);
                if (body.contains(next))
                {
                    continue;
                }
                if (!WorldUtil.isChunkLoaded(level, next.getX() >> 4, next.getZ() >> 4))
                {
                    unseen = true;
                }
                else if (level.getBlockState(next).getFluidState().is(fluidTag))
                {
                    body.add(next);
                    frontier.add(next);
                }
            }
        }
        bounded &= !unseen;
        if (!bounded && !lava)
        {
            ColonyAutopilot.LOGGER.info("[{}] the water at {} is part of a body too large to drain, or one that runs on into unloaded ground — leaving it (partial drains just flow back)",
              colony.getName(), center.toShortString());
            return;
        }

        int minX = center.getX();
        int minZ = center.getZ();
        int maxX = center.getX();
        int maxZ = center.getZ();
        for (final BlockPos pos : body)
        {
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        final List<SiteSelector.Footprint> shields = untouchableGround(level, colony, minX, minZ, maxX, maxZ);
        for (final BlockPos pos : body)
        {
            if (SiteSelector.insideAny(shields, pos.getX(), pos.getZ()))
            {
                ColonyAutopilot.LOGGER.info("[{}] the {} at {} reaches into a building's grounds, a protected zone or another colony's land — left alone",
                  colony.getName(), lava ? "lava" : "water", center.toShortString());
                return;
            }
        }
        int cleared = 0;
        for (final BlockPos pos : body)
        {
            final BlockState state = level.getBlockState(pos);
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                  && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
            {
                WorldUtil.setBlockState(level, pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
            }
            else
            {
                WorldUtil.setBlockState(level, pos, bounded ? Blocks.AIR.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
            }
            cleared++;
        }
        ColonyAutopilot.LOGGER.info("[{}] a second death in the same {} — {} {} blocks at {}{}",
          colony.getName(), lava ? "lava pool" : "water", bounded ? "cleared away" : "turned to stone", cleared, center.toShortString(),
          bounded ? "" : " (body too large, or not all of it loaded, to remove safely — stone cannot creep back)");
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        plan = GrowthPlanLoader.loadOrCreateDefault();
        gravesPlan = plan.isEmpty() ? plan : GrowthPlanLoader.withGraveyard(plan);
        final Map<Block, Integer> ranks = new HashMap<>();
        for (int i = 0; i < plan.size(); i++)
        {
            ranks.putIfAbsent(plan.get(i).hut(), i);
        }

        for (int i = 0; i < gravesPlan.size(); i++)
        {
            if (gravesPlan.get(i).hut() == ModBlocks.blockHutGraveyard)
            {
                ranks.putIfAbsent(ModBlocks.blockHutGraveyard, i);
                break;
            }
        }
        planRankByHut = ranks;
        openingBuilderStep = null;
        for (final ResolvedStep step : plan)
        {
            if (step.hut() == ModBlocks.blockHutBuilder && step.targetLevel() == 0)
            {
                openingBuilderStep = step;
                break;
            }
        }
        BuilderCap.setOpeningBuilderHuts(openingBuilderStep == null ? 1 : openingBuilderStep.targetCount());
        pendingRoadReconnects.clear();
        apronOwed.clear();

        lastPlacement.clear();
        pendingSince.clear();
        reservedPlots.clear();
        blockedUntil.clear();
        lastGateLogged.clear();
        housingHoldLogged.clear();
        unavailableHuts.clear();
        unavailablePack.clear();
        stableInPack.clear();
        stableProbedAt.clear();
        plotBlockedUntil.clear();
        plotFailStrikes.clear();
        firstStrikeRun.clear();
        plotShortage.clear();
        fullParks.clear();
        structuresYielded.clear();
        builderRescue.clear();
        rescueFrontier.clear();
        coldPadTries.clear();
        designPaths.clear();
        universityWidened.clear();
        lastTerraform.clear();
        terraformAnchors.clear();
        stairRefused.clear();
        stairLonger.clear();
        walkRefused.clear();
        rescueRefused.clear();
        importantCallUnanswered.clear();
        walkPlacedLast.clear();
        reconnectLater.clear();
        requeuedAt.clear();
        deadRoads.clear();
        orphanRoads.clear();
        liftedRoads.clear();
        relaidAt.clear();
        lastBreak.clear();
        researchLockLogged.clear();
        roadLedger.clear();
        roadsLoaded.clear();
        bootSwept.clear();
        bootDelay.clear();
        bootPass.clear();
        bootChanged.clear();
        pondTended.clear();
        FishingPond.clearThawScans();
        PlantationFields.clearDesigns();
        ForesterGrove.clearGroundWarnings();
        Paddocks.mostStrays.clear();
        Paddocks.bardingWaits.clear();
        ravineChecked.clear();
        roadsAwaitingGrade.clear();
        groundsSweeps.clear();
        sweepOwed.clear();
        ravineSurveys.clear();
        jobWatch.clear();
        lavaDeathSpots.clear();
        drownDeathSpots.clear();
        fallDeathSpots.clear();
        lastGuardBalance.clear();
        guardTowersSeen.clear();
        graveyardShellWarned.clear();
        barracksForDruidsAt.clear();
        anticipationConversion.clear();
        BuilderCap.clear();
        townHallAproned.clear();
        apronRetries.clear();
        offsetLearning.clear();
        offsetMisses.clear();
        ColonySettings.invalidateAll();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {

        try
        {
            if (AutopilotConfig.SPEC.isLoaded())
            {
                for (final IColony colony : IColonyManager.getInstance().getAllColonies())
                {
                    if (!guardStaffingLive(colony))
                    {
                        releaseHeldGuardPosts(colony);
                    }
                }
            }
        }
        catch (final RuntimeException e)
        {
            ColonyAutopilot.LOGGER.warn("Handing the guard posts back at shutdown failed", e);
        }

        pendingRoadReconnects.clear();
        reconnectLater.clear();
        apronOwed.clear();
        rescuedHoles.clear();
        holesAwaiting.clear();
        groundsSweeps.values().forEach(VillageGrounds::reportNotches);
        groundsSweeps.clear();
        sweepOwed.clear();
        ravineSurveys.clear();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {

            if (++tickCounter >= EVALUATION_PERIOD_TICKS)
            {
                tickCounter = 0;
                releaseHeldGuardPosts();
            }
            return;
        }
        if (!pendingRoadReconnects.isEmpty())
        {
            drainRoadReconnects();
        }
        if (!rescuedHoles.isEmpty())
        {
            fillRescuedHoles();
        }
        if (!groundsSweeps.isEmpty())
        {

            groundsSweeps.entrySet().removeIf(entry -> {
                final boolean off = !AutopilotConfig.get(entry.getValue().colony, AutopilotConfig.TERRAFORM_ENABLED);
                if (off)
                {
                    VillageGrounds.reportNotches(entry.getValue());
                }
                return off;
            });
        }
        if (!groundsSweeps.isEmpty())
        {
            final Iterator<Map.Entry<ColonyId, VillageGrounds.Sweep>> sweeps = groundsSweeps.entrySet().iterator();
            while (sweeps.hasNext())
            {
                final VillageGrounds.Sweep sweep = sweeps.next().getValue();

                if (AutopilotConfig.get(sweep.colony, AutopilotConfig.KEEP_VILLAGE_LOADED) && !ChunkKeeper.holds(sweep.colony))
                {
                    continue;
                }
                try
                {
                    if (VillageGrounds.drain(sweep))
                    {
                        sweeps.remove();
                    }
                }
                catch (final Exception e)
                {
                    sweeps.remove();
                    ColonyAutopilot.LOGGER.warn("Village grounds sweep failed", e);
                    VillageGrounds.reportNotches(sweep);
                }
            }
        }
        if (!ravineSurveys.isEmpty())
        {
            ravineSurveys.entrySet().removeIf(entry -> !AutopilotConfig.get(entry.getValue().colony, AutopilotConfig.TERRAFORM_ENABLED));
        }
        if (!ravineSurveys.isEmpty())
        {
            final Iterator<Map.Entry<ColonyId, RavineSurvey>> surveys = ravineSurveys.entrySet().iterator();
            while (surveys.hasNext())
            {
                try
                {
                    if (drainRavineSurvey(surveys.next().getValue()))
                    {
                        surveys.remove();
                    }
                }
                catch (final Exception e)
                {
                    surveys.remove();
                    ColonyAutopilot.LOGGER.warn("The ravine survey failed", e);
                }
            }
        }

        for (final IColony colony : rota.take(event.getServer()))
        {
            if (!plan.isEmpty() && AutopilotConfig.get(colony, AutopilotConfig.GROWTH_ENABLED))
            {
                try
                {
                    evaluate(colony);
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("Growth evaluation failed for colony {}", colony.getName(), e);
                }
            }
            else if (bootPass.containsKey(ColonyAutopilot.colonyKey(colony)))
            {

                try
                {
                    continueRoadPasses(colony, ColonyAutopilot.colonyKey(colony));
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("The road passes failed for colony {}", colony.getName(), e);
                }
            }
        }
        if (++tickCounter < EVALUATION_PERIOD_TICKS)
        {
            return;
        }
        tickCounter = 0;
        rota.fill(event.getServer());

        if (pendingRoadReconnects.isEmpty() && !reconnectLater.isEmpty())
        {
            pendingRoadReconnects.addAll(reconnectLater);
            reconnectLater.clear();
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            try
            {
                if (!guardStaffingLive(colony))
                {

                    releaseHeldGuardPosts(colony);
                }
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Releasing held guard posts failed for colony {}", colony.getName(), e);
            }
        }

        final Set<ColonyId> live = new HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            live.add(ColonyAutopilot.colonyKey(colony));

            if (colony.getWorld() instanceof ServerLevel level)
            {
                try
                {
                    Decorator.recordPlacedDecorations(level, colony);
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("Reading the placed decorations of colony {} failed", colony.getName(), e);
                }
            }
        }
        pruneColonyState(live, event.getServer());
    }

    private void pruneColonyState(final Set<ColonyId> live, final MinecraftServer server)
    {
        ColonyAutopilot.retainColonies(live, lastPlacement, pendingSince, reservedPlots, blockedUntil, lastGateLogged, housingHoldLogged, unavailableHuts, unavailablePack, plotBlockedUntil, plotFailStrikes, firstStrikeRun, plotShortage, fullParks, structuresYielded, builderRescue, rescueFrontier, coldPadTries, universityWidened, lastTerraform, terraformAnchors, stairRefused, stairLonger, walkRefused, rescueRefused, importantCallUnanswered, walkPlacedLast, requeuedAt, deadRoads, orphanRoads, lastBreak, researchLockLogged, roadLedger, pondTended, ravineChecked, roadsAwaitingGrade, groundsSweeps, ravineSurveys, jobWatch.state(), lavaDeathSpots, drownDeathSpots, fallDeathSpots, lastGuardBalance, guardTowersSeen, graveyardShellWarned, barracksForDruidsAt, anticipationConversion, BuilderCap.placementWantedAt, BuilderCap.placementAdmittedAt, BuilderCap.placementHeld, BuilderCap.upgradeHeld, BuilderCap.quotaWaivedLogged, Paddocks.mostStrays, Paddocks.bardingWaits);
        bootSwept.retainAll(live);
        sweepOwed.retainAll(live);
        bootDelay.keySet().retainAll(live);
        bootPass.keySet().retainAll(live);
        bootChanged.retainAll(live);
        liftedRoads.retainAll(live);
        relaidAt.keySet().retainAll(live);
        roadsLoaded.retainAll(live);
        townHallAproned.keySet().retainAll(live);
        apronRetries.keySet().retainAll(live);

        for (final ServerLevel level : server.getAllLevels())
        {
            final Set<Integer> ids = new HashSet<>();
            final ColonyGrounds grounds = ColonyGrounds.get(level);
            final Set<Long> standing = new HashSet<>();
            for (final IColony colony : IColonyManager.getInstance().getColonies(level))
            {
                if (!live.contains(ColonyAutopilot.colonyKey(colony)))
                {
                    continue;
                }
                ids.add(colony.getID());
                final Set<Long> anchors = new HashSet<>();
                for (final BlockPos anchor : colony.getServerBuildingManager().getBuildings().keySet())
                {
                    anchors.add(anchor.asLong());
                }
                grounds.retainAnchors(colony.getID(), anchors);
                standing.addAll(anchors);
                final Set<Long> scarecrows = new HashSet<>();
                for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext ->
                  ext.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())))
                {
                    scarecrows.add(extension.getPosition().asLong());
                }
                grounds.retainFields(colony.getID(), scarecrows);
            }

            offsetLearning.keySet().removeIf(pos -> pos.dimension().equals(level.dimension()) && !standing.contains(pos.pos().asLong()));
            offsetMisses.keySet().removeIf(pos -> pos.dimension().equals(level.dimension()) && !standing.contains(pos.pos().asLong()));
            grounds.retain(ids);
            ProtectedZones.get(level).retain(ids);
        }
    }

    private void onColonyDeleted(final ColonyDeletedModEvent event)
    {
        final IColony colony = event.getColony();
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        ColonyGrounds.get(level).forget(colony.getID());
        ProtectedZones.get(level).forget(colony.getID());
        WorldStructures.forget(colony);
        ColonySettings.invalidate(colony);
        Treasury.forget(colony);
        Milestones.forget(colony);

        pendingRoadReconnects.removeIf(building -> building.getColony() == colony);

        apronOwed.removeIf(building -> building.getColony() == colony);
        final Set<ColonyId> live = new HashSet<>();
        for (final IColony other : IColonyManager.getInstance().getAllColonies())
        {
            if (other != colony)
            {
                live.add(ColonyAutopilot.colonyKey(other));
            }
        }
        pruneColonyState(live, level.getServer());
    }

    private void evaluate(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }

        staffGuardPosts(colony);

        if (!level.isLoaded(colony.getCenter()))
        {
            return;
        }
        final long now = level.getGameTime();
        final ColonyId key = ColonyAutopilot.colonyKey(colony);

        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        if (AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED) && hall != null
              && !hall.getPosition().equals(townHallAproned.get(key)))
        {
            final ColonyGrounds grounds = ColonyGrounds.get(level);
            if (grounds.isApronDone(colony.getID(), hall.getPosition()))
            {
                townHallAproned.put(key, hall.getPosition());
            }
            else if (hall.getBuildingLevel() <= 0)
            {

            }
            else if (!grounds.hasAnchorOffset(colony.getID(), hall.getPosition()))
            {
                learnAnchorOffset(colony, level, hall);
            }
            else
            {

                if (townHallAproned.put(key, hall.getPosition()) != null)
                {
                    apronRetries.remove(key);
                }
                final List<SiteSelector.Footprint> others = new ArrayList<>(sacredGround(colony));
                for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
                {
                    if (other != hall)
                    {
                        others.add(SiteSelector.boxOf(other));
                    }
                }
                final int ground = hall.getPosition().getY() - grounds.anchorOffset(colony.getID(), hall.getPosition(), 1);
                final VillageGrounds.SkirtResult skirt = VillageGrounds.smoothSkirt(level, SiteSelector.boxOf(hall), ground,
                  skirtKeepOut(level, colony, SiteSelector.boxOf(hall), others), roadBand(colony, level));
                final boolean converged = skirt.complete() && skirt.graded() == 0;
                if (converged)
                {
                    grounds.markApronDone(colony.getID(), hall.getPosition());
                }
                if (skirt.total() > 0)
                {

                    final String line = "[{}] smoothed {} blocks around the town hall's plot ({} graded, {} re-grassed) — the founder's own hut gets its apron too{}";
                    final String end = converged ? "; at grade now, that was the last pass" : "";
                    if (apronRetries.getOrDefault(key, 0) > 0)
                    {
                        ColonyAutopilot.LOGGER.debug(line, colony.getName(), skirt.total(), skirt.graded(), skirt.recapped(), end);
                    }
                    else
                    {
                        ColonyAutopilot.LOGGER.info(line, colony.getName(), skirt.total(), skirt.graded(), skirt.recapped(), end);
                    }
                }
            }
        }

        final Map<BlockPos, List<BlockPos>> ledger = roadsOf(colony, level);
        if (!ledger.isEmpty())
        {
            final Map<BlockPos, List<BlockPos>> orphaned = new HashMap<>();
            final Iterator<Map.Entry<BlockPos, List<BlockPos>>> doors = ledger.entrySet().iterator();
            while (doors.hasNext())
            {
                final Map.Entry<BlockPos, List<BlockPos>> entry = doors.next();
                if (colony.getServerBuildingManager().getBuilding(entry.getKey()) == null)
                {
                    orphaned.put(entry.getKey(), entry.getValue());
                    doors.remove();
                }
            }
            if (!orphaned.isEmpty())
            {
                final Set<Long> liveBlocks = new HashSet<>();
                for (final List<BlockPos> road : ledger.values())
                {
                    for (final BlockPos block : road)
                    {
                        liveBlocks.add(block.asLong());
                    }
                }

                boolean lifted = false;
                for (final Map.Entry<BlockPos, List<BlockPos>> gone : orphaned.entrySet())
                {

                    final List<BlockPos> left = VillagePaths.erase(level, gone.getValue(), liveBlocks, Set.of(), ColonyGrounds.get(level).roadWater(colony.getID()));
                    lifted |= left.size() != gone.getValue().size();
                    ColonyGrounds.get(level).setRoadLine(colony.getID(), gone.getKey(), null);

                    final Set<BlockPos> stillStanding = new HashSet<>(left);
                    final List<BlockPos> erased = new ArrayList<>();
                    for (final BlockPos block : gone.getValue())
                    {
                        if (!liveBlocks.contains(block.asLong()) && !stillStanding.contains(block))
                        {
                            erased.add(block);
                        }
                    }
                    requeueDependents(colony, ledger, gone.getKey(), erased);
                    if (left.isEmpty())
                    {
                        ColonyAutopilot.LOGGER.info("[{}] the building at {} is gone — its road was lifted neatly",
                          colony.getName(), gone.getKey().toShortString());
                    }
                    else
                    {
                        ledger.put(gone.getKey(), left);
                    }
                }
                if (lifted)
                {
                    bootChanged.add(key);
                    saveRoads(colony, level);
                }
            }
        }

        balanceGuardComposition(colony, level);

        final Long persistedRound = ColonyGrounds.get(level).lastDailyRound(colony.getID());
        if (persistedRound != null)
        {
            ravineChecked.putIfAbsent(key, persistedRound);
            pondTended.putIfAbsent(key, persistedRound);
        }
        final Long ravined = ravineChecked.get(key);
        if (ravined == null || now - ravined >= 24000L)
        {
            ravineChecked.put(key, now);
            try
            {
                surveyRavines(colony, level);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the ravine survey failed", colony.getName(), e);
            }
        }

        final Long tended = pondTended.get(key);
        if (tended == null || now - tended >= 24000L)
        {
            pondTended.put(key, now);
            ColonyGrounds.get(level).setLastDailyRound(colony.getID(), now);

            long arrows = 0;
            long visitors = 0;
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                final Block hut = building.getBuildingType().getBuildingBlock();
                if (hut != ModBlocks.blockHutArchery && hut != ModBlocks.blockHutTavern)
                {
                    continue;
                }
                final BuildingStatisticsModule stats = building.getModule(BuildingStatisticsModule.class);
                if (stats == null)
                {
                    continue;
                }
                if (hut == ModBlocks.blockHutArchery)
                {
                    arrows += stats.getBuildingStatisticsManager().getStatTotal(StatisticsConstants.ARROWS_FIRED);
                }
                else
                {
                    visitors += stats.getBuildingStatisticsManager().getStatTotal(StatisticsConstants.NEW_VISITORS);
                }
            }

            ColonyAutopilot.LOGGER.info("[{}] day report: {} of {} citizens, {} job slots, {} open work orders, {} arrows fired in training, {} visitors greeted, {} items paid from the treasury",
              colony.getName(), colony.getCitizenManager().getCurrentCitizenCount(),
              beds(colony), totalJobSlots(colony), colony.getWorkManager().getWorkOrders().size(), arrows, visitors,
              Treasury.takeSpentToday(colony));

            if (AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS) && !liftedRoads.contains(key))
            {
                requeueOrphanRoads(colony, level);
                requeueDeadRoads(colony, level);
            }
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {

                if (building.getBuildingLevel() == 0 && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGraveyard
                      && AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES)
                      && graveyardShellWarned.computeIfAbsent(key, k -> new HashSet<>()).add(building.getPosition()))
                {
                    ColonyAutopilot.LOGGER.warn("[{}] a never-to-be-built graveyard shell from an older plan reserves its plot at {} — remove the hut block by hand to reclaim the ground (needs eyes)",
                      colony.getName(), building.getPosition().toShortString());
                }
                if (building.getBuildingLevel() <= 0

                      || !WorldUtil.isChunkLoaded(level, building.getPosition().getX() >> 4, building.getPosition().getZ() >> 4))
                {
                    continue;
                }
                try
                {

                    if (AutopilotConfig.get(colony, AutopilotConfig.STARTER_ANIMALS) && Paddocks.keepsAnimals(building))
                    {
                        Paddocks.ensure(level, colony, building);
                    }

                    if (building instanceof BuildingFarmer || building instanceof BuildingPlantation)
                    {
                        outfitWorkplace(building);
                    }

                    else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGraveyard
                               && building.getModule(WorkerBuildingModule.class) instanceof WorkerBuildingModule crew
                               && crew.getHiringMode() == (AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES) ? HiringMode.DEFAULT : HiringMode.LOCKED))
                    {
                        outfitWorkplace(building);
                    }

                    if (!ColonyGrounds.get(level).hasAnchorOffset(colony.getID(), building.getPosition()))
                    {
                        learnAnchorOffset(colony, level, building);
                    }

                    if (AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS))
                    {
                        final Set<Long> ownRoad = new HashSet<>();
                        for (final BlockPos block : roadsOf(colony, level).getOrDefault(building.getPosition(), List.of()))
                        {
                            ownRoad.add(block.asLong());
                        }
                        clearDoorsteps(level, building, ownRoad);
                    }
                    if (AutopilotConfig.get(colony, AutopilotConfig.GROWTH_KEEP_SETTINGS))
                    {
                        SettingsKeeper.keep(building);
                    }
                    if (AutopilotConfig.get(colony, AutopilotConfig.JOB_WATCH))
                    {
                        jobWatch.tend(colony, building);
                    }

                    if (AutopilotConfig.get(colony, AutopilotConfig.ENCHANTER_STATIONS)
                          && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutEnchanter)
                    {
                        tendEnchanterStations(colony, building);
                    }
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("[{}] daily tending failed at the {}", colony.getName(),
                      SiteSelector.plainName(building), e);
                }
            }

            try
            {
                promoteTrainee(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the trainee promotion round failed", colony.getName(), e);
            }

            if (hall != null && hall.getPosition().equals(townHallAproned.get(key))
                  && ColonyGrounds.get(level).hasAnchorOffset(colony.getID(), hall.getPosition())
                  && !ColonyGrounds.get(level).isApronDone(colony.getID(), hall.getPosition())
                  && apronRetries.merge(key, 1, Integer::sum) <= APRON_RETRIES)
            {
                townHallAproned.remove(key);
            }

            if (AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED) && !groundsSweeps.containsKey(key))
            {
                sweepOwed.add(key);
            }
        }

        if (!bootSwept.contains(key))
        {

            if (bootDelay.merge(key, 1, Integer::sum) >= BOOT_SWEEP_AFTER_EVALUATIONS)
            {
                bootSwept.add(key);
                bootDelay.remove(key);
                liftedRoads.remove(key);
                roadsOf(colony, level);
                queueRoads(colony);
                bootPass.put(key, 1);
            }
        }
        else
        {
            continueRoadPasses(colony, key);
        }

        if (sweepOwed.contains(key)
              && (ChunkKeeper.holds(colony) || !AutopilotConfig.get(colony, AutopilotConfig.KEEP_VILLAGE_LOADED))
              && bootSwept.contains(key) && !bootPass.containsKey(key)
              && pendingRoadReconnects.stream().noneMatch(building -> building.getColony() == colony)
              && reconnectLater.stream().noneMatch(building -> building.getColony() == colony))
        {
            sweepOwed.remove(key);

            if (AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED))
            {
                final VillageGrounds.Sweep sweep = VillageGrounds.plan(colony, sacredGround(colony), roadBand(colony, level));
                if (sweep != null)
                {
                    groundsSweeps.put(key, sweep);
                }
            }
        }

        final Long pending = pendingSince.get(key);
        if (pending != null && now - pending < 2400)
        {
            return;
        }
        pendingSince.remove(key);

        if (!colony.getStructurePack().equals(unavailablePack.put(key, colony.getStructurePack())))
        {
            unavailableHuts.remove(key);
        }

        final Long blocked = blockedUntil.get(key);
        if (blocked != null && now < blocked)
        {
            return;
        }

        final Long last = lastPlacement.get(key);
        if (last != null && now - last < AutopilotConfig.get(colony, AutopilotConfig.PLACEMENT_COOLDOWN_MINUTES) * 1200L)
        {
            return;
        }

        final Collection<IBuilding> buildings = colony.getServerBuildingManager().getBuildings().values();
        final int citizens = colony.getCitizenManager().getCurrentCitizenCount();

        final Map<Block, Integer> countByHut = new HashMap<>();
        final Map<Block, Integer> maxLevelByHut = new HashMap<>();
        int builtCount = 0;
        int lowestHomeLevel = Integer.MAX_VALUE;
        int homeBlueprintMax = 0;
        int homesShallow = 0;
        for (final IBuilding building : buildings)
        {
            final Block block = building.getBuildingType().getBuildingBlock();
            countByHut.merge(block, 1, Integer::sum);
            maxLevelByHut.merge(block, building.getBuildingLevel(), Integer::max);
            if (block == ModBlocks.blockHutHome)
            {
                lowestHomeLevel = Math.min(lowestHomeLevel, building.getBuildingLevel());
                homeBlueprintMax = Math.max(homeBlueprintMax, building.getMaxBuildingLevel());
                if (building.getBuildingLevel() <= 1)
                {
                    homesShallow++;
                }
            }
            if (building.getBuildingLevel() > 0)
            {
                builtCount++;
            }
        }
        final int townHallLevel = maxLevelByHut.getOrDefault(ModBlocks.blockHutTownHall, 0);
        final var census = colony.getCitizenManager();

        final boolean bedCapped = census.getMaxCitizens() > 0 && citizens >= census.getMaxCitizens()
              && census.getMaxCitizens() < census.maxCitizensFromResearch();

        final Set<Block> unavailable = unavailableHuts.getOrDefault(key, Set.of());
        final Map<Block, Long> plotBlocked = plotBlockedUntil.getOrDefault(key, Map.of());

        final List<ResolvedStep> walk = planFor(colony);

        final boolean openingReachable = openingBuilderStep != null
              && walkReaches(colony, walk, openingBuilderStep, unavailable, plotBlocked, countByHut, maxLevelByHut, citizens, now, false);
        final Long openingRetry = plotBlocked.get(ModBlocks.blockHutBuilder);
        final boolean openingBuildersDue = openingReachable && AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED)
              && countByHut.getOrDefault(ModBlocks.blockHutBuilder, 0) < openingBuilderStep.targetCount()
              && countByHut.getOrDefault(ModBlocks.blockHutTownHall, 0) > 0 && citizens >= openingBuilderStep.minCitizens()
              && !unavailable.contains(ModBlocks.blockHutBuilder) && (openingRetry == null || now >= openingRetry)
              && (citizens < census.getMaxCitizens() || census.getJoblessCitizen() != null);

        if (!AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED))
        {
            return;
        }

        final Rescue stranded = builderRescue.get(key);
        boolean builderPending = false;
        for (final IBuilding building : buildings)
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBuilder && building.getBuildingLevel() == 0)
            {
                builderPending = true;
                break;
            }
        }
        final int builderHuts = countByHut.getOrDefault(ModBlocks.blockHutBuilder, 0);
        final Long builderRetry = plotBlocked.get(ModBlocks.blockHutBuilder);
        final boolean builderPlaceable = !builderPending && !unavailable.contains(ModBlocks.blockHutBuilder)
              && (builderRetry == null || now >= builderRetry);

        IBuilding covering = null;
        if (stranded != null)
        {
            for (final IBuilding building : buildings)
            {
                if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBuilder
                      && building.getPosition().distSqr(stranded.frontier()) <= SiteSelector.BUILDER_RANGE_SQ
                      && (covering == null || !building.getAllAssignedCitizen().isEmpty()))
                {
                    covering = building;
                    if (!building.getAllAssignedCitizen().isEmpty())
                    {
                        break;
                    }
                }
            }
        }

        final Long strandedRetry = stranded == null ? null : plotBlocked.get(stranded.hut());
        final boolean strandedParked = strandedRetry != null && now < strandedRetry;
        if (covering != null && importantHut(stranded.hut()) && covering.getAllAssignedCitizen().isEmpty() && covering.getBuildingLevel() == 0 && strandedParked
              && (citizens < census.getMaxCitizens() || census.getJoblessCitizen() != null))
        {

        }
        else if (covering != null && importantHut(stranded.hut()) && covering.getAllAssignedCitizen().isEmpty())
        {

            builderRescue.remove(key);
            rescueRefused.putIfAbsent(key, new HashSet<>());
            rescueRefused.get(key).add(stranded.hut());
            plotFreed(key, stranded.hut());
            ColonyAutopilot.LOGGER.info("[{}] the {}'s land lies by the builder's hut at {}, which has no crew — the {} does not wait for one and {}",
              colony.getName(), stranded.stepName(), covering.getPosition().toShortString(), stranded.stepName(), afterRefusedRescue(colony));
        }
        else if (covering != null && importantHut(stranded.hut()))
        {

            if (strandedParked)
            {
                plotFreed(key, stranded.hut());
                ColonyAutopilot.LOGGER.info("[{}] the {} is within reach of the builder's hut at {}, which has its crew — it is searched again at the next turn",
                  colony.getName(), stranded.stepName(), covering.getPosition().toShortString());
            }
        }
        else if (covering != null)
        {
            builderRescue.remove(key);
            ColonyAutopilot.LOGGER.info(covering.getAllAssignedCitizen().isEmpty()
                ? "[{}] the {} waits on the builder's hut at {}, which covers its land but has no crew — it needs a builder, not another hut"
                : "[{}] the {} is within reach of the builder's hut at {}, which has its crew again — no other hut needed",
              colony.getName(), stranded.stepName(), covering.getPosition().toShortString());
        }
        else if (stranded != null && !builderPending && builderHuts >= MAX_RESCUE_BUILDER_HUTS)
        {
            builderRescue.remove(key);

            rescueRefused.computeIfAbsent(key, k -> new HashSet<>()).add(stranded.hut());
            if (importantHut(stranded.hut()))
            {
                plotFreed(key, stranded.hut());
            }
            ColonyAutopilot.LOGGER.warn("[{}] the frontier outgrew every builder's reach but the village already keeps {} builders' huts — no more are raised, and the {} {}",
              colony.getName(), builderHuts, stranded.stepName(), afterRefusedRescue(colony));
        }
        else if (stranded != null && builderPlaceable && citizens >= census.getMaxCitizens() && census.getJoblessCitizen() == null)
        {

            builderRescue.remove(key);
            rescueRefused.computeIfAbsent(key, k -> new HashSet<>()).add(stranded.hut());
            if (importantHut(stranded.hut()))
            {
                plotFreed(key, stranded.hut());
            }
            ColonyAutopilot.LOGGER.info("[{}] no citizen is free to crew a builder's hut toward the {}'s land (the village is at its citizen ceiling and everyone has a job) — none is raised, and the {} {}",
              colony.getName(), stranded.stepName(), stranded.stepName(), afterRefusedRescue(colony));
        }
        else if (stranded != null && builderPlaceable && !BuilderCap.mayPlace(colony, level, ModBlocks.blockHutBuilder))
        {

        }
        else if (stranded != null && builderPlaceable)
        {
            ColonyAutopilot.LOGGER.info("[{}] raising builder's hut #{} toward {} — the {} found land beyond every crew's reach",
              colony.getName(), builderHuts + 1, stranded.frontier().toShortString(), stranded.stepName());
            rescueFrontier.put(key, new StagedFrontier(stranded.frontier(), now + 2400));
            beginPlacement(colony, level, new ResolvedStep(ModBlocks.blockHutBuilder, "Builder's Hut", 0, 0), ModBlocks.blockHutBuilder);
            return;
        }
        else if (stranded != null && (builderPending || unavailable.contains(ModBlocks.blockHutBuilder)))
        {
            builderRescue.remove(key);
            if (importantHut(stranded.hut()))
            {

                rescueRefused.putIfAbsent(key, new HashSet<>());
                rescueRefused.get(key).add(stranded.hut());
                plotFreed(key, stranded.hut());
            }
        }

        boolean importantDue = false;

        boolean importantWaiting = false;

        boolean recordWanted = stranded == null || !importantHut(stranded.hut());
        final Long importantCallAt = importantCallUnanswered.get(key);
        final boolean importantAsked = countByHut.getOrDefault(ModBlocks.blockHutTownHall, 0) > 0
              && (importantCallAt == null || now - importantCallAt >= 2400) && AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED);
        for (final ResolvedStep planned : walk)
        {
            final Block stepHut = planned.hut();
            if (!importantHut(stepHut) || planned.targetLevel() > 0 || countByHut.getOrDefault(stepHut, 0) >= planned.targetCount()
                  || unavailable.contains(stepHut))
            {
                continue;
            }
            if (stranded != null && stepHut == stranded.hut())
            {
                recordWanted = true;
            }
            if (importantWaiting || !importantAsked || citizens < planned.minCitizens() || !hutUnlocked(colony, stepHut))
            {
                continue;
            }
            final Long stepRetry = plotBlocked.get(stepHut);
            if ((stepRetry == null || now >= stepRetry || (terraformer != null && terraformer.cuttingPadFor(colony, stepHut)))
                  && walkReaches(colony, walk, planned, unavailable, plotBlocked, countByHut, maxLevelByHut, citizens, now, false)
                  && (stepHut != ModBlocks.blockHutStable || Boolean.TRUE.equals(shipsStable(colony))))
            {

                importantWaiting = true;
                importantDue = walkReaches(colony, walk, planned, unavailable, plotBlocked, countByHut, maxLevelByHut, citizens, now, true);
            }
        }
        if (!recordWanted && builderRescue.get(key) == stranded)
        {
            builderRescue.remove(key);
            ColonyAutopilot.LOGGER.debug("[{}] the {}'s rescue is dropped: no step of the plan waits for one any more — its land is free again",
              colony.getName(), stranded.stepName());
        }

        final Long towerRetry = plotBlocked.get(ModBlocks.blockHutGuardTower);
        final boolean noGuardPost = countByHut.getOrDefault(ModBlocks.blockHutGuardTower, 0) + countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) == 0;

        final boolean walksTurn = importantWaiting && !walkPlacedLast.containsKey(key);
        if (!openingBuildersDue && ((!importantDue && !walksTurn) || noGuardPost) && !unavailable.contains(ModBlocks.blockHutGuardTower) && (towerRetry == null || now >= towerRetry))
        {
            int unguarded = 0;
            int guardPosts = 0;
            boolean guardPending = false;
            for (final IBuilding building : buildings)
            {
                final Block block = building.getBuildingType().getBuildingBlock();
                if (block == ModBlocks.blockHutGuardTower || block == ModBlocks.blockHutBarracks)
                {
                    guardPosts++;
                    if (building.getBuildingLevel() == 0)
                    {
                        guardPending = true;
                        break;
                    }
                }
                else if (!building.isGuardBuildingNear())
                {
                    unguarded++;
                }
            }
            if (!guardPending && unguarded >= 3 && guardPosts * 5 <= buildings.size())
            {

                final Long barracksRetry = plotBlocked.get(ModBlocks.blockHutBarracks);
                if (barracksDue(colony, countByHut, unavailable) && (barracksRetry == null || now >= barracksRetry)
                      && BuilderCap.mayPlace(colony, level, ModBlocks.blockHutBarracks))
                {
                    walkPlacedLast.remove(key);
                    beginPlacement(colony, level, new ResolvedStep(ModBlocks.blockHutBarracks, "Barracks", 0, 0), ModBlocks.blockHutBarracks);
                    return;
                }

                if (countByHut.getOrDefault(ModBlocks.blockHutGuardTower, 0) < AutopilotConfig.get(colony, AutopilotConfig.GUARD_TOWER_CAP)
                      && BuilderCap.mayPlace(colony, level, ModBlocks.blockHutGuardTower))
                {
                    walkPlacedLast.remove(key);
                    beginPlacement(colony, level, new ResolvedStep(ModBlocks.blockHutGuardTower, "Guard Tower", 0, 0), ModBlocks.blockHutGuardTower);
                    return;
                }
            }
        }

        if (upgrades != null && bedCapped)
        {
            boolean anyUnbuilt = false;
            boolean allStruggling = true;
            for (final IBuilding building : buildings)
            {
                if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutHome && building.getBuildingLevel() == 0)
                {
                    anyUnbuilt = true;
                    if (!upgrades.struggling(colony, building.getID()))
                    {
                        allStruggling = false;
                        break;
                    }
                }
            }
            final Long homeRetry = plotBlocked.get(ModBlocks.blockHutHome);

            if (anyUnbuilt && allStruggling && !unavailable.contains(ModBlocks.blockHutHome)
                  && (homeRetry == null || now >= homeRetry) && BuilderCap.mayPlace(colony, level, ModBlocks.blockHutHome))
            {
                ColonyAutopilot.LOGGER.info("[{}] every unbuilt residence stands on ground the builders keep refusing — placing a fresh one on better ground",
                  colony.getName());
                beginPlacement(colony, level, new ResolvedStep(ModBlocks.blockHutHome, "Residence", 0, 0), ModBlocks.blockHutHome);
                return;
            }
        }

        if (stranded == null && !importantDue && !walksTurn && builderPlaceable && builderHuts >= 2 && builderHuts < Math.min(MAX_BUILDER_HUTS, 2 + builtCount / BUILDINGS_PER_CREW)

              && (citizens < census.getMaxCitizens() || census.getJoblessCitizen() != null)
              && BuilderCap.mayPlace(colony, level, ModBlocks.blockHutBuilder))
        {
            ColonyAutopilot.LOGGER.info("[{}] raising builder's hut #{} — {} buildings outgrew {} crews",
              colony.getName(), builderHuts + 1, builtCount, builderHuts);

            rescueFrontier.remove(key);
            walkPlacedLast.remove(key);
            beginPlacement(colony, level, new ResolvedStep(ModBlocks.blockHutBuilder, "Builder's Hut", 0, 0), ModBlocks.blockHutBuilder);
            return;
        }

        for (final ResolvedStep planned : planFor(colony))
        {
            ResolvedStep step = planned;
            Block hutBlock = step.hut();

            if (planned.targetLevel() > 0)
            {

                if (unavailable.contains(hutBlock) || maxLevelByHut.getOrDefault(hutBlock, 0) >= planned.targetLevel())
                {
                    continue;
                }
                if (citizens < planned.minCitizens())
                {
                    final String gate = planned.name() + "@" + planned.minCitizens();
                    if (!gate.equals(lastGateLogged.put(key, gate)))
                    {
                        ColonyAutopilot.LOGGER.info("[{}] growth waiting at {} — needs {} citizens, colony has {}",
                          colony.getName(), planned.name(), planned.minCitizens(), citizens);
                    }
                    return;
                }
                final String gate = planned.name() + "@L" + planned.targetLevel();
                if (!gate.equals(lastGateLogged.put(key, gate)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] growth waiting at the {} — it must reach level {} (level {} now) before the village grows further",
                      colony.getName(), planned.name(), planned.targetLevel(), maxLevelByHut.getOrDefault(hutBlock, 0));
                }
                if (upgrades != null && hutBlock == ModBlocks.blockHutTownHall)
                {
                    upgrades.driveTownHall(colony, level);
                }
                return;
            }

            if (hutBlock == ModBlocks.blockHutTownHall)
            {
                if (countByHut.getOrDefault(ModBlocks.blockHutTownHall, 0) > 0)
                {
                    continue;
                }
                final String gate = "Town Hall@missing";
                if (!gate.equals(lastGateLogged.put(key, gate)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] growth waits for the player to place the town hall — the plan never places one",
                      colony.getName());
                }
                return;
            }

            final boolean towerStep = hutBlock == ModBlocks.blockHutGuardTower;
            if (towerStep && barracksDue(colony, countByHut, unavailable))
            {
                step = new ResolvedStep(ModBlocks.blockHutBarracks, "Barracks", planned.minCitizens(), planned.targetCount());
                hutBlock = ModBlocks.blockHutBarracks;
            }
            if (unavailable.contains(hutBlock))
            {
                continue;
            }
            if (hutBlock == ModBlocks.blockHutGraveyard && AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES))
            {
                continue;

            }
            final Long retryAt = plotBlocked.get(hutBlock);
            if (retryAt != null && now < retryAt)
            {

                if (importantHut(hutBlock) && terraformer != null && terraformer.cuttingPadFor(colony, hutBlock))
                {
                    return;
                }
                continue;
            }
            final int standing = countByHut.getOrDefault(planned.hut(), 0)
                                   + (towerStep ? countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) : 0);
            if (standing >= planned.targetCount())
            {
                continue;
            }

            if (hutBlock == ModBlocks.blockHutStable)
            {
                final Boolean shipped = shipsStable(colony);
                if (shipped == null)
                {
                    continue;
                }
                if (!shipped)
                {
                    ColonyAutopilot.LOGGER.info("[{}] pack '{}' has no Stable to build from — the village builds none itself (not every style pack ships one); one of another style can be placed by hand once Mounted Combat is researched",
                      colony.getName(), colony.getStructurePack());
                    unavailableHuts.computeIfAbsent(key, k -> new HashSet<>()).add(hutBlock);
                    continue;
                }
            }

            if (citizens < step.minCitizens())
            {
                final String gate = step.name() + "@" + step.minCitizens();
                if (!gate.equals(lastGateLogged.put(key, gate)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] growth waiting at {} — needs {} citizens, colony has {}",
                      colony.getName(), step.name(), step.minCitizens(), citizens);
                }
                return;
            }

            final ResourceLocation hutResearch = colony.getResearchManager().getResearchEffectIdFrom(hutBlock);
            if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(hutResearch)
                  && colony.getResearchManager().getResearchEffects().getEffectStrength(hutResearch) < 1)
            {
                if (researchLockLogged.computeIfAbsent(key, k -> new HashSet<>()).add(hutBlock))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the {} awaits university research — the village will build it once unlocked",
                      colony.getName(), step.name());
                }
                continue;
            }

            final Set<Block> lockLogged = researchLockLogged.get(key);
            if (lockLogged != null)
            {
                lockLogged.remove(hutBlock);
            }

            if (hutBlock == ModBlocks.blockHutHome
                  && homePlacementHeld(colony, key, standing, lowestHomeLevel, homeBlueprintMax, townHallLevel, bedCapped, homesShallow))
            {
                continue;
            }

            if (hutBlock != ModBlocks.blockHutHome && hutBlock != ModBlocks.blockHutTownHall && !importantHut(hutBlock)
                  && citizens >= colony.getCitizenManager().getMaxCitizens()
                  && colony.getCitizenManager().getJoblessCitizen() == null)
            {
                continue;
            }

            if (importantHut(hutBlock) || importantWaiting)
            {
                importantCallUnanswered.put(key, now);
            }
            beginPlacement(colony, level, step, hutBlock);

            if (pendingSince.containsKey(key))
            {
                walkPlacedLast.put(key, now);
            }
            return;
        }

        if (!AutopilotConfig.get(colony, AutopilotConfig.ENDLESS_EXPANSION))
        {
            return;
        }

        if (AutopilotConfig.get(colony, AutopilotConfig.UPGRADES_BEFORE_EXPANSION) && AutopilotConfig.upgradesBuildings(colony))
        {
            for (final IBuilding building : buildings)
            {

                if (building.getBuildingLevel() >= building.getMaxBuildingLevel()
                      || UpgradeDirector.tornDown(building)
                      || (AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES)
                            && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGraveyard))
                {
                    continue;
                }
                final ResourceLocation effect = colony.getResearchManager().getResearchEffectIdFrom(building.getBuildingType().getBuildingBlock());
                if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(effect)
                      && colony.getResearchManager().getResearchEffects().getEffectStrength(effect) <= building.getBuildingLevel())
                {
                    continue;
                }
                return;
            }
        }

        if (bedCapped)
        {
            final int homes = countByHut.getOrDefault(ModBlocks.blockHutHome, 0);
            final int towers = countByHut.getOrDefault(ModBlocks.blockHutGuardTower, 0);

            final boolean needTower = homes > 0 && towers * 3 < homes && towers < AutopilotConfig.get(colony, AutopilotConfig.GUARD_TOWER_CAP)
                                        && colony.getCitizenManager().getJoblessCitizen() != null;

            final boolean homesAllowed = colony.getCitizenManager().getCurrentCitizenCount() < totalJobSlots(colony);
            if (needTower || (homesAllowed && !homePlacementHeld(colony, key, homes, lowestHomeLevel, homeBlueprintMax, townHallLevel, true, homesShallow)))
            {
                final Block next = needTower ? ModBlocks.blockHutGuardTower : ModBlocks.blockHutHome;
                final Long homeRetryAt = plotBlocked.get(next);

                if (!unavailable.contains(next) && (homeRetryAt == null || now >= homeRetryAt))
                {
                    beginPlacement(colony, level, new ResolvedStep(next, needTower ? "Guard Tower" : "Residence", 0, 0), next);
                    return;
                }
            }

        }

        if (upgrades != null && upgrades.anyRepairPending(colony))
        {
            return;
        }

        final int targetPerType = 1 + citizens / AutopilotConfig.get(colony, AutopilotConfig.CITIZENS_PER_EXTRA_PRODUCTION);
        if (targetPerType < 2)
        {
            return;
        }
        final ResolvedStep tower = new ResolvedStep(ModBlocks.blockHutGuardTower, "Guard Tower", 0, 0);
        if (unavailable.contains(tower.hut()))
        {
            return;
        }
        final Long retryAt = plotBlocked.get(tower.hut());
        if (retryAt != null && now < retryAt)
        {
            return;
        }
        final int count = countByHut.getOrDefault(tower.hut(), 0);
        if (count > 0 && count < targetPerType && count < AutopilotConfig.get(colony, AutopilotConfig.GUARD_TOWER_CAP)
              && colony.getCitizenManager().getJoblessCitizen() != null)
        {
            beginPlacement(colony, level, tower, tower.hut());
        }
    }

    private String afterRefusedRescue(final IColony colony)
    {
        return AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED) && terraformer != null
                 ? "takes the terraformer's ladder instead" : "keeps retrying its own search";
    }

    private static boolean barracksDue(final IColony colony, final Map<Block, Integer> countByHut, final Set<Block> unavailable)
    {
        if (countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) > 0 || unavailable.contains(ModBlocks.blockHutBarracks))
        {
            return false;
        }
        return hutUnlocked(colony, ModBlocks.blockHutBarracks);
    }

    private static boolean importantHut(final Block hut)
    {
        return hut == ModBlocks.blockHutTavern || hut == ModBlocks.blockHutWareHouse || hut == ModBlocks.blockHutBlacksmith
                 || hut == ModBlocks.blockHutCook || hut == ModBlocks.blockHutUniversity || hut == ModBlocks.blockHutStable
                 || hut == ModBlocks.blockHutLibrary || hut == ModBlocks.blockHutHospital;
    }

    private static boolean hutUnlocked(final IColony colony, final Block hut)
    {
        final ResourceLocation research = colony.getResearchManager().getResearchEffectIdFrom(hut);
        return !MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(research)
                 || colony.getResearchManager().getResearchEffects().getEffectStrength(research) >= 1;
    }

    private static boolean walkReaches(final IColony colony, final List<ResolvedStep> walk, final ResolvedStep target, final Set<Block> unavailable,
      final Map<Block, Long> plotBlocked, final Map<Block, Integer> countByHut, final Map<Block, Integer> maxLevelByHut, final int citizens, final long now,
      final boolean nextUp)
    {
        for (int i = 0; walk.get(i) != target; i++)
        {
            final ResolvedStep earlier = walk.get(i);
            final Long earlierRetry = plotBlocked.get(earlier.hut());
            final int earlierStanding = countByHut.getOrDefault(earlier.hut(), 0)
              + (earlier.hut() == ModBlocks.blockHutGuardTower ? countByHut.getOrDefault(ModBlocks.blockHutBarracks, 0) : 0);
            if (!(unavailable.contains(earlier.hut()) || (earlier.targetLevel() > 0
              ? maxLevelByHut.getOrDefault(earlier.hut(), 0) >= earlier.targetLevel()
              : earlier.hut() == ModBlocks.blockHutTownHall || citizens >= earlier.minCitizens() || earlierStanding >= earlier.targetCount()
                  || (earlierRetry != null && now < earlierRetry)
                  || (earlier.hut() == ModBlocks.blockHutGraveyard && AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES)))))
            {
                return false;
            }

            if (nextUp && earlier.targetLevel() == 0 && earlier.hut() != ModBlocks.blockHutTownHall && earlier.hut() != ModBlocks.blockHutHome
                  && earlier.hut() != ModBlocks.blockHutGuardTower && earlier.hut() != ModBlocks.blockHutStable && !unavailable.contains(earlier.hut())
                  && earlierStanding < earlier.targetCount() && (earlierRetry == null || now >= earlierRetry)
                  && !(earlier.hut() == ModBlocks.blockHutGraveyard && AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES))
                  && hutUnlocked(colony, earlier.hut())
                  && (importantHut(earlier.hut()) || citizens < colony.getCitizenManager().getMaxCitizens()
                        || colony.getCitizenManager().getJoblessCitizen() != null))
            {
                return false;
            }
        }
        return true;
    }

    private record Design(int tier, boolean mapped, String path, java.nio.file.Path file) implements Comparable<Design>
    {
        @Override
        public int compareTo(final Design other)
        {
            int order = Integer.compare(tier, other.tier);
            order = order != 0 ? order : Boolean.compare(other.mapped, mapped);
            order = order != 0 ? order : Boolean.compare(other.path.contains("/default/"), path.contains("/default/"));
            order = order != 0 ? order : Integer.compare(path.length(), other.path.length());
            return order != 0 ? order : path.compareTo(other.path);
        }
    }

    private static Blueprint findDesign(final String packName, final Block hutBlock, final String hutName, final String mappedFile,
      final HolderLookup.Provider registries)
    {
        final StructurePackMeta pack = StructurePacks.waitUntilFinishedLoading() ? StructurePacks.getStructurePack(packName) : null;
        if (pack == null)
        {
            return null;
        }

        final List<Design> order = new ArrayList<>();
        final java.util.ArrayDeque<java.nio.file.Path> folders = new java.util.ArrayDeque<>(List.of(pack.getPath()));
        while (!folders.isEmpty())
        {
            final java.nio.file.Path folder = folders.pop();
            try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(folder))
            {
                for (final java.nio.file.Path entry : (Iterable<java.nio.file.Path>) entries::iterator)
                {
                    if (java.nio.file.Files.isDirectory(entry))
                    {
                        folders.push(entry);
                        continue;
                    }

                    final String file = entry.getFileName().toString().replace(".blueprint", "");

                    if (entry.toString().endsWith("blueprint") && (hutBlock != ModBlocks.blockHutStable || file.endsWith("1")))
                    {
                        final String path = entry.getParent().resolve(file + ".blueprint").toString().replace('\\', '/');
                        order.add(new Design(file.equals(hutName + "1") ? 0 : file.endsWith("1") && !file.startsWith("alt") ? 1 : 2,
                          mappedFile != null && path.endsWith(mappedFile), path, entry));
                    }
                }
            }
            catch (final java.io.IOException e)
            {
                ColonyAutopilot.LOGGER.warn("could not list the style pack folder {}", folder, e);
            }
        }
        Collections.sort(order);
        Blueprint invisible = null;
        for (final Design design : order)
        {

            final Blueprint bp = StructurePacks.getBlueprint(pack.getName(), design.file(), registries);
            final BlockPos at = bp == null ? null : bp.getPrimaryBlockOffset();
            if (at != null && at.getX() >= 0 && at.getY() >= 0 && at.getZ() >= 0
                  && at.getX() < bp.getSizeX() && at.getY() < bp.getSizeY() && at.getZ() < bp.getSizeZ()
                  && bp.getPalette()[bp.getStructure()[at.getY()][at.getZ()][at.getX()] & 0xFFFF].getBlock() == hutBlock)
            {

                if (!BlueprintTagUtils.getBlueprintTags(bp).getOrDefault(BlockPos.ZERO, List.of()).contains("invisible"))
                {
                    return bp;
                }
                invisible = invisible == null ? bp : invisible;
            }
        }
        return invisible;
    }

    private void beginPlacement(final IColony colony, final ServerLevel level, final ResolvedStep step, final Block hutBlock)
    {

        importantCallUnanswered.remove(ColonyAutopilot.colonyKey(colony));

        if (!AutopilotConfig.live(colony, AutopilotConfig.GROWTH_ENABLED))
        {
            return;
        }

        if (!BuilderCap.mayPlace(colony, level, hutBlock))
        {
            return;
        }

        try
        {
            final ColonyId key = ColonyAutopilot.colonyKey(colony);
            final String packName = colony.getStructurePack();
            final StructurePackMeta pack = StructurePacks.getStructurePack(packName);
            if (pack == null)
            {

                final String gate = "pack:" + packName;
                if (!gate.equals(lastGateLogged.put(key, gate)))
                {
                    ColonyAutopilot.LOGGER.warn("[{}] structure pack '{}' is not loaded — growth waits until it is (removed or renamed from the modpack?)", colony.getName(), packName);
                }
                blockedUntil.put(key, level.getGameTime() + 1200);

                BuilderCap.placementAbandoned(colony);
                return;
            }

            pendingSince.put(key, level.getGameTime());
            final String designKey = packName + "|" + BuiltInRegistries.BLOCK.getKey(hutBlock);
            final java.nio.file.Path known = designPaths.get(designKey);

            final String hutName = hutBlock instanceof AbstractBlockHut<?> hut ? hut.getBlueprintName() : null;
            final String mappedFile = hutName == null || BlueprintMapping.getPathMapping("", hutName) == null
                                        ? null : "/" + BlueprintMapping.getPathMapping("", hutName) + "1.blueprint";
            final HolderLookup.Provider registries = level.registryAccess();
            final CompletableFuture<Blueprint> future = known != null
              ? StructurePacks.getBlueprintFuture(packName, known, true, registries)
              : CompletableFuture.supplyAsync(() -> findDesign(packName, hutBlock, hutName, mappedFile, registries), IOPool.getExecutor());
            future.whenComplete((found, failure) -> {
                if (failure != null)
                {

                    ColonyAutopilot.LOGGER.warn("[{}] the search of pack '{}' for {} failed", colony.getName(), packName, step.name(), failure);
                }
            });

            ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(future, level, blueprint -> {
                pendingSince.remove(key);

                if (!AutopilotConfig.live(colony, AutopilotConfig.GROWTH_ENABLED) || !AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED))
                {
                    BuilderCap.placementAbandoned(colony);
                    return;
                }
                try
                {
                    if (blueprint == null && known != null)
                    {
                        designPaths.remove(designKey);

                        BuilderCap.placementAbandoned(colony);
                        beginPlacement(colony, level, step, hutBlock);
                    }
                    else
                    {
                        if (blueprint != null && blueprint.getFilePath() != null && blueprint.getFileName() != null)
                        {
                            designPaths.put(designKey, blueprint.getFilePath().resolve(blueprint.getFileName() + ".blueprint"));
                        }
                        completePlacement(colony, level, step, hutBlock, pack, blueprint);

                        BuilderCap.placementAbandoned(colony);
                        if (terraformer != null && terraformer.cuttingPad(colony))
                        {

                            BuilderCap.padUnderway(colony, level);
                        }
                    }
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("[{}] placement of {} failed", colony.getName(), step.name(), e);

                    BuilderCap.placementAbandoned(colony);
                }
            }));
        }
        catch (final RuntimeException e)
        {
            BuilderCap.placementAbandoned(colony);
            throw e;
        }
    }

    private void completePlacement(
      final IColony colony,
      final ServerLevel level,
      final ResolvedStep step,
      final Block hutBlock,
      final StructurePackMeta pack,
      final Blueprint blueprint)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (blueprint == null && hutBlock == ModBlocks.blockHutStable)
        {

            ColonyAutopilot.LOGGER.info("[{}] pack '{}' has no Stable to build from — the village builds none itself (not every style pack ships one); one of another style can be placed by hand once Mounted Combat is researched",
              colony.getName(), pack.getName());
            unavailableHuts.computeIfAbsent(key, k -> new HashSet<>()).add(hutBlock);
            return;
        }
        if (blueprint == null)
        {
            ColonyAutopilot.LOGGER.warn("[{}] pack '{}' has no blueprint for {} — that hut is skipped for this colony",
              colony.getName(), pack.getName(), step.name());
            unavailableHuts.computeIfAbsent(key, k -> new HashSet<>()).add(hutBlock);
            return;
        }

        final Long placed = lastPlacement.get(key);
        if (placed != null && level.getGameTime() - placed < AutopilotConfig.get(colony, AutopilotConfig.PLACEMENT_COOLDOWN_MINUTES) * 1200L)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the lookup for {} came back inside the placement cooldown — dropped: another placement landed while it was queued",
              colony.getName(), step.name());
            return;
        }

        if (!WorldStructures.whole(colony))
        {
            return;
        }

        blueprint.setRotationMirror(RotationMirror.NONE, level);

        final int groundOffset = BlueprintGround.placementOffset(blueprint);
        final int anchorOffset = AutopilotConfig.get(colony, AutopilotConfig.BLUEPRINT_GROUND_OFFSET) ? groundOffset : 1;
        final int lift = anchorOffset - 1;
        final BlockState anchorState = blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        final Direction authoredFacing = anchorState != null && anchorState.hasProperty(AbstractBlockHut.FACING)
                                          ? anchorState.getValue(AbstractBlockHut.FACING) : Direction.SOUTH;

        final BlueprintGround.Front front = BlueprintGround.front(blueprint, groundOffset);
        final int frontRotation = (front != null ? front.side() : authoredFacing).get2DDataValue();
        final Map<Direction, SiteSelector.Placement> options = new EnumMap<>(Direction.class);
        for (final Direction facing : new Direction[] {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST})
        {
            final int steps = (4 + facing.get2DDataValue() - frontRotation) % 4;
            blueprint.setRotationMirror(RotationMirror.of(Rotation.values()[steps], Mirror.NONE), level);

            final BlockPos door = front != null ? front.doorOffset().rotate(Rotation.values()[steps]) : BlockPos.ZERO;
            options.put(facing, new SiteSelector.Placement(blueprint.getSizeX(), blueprint.getSizeZ(),
              blueprint.getPrimaryBlockOffset().getX(), blueprint.getPrimaryBlockOffset().getZ(), door.getX(), door.getZ()));
        }

        final Map<BlockPos, SiteSelector.Footprint> reserved = reservedPlots.computeIfAbsent(key, k -> new HashMap<>());
        pruneReservations(colony, reserved);

        final List<BlockPos> spreadFrom = new ArrayList<>();
        final List<BlockPos> coverFrom = new ArrayList<>();
        final boolean guardPost = hutBlock == ModBlocks.blockHutGuardTower || hutBlock == ModBlocks.blockHutBarracks;
        final List<BlockPos> spokenFor = new ArrayList<>();
        if (guardPost)
        {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                final Block block = building.getBuildingType().getBuildingBlock();
                if ((block == ModBlocks.blockHutGuardTower || block == ModBlocks.blockHutBarracks)
                      && building.getBuildingLevel() == 0)
                {
                    spokenFor.add(building.getPosition());
                }
            }
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (hutBlock == ModBlocks.blockHutBuilder
                  && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBuilder)
            {
                spreadFrom.add(building.getPosition());
            }
            if (guardPost && !building.isGuardBuildingNear()
                  && spokenFor.stream().noneMatch(post -> SiteSelector.withinGuardClaim(post, building.getPosition())))
            {
                coverFrom.add(building.getPosition());
            }
        }
        if (hutBlock == ModBlocks.blockHutBuilder && spreadFrom.isEmpty())
        {

            spreadFrom.add(colony.getCenter());
        }
        final boolean selfBuilding = hutBlock == ModBlocks.blockHutBuilder;

        final StagedFrontier staged = selfBuilding ? rescueFrontier.remove(key) : null;
        final BlockPos frontier = staged != null && level.getGameTime() <= staged.until() ? staged.frontier() : null;

        final boolean wooded = hutBlock == ModBlocks.blockHutLumberjack;

        final boolean keystone = hutBlock == ModBlocks.blockHutUniversity;

        final boolean coreHut = importantHut(hutBlock);

        final boolean fisher = hutBlock == ModBlocks.blockHutFisherman;

        final boolean farm = hutBlock == ModBlocks.blockHutFarmer;

        final int configRadius = AutopilotConfig.get(colony, AutopilotConfig.SEARCH_RADIUS_BLOCKS);
        final boolean terraforms = AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED) && terraformer != null;

        final boolean earned = ColonyGrounds.get(level).structuresYield(colony.getID(), hutBlock);
        final int parkedBefore = Math.max(fullParks.getOrDefault(key, Map.of()).getOrDefault(hutBlock, 0), earned && terraforms ? 1 : 0);

        final Rescue bridging = frontier == null ? null : builderRescue.get(key);
        final int bridgeRadius = bridging == null || !importantHut(bridging.hut()) ? 0
                                   : Math.min(200, Math.max(Math.abs(frontier.getX() - colony.getCenter().getX()), Math.abs(frontier.getZ() - colony.getCenter().getZ())));
        final int naturalRadius = keystone
                                    ? (universityWidened.containsKey(key) || earned && terraforms ? configRadius : Math.min(configRadius, SiteSelector.KEYSTONE_RADIUS))
                                    : Math.max(bridgeRadius, Math.min(200, configRadius + configRadius / 2 * Math.min(parkedBefore, 2)));

        final int searchedRadius = coreHut && (!keystone || earned || !terraforms) ? Math.min(200, configRadius + configRadius / 2 * 2) : naturalRadius;
        final List<SiteSelector.Footprint> sacred = sacredGround(colony);

        final List<SiteSelector.Footprint> structures = WorldStructures.footprintsFor(colony);

        final List<SiteSelector.Footprint> doorsteps = doorstepZones(colony, level);

        final Map<Block, SiteSelector.Footprint> pendingPads = lastTerraform.get(key);
        if (pendingPads != null)
        {
            for (final Map.Entry<Block, SiteSelector.Footprint> pad : pendingPads.entrySet())
            {
                if (!pad.getKey().equals(hutBlock))
                {
                    sacred.add(pad.getValue());
                }
            }
        }

        final Rescue landHeld = builderRescue.get(key);
        if (landHeld != null && landHeld.hut() != hutBlock)
        {
            sacred.add(landHeld.land());
        }

        SiteSelector.Result site = null;
        final Map<Block, SiteSelector.Result> readyAnchors = terraformAnchors.get(key);
        if (readyAnchors != null)
        {
            site = readyAnchors.remove(hutBlock);

            final Set<Long> cold = new HashSet<>();
            if (site != null)
            {
                final SiteSelector.Placement pad = options.get(site.facing());
                final int padX = site.anchor().getX() - pad.offsetX();
                final int padZ = site.anchor().getZ() - pad.offsetZ();
                for (int cx = (padX - 3) >> 4; cx <= (padX + pad.sizeX() + 2) >> 4; cx++)
                {
                    for (int cz = (padZ - 3) >> 4; cz <= (padZ + pad.sizeZ() + 2) >> 4; cz++)
                    {
                        if (!WorldUtil.isChunkLoaded(level, cx, cz))
                        {
                            cold.add(ChunkPos.asLong(cx, cz));
                        }
                    }
                }
            }
            if (!cold.isEmpty())
            {

                final int tries = coldPadTries.computeIfAbsent(key, k -> new HashMap<>()).merge(hutBlock, 1, Integer::sum);
                if (tries > COLD_PAD_TRIES)
                {
                    coldPadTries.get(key).remove(hutBlock);
                    ColonyAutopilot.LOGGER.info("[{}] the pad cleared for {} at {} never loaded — giving it up and choosing the plot again",
                      colony.getName(), step.name(), site.anchor().toShortString());
                    terraformAbandoned(key, hutBlock);
                    site = null;
                }
                else
                {
                    readyAnchors.put(hutBlock, site);
                    if (frontier != null)
                    {

                        rescueFrontier.put(key, new StagedFrontier(frontier, level.getGameTime() + 1200 + 2400));
                    }
                    ChunkKeeper.requestFrontier(level, cold);
                    if (tries == 1)
                    {
                        ColonyAutopilot.LOGGER.info("[{}] the pad cleared for {} at {} is not loaded right now — trying again each game-minute",
                          colony.getName(), step.name(), site.anchor().toShortString());
                    }
                    plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 1200);
                    return;
                }
            }
            else if (site != null && coldPadTries.containsKey(key))
            {
                coldPadTries.get(key).remove(hutBlock);
            }
            if (site != null && SiteSelector.groundHeight(level, site.anchor().getX(), site.anchor().getZ()) != site.anchor().getY())
            {

                ColonyAutopilot.LOGGER.info("[{}] the pad cleared for {} at {} is not at its graded level — choosing the plot again",
                  colony.getName(), step.name(), site.anchor().toShortString());
                site = null;
            }
        }

        if (site != null)
        {
            final SiteSelector.Placement anchored = options.get(site.facing());
            final int zeroX = site.anchor().getX() - anchored.offsetX();
            final int zeroZ = site.anchor().getZ() - anchored.offsetZ();

            final SiteSelector.Footprint anchoredPlot = withDoorstep(new SiteSelector.Footprint(zeroX, zeroZ,
              zeroX + anchored.sizeX() - 1, zeroZ + anchored.sizeZ() - 1), site.facing());
            final List<SiteSelector.Footprint> closed = new ArrayList<>(sacred);

            closed.removeAll(structures);
            closed.addAll(doorsteps);
            for (final SiteSelector.Footprint zone : closed)
            {
                if (anchoredPlot.intersects(zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ()))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the terraformed pad reserved for the {} is no longer free (a protected zone, field, ornament, pond or another hut's pad now covers it) — abandoning that pad and finding new ground",
                      colony.getName(), step.name());
                    site = null;
                    final Map<Block, SiteSelector.Footprint> abandonRects = lastTerraform.get(key);
                    if (abandonRects != null)
                    {
                        abandonRects.remove(hutBlock);
                    }
                    break;
                }
            }
        }

        final boolean onPad = site != null;

        final Set<Long> band = !onPad && (farm || fisher) ? roadBand(colony, level) : Set.of();
        if (site == null)
        {
            site = SiteSelector.findSite(level, colony, options, sacred, doorsteps, spreadFrom, coverFrom, wooded, frontier, naturalRadius, keystone, selfBuilding, fisher, farm,
              band, false);
            if (site == null && searchedRadius > naturalRadius)
            {
                site = SiteSelector.findSite(level, colony, options, sacred, doorsteps, spreadFrom, coverFrom, wooded, frontier, searchedRadius, keystone, selfBuilding, fisher, farm,
                  band, false);
            }

            if (site == null && (parkedBefore >= 1
                  || ((plotFailStrikes.getOrDefault(key, Map.of()).getOrDefault(hutBlock, 0) >= 2 || earned) && !terraforms))
                  && sacred.removeAll(structures))
            {

                if (structuresYielded.computeIfAbsent(key, k -> new HashSet<>()).add(hutBlock))
                {
                    ColonyAutopilot.LOGGER.info("[{}] no ground clear of the world's structures for {} within {} blocks — it may now stand on one",
                      colony.getName(), step.name(), searchedRadius);
                }
                site = SiteSelector.findSite(level, colony, options, sacred, doorsteps, spreadFrom, coverFrom, wooded, frontier, searchedRadius, keystone, selfBuilding, fisher, farm,
                  band, false);
            }
        }

        if (site == null && coreHut)
        {
            site = SiteSelector.findSite(level, colony, options, sacred, doorsteps, spreadFrom, coverFrom, wooded, frontier, searchedRadius, keystone, selfBuilding, fisher, farm,
              band, true);
            if (site != null)
            {
                ColonyAutopilot.LOGGER.info("[{}] no plot for the {} inside the colony's claim — it goes at the claim's edge, at {}, and the colony claims the ground under it",
                  colony.getName(), step.name(), site.anchor().toShortString());
            }
        }
        if (site == null && bridgeRadius > 0)
        {

            builderRescue.remove(key);
            rescueRefused.putIfAbsent(key, new HashSet<>());
            rescueRefused.get(key).add(bridging.hut());
            plotFreed(key, bridging.hut());
            ColonyAutopilot.LOGGER.info("[{}] no plot for a builder's hut within {} blocks toward the {}'s land at {} — no hut raised; the {} {}",
              colony.getName(), naturalRadius, bridging.stepName(), frontier.toShortString(), bridging.stepName(), afterRefusedRescue(colony));
        }
        if (site == null)
        {

            final int strikeCount = plotFailStrikes.computeIfAbsent(key, k -> new HashMap<>()).merge(hutBlock, coreHut ? 2 : 1, Integer::sum);

            final Rescue held = builderRescue.get(key);
            if (held != null && held.hut() == hutBlock)
            {
                builderRescue.remove(key);
            }
            final boolean rescueFree = held == null || held.hut() == hutBlock || !importantHut(held.hut());

            final Set<Block> refusedRescues = rescueRefused.computeIfAbsent(key, k -> new HashSet<>());
            final SiteSelector.Result probe = strikeCount >= 2 && !selfBuilding && !refusedRescues.contains(hutBlock) && rescueFree
                ? SiteSelector.findSite(level, colony, options, sacred, doorsteps, List.of(), List.of(), false, null, searchedRadius, keystone, true, fisher, farm,
                    band, false)
                : null;
            if (probe != null)
            {

                ColonyAutopilot.LOGGER.info("[{}] the {} has buildable land beyond every builder's reach — a builder's hut will bridge the frontier",
                  colony.getName(), step.name());
                final SiteSelector.Placement probed = options.get(probe.facing());
                final int probeX = probe.anchor().getX() - probed.offsetX();
                final int probeZ = probe.anchor().getZ() - probed.offsetZ();

                final BlockPos reachPoint = new BlockPos(probe.anchor().getX(), SiteSelector.groundHeight(level, probe.anchor().getX(), probe.anchor().getZ()),
                  probe.anchor().getZ());
                builderRescue.put(key, new Rescue(step.name(), hutBlock, reachPoint,
                  withDoorstep(new SiteSelector.Footprint(probeX, probeZ, probeX + probed.sizeX() - 1, probeZ + probed.sizeZ() - 1), probe.facing())));
                plotFailStrikes.get(key).remove(hutBlock);
                plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 24000);
                return;
            }
            if (strikeCount >= 2 && terraforms)
            {

                final FirstStrike early = firstStrikeRun.getOrDefault(key, Map.of()).get(hutBlock);
                final long busyRetry = early != null && early.until() > level.getGameTime() ? early.until() : level.getGameTime() + 6000;

                Collection<SiteSelector.Footprint> avoid = sacred;
                final SiteSelector.Footprint lastRect = lastTerraform.getOrDefault(key, Map.of()).get(hutBlock);
                if (lastRect != null)
                {
                    avoid = new ArrayList<>(avoid);
                    avoid.add(lastRect);
                }
                final SiteSelector.TerraformSite flatten = SiteSelector.findTerraformSite(level, colony, options, avoid, doorsteps, spreadFrom, frontier, searchedRadius, selfBuilding, false, fisher,
                  fisher ? band : Set.of());
                if (flatten != null && terraformer.begin(colony, level, hutBlock, step.name(), flatten))
                {

                    lastTerraform.computeIfAbsent(key, k -> new HashMap<>())
                      .put(hutBlock, withDoorstep(new SiteSelector.Footprint(flatten.minX(), flatten.minZ(), flatten.maxX(), flatten.maxZ()), flatten.site().facing()));
                    terraformAnchors.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, flatten.site());

                    refusedRescues.remove(hutBlock);

                    plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 120000);
                    return;
                }
                if (flatten != null)
                {

                    plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, busyRetry);
                    return;
                }
                final Map<Block, SiteSelector.Footprint> rects = lastTerraform.get(key);
                if (rects != null)
                {
                    rects.remove(hutBlock);
                }
                if (keystone && !earned && universityWidened.putIfAbsent(key, Boolean.TRUE) == null)
                {

                    ColonyAutopilot.LOGGER.info("[{}] no ground for the {} within {} blocks even for the terraformer — widening its search to the full {} blocks",
                      colony.getName(), step.name(), SiteSelector.KEYSTONE_RADIUS, AutopilotConfig.get(colony, AutopilotConfig.SEARCH_RADIUS_BLOCKS));
                    plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 6000);
                    return;
                }

                final SiteSelector.Placement south = options.get(Direction.SOUTH);
                final int strikes = fullParks.computeIfAbsent(key, k -> new HashMap<>()).merge(hutBlock, 1, Integer::sum);

                ColonyGrounds.get(level).setStructuresYield(colony.getID(), hutBlock, true);

                if (strikes >= 3 || coreHut)
                {

                    final SiteSelector.TerraformSite forced = SiteSelector.findTerraformSite(level, colony, options, avoid, doorsteps, spreadFrom, frontier, searchedRadius, selfBuilding, true, fisher,
                      Set.of());
                    if (forced != null && terraformer.begin(colony, level, hutBlock, step.name(), forced))
                    {
                        lastTerraform.computeIfAbsent(key, k -> new HashMap<>())
                          .put(hutBlock, withDoorstep(new SiteSelector.Footprint(forced.minX(), forced.minZ(), forced.maxX(), forced.maxZ()), forced.site().facing()));
                        terraformAnchors.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, forced.site());
                        fullParks.get(key).remove(hutBlock);
                        refusedRescues.remove(hutBlock);
                        plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 120000);
                        ColonyAutopilot.LOGGER.info("[{}] no natural or normally-terraformable plot for the {} within {} blocks — force-carving its footprint flat at colony level as a last resort; it WILL build there",
                          colony.getName(), step.name(), searchedRadius);
                        return;
                    }
                    if (forced != null)
                    {

                        plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, busyRetry);
                        return;
                    }

                    ColonyAutopilot.LOGGER.warn("[{}] the {} has no claimed, in-reach, building-clear ground the terraformer can cut within {} blocks to even force-carve (needs a ~{}x{} plot) — retrying, it will NOT be skipped",
                      colony.getName(), step.name(), searchedRadius, south.sizeX(), south.sizeZ());
                    plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 48000);
                    refusedRescues.remove(hutBlock);
                    return;
                }
                ColonyAutopilot.LOGGER.info("[{}] no buildable plot and no terraformable land for {} within {} blocks (needs a ~{}x{} plot) — parked for 40 game-minutes; strike {} of 3, the next search runs wider",
                  colony.getName(), step.name(), searchedRadius, south.sizeX(), south.sizeZ(), strikes);
                plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 48000);
                refusedRescues.remove(hutBlock);
                return;
            }

            ColonyAutopilot.LOGGER.info("[{}] no buildable plot for {} within {} blocks — later plan steps continue, retrying it in {} game-minutes",
              colony.getName(), step.name(), searchedRadius, coreHut ? 40 : 5);
            plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + (coreHut ? 48000 : 6000));
            if (strikeCount >= 2)
            {

                refusedRescues.remove(hutBlock);

                ColonyGrounds.get(level).setStructuresYield(colony.getID(), hutBlock, true);
            }
            else
            {

                final Set<Block> shortOf = plotShortage.computeIfAbsent(key, k -> new HashSet<>());
                if (shortOf.add(hutBlock) && shortOf.size() == PLOT_SHORTAGE_HUTS)
                {
                    ColonyAutopilot.LOGGER.info("[{}] the village is short of plots — {} huts in a row found none, and it stays so until the search finds one",
                      colony.getName(), PLOT_SHORTAGE_HUTS);
                }
            }
            if (!terraforms)
            {
                firstStrikeRun.remove(key);
            }
            else
            {

                final Map<Block, Long> parks = plotBlockedUntil.get(key);
                final Map<Block, FirstStrike> run = firstStrikeRun.computeIfAbsent(key, k -> new HashMap<>());
                run.put(hutBlock, new FirstStrike(step.name(), parks.get(hutBlock), false));
                final long now = level.getGameTime();
                int waiting = 0;
                for (final Map.Entry<Block, FirstStrike> struck : run.entrySet())
                {
                    final FirstStrike first = struck.getValue();
                    if (!first.carried() && first.until() > now && Long.valueOf(first.until()).equals(parks.get(struck.getKey())))
                    {
                        waiting++;
                    }
                }
                Block next = null;
                long nextEnd = Long.MAX_VALUE;
                boolean tied = false;
                for (final Map.Entry<Block, Long> park : parks.entrySet())
                {
                    if (park.getValue() > now && park.getValue() < nextEnd)
                    {
                        next = park.getKey();
                        nextEnd = park.getValue();
                        tied = false;
                    }
                    else if (park.getValue() == nextEnd)
                    {
                        tied = true;
                    }
                }
                final FirstStrike released = next == null || tied ? null : run.get(next);
                if (waiting >= PLOT_SHORTAGE_HUTS && released != null && released.until() == nextEnd && !terraformer.busy(colony))
                {
                    parks.remove(next);
                    ColonyAutopilot.LOGGER.info("[{}] {} huts in a row found no plot — the {} is searched again now, not in 5 game-minutes: its next refusal goes up the ladder (reach probe, then the terraformer)",
                      colony.getName(), waiting, released.name());
                }
            }
            return;
        }

        final Map<Block, FirstStrike> run = firstStrikeRun.get(key);
        if (run != null)
        {
            final Map<Block, Long> parks = plotBlockedUntil.getOrDefault(key, Map.of());
            run.entrySet().removeIf(struck -> struck.getKey() == hutBlock || parks.get(struck.getKey()) != null
              || struck.getValue().until() <= level.getGameTime());
            run.replaceAll((hut, struck) -> new FirstStrike(struck.name(), struck.until(), true));
        }

        if (!onPad)
        {
            final Set<Block> shortOf = plotShortage.remove(key);
            if (shortOf != null && shortOf.size() >= PLOT_SHORTAGE_HUTS)
            {
                ColonyAutopilot.LOGGER.info("[{}] the search found a plot for {} — the village is no longer short of plots",
                  colony.getName(), step.name());
            }
        }

        if (frontier != null && site.anchor().above(lift).distSqr(frontier) > SiteSelector.BUILDER_RANGE_SQ)
        {
            ColonyAutopilot.LOGGER.info("[{}] no plot for a builder's hut within reach of the stranded land at {} (the nearest, {}, is {} blocks short of covering it) — no hut raised; the stranded building {}",
              colony.getName(), frontier.toShortString(), site.anchor().toShortString(),
              (int) Math.ceil(Math.sqrt(site.anchor().above(lift).distSqr(frontier)) - Math.sqrt(SiteSelector.BUILDER_RANGE_SQ)), afterRefusedRescue(colony));

            if (onPad)
            {
                terraformAbandoned(key, hutBlock);
            }

            plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 24000);
            final Rescue unbridged = builderRescue.remove(key);
            if (unbridged != null)
            {
                rescueRefused.computeIfAbsent(key, k -> new HashSet<>()).add(unbridged.hut());
                if (importantHut(unbridged.hut()))
                {
                    plotFreed(key, unbridged.hut());
                }
            }
            return;
        }

        final SiteSelector.Result hutSite = lift == 0 ? site : new SiteSelector.Result(site.anchor().above(lift), site.facing());

        final int steps = (4 + site.facing().get2DDataValue() - frontRotation) % 4;
        final BlockState state = hutBlock.defaultBlockState().setValue(AbstractColonyBlock.FACING, Rotation.values()[steps].rotate(authoredFacing));
        level.setBlockAndUpdate(hutSite.anchor(), state);
        if (!(level.getBlockEntity(hutSite.anchor()) instanceof TileEntityColonyBuilding hut))
        {
            ColonyAutopilot.LOGGER.warn("[{}] hut block at {} produced no colony building tile entity", colony.getName(), hutSite.anchor());

            plotBlockedUntil.computeIfAbsent(key, k -> new HashMap<>()).put(hutBlock, level.getGameTime() + 6000);
            return;
        }

        hut.setStructurePack(pack);

        String fullPath = blueprint.getFilePath().toString().replace('\\', '/');
        final String packRoot = pack.getPath().toString().replace('\\', '/');
        final String packFolder = packRoot.substring(packRoot.lastIndexOf('/') + 1);
        final int rootIdx = fullPath.lastIndexOf(packFolder + "/");
        if (rootIdx >= 0)
        {
            fullPath = fullPath.substring(rootIdx + packFolder.length() + 1);
        }
        else if (fullPath.startsWith(packRoot + "/"))
        {
            fullPath = fullPath.substring(packRoot.length() + 1);
        }
        final String fileName = blueprint.getFileName();
        final String levelOnePath = fullPath + "/" + fileName.substring(0, fileName.length() - 1) + "1.blueprint";
        hut.setBlueprintPath(levelOnePath);

        final FakePlayer owner = ColonyAutopilot.fakeOwner(level, colony);
        state.getBlock().setPlacedBy(level, hutSite.anchor(), state, owner, new ItemStack(hutBlock));

        final SiteSelector.Placement chosen = options.get(site.facing());
        final int zeroX = site.anchor().getX() - chosen.offsetX();
        final int zeroZ = site.anchor().getZ() - chosen.offsetZ();
        final SiteSelector.Footprint plot = new SiteSelector.Footprint(zeroX, zeroZ,
          zeroX + chosen.sizeX() - 1, zeroZ + chosen.sizeZ() - 1);

        for (final SiteSelector.Footprint box : structures)
        {
            if (box.intersects(plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ()))
            {
                WorldStructures.renew(colony);
                break;
            }
        }

        final List<SiteSelector.Footprint> obstacles = new ArrayList<>(sacredGround(colony));
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!building.getPosition().equals(hutSite.anchor()))
            {
                obstacles.add(SiteSelector.boxOf(building));
            }
        }

        reserved.put(hutSite.anchor(), plot);

        ColonyGrounds.get(level).setAnchorOffset(colony.getID(), hutSite.anchor(), anchorOffset);
        ColonyGrounds.get(level).setFront(colony.getID(), hutSite.anchor(), site.facing());

        final Map<Block, Integer> strikesForColony = plotFailStrikes.get(key);
        if (strikesForColony != null && !onPad)
        {
            strikesForColony.remove(hutBlock);
        }
        final Map<Block, Integer> parksForColony = fullParks.get(key);
        if (parksForColony != null)
        {
            parksForColony.remove(hutBlock);
        }
        final Set<Block> yieldedForColony = structuresYielded.get(key);
        if (yieldedForColony != null)
        {
            yieldedForColony.remove(hutBlock);
        }
        ColonyGrounds.get(level).setStructuresYield(colony.getID(), hutBlock, false);
        if (selfBuilding)
        {
            rescueRefused.remove(key);
            final Rescue bridged = builderRescue.remove(key);
            if (bridged != null && importantHut(bridged.hut()) && frontier != null)
            {

                builderRescue.put(key, bridged);
                plotBlockedUntil.putIfAbsent(key, new HashMap<>());
                plotBlockedUntil.get(key).put(bridged.hut(), level.getGameTime() + 24000);
            }
            else if (bridged != null && importantHut(bridged.hut()))
            {
                plotFreed(key, bridged.hut());
            }
        }
        else
        {

            final Rescue held = builderRescue.get(key);
            if (held != null && held.stepName().equals(step.name()))
            {
                builderRescue.remove(key);
            }
            final Set<Block> refusedForColony = rescueRefused.get(key);
            if (refusedForColony != null)
            {
                refusedForColony.remove(hutBlock);
            }
        }
        if (keystone)
        {
            universityWidened.remove(key);
        }
        final Map<Block, SiteSelector.Footprint> terraformRects = lastTerraform.get(key);
        if (terraformRects != null)
        {
            terraformRects.remove(hutBlock);
        }
        final Map<Block, SiteSelector.Result> placedAnchors = terraformAnchors.get(key);
        if (placedAnchors != null)
        {
            placedAnchors.remove(hutBlock);
        }
        lastPlacement.put(key, level.getGameTime());
        BuilderCap.started(colony, level, BuilderCap.Kind.PLACEMENT);
        final Direction towardCentre = SiteSelector.facingToward(colony.getCenter(), hutSite.anchor().getX(), hutSite.anchor().getZ());
        ColonyAutopilot.LOGGER.info("[{}] placed {} at {}, its {} {}{}", colony.getName(), step.name(), hutSite.anchor().toShortString(),
          front != null ? "entrance" : "hut face",
          site.facing() == towardCentre ? "toward the centre (" + site.facing() + ")"
            : "to the " + site.facing() + " (the centre lies " + towardCentre + " — no rotation toward it fit the plot)",
          lift == 0 ? "" : " (" + (lift > 0 ? "lifted " + lift : "sunk " + -lift) + " for the style's ground line)");

        int evicted = 0;
        final Map<BlockPos, List<BlockPos>> roads = roadsOf(colony, level);
        for (final Map.Entry<BlockPos, List<BlockPos>> road : roads.entrySet())
        {
            final List<BlockPos> inside = new ArrayList<>();
            for (final BlockPos block : road.getValue())
            {
                if (plot.intersects(block.getX(), block.getZ(), block.getX(), block.getZ()))
                {
                    inside.add(block);
                }
            }
            if (!inside.isEmpty())
            {

                final Set<BlockPos> kept = new HashSet<>(VillagePaths.erase(level, inside, Set.of(), Set.of(), null));
                final Set<Long> laidInWater = ColonyGrounds.get(level).roadWater(colony.getID());
                final List<BlockPos> lifted = new ArrayList<>();
                for (final BlockPos block : inside)
                {
                    if (!kept.contains(block))
                    {
                        lifted.add(block);
                        laidInWater.remove(block.asLong());
                    }
                }
                road.getValue().removeAll(lifted);
                evicted += lifted.size();

                requeueDependents(colony, roads, road.getKey(), lifted);
                final IBuilding roadOwner = colony.getServerBuildingManager().getBuilding(road.getKey());
                if (roadOwner != null && roadOwner.getBuildingLevel() > 0)
                {
                    pendingRoadReconnects.add(roadOwner);
                }
            }
        }
        if (evicted > 0)
        {
            bootChanged.add(key);
            saveRoads(colony, level);
        }
        for (int ex = plot.minX() - 1; ex <= plot.maxX() + 1; ex++)
        {
            for (int ez = plot.minZ() - 1; ez <= plot.maxZ() + 1; ez++)
            {
                if (!WorldUtil.isChunkLoaded(level, ex >> 4, ez >> 4))
                {
                    continue;
                }

                final BlockPos cap = new BlockPos(ex, SiteSelector.groundHeight(level, ex, ez) - 1, ez);
                if (VillagePaths.dismantleLamp(level, cap, false))
                {
                    evicted += 4;
                }
            }
        }
        if (evicted > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] cleared {} blocks of the village's own roads and lamps from the {}'s plot — nothing stands where the walls will",
              colony.getName(), evicted, step.name());
        }

        if (AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED))
        {

            final VillageGrounds.SkirtResult skirt = VillageGrounds.smoothSkirt(level, plot, site.anchor().getY() - 1,
              skirtKeepOut(level, colony, plot, obstacles), roadBand(colony, level));
            if (skirt.total() > 0)
            {
                ColonyAutopilot.LOGGER.debug("[{}] smoothed {} blocks around the {}'s plot — walkable from the first day",
                  colony.getName(), skirt.total(), step.name());
            }
        }

        if (AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS) && !liftedRoads.contains(key))
        {

            final BlockPos doorColumn = front != null ? site.anchor().offset(front.doorOffset().rotate(Rotation.values()[steps])) : site.anchor();
            final int toFrontEdge = switch (site.facing())
            {
                case NORTH -> doorColumn.getZ() - zeroZ;
                case SOUTH -> zeroZ + chosen.sizeZ() - 1 - doorColumn.getZ();
                case WEST -> doorColumn.getX() - zeroX;
                case EAST -> zeroX + chosen.sizeX() - 1 - doorColumn.getX();
                default -> Math.max(chosen.sizeX(), chosen.sizeZ()) / 2;
            };
            final BlockPos pathStart = new BlockPos(doorColumn.getX(), site.anchor().getY(), doorColumn.getZ()).relative(site.facing(), toFrontEdge + 1);
            final IBuilding hall = colony.getServerBuildingManager().getTownHall();
            final BlockPos centre = colony.getCenter();
            final SiteSelector.Footprint hallBox = hall != null ? SiteSelector.boxOf(hall) : null;
            carveDoorstepRoad(colony, level, hutSite.anchor(), pathStart, site.facing(), plot, centre,
              SiteSelector.yardTop(level, ColonyGrounds.get(level), colony.getID(), centre, hallBox), hallBox, obstacles, VillagePaths.ARRIVAL, false);
        }

        final List<SiteSelector.Footprint> grounds = new ArrayList<>(obstacles);
        if (landHeld != null && landHeld.hut() != hutBlock)
        {
            grounds.add(landHeld.land());
        }
        if (hutBlock == ModBlocks.blockHutFisherman)
        {

            final List<SiteSelector.Footprint> pondObstacles = new ArrayList<>(grounds);
            pondObstacles.addAll(doorsteps);
            FishingPond.ensure(level, colony, hutSite.anchor(), plot, site.facing(), pondObstacles, roadBand(colony, level));
        }

        final IBuilding placedFarm = hutBlock == ModBlocks.blockHutFarmer && AutopilotConfig.get(colony, AutopilotConfig.FARM_FIELDS)
                                       ? colony.getServerBuildingManager().getBuilding(hutSite.anchor()) : null;
        if (placedFarm != null)
        {

            final List<SiteSelector.Footprint> fieldObstacles = new ArrayList<>(grounds);
            fieldObstacles.add(plot);
            fieldObstacles.addAll(doorsteps);
            FarmFields.layNaturalField(level, colony, placedFarm, fieldObstacles, roadBand(colony, level));
        }

        if (hutBlock == ModBlocks.blockHutLumberjack)
        {
            ForesterGrove.ensure(level, colony, site.anchor(), plot, grounds);
        }

        if (hutBlock == ModBlocks.blockHutUniversity && AutopilotConfig.get(colony, AutopilotConfig.INSTANT_UNIVERSITY)
              && AutopilotConfig.buildsPlacedHuts(colony))
        {
            grantFirstLevel(colony, level, hutSite.anchor(), pack, levelOnePath, RotationMirror.of(Rotation.values()[steps], Mirror.NONE));
        }
    }

    private void learnAnchorOffset(final IColony colony, final ServerLevel level, final IBuilding building)
    {
        final BlockPos anchor = building.getPosition();
        final GlobalPos here = GlobalPos.of(level.dimension(), anchor);
        final long now = level.getGameTime();
        final Long retryAt = offsetLearning.get(here);
        if (retryAt != null && now < retryAt)
        {
            return;
        }
        offsetLearning.put(here, now + 6000L);
        final int colonyId = colony.getID();

        final String blueprintPath = DamageWatch.pathForLevel(building.getBlueprintPath(), Math.max(1, building.getBuildingLevel()));

        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(

          StructurePacks.getBlueprintFuture(building.getStructurePack(), blueprintPath, true, level.registryAccess())
            .exceptionally(failure -> null), level, blueprint -> {
            try
            {
            if (blueprint == null)
            {
                final int misses = offsetMisses.merge(here, 1, Integer::sum);
                if (misses >= 3)
                {

                    offsetLearning.put(here, Long.MAX_VALUE);
                    apronOwed.remove(building);
                    ColonyAutopilot.LOGGER.warn("[{}] the {}'s blueprint cannot be read (pack '{}', path '{}') — its ground line stays unknown this session, so no doorstep road is carved to it and no apron is graded around it: a road on a guessed line can miss the door (fix the pack or path and restart)",
                      colony.getName(), SiteSelector.plainName(building), building.getStructurePack(), blueprintPath);
                    if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutTownHall)
                    {
                        townHallAproned.put(ColonyAutopilot.colonyKey(colony), anchor);
                    }
                }
                else
                {
                    ColonyAutopilot.LOGGER.debug("[{}] could not read the {}'s blueprint (pack '{}', path '{}') — its ground line stays unknown; retrying in five game-minutes",
                      colony.getName(), SiteSelector.plainName(building), building.getStructurePack(), blueprintPath);
                }
                return;
            }
            offsetLearning.remove(here);
            offsetMisses.remove(here);

            final int offset = BlueprintTagUtils.getGroundAnchorOffset(blueprint, 1);
            ColonyGrounds.get(level).setAnchorOffset(colonyId, anchor, offset);
            if (offset != 1)
            {
                ColonyAutopilot.LOGGER.info("[{}] the {} stands {} block(s) above its ground line (its style's blueprint says so) — grading and doorstep work use that line from now on",
                  colony.getName(), SiteSelector.plainName(building), offset);
            }

            if ((AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS) || apronOwed.contains(building)) && building.getBuildingLevel() > 0)
            {
                pendingRoadReconnects.add(building);
            }
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] learning the {}'s ground line failed", colony.getName(), SiteSelector.plainName(building), e);
            }
        }));
    }

    private void grantFirstLevel(final IColony colony, final ServerLevel level, final BlockPos anchor,
      final StructurePackMeta pack, final String blueprintPath, final RotationMirror rotationMirror)
    {
        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(pack.getName(), blueprintPath, level.registryAccess()), level, loaded -> {

            try
            {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(anchor);
            if (loaded == null || building == null || building.getBuildingLevel() > 0)
            {
                return;
            }

            building.setRotationMirror(rotationMirror);
            building.setBuildingLevel(1);
            building.onUpgradeComplete(loaded, 1);

            for (final WorkOrderBuilding order : List.copyOf(colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class)))
            {
                if (order.getLocation().equals(anchor))
                {
                    colony.getWorkManager().removeWorkOrder(order.getID());
                }
            }
            if (upgrades != null)
            {
                upgrades.scheduleRepair(colony, building.getID());
            }

            pendingRoadReconnects.add(building);
            ColonyAutopilot.LOGGER.info("[{}] the {} opened at level 1 — research starts now, and the builders raise its walls around the scholars",
              colony.getName(), SiteSelector.plainName(building));
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] opening the university at level 1 failed at {}", colony.getName(), anchor.toShortString(), e);
            }
        }));
    }

    List<SiteSelector.Footprint> doorstepZones(final IColony colony, final ServerLevel level)
    {
        final List<SiteSelector.Footprint> zones = new ArrayList<>();
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final Map<BlockPos, SiteSelector.Footprint> reserved = reservedPlots.getOrDefault(ColonyAutopilot.colonyKey(colony), Map.of());
        final Map<BlockPos, List<BlockPos>> roads = roadsOf(colony, level);
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final BlockPos anchor = building.getPosition();
            final SiteSelector.Footprint box = reserved.containsKey(anchor) ? reserved.get(anchor) : SiteSelector.boxOf(building);

            Direction front = null;
            final List<BlockPos> road = roads.get(anchor);
            if (road != null && !road.isEmpty())
            {
                final BlockPos first = road.get(0);
                final boolean withinX = first.getX() >= box.minX() && first.getX() <= box.maxX();
                final boolean withinZ = first.getZ() >= box.minZ() && first.getZ() <= box.maxZ();
                if (withinX && first.getZ() >= box.minZ() - 2 && first.getZ() < box.minZ())
                {
                    front = Direction.NORTH;
                }
                else if (withinX && first.getZ() <= box.maxZ() + 2 && first.getZ() > box.maxZ())
                {
                    front = Direction.SOUTH;
                }
                else if (withinZ && first.getX() >= box.minX() - 2 && first.getX() < box.minX())
                {
                    front = Direction.WEST;
                }
                else if (withinZ && first.getX() <= box.maxX() + 2 && first.getX() > box.maxX())
                {
                    front = Direction.EAST;
                }
            }
            if (front == null)
            {
                final BlockState hut = WorldUtil.isChunkLoaded(level, anchor.getX() >> 4, anchor.getZ() >> 4) ? level.getBlockState(anchor) : null;
                front = grounds.front(colony.getID(), anchor, hut != null && hut.hasProperty(AbstractBlockHut.FACING) ? hut.getValue(AbstractBlockHut.FACING) : null);
            }
            if (front == null)
            {
                continue;
            }
            zones.add(switch (front)
            {
                case NORTH -> new SiteSelector.Footprint(box.minX(), box.minZ() - SiteSelector.DOORSTEP_STRIP, box.maxX(), box.minZ() - 1);
                case SOUTH -> new SiteSelector.Footprint(box.minX(), box.maxZ() + 1, box.maxX(), box.maxZ() + SiteSelector.DOORSTEP_STRIP);
                case WEST -> new SiteSelector.Footprint(box.minX() - SiteSelector.DOORSTEP_STRIP, box.minZ(), box.minX() - 1, box.maxZ());
                default -> new SiteSelector.Footprint(box.maxX() + 1, box.minZ(), box.maxX() + SiteSelector.DOORSTEP_STRIP, box.maxZ());
            });
        }
        return zones;
    }

    private static SiteSelector.Footprint withDoorstep(final SiteSelector.Footprint plot, final Direction facing)
    {
        return switch (facing)
        {
            case NORTH -> new SiteSelector.Footprint(plot.minX(), plot.minZ() - SiteSelector.DOORSTEP_STRIP, plot.maxX(), plot.maxZ());
            case SOUTH -> new SiteSelector.Footprint(plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ() + SiteSelector.DOORSTEP_STRIP);
            case WEST -> new SiteSelector.Footprint(plot.minX() - SiteSelector.DOORSTEP_STRIP, plot.minZ(), plot.maxX(), plot.maxZ());
            default -> new SiteSelector.Footprint(plot.minX(), plot.minZ(), plot.maxX() + SiteSelector.DOORSTEP_STRIP, plot.maxZ());
        };
    }

    Set<Long> roadBand(final IColony colony, final ServerLevel level)
    {
        final Set<Long> band = new HashSet<>();
        for (final List<BlockPos> road : roadsOf(colony, level).values())
        {
            for (final BlockPos block : road)
            {
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        band.add(BlockPos.asLong(block.getX() + dx, 0, block.getZ() + dz));
                    }
                }
            }
        }
        return band;
    }

    private int queueRoads(final IColony colony)
    {
        int queued = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() > 0)
            {
                pendingRoadReconnects.add(building);
                queued++;
            }
        }
        return queued;
    }

    private void drainRoadReconnects()
    {

        if (!reconnectLater.isEmpty())
        {
            pendingRoadReconnects.addAll(reconnectLater);
            reconnectLater.clear();
        }
        final Iterator<IBuilding> queue = pendingRoadReconnects.iterator();
        int budget = 2;
        while (queue.hasNext() && budget-- > 0)
        {
            final IBuilding building = queue.next();
            queue.remove();

            if (building.getColony() == null
                  || building.getColony().getServerBuildingManager().getBuilding(building.getPosition()) != building
                  || IColonyManager.getInstance().getColonyByWorld(building.getColony().getID(), building.getColony().getWorld()) != building.getColony())
            {
                apronOwed.remove(building);
                continue;
            }
            lastCarveSpent = 0;
            try
            {

                if (apronOwed.contains(building) && apronFirstBuild(building))
                {
                    apronOwed.remove(building);
                }
                if (AutopilotConfig.get(building.getColony(), AutopilotConfig.BUILD_PATHS) && !liftedRoads.contains(ColonyAutopilot.colonyKey(building.getColony())))
                {
                    reconnectRoad(building);
                }
                outfitWorkplace(building);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Road reconnect / workplace outfit failed for {}", building.getID(), e);
            }

            if (lastCarveSpent > HEAVY_CARVE_EXPANSIONS)
            {
                break;
            }
        }
    }

    private static List<SiteSelector.Footprint> skirtKeepOut(final ServerLevel level, final IColony colony,
      final SiteSelector.Footprint plot, final List<SiteSelector.Footprint> boxes)
    {
        final List<SiteSelector.Footprint> keepOut = new ArrayList<>(boxes);
        keepOut.addAll(SiteSelector.foreignClaims(level, colony, plot.minX() - 40, plot.minZ() - 40, plot.maxX() + 40, plot.maxZ() + 40));
        return keepOut;
    }

    private boolean apronFirstBuild(final IBuilding building)
    {
        final IColony colony = building.getColony();
        if (colony == null || !AutopilotConfig.get(colony, AutopilotConfig.TERRAFORM_ENABLED) || !(colony.getWorld() instanceof ServerLevel level))
        {
            return true;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        if (!grounds.hasAnchorOffset(colony.getID(), building.getPosition()))
        {
            learnAnchorOffset(colony, level, building);
            return false;
        }
        final List<SiteSelector.Footprint> others = new ArrayList<>(sacredGround(colony));
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (other != building)
            {
                others.add(SiteSelector.boxOf(other));
            }
        }
        final int ground = building.getPosition().getY() - grounds.anchorOffset(colony.getID(), building.getPosition(), 1);
        final VillageGrounds.SkirtResult skirt = VillageGrounds.smoothSkirt(level, SiteSelector.boxOf(building), ground,
          skirtKeepOut(level, colony, SiteSelector.boxOf(building), others), roadBand(colony, level));
        if (skirt.total() > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] smoothed {} blocks around the {} you placed — a hand-placed hut gets its apron once its first level stands",
              colony.getName(), skirt.total(), SiteSelector.plainName(building));
        }
        return true;
    }

    private void outfitWorkplace(final IBuilding building)
    {
        if (building.getBuildingLevel() <= 0 || building.getColony() == null
              || !(building.getColony().getWorld() instanceof ServerLevel level)
              || !WorldUtil.isChunkLoaded(level, building.getPosition().getX() >> 4, building.getPosition().getZ() >> 4))
        {
            return;
        }
        final IColony colony = building.getColony();
        if (AutopilotConfig.get(colony, AutopilotConfig.FARM_FIELDS) && building instanceof BuildingFarmer)
        {
            FarmFields.ensure(level, colony, building, obstaclesAround(building), roadBand(colony, level));
        }
        else if (AutopilotConfig.get(colony, AutopilotConfig.FARM_FIELDS) && building instanceof BuildingPlantation)
        {
            PlantationFields.ensure(level, colony, building, obstaclesAround(building), roadBand(colony, level));
        }
        else if (AutopilotConfig.get(colony, AutopilotConfig.STARTER_ANIMALS) && Paddocks.keepsAnimals(building))
        {
            Paddocks.ensure(level, colony, building);
        }
        else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGuardTower)
        {
            balanceGuardTower(colony, building);
        }
        else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBarracksTower && guardStaffingLive(colony) && !handHired(building))
        {

            setAutopilotPosts(building);
        }
        else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFisherman)
        {

            final BlockState hutState = level.getBlockState(building.getPosition());
            if (hutState.hasProperty(AbstractBlockHut.FACING))
            {
                FishingPond.ensure(level, colony, building.getPosition(), SiteSelector.boxOf(building),
                  ColonyGrounds.get(level).front(colony.getID(), building.getPosition(), hutState.getValue(AbstractBlockHut.FACING)), obstaclesAround(building),
                  roadBand(colony, level));
            }
        }
        else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutSifter)
        {

            final MinimumStockModule stock = building.getModule(MinimumStockModule.class);
            if (stock != null)
            {
                final List<ItemStack> supplies = List.of(new ItemStack(ModItems.sifterMeshString), new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL));
                final boolean held = supplies.stream().allMatch(stock::isStocked);
                ProvidenceSweep.addStockLine(building, stock, supplies.get(0), 1);
                ProvidenceSweep.addStockLine(building, stock, supplies.get(1), 2);
                ProvidenceSweep.addStockLine(building, stock, supplies.get(2), 2);

                if (!held && supplies.stream().allMatch(stock::isStocked))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the sifter's supplies are on standing order — sand and gravel, and a mesh for the colony's Fletcher to make", colony.getName());
                }
            }
        }
        else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutNetherWorker
                   && building instanceof BuildingNetherWorker nether && nether.getPortalLocation() == null)
        {

            ColonyAutopilot.LOGGER.warn("[{}] the nether worker's hut has NO portal tag in its blueprint — he will never travel; the structure pack needs a 'portal' tag",
              colony.getName());
        }

        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutLumberjack)
        {

            final MinimumStockModule stock = building.getModule(MinimumStockModule.class);
            if (stock != null)
            {
                ProvidenceSweep.addStockLine(building, stock, new ItemStack(Items.OAK_SAPLING), 1);
            }

            ForesterGrove.ensure(level, colony, building.getPosition(), SiteSelector.boxOf(building), obstaclesAround(building));
        }
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutHospital)
        {

            final MinimumStockModule stock = building.getModule(MinimumStockModule.class);
            if (stock != null)
            {
                final List<ItemStack> cures = List.of(new ItemStack(Items.POPPY), new ItemStack(Items.DANDELION), new ItemStack(Items.HONEY_BOTTLE),
                  new ItemStack(Items.GOLDEN_APPLE));
                final boolean held = cures.stream().allMatch(stock::isStocked);
                for (final ItemStack cure : cures)
                {
                    ProvidenceSweep.addStockLine(building, stock, cure, 1);
                }

                if (!held && cures.stream().allMatch(stock::isStocked))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the hospital's cure cabinet is on standing order — poppy, dandelion, honey bottle and golden apple wait before anyone falls ill (carrot, potato and kelp, the other cure items, come from the fields)",
                      colony.getName());
                }
            }
        }
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGraveyard)
        {

            if (AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES))
            {

                final com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule crew =
                  building.getModule(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class);
                if (crew != null)
                {
                    for (final com.minecolonies.api.colony.ICitizenData undertaker : crew.getAssignedCitizen())
                    {
                        crew.removeCitizen(undertaker);
                    }
                    crew.setHiringMode(com.minecolonies.api.colony.buildings.HiringMode.LOCKED);
                }
            }
            else
            {

                final com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule crew =
                  building.getModule(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class);
                if (crew != null && crew.getHiringMode() == com.minecolonies.api.colony.buildings.HiringMode.LOCKED)
                {
                    crew.setHiringMode(com.minecolonies.api.colony.buildings.HiringMode.DEFAULT);
                }
            }
        }
        if (building instanceof BuildingComposter composter && composter.getBarrels().isEmpty())
        {

            ColonyAutopilot.LOGGER.warn("[{}] the composter's hut has NO barrels in its blueprint — he can never compost; the structure pack needs blockBarrel positions",
              colony.getName());
        }
        if (building instanceof BuildingConcreteMixer mixer && mixer.getMaxConcretePlaced() == 0)
        {

            ColonyAutopilot.LOGGER.warn("[{}] the concrete mixer's hut has NO flowing-water channel in its blueprint — powder can never harden; the structure pack needs water positions",
              colony.getName());
        }
        if (building instanceof BuildingAlchemist alchemist)
        {

            if (alchemist.getAllSoilPositions().isEmpty())
            {
                ColonyAutopilot.LOGGER.warn("[{}] the alchemist's hut has NO soul sand plots in its blueprint — netherwart can never grow; the structure pack needs soul sand positions",
                  colony.getName());
            }
            if (alchemist.getAllBrewingStandPositions().isEmpty())
            {
                ColonyAutopilot.LOGGER.warn("[{}] the alchemist's hut has NO brewing stands in its blueprint — potions can never brew; the structure pack needs brewing stands",
                  colony.getName());
            }
        }
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutEnchanter)
        {

            final MinimumStockModule stock = building.getModule(MinimumStockModule.class);
            if (stock != null)
            {
                ProvidenceSweep.addStockLine(building, stock, new ItemStack(Items.BOOK), 1);
            }
        }

        enrollEnchanterStations(colony, building);
    }

    private static void enrollEnchanterStations(final IColony colony, final IBuilding completed)
    {
        if (!AutopilotConfig.get(colony, AutopilotConfig.ENCHANTER_STATIONS))
        {
            return;
        }
        if (completed.getBuildingType().getBuildingBlock() == ModBlocks.blockHutEnchanter)
        {
            final EnchanterStationsModule stations = completed.getModule(EnchanterStationsModule.class);
            if (stations != null)
            {
                final Set<BlockPos> held = stations.getBuildingsToGatherFrom();
                int enrolled = 0;
                for (final IBuilding colleague : colony.getServerBuildingManager().getBuildings().values())
                {
                    if (stationEligible(colleague) && !held.contains(colleague.getID()))
                    {
                        stations.addWorker(colleague.getID());
                        enrolled++;
                    }
                }
                if (enrolled > 0)
                {
                    ColonyAutopilot.LOGGER.info("[{}] the enchanter now draws experience from {} more workplace(s)", colony.getName(), enrolled);
                }
            }
            return;
        }
        if (!stationEligible(completed))
        {
            return;
        }
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (other.getBuildingType().getBuildingBlock() == ModBlocks.blockHutEnchanter && other.getBuildingLevel() > 0)
            {
                final EnchanterStationsModule stations = other.getModule(EnchanterStationsModule.class);
                if (stations != null && !stations.getBuildingsToGatherFrom().contains(completed.getID()))
                {
                    stations.addWorker(completed.getID());
                }
            }
        }
    }

    private static boolean stationEligible(final IBuilding building)
    {
        return building.getBuildingLevel() > 0
              && building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutEnchanter
              && !(building instanceof BuildingBuilder)
              && !(building instanceof AbstractBuildingGuards)
              && building.getModule(WorkerBuildingModule.class) != null;
    }

    private void tendEnchanterStations(final IColony colony, final IBuilding enchanter)
    {
        final EnchanterStationsModule stations = enchanter.getModule(EnchanterStationsModule.class);
        if (stations == null)
        {
            return;
        }
        final Set<BlockPos> assigned = stations.getBuildingsToGatherFrom();
        int enrolled = 0;
        for (final IBuilding colleague : colony.getServerBuildingManager().getBuildings().values())
        {
            if (stationEligible(colleague)
                  && !assigned.contains(colleague.getID())
                  && !colleague.getAllAssignedCitizen().isEmpty())
            {
                stations.addWorker(colleague.getID());
                enrolled++;
            }
        }
        if (enrolled > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the enchanter's station list had lost {} workplaces — re-enrolled them",
              colony.getName(), enrolled);
        }
    }

    private void balanceGuardTower(final IColony colony, final IBuilding tower)
    {
        final JobEntry standing = decidedGuardType(tower);
        if (standing != null)
        {

            if (tower.getAllAssignedCitizen().isEmpty() && !onAuto(tower, standing))
            {
                postGuardTower(colony, tower, standing);
            }
            return;
        }

        JobEntry kept = null;
        JobEntry onDuty = null;
        int openStamped = 0;
        boolean anyClosed = false;
        boolean newerClosed = false;
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            final boolean everyVersion = module.getJobEntry() == ModJobs.knight.get() || module.getJobEntry() == ModJobs.archer.get();
            if (module.getHiringMode() == HiringMode.MANUAL)
            {
                anyClosed = true;
                newerClosed |= !everyVersion;
            }
            else if (everyVersion)
            {
                openStamped++;
                kept = module.getJobEntry();
            }
            else if (!module.getAssignedCitizen().isEmpty())
            {
                onDuty = module.getJobEntry();
            }
        }

        if (openStamped == 0 && onDuty != null)
        {
            kept = onDuty;
            openStamped = 1;
            newerClosed = false;
        }
        if (anyClosed && openStamped == 1 && !newerClosed)
        {
            for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
            {
                if (module.getJobEntry() != kept)
                {
                    module.setHiringMode(HiringMode.MANUAL);
                }
            }
            tower.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] the guard tower at {} gained guard posts in an update — closed them again around its standing {}",
              colony.getName(), tower.getPosition().toShortString(), kept.getKey().getPath());
            return;
        }
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getHiringMode() != HiringMode.DEFAULT)
            {
                return;
            }
        }
        postGuardTower(colony, tower, null);
    }

    private void postGuardTower(final IColony colony, final IBuilding tower, final JobEntry standing)
    {

        final GuardCensus stamped = new GuardCensus();
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (other != tower && other.getBuildingLevel() > 0
                  && other.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGuardTower)
            {
                stamped.add(decidedGuardType(other), 1, true);
            }
        }
        final JobEntry posted = nextFighter(colony, tower, stamped);
        if (posted == standing)
        {
            return;
        }

        final HiringMode open = guardStaffingLive(colony) ? HiringMode.LOCKED : HiringMode.DEFAULT;
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            module.setHiringMode(module.getJobEntry() == posted ? open : HiringMode.MANUAL);
        }
        tower.markDirty();
        ColonyAutopilot.LOGGER.info("[{}] the {}guard tower at {} posts a {} ({} melee and {} ranged towers stand already)",
          colony.getName(), standing == null ? "" : "empty " + standing.getKey().getPath() + "'s ", tower.getPosition().toShortString(),
          posted.getKey().getPath(), stamped.melee(), stamped.ranged());
    }

    private static final ResourceLocation HUSCARL_JOB = ResourceLocation.fromNamespaceAndPath("minecolonies", "huscarl");
    private static final ResourceLocation MARKSMAN_JOB = ResourceLocation.fromNamespaceAndPath("minecolonies", "marksman");
    static final ResourceLocation HUSCARL_RESEARCH = ResourceLocation.fromNamespaceAndPath("minecolonies", "effects/huscarl");
    static final ResourceLocation MARKSMAN_RESEARCH = ResourceLocation.fromNamespaceAndPath("minecolonies", "effects/marksman");

    private static final int KNIGHTS_PER_HUSCARL = 2;

    private static JobEntry researchedPost(final IColony colony, final IBuilding post, final ResourceLocation job, final ResourceLocation research)
    {
        if (colony.getResearchManager().getResearchEffects().getEffectStrength(research) <= 0)
        {
            return null;
        }
        for (final GuardBuildingModule module : post.getModulesByType(GuardBuildingModule.class))
        {
            if (job.equals(module.getJobEntry().getKey()))
            {
                return module.getJobEntry();
            }
        }
        return null;
    }

    private static JobEntry nextRanged(final IColony colony, final IBuilding post)
    {
        final JobEntry marksman = researchedPost(colony, post, MARKSMAN_JOB, MARKSMAN_RESEARCH);
        return marksman != null ? marksman : ModJobs.archer.get();
    }

    private static JobEntry nextFighter(final IColony colony, final IBuilding post, final GuardCensus count)
    {
        if (count.melee() >= AutopilotConfig.get(colony, AutopilotConfig.KNIGHTS_PER_ARCHER) * (count.ranged() + 1))
        {
            return nextRanged(colony, post);
        }
        final JobEntry huscarl = researchedPost(colony, post, HUSCARL_JOB, HUSCARL_RESEARCH);
        return huscarl != null && count.knights >= KNIGHTS_PER_HUSCARL * (count.huscarls + 1) ? huscarl : ModJobs.knight.get();
    }

    private void staffGuardPosts(final IColony colony)
    {
        final List<IBuilding> towers = new ArrayList<>();
        final List<IBuilding> billets = new ArrayList<>();
        final List<IBuilding> stables = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {

            if (building.getBuildingLevel() <= 0 || colony.getWorld() == null
                  || !WorldUtil.isChunkLoaded(colony.getWorld(), building.getPosition().getX() >> 4, building.getPosition().getZ() >> 4))
            {
                continue;
            }
            final Block block = building.getBuildingType().getBuildingBlock();
            if (block == ModBlocks.blockHutGuardTower)
            {

                final boolean firstSight = guardTowersSeen.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashSet<>())
                                             .add(building.getPosition());
                if (decidedGuardType(building) != null)
                {
                    holdStampedPost(colony, building);
                    if (building.getAllAssignedCitizen().isEmpty())
                    {
                        balanceGuardTower(colony, building);
                    }
                    towers.add(building);
                }
                else if (!(firstSight && repairStampedTower(colony, building)))
                {

                    releaseHeldPosts(colony, building);
                }
            }
            else if (block == ModBlocks.blockHutBarracksTower)
            {

                if (handHired(building))
                {
                    reserveForPlayer(colony, building);
                    continue;
                }
                setAutopilotPosts(building);
                billets.add(building);
            }
            else if (block == ModBlocks.blockHutStable)
            {

                final int minutes = 2;
                final IntSetting wait = building.getSetting(BuildingStable.PATROL_INTERVAL);
                if (wait != null && wait.getValue() == wait.getDefault() && wait.getValue() != minutes
                      && AutopilotConfig.live(colony, AutopilotConfig.CAVALRY_PATROLS))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the riders of the stable at {} wait {} minutes between two outings now, not MineColonies' {} — the Stable's own patrol interval, and another value set there is kept",
                      colony.getName(), building.getPosition().toShortString(), minutes, wait.getValue());
                    wait.setValue(minutes);
                    building.markDirty();
                }

                if (building instanceof BuildingStable stable)
                {
                    Paddocks.fitBarding(colony, stable);
                }
                stables.add(building);
            }
        }
        boolean hired;
        int sweeps = 0;
        do
        {
            hired = false;
            for (final IBuilding tower : towers)
            {
                final JobEntry decided = decidedGuardType(tower);
                if (decided != null && tower.getAllAssignedCitizen().isEmpty())
                {
                    hired |= staffGuardPost(colony, tower, Set.of(decided));
                }
            }
            billets.sort(Comparator.comparingInt(billet -> billet.getAllAssignedCitizen().size()));
            for (final IBuilding billet : billets)
            {
                if (billet.getAllAssignedCitizen().size() < billet.getBuildingLevel())
                {
                    hired |= seatBillet(colony, billet);
                }
            }
            for (final IBuilding stable : stables)
            {

                if (!stable.getModule(BuildingModules.STABLEMASTER_WORK).getAssignedCitizen().isEmpty()
                      && stable.getModule(BuildingModules.CAVALRY_STABLE_WORK).getAssignedCitizen().size()
                           < colony.getAnimalManager().getAnimalsOfClassByHome(CavalryHorseEntity.class, stable).stream()
                               .filter(data -> data.getManagedAnimal().orElse(null) instanceof CavalryHorseEntity mount && mount.isReadyForCombat()).count())
                {
                    hired |= staffGuardPost(colony, stable, Set.of(ModJobs.cavalry.get()));
                }
            }
        }
        while (hired && ++sweeps < 32);
    }

    private static void holdStampedPost(final IColony colony, final IBuilding tower)
    {
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getHiringMode() == HiringMode.DEFAULT)
            {
                module.setHiringMode(HiringMode.LOCKED);
                tower.markDirty();
                ColonyAutopilot.LOGGER.info("[{}] the doctrine holds the {} post of the guard tower at {} now — MineColonies' own hiring stands aside",
                  colony.getName(), module.getJobEntry().getKey().getPath(), tower.getPosition().toShortString());
            }
        }
    }

    static void releaseHeldGuardPosts()
    {
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {

            try
            {
                releaseHeldGuardPosts(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Releasing held guard posts failed for colony {}", colony.getName(), e);
            }
        }
    }

    static void releaseHeldGuardPosts(final IColony colony)
    {
        int released = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Block block = building.getBuildingType().getBuildingBlock();
            if (block == ModBlocks.blockHutGuardTower || block == ModBlocks.blockHutBarracksTower)
            {
                released += releaseHeldPosts(colony, building);
            }

            else if (block == ModBlocks.blockHutGraveyard && !AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES)
                       && building.getModule(WorkerBuildingModule.class) instanceof WorkerBuildingModule crew && crew.getHiringMode() == HiringMode.LOCKED)
            {
                crew.setHiringMode(HiringMode.DEFAULT);
                ColonyAutopilot.LOGGER.info("[{}] graves rise again — the undertaker's post the autopilot held at the graveyard goes back to MineColonies' own hiring",
                  colony.getName());
            }
        }
        if (released > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the autopilot's guard staffing is off — the {} guard post(s) it held go back to MineColonies' own hiring",
              colony.getName(), released);
        }
    }

    private static int releaseHeldPosts(final IColony colony, final IBuilding tower)
    {
        int released = 0;
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getHiringMode() == HiringMode.LOCKED)
            {
                module.setHiringMode(HiringMode.DEFAULT);
                tower.markDirty();
                released++;
            }
        }
        if (released > 0 && tower.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGuardTower && decidedGuardType(tower) == null)
        {
            ColonyAutopilot.LOGGER.info("[{}] the guard tower at {} is yours now — its {} held post(s) went back to MineColonies' own hiring",
              colony.getName(), tower.getPosition().toShortString(), released);
        }
        return released;
    }

    private static boolean onAuto(final IBuilding tower, final JobEntry job)
    {
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getJobEntry() == job)
            {
                return module.getHiringMode() == HiringMode.AUTO;
            }
        }
        return false;
    }

    private static JobEntry postJob(final IBuilding post, final ResourceLocation key)
    {
        for (final GuardBuildingModule module : post.getModulesByType(GuardBuildingModule.class))
        {
            if (key.equals(module.getJobEntry().getKey()))
            {
                return module.getJobEntry();
            }
        }
        return null;
    }

    private static boolean repairStampedTower(final IColony colony, final IBuilding tower)
    {
        GuardBuildingModule stamp = null;
        final List<GuardBuildingModule> added = new ArrayList<>();
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            switch (module.getHiringMode())
            {
                case LOCKED ->
                {
                    if (stamp != null)
                    {
                        return false;
                    }
                    stamp = module;
                }
                case DEFAULT ->
                {

                    if (!module.getAssignedCitizen().isEmpty()
                          || module.getJobEntry() == ModJobs.knight.get() || module.getJobEntry() == ModJobs.archer.get())
                    {
                        return false;
                    }
                    added.add(module);
                }
                case AUTO ->
                {
                    return false;
                }
                default -> { }
            }
        }
        if (stamp == null || added.isEmpty())
        {
            return false;
        }
        for (final GuardBuildingModule module : added)
        {
            module.setHiringMode(HiringMode.MANUAL);
        }
        tower.markDirty();
        ColonyAutopilot.LOGGER.info("[{}] the guard tower at {} gained {} guard post(s) in an update — closed them again around its standing {}",
          colony.getName(), tower.getPosition().toShortString(), added.size(), stamp.getJobEntry().getKey().getPath());
        return true;
    }

    private static JobEntry decidedGuardType(final IBuilding tower)
    {
        JobEntry open = null;
        int openCount = 0;
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getHiringMode() != HiringMode.MANUAL)
            {
                openCount++;
                open = module.getJobEntry();
            }
        }
        return openCount == 1 ? open : null;
    }

    private static boolean seatBillet(final IColony colony, final IBuilding tower)
    {
        final int perDruid = AutopilotConfig.get(colony, AutopilotConfig.GUARDS_PER_DRUID);
        if (perDruid > 0 && underDruidQuota(colony, perDruid) && staffGuardPost(colony, tower, Set.of(ModJobs.druid.get())))
        {
            return true;
        }

        final JobEntry wanted = nextFighter(colony, tower, censusGuards(colony, true));
        if (seatable(tower, wanted))
        {

            return staffGuardPost(colony, tower, Set.of(wanted), false) || staffGuardPost(colony, tower, Set.of(wanted), true);
        }

        for (final JobEntry next : fallbackPosts(colony, tower, wanted))
        {
            if (next != wanted && seatable(tower, next))
            {
                return staffGuardPost(colony, tower, Set.of(next), false);
            }
        }
        return false;
    }

    private static List<JobEntry> fallbackPosts(final IColony colony, final IBuilding tower, final JobEntry wanted)
    {
        final List<JobEntry> ranged = new ArrayList<>();
        for (final JobEntry job : new JobEntry[] {researchedPost(colony, tower, MARKSMAN_JOB, MARKSMAN_RESEARCH), ModJobs.archer.get()})
        {
            if (job != null)
            {
                ranged.add(job);
            }
        }
        final List<JobEntry> melee = new ArrayList<>();
        melee.add(ModJobs.knight.get());
        final JobEntry huscarl = researchedPost(colony, tower, HUSCARL_JOB, HUSCARL_RESEARCH);
        if (huscarl != null)
        {
            melee.add(huscarl);
        }
        final List<JobEntry> chain = new ArrayList<>();
        if (ranged.contains(wanted))
        {
            chain.addAll(ranged);
            chain.addAll(melee);
        }
        else
        {
            chain.addAll(melee);
            chain.addAll(ranged);
        }
        return chain;
    }

    private static boolean seatable(final IBuilding post, final JobEntry job)
    {
        for (final GuardBuildingModule module : post.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getJobEntry() == job && !module.isFull() && canSeat(post, module))
            {
                return true;
            }
        }
        return false;
    }

    static boolean handHired(final IBuilding tower)
    {
        if (tower.getBuildingType().getBuildingBlock() != ModBlocks.blockHutBarracksTower)
        {
            return false;
        }
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getHiringMode() == HiringMode.MANUAL || module.getHiringMode() == HiringMode.AUTO)
            {
                return true;
            }
        }
        return false;
    }

    private static void reserveForPlayer(final IColony colony, final IBuilding tower)
    {
        int closed = 0;
        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {

            if (module.getHiringMode() == HiringMode.DEFAULT)
            {
                module.setHiringMode(HiringMode.LOCKED);
                closed++;
            }
        }
        if (closed > 0)
        {
            tower.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] the barracks tower at {} is staffed by hand — its other {} guard post(s) were locked, so nothing fills the slot you reserved; set your manual post(s) back to default and the autopilot takes the tower back",
              colony.getName(), tower.getPosition().toShortString(), closed);
        }
    }

    private static void setAutopilotPosts(final IBuilding tower)
    {

        for (final GuardBuildingModule module : tower.getModulesByType(GuardBuildingModule.class))
        {
            if (module.getHiringMode() == HiringMode.DEFAULT)
            {
                module.setHiringMode(HiringMode.LOCKED);
                tower.markDirty();
            }
        }
    }

    static final class GuardCensus
    {
        int knights;
        int huscarls;
        int archers;
        int marksmen;
        int druids;
        int others;

        void add(final JobEntry job, final int heads, final boolean countUnknown)
        {
            if (job == null)
            {
                return;
            }
            if (job == ModJobs.knight.get())
            {
                knights += heads;
            }
            else if (job == ModJobs.archer.get())
            {
                archers += heads;
            }
            else if (job == ModJobs.druid.get())
            {
                druids += heads;
            }
            else if (HUSCARL_JOB.equals(job.getKey()))
            {
                huscarls += heads;
            }
            else if (MARKSMAN_JOB.equals(job.getKey()))
            {
                marksmen += heads;
            }
            else if (countUnknown)
            {
                others += heads;
            }
        }

        int melee()
        {
            return knights + huscarls;
        }

        int ranged()
        {
            return archers + marksmen;
        }

        int fighters()
        {
            return melee() + ranged() + others;
        }

        int total()
        {
            return fighters() + druids;
        }
    }

    static GuardCensus censusGuards(final IColony colony, final boolean withVacantStamps)
    {
        final GuardCensus census = new GuardCensus();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Block hut = building.getBuildingType().getBuildingBlock();

            final boolean tower = hut == ModBlocks.blockHutGuardTower || hut == ModBlocks.blockHutBarracksTower;
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                census.add(module.getJobEntry(), module.getAssignedCitizen().size(), tower);
            }
            if (withVacantStamps && hut == ModBlocks.blockHutGuardTower && building.getBuildingLevel() > 0
                  && building.getAllAssignedCitizen().isEmpty())
            {
                census.add(decidedGuardType(building), 1, true);
            }
        }
        return census;
    }

    private static int totalJobSlots(final IColony colony)
    {
        int slots = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0)
            {
                continue;
            }
            int guardSlots = 0;
            for (final GuardBuildingModule guard : building.getModulesByType(GuardBuildingModule.class))
            {
                guardSlots = Math.max(guardSlots, guard.getModuleMax());
            }
            slots += guardSlots;
            for (final WorkerBuildingModule crew : building.getModulesByType(WorkerBuildingModule.class))
            {
                if (!(crew instanceof GuardBuildingModule))
                {
                    slots += crew.getModuleMax();
                }
            }
        }
        return slots;
    }

    private static boolean underDruidQuota(final IColony colony, final int perGuards)
    {
        final GuardCensus c = censusGuards(colony, true);
        return (c.druids + 1) * perGuards <= c.fighters();
    }

    private void balanceGuardComposition(final IColony colony, final ServerLevel level)
    {
        if (colony.getRaiderManager().isRaided())
        {
            return;
        }

        if (!colony.getSettings().getSetting(com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall.AUTO_HIRING_MODE).getValue())
        {
            return;
        }

        GuardCensus c = censusGuards(colony, false);
        if (c.total() == 0)
        {
            return;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final long now = level.getGameTime();
        final Long last = lastGuardBalance.get(key);
        boolean canConvert = last == null || now - last >= 24000L;

        final boolean bonusConvert = !canConvert && anticipationConversion.getOrDefault(key, -1L) == now / 24000L;
        if (bonusConvert)
        {
            canConvert = true;
        }
        if (!canConvert)
        {
            return;
        }
        int moved = 0;
        final int perDruid = AutopilotConfig.get(colony, AutopilotConfig.GUARDS_PER_DRUID);
        final int targetDruids = perDruid > 0 ? c.total() / (perDruid + 1) : 0;
        while (c.druids < targetDruids)
        {
            if (!convertBarracksGuard(colony, ModJobs.druid.get(), GrowthDirector::isFighter))
            {

                boolean anyTower = false;
                boolean anyAutoTower = false;
                boolean freeAutoBed = false;
                for (final IBuilding tower : colony.getServerBuildingManager().getBuildings().values())
                {
                    if (tower.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBarracksTower)
                    {
                        anyTower = true;
                        anyAutoTower |= !handHired(tower);

                        freeAutoBed |= !handHired(tower) && tower.getBuildingLevel() > 0
                                         && tower.getAllAssignedCitizen().size() < tower.getBuildingLevel();
                    }
                }
                if ((!anyTower || anyAutoTower) && !freeAutoBed)
                {
                    queueBarracksForDruids(colony, level, key, now);
                }
                break;
            }
            moved++;
            c = censusGuards(colony, false);
        }

        c = censusGuards(colony, true);
        IBuilding barracks = null;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (barracks == null && building.getBuildingLevel() > 0
                  && building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBarracksTower && !handHired(building))
            {
                barracks = building;
            }
        }
        if (barracks != null)
        {
            final JobEntry huscarl = researchedPost(colony, barracks, HUSCARL_JOB, HUSCARL_RESEARCH);
            final JobEntry marksman = researchedPost(colony, barracks, MARKSMAN_JOB, MARKSMAN_RESEARCH);
            final JobEntry archer = ModJobs.archer.get();

            final JobEntry[] jobs = {ModJobs.knight.get(), postJob(barracks, HUSCARL_JOB), archer, postJob(barracks, MARKSMAN_JOB)};

            for (int move = 0; move <= c.total(); move++)
            {
                final int fighters = c.melee() + c.ranged();
                final int wantRanged = fighters / (AutopilotConfig.get(colony, AutopilotConfig.KNIGHTS_PER_ARCHER) + 1);
                final int wantHuscarls = huscarl == null ? 0 : (fighters - wantRanged) / (KNIGHTS_PER_HUSCARL + 1);
                final int wantMarksmen = marksman == null ? 0 : wantRanged;
                final int[] over = {
                  c.knights - (fighters - wantRanged - wantHuscarls), c.huscarls - wantHuscarls,
                  c.archers - (wantRanged - wantMarksmen), c.marksmen - wantMarksmen};

                final boolean retired = c.archers > 0 && marksman != null;

                final boolean[] movable = movableTypes(colony, jobs);
                int most = -1;
                int least = 0;
                for (int i = 0; i < jobs.length; i++)
                {
                    if (jobs[i] == null)
                    {
                        continue;
                    }

                    if (movable[i] && (!retired || i != 2) && (most < 0 || over[i] > over[most]))
                    {
                        most = i;
                    }
                    if (over[i] < over[least])
                    {
                        least = i;
                    }
                }
                if (over[least] >= 0 || most < 0)
                {
                    break;
                }

                final JobEntry surplus = jobs[most];
                final boolean made = (retired && convertBarracksGuard(colony, jobs[least], job -> job == archer))
                                       || (over[most] - over[least] >= 2 && convertBarracksGuard(colony, jobs[least], job -> job == surplus));
                if (!made)
                {
                    break;
                }
                moved++;
                c = censusGuards(colony, true);
            }
        }
        if (moved > 0)
        {
            lastGuardBalance.put(key, now);
            if (bonusConvert)
            {
                anticipationConversion.remove(key);
            }
        }
    }

    private static boolean[] movableTypes(final IColony colony, final JobEntry[] jobs)
    {
        final boolean[] movable = new boolean[jobs.length];
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0 || building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutBarracksTower
                  || handHired(building))
            {
                continue;
            }
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                for (int i = 0; i < jobs.length; i++)
                {
                    movable[i] |= jobs[i] != null && module.getJobEntry() == jobs[i] && !module.getAssignedCitizen().isEmpty();
                }
            }
        }
        return movable;
    }

    private static boolean isFighter(final JobEntry job)
    {
        return job != ModJobs.druid.get();
    }

    private static boolean convertBarracksGuard(final IColony colony, final JobEntry toJob, final java.util.function.Predicate<JobEntry> from)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutBarracksTower || handHired(building))
            {
                continue;
            }
            GuardBuildingModule target = null;
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                if (module.getJobEntry() == toJob)
                {
                    target = module;
                }
            }
            if (target == null)
            {
                continue;
            }
            GuardBuildingModule sourceModule = null;
            ICitizenData victim = null;
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                if (module == target || !from.test(module.getJobEntry()))
                {
                    continue;
                }
                for (final ICitizenData guard : module.getAssignedCitizen())
                {
                    if (victim == null || skillOf(guard, target) > skillOf(victim, target))
                    {
                        victim = guard;
                        sourceModule = module;
                    }
                }
            }
            if (victim == null)
            {
                continue;
            }
            sourceModule.removeCitizen(victim);
            victim.setJob(null);
            if (target.assignCitizen(victim))
            {
                ColonyAutopilot.LOGGER.info("[{}] reassigned {} from {} to {} to hold the militia's balance",
                  colony.getName(), victim.getName(), sourceModule.getJobEntry().getKey().getPath(), toJob.getKey().getPath());
                return true;
            }
            sourceModule.assignCitizen(victim);
            return false;
        }
        return false;
    }

    private void queueBarracksForDruids(final IColony colony, final ServerLevel level, final ColonyId key, final long now)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.EXPANSION_ENABLED) || pendingSince.containsKey(key))
        {
            return;
        }

        final ResourceLocation research = colony.getResearchManager().getResearchEffectIdFrom(ModBlocks.blockHutBarracks);
        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(research)
              && colony.getResearchManager().getResearchEffects().getEffectStrength(research) < 1)
        {
            return;
        }

        final Long parked = plotBlockedUntil.getOrDefault(key, Map.of()).get(ModBlocks.blockHutBarracks);
        if (unavailableHuts.getOrDefault(key, Set.of()).contains(ModBlocks.blockHutBarracks) || (parked != null && now < parked))
        {
            return;
        }
        final Long last = barracksForDruidsAt.get(key);
        if (last != null && now - last < BARRACKS_FOR_DRUIDS_COOLDOWN)
        {
            return;
        }

        for (final IBuilding standing : colony.getServerBuildingManager().getBuildings().values())
        {
            final Block hut = standing.getBuildingType().getBuildingBlock();
            if ((hut == ModBlocks.blockHutBarracks || hut == ModBlocks.blockHutBarracksTower) && standing.getBuildingLevel() == 0)
            {
                return;
            }
        }

        final Long lastPlaced = lastPlacement.get(key);
        if (lastPlaced != null && now - lastPlaced < AutopilotConfig.get(colony, AutopilotConfig.PLACEMENT_COOLDOWN_MINUTES) * 1200L)
        {
            return;
        }

        if (!BuilderCap.mayPlace(colony, level, ModBlocks.blockHutBarracks))
        {
            return;
        }
        barracksForDruidsAt.put(key, now);
        ColonyAutopilot.LOGGER.info("[{}] the militia wants more druids than the barracks can billet — queuing another barracks (build it, or lower growth.guardsPerDruid)",
          colony.getName());
        beginPlacement(colony, level, new ResolvedStep(ModBlocks.blockHutBarracks, "Barracks", 0, 0), ModBlocks.blockHutBarracks);
    }

    private static boolean staffGuardPost(final IColony colony, final IBuilding post, final Set<JobEntry> allowed)
    {
        return staffGuardPost(colony, post, allowed, false);
    }

    private static boolean staffGuardPost(final IColony colony, final IBuilding post, final Set<JobEntry> allowed, final boolean anySchool)
    {
        GuardBuildingModule neediest = null;
        int fewest = Integer.MAX_VALUE;
        for (final GuardBuildingModule module : post.getModulesByType(GuardBuildingModule.class))
        {
            if (!allowed.contains(module.getJobEntry()) || module.isFull() || !canSeat(post, module))
            {
                continue;
            }
            final int staffed = module.getAssignedCitizen().size();
            if (staffed < fewest)
            {
                fewest = staffed;
                neediest = module;
            }
        }
        if (neediest == null)
        {
            return false;
        }

        ICitizenData jobless = post.getSetting(AbstractBuildingGuards.HIRE_TRAINEE).getValue() ? bestPupil(colony, neediest, anySchool) : null;
        if (jobless != null)
        {
            jobless.setJob(null);
        }
        else if (anySchool)
        {
            return false;
        }
        else
        {
            jobless = colony.getCitizenManager().getJoblessCitizen();
        }

        boolean student = false;
        if (jobless == null)
        {
            jobless = bestStudent(colony, neediest);
            student = jobless != null;
            if (student)
            {
                jobless.setJob(null);
            }
        }
        if (jobless == null || !neediest.assignCitizen(jobless))
        {
            return false;
        }
        ColonyAutopilot.LOGGER.info("[{}] {} takes up the {} post at the {}{}",
          colony.getName(), jobless.getName(), neediest.getJobEntry().getKey().getPath(), SiteSelector.plainName(post),
          student ? " — a student called from the Library, nobody being without a workplace" : "");
        return true;
    }

    private static ICitizenData bestStudent(final IColony colony, final GuardBuildingModule module)
    {
        ICitizenData best = null;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.getJob() != null && !citizen.isChild() && citizen.getJob().getJobRegistryEntry().equals(ModJobs.student.get())
                  && (best == null || skillOf(citizen, module) > skillOf(best, module)))
            {
                best = citizen;
            }
        }
        return best;
    }

    private static boolean canSeat(final IBuilding post, final GuardBuildingModule module)
    {
        if (post.getColony().getWorld() == null
              || !WorldUtil.isChunkLoaded(post.getColony().getWorld(), post.getPosition().getX() >> 4, post.getPosition().getZ() >> 4))
        {
            return false;
        }
        final HiringMode mode = module.getHiringMode() == HiringMode.LOCKED ? HiringMode.DEFAULT : module.getHiringMode();
        return BuildingUtils.canAutoHire(post, mode, module.getJobEntry());
    }

    private static JobEntry schoolOf(final JobEntry post)
    {
        if (post == ModJobs.archer.get() || MARKSMAN_JOB.equals(post.getKey()))
        {
            return ModJobs.archerInTraining.get();
        }
        return post == ModJobs.knight.get() || HUSCARL_JOB.equals(post.getKey()) ? ModJobs.knightInTraining.get() : null;
    }

    private static ICitizenData bestPupil(final IColony colony, final GuardBuildingModule module)
    {
        return bestPupil(colony, module, false);
    }

    private static ICitizenData bestPupil(final IColony colony, final GuardBuildingModule module, final boolean anySchool)
    {
        final JobEntry school = schoolOf(module.getJobEntry());
        ICitizenData best = null;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (school == null || citizen.getJob() == null)
            {
                continue;
            }
            final JobEntry job = citizen.getJob().getJobRegistryEntry();
            final boolean pupil = job.equals(school)
                                    || (anySchool && (job.equals(ModJobs.archerInTraining.get()) || job.equals(ModJobs.knightInTraining.get())));
            if (pupil && (best == null || skillOf(citizen, module) > skillOf(best, module)))
            {
                best = citizen;
            }
        }
        return best;
    }

    private static void promoteTrainee(final IColony colony)
    {
        if (colony.getRaiderManager().isRaided())
        {
            return;
        }
        for (final IBuilding post : colony.getServerBuildingManager().getBuildings().values())
        {

            if (post.getBuildingLevel() <= 0 || handHired(post)
                  || (post.getBuildingType().getBuildingBlock() == ModBlocks.blockHutGuardTower && decidedGuardType(post) == null))
            {
                continue;
            }
            for (final GuardBuildingModule module : post.getModulesByType(GuardBuildingModule.class))
            {
                if (schoolOf(module.getJobEntry()) == null)
                {
                    continue;
                }
                if (module.getJobEntry() == ModJobs.archer.get() && nextRanged(colony, post) != ModJobs.archer.get())
                {
                    continue;
                }
                if (!module.isFull() || !canSeat(post, module) || !post.getSetting(AbstractBuildingGuards.HIRE_TRAINEE).getValue())
                {
                    continue;
                }
                final ICitizenData graduate = bestPupil(colony, module);
                ICitizenData weakest = null;
                for (final ICitizenData guard : module.getAssignedCitizen())
                {
                    if (weakest == null || skillOf(guard, module) < skillOf(weakest, module))
                    {
                        weakest = guard;
                    }
                }
                if (graduate == null || weakest == null
                      || skillOf(graduate, module) <= skillOf(weakest, module) + 2)
                {
                    continue;
                }
                final int gradSkill = skillOf(graduate, module);
                final int weakSkill = skillOf(weakest, module);
                graduate.setJob(null);
                module.removeCitizen(weakest);
                if (module.assignCitizen(graduate))
                {
                    ColonyAutopilot.LOGGER.info("[{}] promoted {} into the {} — they out-skill {} ({} to {}), who steps down to find other work",
                      colony.getName(), graduate.getName(), SiteSelector.plainName(post), weakest.getName(), gradSkill, weakSkill);
                }
                return;
            }
        }
    }

    private static int skillOf(final ICitizenData citizen, final GuardBuildingModule module)
    {
        return citizen.getCitizenSkillHandler().getLevel(module.getPrimarySkill());
    }

    private static final int RAVINE_HOLE_DEPTH = 5;
    private static final int RAVINE_CLUSTER_MIN = 30;
    private static final int RAVINE_GRADE_RANGE = 40;
    private static final int RAVINE_SURVEY_MARGIN = 8;

    private static final int RAVINE_MAX_WIDTH = 16;
    private static final int RAVINE_BANK_SLACK = 2;
    private static final int RAVINE_GASH_SHARE_PERCENT = 75;

    private static final int RAVINE_COLUMNS_PER_TICK = 128;

    private static final class RavineSurvey
    {
        final IColony colony;
        final ServerLevel level;
        final int minX;
        final int minZ;
        final int width;
        final int depth;
        final List<int[]> anchors;
        final List<SiteSelector.Footprint> shields;
        final Map<Long, BlockPos> roadOwner;
        final List<int[]> holes = new ArrayList<>();
        final Set<BlockPos> buriedRoads = new HashSet<>();

        final Map<Long, Integer> heights = new HashMap<>();
        final Map<Long, Integer> grades = new HashMap<>();

        final Map<Long, int[]> sunken = new HashMap<>();

        int probeReads;
        boolean judged;
        int cursor = 0;
        boolean waitingForTerraformer;

        RavineSurvey(final IColony colony, final ServerLevel level, final int minX, final int minZ, final int width, final int depth,
          final List<int[]> anchors, final List<SiteSelector.Footprint> shields, final Map<Long, BlockPos> roadOwner)
        {
            this.colony = colony;
            this.level = level;
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            this.anchors = anchors;
            this.shields = shields;
            this.roadOwner = roadOwner;
        }
    }

    private void surveyRavines(final IColony colony, final ServerLevel level)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED) || terraformer == null)
        {
            return;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (ravineSurveys.containsKey(key))
        {
            return;
        }

        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final List<int[]> anchors = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {

            if (building.getBuildingLevel() > 0 && grounds.hasAnchorOffset(colony.getID(), building.getPosition()))
            {
                final BlockPos pos = building.getPosition();
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());

                if (!building.hasParent())
                {
                    anchors.add(new int[] {pos.getX(), pos.getZ(), pos.getY() - grounds.anchorOffset(colony.getID(), pos, 1)});
                }
            }
        }
        if (anchors.size() < 2)
        {
            return;
        }
        minX -= RAVINE_SURVEY_MARGIN;
        maxX += RAVINE_SURVEY_MARGIN;
        minZ -= RAVINE_SURVEY_MARGIN;
        maxZ += RAVINE_SURVEY_MARGIN;

        final List<SiteSelector.Footprint> shields = new ArrayList<>(sacredGround(colony));
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            shields.add(SiteSelector.boxOf(building));
        }

        final Map<Long, BlockPos> roadOwner = new HashMap<>();
        for (final Map.Entry<BlockPos, List<BlockPos>> road : roadsOf(colony, level).entrySet())
        {
            for (final BlockPos block : road.getValue())
            {
                roadOwner.put(BlockPos.asLong(block.getX(), 0, block.getZ()), road.getKey());
            }
        }

        ravineSurveys.put(key, new RavineSurvey(colony, level, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, anchors, shields, roadOwner));
    }

    private boolean drainRavineSurvey(final RavineSurvey survey)
    {
        if (!(survey.colony.getWorld() instanceof ServerLevel))
        {
            return true;
        }
        final ServerLevel level = survey.level;
        for (int visited = 0; visited < RAVINE_COLUMNS_PER_TICK && TickBudget.has(); visited++)
        {
            TickBudget.spend(1);
            if (survey.cursor >= survey.width * survey.depth)
            {
                if (!survey.judged)
                {
                    survey.judged = true;
                    judgePatches(survey);
                }
                if (survey.holes.size() >= RAVINE_CLUSTER_MIN && terraformer != null)
                {
                    if (!terraformer.beginGrading(survey.colony, level, survey.holes))
                    {

                        if (!survey.waitingForTerraformer)
                        {
                            survey.waitingForTerraformer = true;
                            ColonyAutopilot.LOGGER.debug("[{}] {} sunken column(s) wait for the terraformer to finish the pad it is cutting — the causeway follows",
                              survey.colony.getName(), survey.holes.size());
                        }
                        return false;
                    }
                    roadsAwaitingGrade.put(ColonyAutopilot.colonyKey(survey.colony), survey.buriedRoads);
                }
                return true;
            }
            final int x = survey.minX + survey.cursor % survey.width;
            final int z = survey.minZ + survey.cursor / survey.width;
            survey.cursor++;
            if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
            {
                continue;
            }
            if (SiteSelector.insideAny(survey.shields, x, z))
            {
                continue;
            }
            final int grade = gradeAt(survey, x, z);
            if (grade != Integer.MIN_VALUE && grade - surfaceAt(survey, x, z) >= RAVINE_HOLE_DEPTH)
            {

                survey.probeReads = 0;
                final boolean gash = gashAcross(survey, x, z, 1, 0) || gashAcross(survey, x, z, 0, 1);
                TickBudget.spend(survey.probeReads);
                survey.sunken.put(BlockPos.asLong(x, 0, z), new int[] {x, z, grade, gash ? 1 : 0});
            }
        }
        return false;
    }

    private static void judgePatches(final RavineSurvey survey)
    {
        final Set<Long> seen = new HashSet<>();
        final java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
        for (final Map.Entry<Long, int[]> start : survey.sunken.entrySet())
        {
            if (!seen.add(start.getKey()))
            {
                continue;
            }
            final List<int[]> patch = new ArrayList<>();
            int gashColumns = 0;
            open.add(start.getValue());
            while (!open.isEmpty())
            {
                final int[] column = open.poll();
                patch.add(column);
                gashColumns += column[3];
                for (final Direction way : Direction.Plane.HORIZONTAL)
                {
                    final long next = BlockPos.asLong(column[0] + way.getStepX(), 0, column[1] + way.getStepZ());
                    final int[] neighbour = survey.sunken.get(next);
                    if (neighbour != null && seen.add(next))
                    {
                        open.add(neighbour);
                    }
                }
            }
            if (gashColumns * 100 < patch.size() * RAVINE_GASH_SHARE_PERCENT)
            {
                continue;
            }
            for (final int[] column : patch)
            {
                survey.holes.add(new int[] {column[0], column[1], column[2]});
                final BlockPos road = survey.roadOwner.get(BlockPos.asLong(column[0], 0, column[1]));
                if (road != null)
                {
                    survey.buriedRoads.add(road);
                }
            }
        }
    }

    private static int gradeAt(final RavineSurvey survey, final int x, final int z)
    {
        return survey.grades.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
            double blend = 0;
            double weight = 0;
            int near = 0;
            for (final int[] anchor : survey.anchors)
            {
                final long dx = x - anchor[0];
                final long dz = z - anchor[1];
                final long distSq = dx * dx + dz * dz;
                if (distSq <= (long) RAVINE_GRADE_RANGE * RAVINE_GRADE_RANGE)
                {
                    final double w = 1.0 / (distSq + 1);
                    blend += w * anchor[2];
                    weight += w;
                    near++;
                }
            }
            return near < 2 ? Integer.MIN_VALUE : (int) Math.round(blend / weight);
        });
    }

    private static int surfaceAt(final RavineSurvey survey, final int x, final int z)
    {
        return survey.heights.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
            survey.probeReads++;
            return VillagePaths.surfaceY(survey.level, x, z);
        });
    }

    private static boolean gashAcross(final RavineSurvey survey, final int x, final int z, final int stepX, final int stepZ)
    {
        final int ahead = stepsToBank(survey, x, z, stepX, stepZ);
        final int behind = ahead == 0 ? 0 : stepsToBank(survey, x, z, -stepX, -stepZ);
        return ahead != 0 && behind != 0 && ahead + behind - 1 <= RAVINE_MAX_WIDTH;
    }

    private static int stepsToBank(final RavineSurvey survey, final int x, final int z, final int stepX, final int stepZ)
    {
        final int grade = gradeAt(survey, x, z);
        final int overFloor = surfaceAt(survey, x, z) + RAVINE_HOLE_DEPTH - RAVINE_BANK_SLACK;
        for (int step = 1; step <= RAVINE_MAX_WIDTH; step++)
        {
            final int bx = x + stepX * step;
            final int bz = z + stepZ * step;
            if (!WorldUtil.isChunkLoaded(survey.level, bx >> 4, bz >> 4))
            {
                return 0;
            }
            final int bankGrade = gradeAt(survey, bx, bz);
            final int bankSurface = surfaceAt(survey, bx, bz);
            if (!SiteSelector.insideAny(survey.shields, bx, bz) && bankSurface >= overFloor
                  && bankSurface >= (bankGrade == Integer.MIN_VALUE ? grade : bankGrade) - RAVINE_BANK_SLACK)
            {
                return step;
            }
        }
        return 0;
    }

    void gradingFinished(final IColony colony)
    {
        final Set<BlockPos> anchors = roadsAwaitingGrade.remove(ColonyAutopilot.colonyKey(colony));
        if (anchors == null || anchors.isEmpty())
        {
            return;
        }
        int queued = 0;
        for (final BlockPos anchor : anchors)
        {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(anchor);
            if (building != null)
            {
                pendingRoadReconnects.add(building);
                queued++;
            }
        }
        if (queued > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] re-carving {} road(s) across the freshly graded ground", colony.getName(), queued);
        }
    }

    List<SiteSelector.Footprint> obstaclesAround(final IBuilding building)
    {
        final IColony colony = building.getColony();
        final List<SiteSelector.Footprint> obstacles = new ArrayList<>(sacredGround(colony));
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            obstacles.add(SiteSelector.boxOf(other));
        }
        final Rescue held = builderRescue.get(ColonyAutopilot.colonyKey(colony));
        if (held != null)
        {
            obstacles.add(held.land());
        }
        return obstacles;
    }

    private void reconnectRoad(final IBuilding building)
    {
        final IColony colony = building.getColony();
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level) || building.getBuildingLevel() <= 0)
        {
            return;
        }
        final BlockPos anchor = building.getPosition();
        if (!WorldUtil.isChunkLoaded(level, anchor.getX() >> 4, anchor.getZ() >> 4))
        {
            return;
        }
        final BlockState hutState = level.getBlockState(anchor);
        if (!hutState.hasProperty(AbstractBlockHut.FACING))
        {
            return;
        }
        final Direction facing = hutState.getValue(AbstractBlockHut.FACING);

        final ColonyGrounds grounds = ColonyGrounds.get(level);
        if (!grounds.hasAnchorOffset(colony.getID(), anchor))
        {

            learnAnchorOffset(colony, level, building);
            return;
        }
        final int groundY = anchor.getY() - grounds.anchorOffset(colony.getID(), anchor, 1);

        final Tuple<BlockPos, BlockPos> corners = building.getCorners();

        final Direction recorded = grounds.front(colony.getID(), anchor, facing);
        final Entrance entrance = builtEntrance(level, corners, groundY, recorded);

        if (entrance == null && upgrades != null && upgrades.repairPending(colony, anchor)
              && !roadsOf(colony, level).getOrDefault(anchor, List.of()).isEmpty())
        {
            return;
        }
        final Direction exit = entrance != null ? entrance.side() : recorded;
        final BlockPos from = entrance != null ? entrance.door() : anchor;

        if (entrance != null && exit != recorded)
        {
            grounds.setFront(colony.getID(), anchor, exit);
        }
        final int toFrontEdge = switch (exit)
        {
            case NORTH -> from.getZ() - corners.getA().getZ();
            case SOUTH -> corners.getB().getZ() - from.getZ();
            case WEST -> from.getX() - corners.getA().getX();
            default -> corners.getB().getX() - from.getX();
        };
        final BlockPos pathStart = new BlockPos(from.getX(), groundY + 1, from.getZ()).relative(exit, toFrontEdge + 1);

        BlockPos target = colony.getCenter();

        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        SiteSelector.Footprint targetBox = hall != null ? SiteSelector.boxOf(hall) : null;
        Integer roadTop = null;
        boolean stub = false;
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutTownHall)
        {

            target = null;
            targetBox = null;
            final Map<BlockPos, List<BlockPos>> roads = roadsOf(colony, level);
            final List<BlockPos> own = roads.getOrDefault(anchor, List.of());

            final List<List<BlockPos>> leadOn = new ArrayList<>();
            for (final Map.Entry<BlockPos, List<BlockPos>> entry : roads.entrySet())
            {
                if (!entry.getKey().equals(anchor) && !entry.getValue().isEmpty())
                {
                    leadOn.add(entry.getValue());
                }
            }

            double best = Double.MAX_VALUE;
            final Map<Long, List<Integer>> ownAt = new HashMap<>();
            int ownMinX = Integer.MAX_VALUE, ownMaxX = Integer.MIN_VALUE, ownMinZ = Integer.MAX_VALUE, ownMaxZ = Integer.MIN_VALUE;
            for (int place = 0; place < own.size(); place++)
            {
                final BlockPos mine = own.get(place);
                ownAt.computeIfAbsent(BlockPos.asLong(mine.getX(), 0, mine.getZ()), k -> new ArrayList<>()).add(place);
                ownMinX = Math.min(ownMinX, mine.getX() - 1);
                ownMaxX = Math.max(ownMaxX, mine.getX() + 1);
                ownMinZ = Math.min(ownMinZ, mine.getZ() - 1);
                ownMaxZ = Math.max(ownMaxZ, mine.getZ() + 1);
            }
            for (final List<BlockPos> road : leadOn)
            {
                for (final BlockPos block : road)
                {
                    if (block.getX() < ownMinX || block.getX() > ownMaxX || block.getZ() < ownMinZ || block.getZ() > ownMaxZ)
                    {
                        continue;
                    }
                    int first = Integer.MAX_VALUE;
                    for (int dx = -1; dx <= 1; dx++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            final List<Integer> beside = ownAt.get(BlockPos.asLong(block.getX() + dx, 0, block.getZ() + dz));
                            if (beside == null)
                            {
                                continue;
                            }
                            for (final int place : beside)
                            {
                                if (place >= first)
                                {
                                    break;
                                }
                                if (Math.abs(own.get(place).getY() - block.getY()) <= 1)
                                {
                                    first = place;
                                    break;
                                }
                            }
                        }
                    }
                    if (first == Integer.MAX_VALUE || !WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4)
                          || !level.getBlockState(block).is(Blocks.DIRT_PATH))
                    {
                        continue;
                    }
                    final double away = own.get(first).distSqr(pathStart);
                    if (away < best)
                    {
                        best = away;
                        target = block;
                    }
                }
            }

            if (target == null)
            {
                for (final List<BlockPos> road : leadOn)
                {
                    for (final BlockPos block : road)
                    {
                        final double away = (double) (block.getX() - pathStart.getX()) * (block.getX() - pathStart.getX())
                                              + (double) (block.getZ() - pathStart.getZ()) * (block.getZ() - pathStart.getZ());
                        if (away < best && WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4)
                              && level.getBlockState(block).is(Blocks.DIRT_PATH))
                        {
                            best = away;
                            target = block;
                        }
                    }
                }
            }
            if (target == null)
            {

                if (pendingRoadReconnects.stream().anyMatch(other -> other != building && other.getColony() == colony))
                {
                    reconnectLater.add(building);
                }
                stub = true;
                target = pathStart.relative(exit, DOORSTEP_EXIT);
                roadTop = pathStart.getY() - 1;
            }
            else
            {
                roadTop = target.getY();
            }
        }

        final List<SiteSelector.Footprint> obstacles = new ArrayList<>(sacredGround(colony));
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!other.getPosition().equals(anchor))
            {
                obstacles.add(SiteSelector.boxOf(other));
            }
        }

        final int targetTop = roadTop != null ? roadTop : SiteSelector.yardTop(level, grounds, colony.getID(), target, targetBox);

        if (carveDoorstepRoad(colony, level, anchor, pathStart, exit, SiteSelector.boxOf(building), target, targetTop, targetBox, obstacles,
          roadTop != null ? 0 : VillagePaths.ARRIVAL, stub))
        {
            ColonyAutopilot.LOGGER.debug("[{}] road re-carved from the {}'s door",
              colony.getName(), SiteSelector.plainName(building));
        }
    }

    private static Entrance builtEntrance(final ServerLevel level, final Tuple<BlockPos, BlockPos> corners, final int groundY, final Direction preferred)
    {
        final int minX = corners.getA().getX(), maxX = corners.getB().getX();
        final int minZ = corners.getA().getZ(), maxZ = corners.getB().getZ();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Entrance bestDoor = null;
        Entrance gate = null;
        for (int y = groundY; y <= groundY + BlueprintGround.ENTRANCE_BAND && bestDoor == null; y++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                for (int x = minX; x <= maxX; x++)
                {
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                    {
                        continue;
                    }
                    final BlockState state = level.getBlockState(pos.set(x, y, z));
                    final boolean door = state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)
                                           && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                    final boolean isGate = !door && state.getBlock() instanceof FenceGateBlock;
                    if ((!door && !isGate) || (isGate && gate != null) || !state.hasProperty(HorizontalDirectionalBlock.FACING))
                    {
                        continue;
                    }
                    final Direction axis = state.getValue(HorizontalDirectionalBlock.FACING);
                    Direction way = null;
                    int shortest = Integer.MAX_VALUE;
                    for (final Direction out : new Direction[] {axis, axis.getOpposite()})
                    {
                        BlockPos step = pos.immutable().relative(out);
                        int run = 0;
                        boolean open = true;
                        while (step.getX() >= minX && step.getX() <= maxX && step.getZ() >= minZ && step.getZ() <= maxZ)
                        {
                            if (!WorldUtil.isChunkLoaded(level, step.getX() >> 4, step.getZ() >> 4)
                                  || !level.getBlockState(step).getCollisionShape(level, step).isEmpty())
                            {
                                open = false;
                                break;
                            }
                            run++;
                            step = step.relative(out);
                        }
                        if (open && run < shortest)
                        {
                            shortest = run;
                            way = out;
                        }
                    }
                    if (way == null)
                    {
                        continue;
                    }
                    final Entrance found = new Entrance(pos.immutable(), way);
                    if (!door)
                    {
                        gate = found;
                    }
                    else if (bestDoor == null || (way == preferred && bestDoor.side() != preferred))
                    {
                        bestDoor = found;
                    }
                }
            }
        }
        return bestDoor != null ? bestDoor : gate;
    }

    private static final int DOORSTEP_EXIT = 4;

    private static final int SHORT_ROAD_SKIP = 7;

    private static final int ORPHAN_RETRY_DAYS = 7;

    private void requeueOrphanRoads(final IColony colony, final ServerLevel level)
    {
        final Map<BlockPos, List<BlockPos>> ledger = roadsOf(colony, level);
        if (ledger.size() < 2)
        {
            return;
        }
        final Map<BlockPos, OrphanRoad> memo = orphanRoads.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        memo.keySet().retainAll(ledger.keySet());
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        final SiteSelector.Footprint hallBox = hall != null ? SiteSelector.boxOf(hall) : null;

        final int hallYard = hall != null ? SiteSelector.yardTop(level, ColonyGrounds.get(level), colony.getID(), colony.getCenter(), hallBox) : 0;
        final BlockPos centre = colony.getCenter();

        final Map<Long, List<BlockPos>> columns = new HashMap<>();
        for (final List<BlockPos> road : ledger.values())
        {
            for (final BlockPos block : road)
            {
                columns.computeIfAbsent(BlockPos.asLong(block.getX(), 0, block.getZ()), k -> new ArrayList<>()).add(block);
            }
        }
        for (final Map.Entry<BlockPos, List<BlockPos>> entry : ledger.entrySet())
        {
            final IBuilding owner = colony.getServerBuildingManager().getBuilding(entry.getKey());
            final List<BlockPos> blocks = entry.getValue();
            if (owner == null || owner.getBuildingLevel() <= 0 || blocks.isEmpty() || reconnectLater.contains(owner)
                  || pendingRoadReconnects.contains(owner))
            {
                continue;
            }
            final Set<Long> own = new HashSet<>();
            for (final BlockPos block : blocks)
            {
                own.add(block.asLong());
            }
            boolean joined = false;
            boolean loaded = true;
            for (final BlockPos block : blocks)
            {
                if (!WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4))
                {
                    loaded = false;
                    break;
                }
                if (hallBox != null && hallBox.intersects(block.getX() - 1, block.getZ() - 1, block.getX() + 1, block.getZ() + 1)
                      && !entry.getKey().equals(hall.getPosition()))
                {
                    joined = true;
                    break;
                }

                if (hall != null && !entry.getKey().equals(hall.getPosition())
                      && Math.abs(block.getX() - centre.getX()) <= VillagePaths.ARRIVAL + 1 && Math.abs(block.getZ() - centre.getZ()) <= VillagePaths.ARRIVAL + 1
                      && Math.abs(block.getY() - hallYard) <= RoadRouter.YARD_STEP)
                {
                    joined = true;
                    break;
                }
                for (int dx = -1; dx <= 1 && !joined; dx++)
                {
                    for (int dz = -1; dz <= 1 && !joined; dz++)
                    {
                        final List<BlockPos> beside = columns.get(BlockPos.asLong(block.getX() + dx, 0, block.getZ() + dz));
                        if (beside == null)
                        {
                            continue;
                        }
                        for (final BlockPos other : beside)
                        {

                            if (!own.contains(other.asLong()) && Math.abs(other.getY() - block.getY()) <= 1
                                  && (!WorldUtil.isChunkLoaded(level, other.getX() >> 4, other.getZ() >> 4) || level.getBlockState(other).is(Blocks.DIRT_PATH)))
                            {
                                joined = true;
                                break;
                            }
                        }
                    }
                }
                if (joined)
                {
                    break;
                }
            }
            if (loaded && joined)
            {
                memo.remove(entry.getKey());
            }
            else if (loaded)
            {

                final OrphanRoad last = memo.get(entry.getKey());
                final Set<BlockPos> standing = new HashSet<>(blocks);
                final boolean same = last != null && last.blocks().equals(standing);
                if (same && last.days() >= 1 && last.days() <= ORPHAN_RETRY_DAYS)
                {
                    memo.put(entry.getKey(), new OrphanRoad(standing, last.days() + 1));
                    if (last.days() == 1)
                    {
                        ColonyAutopilot.LOGGER.info("[{}] the road from the {}'s door was queued for a re-carve twice and still does not touch the village, with the very blocks it had — not queued again for {} game-days unless a road of the village changes",
                          colony.getName(), SiteSelector.plainName(owner), ORPHAN_RETRY_DAYS);
                    }
                    continue;
                }
                memo.put(entry.getKey(), new OrphanRoad(standing, same && last.days() == 0 ? 1 : 0));
                ColonyAutopilot.LOGGER.info("[{}] the road from the {}'s door does not touch the village — queued for a re-carve",
                  colony.getName(), SiteSelector.plainName(owner));
                reconnectLater.add(owner);
            }
        }
    }

    private static final int DEAD_ROAD_CELLS = 3;

    private void requeueDeadRoads(final IColony colony, final ServerLevel level)
    {
        final Map<BlockPos, List<BlockPos>> roads = roadsOf(colony, level);
        final Map<Long, List<BlockPos>> byColumn = new HashMap<>();
        for (final List<BlockPos> blocks : roads.values())
        {
            for (final BlockPos block : blocks)
            {
                byColumn.computeIfAbsent(BlockPos.asLong(block.getX(), 0, block.getZ()), k -> new ArrayList<>()).add(block);
            }
        }
        final Map<BlockPos, DeadRoad> memo = deadRoads.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        for (final Map.Entry<BlockPos, List<BlockPos>> entry : roads.entrySet())
        {
            final IBuilding owner = colony.getServerBuildingManager().getBuilding(entry.getKey());
            if (owner == null || owner.getBuildingLevel() <= 0 || reconnectLater.contains(owner) || pendingRoadReconnects.contains(owner))
            {
                continue;
            }
            final Set<Long> own = new HashSet<>();
            for (final BlockPos block : entry.getValue())
            {
                own.add(block.asLong());
            }
            int dead = 0;
            for (final BlockPos block : entry.getValue())
            {
                if (WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4) && deadRoadCell(level, block)
                      && !crossedHere(level, block, own, byColumn.get(BlockPos.asLong(block.getX(), 0, block.getZ()))))
                {
                    dead++;
                }
            }
            if (dead <= DEAD_ROAD_CELLS)
            {
                memo.remove(entry.getKey());
                continue;
            }
            final DeadRoad last = memo.get(entry.getKey());
            if (last != null && last.cells() == dead)
            {
                if (!last.told())
                {
                    memo.put(entry.getKey(), new DeadRoad(dead, true));
                    ColonyAutopilot.LOGGER.info("[{}] the road from the {}'s door was re-carved and is still {} cells short, where its carve cannot lay them (most often at a junction) — not queued again until that changes",
                      colony.getName(), SiteSelector.plainName(owner), dead);
                }
                continue;
            }
            memo.put(entry.getKey(), new DeadRoad(dead, false));
            ColonyAutopilot.LOGGER.info("[{}] the road from the {}'s door has lost more than {} of its blocks — queued for a re-carve",
              colony.getName(), SiteSelector.plainName(owner), DEAD_ROAD_CELLS);
            reconnectLater.add(owner);
        }
    }

    private static boolean crossedHere(final ServerLevel level, final BlockPos cell, final Set<Long> own, final List<BlockPos> column)
    {
        for (final BlockPos other : column)
        {
            if (!own.contains(other.asLong()) && Math.abs(other.getY() - cell.getY()) <= 1
                  && (level.getBlockState(other).is(Blocks.DIRT_PATH) || level.getBlockState(other).is(Blocks.GLOWSTONE)))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean keepJunctionBlock(final ServerLevel level, final BlockPos block, final List<SiteSelector.Footprint> obstacles)
    {
        if (!WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4))
        {
            return true;
        }
        final BlockState state = level.getBlockState(block);
        if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.GLOWSTONE) || state.is(Blocks.COBBLESTONE))
        {
            return true;
        }
        if (!deadRoadCell(level, block) || SiteSelector.insideAny(obstacles, block.getX(), block.getZ()))
        {
            return false;
        }

        for (int up = 1; up <= 4; up++)
        {
            if (level.getBlockState(block.above(up)).is(Blocks.DIRT_PATH))
            {
                return false;
            }
        }
        WorldUtil.setBlockState(level, block, Blocks.DIRT_PATH.defaultBlockState());
        return true;
    }

    private static boolean deadRoadCell(final ServerLevel level, final BlockPos block)
    {
        final BlockState state = level.getBlockState(block);
        if (!state.canBeReplaced() && !VillageGrounds.isEarth(state.getBlock()) && !SporeCompat.isInfection(state.getBlock()))
        {
            return false;
        }
        final BlockPos under = block.below();
        final BlockState ground = level.getBlockState(under);
        return ground.isFaceSturdy(level, under, Direction.UP) && !SporeCompat.isInfection(ground.getBlock());
    }

    private static boolean endsOn(final List<BlockPos> other, final List<BlockPos> blocks, final Set<Long> shared)
    {
        final BlockPos last = other.get(other.size() - 1);
        for (final BlockPos block : blocks)
        {
            if (!shared.contains(block.asLong()) && Math.abs(block.getX() - last.getX()) <= 2 && Math.abs(block.getZ() - last.getZ()) <= 2
                  && Math.abs(block.getY() - last.getY()) <= 1)
            {
                return true;
            }
        }
        return false;
    }

    private void requeueSharers(final IColony colony, final Map<BlockPos, List<BlockPos>> ledger, final BlockPos anchor, final List<BlockPos> erased)
    {
        final Map<Long, Integer> columns = new HashMap<>();
        for (final BlockPos block : erased)
        {
            columns.merge(BlockPos.asLong(block.getX(), 0, block.getZ()), block.getY(), Math::max);
        }
        for (final Map.Entry<BlockPos, List<BlockPos>> entry : ledger.entrySet())
        {
            if (entry.getKey().equals(anchor))
            {
                continue;
            }
            final IBuilding other = colony.getServerBuildingManager().getBuilding(entry.getKey());
            if (other == null || reconnectLater.contains(other))
            {
                continue;
            }
            sharer:
            for (final BlockPos block : entry.getValue())
            {
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        final Integer y = columns.get(BlockPos.asLong(block.getX() + dx, 0, block.getZ() + dz));
                        if (y != null && Math.abs(y - block.getY()) <= 1)
                        {
                            if (mayRequeue(colony, anchor, other))
                            {
                                reconnectLater.add(other);
                            }
                            break sharer;
                        }
                    }
                }
            }
        }
    }

    private void requeueDependents(final IColony colony, final Map<BlockPos, List<BlockPos>> ledger, final BlockPos anchor, final List<BlockPos> erased)
    {
        if (erased.isEmpty())
        {
            return;
        }
        requeueSharers(colony, ledger, anchor, erased);
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (other.getPosition().equals(anchor) || other.getBuildingLevel() <= 0 || ledger.containsKey(other.getPosition()))
            {
                continue;
            }
            final SiteSelector.Footprint box = SiteSelector.boxOf(other);
            for (final BlockPos block : erased)
            {
                if (box.intersects(block.getX() - SHORT_ROAD_SKIP - 2, block.getZ() - SHORT_ROAD_SKIP - 2, block.getX() + SHORT_ROAD_SKIP + 2, block.getZ() + SHORT_ROAD_SKIP + 2))
                {
                    if (mayRequeue(colony, anchor, other))
                    {
                        reconnectLater.add(other);
                    }
                    break;
                }
            }
        }
    }

    private boolean mayRequeue(final IColony colony, final BlockPos from, final IBuilding other)
    {
        final long now = colony.getWorld().getGameTime();
        final Map<List<BlockPos>, Long> pairs = requeuedAt.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        final List<BlockPos> pair = List.of(from, other.getPosition());
        final Long at = pairs.get(pair);
        if (at != null && now - at < 24000L)
        {
            return false;
        }
        pairs.put(pair, now);
        return true;
    }

    private boolean carveDoorstepRoad(final IColony colony, final ServerLevel level, final BlockPos anchor,
      final BlockPos pathStart, final Direction facing, final SiteSelector.Footprint ownGrounds,
      final BlockPos target, final int targetTop, final SiteSelector.Footprint targetBox, final List<SiteSelector.Footprint> obstacles, final int reach,
      final boolean stub)
    {
        final Map<BlockPos, List<BlockPos>> ledger = roadsOf(colony, level);
        final List<BlockPos> previous = ledger.get(anchor);

        final Set<Long> liveRoads = new HashSet<>();
        final Set<Long> liveBlocks = new HashSet<>();
        final Map<Long, Integer> junctions = new HashMap<>();

        final Map<Long, Integer> throughRoads = new HashMap<>();
        final long doorDx = target.getX() - pathStart.getX();
        final long doorDz = target.getZ() - pathStart.getZ();
        final double onward = Math.sqrt((double) (doorDx * doorDx + doorDz * doorDz)) - SHORT_ROAD_SKIP;
        for (final Map.Entry<BlockPos, List<BlockPos>> entry : ledger.entrySet())
        {
            if (entry.getKey().equals(anchor))
            {
                continue;
            }
            boolean leadsOn = false;
            boolean reaches = false;
            for (final BlockPos block : entry.getValue())
            {

                if (WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4))
                {
                    final BlockState standing = level.getBlockState(block);
                    if (!standing.is(Blocks.DIRT_PATH) && !standing.is(Blocks.GLOWSTONE) && !standing.is(Blocks.COBBLESTONE))
                    {
                        continue;
                    }
                }
                liveRoads.add(BlockPos.asLong(block.getX(), 0, block.getZ()));
                liveBlocks.add(block.asLong());
                final long dx = target.getX() - block.getX();
                final long dz = target.getZ() - block.getZ();
                leadsOn |= onward > 0 && dx * dx + dz * dz < onward * onward;

                reaches |= Math.abs(block.getY() - targetTop) <= RoadRouter.YARD_STEP
                             && ((Math.abs(dx) <= reach + 1 && Math.abs(dz) <= reach + 1)
                                   || (targetBox != null && targetBox.intersects(block.getX() - 2, block.getZ() - 2, block.getX() + 2, block.getZ() + 2)));
            }
            if (leadsOn)
            {
                for (final BlockPos block : entry.getValue())
                {
                    throughRoads.merge(BlockPos.asLong(block.getX(), 0, block.getZ()), block.getY(), Math::min);
                }
            }
            if (reaches)
            {
                for (final BlockPos block : entry.getValue())
                {

                    if (WorldUtil.isChunkLoaded(level, block.getX() >> 4, block.getZ() >> 4)
                          && !level.getBlockState(block).is(net.minecraft.world.level.block.Blocks.COBBLESTONE))
                    {
                        junctions.merge(BlockPos.asLong(block.getX(), 0, block.getZ()), block.getY(), Math::max);
                    }
                }
            }
        }

        int doorTop = pathStart.getY() - 1;

        final BlockPos yard = pathStart.relative(facing.getOpposite());
        if (WorldUtil.isChunkLoaded(level, yard.getX() >> 4, yard.getZ() >> 4))
        {
            final int yardTop = VillagePaths.surfaceY(level, yard.getX(), yard.getZ());
            if (Math.abs(yardTop - doorTop) <= 1)
            {
                doorTop = yardTop;
            }
        }

        boolean adjacent = !stub && Math.abs(target.getX() - pathStart.getX()) <= SHORT_ROAD_SKIP
              && Math.abs(target.getZ() - pathStart.getZ()) <= SHORT_ROAD_SKIP && Math.abs(targetTop - doorTop) <= RoadRouter.YARD_STEP;
        for (int dx = -SHORT_ROAD_SKIP; !stub && !adjacent && dx <= SHORT_ROAD_SKIP; dx++)
        {
            for (int dz = -SHORT_ROAD_SKIP; dz <= SHORT_ROAD_SKIP; dz++)
            {
                final Integer roadY = throughRoads.get(BlockPos.asLong(pathStart.getX() + dx, 0, pathStart.getZ() + dz));

                if (roadY != null && Math.abs(roadY - doorTop) <= RoadRouter.YARD_STEP
                      && VillagePaths.walkable(level, pathStart, new BlockPos(pathStart.getX() + dx, roadY, pathStart.getZ() + dz)))
                {
                    adjacent = true;
                    break;
                }
            }
        }
        if (adjacent)
        {
            if (previous != null)
            {

                int joined = 0;
                for (final Map.Entry<BlockPos, List<BlockPos>> entry : ledger.entrySet())
                {
                    if (!entry.getKey().equals(anchor) && !entry.getValue().isEmpty() && endsOn(entry.getValue(), previous, liveBlocks))
                    {
                        joined++;
                    }
                }
                if (joined > 0)
                {
                    ColonyAutopilot.LOGGER.debug("[{}] the door at {} opens onto the village, but {} road(s) join its own road — that stays",
                      colony.getName(), anchor.toShortString(), joined);
                    return false;
                }

                final List<BlockPos> left = VillagePaths.erase(level, previous, liveBlocks, Set.of(), ColonyGrounds.get(level).roadWater(colony.getID()));
                ColonyGrounds.get(level).setRoadLine(colony.getID(), anchor, null);
                if (left.isEmpty())
                {
                    ledger.remove(anchor);
                }
                else
                {
                    ledger.put(anchor, left);
                }
                if (left.size() != previous.size())
                {
                    bootChanged.add(ColonyAutopilot.colonyKey(colony));
                    saveRoads(colony, level);
                    final List<BlockPos> erased = new ArrayList<>();
                    for (final BlockPos block : previous)
                    {
                        if (!liveBlocks.contains(block.asLong()) && !left.contains(block))
                        {
                            erased.add(block);
                        }
                    }

                    requeueDependents(colony, ledger, anchor, erased);
                }
            }

            final BlockPos beside = pathStart.relative(facing.getClockWise());
            String lit = "no room for a lamp post beside it";
            boolean lampShielded = !WorldUtil.isChunkLoaded(level, beside.getX() >> 4, beside.getZ() >> 4);
            for (final SiteSelector.Footprint shield : sacredGround(colony))
            {
                if (lampShielded || shield.intersects(beside.getX(), beside.getZ(), beside.getX(), beside.getZ()))
                {
                    lampShielded = true;
                    break;
                }
            }
            if (!lampShielded)
            {
                final BlockPos lamp = new BlockPos(beside.getX(), VillagePaths.surfaceY(level, beside.getX(), beside.getZ()), beside.getZ());

                final int spacing = AutopilotConfig.get(colony, AutopilotConfig.LAMP_SPACING);
                if (Math.abs(lamp.getY() - (pathStart.getY() - 1)) <= 2)
                {
                    if (VillagePaths.standingPost(level, lamp))
                    {
                        lit = "lamplit";
                    }
                    else if (VillagePaths.crowded(lamp, VillagePaths.postsIn(level, lamp.getX() - spacing, lamp.getZ() - spacing, lamp.getX() + spacing,
                      lamp.getZ() + spacing), spacing))
                    {
                        lit = "a lamp post stands within " + spacing + " blocks";
                    }
                    else if (VillagePaths.lamp(level, lamp))
                    {
                        lit = "lamplit";
                    }
                }
            }
            ColonyAutopilot.LOGGER.debug("[{}] the door at {} opens onto the village — {}, no road stub carved",
              colony.getName(), anchor.toShortString(), lit);
            return false;
        }

        BlockPos elbow = pathStart.relative(facing, DOORSTEP_EXIT);
        final IBuilding owner = colony.getServerBuildingManager().getBuilding(anchor);
        final String door = owner != null ? SiteSelector.plainName(owner) : "building at " + anchor.toShortString();

        final IBuilding parent = owner != null && owner.hasParent() ? colony.getServerBuildingManager().getBuilding(owner.getParent()) : null;
        if (parent != null)
        {
            final SiteSelector.Footprint parentBox = SiteSelector.boxOf(parent);
            final BlockPos nextOut = pathStart.relative(facing);
            final boolean hutBlockAlone = ownGrounds.maxX() - ownGrounds.minX() < 2 && ownGrounds.maxZ() - ownGrounds.minZ() < 2;
            if (hutBlockAlone || (parentBox.intersects(pathStart.getX(), pathStart.getZ(), pathStart.getX(), pathStart.getZ())
                                    && parentBox.intersects(nextOut.getX(), nextOut.getZ(), nextOut.getX(), nextOut.getZ())))
            {
                ColonyAutopilot.LOGGER.debug("[{}] the {} is part of the {} and {} — no road of its own",
                  colony.getName(), door, SiteSelector.plainName(parent),
                  hutBlockAlone ? "has no walls of its own to find a door in" : "its doorstep at " + pathStart.toShortString() + " begins in that building's grounds");
                return false;
            }
        }

        final List<SiteSelector.Footprint> guarded = new ArrayList<>(obstacles);
        guarded.add(ownGrounds);

        final List<SiteSelector.Footprint> fisherWater = new ArrayList<>();
        for (final IBuilding hut : colony.getServerBuildingManager().getBuildings().values())
        {
            if (hut.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFisherman)
            {
                fisherWater.add(SiteSelector.boxOf(hut));
                fisherWater.addAll(FishingPond.dugPondShields(level, colony, hut));
            }
        }

        Set<Long> lastCarve = null;
        if (previous != null)
        {
            lastCarve = new HashSet<>();
            for (final BlockPos block : previous)
            {
                lastCarve.add(block.asLong());
            }
            lastCarve.removeAll(liveBlocks);
        }

        final RoadRouter.Survey survey = new RoadRouter.Survey(RoadRouter.CARVE_BUDGET);
        survey.own = lastCarve;

        final long[] lastLine = previous != null ? ColonyGrounds.get(level).roadLine(colony.getID(), anchor) : null;
        if (lastLine != null)
        {
            survey.line = new LongOpenHashSet(lastLine);
        }

        List<int[]> walked = null;
        final Set<Long> laidInWater = ColonyGrounds.get(level).roadWater(colony.getID());

        final Set<Long> blind = new HashSet<>();
        final int lampSpacing = AutopilotConfig.get(colony, AutopilotConfig.LAMP_SPACING);
        final List<BlockPos> painted = VillagePaths.connect(level, pathStart, true, elbow, obstacles, guarded, fisherWater, liveRoads, junctions, lastCarve, null, null, blind,
          reach, lampSpacing, laidInWater);

        final Set<Long> fresh = new HashSet<>();
        for (final BlockPos block : painted)
        {
            fresh.add(block.asLong());
        }

        for (int back = 0; back <= DOORSTEP_EXIT; back++)
        {
            final BlockPos at = elbow.relative(facing.getOpposite(), back);
            boolean laid = false;
            for (final BlockPos block : painted)
            {
                if (block.getX() == at.getX() && block.getZ() == at.getZ())
                {
                    laid = true;
                    break;
                }
            }
            if (laid)
            {
                elbow = at;
                break;
            }
        }

        Set<Long> onwardPrevious = null;
        if (lastCarve != null)
        {
            onwardPrevious = new HashSet<>(lastCarve);
            onwardPrevious.removeAll(fresh);
        }
        if (!stub)
        {
            final ColonyId key = ColonyAutopilot.colonyKey(colony);
            final long now = level.getGameTime();

            final Map<BlockPos, Long> refusedWalks = walkRefused.computeIfAbsent(key, k -> new HashMap<>());
            final Long walkRefusedAt = refusedWalks.get(anchor);
            final boolean walkFresh = walkRefusedAt != null && now - walkRefusedAt < 24000L;
            List<int[]> route = walkFresh ? null : RoadRouter.route(level, colony, elbow, target, targetTop, targetBox, reach, guarded, junctions, fresh,
              survey, RoadRouter.CARVE_BUDGET);

            final int span = Math.max(Math.abs(target.getX() - elbow.getX()), Math.abs(target.getZ() - elbow.getZ()));
            final Map<BlockPos, Long> refusedStairs = stairRefused.computeIfAbsent(key, k -> new HashMap<>());
            final Map<BlockPos, Long> longerStairs = stairLonger.computeIfAbsent(key, k -> new HashMap<>());
            final Long refusedAt = refusedStairs.get(anchor);
            final Long longerAt = longerStairs.get(anchor);
            final boolean freshLonger = longerAt != null && now - longerAt < 24000L;
            if ((route == null || (route.size() > 2 * (span + reach) && !freshLonger)) && (refusedAt == null || now - refusedAt >= 24000L))
            {
                final List<int[]> stair = RoadRouter.routeStair(level, colony, elbow, target, targetTop, targetBox, reach, guarded, junctions, fresh,
                  survey, RoadRouter.CARVE_BUDGET);
                if (stair == null)
                {
                    refusedStairs.put(anchor, now);
                    if (route == null && !walkFresh)
                    {
                        refusedWalks.put(anchor, now);
                    }
                }
                else if (route == null || stair.size() < route.size())
                {
                    refusedStairs.remove(anchor);
                    longerStairs.remove(anchor);
                    if (!stair.isEmpty())
                    {
                        ColonyAutopilot.LOGGER.info("[{}] a stair from {} to {}: {} columns cut into the ground or filled against it, one block a column{}",
                          colony.getName(), elbow.toShortString(), target.toShortString(), stair.size(),
                          route == null ? "" : " (the walk round was " + route.size() + ")");
                    }
                    route = stair;
                }
                else
                {
                    longerStairs.put(anchor, now);
                }
            }
            if (route != null)
            {
                refusedWalks.remove(anchor);
            }
            final boolean unrouted = route == null;
            if (!unrouted && route.isEmpty())
            {
                route = null;
            }
            final int doorstep = painted.size();
            painted.addAll(VillagePaths.connect(level, elbow, false, target, guarded, guarded, fisherWater, liveRoads, junctions, onwardPrevious, route, fresh, blind,
              reach, lampSpacing, laidInWater));
            walked = route;
            if (unrouted && !painted.isEmpty())
            {

                final int laid = painted.size() - doorstep;
                final String carved = laid > 0 ? "the straight line is carved (" + laid + " blocks)" : "the straight line lays nothing either, the doorstep alone stands";
                if (walkFresh)
                {

                    ColonyAutopilot.LOGGER.debug("[{}] no routed road from {} to {} (refused within the game-day, the router was not asked again) — {}",
                      colony.getName(), elbow.toShortString(), target.toShortString(), carved);
                }
                else
                {
                    ColonyAutopilot.LOGGER.info("[{}] no routed road from {} to {} — {} (the router's and the carver's debug lines say why)",
                      colony.getName(), elbow.toShortString(), target.toShortString(), carved);
                }
            }
            if (painted.isEmpty())
            {
                route = RoadRouter.route(level, colony, pathStart, target, targetTop, targetBox, reach, guarded, junctions, null,
                  survey, RoadRouter.CARVE_BUDGET);
                walked = route == null || route.isEmpty() ? null : route;
                painted.addAll(VillagePaths.connect(level, pathStart, true, target, guarded, guarded, fisherWater, liveRoads, junctions, lastCarve,
                  walked, null, blind, reach, lampSpacing, laidInWater));
                if (route == null && !painted.isEmpty())
                {

                    final String straight = "[{}] no routed road from the {}'s door at {} to {} — the straight line is carved from the door ({} blocks) (the router's and the carver's debug lines say why)";
                    if (walkFresh)
                    {
                        ColonyAutopilot.LOGGER.debug(straight, colony.getName(), door, pathStart.toShortString(), target.toShortString(), painted.size());
                    }
                    else
                    {
                        ColonyAutopilot.LOGGER.info(straight, colony.getName(), door, pathStart.toShortString(), target.toShortString(), painted.size());
                    }
                }
            }
        }
        lastCarveSpent = RoadRouter.CARVE_BUDGET - survey.budgetLeft();
        if (painted.isEmpty())
        {

            if (!"nothing laid".equals(lastBreak.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>()).put(anchor, "nothing laid")))
            {
                ColonyAutopilot.LOGGER.info("[{}] no road could be laid from the {}'s door at {} toward {} (the carver's debug lines say what stands in the way){}",
                  colony.getName(), door, pathStart.toShortString(), target.toShortString(), previous != null ? " — the road it had stays" : "");
            }
            return false;
        }
        final int roadEnd = painted.size();
        if (previous != null)
        {
            final Set<Long> spare = new HashSet<>(liveBlocks);
            for (final BlockPos block : painted)
            {
                spare.add(block.asLong());
            }

            final IBuilding hallBuilding = colony.getServerBuildingManager().getTownHall();
            for (final Map.Entry<BlockPos, List<BlockPos>> entry : ledger.entrySet())
            {
                if (entry.getKey().equals(anchor) || entry.getValue().isEmpty())
                {
                    continue;
                }
                final BlockPos last = entry.getValue().get(entry.getValue().size() - 1);

                final boolean hallsRoad = hallBuilding != null && entry.getKey().equals(hallBuilding.getPosition());
                for (final BlockPos block : previous)
                {
                    if (!spare.contains(block.asLong()) && Math.abs(block.getX() - last.getX()) <= 2 && Math.abs(block.getZ() - last.getZ()) <= 2
                          && Math.abs(block.getY() - last.getY()) <= 1)
                    {
                        if (hallsRoad)
                        {
                            if (!reconnectLater.contains(hallBuilding) && mayRequeue(colony, anchor, hallBuilding))
                            {
                                reconnectLater.add(hallBuilding);
                            }
                            break;
                        }

                        if (!keepJunctionBlock(level, block, obstacles))
                        {
                            continue;
                        }
                        spare.add(block.asLong());
                        painted.add(block);
                    }
                }
            }

            final List<BlockPos> kept = VillagePaths.erase(level, previous, spare, blind, laidInWater);

            if (painted.isEmpty())
            {
                painted.addAll(kept);
            }
            else
            {
                painted.addAll(painted.size() - 1, kept);
            }
            final Set<Long> stillStanding = new HashSet<>();
            for (final BlockPos block : kept)
            {
                stillStanding.add(block.asLong());
            }
            final List<BlockPos> erased = new ArrayList<>();
            for (final BlockPos block : previous)
            {
                if (!spare.contains(block.asLong()) && !stillStanding.contains(block.asLong()))
                {
                    erased.add(block);
                }
            }
            requeueDependents(colony, ledger, anchor, erased);
        }
        final boolean changed = previous == null || previous.size() != painted.size() || !new HashSet<>(previous).containsAll(painted);
        if (changed)
        {
            bootChanged.add(ColonyAutopilot.colonyKey(colony));

            orphanRoads.remove(ColonyAutopilot.colonyKey(colony));
        }
        ledger.put(anchor, painted);
        long[] line = null;
        if (walked != null)
        {
            line = new long[walked.size()];
            for (int k = 0; k < line.length; k++)
            {
                line[k] = BlockPos.asLong(walked.get(k)[0], 0, walked.get(k)[1]);
            }
        }
        ColonyGrounds.get(level).setRoadLine(colony.getID(), anchor, line);
        saveRoads(colony, level);

        if (blind.isEmpty())
        {
            final String broken = VillagePaths.breakIn(level, painted, roadEnd, pathStart);
            final Map<BlockPos, String> breaks = lastBreak.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
            if (broken == null)
            {
                breaks.remove(anchor);
                ColonyAutopilot.LOGGER.debug("[{}] the road from the {}'s door joins up end to end ({} blocks judged)", colony.getName(), door, roadEnd);
            }
            else if (!broken.equals(breaks.put(anchor, broken)))
            {

                ColonyAutopilot.LOGGER.warn("[{}] the road from the {}'s door does not join up: {}", colony.getName(), door, broken);
            }
        }
        return true;
    }

    private Map<BlockPos, List<BlockPos>> roadsOf(final IColony colony, final ServerLevel level)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (roadsLoaded.add(key))
        {
            final Map<BlockPos, List<BlockPos>> saved = ColonyGrounds.get(level).roads(colony.getID());
            if (!saved.isEmpty())
            {
                roadLedger.put(key, saved);
            }
        }
        return roadLedger.computeIfAbsent(key, k -> new HashMap<>());
    }

    Set<Long> roadBlocks(final IColony colony, final ServerLevel level)
    {
        if (!AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS) || liftedRoads.contains(ColonyAutopilot.colonyKey(colony)))
        {
            return Set.of();
        }
        final Set<Long> blocks = new HashSet<>();
        for (final List<BlockPos> road : roadsOf(colony, level).values())
        {
            for (final BlockPos block : road)
            {
                blocks.add(block.asLong());
            }
        }
        return blocks;
    }

    Set<Long> roadEnds(final IColony colony, final ServerLevel level)
    {
        final Set<Long> ends = new HashSet<>();
        for (final List<BlockPos> road : roadsOf(colony, level).values())
        {
            if (!road.isEmpty())
            {
                ends.add(road.get(road.size() - 1).asLong());
            }
        }
        return ends;
    }

    void forgetLamp(final IColony colony, final ServerLevel level, final BlockPos footing)
    {
        boolean changed = false;
        for (final List<BlockPos> road : roadsOf(colony, level).values())
        {
            changed |= road.removeIf(footing::equals);
        }
        if (changed)
        {
            saveRoads(colony, level);
        }
    }

    private void saveRoads(final IColony colony, final ServerLevel level)
    {
        ColonyGrounds.get(level).setRoads(colony.getID(), roadLedger.getOrDefault(ColonyAutopilot.colonyKey(colony), Map.of()));
    }

    private static void clearDoorsteps(final ServerLevel level, final IBuilding building, final Set<Long> ownRoad)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();

        final BlockPos hut = building.getPosition();
        final int groundY = hut.getY() - ColonyGrounds.get(level).anchorOffset(building.getColony().getID(), hut, 1);
        final int minX = corners.getA().getX(), maxX = corners.getB().getX();
        final int minZ = corners.getA().getZ(), maxZ = corners.getB().getZ();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {

                final int dWest = x - minX, dEast = maxX - x, dNorth = z - minZ, dSouth = maxZ - z;
                if (Math.min(Math.min(dWest, dEast), Math.min(dNorth, dSouth)) > DOORSTEP_DEPTH || !WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                for (int y = groundY; y <= groundY + BlueprintGround.ENTRANCE_BAND; y++)
                {
                    final BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(DoorBlock.HALF) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                          || !state.hasProperty(HorizontalDirectionalBlock.FACING))
                    {
                        continue;
                    }

                    final Direction axis = state.getValue(HorizontalDirectionalBlock.FACING);
                    final int alongAxis = axis.getAxis() == Direction.Axis.X ? dEast : dSouth;
                    final int againstAxis = axis.getAxis() == Direction.Axis.X ? dWest : dNorth;
                    final Direction positive = axis.getAxis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
                    final Direction out = alongAxis <= againstAxis ? positive : positive.getOpposite();
                    int dug = 0;
                    for (int step = 1; step <= 2; step++)
                    {

                        final BlockPos base = pos.immutable().relative(out, step);

                        if (!WorldUtil.isChunkLoaded(level, base.getX() >> 4, base.getZ() >> 4))
                        {
                            continue;
                        }
                        if ((level.getBlockState(base).is(Blocks.DIRT_PATH) && ownRoad.contains(base.asLong()))
                              || (level.getBlockState(base.above()).is(Blocks.DIRT_PATH) && ownRoad.contains(base.above().asLong())))
                        {
                            continue;
                        }
                        for (int height = 0; height <= 1; height++)
                        {
                            final BlockPos front = pos.immutable().relative(out, step).above(height);
                            final BlockState blocking = level.getBlockState(front);
                            if (VillageGrounds.isEarth(blocking.getBlock())
                                  || blocking.is(Blocks.DIRT_PATH) || blocking.is(Blocks.GRAVEL))
                            {
                                WorldUtil.setBlockState(level, front, Blocks.AIR.defaultBlockState());
                                dug++;
                            }
                        }
                    }
                    if (dug > 0)
                    {
                        ColonyAutopilot.LOGGER.info("[{}] dug the {}'s doorway at {} back out — {} block(s) had buried it",
                          building.getColony().getName(), SiteSelector.plainName(building), pos.toShortString(), dug);
                    }
                }
            }
        }
    }
}
