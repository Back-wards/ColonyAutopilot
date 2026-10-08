// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.ColonyState;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.items.component.ColonyId;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(ColonyAutopilot.MOD_ID)
public class ColonyAutopilot
{
    public static final String MOD_ID = "colonyautopilot";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean RIG_ACTIVE_COLONIES = Boolean.getBoolean("colonyautopilot.rig.activeColonies");

    private static final boolean RIG_COLONY_EVENTS = Boolean.getBoolean("colonyautopilot.rig.colonyEvents");

    private static long rigTicks = 10;

    private static boolean rigHookWarned;

    public ColonyAutopilot(final IEventBus modBus, final ModContainer container)
    {
        container.registerConfig(ModConfig.Type.SERVER, AutopilotConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AutopilotConfig.CLIENT_SPEC);
        ModItems.ITEMS.register(modBus);
        ColonyBucksLoot.LOOT_MODIFIERS.register(modBus);
        modBus.addListener(com.backwards.colonyautopilot.net.SettingsPayloads::register);
        modBus.addListener((final BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            {
                event.accept(ModItems.ZONE_MARKER);
                event.accept(ModItems.COLONY_BUCKS);
            }
        });

        if (!Maker.intact(container))
        {
            LOGGER.error("Colony Autopilot stays off: this jar no longer carries its maker's mark");
            return;
        }
        ProblemLedger.arm();

        NeoForge.EVENT_BUS.addListener((final ServerAboutToStartEvent event) -> ProblemLedger.clear());
        RecipeErrorFilter.install();

        final ResearchDirector research = new ResearchDirector();
        final UpgradeDirector director = new UpgradeDirector(research);
        final Milestones milestones = new Milestones();
        final GrowthDirector growth = new GrowthDirector();
        final Terraformer terraformer = new Terraformer();
        growth.setTerraformer(terraformer);
        growth.setUpgradeDirector(director);
        director.setGrowthDirector(growth);
        BuilderCap.setUpgradeDirector(director);
        terraformer.setGrowthDirector(growth);
        final WorkshopDirector workshops = new WorkshopDirector(growth);
        NeoForge.EVENT_BUS.register(new TickBudget());
        NeoForge.EVENT_BUS.register(director);
        NeoForge.EVENT_BUS.register(new MaterialTrickle());
        NeoForge.EVENT_BUS.register(growth);
        NeoForge.EVENT_BUS.register(terraformer);
        NeoForge.EVENT_BUS.register(new ProvidenceSweep(workshops));
        NeoForge.EVENT_BUS.register(new DamageWatch(director));
        NeoForge.EVENT_BUS.register(new ChunkKeeper());
        final GolemKeeper golems = new GolemKeeper();
        NeoForge.EVENT_BUS.register(golems);
        NeoForge.EVENT_BUS.register(research);
        NeoForge.EVENT_BUS.register(workshops);
        NeoForge.EVENT_BUS.register(new LampLighter(growth));
        NeoForge.EVENT_BUS.register(new Decorator(growth));
        NeoForge.EVENT_BUS.register(new AutopilotCommands(growth, golems));
        NeoForge.EVENT_BUS.register(new WarehouseJanitor());
        NeoForge.EVENT_BUS.register(new MourningMute());
        NeoForge.EVENT_BUS.register(new Quartermaster());
        NeoForge.EVENT_BUS.register(new Treasury());
        NeoForge.EVENT_BUS.register(new GroundsWarden(growth));
        NeoForge.EVENT_BUS.register(milestones);
        NeoForge.EVENT_BUS.register(new ColonyBucksLoot());

        if (net.neoforged.fml.ModList.get().isLoaded("k_turrets"))
        {
            NeoForge.EVENT_BUS.register(new KTurretsCompat());
            LOGGER.info("K-Turrets found — turrets and drones will be taught the spore horde as targets");
        }

        if (net.neoforged.fml.ModList.get().isLoaded("tacz"))
        {
            NeoForge.EVENT_BUS.register(new GunEnchants());
            LOGGER.info("TaCZ found — guns take enchantments (guns.enchantments)");
        }

        if (net.neoforged.api.distmarker.Dist.CLIENT == net.neoforged.fml.loading.FMLEnvironment.dist)
        {
            ZoneRenderer.register();
        }

        NeoForge.EVENT_BUS.register(new SpawnKit());
        final SporeCompat sporeCompat = new SporeCompat();
        NeoForge.EVENT_BUS.register(sporeCompat);

        modBus.addListener((final FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            director.subscribeToMineColonies();
            milestones.subscribeToMineColonies();
            growth.subscribeToMineColonies();
            research.subscribeToMineColonies();
            muteDomumBlockEntityFlood();
        }));

