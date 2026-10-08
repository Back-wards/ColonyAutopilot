// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintTagUtils;
import com.minecolonies.api.blocks.AbstractBlockHut;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class BlueprintGround
{

    private static final int MAX_OFFSET = 16;

    static final int ENTRANCE_BAND = 6;

    record Front(Direction side, BlockPos doorOffset)
    {
    }

    private BlueprintGround()
    {
    }

    static int placementOffset(final Blueprint blueprint)
    {
        final BlockPos anchor = blueprint.getPrimaryBlockOffset();
        final BlockState anchorState = blueprint.getBlockState(anchor);
        final String[] missing = blueprint.getMissingMods();
        if (BlueprintTagUtils.getFirstPosForTag(blueprint, Constants.GROUNDLEVEL_TAG) != null
              || anchorState == null || !(anchorState.getBlock() instanceof AbstractBlockHut)
              || (missing != null && missing.length > 0))
        {
            return BlueprintTagUtils.getGroundAnchorOffset(blueprint, 1);
        }
        final short[][][] structure = blueprint.getStructure();
        final BlockState[] palette = blueprint.getPalette();
        final int sizeX = blueprint.getSizeX(), sizeY = blueprint.getSizeY(), sizeZ = blueprint.getSizeZ();
        final Block keep = ModBlocks.blockSubstitution.get();
        boolean outsideSeen = false;
        for (int y = sizeY - 1; y >= 0; y--)
        {
            boolean air = false;
            boolean settled = false;
            for (int z = 0; z < sizeZ && !air; z++)
            {
                for (int x = 0; x < sizeX && !air; x++)
                {
                    if (x != 0 && z != 0 && x != sizeX - 1 && z != sizeZ - 1)
                    {
                        continue;
                    }
                    final BlockState state = palette[structure[y][z][x] & 0xFFFF];
                    if (state == null || state.isAir())
                    {
                        air = true;
                    }
                    else if (state.getBlock() != keep)
                    {
                        settled = true;
                    }
                }
            }
            if (air)
            {
                outsideSeen = true;
                continue;
            }
            if (!settled)
            {
                continue;
            }
            final int offset = anchor.getY() - y;
            if (!outsideSeen || offset < 0 || offset > MAX_OFFSET)
            {
                ColonyAutopilot.LOGGER.info("blueprint {}: no readable ground line ({}) — the hut block goes on the surface, as the build tool would put it",
                  blueprint.getFileName(), !outsideSeen ? "its top layer is closed all round"
                                             : "the line reads " + Math.abs(offset) + " layer(s) " + (offset < 0 ? "above" : "under") + " the hut block");
                return 1;
            }
            return offset;
        }
        return 1;
    }

    static Front front(final Blueprint blueprint, final int placementOffset)
    {
        final BlockPos anchor = blueprint.getPrimaryBlockOffset();
        final short[][][] structure = blueprint.getStructure();
        final BlockState[] palette = blueprint.getPalette();
        final int sizeX = blueprint.getSizeX(), sizeY = blueprint.getSizeY(), sizeZ = blueprint.getSizeZ();
        final int groundLayer = anchor.getY() - placementOffset;
        final Block keep = ModBlocks.blockSubstitution.get();
        final Block water = ModBlocks.blockFluidSubstitution.get();
        Front gate = null;
        for (int y = Math.max(0, groundLayer); y <= Math.min(sizeY - 1, groundLayer + ENTRANCE_BAND); y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    final BlockState state = palette[structure[y][z][x] & 0xFFFF];
                    if (state == null)
                    {
                        continue;
                    }
                    final boolean door = state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)
                                           && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                    final boolean isGate = !door && state.getBlock() instanceof FenceGateBlock;
                    if ((!door && !isGate) || (isGate && gate != null) || !state.hasProperty(HorizontalDirectionalBlock.FACING))
                    {
                        continue;
                    }
                    final Direction axis = state.getValue(HorizontalDirectionalBlock.FACING);
                    Direction way = null;
                    int shortest = Integer.MAX_VALUE;
                    for (final Direction out : new Direction[] {axis, axis.getOpposite()})
                    {
                        int px = x + out.getStepX(), pz = z + out.getStepZ(), run = 0;
                        boolean open = true;
                        while (px >= 0 && px < sizeX && pz >= 0 && pz < sizeZ)
                        {
                            final BlockState between = palette[structure[y][pz][px] & 0xFFFF];
                            if (between != null && !between.isAir() && between.getBlock() != keep && between.getBlock() != water)
                            {
                                open = false;
                                break;
                            }
                            run++;
                            px += out.getStepX();
                            pz += out.getStepZ();
                        }
                        if (open && run < shortest)
                        {
                            shortest = run;
                            way = out;
                        }
                    }
                    if (way == null)
                    {
                        continue;
                    }
                    final Front found = new Front(way, new BlockPos(x, y, z).subtract(anchor));
                    if (door)
                    {
                        return found;
                    }
                    gate = found;
                }
            }
        }
        return gate != null ? gate : opening(blueprint, placementOffset);
    }

    private static Front opening(final Blueprint blueprint, final int placementOffset)
    {
        if (placementOffset > 2)
        {
            return null;
        }
        final BlockPos anchor = blueprint.getPrimaryBlockOffset();
        final short[][][] structure = blueprint.getStructure();
        final BlockState[] palette = blueprint.getPalette();
        final int sizeX = blueprint.getSizeX(), sizeZ = blueprint.getSizeZ();
        final int y = anchor.getY();
        final int[][] steps = new int[sizeZ][sizeX];
        final java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[] {anchor.getX(), anchor.getZ()});
        steps[anchor.getZ()][anchor.getX()] = 1;
        final int[] soonest = new int[4];
        final BlockPos[] exit = new BlockPos[4];
        while (!queue.isEmpty())
        {
            final int[] cell = queue.poll();
            for (final Direction way : Direction.Plane.HORIZONTAL)
            {
                final int x = cell[0] + way.getStepX(), z = cell[1] + way.getStepZ();
                if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ || steps[z][x] != 0
                      || !passable(structure, palette, x, y, z) || !passable(structure, palette, x, y + 1, z))
                {
                    continue;
                }
                steps[z][x] = steps[cell[1]][cell[0]] + 1;
                queue.add(new int[] {x, z});
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final boolean onEdge = switch (side)
                    {
                        case NORTH -> z == 0;
                        case SOUTH -> z == sizeZ - 1;
                        case WEST -> x == 0;
                        default -> x == sizeX - 1;
                    };
                    if (onEdge && soonest[side.get2DDataValue()] == 0)
                    {
                        soonest[side.get2DDataValue()] = steps[z][x];
                        exit[side.get2DDataValue()] = new BlockPos(x, y, z);
                    }
                }
            }
        }
        Direction best = null;
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            final int reached = soonest[side.get2DDataValue()];
            if (reached != 0 && (best == null || reached < soonest[best.get2DDataValue()]))
            {
                best = side;
            }
        }
        return best == null ? null : new Front(best, exit[best.get2DDataValue()].subtract(anchor));
    }

    private static boolean passable(final short[][][] structure, final BlockState[] palette, final int x, final int y, final int z)
    {
        if (y >= structure.length)
        {
            return true;
        }
        final BlockState state = palette[structure[y][z][x] & 0xFFFF];
        if (state == null || !state.getFluidState().isEmpty())
        {
            return false;
        }
        return state.isAir() || state.getBlock() == ModBlocks.blockSubstitution.get() || !state.blocksMotion()
                 || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }
}
