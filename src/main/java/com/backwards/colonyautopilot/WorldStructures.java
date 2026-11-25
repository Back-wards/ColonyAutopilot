// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.WorldUtil;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class WorldStructures
{

    private static final int MARGIN = 3;

    private static final int BURIED = 3;

    private static final int REFRESH_TICKS = 1200;

    private static final int OUTFIT_REACH = 101;

    private static final int READS_AT_ONCE = 8;

    private static final long READ_PATIENCE_MILLIS = 30_000;

    private record Start(ResourceKey<Level> dimension, Structure structure, long chunk)
    {
    }

    private record Survey(long until, boolean whole, List<SiteSelector.Footprint> boxes, Map<Start, SiteSelector.Footprint> named)
    {
    }

    private static final Survey NONE = new Survey(0, true, List.of(), Map.of());

    private record Pieces(BoundingBox hull, List<BoundingBox> boxes)
    {
    }

    private record Read(CompletableFuture<Pieces> answer, long asked)
    {
    }

    private static final Pieces UNSAVED = new Pieces(null, List.of());

    private static final Map<Start, Pieces> PIECES = new HashMap<>();

    private static final Map<Start, Read> READS = new HashMap<>();

    private static final Map<Start, CompletableFuture<Pieces>> LATE = new HashMap<>();
    private static final Map<ColonyId, Survey> SURVEYS = new HashMap<>();

    private WorldStructures()
    {
    }

    static void clear()
    {
        PIECES.clear();
        READS.clear();
        LATE.clear();
        SURVEYS.clear();
    }

    static void forget(final IColony colony)
    {
        SURVEYS.remove(ColonyAutopilot.colonyKey(colony));
    }

    static void renew(final IColony colony)
    {
        SURVEYS.computeIfPresent(ColonyAutopilot.colonyKey(colony), (key, survey) -> new Survey(0, survey.whole(), survey.boxes(), survey.named()));
    }

    static List<SiteSelector.Footprint> footprintsFor(final IColony colony)
    {
        return survey(colony, false).boxes();
    }

    static boolean whole(final IColony colony)
    {
        return survey(colony, true).whole();
    }

    private static Survey survey(final IColony colony, final boolean anew)
    {
        if (!AutopilotConfig.get(colony, AutopilotConfig.AVOID_STRUCTURES) || !(colony.getWorld() instanceof ServerLevel level))
        {
            return NONE;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        Survey survey = SURVEYS.get(key);

        if (survey == null || anew || level.getGameTime() >= survey.until() || !survey.whole() && answered())
        {
            survey = read(level, colony, survey == null ? NONE : survey);
            SURVEYS.put(key, survey);
        }
        return survey;
    }

    private static boolean answered()
    {
        for (final Read read : READS.values())
        {
            if (read.answer().isDone())
            {
                return true;
            }
        }
        return false;
    }

    private static Survey read(final ServerLevel level, final IColony colony, final Survey before)
    {
        final long began = System.nanoTime();
        final Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        final int answers = settle(registry);
        final BlockPos center = colony.getCenter();

        final int search = Math.min(200, 2 * AutopilotConfig.get(colony, AutopilotConfig.SEARCH_RADIUS_BLOCKS));
        int west = center.getX() - search;
        int north = center.getZ() - search;
        int east = center.getX() + search;
        int south = center.getZ() + search;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final BlockPos hut = building.getPosition();
            west = Math.min(west, hut.getX());
            north = Math.min(north, hut.getZ());
            east = Math.max(east, hut.getX());
            south = Math.max(south, hut.getZ());
        }
        final Map<Structure, Boolean> skipped = new HashMap<>();
        final Set<Start> seen = new HashSet<>();
        final List<SiteSelector.Footprint> boxes = new ArrayList<>();
        final Map<Start, SiteSelector.Footprint> named = new HashMap<>(before.named());
        int waiting = 0;
        for (int cx = (west - OUTFIT_REACH) >> 4; cx <= (east + OUTFIT_REACH) >> 4; cx++)
        {
            for (int cz = (north - OUTFIT_REACH) >> 4; cz <= (south + OUTFIT_REACH) >> 4; cz++)
            {

                final LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
                if (loaded == null)
                {
                    continue;
                }
                for (final Map.Entry<Structure, LongSet> entry : loaded.getAllReferences().entrySet())
                {
                    if (skipped.computeIfAbsent(entry.getKey(), structure -> passedOver(registry, structure)))
                    {
                        continue;
                    }
                    for (final long at : entry.getValue())
                    {
                        final Start start = new Start(level.dimension(), entry.getKey(), at);
                        if (!seen.add(start))
                        {
                            continue;
                        }
                        Pieces pieces = PIECES.get(start);
                        if (pieces == null || pieces == UNSAVED)
                        {

                            final ChunkAccess home = held(level, at);
                            if (home != null)
                            {
                                pieces = loadedPieces(home.getStartForStructure(entry.getKey()));
                                PIECES.put(start, pieces);
                            }
                            else if (pieces == null)
                            {
                                if (!READS.containsKey(start) && READS.size() < READS_AT_ONCE)
                                {
                                    READS.put(start, new Read(ask(level, at, String.valueOf(registry.getKey(entry.getKey()))), System.nanoTime()));
                                }
                                waiting++;
                                continue;
                            }
                        }

                        int minX = Integer.MAX_VALUE;
                        int minZ = Integer.MAX_VALUE;
                        int maxX = Integer.MIN_VALUE;
                        int maxZ = Integer.MIN_VALUE;
                        boolean stands = false;
                        final int high = pieces.boxes().isEmpty() ? -1 : highOverLand(level, pieces.hull());
                        for (final Iterator<BoundingBox> each = pieces.boxes().iterator(); each.hasNext(); )
                        {
                            final BoundingBox piece = each.next();
                            final int read = surface(level, piece, pieces.hull().minY(), high);
                            if (read < 0)
                            {

                                each.remove();
                                continue;
                            }

                            stands |= read > 0;
                            minX = Math.min(minX, piece.minX());
                            minZ = Math.min(minZ, piece.minZ());
                            maxX = Math.max(maxX, piece.maxX());
                            maxZ = Math.max(maxZ, piece.maxZ());
                        }
                        final SiteSelector.Footprint ground = new SiteSelector.Footprint(minX - MARGIN, minZ - MARGIN, maxX + MARGIN, maxZ + MARGIN);

                        if (stands && !stoodOn(colony, ground))
                        {
                            boxes.add(ground);
                            named.put(start, ground);
                        }
                        else if (stands || pieces.boxes().isEmpty())
                        {

                            named.remove(start);
                        }
                    }
                }
            }
        }
        final boolean whole = waiting == 0;

        if (whole)
        {
            named.keySet().removeIf(start -> skipped.computeIfAbsent(start.structure(), structure -> passedOver(registry, structure)));
            if (!named.equals(before.named()))
            {

                final List<String> listed = new ArrayList<>();
                named.forEach((start, ground) -> listed.add(registry.getKey(start.structure()) + " (" + ground.minX() + ", " + ground.minZ() + ")-(" + ground.maxX() + ", " + ground.maxZ() + ")"));
                Collections.sort(listed);
                ColonyAutopilot.LOGGER.info("[{}] building around {} world structure(s){}", colony.getName(), listed.size(), listed.isEmpty() ? "" : ": " + String.join(", ", listed));
            }
        }
        ColonyAutopilot.LOGGER.debug("[{}] world structures surveyed in {} ms: {} in reach, {} answer(s) from saved chunks taken in, {} to build around{}",
          colony.getName(), (System.nanoTime() - began) / 10_000 / 100.0, seen.size(), answers, boxes.size(),
          whole ? "" : " — " + waiting + " more wait for their saved chunk, read off the server thread, and no building is placed until they are in");
        return new Survey(level.getGameTime() + (whole ? REFRESH_TICKS : 20), whole, List.copyOf(boxes), whole ? named : before.named());
    }

    private static ChunkAccess held(final ServerLevel level, final long chunk)
    {
        final ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk);
        return holder == null ? null : holder.getChunkIfPresentUnchecked(ChunkStatus.STRUCTURE_STARTS);
    }

    private static CompletableFuture<Pieces> ask(final ServerLevel level, final long chunk, final String id)
    {
        final CollectFields kept = new CollectFields(new FieldSelector("structures", CompoundTag.TYPE, "starts"));
        return level.getChunkSource().chunkMap.chunkScanner().scanChunk(new ChunkPos(chunk), kept)
                 .thenApply(read -> kept.getResult() instanceof CompoundTag data
                                      ? savedPieces(data.getCompound("structures").getCompound("starts").getCompound(id)) : UNSAVED)
                 .exceptionally(failed -> null);
    }

    private static int settle(final Registry<Structure> registry)
    {
        int answers = 0;
        for (final Iterator<Map.Entry<Start, CompletableFuture<Pieces>>> each = LATE.entrySet().iterator(); each.hasNext(); )
        {
            final Map.Entry<Start, CompletableFuture<Pieces>> late = each.next();
            if (!late.getValue().isDone())
            {
                continue;
            }
            each.remove();
            final Pieces pieces = late.getValue().getNow(null);

            if (pieces != null && !pieces.boxes().isEmpty() && PIECES.get(late.getKey()) == UNSAVED)
            {
                PIECES.put(late.getKey(), pieces);
                answers++;
                ColonyAutopilot.LOGGER.info("world structures: the pieces of {} in the saved chunk {} were read after all — it is built around from now on",
                  registry.getKey(late.getKey().structure()), new ChunkPos(late.getKey().chunk()));
            }
        }
        for (final Iterator<Map.Entry<Start, Read>> each = READS.entrySet().iterator(); each.hasNext(); )
        {
            final Map.Entry<Start, Read> read = each.next();
            final CompletableFuture<Pieces> answer = read.getValue().answer();
            final boolean slow = !answer.isDone();
            if (slow && (System.nanoTime() - read.getValue().asked()) / 1_000_000 < READ_PATIENCE_MILLIS)
            {
                continue;
            }
            each.remove();
            answers++;
            final Pieces pieces = answer.getNow(null);
            final boolean found = pieces != null && !pieces.boxes().isEmpty();

            if (PIECES.putIfAbsent(read.getKey(), found ? pieces : UNSAVED) != null || found)
            {
                continue;
            }
            final ChunkPos home = new ChunkPos(read.getKey().chunk());
            final String what = registry.getKey(read.getKey().structure()) + " in the saved chunk " + home;
            if (pieces == UNSAVED)
            {

                ColonyAutopilot.LOGGER.warn("world structures: the chunk {} that {} starts in was never saved (the game stopped before it was written, or its region file was replaced) — the structure is NOT built around, in this session or a later one, until that chunk is loaded: go there, or run /execute in {} run forceload add {} {} and, once a 'building around' line names it, /execute in {} run forceload remove {} {}",
                  home, registry.getKey(read.getKey().structure()), read.getKey().dimension().location(), home.getMinBlockX(), home.getMinBlockZ(),
                  read.getKey().dimension().location(), home.getMinBlockX(), home.getMinBlockZ());
            }
            else if (pieces != null)
            {
                ColonyAutopilot.LOGGER.info("world structures: no pieces are saved for {} (a world from an older version, or a region file that was replaced) — it is not built around until that chunk is loaded", what);
            }
            else if (!slow)
            {
                ColonyAutopilot.LOGGER.warn("world structures: the pieces of {} could not be read (the game's own warning says why) — it is not built around until that chunk is loaded", what);
            }
            else
            {
                LATE.put(read.getKey(), answer);
                ColonyAutopilot.LOGGER.warn("world structures: the pieces of {} were not read within {} seconds — it is not built around until they are, or until that chunk is loaded",
                  what, READ_PATIENCE_MILLIS / 1000);
            }
        }
        return answers;
    }

    private static Pieces loadedPieces(final StructureStart start)
    {
        final List<BoundingBox> boxes = new ArrayList<>();
        if (start != null)
        {
            for (final StructurePiece piece : start.getPieces())
            {
                boxes.add(piece.getBoundingBox());
            }
        }
        return piecesOf(boxes);
    }

    private static Pieces savedPieces(final CompoundTag start)
    {
        final List<BoundingBox> boxes = new ArrayList<>();
        final ListTag children = start.getList("Children", Tag.TAG_COMPOUND);
        for (int i = 0; i < children.size(); i++)
        {
            final int[] box = children.getCompound(i).getIntArray("BB");
            if (box.length == 6)
            {
                boxes.add(new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
            }
        }
        return piecesOf(boxes);
    }

    private static Pieces piecesOf(final List<BoundingBox> boxes)
    {
        return new Pieces(BoundingBox.encapsulatingBoxes(boxes).orElse(null), boxes);
    }

    private static boolean stoodOn(final IColony colony, final SiteSelector.Footprint ground)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            if (ground.intersects(corners.getA().getX(), corners.getA().getZ(), corners.getB().getX(), corners.getB().getZ()))
            {
                return true;
            }
        }
        return false;
    }

    private static int highOverLand(final ServerLevel level, final BoundingBox hull)
    {
        boolean unread = false;
        for (final int x : new int[] {hull.minX() - MARGIN, hull.maxX() + MARGIN})
        {
            for (final int z : new int[] {hull.minZ() - MARGIN, hull.maxZ() + MARGIN})
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    unread = true;
                }
                else if (hull.minY() <= SiteSelector.groundHeight(level, x, z) + SiteSelector.SKY_PROBE_DEPTH)
                {
                    return -1;
                }
            }
        }
        return unread ? 0 : 1;
    }

    private static int surface(final ServerLevel level, final BoundingBox box, final int bottom, final int high)
    {
        final int[][] at = {{(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2},
          {box.minX(), box.minZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.minZ()}, {box.maxX(), box.maxZ()}};
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        boolean unread = false;
        for (final int[] xz : at)
        {
            if (!WorldUtil.isChunkLoaded(level, xz[0] >> 4, xz[1] >> 4))
            {
                unread = true;
            }
            else if (box.maxY() >= SiteSelector.groundHeight(level, xz[0], xz[1]) - BURIED)
            {
                if (high < 0)
                {
                    return 1;
                }

                int y = bottom - 1;
                while (y >= bottom - SiteSelector.SKY_PROBE_DEPTH && y >= level.getMinBuildHeight() && level.getBlockState(probe.set(xz[0], y, xz[1])).isAir())
                {
                    y--;
                }
                if (y >= bottom - SiteSelector.SKY_PROBE_DEPTH)
                {
                    return 1;
                }

                unread |= high == 0;
            }
        }
        return unread ? 0 : -1;
    }

    private static boolean passedOver(final Registry<Structure> registry, final Structure structure)
    {
        final ResourceLocation id = registry.getKey(structure);
        for (final String entry : AutopilotConfig.STRUCTURES_IGNORED.get())
        {
            if (entry.startsWith("#"))
            {
                final ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && registry.wrapAsHolder(structure).is(TagKey.create(Registries.STRUCTURE, tag)))
                {
                    return true;
                }
            }
            else if (id != null && entry.equals(id.toString()))
            {
                return true;
            }
        }
        return false;
    }
}
