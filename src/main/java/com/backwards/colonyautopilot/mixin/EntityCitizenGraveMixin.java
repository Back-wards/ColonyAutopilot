// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.ProvidenceSweep;
import com.backwards.colonyautopilot.Treasury;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.managers.interfaces.IGraveManager;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityCitizen.class)
public class EntityCitizenGraveMixin
{

    @Inject(method = "die", at = @At("HEAD"))
    private void colonyautopilot$bucksToTreasury(final DamageSource source, final CallbackInfo ci)
    {
        final ICitizenData dying = ((EntityCitizen) (Object) this).getCitizenData();

        if (dying != null && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.masterOn())
        {
            Treasury.depositPockets(dying);
        }
    }

    @Redirect(method = "die",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/api/colony/managers/interfaces/IGraveManager;createCitizenGrave(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/minecolonies/api/colony/ICitizenData;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos colonyautopilot$noGrave(final IGraveManager manager, final Level world, final BlockPos pos, final ICitizenData citizenData)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(citizenData.getColony(), AutopilotConfig.NO_GRAVES))
        {

            return null;
        }
        ProvidenceSweep.reclaimIssuedKit(citizenData, null);
        return manager.createCitizenGrave(world, pos, citizenData);
    }

    @Redirect(method = "die",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/api/util/InventoryUtils;dropItemHandler(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/level/Level;III)V"))
    private void colonyautopilot$noDeathDrop(final IItemHandler handler, final Level world, final int x, final int y, final int z)
    {
        final ICitizenData dying = ((EntityCitizen) (Object) this).getCitizenData();
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.live(dying == null ? null : dying.getColony(), AutopilotConfig.NO_GRAVES))
        {

            if (dying != null)
            {
                ProvidenceSweep.reclaimIssuedKit(dying, null);
            }
            InventoryUtils.dropItemHandler(handler, world, x, y, z);
        }
    }

    @Redirect(method = "die",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/api/util/MessageUtils$MessageBuilderColonyPlayerSelector;forManagers()V"))
    private void colonyautopilot$quietArrivalDeath(final MessageUtils.MessageBuilderColonyPlayerSelector selector)
    {
        final ICitizenData citizen = ((EntityCitizen) (Object) this).getCitizenData();
        if (citizen == null || !ProvidenceSweep.diedOnArrival(citizen))
        {
            selector.forManagers();
        }
    }
}
