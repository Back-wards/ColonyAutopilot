---
navigation:
  title: Protected Zones
  position: 25
---

# Protected Zones

The autopilot is an eager builder. Protected zones are how you keep it off your
own ground: without one, a plot the colony levels is cleared of whatever stands on
it, your own blocks included (see *Growth* in
[Autopilot Systems](autopilot-features.md)). Say you raise a tower and a wheat
patch on the colony's land: mark a zone over them, and the village builds around
that ground, forever.

A zone runs from bedrock to sky. Inside it the autopilot never places a building
or an ornament, lays a field, carves a road, raises a lamp, levels ground or fells
a tree. MineColonies' own Forester is another matter: he fells trees within 50
blocks of where he works, further when he runs short, zone or not. Keep trees you
want well away from him, or mark out his woods with the Forest Tool from his hut.
The colony's upkeep leaves a zone alone too: your lava moat is not capped, your
berry hedge is not cleared, and, with Spore installed, the creep guard scrubs
nothing there, so spore creep inside a zone is yours to clear (spreaders standing
in it are still killed). When a citizen dies nearby, the clean-up that fills the
pit or caps the lava stops at the zone's edge as well.

## Marking a zone

You mark zones with the **Zone Marker** wand. Every player finds one in the
founder's satchel on first joining (while the server has protected zones on),
<CommandLink command="/colonyautopilot zone wand" close={true}>/colonyautopilot zone wand</CommandLink>
hands out another, and you can craft one: a Build Tool ringed by eight sticks.

<Column fullWidth={true} alignItems="center">
<Recipe id="colonyautopilot:zone_marker" fallbackText="Zone Marker: eight sticks round a Build Tool." />
</Column>

1. Hold the wand and right-click one corner block, then the opposite corner. Both
   must be inside your colony's claimed area, and the second click must come
   within two minutes of the first. The zone is the rectangle between them. A
   held button counts as one click; clicking the same block twice, a second
   apart, makes a one-block zone.
2. An edge longer than 48 blocks is cut back to 48 (the chat tells you). A colony
   holds up to 24 zones; after that, new marks are refused until you remove one.
3. While you hold the wand in either hand inside your colony, you see within about
   64 blocks: your zones outlined in **gold**, the ground the colony already holds
   in **red** (the wand refuses a zone over it), and your first corner as a
   **cyan** post. In single-player the zones also show as green boxes.

To remove a zone, sneak-right-click any block inside it with the wand. Where zones
overlap, every zone over that spot goes.

Marking or removing a zone needs the right to manage huts (Officer rank by
default), and everyone online is told when a zone is marked or removed, except
players who have muted the village news with
<CommandLink command="/colonyautopilot chat off" close={true}>/colonyautopilot chat off</CommandLink>.

A colony whose zones are switched off on its Autopilot page (`zones.enabled`)
shows none: its marks are kept but not enforced.

## Rules worth knowing

* **Occupied ground refuses.** If your rectangle overlaps a colony building, a
  hut placed but not built yet, a field (a farm's or a plantation's), or ground
  the colony has already committed to its next building (a plot being levelled),
  the wand refuses and names what is there. Move a corner and mark again. A zone
  can never cover a hut: zone the ground beside it instead.
* **Old work inside a new zone.** The autopilot's lamp posts inside come down at
  once, and levelling already under way stops at the zone's edge. A road the
  village laid there stays until the village next lays that road again (each time
  the world loads, for one): the new road goes round the zone, and the old one
  inside it is taken up, except any block of it you have changed since.
* **How far the village reaches.** The colony looks for building plots within 80
  blocks of its Town Hall (**Search radius blocks**, under Growth on the Autopilot
  page); when the land there runs out, it looks further, up to twice as far. It
  keeps 3 empty blocks between buildings. Build your own base well away from the
  village, or zone its ground before the village gets there.
* See your zones with
  <CommandLink command="/colonyautopilot zone list" close={true}>/colonyautopilot zone list</CommandLink>
  (the colony you stand in). `/colonyautopilot zone clear` (**op**) removes every
  zone of the colony you stand in at once.
