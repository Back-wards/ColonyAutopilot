---
navigation:
  title: The Spore Infection
  position: 20
---

# The Spore Infection

A fungal plague in two layers: **Fungal Infection: Spore** is the engine — the
creep, the mounds, the infected — and **Spore Inquisition** is the apocalypse
on top: the RedNight, the corruption clock, the ordeals and the finale.
Everything below is taken from the mods' actual code, not folklore.

## How creep actually spreads

Creep blocks are **inert** — they never spread on their own, no matter how long
you stare at them. Every blighted block was stamped there by a living spreader
pulsing a sphere of block-conversion around itself:

* **Mound** — the core spreader. It ages through four stages, and in this pack
  its creep ring grows from **radius 15 to radius 30** as it matures (tuned
  larger than stock). At age 3+ it also breeds **tendrils**. Every pulse
  converts terrain (stone to infested stone, grass to mycelium…) and plants
  fungal growth.
* **Tendril** — a small, **invulnerable** planter that carries the infection
  forward and turns corpses, loot and spawners into fresh mounds. No weapon can
  hurt it. Anywhere, two things clear one out: the [CDU machine](survival-kit.md),
  and the Inquisition's **CCC / SCC chestplates** — wear one and every tendril
  within 16 blocks is removed. Inside the colony's claim the autopilot's creep
  guard removes them too (see [Fighting Back](fighting-back.md)).
* **GastGeber** — a walker that infects the ground under its feet as it roams.
* **Scamper** — erupts into a brand-new mound when it dies. Kill it away from
  your walls.
* **Proto & Hive Tumor** (the bosses) pulse a huge radius-40 ring, but slowly.

**The rule that follows:** no spreader nearby = no spread, ever. Kill the
mound and its tendrils and that front is dead. Distance protects your
*terrain* completely — a far-off colony with no spreader in its loaded chunks
never rots. (It does NOT protect *you* — see the RedNight below.) One caveat:
the boss-tier creatures **force-load their own chunks**, so an infection front
anchored by a live Proto or calamity keeps crawling even with nobody online.

## The mycelium debuff

Nearly every infected attack, cloud and block applies **Mycelium**: it drains
your hunger, then chips fixed damage on a timer — and **anything that dies
while infected converts into an infected mob**. Players included: die with the
debuff on and an infected copy of you gets up.

* **Prevent it:** wear the **gas mask** — total immunity ([survival kit](survival-kit.md)).
* **Cure it:** eat a **Milky Sack** or drink plain **milk** — but only the
  living can be cured; clear it *before* the fight kills you.
* Colony note: MineColonies **citizens never convert** (they just die), and
  this pack protects the iron golems (10% conversion chance instead of the
  stock 70%) and makes the K-Turrets fully immune.

## The RedNight and the corruption clock

After the grace period (~50 game-days, this addon's own setting —
`spores.gracePeriodDays`; it must be **1 or more** for the RedNight to come at
all, **0 switches it off**: no grace and no RedNight; every 5 days the chat
counts it down — "Days before rednight - 50 Days") the
**RedNight** falls once per world: a colossal age-10 "primordial mound"
descends with an apostle honor guard, and the world gains a **corruption**
meter (0–100%). The mound hangs far overhead at the world spawn, whispering a
line of lore at anyone who comes within 40 blocks — but it will not lift a
finger against you while you keep your hands off it. Hit it, though, and
**every single blow spawns another [Challenge Watcher](bestiary.md)** up there
beside it. Do not go poking it until you can handle an arena's worth of them.

* Corruption **ticks up slowly** — at this pack's rate, one point per
  30-minute cycle for every Proto and every RedNight-mound in a loaded chunk,
  and only while somebody is online: an empty server rots no further. Every
  MIDNIGHT of a Proto's clock adds a flat **+5 (1.25%)** on top, even with
  ordeals switched off. Nothing else raises it except your own
  [offerings](offerings.md), and the only things that push it back are the
  [cleansing nukes](cleansing-nukes.md): the SZC ice nuke does the heavy
  lifting (−19% per use), and SOLAR PUNCH trims a little more (~1%).
* Once a Proto exists, the four **ordeals** count down and strike in order —
  **DAWN, NOON, DUSK, MIDNIGHT** — each one announced by a red message and a
  bell, then the wave lands ~5 minutes later **on whoever is nearest that
  Proto**: it digs itself out of the ground around you and scatters into a
  ring, it does not march in from the horizon. Corruption past
  **17% / 35% / 52% / 70%** makes each wave meaner (those thresholds escalate
  the waves; they are not the firing times). Each Proto runs this campaign
  **once** — DAWN through MIDNIGHT — and then **dies**, about 53 minutes after
  MIDNIGHT, taking its share of the corruption tick and its forced
  chunk-loading with it (the RedNight mound keeps paying its own). Fresh
  ordeals need fresh Protos. Know that **DUSK always delivers a
  [Challenge Watcher](bestiary.md)**, an unkillable wave-spawner with its own
  rules — read its bestiary entry *before* it's standing in your town square.
* At high corruption (~69%+) the RedNight begins judging the world, and
  eventually fires the **ECLIPSE** — the "second coming" raid. It targets up
  to three players **anywhere in the world** and teleport-summons three
  overcharged calamity bosses ~150 blocks from each of them, already locked
  onto your position. **Distance does not save you from this.** Kill the
  sources or end the run.
* Corruption **caps at 99%** in this pack — the finale never auto-triggers;
  it waits for your [offering](offerings.md).

## Who's who

The full enemy roster — spreaders, mini-bosses, the nine calamities and the
Proto itself, with fight notes — lives in [the bestiary](bestiary.md).
