---
navigation:
  title: Fighting Back
  position: 30
---

# Fighting Back

Your colony defends itself: it raises guards, golems and riders, scrubs the creep
off its ground and patches its own walls. This chapter covers what it does on its
own, then what you bring to the fight. The Spore parts apply where Fungal
Infection: Spore (and Spore Inquisition) are installed.

## What the colony fields on its own

* **Guards** — knights, huscarls, archers, marksmen and druids hold the Barracks
  and up to 20 standalone guard towers. They attack the infected on sight, as they
  do raiders and night monsters. How the posts are filled is under *The militia*
  below. In Progression Mode their kills pay into the treasury
  ([ColonyBucks](colonybucks.md)).
* **Iron golems** — two patrol the Town Hall and one guards every blacksmith; a
  fallen golem is reforged a game day after it falls. Don't want them? Switch
  **Village golem** off on the town hall's Autopilot page (or an operator runs
  `/colonyautopilot golems off` for every colony that has not set its own): the
  forging stops and the standing ones leave. With Spore, a golem that dies
  infected can rise as one of the infection's constructs: Spore's own config
  decides how often, 70% by its
  default and 10% in the Mutant-Spore-Apocalypse pack (the autopilot does not
  change it). So never `/kill` a golem near the creep.
* **The creep guard** (with Spore) — the colony's ground is scrubbed of creep down
  to 48 blocks below the surface. Infested earth is cured (podzol, coarse and
  rooted dirt the creep took come back as plain dirt), the ground the creep ate is
  filled back up to where the patrol last found it clean (no crater, no flooded
  pit), and the four spreaders (mound, Gastgeber, tendril, scamper) are killed on
  sight inside the colony. Bosses are spared, and so is the RedNight's own
  primordial mound. When the creep keeps pressing one stretch of the border, the
  spreaders in a narrow band just outside it are cut too, so the siege dies at its
  source. A [protected zone](protected-zones.md) is never scrubbed, though
  spreaders inside it are still killed. Switch: **Creep guard** (Spores) on the
  Autopilot page.
* **K-Turrets** (if installed) — every turret and drone is made **invulnerable**
  (it comes down only through its own menu; `spores.turretsInvulnerable`), and a
  kill by a turret you own earns ColonyBucks like your own. With Spore installed
  too, the turrets are taught to target the whole spore horde, the licker and the
  calamities' tails and arms included.
* **Citizen care** (with Spore) — the infection cannot kill a citizen or visitor,
  only slow them, and the colony's nurse cures the spore's other effects: see
  *People* in [Autopilot Systems](autopilot-features.md).

### The militia

The guard towers upgrade in step with the Town Hall, and the autopilot decides who
stands in them.

* **The mix.** Three melee guards stand for every ranged one
  (`growth.knightsPerArcher`), with one **druid** for every four fighters on top.
  The druid heals and strengthens the guards and throws Slowness and Weakness at
  the enemy; the colony keeps him stocked with the reagent that improves his
  potions.
* **Melee.** Knights, and once the University researches the **Huscarl** (an axe
  that cuts through armour, but no shield) one huscarl for every two knights,
  while the knights hold the shield wall.
* **Ranged.** Archers until the University researches the **Marksman** (a crossbow
  that pierces armour), marksmen after. The autopilot researches both ahead of
  everything else. Once the Marksman is researched, the Barracks' archers are moved
  to the new posts at the next daily rebalance (never during a raid); a Guard
  Tower's archer serves until he falls, and the tower is then posted afresh.
* **Schools.** The Archery's pupils graduate into the ranged posts, the Combat
  Academy's into the knight and huscarl posts.
* **Who seats the guards.** Every guard post of a Barracks Tower the autopilot
  staffs reads **Locked (no kids)** in the hire window, and so does the one post a
  Guard Tower is given; its other posts read **Manual**. Locked (no kids) is
  MineColonies' only label for a locked post, and here it means the autopilot is
  holding it: it seats every guard itself, by MineColonies' own hiring rules (a
  pupil of the post's school first, then a jobless citizen), and once a game day
  it rebalances the whole mix in one go.
