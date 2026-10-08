// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.core.colony.managers.VisitorManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisitorManager.class)
public class VisitorManagerMixin
{
    @Inject(method = "removeCivilian", at = @At("HEAD"), cancellable = true)
    private void colonyautopilot$deadVisitorIsAlreadyGone(final ICivilianData citizen, final CallbackInfo ci)
    {
        if (citizen == null)
        {
            ci.cancel();
        }
    }
}
