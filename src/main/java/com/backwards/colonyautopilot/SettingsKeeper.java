// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack;

final class SettingsKeeper
{
    private SettingsKeeper()
    {
    }

    static void keep(final IBuilding building)
    {

        if (!building.hasModule(ISettingsModule.class))
        {
            return;
        }
        force(building, BuildingLumberjack.DEFOLIATE, "break-leaves");
        force(building, BuildingLumberjack.REPLANT, "replant");
        force(building, AbstractBuilding.BREEDING, "breeding");
        force(building, AbstractBuildingGuards.RETREAT, "retreat-when-hurt");

        if (!GrowthDirector.handHired(building))
        {
            force(building, AbstractBuildingGuards.HIRE_TRAINEE, "hire-trainees");
        }

        final StringSetting builderMode = building.getSetting(BuildingBuilder.MODE);
        if (builderMode != null && !builderMode.getValue().equals(builderMode.getSettings().get(0)))
        {
            builderMode.trigger();
            building.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] switched the {} back to automatic work-order claiming",
              building.getColony().getName(), building.getBuildingDisplayName());
        }

        final com.minecolonies.core.colony.buildings.modules.settings.BlockSetting fill =
          building.getSetting(com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner.FILL_BLOCK);
        if (fill != null && "structurize".equals(
          net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(fill.getValue().getBlock()).getNamespace()))
        {
            fill.setValue(fill.getDefault());
            building.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] reset the {}'s fill block to its default — it was a placeholder block",
              building.getColony().getName(), building.getBuildingDisplayName());
        }
        if (building instanceof BuildingCowboy)
        {

            final int target = Math.max(2, building.getBuildingLevel() * 2);
            raise(building, BuildingCowboy.MILKING_AMOUNT, target, "milkings-per-day");
            raise(building, BuildingCowboy.STEWING_AMOUNT, target, "stews-per-day");
        }
    }

    private static void raise(final IBuilding building, final ISettingKey<IntSetting> key, final int target, final String label)
    {
        final IntSetting setting = building.getSetting(key);
        if (setting != null && setting.getValue() < target)
        {
            setting.setValue(target);
            building.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] raised {} to {} at the {} — the automation profile keeps it there",
              building.getColony().getName(), label, target, building.getBuildingDisplayName());
        }
    }

    private static void force(final IBuilding building, final ISettingKey<BoolSetting> key, final String label)
    {
        final BoolSetting setting = building.getSetting(key);
        if (setting != null && !setting.getValue())
        {
            setting.trigger();
            building.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] switched {} ON at the {} — the automation profile keeps it that way",
              building.getColony().getName(), label, building.getBuildingDisplayName());
        }
    }
}
