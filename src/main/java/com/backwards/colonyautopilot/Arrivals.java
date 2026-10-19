// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Arrivals
{
    static final String BORN_IN_CHAOS = "born_in_chaos_v1";
    public static final String MUTATIONCRAFT = "mutationcraft";

    private record Hold(String owner, String method)
    {
    }

    private static final List<Hold> HOLDS = List.of(
      new Hold("com.asestefan.mutationcraft.behavior.MutantConversion",
        "evolve(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/entity/Mob;"),
      new Hold("com.asestefan.mutationcraft.behavior.MutantConversion",
        "convert(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/entity/Mob;"),
      new Hold("com.asestefan.mutationcraft.behavior.AberrationSpawns",
        "spawnAt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/EntityType;DDD)Lnet/minecraft/world/entity/Mob;"),
      new Hold("com.asestefan.mutationcraft.behavior.AberrationSpawns",
        "spawnNear(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/EntityType;DDD)Lnet/minecraft/world/entity/Mob;"),
      new Hold("com.asestefan.mutationcraft.behavior.NecroptorBehavior",
        "spawnHook(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"),
      new Hold("com.asestefan.mutationcraft.behavior.HookBehavior",
        "summonOne(Lnet/minecraft/server/level/ServerLevel;Lcom/asestefan/mutationcraft/entity/HookEntity;Lnet/minecraft/world/entity/EntityType;)V"));

    private static final Set<String> HOOKS = Set.of("light_hook", "medium_hook", "heavy_hook");

    private static final double HOOK_NEST_RADIUS = 48;
    private static final double HOOK_CROWD_RADIUS = 32;

    private static final Set<String> ADVANCED = Set.of("human_stage_1", "human_stage_2", "human_stage_3", "assimilated_enderman",
      "assimilated_evoker", "carnivorae");
    private static final Set<String> ELITE = Set.of("assimilated_creeper", "flayer", "parasitic_ram", "parasitic_roller", "light_hook",
      "medium_hook");
    private static final Set<String> BOSS = Set.of("corrosion_queen", "human_herder", "resenter", "parasitic_shooter", "heavy_hook");

    private static final int COUNTDOWN_DAYS = 25;

    private int tick = 0;

    private final Set<String> said = new HashSet<>();

    static int arrivalDay(final EntityType<?> type)
    {
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (BORN_IN_CHAOS.equals(id.getNamespace()))
        {
            return AutopilotConfig.ARRIVALS_BORN_IN_CHAOS_DAY.get();
        }
        if (!MUTATIONCRAFT.equals(id.getNamespace()))
        {
            return 0;
        }
        final String path = id.getPath();
        return (BOSS.contains(path) ? AutopilotConfig.ARRIVALS_BOSS_MUTANTS_DAY
          : ELITE.contains(path) ? AutopilotConfig.ARRIVALS_ELITE_MUTANTS_DAY
          : ADVANCED.contains(path) ? AutopilotConfig.ARRIVALS_ADVANCED_MUTANTS_DAY
          : AutopilotConfig.ARRIVALS_MUTANTS_DAY).get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSpawnPlacementCheck(final MobSpawnEvent.SpawnPlacementCheck event)
    {
        if ((event.getSpawnType() == MobSpawnType.NATURAL || event.getSpawnType() == MobSpawnType.CHUNK_GENERATION)
              && held(event.getEntityType(), event.getLevel().getLevel()))
        {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    public static boolean held(final EntityType<?> type, final Level level)
    {
        if (!AutopilotConfig.SPEC.isLoaded())
        {
            return false;
        }
        final int day = arrivalDay(type);
        return day > 0 && level.getGameTime() < day * 24000L;
    }

    public static boolean hookNestFull(final ServerLevel level, final Entity necroptor)
    {
        final int limit = AutopilotConfig.SPEC.isLoaded() ? AutopilotConfig.ARRIVALS_HOOK_LIMIT.get() : 0;
        return limit > 0 && level.getEntitiesOfClass(Mob.class, necroptor.getBoundingBox().inflate(HOOK_NEST_RADIUS),
          mob -> HOOKS.contains(mutationcraftPath(mob)) && mob.distanceToSqr(necroptor) <= HOOK_NEST_RADIUS * HOOK_NEST_RADIUS).size() >= limit;
    }

    public static boolean hookCrowded(final ServerLevel level, final Entity hook)
    {
        final int limit = AutopilotConfig.SPEC.isLoaded() ? AutopilotConfig.ARRIVALS_HOOK_CROWD_LIMIT.get() : 0;
        return limit > 0 && level.getEntitiesOfClass(Mob.class, hook.getBoundingBox().inflate(HOOK_CROWD_RADIUS),
          mob -> mob != hook && mutationcraftPath(mob) != null && mob.distanceToSqr(hook) <= HOOK_CROWD_RADIUS * HOOK_CROWD_RADIUS).size() >= limit;
    }

    private static String mutationcraftPath(final Entity entity)
    {
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return MUTATIONCRAFT.equals(id.getNamespace()) ? id.getPath() : null;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        tick = 0;
        said.clear();
        if (ModList.get().isLoaded(MUTATIONCRAFT))
        {
            checkHolds();
        }
    }

    private static void checkHolds()
    {
        final List<String> missing = new ArrayList<>();
        for (final Hold hold : HOLDS)
        {
            boolean found = false;
            try
            {
                for (final Method method : Class.forName(hold.owner()).getDeclaredMethods())
                {
                    found |= (method.getName() + MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString())
                      .equals(hold.method());
                }
            }
            catch (final ClassNotFoundException | LinkageError e)
            {

            }
            if (!found)
            {
                missing.add(hold.method().substring(0, hold.method().indexOf('(')));
            }
        }
        if (missing.isEmpty())
        {
            ColonyAutopilot.LOGGER.info("Arrival holds: Mutationcraft's {} held methods are where the mixins expect them", HOLDS.size());
        }
        else
        {
            ColonyAutopilot.LOGGER.warn("Mutationcraft has changed: the arrival holds on {} cannot attach, so its evolutions, conversions and hooks"
              + " may bring a mutant tier before its day, or hooks breed and summon past their limits", missing);
        }
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (++tick < 600)
        {
            return;
        }
        tick = 0;
        final MinecraftServer server = event.getServer();
        if (!AutopilotConfig.MILESTONES_ENABLED.get() || server.getPlayerList().getPlayers().isEmpty())
        {
            return;
        }
        for (final Line line : lines(server.overworld().getGameTime()))
        {

            if (said.add(line.day() + "|" + line.text()))
            {

                server.getPlayerList().broadcastSystemMessage(Component.literal(line.text()), false);
            }
        }
    }

    record Line(int day, String text)
    {
    }

    static List<Line> lines(final long gameTime)
    {

        final Map<Integer, List<String>> byDay = new TreeMap<>();
        if (ModList.get().isLoaded(BORN_IN_CHAOS))
        {
            byDay.computeIfAbsent(AutopilotConfig.ARRIVALS_BORN_IN_CHAOS_DAY.get(), d -> new ArrayList<>()).add("the Born in Chaos creatures");
        }

        if (ModList.get().isLoaded(MUTATIONCRAFT))
        {
            byDay.computeIfAbsent(AutopilotConfig.ARRIVALS_MUTANTS_DAY.get(), d -> new ArrayList<>()).add("the mutants");
        }
        final List<Line> lines = new ArrayList<>();
        for (final Map.Entry<Integer, List<String>> arrival : byDay.entrySet())
        {
            if (arrival.getKey() <= 0)
            {
                continue;
            }
            final String who = String.join(" and ", arrival.getValue());
            final long left = arrival.getKey() * 24000L - gameTime;
            if (left > 0)
            {
                final long daysLeft = (left + 23999L) / 24000L;
                if (daysLeft <= COUNTDOWN_DAYS && daysLeft % 5 == 0)
                {
                    lines.add(new Line(arrival.getKey(), "Days before " + who + " - " + daysLeft + " Days"));
                }
            }
            else if (left > -24000L)
            {
                lines.add(new Line(arrival.getKey(), Character.toUpperCase(who.charAt(0)) + who.substring(1) + " have come"));
            }
        }
        return lines;
    }
}
