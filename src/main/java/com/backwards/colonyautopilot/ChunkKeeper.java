// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChunkKeeper
{
    private static final TicketType<ChunkPos> VILLAGE_TICKET =
      TicketType.create(ColonyAutopilot.MOD_ID + ":village", Comparator.comparingLong(ChunkPos::toLong));

    private static final int FRONTIER_LIFESPAN_TICKS = 13200;

    private static final int FRONTIER_DISTANCE = 0;

    static final int FRONTIER_MAX_CHUNKS = 16;

    private static final TicketType<ChunkPos> FRONTIER_TICKET =
      TicketType.create(ColonyAutopilot.MOD_ID + ":frontier", Comparator.comparingLong(ChunkPos::toLong), FRONTIER_LIFESPAN_TICKS);

    private static final int PERIOD_TICKS = 600;

    private static final int MARGIN_CHUNKS = 1;

    private static final int FIELD_WALK_BLOCKS = 48;

    private static final int MAX_CHUNKS_PER_COLONY = 512;

    private record Kept(ServerLevel level, Set<Long> chunks)
    {
    }

    private int tickCounter = 17;

    private final Map<ColonyId, Kept> kept = new HashMap<>();

    private final Set<ColonyId> capWarned = new HashSet<>();

    private static final Set<ColonyId> ticketed = new HashSet<>();

    static boolean holds(final IColony colony)
    {
        return ticketed.contains(ColonyAutopilot.colonyKey(colony));
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        kept.clear();
        capWarned.clear();
        ticketed.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {

        kept.clear();
        capWarned.clear();
        ticketed.clear();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {
            ticketed.clear();
            if (!kept.isEmpty())
            {
                for (final Kept state : kept.values())
                {
                    for (final long chunk : state.chunks())
                    {
                        release(state.level(), chunk);
                    }
                }
                kept.clear();
                capWarned.clear();
                ColonyAutopilot.LOGGER.info("Village chunk keeping disabled — released every held chunk ticket");
            }
            return;
        }
        if (++tickCounter < PERIOD_TICKS)
        {
            return;
        }
        tickCounter = 0;

        final Set<ColonyId> live = new HashSet<>();
        final Map<ColonyId, Kept> wantedNow = new HashMap<>();
        final Map<ServerLevel, Set<Long>> wantedInLevel = new HashMap<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (!AutopilotConfig.get(colony, AutopilotConfig.KEEP_VILLAGE_LOADED))
            {
                continue;
            }
            final ColonyId key = ColonyAutopilot.colonyKey(colony);

            Kept now = kept.get(key);
            try
            {
                if (colony.getWorld() instanceof ServerLevel level)
                {
                    now = new Kept(level, wantedChunks(colony));
                }
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Chunk keeping failed for colony {}", colony.getName(), e);
            }
            live.add(key);
            if (now != null)
            {
                wantedNow.put(key, now);
                wantedInLevel.computeIfAbsent(now.level(), k -> new HashSet<>()).addAll(now.chunks());
            }
        }

        for (final Kept state : wantedNow.values())
        {
            for (final long chunk : state.chunks())
            {
                final ChunkPos pos = new ChunkPos(chunk);

                state.level().getChunkSource().addRegionTicket(VILLAGE_TICKET, pos, 2, pos, true);
            }
        }

        for (final Kept held : kept.values())
        {
            final Set<Long> stillWanted = wantedInLevel.getOrDefault(held.level(), Set.of());
            for (final long chunk : held.chunks())
            {
                if (!stillWanted.contains(chunk))
                {
                    release(held.level(), chunk);
                }
            }
        }
        kept.clear();
        kept.putAll(wantedNow);
        ticketed.clear();
        ticketed.addAll(live);
        capWarned.retainAll(live);
    }

    private Set<Long> wantedChunks(final IColony colony)
    {
        final Set<Long> wanted = new HashSet<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            keep(colony, wanted, SectionPos.blockToSectionCoord(corners.getA().getX()) - MARGIN_CHUNKS, SectionPos.blockToSectionCoord(corners.getA().getZ()) - MARGIN_CHUNKS,
              SectionPos.blockToSectionCoord(corners.getB().getX()) + MARGIN_CHUNKS, SectionPos.blockToSectionCoord(corners.getB().getZ()) + MARGIN_CHUNKS);
        }

        for (final IBuildingExtension field : colony.getServerBuildingManager().getBuildingExtensions(IBuildingExtension::isTaken))
        {
            final BlockPos at = field.getPosition();
            int minX = at.getX();
            int minZ = at.getZ();
            int maxX = at.getX();
            int maxZ = at.getZ();
            if (field instanceof FarmField farm)
            {
                minX -= farm.getRadius(Direction.WEST);
                minZ -= farm.getRadius(Direction.NORTH);
                maxX += farm.getRadius(Direction.EAST);
                maxZ += farm.getRadius(Direction.SOUTH);
            }
            else if (field instanceof PlantationField plantation)
            {
                for (final BlockPos work : plantation.getWorkingPositions())
                {
                    minX = Math.min(minX, work.getX() - 1);
                    minZ = Math.min(minZ, work.getZ() - 1);
                    maxX = Math.max(maxX, work.getX() + 1);
                    maxZ = Math.max(maxZ, work.getZ() + 1);
                }
            }

            final BlockPos hut = field.getBuildingId();
            if (Math.abs(hut.getX() - at.getX()) <= FIELD_WALK_BLOCKS && Math.abs(hut.getZ() - at.getZ()) <= FIELD_WALK_BLOCKS)
            {
                minX = Math.min(minX, hut.getX());
                minZ = Math.min(minZ, hut.getZ());
                maxX = Math.max(maxX, hut.getX());
                maxZ = Math.max(maxZ, hut.getZ());
            }
            keep(colony, wanted, SectionPos.blockToSectionCoord(minX), SectionPos.blockToSectionCoord(minZ),
              SectionPos.blockToSectionCoord(maxX), SectionPos.blockToSectionCoord(maxZ));
        }
        return wanted;
    }

    private void keep(final IColony colony, final Set<Long> wanted, final int minChunkX, final int minChunkZ, final int maxChunkX, final int maxChunkZ)
    {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
        {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            {
                if (wanted.size() >= MAX_CHUNKS_PER_COLONY)
                {
                    if (capWarned.add(ColonyAutopilot.colonyKey(colony)))
                    {
                        ColonyAutopilot.LOGGER.info("[{}] village spans over {} chunks — not keeping more loaded",
                          colony.getName(), MAX_CHUNKS_PER_COLONY);
                    }
                    return;
                }
                wanted.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }
    }

    private static void release(final ServerLevel level, final long chunk)
    {
        final ChunkPos pos = new ChunkPos(chunk);
        level.getChunkSource().removeRegionTicket(VILLAGE_TICKET, pos, 2, pos, true);
    }

    static void requestFrontier(final ServerLevel level, final Set<Long> chunks)
    {
        if (!AutopilotConfig.live(AutopilotConfig.FRONTIER_TICKETS))
        {
            return;
        }
        int issued = 0;
        for (final long chunk : chunks)
        {
            if (issued++ >= FRONTIER_MAX_CHUNKS)
            {
                break;
            }
            final ChunkPos pos = new ChunkPos(chunk);
            level.getChunkSource().addRegionTicket(FRONTIER_TICKET, pos, FRONTIER_DISTANCE, pos, false);
        }
    }
}
