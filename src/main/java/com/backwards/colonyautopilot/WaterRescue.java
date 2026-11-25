// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGateHouse;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.colony.jobs.JobFisherman;
import com.minecolonies.core.colony.jobs.guard.JobCavalry;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.SittingEntity;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WaterRescue
{
    private static final int PERIOD_TICKS = 100;

    private static final long TRAPPED_TICKS = 600L;

    private static final long ADRIFT_TICKS = 2400L;

    private static final int BODY_CAP = 256;

    private int tickCounter = 19;

    private final ColonyRota rota = new ColonyRota();

    private final Map<ColonyId, Map<UUID, long[]>> wet = new HashMap<>();

    private final Map<ColonyId, Map<Long, Long>> told = new HashMap<>();

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        wet.clear();
        told.clear();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {
            wet.clear();
            told.clear();
            return;
        }
        if (++tickCounter >= PERIOD_TICKS)
        {
            tickCounter = 0;
            ColonyAutopilot.retainColonies(rota.fill(event.getServer()), wet, told);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {
            try
            {
                look(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Water rescue failed for colony {}", colony.getName(), e);
            }
        }
    }

    static boolean openWater(final BlockState state)
    {
        return state.is(Blocks.WATER) ? state.getFluidState().isSource()
                 : state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    static boolean climbableBank(final ServerLevel level, final BlockPos cell)
    {
        final BlockState floor = level.getBlockState(cell);
        if (floor.is(BlockTags.CLIMBABLE) || (floor.getFluidState().isEmpty() && level.getBlockState(cell.above()).is(BlockTags.CLIMBABLE)))
        {
            return true;
        }
        return floor.getFluidState().isEmpty() && !floor.is(Blocks.ICE) && !floor.is(Blocks.FROSTED_ICE)
                 && floor.getCollisionShape(level, cell).max(Direction.Axis.Y) <= 1.0
                 && clear(level, cell.above()) && clear(level, cell.above(2));
    }

    static boolean clear(final ServerLevel level, final BlockPos cell)
    {
        final BlockState state = level.getBlockState(cell);
        return state.getCollisionShape(level, cell).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean overOpenWater(final ServerLevel level, final BlockPos feet)
    {
        return openWater(level.getBlockState(feet)) || openWater(level.getBlockState(feet.below()));
    }

    private void look(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final Map<UUID, long[]> clocks = wet.computeIfAbsent(ColonyAutopilot.colonyKey(colony), key -> new HashMap<>());
        final Set<UUID> wetNow = new HashSet<>();

        final Map<Long, Long> counted = told.computeIfAbsent(ColonyAutopilot.colonyKey(colony), key -> new HashMap<>());
        final long oldest = clocks.values().stream().mapToLong(clock -> clock[0]).min().orElse(Long.MAX_VALUE);
        counted.values().removeIf(at -> at < oldest);
        if (AutopilotConfig.get(colony, AutopilotConfig.JOB_WATCH))
        {
            for (final ICitizenData data : colony.getCitizenManager().getCitizens())
            {
                final AbstractEntityCitizen citizen = data.getEntity().orElse(null);
                if (citizen != null && lookAt(level, colony, citizen, data, clocks, counted))
                {
                    wetNow.add(citizen.getUUID());
                }
            }

            for (final IAnimalData animal : colony.getAnimalManager().getAnimalsOfClassByHome(CavalryHorseEntity.class, null))
            {
                if (animal.getManagedAnimal().orElse(null) instanceof CavalryHorseEntity mount && lookAt(level, colony, mount, null, clocks, counted))
                {
                    wetNow.add(mount.getUUID());
                }
            }
        }
        if (AutopilotConfig.get(colony, AutopilotConfig.VILLAGE_GOLEM))
        {
            for (final IronGolem golem : GolemKeeper.guardians(colony))
            {
                if (lookAt(level, colony, golem, null, clocks, counted))
                {
                    wetNow.add(golem.getUUID());
                }
            }
        }
        clocks.keySet().retainAll(wetNow);
    }

    private static boolean lookAt(final ServerLevel level, final IColony colony, final Mob entity, final ICitizenData data,
      final Map<UUID, long[]> clocks, final Map<Long, Long> counted)
    {

        final Mob body = entity.getVehicle() instanceof CavalryHorseEntity mount ? mount : entity;
        final boolean wet = entity.isInWater() || body.isInWater();
        if (!entity.isAlive() || entity.level() != level || !(wet || clocks.containsKey(entity.getUUID()))
              || !level.isPositionEntityTicking(body.blockPosition()))
        {
            return false;
        }
        if (body == entity && entity.isPassenger())
        {

            final BlockPos nap = entity.getVehicle() instanceof SittingEntity seat
                                   ? BlockPos.containing(seat.getX(), seat.getY() + entity.getType().getDimensions().height() / 2 + 0.26, seat.getZ()) : null;
            if (nap == null || !level.getBlockState(nap).getCollisionShape(level, nap).isEmpty() || !overOpenWater(level, nap))
            {
                return false;
            }
        }

        if (!wet && (body.onGround() || !overOpenWater(level, body.blockPosition())))
        {
            return false;
        }
        final long now = level.getGameTime();
        final long[] clock = clocks.computeIfAbsent(entity.getUUID(), key -> new long[] {now, now + TRAPPED_TICKS, 0, Long.MAX_VALUE});
        if (entity instanceof CavalryHorseEntity && entity.isVehicle())
        {
            return true;
        }

        final boolean drowning = entity.getAirSupply() <= 0 || (body != entity && body.getAirSupply() <= PERIOD_TICKS);
        if (!drowning && (now < clock[1] || !TickBudget.has()))
        {
            return true;
        }
        final long inWater = now - clock[0];
        final long adrift = clock[0] + ADRIFT_TICKS;
        clock[1] = now < adrift ? Math.min(now + TRAPPED_TICKS, adrift) : now + TRAPPED_TICKS;
        final boolean golem = entity instanceof IronGolem;
        final String who = data == null ? "the " + entity.getName().getString() : data.getName() + (body == entity ? "" : " on his mount");
        final BlockPos from = body.blockPosition();

        final boolean nowhere = clock[3] != Long.MAX_VALUE && BlockPosUtil.getDistanceSquared2D(from, BlockPos.of(clock[3])) < 8 * 8;
        clock[3] = BlockPos.asLong(from.getX(), 0, from.getZ());

        if (data != null && !drowning && data.getJob() instanceof JobFisherman fisher && entity instanceof EntityCitizen worker
              && worker.getCitizenAI().getState() == CitizenAIState.WORKING && fisher.getWater() != null
              && BlockPosUtil.dist(from, fisher.getWater().getB()) <= 4)
        {
            sayOnce(clock, colony, who, from, inWater, "he is the fisherman, at work; not moved");
            return true;
        }
        if (SiteSelector.insideAny(ProtectedZones.footprintsFor(colony), from.getX(), from.getZ()))
        {
            sayOnce(clock, colony, who, from, inWater, "it lies in a protected zone; not moved");
            return true;
        }

        final Survey water = Survey.of(level, from);
        if (water == null)
        {

            if (drowning && data != null && body == entity && recall(level, (AbstractEntityCitizen) entity, data.getHomePosition()))
            {
                ColonyAutopilot.LOGGER.info("[{}] {} ran out of air in the water at {} after {} seconds, where no bank can be read — sent home to {}",
                  colony.getName(), who, from.toShortString(), inWater / 20, entity.blockPosition().toShortString());
                return false;
            }

            final BuildingStable stall = drowning && (body != entity || data == null) ? recallToStall(level, body) : null;
            if (stall != null)
            {
                entity.getNavigation().stop();
                body.setDeltaMovement(Vec3.ZERO);
                ColonyAutopilot.LOGGER.info("[{}] {} ran out of air in the water at {} after {} seconds, where no bank can be read — recalled to a stall of the Stable at {}",
                  colony.getName(), who, from.toShortString(), inWater / 20, stall.getPosition().toShortString());
                return false;
            }
            return true;
        }

        if (entity instanceof AbstractEntityCitizen citizen && data.getWorkBuilding() instanceof AbstractBuildingGuards guards
              && !(guards instanceof BuildingGateHouse) && GuardTaskSetting.GUARD.equals(guards.getTask()))
        {
            final BlockPos post = guards.getGuardPos(citizen);
            if (post != null && post.getY() <= water.top + 1 && water.body.contains(BlockPos.asLong(post.getX(), 0, post.getZ())))
            {
                sayOnce(clock, colony, who, from, inWater, "his guard position, the player's, lies in this water; not moved");
                return true;
            }
        }

        final boolean following = data != null && data.getWorkBuilding() instanceof AbstractBuildingGuards tower
                                    && GuardTaskSetting.FOLLOW.equals(tower.getTask())
                                    && tower.getPlayerToFollowOrRally() instanceof ServerPlayer leader && leader.level() == level
                                    && BlockPosUtil.getDistance2D(from, leader.blockPosition()) <= 30;

        final String stay;
        if (golem)
        {
            stay = "was stuck in the water at " + from.toShortString() + " for " + inWater / 20 + " seconds";
        }
        else if (drowning)
        {
            stay = (entity.getAirSupply() <= 0 ? "ran out of air" : "was going under, his mount's air nearly out,")
                     + " in the water at " + from.toShortString() + " after " + inWater / 20 + " seconds";
        }
        else if (water.trapped())
        {
            stay = "was stuck in the water at " + from.toShortString() + " for " + inWater / 20 + " seconds — none of its banks is low enough to climb out of";
        }
        else if ((inWater >= ADRIFT_TICKS || (data != null && nowhere && !following)) && water.afloat && !colony.getRaiderManager().isRaided())
        {

            stay = "was afloat in the water at " + from.toShortString() + " for " + inWater / 20 + " seconds without reaching land";
        }
        else
        {
            sayOnce(clock, colony, who, from, inWater, water.why() + "; left to swim out");
            return true;
        }

        final double[] spot = water.landing(level, colony, body, body == entity ? null : entity);
        if (spot != null)
        {
            entity.getNavigation().stop();
            if (body != entity)
            {
                body.getNavigation().stop();
            }
            else if (entity.isPassenger())
            {
                entity.stopRiding();
            }
            body.teleportTo(spot[0], spot[1], spot[2]);

            body.setDeltaMovement(Vec3.ZERO);

            ColonyAutopilot.LOGGER.info("[{}] {} {} — moved onto the bank at {}", colony.getName(), who, stay, body.blockPosition().toShortString());
            if (!golem)
            {
                tell(level, colony, who, from, body.blockPosition(), water, counted, clock[0]);
            }
            return false;
        }
        if (body != entity || (data == null && !golem))
        {

            final BuildingStable stable = recallToStall(level, body);
            if (stable != null)
            {
                entity.getNavigation().stop();
                body.setDeltaMovement(Vec3.ZERO);
                ColonyAutopilot.LOGGER.info("[{}] {} {} — no dry bank beside it holds a mount; recalled to a stall of the Stable at {}",
                  colony.getName(), who, stay, stable.getPosition().toShortString());

                tell(level, colony, who, from, new BlockPos(from.getX(), water.top + 1, from.getZ()), water, counted, clock[0]);
                return false;
            }
            sayOnce(clock, colony, who, from, inWater, "no dry bank beside it holds a mount" + (body == entity ? "" : " with its rider") + "; not moved");
            return true;
        }
        if (data != null)
        {
            if (!recall(level, (AbstractEntityCitizen) entity, data.getHomePosition()))
            {
                return true;
            }
            ColonyAutopilot.LOGGER.info("[{}] {} {}, and no dry bank lies beside it — sent home to {}", colony.getName(), who, stay,
              entity.blockPosition().toShortString());

            tell(level, colony, who, from, new BlockPos(from.getX(), water.top + 1, from.getZ()), water, counted, clock[0]);
            return false;
        }

        entity.discard();
        ColonyAutopilot.LOGGER.info("[{}] {} {}, and no dry bank lies beside it, so it was dismissed; its post forges a fresh one",
          colony.getName(), who, stay);
        return false;
    }

    private static void sayOnce(final long[] clock, final IColony colony, final String who, final BlockPos from, final long inWater, final String why)
    {
        if (clock[2] == 0)
        {
            clock[2] = 1;
            ColonyAutopilot.LOGGER.debug("[{}] {} has been in the water at {} for {} seconds — {}", colony.getName(), who, from.toShortString(),
              inWater / 20, why);
        }
    }

    private static void tell(final ServerLevel level, final IColony colony, final String who, final BlockPos from,
      final BlockPos to, final Survey water, final Map<Long, Long> counted, final long since)
    {
        if (!water.trapped())
        {
            return;
        }
        final Long last = counted.get(BlockPos.asLong(from.getX(), 0, from.getZ()));
        if (last != null && since <= last)
        {
            return;
        }
        final long now = level.getGameTime();
        counted.put(BlockPos.asLong(from.getX(), 0, from.getZ()), now);
        for (final long column : water.body)
        {
            counted.put(column, now);
        }
        ProblemLedger.stuckInWater(level, colony, who, from, to);
    }

    static boolean recall(final ServerLevel level, final AbstractEntityCitizen citizen, final BlockPos building)
    {
        return building != null && loaded(level, new AABB(building).inflate(6)) && TeleportHelper.teleportCitizen(citizen, level, building);
    }

    static BuildingStable recallToStall(final ServerLevel level, final Mob body)
    {
        return body instanceof CavalryHorseEntity mount && mount.getAnimalData() != null
                 && mount.getAnimalData().getHomeBuilding() instanceof BuildingStable stable && stable.getBuildingLevel() > 0
                 && Paddocks.toStall(level, stable, mount) ? stable : null;
    }

    public static boolean recallFromWall(final ServerLevel level, final AbstractEntityCitizen citizen)
    {
        final ICitizenData data = citizen.getCitizenData();
        final IBuilding work = data == null ? null : data.getWorkBuilding();
        final BlockPos from = citizen.blockPosition();
        if (work == null || !AutopilotConfig.get(data.getColony(), AutopilotConfig.JOB_WATCH)
              || citizen.isInWater() || !citizen.canBeStuck() || data.getColony().getRaiderManager().isRaided()
              || !recall(level, citizen, work.getPosition()))
        {
            return false;
        }
        ColonyAutopilot.LOGGER.debug("[{}] {} was lifted out of a wall around {} for the third time in a game-minute — recalled to the {} at {}",
          data.getColony().getName(), data.getName(), from.toShortString(), SiteSelector.plainName(work), work.getPosition().toShortString());
        if (work instanceof BuildingStable stable && data.getJob() instanceof JobCavalry rider && rider.getMount() != null
              && level.getEntity(rider.getMount()) instanceof CavalryHorseEntity mount && mount.isAlive() && !mount.isVehicle()
              && !mount.isLeashed())
        {
            Paddocks.toStall(level, stable, mount);
        }
        return true;
    }

    private static boolean loaded(final ServerLevel level, final AABB box)
    {
        return DamageWatch.chunksLoaded(level, Mth.floor(box.minX), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxZ));
    }

    private static final class Survey
    {

        final int top;

        final boolean afloat;

        final Set<Long> body = new HashSet<>();

        final boolean covered;

        final BlockPos start;

        BlockPos exit;

        String cutShort;

        private int reads;

        private Survey(final BlockPos start, final int top, final boolean afloat, final boolean covered)
        {
            this.start = start;
            this.top = top;
            this.afloat = afloat;
            this.covered = covered;
        }

        static Survey of(final ServerLevel level, final BlockPos feet)
        {

            BlockPos at = openWater(level.getBlockState(feet)) ? feet : openWater(level.getBlockState(feet.below())) ? feet.below() : null;

            if (at == null && level.getBlockState(feet).getFluidState().is(FluidTags.WATER) && openWater(level.getBlockState(feet.above())))
            {
                at = feet.above();
            }
            if (at == null)
            {
                TickBudget.spend(2);
                return null;
            }
            int top = at.getY();
            while (openWater(level.getBlockState(new BlockPos(at.getX(), top + 1, at.getZ()))))
            {
                top++;
            }
            final BlockPos over = new BlockPos(at.getX(), top + 1, at.getZ());
            final Survey water = new Survey(at, top, openWater(level.getBlockState(new BlockPos(at.getX(), top - 1, at.getZ()))),
              !level.getBlockState(over).getCollisionShape(level, over).isEmpty());
            water.reads = 4 + top - at.getY();
            water.walk(level);
            TickBudget.spend(water.reads);
            return water;
        }

        private void walk(final ServerLevel level)
        {
            final ArrayDeque<Long> queue = new ArrayDeque<>();
            final long first = BlockPos.asLong(start.getX(), 0, start.getZ());
            body.add(first);
            queue.add(first);
            while (!queue.isEmpty() && cutShort == null)
            {
                final long column = queue.poll();
                final int x = BlockPos.getX(column);
                final int z = BlockPos.getZ(column);
                final BlockPos surface = new BlockPos(x, top + 1, z);
                final BlockState over = level.getBlockState(surface);
                reads++;
                if (!over.getFluidState().isEmpty())
                {
                    cutShort = "its surface is not level";
                    break;
                }
                final boolean open = over.getCollisionShape(level, surface).isEmpty();
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final int nx = x + side.getStepX();
                    final int nz = z + side.getStepZ();
                    final long key = BlockPos.asLong(nx, 0, nz);
                    if (body.contains(key))
                    {
                        continue;
                    }
                    if (!WorldUtil.isChunkLoaded(level, nx >> 4, nz >> 4))
                    {
                        cutShort = "it runs on into unloaded ground";
                        break;
                    }
                    final BlockPos beside = new BlockPos(nx, top, nz);
                    final BlockState state = level.getBlockState(beside);
                    reads++;
                    if (openWater(state))
                    {
                        reads++;
                        if (level.getBlockState(beside.above()).getCollisionShape(level, beside.above()).isEmpty())
                        {
                            body.add(key);
                            if (body.size() > BODY_CAP)
                            {
                                cutShort = "it is too large to see whole (over " + BODY_CAP + " columns)";
                                break;
                            }
                            queue.add(key);
                        }
                    }
                    else if (state.getFluidState().is(FluidTags.WATER) && state.getCollisionShape(level, beside).isEmpty())
                    {

                        cutShort = "it runs on into moving water";
                        break;
                    }
                    else if (open && exit == null)
                    {
                        reads += 3;
                        if (climbableBank(level, beside))
                        {
                            exit = beside;
                        }
                    }
                }
            }
        }

        boolean trapped()
        {
            return cutShort == null && exit == null;
        }

        String why()
        {
            return exit != null ? "it has a way out he can climb at " + exit.toShortString() : cutShort;
        }

        double[] landing(final ServerLevel level, final IColony colony, final Mob entity, final Mob rider)
        {
            reads = 0;
            double[] secondBest = null;
            final Map<Long, Long> candidates = new LinkedHashMap<>();
            if (covered)
            {
                final long own = BlockPos.asLong(start.getX(), 0, start.getZ());
                candidates.put(own, own);
            }
            for (final long column : body)
            {
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final long next = BlockPos.asLong(BlockPos.getX(column) + side.getStepX(), 0, BlockPos.getZ(column) + side.getStepZ());
                    if (!body.contains(next))
                    {
                        candidates.putIfAbsent(next, next);
                    }
                }
            }
            for (final long column : new ArrayList<>(candidates.keySet()))
            {
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final long next = BlockPos.asLong(BlockPos.getX(column) + side.getStepX(), 0, BlockPos.getZ(column) + side.getStepZ());
                    if (!body.contains(next))
                    {
                        candidates.putIfAbsent(next, column);
                    }
                }
            }
            final List<Long> order = new ArrayList<>(candidates.keySet());
            order.sort(Comparator.comparingDouble(column -> Math.pow(BlockPos.getX(column) + 0.5 - entity.getX(), 2)
                                                              + Math.pow(BlockPos.getZ(column) + 0.5 - entity.getZ(), 2)));
            final List<SiteSelector.Footprint> zones = ProtectedZones.footprintsFor(colony);
            final double seat = rider == null ? 0 : Math.max(0, rider.getY() - entity.getY());
            try
            {
                for (final long column : order)
                {
                    final int x = BlockPos.getX(column);
                    final int z = BlockPos.getZ(column);
                    final long via = candidates.get(column);

                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4)
                          || (via != column && !WorldUtil.isChunkLoaded(level, BlockPos.getX(via) >> 4, BlockPos.getZ(via) >> 4)))
                    {
                        continue;
                    }
                    for (int y = top + 1; y <= top + 3; y++)
                    {
                        final BlockPos feet = new BlockPos(x, y, z);
                        final double floorTop = firmFloor(level, feet.below());
                        if (floorTop < 0 || hazard(level.getBlockState(feet)) || hazard(level.getBlockState(feet.above()))
                              || (via != column && !(clear(level, new BlockPos(BlockPos.getX(via), y, BlockPos.getZ(via)))
                                                       && clear(level, new BlockPos(BlockPos.getX(via), y + 1, BlockPos.getZ(via))))))
                        {
                            reads += 5;
                            continue;
                        }
                        AABB box = entity.getDimensions(Pose.STANDING).makeBoundingBox(x + 0.5, y - 1 + floorTop, z + 0.5);
                        if (rider != null)
                        {
                            box = box.minmax(rider.getDimensions(Pose.STANDING).makeBoundingBox(x + 0.5, y - 1 + floorTop + seat, z + 0.5));
                        }
                        reads += 12;
                        if (!loaded(level, box) || !level.noCollision(entity, box) || level.containsAnyLiquid(box) || !standingRoomBeside(level, feet))
                        {
                            continue;
                        }
                        if (SiteSelector.insideAny(zones, x, z) || IColonyManager.getInstance().getColonyByPosFromWorld(level, feet) != colony)
                        {
                            break;
                        }
                        final double[] spot = {x + 0.5, y - 1 + floorTop, z + 0.5};

                        final AABB room = box.setMaxY(box.minY + 2.65);
                        final boolean low = rider == null && entity instanceof CavalryHorseEntity
                                              && !(level.noCollision(entity, room) && !level.containsAnyLiquid(room));

                        reads += (int) (Math.ceil(box.maxX) - Math.floor(box.minX)) * (int) (Math.ceil(box.maxZ) - Math.floor(box.minZ))
                                   * ((int) Math.ceil(box.maxY) - top);
                        if (!low && !level.containsAnyLiquid(box.setMinY(top)))
                        {
                            return spot;
                        }
                        if (secondBest == null)
                        {
                            secondBest = spot;
                        }
                        break;
                    }
                }
                return secondBest;
            }
            finally
            {
                TickBudget.spend(reads);
            }
        }

        private boolean standingRoomBeside(final ServerLevel level, final BlockPos feet)
        {
            for (final Direction side : Direction.Plane.HORIZONTAL)
            {
                final BlockPos next = feet.relative(side);
                reads += 3;
                if (!body.contains(BlockPos.asLong(next.getX(), 0, next.getZ())) && WorldUtil.isChunkLoaded(level, next.getX() >> 4, next.getZ() >> 4)
                      && firmFloor(level, next.below()) >= 0 && clear(level, next) && clear(level, next.above()))
                {
                    return true;
                }
            }
            return false;
        }
    }

    private static double firmFloor(final ServerLevel level, final BlockPos pos)
    {
        final BlockState floor = level.getBlockState(pos);
        final double top = floor.getCollisionShape(level, pos).max(Direction.Axis.Y);
        return top >= 0.9375 && top <= 1.0 && floor.getFluidState().isEmpty() && !harmfulFloor(floor)
                 && !floor.is(Blocks.CACTUS) && !floor.is(Blocks.POWDER_SNOW) ? top : -1;
    }

    static boolean harmfulFloor(final BlockState floor)
    {
        return floor.is(Blocks.MAGMA_BLOCK) || floor.is(BlockTags.CAMPFIRES) || floor.is(BlockTags.LOGS) || floor.is(BlockTags.LEAVES);
    }

    private static boolean hazard(final BlockState state)
    {
        return state.is(BlockTags.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB) || state.is(Blocks.WITHER_ROSE)
                 || state.is(Blocks.POWDER_SNOW);
    }
}
