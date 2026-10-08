// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

public final class BuilderCap
{
    enum Kind
    {
        PLACEMENT,
        UPGRADE
    }

    private static final int ESTABLISHED_HALL_LEVEL = 3;

    private static final long PLACEMENT_WISH_TICKS = 600L;

    private static UpgradeDirector upgrades;

    static final Map<ColonyId, Long> placementWantedAt = new HashMap<>();

    static final Map<ColonyId, Long> placementAdmittedAt = new HashMap<>();

    static final Map<ColonyId, String> placementHeld = new HashMap<>();
    static final Map<ColonyId, String> upgradeHeld = new HashMap<>();

    static final Map<ColonyId, String> quotaWaivedLogged = new HashMap<>();

    static void setUpgradeDirector(final UpgradeDirector director)
    {
        upgrades = director;
    }

    static void clear()
    {
        placementWantedAt.clear();
        placementAdmittedAt.clear();
        placementHeld.clear();
        upgradeHeld.clear();
        quotaWaivedLogged.clear();
    }

    private record Load(int builders, int builderHuts, int jobs)
    {
        int cap()
        {
            return builders * AutopilotConfig.ECONOMY_JOBS_PER_BUILDER.get();
        }
    }

    private static Load load(final IColony colony)
    {
        int builders = 0;
        int builderHuts = 0;
        int jobs = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Block hut = building.getBuildingType().getBuildingBlock();

            if (hut == ModBlocks.blockHutBuilder && !UpgradeDirector.tornDown(building))
            {
                builderHuts++;
                if (building.getBuildingLevel() >= 1)
                {
                    builders++;
                }
            }

            if (building.getBuildingLevel() == 0 && building.getMaxBuildingLevel() > 0
                  && !UpgradeDirector.tornDown(building)
                  && !(AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES) && hut == ModBlocks.blockHutGraveyard)
                  && (upgrades == null || !upgrades.parked(colony, building.getID()))
                  && (upgrades == null || upgrades.orderable(colony, building)))
            {
                jobs++;
            }
        }

        for (final WorkOrderBuilding order : colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuilding.class))
        {
            if (order.getWorkOrderType() == WorkOrderType.UPGRADE)
            {
                jobs++;
            }
        }
        return new Load(builders, builderHuts, jobs);
    }

    static boolean mayPlace(final IColony colony, final ServerLevel level, final Block hut)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (!AutopilotConfig.progressionMode(colony))
        {
            placementHeld.remove(key);
            quotaWaivedLogged.remove(key);
            return true;
        }
        final long now = level.getGameTime();
        placementWantedAt.put(key, now);
        final Load load = load(colony);

        if (hut != ModBlocks.blockHutBuilder || load.builderHuts() > 0)
        {
            if (load.jobs() >= load.cap())
            {
                return hold(colony, placementHeld, "placement held: " + load.jobs() + " of " + load.cap() + " builder jobs in flight");
            }
            final ColonyGrounds grounds = ColonyGrounds.get(level);
            if (hallLevel(colony) < ESTABLISHED_HALL_LEVEL)
            {
                if (grounds.lastJobKind(colony.getID()) == ColonyGrounds.JOB_PLACEMENT && upgradeDue(colony))
                {
                    return hold(colony, placementHeld, "placement waits its turn: the last job started was a placement and an upgrade is due");
                }
            }
            else
            {
                final int since = grounds.upgradesSinceLastPlacement(colony.getID());
                final int wanted = AutopilotConfig.ECONOMY_UPGRADES_PER_PLACEMENT.get();
                if (since < wanted)
                {
                    if (upgradeDue(colony))
                    {
                        return hold(colony, placementHeld, "placement waits for the upgrades: " + since + " of " + wanted + " started since the last placement");
                    }
                    note(colony, quotaWaivedLogged, "builder cap: no upgrade is due — the placement goes ahead after " + since + " of " + wanted + " upgrades");
                }
            }
        }
        placementHeld.remove(key);
        placementAdmittedAt.put(key, now);
        return true;
    }

    static boolean mayUpgrade(final IColony colony, final ServerLevel level, final boolean jobCountOnly)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (!AutopilotConfig.progressionMode(colony))
        {
            upgradeHeld.remove(key);
            return true;
        }
        final long now = level.getGameTime();
        final Load load = load(colony);
        final Long admitted = placementAdmittedAt.get(key);
        final int jobs = load.jobs() + (admitted != null && now - admitted <= PLACEMENT_WISH_TICKS ? 1 : 0);
        if (jobs >= load.cap())
        {
            return hold(colony, upgradeHeld, "upgrade held: " + jobs + " of " + load.cap() + " builder jobs in flight");
        }

        final Long wanted = placementWantedAt.get(key);
        if (!jobCountOnly && wanted != null && now - wanted <= PLACEMENT_WISH_TICKS)
        {
            final ColonyGrounds grounds = ColonyGrounds.get(level);
            if (hallLevel(colony) < ESTABLISHED_HALL_LEVEL)
            {
                if (grounds.lastJobKind(colony.getID()) == ColonyGrounds.JOB_UPGRADE)
                {
                    return hold(colony, upgradeHeld, "upgrade waits its turn: the last job started was an upgrade and a placement is due");
                }
            }
            else
            {

                final int since = grounds.upgradesSinceLastPlacement(colony.getID());
                final int quota = AutopilotConfig.ECONOMY_UPGRADES_PER_PLACEMENT.get();
                if (since >= quota)
                {
                    return hold(colony, upgradeHeld, "upgrade waits its turn: " + since + " of " + quota + " upgrades started since the last placement and a placement is due");
                }
            }
        }
        upgradeHeld.remove(key);
        return true;
    }

    static void started(final IColony colony, final ServerLevel level, final Kind kind)
    {
        if (!AutopilotConfig.progressionMode(colony))
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final int since = grounds.upgradesSinceLastPlacement(colony.getID());
        grounds.setLastJobKind(colony.getID(), kind == Kind.PLACEMENT ? ColonyGrounds.JOB_PLACEMENT : ColonyGrounds.JOB_UPGRADE);
        grounds.setUpgradesSinceLastPlacement(colony.getID(), kind == Kind.PLACEMENT ? 0 : since + 1);
        if (kind == Kind.PLACEMENT)
        {
            final ColonyId key = ColonyAutopilot.colonyKey(colony);
            placementWantedAt.remove(key);
            placementAdmittedAt.remove(key);
            quotaWaivedLogged.remove(key);
        }

        if (!ColonyAutopilot.LOGGER.isDebugEnabled())
        {
            return;
        }
        final Load load = load(colony);
        if (kind == Kind.PLACEMENT)
        {
            ColonyAutopilot.LOGGER.debug("[{}] builder cap: a placement started after {} upgrades — {} of {} builder jobs in flight, town hall level {}",
              colony.getName(), since, load.jobs(), load.cap(), hallLevel(colony));
        }
        else
        {
            ColonyAutopilot.LOGGER.debug("[{}] builder cap: an upgrade started ({} since the last placement) — {} of {} builder jobs in flight, town hall level {}",
              colony.getName(), since + 1, load.jobs(), load.cap(), hallLevel(colony));
        }
    }

    static void placementAbandoned(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        placementAdmittedAt.remove(key);
        placementWantedAt.remove(key);
    }

    public static String holdText(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final String reason = placementHeld.get(key);
        final Long asked = placementWantedAt.get(key);
        return reason == null || asked == null || colony.getWorld() == null
                 || colony.getWorld().getGameTime() - asked > PLACEMENT_WISH_TICKS ? "" : reason;
    }

    private static int hallLevel(final IColony colony)
    {
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        return hall == null ? 0 : hall.getBuildingLevel();
    }

    private static boolean upgradeDue(final IColony colony)
    {
        return upgrades != null && upgrades.upgradeDue(colony);
    }

    private static boolean hold(final IColony colony, final Map<ColonyId, String> holds, final String reason)
    {
        note(colony, holds, reason);
        return false;
    }

    private static void note(final IColony colony, final Map<ColonyId, String> notes, final String reason)
    {
        if (!reason.equals(notes.put(ColonyAutopilot.colonyKey(colony), reason)))
        {
            ColonyAutopilot.LOGGER.debug("[{}] {}", colony.getName(), reason);
        }
    }
}
