// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.AutopilotConfig;
import com.backwards.colonyautopilot.WaterRescue;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityCitizen.class)
public class EntityCitizenWallMixin
{

    @Unique
    private static final int COLONYAUTOPILOT$WALL_RESCUES = 3;

    @Unique
    private static final long COLONYAUTOPILOT$WALL_WINDOW_TICKS = 1200L;

    @Unique
    private static final int COLONYAUTOPILOT$WALL_REACH = 6;

    @Unique
    private BlockPos colonyautopilot$wallSpot;

    @Unique
    private long colonyautopilot$wallSince;

    @Unique
    private int colonyautopilot$wallRescues;

    @Inject(method = "handleInWallDamage(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void colonyautopilot$recallFromTheWall(final DamageSource source, final CallbackInfoReturnable<Boolean> cir)
    {
        final EntityCitizen citizen = (EntityCitizen) (Object) this;

        if (!source.is(DamageTypes.IN_WALL) || !(citizen.level() instanceof final ServerLevel level)
              || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return;
        }
        final long now = level.getGameTime();
        final BlockPos at = citizen.blockPosition();
        if (colonyautopilot$wallSpot == null || now - colonyautopilot$wallSince > COLONYAUTOPILOT$WALL_WINDOW_TICKS || now < colonyautopilot$wallSince
              || BlockPosUtil.dist(at, colonyautopilot$wallSpot) > COLONYAUTOPILOT$WALL_REACH)
        {
            colonyautopilot$wallSpot = at;
            colonyautopilot$wallSince = now;
            colonyautopilot$wallRescues = 0;
        }
        if (++colonyautopilot$wallRescues < COLONYAUTOPILOT$WALL_RESCUES)
        {
            return;
        }
        colonyautopilot$wallSpot = null;
        if (WaterRescue.recallFromWall(level, citizen))
        {
            cir.setReturnValue(true);
        }
    }
}
