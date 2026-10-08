// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

public final class ZoneRenderer
{
    private static List<AABB> cachedBoxes = List.of();
    private static long lastRebuildTick = Long.MIN_VALUE;

    private ZoneRenderer()
    {
    }

    static void register()
    {
        NeoForge.EVENT_BUS.register(ZoneRenderer.class);
    }

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
              || (!mc.player.getMainHandItem().is(ModItems.ZONE_MARKER.get())
                    && !mc.player.getOffhandItem().is(ModItems.ZONE_MARKER.get())))
        {
            return;
        }

        final long now = mc.level.getGameTime();
        if (now < lastRebuildTick || now >= lastRebuildTick + 20L)
        {
            lastRebuildTick = now;
            cachedBoxes = rebuild(mc);
        }
        if (cachedBoxes.isEmpty())
        {
            return;
        }

        final Camera camera = mc.gameRenderer.getMainCamera();
        final Vec3 cam = camera.getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (final AABB box : cachedBoxes)
        {
            LevelRenderer.renderLineBox(pose, lines, box, 0.15F, 0.9F, 0.35F, 0.85F);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static List<AABB> rebuild(final Minecraft mc)
    {
        if (mc.getSingleplayerServer() == null)
        {
            return List.of();
        }

        final ProtectedZones zones = ProtectedZones.loaded(mc.level.dimension());
        if (zones == null)
        {
            return List.of();
        }
        final List<SiteSelector.Footprint> footprints = zones.allBoxes();
        if (footprints.isEmpty())
        {
            return List.of();
        }
        final List<AABB> boxes = new ArrayList<>(footprints.size());
        for (final SiteSelector.Footprint f : footprints)
        {
            int minH = Integer.MAX_VALUE;
            int maxH = Integer.MIN_VALUE;
            final int[][] corners = {{f.minX(), f.minZ()}, {f.minX(), f.maxZ()}, {f.maxX(), f.minZ()}, {f.maxX(), f.maxZ()}};
            for (final int[] c : corners)
            {
                final int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, c[0], c[1]);
                minH = Math.min(minH, h);
                maxH = Math.max(maxH, h);
            }

            boxes.add(new AABB(f.minX(), minH - 1.0, f.minZ(), f.maxX() + 1.0, maxH + 6.0, f.maxZ() + 1.0));
        }
        return boxes;
    }
}
