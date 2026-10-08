// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.AbstractJobStructure;
import com.minecolonies.core.colony.jobs.JobArcherTraining;
import com.minecolonies.core.colony.jobs.JobCombatTraining;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.jobs.JobNetherWorker;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkComposter;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

public class WarehouseJanitor
{

    private static final int PERIOD_TICKS = 12000;

    private static final Set<Item> JUNK = Set.of(
      Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.BONE, Items.SPIDER_EYE,
      Items.SHORT_GRASS, Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN,
      Items.DEAD_BUSH, Items.VINE, Items.SEAGRASS, Items.LILY_PAD, Items.PUFFERFISH);

    private static final List<String> MODDED_JUNK_IDS = List.of("mysticalagriculture:inferium_essence");
    private static Set<Item> moddedJunk;

    private static final Set<Item> TRIMMABLE = Set.of(
      Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.SAND,
      Items.NETHERRACK, Items.DIORITE, Items.GRANITE, Items.ANDESITE, Items.TUFF,
      Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);

    private static final Set<Item> POCKET_RUBBLE = Set.of(
      Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.SAND,
      Items.NETHERRACK, Items.DIORITE, Items.GRANITE, Items.ANDESITE, Items.TUFF);

    private static final Set<Item> VALUABLES = Set.of(
      Items.IRON_INGOT, Items.IRON_NUGGET, Items.RAW_IRON, Items.IRON_BLOCK, Items.RAW_IRON_BLOCK,
      Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.RAW_GOLD, Items.GOLD_BLOCK, Items.RAW_GOLD_BLOCK,
      Items.COPPER_INGOT, Items.RAW_COPPER, Items.COPPER_BLOCK, Items.RAW_COPPER_BLOCK,
      Items.DIAMOND, Items.DIAMOND_BLOCK, Items.EMERALD, Items.EMERALD_BLOCK,
      Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.NETHERITE_BLOCK, Items.ANCIENT_DEBRIS,
      Items.LAPIS_LAZULI, Items.LAPIS_BLOCK, Items.REDSTONE, Items.REDSTONE_BLOCK,
      Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.QUARTZ, Items.AMETHYST_SHARD);

    private static final int WAREHOUSE_STACKS_PER_ITEM = 4;