* **Taking a tower over.** To post a guard of your own choosing, set one post of
  that Barracks Tower to manual (or automatic) hiring. The autopilot then leaves
  that whole tower to you, and locks its other posts so nothing takes the slot.
  Set your post back to default and the autopilot takes the tower back. With the
  Town Hall's **Auto Worker Hiring** switched Off, the autopilot seats and
  rebalances no guard at all: the garrison is yours.
* **Before you remove the mod**, run `/colonyautopilot off` (**op**) once, or
  switch the colony's growth off (`growth.enabled`): either hands every held post
  back to MineColonies' own hiring. Otherwise those posts stay shut.

### Cavalry

* **When.** One **Stable** is built at 11 citizens, just before the Library, once
  the University has researched Mounted Combat (the autopilot researches it for you;
  until then the colony builds on past it). Once it is the plan's next building new
  guard towers wait for it (while it waits behind an earlier step they take turns with
  the plan), and it is placed even while everyone has a job (its
  Stablemaster is hired when someone is free). With no plot in your claim it can go at
  the claim's edge, on levelled ground while the colony's **Terraform** switch is on, or
  near land a new Builder's Hut is raised to reach when a citizen is free (that land is
  kept for it); until one of these is possible it is tried again every 40 game-minutes.
  The Tavern, Warehouse, Blacksmith's Hut, Dining Hall, University, Library and Hospital
  are built by the same rules, and none of the eight, the Stable included, holds back
  your first guard post. Only the eleven
  styles (of 23) that have a Stable the colony can build get one: they are listed
  under *Pick your style* in [Getting Started](getting-started.md). A village of
  another style builds no Stable itself, but Mounted Combat is still researched,
  so you can place a Stable from one of those styles yourself: the colony then
  builds and staffs it and keeps its mounts and riders as it would its own. If
  you updated from an older version of this mod, your growth plan may have no
  Stable step: see *Updating from an older version* in
  [Getting Started](getting-started.md).
* **Mounts.** The autopilot supplies trained mounts, two per Stable level, and
  replaces one that dies at the next daily round. The Stablemaster is hired from
  the citizens without a job, like any worker: while everyone works, the Stable
  waits for one and its mounts stand unready. He is handed leather horse armour,
  which readies a mount at once (leather you put in the Stable's chest is used
  first; in Progression Mode the armour is paid for).
* **Riders.** Up to one rider a Stable level, each seated once a mount is ready
  for him and a citizen is free for the post, and armed with his spear. The
  Stable is upgraded with the Town Hall, as the guard towers are, so it gains one
  rider seat and two mounts for each level it climbs. A mount left riderless
  outside the Stable is put back in a stall once a game day. The riders patrol
  the whole town: each ride goes to one of the town's buildings, they stand there
  for a minute, ride home, and ride out again two minutes after leaving it. The
  two minutes are the Stable's own patrol interval, which you can change in its
  window. Set the Stable's task to Guard there and they stay home. **Cavalry
  patrols** (Growth, on the Autopilot page) switches all of this off: the riders
  then follow MineColonies' own rule and keep to their yard.
* **Barding.** The mounts wear the armour of the colony's barding research:
  leather after Barding, iron after Improved Barding, diamond after Advanced
  Barding. It shows on the horse and protects it on top of the research. In
  Progression Mode the treasury buys each piece (a diamond one costs one
  ColonyBuck); a mount it cannot pay for waits. A mount that dies drops none of
  it, and armour you put on a mount yourself is left alone. **Barding armour**
  (Growth, on the Autopilot page) takes it off again.
* **Yours to change.** **Starter animals** (Growth, on the Autopilot page) switches
  the mounts and the return to the stall off. Riders stand outside the militia's
  mix, and their post is never locked: set it to manual in the hire window and it
  is yours.

### Walls that hold

A stone wall round the village holds against the whole spore horde except the
**Proto**: inside a colony, only the Proto breaks blocks. Every other spore
creature keeps its full damage but loses its demolition, so a calamity's shells
and bombs hurt whatever they hit and break nothing, in the village and out in the
wild alike.

