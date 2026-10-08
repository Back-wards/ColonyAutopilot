// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import dev.buildtool.kturrets.Turret;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

final class KTurretsCompat
{
    private static final TagKey<EntityType<?>> FUNGUS_ENTITIES =
      TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(SporeCompat.MOD_ID, "fungus_entities"));

    @SubscribeEvent
    public void onTurretJoin(final EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Turret turret)
              || !AutopilotConfig.live(AutopilotConfig.SPORES_TURRETS_TARGET_INFECTED))
        {
            return;
        }
        try
        {
            final List<EntityType<?>> targets = Turret.decodeTargets(turret.getTargets());
            final List<EntityType<?>> missing = new ArrayList<>();
            for (final var holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(FUNGUS_ENTITIES))
            {
                if (!targets.contains(holder.value()))
                {
                    missing.add(holder.value());
                }
            }
            if (!missing.isEmpty())
            {
                targets.addAll(missing);
                turret.setTargets(Turret.encodeTargets(targets));
                ColonyAutopilot.LOGGER.debug("Taught the turret at {} to target {} spore creatures",
                  turret.blockPosition().toShortString(), missing.size());
            }
        }
        catch (final Exception e)
        {

            ColonyAutopilot.LOGGER.info("Could not teach a turret the spore targets", e);
        }
    }

    static boolean ownedTurret(final net.minecraft.world.entity.Entity entity)
    {
        return entity instanceof Turret turret && turret.getOwner().isPresent();
    }

    @SubscribeEvent
    public void onTurretHurt(final LivingIncomingDamageEvent event)
    {
        if (event.getEntity() instanceof Turret
              && AutopilotConfig.live(AutopilotConfig.SPORES_TURRETS_INVULNERABLE)
              && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            event.setCanceled(true);
        }
    }
}
