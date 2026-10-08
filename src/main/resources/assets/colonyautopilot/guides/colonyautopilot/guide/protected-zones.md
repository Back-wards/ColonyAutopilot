---
navigation:
  title: Protected Zones
  position: 70
---

# Protected Zones

The autopilot is an eager builder. Protected zones are how you keep it off your
own ground — it plans, builds, terraforms, paths and lamps **around** them,
forever. Nothing is scrubbed inside one either: the creep guard leaves a zone
alone, so spore creep there is yours to clear.

## Marking a zone

1. Hold the **Zone Marker** wand (from the founder's satchel, or
   <CommandLink command="/colonyautopilot zone wand">/colonyautopilot zone wand</CommandLink>).
2. Right-click one corner block, then the opposite corner. The zone is the
   rectangle between them (up to 48 blocks an edge, up to 24 zones per colony).
3. While the wand is in hand, zones render so you can see what's protected.

Marking or removing a zone needs the right to manage huts (Officer rank by
default). Sneak-right-click inside a zone with the wand to remove it.

Existing autopilot street-lamps inside a freshly marked zone are cleaned up on the
spot.

## Rules worth knowing

* **Occupied ground refuses.** If your rectangle overlaps a colony building, a
  placed-but-unbuilt hut, a field (a farm's or a plantation's), or ground the
  colony has already committed to its next building (a pad being levelled), the
  wand refuses and names the occupant — move a corner and mark again.
* **Zones are not retroactive.** A building that existed before you marked can't be
  pulled out by the zone; enforcement blocks NEW work only.
* Manage with <CommandLink command="/colonyautopilot zone list">/colonyautopilot zone list</CommandLink>;
  `/colonyautopilot zone clear` (**op**) removes every zone of the colony you stand in at
  once.
