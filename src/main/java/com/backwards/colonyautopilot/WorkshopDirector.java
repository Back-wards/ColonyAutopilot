// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCrusher;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkComposter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.minecolonies.api.util.constant.BuildingConstants.BUILDING_FLOWER_LIST;

public class WorkshopDirector
{
    private static final int PERIOD_TICKS = 600;

    private int tickCounter = 23;

    private final ColonyRota rota = new ColonyRota();

    private final GrowthDirector growth;

    private Map<Block, List<IRecipeStorage>> lessons;

    private Map<Block, List<IRecipeStorage>> masterLessons;

    WorkshopDirector(final GrowthDirector growth)
    {
        this.growth = growth;
    }

    private final Map<ColonyId, Set<String>> refusedLessonsLogged = new HashMap<>();

    private final Map<ColonyId, Map<BlockPos, TaughtState>> fullyTaughtAt = new HashMap<>();

    private record TaughtState(int level, boolean mature, IBuilding building) {}

    private Map<net.minecraft.world.item.Item, RecipeHolder<CraftingRecipe>> craftableIndex;

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        lessons = null;
        masterLessons = null;
        craftableIndex = null;
        refusedLessonsLogged.clear();
        fullyTaughtAt.clear();
    }

    boolean teachCrafterFor(final IColony colony, final ItemStack wanted)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.TEACH_RECIPES) || !(colony.getWorld() instanceof ServerLevel level))
        {
            return false;
        }
        if (craftableIndex == null)
        {
            craftableIndex = new HashMap<>();
            for (final RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
            {
                final CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial())
                {
                    continue;
                }
                final ItemStack result = recipe.getResultItem(level.registryAccess());
                if (result.isEmpty() || recipe.getIngredients().size() > 9)
                {
                    continue;
                }

                boolean foreign = false;
                if (BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace().equals("minecraft"))
                {
                    for (final Ingredient ingredient : recipe.getIngredients())
                    {
                        final ItemStack[] options = ingredient.getItems();
                        if (options.length > 0 && !BuiltInRegistries.ITEM.getKey(options[0].getItem()).getNamespace().equals("minecraft"))
                        {
                            foreign = true;
                            break;
                        }
                    }
                }
                if (!foreign)
                {
                    craftableIndex.putIfAbsent(result.getItem(), holder);
                }
            }
        }
        final RecipeHolder<CraftingRecipe> holder = craftableIndex.get(wanted.getItem());
        if (holder == null)
        {
            return false;
        }
        final List<ItemStack> inputs = new ArrayList<>();
        for (final Ingredient ingredient : holder.value().getIngredients())
        {
            if (ingredient.isEmpty())
            {
                continue;
            }
            final ItemStack[] options = ingredient.getItems();
            if (options.length == 0)
            {
                return false;
            }
            inputs.add(options[0].copy());
        }
        if (inputs.isEmpty())
        {
            return false;
        }

        final ItemStack result = holder.value().getResultItem(level.registryAccess()).copy();
        if (AutopilotConfig.progressionMode(colony) && inputs.stream().anyMatch(input -> Exchange.familyValue(input.getItem()) > 0)
              && (ItemStackUtils.ISFOOD.test(wanted) || inputs.stream().mapToLong(Treasury::cost).sum() > Treasury.cost(result)))
        {
            return false;
        }
        final IRecipeStorage storage = grid(result, inputs.toArray(new ItemStack[0]));
        final IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            for (final AbstractCraftingBuildingModule module : building.getModules(AbstractCraftingBuildingModule.class))
            {

                if (module.getRecipes().contains(token) && worked(building, module))
                {
                    return false;
                }
            }
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0 || building.getAllAssignedCitizen().isEmpty())
            {
                continue;
            }
            for (final AbstractCraftingBuildingModule module : building.getModules(AbstractCraftingBuildingModule.class))
            {
                if (worked(building, module) && module.addRecipe(token))
                {
                    building.markDirty();
                    ColonyAutopilot.LOGGER.info("[{}] a stranded request taught the {} to craft {} — production replaces providence",
                      colony.getName(), building.getBuildingDisplayName(), wanted.getHoverName().getString());
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean worked(final IBuilding building, final AbstractCraftingBuildingModule module)
    {
        final IJob<?> benchJob = module.getCraftingJob();
        return benchJob instanceof AbstractJobCrafter
                 && building.getModulesByType(WorkerBuildingModule.class).stream()
                      .anyMatch(post -> post.getJobEntry() == benchJob.getJobRegistryEntry() && post.hasAssignedCitizen());
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
            ColonyAutopilot.retainColonies(rota.fill(event.getServer()), refusedLessonsLogged, fullyTaughtAt);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {

            final boolean teach = AutopilotConfig.get(colony, AutopilotConfig.TEACH_RECIPES);
            final boolean stock = AutopilotConfig.get(colony, AutopilotConfig.WAREHOUSE_STOCK);
            if (!teach && !stock)
            {
                continue;
            }
            try
            {
                evaluate(colony, teach, stock);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Workshop evaluation failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void evaluate(final IColony colony, final boolean teach, final boolean stock)
    {
        if (!(colony.getWorld() instanceof ServerLevel))
        {
            return;
        }
        final boolean mature = teach && growth.planComplete(colony);
        final Set<BlockPos> warehouses = new HashSet<>();
        final Set<BlockPos> crafters = new HashSet<>();
        final Set<BlockPos> crushers = new HashSet<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0)
            {
                continue;
            }
            final Block block = building.getBuildingType().getBuildingBlock();
            if (block == ModBlocks.blockHutWareHouse)
            {
                warehouses.add(building.getPosition());
                if (stock)
                {
                    applyWarehouseStock(colony, building);
                }
            }
            else if (teach)
            {
                if (block == ModBlocks.blockHutCrusher)
                {
                    crushers.add(building.getPosition());
                    wakeCrusher(colony, building);
                }
                else if (block == ModBlocks.blockHutComposter)
                {
                    fillItemList(colony, building, EntityAIWorkComposter.COMPOSTABLE_LIST,
                      () -> IColonyManager.getInstance().getCompatibilityManager().getCompostInputs(), "compostables");
                }
                else if (block == ModBlocks.blockHutBeekeeper)
                {
                    fillItemList(colony, building, BUILDING_FLOWER_LIST,
                      () -> IColonyManager.getInstance().getCompatibilityManager().getImmutableFlowers(), "breeding flowers");
                }
                else
                {
                    crafters.add(building.getPosition());
                    teachLessons(colony, building, block, mature);
                }
            }
        }

        if (colony.getWorld() instanceof ServerLevel level)
        {
            final Set<Long> standing = new HashSet<>();
            for (final BlockPos warehouse : warehouses)
            {
                standing.add(warehouse.asLong());
            }
            ColonyGrounds.get(level).retainWarehouses(colony.getID(), standing);
        }

        if (teach)
        {
            final Map<BlockPos, TaughtState> taughtAt = fullyTaughtAt.get(ColonyAutopilot.colonyKey(colony));
            if (taughtAt != null)
            {
                taughtAt.keySet().retainAll(crafters);
            }

            if (colony.getWorld() instanceof ServerLevel level)
            {
                final Set<Long> live = new HashSet<>();
                crushers.forEach(crusher -> live.add(crusher.asLong()));
                ColonyGrounds.get(level).retainCrushers(colony.getID(), live);
            }
        }
    }

    private void applyWarehouseStock(final IColony colony, final IBuilding warehouse)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }

        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final MinimumStockModule module = warehouse.getModule(MinimumStockModule.class);
        if (module == null)
        {
            return;
        }
        final net.minecraft.world.item.Item[] staples = {Items.OAK_PLANKS, Items.STONE_BRICKS, Items.IRON_INGOT, Items.CHARCOAL,
          Items.TORCH, Items.BREAD, Items.STICK, Items.GLASS, Items.ARROW};
        final int[] stacks = {2, 2, 1, 1, 1, 1, 1, 1, 1};
        int seeded = grounds.warehouseSeeded(colony.getID(), warehouse.getPosition());

        for (int i = 0; seeded > 0 && i < staples.length; i++)
        {
            if ((seeded & (1 << i)) != 0)
            {
                ProvidenceSweep.recordStockLine(warehouse, module, new ItemStack(staples[i]), stacks[i]);
            }
        }
        if (grounds.warehouseStockLevel(colony.getID(), warehouse.getPosition()) >= warehouse.getBuildingLevel())
        {
            return;
        }
        if (seeded < 0)
        {

            seeded = 0;
            boolean stockedBefore = false;
            for (final net.minecraft.world.item.Item staple : staples)
            {
                stockedBefore |= module.isStocked(new ItemStack(staple));
            }
            if (stockedBefore)
            {
                for (int i = 0; i < staples.length; i++)
                {
                    if (module.isStocked(new ItemStack(staples[i])) || i < 5 * warehouse.getBuildingLevel())
                    {
                        seeded |= 1 << i;
                    }
                }
            }
        }
        for (int i = 0; i < staples.length; i++)
        {
            if ((seeded & (1 << i)) != 0)
            {
                continue;
            }
            final ItemStack stack = new ItemStack(staples[i]);
            if (!module.isStocked(stack))
            {
                module.addMinimumStock(stack, stacks[i]);
            }
            if (module.isStocked(stack))
            {
                seeded |= 1 << i;
                ProvidenceSweep.recordStockLine(warehouse, module, stack, stacks[i]);
            }
        }
        grounds.setWarehouseSeeded(colony.getID(), warehouse.getPosition(), seeded);
        grounds.setWarehouseStockLevel(colony.getID(), warehouse.getPosition(), warehouse.getBuildingLevel());
        ColonyAutopilot.LOGGER.info("[{}] warehouse standing stock set — the workshops produce the staples they can make",
          colony.getName());
    }

    private void wakeCrusher(final IColony colony, final IBuilding crusher)
    {
        final IntSetting limit = crusher.getSetting(BuildingCrusher.DAILY_LIMIT);
        if (limit == null || !(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);

        if (limit.getValue() > 0)
        {
            grounds.markCrusherWoken(colony.getID(), crusher.getPosition());
            return;
        }
        if (!grounds.crusherWoken(colony.getID(), crusher.getPosition()))
        {
            limit.setValue(16);
            crusher.markDirty();
            grounds.markCrusherWoken(colony.getID(), crusher.getPosition());
            ColonyAutopilot.LOGGER.info("[{}] set the crusher to 16 crushes per day (it ships turned off)", colony.getName());
        }
    }

    private void fillItemList(final IColony colony, final IBuilding building, final String listId, final Supplier<Set<ItemStorage>> items, final String what)
    {
        for (final ItemListModule module : building.getModules(ItemListModule.class))
        {
            if (module.getId().equals(listId) && module.getList().isEmpty())
            {
                final Set<ItemStorage> candidates = items.get();
                if (candidates.isEmpty())
                {
                    return;
                }
                for (final ItemStorage item : candidates)
                {
                    module.addItem(item);
                }
                ColonyAutopilot.LOGGER.info("[{}] filled the {}'s {} list ({} items)",
                  colony.getName(), building.getBuildingDisplayName(), what, candidates.size());
            }
        }
    }

    private void teachLessons(final IColony colony, final IBuilding building, final Block block, final boolean mature)
    {
        if (lessons == null)
        {
            lessons = buildLessons();
            masterLessons = buildMasterLessons();
        }
        final Map<BlockPos, TaughtState> taughtAt = fullyTaughtAt.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        final TaughtState memo = taughtAt.get(building.getPosition());
        if (memo != null && memo.level() == building.getBuildingLevel() && memo.mature() == mature && memo.building() == building)
        {
            return;
        }
        final List<IRecipeStorage> curriculum = new ArrayList<>(lessons.getOrDefault(block, List.of()));
        if (mature)
        {
            curriculum.addAll(masterLessons.getOrDefault(block, List.of()));
        }
        if (curriculum.isEmpty())
        {
            return;
        }
        final List<AbstractCraftingBuildingModule> modules = building.getModules(AbstractCraftingBuildingModule.class);
        if (modules.isEmpty())
        {
            return;
        }
        int taught = 0;
        boolean allLanded = true;
        for (final IRecipeStorage storage : curriculum)
        {
            final IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
            boolean known = false;
            for (final AbstractCraftingBuildingModule module : modules)
            {
                if (module.getRecipes().contains(token))
                {
                    known = true;
                    break;
                }
            }
            if (known)
            {
                continue;
            }
            boolean accepted = false;
            for (final AbstractCraftingBuildingModule module : modules)
            {

                if (module.addRecipe(token))
                {
                    taught++;
                    accepted = true;
                    break;
                }
            }
            if (!accepted)
            {
                allLanded = false;

                final String lessonKey = building.getID().toShortString() + '|' + storage.getPrimaryOutput().getHoverName().getString();
                if (refusedLessonsLogged.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashSet<>()).add(lessonKey))
                {
                    ColonyAutopilot.LOGGER.debug("[{}] the {} did not accept a lesson for {} — retried quietly until it can",
                      colony.getName(), building.getBuildingDisplayName(), storage.getPrimaryOutput().getHoverName().getString());
                }
            }
        }
        if (taught > 0)
        {
            building.markDirty();
            ColonyAutopilot.LOGGER.info("[{}] taught the {} {} recipe(s)", colony.getName(), building.getBuildingDisplayName(), taught);
        }
        if (allLanded)
        {
            taughtAt.put(building.getPosition(), new TaughtState(building.getBuildingLevel(), mature, building));
        }
    }

    private static Map<Block, List<IRecipeStorage>> buildLessons()
    {
        final Map<Block, List<IRecipeStorage>> map = new HashMap<>();

        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)));
        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.SPRUCE_PLANKS, 4), new ItemStack(Items.SPRUCE_LOG)));
        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.BIRCH_PLANKS, 4), new ItemStack(Items.BIRCH_LOG)));
        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.JUNGLE_PLANKS, 4), new ItemStack(Items.JUNGLE_LOG)));
        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.ACACIA_PLANKS, 4), new ItemStack(Items.ACACIA_LOG)));
        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.DARK_OAK_PLANKS, 4), new ItemStack(Items.DARK_OAK_LOG)));
        learn(map, ModBlocks.blockHutSawmill, grid(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS, 2)));

        learn(map, ModBlocks.blockHutStonemason, grid(new ItemStack(Items.STONE_BRICKS, 4), new ItemStack(Items.STONE, 4)));

        learn(map, ModBlocks.blockHutStoneSmeltery, furnace(new ItemStack(Items.STONE), new ItemStack(Items.COBBLESTONE)));
        learn(map, ModBlocks.blockHutStoneSmeltery, furnace(new ItemStack(Items.SMOOTH_STONE), new ItemStack(Items.STONE)));

        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.STICK)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_HOE), new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.SHEARS), new ItemStack(Items.IRON_INGOT, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_INGOT, 5)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_INGOT, 8)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_INGOT, 7)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_INGOT, 4)));

        learn(map, ModBlocks.blockHutFletcher, grid(new ItemStack(Items.ARROW, 4),
          new ItemStack(Items.FLINT), new ItemStack(Items.STICK), new ItemStack(Items.FEATHER)));

        learn(map, ModBlocks.blockHutGlassblower, furnace(new ItemStack(Items.GLASS), new ItemStack(Items.SAND)));
        learn(map, ModBlocks.blockHutGlassblower, grid(new ItemStack(Items.GLASS_PANE, 16), new ItemStack(Items.GLASS, 6)));

        learn(map, ModBlocks.blockHutDyer, grid(new ItemStack(Items.WHITE_DYE), new ItemStack(Items.BONE_MEAL)));
        learn(map, ModBlocks.blockHutDyer, grid(new ItemStack(Items.BLACK_DYE), new ItemStack(Items.INK_SAC)));

        learn(map, ModBlocks.blockHutMechanic, grid(new ItemStack(Items.TORCH, 4), new ItemStack(Items.COAL), new ItemStack(Items.STICK)));

        learn(map, ModBlocks.blockHutBaker, grid(new ItemStack(Items.BREAD), new ItemStack(Items.WHEAT, 3)));

        return map;
    }

    private static Map<Block, List<IRecipeStorage>> buildMasterLessons()
    {
        final Map<Block, List<IRecipeStorage>> map = new HashMap<>();
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_SHOVEL), new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.STICK)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.STICK, 2)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND, 5)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND, 8)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND, 7)));
        learn(map, ModBlocks.blockHutBlacksmith, grid(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND, 4)));
        return map;
    }

    private static void learn(final Map<Block, List<IRecipeStorage>> map, final Block hut, final IRecipeStorage storage)
    {
        map.computeIfAbsent(hut, k -> new ArrayList<>()).add(storage);
    }

    private static IRecipeStorage grid(final ItemStack output, final ItemStack... inputs)
    {
        final List<ItemStorage> in = new ArrayList<>();
        for (final ItemStack stack : inputs)
        {
            in.add(new ItemStorage(stack));
        }
        return RecipeStorage.builder()
          .withInputs(in)
          .withPrimaryOutput(output)
          .withGridSize(3)
          .withIntermediate(Blocks.AIR)
          .build();
    }

    private static IRecipeStorage furnace(final ItemStack output, final ItemStack input)
    {
        return RecipeStorage.builder()
          .withInputs(List.of(new ItemStorage(input)))
          .withPrimaryOutput(output)
          .withGridSize(1)
          .withIntermediate(Blocks.FURNACE)
          .build();
    }
}
