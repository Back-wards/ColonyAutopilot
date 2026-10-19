// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.Arrivals;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.asestefan.mutationcraft.behavior.NecroptorBehavior")
public class NecroptorBehaviorMixin
{
    @Inject(method = "spawnHook(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
      at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$holdHook(final ServerLevel level, final LivingEntity necroptor, final CallbackInfo ci)
    {
        if (Arrivals.held(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(Arrivals.MUTATIONCRAFT, "light_hook")), level)
              || Arrivals.hookNestFull(level, necroptor))
        {
            ci.cancel();
        }
    }
}
