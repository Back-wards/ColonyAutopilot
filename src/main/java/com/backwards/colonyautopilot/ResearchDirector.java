// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.IBuildingRegistry;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.IResearchRequirement;
import com.minecolonies.api.research.requirements.BuildingResearchRequirement;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.research.LocalResearch;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResearchDirector
{
    private static final int PERIOD_TICKS = 600;

    private record WantedResearch(ResourceLocation branch, ResourceLocation id, int depth, Block unlocks, int tier)
    {
    }

    private enum Start
    {
        STARTED, REFUSED, WAITS_FOR_FUNDS, SAVING
    }

    private int tickCounter = 20;

    private final ColonyRota rota = new ColonyRota();

    private final Map<String, List<WantedResearch>> wantedBySignature = new HashMap<>();

    private final Set<Map.Entry<ColonyId, ResourceLocation>> neverConjuredLogged = new HashSet<>();

    private final Set<ResourceLocation> vetoWarned = new HashSet<>();

    private static String planSignature(final IColony colony)
    {
        return AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES) + "|" + AutopilotConfig.get(colony, AutopilotConfig.RESEARCH_FULL_TREE)
                 + "|" + AutopilotConfig.RESEARCH_CAPSTONES.get() + "|" + AutopilotConfig.RESEARCH_FORKS.get();
    }

    private List<WantedResearch> planFor(final IColony colony)
    {
        return wantedBySignature.computeIfAbsent(planSignature(colony), signature -> computeWanted(colony));
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        wantedBySignature.clear();
        freeResearchGranted.clear();
        neverConjuredLogged.clear();
        vetoWarned.clear();
    }

    public void subscribeToMineColonies()
    {
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyDeletedModEvent.class, this::onColonyDeleted);
    }

    private void onColonyDeleted(final ColonyDeletedModEvent event)
    {
        final IColony colony = event.getColony();
        if (colony == null)
        {
            return;
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        freeResearchGranted.remove(key);
        neverConjuredLogged.removeIf(entry -> entry.getKey().equals(key));
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn())
        {
            Treasury.clearWaitingResearch();
            return;
        }
        if (++tickCounter >= PERIOD_TICKS)
        {
            tickCounter = 0;

            ColonyAutopilot.retainColonies(rota.fill(event.getServer()), freeResearchGranted);
        }
        for (final IColony colony : rota.take(event.getServer()))
        {

            try
            {
                grantFreeResearch(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Free research grant failed for colony {}", colony.getName(), e);
            }
            if (!AutopilotConfig.get(colony, AutopilotConfig.AUTO_RESEARCH))
            {
                Treasury.setWaitingResearch(colony, 0);
                Treasury.clearReserve(colony, "the university's research is switched off");
                continue;
            }
            try
            {
                evaluate(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Research evaluation failed for colony {}", colony.getName(), e);
            }
        }
    }

    private final Map<ColonyId, String> freeResearchGranted = new HashMap<>();

    private void grantFreeResearch(final IColony colony)
    {
        final boolean freeSpeed = AutopilotConfig.live(colony, AutopilotConfig.RESEARCH_FREE_BUILD_SPEED);
        final boolean freeBarracks = AutopilotConfig.live(colony, AutopilotConfig.RESEARCH_FREE_BARRACKS);
        final boolean freePopulation = AutopilotConfig.live(colony, AutopilotConfig.RESEARCH_FREE_POPULATION);
        final boolean freeDruid = AutopilotConfig.live(colony, AutopilotConfig.RESEARCH_FREE_DRUID);
        final String switches = freeSpeed + "|" + freeBarracks + "|" + freePopulation + "|" + freeDruid;
        if ((!freeSpeed && !freeBarracks && !freePopulation && !freeDruid)
              || switches.equals(freeResearchGranted.put(ColonyAutopilot.colonyKey(colony), switches)))
        {
            return;
        }
        final ResourceLocation barracksEffect = freeBarracks
          ? colony.getResearchManager().getResearchEffectIdFrom(ModBlocks.blockHutBarracks) : null;
        final ILocalResearchTree local = colony.getResearchManager().getResearchTree();
        final IGlobalResearchTree tree = IGlobalResearchTree.getInstance();
        int granted = 0;

        final Set<String> grantedFor = new LinkedHashSet<>();
        for (final ResourceLocation branch : tree.getBranches())
        {
            final Deque<ResourceLocation> queue = new ArrayDeque<>(tree.getPrimaryResearch(branch));
            final Set<ResourceLocation> visited = new HashSet<>();
            while (!queue.isEmpty())
            {
                final ResourceLocation id = queue.poll();
                if (!visited.add(id))
                {
                    continue;
                }
                final IGlobalResearch research = tree.getResearch(branch, id);
                if (research == null)
                {
                    continue;
                }
                queue.addAll(research.getChildren());
                String wantedFor = null;
                for (final IResearchEffect effect : research.getEffects())
                {
                    if (freeSpeed && (ResearchConstants.BLOCK_PLACE_SPEED.equals(effect.getId()) || ResearchConstants.BLOCK_BREAK_SPEED.equals(effect.getId())))
                    {
                        wantedFor = "build speed";
                    }
                    else if (barracksEffect != null && barracksEffect.equals(effect.getId()))
                    {
                        wantedFor = "barracks";
                    }
                    else if (freePopulation && ResearchConstants.CITIZEN_CAP.equals(effect.getId()))
                    {
                        wantedFor = "population";
                    }
                    else if (freeDruid && ResearchConstants.DRUID_USE_POTIONS.equals(effect.getId()))
                    {
                        wantedFor = "druid potions";
                    }
                    if (wantedFor != null)
                    {
                        break;
                    }
                }
                if (wantedFor == null)
                {
                    continue;
                }

                final Deque<IGlobalResearch> line = new ArrayDeque<>();
                for (IGlobalResearch node = research; node != null;
                      node = node.getParent() == null ? null : tree.getResearch(branch, node.getParent()))
                {
                    line.push(node);
                }
                final int before = granted;
                for (final IGlobalResearch step : line)
                {
                    final ILocalResearch entry = local.getResearch(branch, step.getId());
                    if (entry != null && entry.getState() == ResearchState.FINISHED)
                    {
                        continue;
                    }

                    final boolean alreadyApplied = local.hasCompletedResearch(step.getId());
                    if (entry == null)
                    {
                        final LocalResearch done = new LocalResearch(step.getId(), branch, step.getDepth());
                        done.setState(ResearchState.FINISHED);
                        done.setProgress(tree.getBranchData(branch).getBaseTime(step.getDepth()));
                        local.addResearch(branch, done);
                    }
                    else
                    {
                        entry.setState(ResearchState.FINISHED);
                        local.finishResearch(step.getId());
                    }
                    if (!alreadyApplied)
                    {
                        for (final IResearchEffect effect : step.getEffects())
                        {
                            colony.getResearchManager().getResearchEffects().applyEffect(effect);
                        }
                    }
                    granted++;
                }
                if (granted > before)
                {
                    grantedFor.add(wantedFor);
                }
            }
        }
        if (granted > 0)
        {
            colony.getResearchManager().markDirty();
            ColonyAutopilot.LOGGER.info("[{}] {} researches granted free ({}, with prerequisites)", colony.getName(), granted, String.join(" + ", grantedFor));
        }
    }

    private void evaluate(final IColony colony)
    {

        Treasury.setWaitingResearch(colony, 0);

        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }

        if (!AutopilotConfig.progressionMode(colony))
        {
            Treasury.clearReserve(colony, "Progression Mode is off");
        }

        BuildingUniversity university = null;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingUniversity candidate && candidate.getBuildingLevel() > 0
                  && (university == null || candidate.getBuildingLevel() > university.getBuildingLevel()))
            {
                university = candidate;
            }
        }
        if (university == null)
        {
            Treasury.clearReserve(colony, "no university stands");
            return;
        }

        final WorkerBuildingModule researchers = university.getModule(WorkerBuildingModule.class);
        if (researchers != null && !researchers.isFull()
              && com.minecolonies.core.util.BuildingUtils.canAutoHire(university, researchers.getHiringMode(), researchers.getJobEntry()))
        {
            final ICitizenData jobless = colony.getCitizenManager().getJoblessCitizen();
            if (jobless != null && researchers.assignCitizen(jobless))
            {
                ColonyAutopilot.LOGGER.info("[{}] assigned {} to the university ({}/{} researcher slots staffed) — each filled slot runs one more concurrent research",
                  colony.getName(), jobless.getName(), researchers.getAssignedCitizen().size(), researchers.getModuleMax());
            }
        }

        final List<WantedResearch> wanted = planFor(colony);

        final ILocalResearchTree localTree = colony.getResearchManager().getResearchTree();

        final int bench = researchers == null ? 0 : researchers.getAssignedCitizen().size();

        final ColonyGrounds.Reserve reserve = ColonyGrounds.get(level).reserve(colony.getID());
        if (reserve != null)
        {
            final ResourceLocation reserved = ResourceLocation.tryParse(reserve.id());
            if (reserved != null && (localTree.hasCompletedResearch(reserved)
                                       || localTree.getResearchInProgress().stream().anyMatch(running -> reserved.equals(running.getId()))))
            {
                Treasury.clearReserve(colony, "it already began");
            }
            else if (bench == 0)
            {
                Treasury.clearReserve(colony, "no researcher works at the university");
            }
        }
        if (localTree.getResearchInProgress().size() >= Math.min(university.getBuildingLevel(), bench))
        {
            return;
        }

        int waitingForFunds = 0;
        int fundsTier = Integer.MAX_VALUE;

        boolean picked = false;
        for (final WantedResearch want : wanted)
        {
            if (want.tier() > fundsTier)
            {

                break;
            }
            if (localTree.getResearch(want.branch(), want.id()) != null)
            {
                continue;
            }
            final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(want.branch(), want.id());
            if (research == null || !research.canResearch(university, localTree))
            {
                continue;
            }
            boolean requirementsMet = true;
            for (final IResearchRequirement requirement : research.getResearchRequirements())
            {
                if (!requirement.isFulfilled(colony))
                {
                    requirementsMet = false;
                    break;
                }
            }
            if (!requirementsMet)
            {
                continue;
            }

            final Start start = beginResearch(colony, level, university, localTree, research, want, waitingForFunds == 0, !picked);
            if (start == Start.STARTED)
            {
                picked = true;
                break;
            }
            if (start == Start.WAITS_FOR_FUNDS || start == Start.SAVING)
            {

                picked |= start == Start.SAVING;
                waitingForFunds++;
                fundsTier = want.tier();
            }
        }
        Treasury.setWaitingResearch(colony, waitingForFunds);
        if (!picked)
        {
            Treasury.clearReserve(colony, "nothing the university can start waits for money");
        }
    }

    Map<Block, Integer> researchBlockedLevels(final IColony colony)
    {
        if (!AutopilotConfig.live(colony, AutopilotConfig.AUTO_RESEARCH))
        {
            return Map.of();
        }
        int universityLevel = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingUniversity && building.getBuildingLevel() > universityLevel)
            {
                universityLevel = building.getBuildingLevel();
            }
        }
        if (universityLevel <= 0)
        {
            return Map.of();
        }
        final List<WantedResearch> wanted = planFor(colony);
        final ILocalResearchTree localTree = colony.getResearchManager().getResearchTree();
        final Map<Block, Integer> blocked = new HashMap<>();
        int deepestWanted = 0;
        for (final WantedResearch want : wanted)
        {
            if (localTree.getResearch(want.branch(), want.id()) != null)
            {
                continue;
            }
            deepestWanted = Math.max(deepestWanted, want.depth());
            final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(want.branch(), want.id());
            if (research == null)
            {
                continue;
            }
            for (final IResearchRequirement requirement : research.getResearchRequirements())
            {
                if (requirement instanceof BuildingResearchRequirement building && !requirement.isFulfilled(colony))
                {
                    final BuildingEntry entry = IBuildingRegistry.getInstance().get(building.getBuilding());
                    if (entry != null)
                    {
                        blocked.merge(entry.getBuildingBlock(), building.getBuildingLevel(), Integer::max);
                    }
                }
            }
        }

        if (deepestWanted > universityLevel)
        {
            blocked.merge(ModBlocks.blockHutUniversity, deepestWanted, Integer::max);
        }
        return blocked;
    }

    private Start beginResearch(
      final IColony colony,
      final ServerLevel level,
      final BuildingUniversity university,
      final ILocalResearchTree localTree,
      final IGlobalResearch research,
      final WantedResearch want,
      final boolean logWait,
      final boolean pick)
    {
        final FakePlayer actor = FakePlayerFactory.get(level,
          new GameProfile(colony.getPermissions().getOwner(), colony.getPermissions().getOwnerName()));
        actor.getInventory().clearContent();
        final boolean progression = AutopilotConfig.progressionMode(colony);
        long conjured = 0;
        long conjuredCredits = 0;
        long familyCredits = 0;
        ItemStack familyGood = ItemStack.EMPTY;
        for (final SizedIngredient cost : research.getCostList())
        {
            final ItemStack[] candidates = cost.getItems();
            if (cost.count() <= 0 || candidates == null || candidates.length == 0)
            {
                continue;
            }

            int inRacks = Math.min(cost.count(), InventoryUtils.hasBuildingEnoughElseCount(university,
              item -> IGlobalResearch.isUniversityResearchMatch(item, cost), cost.count()));

            if (pick && progression && inRacks < cost.count())
            {
                inRacks += stockFromWarehouse(colony, university, cost, cost.count() - inRacks, research);
            }
            final int missing = cost.count() - inRacks;
            if (missing <= 0)
            {
                continue;
            }

            ItemStack conjurable = ItemStack.EMPTY;
            for (final ItemStack candidate : candidates)
            {
                if (!Treasury.isCurrency(candidate) && !(progression && Treasury.neverConjured(candidate)))
                {
                    conjurable = candidate;
                    break;
                }
            }
            if (conjurable.isEmpty())
            {
                actor.getInventory().clearContent();
                if (neverConjuredLogged.add(Map.entry(ColonyAutopilot.colonyKey(colony), want.id())))
                {
                    ColonyAutopilot.LOGGER.info("[{}] research '{}' needs {}x {}, which the autopilot never conjures (ColonyBucks; in Progression Mode also spawners, vaults, trial keys, spawn eggs and Mystical Agriculture's seeds) — it waits for the university's racks to hold them",
                      colony.getName(), MutableComponent.create(research.getName()).getString(), missing, candidates[0].getHoverName().getString());
                }
                return Start.WAITS_FOR_FUNDS;
            }
            final ItemStack stack = conjurable.copy();
            stack.setCount(missing);

            conjuredCredits += Treasury.cost(stack);
            if (Exchange.familyValue(stack.getItem()) > 0)
            {
                familyCredits += Treasury.cost(stack);
                if (familyGood.isEmpty() || Treasury.cost(stack) > Treasury.cost(familyGood))
                {
                    familyGood = stack.copy();
                }
            }
            actor.getInventory().add(stack);
            conjured += missing;
        }

        final String researchName = MutableComponent.create(research.getName()).getString();

        if (!research.hasEnoughResources(actor, university.getPosition()))
        {
            actor.getInventory().clearContent();
            ColonyAutopilot.LOGGER.debug("[{}] could not begin research '{}'", colony.getName(), want.id());
            return Start.REFUSED;
        }
        final int itemsPerBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
        final long depthFee = (long) AutopilotConfig.ECONOMY_RESEARCH_FEE_PER_TIER.get() * research.getDepth() * itemsPerBuck;
        final long price = depthFee + conjuredCredits;

        final ColonyGrounds.Reserve saved = pick && progression ? Treasury.releaseReserve(colony, level) : null;
        if (!Treasury.canAfford(colony, level, price))
        {
            actor.getInventory().clearContent();
            if (logWait)
            {
                ColonyAutopilot.LOGGER.debug("[{}] research '{}' waits for {} bucks (fee for depth {} + {} cost items conjured) — the treasury cannot pay it yet",
                  colony.getName(), researchName, (price + itemsPerBuck - 1) / itemsPerBuck, research.getDepth(), conjured);
            }
            if (pick && progression)
            {
                Treasury.reserve(colony, level, price, price - familyCredits, familyGood.isEmpty() ? null : familyGood.getItem(), want.id().toString(),
                  researchName, saved);
                return Start.SAVING;
            }
            return Start.WAITS_FOR_FUNDS;
        }

        localTree.attemptBeginResearch(actor, colony, university, research);
        actor.getInventory().clearContent();

        if (localTree.getResearch(want.branch(), want.id()) == null)
        {
            if (AutopilotConfig.progressionMode(colony) && vetoWarned.add(want.id()))
            {

                ColonyAutopilot.LOGGER.warn("[{}] research '{}' did not begin despite passing every check — retried next cadence; please report this",
                  colony.getName(), researchName);
            }
            else
            {

                ColonyAutopilot.LOGGER.debug("[{}] could not begin research '{}'", colony.getName(), want.id());
            }
            return Start.REFUSED;
        }

        Treasury.charge(colony, level, price, Treasury.Line.RESEARCH);
        if (saved != null)
        {
            ColonyAutopilot.LOGGER.debug("[{}] research '{}' is bought — the treasury saved for {} and saves for nothing now", colony.getName(), researchName,
              saved.id().equals(want.id().toString()) ? "it" : "research '" + saved.name() + "' before");
        }
        if (price > 0 && AutopilotConfig.progressionMode(colony))
        {

            ColonyAutopilot.LOGGER.info("[{}] the treasury paid {} bucks and {} items to begin research '{}' (fee for depth {} + {} cost items conjured)",
              colony.getName(), price / itemsPerBuck, price % itemsPerBuck, researchName, research.getDepth(), conjured);
        }
        if (want.unlocks() != null)
        {
            ColonyAutopilot.LOGGER.info("[{}] the university began researching '{}' — unlocks the {}",
              colony.getName(), researchName, want.unlocks().getName().getString());
        }
        else
        {
            ColonyAutopilot.LOGGER.info("[{}] the university began researching '{}' (prerequisite on the way to a new building)",
              colony.getName(), researchName);
        }
        if (want.unlocks() != null
              && !(AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES) && want.unlocks() == ModBlocks.blockHutGraveyard))
        {

            Milestones.say(colony, "colonyautopilot.milestone.research", researchName);
        }
        return Start.STARTED;
    }

    private static int stockFromWarehouse(final IColony colony, final BuildingUniversity university, final SizedIngredient cost, final int wanted,
      final IGlobalResearch research)
    {
        final IItemHandler racks = university.getItemHandlerCap();
        if (racks == null)
        {
            return 0;
        }
        int moved = 0;
        ItemStack sample = ItemStack.EMPTY;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() != ModBlocks.blockHutWareHouse || building.getBuildingLevel() <= 0 || building.getTileEntity() == null)
            {
                continue;
            }
            final IItemHandler stock = building.getItemHandlerCap();
            for (int slot = 0; stock != null && slot < stock.getSlots() && moved < wanted; slot++)
            {
                final ItemStack held = stock.getStackInSlot(slot);
                if (held.isEmpty() || Exchange.familyValue(held.getItem()) <= 0 || !IGlobalResearch.isUniversityResearchMatch(held, cost))
                {
                    continue;
                }
                final ItemStack taken = stock.extractItem(slot, Math.min(wanted - moved, held.getCount()), false);
                final ItemStack left = InventoryUtils.addItemStackToItemHandlerWithResult(racks, taken.copy());
                moved += taken.getCount() - left.getCount();
                if (sample.isEmpty())
                {
                    sample = taken.copy();
                }
                if (!left.isEmpty())
                {

                    InventoryUtils.addItemStackToItemHandler(stock, left);
                    break;
                }
            }
        }
        if (moved > 0)
        {
            ColonyAutopilot.LOGGER.info("[{}] the university took {}x {} from the warehouse for research '{}' — paid in kind, not charged",
              colony.getName(), moved, sample.getHoverName().getString(), MutableComponent.create(research.getName()).getString());
        }
        return moved;
    }

    private static Set<ResourceLocation> chosenResearchIds()
    {
        final Set<ResourceLocation> ids = new HashSet<>();
        for (final String list : new String[] {AutopilotConfig.RESEARCH_CAPSTONES.get(), AutopilotConfig.RESEARCH_FORKS.get()})
        {
            for (final String entry : list.split(","))
            {
                final String trimmed = entry.trim();
                if (!trimmed.isEmpty())
                {
                    final ResourceLocation id = ResourceLocation.tryParse(trimmed);
                    if (id != null)
                    {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private static boolean gravebound(final IGlobalResearch research)
    {
        for (final IResearchRequirement requirement : research.getResearchRequirements())
        {
            if (requirement instanceof BuildingResearchRequirement building)
            {
                final BuildingEntry entry = IBuildingRegistry.getInstance().get(building.getBuilding());
                if (entry != null && entry.getBuildingBlock() == ModBlocks.blockHutGraveyard)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private List<WantedResearch> computeWanted(final IColony colony)
    {
        final Set<ResourceLocation> hutEffects = new HashSet<>();
        final Map<ResourceLocation, Block> hutByEffect = new HashMap<>();
        for (final Block hut : ModBlocks.getHuts())
        {
            final ResourceLocation effectId = colony.getResearchManager().getResearchEffectIdFrom(hut);
            if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(effectId))
            {
                hutEffects.add(effectId);
                hutByEffect.put(effectId, hut);
            }
        }

        final boolean fullTree = AutopilotConfig.get(colony, AutopilotConfig.RESEARCH_FULL_TREE);
        final Set<ResourceLocation> chosenResearch = chosenResearchIds();
        final IGlobalResearchTree tree = IGlobalResearchTree.getInstance();

        for (final ResourceLocation chosen : chosenResearch)
        {
            if (!tree.hasResearch(chosen))
            {
                ColonyAutopilot.LOGGER.warn("research.forks/research.capstones entry '{}' matches no research — that either/or is left to the player", chosen);
            }
        }
        final Map<ResourceLocation, WantedResearch> found = new HashMap<>();
        for (final ResourceLocation branch : tree.getBranches())
        {
            final Deque<ResourceLocation> queue = new ArrayDeque<>(tree.getPrimaryResearch(branch));
            final Set<ResourceLocation> visited = new HashSet<>();
            while (!queue.isEmpty())
            {
                final ResourceLocation id = queue.poll();
                if (!visited.add(id))
                {
                    continue;
                }
                final IGlobalResearch research = tree.getResearch(branch, id);
                if (research == null)
                {
                    continue;
                }
                if (AutopilotConfig.live(colony, AutopilotConfig.NO_GRAVES) && gravebound(research))
                {
                    continue;
                }
                queue.addAll(research.getChildren());

                Block unlocked = null;
                boolean raisesCap = false;
                boolean combat = false;
                boolean doctrine = false;
                for (final IResearchEffect effect : research.getEffects())
                {

                    if (GrowthDirector.HUSCARL_RESEARCH.equals(effect.getId()) || GrowthDirector.MARKSMAN_RESEARCH.equals(effect.getId()))
                    {
                        doctrine = true;
                    }
                    if (hutEffects.contains(effect.getId()))
                    {
                        unlocked = hutByEffect.get(effect.getId());
                        break;
                    }
                    if (ResearchConstants.CITIZEN_CAP.equals(effect.getId()))
                    {
                        raisesCap = true;
                    }
                    if (combatEffects().contains(effect.getId()))
                    {
                        combat = true;
                    }
                }

                final int tier;
                if (doctrine)
                {
                    tier = -1;
                }
                else if (unlocked != null)
                {
                    tier = 0;
                }
                else if (raisesCap)
                {

                    tier = 0;
                }
                else if (fullTree)
                {

                    final IGlobalResearch parent = research.getParent() == null ? null : tree.getResearch(branch, research.getParent());
                    if (!chosenResearch.contains(id)
                          && ((parent != null && parent.hasOnlyChild()) || research.getDepth() >= ResearchConstants.MAX_DEPTH))
                    {
                        continue;
                    }
                    tier = combat ? 1 : 2;
                }
                else
                {
                    continue;
                }

                want(found, branch, id, research.getDepth(), unlocked, tier);

                ResourceLocation parentId = research.getParent();
                while (parentId != null)
                {
                    final IGlobalResearch parent = tree.getResearch(branch, parentId);
                    if (parent == null)
                    {
                        break;
                    }

                    final IGlobalResearch grandparent = parent.getParent() == null ? null : tree.getResearch(branch, parent.getParent());
                    if (tier >= 1 && !chosenResearch.contains(parentId)
                          && ((grandparent != null && grandparent.hasOnlyChild()) || parent.getDepth() >= ResearchConstants.MAX_DEPTH))
                    {
                        break;
                    }
                    want(found, branch, parentId, parent.getDepth(), null, tier);
                    parentId = parent.getParent();
                }
            }
        }

        final List<WantedResearch> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparingInt(WantedResearch::tier).thenComparingInt(WantedResearch::depth));
        ColonyAutopilot.LOGGER.info("Research plan assembled: {} researches ({} unlock buildings, population chain and the open tree behind them)",
          result.size(), result.stream().filter(want -> want.unlocks() != null).count());
        return result;
    }

    private static void want(final Map<ResourceLocation, WantedResearch> found, final ResourceLocation branch,
      final ResourceLocation id, final int depth, final Block unlocks, final int tier)
    {
        final WantedResearch existing = found.get(id);
        if (existing == null || tier < existing.tier())
        {
            found.put(id, new WantedResearch(branch, id, depth,
              existing != null && existing.unlocks() != null ? existing.unlocks() : unlocks, tier));
        }
    }

    private static Set<ResourceLocation> combatEffects;

    private static Set<ResourceLocation> combatEffects()
    {
        if (combatEffects == null)
        {
            combatEffects = Set.of(
              ResearchConstants.WALKING, ResearchConstants.SOFT_SHOES, ResearchConstants.FLEEING_SPEED,
              ResearchConstants.MELEE_ARMOR, ResearchConstants.ARCHER_ARMOR, ResearchConstants.PLATE_ARMOR,
              ResearchConstants.ARMOR_DURABILITY, ResearchConstants.HEALTH_BOOST, ResearchConstants.BLOCK_ATTACKS,
              ResearchConstants.SHIELD_USAGE, ResearchConstants.KNIGHT_TAUNT, ResearchConstants.RETREAT,
              ResearchConstants.FLEEING_DAMAGE, ResearchConstants.FIRE_RES, ResearchConstants.REGENERATION,
              ResearchConstants.MELEE_DAMAGE, ResearchConstants.ARCHER_DAMAGE, ResearchConstants.ARROW_PIERCE,
              ResearchConstants.DOUBLE_ARROWS, ResearchConstants.ARCHER_USE_ARROWS, ResearchConstants.GUARD_CRIT,
              ResearchConstants.KNIGHT_WHIRLWIND, ResearchConstants.LOOTING);
        }
        return combatEffects;
    }
}
