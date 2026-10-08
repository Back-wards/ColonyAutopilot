// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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

    private static final ResourceLocation STANDARD_ISSUE = ResourceLocation.fromNamespaceAndPath("tacz", "ak47");

    static ItemStack standardIssue()
    {
        final net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
        {
            return ItemStack.EMPTY;
        }
        try
        {
            final ItemStack rifle = com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(STANDARD_ISSUE)
              .setFireMode(com.tacz.guns.api.item.gun.FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build(server.registryAccess());
            if (rifle.isEmpty())
            {

                ColonyAutopilot.LOGGER.warn("No standard-issue rifle for the gunners: TaCZ has no gun {} loaded (is the default gun pack installed?)", STANDARD_ISSUE);
                return ItemStack.EMPTY;
            }
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, rifle,
              tag -> tag.putBoolean(AutopilotConfig.ISSUED_GUN_TAG, true));
            return rifle;
        }
        catch (final RuntimeException e)
        {
            ColonyAutopilot.LOGGER.warn("No standard-issue rifle for the gunners: TaCZ could not build {} (is the default gun pack installed?)", STANDARD_ISSUE, e);
            return ItemStack.EMPTY;
        }
    }

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
        if ((addition.isEnchanted() || addition.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS))
              && event.getLeft().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                   net.minecraft.world.item.component.CustomData.EMPTY).contains(AutopilotConfig.ISSUED_GUN_TAG))
        {
            event.setCanceled(true);
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

    private final Map<AbstractEntityCitizen, Long> aiShots = new WeakHashMap<>();

    private boolean following;

    private static final int FOLLOW_UP_TICKS = 10;

    @SubscribeEvent
    public void onGunShoot(final GunShootEvent event)
    {
        if (!following && event.getLogicalSide().isServer() && event.getShooter() instanceof AbstractEntityCitizen citizen
              && AutopilotConfig.SPEC.isLoaded() && AutopilotConfig.live(AutopilotConfig.FULL_AUTO_GUNNERS))
        {
            aiShots.put(citizen, citizen.level().getGameTime());
        }
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (aiShots.isEmpty())
        {
            return;
        }
        following = true;
        try
        {
            aiShots.entrySet().removeIf(shot -> !followUp(shot.getKey(), shot.getValue()));
        }
        finally
        {
            following = false;
        }
    }

    @SubscribeEvent
    public void onServerTickRegun(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.SPEC.isLoaded() || !AutopilotConfig.masterOn())
        {
            return;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            for (final ICitizenData data : colony.getCitizenManager().getCitizens())
            {
                final AbstractEntityCitizen citizen = data.getEntity().orElse(null);
                if (citizen == null || !citizen.getMainHandItem().isEmpty())
                {
                    continue;
                }
                final int slot = data.getInventory().getHeldItemSlot(InteractionHand.MAIN_HAND);
                if (slot >= 0 && slot < data.getInventory().getSlots()
                      && IGun.getIGunOrNull(data.getInventory().getStackInSlot(slot)) != null)
                {
                    CitizenItemUtils.setHeldItem(citizen, InteractionHand.MAIN_HAND, slot);
                }
            }
        }
    }

    private static boolean followUp(final AbstractEntityCitizen citizen, final long shotAt)
    {
        final long since = citizen.level().getGameTime() - shotAt;
        if (!citizen.isAlive() || since >= FOLLOW_UP_TICKS || !(citizen instanceof IThreatTableEntity fighter))
        {
            return false;
        }
        final ItemStack held = citizen.getInventoryCitizen().getHeldItem(InteractionHand.MAIN_HAND);
        final IGun gun = IGun.getIGunOrNull(held);
        if (gun == null || gun.getFireMode(held) != FireMode.AUTO)
        {
            return false;
        }
        final IGunOperator operator = IGunOperator.fromLivingEntity(citizen);

        if (since == 0 || operator.getSynShootCoolDown() > 0)
        {
            return true;
        }

        final LivingEntity target = fighter.getThreatTable().getTargetMob();
        if (target == null || !target.isAlive() || !citizen.getSensing().hasLineOfSight(target))
        {
            return false;
        }

        citizen.getLookControl().setLookAt(target);
        final ShootResult result = operator.shoot(citizen::getXRot, citizen::getYRot);
        return result == ShootResult.SUCCESS || result == ShootResult.COOL_DOWN;
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
          nearby -> nearby != target && nearby != attacker && nearby.isAlive() && (nearby instanceof Enemy || isSpore(nearby)));

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
        final ItemStack held = attacker instanceof AbstractEntityCitizen citizen
                                 ? citizen.getInventoryCitizen().getHeldItem(InteractionHand.MAIN_HAND) : attacker.getMainHandItem();
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
