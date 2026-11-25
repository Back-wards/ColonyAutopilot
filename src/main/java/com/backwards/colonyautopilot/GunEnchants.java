// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Set;

final class GunEnchants
{
    private static final ResourceKey<Enchantment> HIGH_CALIBER = key("high_caliber");
    private static final ResourceKey<Enchantment> FUNGICIDE = key("fungicide");
    private static final ResourceKey<Enchantment> MARKSMAN = key("marksman");
    private static final ResourceKey<Enchantment> SHRAPNEL = key("shrapnel");
    private static final ResourceKey<Enchantment> CRYO_ROUNDS = key("cryo_rounds");
    private static final ResourceKey<Enchantment> INCENDIARY = key("incendiary");

    private static final float HIGH_CALIBER_PER_LEVEL = 0.12F;
    private static final float FUNGICIDE_PER_LEVEL = 0.25F;
    private static final float MARKSMAN_PER_LEVEL = 0.25F;

    private static final float SHRAPNEL_SHARE = 0.1F;
    private static final double SHRAPNEL_REACH = 2.5D;
    private static final int SHRAPNEL_HIT_PER_LEVEL = 2;

    private static final int[] CRYO_TICKS = {60, 100};
    private static final int CRYO_STANDSTILL = 9;
    private static final float INCENDIARY_SECONDS_PER_LEVEL = 3F;

    @SubscribeEvent
    public void onAnvil(final net.neoforged.neoforge.event.AnvilUpdateEvent event)
    {
        final ItemStack addition = event.getRight();
        if (IGun.getIGunOrNull(event.getLeft()) == null)
        {
            for (final Holder<Enchantment> enchantment : net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(addition).keySet())
            {
                if (enchantment.unwrapKey().map(key -> key.location().getNamespace().equals(ColonyAutopilot.MOD_ID)).orElse(false))
                {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    private static ResourceKey<Enchantment> key(final String name)
    {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, name));
    }

    @SubscribeEvent
    public void onGunHit(final EntityHurtByGunEvent.Pre event)
    {
        final ItemStack gun = firedGun(event.getLogicalSide().isServer(), event.getAttacker(), event.getGunId());
        if (gun == null || event.getHurtEntity() == null)
        {
            return;
        }
        final Level level = event.getHurtEntity().level();

        float share = HIGH_CALIBER_PER_LEVEL * level(level, gun, HIGH_CALIBER);
        if (isSpore(event.getHurtEntity()))
        {
            share += FUNGICIDE_PER_LEVEL * level(level, gun, FUNGICIDE);
        }

        final float plain = event.isHeadShot() ? event.getAmount() : event.getBaseAmount();
        event.setBaseAmount(event.getBaseAmount() * (1F + share));
        if (event.isHeadShot())
        {
            event.setHeadshotMultiplier(event.getHeadshotMultiplier() * (1F + MARKSMAN_PER_LEVEL * level(level, gun, MARKSMAN)));
        }
        final float dealt = event.isHeadShot() ? event.getAmount() : event.getBaseAmount();
        if (dealt != plain)
        {

            ColonyAutopilot.LOGGER.debug("Gun enchantments: {} firing {} hit {} for {} instead of {}{}", event.getAttacker().getName().getString(),
              event.getGunId(), event.getHurtEntity().getName().getString(), dealt, plain, event.isHeadShot() ? " (headshot)" : "");
        }
    }

    @SubscribeEvent
    public void afterGunHit(final EntityHurtByGunEvent.Post event)
    {
        final ItemStack gun = firedGun(event.getLogicalSide().isServer(), event.getAttacker(), event.getGunId());
        if (gun == null || !(event.getHurtEntity() instanceof LivingEntity target))
        {
            return;
        }
        wound(gun, target, event.getAttacker());
        shrapnel(gun, target, event.getAttacker(), event.getBaseAmount(), event.getDamageSource(GunDamageSourcePart.NON_ARMOR_PIERCING));
    }

    @SubscribeEvent
    public void onGunKill(final EntityKillByGunEvent event)
    {
        final ItemStack gun = firedGun(event.getLogicalSide().isServer(), event.getAttacker(), event.getGunId());
        if (gun != null && event.getKilledEntity() != null)
        {
            shrapnel(gun, event.getKilledEntity(), event.getAttacker(), event.getBaseDamage(), event.getDamageSource(GunDamageSourcePart.NON_ARMOR_PIERCING));
        }
    }

    private static void shrapnel(final ItemStack gun, final LivingEntity target, final LivingEntity attacker, final float bulletDamage,
      final DamageSource source)
    {
        final int shrapnel = level(target.level(), gun, SHRAPNEL);
        if (shrapnel <= 0 || source == null)
        {
            return;
        }

        final List<LivingEntity> bystanders = target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SHRAPNEL_REACH),
          nearby -> nearby != target && nearby != attacker && nearby.isAlive() && (nearby instanceof Enemy || isSpore(nearby))
                      && !(nearby instanceof OwnableEntity owned && owned.getOwnerUUID() != null)
                      && !nearby.getType().is(ColonyAutopilot.GUARDS_IGNORE));

        bystanders.sort(java.util.Comparator.comparingDouble(nearby -> nearby.distanceToSqr(target)));
        for (int i = 0; i < bystanders.size() && i < SHRAPNEL_HIT_PER_LEVEL * shrapnel; i++)
        {
            final LivingEntity bystander = bystanders.get(i);

            bystander.invulnerableTime = 0;

            bystander.hurt(source, bulletDamage * SHRAPNEL_SHARE);
            wound(gun, bystander, attacker);
        }
    }

