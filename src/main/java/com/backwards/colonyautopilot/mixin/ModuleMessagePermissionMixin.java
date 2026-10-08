// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.ColonyAutopilot;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.TOOL_PERMISSION_SCEPTER_PERMISSION_DENY;

@Pseudo
@Mixin(targets = {
  "steve_gall.minecolonies_tweaks.core.common.network.message.AssignFilterableItemsMessage",
  "steve_gall.minecolonies_tweaks.core.common.network.message.AssignIdListMessage",
  "steve_gall.minecolonies_tweaks.core.common.network.message.MaximumStockUpdateMessage",
  "steve_gall.minecolonies_tweaks.core.common.network.message.FarmFieldPlotResize2Message",
  "steve_gall.minecolonies_compatibility.core.common.network.message.ModuleMenuOpenMessage",
  "steve_gall.minecolonies_compatibility.core.common.network.message.NetworkStorageRefreshMessage",
  "steve_gall.minecolonies_compatibility.core.common.network.message.RestrictGiveToolMessage",
  "steve_gall.minecolonies_compatibility.core.common.network.message.RestrictSetAreaMessage",
  "steve_gall.minecolonies_compatibility.core.common.network.message.RestrictSetEnabledMessage",
  "steve_gall.minecolonies_compatibility.module.common.silentgear.network.RepairMaterialMaterialMessage"})
public class ModuleMessagePermissionMixin
{
    @Inject(method = "handle(Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void colonyautopilot$managersOnly(final IPayloadContext context, final CallbackInfo ci)
    {
        if (!(context.player() instanceof ServerPlayer player) || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return;
        }
        final IColony colony;
        try
        {
            colony = colonyautopilot$colonyOf(this, player);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return;
        }
        if (colony != null && !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(TOOL_PERMISSION_SCEPTER_PERMISSION_DENY).sendTo(player);
            colonyautopilot$resync(this, player, colony);
            ColonyAutopilot.LOGGER.debug("[{}] {} may not manage the colony's huts — the {} refused", colony.getName(), player.getName().getString(),
              this.getClass().getSimpleName());
            ci.cancel();
        }
    }

    private static void colonyautopilot$resync(final Object message, final ServerPlayer player, final IColony colony)
    {
        try
        {
            if (message.getClass().getName().endsWith(".FarmFieldPlotResize2Message"))
            {
                final Object position = message.getClass().getMethod("getPosition").invoke(message);
                if (position instanceof BlockPos pos && player.level().getBlockEntity(pos) instanceof TileEntityScarecrow scarecrow)
                {
                    player.connection.send(scarecrow.getUpdatePacket());
                }
                return;
            }
            final Object modulePos = message.getClass().getMethod("getModulePos").invoke(message);
            final Object buildingId = modulePos == null ? null : modulePos.getClass().getMethod("getBuildingId").invoke(modulePos);
            final IBuilding building = buildingId instanceof BlockPos pos ? colony.getServerBuildingManager().getBuilding(pos) : null;
            if (building != null)
            {
                building.markDirty();
            }
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {

        }
    }

    private static IColony colonyautopilot$colonyOf(final Object message, final ServerPlayer player) throws ReflectiveOperationException
    {
        if (message.getClass().getName().endsWith(".FarmFieldPlotResize2Message"))
        {
            final Object position = message.getClass().getMethod("getPosition").invoke(message);
            return position instanceof BlockPos pos && player.level().getBlockEntity(pos) instanceof TileEntityScarecrow scarecrow ? scarecrow.getCurrentColony() : null;
        }
        final Object modulePos = message.getClass().getMethod("getModulePos").invoke(message);
        if (modulePos == null)
        {
            return null;
        }
        final int colonyId = (Integer) modulePos.getClass().getMethod("getColonyId").invoke(modulePos);
        @SuppressWarnings("unchecked")
        final ResourceKey<Level> dimension = (ResourceKey<Level>) modulePos.getClass().getMethod("getDimensionId").invoke(modulePos);
        return IColonyManager.getInstance().getColonyByDimension(colonyId, dimension);
    }
}
