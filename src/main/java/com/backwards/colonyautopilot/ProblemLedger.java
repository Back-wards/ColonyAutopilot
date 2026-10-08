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

    private static final int STUCK_RESCUES = 3;

    private static final long STUCK_WINDOW_TICKS = 48000L;

    public static synchronized void stuck(final AbstractEntityCitizen citizen, final BlockPos destination)
    {
        if (citizen.level().isClientSide() || citizen.getCitizenData() == null
              || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn() || !AutopilotConfig.PROBLEMS_ENABLED.get())
        {
            return;
        }
        final BlockPos where = citizen.blockPosition();
        final long now = citizen.level().getGameTime();
        final String dimension = citizen.level().dimension().location().toString();
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
        if (ownCount >= STUCK_RESCUES)
        {
            if (STUCK_SPOTS.size() >= STUCK_SPOTS_CAP)
            {
                STUCK_SPOTS.remove(STUCK_SPOTS.iterator().next());
            }
            STUCK_SPOTS.add(GlobalPos.of(citizen.level().dimension(), where.immutable()));

            String inside = null;
            for (final com.minecolonies.api.colony.buildings.IBuilding building : citizen.getCitizenData().getColony().getServerBuildingManager().getBuildings().values())
            {
                if (building.getBuildingLevel() <= 0)
                {
                    continue;
                }
                final var corners = building.getCorners();
                final int loY = Math.min(corners.getA().getY(), corners.getB().getY()), hiY = Math.max(corners.getA().getY(), corners.getB().getY());
                if (SiteSelector.boxOf(building).intersects(where.getX(), where.getZ(), where.getX(), where.getZ()) && where.getY() >= loY && where.getY() <= hiY)
                {
                    inside = SiteSelector.plainName(building);
                    break;
                }
            }

            ColonyAutopilot.LOGGER.warn("[{}] citizens keep getting stuck around {} — {} rescues there in two game-days, the last {} on the way to {}. {}",
              citizen.getCitizenData().getColony().getName(), where.toShortString(), STUCK_RESCUES, citizen.getCitizenData().getName(),
              destination == null || BlockPos.ZERO.equals(destination) ? "an unknown place" : destination.toShortString(),
              inside != null ? "The spot lies inside the " + inside + " — its blueprint's own interior, where MineColonies does the walking; nothing outside to plug or bridge"
                             : "Likely a cave mouth, a pit or a ledge with no way back up: look there, and plug or bridge it");

            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    stuckCells.remove(dimension + "/" + (cellX + dx) + "/" + (cellZ + dz));
                }
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
    }
}
