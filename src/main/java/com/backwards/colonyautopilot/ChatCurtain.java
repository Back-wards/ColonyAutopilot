// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = ColonyAutopilot.MOD_ID, value = Dist.CLIENT)
public final class ChatCurtain
{
    private ChatCurtain()
    {
    }

    @SubscribeEvent
    static void onRegisterClientCommands(final RegisterClientCommandsEvent event)
    {

        event.getDispatcher().register(
          Commands.literal("colonyautopilot")
            .then(Commands.literal("chat")
              .then(Commands.literal("on").executes(context -> setMuted(context.getSource(), false)))
              .then(Commands.literal("off").executes(context -> setMuted(context.getSource(), true)))));
    }

    private static int setMuted(final CommandSourceStack source, final boolean muted)
    {
        AutopilotConfig.MUTE_COLONY_CHAT.set(muted);
        AutopilotConfig.CLIENT_SPEC.save();
        source.sendSuccess(() -> Component.literal(muted
          ? "Colony chatter muted for you — MineColonies announcements and the autopilot's village news stay out of your chat. '/colonyautopilot chat on' brings them back."
          : "Colony chatter is back on for you."), false);
        return 1;
    }

    private static final String VISITOR_DEATH_KEY = "com.minecolonies.coremod.gui.tavern.visitordeath";

    @SubscribeEvent
    static void onSystemChat(final ClientChatReceivedEvent.System event)
    {
        final Component message = event.getMessage();
        if (AutopilotConfig.MUTE_COLONY_CHAT.get() && isColonyChatter(message))
        {
            event.setCanceled(true);
            return;
        }

        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(AutopilotConfig.PROVIDENCE_ENABLED)
              && AutopilotConfig.KEEP_VISITORS_COMING.get() && hasKey(message, VISITOR_DEATH_KEY))
        {
            event.setCanceled(true);
        }
    }

    private static boolean hasKey(final Component message, final String key)
    {
        if (message.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key))
        {
            return true;
        }
        for (final Component sibling : message.getSiblings())
        {
            if (hasKey(sibling, key))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isColonyChatter(final Component message)
    {
        if (message.getContents() instanceof TranslatableContents translatable)
        {
            final String key = translatable.getKey();
            if (key.startsWith("colonyautopilot.") || key.contains("minecolonies") || key.startsWith("block.blockhut"))
            {
                return true;
            }
        }
        for (final Component sibling : message.getSiblings())
        {
            if (isColonyChatter(sibling))
            {
                return true;
            }
        }
        return false;
    }
}
