// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.SporeCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SummonCommand.class)
public class SummonCommandMixin
{
    @Redirect(method = "createEntity(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/Holder$Reference;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/nbt/CompoundTag;Z)Lnet/minecraft/world/entity/Entity;",
      at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"),
      require = 0)
    private static boolean colonyautopilot$playerSummon(final ServerLevel level, final Entity entity, final CommandSourceStack source)
    {
        SporeCompat.playerSummon(source, entity);
        return level.tryAddFreshEntityWithPassengers(entity);
    }
}