    private static final Set<Item> POTIONS = Set.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);
    private static final int POTION_KEEP = 24;

    private static final int BUILDING_STACKS_PER_ITEM = 1;

    private static final int HUT_BUFFER = 2 * 64;

    private static final int POCKET_KEEP = 64;

    private int tickCounter = 12;

    private int round = 0;

    private final ColonyRota rota = new ColonyRota();

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
            round++;
            rota.fill(event.getServer());
        }
        for (final IColony colony : rota.take(event.getServer()))
        {
            if (!AutopilotConfig.get(colony, AutopilotConfig.WAREHOUSE_JANITOR))
            {
                continue;
            }
            try
            {
                tidy(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Warehouse janitor failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void tidy(final IColony colony)
    {
        if (AutopilotConfig.get(colony, AutopilotConfig.PURGE_MOB_DROPS))
        {
            purgeJunk(colony);
        }
        capEnchantedBooks(colony);
        final List<IItemHandler> warehouses = new ArrayList<>();
        final List<AbstractBuildingStructureBuilder> builderHuts = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0 || building.getTileEntity() == null)
            {
                continue;
            }
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse)
            {
                tidyWarehouse(colony, building);
                final IItemHandler racks = building.getItemHandlerCap();
                if (racks != null && racks.getSlots() > 0)
                {
                    warehouses.add(racks);
                }
            }
            else if (building instanceof AbstractBuildingStructureBuilder builder)
            {
                builderHuts.add(builder);
            }
        }
        if (!builderHuts.isEmpty())
        {

            builderHuts.sort(Comparator.comparingLong(hut -> hut.getPosition().asLong()));
            tidyBuilderHut(colony, builderHuts.get(round % builderHuts.size()), warehouses);
        }
        tidyPockets(colony);
    }

    private static void purgeJunk(final IColony colony)
    {
        final boolean sporeLoaded = ModList.get().isLoaded(SporeCompat.MOD_ID);

        final Set<Item> buildNeeded = new HashSet<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof AbstractBuildingStructureBuilder builder)
            {
                for (final BuildingBuilderResource resource : builder.getNeededResources().values())
                {
                    buildNeeded.add(resource.getItemStack().getItem());
                }
            }
        }
        int junked = 0;
        int gear = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() > 0 && building.getTileEntity() != null)
            {

                final boolean keepsAmmo = building instanceof AbstractBuildingGuards
                                            || building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse
                                            || building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFletcher;

                final Set<Item> spared = sparedAt(building, buildNeeded);

                final boolean keepsGear = building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutArchery
                                            || building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutCombatAcademy;
                final int[] voided = voidJunk(building.getItemHandlerCap(), sporeLoaded, keepsAmmo, keepsGear, spared);
                junked += voided[0];
                gear += voided[1];
            }
        }
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {

            if (citizen.getJob() instanceof JobDeliveryman)
            {
                continue;
            }
            final boolean guard = citizen.getJob() instanceof AbstractJobGuard;

            final IBuilding work = citizen.getWorkBuilding();
            final boolean keepsAmmo = guard || (work != null && work.getBuildingType().getBuildingBlock() == ModBlocks.blockHutFletcher);

            final boolean keepsKit = guard || citizen.getJob() instanceof JobCombatTraining || citizen.getJob() instanceof JobArcherTraining
                                       || citizen.getJob() instanceof JobNetherWorker;
            final Set<Item> spared = work != null ? sparedAt(work, buildNeeded) : buildNeeded;
            final int[] voided = voidJunk(citizen.getInventory(), sporeLoaded, keepsAmmo, keepsKit, spared);
            junked += voided[0];
            gear += voided[1];
        }
        if (junked > 0 || gear > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the janitor purged {} junk mob-drop(s) and {} piece(s) of damaged mob gear from the colony's shelves and pockets — detritus nothing can use",
              colony.getName(), junked, gear);
        }
    }

    private static Set<Item> sparedAt(final IBuilding building, final Set<Item> buildNeeded)
    {
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutComposter)
        {
            return withCompostables(building, buildNeeded);
        }
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutCrusher)
        {
            return withCrafterInputs(building, buildNeeded);
        }
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutPlantation)
        {
            final BuildingExtensionsModule fields = building.getModule(BuildingExtensionsModule.class);
            return withPlants(fields == null ? List.of() : fields.getOwnedExtensions(), buildNeeded);
        }
        if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse)
        {
            return withPlants(building.getColony().getServerBuildingManager().getBuildingExtensions(extension -> extension instanceof PlantationField), buildNeeded);
        }
        return buildNeeded;
    }

    private static Set<Item> withPlants(final Collection<IBuildingExtension> fields, final Set<Item> buildNeeded)
    {
        final Set<Item> spared = new HashSet<>(buildNeeded);
        for (final IBuildingExtension field : fields)
        {
            if (field instanceof PlantationField plantation)
            {
                spared.add(plantation.getModule().getItem());
            }
        }
        return spared;
    }

    private static Set<Item> withCrafterInputs(final IBuilding crafter, final Set<Item> buildNeeded)
    {
        final Set<Item> spared = new HashSet<>(buildNeeded);
        for (final ICraftingBuildingModule module : crafter.getModules(ICraftingBuildingModule.class))
        {
            for (final IToken<?> token : module.getRecipes())
            {
                final IRecipeStorage recipe = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
                if (recipe != null)
                {
                    for (final ItemStorage input : recipe.getCleanedInput())
                    {
                        spared.add(input.getItem());
                    }
                }
            }
        }
        return spared;
    }

    private static Set<Item> withCompostables(final IBuilding composter, final Set<Item> buildNeeded)
    {
        final Set<Item> spared = new HashSet<>(buildNeeded);
        for (final ItemListModule module : composter.getModules(ItemListModule.class))
        {
            if (module.getId().equals(EntityAIWorkComposter.COMPOSTABLE_LIST))
            {
                for (final ItemStorage entry : module.getList())
                {
                    spared.add(entry.getItem());
                }
            }
        }
        return spared;
    }

    private static int[] voidJunk(final IItemHandler handler, final boolean sporeLoaded, final boolean keepArrows, final boolean keepGear, final Set<Item> buildNeeded)
    {
        if (handler == null)
        {
            return new int[] {0, 0};
        }
        int junked = 0;
        int gear = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);

            if (stack.isEmpty() || stack.is(ModItems.COLONY_BUCKS.get()))
            {
                continue;
            }
            final Item item = stack.getItem();
            if ((sporeLoaded && SporeCompat.isSporeItem(item))
                  || (JUNK.contains(item) && !buildNeeded.contains(item)) || moddedJunk().contains(item)
                  || (!keepArrows && item instanceof ArrowItem))
            {
                junked += handler.extractItem(slot, stack.getCount(), false).getCount();
            }
            else if (!keepGear && mobDropGear(stack))
            {
                gear += handler.extractItem(slot, stack.getCount(), false).getCount();
            }
        }
        return new int[] {junked, gear};
    }

    private static boolean mobDropGear(final ItemStack stack)
    {
        return stack.isDamaged()
                 && (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof SwordItem
                       || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                       || stack.getItem() instanceof TridentItem);
    }

    private static Set<Item> moddedJunk()
    {
        if (moddedJunk == null)
        {
            final Set<Item> found = new HashSet<>();
            for (final String id : MODDED_JUNK_IDS)
            {
                BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).ifPresent(found::add);
            }
            moddedJunk = found;
        }
        return moddedJunk;
    }

    private static void tidyBuilderHut(final IColony colony, final AbstractBuildingStructureBuilder builder, final List<IItemHandler> warehouses)
    {
        final IItemHandler racks = builder.getItemHandlerCap();
        if (racks == null || racks.getSlots() == 0)
        {
            return;
        }

        final Map<Item, Integer> needed = new HashMap<>();
        for (final BuildingBuilderResource resource : builder.getNeededResources().values())
        {
            needed.merge(resource.getItemStack().getItem(), resource.getAmount(), Integer::sum);
        }
        final Map<Item, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < racks.getSlots(); slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (!stack.isEmpty())
            {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        int shipped = 0;
        int trimmed = 0;
        final Set<Item> saturated = new HashSet<>();
        for (int slot = 0; slot < racks.getSlots(); slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            final Item item = stack.getItem();
            if (stack.getMaxDamage() > 0 || ItemStackUtils.ISFOOD.test(stack))
            {
                continue;
            }
            final int floor = needed.getOrDefault(item, 0) + (TRIMMABLE.contains(item) ? HUT_BUFFER : 0);
            final int excess = Math.min(counts.getOrDefault(item, 0) - floor, stack.getCount());
            if (excess <= 0)
            {
                continue;
            }
            if (saturated.contains(item))
            {
                if (TRIMMABLE.contains(item))
                {

                    final int taken = racks.extractItem(slot, excess, false).getCount();
                    trimmed += taken;
                    counts.merge(item, -taken, Integer::sum);
                }
                continue;
            }

            final ItemStack outbound = racks.extractItem(slot, excess, false);
            ItemStack left = outbound;
            for (final IItemHandler warehouse : warehouses)
            {
                if (left.isEmpty())
                {
                    break;
                }
                left = ItemHandlerHelper.insertItemStacked(warehouse, left, false);
            }
            final int moved = outbound.getCount() - left.getCount();
            shipped += moved;
            counts.merge(item, -moved, Integer::sum);
            if (!left.isEmpty())
            {
                saturated.add(item);
                if (TRIMMABLE.contains(item))
                {
                    trimmed += left.getCount();
                    counts.merge(item, -left.getCount(), Integer::sum);
                }
                else
                {
                    ItemHandlerHelper.insertItemStacked(racks, left, false);
                }
            }
        }
        if (shipped > 0 || trimmed > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] janitor cleared the {} at {}: {} items shipped to the warehouse, {} rubble burned",
              colony.getName(), builder.getBuildingDisplayName(), builder.getPosition().toShortString(), shipped, trimmed);
        }
    }

    private static void tidyWarehouse(final IColony colony, final IBuilding building)
    {
        final IItemHandler racks = building.getItemHandlerCap();
        if (racks == null || racks.getSlots() == 0)
        {
            return;
        }

        final Map<Item, Integer> buildNeeds = new HashMap<>();
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (other instanceof AbstractBuildingStructureBuilder builder)
            {
                for (final BuildingBuilderResource resource : builder.getNeededResources().values())
                {
                    buildNeeds.merge(resource.getItemStack().getItem(), resource.getAmount(), Integer::sum);
                }
            }
        }
        final Set<String> keptForBuilds = new TreeSet<>();

        final List<Map.Entry<Predicate<ItemStack>, Integer>> floors = new ArrayList<>();
        final MinimumStockModule stock = building.getModule(MinimumStockModule.class);
        if (stock != null)
        {
            stock.alterItemsToBeKept((matcher, amount, tight) -> floors.add(Map.entry(matcher, amount)));
        }
        final boolean goodsKept = AutopilotConfig.progressionMode(colony);
        final Map<Object, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < racks.getSlots(); slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (!stack.isEmpty() && capped(stack, goodsKept))
            {
                counts.merge(potionAwareKey(stack), stack.getCount(), Integer::sum);
            }
        }

        int trimmed = 0;
        for (int slot = 0; slot < racks.getSlots(); slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            if (!capped(stack, goodsKept))
            {
                continue;
            }
            int cap;
            if (POTIONS.contains(stack.getItem()))
            {
                cap = POTION_KEEP;
            }
            else
            {
                final int stacksAllowed = stack.getItem() instanceof BlockItem && !ItemStackUtils.ISFOOD.test(stack)
                                            ? BUILDING_STACKS_PER_ITEM : WAREHOUSE_STACKS_PER_ITEM;
                cap = stacksAllowed * stack.getMaxStackSize();
            }
            for (final Map.Entry<Predicate<ItemStack>, Integer> floor : floors)
            {
                if (floor.getValue() > cap && floor.getKey().test(stack))
                {
                    cap = floor.getValue();
                }
            }
            final Object key = potionAwareKey(stack);
            final int held = counts.getOrDefault(key, 0);
            final int needed = buildNeeds.getOrDefault(stack.getItem(), 0);
            if (needed > cap)
            {
                if (held > cap)
                {
                    keptForBuilds.add(stack.getHoverName().getString());
                }
                cap = needed;
            }
            if (held > cap)
            {
                final int take = Math.min(held - cap, stack.getCount());
                final int taken = racks.extractItem(slot, take, false).getCount();
                counts.merge(key, -taken, Integer::sum);
                trimmed += taken;
            }
        }
        if (trimmed > 0 || !keptForBuilds.isEmpty())
        {
            ColonyAutopilot.LOGGER.debug("[{}] janitor kept the warehouse tidy — {} surplus items cleared (one stack of each building material, four of the rest; the treasury and what the open builds need untouched){}",
              colony.getName(), trimmed, keptForBuilds.isEmpty() ? "" : "; kept for the open builds: " + String.join(", ", keptForBuilds));
        }
    }

    private static final int ENCHANTED_BOOK_KEEP = 2;

    private static void capEnchantedBooks(final IColony colony)
    {
        final List<IItemHandler> shelves = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() > 0 && building.getTileEntity() != null
                  && (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse
                        || building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutEnchanter))
            {
                final IItemHandler racks = building.getItemHandlerCap();
                if (racks != null && racks.getSlots() > 0)
                {
                    shelves.add(racks);
                }
            }
        }
        if (shelves.isEmpty())
        {
            return;
        }
        record Copy(IItemHandler racks, int slot, int level, int count) {}
        final Map<Holder<Enchantment>, List<Copy>> byEnchant = new HashMap<>();
        for (final IItemHandler racks : shelves)
        {
            for (int slot = 0; slot < racks.getSlots(); slot++)
            {
                final ItemStack stack = racks.getStackInSlot(slot);
                if (!stack.is(Items.ENCHANTED_BOOK))
                {
                    continue;
                }
                final ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
                if (stored == null || stored.size() != 1)
                {
                    continue;
                }
                for (final var entry : stored.entrySet())
                {
                    byEnchant.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                      .add(new Copy(racks, slot, entry.getIntValue(), stack.getCount()));
                }
            }
        }
        int voided = 0;
        for (final List<Copy> copies : byEnchant.values())
        {
            int held = 0;
            for (final Copy copy : copies)
            {
                held += copy.count();
            }
            if (held <= ENCHANTED_BOOK_KEEP)
            {
                continue;
            }
            copies.sort(Comparator.comparingInt(Copy::level).reversed());
            int keep = ENCHANTED_BOOK_KEEP;
            for (final Copy copy : copies)
            {
                final int kept = Math.min(keep, copy.count());
                keep -= kept;
                final int surplus = copy.count() - kept;
                if (surplus > 0)
                {
                    voided += copy.racks().extractItem(copy.slot(), surplus, false).getCount();
                }
            }
        }
        if (voided > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] janitor thinned the spellbook shelves — {} surplus enchanted book(s) voided (at most {} of each enchantment stay, highest levels kept)",
              colony.getName(), voided, ENCHANTED_BOOK_KEEP);
        }
    }

    private static boolean capped(final ItemStack stack, final boolean goodsKept)
    {

        if (goodsKept && Exchange.familyValue(stack.getItem()) > 0 && !ItemStackUtils.ISFOOD.test(stack))
        {
            return false;
        }

        if (stack.is(ModItems.COLONY_BUCKS.get()))
        {
            return false;
        }

        return POTIONS.contains(stack.getItem())
                 || (stack.getMaxDamage() == 0 && stack.getMaxStackSize() > 1 && !VALUABLES.contains(stack.getItem()));
    }

    private static Object potionAwareKey(final ItemStack stack)
    {
        return POTIONS.contains(stack.getItem()) ? new ItemStorage(stack) : stack.getItem();
    }

    private static void tidyPockets(final IColony colony)
    {
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (!(citizen.getJob() instanceof AbstractJobStructure))
            {
                continue;
            }
            final IItemHandler pockets = citizen.getInventory();
            if (pockets == null || pockets.getSlots() == 0)
            {
                continue;
            }
            final Map<Item, Integer> needed = new HashMap<>();
            if (citizen.getWorkBuilding() instanceof AbstractBuildingStructureBuilder site)
            {
                for (final BuildingBuilderResource resource : site.getNeededResources().values())
                {
                    needed.merge(resource.getItemStack().getItem(), resource.getAmount(), Integer::sum);
                }
            }
            final Map<Item, Integer> kept = new HashMap<>();
            int cleared = 0;
            for (int slot = 0; slot < pockets.getSlots(); slot++)
            {
                final ItemStack stack = pockets.getStackInSlot(slot);
                if (stack.isEmpty() || stack.getMaxDamage() > 0)
                {
                    continue;
                }
                if (!POCKET_RUBBLE.contains(stack.getItem()))
                {
                    continue;
                }
                final int have = kept.getOrDefault(stack.getItem(), 0);
                if (have >= Math.max(POCKET_KEEP, needed.getOrDefault(stack.getItem(), 0)))
                {
                    cleared += pockets.extractItem(slot, stack.getCount(), false).getCount();
                }
                else
                {
                    kept.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
            if (cleared > 0)
            {
                ColonyAutopilot.LOGGER.debug("[{}] janitor emptied {} items of digging spoil from {}'s pockets — back to work",
                  colony.getName(), cleared, citizen.getName());
            }
        }
    }
}
