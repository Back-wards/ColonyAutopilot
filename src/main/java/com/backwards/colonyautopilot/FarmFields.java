// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.minecolonies.api.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FarmFields
{

    private static final Set<GlobalPos> DEAD_GROUND_WARNED = new HashSet<>();

    static void clearDeadGroundWarnings()
    {
        DEAD_GROUND_WARNED.clear();
    }

    private static final int FIELD_RADIUS = 5;

    private static final int FIELD_REACH = 48;

    private static final int CARVE_CEILING = 12;

    private static final ItemStack[] CROPS = {
      new ItemStack(Items.WHEAT_SEEDS),
      new ItemStack(Items.POTATO),
      new ItemStack(Items.CARROT),
      new ItemStack(Items.BEETROOT_SEEDS),
    };

    private static final int[] FIRST_CROPS = {1, 2, 0};

    private static final int[] CROP_WEIGHTS = {6, 4, 2, 1};

    static boolean sows(final Item item)
    {
        for (final ItemStack crop : CROPS)
        {
            if (crop.is(item))
            {
                return true;
            }
        }
        return false;
    }

    private static ItemStack nextCrop(final IColony colony)
    {
        final int[] planted = new int[CROPS.length];
        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext ->
          ext.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())))
        {
            if (extension instanceof FarmField field && !field.getSeed().isEmpty())
            {
                for (int i = 0; i < CROPS.length; i++)
                {
                    if (field.getSeed().getItem() == CROPS[i].getItem())
                    {
                        planted[i]++;
                        break;
                    }
                }
            }
        }
        int sown = 0;
        int weights = 0;
        for (int i = 0; i < CROPS.length; i++)
        {
            sown += planted[i];
            weights += CROP_WEIGHTS[i];
        }
        int pick = -1;
        for (final int first : FIRST_CROPS)
        {
            if (planted[first] == 0)
            {
                pick = first;
                break;
            }
        }
        if (pick < 0)
        {

            pick = 0;
            for (int i = 1; i < CROPS.length; i++)
            {
                if (CROP_WEIGHTS[i] * (sown + 1) - planted[i] * weights > CROP_WEIGHTS[pick] * (sown + 1) - planted[pick] * weights)
                {
                    pick = i;
                }
            }
        }
        ColonyAutopilot.LOGGER.info("[{}] the next field grows {} — the colony's fields: wheat {}, potato {}, carrot {}, beetroot {} (potato, carrot and wheat first, then aimed 3 : 2 : 1 : 0.5)",
          colony.getName(), CROPS[pick].getHoverName().getString(), planted[0], planted[1], planted[2], planted[3]);
        return CROPS[pick].copy();
    }

    private FarmFields()
    {
    }

    static void ensure(final ServerLevel level, final IColony colony, final IBuilding farm, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand)
    {
        final BuildingExtensionsModule module = farm.getModule(BuildingExtensionsModule.class);
        if (module == null)
        {
            ColonyAutopilot.LOGGER.warn("[{}] the {} has no fields module — cannot lay out fields",
              colony.getName(), SiteSelector.plainName(farm));
            return;
        }
        final int owned = module.getOwnedExtensions().size();

        int unclaimed = 0;
        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(ext ->
          ext.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) && !ext.isTaken()
            && ext.getPosition().distSqr(farm.getPosition()) <= FIELD_REACH * FIELD_REACH))
        {
            if (extension instanceof FarmField waiting && waiting.getSeed().isEmpty())
            {
                final ItemStack adoptedCrop = nextCrop(colony);
                waiting.setSeed(adoptedCrop);
                module.assignExtension(waiting);
                ColonyAutopilot.LOGGER.info("[{}] seeded the waiting field at {} with {} for the {}",
                  colony.getName(), waiting.getPosition().toShortString(), adoptedCrop.getHoverName().getString(), farm.getBuildingDisplayName());
            }
            unclaimed++;
        }
        if (owned + unclaimed >= farm.getBuildingLevel())
        {
            ColonyAutopilot.LOGGER.debug("[{}] the {} is field-covered ({} owned, {} unclaimed, level {})",
              colony.getName(), farm.getBuildingDisplayName(), owned, unclaimed, farm.getBuildingLevel());
            return;
        }

        final FieldSurvey survey = surveyFieldPlots(level, colony, farm.getPosition(), obstacles, roadBand, false);
        BlockPos plot = survey.natural();
        boolean forcedCarve = false;
        if (plot == null)
        {
            if (survey.forced() == null)
            {
                ColonyAutopilot.LOGGER.info("[{}] no claimed ground clear of buildings and roads near the {} for a field yet — looking again tomorrow",
                  colony.getName(), farm.getBuildingDisplayName());
                return;
            }

            plot = survey.forced();
            forcedCarve = true;
        }
        layField(level, colony, farm, module, plot, forcedCarve);
    }

    static void layNaturalField(final ServerLevel level, final IColony colony, final IBuilding farm, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand)
    {
        final BuildingExtensionsModule module = farm.getModule(BuildingExtensionsModule.class);
        final BlockPos plot = module == null ? null : surveyFieldPlots(level, colony, farm.getPosition(), obstacles, roadBand, false).natural();
        if (plot != null)
        {
            layField(level, colony, farm, module, plot, false);
        }
    }

    private static void layField(final ServerLevel level, final IColony colony, final IBuilding farm, final BuildingExtensionsModule module,
      final BlockPos plot, final boolean forcedCarve)
    {

        carveFieldPlane(level, plot);

        final BlockPos base = plot.above();
        final BlockState lower = ModBlocks.blockScarecrow.defaultBlockState();
        WorldUtil.setBlockState(level, base, lower);
        WorldUtil.setBlockState(level, base.above(), lower.setValue(BlockScarecrow.HALF, DoubleBlockHalf.UPPER));

        final FarmField field = FarmField.create(base, level);
        final ItemStack crop = nextCrop(colony);
        field.setSeed(crop);
        colony.getServerBuildingManager().addBuildingExtension(field);
        module.assignExtension(field);

        ColonyGrounds.get(level).addField(colony.getID(), base);
        carveWells(level, base);
        if (farm.getBuildingLevel() > 0)
        {

            stockSeed(farm, crop);
        }

        ColonyAutopilot.LOGGER.info(forcedCarve
            ? "[{}] force-carved a {} field for the {} at {} — no natural ground, so the colony made one (last-resort carve){}"
            : "[{}] laid out a {} field for the {} at {}{}",
          colony.getName(), crop.getHoverName().getString(), farm.getBuildingDisplayName(), base.toShortString(),
          farm.getBuildingLevel() > 0 ? "" : " — its natural plot, laid as the farm is placed");
        Milestones.say(colony, "colonyautopilot.milestone.field", crop.getHoverName().getString());
    }

    static boolean hasPlantedCrops(final ServerLevel level, final IBuilding farm)
    {
        final BuildingExtensionsModule module = farm.getModule(BuildingExtensionsModule.class);
        if (module == null)
        {
            return false;
        }
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (final IBuildingExtension extension : module.getOwnedExtensions())
        {
            if (!(extension instanceof FarmField field))
            {
                continue;
            }
            if (!field.getSeed().isEmpty() && field.getFieldStage() != FarmField.Stage.EMPTY)
            {
                return true;
            }
            final BlockPos scarecrow = field.getPosition();
            if (!WorldUtil.isChunkLoaded(level, scarecrow.getX() >> 4, scarecrow.getZ() >> 4))
            {
                continue;
            }

            final int cropY = scarecrow.getY();
            for (int x = scarecrow.getX() - field.getRadius(Direction.WEST); x <= scarecrow.getX() + field.getRadius(Direction.EAST); x++)
            {
                for (int z = scarecrow.getZ() - field.getRadius(Direction.NORTH); z <= scarecrow.getZ() + field.getRadius(Direction.SOUTH); z++)
                {
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
                    {
                        continue;
                    }
                    final BlockState planted = level.getBlockState(probe.set(x, cropY, z));
                    if (planted.is(BlockTags.CROPS)
                          && !(planted.getBlock() instanceof CropBlock crop && crop.isMaxAge(planted)))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static void tend(final ServerLevel level, final IColony colony, final IBuilding farm)
    {
        final BuildingExtensionsModule module = farm.getModule(BuildingExtensionsModule.class);
        if (module == null)
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);

        final List<SiteSelector.Footprint> shields = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            shields.add(SiteSelector.boxOf(building));
        }
        shields.addAll(ProtectedZones.footprintsFor(colony));
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (final IBuildingExtension extension : module.getOwnedExtensions())
        {
            if (!(extension instanceof FarmField field))
            {
                continue;
            }
            final BlockPos scarecrow = field.getPosition();
            if (!WorldUtil.isChunkLoaded(level, scarecrow.getX() >> 4, scarecrow.getZ() >> 4))
            {
                continue;
            }
            if (!grounds.isField(colony.getID(), scarecrow))
            {
                if (!laidOutByTheMod(level, field, scarecrow))
                {
                    continue;
                }
                grounds.addField(colony.getID(), scarecrow);
            }
            else if (!defaultRadii(field))
            {
                continue;
            }

            final int planeY = scarecrow.getY() - 1;
            final int minX = scarecrow.getX() - field.getRadius(Direction.WEST);
            final int maxX = scarecrow.getX() + field.getRadius(Direction.EAST);
            final int minZ = scarecrow.getZ() - field.getRadius(Direction.NORTH);
            final int maxZ = scarecrow.getZ() + field.getRadius(Direction.SOUTH);

            final List<SiteSelector.Footprint> nearShields = new ArrayList<>();
            for (final SiteSelector.Footprint box : shields)
            {
                if (box.intersects(minX - 8, minZ - 8, maxX + 8, maxZ + 8))
                {
                    nearShields.add(box);
                }
            }
            int healthy = 0;
            int repaired = 0;
            int cells = 0;
            int flooded = 0;
            int shieldedDead = 0;
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    cells++;
                    if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4)
                          || (x == scarecrow.getX() && z == scarecrow.getZ()))
                    {
                        healthy++;
                        continue;
                    }
                    repaired += repairCell(level, x, planeY, z, nearShields, probe);
                    final BlockState plane = level.getBlockState(probe.set(x, planeY, z));
                    if (!plane.getFluidState().isEmpty())
                    {

                        if (Math.abs(x - scarecrow.getX()) != 3 || Math.abs(z - scarecrow.getZ()) != 3)
                        {
                            flooded++;
                        }
                    }
                    else if (plane.is(BlockTags.DIRT) || plane.is(Blocks.FARMLAND))
                    {
                        healthy++;
                    }
                    else if (shielded(x, z, nearShields))
                    {

                        shieldedDead++;
                    }
                }
            }

            final int wells = carveWells(level, scarecrow);
            if (wells > 0)
            {
                repaired += wells;
                ColonyAutopilot.LOGGER.debug("[{}] carved {} water wells into the {}'s field at {} — dry farmland unravels faster than the farmer can plant it",
                  colony.getName(), wells, farm.getBuildingDisplayName(), scarecrow.toShortString());
            }
            stockSeed(farm, field.getSeed());
            if (repaired > 0)
            {
                ColonyAutopilot.LOGGER.info("[{}] repaired {} blocks of the {}'s field at {} — flooded or broken ground made whole",
                  colony.getName(), repaired, farm.getBuildingDisplayName(), scarecrow.toShortString());
            }
            final GlobalPos fieldKey = GlobalPos.of(level.dimension(), scarecrow.immutable());
            if (healthy * 10 >= cells * 7)
            {
                DEAD_GROUND_WARNED.remove(fieldKey);
            }
            else if (DEAD_GROUND_WARNED.add(fieldKey))
            {
                ColonyAutopilot.LOGGER.warn("[{}] the field at {} is mostly dead ground ({} of {} cells farmable{}{}) — the {} works a crippled plot (needs eyes)",
                  colony.getName(), scarecrow.toShortString(), healthy, cells,
                  flooded > 0 ? ", " + flooded + " under water" : "",
                  shieldedDead > 0 ? ", " + shieldedDead + " under a building footprint or protected zone" : "",
                  SiteSelector.plainName(farm));
            }
        }
    }

    private static boolean defaultRadii(final FarmField field)
    {
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            if (field.getRadius(side) != FIELD_RADIUS)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean laidOutByTheMod(final ServerLevel level, final FarmField field, final BlockPos scarecrow)
    {
        if (!defaultRadii(field))
        {
            return false;
        }

        int wells = 0;
        for (final int[] quarter : new int[][] {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}})
        {
            final BlockPos well = scarecrow.below().offset(quarter[0], 0, quarter[1]);
            if (!WorldUtil.isChunkLoaded(level, well.getX() >> 4, well.getZ() >> 4))
            {
                return false;
            }
            if (level.getFluidState(well).isSource())
            {
                wells++;
            }
        }
        return wells >= 3;
    }

    private static int carveWells(final ServerLevel level, final BlockPos scarecrow)
    {
        int carved = 0;
        for (final int[] quarter : new int[][] {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}})
        {
            final BlockPos well = scarecrow.below().offset(quarter[0], 0, quarter[1]);
            if (!WorldUtil.isChunkLoaded(level, well.getX() >> 4, well.getZ() >> 4))
            {
                continue;
            }
            final BlockState state = level.getBlockState(well);
            if (!state.getFluidState().isEmpty())
            {
                continue;
            }
            if (!state.is(BlockTags.DIRT) && !state.is(Blocks.FARMLAND) && !state.isAir() && !state.canBeReplaced())
            {
                continue;
            }
            if (level.getBiome(well).value().coldEnoughToSnow(well))
            {
                WorldUtil.setBlockState(level, well.below(), Blocks.GLOWSTONE.defaultBlockState());
            }
            WorldUtil.setBlockState(level, well, Blocks.WATER.defaultBlockState());
            carved++;
        }
        return carved;
    }

    private static void stockSeed(final IBuilding farm, final ItemStack seed)
    {
        final MinimumStockModule stock = farm.getModule(MinimumStockModule.class);
        if (stock == null || seed.isEmpty())
        {
            return;
        }

        if (!stock.isStocked(seed))
        {
            stock.addMinimumStock(seed.copyWithCount(1), 1);
        }

        ProvidenceSweep.recordStockLine(farm, stock, seed, 1);
    }

    private static int repairCell(final ServerLevel level, final int x, final int planeY, final int z,
      final List<SiteSelector.Footprint> shields, final BlockPos.MutableBlockPos pos)
    {
        int edits = 0;

        final boolean cellShielded = shielded(x, z, shields);

        if (!cellShielded)
        {
            for (int y = planeY; y <= planeY + 2; y++)
            {
                if (level.getBlockState(pos.set(x, y, z)).is(BlockTags.LOGS))
                {

                    if (VillagePaths.fellTree(level, pos.immutable(), shields, false))
                    {
                        edits++;
                    }
                    break;
                }
            }
        }

        final BlockState state = level.getBlockState(pos.set(x, planeY, z));
        if (!cellShielded && !state.hasBlockEntity() && state.getFluidState().isEmpty() && !state.is(BlockTags.DIRT) && !state.is(Blocks.FARMLAND))
        {
            WorldUtil.setBlockState(level, pos.set(x, planeY, z), Blocks.DIRT.defaultBlockState());
            edits++;
        }

        if (!cellShielded && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 > planeY)
        {
            for (int y = planeY + CARVE_CEILING; y > planeY; y--)
            {
                final BlockState above = level.getBlockState(pos.set(x, y, z));
                if (above.isAir() || above.hasBlockEntity())
                {

                    continue;
                }
                if (!above.getFluidState().isEmpty())
                {
                    if (above.getBlock() instanceof LiquidBlock)
                    {
                        WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                        edits++;
                    }
                    else if (above.hasProperty(BlockStateProperties.WATERLOGGED) && above.getValue(BlockStateProperties.WATERLOGGED))
                    {
                        WorldUtil.setBlockState(level, pos, above.setValue(BlockStateProperties.WATERLOGGED, false));
                        edits++;
                    }
                    continue;
                }
                if (above.canBeReplaced()
                      || above.is(BlockTags.CROPS) || above.getBlock() instanceof CropBlock
                      || above.getBlock() instanceof StemBlock || above.getBlock() instanceof AttachedStemBlock
                      || above.is(Blocks.PUMPKIN) || above.is(Blocks.MELON))
                {
                    continue;
                }
                WorldUtil.setBlockState(level, pos, Blocks.AIR.defaultBlockState());
                edits++;
            }
        }
        return edits;
    }

    private static boolean shielded(final int x, final int z, final List<SiteSelector.Footprint> shields)
    {
        for (final SiteSelector.Footprint box : shields)
        {
            if (box.intersects(x, z, x, z))
            {
                return true;
            }
        }
        return false;
    }

    private record FieldSurvey(BlockPos natural, BlockPos forced)
    {
    }

    static BlockPos naturalFieldPlot(final ServerLevel level, final IColony colony, final BlockPos hut, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand)
    {
        return surveyFieldPlots(level, colony, hut, obstacles, roadBand, true).natural();
    }

    private static final int NATURAL_CELLS = 85;

    private static final List<int[]> SURVEY_RING = surveyRing();

    private static List<int[]> surveyRing()
    {
        final List<int[]> ring = new ArrayList<>();
        for (int dx = -FIELD_REACH; dx <= FIELD_REACH; dx++)
        {
            for (int dz = -FIELD_REACH; dz <= FIELD_REACH; dz++)
            {
                final int distSq = dx * dx + dz * dz;
                if (distSq >= 10 * 10 && distSq <= FIELD_REACH * FIELD_REACH)
                {
                    ring.add(new int[] {dx, dz, distSq});
                }
            }
        }
        ring.sort(Comparator.comparingInt(offset -> offset[2]));
        return List.copyOf(ring);
    }

    private static FieldSurvey surveyFieldPlots(final ServerLevel level, final IColony colony, final BlockPos anchor, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand, final boolean anyNatural)
    {
        BlockPos naturalBest = null;
        int naturalScore = 0;
        BlockPos forcedBest = null;
        int forcedSpread = Integer.MAX_VALUE;
        int band = 0;
        for (final int[] offset : SURVEY_RING)
        {

            final int ring = (int) ((Math.sqrt(offset[2]) - 10) / 5);
            if (ring != band)
            {
                if (naturalScore >= 100)
                {
                    break;
                }
                band = ring;
            }
            final int cx = anchor.getX() + offset[0];
            final int cz = anchor.getZ() + offset[1];

            boolean boxed = false;
            for (final SiteSelector.Footprint box : obstacles)
            {

                if (box.intersects(cx - (FIELD_RADIUS + 2), cz - (FIELD_RADIUS + 2), cx + (FIELD_RADIUS + 2), cz + (FIELD_RADIUS + 2)))
                {
                    boxed = true;
                    break;
                }
            }
            if (boxed || onRoad(cx, cz, roadBand))
            {
                continue;
            }

            if (!WorldUtil.isChunkLoaded(level, (cx - FIELD_RADIUS) >> 4, (cz - FIELD_RADIUS) >> 4)
                  || !WorldUtil.isChunkLoaded(level, (cx + FIELD_RADIUS) >> 4, (cz - FIELD_RADIUS) >> 4)
                  || !WorldUtil.isChunkLoaded(level, (cx - FIELD_RADIUS) >> 4, (cz + FIELD_RADIUS) >> 4)
                  || !WorldUtil.isChunkLoaded(level, (cx + FIELD_RADIUS) >> 4, (cz + FIELD_RADIUS) >> 4))
            {
                continue;
            }
            final BlockPos ground = new BlockPos(cx, SiteSelector.groundHeight(level, cx, cz) - 1, cz);
            if (!colony.isCoordInColony(level, ground))
            {
                continue;
            }
            if (ground.getY() > anchor.getY() + 12)
            {
                continue;

            }
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) - 1 > ground.getY())
            {
                continue;
            }
            if (!level.getBlockState(ground.above()).canBeReplaced()
                  || !level.getBlockState(ground.above(2)).canBeReplaced()
                  || level.getBlockState(ground).is(ModBlocks.blockScarecrow))
            {
                continue;
            }

            int score = 0;
            int dead = 0;
            count:
            for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++)
            {
                for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++)
                {
                    if (level.getBlockState(ground.offset(dx, 0, dz)).is(BlockTags.DIRT))
                    {
                        score++;
                    }
                    else if (++dead > (2 * FIELD_RADIUS + 1) * (2 * FIELD_RADIUS + 1) - NATURAL_CELLS)
                    {
                        break count;
                    }
                }
            }
            if (score > naturalScore && (score < NATURAL_CELLS || !floods(level, cx, cz, ground.getY())))
            {
                naturalScore = score;
                naturalBest = ground;
                if (anyNatural && score >= NATURAL_CELLS)
                {
                    return new FieldSurvey(ground, null);
                }
            }
            if (anyNatural)
            {
                continue;
            }

            int minH = Integer.MAX_VALUE;
            int maxH = Integer.MIN_VALUE;
            int wet = 0;
            for (final int sdx : new int[] {-FIELD_RADIUS, 0, FIELD_RADIUS})
            {
                for (final int sdz : new int[] {-FIELD_RADIUS, 0, FIELD_RADIUS})
                {
                    final int h = SiteSelector.groundHeight(level, cx + sdx, cz + sdz);
                    minH = Math.min(minH, h);
                    maxH = Math.max(maxH, h);
                    if (!level.getBlockState(new BlockPos(cx + sdx, h - 1, cz + sdz)).getFluidState().isEmpty())
                    {
                        wet++;
                    }
                }
            }

            final int spread = (maxH - minH) + wet * 50;
            if (spread < forcedSpread && !floods(level, cx, cz, ground.getY()))
            {
                forcedSpread = spread;
                forcedBest = ground;
            }
        }
        final BlockPos natural = naturalScore >= NATURAL_CELLS ? naturalBest : null;
        return new FieldSurvey(natural, forcedBest);
    }

    private static boolean floods(final ServerLevel level, final int cx, final int cz, final int planeY)
    {
        final BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        for (int x = cx - FIELD_RADIUS - 1; x <= cx + FIELD_RADIUS + 1; x++)
        {
            for (int z = cz - FIELD_RADIUS - 1; z <= cz + FIELD_RADIUS + 1; z++)
            {

                if (WorldUtil.isChunkLoaded(level, x >> 4, z >> 4)
                      && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 > planeY
                      && !level.getFluidState(top.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z)).isEmpty())
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean onRoad(final int cx, final int cz, final Set<Long> roadBand)
    {
        if (roadBand.isEmpty())
        {
            return false;
        }
        for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++)
        {
            for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++)
            {
                if (roadBand.contains(BlockPos.asLong(cx + dx, 0, cz + dz)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static void carveFieldPlane(final ServerLevel level, final BlockPos center)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++)
        {
            for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++)
            {
                carveFieldColumn(level, center.getX() + dx, center.getY(), center.getZ() + dz, pos);
            }
        }
    }

    static void carveFieldColumn(final ServerLevel level, final int x, final int targetY, final int z, final BlockPos.MutableBlockPos pos)
    {
        if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
        {
            return;
        }
        final int surfaceTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        final int cutTop = Math.min(surfaceTop, targetY + CARVE_CEILING);
        for (int y = cutTop; y > targetY; y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.hasBlockEntity())
            {
                return;
            }
            if (!state.isAir())
            {
                WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        for (int y = targetY - 1; y > targetY - CARVE_CEILING; y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.hasBlockEntity())
            {
                return;
            }
            if (state.canBeReplaced() || !state.getFluidState().isEmpty())
            {
                WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.DIRT.defaultBlockState());
            }
            else
            {
                break;
            }
        }

        if (!level.getBlockState(pos.set(x, targetY, z)).hasBlockEntity())
        {
            WorldUtil.setBlockState(level, pos.set(x, targetY, z), Blocks.DIRT.defaultBlockState());
        }
    }
}
