// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.client;

import com.backwards.colonyautopilot.net.SettingsPayloads.SettingsSnapshot;

import java.util.function.Consumer;

public final class ClientSettingsCache
{

    private static Consumer<SettingsSnapshot> presenter = snapshot -> {};

    private ClientSettingsCache()
    {
    }

    public static void onSnapshot(final SettingsSnapshot snapshot)
    {
        presenter.accept(snapshot);
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
        presenter = snapshot -> {};
    }
}
