// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Paddocks
{

    private static Map<Block, EntityType<? extends Animal>> livestock;

    private static Map<Block, EntityType<? extends Animal>> livestock()
    {
        if (livestock == null)
        {
            livestock = Map.of(
              ModBlocks.blockHutShepherd, EntityType.SHEEP,
              ModBlocks.blockHutCowboy, EntityType.COW,
              ModBlocks.blockHutChickenHerder, EntityType.CHICKEN,
              ModBlocks.blockHutSwineHerder, EntityType.PIG,
              ModBlocks.blockHutRabbitHutch, EntityType.RABBIT);
        }
        return livestock;
    }

    private Paddocks()
    {
    }

    static boolean keepsAnimals(final IBuilding building)
    {
        final Block block = building.getBuildingType().getBuildingBlock();
        return livestock().containsKey(block) || block == ModBlocks.blockHutBeekeeper;
    }

    static void ensure(final ServerLevel level, final IColony colony, final IBuilding building)
    {
        if (building instanceof BuildingBeekeeper beekeeper)
        {
            ensureHive(level, colony, beekeeper);
            return;
        }
        final EntityType<? extends Animal> species = livestock().get(building.getBuildingType().getBuildingBlock());
        if (species == null)
        {
            return;
        }
        final AnimalHerdingModule module = building.getModule(AnimalHerdingModule.class);
        if (module == null)
        {
            return;
        }

        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final AABB pen = new AABB(corners.getA().getX(), corners.getA().getY(), corners.getA().getZ(),
          corners.getB().getX(), corners.getB().getY(), corners.getB().getZ());
        final int inPen = level.getEntitiesOfClass(Animal.class, pen, animal -> animal.getType() == species).size();
        if (inPen >= 2)
        {
            return;
        }
        final BlockPos spot = penSpot(level, building);
        if (spot == null)
        {

            ColonyAutopilot.LOGGER.info("[{}] the {} at {} has no sealed paddock anywhere in its grounds — starter {} withheld (fenceless style, or a gap in the fence line)",
              colony.getName(), building.getBuildingDisplayName(), building.getPosition().toShortString(),
              species.getDescription().getString());
            return;
        }
        int spawned = 0;
        for (int i = inPen; i < 2; i++)
        {
            final Animal animal = species.spawn(level, spot, MobSpawnType.MOB_SUMMONED);
            if (animal != null)
            {

                animal.setPersistenceRequired();
                spawned++;
            }
        }
        if (spawned > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] bought {} {} at market for the {} — the pen was short its breeding pair",
              colony.getName(), spawned, species.getDescription().getString(), building.getBuildingDisplayName());

            Milestones.say(colony, "colonyautopilot.milestone.animals",
              spawned, species.getDescription().getString(), Component.translatableEscape(building.getBuildingDisplayName()));
        }
    }

    private static BlockPos penSpot(final ServerLevel level, final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final int minX = Math.min(corners.getA().getX(), corners.getB().getX());
        final int maxX = Math.max(corners.getA().getX(), corners.getB().getX());
        final int minZ = Math.min(corners.getA().getZ(), corners.getB().getZ());
        final int maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ());
        final int floorY = Math.min(corners.getA().getY(), corners.getB().getY());
        final int roofY = Math.max(corners.getA().getY(), corners.getB().getY());

        final List<BlockPos> seeds = new ArrayList<>();
        for (int x = minX + 1; x <= maxX - 1; x++)
        {
            for (int z = minZ + 1; z <= maxZ - 1; z++)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                for (int y = floorY; y < roofY; y++)
                {
                    if (standable(level, x, y, z))
                    {
                        seeds.add(new BlockPos(x, y, z));
                        break;
                    }
                }
            }
        }

        final Set<Long> visited = new HashSet<>();
        BlockPos best = null;
        int bestSize = 0;
        boolean bestSky = false;
        for (final BlockPos seed : seeds)
        {
            if (!visited.add(seed.asLong()))
            {
                continue;
            }
            final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            final List<BlockPos> region = new ArrayList<>();
            boolean escaped = false;
            queue.add(seed);
            while (!queue.isEmpty())
            {
                final BlockPos cell = queue.poll();
                region.add(cell);
                if (cell.getX() <= minX || cell.getX() >= maxX || cell.getZ() <= minZ || cell.getZ() >= maxZ)
                {

                    escaped = true;
                    continue;
                }
                for (int dir = 0; dir < 4; dir++)
                {
                    final int x2 = cell.getX() + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                    final int z2 = cell.getZ() + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        final int y2 = cell.getY() + dy;
                        if (y2 < floorY || y2 >= roofY)
                        {
                            continue;
                        }
                        final BlockPos next = new BlockPos(x2, y2, z2);
                        if (standable(level, x2, y2, z2) && visited.add(next.asLong()))
                        {
                            queue.add(next);
                        }
                    }
                }
            }
            if (escaped)
            {
                continue;
            }

            boolean sky = false;
            BlockPos pick = region.get(0);
            for (final BlockPos cell : region)
            {
                if (level.canSeeSky(cell))
                {
                    sky = true;
                    pick = cell;
                    break;
                }
            }
            if (best == null || (sky && !bestSky) || (sky == bestSky && region.size() > bestSize))
            {
                best = pick;
                bestSize = region.size();
                bestSky = sky;
            }
        }
        return best;
    }

    private static boolean standable(final ServerLevel level, final int x, final int y, final int z)
    {
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        final BlockState below = level.getBlockState(probe.set(x, y - 1, z));
        return below.isSolidRender(level, probe) && below.getFluidState().isEmpty()
                 && isClear(level.getBlockState(probe.set(x, y, z)))
                 && isClear(level.getBlockState(probe.set(x, y + 1, z)));
    }

    private static boolean isClear(final BlockState state)
    {
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty()
                 && !(state.getBlock() instanceof FenceGateBlock) && !(state.getBlock() instanceof DoorBlock);
    }

    private static int plantFlowers(final ServerLevel level, final IColony colony, final BlockPos hive)
    {
        final List<SiteSelector.Footprint> zones = ProtectedZones.footprintsFor(colony);
        int planted = 0;
        for (final BlockPos side : new BlockPos[] {hive.north(2), hive.south(2), hive.east(2), hive.west(2)})
        {
            final BlockState at = level.getBlockState(side);
            if (planted >= 2 || !at.canBeReplaced() || !at.getFluidState().isEmpty())
            {
                continue;
            }
            boolean zoned = false;
            for (final SiteSelector.Footprint zone : zones)
            {
                zoned |= zone.intersects(side.getX(), side.getZ(), side.getX(), side.getZ());
            }
            if (zoned)
            {
                continue;
            }
            final BlockPos ground = side.below();
            final BlockState soil = level.getBlockState(ground);
            if (!soil.is(BlockTags.DIRT))
            {
                if (!soil.is(Blocks.SAND) && !soil.is(Blocks.RED_SAND))
                {
                    continue;
                }
                WorldUtil.setBlockState(level, ground, Blocks.GRASS_BLOCK.defaultBlockState());
            }
            WorldUtil.setBlockState(level, side, Blocks.POPPY.defaultBlockState());
            planted++;
        }
        return planted;
    }

    private static void ensureHive(final ServerLevel level, final IColony colony, final BuildingBeekeeper beekeeper)
    {

        for (final BlockPos hive : List.copyOf(beekeeper.getHives()))
        {
            if (WorldUtil.isChunkLoaded(level, hive.getX() >> 4, hive.getZ() >> 4)
                  && !(level.getBlockEntity(hive) instanceof BeehiveBlockEntity))
            {
                beekeeper.removeHive(hive);
                ColonyAutopilot.LOGGER.info("[{}] the {}'s hive at {} is gone from the world — deregistered; a fresh nest will be set up",
                  colony.getName(), beekeeper.getBuildingDisplayName(), hive.toShortString());
            }
        }
        if (!beekeeper.getHives().isEmpty())
        {
            int beesAlive = 0;
            BlockPos home = null;
            for (final BlockPos hive : beekeeper.getHives())
            {
                if (!WorldUtil.isChunkLoaded(level, hive.getX() >> 4, hive.getZ() >> 4))
                {
                    continue;
                }
                if (home == null)
                {
                    home = hive;
                }

                if (level.getBlockEntity(hive) instanceof BeehiveBlockEntity nest)
                {
                    beesAlive += nest.getOccupantCount();
                }
                beesAlive += level.getEntitiesOfClass(Bee.class, new AABB(hive).inflate(48, 8, 48), Bee::isAlive).size();

                int flowers = 0;
                for (final BlockPos probe : BlockPos.betweenClosed(hive.offset(-3, -1, -3), hive.offset(3, 1, 3)))
                {
                    if (level.getBlockState(probe).is(BlockTags.SMALL_FLOWERS))
                    {
                        flowers++;
                    }
                }
                if (flowers > 0)
                {
                    continue;
                }
                final int planted = plantFlowers(level, colony, hive);
                if (planted > 0)
                {
                    ColonyAutopilot.LOGGER.info("[{}] replanted {} flowers by the {}'s hive at {} — the bees were out of blooms",
                      colony.getName(), planted, beekeeper.getBuildingDisplayName(), hive.toShortString());
                }
                else
                {
                    ColonyAutopilot.LOGGER.debug("[{}] no plantable ground within reach of the {}'s hive at {} — no blooms, no honey",
                      colony.getName(), beekeeper.getBuildingDisplayName(), hive.toShortString());
                }
            }
            if (home != null && beesAlive == 0)
            {

                int moved = 0;
                for (int i = 0; i < 2; i++)
                {
                    final Mob bee = EntityType.BEE.spawn(level, home.above(), MobSpawnType.MOB_SUMMONED);
                    if (bee != null)
                    {
                        bee.setPersistenceRequired();
                        moved++;
                    }
                }
                if (moved > 0)
                {
                    ColonyAutopilot.LOGGER.info("[{}] the {}'s bees died out — {} new bees moved in at {}",
                      colony.getName(), beekeeper.getBuildingDisplayName(), moved, home.toShortString());
                }
            }
            return;
        }
        final BlockPos anchor = beekeeper.getPosition();
        for (int distance = 3; distance <= 9; distance += 3)
        {
            for (int angle = 0; angle < 8; angle++)
            {
                final double theta = angle * Math.PI / 4;
                final int x = anchor.getX() + (int) Math.round(Math.cos(theta) * distance);
                final int z = anchor.getZ() + (int) Math.round(Math.sin(theta) * distance);
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    continue;
                }
                final BlockPos nest = new BlockPos(x, SiteSelector.groundHeight(level, x, z), z);
                if (nest.getY() > anchor.getY() + 12)
                {
                    continue;

                }
                final var ground = level.getBlockState(nest.below());

                if (!level.getBlockState(nest).canBeReplaced() || !level.getBlockState(nest.above()).isAir()
                      || !(ground.is(net.minecraft.tags.BlockTags.DIRT) || ground.is(net.minecraft.tags.BlockTags.SAND))
                      || level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) > nest.getY())
                {
                    continue;
                }
                boolean taken = false;
                for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
                {
                    if (other != beekeeper && SiteSelector.boxOf(other).intersects(x, z, x, z))
                    {
                        taken = true;
                        break;
                    }
                }
                for (final SiteSelector.Footprint zone : ProtectedZones.footprintsFor(colony))
                {
                    if (zone.intersects(x, z, x, z))
                    {
                        taken = true;
                        break;
                    }
                }
                if (taken)
                {
                    continue;
                }
                WorldUtil.setBlockState(level, nest, Blocks.BEE_NEST.defaultBlockState());
                beekeeper.addHive(nest);
                for (int i = 0; i < 2; i++)
                {
                    final Mob bee = EntityType.BEE.spawn(level, nest.above(), MobSpawnType.MOB_SUMMONED);
                    if (bee != null)
                    {
                        bee.setPersistenceRequired();
                    }
                }
                plantFlowers(level, colony, nest);
                ColonyAutopilot.LOGGER.info("[{}] set up a bee nest with two bees for the {} at {}",
                  colony.getName(), beekeeper.getBuildingDisplayName(), nest.toShortString());
                Milestones.say(colony, "colonyautopilot.milestone.hive");
                return;
            }
        }
        ColonyAutopilot.LOGGER.debug("[{}] no natural ground for a bee nest within 9 blocks of the {} — the keeper waits for a hive",
          colony.getName(), beekeeper.getBuildingDisplayName());
    }
}
