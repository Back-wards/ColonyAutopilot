// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.DamageSourceKeys;
import com.minecolonies.core.colony.events.raid.RaidManager;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractCraftingRequestResolver;
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Treasury
{

    private static final ResourceLocation TACZ_GUN = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");

    private static final Map<ColonyId, Map<IToken<?>, Refusal>> WAITING_REQUESTS = new HashMap<>();

    record Refusal(long credits, ProvidenceSweep.Category category, Goal goal, boolean crafterHeld)
    {
    }

    public record Goal(String what, long price, long stocked, Item good, String where)
    {
    }

    private static final Map<ColonyId, Map<Long, Goal>> WAITING_BUILDS = new HashMap<>();

    private static final Map<ColonyId, Integer> WAITING_RESEARCH = new HashMap<>();

    private static final Map<ColonyId, Integer> WAITING_CURES = new HashMap<>();
    private static final Map<ColonyId, Integer> WAITING_SPORE_CURES = new HashMap<>();

    private static final Map<ColonyId, Long> SPENT_TODAY = new HashMap<>();

    public enum Line
    {
        KILLS("kills banked", true), RAIDS("raid pay", true), DEPOSITS("deposits", true), RELIEF("operator relief", true),
        EXCHANGE("sold at the exchange", true), CONSTRUCTION("construction", false), RESEARCH("research", false), GEAR("guard gear", false),
        ROUNDS("gunners' rounds", false), TOOLS("tools", false), SUPPLY("other supply", false), REPAIRS("repairs", false), CURES("cures", false);

        public final String label;
        public final boolean income;

        Line(final String label, final boolean income)
        {
            this.label = label;
            this.income = income;
        }
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {

        clearWaitingRequests();
        clearWaitingResearch();
        SPENT_TODAY.clear();
        WAITING_BUILDS.clear();
        WAITING_CURES.clear();
        WAITING_SPORE_CURES.clear();
    }

    static long cost(final ItemStack stack)
    {
        final Item item = stack.getItem();
        final boolean gear;
        if (item instanceof TieredItem tiered)
        {
            final Tier tier = tiered.getTier();
            gear = tier != Tiers.WOOD && tier != Tiers.STONE && tier != Tiers.GOLD;
        }
        else if (item instanceof ArmorItem armour)
        {

            final ArmorMaterial material = armour.getMaterial().value();
            gear = material != ArmorMaterials.LEATHER.value() && material != ArmorMaterials.CHAIN.value() && material != ArmorMaterials.GOLD.value();
        }
        else
        {
            gear = BuiltInRegistries.ITEM.getKey(item).equals(TACZ_GUN);
        }
        final long weighed = gear ? (long) stack.getCount() * AutopilotConfig.ECONOMY_GEAR_WEIGHT.get() : stack.getCount();
        final long floor = Exchange.familyValue(item);

        final long marked = floor == 0 ? 0L : BigDecimal.valueOf(AutopilotConfig.ECONOMY_CONJURE_MARKUP.get())
          .multiply(BigDecimal.valueOf(floor)).setScale(0, RoundingMode.CEILING).longValueExact();
        return Math.max(weighed, marked * stack.getCount());
    }

    static long cureCost(final Disease disease)
    {
        long credits = 0L;
        for (final ItemStorage cure : disease.cureItems())
        {
            credits += cost(cure.getItemStack().copyWithCount(cure.getAmount()));
        }
        return credits;
    }

    static boolean isCurrency(final ItemStack stack)
    {
        return stack.is(ModItems.COLONY_BUCKS.get());
    }

    static boolean carriesCurrency(final ItemStack stack)
    {
        for (final ItemStack held : contents(stack))
        {
            if (isCurrency(held) || carriesCurrency(held))
            {
                return true;
            }
        }
        return false;
    }

    static boolean holdsItems(final ItemStack stack)
    {
        return !contents(stack).isEmpty();
    }

    private static final TagKey<Item> NEVER_CONJURED = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "never_conjured"));

    static boolean neverConjured(final ItemStack stack)
    {
        return stack.getItem() instanceof SpawnEggItem || stack.is(Items.SPAWNER) || stack.is(Items.TRIAL_SPAWNER) || stack.is(Items.VAULT)
                 || stack.is(Items.TRIAL_KEY) || stack.is(Items.OMINOUS_TRIAL_KEY) || stack.is(NEVER_CONJURED);
    }

    private static List<ItemStack> contents(final ItemStack stack)
    {
        final List<ItemStack> held = new ArrayList<>();
        final ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null)
        {
            container.nonEmptyItems().forEach(held::add);
        }
        final BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null)
        {
            bundle.items().forEach(held::add);
        }
        return held;
    }

    static boolean canAfford(final IColony colony, final ServerLevel level, final long items)
    {
        return items <= 0 || !AutopilotConfig.progressionMode(colony) || spendable(level, colony.getID()) >= items;
    }

    static long spendable(final IColony colony)
    {
        return credits(colony) - reservedCredits(colony);
    }

    private static long spendable(final ServerLevel level, final int colonyId)
    {
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final ColonyGrounds.Reserve reserve = grounds.reserve(colonyId);
        return grounds.treasuryCredits(colonyId) - (reserve == null ? 0L : reserve.credits());
    }

    static boolean charge(final IColony colony, final ServerLevel level, final long items, final Line line)
    {
        if (items <= 0 || !AutopilotConfig.progressionMode(colony))
        {
            return true;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final long credits = grounds.treasuryCredits(colony.getID());
        if ((line == Line.CURES ? credits : spendable(level, colony.getID())) < items)
        {
            return false;
        }
        final long left = credits - items;
        grounds.setTreasuryCredits(colony.getID(), left);
        SPENT_TODAY.merge(ColonyAutopilot.colonyKey(colony), items, Long::sum);
        tally(level, colony.getID(), line, items);
        final int perBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
        ColonyAutopilot.LOGGER.debug("[{}] the treasury paid {} items — {} bucks and {} items left", colony.getName(), items, left / perBuck, left % perBuck);
        return true;
    }

    static void deposit(final IColony colony, final ServerLevel level, final int bucks, final Line line)
    {
        if (bucks <= 0)
        {

            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final int perBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
        final long credits = grounds.treasuryCredits(colony.getID()) + (long) bucks * perBuck;
        grounds.setTreasuryCredits(colony.getID(), credits);
        tally(level, colony.getID(), line, (long) bucks * perBuck);
        ColonyAutopilot.LOGGER.info("[{}] {} ColonyBucks deposited — treasury {} bucks and {} items", colony.getName(), bucks, credits / perBuck, credits % perBuck);
    }

    static void noteExchange(final IColony colony, final int bucks)
    {
        if (colony.getWorld() instanceof ServerLevel level)
        {
            tally(level, colony.getID(), Line.EXCHANGE, (long) bucks * AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get());
        }
    }

    private static void tally(final ServerLevel level, final int colonyId, final Line line, final long credits)
    {
        if (credits <= 0)
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final long[] ledger = rolled(grounds.ledger(colonyId), grounds.ledgerDay(level.getDayTime() / 24000L));
        ledger[1 + line.ordinal()] += credits;
        grounds.setLedger(colonyId, ledger);
    }

    private static long[] rolled(final long[] saved, final long day)
    {
        final int lines = Line.values().length;
        final long[] next = new long[1 + 2 * lines];
        next[0] = day;
        if (saved == null || saved.length % 2 == 0 || saved.length > next.length)
        {
            return next;
        }
        final int savedLines = (saved.length - 1) / 2;
        final long[] wide = new long[next.length];
        wide[0] = saved[0];
        System.arraycopy(saved, 1, wide, 1, savedLines);
        System.arraycopy(saved, 1 + savedLines, wide, 1 + lines, savedLines);
        if (wide[0] == day)
        {
            return wide;
        }
        if (wide[0] == day - 1)
        {
            System.arraycopy(wide, 1, next, 1 + lines, lines);
        }
        return next;
    }

    public static long[] ledger(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return rolled(null, 0L);
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        return rolled(grounds.ledger(colony.getID()), grounds.ledgerDay(level.getDayTime() / 24000L)).clone();
    }

    static void reserve(final IColony colony, final ServerLevel level, final long credits, final long stocked, final Item good, final String id,
      final String name, final ColonyGrounds.Reserve before)
    {
        final ColonyGrounds.Reserve reserve = new ColonyGrounds.Reserve(credits, stocked, id, name,
          good == null ? "" : BuiltInRegistries.ITEM.getKey(good).toString());
        ColonyGrounds.get(level).setReserve(colony.getID(), reserve);
        if (!reserve.equals(before))
        {
            final int perBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
            ColonyAutopilot.LOGGER.debug("[{}] the treasury saves {} bucks and {} items for research '{}' — every other spender waits on the rest (balance {} bucks and {} items)",
              colony.getName(), credits / perBuck, credits % perBuck, name, credits(colony) / perBuck, credits(colony) % perBuck);
        }
    }

    static ColonyGrounds.Reserve releaseReserve(final IColony colony, final ServerLevel level)
    {
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final ColonyGrounds.Reserve reserve = grounds.reserve(colony.getID());
        grounds.setReserve(colony.getID(), null);
        return reserve;
    }

    static void clearReserve(final IColony colony, final String why)
    {
        if (colony.getWorld() instanceof ServerLevel level && releaseReserve(colony, level) instanceof ColonyGrounds.Reserve released)
        {
            ColonyAutopilot.LOGGER.debug("[{}] the treasury no longer saves for research '{}' — {}", colony.getName(), released.name(), why);
        }
    }

    public static long reservedCredits(final IColony colony)
    {
        return colony.getWorld() instanceof ServerLevel level && AutopilotConfig.progressionMode(colony)
                 && ColonyGrounds.get(level).reserve(colony.getID()) instanceof ColonyGrounds.Reserve reserve ? reserve.credits() : 0L;
    }

    public static long savingBucks(final IColony colony)
    {
        return Math.ceilDiv(reservedCredits(colony), AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get());
    }

    public static long savedBucks(final IColony colony)
    {
        final long reserved = reservedCredits(colony);
        return credits(colony) >= reserved ? savingBucks(colony) : credits(colony) / AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
    }

    public static String reservedFor(final IColony colony)
    {
        return reservedCredits(colony) > 0 && ColonyGrounds.get((ServerLevel) colony.getWorld()).reserve(colony.getID()) instanceof ColonyGrounds.Reserve reserve
                 ? reserve.name() : "";
    }

    public static long credits(final IColony colony)
    {
        return colony.getWorld() instanceof ServerLevel level ? ColonyGrounds.get(level).treasuryCredits(colony.getID()) : 0L;
    }

    public static long bucks(final IColony colony)
    {
        return credits(colony) / AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
    }

    public static long items(final IColony colony)
    {
        return credits(colony) % AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
    }

    static void resetWaitingRequests(final IColony colony)
    {
        WAITING_REQUESTS.remove(ColonyAutopilot.colonyKey(colony));
    }

    static void clearWaitingRequests()
    {
        WAITING_REQUESTS.clear();
    }

    static void pruneWaitingRequests(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<IToken<?>, Refusal> waiting = WAITING_REQUESTS.get(key);
        if (waiting == null)
        {
            return;
        }
        final IRequestManager manager = colony.getRequestManager();

        waiting.entrySet().removeIf(entry -> {
            final IRequest<?> request = manager.getRequestForToken(entry.getKey());

            if (request == null || ProvidenceSweep.CLOSED.contains(request.getState()))
            {
                return true;
            }

            if (request.getRequest() instanceof Tool || !crafterHeld(manager, entry.getKey()))
            {
                return false;
            }

            return !(entry.getValue().crafterHeld() && strandedBelow(manager, request));
        });
        if (waiting.isEmpty())
        {
            WAITING_REQUESTS.remove(key);
        }
    }

    private static boolean crafterHeld(final IRequestManager manager, final IToken<?> token)
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

    private static boolean strandedBelow(final IRequestManager manager, final IRequest<?> request)
    {
        final Set<IToken<?>> stranded = new HashSet<>(manager.getPlayerResolver().getAllAssignedRequests());
        stranded.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());
        final Deque<IToken<?>> below = new ArrayDeque<>(request.getChildren());
        for (int read = 0; !below.isEmpty() && read < ProvidenceSweep.CHAIN_HOPS; read++)
        {
            final IToken<?> token = below.poll();
            if (stranded.contains(token))
            {
                return true;
            }
            final IRequest<?> child = manager.getRequestForToken(token);
            if (child != null)
            {
                below.addAll(child.getChildren());
            }
        }
        return false;
    }

    static void noteWaitingRequest(final IColony colony, final IToken<?> token, final ProvidenceSweep.Category category, final ItemStack stack)
    {
        final long price = cost(stack);

        final boolean good = Exchange.familyValue(stack.getItem()) > 0;
        final Goal goal = new Goal(stack.getCount() + "x " + stack.getHoverName().getString(), price, good ? 0L : price, good ? stack.getItem() : null,
          "the warehouse");
        WAITING_REQUESTS.computeIfAbsent(ColonyAutopilot.colonyKey(colony), k -> new HashMap<>())
          .put(token, new Refusal(spendable(colony), category, goal, crafterHeld(colony.getRequestManager(), token)));
    }

    static void forgetWaitingBuilds(final IColony colony)
    {
        WAITING_BUILDS.remove(ColonyAutopilot.colonyKey(colony));
    }

    static void noteWaitingBuild(final IColony colony, final BlockPos hut, final Goal goal)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (goal != null)
        {
            WAITING_BUILDS.computeIfAbsent(key, k -> new HashMap<>()).put(hut.asLong(), goal);
        }
        else if (WAITING_BUILDS.get(key) instanceof Map<Long, Goal> builds && builds.remove(hut.asLong()) != null && builds.isEmpty())
        {
            WAITING_BUILDS.remove(key);
        }
    }

    public static Goal savingFor(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level) || !AutopilotConfig.progressionMode(colony))
        {
            return null;
        }
        if (ColonyGrounds.get(level).reserve(colony.getID()) instanceof ColonyGrounds.Reserve reserve)
        {
            final Item good = reserve.good().isEmpty() ? null : BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(reserve.good())).orElse(null);
            return new Goal("research '" + reserve.name() + "'", reserve.credits(), reserve.stocked(), good, "the warehouse or the University's racks");
        }
        final ColonyId key = ColonyAutopilot.colonyKey(colony);

        final boolean couriers = ProvidenceSweep.hasCouriers(colony);
        Goal costliest = null;
        for (final Refusal refusal : WAITING_REQUESTS.getOrDefault(key, Map.of()).values())
        {
            if (costliest == null || refusal.goal().price() > costliest.price())
            {
                final Goal goal = refusal.goal();
                costliest = couriers ? goal : new Goal(goal.what(), goal.price(), goal.price(), null, goal.where());
            }
        }
        for (final Goal build : WAITING_BUILDS.getOrDefault(key, Map.of()).values())
        {
            if (costliest == null || build.price() > costliest.price())
            {
                costliest = build;
            }
        }
        return costliest;
    }

    static void forgetWaitingRequest(final IColony colony, final IToken<?> token)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        final Map<IToken<?>, Refusal> waiting = WAITING_REQUESTS.get(key);
        if (waiting != null && waiting.remove(token) != null && waiting.isEmpty())
        {
            WAITING_REQUESTS.remove(key);
        }
    }

    static Refusal refusal(final IColony colony, final IToken<?> token)
    {
        final Map<IToken<?>, Refusal> waiting = WAITING_REQUESTS.get(ColonyAutopilot.colonyKey(colony));
        return waiting == null ? null : waiting.get(token);
    }

    static int waitingUnder(final IColony colony, final Set<IToken<?>> own)
    {
        final Map<IToken<?>, Refusal> waiting = WAITING_REQUESTS.get(ColonyAutopilot.colonyKey(colony));
        if (waiting == null || own.isEmpty())
        {
            return 0;
        }
        final IRequestManager manager = colony.getRequestManager();
        int count = 0;
        for (final IToken<?> token : waiting.keySet())
        {

            IToken<?> at = token;
            for (int hop = 0; at != null && hop < ProvidenceSweep.CHAIN_HOPS; hop++)
            {
                if (own.contains(at))
                {
                    count++;
                    break;
                }

                final IRequest<?> request = manager.getRequestForToken(at);
                at = request == null ? null : request.getParent();
            }
        }
        return count;
    }

    public static int waitingRequests(final IColony colony)
    {
        final Map<IToken<?>, Refusal> waiting = WAITING_REQUESTS.get(ColonyAutopilot.colonyKey(colony));
        return waiting == null ? 0 : waiting.size();
    }

    static void setWaitingResearch(final IColony colony, final int count)
    {
        if (count <= 0)
        {
            WAITING_RESEARCH.remove(ColonyAutopilot.colonyKey(colony));
        }
        else
        {
            WAITING_RESEARCH.put(ColonyAutopilot.colonyKey(colony), count);
        }
    }

    static void clearWaitingResearch()
    {
        WAITING_RESEARCH.clear();
    }

    public static int waitingResearch(final IColony colony)
    {
        return WAITING_RESEARCH.getOrDefault(ColonyAutopilot.colonyKey(colony), 0);
    }

    static void setWaitingCures(final IColony colony, final boolean spore, final int count)
    {
        final Map<ColonyId, Integer> waiting = spore ? WAITING_SPORE_CURES : WAITING_CURES;
        if (count <= 0)
        {
            waiting.remove(ColonyAutopilot.colonyKey(colony));
        }
        else
        {
            waiting.put(ColonyAutopilot.colonyKey(colony), count);
        }
    }

    public static int waitingCures(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        return WAITING_CURES.getOrDefault(key, 0) + WAITING_SPORE_CURES.getOrDefault(key, 0);
    }

    static long takeSpentToday(final IColony colony)
    {
        final Long spent = SPENT_TODAY.remove(ColonyAutopilot.colonyKey(colony));
        return spent == null ? 0L : spent;
    }

    static void collectDeposits(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level) || !AutopilotConfig.progressionMode(colony))
        {
            return;
        }
        int bucks = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {

            if (building.getTileEntity() != null
                  && (building == colony.getServerBuildingManager().getTownHall()
                        || (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutWareHouse && building.getBuildingLevel() > 0)))
            {
                bucks += extractBucks(building.getItemHandlerCap());
            }
        }
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            bucks += extractBucks(citizen.getInventory());
        }
        if (bucks > 0)
        {
            deposit(colony, level, bucks, Line.DEPOSITS);
        }
    }

    public static void depositPockets(final ICitizenData citizen)
    {
        final IColony colony = citizen.getColony();
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final int bucks = extractBucks(citizen.getInventory());
        if (bucks > 0)
        {
            ColonyAutopilot.LOGGER.debug("[{}] {} died carrying {} ColonyBucks — they go to the treasury", colony.getName(), citizen.getName(), bucks);
            deposit(colony, level, bucks, Line.DEPOSITS);
        }
    }

    static boolean isCitizen(final Entity entity)
    {
        return entity instanceof EntityCitizen;
    }

    static boolean bankKill(final Entity killer, final LivingEntity victim, final String chance)
    {
        if (!(killer instanceof EntityCitizen citizen) || !AutopilotConfig.masterOn())
        {
            return false;
        }
        final ICitizenData data = citizen.getCitizenData();
        final IColony colony = data == null ? null : data.getColony();
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level) || !AutopilotConfig.progressionMode(colony))
        {
            return false;
        }
        ColonyAutopilot.LOGGER.debug("[{}] {} killed a {} — its ColonyBucks goes straight to the treasury (chance {})", colony.getName(), data.getName(),
          BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), chance);
        deposit(colony, level, 1, Line.KILLS);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof AbstractEntityMinecoloniesRaider raider) || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn()
              || event.getSource().is(DamageSourceKeys.DESPAWN))
        {
            return;
        }
        final IColony colony = raider.getColony();
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level) || !AutopilotConfig.progressionMode(colony)
              || !colony.getRaiderManager().isRaided() || !(colony.getRaiderManager() instanceof RaidManager raids) || raids.getLastRaid() == null)
        {
            return;
        }
        final RaidManager.RaidHistory raid = raids.getLastRaid();
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final long[] pay = grounds.raidPay(colony.getID());
        final boolean same = pay != null && pay[0] == raid.raidTime;
        final long paid = same ? pay[1] : 0;
        if (paid >= raid.raiderAmount)
        {
            ColonyAutopilot.LOGGER.debug("[{}] a {} fell in the raid — no raid pay: all {} of its raiders are paid for",
              colony.getName(), BuiltInRegistries.ENTITY_TYPE.getKey(raider.getType()), raid.raiderAmount);
            return;
        }
        grounds.setRaidPay(colony.getID(), raid.raidTime, paid + 1, same ? pay[2] : 0);
        ColonyAutopilot.LOGGER.debug("[{}] a {} fell in the raid — raid pay {} of {}", colony.getName(), BuiltInRegistries.ENTITY_TYPE.getKey(raider.getType()),
          paid + 1, raid.raiderAmount);
        deposit(colony, level, 1, Line.RAIDS);
    }

    public static void raidEnded(final IColony colony)
    {
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn() || !(colony.getWorld() instanceof ServerLevel level)
              || !(colony.getRaiderManager() instanceof RaidManager raids) || raids.getLastRaid() == null)
        {
            return;
        }
        final ColonyGrounds grounds = ColonyGrounds.get(level);
        final long[] pay = grounds.raidPay(colony.getID());
        if (pay == null || pay[0] != raids.getLastRaid().raidTime || pay[1] <= pay[2])
        {
            return;
        }
        final int told = Milestones.tellOfficers(colony, Component.translatable("colonyautopilot.economy.raidpay", pay[1]).withStyle(ChatFormatting.GOLD));
        if (told == 0)
        {
            return;
        }
        grounds.setRaidPay(colony.getID(), pay[0], pay[1], pay[1]);
        ColonyAutopilot.LOGGER.info("[{}] the raid is over — its raiders paid {} ColonyBucks ({} of the {} it sent) — {} officer(s) told", colony.getName(), pay[1],
          pay[1], raids.getLastRaid().raiderAmount, told);
    }

    private static int extractBucks(final IItemHandler handler)
    {
        if (handler == null)
        {
            return 0;
        }
        int bucks = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(ModItems.COLONY_BUCKS.get()))
            {
                bucks += handler.extractItem(slot, stack.getCount(), false).getCount();
            }
        }
        return bucks;
    }

    @SubscribeEvent
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event)
    {
        final ItemStack held = event.getItemStack();
        if (!held.is(ModItems.COLONY_BUCKS.get()) || !event.getEntity().isShiftKeyDown())
        {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            if (event.getLevel().getBlockState(event.getPos()).is(ModBlocks.blockHutTownHall))
            {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }
        if (!AutopilotConfig.masterOn())
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, event.getPos());
        final IBuilding hall = colony == null ? null : colony.getServerBuildingManager().getTownHall();
        if (hall == null || !hall.getPosition().equals(event.getPos()))
        {
            return;
        }
        final int bucks = held.getCount();
        held.shrink(bucks);
        deposit(colony, level, bucks, Line.DEPOSITS);
        event.getEntity().displayClientMessage(Component.literal(bucks + " ColonyBucks paid into the treasury of " + colony.getName()), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static void retain(final Set<ColonyId> live)
    {
        ColonyAutopilot.retainColonies(live, WAITING_REQUESTS, WAITING_RESEARCH, SPENT_TODAY, WAITING_BUILDS, WAITING_CURES, WAITING_SPORE_CURES);
    }

    static void forget(final IColony colony)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        WAITING_REQUESTS.remove(key);
        WAITING_RESEARCH.remove(key);
        SPENT_TODAY.remove(key);
        WAITING_BUILDS.remove(key);
        WAITING_CURES.remove(key);
        WAITING_SPORE_CURES.remove(key);
    }
}
