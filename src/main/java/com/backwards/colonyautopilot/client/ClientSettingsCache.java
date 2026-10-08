// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.client;

import com.backwards.colonyautopilot.net.SettingsPayloads.SettingsSnapshot;
import com.minecolonies.api.items.component.ColonyId;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ClientSettingsCache
{
    private static final Map<ColonyId, SettingsSnapshot> latest = new HashMap<>();

    private static Consumer<SettingsSnapshot> presenter = snapshot -> {};

    private ClientSettingsCache()
    {
    }

    public static void onSnapshot(final SettingsSnapshot snapshot)
    {
        latest.put(snapshot.colony(), snapshot);
        presenter.accept(snapshot);
    }

    public static SettingsSnapshot latest(final ColonyId colony)
    {
        return latest.get(colony);
    }

    public static void present(final Consumer<SettingsSnapshot> viewer)
    {
        presenter = viewer;
    }

    public static void withdraw(final Consumer<SettingsSnapshot> viewer)
    {
        if (presenter == viewer)
        {
            presenter = snapshot -> {};
        }
    }

    public static void clear()
    {
        latest.clear();
        presenter = snapshot -> {};
    }
}