    private static void wound(final ItemStack gun, final LivingEntity victim, final LivingEntity attacker)
    {

        if (victim instanceof AbstractEntityCitizen)
        {
            return;
        }
        chill(gun, victim, attacker);
        final int incendiary = level(victim.level(), gun, INCENDIARY);
        if (incendiary > 0 && victim.isAlive())
        {
            victim.igniteForSeconds(INCENDIARY_SECONDS_PER_LEVEL * incendiary);
        }
    }

    private static final Set<String> SPORE_BOSS_BASES = Set.of("com.Harbinger.Spore.Sentities.BaseEntities.Organoid",
      "com.Harbinger.Spore.Sentities.BaseEntities.Calamity", "com.Harbinger.Spore.Sentities.BaseEntities.Hyper");

    private static boolean holdsItsGround(final LivingEntity target)
    {
        if (target instanceof net.minecraft.world.entity.player.Player || target.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES))
        {
            return true;
        }
        if (!isSpore(target))
        {
            return target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 1D;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            if (SPORE_BOSS_BASES.contains(type.getName()))
            {
                return true;
            }
        }
        return false;
    }

    private static void chill(final ItemStack gun, final LivingEntity target, final LivingEntity attacker)
    {
        final int cryo = level(target.level(), gun, CRYO_ROUNDS);
        if (cryo <= 0 || !target.isAlive())
        {
            return;
        }
        final boolean slowedOnly = holdsItsGround(target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CRYO_TICKS[Math.min(cryo, CRYO_TICKS.length) - 1],
          slowedOnly ? cryo - 1 : CRYO_STANDSTILL), attacker);
        if (!slowedOnly)
        {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze()));
        }
    }

    private static ItemStack firedGun(final boolean serverSide, final LivingEntity attacker, final ResourceLocation gunId)
    {
        if (!serverSide || attacker == null || !AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.live(AutopilotConfig.GUN_ENCHANTMENTS))
        {
            return null;
        }
        final ItemStack held = attacker.getMainHandItem();
        final IGun gun = IGun.getIGunOrNull(held);
        return gun != null && held.isEnchanted() && gun.getGunId(held).equals(gunId) ? held : null;
    }

    private static int level(final Level level, final ItemStack gun, final ResourceKey<Enchantment> enchantment)
    {
        return level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(registry -> registry.get(enchantment))
                 .map(holder -> gun.getEnchantmentLevel((Holder<Enchantment>) holder)).orElse(0);
    }

    private static boolean isSpore(final Entity entity)
    {
        return SporeCompat.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }
}
