// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProblemLedger
{
    private static final int CAPACITY = 20;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private static final ArrayDeque<String> RECENT = new ArrayDeque<>();

    private static final Set<String> announced = new HashSet<>();

    private static final int ANNOUNCED_CAP = 256;

    private static int pendingPushes = 0;

    private ProblemLedger()
    {
    }

    static void arm()
    {
        try
        {
            final AbstractAppender listener = new AbstractAppender("colonyautopilot-problems", null, null, true, Property.EMPTY_ARRAY)
            {
                @Override
                public void append(final LogEvent event)
                {
                    if (!event.getLoggerName().startsWith("com.backwards.colonyautopilot")
                          || !event.getLevel().isMoreSpecificThan(Level.WARN)
                          || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.PROBLEMS_ENABLED.get())
                    {
                        return;
                    }
                    final String clock = CLOCK.format(Instant.ofEpochMilli(event.getTimeMillis()).atZone(ZoneId.systemDefault()));
                    final Throwable thrown = event.getThrown();
                    final String body = event.getMessage().getFormattedMessage()
                             + (thrown == null ? "" : " — " + thrown.getClass().getSimpleName() + (thrown.getMessage() == null ? "" : ": " + thrown.getMessage()));

                    record(clock + " " + body, body);
                }
            };
            listener.start();
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().getRootLogger().addAppender(listener, Level.WARN, null);
            context.updateLoggers();
        }
        catch (final Exception e)
        {
            ColonyAutopilot.LOGGER.warn("The problem ledger could not attach to the logger — '/colonyautopilot problems' will stay empty", e);
        }
    }

    private static final Map<String, long[]> stuckCells = new HashMap<>();

    private static final Set<GlobalPos> STUCK_SPOTS = new LinkedHashSet<>();

    private static final int STUCK_SPOTS_CAP = 64;

    public static synchronized GlobalPos stuckSpot(final BlockPos pos)
    {
        for (final GlobalPos spot : STUCK_SPOTS)
        {
            if (spot.pos().equals(pos))
            {
                return spot;
            }
        }
        return null;
    }

    private static final Map<GlobalPos, long[]> RESCUED = new LinkedHashMap<>();

    private static final Set<Long> HOLES_LEFT = new HashSet<>();

    private static final Map<GlobalPos, Object[]> HELD_REPORTS = new HashMap<>();

    private static final String STUCK_REPORT = "[{}] {} rescues in two game-days from being stuck around {}; the last, {}, on the way to {}. {}";

    private static final int STUCK_RESCUES = 3;

    private static final long STUCK_WINDOW_TICKS = 48000L;

    public static synchronized void stuck(final AbstractEntityCitizen citizen, final BlockPos destination)
    {

        if (destination == null || BlockPos.ZERO.equals(destination) || citizen.level().isClientSide() || citizen.getCitizenData() == null
              || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return;
        }
        final BlockPos where = citizen.blockPosition();

        final boolean cold = citizen.level() instanceof final net.minecraft.server.level.ServerLevel level && !level.isPositionEntityTicking(destination);

        final boolean swimming = citizen.isInWater();

        boolean handedOn = false;
        try
        {
            handedOn = !cold && !swimming && citizen.level() instanceof final net.minecraft.server.level.ServerLevel server
                         && buildingAt(citizen.getCitizenData().getColony(), where) == null
                         && keepRescue(server, citizen.getCitizenData().getColony(), where);
        }
        catch (final RuntimeException e)
        {
            ColonyAutopilot.LOGGER.warn("[{}] the hole rule could not count the rescue at {}", citizen.getCitizenData().getColony().getName(), where.toShortString(), e);
        }
        if (AutopilotConfig.PROBLEMS_ENABLED.get() && thirdRescue(citizen.level(), where))
        {
            String advice = cold
              ? "The place he was going lies in a chunk that does not tick — nobody is near it and it is outside the chunks the village keeps loaded —"
                  + " so no citizen can walk into it and every try ends in a rescue, which leaves him standing still there until MineColonies' next citizen sweep."
                  + " Likely nothing to plug or bridge: it clears while a player is near that place"
              : "Likely a cave mouth, a pit or a ledge with no way back up: look there, and plug or bridge it";
            if (swimming && !cold)
            {
                advice = WATER_ADVICE;
            }

            final com.minecolonies.api.colony.buildings.IBuilding building = swimming ? null : buildingAt(citizen.getCitizenData().getColony(), where);
            if (building != null)
            {
                final boolean built = building.getBuildingLevel() > 0;

                final boolean underWork = built && citizen.getCitizenData().getColony().getServerBuildingManager().getBuildings().values().stream()
                  .anyMatch(hut -> hut instanceof com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder crew
                    && crew.getWorkOrder() instanceof com.minecolonies.core.colony.workorders.WorkOrderBuilding active
                    && (active.getLocation().equals(building.getID()) || building.getChildren().contains(active.getLocation())));
                advice = underWork
                  ? "The spot lies inside the " + SiteSelector.plainName(building) + ", which a builder is working on: he takes out or replaces its blocks one by one, and a ladder or a step can be missing until he is done, so it should clear when that work is finished; look there again if the rescues go on after that"
                  : built
                    ? "The spot lies inside the " + SiteSelector.plainName(building) + " — its blueprint's own interior, where MineColonies does the walking; nothing outside to plug or bridge"
                    : "The spot lies in the building site of the " + SiteSelector.plainName(building) + ", which is not built yet: it should clear when the building stands; look there again if the rescues go on after that";
            }

            if (ColonyAutopilot.trapsTagged)
            {
                for (final BlockPos near : BlockPos.betweenClosed(where.offset(-3, -1, -3), where.offset(3, 2, 3)))
                {
                    if (citizen.level().isLoaded(near) && citizen.level().getBlockState(near).is(com.minecolonies.api.items.ModTags.dangerousBlocks))
                    {
                        advice += ". A trap stands close by (" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(citizen.level().getBlockState(near).getBlock())
                                    + " at " + near.toShortString() + "): citizens do not walk through traps, so a line of them with no gap ends a walk too — leave a gap, a door or a fence gate";
                        break;
                    }
                }
            }

            final Object[] report = {citizen.getCitizenData().getColony().getName(), STUCK_RESCUES, where.toShortString(), citizen.getCitizenData().getName(),
              destination.toShortString(), advice};
            if (handedOn)
            {

                HELD_REPORTS.put(GlobalPos.of(citizen.level().dimension(), where.immutable()), report);
            }
            else
            {
                ColonyAutopilot.LOGGER.warn(STUCK_REPORT, report);
            }
        }
    }

    private static com.minecolonies.api.colony.buildings.IBuilding buildingAt(final com.minecolonies.api.colony.IColony colony, final BlockPos where)
    {
        for (final com.minecolonies.api.colony.buildings.IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final var corners = building.getCorners();
            if (building.getBuildingLevel() <= 0 && corners.getB().getX() - corners.getA().getX() < 2 && corners.getB().getZ() - corners.getA().getZ() < 2)
            {
                continue;
            }
            final int loY = Math.min(corners.getA().getY(), corners.getB().getY()), hiY = Math.max(corners.getA().getY(), corners.getB().getY());
            if (SiteSelector.boxOf(building).intersects(where.getX(), where.getZ(), where.getX(), where.getZ()) && where.getY() >= loY && where.getY() <= hiY)
            {
                return building;
            }
        }
        return null;
    }

    private static boolean keepRescue(final net.minecraft.server.level.ServerLevel level, final com.minecolonies.api.colony.IColony colony, final BlockPos where)
    {
        final long now = level.getGameTime();
        final long[] tally = RESCUED.computeIfAbsent(GlobalPos.of(level.dimension(), where.immutable()), k -> new long[] {now, 0});
        if (now - tally[0] > STUCK_WINDOW_TICKS || now < tally[0])
        {
            tally[0] = now;
            tally[1] = 0;
        }
        tally[1]++;
        if (RESCUED.size() > STUCK_SPOTS_CAP)
        {
            RESCUED.remove(RESCUED.keySet().iterator().next());
        }
        return !HOLES_LEFT.contains(where.asLong()) && AutopilotConfig.live(colony, AutopilotConfig.TERRAFORM_ENABLED) && rescuesIn(level, where, null) >= 2
                 && GrowthDirector.holeRescued(colony, where);
    }

    static synchronized int rescuesIn(final net.minecraft.world.level.Level level, final BlockPos where, final Set<Long> cells)
    {
        final long now = level.getGameTime();
        int count = 0;
        for (final Map.Entry<GlobalPos, long[]> rescue : RESCUED.entrySet())
        {
            final BlockPos spot = rescue.getKey().pos();
            final long[] tally = rescue.getValue();
            if (rescue.getKey().dimension().equals(level.dimension()) && now - tally[0] <= STUCK_WINDOW_TICKS && now >= tally[0]
                  && (cells != null ? cells.contains(spot.asLong())
                        : Math.abs(spot.getX() - where.getX()) <= 4 && Math.abs(spot.getZ() - where.getZ()) <= 4 && Math.abs(spot.getY() - where.getY()) <= 8))
            {
                count += (int) tally[1];
            }
        }
        return count;
    }

    static synchronized void holeDone(final net.minecraft.world.level.Level level, final BlockPos where, final Set<Long> cells, final boolean filled)
    {
        if (!filled)
        {
            if (HOLES_LEFT.size() > 4096)
            {
                HOLES_LEFT.clear();
            }
            HOLES_LEFT.addAll(cells);
            return;
        }
        final java.util.function.Predicate<GlobalPos> inHole = spot -> spot.dimension().equals(level.dimension()) && cells.contains(spot.pos().asLong());
        RESCUED.keySet().removeIf(inHole);

        HELD_REPORTS.keySet().removeIf(inHole);
        forgetCells(level.dimension().location().toString(), where.getX() >> 4, where.getZ() >> 4);
    }

    static synchronized void releaseHeld(final net.minecraft.world.level.Level level, final BlockPos where, final boolean filled)
    {
        final Object[] report = HELD_REPORTS.remove(GlobalPos.of(level.dimension(), where));
        if (report != null && !filled)
        {
            ColonyAutopilot.LOGGER.warn(STUCK_REPORT, report);
        }
    }

    private static final String WATER_ADVICE = "Likely water whose banks stand a block or more above it, which no swimmer can climb:"
                                                 + " cut a step down to the water's level, set a ladder, or bridge or fill it";

    static synchronized void stuckInWater(final net.minecraft.world.level.Level level, final com.minecolonies.api.colony.IColony colony,
      final String who, final BlockPos from, final BlockPos to)
    {
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.PROBLEMS_ENABLED.get() || !thirdRescue(level, to))
        {
            return;
        }
        ColonyAutopilot.LOGGER.warn("[{}] {} rescues in two game-days from being stuck around {}; the last, {}, was lifted out of the water at {}. {}",
          colony.getName(), STUCK_RESCUES, to.toShortString(), who, from.toShortString(), WATER_ADVICE);
    }

    private static boolean thirdRescue(final net.minecraft.world.level.Level level, final BlockPos where)
    {
        final long now = level.getGameTime();
        final String dimension = level.dimension().location().toString();
        final int cellX = where.getX() >> 4;
        final int cellZ = where.getZ() >> 4;
        long ownCount = 0;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                final long[] tally = stuckCells.computeIfAbsent(dimension + "/" + (cellX + dx) + "/" + (cellZ + dz), k -> new long[] {now, 0});
                if (now - tally[0] > STUCK_WINDOW_TICKS || now < tally[0])
                {
                    tally[0] = now;
                    tally[1] = 0;
                }
                tally[1]++;
                if (dx == 0 && dz == 0)
                {
                    ownCount = tally[1];
                }
            }
        }
        if (ownCount < STUCK_RESCUES)
        {
            return false;
        }
        if (STUCK_SPOTS.size() >= STUCK_SPOTS_CAP)
        {
            STUCK_SPOTS.remove(STUCK_SPOTS.iterator().next());
        }
        STUCK_SPOTS.add(GlobalPos.of(level.dimension(), where.immutable()));

        forgetCells(dimension, cellX, cellZ);
        return true;
    }

    private static void forgetCells(final String dimension, final int cellX, final int cellZ)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                stuckCells.remove(dimension + "/" + (cellX + dx) + "/" + (cellZ + dz));
            }
        }
    }

    private static synchronized void record(final String entry, final String identity)
    {
        if (RECENT.size() >= CAPACITY)
        {
            RECENT.removeFirst();
        }
        RECENT.addLast(entry);
        if (announced.size() >= ANNOUNCED_CAP)
        {
            announced.clear();
        }
        if (announced.add(identity))
        {
            pendingPushes++;
        }
    }

    static synchronized int drainNewProblems()
    {
        final int fresh = pendingPushes;
        pendingPushes = 0;
        return fresh;
    }

    static synchronized List<String> snapshot()
    {
        return new ArrayList<>(RECENT);
    }

    static synchronized void clear()
    {
        RECENT.clear();
        announced.clear();
        pendingPushes = 0;
        stuckCells.clear();
        STUCK_SPOTS.clear();
        RESCUED.clear();
        HOLES_LEFT.clear();
        HELD_REPORTS.clear();
    }
}
