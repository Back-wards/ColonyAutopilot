// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin.client;

import com.backwards.colonyautopilot.client.ExchangeWindow;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWindowTownHall.class)
public abstract class AbstractWindowTownHallMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void colonyautopilot$addExchangeBookmark(final BuildingTownHall.View townHall, final String page, final CallbackInfo ci)
    {
        if (!((Object) this instanceof ExchangeWindow))
        {
            ExchangeWindow.addBookmark((AbstractWindowSkeleton) (Object) this, townHall);
        }
    }
}
