// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LampLighter
{
    private static final int PERIOD_TICKS = 600;

    private static final int MARGIN = 6;

    private static final int STEP = 4;

    private static final int VILLAGE_LEVEL_WINDOW = 12;

    private int tickCounter = 11;

    private final ColonyRota rota = new ColonyRota();

    private final Map<ColonyId, Integer> cursor = new HashMap<>();

    private final GrowthDirector growth;

    LampLighter(final GrowthDirector growth)
    {
        this.growth = growth;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        cursor.clear();
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
            ColonyAutopilot.retainColonies(rota.fill(event.getServer()), cursor);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {
            if (!AutopilotConfig.get(colony, AutopilotConfig.LIGHT_VILLAGE))
            {
                continue;
            }
            try
            {
                evaluate(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Lamplighting failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void evaluate(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final List<IBuilding> buildings = new ArrayList<>(colony.getServerBuildingManager().getBuildings().values());
        if (buildings.isEmpty())
        {
            return;
        }

        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final int index = cursor.merge(key, 1, Integer::sum) % buildings.size();
        final IBuilding building = buildings.get(index);
        if (building.getBuildingLevel() <= 0)
        {
            return;
        }

        final List<SiteSelector.Footprint> boxes = new ArrayList<>(buildings.size());
        for (final IBuilding other : buildings)
        {
            boxes.add(SiteSelector.boxOf(other));
        }
        if (growth != null)
        {

            boxes.addAll(growth.sacredGround(colony));
        }

        final SiteSelector.Footprint box = SiteSelector.boxOf(building);
        final SiteSelector.Footprint grounds = new SiteSelector.Footprint(box.minX() - MARGIN, box.minZ() - MARGIN, box.maxX() + MARGIN, box.maxZ() + MARGIN);
        final int spacing = AutopilotConfig.get(colony, AutopilotConfig.LAMP_SPACING);

        final SiteSelector.Footprint thinned = new SiteSelector.Footprint(grounds.minX() - spacing, grounds.minZ() - spacing,
          grounds.maxX() + spacing, grounds.maxZ() + spacing);
        final int reach = MARGIN + spacing + VillagePaths.LAMP_REACH + 1;

        boxes.addAll(SiteSelector.foreignClaims(level, colony, box.minX() - reach - 1, box.minZ() - reach - 1,
          box.maxX() + reach + 1, box.maxZ() + reach + 1));

        final int hutY = building.getPosition().getY();
        final List<BlockPos> posts = new ArrayList<>();
        final List<BlockPos> roads = new ArrayList<>();
        final List<BlockPos> dark = new ArrayList<>();
        int removedSky = 0;
        for (int x = box.minX() - reach; x <= box.maxX() + reach; x++)
        {
            for (int z = box.minZ() - reach; z <= box.maxZ() + reach; z++)
            {
                if (!loaded(level, x, z))
                {
                    continue;
                }

                final BlockPos base = VillagePaths.postAt(level, x, z);
                if (base != null)
                {

                    boolean zoned = false;
                    for (final SiteSelector.Footprint other : boxes)
                    {
                        if (other.intersects(x, z, x, z))
                        {
                            zoned = true;
                            break;
                        }
                    }
                    if (zoned)
                    {
                        continue;
                    }
                    final var footing = level.getBlockState(base);

                    boolean skyHigh = grounds.intersects(x, z, x, z) && base.getY() > hutY + VILLAGE_LEVEL_WINDOW
                                        && footing.is(Blocks.COBBLESTONE)
                                        && !roadside(level, base);
                    if (skyHigh)
                    {
                        for (final IBuilding other : buildings)
                        {
                            if (base.getY() <= other.getPosition().getY() + VILLAGE_LEVEL_WINDOW)
                            {
                                skyHigh = false;
                                break;
                            }
                        }
                    }
                    if (skyHigh)
                    {
                        dismantle(level, base);
                        removedSky++;
                        continue;
                    }
                    posts.add(base);
                    continue;
                }

                if (thinned.intersects(x, z, x, z))
                {
                    final BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                    if (top.getY() <= hutY + VILLAGE_LEVEL_WINDOW && level.getBlockState(top).is(Blocks.DIRT_PATH))
                    {
                        roads.add(top);
                    }
                }

                if (!grounds.intersects(x, z, x, z) || (x - grounds.minX()) % STEP != 0 || (z - grounds.minZ()) % STEP != 0)
                {
                    continue;
                }
                boolean inside = false;
                for (final SiteSelector.Footprint other : boxes)
                {

                    if (other.intersects(x - 1, z - 1, x + 1, z + 1))
                    {
                        inside = true;
                        break;
                    }
                }
                if (inside)
                {
                    continue;
                }

                final int groundY = SiteSelector.groundHeight(level, x, z) - 1;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 > groundY)
                {
                    continue;
                }

                if (groundY > hutY + VILLAGE_LEVEL_WINDOW)
                {
                    continue;
                }
                final BlockPos ground = new BlockPos(x, groundY, z);

                if (level.getBrightness(LightLayer.BLOCK, ground.above()) == 0)
                {
                    dark.add(ground);
                }
            }
        }

        final Set<Long> ends = growth != null ? growth.roadEnds(colony, level) : Set.of();
        final List<BlockPos> standing = new ArrayList<>(posts);
        int removed = removedSky;
        for (final BlockPos post : posts)
        {
            if (thinned.intersects(post.getX(), post.getZ(), post.getX(), post.getZ()) && level.getBlockState(post).is(Blocks.COBBLESTONE)
                  && !ends.contains(post.asLong()) && VillagePaths.crowded(post, standing, spacing) && !lightsRoad(level, post, standing))
            {
                dismantle(level, post);
                standing.remove(post);
                if (growth != null)
                {
                    growth.forgetLamp(colony, level, post);
                }
                removed++;
            }
        }

        int raised = 0;
        for (final BlockPos road : roads)
        {
            if (!VillagePaths.lit(road.above(), standing, null) && level.getBrightness(LightLayer.BLOCK, road.above()) == 0)
            {
                final BlockPos spot = besideRoad(level, road, boxes);
                if (spot != null)
                {
                    standing.add(spot);
                    raised++;
                }
            }
        }

        for (final BlockPos ground : dark)
        {

            if (!VillagePaths.crowded(ground, standing, spacing) && VillagePaths.lamp(level, ground))
            {
                raised++;
                break;
            }
        }

        if (raised > 0 || removed > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the lamplighter raised {} and dismantled {} lamp post(s) around the {}",
              colony.getName(), raised, removed, building.getBuildingDisplayName());
        }
    }

    private static boolean loaded(final ServerLevel level, final int x, final int z)
    {
        return WorldUtil.isChunkLoaded(level, x >> 4, z >> 4)
                 && WorldUtil.isChunkLoaded(level, (x - 1) >> 4, z >> 4) && WorldUtil.isChunkLoaded(level, (x + 1) >> 4, z >> 4)
                 && WorldUtil.isChunkLoaded(level, x >> 4, (z - 1) >> 4) && WorldUtil.isChunkLoaded(level, x >> 4, (z + 1) >> 4);
    }

    private static BlockPos besideRoad(final ServerLevel level, final BlockPos road, final List<SiteSelector.Footprint> boxes)
    {
        for (int out = 1; out <= 2; out++)
        {
            for (final Direction side : Direction.Plane.HORIZONTAL)
            {
                final BlockPos spot = road.relative(side, out);
                boolean inside = !loaded(level, spot.getX(), spot.getZ());
                for (final SiteSelector.Footprint other : boxes)
                {
                    inside |= other.intersects(spot.getX() - 1, spot.getZ() - 1, spot.getX() + 1, spot.getZ() + 1);
                }
                if (!inside && VillagePaths.lamp(level, spot))
                {
                    return spot;
                }
            }
        }
        return null;
    }

    private static boolean lightsRoad(final ServerLevel level, final BlockPos post, final List<BlockPos> standing)
    {
        final BlockPos cap = post.above(3);
        final int reach = VillagePaths.LAMP_REACH + 1;
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = Math.abs(dx) - reach; dz <= reach - Math.abs(dx); dz++)
            {
                final int x = cap.getX() + dx;
                final int z = cap.getZ() + dz;
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    return true;
                }
                final int up = reach - Math.abs(dx) - Math.abs(dz);
                for (int dy = -up; dy <= up; dy++)
                {
                    final BlockPos stand = new BlockPos(x, cap.getY() + dy, z);
                    if (level.getBlockState(stand.below()).is(Blocks.DIRT_PATH) && !VillagePaths.lit(stand, standing, post))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean roadside(final ServerLevel level, final BlockPos post)
    {
        return level.getBlockState(post.north()).is(Blocks.DIRT_PATH)
                 || level.getBlockState(post.south()).is(Blocks.DIRT_PATH)
                 || level.getBlockState(post.east()).is(Blocks.DIRT_PATH)
                 || level.getBlockState(post.west()).is(Blocks.DIRT_PATH);
    }

    private static void dismantle(final ServerLevel level, final BlockPos base)
    {
        if (!VillagePaths.dismantleLamp(level, base.above(3), true))
        {
            for (int i = 1; i <= 3; i++)
            {
                WorldUtil.setBlockState(level, base.above(i), Blocks.AIR.defaultBlockState());
            }
            if (level.getBlockState(base).is(Blocks.COBBLESTONE))
            {
                WorldUtil.setBlockState(level, base, Blocks.DIRT.defaultBlockState());
            }
        }
    }
}
