// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.Treasury;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.events.raid.RaidManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RaidManager.class)
public class RaidManagerMixin
{
    @Shadow
    @Final
    private Colony colony;

    @Inject(method = "onRaidEventFinished", at = @At("TAIL"))
    private void colonyautopilot$raidPaid(final IColonyRaidEvent finishedRaid, final CallbackInfo ci)
    {
        try
        {
            Treasury.raidEnded(colony);
        }
        catch (final RuntimeException e)
        {

            ColonyAutopilot.LOGGER.warn("The raid pay's notice for colony {} failed", colony.getName(), e);
        }
    }
}
