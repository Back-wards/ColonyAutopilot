// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneMarkerItem extends Item
{

    private record Pending(BlockPos corner, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int colonyId, long atMillis)
    {
    }

    static void clearPending()
    {
        PENDING.clear();
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final long PENDING_EXPIRY_MILLIS = 120_000L;

    private static final DustParticleOptions ZONE_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.1F), 1.0F);
    private static final DustParticleOptions CORNER_DUST = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 1.0F), 1.3F);
    private static final DustParticleOptions COLONY_DUST = new DustParticleOptions(new Vector3f(0.95F, 0.3F, 0.25F), 0.85F);

    private static final long SHOW_PERIOD_MILLIS = 400L;
    private static final int SHOW_RANGE = 64;
    private static final int EDGE_RANGE = 48;

    private static long lastShowMillis = 0L;

    public ZoneMarkerItem(final Properties properties)
    {
        super(properties);
    }

    static void showZones(final MinecraftServer server, final GrowthDirector growth)
    {
        final long now = System.currentTimeMillis();
        if (now - lastShowMillis < SHOW_PERIOD_MILLIS)
        {
            return;
        }
        lastShowMillis = now;
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (!player.getMainHandItem().is(ModItems.ZONE_MARKER.get())
                  && !player.getOffhandItem().is(ModItems.ZONE_MARKER.get()))
            {
                continue;
            }
            final ServerLevel level = player.serverLevel();

            player.connection.suspendFlushing();
            try
            {
                final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, player.blockPosition());
                if (colony != null && AutopilotConfig.get(colony, AutopilotConfig.ZONES_ENABLED))
                {
                    final int eye = player.getBlockY() + 1;
                    final java.util.List<SiteSelector.Footprint> zones = ProtectedZones.footprintsFor(colony);
                    for (final SiteSelector.Footprint zone : zones)
                    {
                        if (nearHolder(player, zone))
                        {
                            outline(level, player, zone, eye, ZONE_DUST, 1, -4, 8);
                        }
                    }

                    final java.util.List<SiteSelector.Footprint> colonyGround = new java.util.ArrayList<>(SiteSelector.occupiedGround(colony));
                    if (growth != null)
                    {
                        colonyGround.addAll(growth.reservedPads(colony));
                    }
                    for (final SiteSelector.Footprint ground : colonyGround)
                    {
                        if (nearHolder(player, ground))
                        {
                            outline(level, player, ground, eye, COLONY_DUST, 2, -1, 4);
                        }
                    }
                }
                final Pending pending = PENDING.get(player.getUUID());
                if (pending != null && now - pending.atMillis() <= PENDING_EXPIRY_MILLIS && pending.dimension().equals(level.dimension()))
                {
                    for (int y = -2; y <= 12; y++)
                    {
                        level.sendParticles(player, CORNER_DUST, true,
                          pending.corner().getX() + 0.5, pending.corner().getY() + 0.5 + y, pending.corner().getZ() + 0.5,
                          1, 0, 0, 0, 0);
                    }
                }
            }
            catch (final Exception e)
            {
                ColonyAutopilot.LOGGER.info("Zone display failed for {}", player.getName().getString(), e);
            }
            finally
            {
                player.connection.resumeFlushing();
            }
        }
    }

    private static boolean nearHolder(final ServerPlayer player, final SiteSelector.Footprint box)
    {
        return player.getX() >= box.minX() - SHOW_RANGE && player.getX() <= box.maxX() + 1 + SHOW_RANGE
                 && player.getZ() >= box.minZ() - SHOW_RANGE && player.getZ() <= box.maxZ() + 1 + SHOW_RANGE;
    }

    private static void outline(final ServerLevel level, final ServerPlayer player, final SiteSelector.Footprint box, final int eye,
      final DustParticleOptions dust, final int step, final int pillarLo, final int pillarHi)
    {
        for (int x = box.minX(); x <= box.maxX() + 1; x += step)
        {
            edgeDust(level, player, x, eye, box.minZ(), dust);
            edgeDust(level, player, x, eye, box.maxZ() + 1, dust);
        }
        for (int z = box.minZ(); z <= box.maxZ() + 1; z += step)
        {
            edgeDust(level, player, box.minX(), eye, z, dust);
            edgeDust(level, player, box.maxX() + 1, eye, z, dust);
        }
        for (final int[] corner : new int[][] {
          {box.minX(), box.minZ()}, {box.minX(), box.maxZ() + 1},
          {box.maxX() + 1, box.minZ()}, {box.maxX() + 1, box.maxZ() + 1}})
        {
            for (int y = pillarLo; y <= pillarHi; y++)
            {
                level.sendParticles(player, dust, true, corner[0], eye + y + 0.5, corner[1], 1, 0, 0, 0, 0);
            }
        }
    }

    private static void edgeDust(final ServerLevel level, final ServerPlayer player, final int x, final int y, final int z,
      final DustParticleOptions dust)
    {
        if (Math.abs(x - player.getX()) > EDGE_RANGE || Math.abs(z - player.getZ()) > EDGE_RANGE)
        {
            return;
        }
        level.sendParticles(player, dust, true, x, y + 0.5, z, 1, 0, 0, 0, 0);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context)
    {
        if (!(context.getLevel() instanceof ServerLevel level))
        {
            return InteractionResult.SUCCESS;
        }
        final Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }
        final BlockPos pos = context.getClickedPos();
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null ? !AutopilotConfig.ZONES_ENABLED.get() : !AutopilotConfig.get(colony, AutopilotConfig.ZONES_ENABLED))
        {
            player.sendSystemMessage(Component.literal("Protected zones are turned off here (zones.enabled)."));
            return InteractionResult.SUCCESS;
        }
        if (colony == null)
        {
            player.sendSystemMessage(Component.literal("Stand within your colony's claimed area to mark a protected zone."));
            return InteractionResult.SUCCESS;
        }

        if (!colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.MANAGE_HUTS))
        {
            player.sendSystemMessage(Component.literal("Only the colony's officers may mark or remove protected zones — ask the founder for a promotion (Town Hall > Permissions)."));
            return InteractionResult.SUCCESS;
        }
        final ProtectedZones zones = ProtectedZones.get(level);

        if (player.isShiftKeyDown())
        {
            final int removed = zones.removeAt(colony.getID(), pos.getX(), pos.getZ());
            if (removed > 0)
            {
                broadcast(level, player, player.getName().getString() + " removed "
                  + removed + " protected zone" + (removed == 1 ? "" : "s") + " at "
                  + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".");
            }
            else
            {
                player.sendSystemMessage(Component.literal("No protected zone here to remove."));
            }
            PENDING.remove(player.getUUID());
            return InteractionResult.SUCCESS;
        }

        final long now = System.currentTimeMillis();
        final Pending pending = PENDING.get(player.getUUID());
        final boolean elsewhere = pending != null
                                    && (!pending.dimension().equals(level.dimension()) || pending.colonyId() != colony.getID());
        if (pending == null || now - pending.atMillis() > PENDING_EXPIRY_MILLIS || elsewhere)
        {
            PENDING.put(player.getUUID(), new Pending(pos.immutable(), level.dimension(), colony.getID(), now));
            player.sendSystemMessage(Component.literal("Zone corner 1 set at " + pos.getX() + ", "
              + pos.getY() + ", " + pos.getZ()
              + " — right-click the opposite corner. (Sneak-right-click a zone to delete it.)"
              + (elsewhere ? " The previous corner was in another colony or dimension and was dropped." : "")));
            return InteractionResult.SUCCESS;
        }

        PENDING.remove(player.getUUID());
        final BlockPos c1 = pending.corner();
        int minX = Math.min(c1.getX(), pos.getX());
        int minZ = Math.min(c1.getZ(), pos.getZ());
        int maxX = Math.max(c1.getX(), pos.getX());
        int maxZ = Math.max(c1.getZ(), pos.getZ());

        final int maxEdge = AutopilotConfig.get(colony, AutopilotConfig.ZONE_MAX_EDGE);
        boolean clamped = false;
        if (maxX - minX + 1 > maxEdge)
        {
            if (pos.getX() >= c1.getX())
            {
                maxX = minX + maxEdge - 1;
            }
            else
            {
                minX = maxX - maxEdge + 1;
            }
            clamped = true;
        }
        if (maxZ - minZ + 1 > maxEdge)
        {
            if (pos.getZ() >= c1.getZ())
            {
                maxZ = minZ + maxEdge - 1;
            }
            else
            {
                minZ = maxZ - maxEdge + 1;
            }
            clamped = true;
        }

        final SiteSelector.Footprint box = new SiteSelector.Footprint(minX, minZ, maxX, maxZ);

        final String occupant = SiteSelector.occupantOverlapping(colony, box);
        if (occupant != null)
        {
            player.sendSystemMessage(Component.literal("That zone overlaps " + occupant
              + " — that ground is already the colony's. Move a corner to clear ground and mark again."));
            return InteractionResult.SUCCESS;
        }

        final String reservedPad = GrowthDirector.reservedPadOverlapping(colony, box);
        if (reservedPad != null)
        {
            player.sendSystemMessage(Component.literal("That zone overlaps " + reservedPad
              + " — the colony has already committed that ground. Move a corner to clear ground and mark again."));
            return InteractionResult.SUCCESS;
        }

        if (!zones.add(colony.getID(), box))
        {
            player.sendSystemMessage(Component.literal("This colony already has the most protected zones allowed ("
              + AutopilotConfig.get(colony, AutopilotConfig.ZONE_MAX_PER_COLONY) + "). Delete one first (sneak-right-click)."));
            return InteractionResult.SUCCESS;
        }

        final int lampsCleared = VillagePaths.clearLampsIn(level, colony, box);
        final int width = maxX - minX + 1;
        final int depth = maxZ - minZ + 1;

        broadcast(level, player, player.getName().getString() + " marked a protected zone for "
          + colony.getName() + ": x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ
          + " (" + width + " x " + depth + "). The village will build around it."
          + (clamped ? " (Clamped to the " + maxEdge + "-block limit.)" : "")
          + (lampsCleared > 0 ? " Cleared " + lampsCleared + " lamp post" + (lampsCleared == 1 ? "" : "s") + " inside it." : ""));
        ColonyAutopilot.LOGGER.info("[{}] {} marked a protected zone x {}..{}, z {}..{}",
          colony.getName(), player.getName().getString(), minX, maxX, minZ, maxZ);
        return InteractionResult.SUCCESS;
    }

    private static void broadcast(final ServerLevel level, final Player actor, final String text)
    {
        for (final ServerPlayer online : level.getServer().getPlayerList().getPlayers())
        {
            online.sendSystemMessage(online == actor ? Component.literal(text)
                                       : Component.translatableWithFallback("colonyautopilot.zone.news", "%s", text));
        }
    }
}
