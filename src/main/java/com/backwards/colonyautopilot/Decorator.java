// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.colony.workorders.WorkOrderDecoration;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Decorator
{
    private static final int PERIOD_TICKS = 1200;

    private static final int MAX_SIZE = 13;

    private record DecoPrint(String path, int sizeX, int sizeZ)
    {
    }

    private final GrowthDirector growth;

    private int tickCounter = 24;

    private final ColonyRota rota = new ColonyRota();

    private final Map<String, List<DecoPrint>> catalog = new HashMap<>();

    private final Map<String, CompletableFuture<List<StructurePacks.Category>>> pendingCategories = new HashMap<>();

    private final Map<String, List<CompletableFuture<List<Blueprint>>>> pendingBlueprints = new HashMap<>();

    public Decorator(final GrowthDirector growth)
    {
        this.growth = growth;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        catalog.clear();
        pendingCategories.clear();
        pendingBlueprints.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {
        pendingCategories.clear();
        pendingBlueprints.clear();
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
            rota.fill(event.getServer());
        }
        for (final IColony colony : rota.take(event.getServer()))
        {

            if (!AutopilotConfig.get(colony, AutopilotConfig.EXPANSION_ENABLED) || AutopilotConfig.get(colony, AutopilotConfig.DECORATIONS_TARGET) <= 0)
            {
                continue;
            }
            try
            {
                decorate(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Decoration pass failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void decorate(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level) || !level.isLoaded(colony.getCenter())
              || !growth.planComplete(colony))
        {
            return;
        }
        final String packName = colony.getStructurePack();
        final List<DecoPrint> prints = catalogFor(packName, level);
        if (prints == null || prints.isEmpty())
        {
            return;
        }

        final List<BlockPos> ordered = new ArrayList<>();
        for (final WorkOrderDecoration order : colony.getWorkManager().getWorkOrdersOfType(WorkOrderDecoration.class))
        {
            ordered.add(order.getLocation());
        }
        int standing = ordered.size();

        final Map<Long, int[]> records = ColonyGrounds.get(level).decorations(colony.getID());
        final Set<Long> counted = new HashSet<>();

        final int target = AutopilotConfig.get(colony, AutopilotConfig.DECORATIONS_TARGET);
        final BlockPos center = colony.getCenter();
        for (int spot = 0; spot < target * 4 && standing < target; spot++)
        {
            final double theta = (spot % 8) * Math.PI / 4 + (spot / 8) * Math.PI / 8;
            final int ring = 16 + 7 * (spot / 8);
            final int x = center.getX() + (int) Math.round(Math.cos(theta) * ring);
            final int z = center.getZ() + (int) Math.round(Math.sin(theta) * ring);
            if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
            {
                continue;
            }

            if (ordered.stream().anyMatch(pos -> (pos.getX() - x) * (pos.getX() - x) + (pos.getZ() - z) * (pos.getZ() - z) < 8 * 8))
            {
                continue;
            }

            final Long covering = covering(records, x, z);
            if (covering != null)
            {
                if (!ordered.contains(BlockPos.of(covering)) && counted.add(covering))
                {
                    standing++;
                }
                continue;
            }
            final BlockPos anchor = new BlockPos(x, SiteSelector.groundHeight(level, x, z), z);

            if (anchor.getY() > center.getY() + 16)
            {
                continue;
            }
            if (controllerNear(level, anchor))
            {
                standing++;
                continue;
            }
            final DecoPrint print = prints.get(spot % prints.size());
            if (!spotFree(level, colony, anchor, print))
            {
                continue;
            }
            commission(colony, level, packName, print, anchor);
            return;
        }
    }

    private void commission(final IColony colony, final ServerLevel level, final String packName, final DecoPrint print, final BlockPos anchor)
    {
        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(packName, print.path(), level.registryAccess()), level, blueprint -> {

            try
            {
            if (blueprint == null)
            {
                return;
            }
            final String name = prettyName(print.path());

            final BlockPos lifted = AutopilotConfig.get(colony, AutopilotConfig.BLUEPRINT_GROUND_OFFSET)
                                      ? anchor.above(BlueprintGround.placementOffset(blueprint) - 1) : anchor;
            final WorkOrderDecoration order = WorkOrderDecoration.create(
              WorkOrderType.BUILD, packName, print.path(), name, lifted, RotationMirror.NONE, 0);
            order.setBlueprint(blueprint, level);
            colony.getWorkManager().addWorkOrder(order, false);

            if (order.getID() == 0)
            {
                ColonyAutopilot.LOGGER.debug("[{}] MineColonies refused the {} at {} — not recorded", colony.getName(), name, lifted.toShortString());
                return;
            }

            final AABB box = order.getBoundingBox();
            final BlockPos witness = witnessOf(blueprint, lifted, anchor.getY());
            ColonyGrounds.get(level).addDecoration(colony.getID(), lifted,
              (int) Math.floor(box.minX), (int) Math.floor(box.minZ), (int) Math.floor(box.maxX), (int) Math.floor(box.maxZ),
              witness.getX(), witness.getY(), witness.getZ());
            ColonyAutopilot.LOGGER.info("[{}] commissioned a {} at {} — the village decorates itself now",
              colony.getName(), name, lifted.toShortString());
            Milestones.say(colony, "colonyautopilot.milestone.decoration", name);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] commissioning a decoration at {} failed", colony.getName(), anchor.toShortString(), e);
            }
        }));
    }

    private static boolean controllerNear(final ServerLevel level, final BlockPos anchor)
    {

        for (int dy = -8; dy <= 8; dy++)
        {
            if (level.getBlockState(anchor.offset(0, dy, 0)).getBlock() instanceof BlockDecorationController)
            {
                return true;
            }
        }
        return false;
    }

    private static BlockPos witnessOf(final Blueprint blueprint, final BlockPos location, final int groundY)
    {
        final BlockPos offset = blueprint.getPrimaryBlockOffset();
        final short[][][] structure = blueprint.getStructure();
        final BlockState[] palette = blueprint.getPalette();
        final int sizeX = blueprint.getSizeX(), sizeY = blueprint.getSizeY(), sizeZ = blueprint.getSizeZ();
        final int fromY = Math.max(0, groundY - location.getY() + offset.getY());
        if (offset.getX() >= 0 && offset.getX() < sizeX && offset.getZ() >= 0 && offset.getZ() < sizeZ)
        {
            for (int y = fromY; y < sizeY; y++)
            {
                if (standsBuilt(palette[structure[y][offset.getZ()][offset.getX()] & 0xFFFF]))
                {
                    return location.offset(0, y - offset.getY(), 0);
                }
            }
        }
        for (int y = fromY; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    if (standsBuilt(palette[structure[y][z][x] & 0xFFFF]))
                    {
                        return location.offset(x - offset.getX(), y - offset.getY(), z - offset.getZ());
                    }
                }
            }
        }
        return location;
    }

    private static boolean standsBuilt(final BlockState state)
    {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced())
        {
            return false;
        }
        final Block block = state.getBlock();
        return block != ModBlocks.blockSubstitution.get() && block != ModBlocks.blockSolidSubstitution.get()
                 && block != ModBlocks.blockFluidSubstitution.get() && block != ModBlocks.blockTagSubstitution.get();
    }

    private static Long covering(final Map<Long, int[]> records, final int x, final int z)
    {
        for (final Map.Entry<Long, int[]> record : records.entrySet())
        {
            final int[] box = record.getValue();
            if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3])
            {
                return record.getKey();
            }
        }
        return null;
    }

    static void recordPlacedDecorations(final ServerLevel level, final IColony colony)
    {
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final Map<Long, int[]> known = grounds.decorations(colony.getID());
        for (final long chunk : List.copyOf(colony.getLoadedChunks()))
        {
            final LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
            if (loaded == null)
            {
                continue;
            }
            for (final BlockEntity entity : loaded.getBlockEntities().values())
            {
                if (!(entity instanceof TileEntityDecorationController controller) || known.containsKey(controller.getBlockPos().asLong()))
                {
                    continue;
                }
                final BlockPos at = controller.getBlockPos();

                final Tuple<BlockPos, BlockPos> corners = controller.getSchematicCorners();
                final BlockPos a = corners.getA();
                final BlockPos b = corners.getB();
                final boolean unknown = a.equals(at) && b.equals(at) || a.equals(BlockPos.ZERO) && b.equals(BlockPos.ZERO);
                final int shield = 2;
                final int minX = unknown ? at.getX() - shield : at.getX() + Math.min(a.getX(), b.getX());
                final int minZ = unknown ? at.getZ() - shield : at.getZ() + Math.min(a.getZ(), b.getZ());
                final int maxX = unknown ? at.getX() + shield : at.getX() + Math.max(a.getX(), b.getX());
                final int maxZ = unknown ? at.getZ() + shield : at.getZ() + Math.max(a.getZ(), b.getZ());
                grounds.addDecoration(colony.getID(), at, minX, minZ, maxX, maxZ);
                ColonyAutopilot.LOGGER.debug("[{}] the decoration at {} ({}) joins the sacred ground — roads and lamps keep off it",
                  colony.getName(), at.toShortString(), controller.getSchematicName());
            }
        }
    }

    private boolean spotFree(final ServerLevel level, final IColony colony, final BlockPos anchor, final DecoPrint print)
    {

        final int reach = Math.max(1, Math.max(print.sizeX(), print.sizeZ()) - 1);
        final int margin = Math.max(MAX_SIZE / 2, reach);
        final int minX = anchor.getX() - margin;
        final int minZ = anchor.getZ() - margin;
        final int maxX = anchor.getX() + margin;
        final int maxZ = anchor.getZ() + margin;

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
        {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
            {
                if (!WorldUtil.isChunkLoaded(level, cx, cz)
                      || IColonyManager.getInstance().getColonyByPosFromWorld(level, new BlockPos(cx << 4, anchor.getY(), cz << 4)) != colony)
                {
                    return false;
                }
            }
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (SiteSelector.boxOf(building).intersects(minX, minZ, maxX, maxZ))
            {
                return false;
            }
        }

        for (final SiteSelector.Footprint shield : growth.sacredGround(colony))
        {
            if (shield.intersects(minX, minZ, maxX, maxZ))
            {
                return false;
            }
        }

        for (final SiteSelector.Footprint doorstep : growth.doorstepZones(colony, level))
        {
            if (doorstep.intersects(minX, minZ, maxX, maxZ))
            {
                return false;
            }
        }
        final var ground = level.getBlockState(anchor.below());
        if (ground.is(Blocks.DIRT_PATH) || ground.is(Blocks.GRAVEL) || !ground.getFluidState().isEmpty() || ground.canBeReplaced())
        {
            return false;
        }

        for (int x = anchor.getX() - reach; x <= anchor.getX() + reach; x++)
        {
            for (int z = anchor.getZ() - reach; z <= anchor.getZ() + reach; z++)
            {
                if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                {
                    return false;
                }
                final var surface = level.getBlockState(new BlockPos(x, SiteSelector.groundHeight(level, x, z) - 1, z));
                if (surface.is(Blocks.DIRT_PATH) || surface.is(Blocks.GRAVEL))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private List<DecoPrint> catalogFor(final String packName, final ServerLevel level)
    {
        final List<DecoPrint> known = catalog.get(packName);
        if (known != null)
        {
            return known;
        }
        final CompletableFuture<List<StructurePacks.Category>> categories =
          pendingCategories.computeIfAbsent(packName, name -> StructurePacks.getCategoriesFuture(name, "decorations"));
        if (categories.isCompletedExceptionally())
        {

            catalog.put(packName, List.of());
            pendingCategories.remove(packName);
            return catalog.get(packName);
        }
        if (!categories.isDone())
        {
            return null;
        }
        List<CompletableFuture<List<Blueprint>>> lists = pendingBlueprints.get(packName);
        if (lists == null)
        {
            lists = new ArrayList<>();
            lists.add(StructurePacks.getBlueprintsFuture(packName, "decorations", level.registryAccess()));
            for (final StructurePacks.Category category : categories.join())
            {
                lists.add(StructurePacks.getBlueprintsFuture(packName, category.subPath, level.registryAccess()));
            }
            pendingBlueprints.put(packName, lists);
            return null;
        }
        for (final CompletableFuture<List<Blueprint>> list : lists)
        {
            if (!list.isDone())
            {
                return null;
            }
        }

        final StructurePackMeta pack = StructurePacks.getStructurePack(packName);
        final List<DecoPrint> prints = new ArrayList<>();
        if (pack != null)
        {
            for (final CompletableFuture<List<Blueprint>> list : lists)
            {
                final List<Blueprint> blueprints = list.isCompletedExceptionally() ? null : list.join();
                if (blueprints == null)
                {
                    continue;
                }
                for (final Blueprint blueprint : blueprints)
                {
                    if (blueprint == null || blueprint.getSizeX() > MAX_SIZE || blueprint.getSizeZ() > MAX_SIZE)
                    {
                        continue;
                    }

                    String directory = blueprint.getFilePath().toString().replace('\\', '/')
                                         .replace(pack.getPath().toString().replace('\\', '/'), "");
                    while (directory.startsWith("/"))
                    {
                        directory = directory.substring(1);
                    }

                    if (directory.equals("decorations/supplies") || directory.startsWith("decorations/supplies/"))
                    {
                        continue;
                    }
                    prints.add(new DecoPrint(directory + "/" + blueprint.getFileName() + ".blueprint", blueprint.getSizeX(), blueprint.getSizeZ()));
                }
            }
        }
        prints.sort(Comparator.comparing(DecoPrint::path));
        catalog.put(packName, prints);
        pendingCategories.remove(packName);
        pendingBlueprints.remove(packName);
        ColonyAutopilot.LOGGER.debug("Decoration catalog for pack '{}': {} small pieces", packName, prints.size());
        return prints;
    }

    private static String prettyName(final String path)
    {
        String name = path.substring(path.lastIndexOf('/') + 1).replace(".blueprint", "").replace('_', ' ');
        final StringBuilder pretty = new StringBuilder(name.length());
        boolean wordStart = true;
        for (final char c : name.toCharArray())
        {
            pretty.append(wordStart ? Character.toUpperCase(c) : c);
            wordStart = c == ' ';
        }
        return pretty.toString();
    }
}
