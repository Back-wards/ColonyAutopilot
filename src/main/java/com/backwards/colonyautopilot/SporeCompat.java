// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

final class SporeCompat
{
    static final String MOD_ID = "spore";

    private static final List<ResourceLocation> INFESTED_EARTH_IDS = List.of(
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_dirt"),
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_sand"),
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_red_sand"),
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_gravel"),
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_clay"),
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_soul_sand"),
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "rooted_mycelium"),
      ResourceLocation.withDefaultNamespace("mycelium"));

    private static final ResourceLocation INFECTION_EFFECT = ResourceLocation.fromNamespaceAndPath(MOD_ID, "mycelium_ef");

    private static final Set<ResourceLocation> INFECTION_EFFECTS = Set.of(INFECTION_EFFECT,
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "marker"), ResourceLocation.fromNamespaceAndPath(MOD_ID, "uneasy"));

    private static final ResourceKey<DamageType> INFECTION_DAMAGE =
      ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "mycelium_overtake"));

    private static final int INFECTION_SLOW_TICKS = 100;

    private static final long SPORE_CURE_CREDITS = 1L;

    private static Set<Block> infestedEarth;

    static Set<Block> infestedEarth()
    {
        if (infestedEarth == null)
        {
            final Set<Block> found = new HashSet<>();
            if (ModList.get().isLoaded(MOD_ID))
            {
                for (final ResourceLocation id : INFESTED_EARTH_IDS)
                {
                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(found::add);
                }
            }
            infestedEarth = Set.copyOf(found);
        }
        return infestedEarth;
    }

    private static final Map<ResourceLocation, ResourceLocation> CREEP_CURE_IDS = Map.ofEntries(
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_stone"), ResourceLocation.withDefaultNamespace("stone")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_deepslate"), ResourceLocation.withDefaultNamespace("deepslate")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_cobblestone"), ResourceLocation.withDefaultNamespace("cobblestone")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_cobbled_deepslate"), ResourceLocation.withDefaultNamespace("cobbled_deepslate")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_dirt"), ResourceLocation.withDefaultNamespace("dirt")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_sand"), ResourceLocation.withDefaultNamespace("sand")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_red_sand"), ResourceLocation.withDefaultNamespace("red_sand")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_gravel"), ResourceLocation.withDefaultNamespace("gravel")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_clay"), ResourceLocation.withDefaultNamespace("clay")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_netherrack"), ResourceLocation.withDefaultNamespace("netherrack")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_soul_sand"), ResourceLocation.withDefaultNamespace("soul_sand")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_end_stone"), ResourceLocation.withDefaultNamespace("end_stone")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_bricks"), ResourceLocation.withDefaultNamespace("bricks")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_stone_bricks"), ResourceLocation.withDefaultNamespace("stone_bricks")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_laboratory_block"), ResourceLocation.fromNamespaceAndPath(MOD_ID, "lab_block")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_laboratory_block1"), ResourceLocation.fromNamespaceAndPath(MOD_ID, "lab_block1")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_laboratory_block2"), ResourceLocation.fromNamespaceAndPath(MOD_ID, "lab_block2")),
      Map.entry(ResourceLocation.fromNamespaceAndPath(MOD_ID, "infested_laboratory_block3"), ResourceLocation.fromNamespaceAndPath(MOD_ID, "lab_block3")),
      Map.entry(ResourceLocation.withDefaultNamespace("mycelium"), ResourceLocation.withDefaultNamespace("dirt")));

    private static Map<Block, Block> creepCure;

    static Map<Block, Block> creepCure()
    {
        if (creepCure == null)
        {
            final Map<Block, Block> found = new HashMap<>();
            if (ModList.get().isLoaded(MOD_ID))
            {
                for (final Map.Entry<ResourceLocation, ResourceLocation> pair : CREEP_CURE_IDS.entrySet())
                {
                    BuiltInRegistries.BLOCK.getOptional(pair.getKey()).ifPresent(sick ->
                      BuiltInRegistries.BLOCK.getOptional(pair.getValue()).ifPresent(clean -> found.put(sick, clean)));
                }
            }
            creepCure = Map.copyOf(found);
        }
        return creepCure;
    }

    private static final List<String> NATURAL_INFECTION_IDS = List.of(
      "rooted_mycelium", "fungal_stem_sapling", "bile",
      "biomass_block", "calcified_biomass_block", "gastric_biomass_block", "sicken_biomass_block", "rooted_biomass", "biomass_bulb",
      "biomass_lump", "growth_mycelium", "growths_big", "growths_small", "wall_growths", "wall_growths_big", "wall_growths_fleshy",
      "mycelium_block", "mycelium_slab", "mycelium_veins", "membrane_block", "organite", "fungal_shell", "fungal_roots", "fungal_stem",
      "fungal_stem_top", "hanging_fungal_stem", "underwater_fungal_stem", "underwater_fungal_stem_top", "fungal_clamp", "blomfung",
      "bloomfung2", "glowshroom", "crusted_bile", "bile_lump", "acidic_sack", "acid", "remains", "wall_remains", "brain_remnants",
      "innards_block", "heart_block", "cerebrum_block", "braio_block", "lungs", "vocals", "hand", "fang_lump", "drowned_lump",
      "poisoning_lump", "exploding_lump", "hive_spawn", "overgrown_spawner", "outpost_watcher", "rotten_log", "rotten_planks",
      "rotten_slab", "rotten_stair", "rotten_branch", "rotten_bush", "rotten_crops", "rotten_fern", "rotten_grass", "rotten_scraps");

    private static Set<Block> infection;

    static boolean isInfection(final Block block)
    {
        if (infection == null)
        {
            final Set<Block> found = new HashSet<>(creepCure().keySet());
            if (ModList.get().isLoaded(MOD_ID))
            {
                for (final String id : NATURAL_INFECTION_IDS)
                {
                    BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_ID, id)).ifPresent(found::add);
                }
            }
            infection = Set.copyOf(found);
        }
        return infection.contains(block);
    }

    private static final String REDNIGHT_MARKER = "autopilot_rednight";

    static boolean rednightFallen(final ServerLevel level)
    {
        return level.getScoreboard().getObjective(REDNIGHT_MARKER) != null;
    }

    private boolean rednightSettled = false;

    private int rednightTick = 0;

    private long countdownSaid = -1;

    private static final int NURSE_PERIOD_TICKS = 40;

    private int nurseTick = 4;

    private final Map<ColonyId, Map<Integer, Long>> infectedSince = new HashMap<>();

    private final Map<ColonyId, Map<Integer, Long>> cureWaitLogged = new HashMap<>();

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarted(final net.neoforged.neoforge.event.server.ServerStartedEvent event)
    {
        rednightSettled = false;
        rednightTick = 0;
        countdownSaid = -1;
        nurseTick = 4;
        infectedSince.clear();
        cureWaitLogged.clear();
        shieldAnnounced.clear();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerTick(final net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)
    {
        if (++nurseTick >= NURSE_PERIOD_TICKS)
        {
            nurseTick = 0;
            nurseAllColonies();
        }

        if (rednightSettled || ++rednightTick < 600)
        {
            return;
        }
        rednightTick = 0;
        final int graceDays = AutopilotConfig.SPORES_GRACE_DAYS.get();
        if (graceDays <= 0 || !ModList.get().isLoaded("sporeinquisition") || !ModList.get().isLoaded(MOD_ID))
        {
            return;
        }
        final ServerLevel level = event.getServer().overworld();
        if (rednightFallen(level))
        {
            rednightSettled = true;
            return;
        }
        final long graceLeft = graceDays * 24000L - level.getGameTime();
        if (graceLeft > 0)
        {
            final long daysLeft = (graceLeft + 23999L) / 24000L;
            if (daysLeft % 5 == 0 && daysLeft != countdownSaid && AutopilotConfig.MILESTONES_ENABLED.get()
                  && !event.getServer().getPlayerList().getPlayers().isEmpty())
            {
                countdownSaid = daysLeft;

                event.getServer().getPlayerList().broadcastSystemMessage(
                  net.minecraft.network.chat.Component.literal("Days before rednight - " + daysLeft + " Days"), false);
            }
            return;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)
        {
            return;

        }
        if (event.getServer().getPlayerList().getPlayers().isEmpty())
        {
            return;
        }
        level.getScoreboard().addObjective(REDNIGHT_MARKER, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
          net.minecraft.network.chat.Component.literal(REDNIGHT_MARKER),
          net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
        rednightSettled = true;
        event.getServer().getCommands().performPrefixedCommand(
          event.getServer().createCommandSourceStack().withSuppressedOutput(),
          "function inqui:summon_primordial_mound");
        ColonyAutopilot.LOGGER.info("The quiet years are over — day {} has come and the rednight falls over the world spawn (SI's own once-per-world drop was consumed by the grace period; this is its scheduled return)",
          graceDays);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onEntityJoin(final net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)
    {

        if (!(event.getLevel() instanceof ServerLevel level) || !ModList.get().isLoaded(MOD_ID)
              || !BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getNamespace().equals(MOD_ID))
        {
            return;
        }

        final net.minecraft.nbt.CompoundTag data = event.getEntity().getPersistentData();
        final int graceDays = AutopilotConfig.SPORES_GRACE_DAYS.get();

        if (event.loadedFromDisk() || data.getBoolean(GRACE_SEEN_TAG)
              || (event.getEntity() instanceof Projectile projectile && projectile.getOwner() instanceof Player)
              || graceDays <= 0 || level.getGameTime() >= graceDays * 24000L || rednightFallen(level))
        {
            data.putBoolean(GRACE_SEEN_TAG, true);
            return;
        }
        event.setCanceled(true);
    }

    private static final String GRACE_SEEN_TAG = ColonyAutopilot.MOD_ID + ":grace_seen";

    private final Set<ColonyId> shieldAnnounced = new HashSet<>();

    private static final Set<String> CREEP_PAINTERS = Set.of("mound", "gastgaber", "proto", "hivetumor");

    @net.neoforged.bus.api.SubscribeEvent
    public void onSporeGriefing(final net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event)
    {
        if (!AutopilotConfig.masterOn()
              || !event.canGrief()
              || !isSporeEntity(event.getEntity())
              || !(event.getEntity().level() instanceof ServerLevel level))
        {
            return;
        }
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (AutopilotConfig.SPORE_DEMOLITION_RIGHTS.get().contains(id.toString()))
        {
            return;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, event.getEntity().blockPosition());
        if (!AutopilotConfig.get(colony, AutopilotConfig.SPORES_CREEP_GUARD))
        {
            return;
        }
        if (!CREEP_PAINTERS.contains(id.getPath()))
        {
            event.setCanGrief(false);
            return;
        }
        if (colony != null)
        {
            event.setCanGrief(false);
            if (shieldAnnounced.add(ColonyAutopilot.colonyKey(colony)))
            {
                ColonyAutopilot.LOGGER.info("[{}] the creep guard refused a spore's block-break inside the colony", colony.getName());
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onExplosion(final net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event)
    {
        if (!AutopilotConfig.masterOn() || event.getAffectedBlocks().isEmpty())
        {
            return;
        }

        final IColony at = event.getLevel() instanceof ServerLevel level
          ? IColonyManager.getInstance().getColonyByPosFromWorld(level, net.minecraft.core.BlockPos.containing(event.getExplosion().center())) : null;
        if (!AutopilotConfig.get(at, AutopilotConfig.SPORES_CREEP_GUARD))
        {
            return;
        }
        boolean spore = false;
        boolean licensed = false;
        for (final Entity culprit : new Entity[] {event.getExplosion().getDirectSourceEntity(), event.getExplosion().getIndirectSourceEntity()})
        {
            if (culprit == null)
            {
                continue;
            }
            final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(culprit.getType());
            if (id.getNamespace().equals(MOD_ID))
            {
                spore = true;
                if (AutopilotConfig.SPORE_DEMOLITION_RIGHTS.get().contains(id.toString()))
                {
                    licensed = true;
                }
            }
        }
        if (spore && !licensed)
        {
            event.getAffectedBlocks().clear();
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onInfectionDamage(final net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event)
    {
        if (!(event.getEntity() instanceof AbstractEntityCitizen villager) || !event.getSource().is(INFECTION_DAMAGE) || !AutopilotConfig.masterOn())
        {
            return;
        }
        final IColony colony = villager.getCitizenColonyHandler().getColonyOrRegister();
        if (colony != null && !AutopilotConfig.get(colony, AutopilotConfig.SPORES_CURE_INFECTION))
        {
            return;
        }
        event.setCanceled(true);
        if (!villager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
        {
            ColonyAutopilot.LOGGER.debug("[{}] the infection slows {} instead of hurting them", colony == null ? "-" : colony.getName(), villager.getName().getString());
        }
        villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, INFECTION_SLOW_TICKS, 0, false, true));
    }

    private static final Set<ResourceLocation> unknownSporeEffects = new HashSet<>();

    private void nurseAllColonies()
    {
        if (!ModList.get().isLoaded(MOD_ID) || !AutopilotConfig.masterOn())
        {
            return;
        }
        final Set<ColonyId> live = new HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            live.add(ColonyAutopilot.colonyKey(colony));
            if (!AutopilotConfig.get(colony, AutopilotConfig.SPORES_CURE_INFECTION) || !(colony.getWorld() instanceof ServerLevel level))
            {
                Treasury.setWaitingCures(colony, true, 0);
                continue;
            }
            try
            {
                nurseColony(colony, level);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the spore nurse failed", colony.getName(), e);
            }
        }

        ColonyAutopilot.retainColonies(live, infectedSince, cureWaitLogged);
        shieldAnnounced.retainAll(live);
    }

    private void nurseColony(final IColony colony, final ServerLevel level)
    {
        final long now = level.getGameTime();
        final long fester = AutopilotConfig.get(colony, AutopilotConfig.SPORES_FESTER_TICKS);
        final Map<Integer, Long> since = infectedSince.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        final Map<Integer, Long> logged = cureWaitLogged.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        final Set<Integer> roster = new HashSet<>();
        final List<String> nursed = new ArrayList<>();
        int waiting = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            final int id = citizen.getId();
            roster.add(id);
            final LivingEntity entity = citizen.getEntity().orElse(null);
            if (entity == null)
            {
                continue;
            }

            final List<MobEffectInstance> spore = new ArrayList<>();
            for (final MobEffectInstance effect : List.copyOf(entity.getActiveEffects()))
            {
                final var key = effect.getEffect().unwrapKey();
                if (key.isPresent() && key.get().location().getNamespace().equals(MOD_ID) && !INFECTION_EFFECTS.contains(key.get().location()))
                {
                    spore.add(effect);
                }
            }
            if (spore.isEmpty())
            {
                since.remove(id);
                continue;
            }

            if (fester > 0)
            {
                final Long first = since.putIfAbsent(id, now);
                if (first == null || now - first < fester)
                {
                    continue;
                }
            }

            if (!Treasury.charge(colony, level, SPORE_CURE_CREDITS, Treasury.Line.CURES))
            {
                waiting++;
                if (!Long.valueOf(now / 24000L).equals(logged.put(id, now / 24000L)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the spore cure of {} waits for funds ({} credits, {} needed) — the spore's harm stays on him until the treasury can pay",
                      colony.getName(), citizen.getName(), Treasury.credits(colony), SPORE_CURE_CREDITS);
                }
                continue;
            }
            logged.remove(id);
            for (final MobEffectInstance effect : spore)
            {
                final var location = effect.getEffect().unwrapKey().map(k -> k.location()).orElse(null);
                if (location != null && unknownSporeEffects.add(location))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the nurse now also clears the spore effect {}", colony.getName(), location);
                }
                entity.removeEffect(effect.getEffect());
            }
            since.remove(id);
            nursed.add(citizen.getName());
        }
        if (!nursed.isEmpty())
        {
            ColonyAutopilot.LOGGER.info("[{}] the village nursed {} citizen(s) of the spore's other harm ({}{}) — wiped {} — the infection itself stays, slowing not killing{}",
              colony.getName(), nursed.size(), String.join(", ", nursed.subList(0, Math.min(3, nursed.size()))),
              nursed.size() > 3 ? ", …" : "", fester > 0 ? "after it festered" : "on sight",
              AutopilotConfig.progressionMode(colony) ? " — the treasury paid " + SPORE_CURE_CREDITS + " credit a cure for " + nursed.size() : "");
        }
        Treasury.setWaitingCures(colony, true, waiting);

        since.keySet().retainAll(roster);
    }

    static boolean isSporeEntity(final Entity entity)
    {
        return entity != null && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals(MOD_ID);
    }

    static boolean isSporeBlock(final Block block)
    {
        return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(MOD_ID);
    }

    static boolean isSporeSpreader(final Entity entity)
    {
        if (entity == null)
        {
            return false;
        }
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!id.getNamespace().equals(MOD_ID))
        {
            return false;
        }
        if (id.getPath().equals("mound"))
        {
            return entity.saveWithoutId(new CompoundTag()).getInt("age") < 10;
        }
        return id.getPath().equals("gastgaber") || id.getPath().equals("tendril") || id.getPath().equals("scamper");
    }

    static boolean isSporeItem(final Item item)
    {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(MOD_ID);
    }

    private static final String SI_SCORE_HOLDER = "!finale";
    private static final String CORRUPTION_OBJECTIVE = "proto";
    private static final String[] ESCALATION_OBJECTIVES = {"ord_thr1", "ord_thr2", "ord_thr3", "ord_thr4"};

    static OptionalInt corruptionScore(final MinecraftServer server)
    {
        return readFinaleScore(server, CORRUPTION_OBJECTIVE);
    }

    static OptionalInt nearestEscalationGap(final MinecraftServer server)
    {
        final OptionalInt corruption = corruptionScore(server);
        if (corruption.isEmpty())
        {
            return OptionalInt.empty();
        }
        int gap = Integer.MAX_VALUE;
        for (final String gate : ESCALATION_OBJECTIVES)
        {
            final OptionalInt threshold = readFinaleScore(server, gate);
            if (threshold.isPresent() && threshold.getAsInt() > corruption.getAsInt())
            {
                gap = Math.min(gap, threshold.getAsInt() - corruption.getAsInt());
            }
        }
        return gap == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(gap);
    }

    private static OptionalInt readFinaleScore(final MinecraftServer server, final String objectiveName)
    {
        if (server == null)
        {
            return OptionalInt.empty();
        }
        try
        {
            final Scoreboard board = server.getScoreboard();
            final Objective objective = board.getObjective(objectiveName);
            if (objective == null)
            {
                return OptionalInt.empty();
            }
            final ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(SI_SCORE_HOLDER), objective);
            return info == null ? OptionalInt.empty() : OptionalInt.of(info.value());
        }
        catch (final Exception e)
        {
            return OptionalInt.empty();
        }
    }

}
