// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.client;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = ColonyAutopilot.MOD_ID, value = Dist.CLIENT)
public final class AutopilotMenu
{
    private AutopilotMenu()
    {
    }

    @SubscribeEvent
    static void onRegisterClientCommands(final RegisterClientCommandsEvent event)
    {
        event.getDispatcher().register(
          Commands.literal("colonyautopilot")
            .then(Commands.literal("menu").executes(context -> {
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null)
                {
                    return 0;
                }
                final IColonyView colony = IColonyManager.getInstance().getClosestColonyView(mc.level, mc.player.blockPosition());
                if (colony == null)
                {
                    context.getSource().sendFailure(Component.literal("No colony near you — the window shows one colony's settings."));
                    return 0;
                }

                if (!mc.player.hasPermissions(2) && !colony.getPermissions().hasPermission(mc.player, Action.ACCESS_HUTS))
                {
                    context.getSource().sendFailure(Component.translatable("colonyautopilot.gui.noaccess", colony.getName()));
                    return 0;
                }
                final IBuildingView hall = colony.getClientBuildingManager().getTownHall();
                final BuildingTownHall.View townHall = hall instanceof BuildingTownHall.View view ? view : null;

                mc.tell(() -> new AutopilotWindow(colony, townHall).open());
                return 1;
            })));
    }

    @SubscribeEvent
    static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientSettingsCache.clear();
    }
}
