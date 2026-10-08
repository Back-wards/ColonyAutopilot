// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColonySettings
{

    public enum Scope
    {
        COLONY, SERVER
    }

    public record Key(String path, String section, String name, Class<?> type, Object def, Object min, Object max, Scope scope, String comment, Set<String> requires)
    {

        public boolean available()
        {
            for (final String mod : requires)
            {
                if (!ModList.get().isLoaded(mod))
                {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Map<String, Set<String>> REQUIRES = Map.of(
      "guns.enchantments", Set.of("tacz"),
      "guns.fullAutoGunners", Set.of("tacz", "minecolonies_compatibility"),
      "guns.gunnerGuards", Set.of("tacz", "minecolonies_compatibility"),
      "spores.turretsTargetInfected", Set.of(SporeCompat.MOD_ID, "k_turrets"),
      "spores.turretsInvulnerable", Set.of(SporeCompat.MOD_ID, "k_turrets"),
      "tweaks.muteFarmersDelightRecipeErrors", Set.of("sporeinquisition"));

    private static Set<String> requiresOf(final String path)
    {
        final Set<String> named = REQUIRES.get(path);
        return named != null ? named : path.startsWith("spores.") ? Set.of(SporeCompat.MOD_ID) : Set.of();
    }

    private static final Set<String> SERVER_ONLY = Set.of(
      "master.enabled", "meta.configVersion",
      "tweaks.workersWorkInRain", "tweaks.disableVanillaPatrols", "tweaks.disableFireTick", "tweaks.spawnChunkRadius",
      "tweaks.hostileSpawnCap", "tweaks.potionDurationMultiplier", "tweaks.ignoreToolTier", "tweaks.ignoreFoodQuality",
      "tweaks.spawnStarterKit", "tweaks.muteFarmersDelightRecipeErrors", "tweaks.workBudgetPerTick",
      "guns.enchantments", "guns.fullAutoGunners", "spores.gracePeriodDays", "spores.turretsTargetInfected", "spores.turretsInvulnerable",

      "autoupgrade.checkIntervalSeconds", "problems.enabled", "milestones.problemChat", "zones.maxPerColony", "growth.frontierTickets",

      "economy.progressionModeLocked", "economy.itemsPerBuck", "economy.gearWeight", "economy.researchFeePerTier",
      "economy.jobsPerBuilder", "economy.upgradesPerPlacement", "economy.dropChance", "economy.lootingBonus", "economy.bossHealth",
      "economy.chestMin", "economy.chestMax", "economy.mineshaftMin", "economy.mineshaftMax", "economy.exchange", "economy.conjureMarkup",
      "trickle.itemsPerMinute", "providence.itemsPerSweep", "providence.delaySeconds");

    private static final String PROGRESSION_MODE = "economy.progressionMode";

    private static volatile List<Key> keys;
    private static volatile Map<String, Key> byPath;

    private static final Map<ColonyId, Map<String, Object>> cache = new HashMap<>();

    private ColonySettings()
    {
    }

    public static List<Key> keys()
    {
        if (keys == null)
        {
            build();
        }
        return keys;
    }

    public static Key find(final String path)
    {
        if (byPath == null)
        {
            build();
        }
        return byPath.get(path);
    }

    public static Key key(final String path)
    {
        final Key key = find(path);
        if (key == null)
        {
            throw new IllegalArgumentException("no config key " + path);
        }
        return key;
    }

    private static synchronized void build()
    {
        if (keys != null)
        {
            return;
        }
        final List<Key> list = new ArrayList<>();
        walk("", AutopilotConfig.SPEC.getSpec(), list);
        final Map<String, Key> map = new HashMap<>();
        final List<Key> listed = new ArrayList<>();
        for (final Key key : list)
        {
            map.put(key.path(), key);
            if (key.available())
            {
                listed.add(key);
            }
        }
        byPath = map;
        keys = List.copyOf(listed);
    }

    private static void walk(final String prefix, final UnmodifiableConfig spec, final List<Key> out)
    {
        for (final UnmodifiableConfig.Entry entry : spec.entrySet())
        {
            final String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            final Object raw = entry.getValue();
            if (raw instanceof UnmodifiableConfig section)
            {
                walk(path, section, out);
            }
            else if (raw instanceof ModConfigSpec.ValueSpec value)
            {
                final Object def = value.getDefault();
                if (!(def instanceof Boolean || def instanceof Integer || def instanceof Double))
                {
                    continue;
                }
                final ModConfigSpec.Range<?> range = value.getRange();
                out.add(new Key(path, prefix, entry.getKey(), def.getClass(), def,
                  range == null ? null : range.getMin(), range == null ? null : range.getMax(),
                  SERVER_ONLY.contains(path) ? Scope.SERVER : Scope.COLONY, value.getComment() == null ? "" : value.getComment(), requiresOf(path)));
            }
        }
    }

    public static Object effective(final IColony colony, final Key key)
    {
        if (colony == null || key.scope() == Scope.SERVER || locked(key))
        {
            return serverValue(key);
        }
        final Object own;
        synchronized (cache)
        {
            own = cache.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> parseOverrides(colony)).get(key.path());
        }
        return own != null ? own : serverValue(key);
    }

    public static boolean isOverridden(final IColony colony, final Key key)
    {
        return colony != null && colony.getWorld() instanceof ServerLevel level
                 && ColonyGrounds.get(level).overrides(colony.getID()).containsKey(key.path());
    }

    public static boolean locked(final Key key)
    {
        return key.path().equals(PROGRESSION_MODE) && AutopilotConfig.ECONOMY_PROGRESSION_MODE_LOCKED.get();
    }

    public static Object serverValue(final Key key)
    {
        if (key.path().equals("master.enabled"))
        {
            return AutopilotConfig.masterOn();
        }
        if (locked(key))
        {
            return Boolean.TRUE;
        }
        return ((ModConfigSpec.ConfigValue<?>) AutopilotConfig.SPEC.getValues().get(key.path())).get();
    }

    private static Map<String, Object> parseOverrides(final IColony colony)
    {
        final Map<String, Object> parsed = new HashMap<>();
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return parsed;
        }
        for (final Map.Entry<String, String> entry : ColonyGrounds.get(level).overrides(colony.getID()).entrySet())
        {
            final Key key = find(entry.getKey());
            if (key == null || key.scope() == Scope.SERVER)
            {
                continue;
            }
            try
            {
                final Object value = parse(key, entry.getValue());
                if (inRange(key, value))
                {
                    parsed.put(key.path(), value);
                }
            }
            catch (final IllegalArgumentException ignored)
            {

            }
        }
        return parsed;
    }

    static Object parse(final Key key, final String text)
    {
        final String trimmed = text == null ? "" : text.trim();
        if (key.type() == Boolean.class)
        {
            if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false"))
            {
                return Boolean.parseBoolean(trimmed);
            }
            throw new IllegalArgumentException("not a switch: use true or false");
        }
        if (key.type() == Integer.class)
        {
            try
            {
                return Integer.parseInt(trimmed);
            }
            catch (final NumberFormatException e)
            {
                throw new IllegalArgumentException("not a whole number");
            }
        }
        try
        {
            return Double.parseDouble(trimmed);
        }
        catch (final NumberFormatException e)
        {
            throw new IllegalArgumentException("not a number");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean inRange(final Key key, final Object value)
    {
        return key.min() == null || (((Comparable) value).compareTo(key.min()) >= 0 && ((Comparable) value).compareTo(key.max()) <= 0);
    }

    public static String set(final IColony colony, final Key key, final String text, final String who, final boolean handBackAtDefault)
    {
        if (!key.available())
        {
            return "not on this server: it needs the mod(s) " + String.join(", ", key.requires());
        }
        if (key.scope() == Scope.SERVER)
        {
            return "server-wide key: set it in the config file or with /colonyautopilot set";
        }
        if (locked(key))
        {
            return "locked on for every colony by the server (economy.progressionModeLocked)";
        }
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return "the colony has no world";
        }
        final Object value;
        try
        {
            value = parse(key, text);
        }
        catch (final IllegalArgumentException e)
        {
            return e.getMessage();
        }
        if (!inRange(key, value))
        {
            return "out of range [" + key.min() + ", " + key.max() + "]";
        }

        if (handBackAtDefault && value.equals(serverValue(key)))
        {
            reset(colony, key, who);
            return null;
        }
        final Object before = effective(colony, key);
        ColonyGrounds.get(level).setOverride(colony.getID(), key.path(), String.valueOf(value));
        invalidate(colony);
        ColonyAutopilot.LOGGER.info("[{}] {} set {} to {} for this colony (server default {})", colony.getName(), who, key.path(), value, serverValue(key));
        switchedOff(colony, key, before);
        return null;
    }

    public static void reset(final IColony colony, final Key key, final String who)
    {
        if (colony.getWorld() instanceof ServerLevel level)
        {
            final Object before = effective(colony, key);
            ColonyGrounds.get(level).clearOverride(colony.getID(), key.path());
            invalidate(colony);
            ColonyAutopilot.LOGGER.info("[{}] {} reset {} to the server default ({})", colony.getName(), who, key.path(), serverValue(key));
            switchedOff(colony, key, before);
        }
    }

    private static void switchedOff(final IColony colony, final Key key, final Object before)
    {
        if (key.path().equals(AutopilotConfig.pathOf(AutopilotConfig.VILLAGE_GOLEM))
              && Boolean.TRUE.equals(before) && Boolean.FALSE.equals(effective(colony, key)))
        {
            final int removed = GolemKeeper.dismiss(colony);
            ColonyAutopilot.LOGGER.info("[{}] village golems switched off for this colony — {} of the keeper's golem(s) left the village",
              colony.getName(), removed);
        }
    }

    public static void invalidate(final IColony colony)
    {
        synchronized (cache)
        {
            cache.remove(ColonyAutopilot.colonyKey(colony));
        }
    }

    public static void invalidateAll()
    {
        synchronized (cache)
        {
            cache.clear();
        }
    }
}
