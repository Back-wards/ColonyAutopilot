// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class MaterialTrickle
{
    private static final int PERIOD_TICKS = 100;

    private int tickCounter = 5;

    private final ColonyRota rota = new ColonyRota();

    private final Map<IBuilding, Double> carryover = new WeakHashMap<>();

    private final Set<IBuilding> rackFullAnnounced = Collections.newSetFromMap(new WeakHashMap<>());

    private final Set<IBuilding> fundsShortAnnounced = Collections.newSetFromMap(new WeakHashMap<>());

    private final Set<IBuilding> neverConjuredAnnounced = Collections.newSetFromMap(new WeakHashMap<>());

    private final Set<IBuilding> inKindAnnounced = Collections.newSetFromMap(new WeakHashMap<>());

    private final Set<IBuilding> trickleFailedAnnounced = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        carryover.clear();
        rackFullAnnounced.clear();
        fundsShortAnnounced.clear();
        neverConjuredAnnounced.clear();
        inKindAnnounced.clear();
        trickleFailedAnnounced.clear();
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

            try
            {
                Treasury.collectDeposits(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("[{}] the treasury's deposit sweep failed", colony.getName(), e);
            }

            Treasury.forgetWaitingBuilds(colony);

            if (colony.getWorld() == null || !AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_ENABLED)
                  || !AutopilotConfig.get(colony, AutopilotConfig.TRICKLE_ENABLED)
                  || !AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_BUILD_MATERIALS))
            {
                continue;
            }

            final double colonyBudget = AutopilotConfig.get(colony, AutopilotConfig.TRICKLE_ITEMS_PER_MINUTE) * (PERIOD_TICKS / 1200.0);
            final List<ItemStack> inKind = inKindStock(colony);
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                if (building instanceof BuildingBuilder hut)
                {
                    try
                    {
                        trickleInto(hut, colonyBudget, inKind);
                        trickleFailedAnnounced.remove(hut);
                    }
                    catch (final Exception e)
                    {

                        if (trickleFailedAnnounced.add(hut))
                        {
                            ColonyAutopilot.LOGGER.warn("[{}] Trickle failed for builder hut at {} — its build gets no materials until this resolves",
                              colony.getName(), building.getID().toShortString(), e);
                        }
                        else
                        {
                            ColonyAutopilot.LOGGER.debug("Trickle failed for builder hut at {}: {}", building.getID(), e.toString());
                        }
                    }
                }
            }
        }
    }

    private static List<ItemStack> inKindStock(final IColony colony)
    {
        if (!AutopilotConfig.progressionMode(colony) || !ProvidenceSweep.hasCouriers(colony))
        {
            return null;
        }
        final List<ItemStack> stock = ProvidenceSweep.warehouseStacks(colony);
        stock.addAll(ProvidenceSweep.courierStacks(colony));
        return stock;
    }

    private static String buildName(final BuildingBuilder hut)
    {
        return hut.getWorkOrder() != null ? "the build of the " + hut.getWorkOrder().getDisplayName().getString()
                 : "the builder's hut at " + hut.getPosition().toShortString();
    }

    static Map<Item, List<ItemStack>> onHand(final AbstractBuildingStructureBuilder hut)
    {
        final Map<Item, List<ItemStack>> onHand = new HashMap<>();
        final IItemHandler racks = hut.getTileEntity() == null ? null : hut.getItemHandlerCap();
        if (racks != null)
        {
            for (int slot = 0; slot < racks.getSlots(); slot++)
            {
                final ItemStack inSlot = racks.getStackInSlot(slot);
                if (!inSlot.isEmpty())
                {
                    onHand.computeIfAbsent(inSlot.getItem(), k -> new ArrayList<>()).add(inSlot);
                }
            }
        }
        for (final ICitizenData builder : hut.getAllAssignedCitizen())
        {
            final IItemHandler carried = builder.getInventory();
            if (carried == null)
            {
                continue;
            }
            for (int slot = 0; slot < carried.getSlots(); slot++)
            {
                final ItemStack inSlot = carried.getStackInSlot(slot);
                if (!inSlot.isEmpty())
                {
                    onHand.computeIfAbsent(inSlot.getItem(), k -> new ArrayList<>()).add(inSlot);
                }
            }
        }
        return onHand;
    }

    static int held(final Map<Item, List<ItemStack>> onHand, final ItemStack wanted)
    {
        int held = 0;
        for (final ItemStack inSlot : onHand.getOrDefault(wanted.getItem(), List.of()))
        {
            if (ItemStackUtils.compareItemStacksIgnoreStackSize(inSlot, wanted, true, true))
            {
                held += inSlot.getCount();
            }
        }
        return held;
    }

    private void trickleInto(final BuildingBuilder hut, final double budgetPerPeriod, final List<ItemStack> inKind)
    {
        final Map<String, BuildingBuilderResource> needed = hut.getModule(BuildingModules.BUILDING_RESOURCES).getNeededResources();
        if (needed.isEmpty())
        {
            carryover.remove(hut);
            rackFullAnnounced.remove(hut);
            fundsShortAnnounced.remove(hut);
            neverConjuredAnnounced.remove(hut);
            inKindAnnounced.remove(hut);
            Treasury.noteWaitingBuild(hut.getColony(), hut.getPosition(), null);
            return;
        }
        if (hut.getTileEntity() == null)
        {

            return;
        }
        final IItemHandler racks = hut.getItemHandlerCap();
        if (racks == null || !(hut.getColony().getWorld() instanceof ServerLevel level))
        {
            return;
        }

        double budget = budgetPerPeriod + Math.min(carryover.getOrDefault(hut, 0.0), Math.max(budgetPerPeriod, 1.0));

        final List<BuildingBuilderResource> resources = new ArrayList<>(needed.values());
        resources.sort(Comparator.comparing(BuildingBuilderResource::getName));

        final Map<Item, List<ItemStack>> onHand = onHand(hut);

        final Map<BuildingBuilderResource, Integer> missingOf = new LinkedHashMap<>();
        final List<String> leftInKind = new ArrayList<>();
        final boolean progression = AutopilotConfig.progressionMode(hut.getColony());
        for (final BuildingBuilderResource resource : resources)
        {

            if (Treasury.isCurrency(resource.getItemStack()) || Treasury.carriesCurrency(resource.getItemStack()))
            {
                continue;
            }

            if (progression && (Treasury.holdsItems(resource.getItemStack()) || Treasury.neverConjured(resource.getItemStack())))
            {
                if (neverConjuredAnnounced.add(hut))
                {
                    ColonyAutopilot.LOGGER.debug("[{}] the trickle leaves {} to the colony's own economy at {} — a filled container, spawner, vault, trial key, spawn egg or Mystical Agriculture seed is never conjured in Progression Mode",
                      hut.getColony().getName(), resource.getItemStack().getHoverName().getString(), hut.getID().toShortString());
                }
                continue;
            }
            int missing = resource.getAmount() - held(onHand, resource.getItemStack());

            if (missing > 0 && inKind != null && Exchange.familyValue(resource.getItemStack().getItem()) > 0)
            {
                int stocked = 0;
                for (final ItemStack held : inKind)
                {
                    if (ItemStackUtils.compareItemStacksIgnoreStackSize(held, resource.getItemStack(), true, true))
                    {
                        stocked += held.getCount();
                    }
                }
                if (stocked > 0)
                {
                    leftInKind.add(Math.min(stocked, missing) + "x " + resource.getItemStack().getHoverName().getString());
                    missing -= Math.min(stocked, missing);
                }
            }
            if (missing > 0)
            {
                missingOf.put(resource, missing);
            }
        }
        if (leftInKind.isEmpty())
        {
            inKindAnnounced.remove(hut);
        }
        else if (inKindAnnounced.add(hut))
        {
            ColonyAutopilot.LOGGER.debug("[{}] the trickle leaves {} to the couriers at {} — the warehouse holds them, and the colony pays the exchange's goods in kind",
              hut.getColony().getName(), String.join(", ", leftInKind), hut.getID().toShortString());
        }

        long buildPrice = 0;
        long buildFamily = 0;
        Item buildGood = null;
        long buildGoodPrice = 0;
        for (final Map.Entry<BuildingBuilderResource, Integer> left : missingOf.entrySet())
        {
            final long price = Treasury.cost(left.getKey().getItemStack().copyWithCount(left.getValue()));
            buildPrice += price;
            if (Exchange.familyValue(left.getKey().getItemStack().getItem()) > 0)
            {
                buildFamily += price;
                if (price > buildGoodPrice)
                {
                    buildGood = left.getKey().getItemStack().getItem();
                    buildGoodPrice = price;
                }
            }
        }

        final List<BuildingBuilderResource> order = new ArrayList<>(missingOf.keySet());
        if (!order.isEmpty())
        {
            Collections.rotate(order, -(int) ((hut.getColony().getWorld().getGameTime() / PERIOD_TICKS) % order.size()));
        }
        boolean racksFull = false;
        boolean unpaid = false;
        boolean progress = true;
        int trickledItems = 0;
        int trickledTypes = 0;
        while (budget >= 1 && !missingOf.isEmpty() && progress && !racksFull && !unpaid)
        {
            progress = false;
            final int share = Math.max(1, (int) Math.ceil(budget / missingOf.size()));
            for (final BuildingBuilderResource resource : order)
            {
                final Integer missing = missingOf.get(resource);
                if (missing == null || budget < 1)
                {
                    continue;
                }
                final ItemStack stack = resource.getItemStack().copy();
                final int toInsert = Math.min(Math.min(missing, share), Math.min((int) budget, stack.getMaxStackSize()));
                stack.setCount(toInsert);

                if (!Treasury.canAfford(hut.getColony(), level, Treasury.cost(stack)))
                {
                    if (fundsShortAnnounced.add(hut))
                    {
                        ColonyAutopilot.LOGGER.debug("[{}] the treasury cannot pay for the crew's materials at {} — the trickle waits for ColonyBucks",
                          hut.getColony().getName(), hut.getID().toShortString());
                    }
                    unpaid = true;
                    break;
                }

                final ItemStack remainder = InventoryUtils.addItemStackToItemHandlerWithResult(racks, stack);
                final int inserted = toInsert - remainder.getCount();
                Treasury.charge(hut.getColony(), level, Treasury.cost(stack.copyWithCount(inserted)), Treasury.Line.CONSTRUCTION);
                budget -= inserted;

                if (inserted > 0)
                {
                    progress = true;
                    trickledItems += inserted;
                    trickledTypes++;
                }
                if (!remainder.isEmpty())
                {

                    if (rackFullAnnounced.add(hut))
                    {
                        ColonyAutopilot.LOGGER.debug("[{}] the crew's racks at {} are full while build materials are still missing — waiting on the janitor for space",
                          hut.getColony().getName(), hut.getID().toShortString());
                    }
                    racksFull = true;
                    break;
                }
                if (missing - inserted <= 0)
                {
                    missingOf.remove(resource);
                }
                else
                {
                    missingOf.put(resource, missing - inserted);
                }
            }
        }
        if (!racksFull)
        {
            rackFullAnnounced.remove(hut);
        }
        if (!unpaid)
        {
            fundsShortAnnounced.remove(hut);
        }

        Treasury.noteWaitingBuild(hut.getColony(), hut.getPosition(), unpaid ? new Treasury.Goal(buildName(hut), buildPrice, buildPrice - buildFamily,
          buildGood, inKind != null ? "the warehouse or the builder's hut's racks" : "the builder's hut's racks") : null);
        if (trickledItems > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] trickled {} items ({} stacks) into the builder hut at {} — {} materials still missing",
              hut.getColony().getName(), trickledItems, trickledTypes, hut.getID().toShortString(), missingOf.size());
        }

        carryover.put(hut, budget);
    }
}
