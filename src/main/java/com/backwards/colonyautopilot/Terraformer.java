// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Terraformer
{

    private static final int COLUMNS_PER_TICK = 8;

    private static final int WRITES_PER_TICK = 160;

    private static final int MAX_CUT = 12;

    private static final int SKY_HEADROOM = 32;

    private static final int CAUSEWAY_THICKNESS = 12;

    private static final class Job
    {
        final ServerLevel level;
        final IColony colony;
        final Block hut;
        final String hutName;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final int targetY;
        final java.util.List<int[]> ramp;
        final boolean forced;
        final java.util.List<int[]> gradeColumns;

        int gradeRaised;

        final java.util.Set<Long> raisedColumns = new java.util.HashSet<>();
        int cursorX;
        int cursorZ;
        int rampCursor;
        int gradeCursor;

        Job(final ServerLevel level, final IColony colony, final Block hut, final String hutName, final SiteSelector.TerraformSite site)
        {
            this.level = level;
            this.colony = colony;
            this.hut = hut;
            this.hutName = hutName;
            this.minX = site.minX();
            this.minZ = site.minZ();
            this.maxX = site.maxX();
            this.maxZ = site.maxZ();
            this.targetY = site.targetY();
            this.ramp = site.ramp();
            this.forced = site.forced();
            this.gradeColumns = null;
            this.cursorX = site.minX();
            this.cursorZ = site.minZ();
        }

        Job(final ServerLevel level, final IColony colony, final java.util.List<int[]> gradeColumns)
        {
            this.level = level;
            this.colony = colony;
            this.hut = null;
            this.hutName = "the village grounds";
            this.minX = 0;
            this.minZ = 0;
            this.maxX = -1;
            this.maxZ = -1;
            this.targetY = 0;
            this.ramp = java.util.List.of();
            this.forced = false;
            this.gradeColumns = gradeColumns;
        }
    }

    private GrowthDirector growthDirector;

    private final Map<ColonyId, Job> jobs = new HashMap<>();

    void setGrowthDirector(final GrowthDirector growthDirector)
    {
        this.growthDirector = growthDirector;
    }

    @SubscribeEvent
    public void onServerStarted(final ServerStartedEvent event)
    {
        jobs.clear();
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {

        jobs.clear();
    }

    boolean begin(final IColony colony, final ServerLevel level, final Block hut, final String hutName, final SiteSelector.TerraformSite site)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (jobs.containsKey(key))
        {
            return false;
        }
        jobs.put(key, new Job(level, colony, hut, hutName, site));
        ColonyAutopilot.LOGGER.info("[{}] {} for {} — clearing land at ({}, {}) to ({}, {}), ground level {}{}",
          colony.getName(), site.forced() ? "FORCE-carving a last-resort plot" : "no natural plot", hutName,
          site.minX(), site.minZ(), site.maxX(), site.maxZ(), site.targetY(),
          site.ramp().isEmpty() ? "" : ", and grading a walkable approach of " + site.ramp().size() + " columns");
        Milestones.say(colony, "colonyautopilot.milestone.terraform", hutName);
        return true;
    }

    boolean beginGrading(final IColony colony, final ServerLevel level, final java.util.List<int[]> columns)
    {
        final ColonyId key = ColonyAutopilot.colonyKey(colony);
        if (jobs.containsKey(key))
        {
            return false;
        }
        jobs.put(key, new Job(level, colony, columns));
        ColonyAutopilot.LOGGER.info("[{}] the ground gapes between the village's buildings — raising {} sunken column(s) to grade with a causeway",
          colony.getName(), columns.size());
        Milestones.say(colony, "colonyautopilot.milestone.ravine");
        return true;
    }

    @SubscribeEvent
    public void onServerTick(final ServerTickEvent.Post event)
    {

        if (jobs.isEmpty() || !AutopilotConfig.masterOn())
        {
            return;
        }

        jobs.entrySet().removeIf(entry -> {
            if (AutopilotConfig.get(entry.getValue().colony, AutopilotConfig.TERRAFORM_ENABLED))
            {
                return false;
            }
            abandon(entry.getKey(), entry.getValue());
            return true;
        });
        if (jobs.isEmpty())
        {
            return;
        }

        jobs.entrySet().removeIf(entry -> {
            final boolean gone = com.minecolonies.api.colony.IColonyManager.getInstance()
                                   .getColonyByWorld(entry.getValue().colony.getID(), entry.getValue().level) != entry.getValue().colony;
            if (gone)
            {
                abandon(entry.getKey(), entry.getValue());
            }
            return gone;
        });
        final Iterator<Map.Entry<ColonyId, Job>> iterator = jobs.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<ColonyId, Job> entry = iterator.next();
            final Job job = entry.getValue();
            try
            {
                if (workOn(job))
                {
                    iterator.remove();
                    if (job.gradeColumns != null)
                    {
                        ColonyAutopilot.LOGGER.info("[{}] the causeway stands — {} of {} sunken column(s) raised to the village grade",
                          job.colony.getName(), job.gradeRaised, job.gradeColumns.size());
                        if (growthDirector != null)
                        {
                            growthDirector.gradingFinished(job.colony);
                        }
                    }
                    else
                    {
                        ColonyAutopilot.LOGGER.debug("[{}] land cleared for {} — the plot search will find it shortly",
                          job.colony.getName(), job.hutName);
                        if (growthDirector != null)
                        {
                            growthDirector.plotFreed(entry.getKey(), job.hut);

                            growthDirector.groundReshaped(job.colony, new SiteSelector.Footprint(job.minX, job.minZ, job.maxX, job.maxZ), job.ramp);
                        }
                    }
                }
            }
            catch (final Exception e)
            {
                iterator.remove();
                abandon(entry.getKey(), job);
                ColonyAutopilot.LOGGER.warn("[{}] terraforming for {} failed", job.colony.getName(), job.hutName, e);
            }
        }
    }

    private void abandon(final ColonyId key, final Job job)
    {
        if (growthDirector == null)
        {
            return;
        }
        if (job.gradeColumns == null)
        {
            growthDirector.terraformAbandoned(key, job.hut);
        }
        else
        {
            growthDirector.gradingFinished(job.colony);
        }
    }

    private boolean workOn(final Job job)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        final java.util.List<SiteSelector.Footprint> zones = ProtectedZones.footprintsFor(job.colony);
        int writes = 0;
        if (job.gradeColumns != null)
        {
            while (job.gradeCursor < job.gradeColumns.size() && writes < WRITES_PER_TICK && TickBudget.has())
            {
                TickBudget.spend(1);
                final int[] column = job.gradeColumns.get(job.gradeCursor++);
                if (insideZone(zones, column[0], column[1]))
                {
                    continue;
                }
                final int filled = fillColumn(job.level, column[0], column[1], column[2], pos);
                if (filled > 0)
                {
                    job.gradeRaised++;
                    job.raisedColumns.add(BlockPos.asLong(column[0], 0, column[1]));
                }
                writes += filled;
                TickBudget.spend(filled);
            }

            if (!job.raisedColumns.isEmpty())
            {
                ColonyGrounds.get(job.level).addCausewayColumns(job.colony.getID(), job.raisedColumns);
                job.raisedColumns.clear();
            }
            return job.gradeCursor >= job.gradeColumns.size();
        }
        final int skyCeiling = job.colony.getCenter().getY() + SKY_HEADROOM;
        for (int columns = 0; columns < COLUMNS_PER_TICK && writes < WRITES_PER_TICK && TickBudget.has(); columns++)
        {
            TickBudget.spend(1);
            if (job.cursorZ > job.maxZ)
            {

                if (job.rampCursor >= job.ramp.size())
                {
                    return true;
                }
                final int[] tread = job.ramp.get(job.rampCursor++);

                if (!insideZone(zones, tread[0], tread[1]))
                {
                    final int written = levelColumn(job.level, tread[0], tread[1], tread[2] - 1, job.forced ? SiteSelector.FORCED_MAX_CUT : SiteSelector.RAMP_CUT, skyCeiling, pos);
                    writes += written;
                    TickBudget.spend(written);
                }
                continue;
            }
            if (!insideZone(zones, job.cursorX, job.cursorZ))
            {
                final int written = levelColumn(job.level, job.cursorX, job.cursorZ, job.targetY, job.forced ? SiteSelector.FORCED_MAX_CUT : MAX_CUT, skyCeiling, pos);
                writes += written;
                TickBudget.spend(written);
            }
            if (++job.cursorX > job.maxX)
            {
                job.cursorX = job.minX;
                job.cursorZ++;
            }
        }
        return job.cursorZ > job.maxZ && job.rampCursor >= job.ramp.size();
    }

    private static boolean insideZone(final java.util.List<SiteSelector.Footprint> zones, final int x, final int z)
    {
        for (final SiteSelector.Footprint zone : zones)
        {
            if (zone.intersects(x, z, x, z))
            {
                return true;
            }
        }
        return false;
    }

    private static int levelColumn(final ServerLevel level, final int x, final int z, final int targetY, final int maxCut, final int skyCeiling, final BlockPos.MutableBlockPos pos)
    {
        if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
        {
            return 0;
        }
        int writes = 0;
        final int surfaceTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceTop > skyCeiling && !SiteSelector.solidBeneathOrCaveRoof(level, x, z, surfaceTop, pos))
        {
            return 0;

        }

        final int cutTop = surfaceTop > targetY + maxCut && !SiteSelector.solidBeneathOrCaveRoof(level, x, z, surfaceTop, pos)
                             ? targetY + maxCut : surfaceTop;
        for (int y = cutTop; y > targetY; y--)
        {
            if (level.getBlockState(pos.set(x, y, z)).hasBlockEntity())
            {
                return writes;
            }
        }
        for (int y = cutTop; y > targetY; y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.hasBlockEntity())
            {
                return writes;
            }
            if (!state.isAir())
            {
                WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
                writes++;
            }
        }

        for (int y = targetY; y > targetY - maxCut; y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.hasBlockEntity())
            {
                return writes;
            }
            if (state.canBeReplaced() || !state.getFluidState().isEmpty())
            {
                WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.DIRT.defaultBlockState());
                writes++;
            }
            else if (y < targetY)
            {
                break;
            }
        }

        if (level.getBlockState(pos.set(x, targetY, z)).is(Blocks.DIRT))
        {
            WorldUtil.setBlockState(level, pos.set(x, targetY, z), Blocks.GRASS_BLOCK.defaultBlockState());
            writes++;
        }
        return writes;
    }

    private static int fillColumn(final ServerLevel level, final int x, final int z, final int targetY, final BlockPos.MutableBlockPos pos)
    {
        if (!WorldUtil.isChunkLoaded(level, x >> 4, z >> 4))
        {
            return 0;
        }
        int writes = 0;
        for (int y = targetY; y > targetY - CAUSEWAY_THICKNESS; y--)
        {
            final BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.hasBlockEntity())
            {
                return writes;
            }

            if (state.isAir() || state.canBeReplaced()
                  || (!state.getFluidState().isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED))
                  || state.is(Blocks.DIRT_PATH) || state.is(Blocks.OAK_FENCE) || state.is(Blocks.GLOWSTONE))
            {
                WorldUtil.setBlockState(level, pos.set(x, y, z), Blocks.DIRT.defaultBlockState());
                writes++;
            }
            else if (y < targetY)
            {
                break;
            }
        }
        if (level.getBlockState(pos.set(x, targetY, z)).is(Blocks.DIRT))
        {
            WorldUtil.setBlockState(level, pos.set(x, targetY, z), Blocks.GRASS_BLOCK.defaultBlockState());
            writes++;
        }
        return writes;
    }
}
