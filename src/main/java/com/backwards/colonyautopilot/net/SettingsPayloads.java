// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.net;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.BuilderCap;
import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.ColonySettings;
import com.backwards.colonyautopilot.GrowthDirector;
import com.backwards.colonyautopilot.Treasury;
import com.backwards.colonyautopilot.client.ClientSettingsCache;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public final class SettingsPayloads
{

    private static final int MAX_TEXT = 64;

    private SettingsPayloads()
    {
    }

    public record OpenSettings(ColonyId colony) implements CustomPacketPayload
    {
        public static final Type<OpenSettings> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "open_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenSettings> CODEC =
          StreamCodec.composite(ColonyId.STREAM_CODEC, OpenSettings::colony, OpenSettings::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record ChangeSetting(ColonyId colony, String path, String text, boolean reset) implements CustomPacketPayload
    {
        public static final Type<ChangeSetting> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "change_setting"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChangeSetting> CODEC = StreamCodec.of(
          (buf, msg) -> {
              ColonyId.STREAM_CODEC.encode(buf, msg.colony());
              buf.writeUtf(msg.path(), 128);
              buf.writeUtf(msg.text(), MAX_TEXT);
              buf.writeBoolean(msg.reset());
          },
          buf -> new ChangeSetting(ColonyId.STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readUtf(MAX_TEXT), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record Row(String path, String type, String value, String def, String min, String max, boolean overridden, boolean serverWide, String comment)
    {
        static void write(final RegistryFriendlyByteBuf buf, final Row row)
        {
            buf.writeUtf(row.path(), 128);
            buf.writeUtf(row.type(), 16);
            buf.writeUtf(row.value(), MAX_TEXT);
            buf.writeUtf(row.def(), MAX_TEXT);
            buf.writeUtf(row.min(), MAX_TEXT);
            buf.writeUtf(row.max(), MAX_TEXT);
            buf.writeBoolean(row.overridden());
            buf.writeBoolean(row.serverWide());
            buf.writeUtf(row.comment(), 2048);
        }

        static Row read(final RegistryFriendlyByteBuf buf)
        {
            return new Row(buf.readUtf(128), buf.readUtf(16), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
              buf.readBoolean(), buf.readBoolean(), buf.readUtf(2048));
        }
    }

    private static final int MAX_HOLD = 256;

    private static final int MAX_NAME = 128;

    private static final int MAX_GOAL = 256;

    private static final int MAX_GOOD = 128;

    public record SettingsSnapshot(ColonyId colony, String colonyName, boolean canEdit, List<Row> rows, boolean progression, long treasuryBucks,
                                   int waitingRequests, int waitingResearch, int waitingCures, String placementHold, boolean exchange,
                                   String saving, long savingBucks, long savedBucks, int perBuck, long[] ledger, String goal, long goalPrice,
                                   long goalStocked, String goalGood, String goalWhere, String roads) implements CustomPacketPayload
    {
        public static final Type<SettingsSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "settings_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsSnapshot> CODEC = StreamCodec.of(
          (buf, msg) -> {
              ColonyId.STREAM_CODEC.encode(buf, msg.colony());
              buf.writeUtf(msg.colonyName(), 256);
              buf.writeBoolean(msg.canEdit());
              buf.writeVarInt(msg.rows().size());
              for (final Row row : msg.rows())
              {
                  Row.write(buf, row);
              }
              buf.writeBoolean(msg.progression());
              buf.writeVarLong(msg.treasuryBucks());
              buf.writeVarInt(msg.waitingRequests());
              buf.writeVarInt(msg.waitingResearch());
              buf.writeVarInt(msg.waitingCures());
              buf.writeUtf(msg.placementHold(), MAX_HOLD);
              buf.writeBoolean(msg.exchange());
              buf.writeUtf(msg.saving(), MAX_NAME);
              buf.writeVarLong(msg.savingBucks());
              buf.writeVarLong(msg.savedBucks());
              buf.writeVarInt(msg.perBuck());
              buf.writeVarInt(msg.ledger().length);
              for (final long tally : msg.ledger())
              {
                  buf.writeVarLong(tally);
              }
              buf.writeUtf(msg.goal(), MAX_GOAL);
              buf.writeVarLong(msg.goalPrice());
              buf.writeVarLong(msg.goalStocked());
              buf.writeUtf(msg.goalGood(), MAX_GOOD);
              buf.writeUtf(msg.goalWhere(), MAX_GOOD);
              buf.writeUtf(msg.roads(), MAX_HOLD);
          },
          buf -> {
              final ColonyId colony = ColonyId.STREAM_CODEC.decode(buf);
              final String name = buf.readUtf(256);
              final boolean canEdit = buf.readBoolean();
              final int count = buf.readVarInt();
              final List<Row> rows = new ArrayList<>(Math.min(count, 512));
              for (int i = 0; i < count; i++)
              {
                  rows.add(Row.read(buf));
              }

              final boolean progression = buf.readBoolean();
              final long bucks = buf.readVarLong();
              final int requests = buf.readVarInt();
              final int research = buf.readVarInt();
              final int cures = buf.readVarInt();
              final String hold = buf.readUtf(MAX_HOLD);
              final boolean exchange = buf.readBoolean();
              final String saving = buf.readUtf(MAX_NAME);
              final long savingBucks = buf.readVarLong();
              final long savedBucks = buf.readVarLong();
              final int perBuck = buf.readVarInt();

              final long[] ledger = new long[Math.min(buf.readVarInt(), 64)];
              for (int i = 0; i < ledger.length; i++)
              {
                  ledger[i] = buf.readVarLong();
              }
              return new SettingsSnapshot(colony, name, canEdit, rows, progression, bucks, requests, research, cures, hold, exchange, saving, savingBucks,
                savedBucks, perBuck, ledger, buf.readUtf(MAX_GOAL), buf.readVarLong(), buf.readVarLong(), buf.readUtf(MAX_GOOD), buf.readUtf(MAX_GOOD),
                buf.readUtf(MAX_HOLD));
          });

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public static void register(final RegisterPayloadHandlersEvent event)
    {

        final PayloadRegistrar registrar = event.registrar("7");
        registrar.playToServer(ExchangePayloads.OpenExchange.TYPE, ExchangePayloads.OpenExchange.CODEC, ExchangePayloads::onOpenExchange);
        registrar.playToServer(ExchangePayloads.RelayRoads.TYPE, ExchangePayloads.RelayRoads.CODEC, ExchangePayloads::onRelayRoads);
        registrar.playToServer(OpenSettings.TYPE, OpenSettings.CODEC, SettingsPayloads::onOpen);
        registrar.playToServer(ChangeSetting.TYPE, ChangeSetting.CODEC, SettingsPayloads::onChange);
        registrar.playToClient(SettingsSnapshot.TYPE, SettingsSnapshot.CODEC, (payload, context) -> context.enqueueWork(() -> {

            if (FMLEnvironment.dist.isClient())
            {
                ClientSettingsCache.onSnapshot(payload);
            }
        }));
    }

    private static void onOpen(final OpenSettings payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }
            final IColony colony = resolve(payload.colony(), player);
            if (colony != null)
            {

                PacketDistributor.sendToPlayer(player, mayLook(colony, player) ? snapshot(colony, player) : denied(colony));
            }
        });
    }

    private static void onChange(final ChangeSetting payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }
            final IColony colony = resolve(payload.colony(), player);
            if (colony == null)
            {
                return;
            }
            if (!mayEdit(colony, player))
            {
                player.sendSystemMessage(Component.translatable("colonyautopilot.settings.denied").withStyle(ChatFormatting.RED));

                PacketDistributor.sendToPlayer(player, mayLook(colony, player) ? snapshot(colony, player) : denied(colony));
                return;
            }
            final ColonySettings.Key key = ColonySettings.find(payload.path());
            if (key != null)
            {
                if (payload.reset())
                {
                    ColonySettings.reset(colony, key, player.getGameProfile().getName());
                }
                else
                {
                    final String refused = ColonySettings.set(colony, key, payload.text(), player.getGameProfile().getName(), true);
                    if (refused != null)
                    {
                        player.sendSystemMessage(Component.literal(payload.path() + ": " + refused).withStyle(ChatFormatting.RED));
                    }
                }
            }
            PacketDistributor.sendToPlayer(player, snapshot(colony, player));
        });
    }

    static IColony resolve(final ColonyId id, final ServerPlayer player)
    {
        final ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(id.dimension());
        return level == null ? null : IColonyManager.getInstance().getColonyByWorld(id.id(), level);
    }

    public static boolean mayLook(final IColony colony, final ServerPlayer player)
    {
        return player.hasPermissions(2) || colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS);
    }

    static boolean mayEdit(final IColony colony, final ServerPlayer player)
    {
        return player.hasPermissions(2) || colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
    }

    static SettingsSnapshot denied(final IColony colony)
    {
        return new SettingsSnapshot(new ColonyId(colony.getID(), colony.getDimension()), nameOf(colony), false, List.of(), false, 0L, 0, 0, 0, "", false,
          "", 0L, 0L, 1, new long[0], "", 0L, 0L, "", "", ExchangePayloads.ROADS_DENIED);
    }

    private static String nameOf(final IColony colony)
    {
        final String name = colony.getName();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    static SettingsSnapshot snapshot(final IColony colony, final ServerPlayer player)
    {
        final List<Row> rows = new ArrayList<>();
        for (final ColonySettings.Key key : ColonySettings.keys())
        {
            final String type = key.type() == Boolean.class ? "boolean" : key.type() == Integer.class ? "int" : "double";

            final boolean locked = ColonySettings.locked(key);

            rows.add(new Row(key.path(), type, String.valueOf(ColonySettings.effective(colony, key)), String.valueOf(ColonySettings.serverValue(key)),
              key.min() == null ? "" : String.valueOf(key.min()), key.max() == null ? "" : String.valueOf(key.max()),
              ColonySettings.isOverridden(colony, key) && !locked, key.scope() == ColonySettings.Scope.SERVER || locked, tooltip(key.comment())));
        }
        final boolean progression = AutopilotConfig.masterOn() && AutopilotConfig.progressionMode(colony);

        final String hold = progression ? BuilderCap.holdText(colony) : "";

        final String saving = progression ? Treasury.reservedFor(colony) : "";
        final Treasury.Goal goal = progression ? Treasury.savingFor(colony) : null;

        final String roads = mayEdit(colony, player) ? GrowthDirector.roadsRefused(colony) : ExchangePayloads.ROADS_DENIED;

        return new SettingsSnapshot(new ColonyId(colony.getID(), colony.getDimension()), nameOf(colony), mayEdit(colony, player), rows,
          progression, Treasury.bucks(colony),
          Treasury.waitingRequests(colony), Treasury.waitingResearch(colony), Treasury.waitingCures(colony),
          hold.length() > MAX_HOLD ? hold.substring(0, MAX_HOLD) : hold,

          progression && AutopilotConfig.live(AutopilotConfig.ECONOMY_EXCHANGE),
          saving.length() > MAX_NAME ? saving.substring(0, MAX_NAME) : saving, saving.isEmpty() ? 0L : Treasury.savingBucks(colony),
          saving.isEmpty() ? 0L : Treasury.savedBucks(colony), AutopilotConfig.ECONOMY_ITEMS_PER_BUCK.get(), Treasury.ledger(colony),
          goal == null ? "" : goal.what().length() > MAX_GOAL ? goal.what().substring(0, MAX_GOAL) : goal.what(), goal == null ? 0L : goal.price(),
          goal == null ? 0L : goal.stocked(), goal == null || goal.good() == null ? "" : cut(BuiltInRegistries.ITEM.getKey(goal.good()).toString()),
          goal == null ? "" : cut(goal.where()), roads.length() > MAX_HOLD ? roads.substring(0, MAX_HOLD) : roads);
    }

    private static String cut(final String text)
    {
        return text.length() > MAX_GOOD ? text.substring(0, MAX_GOOD) : text;
    }

    private static final int TOOLTIP_CHARS = 520;

    public static String tooltip(final String comment)
    {
        final String kept = description(comment);
        if (kept.length() <= TOOLTIP_CHARS)
        {
            return kept;
        }
        final int cut = kept.lastIndexOf(". ", TOOLTIP_CHARS);
        return (cut > 80 ? kept.substring(0, cut + 1) : kept.substring(0, TOOLTIP_CHARS)) + " …";
    }

    public static String description(final String comment)
    {
        final StringBuilder kept = new StringBuilder();
        for (final String line : comment.split("\n"))
        {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Default:") || trimmed.startsWith("Range:") || trimmed.startsWith("Allowed Values:"))
            {
                continue;
            }
            kept.append(kept.length() == 0 ? "" : " ").append(trimmed);
        }
        return kept.toString();
    }
}
