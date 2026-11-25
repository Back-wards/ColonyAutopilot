// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import com.backwards.colonyautopilot.ProvidenceSweep;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkStablemaster;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityAIWorkStablemaster.class)
public class EntityAIWorkStablemasterMixin
{

    @Unique
    private static java.lang.reflect.Field colonyautopilot$pick;

    @Unique
    private static boolean colonyautopilot$pickLooked;

    @Inject(method = "readyMountForCombat()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;", at = @At("HEAD"), require = 0)
    private void colonyautopilot$forgetHealedMount(final CallbackInfoReturnable<IAIState> callback)
    {
        if (!colonyautopilot$pickLooked)
        {
            colonyautopilot$pickLooked = true;
            try
            {
                final java.lang.reflect.Field field = EntityAIWorkStablemaster.class.getDeclaredField("horseToGetReady");
                if (field.getType() == CavalryHorseEntity.class)
                {
                    field.setAccessible(true);
                    colonyautopilot$pick = field;
                }
            }
            catch (final ReflectiveOperationException | RuntimeException e)
            {

            }
        }
        if (colonyautopilot$pick == null)
        {
            return;
        }
        try
        {
            if (colonyautopilot$pick.get(this) instanceof CavalryHorseEntity horse && (!horse.isAlive()
              || !(horse.needsHealthTraining() || horse.getCombatCooldown() > 0 || horse.getHealth() < horse.getMaxHealth())))
            {
                colonyautopilot$pick.set(this, null);
            }
        }
        catch (final IllegalAccessException e)
        {
            colonyautopilot$pick = null;
        }
    }

    @Redirect(method = "readyMount(Lcom/minecolonies/core/entity/ai/workers/production/herders/EntityAIWorkStablemaster$MountMaintenance;Lcom/minecolonies/core/entity/other/cavalry/CavalryHorseEntity;)Z",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/api/colony/ICitizenData;createRequestAsync(Lcom/minecolonies/api/colony/requestsystem/requestable/IRequestable;)Lcom/minecolonies/api/colony/requestsystem/token/IToken;"),
      require = 0)
    private IToken<?> colonyautopilot$armourForReadying(final ICitizenData stablemaster, final IRequestable list,
      final EntityAIWorkStablemaster.MountMaintenance task)
    {
        if (task == EntityAIWorkStablemaster.MountMaintenance.READYING && ProvidenceSweep.handReadyingArmour(stablemaster))
        {
            return null;
        }
        return stablemaster.createRequestAsync(list);
    }
}
