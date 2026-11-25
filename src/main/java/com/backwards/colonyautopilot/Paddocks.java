// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final int LINGER_AFTER_DISMOUNT = 6000;

    static final Map<ColonyId, Map<BlockPos, Integer>> mostStrays = new HashMap<>();

    private static final String BARDING_MARK = "colonyautopilot_barding";

    static final Map<ColonyId, Set<BlockPos>> bardingWaits = new HashMap<>();

    private Paddocks()
    {
    }

    static boolean keepsAnimals(final IBuilding building)
    {
        final Block block = building.getBuildingType().getBuildingBlock();
        return livestock().containsKey(block) || block == ModBlocks.blockHutBeekeeper || block == ModBlocks.blockHutStable;
    }

    static void ensure(final ServerLevel level, final IColony colony, final IBuilding building)
    {
        if (building instanceof BuildingBeekeeper beekeeper)
        {
            ensureHive(level, colony, beekeeper);
            return;
        }
        if (building instanceof BuildingStable stable)
        {
            tendStable(level, colony, stable);
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

        for (int chunkX = Math.min(corners.getA().getX(), corners.getB().getX()) >> 4; chunkX <= Math.max(corners.getA().getX(), corners.getB().getX()) >> 4; chunkX++)
        {
            for (int chunkZ = Math.min(corners.getA().getZ(), corners.getB().getZ()) >> 4; chunkZ <= Math.max(corners.getA().getZ(), corners.getB().getZ()) >> 4; chunkZ++)
            {
                if (!WorldUtil.isChunkLoaded(level, chunkX, chunkZ) || !level.areEntitiesLoaded(ChunkPos.asLong(chunkX, chunkZ)))
                {
                    return;
                }
            }
        }

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
              colony.getName(), SiteSelector.plainName(building), building.getPosition().toShortString(),
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
              colony.getName(), spawned, species.getDescription().getString(), SiteSelector.plainName(building));

            Milestones.say(colony, "colonyautopilot.milestone.animals",
              spawned, species.getDescription().getString(), Component.translatableEscape(building.getBuildingDisplayName()));
        }
    }

    private static void tendStable(final ServerLevel level, final IColony colony, final BuildingStable stable)
    {

        final List<CavalryHorseEntity> herd = new ArrayList<>();
        int colonyHerd = 0;
        for (final CavalryHorseEntity mount : level.getEntities(ModEntities.CAVALRY_HORSE, CavalryHorseEntity::isAlive))
        {
            final IAnimalData data = mount.getColonyId() == colony.getID() ? colony.getAnimalManager().getAnimal(mount.getManagedAnimalId()) : null;
            if (data != null)
            {
                colonyHerd++;
            }
            final Entity rider = mount.getFirstPassenger();
            if (data != null && data.getHomeBuilding() == null
                  && (rider == null ? stable.getPosition().equals(colony.getServerBuildingManager().getBestBuilding(mount.blockPosition(), BuildingStable.class))
                    : rider instanceof AbstractEntityCitizen citizen && citizen.getCitizenData() != null && stable.equals(citizen.getCitizenData().getWorkBuilding())))
            {
                data.setHomeBuilding(stable);
            }
            if (data != null && data.getHomeBuilding() == stable)
            {
                herd.add(mount);
            }
        }

        final Tuple<BlockPos, BlockPos> corners = stable.getCorners();
        final AABB yard = new AABB(corners.getA().getX(), corners.getA().getY(), corners.getA().getZ(),
          corners.getB().getX(), corners.getB().getY(), corners.getB().getZ());
        int stabled = 0;
        for (final CavalryHorseEntity mount : herd)
        {

            final long dismounted = mount.getLastDismountTime();
            if (mount.getPassengers().isEmpty() && !mount.hasReservation() && !mount.isLeashed() && !mount.getBoundingBox().intersects(yard)
                  && !(mount.isReadyForCombat() && dismounted > 0 && level.getGameTime() - dismounted < LINGER_AFTER_DISMOUNT))
            {
                if (!toStall(level, stable, mount))
                {
                    break;
                }
                stabled++;
            }
        }
        if (stabled > 0)
        {
            final String line = "[{}] {} mount(s) stood riderless outside the {} at {} — put back in its stalls";
            final Map<BlockPos, Integer> most = mostStrays.computeIfAbsent(ColonyAutopilot.colonyKey(colony), key -> new HashMap<>());
            if (stabled > most.getOrDefault(stable.getPosition(), 0))
            {
                most.put(stable.getPosition(), stabled);
                ColonyAutopilot.LOGGER.info(line, colony.getName(), stabled, SiteSelector.plainName(stable), stable.getPosition().toShortString());
            }
            else
            {
                ColonyAutopilot.LOGGER.debug(line, colony.getName(), stabled, SiteSelector.plainName(stable), stable.getPosition().toShortString());
            }
        }

        boolean herdInSight = true;
        for (final BlockPos stall : stable.stallPositions())
        {
            herdInSight &= !stable.isInBuilding(stall) || level.areEntitiesLoaded(ChunkPos.asLong(stall));
        }
        for (final ICitizenData rider : stable.getModule(BuildingModules.CAVALRY_STABLE_WORK).getAssignedCitizen())
        {
            herdInSight &= rider.getEntity().isPresent();
        }

        int room = -colonyHerd;
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (other instanceof BuildingStable)
            {
                room += 2 * other.getBuildingLevel();
            }
        }
        int bought = 0;
        for (int wanted = herdInSight ? Math.min(2 * stable.getBuildingLevel() - herd.size(), room) : 0; wanted > 0; wanted--)
        {
            final BlockPos stall = stallFor(level, stable, false);
            final Horse horse = stall == null ? null : EntityType.HORSE.spawn(level, stall, MobSpawnType.MOB_SUMMONED);
            final CavalryHorseEntity mount = horse == null ? null : CavalryHorseEntity.createFromVanilla(colony, level, horse);
            if (mount == null)
            {
                if (horse != null)
                {
                    horse.discard();
                }
                else if (stall == null)
                {

                    ColonyAutopilot.LOGGER.info("[{}] the {} at {} has no stall a horse can stand in — mounts withheld (no 'stall' tag inside the building, or none with a solid floor and two clear blocks above it)",
                      colony.getName(), SiteSelector.plainName(stable), stable.getPosition().toShortString());
                }
                break;
            }
            mount.getAnimalData().setHomeBuilding(stable);
            bought++;
        }
        if (bought > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] bought {} trained mount(s) at market for the {} — it stood short of its two a level",
              colony.getName(), bought, SiteSelector.plainName(stable));
            Milestones.say(colony, "colonyautopilot.milestone.animals",
              bought, ModEntities.CAVALRY_HORSE.getDescription().getString(), Component.translatableEscape(stable.getBuildingDisplayName()));
        }
    }

    static void fitBarding(final IColony colony, final BuildingStable stable)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final boolean on = AutopilotConfig.get(colony, AutopilotConfig.BARDING_ARMOUR);
        final double barding = colony.getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.MOUNT_ARMOR);

        final Item wanted = !on || barding < 0.1 ? null : barding < 0.25 ? Items.LEATHER_HORSE_ARMOR : barding < 0.4 ? Items.IRON_HORSE_ARMOR : Items.DIAMOND_HORSE_ARMOR;
        int fitted = 0;
        int waiting = 0;
        int takenOff = 0;
        long paid = 0;
        for (final IAnimalData data : colony.getAnimalManager().getAnimalsOfClassByHome(CavalryHorseEntity.class, stable))
        {
            if (!(data.getManagedAnimal().orElse(null) instanceof CavalryHorseEntity mount) || !mount.isAlive())
            {
                continue;
            }
            final ItemStack worn = mount.getBodyArmorItem();
            final CustomData mark = worn.get(DataComponents.CUSTOM_DATA);
            final boolean ours = mark != null && mark.contains(BARDING_MARK);
            if (!worn.isEmpty() && !ours)
            {
                continue;
            }
            if (wanted == null)
            {
                if (ours && !on)
                {
                    mount.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
                    takenOff++;
                }
                continue;
            }
            final ItemStack piece = new ItemStack(wanted);
            if (worn.is(wanted) || !mount.isBodyArmorItem(piece))
            {
                continue;
            }
            final long price = wanted == Items.DIAMOND_HORSE_ARMOR ? AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get() : Treasury.cost(piece);
            if (!Treasury.canAfford(colony, level, price))
            {
                waiting++;
                continue;
            }
            final CompoundTag tag = new CompoundTag();
            tag.putBoolean(BARDING_MARK, true);
            piece.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            mount.setItemSlot(EquipmentSlot.BODY, piece);
            mount.setDropChance(EquipmentSlot.BODY, 0.0F);
            Treasury.charge(colony, level, price, Treasury.Line.GEAR);
            fitted++;
            paid += price;
        }
        if (fitted > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] {} mount(s) of the {} at {} wear {} now, the colony's barding — {}",
              colony.getName(), fitted, SiteSelector.plainName(stable), stable.getPosition().toShortString(),
              new ItemStack(wanted).getHoverName().getString(),
              AutopilotConfig.progressionMode(colony) ? "the treasury paid " + paid + " credit(s)" : "free outside Progression Mode");
        }
        if (takenOff > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the barding the colony fitted is off {} mount(s) of the {} at {} — growth.bardingArmour is off",
              colony.getName(), takenOff, SiteSelector.plainName(stable), stable.getPosition().toShortString());
        }

        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        Set<BlockPos> waits = bardingWaits.get(key);
        if (waits == null)
        {
            waits = new HashSet<>();
            bardingWaits.put(key, waits);
        }
        if (waiting == 0)
        {
            waits.remove(stable.getPosition());
        }
        else if (waits.add(stable.getPosition()))
        {
            ColonyAutopilot.LOGGER.info("[{}] {} mount(s) of the {} at {} wait for their {}: the treasury cannot pay for it yet",
              colony.getName(), waiting, SiteSelector.plainName(stable), stable.getPosition().toShortString(),
              new ItemStack(wanted).getHoverName().getString());
        }
    }

    static boolean toStall(final ServerLevel level, final BuildingStable stable, final CavalryHorseEntity mount)
    {
        final BlockPos stall = stallFor(level, stable, mount.isVehicle());
        if (stall == null)
        {
            return false;
        }
        mount.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 0.5);
        mount.getNavigation().stop();
        return true;
    }

    private static BlockPos stallFor(final ServerLevel level, final BuildingStable stable, final boolean ridden)
    {
        for (int tries = stable.stallPositions().size(); tries > 0; tries--)
        {
            final BlockPos floor = stable.getNextStallPosition();
            if (floor != null && stable.isInBuilding(floor) && WorldUtil.isChunkLoaded(level, floor.getX() >> 4, floor.getZ() >> 4)
                  && standable(level, floor.getX(), floor.getY() + 1, floor.getZ()) && (!ridden || isClear(level.getBlockState(floor.above(3)))))
            {
                return floor.above();
            }
        }
        return null;
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

            if (planted >= 2 || !WorldUtil.isChunkLoaded(level, side.getX() >> 4, side.getZ() >> 4))
            {
                continue;
            }
            final BlockState at = level.getBlockState(side);
            if (!at.canBeReplaced() || !at.getFluidState().isEmpty())
            {
                continue;
            }
            if (SiteSelector.insideAny(zones, side.getX(), side.getZ()))
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
                  colony.getName(), SiteSelector.plainName(beekeeper), hive.toShortString());
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

                boolean bloomsLoaded = true;
                for (final int dx : new int[] {-3, 3})
                {
                    for (final int dz : new int[] {-3, 3})
                    {
                        bloomsLoaded &= WorldUtil.isChunkLoaded(level, (hive.getX() + dx) >> 4, (hive.getZ() + dz) >> 4);
                    }
                }
                if (!bloomsLoaded)
                {
                    continue;
                }

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
                      colony.getName(), planted, SiteSelector.plainName(beekeeper), hive.toShortString());
                }
                else
                {
                    ColonyAutopilot.LOGGER.debug("[{}] no plantable ground within reach of the {}'s hive at {} — no blooms, no honey",
                      colony.getName(), SiteSelector.plainName(beekeeper), hive.toShortString());
                }
            }
            if (home != null && beesAlive == 0)
            {

                for (final BlockPos hive : beekeeper.getHives())
                {
                    for (int chunkX = (hive.getX() - 48) >> 4; chunkX <= (hive.getX() + 48) >> 4; chunkX++)
                    {
                        for (int chunkZ = (hive.getZ() - 48) >> 4; chunkZ <= (hive.getZ() + 48) >> 4; chunkZ++)
                        {
                            if (WorldUtil.isChunkLoaded(level, chunkX, chunkZ) && !level.areEntitiesLoaded(ChunkPos.asLong(chunkX, chunkZ)))
                            {
                                return;
                            }
                        }
                    }
                }

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
                      colony.getName(), SiteSelector.plainName(beekeeper), moved, home.toShortString());
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
                taken |= SiteSelector.insideAny(ProtectedZones.footprintsFor(colony), x, z);
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
                  colony.getName(), SiteSelector.plainName(beekeeper), nest.toShortString());
                Milestones.say(colony, "colonyautopilot.milestone.hive");
                return;
            }
        }
        ColonyAutopilot.LOGGER.debug("[{}] no natural ground for a bee nest within 9 blocks of the {} — the keeper waits for a hive",
          colony.getName(), SiteSelector.plainName(beekeeper));
    }
}
