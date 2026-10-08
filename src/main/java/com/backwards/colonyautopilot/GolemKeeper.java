// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GolemKeeper
{
    private static final int PERIOD_TICKS = 600;

    private static final String GOLEM_TAG = ColonyAutopilot.MOD_ID + ":village_golem";

    private static final String POST_TAG = ColonyAutopilot.MOD_ID + ":golem_post";

    private static final String HOME_TAG = ColonyAutopilot.MOD_ID + ":golem_home";

    private static final int PATROL_RADIUS = 48;

    private static final int ADOPTION_SCAN = PATROL_RADIUS + PATROL_RADIUS / 2 + 16;

    private static final long RESPAWN_COOLDOWN_TICKS = 200L;

    private static final long REFORGE_AFTER_FALL_TICKS = 24000L;

    private static final String HALL_GOLEM_NAME = "Village Golem";
    private static final String SMITH_GOLEM_NAME = "Blacksmith's Golem";

    private int tickCounter = 14;

    private final ColonyRota rota = new ColonyRota();

    private final Map<ColonyId, Map<Long, Long>> lastSpawn = new HashMap<>();

    private final Map<ColonyId, Map<Long, UUID>> guardians = new HashMap<>();

    private final Map<UUID, GlobalPos> wentCold = new HashMap<>();

    private final Set<GlobalPos> twinCheck = new HashSet<>();

    private static GolemKeeper instance;

    public GolemKeeper()
    {
        instance = this;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        lastSpawn.clear();
        guardians.clear();
        wentCold.clear();
        twinCheck.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {
        lastSpawn.clear();
        guardians.clear();
        wentCold.clear();
        twinCheck.clear();
    }

    @SubscribeEvent
    public void onEntityLeave(final EntityLeaveLevelEvent event)
    {
        if (!(event.getEntity() instanceof IronGolem golem) || !(event.getLevel() instanceof ServerLevel level)
              || !golem.getPersistentData().getBoolean(GOLEM_TAG))
        {
            return;
        }
        final Entity.RemovalReason reason = golem.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK)
        {
            wentCold.put(golem.getUUID(), GlobalPos.of(level.dimension(), golem.blockPosition()));
        }
        else
        {
            wentCold.remove(golem.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGolemDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof IronGolem golem) || !(golem.level() instanceof ServerLevel level)
              || !golem.getPersistentData().getBoolean(GOLEM_TAG))
        {
            return;
        }
        golem.setCustomName(null);
        ColonyGrounds.get(level).setGolemFall(golem.getPersistentData().getLong(POST_TAG), level.getGameTime());
        ColonyAutopilot.LOGGER.info("a village golem fell at {} — its post forges the next one a game-day from now",
          golem.blockPosition().toShortString());
    }

    @SubscribeEvent
    public void onEntityJoin(final EntityJoinLevelEvent event)
    {
        if (event.getEntity() instanceof IronGolem golem && event.getLevel() instanceof ServerLevel level
              && golem.getPersistentData().getBoolean(GOLEM_TAG))
        {
            wentCold.remove(golem.getUUID());
            twinCheck.add(GlobalPos.of(level.dimension(), BlockPos.of(golem.getPersistentData().getLong(POST_TAG))));
        }
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {
            return;
        }
        if (++tickCounter >= PERIOD_TICKS)
        {
            tickCounter = 0;
            final Set<ColonyId> live = rota.fill(event.getServer());
            ColonyAutopilot.retainColonies(live, lastSpawn, guardians);
            final Set<UUID> onTheBooks = new HashSet<>();
            for (final Map<Long, UUID> posts : guardians.values())
            {
                onTheBooks.addAll(posts.values());
            }
            wentCold.keySet().retainAll(onTheBooks);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {
            if (!AutopilotConfig.get(colony, AutopilotConfig.VILLAGE_GOLEM))
            {
                continue;
            }
            try
            {
                ensureGolems(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Golem keeping failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void ensureGolems(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        if (townHall == null || townHall.getBuildingLevel() <= 0)
        {
            return;
        }

        final Set<Long> livePosts = new HashSet<>();
        livePosts.add(sideKey(townHall.getPosition(), 0));
        livePosts.add(sideKey(townHall.getPosition(), 1));
        ensurePost(colony, level, townHall, true, 0);
        ensurePost(colony, level, townHall, true, 1);
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBlacksmith && building.getBuildingLevel() > 0)
            {
                livePosts.add(sideKey(building.getPosition(), 0));
                ensurePost(colony, level, building, false, 0);
            }
        }

        for (final Entity named : level.getEntities(EntityTypeTest.forClass(Entity.class), entity -> SporeCompat.isSporeEntity(entity) && entity.getCustomName() != null
              && (HALL_GOLEM_NAME.equals(entity.getCustomName().getString()) || SMITH_GOLEM_NAME.equals(entity.getCustomName().getString()))))
        {
            if (WorldUtil.isChunkLoaded(level, named.getBlockX() >> 4, named.getBlockZ() >> 4)
                  && IColonyManager.getInstance().getColonyByPosFromWorld(level, named.blockPosition()) == colony)
            {
                named.setCustomName(null);
            }
        }

        ColonyGrounds.get(level).pruneGolemFalls(level.getGameTime() - REFORGE_AFTER_FALL_TICKS);

        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<Long, Long> colonySpawns = lastSpawn.get(key);
        if (colonySpawns != null)
        {
            colonySpawns.keySet().retainAll(livePosts);
        }
        final Map<Long, UUID> colonyGuardians = guardians.get(key);
        if (colonyGuardians != null)
        {
            colonyGuardians.keySet().retainAll(livePosts);
        }
    }

    private static long sideKey(final BlockPos post, final int side)
    {
        return side == 0 ? post.asLong() : BlockPos.asLong(post.getX(), post.getY() + 1024, post.getZ());
    }

    private void ensurePost(final IColony colony, final ServerLevel level, final IBuilding building, final boolean isTownHall, final int side)
    {
        final BlockPos post = building.getPosition();
        if (!WorldUtil.isChunkLoaded(level, post.getX() >> 4, post.getZ() >> 4))
        {
            return;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final long now = level.getGameTime();
        final long postKey = sideKey(post, side);
        final Map<Long, Long> colonySpawns = lastSpawn.computeIfAbsent(key, k -> new HashMap<>());

        final Map<Long, UUID> colonyGuardians = guardians.computeIfAbsent(key, k -> new HashMap<>());
        final UUID known = colonyGuardians.get(postKey);
        if (known != null)
        {
            if (level.getEntity(known) instanceof IronGolem golem && golem.isAlive())
            {

                restoreLeash(golem, post);
                if (twinCheck.remove(GlobalPos.of(level.dimension(), BlockPos.of(postKey))))
                {
                    for (final IronGolem twin : level.getEntitiesOfClass(IronGolem.class,
                      new AABB(post).inflate(ADOPTION_SCAN, 48, ADOPTION_SCAN),
                      candidate -> candidate != golem && candidate.isAlive() && candidate.getPersistentData().getBoolean(GOLEM_TAG)
                                     && candidate.getPersistentData().getLong(POST_TAG) == postKey))
                    {
                        twin.discard();
                    }
                }
                return;
            }

            final GlobalPos cold = wentCold.get(known);
            if (cold != null && cold.dimension().equals(level.dimension())
                  && !WorldUtil.isChunkLoaded(level, cold.pos().getX() >> 4, cold.pos().getZ() >> 4))
            {
                return;
            }
            colonyGuardians.remove(postKey);
            wentCold.remove(known);
        }

        final List<IronGolem> ours = level.getEntitiesOfClass(IronGolem.class,
          new AABB(post).inflate(ADOPTION_SCAN, 48, ADOPTION_SCAN),
          golem -> golem.isAlive() && golem.getPersistentData().getBoolean(GOLEM_TAG)
                     && golem.getPersistentData().getLong(POST_TAG) == postKey);
        if (!ours.isEmpty())
        {
            colonyGuardians.put(postKey, ours.get(0).getUUID());
            restoreLeash(ours.get(0), post);
            for (final IronGolem extra : ours.subList(1, ours.size()))
            {
                extra.discard();
            }
            return;
        }

        final Long last = colonySpawns.get(postKey);
        if (last != null && now - last < RESPAWN_COOLDOWN_TICKS)
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final Long fell = grounds.golemFall(postKey);
        if (fell != null && now >= fell && now - fell < REFORGE_AFTER_FALL_TICKS)
        {
            return;
        }

        Direction avoid = null;
        if (isTownHall)
        {
            final UUID twin = colonyGuardians.get(sideKey(post, 1 - side));
            if (twin != null && level.getEntity(twin) instanceof IronGolem living && living.isAlive())
            {
                final CompoundTag twinData = living.getPersistentData();
                final BlockPos twinAt = twinData.contains(HOME_TAG) ? BlockPos.of(twinData.getLong(HOME_TAG)) : living.blockPosition();
                final int gx = twinAt.getX() - post.getX();
                final int gz = twinAt.getZ() - post.getZ();
                avoid = Math.abs(gx) >= Math.abs(gz) ? (gx >= 0 ? Direction.EAST : Direction.WEST)
                                                     : (gz >= 0 ? Direction.SOUTH : Direction.NORTH);
            }
        }

        final BlockPos stand = spawnSpotOutside(level, building, side == 1, avoid);
        if (stand == null)
        {

            ColonyAutopilot.LOGGER.debug("[{}] no dry standing spot outside the {} yet — golem deferred",
              colony.getName(), isTownHall ? "town hall" : "blacksmith");
            return;
        }
        final IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem == null)
        {
            return;
        }
        golem.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
        golem.setPlayerCreated(true);
        golem.setPersistenceRequired();

        final int sx = stand.getX() - post.getX();
        final int sz = stand.getZ() - post.getZ();
        final Direction sideDir = Math.abs(sx) >= Math.abs(sz) ? (sx >= 0 ? Direction.EAST : Direction.WEST)
                                                               : (sz >= 0 ? Direction.SOUTH : Direction.NORTH);
        final BlockPos home = isTownHall ? post.relative(sideDir, PATROL_RADIUS / 2) : post;
        golem.restrictTo(home, PATROL_RADIUS);
        golem.getPersistentData().putBoolean(GOLEM_TAG, true);
        golem.getPersistentData().putLong(POST_TAG, postKey);
        golem.getPersistentData().putLong(HOME_TAG, home.asLong());
        golem.setCustomName(Component.literal(isTownHall ? HALL_GOLEM_NAME : SMITH_GOLEM_NAME));
        level.addFreshEntity(golem);
        colonySpawns.put(postKey, now);
        colonyGuardians.put(postKey, golem.getUUID());
        grounds.clearGolemFall(postKey);

        if (last == null)
        {
            ColonyAutopilot.LOGGER.info("[{}] an iron golem now patrols the {} side of the {} at {}",
              colony.getName(), sideDir.getName(), isTownHall ? "town hall" : "blacksmith", post.toShortString());
            Milestones.say(colony, isTownHall ? "colonyautopilot.milestone.golem" : "colonyautopilot.milestone.golem.blacksmith");
        }
        else
        {
            ColonyAutopilot.LOGGER.debug("[{}] reforged the fallen {} golem at {}",
              colony.getName(), isTownHall ? "town hall" : "blacksmith", post.toShortString());
        }
    }

    private static void restoreLeash(final IronGolem golem, final BlockPos post)
    {
        if (golem.hasRestriction())
        {
            return;
        }
        final CompoundTag data = golem.getPersistentData();
        final BlockPos home = data.contains(HOME_TAG) ? BlockPos.of(data.getLong(HOME_TAG)) : post;
        golem.restrictTo(home, PATROL_RADIUS);
    }

    int dismissAll()
    {
        int removed = 0;

        final Set<ServerLevel> levels = new HashSet<>();

        final Set<ColonyId> kept = new HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (colony.getWorld() instanceof ServerLevel level)
            {
                levels.add(level);
                if (AutopilotConfig.get(colony, AutopilotConfig.VILLAGE_GOLEM))
                {
                    kept.add(ColonyAutopilot.colonyKey(colony));
                }
            }
        }
        for (final ServerLevel level : levels)
        {
            for (final IronGolem golem : level.getEntities(EntityType.IRON_GOLEM,
              candidate -> candidate.isAlive() && candidate.getPersistentData().getBoolean(GOLEM_TAG)))
            {
                final IColony at = IColonyManager.getInstance().getColonyByPosFromWorld(level, golem.blockPosition());
                if (at != null && kept.contains(ColonyAutopilot.colonyKey(at)))
                {
                    continue;
                }
                golem.discard();
                removed++;
            }
        }
        lastSpawn.keySet().retainAll(kept);
        guardians.keySet().retainAll(kept);
        final Set<UUID> onTheBooks = new HashSet<>();
        for (final Map<Long, UUID> posts : guardians.values())
        {
            onTheBooks.addAll(posts.values());
        }
        wentCold.keySet().retainAll(onTheBooks);
        twinCheck.clear();
        return removed;
    }

    static int dismiss(final IColony colony)
    {
        if (instance == null || !(colony.getWorld() instanceof ServerLevel level))
        {
            return 0;
        }
        final Set<Long> posts = new HashSet<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building == colony.getServerBuildingManager().getTownHall())
            {
                posts.add(sideKey(building.getPosition(), 0));
                posts.add(sideKey(building.getPosition(), 1));
            }
            else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBlacksmith)
            {
                posts.add(sideKey(building.getPosition(), 0));
            }
        }
        int removed = 0;
        for (final IronGolem golem : level.getEntities(EntityType.IRON_GOLEM,
          candidate -> candidate.isAlive() && candidate.getPersistentData().getBoolean(GOLEM_TAG)))
        {
            if (posts.contains(golem.getPersistentData().getLong(POST_TAG))
                  || IColonyManager.getInstance().getColonyByPosFromWorld(level, golem.blockPosition()) == colony)
            {
                golem.discard();
                removed++;
            }
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        instance.lastSpawn.remove(key);
        final Map<Long, UUID> gone = instance.guardians.remove(key);
        if (gone != null)
        {
            instance.wentCold.keySet().removeAll(gone.values());
        }
        return removed;
    }

    private static BlockPos spawnSpotOutside(final ServerLevel level, final IBuilding building,
      final boolean mirrorFirst, final Direction avoid)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final int midX = (corners.getA().getX() + corners.getB().getX()) / 2;
        final int midZ = (corners.getA().getZ() + corners.getB().getZ()) / 2;
        final Direction[] order = mirrorFirst
                                    ? new Direction[] {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}
                                    : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

        final int postY = building.getPosition().getY();
        BlockPos fallback = null;
        for (final Direction dir : order)
        {
            if (dir == avoid)
            {
                continue;
            }

            for (int out = 2; out <= 6; out++)
            {
                final int x = dir == Direction.EAST ? corners.getB().getX() + out
                                : dir == Direction.WEST ? corners.getA().getX() - out : midX;
                final int z = dir == Direction.SOUTH ? corners.getB().getZ() + out
                                : dir == Direction.NORTH ? corners.getA().getZ() - out : midZ;
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    break;
                }
                final BlockPos stand = standableColumn(level, x, z, postY);
                if (stand == null)
                {
                    continue;
                }
                if (fallback == null)
                {
                    fallback = stand;
                }
                if (Math.abs(stand.getY() - postY) <= 6)
                {
                    return stand;
                }
            }
        }
        return fallback;
    }

    private static BlockPos standableColumn(final ServerLevel level, final int x, final int z, final int refY)
    {

        final int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), refY + 5);
        for (int y = top; y >= refY - 8; y--)
        {
            final BlockPos feet = new BlockPos(x, y, z);
            final var floor = level.getBlockState(feet.below());
            final boolean solidDryFloor = !floor.getCollisionShape(level, feet.below()).isEmpty()
                  && floor.getFluidState().isEmpty() && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(Blocks.CAMPFIRE)
                  && !floor.is(BlockTags.LOGS) && !floor.is(BlockTags.LEAVES);
            if (solidDryFloor && bodyRoom(level, feet) && bodyRoom(level, feet.above()) && bodyRoom(level, feet.above(2)))
            {
                return feet;
            }
        }
        return null;
    }

    private static boolean bodyRoom(final ServerLevel level, final BlockPos cell)
    {
        final var state = level.getBlockState(cell);
        return state.getCollisionShape(level, cell).isEmpty() && state.getFluidState().isEmpty();
    }
}
