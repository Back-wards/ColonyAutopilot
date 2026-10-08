// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectedZones extends SavedData
{
    private static final String NAME = "colonyautopilot_zones";

    private final Map<Integer, List<SiteSelector.Footprint>> byColony = new ConcurrentHashMap<>();

    public ProtectedZones()
    {
    }

    private static final Map<ResourceKey<Level>, ProtectedZones> LOADED = new ConcurrentHashMap<>();

    static ProtectedZones get(final ServerLevel level)
    {
        final ProtectedZones zones = level.getDataStorage().computeIfAbsent(
          new SavedData.Factory<>(ProtectedZones::new, ProtectedZones::load, null), NAME);
        LOADED.put(level.dimension(), zones);
        return zones;
    }

    static ProtectedZones loaded(final ResourceKey<Level> dimension)
    {
        return LOADED.get(dimension);
    }

    static void clearLoaded()
    {
        LOADED.clear();
    }

    static List<SiteSelector.Footprint> footprintsFor(final IColony colony)
    {
        if (!AutopilotConfig.get(colony, AutopilotConfig.ZONES_ENABLED) || !(colony.getWorld() instanceof ServerLevel level))
        {
            return List.of();
        }
        return get(level).byColony.getOrDefault(colony.getID(), List.of());
    }

    List<SiteSelector.Footprint> boxesFor(final int colonyId)
    {
        return byColony.getOrDefault(colonyId, List.of());
    }

    List<SiteSelector.Footprint> allBoxes()
    {
        final List<SiteSelector.Footprint> all = new ArrayList<>();
        for (final List<SiteSelector.Footprint> boxes : byColony.values())
        {
            all.addAll(boxes);
        }
        return all;
    }

    void retain(final java.util.Set<Integer> liveColonyIds)
    {
        if (byColony.keySet().retainAll(liveColonyIds))
        {
            setDirty();
        }
    }

    void forget(final int colonyId)
    {
        if (byColony.remove(colonyId) != null)
        {
            setDirty();
        }
    }

    boolean add(final int colonyId, final SiteSelector.Footprint box)
    {
        final List<SiteSelector.Footprint> current = byColony.getOrDefault(colonyId, List.of());
        if (current.size() >= AutopilotConfig.ZONE_MAX_PER_COLONY.get())
        {
            return false;
        }
        final List<SiteSelector.Footprint> next = new ArrayList<>(current);
        next.add(box);
        byColony.put(colonyId, List.copyOf(next));
        setDirty();
        return true;
    }

    int removeAt(final int colonyId, final int x, final int z)
    {
        final List<SiteSelector.Footprint> current = byColony.get(colonyId);
        if (current == null || current.isEmpty())
        {
            return 0;
        }
        final List<SiteSelector.Footprint> keep = new ArrayList<>();
        for (final SiteSelector.Footprint box : current)
        {
            if (!box.intersects(x, z, x, z))
            {
                keep.add(box);
            }
        }
        final int removed = current.size() - keep.size();
        if (removed > 0)
        {
            if (keep.isEmpty())
            {
                byColony.remove(colonyId);
            }
            else
            {
                byColony.put(colonyId, List.copyOf(keep));
            }
            setDirty();
        }
        return removed;
    }

    int clear(final int colonyId)
    {
        final List<SiteSelector.Footprint> current = byColony.remove(colonyId);
        if (current == null || current.isEmpty())
        {
            return 0;
        }
        setDirty();
        return current.size();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ListTag colonies = new ListTag();
        byColony.forEach((id, boxes) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("id", id);
            final int[] flat = new int[boxes.size() * 4];
            for (int i = 0; i < boxes.size(); i++)
            {
                final SiteSelector.Footprint b = boxes.get(i);
                flat[i * 4] = b.minX();
                flat[i * 4 + 1] = b.minZ();
                flat[i * 4 + 2] = b.maxX();
                flat[i * 4 + 3] = b.maxZ();
            }
            entry.putIntArray("boxes", flat);
            colonies.add(entry);
        });
        tag.put("colonies", colonies);
        return tag;
    }

    static ProtectedZones load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ProtectedZones zones = new ProtectedZones();
        final ListTag colonies = tag.getList("colonies", Tag.TAG_COMPOUND);
        for (int i = 0; i < colonies.size(); i++)
        {
            final CompoundTag entry = colonies.getCompound(i);
            final int id = entry.getInt("id");
            final int[] flat = entry.getIntArray("boxes");
            final List<SiteSelector.Footprint> boxes = new ArrayList<>(flat.length / 4);
            for (int j = 0; j + 3 < flat.length; j += 4)
            {
                boxes.add(new SiteSelector.Footprint(flat[j], flat[j + 1], flat[j + 2], flat[j + 3]));
            }
            if (!boxes.isEmpty())
            {
                zones.byColony.put(id, List.copyOf(boxes));
            }
        }
        return zones;
    }
}
