// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorkerUtil.class)
public class WorkerUtilMixin
{
    @Inject(method = "getCorrectHarvestLevelForBlock", at = @At("HEAD"), cancellable = true)
    private static void colonyautopilot$anyToolMinesAnyBlock(final BlockState state, final CallbackInfoReturnable<Integer> cir)
    {
        if (AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(AutopilotConfig.IGNORE_TOOL_TIER))
        {
            cir.setReturnValue(0);
        }
    }
}
