// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.placement.AbstractBlueprintIterator;
import com.ldtteam.structurize.placement.BlockPlacementResult.Result;
import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.util.BlockInfo;
import com.ldtteam.structurize.util.BlockUtils;
import com.ldtteam.structurize.util.ChangeStorage;
import com.ldtteam.structurize.util.ITickedWorldOperation;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.CreativeBuildingStructureHandler;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class DamageWatch
{

    private static final int PERIOD_TICKS = 30;

    private static final int REPASTE_WINDOW_TICKS = 1200;

    private static final int FAILED_PASTE_COOLDOWN_TICKS = 24000;

    private static final long RECYCLE_RECHECK_TICKS = 1200L;

    private static final int PASTE_STEPS_PER_TICK = 4;

    private final UpgradeDirector director;

    private int tickCounter = 3;

    private final Map<ColonyId, Integer> cursor = new HashMap<>();

    private final Map<ColonyId, Long> scanPending = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> repasting = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, Long>> cooldownUntil = new HashMap<>();

    private final Map<ColonyId, Boolean> wasRaided = new HashMap<>();

    private final Map<ColonyId, ScanJob> activeScans = new HashMap<>();

    private static final int LAYERS_PER_TICK = 4;

    private static final int CURE_WRITES_PER_TICK = 800;

    private static final ChangeStorage NO_UNDO =
      new ChangeStorage(Component.literal("colonyautopilot paste"), UUID.fromString("c0107a17-0000-0000-0000-000000000001"));

    private final Map<ColonyId, Map<BlockPos, BuildingMemory>> ledger = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, HealTask>> healing = new HashMap<>();

    private static final int HEAL_WINDOW_TICKS = 3600;

    private static final List<String> BLOCK_CONTENT_TAGS = List.of("Items", "inventory", "item", "Book", "RecordItem");

    private static final List<String> ENTITY_CONTENT_TAGS = List.of("Item", "Items", "ArmorItems", "HandItems", "body_armor_item");

    private static final int HEAL_CLEAR_RADIUS = 3;

    private record BuildingMemory(Block hutBlock, int level, Direction facing, RotationMirror rotationMirror, String pack, String blueprintPath,
      Integer anchorOffset, Direction front, boolean takenInPlace, boolean owesRepair)
    {
        BuildingMemory taken()
        {
            return new BuildingMemory(hutBlock, level, facing, rotationMirror, pack, blueprintPath, anchorOffset, front, true, owesRepair);
        }

        ColonyGrounds.SentinelMemory saved()
        {
            return new ColonyGrounds.SentinelMemory(BuiltInRegistries.BLOCK.getKey(hutBlock).toString(), level, facing.get2DDataValue(),
              rotationMirror.name(), pack, blueprintPath, anchorOffset, front == null ? null : front.get2DDataValue(), takenInPlace);
        }

        static BuildingMemory of(final ColonyGrounds.SentinelMemory saved, final boolean owesRepair)
        {
            final ResourceLocation id = ResourceLocation.tryParse(saved.hutBlock());
            final Block hut = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (!(hut instanceof AbstractBlockHut<?>))
            {
                return null;
            }
            RotationMirror rotation = RotationMirror.NONE;
            if (saved.rotationMirror() != null)
            {
                try
                {
                    rotation = RotationMirror.valueOf(saved.rotationMirror());
                }
                catch (final IllegalArgumentException e)
                {

                }
            }
            return new BuildingMemory(hut, saved.level(), Direction.from2DDataValue(saved.facing()), rotation, saved.pack(), saved.blueprintPath(),
              saved.anchorOffset(), saved.front() == null ? null : Direction.from2DDataValue(saved.front()), saved.takenInPlace(), owesRepair);
        }
    }

    private record HealTask(int level, long until) {}

    private static final class ScanJob
    {
        final IColony colony;
        final BlockPos buildingId;
        final Blueprint blueprint;
        final BlockPos zero;
        final BlockState anchorWant;
        final RotationMirror rotationMirror;
        int y;
        int x;
        int z;

        int chargedLayer = -1;
        int missing;
        int cured;

        int infested;
        final List<String> samples = new ArrayList<>();

        final List<BlockPos> familyCells = new ArrayList<>();

        ScanJob(final IColony colony, final BlockPos buildingId, final Blueprint blueprint, final BlockPos zero, final BlockState anchorWant, final RotationMirror rotationMirror)
        {
            this.colony = colony;
            this.buildingId = buildingId;
            this.blueprint = blueprint;
            this.zero = zero;
            this.anchorWant = anchorWant;
            this.rotationMirror = rotationMirror;
        }
    }

    DamageWatch(final UpgradeDirector director)
    {
        this.director = director;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        cursor.clear();
        scanPending.clear();
        repasting.clear();
        cooldownUntil.clear();
        activeScans.clear();
        wasRaided.clear();
        ledger.clear();
        healing.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {

        cursor.clear();
        scanPending.clear();
        repasting.clear();
        cooldownUntil.clear();
        activeScans.clear();
        wasRaided.clear();
        ledger.clear();
        healing.clear();

        clearStructurizeFutureQueue("blueprintConsumerQueue");
        clearStructurizeFutureQueue("blueprintDataConsumerQueue");
        clearStructurizeFutureQueue("blueprintListConsumerQueue");

        clearStructurizePastes();
    }

    private static void clearStructurizePastes()
    {
        try
        {
            final Field field = Manager.class.getDeclaredField("scanToolOperationPool");
            field.setAccessible(true);
            ((Collection<?>) field.get(null)).removeIf(operation -> operation instanceof PacedCreativePaste);
        }
        catch (final Throwable t)
        {
            ColonyAutopilot.LOGGER.warn("Could not clear the autopilot's pastes from Structurize's operation pool at shutdown", t);
        }
    }

    private static void clearStructurizeFutureQueue(final String fieldName)
    {
        try
        {
            final Field field = ServerFutureProcessor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Queue<?>) field.get(null)).clear();
        }
        catch (final Throwable t)
        {
            ColonyAutopilot.LOGGER.warn("Could not clear Structurize future queue '{}' at shutdown", fieldName, t);
        }
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {

        final boolean repair = AutopilotConfig.masterOn();
        if (!repair)
        {
            activeScans.clear();
        }
        else if (!activeScans.isEmpty())
        {
            drainScans();
        }
        if (++tickCounter < PERIOD_TICKS)
        {
            return;
        }
        tickCounter = 0;

        final Set<ColonyId> live = new HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            live.add(ColonyAutopilot.colonyKey(colony));
            try
            {
                if (repair && AutopilotConfig.get(colony, AutopilotConfig.AUTO_REPAIR_DAMAGE))
                {
                    raidEdge(colony);
                    scanNext(colony);
                }
                sentinel(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Damage scan failed for colony {}", colony.getName(), e);
            }
        }
        ColonyAutopilot.retainColonies(live, cursor, scanPending, repasting, cooldownUntil, activeScans, wasRaided, ledger, healing);
    }

    private void raidEdge(final IColony colony)
    {
        final boolean raided = colony.getRaiderManager().isRaided();
        final Boolean before = wasRaided.put(ColonyAutopilot.colonyKey(colony), raided);
        if (Boolean.TRUE.equals(before) && !raided)
        {
            final Map<BlockPos, Long> cooldowns = cooldownUntil.get(ColonyAutopilot.colonyKey(colony));
            if (cooldowns != null)
            {
                cooldowns.clear();
            }
            ColonyAutopilot.LOGGER.info("[{}] raid ended — sweeping the village for battle damage", colony.getName());
        }
    }

    private void scanNext(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final long now = level.getGameTime();
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (activeScans.containsKey(key))
        {
            return;
        }

        final Long pending = scanPending.get(key);
        if (pending != null && now - pending < 2400)
        {
            return;
        }
        scanPending.remove(key);

        final List<IBuilding> buildings = List.copyOf(colony.getServerBuildingManager().getBuildings().values());
        if (buildings.isEmpty())
        {
            return;
        }
        final List<WorkOrderBuilding> openOrders = colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);

        final int start = cursor.getOrDefault(key, 0);
        for (int i = 0; i < buildings.size(); i++)
        {
            final int idx = (start + i) % buildings.size();
            final IBuilding building = buildings.get(idx);
            if (!scannable(level, key, building, openOrders, now))
            {
                continue;
            }
            cursor.put(key, idx + 1);
            beginScan(colony, key, level, building);
            return;
        }
        cursor.put(key, 0);
    }

    private boolean scannable(final ServerLevel level, final ColonyId key, final IBuilding building, final List<WorkOrderBuilding> openOrders, final long now)
    {

        if (building.isDeconstructed() && building.getBuildingLevel() > 0 && building.hasParent() && !BlockPos.ZERO.equals(building.getParent())
              && !UpgradeDirector.tornDown(building) && !UpgradeDirector.hasOpenOrder(building, openOrders))
        {
            building.setBuildingLevel(building.getBuildingLevel());
            ColonyAutopilot.LOGGER.info("[{}] the {} at {} was left flagged deconstructed by an earlier repair of its parent — the flag is cleared, repairs reach it again",
              building.getColony().getName(), SiteSelector.plainName(building), building.getPosition().toShortString());
        }
        if (building.getBuildingLevel() <= 0 || building.isDeconstructed())
        {
            return false;
        }
        if (now < cooldownUntil.getOrDefault(key, Map.of()).getOrDefault(building.getID(), 0L))
        {
            return false;
        }
        if (now < repasting.getOrDefault(key, Map.of()).getOrDefault(building.getID(), 0L))
        {
            return false;
        }

        final HealTask heal = healing.getOrDefault(key, Map.of()).get(building.getID());
        if (heal != null && now < heal.until())
        {
            return false;
        }

        if (UpgradeDirector.hasOpenOrder(building, openOrders)
              || director.repairPending(building.getColony(), building.getID()))
        {
            return false;
        }

        if (parentRaised(building, openOrders, now) || now < director.addedGraceUntil(building.getColony(), building.getID()))
        {
            return false;
        }

        final long grace = director.recycleGraceUntil(building.getColony(), building.getID());
        if (now < grace)
        {
            cooldownUntil.computeIfAbsent(key, k -> new HashMap<>()).put(building.getID(), Math.min(grace, now + RECYCLE_RECHECK_TICKS));
            ColonyAutopilot.LOGGER.debug("[{}] the {} at {} is not scanned for damage: the watchdog recycled its jammed order and files it again (held {} ticks more at most)",
              building.getColony().getName(), SiteSelector.plainName(building), building.getID().toShortString(), grace - now);
            return false;
        }
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        return level.isLoaded(corners.getA()) && level.isLoaded(corners.getB());
    }

    private boolean parentRaised(final IBuilding building, final List<WorkOrderBuilding> openOrders, final long now)
    {
        final IBuilding parent = building.getColony().getServerBuildingManager().getBuilding(building.getParent());
        return parent != null && parent != building
                 && (UpgradeDirector.hasOpenOrder(parent, openOrders) || director.repairPending(parent.getColony(), parent.getID())
                       || now < director.recycleGraceUntil(parent.getColony(), parent.getID()));
    }

    private void beginScan(final IColony colony, final ColonyId key, final ServerLevel level, final IBuilding building)
    {
        final String path = pathForLevel(building.getBlueprintPath(), building.getBuildingLevel());
        if (path == null)
        {
            return;
        }

        scanPending.put(key, level.getGameTime());
        final BlockPos buildingId = building.getID();
        final String pack = building.getStructurePack();
        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(pack, path, true, level.registryAccess()), level, blueprint -> {
            scanPending.remove(key);
            if (blueprint == null)
            {

                cooldownUntil.computeIfAbsent(key, k -> new HashMap<>())
                  .put(buildingId, level.getGameTime() + AutopilotConfig.get(colony, AutopilotConfig.DAMAGE_RESCAN_COOLDOWN_TICKS));
                ColonyAutopilot.LOGGER.debug("[{}] the damage scan found no blueprint '{}' in the pack '{}' for the building at {} — it waits out a rescan cooldown",
                  colony.getName(), path, pack, buildingId.toShortString());
                return;
            }
            try
            {
                finishScan(colony, key, level, buildingId, blueprint);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] damage scan of {} failed", colony.getName(), buildingId, e);
            }
        }));
    }

    private void finishScan(final IColony colony, final ColonyId key, final ServerLevel level, final BlockPos buildingId, final Blueprint blueprint)
    {
        final IBuilding building = colony.getServerBuildingManager().getBuilding(buildingId);
        if (building == null || blueprint == null || building.getBuildingLevel() <= 0)
        {
            return;
        }

        if (level.getGameTime() < cooldownUntil.getOrDefault(key, Map.of()).getOrDefault(buildingId, 0L))
        {
            return;
        }

        blueprint.setRotationMirror(RotationMirror.NONE, level);
        final BlockState anchorState = blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        final BlockState worldHut = level.getBlockState(building.getPosition());
        final RotationMirror rotationMirror;
        if (anchorState != null && anchorState.hasProperty(AbstractBlockHut.FACING) && worldHut.hasProperty(AbstractBlockHut.FACING))
        {
            final int structureRotation = anchorState.getValue(AbstractBlockHut.FACING).get2DDataValue();
            final int worldRotation = worldHut.getValue(AbstractBlockHut.FACING).get2DDataValue();
            final int steps = (4 + worldRotation - structureRotation) % 4;
            rotationMirror = RotationMirror.of(Rotation.values()[steps], building.getRotationMirror().mirror());
        }
        else
        {
            rotationMirror = building.getRotationMirror();
        }
        blueprint.setRotationMirror(rotationMirror, level);
        final BlockPos zero = building.getPosition().subtract(blueprint.getPrimaryBlockOffset());

        activeScans.put(key, new ScanJob(colony, buildingId, blueprint,
          zero, blueprint.getBlockState(blueprint.getPrimaryBlockOffset()), rotationMirror));
    }

    private void drainScans()
    {
        final var iterator = activeScans.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<ColonyId, ScanJob> entry = iterator.next();
            final ScanJob job = entry.getValue();
            if (!(job.colony.getWorld() instanceof ServerLevel level)
                  || job.blueprint.getBlockState(job.blueprint.getPrimaryBlockOffset()) != job.anchorWant)
            {
                iterator.remove();
                continue;
            }
            final boolean cureSpore = AutopilotConfig.live(job.colony, AutopilotConfig.SPORES_CREEP_GUARD);
            final boolean metered = AutopilotConfig.progressionMode(job.colony);

            boolean loaded = true;
            for (int cx = job.zero.getX() >> 4; loaded && cx <= (job.zero.getX() + job.blueprint.getSizeX() - 1) >> 4; cx++)
            {
                for (int cz = job.zero.getZ() >> 4; loaded && cz <= (job.zero.getZ() + job.blueprint.getSizeZ() - 1) >> 4; cz++)
                {
                    loaded = WorldUtil.isChunkLoaded(level, cx, cz);
                }
            }
            if (!loaded)
            {
                iterator.remove();
                continue;
            }
            final BlockPos.MutableBlockPos localPos = new BlockPos.MutableBlockPos();
            final BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
            final int lastLayer = Math.min(job.blueprint.getSizeY(), job.y + LAYERS_PER_TICK);
            int cures = 0;
            walk:
            for (; job.y < lastLayer && TickBudget.has(); job.y++)
            {

                if (job.chargedLayer != job.y)
                {
                    TickBudget.spend(job.blueprint.getSizeX() * job.blueprint.getSizeZ());
                    job.chargedLayer = job.y;
                }
                for (; job.x < job.blueprint.getSizeX(); job.x++)
                {
                    for (; job.z < job.blueprint.getSizeZ(); job.z++)
                    {
                        final BlockState want = job.blueprint.getBlockState(localPos.set(job.x, job.y, job.z));
                        if (!structuralExpectation(want))
                        {

                            if (metered && want != null && Exchange.familyValue(want.getBlock().asItem()) > 0
                                  && pasteReplaces(level.getBlockState(worldPos.set(job.zero.getX() + job.x, job.zero.getY() + job.y, job.zero.getZ() + job.z)), want))
                            {
                                job.familyCells.add(new BlockPos(job.x, job.y, job.z));
                            }
                            continue;
                        }
                        BlockState got = level.getBlockState(worldPos.set(job.zero.getX() + job.x, job.zero.getY() + job.y, job.zero.getZ() + job.z));

                        if (!got.is(want.getBlock()) && SporeCompat.isInfection(got.getBlock()))
                        {
                            if (cureSpore)
                            {
                                if (cures >= CURE_WRITES_PER_TICK || !TickBudget.has())
                                {
                                    break walk;
                                }

                                final boolean paidFor = metered && Exchange.familyValue(want.getBlock().asItem()) > 0;
                                WorldUtil.setBlockState(level, worldPos.immutable(), paidFor ? Blocks.AIR.defaultBlockState() : want);
                                TickBudget.spend(1);
                                job.cured++;
                                cures++;
                                if (!paidFor)
                                {
                                    continue;
                                }
                                got = Blocks.AIR.defaultBlockState();
                            }
                            else
                            {
                                job.infested++;
                            }
                        }

                        if (metered && pasteReplaces(got, want) && Exchange.familyValue(want.getBlock().asItem()) > 0)
                        {
                            job.familyCells.add(new BlockPos(job.x, job.y, job.z));
                        }
                        if (readsMissing(got, want))
                        {
                            job.missing++;
                            if (job.samples.size() < 6)
                            {
                                job.samples.add("expected " + BuiltInRegistries.BLOCK.getKey(want.getBlock())
                                                  + " at " + worldPos.toShortString()
                                                  + ", found " + BuiltInRegistries.BLOCK.getKey(got.getBlock()));
                            }
                        }
                    }
                    job.z = 0;
                }
                job.x = 0;
            }
            if (job.y >= job.blueprint.getSizeY())
            {
                iterator.remove();
                try
                {
                    verdict(entry.getKey(), level, job);
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("[{}] damage verdict for {} failed", job.colony.getName(), job.buildingId, e);
                }
            }
        }
    }

    static boolean structuralExpectation(final BlockState want)
    {
        return want != null && !want.isAir() && want.canOcclude() && !want.hasBlockEntity()
                 && !"structurize".equals(BuiltInRegistries.BLOCK.getKey(want.getBlock()).getNamespace());
    }

    static boolean readsMissing(final BlockState got, final BlockState want)
    {
        return !got.is(want.getBlock())
                 && (got.isAir() || got.is(Blocks.DIRT_PATH) || got.is(Blocks.FIRE) || !got.getFluidState().isEmpty());
    }

    static boolean pasteReplaces(final BlockState got, final BlockState want)
    {
        return !got.is(want.getBlock()) && (readsMissing(got, want) || SporeCompat.isInfection(got.getBlock()));
    }

    static boolean keepsWorld(final Level world, final BlockPos worldPos, final BlockState want)
    {
        if (want == null || "structurize".equals(BuiltInRegistries.BLOCK.getKey(want.getBlock()).getNamespace()))
        {
            return false;
        }
        final BlockState got = world.getBlockState(worldPos);
        return !got.is(want.getBlock()) && !pasteReplaces(got, want);
    }

    private void verdict(final ColonyId key, final ServerLevel level, final ScanJob job)
    {
        final IBuilding building = job.colony.getServerBuildingManager().getBuilding(job.buildingId);
        if (building == null || building.getBuildingLevel() <= 0)
        {
            return;
        }
        if (job.cured > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the creep guard restored {} infested blocks in the {} from its blueprint",
              job.colony.getName(), job.cured, building.getBuildingDisplayName());
            Milestones.alert(job.colony, Milestones.ALERT_INFECTED, "Your village is infected!");
        }
        final long now = level.getGameTime();
        final Map<BlockPos, Long> colonyCooldowns = cooldownUntil.computeIfAbsent(key, k -> new HashMap<>());
        colonyCooldowns.put(job.buildingId, now + AutopilotConfig.get(job.colony, AutopilotConfig.DAMAGE_RESCAN_COOLDOWN_TICKS));
        if (job.missing < AutopilotConfig.get(job.colony, AutopilotConfig.DAMAGE_THRESHOLD_BLOCKS))
        {
            return;
        }

        final List<WorkOrderBuilding> orders = job.colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class);
        if (UpgradeDirector.hasOpenOrder(building, orders) || director.repairPending(job.colony, job.buildingId)
              || parentRaised(building, orders, now))
        {
            return;
        }

        if (now < director.recycleGraceUntil(job.colony, job.buildingId))
        {
            ColonyAutopilot.LOGGER.debug("[{}] the {} misses {} block(s), but the watchdog has just recycled its jammed order and files it again — no re-paste",
              job.colony.getName(), SiteSelector.plainName(building), job.missing);
            return;
        }

        final int infection = job.cured + job.infested;
        if (infection > 0 && !job.familyCells.isEmpty())
        {
            ColonyAutopilot.LOGGER.debug("[{}] the repair of the {}: spore infection found ({} cell(s)), and its {} cell(s) of the exchange's goods are paid for all the same",
              job.colony.getName(), building.getBuildingDisplayName(), infection, job.familyCells.size());
        }

        final Set<BlockPos> paidGoods = AutopilotConfig.progressionMode(job.colony) ? new HashSet<>() : null;
        int paid = 0;
        int unpaid = 0;
        int unpaidMissing = 0;
        for (final BlockPos local : job.familyCells)
        {
            final BlockState want = job.blueprint.getBlockState(local);
            final BlockState got = level.getBlockState(job.zero.offset(local));
            if (want == null || !pasteReplaces(got, want))
            {
                continue;
            }
            if (Treasury.charge(job.colony, level, Treasury.cost(new ItemStack(want.getBlock())), Treasury.Line.REPAIRS))
            {
                paid++;
                if (paidGoods != null)
                {
                    paidGoods.add(job.zero.offset(local));
                }
            }
            else
            {
                unpaid++;

                if (structuralExpectation(want) && readsMissing(got, want))
                {
                    unpaidMissing++;
                }
            }
        }
        if (unpaid > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the repair of the {} leaves {} block(s) of the exchange's goods out: the treasury cannot pay for them",
              job.colony.getName(), building.getBuildingDisplayName(), unpaid);

            if (paid == 0 && job.missing - unpaidMissing < AutopilotConfig.get(job.colony, AutopilotConfig.DAMAGE_THRESHOLD_BLOCKS))
            {
                return;
            }
        }

        if (AutopilotConfig.progressionMode(job.colony))
        {
            stripContents(job.blueprint);
        }

        final CreativeBuildingStructureHandler handler = new CreativeBuildingStructureHandler(
          level, building.getPosition(), job.blueprint, job.rotationMirror, true);
        if (handler.hasBluePrint())
        {
            registerStandingChildren(level, building, job.blueprint, job.zero);

            repasting.computeIfAbsent(key, k -> new HashMap<>()).put(job.buildingId, Long.MAX_VALUE);

            Manager.addToQueue(new PacedCreativePaste(new StructurePlacer(handler), true, paidGoods,
              done -> repasting.computeIfAbsent(key, k -> new HashMap<>()).put(job.buildingId, done.getGameTime() + REPASTE_WINDOW_TICKS),
              failed -> cooldownUntil.computeIfAbsent(key, k -> new HashMap<>()).put(job.buildingId, failed.getGameTime() + FAILED_PASTE_COOLDOWN_TICKS)));
            ColonyAutopilot.LOGGER.info("[{}] the creep guard is re-pasting the {} from its blueprint — {} block(s) of structure were missing (no builder); e.g. {}",
              job.colony.getName(), building.getBuildingDisplayName(), job.missing, String.join("; ", job.samples));
        }
    }

    private void sentinel(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (!AutopilotConfig.live(colony, AutopilotConfig.SPORES_CREEP_GUARD) || !ModList.get().isLoaded(SporeCompat.MOD_ID))
        {
            ledger.remove(key);
            healing.remove(key);

            if (colony.getWorld() instanceof ServerLevel unwatched)
            {
                ColonyGrounds.get(unwatched).clearSentinelMemory(colony.getID());
            }
            return;
        }
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final Map<BlockPos, BuildingMemory> mem = ledger.computeIfAbsent(key, k -> savedMemory(colony, grounds));
        final Map<BlockPos, HealTask> tasks = healing.computeIfAbsent(key, k -> new HashMap<>());
        final long now = level.getGameTime();

        final Set<BlockPos> registered = new HashSet<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final BlockPos pos = building.getPosition();
            registered.add(pos);
            final HealTask task = tasks.get(pos);
            if (task != null)
            {
                if (now >= task.until())
                {
                    tasks.remove(pos);
                }
                else
                {
                    if (building.isDeconstructed() || building.getBuildingLevel() != task.level())
                    {
                        building.setBuildingLevel(task.level());
                    }
                    continue;
                }
            }

            if (UpgradeDirector.tornDown(building))
            {
                forget(colony, level, pos);
                continue;
            }
            if (building.getBuildingLevel() <= 0 || !WorldUtil.isChunkLoaded(level, pos.getX() >> 4, pos.getZ() >> 4))
            {
                continue;
            }
            final BlockState state = level.getBlockState(pos);
            final Block hutBlock = building.getBuildingType().getBuildingBlock();
            if (state.is(hutBlock) && state.hasProperty(AbstractBlockHut.FACING))
            {
                final BuildingMemory memory = new BuildingMemory(hutBlock, building.getBuildingLevel(),
                  state.getValue(AbstractBlockHut.FACING), building.getRotationMirror() == null ? RotationMirror.NONE : building.getRotationMirror(),
                  building.getStructurePack(), building.getBlueprintPath(),
                  grounds.hasAnchorOffset(colony.getID(), pos) ? grounds.anchorOffset(colony.getID(), pos, 1) : null,
                  grounds.front(colony.getID(), pos, null), false, director.repairPending(colony, pos));

                if (!memory.equals(mem.put(pos, memory)))
                {
                    grounds.setSentinelMemory(colony.getID(), pos, memory.saved());
                }
            }
            else
            {

                final BuildingMemory remembered = mem.get(pos);
                if (remembered != null && !remembered.takenInPlace() && !building.isDeconstructed()
                      && !(building.hasParent() && BlockPos.ZERO.equals(building.getParent())))
                {
                    final BuildingMemory taken = remembered.taken();
                    mem.put(pos, taken);
                    grounds.setSentinelMemory(colony.getID(), pos, taken.saved());
                    ColonyAutopilot.LOGGER.debug("[{}] the hut-block sentinel saw the {}'s hut block taken at {} while it was still registered — it will be raised again",
                      colony.getName(), SiteSelector.plainName(building), pos.toShortString());
                }
            }
        }

        for (final Map.Entry<BlockPos, BuildingMemory> entry : List.copyOf(mem.entrySet()))
        {
            final BlockPos pos = entry.getKey();
            if (registered.contains(pos) || !WorldUtil.isChunkLoaded(level, pos.getX() >> 4, pos.getZ() >> 4))
            {
                continue;
            }
            final HealTask task = tasks.get(pos);
            if (task != null && now < task.until())
            {
                continue;
            }
            if (!entry.getValue().takenInPlace() && !creepNearby(level, pos))
            {

                ColonyAutopilot.LOGGER.info("[{}] the hut-block sentinel forgets the {} at {}: no creep signature, taken for a player's removal",
                  colony.getName(), BuiltInRegistries.BLOCK.getKey(entry.getValue().hutBlock()).getPath(), pos.toShortString());
                forget(colony, level, pos);
                continue;
            }
            tasks.put(pos, new HealTask(entry.getValue().level(), now + HEAL_WINDOW_TICKS));
            try
            {
                healDeregistered(colony, level, pos, entry.getValue());
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the hut-block sentinel failed to rebuild the building at {}", colony.getName(), pos.toShortString(), e);
            }
        }
    }

    private Map<BlockPos, BuildingMemory> savedMemory(final IColony colony, final ColonyGrounds grounds)
    {
        final Map<BlockPos, BuildingMemory> mem = new HashMap<>();
        for (final Map.Entry<Long, ColonyGrounds.SentinelMemory> saved : grounds.sentinelMemory(colony.getID()).entrySet())
        {
            final BlockPos pos = BlockPos.of(saved.getKey());
            final BuildingMemory memory = BuildingMemory.of(saved.getValue(), director.repairPending(colony, pos));
            if (memory == null)
            {
                grounds.removeSentinelMemory(colony.getID(), pos);
                continue;
            }
            mem.put(pos, memory);
        }
        if (!mem.isEmpty())
        {
            ColonyAutopilot.LOGGER.debug("[{}] the hut-block sentinel remembers {} building(s) from the save", colony.getName(), mem.size());
        }
        return mem;
    }

    private void forget(final IColony colony, final ServerLevel level, final BlockPos pos)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<BlockPos, BuildingMemory> mem = ledger.get(key);
        if (mem != null)
        {
            mem.remove(pos);
        }

        final Map<BlockPos, HealTask> tasks = healing.get(key);
        if (tasks != null)
        {
            tasks.remove(pos);
        }
        ColonyGrounds.get(level).removeSentinelMemory(colony.getID(), pos);
    }

    private void healDeregistered(final IColony colony, final ServerLevel level, final BlockPos pos, final BuildingMemory snap)
    {

        if (snap.pack() == null || snap.pack().isBlank() || snap.blueprintPath() == null || snap.blueprintPath().isBlank())
        {
            ColonyAutopilot.LOGGER.debug("[{}] the hut-block sentinel forgets the {} at {}: it has no blueprint to raise it from (a hut placed by hand and never built)",
              colony.getName(), BuiltInRegistries.BLOCK.getKey(snap.hutBlock()).getPath(), pos.toShortString());
            forget(colony, level, pos);
            return;
        }
        final StructurePackMeta packMeta = StructurePacks.getStructurePack(snap.pack());
        if (packMeta == null)
        {
            ColonyAutopilot.LOGGER.warn("[{}] the hut-block sentinel has no structure pack '{}' to rebuild the hut at {} — it forgets the hut",
              colony.getName(), snap.pack(), pos.toShortString());
            forget(colony, level, pos);
            return;
        }
        final String path = pathForLevel(snap.blueprintPath(), snap.level());
        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(snap.pack(), path, true, level.registryAccess()), level, loaded -> {
            if (loaded == null)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the hut-block sentinel found no blueprint '{}' in the pack '{}' to rebuild the hut at {} — it forgets the hut",
                  colony.getName(), path, snap.pack(), pos.toShortString());
                forget(colony, level, pos);
                return;
            }
            if (colony.getServerBuildingManager().getBuilding(pos) != null)
            {
                return;
            }
            try
            {
                rebuild(colony, level, pos, snap, packMeta, path, loaded);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the hut-block sentinel failed mid-rebuild at {}", colony.getName(), pos.toShortString(), e);
            }
          }));
    }

    private void rebuild(final IColony colony, final ServerLevel level, final BlockPos pos, final BuildingMemory snap,
      final StructurePackMeta packMeta, final String path, final Blueprint loaded)
    {
        final RotationMirror rotMir = rotationFor(loaded, snap, level);

        if (!level.getBlockState(pos).is(snap.hutBlock()))
        {
            clearCreepAround(level, pos);
            level.setBlockAndUpdate(pos, snap.hutBlock().defaultBlockState().setValue(AbstractBlockHut.FACING, snap.facing()));
        }
        if (!(level.getBlockEntity(pos) instanceof TileEntityColonyBuilding hut))
        {
            ColonyAutopilot.LOGGER.warn("[{}] the hut-block sentinel re-placed a hut at {} but got no colony tile entity — it forgets the hut", colony.getName(), pos.toShortString());
            forget(colony, level, pos);
            return;
        }
        hut.setStructurePack(packMeta);
        hut.setBlueprintPath(path);

        final BlockState hutState = level.getBlockState(pos);
        final FakePlayer owner = FakePlayerFactory.get(level,
          new GameProfile(colony.getPermissions().getOwner(), colony.getPermissions().getOwnerName()));
        hutState.getBlock().setPlacedBy(level, pos, hutState, owner, new ItemStack(snap.hutBlock()));
        IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null)
        {
            building = colony.getServerBuildingManager().addNewBuilding(hut, level);
        }
        if (building == null)
        {
            ColonyAutopilot.LOGGER.warn("[{}] the hut-block sentinel could not re-register the building at {} — it forgets the hut", colony.getName(), pos.toShortString());
            forget(colony, level, pos);
            return;
        }
        building.setRotationMirror(rotMir);
        building.setBuildingLevel(snap.level());
        building.onUpgradeComplete(null, snap.level());

        for (final IBuilding other : List.copyOf(colony.getServerBuildingManager().getBuildings().values()))
        {
            if (other != building && other instanceof BuildingBarracks barracks && barracks.getTowers().contains(pos))
            {
                barracks.registerBlockPosition(level.getBlockState(pos), pos, level);
            }
        }

        ColonyGrounds.get(level).forgetBuilding(colony.getID(), pos);

        if (snap.owesRepair() || director.repairPending(colony, pos))
        {
            director.scheduleRepair(colony, pos);
            ColonyAutopilot.LOGGER.debug("[{}] the rebuilt {} at {} still owes its repair order", colony.getName(), SiteSelector.plainName(building),
              pos.toShortString());
        }
        if (snap.anchorOffset() != null)
        {
            ColonyGrounds.get(level).setAnchorOffset(colony.getID(), pos, snap.anchorOffset());
        }
        if (snap.front() != null)
        {
            ColonyGrounds.get(level).setFront(colony.getID(), pos, snap.front());
        }

        for (final WorkOrderBuilding order : List.copyOf(colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class)))
        {
            if (order.getLocation().equals(pos))
            {
                colony.getWorkManager().removeWorkOrder(order.getID());
            }
        }

        loaded.setRotationMirror(rotMir, level);
        registerStandingChildren(level, building, loaded, pos.subtract(loaded.getPrimaryBlockOffset()));
        final Set<BlockPos> paidGoods = AutopilotConfig.progressionMode(colony)
          ? payForGoods(colony, level, loaded, pos.subtract(loaded.getPrimaryBlockOffset()), building.getBuildingDisplayName()) : null;
        if (AutopilotConfig.progressionMode(colony))
        {
            stripContents(loaded);
        }
        writeHutSchematicData(level, pos, hut, loaded, packMeta);
        final CreativeBuildingStructureHandler handler = new CreativeBuildingStructureHandler(level, pos, loaded, rotMir, true);

        final IBuilding rebuilt = building;
        if (handler.hasBluePrint())
        {

            final ColonyId key = ColonyAutopilot.colonyKey(colony);
            healing.computeIfAbsent(key, k -> new HashMap<>()).put(pos, new HealTask(snap.level(), Long.MAX_VALUE));

            Manager.addToQueue(new PacedCreativePaste(new StructurePlacer(handler), false, paidGoods, done -> {
                healing.computeIfAbsent(key, k -> new HashMap<>()).put(pos, new HealTask(snap.level(), done.getGameTime() + HEAL_WINDOW_TICKS));
                director.queueOutfit(rebuilt);
            }, failed -> cooldownUntil.computeIfAbsent(key, k -> new HashMap<>()).put(pos, failed.getGameTime() + FAILED_PASTE_COOLDOWN_TICKS)));
        }
        else
        {
            director.queueOutfit(rebuilt);
        }
        ColonyAutopilot.LOGGER.info("[{}] the hut-block sentinel rebuilt the {} the creep had erased — re-registered at level {}, the creep guard is raising it from the blueprint",
          colony.getName(), building.getBuildingDisplayName(), snap.level());
    }

    private static Set<BlockPos> payForGoods(final IColony colony, final ServerLevel level, final Blueprint blueprint, final BlockPos zero, final String name)
    {
        int unpaid = 0;
        final Set<BlockPos> paid = new HashSet<>();
        for (final BlockInfo info : blueprint.getBlockInfoAsList())
        {
            final BlockState want = info.getState();
            if (want == null || Exchange.familyValue(want.getBlock().asItem()) <= 0)
            {
                continue;
            }
            final BlockPos world = zero.offset(info.getPos());
            final BlockState got = level.getBlockState(world);
            if (got.is(want.getBlock()))
            {
                continue;
            }
            if (Treasury.charge(colony, level, Treasury.cost(new ItemStack(want.getBlock())), Treasury.Line.REPAIRS))
            {
                paid.add(world);
            }
            else
            {
                unpaid++;
            }
        }
        if (!paid.isEmpty())
        {
            ColonyAutopilot.LOGGER.debug("[{}] the rebuild of the {} pays for {} block(s) of the exchange's goods", colony.getName(), name, paid.size());
        }
        if (unpaid > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the rebuild of the {} leaves {} block(s) of the exchange's goods out: the treasury cannot pay for them",
              colony.getName(), name, unpaid);
        }
        return paid;
    }

    private static void registerStandingChildren(final ServerLevel level, final IBuilding host, final Blueprint blueprint, final BlockPos zero)
    {
        for (final BlockInfo info : blueprint.getBlockInfoAsList())
        {
            final BlockState want = info.getState();
            if (want == null || !(want.getBlock() instanceof AbstractBlockHut))
            {
                continue;
            }
            final BlockPos world = zero.offset(info.getPos());
            if (!world.equals(host.getPosition()) && level.getBlockState(world).is(want.getBlock()))
            {
                host.registerBlockPosition(want, world, level);
            }
        }
    }

    private static void stripContents(final Blueprint blueprint)
    {
        for (final CompoundTag[][] layer : blueprint.getTileEntities())
        {
            for (final CompoundTag[] row : layer)
            {
                for (final CompoundTag tag : row)
                {
                    if (tag != null)
                    {
                        BLOCK_CONTENT_TAGS.forEach(tag::remove);
                    }
                }
            }
        }
        for (final CompoundTag tag : blueprint.getEntities())
        {
            if (tag != null)
            {
                ENTITY_CONTENT_TAGS.forEach(tag::remove);
            }
        }
    }

    private static void writeHutSchematicData(final ServerLevel level, final BlockPos pos, final TileEntityColonyBuilding hut,
      final Blueprint loaded, final StructurePackMeta packMeta)
    {
        final CompoundTag teData = loaded.getTileEntityData(pos, loaded.getPrimaryBlockOffset());
        if (teData == null || !teData.contains("blueprintDataProvider"))
        {
            return;
        }
        final CompoundTag tagData = teData.getCompound("blueprintDataProvider");
        tagData.putString("name", packMeta.getSubPath(Utils.resolvePath(loaded.getFilePath(), tagData.getString("name"))));
        tagData.putString("pack", loaded.getPackName());
        hut.readSchematicDataFromNBT(teData);
        level.getChunkSource().blockChanged(pos);
        hut.setChanged();
    }

    private RotationMirror rotationFor(final Blueprint blueprint, final BuildingMemory snap, final ServerLevel level)
    {
        blueprint.setRotationMirror(RotationMirror.NONE, level);
        final BlockState anchor = blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        if (anchor != null && anchor.hasProperty(AbstractBlockHut.FACING))
        {
            final int structureRotation = anchor.getValue(AbstractBlockHut.FACING).get2DDataValue();
            final int worldRotation = snap.facing().get2DDataValue();
            final int steps = (4 + worldRotation - structureRotation) % 4;
            return RotationMirror.of(Rotation.values()[steps], snap.rotationMirror().mirror());
        }
        return snap.rotationMirror();
    }

    private boolean creepNearby(final ServerLevel level, final BlockPos center)
    {
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -HEAL_CLEAR_RADIUS; dx <= HEAL_CLEAR_RADIUS; dx++)
        {
            for (int dz = -HEAL_CLEAR_RADIUS; dz <= HEAL_CLEAR_RADIUS; dz++)
            {
                for (int dy = -1; dy <= HEAL_CLEAR_RADIUS + 2; dy++)
                {
                    final Block block = level.getBlockState(p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz)).getBlock();
                    if (SporeCompat.isSporeBlock(block) && SporeCompat.isInfection(block))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void clearCreepAround(final ServerLevel level, final BlockPos center)
    {
        final Map<Block, Block> cure = SporeCompat.creepCure();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -HEAL_CLEAR_RADIUS; dx <= HEAL_CLEAR_RADIUS; dx++)
        {
            for (int dz = -HEAL_CLEAR_RADIUS; dz <= HEAL_CLEAR_RADIUS; dz++)
            {

                for (int dy = -1; dy <= HEAL_CLEAR_RADIUS + 2; dy++)
                {
                    final Block block = level.getBlockState(p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz)).getBlock();
                    final Block clean = cure.get(block);
                    if (clean != null)
                    {
                        WorldUtil.setBlockState(level, p.immutable(), clean.defaultBlockState());
                    }
                    else if (SporeCompat.isInfection(block))
                    {
                        WorldUtil.setBlockState(level, p.immutable(), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    static String pathForLevel(final String path, final int level)
    {
        if (path != null && path.endsWith(".blueprint"))
        {
            final String stem = path.substring(0, path.length() - ".blueprint".length());
            if (!stem.isEmpty() && Character.isDigit(stem.charAt(stem.length() - 1)))
            {
                return stem.substring(0, stem.length() - 1) + level + ".blueprint";
            }
        }
        return path;
    }

    private static final class PacedCreativePaste implements ITickedWorldOperation
    {
        private final StructurePlacer placer;
        private final boolean keepWorld;
        private final Set<BlockPos> paidGoods;

        private final Consumer<ServerLevel> onDone;

        private final Consumer<ServerLevel> onFailed;
        private int structurePhase = 0;
        private BlockPos currentPos = AbstractBlueprintIterator.NULL_POS;
        private long lastStepTick = Long.MIN_VALUE;
        private int stepsThisTick = 0;

        PacedCreativePaste(final StructurePlacer placer, final boolean keepWorld, final Set<BlockPos> paidGoods, final Consumer<ServerLevel> onDone,
          final Consumer<ServerLevel> onFailed)
        {
            this.placer = placer;
            this.keepWorld = keepWorld;
            this.paidGoods = paidGoods;
            this.onDone = onDone;
            this.onFailed = onFailed;
        }

        private boolean leaves(final Level world, final BlockPos pos, final BlockState want)
        {
            return standingHut(world, pos, want) || skips(world, pos, want) || holdsUp(world, pos);
        }

        private static boolean standingHut(final Level world, final BlockPos pos, final BlockState want)
        {
            return want != null && want.getBlock() instanceof AbstractBlockHut && world.getBlockState(pos).is(want.getBlock());
        }

        private boolean skips(final Level world, final BlockPos pos, final BlockState want)
        {
            return keepWorld && keepsWorld(world, pos, want)
                     || paidGoods != null && want != null && Exchange.familyValue(want.getBlock().asItem()) > 0 && !paidGoods.contains(pos);
        }

        private boolean holdsUp(final Level world, final BlockPos pos)
        {
            if (paidGoods != null && paidGoods.contains(pos))
            {
                return false;
            }
            final BlockPos over = pos.above();
            final BlockState held = world.getBlockState(over);
            if (held.isAir())
            {
                return false;
            }
            final BlockState support = world.getBlockState(pos);
            if (!support.isFaceSturdy(world, pos, Direction.UP) || SporeCompat.isInfection(support.getBlock()))
            {
                return false;
            }
            final BlockInfo info = placer.getHandler().getBluePrint().getBlockInfoAsMap().get(placer.getHandler().getStructurePosFromWorld(over));
            final BlockState want = info == null ? null : info.getState();
            return want != null && !held.is(want.getBlock()) && skips(world, over, want);
        }

        @Override
        public boolean apply(final ServerLevel world)
        {

            final Level home = placer.getHandler().getWorld();
            if (home != world)
            {
                return !(home instanceof ServerLevel homeLevel) || homeLevel.getServer() != world.getServer();
            }

            try
            {
                if (!placer.isReady())
                {
                    return false;
                }

                final long tick = world.getGameTime();
                if (tick != lastStepTick)
                {
                    lastStepTick = tick;
                    stepsThisTick = 0;
                }
                if (stepsThisTick >= PASTE_STEPS_PER_TICK)
                {
                    return currentPos == null;
                }
                stepsThisTick++;

                StructurePhasePlacementResult result;
                switch (structurePhase)
                {
                    case 0:

                        result = placer.executeStructureStep(world, null, currentPos, StructurePlacer.Operation.BLOCK_PLACEMENT,
                          () -> placer.getIterator().increment((info, pos, handler) -> !BlockUtils.canBlockFloatInAir(info.getBlockInfo().getState())
                                                                            || leaves(world, pos, info.getBlockInfo().getState())), false);
                        currentPos = result.getIteratorPos();
                        break;
                    case 1:

                        result = placer.executeStructureStep(world, null, currentPos, StructurePlacer.Operation.BLOCK_PLACEMENT,
                          () -> placer.getIterator().increment((info, pos, handler) -> !BlockUtils.isWeakSolidBlock(info.getBlockInfo().getState())
                                                                            || leaves(world, pos, info.getBlockInfo().getState())), false);
                        currentPos = result.getIteratorPos();
                        break;
                    case 2:

                        result = placer.clearWaterStep(world, currentPos);
                        currentPos = result.getIteratorPos();
                        if (result.getBlockResult().getResult() == Result.FINISHED)
                        {
                            currentPos = placer.getIterator().getProgressPos();
                        }
                        break;
                    case 3:

                        result = placer.executeStructureStep(world, null, currentPos, StructurePlacer.Operation.BLOCK_PLACEMENT,
                          () -> placer.getIterator().increment((info, pos, handler) -> BlockUtils.isAnySolid(info.getBlockInfo().getState())
                                                                            || leaves(world, pos, info.getBlockInfo().getState())), false);
                        currentPos = result.getIteratorPos();
                        break;
                    default:

                        result = placer.executeStructureStep(world, null, currentPos, StructurePlacer.Operation.SPAWN_ENTITY,
                          () -> placer.getIterator().increment((info, pos, handler) -> info.getEntities().length == 0), true);
                        currentPos = result.getIteratorPos();
                        break;
                }

                if (result.getBlockResult().getResult() == Result.FINISHED)
                {
                    structurePhase++;
                    if (structurePhase > 4)
                    {
                        structurePhase = 0;
                        currentPos = null;
                        placer.getHandler().onCompletion();
                        onDone.accept(world);
                    }
                }
                return currentPos == null;
            }
            catch (final RuntimeException e)
            {

                ColonyAutopilot.LOGGER.warn("the creative paste at {} failed and was dropped", placer.getHandler().getCenterPos().toShortString(), e);
                currentPos = null;
                onDone.accept(world);
                if (onFailed != null)
                {
                    onFailed.accept(world);
                }
                return true;
            }
        }

        @Override
        public ChangeStorage getChangeStorage()
        {
            return NO_UNDO;
        }
    }
}
