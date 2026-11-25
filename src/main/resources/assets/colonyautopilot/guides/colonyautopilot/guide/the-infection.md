---
navigation:
  title: The Spore Infection
  position: 40
---

# The Spore Infection

This chapter applies where **Fungal Infection: Spore** and **Spore Inquisition**
are installed. Spore is the engine of the plague: the creep, the mounds and the
infected. Spore Inquisition is the apocalypse on top: the RedNight, the
corruption clock, the ordeals and the finale. Colony Autopilot adds the quiet
years before it all begins. The numbers are Spore Inquisition 5's; a "(4.5: ...)"
note marks the few places where 4.5 differs enough to change what you do.

## How creep actually spreads

Creep blocks are **inert**. Leave a patch of infested stone alone for a week and
it will not grow by a single block. Every blighted block was stamped there by a
living **spreader** pulsing a sphere of conversion around itself:

* **Mound**: the core spreader. It grows through four stages, and with Spore
  Inquisition its creep ring widens from **radius 10 to radius 25** (stock
  Spore: 5 to 20). From stage 3 it also breeds **tendrils**. Every pulse
  converts the terrain (stone to infested stone, grass to mycelium and so on)
  and plants fungal growth.
* **Tendril**: a small planter that carries the infection forward: it turns
  corpses and chests of food into fresh mounds, and monster spawners into
  Infested Spawners. No weapon can hurt it; the
  [bestiary](bestiary.md#the-spreaders) lists what removes it.
* **Gastgeber**: a walker that infects the ground under its feet as it roams.
* **Scamper**: a walking seed. A few minutes after it appears (about three and
  a half with Spore Inquisition; stock Spore: ten), it bursts where it stands
  into two or three new mounds. Kill it before then, wherever it is: a Scamper
  you kill seeds nothing.
* **Proto and Hive Tumor** (the bosses) pulse a ring twice a grown mound's
  (radius 50 with Spore Inquisition), but only every five minutes.
* **The RedNight** (below) spreads like an ordinary mound where it sits, on the
  world spawn.

**The rule that follows:** no spreader nearby means no spread, ever. Kill the
mound and its tendrils and that front is dead. Distance protects your
*terrain* completely: a far-off colony with no spreader in its loaded ground
never rots. (It does NOT protect *you*; see the corruption clock below.) One
caveat: the boss-tier creatures **force-load their own chunks**, so a front
anchored by a live Proto or calamity keeps crawling even with nobody online.

## The infected grow

An infected that kills **evolves**. An Infected Human that has killed once
turns, about two and a half minutes later, into a Knight, a Griefer, a
Braiomil or one of nine other kinds. An Infected Villager becomes a
Slasher, a Leaper or a Spitter, an Infected Pillager a Howler, and one change
in ten makes a Scamper instead (above). Every infected you leave alive near the
town is an evolved one in waiting.

* **One kill and 150 seconds** is all it takes with Spore Inquisition's
  settings (stock Spore: 5 minutes).
* **Frost stops the clock**: while an infected has Frostbite on it, it does
  not get any closer to its change.
* A **Reaper** hands the infected around it the kill they need, so near one
  they evolve without killing anything ([bestiary](bestiary.md#the-hypers)).
* An evolved one that racks up **700 kills** goes one step further, into a
  hyper (stock Spore: 7 kills). Killing almost never gets it there, but from
  20% corruption some mounds send out Infected Humans that already carry the
  points: such a one can be a Knight two and a half minutes later and an
  Inquisitor about eight minutes after that.

## The mycelium debuff

Nearly every infected attack, cloud and block applies **Mycelium** (the game
calls it *Mycelium Infection*). At level I it first drains your hunger, then
chips half a heart every 4 seconds. At level II and up it hurts straight away,
every 2 seconds at II. And **anything that dies while infected converts into an
infected mob**. Players included: die with the debuff on and an infected copy
of you gets up.

* **Prevent it:** wear a **gas mask**, total immunity ([Survival Kit](survival-kit.md)).
* **Cure it:** eat a **Milky Sack** or drink plain **milk**. Only the living can
  be cured: clear it *before* the fight kills you.
* **Your colony:** MineColonies **citizens never convert**; a citizen killed
  while infected just dies. The autopilot keeps the infection's damage off
  citizens and visitors (it only slows them) and cures them of the other spore
  effects: see [People](autopilot-features.md#people) in Autopilot Systems.
* The Mutant-Spore-Apocalypse pack's Spore config also protects the **iron
  golems** from the infection's tick (Spore's setting, not the autopilot's); how
  often a golem that dies infected rises again is under *Iron golems* in
  [Fighting Back](fighting-back.md).
* If **K-Turrets** is installed, its turrets cannot be hurt, the infection
  included: see [Fighting Back](fighting-back.md).

## The RedNight and the corruption clock

To see where your world stands,
<CommandLink command="/colonyautopilot report" close={true}>/colonyautopilot report</CommandLink>
gives each colony a **war:** line: the days of quiet left, and once the war is
on, the corruption out of 400 and how far it is to the next escalation.

### The quiet years

A new world starts at peace, so the colony can grow before the plague begins.
For **22 game-days** by default, no Spore creature appears by itself: no
natural spawns, no mounds, no raids, nothing from spawners, structures or
Spore Inquisition's scripts. A game-day is 20 minutes of the world running and
sleeping does not shorten it, so 22 days is about seven hours.

* Every 5 days the chat counts it down: "Days before rednight - 10 Days".
* The quiet holds creatures, never blocks. Your own spawn egg or typed
  `/summon` still works, and a mound you summon yourself still paints creep.
* The first time somebody is online after the last day (never on Peaceful),
  the **RedNight** falls, once per world.
* The length is Colony Autopilot's own setting, `spores.gracePeriodDays`,
  counted from the world's first tick: add the addon to a world older than that
  and there is no quiet. Keep it at **1 or more**: that is what brings the
  RedNight. 0 ends the quiet at once, and the addon then brings no RedNight.
* Set it when you create the world. Only the RedNight the addon brings ends the
  quiet: one that fell before the addon was added, or one summoned by hand,
  does not, and a second one falls when the quiet runs out.
* Want the war sooner? An operator runs
  `/colonyautopilot set spores.gracePeriodDays 1`: in a world a day old or more
  the RedNight falls within half a minute, in a younger one when it turns a day
  old.
* With Spore alone (no Spore Inquisition) there is no RedNight and no
  countdown: its creatures simply start on that day.

### The RedNight

When the quiet ends, a colossal "primordial mound" drifts down onto the
**world spawn** with eight **apostles**: buffed Infected Adventurers armed with
bows, scythes, spears and sabers. They land there and never despawn, and the
world gains a **corruption** meter (below).

* **The apostles** give everything uninfected within 30 blocks of them
  Mycelium II, refreshed every 20 seconds. Go near the world spawn only in a
  gas mask.
* **The mound** settles on the ground and spreads like any mound: a ring of
  creep 10 blocks out, tendrils, and about ten buffed **Scampers** an hour,
  each bursting into new mounds a few minutes later unless you kill it first.
  Every 25 minutes it clears a nine-block space around itself. Once an hour it
  is hoisted up to height 2,000 and drifts down again.
* Once a minute it whispers a line of lore to the player nearest the world
  spawn, if he is within 40 blocks (the RedNight the addon brings may stay
  silent until the world is next loaded).
* It will not lift a finger against you while you keep your hands off it. Hit
  it, though, and **every single blow raises another
  [Challenge Watcher](bestiary.md#the-challenge-watcher)** beside it. Do not go
  poking it until you can handle an arena's worth of them.

### The corruption clock

Corruption runs from 0 to 100%, counted in **400 points** (one point is a
quarter of a percent). The higher it climbs, the worse everything gets.

* **It ticks up slowly**: one step every 30 minutes for every Proto and for
  the RedNight in loaded ground, and only while somebody is online, so an
  empty server rots no further. The step is `corruptionRate` in
  `spore-inquisition-server.toml`: one point by default. **The
  Mutant-Spore-Apocalypse pack ships 4**, a full 1% every half hour for each
  Proto and for the RedNight. Every Proto's **MIDNIGHT** adds a flat **5
  points (1.25%)** on top, even where the ordeal waves are switched off, and
  every living Hive Tumor adds a step each hour while somebody is online.
* **Beyond that, only you move it.** Your own [offerings](offerings.md) raise
  it, and only the [cleansing nukes](cleansing-nukes.md) push it back: the SZC
  ice nuke does the heavy lifting (7.5% a use), the Frozen Kiss takes 1.5% and
  SOLAR PUNCH 0.5%.
* **Corruption reaches every player directly, wherever he is.** Once an hour
  the chat tells everyone online how the world feels, and the hour's effects
  land with it. From about 6% everyone catches **Uneasy** for five minutes
  (you cannot sleep while it lasts), and from 20% **Marker** for five minutes
  too (the infected can see you through walls). From 40% everyone catches
  Mycelium for 10 seconds, and between 60% and 80% Madness V for five
  minutes. From 80% the Mycelium lasts two minutes and the Uneasy the whole
  hour, so nobody sleeps any more. A gas mask keeps the Mycelium off, but not
  the rest.
* **Corruption toughens the horde.** From 20% nearly every infected below the
  calamities carries extra health, from 40% Strength as well, from 60% Speed
  and from 80% Resistance, and at 80% the calamities turn **overcharged**: a
  quarter bigger, with Resistance II and Speed. Every point a nuke takes back
  is felt in every fight.
* **Past 20% the vanilla raiders** (pillagers and the other illagers, not
  MineColonies' own raiders), **the villagers and the iron golems, and past
  40% most vanilla creatures** (zombies, skeletons, creepers, endermen and
  dozens more) join the players' side: they no longer fight you or each other,
  so the village's golems leave a zombie alone and the zombie leaves you
  alone. The infected still fight everyone. (4.5: raiders only.)
* **Ordinary creatures hunt the infected**, at any corruption. Spore
  Inquisition 5 sets Spore's *Mobs that will target infected* to every vanilla
  creature: zombies, skeletons, spiders, wolves, golems and the rest go after
  infected nearby, though a monster that can reach you still picks you first,
  so the night's monsters near the town get drawn into the plague's fights.
  **Tamed wolves** do it too unless they are sitting. Creatures that cannot
  fight, villagers and farm animals among them, do not change. The
  Mutant-Spore-Apocalypse pack adds Mutationcraft's mutants to the list.
  (4.5: only golems, illagers, vexes and piglins.)

### Protos and the ordeals

A Proto is the hive's brain, and every one runs a campaign (fight notes in the
[bestiary](bestiary.md#proto)).

* **It turns the land into the Wastes**: for 200 blocks around it the biome
  becomes the Wastes. Once a minute, everything alive and uninfected standing
  in the Wastes catches Mycelium for a minute, plus Uneasy and Marker. Wear the
  gas mask there. (Your citizens are only slowed by it; see the colony note
  above.) The Wastes stay after the Proto is gone, until an SZC or a Frozen
  Kiss freezes the land over.
* **The four ordeals strike in order**: **DAWN** about 17 minutes after the
  Proto appears, then **NOON, DUSK and MIDNIGHT** about three hours apart.
  That is loaded time: the clock only runs while the Proto's ground is loaded.
  Each one is announced by a red message and a bell to whoever is nearest that
  Proto.
* **The wave lands about 5 minutes later on the player nearest the world
  spawn** (by coordinates, whatever dimension he is in; alone in a world that
  is you). It digs itself out of the ground around him and scatters into a
  ring; it does not march in from the horizon. On a server that can be
  somebody in a town at spawn, far from the explorer who set the ordeal off.
* **Corruption past 17.5%, 35%, 52.5% and 70% makes each wave meaner** (those
  thresholds escalate the waves; they are not the firing times). **DUSK always
  delivers a [Challenge Watcher](bestiary.md#the-challenge-watcher)** and a
  Reaper. From 17.5% it adds a Vanguard and a second Watcher, from 35% a Hive
  Tumor, from 52.5% a Specter and a third Watcher, and from 70% a second Hive
  Tumor. NOON brings a Watcher of its own from 70%. Read the Watcher's
  bestiary entry *before* one is standing in your town square.
* **Each Proto runs this campaign once** and then **dies**, about 53 minutes
  after MIDNIGHT (roughly ten hours of loaded time in all), taking its share of
  the corruption tick and its forced chunk-loading with it. The RedNight keeps
  paying its own. Fresh ordeals need fresh Protos.

### The ECLIPSE and the end

* **From about 69% corruption the RedNight judges the world.** After
  **12 hours** of the world running at that level it fires the **ECLIPSE**,
  the "second coming" raid, and fires it again every 12 hours while corruption
  stays that high. About three minutes before, the chat warns: "We will erase
  all that divides and distinguishes, as the dark sun rises...". It targets up
  to three players **anywhere in the world** and summons an overcharged
  GazenBreacher, Hindenburg and Sieger about 150 blocks from each of them,
  already locked onto his position. **Distance does not save you from this.**
  Pushing corruption back under 69% with the nukes stops the clock (it does
  not run back); otherwise end the run.
* **How the run ends is a setting.** Spore Inquisition's own default
  (`automaticFinalitas = true`) lets corruption climb to 100% and start the
  finale by itself. **The Mutant-Spore-Apocalypse pack ships it off**:
  corruption caps at 99% and the finale waits for your
  [offering](offerings.md). Read the warning at the top of that page first:
  **starting the finale kills every citizen of the town.** The autopilot never
  starts it for you.

## Who's who

The full enemy roster, from the spreaders through the mini-bosses and the nine
calamities to the Proto itself, with fight notes, lives in
[the bestiary](bestiary.md).
