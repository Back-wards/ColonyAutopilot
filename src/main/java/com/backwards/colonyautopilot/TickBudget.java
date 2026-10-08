// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class TickBudget
{
    private static int left = 2000;

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Pre event)
    {
        left = AutopilotConfig.SPEC.isLoaded() ? AutopilotConfig.WORK_BUDGET_PER_TICK.get() : 2000;
    }

    static boolean has()
    {
        return left > 0;
    }

    static void spend(final int units)
    {
        left -= units;
    }
}
