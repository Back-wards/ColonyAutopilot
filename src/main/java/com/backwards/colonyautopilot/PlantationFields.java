// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintTagUtils;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.modules.IBuildingExtensionModule;
import com.minecolonies.api.colony.buildingextensions.plantation.IPlantationModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.blocks.BlockPlantationField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.workorders.WorkOrderPlantationField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlantationFields
{

    private static final List<String> FIELD_FOLDERS = List.of("agriculture/fields", "agriculture/fields/plantation");

    private static final String ORIGINAL_PACK = "Minecolonies Original";

    private static final List<String> PREFERENCE = List.of("kelp_field", "glowb_field", "cocoa_field", "sugar_field", "cactus_field", "bamboo_field",
      "seapickle_field", "seagrass_field", "vine_field", "crimsonp_field", "warpedp_field", "weepv_field", "twistv_field");

    private static final int FOOD_FIRST = 4;

    private static final long SWAP_PAUSE = 24000L;

    private static final int HOME_REACH = 48;

    record Design(String pack, String path, Set<String> plants)
    {
    }

    private static final Map<String, List<Design>> designs = new HashMap<>();

    private static final Map<GlobalPos, Design> choosing = new HashMap<>();

    private static final Map<GlobalPos, Long> lastSwap = new HashMap<>();

    private PlantationFields()
    {
    }

    private static final Set<GlobalPos> NO_GROUND_WARNED = new HashSet<>();

    static void clearDesigns()
    {
        designs.clear();
        choosing.clear();
        lastSwap.clear();
        NO_GROUND_WARNED.clear();
    }

    static void ensure(final ServerLevel level, final IColony colony, final IBuilding plantation, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand)
    {
        if (plantation.getModule(BuildingExtensionsModule.class) == null)
        {
            return;
        }
        final String pack = colony.getStructurePack();
        final BlockPos at = plantation.getPosition();
        withDesigns(level, pack, own -> {
            if (!own.isEmpty())
            {
                tend(level, colony, at, obstacles, roadBand, own);
                return;
            }

            withDesigns(level, ORIGINAL_PACK, borrowed -> {
                if (borrowed.isEmpty())
                {
                    ColonyAutopilot.LOGGER.warn("[{}] no plantation field blueprint in the colony's style '{}' or in '{}' — the planter at {} gets no new field",
                      colony.getName(), pack, ORIGINAL_PACK, at.toShortString());
                }
                tend(level, colony, at, obstacles, roadBand, borrowed);
            });
        });
    }

    private static void withDesigns(final ServerLevel level, final String pack, final Consumer<List<Design>> then)
    {
        final List<Design> known = designs.get(pack);
        if (known != null)
        {
            then.accept(known);
            return;
        }
        final StructurePackMeta meta = StructurePacks.getStructurePack(pack);
        if (meta == null)
        {
            designs.put(pack, List.of());
            then.accept(List.of());
            return;
        }

        final Map<String, IPlantationModule> plants = plantModules();
        CompletableFuture<List<Design>> found = CompletableFuture.completedFuture(new ArrayList<>());
        for (final String folder : FIELD_FOLDERS)
        {
            if (!Files.isDirectory(meta.getPath().resolve(meta.getNormalizedSubPath(folder))))
            {
                continue;
            }
            found = found.thenCombine(StructurePacks.getBlueprintsFuture(pack, folder, level.registryAccess()), (list, blueprints) -> {
                for (final Blueprint blueprint : blueprints)
                {
                    final Set<String> carried = plantsOf(blueprint, plants);
                    if (!carried.isEmpty())
                    {
                        list.add(new Design(pack, folder + "/" + blueprint.getFileName() + ".blueprint", carried));
                    }
                }
                return list;
            });
        }
        found.whenComplete((list, failure) -> level.getServer().execute(() -> {

            try
            {
                if (failure != null)
                {
                    ColonyAutopilot.LOGGER.warn("reading the plantation field blueprints of '{}' failed", pack, failure);
                }
                final List<Design> read = failure != null ? List.of() : List.copyOf(list);
                designs.put(pack, read);
                ColonyAutopilot.LOGGER.info("structure pack '{}' ships {} plantation field blueprint(s) the planter can use", pack, read.size());
                then.accept(read);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("the plantation field blueprints of '{}' could not be used", pack, e);
            }
        }));
    }

    private static Map<String, IPlantationModule> plantModules()
    {
        final Map<String, IPlantationModule> plants = new HashMap<>();
        for (final BuildingExtensionRegistries.BuildingExtensionEntry entry : BuildingExtensionRegistries.getBuildingExtensionRegistry())
        {
            for (final Function<IBuildingExtension, IBuildingExtensionModule> producer : entry.getExtensionModuleProducers())
            {
                if (producer.apply(null) instanceof IPlantationModule plant)
                {
                    plants.putIfAbsent(plant.getFieldTag(), plant);
                }
            }
        }
        return plants;
    }

    private static Set<String> plantsOf(final Blueprint blueprint, final Map<String, IPlantationModule> plants)
    {
        final Set<String> carried = new TreeSet<>();
        if (blueprint == null || !(blueprint.getBlockState(blueprint.getPrimaryBlockOffset()).getBlock() instanceof BlockPlantationField))
        {
            return carried;
        }
        final Set<String> tagged = new HashSet<>();
        for (final List<String> tags : BlueprintTagUtils.getBlueprintTags(blueprint).values())
        {
            tagged.addAll(tags);
        }
        for (final String tag : tagged)
        {
            if (plants.containsKey(tag) && tagged.contains(plants.get(tag).getWorkTag()))
            {
                carried.add(tag);
            }
        }
        return carried;
    }

    private static int rank(final String plant)
    {
        final int at = PREFERENCE.indexOf(plant);
        return at < 0 ? PREFERENCE.size() : at;
    }

    private static int tier(final String plant)
    {
        final int rank = rank(plant);
        return rank == 0 ? 1 : rank < FOOD_FIRST ? 2 : 3;
    }

    private static final class Planter
    {
        final IBuilding building;

        final BuildingExtensionsModule module;

        final Map<String, List<IBuildingExtension>> owned = new HashMap<>();

        final Map<String, List<IBuildingExtension>> athand = new HashMap<>();

        final Set<String> coming = new HashSet<>();

        int comingFields;

        final List<String> wanted = new ArrayList<>();

        final List<String> notes = new ArrayList<>();

        Planter(final IBuilding building, final BuildingExtensionsModule module)
        {
            this.building = building;
            this.module = module;
        }

        int ownedFields()
        {
            int fields = 0;
            for (final List<IBuildingExtension> of : owned.values())
            {
                fields += of.size();
            }
            return fields;
        }
    }

    private static void tend(final ServerLevel level, final IColony colony, final BlockPos tendedAt, final List<SiteSelector.Footprint> obstacles,
      final Set<Long> roadBand, final List<Design> offered)
    {
        final List<IBuilding> all = plantations(colony);
        final Map<BlockPos, Planter> planters = new LinkedHashMap<>();
        for (final IBuilding building : all)
        {
            final BuildingExtensionsModule module = building.getModule(BuildingExtensionsModule.class);
            if (module != null)
            {
                planters.put(building.getPosition(), new Planter(building, module));
            }
        }
        final Planter tended = planters.get(tendedAt);
        if (tended == null || tended.building.getBuildingLevel() <= 0)
        {
            return;
        }
        final Tend tend = new Tend(level, colony, planters, offered);
        for (final IBuildingExtension extension : colony.getServerBuildingManager().getBuildingExtensions(extension -> extension instanceof PlantationField))
        {
            final String plant = ((PlantationField) extension).getModule().getFieldTag();
            if (extension.isTaken())
            {
                final Planter owner = planters.get(extension.getBuildingId());
                if (owner != null)
                {
                    owner.owned.computeIfAbsent(plant, key -> new ArrayList<>()).add(extension);
                }
                continue;
            }
            final IBuilding home = homeOf(all, extension.getPosition());
            if (home == null || !tend.researched.contains(plant) || !planters.containsKey(home.getPosition()))
            {
                continue;
            }
            planters.get(home.getPosition()).athand.computeIfAbsent(plant, key -> new ArrayList<>()).add(extension);
            if (!extension.getPosition().equals(home.getPosition()))
            {
                tend.spareAt.computeIfAbsent(plant, key -> new HashSet<>()).add(home.getPosition());
            }
        }
        for (final Planter planter : planters.values())
        {
            for (final List<IBuildingExtension> fields : planter.athand.values())
            {
                fields.sort(Comparator.comparingDouble(field -> field.getPosition().distSqr(planter.building.getPosition())));
            }
        }
        for (final WorkOrderPlantationField order : colony.getWorkManager().getWorkOrdersOfType(WorkOrderPlantationField.class))
        {
            comingTo(planters, homeOf(all, order.getLocation()), plantsOrdered(order.getStructurePack(), order.getStructurePath()));
        }
        for (final Map.Entry<GlobalPos, Design> chosen : choosing.entrySet())
        {

            if (chosen.getKey().dimension().equals(colony.getDimension()) && colony.isCoordInColony(colony.getWorld(), chosen.getKey().pos()))
            {
                comingTo(planters, homeOf(all, chosen.getKey().pos()), chosen.getValue().plants());
            }
        }

        final GlobalPos tendedKey = GlobalPos.of(level.dimension(), tendedAt);
        if (NO_GROUND_WARNED.contains(tendedKey) && findPlot(level, colony, tended.building, all, obstacles, roadBand) != null)
        {
            NO_GROUND_WARNED.remove(tendedKey);
        }

        final List<Planter> earlier = new ArrayList<>();
        for (final Planter planter : planters.values())
        {
            if (planter.building.getBuildingLevel() > 0)
            {
                tend.takeOver(planter);
                tend.plan(planter, earlier);
                earlier.add(planter);
            }
        }

        final List<SiteSelector.Footprint> kept = new ArrayList<>(obstacles);
        for (final String plant : tended.wanted)
        {
            final Design design = tend.designFor.get(plant);
            final BlockPos plot = findPlot(level, colony, tended.building, all, kept, roadBand);
            if (plot == null)
            {

                if (NO_GROUND_WARNED.add(tendedKey))
                {
                    ColonyAutopilot.LOGGER.warn("[{}] no ground for a plantation field near the Plantation at {} — none level within reach, and none gentle enough to grade (a spread of at most {}), clear of buildings and roads inside the claim; looking again each day",
                      colony.getName(), tendedAt.toShortString(), GRADE_SPREAD);
                }
                break;
            }
            commission(level, colony, tendedAt, plot, design);
            tended.notes.add("commissioned a " + fieldName(design) + " field at " + plot.toShortString() + " for its " + tend.name(plant));
            kept.add(new SiteSelector.Footprint(plot.getX() - 9, plot.getZ() - 9, plot.getX() + 9, plot.getZ() + 9));
        }

        for (final Planter planter : planters.values())
        {
            if (!planter.notes.isEmpty())
            {
                final List<String> grows = new ArrayList<>(planter.owned.keySet());
                grows.sort(Comparator.comparingInt(PlantationFields::rank));
                grows.replaceAll(tend::name);
                ColonyAutopilot.LOGGER.info("[{}] the planter at {} now grows {} ({})", colony.getName(), planter.building.getPosition().toShortString(), grows,
                  String.join("; ", planter.notes));
            }
        }
        if (tend.changed)
        {

            colony.markDirty();
        }
    }

    private static List<IBuilding> plantations(final IColony colony)
    {
        final List<IBuilding> found = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutPlantation)
            {
                found.add(building);
            }
        }
        found.sort(Comparator.comparingLong(building -> building.getPosition().asLong()));
        return found;
    }

    private static IBuilding homeOf(final List<IBuilding> plantations, final BlockPos pos)
    {
        IBuilding home = null;
        double nearest = HOME_REACH * HOME_REACH;
        for (final IBuilding plantation : plantations)
        {
            final double distSq = pos.distSqr(plantation.getPosition());
            if (distSq < nearest || (home == null && distSq == nearest))
            {
                nearest = distSq;
                home = plantation;
            }
        }
        return home;
    }

    private static void comingTo(final Map<BlockPos, Planter> planters, final IBuilding home, final Set<String> plants)
    {
        final Planter planter = home == null ? null : planters.get(home.getPosition());
        if (planter != null)
        {
            planter.coming.addAll(plants);
            planter.comingFields += Math.max(1, plants.size());
        }
    }

    private static final class Tend
    {
        final ServerLevel level;

        final IColony colony;

        final Map<BlockPos, Planter> planters;

        final Map<String, IPlantationModule> plants = plantModules();

        final Set<String> researched = new HashSet<>();

        final Map<String, Design> designFor = new HashMap<>();

        final Map<String, Set<BlockPos>> spareAt = new HashMap<>();

        final Set<String> wanted = new HashSet<>();

        boolean changed;

        Tend(final ServerLevel level, final IColony colony, final Map<BlockPos, Planter> planters, final List<Design> offered)
        {
            this.level = level;
            this.colony = colony;
            this.planters = planters;
            for (final Map.Entry<String, IPlantationModule> plant : plants.entrySet())
            {
                if (researched(colony, plant.getValue()))
                {
                    researched.add(plant.getKey());
                }
            }
            final Comparator<Design> simplest = Comparator.comparingInt((Design design) -> design.plants().size()).thenComparing(Design::path);
            for (final Design design : offered)
            {
                for (final String plant : design.plants())
                {
                    designFor.merge(plant, design, (kept, other) -> simplest.compare(other, kept) < 0 ? other : kept);
                }
            }
        }

        String name(final String plant)
        {
            final IPlantationModule module = plants.get(plant);
            return module == null ? plant : BuiltInRegistries.ITEM.getKey(module.getItem()).getPath().replace('_', ' ');
        }

        boolean grown(final String plant)
        {
            for (final Planter planter : planters.values())
            {
                if (planter.owned.containsKey(plant) || planter.coming.contains(plant))
                {
                    return true;
                }
            }
            return false;
        }

        void takeOver(final Planter planter)
        {
            if (!planter.module.assignManually())
            {
                planter.module.setAssignManually(true);
                planter.module.markDirty();
                changed = true;
                ColonyAutopilot.LOGGER.info("[{}] the planter at {} works the fields the autopilot assigns from now on: its fields tab stays on manual assignment while the autopilot runs this colony",
                  colony.getName(), planter.building.getPosition().toShortString());
            }
        }

        void plan(final Planter planter, final List<Planter> earlier)
        {
            final GlobalPos key = GlobalPos.of(level.dimension(), planter.building.getPosition());
            final boolean mayOrder = !NO_GROUND_WARNED.contains(key);
            final Set<String> growing = new HashSet<>(planter.owned.keySet());
            growing.addAll(planter.coming);
            int slots = maxPlants(planter.building) - growing.size();
            int room = claimCap(colony, planter.building) - planter.ownedFields() - planter.comingFields;

            final List<String> open = new ArrayList<>();
            for (final String plant : researched)
            {
                if (!grown(plant) && !wanted.contains(plant) && (planter.athand.containsKey(plant)
                      || (mayOrder && designFor.containsKey(plant) && spareAt.getOrDefault(plant, Set.of()).stream().allMatch(planter.building.getPosition()::equals))))
                {
                    open.add(plant);
                }
            }
            open.sort(Comparator.comparingInt(PlantationFields::tier).thenComparingInt(plant -> planter.athand.containsKey(plant) ? 0 : 1)
                        .thenComparingInt(PlantationFields::rank).thenComparing(Comparator.naturalOrder()));

            final List<String> twice = new ArrayList<>();
            for (final String plant : planter.owned.keySet())
            {
                for (final Planter before : earlier)
                {
                    if (before.owned.containsKey(plant))
                    {
                        twice.add(plant);
                        break;
                    }
                }
            }
            twice.sort(Comparator.comparingInt(PlantationFields::rank).reversed());
            final Set<String> let = new HashSet<>();
            for (final String plant : twice)
            {
                if (open.isEmpty() || open.size() <= slots)
                {
                    break;
                }
                room += release(planter, plant);
                slots++;
                let.add(plant);
                planter.notes.add("freed " + name(plant) + " — the planter at " + ownerBefore(earlier, plant) + " grows it");
            }

            for (final Iterator<String> next = open.iterator(); slots > 0 && room > 0 && next.hasNext(); )
            {
                final String plant = next.next();
                next.remove();
                if (planter.athand.containsKey(plant))
                {
                    if (!take(planter, plant, ""))
                    {
                        continue;
                    }
                }
                else
                {
                    planter.wanted.add(plant);
                    wanted.add(plant);
                }
                slots--;
                room--;
            }

            if (open.isEmpty() && slots > 0 && room > 0)
            {
                final List<String> spare = new ArrayList<>(planter.athand.keySet());
                spare.sort(Comparator.comparingInt(PlantationFields::rank).thenComparing(Comparator.naturalOrder()));
                for (final String plant : spare)
                {
                    if (slots > 0 && room > 0 && !planter.owned.containsKey(plant) && !let.contains(plant)
                          && take(planter, plant, " — no plant the others lack is at hand or can be ordered here, and a slot should not idle"))
                    {
                        slots--;
                        room--;
                    }
                }
            }

            final Long swapped = lastSwap.get(key);
            if (slots == 0 && (swapped == null || level.getGameTime() - swapped >= SWAP_PAUSE))
            {
                final String better = open.stream().filter(plant -> rank(plant) < FOOD_FIRST).findFirst().orElse(null);
                final String worse = planter.owned.keySet().stream().filter(plant -> rank(plant) >= FOOD_FIRST)
                                       .max(Comparator.comparingInt(PlantationFields::rank).thenComparing(Comparator.naturalOrder())).orElse(null);
                if (better != null && worse != null)
                {
                    open.remove(better);
                    if (planter.athand.containsKey(better))
                    {
                        room += release(planter, worse);
                        take(planter, better, " for " + name(worse) + ", which it freed");
                        lastSwap.put(key, level.getGameTime());
                    }
                    else
                    {

                        planter.wanted.add(better);
                        wanted.add(better);
                    }

                    room--;
                }
            }

            if (slots == 0 && room > 0 && !planter.owned.isEmpty())
            {
                final String again = planter.owned.keySet().stream()
                                       .min(Comparator.comparingInt((String plant) -> planter.owned.get(plant).size()).thenComparingInt(this::fieldsOf)
                                              .thenComparingInt(PlantationFields::rank).thenComparing(Comparator.naturalOrder())).orElseThrow();
                if (planter.athand.containsKey(again))
                {
                    take(planter, again, " — a second field of it, the Large research's");
                }
                else if (mayOrder && designFor.containsKey(again))
                {
                    planter.wanted.add(again);
                }
            }
        }

        boolean take(final Planter planter, final String plant, final String why)
        {
            final List<IBuildingExtension> fields = planter.athand.get(plant);
            final IBuildingExtension field = fields.remove(0);
            if (fields.isEmpty())
            {
                planter.athand.remove(plant);
            }
            if (!planter.module.assignExtension(field))
            {
                return false;
            }
            planter.owned.computeIfAbsent(plant, k -> new ArrayList<>()).add(field);
            changed = true;
            planter.notes.add("took " + name(plant) + (field.getPosition().equals(planter.building.getPosition())
                                                        ? " (its hut plot)" : " (the field at " + field.getPosition().toShortString() + ")") + why);
            return true;
        }

        int release(final Planter planter, final String plant)
        {
            final List<IBuildingExtension> fields = planter.owned.remove(plant);
            for (final IBuildingExtension field : fields)
            {
                planter.module.freeExtension(field);
            }
            changed = true;
            return fields.size();
        }

        int fieldsOf(final String plant)
        {
            int fields = 0;
            for (final Planter planter : planters.values())
            {
                fields += planter.owned.getOrDefault(plant, List.of()).size();
            }
            return fields;
        }

        private static String ownerBefore(final List<Planter> earlier, final String plant)
        {
            for (final Planter before : earlier)
            {
                if (before.owned.containsKey(plant))
                {
                    return before.building.getPosition().toShortString();
                }
            }
            return "?";
        }
    }

    private static String fieldName(final Design design)
    {
        return design.path().substring(design.path().lastIndexOf('/') + 1).replace(".blueprint", "");
    }

    private static void commission(final ServerLevel level, final IColony colony, final BlockPos at, final BlockPos plot, final Design pick)
    {
        final GlobalPos key = GlobalPos.of(level.dimension(), plot);
        if (choosing.containsKey(key))
        {
            return;
        }
        choosing.put(key, pick);
        final CompletableFuture<Blueprint> future = StructurePacks.getBlueprintFuture(pick.pack(), pick.path(), true, level.registryAccess());

        future.whenComplete((blueprint, failure) -> {
            if (failure != null)
            {
                level.getServer().execute(() -> choosing.remove(key));
            }
        });

        ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(future, level, blueprint -> {
            choosing.remove(key);

            try
            {
                if (blueprint == null || !(blueprint.getBlockState(blueprint.getPrimaryBlockOffset()).getBlock() instanceof BlockPlantationField))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the plantation field blueprint {} of '{}' could not be read — no field ordered",
                      colony.getName(), pick.path(), pick.pack());
                    return;
                }

                if (colony.getServerBuildingManager().getBuilding(at) == null)
                {
                    return;
                }

                final BlockPos lifted = AutopilotConfig.get(colony, AutopilotConfig.BLUEPRINT_GROUND_OFFSET)
                                          ? plot.above(BlueprintGround.placementOffset(blueprint) - 1) : plot;
                final WorkOrderPlantationField order = WorkOrderPlantationField.create(
                  WorkOrderType.BUILD, pick.pack(), pick.path(), "Plantation Field", lifted, RotationMirror.NONE, 0);
                order.setBlueprint(blueprint, level);
                colony.getWorkManager().addWorkOrder(order, false);
                if (order.getID() == 0)
                {

                    ColonyAutopilot.LOGGER.info("[{}] MineColonies refused the plantation field ({}) at {} — it reaches past the colony's claim",
                      colony.getName(), pick.path(), lifted.toShortString());
                    return;
                }
                ColonyAutopilot.LOGGER.debug("[{}] commissioned a plantation field ({}) at {} for the planter at {}",
                  colony.getName(), fieldName(pick), lifted.toShortString(), at.toShortString());
                Milestones.say(colony, "colonyautopilot.milestone.plantationfield");
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] commissioning a plantation field at {} failed", colony.getName(), plot.toShortString(), e);
            }
        }));
    }

    private static boolean researched(final IColony colony, final IPlantationModule plant)
    {
        final ResourceLocation needs = plant == null ? null : plant.getRequiredResearchEffect();
        return plant != null && (needs == null || colony.getResearchManager().getResearchEffects().getEffectStrength(needs) > 0);
    }

    private static Set<String> plantsOrdered(final String pack, final String path)
    {
        for (final Design design : designs.getOrDefault(pack, List.of()))
        {
            if (design.path().equals(path))
            {
                return design.plants();
            }
        }
        return Set.of();
    }

    private static int maxPlants(final IBuilding plantation)
    {
        return (plantation.getBuildingLevel() + 1) / 2;
    }

    private static int claimCap(final IColony colony, final IBuilding plantation)
    {
        final int base = (plantation.getBuildingLevel() + 1) / 2;
        return colony.getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.PLANTATION_LARGE) > 0
                 ? base + 1 : base;
    }

    private static final int PLOT_HALF = 7;

    private static final int GRADE_SPREAD = 6;

    private static final int PLOT_REACH = 48;

    private static final List<int[]> PLOT_RING = plotRing();

    private static List<int[]> plotRing()
    {
        final List<int[]> ring = new ArrayList<>();
        for (int dx = -PLOT_REACH; dx <= PLOT_REACH; dx++)
        {
            for (int dz = -PLOT_REACH; dz <= PLOT_REACH; dz++)
            {
                final int distSq = dx * dx + dz * dz;
                if (distSq >= 14 * 14 && distSq <= PLOT_REACH * PLOT_REACH)
                {
                    ring.add(new int[] {dx, dz, distSq});
                }
            }
        }
        ring.sort(Comparator.comparingInt(offset -> offset[2]));
        return List.copyOf(ring);
    }

    private static BlockPos findPlot(final ServerLevel level, final IColony colony, final IBuilding plantation, final List<IBuilding> plantations,
      final List<SiteSelector.Footprint> obstacles, final Set<Long> roadBand)
    {
        final BlockPos anchor = plantation.getPosition();
        BlockPos gentlest = null;
        int gentlestSpread = GRADE_SPREAD + 1;
        for (final int[] offset : PLOT_RING)
        {
            final int cx = anchor.getX() + offset[0];
            final int cz = anchor.getZ() + offset[1];

            boolean boxed = false;
            for (final SiteSelector.Footprint box : obstacles)
            {
                if (box.intersects(cx - PLOT_HALF, cz - PLOT_HALF, cx + PLOT_HALF, cz + PLOT_HALF))
                {
                    boxed = true;
                    break;
                }
            }
            if (boxed || onRoad(cx, cz, roadBand) || !WorldUtil.isChunkLoaded(level, cx >> 4, cz >> 4))
            {
                continue;
            }
            final BlockPos center = new BlockPos(cx, SiteSelector.groundHeight(level, cx, cz), cz);
            if (!colony.isCoordInColony(level, center) || !plantable(level, center) || Mth.abs(center.getY() - anchor.getY()) > 12
                  || homeOf(plantations, center) != plantation)
            {
                continue;
            }
            int min = center.getY();
            int max = center.getY();
            for (final int[] corner : new int[][] {{cx - PLOT_HALF, cz - PLOT_HALF}, {cx + PLOT_HALF, cz - PLOT_HALF}, {cx - PLOT_HALF, cz + PLOT_HALF}, {cx + PLOT_HALF, cz + PLOT_HALF}})
            {
                if (!WorldUtil.isChunkLoaded(level, corner[0] >> 4, corner[1] >> 4))
                {
                    min = Integer.MIN_VALUE;
                    break;
                }
                final int y = SiteSelector.groundHeight(level, corner[0], corner[1]);

                if (!plantable(level, new BlockPos(corner[0], y, corner[1])) || !colony.isCoordInColony(level, new BlockPos(corner[0], y, corner[1])))
                {
                    min = Integer.MIN_VALUE;
                    break;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
            if (min == Integer.MIN_VALUE)
            {
                continue;
            }
            if (max - min <= 3)
            {
                return center;
            }

            if (max - min < gentlestSpread)
            {
                gentlestSpread = max - min;
                gentlest = center;
            }
        }
        if (gentlest == null)
        {
            return null;
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = gentlest.getX() - PLOT_HALF; x <= gentlest.getX() + PLOT_HALF; x++)
        {
            for (int z = gentlest.getZ() - PLOT_HALF; z <= gentlest.getZ() + PLOT_HALF; z++)
            {
                FarmFields.carveFieldColumn(level, x, gentlest.getY() - 1, z, pos);
            }
        }

        final SiteSelector.Footprint graded = new SiteSelector.Footprint(gentlest.getX() - PLOT_HALF, gentlest.getZ() - PLOT_HALF,
          gentlest.getX() + PLOT_HALF, gentlest.getZ() + PLOT_HALF);
        final List<SiteSelector.Footprint> keepOut = new ArrayList<>(obstacles);
        keepOut.addAll(SiteSelector.foreignClaims(level, colony, graded.minX() - 40, graded.minZ() - 40, graded.maxX() + 40, graded.maxZ() + 40));
        final VillageGrounds.SkirtResult skirt = VillageGrounds.smoothSkirt(level, graded, gentlest.getY() - 1, keepOut, roadBand);
        ColonyAutopilot.LOGGER.info("[{}] graded a {}x{} plot for a plantation field at {} — no level ground near the Plantation at {}, and this one lay within {} of level; {} blocks smoothed round it",
          colony.getName(), 2 * PLOT_HALF + 1, 2 * PLOT_HALF + 1, gentlest.toShortString(), anchor.toShortString(), gentlestSpread, skirt.total());
        return gentlest;
    }

    private static boolean onRoad(final int cx, final int cz, final Set<Long> roadBand)
    {
        for (int x = cx - PLOT_HALF; x <= cx + PLOT_HALF && !roadBand.isEmpty(); x++)
        {
            for (int z = cz - PLOT_HALF; z <= cz + PLOT_HALF; z++)
            {
                if (roadBand.contains(BlockPos.asLong(x, 0, z)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean plantable(final ServerLevel level, final BlockPos surface)
    {
        final var ground = level.getBlockState(surface.below());

        return ground.getFluidState().isEmpty() && !ground.canBeReplaced() && !ground.is(net.minecraft.tags.BlockTags.ICE)
                 && !ground.is(net.minecraft.world.level.block.Blocks.DIRT_PATH) && !ground.is(net.minecraft.world.level.block.Blocks.GRAVEL);
    }
}
