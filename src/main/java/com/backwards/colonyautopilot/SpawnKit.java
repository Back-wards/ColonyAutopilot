// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.items.ModItems;
import com.minecolonies.api.blocks.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class SpawnKit
{
    private static final String MARKER = "autopilot_spawnkit";

    private static final String HALL_HOLDER = "#townhall";

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)
              || !AutopilotConfig.live(AutopilotConfig.SPAWN_STARTER_KIT))
        {
            return;
        }
        final ServerScoreboard scoreboard = player.server.getScoreboard();
        Objective marker = scoreboard.getObjective(MARKER);
        if (marker == null)
        {
            marker = scoreboard.addObjective(MARKER, ObjectiveCriteria.DUMMY,
              Component.literal(MARKER), ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        final ScoreAccess given = scoreboard.getOrCreatePlayerScore(player, marker);
        if (given.get() != 0)
        {
            return;
        }
        given.set(1);

        final boolean colonyStands = !com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().isEmpty();
        final ScoreHolder hallHolder = ScoreHolder.forNameOnly(HALL_HOLDER);
        final ReadOnlyScoreInfo handed = scoreboard.getPlayerScoreInfo(hallHolder, marker);
        final boolean founder = !colonyStands && (handed == null || handed.value() == 0);
        if (founder)
        {
            scoreboard.getOrCreatePlayerScore(hallHolder, marker).set(1);
            give(player, new ItemStack(ModBlocks.blockHutTownHall));
        }
        else
        {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(colonyStands
              ? "The village already stands — ask the founder for colony permissions (Town Hall > Permissions > promote to Officer)."
              : "The founder's Town Hall has already been handed out — wait for the village to be founded, then ask the founder for colony permissions (Town Hall > Permissions > promote to Officer)."));
        }

        give(player, new ItemStack(ModItems.buildTool.get()));
        if (AutopilotConfig.ZONES_ENABLED.get())
        {
            give(player, new ItemStack(com.backwards.colonyautopilot.ModItems.ZONE_MARKER.get()));
        }
        final ItemStack fieldGuide = GuideMeCompat.guideStack();
        give(player, fieldGuide.isEmpty() ? FieldGuide.book() : fieldGuide);

        if (ModList.get().isLoaded("akashictome"))
        {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("akashictome:tome"))
              .ifPresent(tome -> give(player, new ItemStack(tome)));
        }
        for (final String id : AutopilotConfig.SPAWN_TOME_BOOKS.get())
        {
            final ResourceLocation bookId = ResourceLocation.tryParse(id);
            if (bookId != null)
            {
                BuiltInRegistries.ITEM.getOptional(bookId).ifPresent(book -> give(player, new ItemStack(book)));
            }
        }

        if (ModList.get().isLoaded("patchouli"))
        {
            for (final String id : AutopilotConfig.SPAWN_PATCHOULI_BOOKS.get())
            {
                final ResourceLocation bookId = ResourceLocation.tryParse(id);

                if (bookId != null && ModList.get().isLoaded(bookId.getNamespace()))
                {
                    final ItemStack book = patchouliBook(bookId);
                    if (!book.isEmpty())
                    {
                        give(player, book);
                    }
                }
            }
        }
        ColonyAutopilot.LOGGER.info("Equipped {} with the founder's satchel ({})", player.getName().getString(),
          founder ? "the world's founder — town hall included"
                  : colonyStands ? "a village already stands — no town hall" : "the founder's town hall is already handed out — no town hall");
    }

    @SuppressWarnings("unchecked")
    private static ItemStack patchouliBook(final ResourceLocation bookId)
    {
        final var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("patchouli:guide_book")).orElse(null);
        final net.minecraft.core.component.DataComponentType<?> component =
          BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.parse("patchouli:book")).orElse(null);
        if (item == null || component == null)
        {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = new ItemStack(item);
        stack.set((net.minecraft.core.component.DataComponentType<ResourceLocation>) component, bookId);
        return stack;
    }

    private static void give(final ServerPlayer player, final ItemStack stack)
    {
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
    }
}