* The mound, the Gastgeber and the Hive Tumor still spread creep over the ground
  everywhere. Out in the wild they may still break blocks; inside a colony they
  cannot.
* This holds wherever the creep guard is on: in a colony its own **Creep guard**
  switch, in the wild the server's. `spores.demolitionRights` lists who may break
  blocks, and it ships with the Proto alone.
* Other monsters follow the game's mob-griefing rule, which the mod leaves alone
  by default. The Mutant-Spore-Apocalypse pack switches mob griefing off
  (`tweaks.disableMobGriefing`), so there creepers and ghasts leave your walls
  alone too.

### Repairs

* **Holes are patched without a builder.** When the creep, an explosion or a fire
  knocks blocks out of a built hut, the missing blocks are put back straight from
  its blueprint. Anything else standing in their place is left as it is: your
  chest, a block you changed, a furnace at work with what is in it, and stocked
  racks. A building under construction is left to its builders, and so are the
  towers of a Barracks being built.
* **Some buildings are left as they stand.** A building that stands mostly in
  other blocks than its blueprint's is not re-laid until a builder has built or
  repaired it, and its infected blocks are only cleaned away, never swapped for
  the blueprint's. Three such buildings: the Town Hall of an abandoned colony you
  took over, where its design differs from its style's blueprint; a hall you
  pasted from another style; and a building you stopped half-way through an
  upgrade by cancelling its order.
* **A destroyed hut rises again** (with Spore, while the creep guard is on), even
  after a restart and after the creep guard has cleaned the ground around it (a
  building of the kind above gets its hut block back and is left as it stands).
  Your own Spore lamps and lab blocks beside it are left alone. A building you
  deconstruct stays down, and so does a hut you break or pick up yourself.
* **The ground under it is filled.** Where the creep is at work, the hollow it ate
  under a standing building is filled with earth, down to solid ground at most 24
  blocks below (never under a mine), and the roads it ate are laid again. The fill
  stops at your own work: any spot where the hollow ends on a chest, a torch, a
  ladder or a rail is left. A bare cellar under a hut, in ground the creep has
  reached, gets filled, so dig cellars beside huts, not under them (a protected
  zone cannot be marked over a hut).
* **Switch and cost.** `repair.autoRepairDamage` (Repair, on the Autopilot page)
  switches repairs off; with it off, a building the creep reached stays holed.
  Ordinary blocks are always repaired free. In Progression Mode (while the
  exchange is on) a repair or a rebuild pays for the exchange's goods it pastes
  (gold, diamond and netherite blocks and their kin) and leaves out what the
  treasury cannot pay for, and it restores walls, not contents: a missing chest
  comes back empty, an item frame without its item.

## What you bring

* **A gas mask on your head and milk in your bag** — total infection immunity and
  the cure; the [Survival Kit](survival-kit.md) covers both.
* **Wire and traps** (with MineTraps) — barbed and razor wire, the wire fence,
  spikes, bear and nail traps, mines, pitfalls, quicksand and the rest are a wall
  to your citizens and to nobody else. Citizens walk around them and cannot be
  pushed or knocked into them, guards still see and shoot through them, and
  raiders, monsters, animals and you are hurt as before. Leave a gap, a door or a
  fence gate in every wire line: a ring of wire with no gap keeps your own
  villagers in, and one who must cross waits at it until MineColonies moves him.
  Chest bombs and explosive blocks are not part of the wall: keep them off the
  colony's ground. Three things the wall does not cover: a citizen already inside
  wire when you place it is cut until he walks out; one standing in the middle of
  a patch of wire cannot get out and dies there; and a mounted rider's horse walks
  into wire and carries him in. A campfire is not a trap block: routes go round
  one, but nothing stops a citizen who is pushed or cuts a corner from stepping on
  it and being burnt.
