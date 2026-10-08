// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutopilotCommands
{
    private final GrowthDirector growth;
    private final GolemKeeper golems;

    private static final Set<String> WORLD_TWEAK_KEYS = Set.of(
      "tweaks.workersWorkInRain", "tweaks.disableVanillaPatrols", "tweaks.disableFireTick", "tweaks.spawnChunkRadius");

    public AutopilotCommands(final GrowthDirector growth, final GolemKeeper golems)
    {
        this.growth = growth;
        this.golems = golems;
    }

    @SubscribeEvent
    public void onRegisterCommands(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
          Commands.literal("colonyautopilot")
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("report").executes(context -> report(context.getSource())))
            .then(Commands.literal("on").requires(src -> src.hasPermission(2)).executes(context -> setMaster(context.getSource(), true)))
            .then(Commands.literal("off").requires(src -> src.hasPermission(2)).executes(context -> setMaster(context.getSource(), false)))
            .then(Commands.literal("expansion").requires(src -> src.hasPermission(2))
              .then(Commands.literal("on").executes(context -> setExpansion(context.getSource(), true)))
              .then(Commands.literal("off").executes(context -> setExpansion(context.getSource(), false))))
            .then(Commands.literal("golems").requires(src -> src.hasPermission(2))
              .then(Commands.literal("on").executes(context -> setGolems(context.getSource(), true)))
              .then(Commands.literal("off").executes(context -> setGolems(context.getSource(), false))))

            .then(Commands.literal("colony").requires(src -> src.hasPermission(2))
              .then(Commands.literal("settings")
                .executes(context -> colonySettings(context.getSource(), null, null))
                .then(Commands.argument("section", StringArgumentType.word())
                  .executes(context -> colonySettings(context.getSource(), null, StringArgumentType.getString(context, "section")))))
              .then(Commands.literal("set")
                .then(Commands.argument("key", StringArgumentType.word())
                  .then(Commands.argument("value", StringArgumentType.word())
                    .executes(context -> colonySet(context.getSource(), null, StringArgumentType.getString(context, "key"), StringArgumentType.getString(context, "value"))))))
              .then(Commands.literal("reset")
                .then(Commands.argument("key", StringArgumentType.word())
                  .executes(context -> colonyReset(context.getSource(), null, StringArgumentType.getString(context, "key")))))
              .then(Commands.literal("roads")
                .executes(context -> colonyRoads(context.getSource(), null, false))
                .then(Commands.literal("lift").executes(context -> colonyRoads(context.getSource(), null, true))))
              .then(Commands.literal("treasury")
                .then(Commands.literal("add")
                  .then(Commands.argument("bucks", IntegerArgumentType.integer(1))
                    .executes(context -> colonyTreasury(context.getSource(), null, IntegerArgumentType.getInteger(context, "bucks"))))))
              .then(Commands.argument("id", IntegerArgumentType.integer(1))
                .then(Commands.literal("settings")
                  .executes(context -> colonySettings(context.getSource(), IntegerArgumentType.getInteger(context, "id"), null))
                  .then(Commands.argument("section", StringArgumentType.word())
                    .executes(context -> colonySettings(context.getSource(), IntegerArgumentType.getInteger(context, "id"), StringArgumentType.getString(context, "section")))))
                .then(Commands.literal("set")
                  .then(Commands.argument("key", StringArgumentType.word())
                    .then(Commands.argument("value", StringArgumentType.word())
                      .executes(context -> colonySet(context.getSource(), IntegerArgumentType.getInteger(context, "id"), StringArgumentType.getString(context, "key"), StringArgumentType.getString(context, "value"))))))
                .then(Commands.literal("reset")
                  .then(Commands.argument("key", StringArgumentType.word())
                    .executes(context -> colonyReset(context.getSource(), IntegerArgumentType.getInteger(context, "id"), StringArgumentType.getString(context, "key")))))
                .then(Commands.literal("roads")
                  .executes(context -> colonyRoads(context.getSource(), IntegerArgumentType.getInteger(context, "id"), false))
                  .then(Commands.literal("lift").executes(context -> colonyRoads(context.getSource(), IntegerArgumentType.getInteger(context, "id"), true))))
                .then(Commands.literal("treasury")
                  .then(Commands.literal("add")
                    .then(Commands.argument("bucks", IntegerArgumentType.integer(1))
                      .executes(context -> colonyTreasury(context.getSource(), IntegerArgumentType.getInteger(context, "id"),
                        IntegerArgumentType.getInteger(context, "bucks"))))))))
            .then(Commands.literal("problems")
              .executes(context -> problems(context.getSource()))
              .then(Commands.literal("on").requires(src -> src.hasPermission(2)).executes(context -> setProblems(context.getSource(), true)))
              .then(Commands.literal("off").requires(src -> src.hasPermission(2)).executes(context -> setProblems(context.getSource(), false)))
              .then(Commands.literal("goto")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                  .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                      .executes(context -> gotoStuckSpot(context.getSource(), new BlockPos(IntegerArgumentType.getInteger(context, "x"),
                        IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z")))))))))
            .then(Commands.literal("settings")
              .executes(context -> settings(context.getSource(), null))
              .then(Commands.argument("section", StringArgumentType.word())
                .executes(context -> settings(context.getSource(), StringArgumentType.getString(context, "section")))))
            .then(Commands.literal("set").requires(src -> src.hasPermission(2))
              .then(Commands.argument("key", StringArgumentType.word())
                .then(Commands.argument("value", StringArgumentType.greedyString())
                  .executes(context -> setValue(context.getSource(),
                    StringArgumentType.getString(context, "key"), StringArgumentType.getString(context, "value"))))))
            .then(Commands.literal("zone")
              .then(Commands.literal("wand").executes(context -> giveWand(context.getSource())))
              .then(Commands.literal("list").executes(context -> listZones(context.getSource())))
              .then(Commands.literal("clear").requires(src -> src.hasPermission(2)).executes(context -> clearZones(context.getSource()))))
            .then(Commands.literal("exchange").executes(context -> openExchange(context.getSource())))

            .then(Commands.literal("treasury")
              .executes(context -> colonyTreasury(context.getSource(), null, 0))
              .then(Commands.argument("id", IntegerArgumentType.integer(1))
                .executes(context -> colonyTreasury(context.getSource(), IntegerArgumentType.getInteger(context, "id"), 0))))
            .then(Commands.literal("guide").executes(context -> giveGuide(context.getSource()))));

    }

    private static int problems(final CommandSourceStack source)
    {
        if (!AutopilotConfig.PROBLEMS_ENABLED.get())
        {
            source.sendSuccess(() -> Component.literal("The problem ledger is OFF — '/colonyautopilot problems on' starts keeping one."), false);
            return 0;
        }
        final var entries = ProblemLedger.snapshot();
        if (entries.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No problems on record — nothing has asked for a human's eyes."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("== problems on record (last " + entries.size() + ", oldest first) =="), false);
        for (final String entry : entries)
        {
            source.sendSuccess(() -> withGoto(entry), false);
        }
        return entries.size();
    }

    private static final Pattern STUCK_SPOT = Pattern.compile("stuck around (-?\\d+), (-?\\d+), (-?\\d+)");

    private static Component withGoto(final String entry)
    {
        final Matcher spot = STUCK_SPOT.matcher(entry);
        if (!spot.find())
        {
            return Component.literal(entry);
        }
        final BlockPos pos = new BlockPos(Integer.parseInt(spot.group(1)), Integer.parseInt(spot.group(2)), Integer.parseInt(spot.group(3)));
        if (ProblemLedger.stuckSpot(pos) == null)
        {
            return Component.literal(entry);
        }
        final String command = "/colonyautopilot problems goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return Component.literal(entry + " ").append(Component.literal("[go there]").withStyle(Style.EMPTY
          .withColor(ChatFormatting.AQUA).withUnderlined(true)
          .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
          .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport to " + pos.toShortString())))));
    }

    private static int gotoStuckSpot(final CommandSourceStack source, final BlockPos pos)
    {
        final ServerPlayer player = source.getPlayer();
        final GlobalPos spot = ProblemLedger.stuckSpot(pos);
        final ServerLevel level = spot == null ? null : source.getServer().getLevel(spot.dimension());
        if (player == null || level == null)
        {
            source.sendFailure(Component.literal("That is not a stuck spot on record — open '/colonyautopilot problems' and click one there."));
            return 0;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (!source.hasPermission(2) && (colony == null || !colony.getPermissions().hasPermission(player, Action.TELEPORT_TO_COLONY)))
        {
            source.sendFailure(Component.literal("Only players the colony there lets teleport in (Friend rank and up) can go to that spot."));
            return 0;
        }
        player.teleportTo(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to the stuck spot at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int setProblems(final CommandSourceStack source, final boolean enabled)
    {
        AutopilotConfig.PROBLEMS_ENABLED.set(enabled);
        AutopilotConfig.SPEC.save();
        if (!enabled)
        {
            ProblemLedger.clear();
        }
        source.sendSuccess(() -> Component.literal(enabled
          ? "The problem ledger is ON — anything that needs a human's eyes lands in '/colonyautopilot problems'."
          : "The problem ledger is OFF and cleared — the server log still records everything."), false);
        ColonyAutopilot.LOGGER.info("Colony Autopilot problem ledger set to {} by {}", enabled ? "ON" : "OFF", source.getTextName());
        return 1;
    }

    private static int setMaster(final CommandSourceStack source, final boolean enabled)
    {

        WorldSwitches.get(source.getServer()).setMaster(enabled);
        AutopilotConfig.setWorldMaster(enabled);
        if (!enabled)
        {

            GrowthDirector.releaseHeldGuardPosts();
        }

        ColonyAutopilot.applyWorldTweaks(source.getServer());
        ColonyAutopilot.applySpawnCapTweak();
        ColonyAutopilot.applyPotionDurationTweak();
        reply(source, Component.literal(enabled
          ? "Colony Autopilot is ON in this world — the villages run themselves again."
          : "Colony Autopilot is OFF in this world — the villages are on their own now (MineColonies rules)."),
          enabled ? "" : "The guard posts the mod held go back to MineColonies' own hiring; the gamerules and the MineColonies setting it changed go back to what they were.",
          SAVED_WHERE,
          enabled ? "" : "'/colonyautopilot on' brings it back.");
        ColonyAutopilot.LOGGER.info("Colony Autopilot master switch set to {} in this world by {}", enabled ? "ON" : "OFF", source.getTextName());
        return 1;
    }

    private static int setExpansion(final CommandSourceStack source, final boolean enabled)
    {
        AutopilotConfig.EXPANSION_ENABLED.set(enabled);
        AutopilotConfig.SPEC.save();
        reply(source, Component.literal(enabled
          ? "Expansion resumed — the growth plan continues."
          : "Expansion halted — no new buildings will be placed."),
          enabled ? masterCaveat() : "Guard towers still rise where coverage is missing; upgrades, repairs and outfitting continue.",
          ownValuesNote(source, AutopilotConfig.pathOf(AutopilotConfig.EXPANSION_ENABLED)));
        ColonyAutopilot.LOGGER.info("Colony Autopilot expansion set to {} by {}", enabled ? "ON" : "OFF", source.getTextName());
        return 1;
    }

    private int setGolems(final CommandSourceStack source, final boolean enabled)
    {
        AutopilotConfig.VILLAGE_GOLEM.set(enabled);
        AutopilotConfig.SPEC.save();
        final int removed = enabled ? 0 : golems.dismissAll();
        reply(source, Component.literal(enabled
          ? "Village golems are ON — the town hall and every blacksmith get their iron guardians on the keeper's next pass."
          : "Village golems are OFF — no more forging, and " + removed + " of the keeper's golem" + (removed == 1 ? "" : "s") + " left the village."),
          enabled ? masterCaveat() : "A golem out in unloaded chunks stays until you run this again near it.",
          ownValuesNote(source, AutopilotConfig.pathOf(AutopilotConfig.VILLAGE_GOLEM)));
        ColonyAutopilot.LOGGER.info("Colony Autopilot village golems set to {} by {}{}", enabled ? "ON" : "OFF", source.getTextName(),
          enabled ? "" : " (" + removed + " dismissed)");
        return 1;
    }

    private static String ownValuesNote(final CommandSourceStack source, final String path)
    {
        final java.util.List<String> names = ownValues(source, path);
        return names.isEmpty() ? ""
                 : " Colonies with their own value keep it: " + String.join(", ", names) + " — '/colonyautopilot colony <id> reset " + path + "' hands one back.";
    }

    private static java.util.List<String> ownValues(final CommandSourceStack source, final String path)
    {
        final java.util.List<String> names = new java.util.ArrayList<>();
        final ColonySettings.Key key = ColonySettings.find(path);

        if (key == null || key.scope() == ColonySettings.Scope.SERVER || ColonySettings.locked(key))
        {
            return names;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (ColonySettings.isOverridden(colony, key))
            {

                names.add(colony.getName() + " (#" + colony.getID()
                            + (colony.getDimension().equals(source.getLevel().dimension()) ? "" : " in " + colony.getDimension().location())
                            + ": " + ColonySettings.effective(colony, key) + ")");
            }
        }
        return names;
    }

    private static String ownValuesTag(final CommandSourceStack source, final String path, final boolean capped)
    {
        final java.util.List<String> names = ownValues(source, path);
        if (names.isEmpty())
        {
            return "";
        }
        return capped && names.size() > 3 ? "  [own value in " + names.size() + " colonies]" : "  [own value in " + String.join(", ", names) + "]";
    }

    private static MutableComponent header(final String text)
    {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent dim(final String text)
    {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent note(final String text)
    {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent value(final Object value)
    {
        if (value instanceof MutableComponent ready)
        {
            return ready;
        }
        final ChatFormatting colour = Boolean.TRUE.equals(value) ? ChatFormatting.GREEN
                                        : Boolean.FALSE.equals(value) ? ChatFormatting.RED : ChatFormatting.YELLOW;
        return Component.literal(String.valueOf(value)).withStyle(colour);
    }

    private static MutableComponent keyLine(final String path, final Object current, final String tag, final String meta)
    {
        final MutableComponent line = Component.literal(path).withStyle(ChatFormatting.WHITE).append(dim(" = ")).append(value(current));
        if (!tag.isEmpty())
        {
            line.append(note(tag));
        }
        return meta.isEmpty() ? line : line.append(dim(meta));
    }

    private static void reply(final CommandSourceStack source, final Component main, final String... notes)
    {
        source.sendSuccess(() -> main, false);
        for (final String text : notes)
        {
            if (text != null && !text.isBlank())
            {
                source.sendSuccess(() -> note(text.trim()), false);
            }
        }
    }

    private static String masterCaveat()
    {
        return AutopilotConfig.masterOn() ? "" : " (the master switch is OFF in this world — nothing runs until '/colonyautopilot on')";
    }

    private static final String SAVED_WHERE =
      "Saved with this world; your other worlds keep their own switch, and a world never switched follows master.enabled in the server config.";

    private static int settings(final CommandSourceStack source, final String section)
    {
        final UnmodifiableConfig spec = AutopilotConfig.SPEC.getSpec();
        final UnmodifiableConfig values = AutopilotConfig.SPEC.getValues();
        if (section == null)
        {
            source.sendSuccess(() -> header("== config sections — '/colonyautopilot settings <section>' lists one =="), false);
            for (final UnmodifiableConfig.Entry entry : spec.entrySet())
            {
                if (entry.getRawValue() instanceof UnmodifiableConfig sub)
                {
                    final String line = entry.getKey() + "  (" + sub.entrySet().size() + " keys)";
                    source.sendSuccess(() -> Component.literal(line), false);
                }
            }
            source.sendSuccess(() -> dim("The file is config/colonyautopilot-server.toml; ops change a key live with '/colonyautopilot set <section.key> <value>'."), false);
            return 1;
        }
        if (spec.get(section) instanceof ModConfigSpec.ValueSpec single)
        {
            final Object current = values.get(section) instanceof ModConfigSpec.ConfigValue<?> value ? shown(source, section, value.get()) : "?";
            source.sendSuccess(() -> keyLine(section, current, ownValuesTag(source, section, false), describe(single)), false);
            final String meaning = meaning(single);
            if (!meaning.isEmpty())
            {
                source.sendSuccess(() -> dim(meaning), false);
            }
            return 1;
        }
        if (!(spec.get(section) instanceof UnmodifiableConfig sub))
        {
            source.sendFailure(Component.literal("No config section '" + section + "' — '/colonyautopilot settings' lists them."));
            return 0;
        }
        source.sendSuccess(() -> header("== [" + section + "] =="), false);
        int shown = 0;
        for (final UnmodifiableConfig.Entry entry : sub.entrySet())
        {
            if (!(entry.getRawValue() instanceof ModConfigSpec.ValueSpec valueSpec))
            {
                continue;
            }
            final String path = section + "." + entry.getKey();
            final Object current = values.get(path) instanceof ModConfigSpec.ConfigValue<?> value ? shown(source, path, value.get()) : "?";
            source.sendSuccess(() -> keyLine(path, current, ownValuesTag(source, path, true), describe(valueSpec)), false);
            shown++;
        }
        source.sendSuccess(() -> dim("'/colonyautopilot settings <section.key>' says what a key does; the town hall page shows each colony's switches and numbers with their descriptions — lists and text keys live in the file only."), false);
        return shown;
    }

    private static Object shown(final CommandSourceStack source, final String path, final Object configured)
    {
        if (!"master.enabled".equals(path))
        {
            return configured;
        }

        final Boolean world = WorldSwitches.get(source.getServer()).master();
        return world == null
                 ? value(configured).append(dim(" (this world follows the config)"))
                 : value(world).append(dim(" in this world (switched by command; the config default is " + configured + ")"));
    }

    private static String describe(final ModConfigSpec.ValueSpec valueSpec)
    {
        final StringBuilder out = new StringBuilder();
        final ModConfigSpec.Range<?> range = valueSpec.getRange();
        if (range != null)
        {
            out.append("  [").append(range.getMin()).append("..").append(range.getMax()).append(']');
        }
        return out.append("  (default ").append(valueSpec.getDefault()).append(')').toString();
    }

    private static String meaning(final ModConfigSpec.ValueSpec valueSpec)
    {
        final String comment = valueSpec.getComment();
        return comment == null || comment.isBlank() ? "" : com.backwards.colonyautopilot.net.SettingsPayloads.description(comment);
    }

    private int setValue(final CommandSourceStack source, final String key, final String rawValue)
    {
        final UnmodifiableConfig spec = AutopilotConfig.SPEC.getSpec();
        final UnmodifiableConfig values = AutopilotConfig.SPEC.getValues();
        if (!(spec.get(key) instanceof ModConfigSpec.ValueSpec valueSpec) || !(values.get(key) instanceof ModConfigSpec.ConfigValue<?> value))
        {
            source.sendFailure(Component.literal("No config key '" + key + "' — '/colonyautopilot settings <section>' lists them as section.key."));
            return 0;
        }
        final Object parsed;
        try
        {
            parsed = parseLike(valueSpec.getDefault(), rawValue.trim());
        }
        catch (final IllegalArgumentException e)
        {
            source.sendFailure(Component.literal(key + " " + e.getMessage()));
            return 0;
        }
        if (parsed == null)
        {
            source.sendFailure(Component.literal(key + " is a list — edit it in config/colonyautopilot-server.toml."));
            return 0;
        }
        if (!valueSpec.test(parsed))
        {
            source.sendFailure(Component.literal(key + ": '" + rawValue + "' is not allowed" + describe(valueSpec)));
            return 0;
        }
        if ("master.enabled".equals(key))
        {
            return setMaster(source, (Boolean) parsed);
        }
        if ("problems.enabled".equals(key))
        {
            return setProblems(source, (Boolean) parsed);
        }
        if ("tweaks.villageGolem".equals(key))
        {
            return setGolems(source, (Boolean) parsed);
        }
        if ("growth.expansion".equals(key))
        {
            return setExpansion(source, (Boolean) parsed);
        }
        final Object before = value.get();
        @SuppressWarnings("unchecked")
        final ModConfigSpec.ConfigValue<Object> writable = (ModConfigSpec.ConfigValue<Object>) value;
        writable.set(parsed);
        AutopilotConfig.SPEC.save();
        final String when;
        if ("tweaks.hostileSpawnCap".equals(key))
        {
            ColonyAutopilot.applySpawnCapTweak();
            when = "saved and applied" + masterCaveat();
        }
        else if ("tweaks.potionDurationMultiplier".equals(key))
        {
            ColonyAutopilot.applyPotionDurationTweak();
            when = "saved and applied" + masterCaveat();
        }
        else if (WORLD_TWEAK_KEYS.contains(key))
        {
            ColonyAutopilot.applyWorldTweaks(source.getServer());
            when = "saved and applied" + masterCaveat();
        }
        else if ("economy.progressionMode".equals(key) && AutopilotConfig.ECONOMY_PROGRESSION_MODE_LOCKED.get())
        {

            when = "saved, but economy.progressionModeLocked holds every colony in Progression Mode";
        }
        else
        {
            when = "saved; takes effect on the next pass";
        }
        reply(source, Component.literal(key).withStyle(ChatFormatting.WHITE).append(dim(": ")).append(value(before))
          .append(dim(" -> ")).append(value(parsed)).append(dim("  (" + when + ")")), ownValuesNote(source, key));
        ColonyAutopilot.LOGGER.info("Colony Autopilot config {} set to {} (was {}) by {}", key, parsed, before, source.getTextName());
        return 1;
    }

    private static IColony colonyFor(final CommandSourceStack source, final Integer id)
    {
        if (id != null)
        {
            final IColony here = IColonyManager.getInstance().getColonyByWorld(id, source.getLevel());
            if (here != null)
            {
                return here;
            }
            IColony elsewhere = null;
            for (final IColony colony : IColonyManager.getInstance().getAllColonies())
            {
                if (colony.getID() == id)
                {
                    if (elsewhere != null)
                    {
                        return null;
                    }
                    elsewhere = colony;
                }
            }
            return elsewhere;
        }
        if (source.getEntity() == null)
        {
            return null;
        }
        return IColonyManager.getInstance().getColonyByPosFromWorld(source.getLevel(), BlockPos.containing(source.getPosition()));
    }

    private static String noColony(final CommandSourceStack source, final Integer id, final String usage)
    {
        if (id != null)
        {
            return "No colony #" + id + " in " + source.getLevel().dimension().location() + " and no single one elsewhere — colony ids count per dimension: run it from inside that colony's dimension.";
        }
        if (source.getEntity() == null)
        {
            return "From the console give the colony's id: /colonyautopilot colony <id> " + usage + ".";
        }
        return "No colony here — stand inside one, or give its id: /colonyautopilot colony <id> " + usage + ".";
    }

    private static int colonySet(final CommandSourceStack source, final Integer id, final String path, final String value)
    {
        final IColony colony = colonyFor(source, id);
        if (colony == null)
        {
            source.sendFailure(Component.literal(noColony(source, id, "set <key> <value>")));
            return 0;
        }
        final ColonySettings.Key key = ColonySettings.find(path);
        if (key == null)
        {
            source.sendFailure(Component.literal("No config key '" + path + "' — '/colonyautopilot colony settings <section>' lists them as section.key."));
            return 0;
        }

        final String refused = ColonySettings.set(colony, key, value, source.getTextName(), false);
        if (refused != null)
        {
            source.sendFailure(Component.literal(path + ": " + refused));
            return 0;
        }
        source.sendSuccess(() -> keyLine(path, ColonySettings.effective(colony, key), "",
          "  for " + colony.getName() + " (server default " + ColonySettings.serverValue(key) + "); takes effect on the next pass"), true);
        return 1;
    }

    private int colonyRoads(final CommandSourceStack source, final Integer id, final boolean lift)
    {
        final IColony colony = colonyFor(source, id);
        if (colony == null)
        {
            source.sendFailure(Component.literal(noColony(source, id, lift ? "roads lift" : "roads")));
            return 0;
        }
        if (lift)
        {
            final int[] lifted = growth.liftRoads(colony);
            source.sendSuccess(() -> Component.literal(lifted[0] == 0 ? colony.getName() + " has no roads on record."
                : "Lifted " + lifted[0] + " road" + (lifted[0] == 1 ? "" : "s") + " of " + colony.getName() + " (" + lifted[1] + " blocks); no road is laid until 'roads' or the next boot."), true);
            return lifted[0];
        }
        if (!AutopilotConfig.masterOn())
        {
            source.sendFailure(Component.literal("The autopilot is off (/colonyautopilot on) — nothing lays roads until it is on."));
            return 0;
        }
        if (!AutopilotConfig.get(colony, AutopilotConfig.BUILD_PATHS))
        {
            source.sendFailure(Component.literal("Roads are off for " + colony.getName() + " (growth.buildPaths) — turn them on first."));
            return 0;
        }
        final int queued = growth.relayRoads(colony);
        source.sendSuccess(() -> Component.literal(queued == 0 ? colony.getName() + " has no finished building with a road to lay."
            : "Laying the roads of " + queued + " building" + (queued == 1 ? "" : "s") + " of " + colony.getName() + " again, two a tick, in passes until they settle — the log names each door and any road that does not join up."), true);
        return queued;
    }

    private static void ledgerDay(final CommandSourceStack source, final String name, final long day, final long[] ledger, final int from, final int perBuck)
    {
        final StringBuilder earned = new StringBuilder();
        final StringBuilder spent = new StringBuilder();
        long in = 0;
        long out = 0;
        for (final Treasury.Line line : Treasury.Line.values())
        {
            final long tally = ledger[from + line.ordinal()];
            if (tally <= 0 || line == Treasury.Line.EXCHANGE)
            {
                continue;
            }
            final StringBuilder side = line.income ? earned : spent;
            side.append(side.isEmpty() ? "" : ", ").append(line.label).append(' ').append(bucksText(tally, perBuck));
            if (line.income)
            {
                in += tally;
            }
            else
            {
                out += tally;
            }
        }
        final long exchanged = ledger[from + Treasury.Line.EXCHANGE.ordinal()];
        final String sums = in == 0 && out == 0 && exchanged == 0 ? "nothing earned or spent"
                              : "earned " + bucksText(in, perBuck) + (earned.isEmpty() ? "" : " (" + earned + ")") + ", spent " + bucksText(out, perBuck)
                                  + (spent.isEmpty() ? "" : " (" + spent + ")")
                                  + (exchanged > 0 ? "; players sold " + bucksText(exchanged, perBuck) + " at the exchange" : "");
        source.sendSuccess(() -> keyLine("ledger " + name, "day " + day + ": " + sums, "", ""), false);
    }

    private static String bucksText(final long credits, final int perBuck)
    {
        return String.format(java.util.Locale.ROOT, "%.2f", credits / (double) perBuck);
    }

    private static int colonyTreasury(final CommandSourceStack source, final Integer id, final int addBucks)
    {
        final IColony colony = colonyFor(source, id);
        if (colony == null)
        {

            source.sendFailure(Component.literal(addBucks > 0 || id != null ? noColony(source, id, "treasury add <bucks>")
                                                   : source.getEntity() == null ? "From the console give the colony's id: /colonyautopilot treasury <id>."
                                                   : "No colony here — stand inside one, or give its id: /colonyautopilot treasury <id>."));
            return 0;
        }
        final ServerPlayer player = source.getPlayer();

        if (player != null && !source.hasPermission(2) && !com.backwards.colonyautopilot.net.SettingsPayloads.mayLook(colony, player))
        {
            source.sendFailure(Component.literal("Only members of " + colony.getName() + " may read its treasury."));
            return 0;
        }

        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            source.sendFailure(Component.literal("The dimension of " + colony.getName() + " is not loaded, and its treasury is kept with it — try again once it is."));
            return 0;
        }
        final int perBuck = AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get();
        if (addBucks > 0)
        {
            Treasury.deposit(colony, level, addBucks, Treasury.Line.RELIEF);

            ColonyAutopilot.LOGGER.info("[{}] {} ColonyBucks added to the treasury by {} — treasury {} bucks and {} items",
              colony.getName(), addBucks, source.getTextName(), Treasury.bucks(colony), Treasury.items(colony));
            source.sendSuccess(() -> Component.literal("Added " + addBucks + " ColonyBucks to the treasury of " + colony.getName() + "."), true);
        }
        final boolean metered = AutopilotConfig.progressionMode(colony);
        final int requests = Treasury.waitingRequests(colony);
        final int research = Treasury.waitingResearch(colony);
        final int cures = Treasury.waitingCures(colony);
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();

        final String payIn = hall == null ? "No town hall stands, so nothing can be deposited until one does."
                               : !AutopilotConfig.masterOn() ? "The autopilot is off in this world: ColonyBucks put in the town hall at "
                                   + hall.getPosition().toShortString() + " wait there until '/colonyautopilot on'."
                               : !metered ? "Progression Mode is off: ColonyBucks put in the town hall at " + hall.getPosition().toShortString()
                                   + " wait there until it is on; a sneak-right-click on the Town Hall block holding them pays them in now,"
                                   + " as does a citizen who dies with bucks on him."
                               : "Deposit ColonyBucks in the town hall at " + hall.getPosition().toShortString() + ": put them by hand in any rack of the town"
                                   + " hall, feed the Town Hall block there with a hopper or pipe, or sneak-right-click it holding them. Bucks on the warehouse"
                                   + " racks are collected too.";
        source.sendSuccess(() -> header("== " + colony.getName() + " — treasury =="), false);
        source.sendSuccess(() -> keyLine("treasury", Treasury.bucks(colony) + " ColonyBucks", "",
          "  (a buck buys " + perBuck + " items)"), false);

        if (AutopilotConfig.masterOn() && metered)
        {
            source.sendSuccess(() -> keyLine("waiting for funds",
              requests + " request" + (requests == 1 ? "" : "s") + ", " + research + " research, " + cures + " cure" + (cures == 1 ? "" : "s"), "", ""), false);

            final long reserved = Treasury.reservedCredits(colony);
            if (reserved > 0)
            {
                source.sendSuccess(() -> keyLine("saving", Treasury.savedBucks(colony) + " of " + Treasury.savingBucks(colony)
                  + " ColonyBucks for research '" + Treasury.reservedFor(colony) + "'", "", "  (every other spender waits on the rest)"), false);
            }

            final Treasury.Goal goal = Treasury.savingFor(colony);
            if (goal != null)
            {
                source.sendSuccess(() -> keyLine("saving for", goal.what() + ": " + bucksText(goal.price(), perBuck) + " ColonyBucks"
                  + (goal.stocked() < goal.price() ? ", " + bucksText(goal.stocked(), perBuck) + " with the exchange's goods it needs, such as "
                                                      + (goal.good() == null ? "?" : new ItemStack(goal.good()).getHoverName().getString()) + ", stocked in " + goal.where() : ""),
                  "", ""), false);
            }
        }
        else
        {
            source.sendSuccess(() -> dim("Nothing is charged: Progression Mode or the autopilot is off."), false);
        }

        final long[] ledger = Treasury.ledger(colony);
        ledgerDay(source, "today", ledger[0], ledger, 1, perBuck);
        if (ledger[0] > 0)
        {
            ledgerDay(source, "yesterday", ledger[0] - 1, ledger, 1 + Treasury.Line.values().length, perBuck);
        }
        source.sendSuccess(() -> keyLine("Progression Mode", metered, "", metered ? masterCaveat() : "  (nothing is charged; the balance keeps)"), false);
        source.sendSuccess(() -> note(payIn), false);
        return 1;
    }

    private static int colonyReset(final CommandSourceStack source, final Integer id, final String path)
    {
        final IColony colony = colonyFor(source, id);
        final ColonySettings.Key key = ColonySettings.find(path);
        if (colony == null || key == null)
        {
            source.sendFailure(Component.literal(colony == null ? noColony(source, id, "reset <key>") : "No config key '" + path + "'."));
            return 0;
        }
        ColonySettings.reset(colony, key, source.getTextName());
        source.sendSuccess(() -> keyLine(path, ColonySettings.effective(colony, key), "", "  for " + colony.getName() + " (the server default again)"), true);
        return 1;
    }

    private static int colonySettings(final CommandSourceStack source, final Integer id, final String section)
    {
        final IColony colony = colonyFor(source, id);
        if (colony == null)
        {
            source.sendFailure(Component.literal(noColony(source, id, "settings [section]")));
            return 0;
        }
        if (section == null)
        {
            final java.util.LinkedHashSet<String> sections = new java.util.LinkedHashSet<>();
            for (final ColonySettings.Key key : ColonySettings.keys())
            {
                sections.add(key.section());
            }
            source.sendSuccess(() -> header("== " + colony.getName() + " — its own settings over the server defaults =="), false);
            source.sendSuccess(() -> Component.literal(String.join(", ", sections)), false);
            source.sendSuccess(() -> dim("'/colonyautopilot colony settings <section>' lists the keys; own values are marked (own), server-wide keys (server)."), false);
            return 1;
        }
        int shown = 0;
        for (final ColonySettings.Key key : ColonySettings.keys())
        {
            if (!key.section().equals(section))
            {
                continue;
            }
            if (shown++ == 0)
            {
                source.sendSuccess(() -> header("== " + colony.getName() + " — [" + section + "] =="), false);
            }
            final String tag = key.scope() == ColonySettings.Scope.SERVER || ColonySettings.locked(key) ? "  (server)"
                                 : ColonySettings.isOverridden(colony, key) ? "  (own; default " + ColonySettings.serverValue(key) + ")" : "";
            source.sendSuccess(() -> keyLine(key.path(), ColonySettings.effective(colony, key), tag, ""), false);
        }
        if (shown == 0)
        {
            source.sendFailure(Component.literal("No section '" + section + "'."));
            return 0;
        }
        return 1;
    }

    private static Object parseLike(final Object template, final String text)
    {
        if (template instanceof Boolean)
        {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))
            {
                return Boolean.parseBoolean(text);
            }
            throw new IllegalArgumentException("expects true or false");
        }
        try
        {
            if (template instanceof Integer)
            {
                return Integer.parseInt(text);
            }
            if (template instanceof Long)
            {
                return Long.parseLong(text);
            }
            if (template instanceof Double)
            {
                return Double.parseDouble(text);
            }
        }
        catch (final NumberFormatException e)
        {
            throw new IllegalArgumentException("expects a number like " + template);
        }
        if (template instanceof String)
        {
            return text;
        }
        return null;
    }

    private int status(final CommandSourceStack source)
    {
        source.sendSuccess(() -> Component.literal("Colony Autopilot by " + Maker.NAME), false);
        final var colonies = IColonyManager.getInstance().getAllColonies();
        if (colonies.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No colonies yet — place a town hall and the autopilot takes it from there."), false);
            return 0;
        }
        for (final IColony colony : colonies)
        {
            int built = 0;
            int placed = 0;
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                placed++;
                if (building.getBuildingLevel() > 0)
                {
                    built++;
                }
            }
            final int citizens = colony.getCitizenManager().getCurrentCitizenCount();
            final int beds = colony.getCitizenManager().getMaxCitizens();
            final int researchCap = (int) colony.getCitizenManager().maxCitizensFromResearch();
            final int orders = colony.getWorkManager().getWorkOrders().size();
            final int researching = colony.getResearchManager().getResearchTree().getResearchInProgress().size();

            final String header = "== " + colony.getName() + " ==";
            final String vitals = "citizens " + citizens + "/" + beds + " (research allows " + researchCap + ")"
                                    + " | buildings " + built + " built, " + (placed - built) + " under way"
                                    + " | work orders " + orders
                                    + " | researching " + researching;
            final String growthLine = growth.growthStatus(colony);
            source.sendSuccess(() -> Component.literal(header), false);
            source.sendSuccess(() -> Component.literal(vitals), false);
            source.sendSuccess(() -> Component.literal(growthLine), false);
        }
        return colonies.size();
    }

    private int report(final CommandSourceStack source)
    {
        final var colonies = IColonyManager.getInstance().getAllColonies();
        if (colonies.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No colonies yet — place a town hall and the autopilot takes it from there."), false);
            return 0;
        }

        final String war = warPosture(source.getLevel());
        for (final IColony colony : colonies)
        {
            final int citizens = colony.getCitizenManager().getCurrentCitizenCount();
            final int beds = colony.getCitizenManager().getMaxCitizens();
            final int researchCap = (int) colony.getCitizenManager().maxCitizensFromResearch();

            int builds = 0;
            for (final var order : colony.getWorkManager().getWorkOrders().values())
            {
                if (order.getWorkOrderType() == WorkOrderType.BUILD)
                {
                    builds++;
                }
            }
            final int orders = colony.getWorkManager().getWorkOrders().size();

            final GrowthDirector.GuardCensus census = GrowthDirector.censusGuards(colony, false);
            final int knights = census.knights;
            final int archers = census.archers;
            final int druids = census.druids;
            final int knightsPerArcher = AutopilotConfig.get(colony, AutopilotConfig.KNIGHTS_PER_ARCHER);
            final int guardsPerDruid = AutopilotConfig.get(colony, AutopilotConfig.GUARDS_PER_DRUID);

            final int researching = colony.getResearchManager().getResearchTree().getResearchInProgress().size();

            final String header = "== " + colony.getName() + " ==";
            final String pop = "citizens " + citizens + "/" + beds + " beds (research allows " + researchCap + ")";
            final String plan = growth.growthStatus(colony);
            final String work = "work orders: " + orders + " open (" + builds + " new build" + (builds == 1 ? "" : "s")
                                  + ", " + (orders - builds) + " upgrade/repair)";
            final String guardLine = "guards: " + knights + " knight" + (knights == 1 ? "" : "s")
                                       + (census.huscarls > 0 ? " / " + census.huscarls + " huscarl" + (census.huscarls == 1 ? "" : "s") : "")
                                       + (archers > 0 ? " / " + archers + " archer" + (archers == 1 ? "" : "s") : "")
                                       + (census.marksmen > 0 ? " / " + census.marksmen + " marksm" + (census.marksmen == 1 ? "an" : "en") : "")
                                       + (census.gunners > 0 ? " / " + census.gunners + " gunner" + (census.gunners == 1 ? "" : "s") : "")
                                       + " / " + druids + " druid" + (druids == 1 ? "" : "s")
                                       + (census.others > 0 ? " / " + census.others + " other" : "")
                                       + "  (doctrine " + knightsPerArcher + " melee:1 ranged, 1 druid per " + guardsPerDruid + " fighters)";
            final String researchLine = "research: " + researching + " in progress";
            final String warLine = "war: " + war;

            source.sendSuccess(() -> Component.literal(header), false);
            source.sendSuccess(() -> Component.literal(pop), false);
            source.sendSuccess(() -> Component.literal(plan), false);
            source.sendSuccess(() -> Component.literal(work), false);
            source.sendSuccess(() -> Component.literal(guardLine), false);
            source.sendSuccess(() -> Component.literal(researchLine), false);
            source.sendSuccess(() -> Component.literal(warLine), false);
        }

        final var problems = ProblemLedger.snapshot();
        final String problemLine = !AutopilotConfig.PROBLEMS_ENABLED.get()
          ? "problems: the ledger is off ('/colonyautopilot problems on' starts one)"
          : problems.isEmpty()
            ? "problems (all colonies): none on record"
            : "problems (all colonies): " + problems.size() + " on record — latest: " + problems.get(problems.size() - 1)
                + "  (full list: /colonyautopilot problems)";
        source.sendSuccess(() -> Component.literal(problemLine), false);
        return colonies.size();
    }

    private static String warPosture(final ServerLevel level)
    {
        if (!net.neoforged.fml.ModList.get().isLoaded(SporeCompat.MOD_ID))
        {
            return "peacetime — the Spore infection is not installed";
        }
        final boolean inquisition = net.neoforged.fml.ModList.get().isLoaded("sporeinquisition");
        final String after = inquisition ? "the rednight" : "spore creatures may spawn";
        final int graceDays = AutopilotConfig.SPORES_GRACE_DAYS.get();
        if (graceDays <= 0)
        {

            return "no grace configured — " + (inquisition
                     ? "spore creatures spawn from day one, and NO rednight comes: it needs spores.gracePeriodDays of 1 or more (it falls when the grace ends)"
                     : "spore creatures spawn from day one (grace 0)");
        }
        final long graceEnds = graceDays * 24000L;
        final long now = level.getGameTime();
        if (now < graceEnds && !SporeCompat.rednightFallen(level))
        {
            final long daysLeft = (graceEnds - now + 23999L) / 24000L;
            return "grace — about " + daysLeft + " game-day" + (daysLeft == 1 ? "" : "s") + " of quiet remain before " + after;
        }
        final StringBuilder line = new StringBuilder("the quiet years are over — the infection is loose");
        final java.util.OptionalInt corruption = SporeCompat.corruptionScore(level.getServer());
        if (corruption.isPresent())
        {
            line.append("; corruption ").append(corruption.getAsInt()).append("/400");
            final java.util.OptionalInt gap = SporeCompat.nearestEscalationGap(level.getServer());
            if (gap.isPresent())
            {
                line.append(", ").append(gap.getAsInt()).append(" to the next escalation");
            }
        }
        return line.toString();
    }

    private static int giveWand(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.literal("Only a player can be given a Zone Marker."));
            return 0;
        }
        final ItemStack stack = new ItemStack(ModItems.ZONE_MARKER.get());
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Here is your Zone Marker — right-click two corners to mark a"
          + " zone the village builds around; sneak-right-click a zone to delete it."), false);
        return 1;
    }

    private static int giveGuide(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.literal("Only a player can be given the field guide."));
            return 0;
        }
        ItemStack stack = GuideMeCompat.guideStack();
        if (stack.isEmpty())
        {
            stack = FieldGuide.book();
        }
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Here is the Colony Autopilot field guide — the pack's Spore recipes and commands."), false);
        return 1;
    }

    private static int openExchange(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.literal("Only a player can trade at the exchange."));
            return 0;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.serverLevel(), player.blockPosition());
        if (colony == null)
        {
            source.sendFailure(Component.literal("You are not standing in a colony's area — the exchange is the colony's."));
            return 0;
        }
        final String refused = Exchange.open(player, colony);
        if (refused != null)
        {
            source.sendFailure(Component.literal(refused));
            return 0;
        }
        return 1;
    }

    private static int listZones(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.literal("Run this as a player standing in your colony."));
            return 0;
        }
        final ServerLevel level = player.serverLevel();
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, player.blockPosition());
        if (colony == null)
        {
            source.sendFailure(Component.literal("You are not standing in a colony's area."));
            return 0;
        }
        final var boxes = ProtectedZones.get(level).boxesFor(colony.getID());
        if (boxes.isEmpty())
        {
            source.sendSuccess(() -> Component.literal(colony.getName() + " has no protected zones."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("== protected zones for " + colony.getName() + " (" + boxes.size() + ") =="), false);
        for (final SiteSelector.Footprint b : boxes)
        {
            source.sendSuccess(() -> Component.literal("  x " + b.minX() + ".." + b.maxX() + ", z " + b.minZ() + ".." + b.maxZ()
              + "  (" + (b.maxX() - b.minX() + 1) + " x " + (b.maxZ() - b.minZ() + 1) + ")"), false);
        }
        return boxes.size();
    }

    private static int clearZones(final CommandSourceStack source)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.literal("Run this as a player standing in your colony."));
            return 0;
        }
        final ServerLevel level = player.serverLevel();
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, player.blockPosition());
        if (colony == null)
        {
            source.sendFailure(Component.literal("You are not standing in a colony's area."));
            return 0;
        }
        final int removed = ProtectedZones.get(level).clear(colony.getID());
        source.sendSuccess(() -> Component.literal(removed > 0
          ? "Cleared " + removed + " protected zone" + (removed == 1 ? "" : "s") + " from " + colony.getName() + "."
          : colony.getName() + " had no protected zones."), false);
        return removed;
    }
}
