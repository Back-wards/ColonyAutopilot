// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.Arrivals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.asestefan.mutationcraft.behavior.HookBehavior")
public class HookBehaviorMixin
{
    @Inject(method = "summonOne(Lnet/minecraft/server/level/ServerLevel;Lcom/asestefan/mutationcraft/entity/HookEntity;Lnet/minecraft/world/entity/EntityType;)V",
      at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$crowdedHook(final ServerLevel level, @Coerce final Entity hook, final EntityType<?> type, final CallbackInfo ci)
    {
        if (Arrivals.hookCrowded(level, hook))
        {
            ci.cancel();
        }
    }
}
