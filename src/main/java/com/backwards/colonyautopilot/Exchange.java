// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.backwards.colonyautopilot.net.SettingsPayloads;
import com.minecolonies.api.colony.IColony;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Exchange implements Merchant
{

    private static final Pattern OFFER_LINE = Pattern.compile("\\s*(\\S+)\\s+x(\\d{1,4})\\s*->\\s*(\\d{1,4})\\s*");

    private static final double REACH = 8.0;

    private static MerchantOffers offers = new MerchantOffers();

    private static boolean offersListEmpty = true;

    private static final Pattern MATERIAL_TAG = Pattern.compile("(ingots|gems|storage_blocks|nuggets|raw_materials|ores|dusts)/([a-z0-9_]+)");

    private record Size(long num, long den)
    {
    }

    private static final Map<String, Size> FORMS = Map.of("c:ingots/%s", new Size(1, 1), "c:gems/%s", new Size(1, 1),
      "c:storage_blocks/%s", new Size(9, 1), "c:storage_blocks/raw_%s", new Size(9, 1), "c:nuggets/%s", new Size(1, 9),
      "c:raw_materials/%s", new Size(1, 1), "c:dusts/%s", new Size(1, 1), "c:ores/%s", new Size(4, 1), "minecraft:%s_ores", new Size(4, 1));

    private static final Map<String, Map<String, Size>> UNTAGGED_MEMBERS = Map.of(
      "netherite", Map.of("minecraft:netherite_scrap", new Size(1, 4), "minecraft:ancient_debris", new Size(3, 4),
        "mysticalagriculture:netherite_essence", new Size(1, 8)),
      "gold", Map.of("minecraft:gilded_blackstone", new Size(2, 1), "minecraft:raw_gold", new Size(10, 3),
        "minecraft:raw_gold_block", new Size(30, 1), "minecraft:golden_horse_armor", new Size(44, 9),
        "#create:stone_types/ochrum", new Size(10, 9), "mysticalagriculture:gold_essence", new Size(1, 2)),
      "diamond", Map.of("minecraft:jukebox", new Size(1, 1), "minecraft:diamond_horse_armor", new Size(4, 1),
        "mysticalagriculture:diamond_essence", new Size(1, 9)));

    private record Term(int bucks, int count, long factorNum, long factorDen)
    {

        long credits(final int perBuck)
        {
            return Math.ceilDiv(Math.ceilDiv((long) bucks * perBuck, count) * factorNum, factorDen);
        }
    }

    private static Map<Item, List<Term>> prices = Map.of();

    private final IColony colony;
    private final ResourceKey<Level> dimension;
    private final Vec3 openedAt;
    private MerchantOffers shown;
    private Player tradingPlayer;

    private Exchange(final IColony colony, final ServerPlayer player, final MerchantOffers shown)
    {
        this.colony = colony;
        this.dimension = player.level().dimension();
        this.openedAt = player.position();
        this.shown = shown;
    }

    static void loadOffers()
    {
        final MerchantOffers parsed = new MerchantOffers();
        final List<String> read = new ArrayList<>();

        final Map<Item, int[]> offered = new LinkedHashMap<>();
        final ItemStack bucks = new ItemStack(ModItems.COLONY_BUCKS.get());
        final List<? extends String> lines = AutopilotConfig.ECONOMY_OFFERS.get();
        for (final String line : lines)
        {
            final Matcher match = OFFER_LINE.matcher(line);
            if (!match.matches())
            {
                ColonyAutopilot.LOGGER.warn("economy.offers: '{}' skipped — write it as '<item id> x<count> -> <bucks>'", line);
                continue;
            }
            final ResourceLocation id = ResourceLocation.tryParse(match.group(1));
            final Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item == Items.AIR)
            {
                ColonyAutopilot.LOGGER.warn("economy.offers: '{}' skipped — no item '{}' on this server", line, match.group(1));
                continue;
            }

            if (item == ModItems.COLONY_BUCKS.get())
            {
                ColonyAutopilot.LOGGER.warn("economy.offers: '{}' skipped — the exchange pays in ColonyBucks and never takes them", line);
                continue;
            }
            final int count = Integer.parseInt(match.group(2));
            final int paid = Integer.parseInt(match.group(3));

            if (count < 1 || count > item.getDefaultMaxStackSize() || paid < 1 || paid > bucks.getMaxStackSize())
            {
                ColonyAutopilot.LOGGER.warn("economy.offers: '{}' skipped — the count must be 1 to {} and the bucks 1 to {}",
                  line, item.getDefaultMaxStackSize(), bucks.getMaxStackSize());
                continue;
            }

            if (offered.putIfAbsent(item, new int[] {count, paid}) != null)
            {
                ColonyAutopilot.LOGGER.warn("economy.offers: '{}' skipped — {} already has an offer, the first one stands", line, id);
                continue;
            }

            parsed.add(new MerchantOffer(new ItemCost(item, count), bucks.copyWithCount(paid), Integer.MAX_VALUE, 0, 0.0F));
            read.add(id + " x" + count + " -> " + paid);
        }
        offers = parsed;
        offersListEmpty = lines.isEmpty();
        ColonyAutopilot.LOGGER.info("The exchange serves {} offer(s) (economy.offers): {}", parsed.size(),
          read.isEmpty() ? "none" : String.join(", ", read));
        loadFamilies(offered);
    }

    private static void loadFamilies(final Map<Item, int[]> offered)
    {
        final Map<Item, List<Term>> priced = new HashMap<>();

        final Map<String, Set<Item>> named = new LinkedHashMap<>();
        offered.forEach((item, offer) -> {
            final Set<String> materials = new LinkedHashSet<>();
            for (final TagKey<Item> tag : new ItemStack(item).getTags().toList())
            {
                final Matcher material = MATERIAL_TAG.matcher(tag.location().getPath());
                if (tag.location().getNamespace().equals("c") && material.matches())
                {
                    materials.add(material.group(1).equals("storage_blocks") && material.group(2).startsWith("raw_")
                                    ? material.group(2).substring("raw_".length()) : material.group(2));
                }
            }
            if (materials.isEmpty())
            {
                priced.computeIfAbsent(item, k -> new ArrayList<>()).add(new Term(offer[1], offer[0], 1, 1));
                named.computeIfAbsent(BuiltInRegistries.ITEM.getKey(item).toString(), k -> new LinkedHashSet<>()).add(item);
                return;
            }
            for (final String material : materials)
            {
                final Map<Item, Size> members = members(material);

                final Size own = members.computeIfAbsent(item, k -> new Size(1, 1));
                members.forEach((member, size) -> priced.computeIfAbsent(member, k -> new ArrayList<>())
                  .add(new Term(offer[1], offer[0], own.den() * size.num(), own.num() * size.den())));
                named.computeIfAbsent(material, k -> new LinkedHashSet<>()).addAll(members.keySet());
            }
        });
        prices = Map.copyOf(priced);

        if (!AutopilotConfig.ECONOMY_EXCHANGE.get())
        {
            ColonyAutopilot.LOGGER.info("In Progression Mode the exchange's goods cost their exchange value — the exchange is off: nothing is priced");
            return;
        }
        final List<String> listed = new ArrayList<>();
        named.forEach((label, members) -> {
            final Set<String> each = new TreeSet<>();

            members.forEach(member -> each.add(BuiltInRegistries.ITEM.getKey(member) + " " + Treasury.cost(new ItemStack(member))));
            listed.add(label + " " + each);
        });

        final double markup = AutopilotConfig.ECONOMY_CONJURE_MARKUP.get();
        ColonyAutopilot.LOGGER.info("In Progression Mode the exchange's goods cost {} their exchange value (markup {}), in credits an item (economy.itemsPerBuck {}) — {} item(s): {}",
          markup == 1.0 ? "once" : markup == 2.0 ? "twice" : markup == 4.0 ? "four times" : markup + " times", markup, AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get(), prices.size(),
          listed.isEmpty() ? "none" : String.join("; ", listed));
    }

    private static Map<Item, Size> members(final String material)
    {
        final Map<Item, Size> members = new HashMap<>();
        FORMS.forEach((form, size) -> {
            final ResourceLocation tag = ResourceLocation.tryParse(form.formatted(material));
            if (tag != null)
            {
                BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag)).forEach(holder -> members.merge(holder.value(), size, Exchange::larger));
            }
        });
        UNTAGGED_MEMBERS.getOrDefault(material, Map.of()).forEach((member, size) -> {
            final boolean tagged = member.startsWith("#");
            final ResourceLocation id = ResourceLocation.tryParse(tagged ? member.substring(1) : member);
            if (id == null)
            {
                return;
            }
            if (tagged)
            {
                BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id)).forEach(holder -> members.merge(holder.value(), size, Exchange::larger));
            }
            else
            {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> members.merge(item, size, Exchange::larger));
            }
        });
        return members;
    }

    private static Size larger(final Size a, final Size b)
    {
        return a.num() * b.den() >= b.num() * a.den() ? a : b;
    }

    static long familyValue(final Item item)
    {
        final List<Term> terms = prices.get(item);
        if (terms == null || !AutopilotConfig.ECONOMY_EXCHANGE.get())
        {
            return 0L;
        }
        final int perBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
        long value = 0L;
        for (final Term term : terms)
        {
            value = Math.max(value, term.credits(perBuck));
        }
        return value;
    }

    public static String open(final ServerPlayer player, final IColony colony)
    {
        if (!AutopilotConfig.masterOn())
        {
            return "The autopilot is off (/colonyautopilot on) — the exchange is closed until it is on.";
        }
        if (!AutopilotConfig.ECONOMY_EXCHANGE.get())
        {
            return "The exchange is closed on this server (economy.exchange).";
        }
        if (!SettingsPayloads.mayLook(colony, player))
        {
            return "Only members of " + colony.getName() + " may trade at its exchange.";
        }

        if (!AutopilotConfig.progressionMode(colony))
        {
            return "The exchange serves colonies in Progression Mode, and " + colony.getName() + " has it off.";
        }
        if (offers.isEmpty())
        {
            return offersListEmpty
              ? "The exchange has nothing to offer: economy.offers is empty."
              : "The exchange has nothing to offer: no line of economy.offers could be read (the server log says why).";
        }
        final Exchange exchange = new Exchange(colony, player, offers.copy());

        exchange.setTradingPlayer(player);

        final OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
          (id, inventory, opener) -> new ExchangeMenu(id, inventory, exchange),
          Component.translatable("colonyautopilot.exchange.title", colony.getName(), Treasury.bucks(colony))));
        if (containerId.isEmpty())
        {
            exchange.setTradingPlayer(null);
            return "The exchange screen could not open.";
        }
        player.sendMerchantOffers(containerId.getAsInt(), exchange.getOffers(), 0, exchange.getVillagerXp(), exchange.showProgressBar(), exchange.canRestock());
        advise(player, colony);
        return null;
    }

    private static void advise(final ServerPlayer player, final IColony colony)
    {
        final Treasury.Goal goal = Treasury.savingFor(colony);
        if (goal == null || goal.good() == null)
        {
            return;
        }
        final int perBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
        final double here = bucksHere(goal.good());
        final String advice = String.format(java.util.Locale.ROOT,
          "The clerk: %s waits on %s. Each %s is worth %.2f ColonyBucks to the colony stocked in %s", colony.getName(), goal.what(),
          new ItemStack(goal.good()).getHoverName().getString(), Treasury.cost(new ItemStack(goal.good())) / (double) perBuck, goal.where())
          + (here < 0 ? "; the exchange does not buy it as it is." : String.format(java.util.Locale.ROOT, ", and %.2f here.", here));
        player.sendSystemMessage(Component.literal(advice).withStyle(net.minecraft.ChatFormatting.GOLD));
        ColonyAutopilot.LOGGER.debug("[{}] the exchange's clerk to {}: {}", colony.getName(), player.getName().getString(), advice);
    }

    private static double bucksHere(final Item item)
    {
        for (final MerchantOffer offer : offers)
        {
            if (offer.getItemCostA().item().value() == item)
            {
                return offer.getResult().getCount() / (double) offer.getItemCostA().count();
            }
        }
        return -1;
    }

    private boolean stillServes(final Player player)
    {
        return AutopilotConfig.live(AutopilotConfig.ECONOMY_EXCHANGE) && AutopilotConfig.progressionMode(colony) && player.isAlive()
                 && player.level().dimension().equals(dimension) && player.position().distanceToSqr(openedAt) <= REACH * REACH
                 && player instanceof ServerPlayer serverPlayer && SettingsPayloads.mayLook(colony, serverPlayer);
    }

    @Override
    public void setTradingPlayer(final Player player)
    {
        this.tradingPlayer = player;
    }

    @Override
    public Player getTradingPlayer()
    {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers()
    {
        return shown;
    }

    @Override
    public void overrideOffers(final MerchantOffers replacement)
    {
        this.shown = replacement;
    }

    @Override
    public void notifyTrade(final MerchantOffer offer)
    {
        final String trader = tradingPlayer == null ? "a player" : tradingPlayer.getName().getString();
        Treasury.noteExchange(colony, offer.getResult().getCount());
        ColonyAutopilot.LOGGER.info("[{}] {} traded {} x{} for {} ColonyBucks at the exchange", colony.getName(), trader,
          BuiltInRegistries.ITEM.getKey(offer.getItemCostA().item().value()), offer.getItemCostA().count(), offer.getResult().getCount());
    }

    @Override
    public void notifyTradeUpdated(final ItemStack stack)
    {

    }

    @Override
    public int getVillagerXp()
    {
        return 0;
    }

    @Override
    public void overrideXp(final int xp)
    {

    }

    @Override
    public boolean showProgressBar()
    {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound()
    {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide()
    {
        return false;
    }

    @Override
    public void openTradingScreen(final Player player, final Component displayName, final int level)
    {
        throw new UnsupportedOperationException("use Exchange.open — a plain MerchantMenu throws on shift-click");
    }

    private static final class ExchangeMenu extends MerchantMenu
    {
        private final Exchange exchange;

        private ExchangeMenu(final int containerId, final Inventory inventory, final Exchange exchange)
        {
            super(containerId, inventory, exchange);
            this.exchange = exchange;
        }

        @Override
        public boolean stillValid(final Player player)
        {
            return super.stillValid(player) && exchange.stillServes(player);
        }

        @Override
        public ItemStack quickMoveStack(final Player player, final int index)
        {
            if (index != RESULT_SLOT)
            {
                return super.quickMoveStack(player, index);
            }
            final Slot slot = this.slots.get(index);
            if (!slot.hasItem())
            {
                return ItemStack.EMPTY;
            }
            final ItemStack moving = slot.getItem();
            if (!inventoryHolds(moving))
            {

                player.displayClientMessage(Component.literal("Make room for " + moving.getCount() + " ColonyBucks"), true);
                return ItemStack.EMPTY;
            }
            final ItemStack taken = moving.copy();
            if (!this.moveItemStackTo(moving, 3, 39, true))
            {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(moving, taken);
            if (moving.isEmpty())
            {
                slot.setByPlayer(ItemStack.EMPTY);
            }
            else
            {
                slot.setChanged();
            }
            if (moving.getCount() == taken.getCount())
            {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, moving);
            return taken;
        }

        private boolean inventoryHolds(final ItemStack stack)
        {
            int room = 0;
            for (int index = 3; index < 39; index++)
            {
                final Slot slot = this.slots.get(index);
                final ItemStack held = slot.getItem();
                if (held.isEmpty())
                {
                    if (slot.mayPlace(stack))
                    {
                        room += slot.getMaxStackSize(stack);
                    }
                }
                else if (stack.isStackable() && ItemStack.isSameItemSameComponents(stack, held))
                {
                    room += Math.max(0, slot.getMaxStackSize(held) - held.getCount());
                }
                if (room >= stack.getCount())
                {
                    return true;
                }
            }
            return false;
        }
    }
}
