// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.Arrivals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.asestefan.mutationcraft.behavior.AberrationSpawns")
public class AberrationSpawnsMixin
{
    @Inject(method = {"spawnAt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/EntityType;DDD)Lnet/minecraft/world/entity/Mob;",
                      "spawnNear(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/EntityType;DDD)Lnet/minecraft/world/entity/Mob;"},
      at = @At("HEAD"), cancellable = true, require = 0)
    private static void colonyautopilot$holdSpawn(final ServerLevel level, final EntityType<?> type, final double x, final double y, final double z,
                                                  final CallbackInfoReturnable<Mob> cir)
    {
        if (type != null && Arrivals.held(type, level))
        {
            cir.setReturnValue(null);
        }
    }
}
