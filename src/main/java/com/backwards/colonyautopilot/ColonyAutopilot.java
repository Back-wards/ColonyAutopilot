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

    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> GUARDS_IGNORE = net.minecraft.tags.TagKey.create(
      net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "guards_ignore"));

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
            final String page = "https://www.curseforge.com/members/backwards/projects";
            LOGGER.warn("this build no longer carries its maker's mark: Colony Autopilot was created by Backwards, " + page);
            NeoForge.EVENT_BUS.addListener((final net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) ->
              event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component
                .literal("This build of Colony Autopilot no longer carries its maker's mark. Colony Autopilot was created by Backwards: ")
                .append(net.minecraft.network.chat.Component.literal(page).withStyle(style -> style.withUnderlined(true)
                  .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, page))))));
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
        final DamageWatch damageWatch = new DamageWatch(director);
        NeoForge.EVENT_BUS.register(damageWatch);
        NeoForge.EVENT_BUS.register(new ChunkKeeper());
        final GolemKeeper golems = new GolemKeeper();
        NeoForge.EVENT_BUS.register(golems);
        NeoForge.EVENT_BUS.register(new WaterRescue());
        NeoForge.EVENT_BUS.register(research);
        NeoForge.EVENT_BUS.register(workshops);
        NeoForge.EVENT_BUS.register(new LampLighter(growth));
        NeoForge.EVENT_BUS.register(new Decorator(growth));
        final AutopilotCommands commands = new AutopilotCommands(growth, golems);
        com.backwards.colonyautopilot.net.SettingsPayloads.setCommands(commands);
        NeoForge.EVENT_BUS.register(commands);
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
        NeoForge.EVENT_BUS.register(new Arrivals());

        modBus.addListener((final FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            director.subscribeToMineColonies();
            milestones.subscribeToMineColonies();
            growth.subscribeToMineColonies();
            research.subscribeToMineColonies();
            damageWatch.subscribeToMineColonies();
            muteDomumBlockEntityFlood();
            silenceCavalryHorseDebug();
        }));

        NeoForge.EVENT_BUS.addListener((final ServerStartedEvent event) -> {
            AutopilotConfig.migrate();
            Exchange.loadOffers();
            applyWorldTweaks(event.getServer());
            applySpawnCapTweak();
            applyPotionDurationTweak();
            checkMineColoniesHolds();
            checkCitizenWall(event.getServer());
            checkStablemasterHold();
            primedOwners.clear();
        });

        NeoForge.EVENT_BUS.addListener((final net.neoforged.neoforge.event.TagsUpdatedEvent event) -> {
            if (event.shouldUpdateStaticData())
            {
                markTraps();
            }
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

        NeoForge.EVENT_BUS.addListener((final LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level)
            {
                primedOwners.keySet().removeIf(key -> key.dimension().equals(level.dimension()));
            }
        });
        NeoForge.EVENT_BUS.addListener((final ServerStoppedEvent event) -> {

            AutopilotConfig.setWorldMaster(false);
            applySpawnCapTweak();
            applyPotionDurationTweak();
            AutopilotConfig.setWorldMaster(null);

            WorldStructures.clear();
        });
        NeoForge.EVENT_BUS.addListener((final net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {

            ZoneMarkerItem.showZones(event.getServer(), growth);

            if (event.getServer().getTickCount() % 200 == 0)
            {
                applyRainTweak();
            }
        });

        NeoForge.EVENT_BUS.addListener((final net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) -> {
            if (event.getServer().getTickCount() % 20 == 1)
            {
                primeOwners();
            }
        });

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

    public static volatile boolean trapsTagged;

    private static void markTraps()
    {
        boolean any = false;
        for (final net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
        {
            final boolean trap = block.defaultBlockState().is(com.minecolonies.api.items.ModTags.dangerousBlocks);
            any |= trap;
            for (final net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates())
            {
                ((CitizenWall) state).colonyautopilot$setCitizenWall(trap);
            }
        }
        trapsTagged = any;
    }

    private static void checkCitizenWall(final MinecraftServer server)
    {
        final java.util.Iterator<net.minecraft.core.Holder<net.minecraft.world.level.block.Block>> traps =
          net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(com.minecolonies.api.items.ModTags.dangerousBlocks).iterator();
        if (!traps.hasNext())
        {
            return;
        }
        final net.minecraft.world.level.block.state.BlockState trap = traps.next().value().defaultBlockState();
        final net.minecraft.resources.ResourceLocation name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(trap.getBlock());
        final net.minecraft.world.level.BlockGetter nowhere = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
        final net.minecraft.world.phys.shapes.VoxelShape cube = net.minecraft.world.phys.shapes.Shapes.block();
        try
        {
            final net.minecraft.world.entity.Entity citizen = com.minecolonies.api.entity.ModEntities.CITIZEN.create(server.overworld());
            if (trap.getCollisionShape(nowhere, net.minecraft.core.BlockPos.ZERO, net.minecraft.world.phys.shapes.CollisionContext.of(citizen)) != cube)
            {
                LOGGER.warn("Trap blocks are not a wall to citizens: the hold on block collision cannot attach (a citizen is not stopped by {}),"
                  + " so a citizen who cuts a corner, stops or is knocked beside wire is cut again (MineTraps' barbed wire first)", name);
            }

            else if (trap.getCollisionShape(nowhere, net.minecraft.core.BlockPos.ZERO) != cube
                       && new net.minecraft.world.level.ClipContext(net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO,
                         net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, citizen)
                            .getBlockShape(trap, nowhere, net.minecraft.core.BlockPos.ZERO) == cube)
            {
                LOGGER.warn("Trap blocks hide what is behind them from citizens: the hold on line of sight cannot attach (a citizen's eyes are"
                  + " stopped by {}), so a guard neither sees nor shoots an enemy behind wire two high or wire on a bank, nor a small one caught in the wire", name);
            }
        }
        catch (final RuntimeException | LinkageError e)
        {
            LOGGER.warn("Trap blocks may not be a wall to citizens: the start-up check of that hold failed", e);
        }
    }

    private static void checkMineColoniesHolds()
    {
        checkHolds("com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob",
          "the pathfinder holds on {} cannot attach, so citizens' routes may run into trap blocks again (through MineTraps' barbed wire fence,"
            + " under a strand at head height, or up a step with a strand overhead), where the wall stops them until MineColonies' stuck handler moves them",
          "isPassable(Lnet/minecraft/world/level/block/state/BlockState;IIILcom/minecolonies/core/entity/pathfinding/MNode;Z)Z",
          "checkHeadBlock(Lcom/minecolonies/core/entity/pathfinding/MNode;III)Z",
          "handleTargetNotPassable(Lcom/minecolonies/core/entity/pathfinding/MNode;IIILnet/minecraft/world/level/block/state/BlockState;)I");
        checkHolds("com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils",
          "the arrival holds on {} cannot attach, so a citizen arrives only within 4 blocks of a hut block again and a hut lifted high"
            + " over its ground stalls its workers (tweaks.reachHutFromAnyHeight)",
          "walkToBuilding(Lcom/minecolonies/api/entity/other/AbstractFastMinecoloniesEntity;Lcom/minecolonies/api/colony/buildings/IBuilding;)Z",
          "walkToPos(Lcom/minecolonies/api/entity/other/AbstractFastMinecoloniesEntity;Lnet/minecraft/core/BlockPos;IZ)Z");
        checkHolds("com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable",
          "the stall filter on {} cannot attach, so a Stable's mounts may be sent to a stall tag far outside the building again"
            + " (Fortress' stable1, Colonial's stable2)",
          "stallPositions()Ljava/util/List;");
        checkHolds("com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable",
          "the town patrol on {} cannot attach, so the Stable's riders patrol only Stables and Gate Houses again, and in a town"
            + " with one Stable never leave it",
          "cavalryPatrolFilter()Ljava/util/function/Predicate;");
        checkHolds("com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable",
          "the riders' next building on {} cannot be named, so only one outing in three ends at a building again: the others end"
            + " at the point before, a spot near it or the Stable itself",
          "startPatrolNext()V");
        checkHolds("com.minecolonies.core.entity.ai.workers.guard.EntityAICavalry",
          "the riders' way home on {} cannot attach, so a rider whose outing ended far from his Stable stands where it ended"
            + " until the next one again, and does not come home between the two",
          "patrol()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;");
        checkHolds("com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler",
          "the early rescue on {} cannot attach, so a citizen boxed in on land waits MineColonies' own two to five minutes again"
            + " before its stuck handler moves him",
          "checkStuck(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;)V",
          "completeStuckAction(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;)V",
          "getStuckLevel()I");
        checkHolds("com.minecolonies.core.entity.citizen.EntityCitizen",
          "the in-wall count on {} cannot attach, so a citizen whose route leads into a wall (a rider under a low gate) is lifted"
            + " out of it and walks back into it again for as long as he takes that route",
          "handleInWallDamage(Lnet/minecraft/world/damagesource/DamageSource;)Z");
        checkHolds("com.minecolonies.core.colony.workorders.WorkOrderBuilding",
          "the builders' reach on {} cannot attach, so a Builder's Hut takes building orders only within 100 blocks of itself again,"
            + " the nearest free crew is not preferred, and an order the autopilot files past that reach is turned down or waits"
            + " for a crew that never claims it",
          "canBuild(Lcom/minecolonies/api/colony/buildings/IBuilding;)Z",
          "tooFarFromAnyBuilder(Lcom/minecolonies/api/colony/IColony;I)Z");
        checkHolds("com.minecolonies.core.colony.workorders.WorkManager",
          "the claim walk the builders' reach copies, {}, has moved, so the nearest-free-crew rule may name a hut MineColonies never"
            + " offers the order to and leave it unclaimed: compare its skips with UpgradeDirector.nearestCrew",
          "tryAssignWorkOrder(Lcom/minecolonies/api/colony/workorders/IServerWorkOrder;Ljava/util/function/Predicate;)V");
    }

    private static void checkStablemasterHold()
    {
        final String lost = "the readying hold on {} cannot attach, so the Stablemaster asks for his leather list again and, with leather in the"
          + " warehouse, a courier brings it in place of the horse armour that readies a mount at once";
        checkHolds("com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkStablemaster", lost,
          "readyMount(Lcom/minecolonies/core/entity/ai/workers/production/herders/EntityAIWorkStablemaster$MountMaintenance;"
            + "Lcom/minecolonies/core/entity/other/cavalry/CavalryHorseEntity;)Z");
        checkHolds("com.minecolonies.api.colony.ICivilianData", lost,
          "createRequestAsync(Lcom/minecolonies/api/colony/requestsystem/requestable/IRequestable;)Lcom/minecolonies/api/colony/requestsystem/token/IToken;");
        final String pickLost = "the pick hold on {} cannot attach, so a mount that healed while it waited holds the Stablemaster's"
          + " pick again until it needs something, and no other mount is health-trained, fed or readied meanwhile";
        checkHolds("com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkStablemaster", pickLost,
          "readyMountForCombat()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;");
        try
        {
            if (Class.forName("com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkStablemaster")
                  .getDeclaredField("horseToGetReady").getType() != com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity.class)
            {
                LOGGER.warn("MineColonies has changed: " + pickLost, "horseToGetReady");
            }
        }
        catch (final NoSuchFieldException e)
        {
            LOGGER.warn("MineColonies has changed: " + pickLost, "horseToGetReady");
        }
        catch (final ClassNotFoundException | LinkageError e)
        {

        }
    }

    private static void checkHolds(final String owner, final String lost, final String... methods)
    {
        final java.util.Set<String> missing = new java.util.HashSet<>(java.util.Set.of(methods));
        try
        {
            for (final java.lang.reflect.Method method : Class.forName(owner).getDeclaredMethods())
            {
                missing.remove(method.getName()
                  + java.lang.invoke.MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString());
            }
        }
        catch (final ClassNotFoundException | LinkageError e)
        {

        }
        if (!missing.isEmpty())
        {
            LOGGER.warn("MineColonies has changed: " + lost, missing);
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

    private static void silenceCavalryHorseDebug()
    {
        try
        {
            NeoForge.EVENT_BUS.unregister(Class.forName("com.minecolonies.core.entity.other.cavalry.CavalryHorseDebug"));

            LOGGER.info("Asked the event bus to drop MineColonies' cavalry-horse debug logger (a stack trace per hit, death and chunk unload of every mount)");
        }
        catch (final ClassNotFoundException e)
        {

        }
        catch (final Throwable t)
        {
            LOGGER.info("Could not silence MineColonies' cavalry-horse debug logger", t);
        }
    }

    static ColonyId colonyKey(final IColony colony)
    {
        return new ColonyId(colony.getID(), colony.getDimension());
    }

    static net.neoforged.neoforge.common.util.FakePlayer fakeOwner(final ServerLevel level, final IColony colony)
    {
        final GameRules.IntegerValue radius = level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS);
        final int own = radius.get();
        final net.minecraft.world.level.storage.WorldData worldData = level.getServer().getWorldData();
        final net.minecraft.world.level.GameType mode = worldData.getGameType();
        final net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        final boolean spawnUnloaded = level.getChunkSource().getChunkNow(spawn.getX() >> 4, spawn.getZ() >> 4) == null;

        try
        {
            radius.set(0, null);
            if (spawnUnloaded)
            {

                worldData.setGameType(net.minecraft.world.level.GameType.ADVENTURE);
            }
            return net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
              new com.mojang.authlib.GameProfile(colony.getPermissions().getOwner(), colony.getPermissions().getOwnerName()));
        }
        finally
        {
            radius.set(own, null);
            if (spawnUnloaded)
            {
                worldData.setGameType(mode);
            }
        }
    }

    private static final java.util.Map<ColonyId, com.mojang.authlib.GameProfile> primedOwners = new java.util.HashMap<>();

    private static void primeOwners()
    {
        final java.util.Set<ColonyId> live = new java.util.HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final ColonyId key = colonyKey(colony);
            live.add(key);
            try
            {
                final com.mojang.authlib.GameProfile owner =
                  new com.mojang.authlib.GameProfile(colony.getPermissions().getOwner(), colony.getPermissions().getOwnerName());

                if (colony.getWorld() instanceof ServerLevel level && !owner.equals(primedOwners.put(key, owner)))
                {
                    fakeOwner(level, colony);
                }
            }
            catch (final Exception e)
            {
                LOGGER.warn("[{}] could not make the colony owner's fake player ahead of the workers who ask for him", colony.getName(), e);
            }
        }
        retainColonies(live, primedOwners);
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

    private static void applyMobGriefingTweak(final MinecraftServer server)
    {
        ownRule(server, GameRules.RULE_MOBGRIEFING, AutopilotConfig.live(AutopilotConfig.DISABLE_MOB_GRIEFING), 0,
          "Disabled mob griefing (mobGriefing=false, tweaks.disableMobGriefing) — creepers and modded mobs break no blocks");
    }

    private static void applySpawnRadiusTweak(final MinecraftServer server)
    {
        final int wanted = AutopilotConfig.SPAWN_CHUNK_RADIUS.get();
        ownRule(server, GameRules.RULE_SPAWN_CHUNK_RADIUS,
          AutopilotConfig.masterOn() && wanted > 0 && net.neoforged.fml.ModList.get().isLoaded("sporeinquisition"), wanted,
          "Spawn chunks kept loaded to radius " + wanted + " (vanilla 2; tweaks.spawnChunkRadius) — the rednight's battlefield at world spawn always ticks");
    }

    static void applyWorldTweaks(final MinecraftServer server)
    {
        applyRainTweak();
        applyPatrolTweak(server);
        applyFireTweak(server);
        applyMobGriefingTweak(server);
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
