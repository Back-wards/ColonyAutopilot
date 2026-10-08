// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ColonyRota
{
    private final ArrayDeque<ColonyId> due = new ArrayDeque<>();

    private WeakReference<MinecraftServer> filledFor = new WeakReference<>(null);

    Set<ColonyId> fill(final MinecraftServer server)
    {
        forServer(server);
        final Set<ColonyId> live = new HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final ColonyId key = ColonyAutopilot.colonyKey(colony);
            live.add(key);
            if (!due.contains(key))
            {
                due.add(key);
            }
        }
        return live;
    }

    void add(final MinecraftServer server, final IColony colony)
    {
        forServer(server);
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (!due.contains(key))
        {
            due.add(key);
        }
    }

    IColony next(final MinecraftServer server)
    {
        forServer(server);
        while (!due.isEmpty())
        {
            final ColonyId key = due.poll();
            final ServerLevel level = server.getLevel(key.dimension());
            final IColony colony = level == null ? null : IColonyManager.getInstance().getColonyByWorld(key.id(), level);
            if (colony != null)
            {
                return colony;
            }
        }
        return null;
    }

    List<IColony> take(final MinecraftServer server)
    {
        final IColony colony = next(server);
        return colony == null ? List.of() : List.of(colony);
    }

    private void forServer(final MinecraftServer server)
    {
        if (filledFor.get() != server)
        {
            due.clear();
            filledFor = new WeakReference<>(server);
        }
    }
}
