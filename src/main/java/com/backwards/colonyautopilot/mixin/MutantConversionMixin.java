// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.Arrivals;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.asestefan.mutationcraft.behavior.MutantConversion")
public class MutantConversionMixin
{
    @Inject(method = "evolve(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/entity/Mob;",
      at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$holdEvolution(final Mob from, final EntityType<?> to, final SoundEvent sound, final CallbackInfoReturnable<Mob> cir)
    {
        if (to != null && Arrivals.held(to, from.level()))
        {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "convert(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/entity/Mob;",
      at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$holdConversion(final Entity victim, final EntityType<?> to, final SoundEvent sound, final CallbackInfoReturnable<Mob> cir)
    {
        if (to != null && Arrivals.held(to, victim.level()))
        {
            cir.setReturnValue(null);
        }
    }
}
