// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.MinimumStack;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.colony.buildings.workerbuildings.PostBox;
import com.minecolonies.core.colony.requestable.SmeltableOre;
import com.minecolonies.core.colony.requestsystem.requesters.IBuildingBasedRequester;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractCraftingProductionResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractCraftingRequestResolver;
import com.minecolonies.core.datalistener.model.Disease;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

public class ProvidenceSweep
{
    private static final int PERIOD_TICKS = 200;

    static final int CHAIN_HOPS = 32;

    static final Set<RequestState> CLOSED = EnumSet.of(RequestState.COMPLETED, RequestState.OVERRULED, RequestState.CANCELLED,
      RequestState.RECEIVED, RequestState.FINALIZING, RequestState.FAILED);

    private final WorkshopDirector workshops;

    ProvidenceSweep(final WorkshopDirector workshops)
    {
        this.workshops = workshops;
    }

    private int tickCounter = 21;

    private final ColonyRota rota = new ColonyRota();

    private final Map<ColonyId, Map<IToken<?>, Long>> firstSeen = new HashMap<>();

    private final Map<ColonyId, Set<IToken<?>>> unconjurable = new HashMap<>();

    private final Map<ColonyId, Map<IToken<?>, KindOff>> kindOff = new HashMap<>();

    private record KindOff(Category category, boolean progression) {}

    private final Set<ColonyId> pantryClosed = new HashSet<>();

    private final Map<ColonyId, Long> lastThreatBrace = new HashMap<>();

    private static final Map<ICitizenData, Long> ARRIVALS = new com.google.common.collect.MapMaker().weakKeys().makeMap();

    private final Map<ColonyId, Map<Integer, Long>> pantryLastFed = new HashMap<>();

    private final Map<ColonyId, Map<Integer, Long>> cureWaitLogged = new HashMap<>();

    private final Map<ColonyId, Set<IToken<?>>> leftOpen = new HashMap<>();

    private final Set<IToken<?>> reachedAbove = new HashSet<>();

    public static final String POSTBOX_LOCKED = "The Postbox is locked in Progression Mode: only the colony spends ColonyBucks.";

    public static final String MENU_LOCKED = "The menu is the colony's in Progression Mode: it keeps its own staples.";

    public static final String RESEARCH_COST_LOCKED =
      "The university's research is the colony's to buy in Progression Mode: only the colony spends ColonyBucks.";

    private static final String TWEAKS_CUSTOMIZABLE = "steve_gall.minecolonies_tweaks.api.common.requestsystem.CustomizableRequestable";
    private static final String TWEAKS_RESEARCH_COST = "steve_gall.minecolonies_tweaks.core.common.research.ResearchCost";
    private static final String TWEAKS_RESEARCH_COST_RESOLVER = "steve_gall.minecolonies_tweaks.core.common.research.ResearchCostResolver";

    private static final long FEED_COOLDOWN_TICKS = 2000L;

    private static final int FEED_BITE_LIMIT = 8;

    private static final double SATURATION_CAP = saturationCap();

