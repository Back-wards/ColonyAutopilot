// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobBlacksmith;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Quartermaster
{
    private static final int PERIOD_TICKS = 1200;

    private static final Map<Class<?>, Item[]> TOOLS = Map.of(
      PickaxeItem.class, new Item[] {Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE},
      AxeItem.class, new Item[] {Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE},
      ShovelItem.class, new Item[] {Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL},
      HoeItem.class, new Item[] {Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE},
      SwordItem.class, new Item[] {Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD});

    private static final Map<EquipmentSlot, Item[]> ARMOR = Map.of(
      EquipmentSlot.HEAD, new Item[] {Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET},
      EquipmentSlot.CHEST, new Item[] {Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE},
      EquipmentSlot.LEGS, new Item[] {Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS},
      EquipmentSlot.FEET, new Item[] {Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS});

    private int tickCounter = 28;

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {
        if (!AutopilotConfig.masterOn() || ++tickCounter < PERIOD_TICKS)
        {
            return;
        }
        tickCounter = 0;

        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {

            if (!AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_ENABLED) || !AutopilotConfig.get(colony, AutopilotConfig.QUARTERMASTER))
            {
                continue;
            }
            try
            {
                outfitOneCitizen(colony);
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.warn("Quartermaster round failed for colony {}", colony.getName(), e);
            }
        }
    }

    static int smithTier(final IColony colony)
    {
        int smithy = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getBuildingBlock() == ModBlocks.blockHutBlacksmith)
            {
                smithy = Math.max(smithy, building.getBuildingLevel());
            }
        }
        final IBuilding hall = colony.getServerBuildingManager().getTownHall();
        return smithy == 0 ? 0 : smithy >= 5 && hall != null && hall.getBuildingLevel() >= 5 ? 2 : 1;
    }

    private void outfitOneCitizen(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final int smithTier = smithTier(colony);
        final int colonyToolTier = smithTier + 1;

        final int colonyArmorTier = smithTier == 0 ? 1 : smithTier + 2;

        final boolean metered = AutopilotConfig.progressionMode(colony);
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {

            if (citizen.getWorkBuilding() == null
                  || (metered && (citizen.getJob() instanceof JobDeliveryman || citizen.getJob() instanceof JobBlacksmith)))
            {
                continue;
            }

            if ((AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_TOOLS) && upgradeTools(colony, level, citizen, colonyToolTier))
                  || (AutopilotConfig.get(colony, AutopilotConfig.PROVIDENCE_ARMOUR_WEAPONS) && upgradeArmor(colony, level, citizen, colonyArmorTier)))
            {
                return;
            }
        }
    }

    private static boolean upgradeTools(final IColony colony, final ServerLevel level, final ICitizenData citizen, final int colonyToolTier)
    {
        final int cap = Math.min(citizen.getWorkBuilding().getMaxEquipmentLevel(), colonyToolTier);
        if (cap < 1)
        {
            return false;
        }
        final InventoryCitizen pockets = citizen.getInventory();

        final Set<Class<?>> kindsAtCap = new HashSet<>();
        for (int slot = 0; AutopilotConfig.progressionMode(colony) && slot < pockets.getSlots(); slot++)
        {
            final ItemStack held = pockets.getStackInSlot(slot);
            if (!held.isEmpty() && TOOLS.containsKey(held.getItem().getClass()) && toolTier(held) >= cap)
            {
                kindsAtCap.add(held.getItem().getClass());
            }
        }
        for (int slot = 0; slot < pockets.getSlots(); slot++)
        {
            final ItemStack held = pockets.getStackInSlot(slot);
            final int heldTier = toolTier(held);
            final Item[] ladder = held.isEmpty() ? null : TOOLS.get(held.getItem().getClass());
            if (ladder == null || heldTier < 0 || heldTier >= cap || kindsAtCap.contains(held.getItem().getClass()))
            {
                continue;
            }
            if (held.isEnchanted())
            {

                continue;
            }
            final ItemStack upgrade = new ItemStack(ladder[cap - 1]);

            if (Treasury.isCurrency(upgrade) || !Treasury.canAfford(colony, level, Treasury.cost(upgrade)))
            {
                return false;
            }
            if (!InventoryUtils.addItemStackToItemHandler(pockets, upgrade.copy()))
            {
                return false;
            }

            Treasury.charge(colony, level, Treasury.cost(upgrade), citizen.getJob() instanceof AbstractJobGuard ? Treasury.Line.GEAR : Treasury.Line.TOOLS);
            final ItemStack retired = pockets.extractItem(slot, held.getCount(), false);
            ColonyAutopilot.LOGGER.debug("[{}] the quartermaster upgraded {}'s {} to {}",
              colony.getName(), citizen.getName(), retired.getHoverName().getString(), upgrade.getHoverName().getString());
            return true;
        }
        return false;
    }

    private static boolean upgradeArmor(final IColony colony, final ServerLevel level, final ICitizenData citizen, final int colonyArmorTier)
    {
        if (!(citizen.getJob() instanceof AbstractJobGuard))
        {
            return false;
        }

        final int cap = Math.min(Math.min(citizen.getWorkBuilding().getBuildingLevelEquivalent(), 4), colonyArmorTier);
        if (cap < 1)
        {
            return false;
        }
        final InventoryCitizen inventory = citizen.getInventory();
        for (final Map.Entry<EquipmentSlot, Item[]> entry : ARMOR.entrySet())
        {
            final ItemStack worn = inventory.getArmorInSlot(entry.getKey());
            if (worn.isEmpty())
            {
                continue;
            }

            if (!(worn.getItem() instanceof ArmorItem) || worn.isEnchanted()
                  || !BuiltInRegistries.ITEM.getKey(worn.getItem()).getNamespace().equals("minecraft"))
            {
                continue;
            }
            final int wornLevel = ItemStackUtils.getArmorLevel(worn);
            if (wornLevel >= cap)
            {
                continue;
            }
            final ItemStack upgrade = new ItemStack(entry.getValue()[cap - 1]);

            if (Treasury.isCurrency(upgrade) || !Treasury.charge(colony, level, Treasury.cost(upgrade), Treasury.Line.GEAR))
            {
                return false;
            }
            inventory.forceArmorStackToSlot(entry.getKey(), upgrade);
            ColonyAutopilot.LOGGER.debug("[{}] the quartermaster re-armored {}: {} replaces {}",
              colony.getName(), citizen.getName(), upgrade.getHoverName().getString(), worn.getHoverName().getString());
            return true;
        }

        return false;
    }

    private static int toolTier(final ItemStack stack)
    {
        if (!stack.isEmpty() && stack.getItem() instanceof TieredItem tiered && tiered.getTier() instanceof Tiers tier)
        {
            return switch (tier)
            {
                case WOOD, GOLD -> 0;
                case STONE -> 1;
                case IRON -> 2;
                case DIAMOND -> 3;
                case NETHERITE -> 4;
            };
        }
        return -1;
    }
}
