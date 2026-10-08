// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColonyGrounds extends SavedData
{
    private static final String NAME = "colonyautopilot_grounds";

    static final int JOB_NONE = 0;
    static final int JOB_PLACEMENT = 1;
    static final int JOB_UPGRADE = 2;

    private final Map<Integer, Long> apronDoneAt = new HashMap<>();

    private final Map<Integer, Long> lastDailyRound = new HashMap<>();

    private final Map<Integer, Map<String, String>> overrides = new HashMap<>();

    private final Map<Integer, Map<Long, Integer>> anchorOffsets = new HashMap<>();

    private final Map<Integer, Map<Long, Integer>> fronts = new HashMap<>();

    private final Map<Integer, Set<Long>> fields = new HashMap<>();

    private final Map<Integer, Set<Long>> ponds = new HashMap<>();

    private final Map<Integer, Map<Long, int[]>> decorations = new HashMap<>();

    private final Map<Integer, Map<Long, Integer>> warehouseStock = new HashMap<>();

    private final Map<Integer, Map<Long, Integer>> warehouseSeeded = new HashMap<>();

    private final Map<Integer, Set<Long>> tornDown = new HashMap<>();

    private final Map<Integer, Set<Long>> crushersWoken = new HashMap<>();

    private final Map<Integer, Set<Long>> owedRepairs = new HashMap<>();

    private final Map<Integer, Map<Long, SentinelMemory>> sentinelMemory = new HashMap<>();

    private final Map<Integer, Set<Long>> causeways = new HashMap<>();

    private final Map<Integer, Map<Long, long[]>> roads = new HashMap<>();

    static final short NO_GROUND = Short.MIN_VALUE;

    private final Map<Integer, Map<Long, short[]>> groundMemory = new HashMap<>();

    private final Map<Integer, Long> treasury = new HashMap<>();

    private final Set<Integer> economyAnnounced = new HashSet<>();

    private final Map<Integer, Integer> lastJobKind = new HashMap<>();

    private final Map<Integer, Integer> upgradesSinceLastPlacement = new HashMap<>();

    private final Map<Integer, Map<Long, Map<String, Integer>>> stockLines = new HashMap<>();

    private final Map<Integer, long[]> raidPay = new HashMap<>();

    private final Map<Integer, Reserve> reserves = new HashMap<>();

    private final Map<Integer, long[]> ledgers = new HashMap<>();

    private long ledgerDay = -1;
    private long ledgerWorldDay = -1;

    private final Map<Long, Long> golemFalls = new HashMap<>();

    record Reserve(long credits, long stocked, String id, String name, String good)
    {
    }

    record SentinelMemory(String hutBlock, int level, int facing, String rotationMirror, String pack, String blueprintPath,
                          Integer anchorOffset, Integer front, boolean takenInPlace)
    {
    }

    public ColonyGrounds()
    {
    }

    static ColonyGrounds get(final ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(
          new SavedData.Factory<>(ColonyGrounds::new, ColonyGrounds::load, null), NAME);
    }

    boolean isApronDone(final int colonyId, final BlockPos hall)
    {
        final Long at = apronDoneAt.get(colonyId);
        return at != null && at == hall.asLong();
    }

    void markApronDone(final int colonyId, final BlockPos hall)
    {
        final Long previous = apronDoneAt.put(colonyId, hall.asLong());
        if (previous == null || previous != hall.asLong())
        {
            setDirty();
        }
    }

    void clearApronDone(final int colonyId)
    {
        if (apronDoneAt.remove(colonyId) != null)
        {
            setDirty();
        }
    }

    Long lastDailyRound(final int colonyId)
    {
        return lastDailyRound.get(colonyId);
    }

    void setLastDailyRound(final int colonyId, final long gameTime)
    {
        lastDailyRound.put(colonyId, gameTime);
        setDirty();
    }

    boolean hasAnchorOffset(final int colonyId, final BlockPos anchor)
    {
        final Map<Long, Integer> offsets = anchorOffsets.get(colonyId);
        return offsets != null && offsets.containsKey(anchor.asLong());
    }

    int anchorOffset(final int colonyId, final BlockPos anchor, final int fallback)
    {
        final Map<Long, Integer> offsets = anchorOffsets.get(colonyId);
        return offsets == null ? fallback : offsets.getOrDefault(anchor.asLong(), fallback);
    }

    void setAnchorOffset(final int colonyId, final BlockPos anchor, final int offset)
    {
        anchorOffsets.computeIfAbsent(colonyId, k -> new HashMap<>()).put(anchor.asLong(), offset);
        setDirty();
    }

    Direction front(final int colonyId, final BlockPos anchor, final Direction fallback)
    {
        final Map<Long, Integer> sides = fronts.get(colonyId);
        final Integer side = sides == null ? null : sides.get(anchor.asLong());
        return side == null ? fallback : Direction.from2DDataValue(side);
    }

    void setFront(final int colonyId, final BlockPos anchor, final Direction side)
    {
        fronts.computeIfAbsent(colonyId, k -> new HashMap<>()).put(anchor.asLong(), side.get2DDataValue());
        setDirty();
    }

    Set<Long> causewayColumns(final int colonyId)
    {
        return Set.copyOf(causeways.getOrDefault(colonyId, Set.of()));
    }

    boolean isCausewayColumn(final int colonyId, final long column)
    {
        final Set<Long> raised = causeways.get(colonyId);
        return raised != null && raised.contains(column);
    }

    void addCausewayColumns(final int colonyId, final Set<Long> columns)
    {
        if (!columns.isEmpty() && causeways.computeIfAbsent(colonyId, k -> new HashSet<>()).addAll(columns))
        {
            setDirty();
        }
    }

    Map<BlockPos, List<BlockPos>> roads(final int colonyId)
    {
        final Map<BlockPos, List<BlockPos>> ledger = new HashMap<>();
        for (final Map.Entry<Long, long[]> road : roads.getOrDefault(colonyId, Map.of()).entrySet())
        {
            final List<BlockPos> blocks = new ArrayList<>(road.getValue().length);
            for (final long block : road.getValue())
            {
                blocks.add(BlockPos.of(block));
            }
            ledger.put(BlockPos.of(road.getKey()), blocks);
        }
        return ledger;
    }

    void setRoads(final int colonyId, final Map<BlockPos, List<BlockPos>> ledger)
    {
        final Map<Long, long[]> packed = new HashMap<>();
        for (final Map.Entry<BlockPos, List<BlockPos>> road : ledger.entrySet())
        {
            final long[] blocks = new long[road.getValue().size()];
            for (int i = 0; i < blocks.length; i++)
            {
                blocks[i] = road.getValue().get(i).asLong();
            }
            packed.put(road.getKey().asLong(), blocks);
        }
        if (packed.isEmpty())
        {
            roads.remove(colonyId);
        }
        else
        {
            roads.put(colonyId, packed);
        }
        setDirty();
    }

    short[] groundMemory(final int colonyId, final long chunk)
    {
        final Map<Long, short[]> chunks = groundMemory.get(colonyId);
        return chunks == null ? null : chunks.get(chunk);
    }

    boolean rememberGround(final int colonyId, final long chunk, final int column, final int height)
    {
        final short[] heights = groundMemory.computeIfAbsent(colonyId, k -> new HashMap<>()).computeIfAbsent(chunk, k -> {
            final short[] fresh = new short[256];
            java.util.Arrays.fill(fresh, NO_GROUND);
            return fresh;
        });
        if (heights[column] == height)
        {
            return false;
        }
        heights[column] = (short) height;
        setDirty();
        return true;
    }

    boolean isField(final int colonyId, final BlockPos scarecrow)
    {
        final Set<Long> laid = fields.get(colonyId);
        return laid != null && laid.contains(scarecrow.asLong());
    }

    void addField(final int colonyId, final BlockPos scarecrow)
    {
        if (fields.computeIfAbsent(colonyId, k -> new HashSet<>()).add(scarecrow.asLong()))
        {
            setDirty();
        }
    }

    Set<Long> ponds(final int colonyId)
    {
        return Set.copyOf(ponds.getOrDefault(colonyId, Set.of()));
    }

    void addPond(final int colonyId, final BlockPos corner)
    {
        if (ponds.computeIfAbsent(colonyId, k -> new HashSet<>()).add(corner.asLong()))
        {
            setDirty();
        }
    }

    void removePond(final int colonyId, final BlockPos corner)
    {
        final Set<Long> dug = ponds.get(colonyId);
        if (dug != null && dug.remove(corner.asLong()))
        {
            setDirty();
        }
    }

    Long golemFall(final long post)
    {
        return golemFalls.get(post);
    }

    void setGolemFall(final long post, final long gameTime)
    {
        golemFalls.put(post, gameTime);
        setDirty();
    }

    void clearGolemFall(final long post)
    {
        if (golemFalls.remove(post) != null)
        {
            setDirty();
        }
    }

    void pruneGolemFalls(final long gameTime)
    {
        if (golemFalls.values().removeIf(fell -> fell < gameTime))
        {
            setDirty();
        }
    }

    Map<Long, int[]> decorations(final int colonyId)
    {
        return Map.copyOf(decorations.getOrDefault(colonyId, Map.of()));
    }

    void addDecoration(final int colonyId, final BlockPos anchor, final int minX, final int minZ, final int maxX, final int maxZ)
    {
        decorations.computeIfAbsent(colonyId, k -> new HashMap<>()).put(anchor.asLong(), new int[] {minX, minZ, maxX, maxZ});
        setDirty();
    }

    void addDecoration(final int colonyId, final BlockPos anchor, final int minX, final int minZ, final int maxX, final int maxZ,
                       final int wx, final int wy, final int wz)
    {
        decorations.computeIfAbsent(colonyId, k -> new HashMap<>()).put(anchor.asLong(), new int[] {minX, minZ, maxX, maxZ, wx, wy, wz});
        setDirty();
    }

    void removeDecoration(final int colonyId, final BlockPos anchor)
    {
        final Map<Long, int[]> boxes = decorations.get(colonyId);
        if (boxes != null && boxes.remove(anchor.asLong()) != null)
        {
            setDirty();
        }
    }

    int warehouseStockLevel(final int colonyId, final BlockPos warehouse)
    {
        final Map<Long, Integer> levels = warehouseStock.get(colonyId);
        return levels == null ? 0 : levels.getOrDefault(warehouse.asLong(), 0);
    }

    void setWarehouseStockLevel(final int colonyId, final BlockPos warehouse, final int level)
    {
        warehouseStock.computeIfAbsent(colonyId, k -> new HashMap<>()).put(warehouse.asLong(), level);
        setDirty();
    }

    int warehouseSeeded(final int colonyId, final BlockPos warehouse)
    {
        final Map<Long, Integer> masks = warehouseSeeded.get(colonyId);
        return masks == null ? -1 : masks.getOrDefault(warehouse.asLong(), -1);
    }

    void setWarehouseSeeded(final int colonyId, final BlockPos warehouse, final int mask)
    {
        final Integer previous = warehouseSeeded.computeIfAbsent(colonyId, k -> new HashMap<>()).put(warehouse.asLong(), mask);
        if (previous == null || previous != mask)
        {
            setDirty();
        }
    }

    void retainWarehouses(final int colonyId, final Set<Long> liveWarehouses)
    {
        final Map<Long, Integer> levels = warehouseStock.get(colonyId);
        boolean pruned = levels != null && levels.keySet().retainAll(liveWarehouses);
        final Map<Long, Integer> masks = warehouseSeeded.get(colonyId);
        pruned |= masks != null && masks.keySet().retainAll(liveWarehouses);
        if (pruned)
        {
            setDirty();
        }
    }

    boolean isTornDown(final int colonyId, final BlockPos hut)
    {
        final Set<Long> huts = tornDown.get(colonyId);
        return huts != null && huts.contains(hut.asLong());
    }

    void setTornDown(final int colonyId, final BlockPos hut, final boolean down)
    {
        final boolean changed = down
                                  ? tornDown.computeIfAbsent(colonyId, k -> new HashSet<>()).add(hut.asLong())
                                  : tornDown.containsKey(colonyId) && tornDown.get(colonyId).remove(hut.asLong());
        if (changed)
        {
            setDirty();
        }
    }

    boolean crusherWoken(final int colonyId, final BlockPos pos)
    {
        final Set<Long> woken = crushersWoken.get(colonyId);
        return woken != null && woken.contains(pos.asLong());
    }

    void markCrusherWoken(final int colonyId, final BlockPos pos)
    {
        if (crushersWoken.computeIfAbsent(colonyId, k -> new HashSet<>()).add(pos.asLong()))
        {
            setDirty();
        }
    }

    void retainCrushers(final int colonyId, final Set<Long> live)
    {
        final Set<Long> woken = crushersWoken.get(colonyId);
        if (woken != null && woken.retainAll(live))
        {
            setDirty();
        }
    }

    boolean owesRepair(final int colonyId, final BlockPos hut)
    {
        final Set<Long> owed = owedRepairs.get(colonyId);
        return owed != null && owed.contains(hut.asLong());
    }

    List<Long> owedRepairs(final int colonyId)
    {
        return new ArrayList<>(owedRepairs.getOrDefault(colonyId, Set.of()));
    }

    void oweRepair(final int colonyId, final BlockPos hut)
    {
        if (owedRepairs.computeIfAbsent(colonyId, k -> new LinkedHashSet<>()).add(hut.asLong()))
        {
            setDirty();
        }
    }

    void settleRepair(final int colonyId, final BlockPos hut)
    {
        final Set<Long> owed = owedRepairs.get(colonyId);
        if (owed != null && owed.remove(hut.asLong()))
        {
            setDirty();
        }
    }

    Map<Long, SentinelMemory> sentinelMemory(final int colonyId)
    {
        return Map.copyOf(sentinelMemory.getOrDefault(colonyId, Map.of()));
    }

    void setSentinelMemory(final int colonyId, final BlockPos hut, final SentinelMemory memory)
    {
        if (!memory.equals(sentinelMemory.computeIfAbsent(colonyId, k -> new HashMap<>()).put(hut.asLong(), memory)))
        {
            setDirty();
        }
    }

    void removeSentinelMemory(final int colonyId, final BlockPos hut)
    {
        final Map<Long, SentinelMemory> remembered = sentinelMemory.get(colonyId);
        if (remembered != null && remembered.remove(hut.asLong()) != null)
        {
            setDirty();
        }
    }

    void clearSentinelMemory(final int colonyId)
    {
        final Map<Long, SentinelMemory> remembered = sentinelMemory.remove(colonyId);
        if (remembered != null && !remembered.isEmpty())
        {
            setDirty();
        }
    }

    void forgetBuilding(final int colonyId, final BlockPos pos)
    {
        final long at = pos.asLong();
        final Map<Long, Integer> levels = warehouseStock.get(colonyId);
        boolean dropped = levels != null && levels.remove(at) != null;
        final Map<Long, Integer> masks = warehouseSeeded.get(colonyId);
        dropped |= masks != null && masks.remove(at) != null;
        final Map<Long, Map<String, Integer>> lines = stockLines.get(colonyId);
        dropped |= lines != null && lines.remove(at) != null;
        final Set<Long> woken = crushersWoken.get(colonyId);
        dropped |= woken != null && woken.remove(at);
        if (dropped)
        {
            setDirty();
        }
    }

    Map<String, String> overrides(final int colonyId)
    {
        return java.util.Collections.unmodifiableMap(overrides.getOrDefault(colonyId, Map.of()));
    }

    void setOverride(final int colonyId, final String path, final String text)
    {
        overrides.computeIfAbsent(colonyId, k -> new HashMap<>()).put(path, text);
        setDirty();
    }

    void clearOverride(final int colonyId, final String path)
    {
        final Map<String, String> own = overrides.get(colonyId);
        if (own != null && own.remove(path) != null)
        {
            setDirty();
        }
    }

    long treasuryCredits(final int colonyId)
    {
        return treasury.getOrDefault(colonyId, 0L);
    }

    void setTreasuryCredits(final int colonyId, final long credits)
    {
        if (credits == 0)
        {
            treasury.remove(colonyId);
        }
        else
        {
            treasury.put(colonyId, credits);
        }
        setDirty();
    }

    boolean economyAnnounced(final int colonyId)
    {
        return economyAnnounced.contains(colonyId);
    }

    void setEconomyAnnounced(final int colonyId, final boolean announced)
    {
        if (announced ? economyAnnounced.add(colonyId) : economyAnnounced.remove(colonyId))
        {
            setDirty();
        }
    }

    int lastJobKind(final int colonyId)
    {
        return lastJobKind.getOrDefault(colonyId, JOB_NONE);
    }

    void setLastJobKind(final int colonyId, final int kind)
    {
        if (kind == JOB_NONE)
        {
            lastJobKind.remove(colonyId);
        }
        else
        {
            lastJobKind.put(colonyId, kind);
        }
        setDirty();
    }

    int upgradesSinceLastPlacement(final int colonyId)
    {
        return upgradesSinceLastPlacement.getOrDefault(colonyId, 0);
    }

    void setUpgradesSinceLastPlacement(final int colonyId, final int count)
    {
        if (count == 0)
        {
            upgradesSinceLastPlacement.remove(colonyId);
        }
        else
        {
            upgradesSinceLastPlacement.put(colonyId, count);
        }
        setDirty();
    }

    int stockLine(final int colonyId, final BlockPos building, final Item item)
    {
        final Map<String, Integer> items = stockLines.getOrDefault(colonyId, Map.of()).get(building.asLong());
        final Integer stacks = items == null ? null : items.get(BuiltInRegistries.ITEM.getKey(item).toString());
        return stacks == null ? -1 : stacks;
    }

    void setStockLine(final int colonyId, final BlockPos building, final Item item, final int stacks)
    {
        final String id = BuiltInRegistries.ITEM.getKey(item).toString();
        final boolean changed;
        if (stacks > 0)
        {
            changed = !Integer.valueOf(stacks).equals(
              stockLines.computeIfAbsent(colonyId, k -> new HashMap<>()).computeIfAbsent(building.asLong(), k -> new HashMap<>()).put(id, stacks));
        }
        else
        {
            final Map<String, Integer> items = stockLines.getOrDefault(colonyId, Map.of()).get(building.asLong());
            changed = items != null && items.remove(id) != null;
        }
        if (changed)
        {
            setDirty();
        }
    }

    long[] raidPay(final int colonyId)
    {
        final long[] pay = raidPay.get(colonyId);
        return pay == null ? null : pay.clone();
    }

    void setRaidPay(final int colonyId, final long raidTime, final long paid, final long told)
    {
        raidPay.put(colonyId, new long[] {raidTime, paid, told});
        setDirty();
    }

    long ledgerDay(final long worldDay)
    {
        if (ledgerWorldDay < 0)
        {
            ledgerDay = worldDay;
        }
        else if (worldDay > ledgerWorldDay)
        {
            ledgerDay += worldDay - ledgerWorldDay;
        }
        if (worldDay != ledgerWorldDay)
        {
            ledgerWorldDay = worldDay;
            setDirty();
        }
        return ledgerDay;
    }

    long[] ledger(final int colonyId)
    {
        final long[] ledger = ledgers.get(colonyId);
        return ledger == null ? null : ledger.clone();
    }

    void setLedger(final int colonyId, final long[] ledger)
    {
        ledgers.put(colonyId, ledger.clone());
        setDirty();
    }

    Reserve reserve(final int colonyId)
    {
        return reserves.get(colonyId);
    }

    void setReserve(final int colonyId, final Reserve reserve)
    {
        if (!java.util.Objects.equals(reserve == null ? reserves.remove(colonyId) : reserves.put(colonyId, reserve), reserve))
        {
            setDirty();
        }
    }

    void forget(final int colonyId)
    {
        boolean dropped = apronDoneAt.remove(colonyId) != null;
        dropped |= lastDailyRound.remove(colonyId) != null;
        dropped |= anchorOffsets.remove(colonyId) != null;
        dropped |= fronts.remove(colonyId) != null;
        dropped |= fields.remove(colonyId) != null;
        dropped |= ponds.remove(colonyId) != null;
        dropped |= decorations.remove(colonyId) != null;
        dropped |= warehouseStock.remove(colonyId) != null;
        dropped |= warehouseSeeded.remove(colonyId) != null;
        dropped |= tornDown.remove(colonyId) != null;
        dropped |= crushersWoken.remove(colonyId) != null;
        dropped |= owedRepairs.remove(colonyId) != null;
        dropped |= sentinelMemory.remove(colonyId) != null;
        dropped |= overrides.remove(colonyId) != null;
        dropped |= causeways.remove(colonyId) != null;
        dropped |= roads.remove(colonyId) != null;
        dropped |= groundMemory.remove(colonyId) != null;
        dropped |= treasury.remove(colonyId) != null;
        dropped |= economyAnnounced.remove(colonyId);
        dropped |= lastJobKind.remove(colonyId) != null;
        dropped |= upgradesSinceLastPlacement.remove(colonyId) != null;
        dropped |= stockLines.remove(colonyId) != null;
        dropped |= raidPay.remove(colonyId) != null;
        dropped |= reserves.remove(colonyId) != null;
        dropped |= ledgers.remove(colonyId) != null;
        if (dropped)
        {
            setDirty();
        }
    }

    void retainAnchors(final int colonyId, final Set<Long> liveAnchors)
    {
        final Map<Long, Integer> offsets = anchorOffsets.get(colonyId);
        boolean pruned = offsets != null && offsets.keySet().retainAll(liveAnchors);
        final Map<Long, Integer> sides = fronts.get(colonyId);
        pruned |= sides != null && sides.keySet().retainAll(liveAnchors);
        final Set<Long> down = tornDown.get(colonyId);
        pruned |= down != null && down.retainAll(liveAnchors);
        final Set<Long> owed = owedRepairs.get(colonyId);
        pruned |= owed != null && owed.retainAll(liveAnchors);

        final Map<Long, Map<String, Integer>> lines = stockLines.get(colonyId);
        pruned |= lines != null && lines.keySet().retainAll(liveAnchors);
        if (pruned)
        {
            setDirty();
        }
    }

    void retainFields(final int colonyId, final Set<Long> liveScarecrows)
    {
        final Set<Long> laid = fields.get(colonyId);
        if (laid != null && laid.retainAll(liveScarecrows))
        {
            setDirty();
        }
    }

    void retain(final Set<Integer> liveColonyIds)
    {
        boolean pruned = apronDoneAt.keySet().retainAll(liveColonyIds);
        pruned |= lastDailyRound.keySet().retainAll(liveColonyIds);
        pruned |= anchorOffsets.keySet().retainAll(liveColonyIds);
        pruned |= fronts.keySet().retainAll(liveColonyIds);
        pruned |= fields.keySet().retainAll(liveColonyIds);
        pruned |= ponds.keySet().retainAll(liveColonyIds);
        pruned |= decorations.keySet().retainAll(liveColonyIds);
        pruned |= warehouseStock.keySet().retainAll(liveColonyIds);
        pruned |= warehouseSeeded.keySet().retainAll(liveColonyIds);
        pruned |= tornDown.keySet().retainAll(liveColonyIds);
        pruned |= crushersWoken.keySet().retainAll(liveColonyIds);
        pruned |= owedRepairs.keySet().retainAll(liveColonyIds);
        pruned |= sentinelMemory.keySet().retainAll(liveColonyIds);
        pruned |= overrides.keySet().retainAll(liveColonyIds);
        pruned |= causeways.keySet().retainAll(liveColonyIds);
        pruned |= roads.keySet().retainAll(liveColonyIds);
        pruned |= groundMemory.keySet().retainAll(liveColonyIds);
        pruned |= treasury.keySet().retainAll(liveColonyIds);
        pruned |= economyAnnounced.retainAll(liveColonyIds);
        pruned |= lastJobKind.keySet().retainAll(liveColonyIds);
        pruned |= upgradesSinceLastPlacement.keySet().retainAll(liveColonyIds);
        pruned |= stockLines.keySet().retainAll(liveColonyIds);
        pruned |= raidPay.keySet().retainAll(liveColonyIds);
        pruned |= reserves.keySet().retainAll(liveColonyIds);
        pruned |= ledgers.keySet().retainAll(liveColonyIds);
        if (pruned)
        {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final Set<Integer> ids = new HashSet<>(apronDoneAt.keySet());
        ids.addAll(lastDailyRound.keySet());
        ids.addAll(anchorOffsets.keySet());
        ids.addAll(fronts.keySet());
        ids.addAll(fields.keySet());
        ids.addAll(ponds.keySet());
        ids.addAll(decorations.keySet());
        ids.addAll(warehouseStock.keySet());
        ids.addAll(warehouseSeeded.keySet());
        ids.addAll(tornDown.keySet());
        ids.addAll(crushersWoken.keySet());
        ids.addAll(owedRepairs.keySet());
        ids.addAll(sentinelMemory.keySet());
        ids.addAll(causeways.keySet());
        ids.addAll(roads.keySet());
        ids.addAll(groundMemory.keySet());
        ids.addAll(overrides.keySet());
        ids.addAll(treasury.keySet());
        ids.addAll(economyAnnounced);
        ids.addAll(lastJobKind.keySet());
        ids.addAll(upgradesSinceLastPlacement.keySet());
        ids.addAll(stockLines.keySet());
        ids.addAll(raidPay.keySet());
        ids.addAll(reserves.keySet());
        ids.addAll(ledgers.keySet());
        final ListTag colonies = new ListTag();
        for (final int id : ids)
        {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("id", id);
            final Map<String, String> own = overrides.get(id);
            if (own != null && !own.isEmpty())
            {
                final CompoundTag settings = new CompoundTag();
                own.forEach(settings::putString);
                entry.put("overrides", settings);
            }
            final Long apronAt = apronDoneAt.get(id);
            if (apronAt != null)
            {
                entry.putLong("apronDoneAt", apronAt);
            }
            final Long round = lastDailyRound.get(id);
            if (round != null)
            {
                entry.putLong("lastDailyRound", round);
            }
            final Long credits = treasury.get(id);
            if (credits != null)
            {
                entry.putLong("treasury", credits);
            }
            if (economyAnnounced.contains(id))
            {
                entry.putBoolean("economyAnnounced", true);
            }
            final Integer jobKind = lastJobKind.get(id);
            if (jobKind != null)
            {
                entry.putInt("lastJobKind", jobKind);
            }
            final Integer upgrades = upgradesSinceLastPlacement.get(id);
            if (upgrades != null)
            {
                entry.putInt("upgradesSinceLastPlacement", upgrades);
            }
            final long[] pay = raidPay.get(id);
            if (pay != null)
            {
                entry.putLongArray("raidPay", pay);
            }
            final Reserve reserve = reserves.get(id);
            if (reserve != null)
            {
                final CompoundTag saved = new CompoundTag();
                saved.putLong("credits", reserve.credits());
                saved.putLong("stocked", reserve.stocked());
                saved.putString("id", reserve.id());
                saved.putString("name", reserve.name());
                saved.putString("good", reserve.good());
                entry.put("reserve", saved);
            }
            final long[] tallies = ledgers.get(id);
            if (tallies != null)
            {
                entry.putLongArray("ledger", tallies);
            }
            final Map<Long, Integer> offsets = anchorOffsets.getOrDefault(id, Map.of());
            final long[] anchors = new long[offsets.size()];
            final int[] values = new int[offsets.size()];
            int i = 0;
            for (final Map.Entry<Long, Integer> offset : offsets.entrySet())
            {
                anchors[i] = offset.getKey();
                values[i] = offset.getValue();
                i++;
            }
            entry.putLongArray("anchors", anchors);
            entry.putIntArray("offsets", values);
            final Map<Long, Integer> sides = fronts.getOrDefault(id, Map.of());
            final long[] frontAnchors = new long[sides.size()];
            final int[] frontSides = new int[sides.size()];
            int f = 0;
            for (final Map.Entry<Long, Integer> side : sides.entrySet())
            {
                frontAnchors[f] = side.getKey();
                frontSides[f] = side.getValue();
                f++;
            }
            entry.putLongArray("frontAnchors", frontAnchors);
            entry.putIntArray("fronts", frontSides);
            entry.putLongArray("fields", packed(fields.getOrDefault(id, Set.of())));
            entry.putLongArray("ponds", packed(ponds.getOrDefault(id, Set.of())));
            final Map<Long, int[]> boxes = decorations.getOrDefault(id, Map.of());
            final long[] decoAnchors = new long[boxes.size()];
            final int[] decoBoxes = new int[boxes.size() * 4];

            final long[] decoWitness = new long[boxes.size()];
            int d = 0;
            for (final Map.Entry<Long, int[]> box : boxes.entrySet())
            {
                final int[] value = box.getValue();
                decoAnchors[d] = box.getKey();
                System.arraycopy(value, 0, decoBoxes, d * 4, 4);
                decoWitness[d] = value.length >= 7 ? BlockPos.asLong(value[4], value[5], value[6]) : box.getKey();
                d++;
            }
            entry.putLongArray("decoAnchors", decoAnchors);
            entry.putIntArray("decoBoxes", decoBoxes);
            entry.putLongArray("decoWitness", decoWitness);
            final Map<Long, Integer> stock = warehouseStock.getOrDefault(id, Map.of());
            final long[] stockAnchors = new long[stock.size()];
            final int[] stockLevels = new int[stock.size()];
            int s = 0;
            for (final Map.Entry<Long, Integer> warehouse : stock.entrySet())
            {
                stockAnchors[s] = warehouse.getKey();
                stockLevels[s] = warehouse.getValue();
                s++;
            }
            entry.putLongArray("stockAnchors", stockAnchors);
            entry.putIntArray("stockLevels", stockLevels);
            final Map<Long, Integer> seeded = warehouseSeeded.getOrDefault(id, Map.of());
            final long[] seededAnchors = new long[seeded.size()];
            final int[] seededMasks = new int[seeded.size()];
            int m = 0;
            for (final Map.Entry<Long, Integer> warehouse : seeded.entrySet())
            {
                seededAnchors[m] = warehouse.getKey();
                seededMasks[m] = warehouse.getValue();
                m++;
            }
            entry.putLongArray("seededAnchors", seededAnchors);
            entry.putIntArray("seededMasks", seededMasks);
            entry.putLongArray("tornDown", packed(tornDown.getOrDefault(id, Set.of())));
            entry.putLongArray("crushersWoken", packed(crushersWoken.getOrDefault(id, Set.of())));
            entry.putLongArray("owedRepairs", packed(owedRepairs.getOrDefault(id, Set.of())));
            final ListTag remembered = new ListTag();
            for (final Map.Entry<Long, SentinelMemory> building : sentinelMemory.getOrDefault(id, Map.of()).entrySet())
            {
                final SentinelMemory memory = building.getValue();
                final CompoundTag saved = new CompoundTag();
                saved.putLong("anchor", building.getKey());
                saved.putString("block", memory.hutBlock());
                saved.putInt("level", memory.level());
                saved.putInt("facing", memory.facing());
                if (memory.rotationMirror() != null)
                {
                    saved.putString("rotation", memory.rotationMirror());
                }
                if (memory.pack() != null)
                {
                    saved.putString("pack", memory.pack());
                }
                if (memory.blueprintPath() != null)
                {
                    saved.putString("path", memory.blueprintPath());
                }
                if (memory.anchorOffset() != null)
                {
                    saved.putInt("offset", memory.anchorOffset());
                }
                if (memory.front() != null)
                {
                    saved.putInt("front", memory.front());
                }
                saved.putBoolean("taken", memory.takenInPlace());
                remembered.add(saved);
            }
            entry.put("sentinel", remembered);
            entry.putLongArray("causeways", packed(causeways.getOrDefault(id, Set.of())));
            final ListTag ledger = new ListTag();
            for (final Map.Entry<Long, long[]> road : roads.getOrDefault(id, Map.of()).entrySet())
            {
                final CompoundTag saved = new CompoundTag();
                saved.putLong("anchor", road.getKey());
                saved.putLongArray("blocks", road.getValue());
                ledger.add(saved);
            }
            entry.put("roads", ledger);
            final Map<Long, short[]> ground = groundMemory.getOrDefault(id, Map.of());
            final long[] groundChunks = new long[ground.size()];
            final int[] groundHeights = new int[ground.size() * 128];
            int g = 0;
            for (final Map.Entry<Long, short[]> chunk : ground.entrySet())
            {
                groundChunks[g] = chunk.getKey();
                final short[] heights = chunk.getValue();
                for (int c = 0; c < 128; c++)
                {
                    groundHeights[g * 128 + c] = (heights[2 * c] << 16) | (heights[2 * c + 1] & 0xFFFF);
                }
                g++;
            }
            entry.putLongArray("groundChunks", groundChunks);
            entry.putIntArray("groundHeights", groundHeights);
            final ListTag lines = new ListTag();
            for (final Map.Entry<Long, Map<String, Integer>> building : stockLines.getOrDefault(id, Map.of()).entrySet())
            {
                final CompoundTag saved = new CompoundTag();
                saved.putLong("anchor", building.getKey());
                final ListTag items = new ListTag();
                final int[] stacks = new int[building.getValue().size()];
                int at = 0;
                for (final Map.Entry<String, Integer> line : building.getValue().entrySet())
                {
                    items.add(StringTag.valueOf(line.getKey()));
                    stacks[at++] = line.getValue();
                }
                saved.put("items", items);
                saved.putIntArray("stacks", stacks);
                lines.add(saved);
            }
            entry.put("stockLines", lines);
            colonies.add(entry);
        }
        tag.put("colonies", colonies);
        tag.putLong("ledgerDay", ledgerDay);
        tag.putLong("ledgerWorldDay", ledgerWorldDay);
        final long[] fallenPosts = new long[golemFalls.size()];
        final long[] fallTimes = new long[golemFalls.size()];
        int g = 0;
        for (final Map.Entry<Long, Long> fall : golemFalls.entrySet())
        {
            fallenPosts[g] = fall.getKey();
            fallTimes[g] = fall.getValue();
            g++;
        }
        tag.putLongArray("golemPosts", fallenPosts);
        tag.putLongArray("golemFalls", fallTimes);
        return tag;
    }

    private static long[] packed(final Set<Long> positions)
    {
        final long[] out = new long[positions.size()];
        int i = 0;
        for (final long pos : positions)
        {
            out[i++] = pos;
        }
        return out;
    }

    static ColonyGrounds load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ColonyGrounds grounds = new ColonyGrounds();
        if (tag.contains("ledgerDay"))
        {
            grounds.ledgerDay = tag.getLong("ledgerDay");
            grounds.ledgerWorldDay = tag.getLong("ledgerWorldDay");
        }
        final long[] fallenPosts = tag.getLongArray("golemPosts");
        final long[] fallTimes = tag.getLongArray("golemFalls");
        if (fallenPosts.length == fallTimes.length)
        {
            for (int j = 0; j < fallenPosts.length; j++)
            {
                grounds.golemFalls.put(fallenPosts[j], fallTimes[j]);
            }
        }
        final ListTag colonies = tag.getList("colonies", Tag.TAG_COMPOUND);
        for (int i = 0; i < colonies.size(); i++)
        {
            final CompoundTag entry = colonies.getCompound(i);
            final int id = entry.getInt("id");
            if (entry.contains("apronDoneAt"))
            {
                grounds.apronDoneAt.put(id, entry.getLong("apronDoneAt"));
            }
            if (entry.contains("lastDailyRound"))
            {
                grounds.lastDailyRound.put(id, entry.getLong("lastDailyRound"));
            }
            if (entry.contains("treasury"))
            {
                grounds.treasury.put(id, entry.getLong("treasury"));
            }
            if (entry.getBoolean("economyAnnounced"))
            {
                grounds.economyAnnounced.add(id);
            }
            if (entry.contains("lastJobKind"))
            {
                grounds.lastJobKind.put(id, entry.getInt("lastJobKind"));
            }
            if (entry.contains("upgradesSinceLastPlacement"))
            {
                grounds.upgradesSinceLastPlacement.put(id, entry.getInt("upgradesSinceLastPlacement"));
            }
            final long[] pay = entry.getLongArray("raidPay");
            if (pay.length == 3)
            {
                grounds.raidPay.put(id, pay);
            }
            if (entry.contains("reserve", Tag.TAG_COMPOUND))
            {
                final CompoundTag saved = entry.getCompound("reserve");
                grounds.reserves.put(id, new Reserve(saved.getLong("credits"), saved.getLong("stocked"), saved.getString("id"), saved.getString("name"),
                  saved.getString("good")));
            }
            final long[] tallies = entry.getLongArray("ledger");
            if (tallies.length > 0)
            {
                grounds.ledgers.put(id, tallies);
            }
            if (entry.contains("overrides"))
            {
                final CompoundTag settings = entry.getCompound("overrides");
                final Map<String, String> own = new HashMap<>();
                for (final String path : settings.getAllKeys())
                {
                    own.put(path, settings.getString(path));
                }
                grounds.overrides.put(id, own);
            }
            final long[] anchors = entry.getLongArray("anchors");
            final int[] values = entry.getIntArray("offsets");
            if (anchors.length > 0 && anchors.length == values.length)
            {
                final Map<Long, Integer> offsets = new HashMap<>();
                for (int j = 0; j < anchors.length; j++)
                {
                    offsets.put(anchors[j], values[j]);
                }
                grounds.anchorOffsets.put(id, offsets);
            }
            final long[] frontAnchors = entry.getLongArray("frontAnchors");
            final int[] frontSides = entry.getIntArray("fronts");
            if (frontAnchors.length > 0 && frontAnchors.length == frontSides.length)
            {
                final Map<Long, Integer> sides = new HashMap<>();
                for (int j = 0; j < frontAnchors.length; j++)
                {
                    sides.put(frontAnchors[j], frontSides[j]);
                }
                grounds.fronts.put(id, sides);
            }
            final long[] laid = entry.getLongArray("fields");
            if (laid.length > 0)
            {
                final Set<Long> set = new HashSet<>();
                for (final long pos : laid)
                {
                    set.add(pos);
                }
                grounds.fields.put(id, set);
            }
            final long[] dug = entry.getLongArray("ponds");
            if (dug.length > 0)
            {
                final Set<Long> set = new HashSet<>();
                for (final long pos : dug)
                {
                    set.add(pos);
                }
                grounds.ponds.put(id, set);
            }
            final long[] decoAnchors = entry.getLongArray("decoAnchors");
            final int[] decoBoxes = entry.getIntArray("decoBoxes");

            final long[] decoWitness = entry.getLongArray("decoWitness");
            if (decoAnchors.length > 0 && decoBoxes.length == decoAnchors.length * 4)
            {
                final Map<Long, int[]> boxes = new HashMap<>();
                for (int j = 0; j < decoAnchors.length; j++)
                {
                    boxes.put(decoAnchors[j], decoWitness.length == decoAnchors.length
                                                ? new int[] {decoBoxes[j * 4], decoBoxes[j * 4 + 1], decoBoxes[j * 4 + 2], decoBoxes[j * 4 + 3],
                                                  BlockPos.getX(decoWitness[j]), BlockPos.getY(decoWitness[j]), BlockPos.getZ(decoWitness[j])}
                                                : new int[] {decoBoxes[j * 4], decoBoxes[j * 4 + 1], decoBoxes[j * 4 + 2], decoBoxes[j * 4 + 3]});
                }
                grounds.decorations.put(id, boxes);
            }
            final long[] stockAnchors = entry.getLongArray("stockAnchors");
            final int[] stockLevels = entry.getIntArray("stockLevels");
            if (stockAnchors.length > 0 && stockAnchors.length == stockLevels.length)
            {
                final Map<Long, Integer> stock = new HashMap<>();
                for (int j = 0; j < stockAnchors.length; j++)
                {
                    stock.put(stockAnchors[j], stockLevels[j]);
                }
                grounds.warehouseStock.put(id, stock);
            }
            final long[] seededAnchors = entry.getLongArray("seededAnchors");
            final int[] seededMasks = entry.getIntArray("seededMasks");
            if (seededAnchors.length > 0 && seededAnchors.length == seededMasks.length)
            {
                final Map<Long, Integer> seeded = new HashMap<>();
                for (int j = 0; j < seededAnchors.length; j++)
                {
                    seeded.put(seededAnchors[j], seededMasks[j]);
                }
                grounds.warehouseSeeded.put(id, seeded);
            }
            final long[] down = entry.getLongArray("tornDown");
            if (down.length > 0)
            {
                final Set<Long> set = new HashSet<>();
                for (final long pos : down)
                {
                    set.add(pos);
                }
                grounds.tornDown.put(id, set);
            }
            final long[] woken = entry.getLongArray("crushersWoken");
            if (woken.length > 0)
            {
                final Set<Long> set = new HashSet<>();
                for (final long pos : woken)
                {
                    set.add(pos);
                }
                grounds.crushersWoken.put(id, set);
            }
            final long[] owed = entry.getLongArray("owedRepairs");
            if (owed.length > 0)
            {
                final Set<Long> set = new LinkedHashSet<>();
                for (final long pos : owed)
                {
                    set.add(pos);
                }
                grounds.owedRepairs.put(id, set);
            }
            final ListTag remembered = entry.getList("sentinel", Tag.TAG_COMPOUND);
            if (!remembered.isEmpty())
            {
                final Map<Long, SentinelMemory> saved = new HashMap<>();
                for (int j = 0; j < remembered.size(); j++)
                {
                    final CompoundTag memory = remembered.getCompound(j);

                    final ResourceLocation block = ResourceLocation.tryParse(memory.getString("block"));
                    if (block == null || !BuiltInRegistries.BLOCK.containsKey(block))
                    {
                        continue;
                    }
                    saved.put(memory.getLong("anchor"), new SentinelMemory(memory.getString("block"), memory.getInt("level"), memory.getInt("facing"),
                      memory.contains("rotation", Tag.TAG_STRING) ? memory.getString("rotation") : null,
                      memory.contains("pack", Tag.TAG_STRING) ? memory.getString("pack") : null,
                      memory.contains("path", Tag.TAG_STRING) ? memory.getString("path") : null,
                      memory.contains("offset", Tag.TAG_INT) ? memory.getInt("offset") : null,
                      memory.contains("front", Tag.TAG_INT) ? memory.getInt("front") : null,
                      memory.getBoolean("taken")));
                }
                if (!saved.isEmpty())
                {
                    grounds.sentinelMemory.put(id, saved);
                }
            }
            final long[] raised = entry.getLongArray("causeways");
            if (raised.length > 0)
            {
                final Set<Long> set = new HashSet<>();
                for (final long column : raised)
                {
                    set.add(column);
                }
                grounds.causeways.put(id, set);
            }
            final ListTag ledger = entry.getList("roads", Tag.TAG_COMPOUND);
            if (!ledger.isEmpty())
            {
                final Map<Long, long[]> saved = new HashMap<>();
                for (int j = 0; j < ledger.size(); j++)
                {
                    saved.put(ledger.getCompound(j).getLong("anchor"), ledger.getCompound(j).getLongArray("blocks"));
                }
                grounds.roads.put(id, saved);
            }
            final long[] groundChunks = entry.getLongArray("groundChunks");
            final int[] groundHeights = entry.getIntArray("groundHeights");
            if (groundChunks.length > 0 && groundHeights.length == groundChunks.length * 128)
            {
                final Map<Long, short[]> ground = new HashMap<>();
                for (int j = 0; j < groundChunks.length; j++)
                {
                    final short[] heights = new short[256];
                    for (int c = 0; c < 128; c++)
                    {
                        final int pair = groundHeights[j * 128 + c];
                        heights[2 * c] = (short) (pair >> 16);
                        heights[2 * c + 1] = (short) pair;
                    }
                    ground.put(groundChunks[j], heights);
                }
                grounds.groundMemory.put(id, ground);
            }
            final ListTag lines = entry.getList("stockLines", Tag.TAG_COMPOUND);
            if (!lines.isEmpty())
            {
                final Map<Long, Map<String, Integer>> saved = new HashMap<>();
                for (int j = 0; j < lines.size(); j++)
                {
                    final ListTag items = lines.getCompound(j).getList("items", Tag.TAG_STRING);

                    final int[] stacks = lines.getCompound(j).getIntArray("stacks");
                    final Map<String, Integer> building = new HashMap<>();
                    for (int k = 0; k < items.size(); k++)
                    {
                        building.put(items.getString(k), k < stacks.length ? stacks[k] : 0);
                    }
                    saved.put(lines.getCompound(j).getLong("anchor"), building);
                }
                grounds.stockLines.put(id, saved);
            }
        }
        return grounds;
    }
}
