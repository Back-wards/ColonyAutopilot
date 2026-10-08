// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public final class WorldSwitches extends SavedData
{
    private static final String NAME = "colonyautopilot_world";

    private Boolean master;

    private final Map<String, int[]> ownedRules = new HashMap<>();

    public WorldSwitches()
    {
    }

    static WorldSwitches get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
          new SavedData.Factory<>(WorldSwitches::new, WorldSwitches::load, null), NAME);
    }

    Boolean master()
    {
        return master;
    }

    void setMaster(final boolean enabled)
    {
        master = enabled;
        setDirty();
    }

    int[] ownedRule(final String rule)
    {
        return ownedRules.get(rule);
    }

    void ownRule(final String rule, final int original, final int applied)
    {
        ownedRules.put(rule, new int[] {original, applied});
        setDirty();
    }

    void releaseRule(final String rule)
    {
        if (ownedRules.remove(rule) != null)
        {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        if (master != null)
        {
            tag.putBoolean("master", master);
        }
        final CompoundTag rules = new CompoundTag();
        for (final Map.Entry<String, int[]> rule : ownedRules.entrySet())
        {
            rules.putIntArray(rule.getKey(), rule.getValue());
        }
        tag.put("ownedRules", rules);
        return tag;
    }

    static WorldSwitches load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final WorldSwitches world = new WorldSwitches();
        if (tag.contains("master"))
        {
            world.master = tag.getBoolean("master");
        }
        final CompoundTag rules = tag.getCompound("ownedRules");
        for (final String rule : rules.getAllKeys())
        {
            final int[] values = rules.getIntArray(rule);
            if (values.length == 2)
            {
                world.ownedRules.put(rule, values);
            }
        }
        return world;
    }
}