        NeoForge.EVENT_BUS.addListener((final ServerStartedEvent event) -> {
            AutopilotConfig.migrate();
            Exchange.loadOffers();
            applyWorldTweaks(event.getServer());
            applySpawnCapTweak();
            applyPotionDurationTweak();
        });
        NeoForge.EVENT_BUS.addListener((final ServerStoppingEvent event) -> {
            ProtectedZones.clearLoaded();
            ZoneMarkerItem.clearPending();
            rainFlipped = false;
        });

        NeoForge.EVENT_BUS.addListener((final LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
            {
                AutopilotConfig.setWorldMaster(WorldSwitches.get(level.getServer()).master());
            }
        });
        NeoForge.EVENT_BUS.addListener((final ServerStoppedEvent event) -> {

            AutopilotConfig.setWorldMaster(false);
            applySpawnCapTweak();
            applyPotionDurationTweak();
            AutopilotConfig.setWorldMaster(null);
        });

        NeoForge.EVENT_BUS.addListener((final net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
          ZoneMarkerItem.showZones(event.getServer(), growth));

        if (RIG_ACTIVE_COLONIES)
        {
            NeoForge.EVENT_BUS.addListener((final ServerStartedEvent event) -> LOGGER.info(
              "rig hook: headless colonies run their work-manager tick every 20 ticks, requests every 11, citizens every 60, buildings every 500 and the colony day at dawn (colonyautopilot.rig.activeColonies) — test scaffolding, never set this in play"));
            if (RIG_COLONY_EVENTS)
            {
                NeoForge.EVENT_BUS.addListener((final ServerStartedEvent event) -> LOGGER.info(
                  "rig hook: headless colonies also run their event tick every 500 ticks (colonyautopilot.rig.colonyEvents) — test scaffolding, never set this in play"));
            }
            NeoForge.EVENT_BUS.addListener((final net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
              tickHeadlessColonies());
        }

        LOGGER.info("Colony Autopilot by {} loaded (auto-upgrade + material trickle armed)", Maker.NAME);
    }

    private static void tickHeadlessColonies()
    {
        final long tick = ++rigTicks;
        final boolean requests = tick % 11 == 0;
        final boolean citizenData = tick % 60 == 0;
        final boolean work = tick % 20 == 0;
        final boolean slow = tick % 500 == 0;
        if (!requests && !citizenData && !work)
        {
            return;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {

            if (colony.getState() == ColonyState.ACTIVE)
            {
                continue;
            }
            try
            {
                if (requests)
                {
                    colony.getRequestManager().tick();
                }
                if (citizenData)
                {
                    colony.getCitizenManager().tickCitizenData(60);
                }
                if (!work)
                {
                    continue;
                }
                colony.getWorkManager().onColonyTick(colony);
                rigDayTime(colony);
                if (slow)
                {
                    colony.getCitizenManager().onColonyTick(colony);
                    colony.getServerBuildingManager().onColonyTick(colony);
                    if (RIG_COLONY_EVENTS)
                    {
                        colony.getEventManager().onColonyTick(colony);
                    }
                }

                colony.markDirty();
            }
            catch (final Exception e)
            {
                if (!rigHookWarned)
                {
                    rigHookWarned = true;
                    LOGGER.warn("rig hook: a headless tick failed for colony {} — later failures go unlogged", colony.getName(), e);
                }
            }
        }
    }

    private static void rigDayTime(final IColony colony) throws ReflectiveOperationException
    {
        if (colony.getWorld() == null || colony.isDay() == com.minecolonies.api.util.WorldUtil.isDayTime(colony.getWorld()))
        {
            return;
        }
        final Class<?> type = com.minecolonies.core.colony.Colony.class;
        final java.lang.reflect.Field isDay = type.getDeclaredField("isDay");
        isDay.setAccessible(true);
        isDay.setBoolean(colony, !colony.isDay());
        if (colony.isDay())
        {
            final java.lang.reflect.Field day = type.getDeclaredField("day");
            day.setAccessible(true);
            day.setInt(colony, colony.getDay() + 1);
            colony.getCitizenManager().onWakeUp();
            LOGGER.info("rig hook: [{}] colony day {} dawns", colony.getName(), colony.getDay());
        }
        else
        {
            colony.getCitizenManager().updateCitizenSleep(false);
        }
    }

    private static void muteDomumBlockEntityFlood()
    {
        try
        {
            final org.apache.logging.log4j.core.LoggerContext context =
              (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);

            final org.apache.logging.log4j.core.config.LoggerConfig config =
              context.getConfiguration().getLoggerConfig("net.minecraft.world.level.block.entity.BlockEntity");
            config.addFilter(new org.apache.logging.log4j.core.filter.AbstractFilter()
            {
                @Override
                public Result filter(final org.apache.logging.log4j.core.LogEvent event)
                {
                    for (Throwable t = event.getThrown(); t != null; t = t.getCause())
                    {
                        final String msg = t.getMessage();
                        if (msg != null && msg.contains("Invalid block entity domum_ornamentum"))
                        {
                            return Result.DENY;
                        }
                    }
                    return Result.NEUTRAL;
                }
            });
            context.updateLoggers();
            LOGGER.info("Muted the domum_ornamentum block-entity desync flood (MineColonies/domum bug — 'Invalid block entity domum_ornamentum' stack traces)");
        }
        catch (final Throwable t)
        {
            LOGGER.info("Could not mute the domum_ornamentum block-entity flood", t);
        }
    }

    static ColonyId colonyKey(final IColony colony)
    {
        return new ColonyId(colony.getID(), colony.getDimension());
    }

    @SafeVarargs
    static void retainColonies(final java.util.Set<ColonyId> live, final java.util.Map<ColonyId, ?>... maps)
    {
        for (final java.util.Map<ColonyId, ?> map : maps)
        {
            map.keySet().retainAll(live);
        }
    }

    private static void applyPatrolTweak(final MinecraftServer server)
    {
        ownRule(server, GameRules.RULE_DO_PATROL_SPAWNING, AutopilotConfig.live(AutopilotConfig.DISABLE_VANILLA_PATROLS), 0,
          "Disabled vanilla pillager patrol spawning (tweaks.disableVanillaPatrols)");
    }

    private static void applyFireTweak(final MinecraftServer server)
    {
        ownRule(server, GameRules.RULE_DOFIRETICK, AutopilotConfig.live(AutopilotConfig.DISABLE_FIRE_TICK), 0,
          "Disabled fire spread (doFireTick=false, tweaks.disableFireTick) — fire can't spread through the colony");
    }

    private static void applySpawnRadiusTweak(final MinecraftServer server)
    {
        final int wanted = AutopilotConfig.SPAWN_CHUNK_RADIUS.get();
        ownRule(server, GameRules.RULE_SPAWN_CHUNK_RADIUS, AutopilotConfig.masterOn() && wanted > 0, wanted,
          "Spawn chunks kept loaded to radius " + wanted + " (vanilla 2; tweaks.spawnChunkRadius) — the rednight's battlefield at world spawn always ticks");
    }

    static void applyWorldTweaks(final MinecraftServer server)
    {
        applyRainTweak();
        applyPatrolTweak(server);
        applyFireTweak(server);
        applySpawnRadiusTweak(server);
    }

    private static <T extends GameRules.Value<T>> void ownRule(final MinecraftServer server, final GameRules.Key<T> key,
      final boolean live, final int wanted, final String appliedLog)
    {
        final T rule = server.getGameRules().getRule(key);
        final boolean flag = rule instanceof GameRules.BooleanValue;
        final int current = flag ? (((GameRules.BooleanValue) rule).get() ? 1 : 0) : ((GameRules.IntegerValue) rule).get();
        final WorldSwitches world = WorldSwitches.get(server);
        final int[] owned = world.ownedRule(key.getId());
        final int target;
        if (live)
        {
            if (current == wanted)
            {
                return;
            }
            world.ownRule(key.getId(), owned != null ? owned[0] : current, wanted);
            target = wanted;
        }
        else
        {
            if (owned == null)
            {
                return;
            }
            world.releaseRule(key.getId());
            if (current != owned[1] || current == owned[0])
            {
                return;
            }
            target = owned[0];
        }
        if (flag)
        {
            ((GameRules.BooleanValue) rule).set(target != 0, server);
        }
        else
        {
            ((GameRules.IntegerValue) rule).set(target, server);
        }
        if (live)
        {
            LOGGER.info(appliedLog);
        }
        else
        {
            LOGGER.info("Gave the world its {} gamerule back ({} -> {}) — the tweak that changed it is off", key.getId(),
              flag ? String.valueOf(current != 0) : String.valueOf(current), flag ? String.valueOf(target != 0) : String.valueOf(target));
        }
    }

    static void applySpawnCapTweak()
    {
        final int target = AutopilotConfig.masterOn() ? AutopilotConfig.HOSTILE_SPAWN_CAP.get() : 70;
        if (net.minecraft.world.entity.MobCategory.MONSTER.getMaxInstancesPerChunk() != target)
        {
            ((com.backwards.colonyautopilot.mixin.MobCategoryAccessor) (Object) net.minecraft.world.entity.MobCategory.MONSTER).setMax(target);
            LOGGER.info("Hostile spawn cap set to {} (vanilla 70; tweaks.hostileSpawnCap)", target);
        }
    }

    private static final java.util.Map<net.minecraft.world.effect.MobEffectInstance, Integer> potionBaselines =
      new java.util.IdentityHashMap<>();

    static void applyPotionDurationTweak()
    {
        final double multiplier = AutopilotConfig.masterOn()
                                    ? AutopilotConfig.POTION_DURATION_MULTIPLIER.get() : 1.0;
        int scaled = 0;
        int pinned = 0;
        int potions = 0;
        for (final net.minecraft.world.item.alchemy.Potion potion : net.minecraft.core.registries.BuiltInRegistries.POTION)
        {
            boolean touched = false;
            for (final net.minecraft.world.effect.MobEffectInstance template : potion.getEffects())
            {
                if (template.getEffect().value().isInstantenous() || template.getDuration() <= 0)
                {
                    continue;
                }
                final int base = potionBaselines.computeIfAbsent(template, net.minecraft.world.effect.MobEffectInstance::getDuration);

                final boolean harmful =
                  template.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
                final int target = harmful ? base : (int) Math.round(base * multiplier);
                if (template.getDuration() != target)
                {
                    ((com.backwards.colonyautopilot.mixin.MobEffectInstanceAccessor) (Object) template).setDuration(target);
                    if (harmful)
                    {
                        pinned++;
                    }
                    else
                    {
                        scaled++;
                    }
                    touched = true;
                }
            }
            if (touched)
            {
                potions++;
            }
        }
        if (scaled > 0 || pinned > 0)
        {
            LOGGER.info("Potion durations x{} — {} beneficial/neutral effect(s) set, {} harmful pinned to vanilla, across {} potion(s) (tweaks.potionDurationMultiplier)",
              multiplier, scaled, pinned, potions);
        }
    }

    private static boolean rainFlipped;

    private static void applyRainTweak()
    {
        final var rainSetting = MinecoloniesAPIProxy.getInstance().getConfig().getServer().workersAlwaysWorkInRain;
        if (!AutopilotConfig.live(AutopilotConfig.WORKERS_WORK_IN_RAIN))
        {
            if (rainFlipped && rainSetting.get())
            {
                rainSetting.set(false);
                LOGGER.info("Turned MineColonies 'workers always work in rain' back off — the tweak that enabled it is off");
            }
            rainFlipped = false;
            return;
        }
        if (!rainSetting.get())
        {
            rainSetting.set(true);
            rainFlipped = true;
            LOGGER.info("Enabled MineColonies 'workers always work in rain' (disable via tweaks.workersWorkInRain)");
        }
    }
}
