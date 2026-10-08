// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.net;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.Exchange;
import com.backwards.colonyautopilot.GrowthDirector;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ExchangePayloads
{
    private ExchangePayloads()
    {
    }

    public record OpenExchange(ColonyId colony) implements CustomPacketPayload
    {
        public static final Type<OpenExchange> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "open_exchange"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenExchange> CODEC =
          StreamCodec.composite(ColonyId.STREAM_CODEC, OpenExchange::colony, OpenExchange::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    static void onOpenExchange(final OpenExchange payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }
            final IColony colony = SettingsPayloads.resolve(payload.colony(), player);
            if (colony == null)
            {
                return;
            }
            final String refused = Exchange.open(player, colony);
            if (refused != null)
            {
                player.sendSystemMessage(Component.literal(refused).withStyle(ChatFormatting.RED));
            }
        });
    }

    static final String ROADS_DENIED = "Only the colony's owner and officers (or an operator) may have its roads laid again.";

    public record RelayRoads(ColonyId colony) implements CustomPacketPayload
    {
        public static final Type<RelayRoads> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "relay_roads"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RelayRoads> CODEC =
          StreamCodec.composite(ColonyId.STREAM_CODEC, RelayRoads::colony, RelayRoads::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    static void onRelayRoads(final RelayRoads payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }
            final IColony colony = SettingsPayloads.resolve(payload.colony(), player);
            if (colony == null)
            {
                return;
            }
            player.sendSystemMessage(SettingsPayloads.mayEdit(colony, player)
              ? GrowthDirector.relayRoadsPressed(colony, player.getGameProfile().getName())
              : Component.literal(ROADS_DENIED).withStyle(ChatFormatting.RED));
        });
    }
}
