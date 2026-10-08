// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenDiedModEvent;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Milestones
{
    private static final int PERIOD_TICKS = 200;
    private static final int POPULATION_STEP = 5;

    private static final int PROBLEM_PUSH_COOLDOWN_PASSES = 120;

    static void say(final IColony colony, final String key, final Object... args)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.MILESTONES_ENABLED))
        {
            return;
        }

        broadcast(colony, named(colony, Component.translatable(key, args)));
    }

    static void say(final IColony colony, final Component message)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.MILESTONES_ENABLED))
        {
            return;
        }
        broadcast(colony, named(colony, message));
    }

    private static Component named(final IColony colony, final Component message)
    {
        return Component.literal("[" + colony.getName() + "] ").withStyle(message.getStyle()).append(message);
    }

    private static void broadcast(final IColony colony, final Component message)
    {
        if (colony.getWorld() == null || colony.getWorld().getServer() == null)
        {
            return;
        }
        for (final net.minecraft.server.level.ServerPlayer online : colony.getWorld().getServer().getPlayerList().getPlayers())
        {
            online.sendSystemMessage(message);
        }
    }

    static int tellOfficers(final IColony colony, final Component message)
    {
        if (colony.getWorld() == null || colony.getWorld().getServer() == null)
        {
            return 0;
        }
        final Component line = named(colony, message);
        final var players = colony.getWorld().getServer().getPlayerList();
        final var perms = colony.getPermissions();
        int told = 0;
        for (final ServerPlayer online : players.getPlayers())
        {

            if (perms.hasPermission(perms.getRank(online), Action.MANAGE_HUTS))
            {
                online.sendSystemMessage(line);
                told++;
            }
        }
        if (told == 0)
        {
            final ServerPlayer owner = players.getPlayer(perms.getOwner());
            if (owner != null)
            {
                owner.sendSystemMessage(line);
                told = 1;
            }
        }
        return told;
    }

    static final String ALERT_DEFEND = "defend";
    static final String ALERT_INFECTED = "infected";

    private static final long ALERT_COOLDOWN_TICKS = 5 * 60 * 20;

    private static final Map<ColonyId, Map<String, Long>> alertSounded = new HashMap<>();

    static void alert(final IColony colony, final String kind, final String text)
    {
        if (colony.getWorld() == null)
        {
            return;
        }

        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return;
        }

        final var server = colony.getWorld().getServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty())
        {
            return;
        }
        final long now = colony.getWorld().getGameTime();
        final Map<String, Long> sounded = alertSounded.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        final Long last = sounded.get(kind);
        if (last != null && now - last < ALERT_COOLDOWN_TICKS)
        {
            return;
        }
        sounded.put(kind, now);
        broadcast(colony, named(colony, Component.literal(text).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        ColonyAutopilot.LOGGER.info("[{}] the horn sounds: {}", colony.getName(), text);
    }

    private int tickCounter = 15;

    private int problemPushCooldown = 0;

    private static final Map<ColonyId, Integer> lastAnnouncedPopulation = new HashMap<>();

    private static final String PROGRESSION_NOTICE = "Progression Mode is on: the village's deliveries and research now cost ColonyBucks. Deposit them in the town hall";

    private static final long TREASURY_REMINDER_COOLDOWN_TICKS = 24000;

    private static final Map<ColonyId, Long> treasuryReminded = new HashMap<>();

    public void subscribeToMineColonies()
    {
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(BuildingConstructionModEvent.class, this::onConstruction);
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(CitizenDiedModEvent.class, this::onCitizenDied);
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyCreatedModEvent.class, this::onColonyCreated);
    }

    private void onColonyCreated(final ColonyCreatedModEvent event)
    {
        if (event.getColony() != null && event.getColony().getWorld() instanceof ServerLevel level
              && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.progressionMode(event.getColony()))
        {
            ColonyGrounds.get(level).setEconomyAnnounced(event.getColony().getID(), true);
        }
    }

    static void forget(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        lastAnnouncedPopulation.remove(key);
        recentDeaths.remove(key);
        treasuryReminded.remove(key);
        alertSounded.remove(key);
    }

    private static final long DEATH_WINDOW_TICKS = 2 * 60 * 20;
    private static final int DEATH_ALERT_COUNT = 4;

    private static final Map<ColonyId, ArrayDeque<Long>> recentDeaths = new HashMap<>();

    private void onCitizenDied(final CitizenDiedModEvent event)
    {
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return;
        }
        if (!(event.getCitizen() instanceof ICitizenData citizen)
              || citizen.getColony() == null || citizen.getColony().getWorld() == null)
        {
            return;
        }

        if (event.getDamageSource() == null || event.getDamageSource().getEntity() == null)
        {
            return;
        }
        final IColony colony = citizen.getColony();
        final long now = colony.getWorld().getGameTime();
        final ArrayDeque<Long> deaths = recentDeaths.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new ArrayDeque<>());
        while (!deaths.isEmpty() && now - deaths.peekFirst() > DEATH_WINDOW_TICKS)
        {
            deaths.pollFirst();
        }
        deaths.addLast(now);
        if (deaths.size() >= DEATH_ALERT_COUNT)
        {
            alert(colony, ALERT_DEFEND, "Help your village defend! " + deaths.size() + " citizens have fallen in minutes.");
        }
    }

    private void onConstruction(final BuildingConstructionModEvent event)
    {
        if (event.getWorkOrder().getWorkOrderType() != WorkOrderType.BUILD)
        {
            return;
        }
        final IColony colony = event.getBuilding().getColony();
        if (colony == null || !AutopilotConfig.live(colony, AutopilotConfig.MILESTONES_ENABLED))
        {
            return;
        }
        say(colony, "colonyautopilot.milestone.building",
          event.getWorkOrder().getDisplayName(), event.getBuilding().getBuildingLevel());
    }

    private void economyNotices(final IColony colony, final ColonyId key)
    {
        if (!AutopilotConfig.progressionMode(colony) || !(colony.getWorld() instanceof ServerLevel level)
              || colony.getServerBuildingManager().getBuildings().isEmpty())
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        if (!grounds.economyAnnounced(colony.getID()))
        {

            final String how = AutopilotConfig.ECONOMY_PROGRESSION_MODE_LOCKED.get() ? "." : ", or switch the mode off on the town hall's Autopilot page.";
            final int told = tellOfficers(colony, Component.literal(PROGRESSION_NOTICE + how).withStyle(ChatFormatting.GOLD));
            if (told > 0)
            {
                grounds.setEconomyAnnounced(colony.getID(), true);
                ColonyAutopilot.LOGGER.info("[{}] told {} officer(s) that Progression Mode is on: deliveries and research now cost ColonyBucks", colony.getName(), told);
            }
            return;
        }

        final long now = level.getGameTime();
        final Long last = treasuryReminded.get(key);
        if ((last != null && now - last < TREASURY_REMINDER_COOLDOWN_TICKS) || !AutopilotConfig.get(colony, AutopilotConfig.MILESTONES_ENABLED))
        {
            return;
        }
        final int requests = Treasury.waitingRequests(colony);
        final int research = Treasury.waitingResearch(colony);
        final int cures = Treasury.waitingCures(colony);

        if (requests + research + cures == 0 || Treasury.credits(colony) >= AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get())
        {
            return;
        }

        final List<Component> counts = new ArrayList<>();
        if (requests > 0)
        {
            counts.add(Component.translatable(requests == 1 ? "colonyautopilot.economy.requests.one" : "colonyautopilot.economy.requests.many", requests));
        }
        if (research > 0)
        {
            counts.add(Component.translatable(research == 1 ? "colonyautopilot.economy.research.one" : "colonyautopilot.economy.research.many", research));
        }
        if (cures > 0)
        {
            counts.add(Component.translatable(cures == 1 ? "colonyautopilot.economy.cures.one" : "colonyautopilot.economy.cures.many", cures));
        }
        final MutableComponent waiting = Component.empty();
        for (int i = 0; i < counts.size(); i++)
        {
            waiting.append(i == 0 ? "" : i == counts.size() - 1 ? " and " : ", ").append(counts.get(i));
        }

        if (tellOfficers(colony, Component.translatable("colonyautopilot.economy.empty", waiting)) > 0)
        {
            treasuryReminded.put(key, now);
        }
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        lastAnnouncedPopulation.clear();
        problemPushCooldown = 0;
        alertSounded.clear();
        recentDeaths.clear();
        treasuryReminded.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {
        lastAnnouncedPopulation.clear();
        problemPushCooldown = 0;
        alertSounded.clear();
        recentDeaths.clear();
        treasuryReminded.clear();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (++tickCounter < PERIOD_TICKS)
        {
            return;
        }
        tickCounter = 0;

        final boolean news = AutopilotConfig.masterOn();
        final Set<ColonyId> live = new HashSet<>();
        IColony pushVia = null;
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (pushVia == null)
            {
                pushVia = colony;
            }
            final ColonyId key = ColonyAutopilot.colonyKey(colony);
            live.add(key);
            if (news)
            {
                economyNotices(colony, key);
            }
            if (!news || !AutopilotConfig.get(colony, AutopilotConfig.MILESTONES_ENABLED))
            {
                continue;
            }
            final int count = colony.getCitizenManager().getCurrentCitizenCount();
            final Integer last = lastAnnouncedPopulation.get(key);
            if (last == null)
            {
                lastAnnouncedPopulation.put(key, count);
            }
            else if (count >= last + POPULATION_STEP)
            {
                lastAnnouncedPopulation.put(key, count);
                say(colony, "colonyautopilot.milestone.population", count);
            }
        }
        ColonyAutopilot.retainColonies(live, lastAnnouncedPopulation);
        ColonyAutopilot.retainColonies(live, recentDeaths);
        ColonyAutopilot.retainColonies(live, treasuryReminded);
        ColonyAutopilot.retainColonies(live, alertSounded);
        if (!news)
        {
            return;
        }

        if (problemPushCooldown > 0)
        {
            problemPushCooldown--;
        }
        else if (pushVia != null && AutopilotConfig.live(AutopilotConfig.PROBLEM_CHAT) && AutopilotConfig.MILESTONES_ENABLED.get())
        {
            final int fresh = ProblemLedger.drainNewProblems();
            if (fresh > 0)
            {

                broadcast(pushVia, Component.translatable("colonyautopilot.problem.push", fresh));
                problemPushCooldown = PROBLEM_PUSH_COOLDOWN_PASSES;
            }
        }
    }
}
