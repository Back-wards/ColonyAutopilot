// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenMournHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class MourningMute
{

    private static final int PERIOD_TICKS = 600;

    private int tickCounter = 8;

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn() || ++tickCounter < PERIOD_TICKS)
        {
            return;
        }
        tickCounter = 0;

        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (!AutopilotConfig.get(colony, AutopilotConfig.NO_MOURNING))
            {
                continue;
            }
            try
            {
                int consoled = 0;
                for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
                {
                    final ICitizenMournHandler mourn = citizen.getCitizenMournHandler();
                    if (!mourn.getDeceasedCitizens().isEmpty() || mourn.isMourning())
                    {
                        mourn.clearDeceasedCitizen();
                        mourn.setMourning(false);
                        consoled++;
                    }
                }
                if (consoled > 0)
                {
                    ColonyAutopilot.LOGGER.info("[{}] consoled {} citizen(s) — the village mourns its dead without downing tools (tweaks.noMourning)",
                      colony.getName(), consoled);
                }
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Mourning mute failed for colony {}", colony.getName(), e);
            }
        }
    }
}