* **Kill the spreaders** — every mound you kill and tendril you clear outside the
  colony is ground the colony never has to fight on. **Kill mounds with frost**: a
  mature mound alerts the Proto as it dies, unless frost was the last thing to hurt
  it ([Bestiary](bestiary.md)).
* **An Eye of the Vigil in the base chest** — dropped by Vigils, and the one sure
  way to remove a **Challenge Watcher** squatting in town: right-click it away.
  Weapons and `/kill` do nothing, and starving it out rarely works in a town that
  keeps feeding it ([Bestiary](bestiary.md)).
* **Break the watch-towers** — Spore's fungal ruins hide an **Outpost Watcher**,
  an eye that keeps calling the infected down on everyone near it. Your guards
  can't fight it, but you can: break it with an axe, and it drops an Eye of the
  Vigil. The [Bestiary](bestiary.md) has the rest.
* **Cold** — snowballs, [cryo grenades](cleansing-nukes.md), Spore's Cryogenic
  Aspect enchantment and the **CDU** machine; the whole horde is freeze-weak
  ([Survival Kit](survival-kit.md)).
* **Guns** — TaCZ firearms, which take this mod's gun enchantments (see
  *Guns and their enchantments* below), and Spore's own flesh guns
  ([Survival Kit](survival-kit.md)).
* **Magic** — with Ars Nouveau installed (the Mutant-Spore-Apocalypse pack has
  it): spells, wards and summons.
* **Potions and scrolls from the warehouse** — once the colony has an Alchemist
  Tower, its warehouse keeps combat potions for you to take, and once it has an
  Enchanter's Tower, teleport scrolls and scrolls that call your guards to you:
  see *Logistics* in [Autopilot Systems](autopilot-features.md).
* **The counter-weapons** — the [Cleansing Nukes](cleansing-nukes.md), the only
  way to push corruption back.

## Guns and their enchantments (with TaCZ)

With TaCZ installed, guns take enchantments: this mod's doing. Put a gun in the
enchanting table like a sword, or add a book at the anvil. Attachments and loaded
ammunition stay on the gun, and re-fitting attachments later keeps the
enchantments.

* **High Caliber I–V** — +12% bullet damage per level.
* **Fungicide I–V** — +25% per level, against spore creatures only. Cannot share a
  gun with High Caliber. Exists only while Fungal Infection: Spore is installed.
* **Marksman I–III** — +25% headshot damage per level.
* **Shrapnel I–III** — every bullet that hits also wounds the nearest hostile mobs
  within about 2.5 blocks of the target for 10% of that bullet's damage: two of
  them per level, six at level III. Whatever else the gun's rounds do (the Cryo
  freeze, the Incendiary fire) lands on them too. The swarm answer. It never harms
  players, citizens, golems, pets or a player's own summons (Ars Nouveau's
  summoned skeletons and ally vexes, and Born in Chaos' controlled creatures where
  installed; one you shoot directly is still hit), but it does splash neutral
  monsters such as endermen and piglins standing in the blast, and they will turn
  on you.
* **Cryo Rounds I–II** — a hit freezes the target where it stands for 3 seconds
  (5 at level II), renewed by every round that lands; with Shrapnel the
  bystanders freeze too. Bosses (the Proto and the other organoids, the
  calamities, the hyper mini-bosses, the Wither, the dragon, the Warden) and other
  players are only slowed, and a flyer keeps flying. Your own citizens are never
  frozen or set alight by a stray round. Cannot share a gun with Incendiary.
* **Incendiary I–II** — hits set the target on fire, 3 seconds per level.

The enchantments ride the bullet's own hit. Grenade and rocket rounds (the M320 and
the RPG-7) carry most of their damage in TaCZ's own blast, which is not enchanted:
on those only the impact hit changes, and a round that goes off against a block
touches nothing.

The switch is `guns.enchantments`, server-wide. Off, the enchanting table refuses
guns, and enchantments already on a gun do nothing.

## The one rule

Night mobs attacking citizens is **intended**: the colony is built to take losses
and replace them. A colony of 25 or more with its combat research done tends to
survive the RedNight; a colony of 20 tends to fall. Buy the colony time and it wins.
