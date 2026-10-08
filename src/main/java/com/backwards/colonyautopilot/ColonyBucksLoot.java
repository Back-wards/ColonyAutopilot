// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public class ColonyBucksLoot
{
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
      DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ColonyAutopilot.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ChestBucks>> CHEST_BUCKS =
      LOOT_MODIFIERS.register("chest_bucks", () -> ChestBucks.CODEC);

    private static final boolean KTURRETS = ModList.get().isLoaded("k_turrets");

    private static final ResourceKey<DamageType> MINECOLONIES_DESPAWN =
      ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecolonies", "despawn"));

    private static final ResourceKey<DamageType> MINECOLONIES_CONSOLE =
      ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecolonies", "console"));

    private static final long BLOW_TICKS = 100;

    private static final Map<LivingEntity, Blow> LAST_BLOW = new WeakHashMap<>();

    private record Blow(WeakReference<Entity> earner, long time) {}

    @SubscribeEvent
    public void onLivingDamage(final LivingDamageEvent.Post event)
    {
        final LivingEntity victim = event.getEntity();
        if (event.getNewDamage() <= 0 || victim.level().isClientSide())
        {
            return;
        }
        final Entity earner = attacker(event.getSource());
        if (earner != null)
        {
            LAST_BLOW.put(victim, new Blow(new WeakReference<>(earner), victim.level().getGameTime()));
        }
    }

    @SubscribeEvent
    public void onLeaveLevel(final EntityLeaveLevelEvent event)
    {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living)
        {
            LAST_BLOW.remove(living);
        }
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {
        LAST_BLOW.clear();
    }

    @SubscribeEvent
    public void onLivingDrops(final LivingDropsEvent event)
    {
        final LivingEntity victim = event.getEntity();

        if (!AutopilotConfig.SPEC.isLoaded() || !victim.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT) || !dropsBucks(victim))
        {
            return;
        }
        final Entity killer = killer(event.getSource(), victim);
        if (killer == null)
        {
            return;
        }

        final int looting = event.getSource().getEntity() instanceof LivingEntity attacker
                              ? EnchantmentHelper.getEnchantmentLevel(victim.level().registryAccess()
                                  .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING), attacker)
                              : 0;
        final double chance = AutopilotConfig.ECONOMY_DROP_CHANCE.get() + looting * AutopilotConfig.ECONOMY_LOOTING_BONUS.get();
        if (victim.getRandom().nextDouble() >= chance)
        {
            return;
        }

        if (Treasury.bankKill(killer, victim, String.format(java.util.Locale.ROOT, "%.3f", chance)))
        {
            return;
        }
        final ItemEntity bucks = new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(),
          new ItemStack(ModItems.COLONY_BUCKS.get()));
        bucks.setDefaultPickUpDelay();
        event.getDrops().add(bucks);
        ColonyAutopilot.LOGGER.debug("A {} dropped a ColonyBucks (chance {})", BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()),
          String.format(java.util.Locale.ROOT, "%.3f", chance));
    }

    private static Entity killer(final DamageSource source, final LivingEntity victim)
    {
        final Entity attacker = attacker(source);
        if (attacker != null || source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
              || source.is(MINECOLONIES_DESPAWN) || source.is(MINECOLONIES_CONSOLE) || source.is(DamageTypes.STARVE))
        {
            return attacker;
        }
        final Blow blow = LAST_BLOW.get(victim);
        return blow != null && victim.level().getGameTime() - blow.time() <= BLOW_TICKS ? blow.earner().get() : null;
    }

    private static Entity attacker(final DamageSource source)
    {
        final Entity direct = earner(source.getEntity());
        return direct != null ? direct : earner(source.getDirectEntity());
    }

    private static Entity earner(final Entity attacker)
    {
        Entity at = attacker;

        for (int hop = 0; at != null && hop < 4; hop++)
        {
            if (at instanceof Player || Treasury.isCitizen(at) || (KTURRETS && KTurretsCompat.ownedTurret(at)))
            {
                return at;
            }
            at = at instanceof TraceableEntity traced ? traced.getOwner() : at instanceof OwnableEntity owned ? owned.getOwner() : null;
        }
        return null;
    }

    private static boolean dropsBucks(final LivingEntity victim)
    {
        final String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        if (victim.getType().is(Tags.EntityTypes.BOSSES) || victim.getMaxHealth() >= AutopilotConfig.ECONOMY_BOSS_HEALTH.get()
              || AutopilotConfig.ECONOMY_DROP_EXCLUDE.get().contains(id))
        {
            return false;
        }
        if (AutopilotConfig.ECONOMY_DROP_INCLUDE.get().contains(id))
        {
            return true;
        }

        if (victim instanceof Npc)
        {
            return false;
        }
        return victim instanceof Enemy || victim.getType().getCategory() == MobCategory.MONSTER
                 || victim instanceof Mob mob && mob.getTarget() instanceof Player;
    }

    public static final class ChestBucks extends LootModifier
    {
        static final MapCodec<ChestBucks> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, ChestBucks::new));

        public ChestBucks(final LootItemCondition[] conditions)
        {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(final ObjectArrayList<ItemStack> generatedLoot, final LootContext context)
        {
            final Entity roller = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (roller != null && !(roller instanceof Player))
            {
                return generatedLoot;
            }
            final String path = context.getQueriedLootTableId().getPath();
            if (!AutopilotConfig.SPEC.isLoaded() || !path.contains("chests/") || path.contains("village"))
            {
                return generatedLoot;
            }
            final boolean mineshaft = path.contains("mineshaft");
            final int count = Mth.nextInt(context.getRandom(),
              (mineshaft ? AutopilotConfig.ECONOMY_MINESHAFT_MIN : AutopilotConfig.ECONOMY_CHEST_MIN).get(),
              (mineshaft ? AutopilotConfig.ECONOMY_MINESHAFT_MAX : AutopilotConfig.ECONOMY_CHEST_MAX).get());
            if (count > 0)
            {
                generatedLoot.add(new ItemStack(ModItems.COLONY_BUCKS.get(), count));
                ColonyAutopilot.LOGGER.debug("Loot table {} gets {} ColonyBucks", context.getQueriedLootTableId(), count);
            }
            return generatedLoot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec()
        {
            return CHEST_BUCKS.get();
        }
    }
}