    private static double saturationCap()
    {
        try
        {
            return CitizenConstants.class.getField("FULL_SATURATION").getDouble(null);
        }
        catch (final ReflectiveOperationException e)
        {
            return 20.0D;
        }
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        firstSeen.clear();
        unconjurable.clear();
        kindOff.clear();
        leftOpen.clear();
        pantryClosed.clear();
        pantryLastFed.clear();
        cureWaitLogged.clear();
        lastThreatBrace.clear();
        ARRIVALS.clear();
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {
            Treasury.clearWaitingRequests();
            return;
        }
        if (++tickCounter >= PERIOD_TICKS)
        {
            tickCounter = 0;
            final Set<ColonyId> live = rota.fill(event.getServer());
            ColonyAutopilot.retainColonies(live, firstSeen, unconjurable, kindOff, leftOpen, pantryLastFed, cureWaitLogged, lastThreatBrace);
            pantryClosed.retainAll(live);
            Treasury.retain(live);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {

            final boolean progression = AutopilotConfig.progressionMode(colony);
            if (progression)
            {

                try
                {
                    ensureRestaurantMenus(colony);
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("Menu upkeep failed for colony {}", colony.getName(), e);
                }
                try
                {
                    lockPostboxes(colony);
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("Postbox lock failed for colony {}", colony.getName(), e);
                }

                try
                {
                    if (!colony.getRaiderManager().isRaided())
                    {
                        Treasury.raidEnded(colony);
                    }
                }
                catch (final Exception e)
                {
                    ColonyAutopilot.LOGGER.warn("Raid pay notice failed for colony {}", colony.getName(), e);
                }
            }
            if (!AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_ENABLED))
            {

                Treasury.resetWaitingRequests(colony);
                Treasury.setWaitingCures(colony, false, 0);
                continue;
            }
            try
            {

                Treasury.pruneWaitingRequests(colony);
                sweep(colony);
                if (!progression && AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_RESTAURANT_MENUS))
                {
                    ensureRestaurantMenus(colony);
                }
                if (AutopilotConfig.get(colony, AutopilotConfig.PANTRY_ENABLED))
                {
                    feedHungryCitizens(colony);
                }
                if (AutopilotConfig.get(colony, AutopilotConfig.INFIRMARY_ENABLED))
                {
                    nurseSickCitizens(colony);
                }
                else
                {
                    Treasury.setWaitingCures(colony, false, 0);
                }
                if (AutopilotConfig.get(colony, AutopilotConfig.RECRUIT_VISITORS))
                {
                    recruitVisitor(colony);
                }
                if (AutopilotConfig.get(colony, AutopilotConfig.KEEP_VISITORS_COMING))
                {
                    keepVisitorsComing(colony);
                }
                final boolean bracing = threatForecast(colony);
                if (bracing)
                {
                    braceForThreat(colony);
                }
                stockWarehouseGoods(colony, bracing);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Providence sweep failed for colony {}", colony.getName(), e);
            }
        }
    }

    private void keepVisitorsComing(final IColony colony)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutTavern
                  && building.getBuildingLevel() > 0)
            {
                final TavernBuildingModule tavern = building.getModule(TavernBuildingModule.class);
                if (tavern != null)
                {
                    tavern.setNoVisitorTime(0);
                }
            }
        }
    }

    private boolean threatForecast(final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.ANTICIPATE_THREATS))
        {
            return false;
        }
        if (colony.getRaiderManager().willRaidTonight())
        {
            return true;
        }
        if (colony.getWorld() instanceof ServerLevel level)
        {
            final OptionalInt gap = SporeCompat.nearestEscalationGap(level.getServer());
            return gap.isPresent() && gap.getAsInt() <= ESCALATION_BRACE_MARGIN;
        }
        return false;
    }

    private static final int ESCALATION_BRACE_MARGIN = 5;

    private void braceForThreat(final IColony colony)
    {

        if (!(colony.getWorld() instanceof ServerLevel level) || !WorldUtil.isDayTime(level))
        {
            return;
        }

        final long day = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)
                           ? level.getDayTime() / 24000L : level.getGameTime() / 24000L;
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (lastThreatBrace.getOrDefault(key, Long.MIN_VALUE) == day)
        {
            return;
        }
        lastThreatBrace.put(key, day);

        GrowthDirector.grantAnticipationConversion(colony, level.getGameTime() / 24000L);

        Milestones.say(colony, "colonyautopilot.milestone.threatBrace");
        ColonyAutopilot.LOGGER.info("[{}] a fight is forecast for tonight — the village braces: the warehouse keeps double its combat potions and scrolls where an Alchemist or Enchanter can supply them, and the militia may rebalance once more",
          colony.getName());
    }

    private static final int COMBAT_POTION_KEEP = 4;
    private static java.util.List<ItemStack> combatPotions;

    private static java.util.List<ItemStack> combatPotions()
    {
        if (combatPotions == null)
        {
            combatPotions = java.util.List.of(
              PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING),
              PotionContents.createItemStack(Items.POTION, Potions.HEALING),
              PotionContents.createItemStack(Items.POTION, Potions.REGENERATION),
              PotionContents.createItemStack(Items.POTION, Potions.STRENGTH));
        }
        return combatPotions;
    }

    private void stockWarehouseGoods(final IColony colony, final boolean brace)
    {
        final boolean potions = AutopilotConfig.live(colony, AutopilotConfig.COMBAT_POTIONS);
        final boolean scrolls = AutopilotConfig.live(colony, AutopilotConfig.ENCHANTER_SCROLLS);
        if ((!potions && !scrolls) || !(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        IBuilding warehouse = null;
        boolean alchemist = false;
        int enchanterLevel = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0)
            {
                continue;
            }
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse)
            {
                warehouse = building;
            }
            else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutAlchemist)
            {
                alchemist = true;
            }
            else if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutEnchanter)
            {
                enchanterLevel = Math.max(enchanterLevel, building.getBuildingLevel());
            }
        }
        final boolean doPotions = potions && alchemist;
        final boolean doScrolls = scrolls && enchanterLevel > 0;
        if ((!doPotions && !doScrolls) || !(warehouse instanceof AbstractBuilding home))
        {
            return;
        }
        final IItemHandler racks = home.getItemHandlerCap();
        if (racks == null)
        {
            return;
        }

        final List<ItemStack> potionTemplates = doPotions ? combatPotions() : List.of();
        final int[] potionHave = new int[potionTemplates.size()];
        final int potionKeep = brace ? COMBAT_POTION_KEEP * 2 : COMBAT_POTION_KEEP;
        final Item[] scrollItems = {ModItems.scrollColonyTP, ModItems.scrollGuardHelp};
        final int[] scrollKeep = {brace ? SCROLL_TP_KEEP * 2 : SCROLL_TP_KEEP,
          enchanterLevel >= 3 ? (brace ? SCROLL_GUARD_HELP_KEEP * 2 : SCROLL_GUARD_HELP_KEEP) : 0};
        final int[] scrollHave = new int[scrollItems.length];
        for (int slot = 0; slot < racks.getSlots(); slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            for (int i = 0; i < potionTemplates.size(); i++)
            {
                if (ItemStack.isSameItemSameComponents(stack, potionTemplates.get(i)))
                {
                    potionHave[i] += stack.getCount();
                    break;
                }
            }
            if (doScrolls)
            {
                for (int i = 0; i < scrollItems.length; i++)
                {
                    if (stack.is(scrollItems[i]))
                    {
                        scrollHave[i] += stack.getCount();
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < potionTemplates.size(); i++)
        {
            if (potionHave[i] < potionKeep)
            {
                final ItemStack top = potionTemplates.get(i).copy();
                top.setCount(potionKeep - potionHave[i]);
                if (Treasury.canAfford(colony, level, Treasury.cost(top)))
                {
                    final int placed = top.getCount() - InventoryUtils.addItemStackToItemHandlerWithResult(racks, top).getCount();
                    Treasury.charge(colony, level, Treasury.cost(top.copyWithCount(placed)), Treasury.Line.GEAR);
                }
            }
        }
        if (doScrolls)
        {
            for (int i = 0; i < scrollItems.length; i++)
            {
                if (scrollHave[i] < scrollKeep[i])
                {
                    final ItemStack top = new ItemStack(scrollItems[i], scrollKeep[i] - scrollHave[i]);
                    if (Treasury.canAfford(colony, level, Treasury.cost(top)))
                    {
                        final int placed = top.getCount() - InventoryUtils.addItemStackToItemHandlerWithResult(racks, top).getCount();
                        Treasury.charge(colony, level, Treasury.cost(top.copyWithCount(placed)), Treasury.Line.GEAR);
                    }
                }
            }
        }
    }

    private static final int SCROLL_TP_KEEP = 4;
    private static final int SCROLL_GUARD_HELP_KEEP = 2;

    private void recruitVisitor(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final var citizenManager = colony.getCitizenManager();
        final int citizens = citizenManager.getCurrentCitizenCount();
        final int room = Math.min(citizenManager.getMaxCitizens(), (int) citizenManager.getPotentialMaxCitizens()) - citizens;
        if (room <= 0)
        {
            return;
        }
        if (!AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_RECRUITS))
        {
            return;
        }
        final int burstAt = AutopilotConfig.get(colony, AutopilotConfig.RECRUIT_BURST_BELOW_CAP);
        if (burstAt > 0 && room >= burstAt && !hasWillingVisitor(colony))
        {
            summonVisitor(colony);
        }
        for (final ICivilianData civilian : new ArrayList<>(colony.getVisitorManager().getCivilianDataMap().values()))
        {
            if (!(civilian instanceof IVisitorData visitor) || visitor.hasCustomTexture())
            {
                continue;
            }
            final ItemStack cost = visitor.getRecruitCost();
            if (cost == null || cost.isEmpty())
            {
                continue;
            }
            colony.getVisitorManager().removeCivilian(visitor);
            visitor.setHomeBuilding(null);
            visitor.setJob(null);
            final ICitizenData newCitizen = citizenManager.createAndRegisterCivilianData();
            newCitizen.deserializeNBT(level.registryAccess(), visitor.serializeNBT(level.registryAccess()));
            newCitizen.setParents("", "");
            newCitizen.setLastPosition(visitor.getLastPosition());
            newCitizen.updateEntityIfNecessary();
            visitor.getEntity().ifPresent(entity -> entity.remove(Entity.RemovalReason.DISCARDED));
            IMinecoloniesAPI.getInstance().getEventBus().post(
              new CitizenAddedModEvent(newCitizen, CitizenAddedModEvent.CitizenAddedSource.HIRED));

            ARRIVALS.put(newCitizen, level.getGameTime());
            ColonyAutopilot.LOGGER.info("[{}] welcomed the traveler {} as a citizen (recruit cost provided: {}x {})",
              colony.getName(), newCitizen.getName(), cost.getCount(), cost.getHoverName().getString());
            Milestones.say(colony, "colonyautopilot.milestone.recruit", newCitizen.getName());
            return;
        }
    }

    private static boolean hasWillingVisitor(final IColony colony)
    {
        for (final ICivilianData civilian : colony.getVisitorManager().getCivilianDataMap().values())
        {
            if (civilian instanceof IVisitorData visitor && !visitor.hasCustomTexture()
                  && visitor.getRecruitCost() != null && !visitor.getRecruitCost().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private static void summonVisitor(final IColony colony)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutTavern || building.getBuildingLevel() <= 0)
            {
                continue;
            }
            final TavernBuildingModule tavern = building.getModule(TavernBuildingModule.class);

            if (tavern == null || tavern.getExternalCitizens().size() >= 3 * building.getBuildingLevel())
            {
                continue;
            }
            if (tavern.spawnVisitor() != null)
            {
                return;
            }
        }
    }

    public static boolean diedOnArrival(final ICitizenData citizen)
    {

        final Long arrived = ARRIVALS.remove(citizen);
        return arrived != null && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(citizen.getColony(), AutopilotConfig.QUIET_ARRIVAL_DEATHS)
                 && citizen.getColony().getWorld().getGameTime() - arrived < 24000L;
    }

    public static void reclaimIssuedKit(final ICitizenData citizen, final IBuilding post)
    {

        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn()
              || !(citizen.getInventory() instanceof IItemHandlerModifiable inventory))
        {
            return;
        }
        final boolean paid = AutopilotConfig.progressionMode(citizen.getColony());
        if (paid && post == null)
        {
            return;
        }
        boolean issued = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            if (inventory.getStackInSlot(slot).getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
              net.minecraft.world.item.component.CustomData.EMPTY).contains(AutopilotConfig.ISSUED_GUN_TAG))
            {
                takeIssued(citizen, inventory, slot, paid ? post : null);
                issued = true;
            }
        }
        for (int slot = 0; issued && slot < inventory.getSlots(); slot++)
        {
            final var item = BuiltInRegistries.ITEM.getKey(inventory.getStackInSlot(slot).getItem());
            if (item.getNamespace().equals("tacz") && item.getPath().equals("ammo"))
            {
                takeIssued(citizen, inventory, slot, paid ? post : null);
            }
        }
    }

    private static void takeIssued(final ICitizenData citizen, final IItemHandlerModifiable inventory, final int slot, final IBuilding post)
    {
        final ItemStack taken = inventory.getStackInSlot(slot);
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        if (post == null)
        {
            return;
        }
        final IItemHandler racks = post.getItemHandlerCap();
        final ItemStack left = racks == null ? taken : InventoryUtils.addItemStackToItemHandlerWithResult(racks, taken);
        if (!left.isEmpty() && citizen.getColony().getWorld() instanceof ServerLevel level)
        {
            final net.minecraft.core.BlockPos at = citizen.getEntity().map(Entity::blockPosition).orElse(post.getPosition());
            net.minecraft.world.Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, left);
        }
    }

    private void feedHungryCitizens(final IColony colony)
    {
        if (colony.getWorld() == null)
        {
            return;
        }
        final boolean served = hasServingRestaurant(colony);
        final double hungerLine = served ? RESTAURANT_SAFETY_NET : CitizenConstants.LOW_SATURATION;

        if (served)
        {
            if (pantryClosed.add(ColonyAutopilot.colonyKey(colony)))
            {
                ColonyAutopilot.LOGGER.info("[{}] the pantry has closed — the restaurant feeds the village now", colony.getName());
            }
            if (!anyoneStarving(colony, hungerLine))
            {
                return;
            }
        }
        else if (pantryClosed.remove(ColonyAutopilot.colonyKey(colony)))
        {
            ColonyAutopilot.LOGGER.info("[{}] the pantry has reopened — the restaurant cannot serve right now", colony.getName());
        }
        final long now = colony.getWorld().getGameTime();
        final Map<Integer, Long> fedAt = pantryLastFed.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());

        final Set<Integer> livingIds = new HashSet<>();
        for (final ICitizenData resident : colony.getCitizenManager().getCitizens())
        {
            livingIds.add(resident.getId());
        }
        fedAt.keySet().retainAll(livingIds);
        int fed = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.getSaturation() > hungerLine)
            {
                continue;
            }
            final Long last = fedAt.get(citizen.getId());
            if (last != null && now - last < FEED_COOLDOWN_TICKS)
            {
                continue;
            }
            final var entity = citizen.getEntity().orElse(null);
            if (entity == null)
            {

                if (InventoryUtils.getItemCountInItemHandler(citizen.getInventory(), ItemStackUtils.ISFOOD) == 0
                      && InventoryUtils.addItemStackToItemHandlerWithResult(citizen.getInventory(), new ItemStack(Items.BREAD, 2)).getCount() < 2)
                {
                    fedAt.put(citizen.getId(), now);
                    ColonyAutopilot.LOGGER.debug("[{}] pantry gave bread to hungry {}", colony.getName(), citizen.getName());
                    if (++fed >= 2)
                    {
                        return;
                    }
                }
                continue;
            }
            boolean ownFood = true;
            int bites = 0;

            final double fillTo = served ? SATURATION_CAP : CitizenConstants.AVERAGE_SATURATION;
            while (citizen.getSaturation() < fillTo && bites < FEED_BITE_LIMIT)
            {
                final int slot = InventoryUtils.findFirstSlotInItemHandlerWith(citizen.getInventory(), ItemStackUtils.ISFOOD);
                if (bites == 0)
                {
                    ownFood = slot >= 0;
                }
                final ItemStack meal = slot >= 0 ? citizen.getInventory().getStackInSlot(slot) : new ItemStack(Items.BREAD);
                final double before = citizen.getSaturation();
                ItemStackUtils.consumeFood(meal, entity, null);
                bites++;
                if (citizen.getSaturation() <= before)
                {
                    break;
                }
            }
            if (bites > 0)
            {
                fedAt.put(citizen.getId(), now);
                ColonyAutopilot.LOGGER.debug("[{}] pantry fed hungry {} — {} bite{} of {}", colony.getName(), citizen.getName(),
                  bites, bites == 1 ? "" : "s", ownFood ? "their own provisions" : "pantry bread");
                if (++fed >= 2)
                {
                    return;
                }
            }
        }
    }

    private static final double RESTAURANT_SAFETY_NET = 2.0D;

    private static boolean anyoneStarving(final IColony colony, final double line)
    {
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.getSaturation() <= line)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasServingRestaurant(final IColony colony)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutCook && building.getBuildingLevel() > 0
                  && !building.getAllAssignedCitizen().isEmpty())
            {
                final RestaurantMenuModule menu = building.getModule(BuildingModules.RESTAURANT_MENU);
                if (menu == null || menu.getMenu().isEmpty() || building.getTileEntity() == null)
                {
                    continue;
                }
                final IItemHandler larder = building.getItemHandlerCap();
                if (larder == null)
                {
                    continue;
                }
                for (int slot = 0; slot < larder.getSlots(); slot++)
                {
                    final ItemStack stack = larder.getStackInSlot(slot);
                    if (!stack.isEmpty() && ItemStackUtils.ISFOOD.test(stack))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final List<Item> MENU_STAPLES = List.of(
      Items.BREAD, Items.BAKED_POTATO, Items.COOKED_COD, Items.COOKED_SALMON, Items.COOKED_BEEF,
      Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN, Items.COOKED_RABBIT);

    private static final Item MENU_BULK_STAPLE = Items.BREAD;

    private static int nutrition(final Item item)
    {
        final var food = item.getFoodProperties(new ItemStack(item), null);
        return food == null ? 0 : food.nutrition();
    }

    private static int menuNutritionFloor(final IColony colony)
    {
        int highestHome = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            final IBuilding home = citizen.getHomeBuilding();
            if (home != null)
            {
                highestHome = Math.max(highestHome, home.getBuildingLevelEquivalent());
            }
        }
        return highestHome >= 3 ? highestHome + 1 : 0;
    }

    private static void ensureRestaurantMenus(final IColony colony)
    {
        final int floor = menuNutritionFloor(colony);

        final List<Item> staples = new ArrayList<>(MENU_STAPLES);
        staples.sort(Comparator.comparingInt(item -> item == MENU_BULK_STAPLE ? 0 : nutrition(item) >= floor ? 1 : 2));
        final boolean locked = AutopilotConfig.progressionMode(colony);

        final Set<ItemStorage> stapleDishes = new HashSet<>();
        MENU_STAPLES.forEach(staple -> stapleDishes.add(new ItemStorage(new ItemStack(staple))));
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final boolean cook = building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutCook;

            final boolean nether = building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutNetherWorker;
            if ((!cook && !nether) || building.getBuildingLevel() <= 0)
            {
                continue;
            }
            final RestaurantMenuModule menu = building.getModule(cook ? BuildingModules.RESTAURANT_MENU : BuildingModules.NETHERMINER_MENU);
            if (menu == null)
            {
                continue;
            }
            final int cap = building.getBuildingLevel() * RestaurantMenuModule.STOCK_PER_LEVEL;
            if (locked)
            {

                final List<ItemStorage> foreign = new ArrayList<>();
                for (final ItemStorage dish : menu.getMenu())
                {
                    if (!stapleDishes.contains(dish))
                    {
                        foreign.add(dish);
                    }
                }
                foreign.forEach(dish -> menu.removeMenuItem(dish.getItemStack()));
                if (!foreign.isEmpty())
                {
                    ColonyAutopilot.LOGGER.info("[{}] took {} dish(es) that are not the colony's staples off {} menu — in Progression Mode the menu is the colony's",
                      colony.getName(), foreign.size(), cook ? "the restaurant's" : "the nether mine's");
                }
            }
            final int before = menu.getMenu().size();
            for (final Item staple : cook ? staples : MENU_STAPLES)
            {
                if (menu.getMenu().size() >= cap)
                {
                    break;
                }
                final ItemStack dish = new ItemStack(staple);

                if (!menu.getMenu().contains(new ItemStorage(dish)))
                {
                    menu.addMenuItem(dish);
                }
            }
            final int added = menu.getMenu().size() - before;
            if (added > 0)
            {
                ColonyAutopilot.LOGGER.info(cook
                    ? "[{}] put {} dishes on the restaurant's menu — the cook can serve now"
                    : "[{}] stocked {} trip rations on the nether mine's menu — expeditions travel fed",
                  colony.getName(), added);
            }

            int traded = 0;
            for (final Item refused : MENU_STAPLES)
            {
                if (!cook || menu.getMenu().size() > cap)
                {
                    break;
                }
                if (refused == MENU_BULK_STAPLE || nutrition(refused) >= floor || !menu.getMenu().contains(new ItemStorage(new ItemStack(refused))))
                {
                    continue;
                }
                Item better = null;
                for (final Item candidate : MENU_STAPLES)
                {
                    if (nutrition(candidate) >= floor && com.minecolonies.api.util.FoodUtils.EDIBLE.test(new ItemStack(candidate))
                          && !menu.getMenu().contains(new ItemStorage(new ItemStack(candidate))))
                    {
                        better = candidate;
                        break;
                    }
                }
                if (better == null)
                {
                    break;
                }
                menu.removeMenuItem(new ItemStack(refused));
                menu.addMenuItem(new ItemStack(better));
                if (!menu.getMenu().contains(new ItemStorage(new ItemStack(better))))
                {
                    menu.addMenuItem(new ItemStack(refused));
                    break;
                }
                traded++;
            }
            if (traded > 0)
            {
                ColonyAutopilot.LOGGER.info("[{}] traded {} dish(es) on a menu for ones its highest homes can eat (nutrition {} or more)",
                  colony.getName(), traded, floor);
            }
        }
    }

    private void nurseSickCitizens(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final long day = level.getGameTime() / 24000L;
        final Map<Integer, Long> logged = cureWaitLogged.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        int waiting = 0;

        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            final var entity = citizen.getEntity().orElse(null);
            if (entity == null || !entity.hasEffect(MobEffects.WITHER))
            {
                continue;
            }
            if (!Treasury.charge(colony, level, WITHER_CURE_CREDITS, Treasury.Line.CURES))
            {
                waiting++;
                if (!Long.valueOf(day).equals(logged.put(citizen.getId(), day)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the cure of {} for the Wither waits for funds ({} credits, {} needed: 1 Milk Bucket) — withering until the treasury can pay",
                      colony.getName(), citizen.getName(), Treasury.credits(colony), WITHER_CURE_CREDITS);
                }
                continue;
            }
            logged.remove(citizen.getId());
            entity.removeEffect(MobEffects.WITHER);
            ColonyAutopilot.LOGGER.info("[{}] the village nursed {} through the Wither (the cure it would have taken: 1 Milk Bucket){}",
              colony.getName(), citizen.getName(),
              AutopilotConfig.progressionMode(colony) ? " — the treasury paid " + WITHER_CURE_CREDITS + " credit" : "");
        }

        ICitizenData healer = null;
        final List<ICitizenData> sick = new ArrayList<>();
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.getEntity().isEmpty() || !citizen.getCitizenDiseaseHandler().isSick())
            {
                continue;
            }
            if (healer == null && citizen.getWorkBuilding() != null
                  && citizen.getWorkBuilding().getBuildingType().getBuildingBlock() == ModBlocks.blockHutHospital)
            {
                healer = citizen;
            }
            else
            {
                sick.add(citizen);
            }
        }
        if (healer != null)
        {
            sick.add(0, healer);
        }
        int nursed = 0;
        for (final ICitizenData patient : sick)
        {
            final Disease disease = patient.getCitizenDiseaseHandler().getDisease();
            final long price = disease == null ? 0L : Treasury.cureCost(disease);
            if (!Treasury.charge(colony, level, price, Treasury.Line.CURES))
            {
                waiting++;
                if (!Long.valueOf(day).equals(logged.put(patient.getId(), day)))
                {
                    ColonyAutopilot.LOGGER.info("[{}] the cure of {} for the {} waits for funds ({} credits, {} needed: {}) — sick until the treasury can pay",
                      colony.getName(), patient.getName(), disease.name().getString(), Treasury.credits(colony), price, disease.getCureString().getString());
                }
                continue;
            }
            logged.remove(patient.getId());
            patient.getCitizenDiseaseHandler().cure();
            ColonyAutopilot.LOGGER.info("[{}] the village nursed {} through the {} (the cure it would have taken: {}){}",
              colony.getName(), patient.getName(),
              disease == null ? "illness" : disease.name().getString(),
              disease == null ? "rest" : disease.getCureString().getString(),
              price > 0 && AutopilotConfig.progressionMode(colony) ? " — the treasury paid " + price + " credits" : "");
            if (++nursed >= NURSED_PER_SWEEP)
            {
                break;
            }
        }
        Treasury.setWaitingCures(colony, false, waiting);
    }

    private static final int NURSED_PER_SWEEP = 4;

    private static final long WITHER_CURE_CREDITS = 1L;

    private void sweep(final IColony colony)
    {
        reachedAbove.clear();
        if (colony.getWorld() == null)
        {
            return;
        }
        final IRequestManager manager = colony.getRequestManager();

        final List<IToken<?>> stranded = new ArrayList<>(manager.getPlayerResolver().getAllAssignedRequests());
        stranded.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());

        final Set<IToken<?>> strandedSet = new HashSet<>(stranded);
        final List<IToken<?>> parked = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            for (final Collection<IToken<?>> open : building.getOpenRequestsByRequestableType().values())
            {
                for (final IToken<?> token : open)
                {
                    if (!strandedSet.contains(token))
                    {
                        parked.add(token);
                    }
                }
            }
        }

        final Map<IToken<?>, Long> seenForColony = firstSeen.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>());
        strandedSet.addAll(parked);
        seenForColony.keySet().retainAll(strandedSet);
        final Set<IToken<?>> hopeless = unconjurable.get(ColonyAutopilot.colonyKey(colony));
        if (hopeless != null)
        {
            hopeless.retainAll(strandedSet);
        }
        final Map<IToken<?>, KindOff> switchedOff = kindOff.get(ColonyAutopilot.colonyKey(colony));
        if (switchedOff != null)
        {
            switchedOff.keySet().retainAll(strandedSet);
        }
        final Set<IToken<?>> logged = leftOpen.get(ColonyAutopilot.colonyKey(colony));
        if (logged != null)
        {
            logged.retainAll(strandedSet);
        }
        if (strandedSet.isEmpty())
        {
            return;
        }

        final long now = colony.getWorld().getGameTime();
        final long delayTicks = AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_DELAY_SECONDS) * 20L;
        final int smithTier = Quartermaster.smithTier(colony);

        int rescues = 0;
        for (final IToken<?> token : strandedSet)
        {
            if (rescues >= RESCUES_PER_SWEEP)
            {
                break;
            }
            final Long seen = seenForColony.putIfAbsent(token, now);
            if (seen == null || now - seen < RESCUE_AGE_TICKS)
            {
                continue;
            }
            final IRequest<?> request = manager.getRequestForToken(token);
            if (request == null || !(request.getRequest() instanceof IDeliverable deliverable))
            {
                continue;
            }
            final AbstractBuilding home;
            if (request.getRequester() instanceof IBuildingBasedRequester requester
                  && requester.getBuilding(manager, token).orElse(null) instanceof AbstractBuilding building)
            {
                home = building;
            }
            else
            {
                home = buildingAt(colony, request.getRequester().getLocation());
            }
            if (home == null)
            {
                continue;
            }
            final ItemStack inReach = findInReach(home, token, deliverable);
            if (inReach.isEmpty())
            {
                continue;
            }

            final MinimumStockModule stock = home.getModule(MinimumStockModule.class);
            if (stock != null && stock.isStocked(inReach))
            {
                continue;
            }

            if (ItemStackUtils.ISFOOD.test(inReach))
            {
                continue;
            }
            try
            {
                manager.overruleRequest(token, inReach);
            }
            catch (final IllegalArgumentException e)
            {
                continue;
            }
            seenForColony.remove(token);
            rescues++;
            ColonyAutopilot.LOGGER.debug("[{}] a request for {} sat already answered in the {}'s own stores — paperwork closed, work resumes",
              colony.getName(), inReach.getHoverName().getString(), home.getBuildingDisplayName());
        }

        final Map<IToken<?>, IRequest<?>> resolved = new HashMap<>();
        for (final IToken<?> token : strandedSet)
        {
            final IRequest<?> request = manager.getRequestForToken(token);
            if (request != null)
            {
                resolved.put(token, request);
            }
        }
        List<ItemStack> warehouseStock = null;

        final boolean couriers = hasCouriers(colony);

        for (final IToken<?> token : strandedSet)
        {
            final IRequest<?> request = resolved.get(token);
            if (request == null || !(request.getRequest() instanceof IDeliverable deliverable))
            {
                continue;
            }
            final AbstractBuilding home;
            if (request.getRequester() instanceof IBuildingBasedRequester requester
                  && requester.getBuilding(manager, token).orElse(null) instanceof AbstractBuilding building)
            {
                home = building;
            }
            else
            {
                home = buildingAt(colony, request.getRequester().getLocation());
            }
            if (home instanceof AbstractBuildingGuards)
            {

                if (warehouseStock == null)
                {
                    warehouseStock = warehouseStacks(colony);
                }

                if ((craftingOn(manager, token) && !(deliverable instanceof Tool))
                      || (couriers && (inFlight(request) || holds(warehouseStock, deliverable))))
                {
                    continue;
                }
                provide(colony, manager, token, seenForColony, now, smithTier);
            }
        }

        for (final IToken<?> token : strandedSet)
        {
            final IRequest<?> request = resolved.get(token);
            if (request == null || !(request.getRequest() instanceof Tool tool))
            {
                continue;
            }
            if (smithTier > 0)
            {
                final Long seen = seenForColony.putIfAbsent(token, now);
                if (seen == null || now - seen < delayTicks)
                {
                    continue;
                }
            }
            if (warehouseStock == null)
            {
                warehouseStock = warehouseStacks(colony);
            }

            if (couriers && (inFlight(request) || holds(warehouseStock, tool)))
            {
                continue;
            }
            provide(colony, manager, token, seenForColony, now, smithTier);
        }

        int provided = 0;
        for (final IToken<?> token : stranded)
        {
            final Long seen = seenForColony.putIfAbsent(token, now);
            if (seen == null || now - seen < delayTicks)
            {
                continue;
            }
            if (provide(colony, manager, token, seenForColony, now, smithTier) && ++provided >= AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_ITEMS_PER_SWEEP))
            {
                return;
            }
        }

        for (final IToken<?> token : parked)
        {
            final Long seen = seenForColony.putIfAbsent(token, now);
            if (seen == null || now - seen < delayTicks * 2)
            {
                continue;
            }
            final IRequestResolver<?> resolver;
            try
            {
                resolver = manager.getResolverForRequest(token);
            }
            catch (final IllegalArgumentException e)
            {
                continue;
            }
            final IRequest<?> request = manager.getRequestForToken(token);
            if (resolver instanceof AbstractCraftingRequestResolver)
            {

                if ((request == null || !(request.getRequest() instanceof Tool)) && !reachedAbove.contains(token))
                {
                    Treasury.forgetWaitingRequest(colony, token);
                }
                continue;
            }
            if (request == null || !(request.getRequest() instanceof IDeliverable deliverable))
            {
                continue;
            }
            if (warehouseStock == null)
            {
                warehouseStock = warehouseStacks(colony);
            }

            if (couriers && ((AutopilotConfig.progressionMode(colony) && inFlight(request)) || holds(warehouseStock, deliverable)))
            {
                continue;
            }

            final AbstractBuilding requester;
            if (request.getRequester() instanceof IBuildingBasedRequester based
                  && based.getBuilding(manager, token).orElse(null) instanceof AbstractBuilding rb)
            {
                requester = rb;
            }
            else
            {
                requester = buildingAt(colony, request.getRequester().getLocation());
            }
            if (requester instanceof BuildingBuilder && buildingHolds(requester, deliverable))
            {
                continue;
            }
            if (provide(colony, manager, token, seenForColony, now, smithTier) && ++provided >= AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_ITEMS_PER_SWEEP))
            {
                return;
            }
        }
    }

    private static Treasury.Line ledgerLine(final Category category, final ItemStack stack)
    {
        final net.minecraft.resources.ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals("tacz") && id.getPath().equals("ammo"))
        {
            return Treasury.Line.ROUNDS;
        }
        return switch (category)
        {
            case GUARD_GEAR, ARMOUR_WEAPONS -> Treasury.Line.GEAR;
            case TOOLS -> Treasury.Line.TOOLS;
            case BUILD_MATERIALS -> Treasury.Line.CONSTRUCTION;
            default -> Treasury.Line.SUPPLY;
        };
    }

    enum Category
    {
        FOOD(AutopilotConfig.PROVIDENCE_FOOD), TOOLS(AutopilotConfig.PROVIDENCE_TOOLS), ARMOUR_WEAPONS(AutopilotConfig.PROVIDENCE_ARMOUR_WEAPONS),
        BUILD_MATERIALS(AutopilotConfig.PROVIDENCE_BUILD_MATERIALS), GUARD_GEAR(AutopilotConfig.PROVIDENCE_GUARD_GEAR), OTHER(null);

        private final net.neoforged.neoforge.common.ModConfigSpec.BooleanValue gate;

        Category(final net.neoforged.neoforge.common.ModConfigSpec.BooleanValue gate)
        {
            this.gate = gate;
        }

        boolean allowed(final IColony colony)
        {
            return gate == null || AutopilotConfig.get(colony, gate);
        }
    }

    static Category categoryOf(final IBuilding home, final IDeliverable deliverable, final ItemStack sample)
    {
        if (home instanceof AbstractBuildingGuards)
        {
            return Category.GUARD_GEAR;
        }
        if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Food)
        {
            return Category.FOOD;
        }
        if (deliverable instanceof Tool)
        {
            return Category.TOOLS;
        }
        final Item item = sample == null || sample.isEmpty() ? null : sample.getItem();
        if (item instanceof ArmorItem || item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.BowItem
              || item instanceof net.minecraft.world.item.CrossbowItem || item instanceof net.minecraft.world.item.ShieldItem
              || item instanceof net.minecraft.world.item.TridentItem)
        {
            return Category.ARMOUR_WEAPONS;
        }
        if (item != null && ItemStackUtils.ISFOOD.test(sample))
        {
            return Category.FOOD;
        }
        if (home instanceof BuildingBuilder)
        {
            return Category.BUILD_MATERIALS;
        }
        return Category.OTHER;
    }

    private boolean provide(
      final IColony colony,
      final IRequestManager manager,
      final IToken<?> token,
      final Map<IToken<?>, Long> seenForColony,
      final long now,
      final int smithTier)
    {
        final IRequest<?> request = manager.getRequestForToken(token);
        if (request == null || !(request.getRequest() instanceof IDeliverable deliverable))
        {
            seenForColony.remove(token);
            return false;
        }

        if (CLOSED.contains(request.getState()))
        {
            seenForColony.remove(token);
            return false;
        }

        final AbstractBuilding home;
        boolean viaWorker = false;

        if (request.getRequester() instanceof IBuildingBasedRequester requester
              && requester.getBuilding(manager, token).orElse(null) instanceof AbstractBuilding building)
        {
            home = building;
            viaWorker = true;
        }
        else
        {
            home = buildingAt(colony, request.getRequester().getLocation());
            if (home == null || home.getTileEntity() == null)
            {
                ColonyAutopilot.LOGGER.info("[{}] providence cannot physically place request {} ({} at {}) — leaving it open",
                  colony.getName(), token, request.getRequester().getClass().getSimpleName(),
                  request.getRequester().getLocation().getInDimensionLocation().toShortString());
                seenForColony.put(token, now);
                return false;
            }
        }

        final Set<IToken<?>> hopeless = unconjurable.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashSet<>());
        if (hopeless.contains(token))
        {
            return false;
        }

        Predicate<Item> pickable = null;

        if (AutopilotConfig.progressionMode(colony))
        {
            final String players = playersOwn(colony, manager, request);
            if (players != null)
            {
                return leaveOpen(colony, token, request, players);
            }

            if (deliverable instanceof SmeltableOre
                  || (deliverable instanceof StackList list && RequestSystemTranslationConstants.REQUESTS_TYPE_SMELTABLE_ORE.equals(list.getDescription())))
            {
                return leaveOpen(colony, token, request, "the smelter's ore waits for the colony's own miner");
            }

            final Predicate<Item> own = deliverable instanceof StackList list ? colonysOwnList(list) : null;
            if (own != null && ((StackList) deliverable).getStacks().stream().noneMatch(listed -> own.test(listed.getItem())))
            {
                return leaveOpen(colony, token, request, "a list a player set, and the colony conjures only from its own list");
            }
            pickable = own != null ? own : item -> true;
        }

        final Treasury.Refusal refusal = AutopilotConfig.progressionMode(colony) ? Treasury.refusal(colony, token) : null;
        if (refusal != null && !refusal.category().allowed(colony))
        {
            Treasury.forgetWaitingRequest(colony, token);
            return false;
        }
        if (refusal != null && refusal.credits() >= Treasury.spendable(colony))
        {
            return false;
        }

        final Map<IToken<?>, KindOff> switchedOff = kindOff.get(ColonyAutopilot.colonyKey(colony));
        final KindOff off = switchedOff == null ? null : switchedOff.get(token);
        if (off != null)
        {
            if (off.progression() == AutopilotConfig.progressionMode(colony) && !off.category().allowed(colony))
            {
                Treasury.forgetWaitingRequest(colony, token);
                return false;
            }
            switchedOff.remove(token);
        }
        final ItemStack stack = conjureStackFor(request, deliverable, smithTier, pickable);
        if (stack.isEmpty())
        {
            ColonyAutopilot.LOGGER.debug("[{}] providence found nothing conjurable for request {}", colony.getName(), token);
            hopeless.add(token);

            Treasury.forgetWaitingRequest(colony, token);
            return false;
        }

        if (AutopilotConfig.progressionMode(colony))
        {
            if (Treasury.holdsItems(stack))
            {
                return leaveOpen(colony, token, request, "it holds items, and a filled container is never conjured in Progression Mode");
            }
            if (Treasury.neverConjured(stack))
            {
                return leaveOpen(colony, token, request, "spawners, vaults, trial keys, spawn eggs and Mystical Agriculture's seeds are never conjured in Progression Mode");
            }

            if (Exchange.familyValue(stack.getItem()) > 0 && servedInstead(manager, request, stack, smithTier) instanceof IRequest<?> above)
            {

                reachedAbove.add(above.getId());
                if (provide(colony, manager, above.getId(), seenForColony, now, smithTier))
                {
                    ColonyAutopilot.LOGGER.debug("[{}] the providence serves the request {} ({}) itself rather than buy {} for it — {}",
                      colony.getName(), above.getId(), above.getShortDisplayString().getString(), stack.getHoverName().getString(),
                      ItemStackUtils.ISFOOD.test(above.getDisplayStacks().get(0)) ? "the colony's food is free" : "it costs less than what it is made of");
                    return true;
                }
                return leaveOpen(colony, token, request,
                  "the request it is made for is not served now (its kind switched off, no room, or waiting for funds), and the colony never buys the exchange's goods where serving that request costs less");
            }
        }

        final Category category = categoryOf(home, deliverable, stack);
        if (!category.allowed(colony))
        {

            Treasury.forgetWaitingRequest(colony, token);
            kindOff.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>())
              .put(token, new KindOff(category, AutopilotConfig.progressionMode(colony)));
            return false;
        }

        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return false;
        }

        final boolean metered = (category != Category.FOOD && !ItemStackUtils.ISFOOD.test(stack)) || Exchange.familyValue(stack.getItem()) > 0;
        if (metered && !Treasury.canAfford(colony, level, Treasury.cost(stack)))
        {
            Treasury.noteWaitingRequest(colony, token, category, stack);

            final long spendable = Math.max(0L, Treasury.spendable(colony));
            final long reserved = Treasury.reservedCredits(colony);
            ColonyAutopilot.LOGGER.debug("[{}] the providence holds {}x {} for funds ({})", colony.getName(), stack.getCount(), stack.getHoverName().getString(),
              reserved > 0 ? spendable + " credits spendable, " + reserved + " held for research '" + Treasury.reservedFor(colony) + "'; " + Treasury.cost(stack) + " needed"
                           : spendable + " credits, " + Treasury.cost(stack) + " needed");
            return false;
        }
        Treasury.forgetWaitingRequest(colony, token);

        String asker;
        String where;
        try
        {
            asker = request.getRequester().getRequesterDisplayName(manager, request).getString();
            where = request.getRequester().getLocation().getInDimensionLocation().toShortString();
        }
        catch (final RuntimeException e)
        {
            asker = "?";
            where = "?";
        }

        if (!(deliverable instanceof Tool))
        {
            workshops.teachCrafterFor(colony, stack);
        }

        ItemStack remainder = stack.copy();
        if (viaWorker)
        {
            final Optional<ICitizenData> citizen = home.getCitizenForRequest(token);
            if (citizen.isPresent())
            {
                remainder = InventoryUtils.addItemStackToItemHandlerWithResult(citizen.get().getInventory(), remainder);
            }
        }
        if (!remainder.isEmpty() && home.getTileEntity() != null)
        {
            remainder = InventoryUtils.addItemStackToItemHandlerWithResult(home.getItemHandlerCap(), remainder);
        }
        if (remainder.getCount() >= stack.getCount())
        {
            return false;
        }

        final int placed = stack.getCount() - remainder.getCount();
        if (metered)
        {
            Treasury.charge(colony, level, Treasury.cost(stack.copyWithCount(placed)), ledgerLine(category, stack));
        }
        try
        {
            manager.overruleRequest(token, stack.copyWithCount(placed));
        }
        catch (final IllegalArgumentException e)
        {

        }
        seenForColony.remove(token);

        ColonyAutopilot.LOGGER.debug("[{}] providence delivered {}x {}{} to {} at {} (request {})",
          colony.getName(), placed, stack.getHoverName().getString(), placed < stack.getCount() ? " (racks full for the rest)" : "",
          asker, where, token);
        return true;
    }

    private static IRequest<?> servedInstead(final IRequestManager manager, final IRequest<?> request, final ItemStack stack, final int smithTier)
    {
        final long price = Treasury.cost(stack);
        IRequest<?> at = request.getParent() == null ? null : manager.getRequestForToken(request.getParent());
        for (int hop = 0; at != null && hop < CHAIN_HOPS; hop++)
        {
            if (at.getRequest() instanceof IDeliverable deliverable && !at.getDisplayStacks().isEmpty())
            {
                if (ItemStackUtils.ISFOOD.test(at.getDisplayStacks().get(0)))
                {
                    return at;
                }
                final Predicate<Item> own = deliverable instanceof StackList list ? colonysOwnList(list) : null;
                final ItemStack instead = conjureStackFor(at, deliverable, smithTier, own != null ? own : item -> true);
                if (!instead.isEmpty() && Treasury.cost(instead) < price)
                {
                    return at;
                }
            }
            at = at.getParent() == null ? null : manager.getRequestForToken(at.getParent());
        }
        return null;
    }

    private boolean leaveOpen(final IColony colony, final IToken<?> token, final IRequest<?> request, final String why)
    {
        Treasury.forgetWaitingRequest(colony, token);
        if (leftOpen.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashSet<>()).add(token))
        {
            ColonyAutopilot.LOGGER.debug("[{}] the providence leaves {} (request {}) to the colony's own economy — {}",
              colony.getName(), request.getShortDisplayString().getString(), token, why);
        }
        return false;
    }

    private static Predicate<Item> colonysOwnList(final StackList list)
    {
        final String kind = list.getDescription();
        if (RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE.equals(kind))
        {
            return item -> item == Items.COAL || item == Items.CHARCOAL;
        }
        final boolean compost = RequestSystemTranslationConstants.REQUESTS_TYPE_COMPOSTABLE.equals(kind);
        if (!compost && !RequestSystemTranslationConstants.REQUEST_TYPE_FLOWERS.equals(kind))
        {
            return null;
        }
        final Set<ItemStorage> seeded = compost ? IColonyManager.getInstance().getCompatibilityManager().getCompostInputs()
                                          : IColonyManager.getInstance().getCompatibilityManager().getImmutableFlowers();

        return item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft") && new ItemStack(item).getFoodProperties(null) == null
                         && seeded.contains(new ItemStorage(new ItemStack(item)));
    }

    private static String playersOwn(final IColony colony, final IRequestManager manager, final IRequest<?> request)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return null;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        IRequest<?> at = request;

        for (int hop = 0; at != null && hop < CHAIN_HOPS; hop++)
        {

            if (tweaksResearchCost(at))
            {
                return "a research's cost a player ordered at the university, and only the colony spends ColonyBucks";
            }
            final IBuilding asker = at.getRequester() instanceof IBuildingBasedRequester based
                                      && based.getBuilding(manager, at.getId()).orElse(null) instanceof IBuilding building
                                      ? building : buildingAt(colony, at.getRequester().getLocation());

            if (asker instanceof PostBox)
            {
                return "the Postbox is locked in Progression Mode";
            }

            if (asker != null && at.getRequest() instanceof MinimumStack line)
            {
                if (asker.getModule(MinimumStockModule.class) instanceof MinimumStockModule stock && stock.isStocked(line.getStack()))
                {
                    if (!autopilotsLine(grounds, colony.getID(), asker, stock, line.getStack()))
                    {
                        return "a stock line the autopilot did not set, and only the colony spends ColonyBucks";
                    }
                }
                else if (!onMenu(asker, line.getStack()))
                {
                    return "a stock line removed after it asked, and only the colony spends ColonyBucks";
                }
            }

            if (asker instanceof BuildingFarmer && at.getRequest() instanceof Stack seed && !(at.getRequester() instanceof AbstractCraftingProductionResolver<?>)
                  && !FarmFields.sows(seed.getStack().getItem()))
            {
                return "a seed the autopilot does not sow, and only the colony spends ColonyBucks";
            }
            at = at.getParent() == null ? null : manager.getRequestForToken(at.getParent());
        }
        return null;
    }

    private static boolean onMenu(final IBuilding building, final ItemStack item)
    {
        if (!(building.getModule(RestaurantMenuModule.class) instanceof RestaurantMenuModule menu))
        {
            return false;
        }
        final boolean cooks = building.getModule(BuildingModules.RESTAURANT_MENU) != null;
        for (final ItemStorage dish : menu.getMenu())
        {
            if (dish.getItem() == item.getItem())
            {
                return true;
            }
            if (cooks && IMinecoloniesAPI.getInstance().getFurnaceRecipes().getFirstSmeltingRecipeByResult(dish) instanceof IRecipeStorage recipe
                  && !recipe.getInput().isEmpty() && recipe.getInput().get(0).getItem() == item.getItem())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean orphanLine(final IBuilding building, final ItemStack item)
    {
        return !(building.getModule(MinimumStockModule.class) instanceof MinimumStockModule stock && stock.isStocked(item)) && !onMenu(building, item);
    }

    private static boolean autopilotsLine(final ColonyGrounds grounds, final int colonyId, final IBuilding building, final MinimumStockModule stock,
      final ItemStack item)
    {
        final int recorded = grounds.stockLine(colonyId, building.getPosition(), item.getItem());
        if (recorded <= 0)
        {
            return recorded == 0;
        }

        final int[] kept = {0};
        stock.alterItemsToBeKept((matches, amount, tight) -> {
            if (matches.test(item))
            {
                kept[0] = amount;
            }
        });
        return kept[0] == recorded * item.getMaxStackSize();
    }

    static boolean tweaksResearchCost(final IRequest<?> request)
    {
        if (TWEAKS_RESEARCH_COST_RESOLVER.equals(request.getRequester().getClass().getName()))
        {
            return true;
        }
        final Object requestable = request.getRequest();
        if (!TWEAKS_CUSTOMIZABLE.equals(requestable.getClass().getName()))
        {
            return false;
        }
        try
        {
            final Object object = requestable.getClass().getMethod("getObject").invoke(requestable);
            return object != null && TWEAKS_RESEARCH_COST.equals(object.getClass().getName());
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    public static boolean postboxLocked(final IColony colony)
    {
        return colony != null && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn() && AutopilotConfig.progressionMode(colony);
    }

    private static void lockPostboxes(final IColony colony)
    {
        final IRequestManager manager = colony.getRequestManager();
        final List<IToken<?>> orders = new ArrayList<>();
        final List<IToken<?>> costs = new ArrayList<>();
        final List<IToken<?>> orphans = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            for (final IToken<?> token : building.getOpenRequestsByRequestableType().getOrDefault(TypeToken.of(MinimumStack.class), List.of()))
            {
                if (manager.getRequestForToken(token) instanceof IRequest<?> request && request.getRequest() instanceof MinimumStack line
                      && orphanLine(building, line.getStack()))
                {
                    orphans.add(token);
                }
            }

            if (building instanceof PostBox)
            {
                building.getOpenRequestsByRequestableType().values().forEach(orders::addAll);
            }
            else if (building instanceof BuildingUniversity)
            {
                for (final Collection<IToken<?>> open : building.getOpenRequestsByRequestableType().values())
                {
                    for (final IToken<?> token : open)
                    {
                        if (manager.getRequestForToken(token) instanceof IRequest<?> request && tweaksResearchCost(request))
                        {
                            costs.add(token);
                        }
                    }
                }
            }
        }
        final int cancelled = cancelOpen(manager, orders);
        if (cancelled > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the Postbox is locked in Progression Mode — {} open order(s) cancelled", colony.getName(), cancelled);
        }
        final int costsCancelled = cancelOpen(manager, costs);
        if (costsCancelled > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] a research's cost is the colony's to buy in Progression Mode — {} research-cost order(s) at the university cancelled",
              colony.getName(), costsCancelled);
        }
        final int orphansCancelled = cancelOpen(manager, orphans);
        if (orphansCancelled > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] {} request(s) left open by a removed stock line or menu dish cancelled — nothing asks for them any more",
              colony.getName(), orphansCancelled);
        }
    }

    private static int cancelOpen(final IRequestManager manager, final List<IToken<?>> tokens)
    {
        int cancelled = 0;
        for (final IToken<?> token : tokens)
        {
            final IRequest<?> request = manager.getRequestForToken(token);
            if (request != null && !CLOSED.contains(request.getState()))
            {
                manager.updateRequestState(token, RequestState.CANCELLED);
                cancelled++;
            }
        }
        return cancelled;
    }

    static void addStockLine(final IBuilding building, final MinimumStockModule stock, final ItemStack stack, final int stacks)
    {
        stock.addMinimumStock(stack, stacks);
        recordStockLine(building, stock, stack, stacks);
    }

    static void recordStockLine(final IBuilding building, final MinimumStockModule stock, final ItemStack stack, final int stacks)
    {
        if (stock.isStocked(stack) && building.getColony() != null && building.getColony().getWorld() instanceof ServerLevel level)
        {
            ColonyGrounds.get(level).setStockLine(building.getColony().getID(), building.getPosition(), stack.getItem(), stacks);
        }
    }

    static void removeStockLine(final IBuilding building, final MinimumStockModule stock, final ItemStack stack)
    {
        stock.removeMinimumStock(stack);
        if (building.getColony() != null && building.getColony().getWorld() instanceof ServerLevel level)
        {
            ColonyGrounds.get(level).setStockLine(building.getColony().getID(), building.getPosition(), stack.getItem(), 0);
        }
    }

    private static final long RESCUE_AGE_TICKS = 1200L;

    private static final int RESCUES_PER_SWEEP = 8;

    private static ItemStack findInReach(final AbstractBuilding home, final IToken<?> token, final IDeliverable deliverable)
    {
        final List<IItemHandler> reach = new ArrayList<>();
        home.getCitizenForRequest(token).ifPresent(citizen -> {
            if (citizen.getInventory() != null)
            {
                reach.add(citizen.getInventory());
            }
        });
        if (home.getItemHandlerCap() != null)
        {
            reach.add(home.getItemHandlerCap());
        }
        ItemStack first = ItemStack.EMPTY;
        int held = 0;
        for (final IItemHandler handler : reach)
        {
            for (int slot = 0; slot < handler.getSlots(); slot++)
            {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && deliverable.matches(stack))
                {
                    if (first.isEmpty())
                    {
                        first = stack;
                    }
                    held += stack.getCount();
                }
            }
        }

        if (first.isEmpty() || held < Math.max(1, deliverable.getMinimumCount()))
        {
            return ItemStack.EMPTY;
        }
        final ItemStack copy = first.copy();
        copy.setCount(Math.min(deliverable.getCount(), first.getCount()));
        return copy;
    }

    private static AbstractBuilding buildingAt(final IColony colony, final ILocation location)
    {
        if (location == null || !colony.getDimension().equals(location.getDimension()))
        {
            return null;
        }
        return colony.getServerBuildingManager().getBuilding(location.getInDimensionLocation()) instanceof AbstractBuilding building
                 ? building : null;
    }

    static List<ItemStack> warehouseStacks(final IColony colony)
    {
        final List<ItemStack> stock = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutWareHouse
                  || building.getBuildingLevel() <= 0 || building.getTileEntity() == null)
            {
                continue;
            }
            final IItemHandler racks = building.getItemHandlerCap();
            if (racks == null)
            {
                continue;
            }
            for (int slot = 0; slot < racks.getSlots(); slot++)
            {
                final ItemStack stack = racks.getStackInSlot(slot);
                if (!stack.isEmpty())
                {
                    stock.add(stack);
                }
            }
        }
        return stock;
    }

    private static boolean holds(final List<ItemStack> warehouseStock, final IDeliverable deliverable)
    {
        for (final ItemStack stack : warehouseStock)
        {
            if (deliverable.matches(stack))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean inFlight(final IRequest<?> request)
    {
        return (request.getState() == RequestState.FOLLOWUP_IN_PROGRESS || request.getState() == RequestState.RESOLVED)
                 && request.hasChildren();
    }

    static List<ItemStack> courierStacks(final IColony colony)
    {
        final List<ItemStack> carried = new ArrayList<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutWareHouse || building.getBuildingLevel() <= 0)
            {
                continue;
            }
            for (final ICitizenData courier : building.getModule(BuildingModules.WAREHOUSE_COURIERS).getAssignedCitizen())
            {
                final IItemHandler pockets = courier.getInventory();
                for (int slot = 0; pockets != null && slot < pockets.getSlots(); slot++)
                {
                    final ItemStack stack = pockets.getStackInSlot(slot);
                    if (!stack.isEmpty())
                    {
                        carried.add(stack);
                    }
                }
            }
        }
        return carried;
    }

    static boolean hasCouriers(final IColony colony)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse && building.getBuildingLevel() > 0
                  && !building.getModule(BuildingModules.WAREHOUSE_COURIERS).getAssignedCitizen().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean craftingOn(final IRequestManager manager, final IToken<?> token)
    {
        try
        {
            return manager.getResolverForRequest(token) instanceof AbstractCraftingRequestResolver;
        }
        catch (final IllegalArgumentException e)
        {
            return false;
        }
    }

    private static boolean buildingHolds(final IBuilding building, final IDeliverable deliverable)
    {
        final IItemHandler racks = building.getItemHandlerCap();
        if (racks == null)
        {
            return false;
        }
        for (int slot = 0; slot < racks.getSlots(); slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (!stack.isEmpty() && deliverable.matches(stack))
            {
                return true;
            }
        }
        return false;
    }

    private static ItemStack conjureStackFor(final IRequest<?> request, final IDeliverable deliverable, final int smithTier, final Predicate<Item> pickable)
    {

        final int tierCap = smithTier == 2 ? 1561 : smithTier == 1 ? 500 : 150;

        final int armorCap = smithTier == 2 ? 528 : smithTier == 1 ? 250 : 150;
        ItemStack match = ItemStack.EMPTY;
        if (pickable != null && deliverable instanceof StackList list)
        {
            long cheapest = Long.MAX_VALUE;
            for (final ItemStack listed : list.getStacks())
            {
                final ItemStack candidate = new ItemStack(listed.getItem());
                if (!pickable.test(candidate.getItem()) || !deliverable.matches(candidate) || Treasury.isCurrency(candidate))
                {
                    continue;
                }
                final long cost = Treasury.cost(candidate);
                if (cost < cheapest || (cost == cheapest && BuiltInRegistries.ITEM.getId(candidate.getItem()) < BuiltInRegistries.ITEM.getId(match.getItem())))
                {
                    match = candidate;
                    cheapest = cost;
                }
            }
        }

        if (match.isEmpty())
        {
            for (final Item item : BuiltInRegistries.ITEM)
            {
                final ItemStack candidate = new ItemStack(item);

                if ((pickable == null || pickable.test(item)) && deliverable.matches(candidate) && !Treasury.isCurrency(candidate))
                {
                    match = candidate;
                    break;
                }
            }
        }
        if (match.isEmpty())
        {
            for (final ItemStack display : request.getDisplayStacks())
            {
                if (pickable == null || pickable.test(display.getItem()))
                {
                    match = display.copy();
                    break;
                }
            }
            if (match.isEmpty())
            {
                return ItemStack.EMPTY;
            }
        }

        if (BuiltInRegistries.ITEM.getKey(match.getItem()).getNamespace().equals("domum_ornamentum"))
        {
            ItemStack textured = ItemStack.EMPTY;
            for (final ItemStack display : request.getDisplayStacks())
            {
                if (deliverable.matches(display))
                {
                    textured = display.copy();
                    break;
                }
            }
            if (textured.isEmpty())
            {
                return ItemStack.EMPTY;
            }
            match = textured;
        }
        if (match.getMaxDamage() > 0)
        {
            ItemStack gear = ItemStack.EMPTY;
            for (final Item item : gearLadder())
            {
                final ItemStack candidate = new ItemStack(item);
                final int cap = item instanceof ArmorItem ? armorCap : tierCap;
                if (candidate.getMaxDamage() <= cap && deliverable.matches(candidate))
                {
                    gear = candidate;
                    break;
                }
            }

            if (gear.isEmpty())
            {
                final List<Item> ladder = gearLadder();
                for (int i = ladder.size() - 1; i >= 0; i--)
                {
                    final ItemStack candidate = new ItemStack(ladder.get(i));
                    if (deliverable.matches(candidate))
                    {
                        gear = candidate;
                        break;
                    }
                }
            }

            if (gear.isEmpty())
            {
                return ItemStack.EMPTY;
            }
            match = gear;
        }
        else if (deliverable instanceof Tool && !BuiltInRegistries.ITEM.getKey(match.getItem()).getNamespace().equals("minecraft"))
        {

            if (BuiltInRegistries.ITEM.getKey(match.getItem()).getNamespace().equals("tacz") && net.neoforged.fml.ModList.get().isLoaded("tacz"))
            {
                final ItemStack rifle = GunEnchants.standardIssue();
                return deliverable.matches(rifle) ? rifle : ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }

        if (Treasury.isCurrency(match) || Treasury.carriesCurrency(match))
        {
            return ItemStack.EMPTY;
        }

        final int pace = ItemStackUtils.ISFOOD.test(match) ? match.getMaxStackSize()
                           : FARM_PRODUCE.contains(match.getItem()) ? PRODUCE_BITE : ITEM_BUNDLE;
        match.setCount(Math.min(deliverable.getCount(), Math.min(pace, match.getMaxStackSize())));
        return match;
    }

    private static final Set<Item> FARM_PRODUCE = Set.of(
      Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.SUGAR_CANE, Items.CACTUS,
      Items.PUMPKIN, Items.MELON_SLICE, Items.EGG, Items.SUGAR);

    private static final int PRODUCE_BITE = 8;

    private static final int ITEM_BUNDLE = 64;

    private static List<Item> gearByDurability;

    private static List<Item> gearLadder()
    {
        if (gearByDurability == null)
        {
            final Map<Item, Integer> durability = new HashMap<>();
            for (final Item item : BuiltInRegistries.ITEM)
            {
                final int maxDamage = new ItemStack(item).getMaxDamage();

                if (maxDamage > 0 && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")
                      && !BuiltInRegistries.ITEM.getKey(item).getPath().startsWith("netherite_"))
                {
                    durability.put(item, maxDamage);
                }
            }
            final List<Item> ladder = new ArrayList<>(durability.keySet());

            ladder.sort(Comparator.<Item>comparingInt(durability::get).reversed()
                          .thenComparing(Comparator.<Item>comparingInt(item -> item instanceof ArmorItem armor ? armor.getDefense() : 0).reversed())
                          .thenComparing(Comparator.<Item>comparingInt(item -> item instanceof ArmorItem ? ItemStackUtils.getArmorLevel(new ItemStack(item)) : 0).reversed())
                          .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
            gearByDurability = ladder;
        }
        return gearByDurability;
    }
}
