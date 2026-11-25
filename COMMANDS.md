# Colony Autopilot — player guide

They all live under `/colonyautopilot`. The read-only commands and the item hand-outs work
for any survival player; the ones that change the village for everyone (marked **op**) need
operator permission on a server. In single-player the host of the world may run them too, with
cheats on or off (also once the world is opened to LAN; a LAN guest still needs operator). A
style-pack guide follows the commands.

| Command | What it does |
|---|---|
| `/colonyautopilot status [<id>]` | One glance at what every colony is doing, or the one colony whose number you give (each colony's header shows it) |
| `/colonyautopilot report [<id>]` | The full colony debrief — population, plan, orders, guards, research, war, problems; with a number, one colony |
| `/colonyautopilot problems` | What needs a human — the recent warnings, in-game |
| `/colonyautopilot problems off` / `on` | **op** — Stop / resume keeping the problem ledger (server-wide) |
| `/colonyautopilot off` / `on` | **op** — The whole autopilot: stand down / resume (for this world) |
| `/colonyautopilot expansion off` / `on` | **op** — Stop / resume placing NEW buildings (the server default; a colony with its own `growth.expansion` keeps it) |
| `/colonyautopilot golems off` / `on` | **op** — No more iron golems (the standing ones leave) / bring them back (the server default; a colony with its own `tweaks.villageGolem` keeps its golems) |
| `/colonyautopilot settings [section]` | Every config key with its value, range and default, one line each; `settings <section.key>` shows one key with what it does; a key some colony holds its own value for (set on its page) is tagged with that colony and value (a count past three colonies), and a key written for a mod the server does not have is tagged `[made for <mod id>, not installed]` |
| `/colonyautopilot set <section.key> <value>` | **op** — Change one config key live; it is written to the file at once (not the `meta` keys, the config's own bookkeeping). It is the server default: a colony that set the key on its page keeps its own value, and the reply names such colonies with the `colony <id> reset <key>` that hands one back |
| `/colonyautopilot menu` | The autopilot page for the colony you stand in (or the nearest one) — every switch and number for THAT colony (also: town hall → **Exchange** tab → **Autopilot settings**). Each row says **PER-COLONY** (this colony only) or **GLOBAL** (every colony). Looking needs access to the colony's huts; changing a PER-COLONY row, its owner or officer rank; changing a GLOBAL row, an operator or the single-player host |
| `/colonyautopilot colony [<id>] roads` / `roads lift` | **op** — Lay every finished building's road of one colony again (two a tick, the boot sweep's own pass), for ground you reshaped by hand or a building you took down, in passes until they settle; `lift` takes every road up cleanly and forgets it, and no road is laid — not even a finished hut's — until `roads` or the next boot; the lift is done in one go, so a large village feels one short hitch. `roads` refuses while the autopilot or the colony's `growth.buildPaths` is off; `roads lift` works either way (e.g. to take the roads up before removing the mod). The log names each door, and warns once of any road a citizen cannot walk end to end. Without op, the colony's owner and officers have the same re-lay as **Re-lay roads** under **Trade** on the town hall's **Exchange** tab: refused, with the reason in its tooltip, while the autopilot or the colony's roads are off or a `roads lift` holds (only `roads` lifts that), and within a game-minute of the colony's last re-lay; `roads` itself has no such wait |
| `/colonyautopilot colony [<id>] settings [section]` / `set <key> <value>` / `reset <key>` | **op** — One colony's own settings from chat. Without an id: the colony the player stands in (the console and command blocks give the id); colony ids count per dimension, so give an id from inside that colony's dimension |
| `/colonyautopilot treasury [<id>]` | One colony's money: its ColonyBucks balance in whole bucks, the requests, researches and cures waiting for funds (while something is charged), what it saves for (the research it sets money aside for, else the costliest build or request waiting for funds, with its price and its price with the diamonds, gold and netherite in it stocked), today's and yesterday's ledger (earned by channel, spent by kind), whether it runs in Progression Mode, and where to deposit. Without an id: the colony you stand in. Anyone with access to the colony's huts may look |
| `/colonyautopilot colony [<id>] treasury add <n>` | **op** — Put n ColonyBucks in one colony's treasury, for testing or relief after a loss |
| `/colonyautopilot exchange` | Trade diamonds, gold, ancient debris and netherite for ColonyBucks on the ordinary trading screen, in the colony you stand in (also: **Trade** on the town hall's **Exchange** tab). Anyone with access to the colony's huts may trade, while the colony is in Progression Mode and the autopilot and `economy.exchange` are on |
| `/colonyautopilot zone wand` / `list` / `clear` | A Zone Marker / this colony's protected zones / **op** — wipe them |
| `/colonyautopilot guide` | Another copy of the field guide (needs the GuideME mod) |
| `/colonyautopilot chat off` / `on` | Mute / restore colony chatter, the alert horn included — for you only (the one-time Progression Mode notice, the rednight countdown and the monster arrivals' countdown still come through, and so do the autopilot's direct answers to you) |

## `/colonyautopilot status`

Prints a short report per colony, or for the one colony whose number you give (`status 3`).
Each colony's header shows its number: `== Emberhold (#3) ==`. Colony numbers count per
dimension, so a header that names a dimension (`#1 in minecraft:the_nether`) belongs to a colony
elsewhere, and a number reaches the colony of that number in your own dimension first.

- **citizens** — current / beds, plus how many the colony's research allows (beds read 0 until
  the first home or the Tavern is built)
- **buildings** — how many stand built and how many are not built yet; when the colony's first
  builds are switched off (Build placed huts, or the Construction master) the count is followed
  by `(yours to build: switched off under autoupgrade)`
- **work orders** — the construction queue's length
- **researching** — how many researches the university is running
- **growth** — how far through the growth plan the village is, the step the plan is waiting
  at or would place next, and why, then the first step it passed over on the way with its
  reason, each of the eight important buildings (the Tavern, the Warehouse, the Blacksmith's Hut,
  the Dining Hall, the University, the Stable, the Library, the Hospital) with its own too whenever
  it is passed over, and how many more. Example:
  `growth: 14/70 steps — next: Sawmill (waiting for 7 citizens (has 6)); passed over: Residence (held: the standing homes deepen to level 2 first) and 2 more`.
  The reasons: not enough citizens, the Town Hall must reach a level first, university
  research still to come, no buildable plot yet (parked), waiting for a builder's hut to reach its
  land at a spot (parked: a new Builder's Hut is being raised toward land out of reach), an important
  building next up with `new guard posts and builders' huts wait for it` (once a guard post stands;
  while one waits behind the named step, which status does not say, new guard posts and builders'
  huts take turns with the named step), the Stable while the style pack is
  still being asked whether it has one (`looking for its blueprint in the style pack`, a passed-over
  step, a moment after a start or a change of style), homes held (the standing ones deepen
  first, or the Town Hall must rise before more homes do), nobody free to staff a workplace,
  the builders' jobs being full (Progression Mode), the structure pack not loaded, or the
  autopilot, the colony's growth or its expansion being switched off. In a style with no
  Stable the Stable step counts as waived (`N waived — no blueprint for them` once the plan is
  complete) from the moment the pack has answered. Status only reads that answer: the pack is
  asked when the plan reaches the Stable step or once a University stands

Use it whenever the village looks idle — it usually isn't, and this says what it's waiting on.

## `/colonyautopilot problems`

The autopilot fixes most trouble by itself — but when something defeats its remedies (a
worker still frozen after every cure, a field that is mostly dead ground, a fisher's hut with no
room for a pond and no water near it, a plantation with no ground for a field, a blueprint missing
the barrels its composter needs, a build site that stalls three orders running although its builder
was sent home each time), it gives up with a warning meant for human eyes. Those
warnings used to live only in the server log. This command shows the recent ones (up to 20,
oldest first, timestamped) right in chat, so you can see what actually needs you. A remedy that
has to repeat is listed once a session: a Courier's Hut whose couriers keep being recalled to
the warehouse warns the first time, then only in the debug log. A small dry hole in the village
where colonists got stuck twice is filled by the colony rather than reported (with **Terraform**
on; the README says which holes), so it does not show here unless other rescues nearby
brought a report before it was filled.

`problems off` stops keeping the ledger and clears it; `problems on` starts it again. The
toggle is server-wide and is written to the server config on the spot. Either way the server
log still records everything — the ledger is just the in-game window.

## `/colonyautopilot report`

The longer read-only debrief, roughly seven lines per colony: population against beds and
the research cap, the growth line, open work orders split into new builds and upgrades,
the guard mix against the doctrine, research in progress, and the war posture (grace days
left before the rednight when Fungal Infection: Spore is installed). After the colonies,
once for the whole server: how many problems are on record and the latest one (or that the
ledger is off). `report 3` prints colony 3 only, then that problems line; the header shows the
colony's number as in `status`. Open to any player, like `status`.

## `/colonyautopilot off` and `on`

The master switch, **for this world**. `off` stands every subsystem down at once — no
more free materials, no new placements, no auto-research, no golems, no upkeep: the colony
reverts to MineColonies' own rules, but for what stays below. What the tweaks changed goes back
too: the gamerules (fire spread, vanilla patrols, mob griefing, the spawn-chunk radius) return
to the values this world had before the mod first changed them — unless you changed one by hand
since, then yours stays; a spawn radius widened before 1.1.65 was never recorded, so
`/gamerule spawnChunkRadius 2` gives vanilla's back — and MineColonies' work-in-rain setting is
turned back off if the mod turned it on. Trap blocks (MineTraps' wire and the rest of
MineColonies' `dangerousblocks` tag) stay a wall to citizens, and their routes still go round
them. The mod's fixes to MineColonies stay too: guards still fight Spore's creatures and leave
Born in Chaos's player-owned ones alone, and a Stable's stalls stay inside it. The
world's own pacing stays: the Spore grace period (`spores.gracePeriodDays`) and, with Born in
Chaos or Mutationcraft, the arrival days and hook limits (`arrivals.*`) hold with the autopilot
off too; set a day or a limit to 0 to release it (with Spore Inquisition set the grace to 1,
not 0: 0 there means no RedNight at all). Made for
handing a built-up village back to normal survival play. `on` brings the autopilot back and
re-applies the tweaks. The choice is kept in this world's save and survives restarts. Your
other worlds keep their own switch; a world you never switched follows `master.enabled` in
`config/colonyautopilot-server.toml`, the default for every world.

## `/colonyautopilot golems off` and `on`

The iron guardians — the **server default**; a colony that set its own `tweaks.villageGolem` on the
town hall's Autopilot page keeps its golems, and the reply names such colonies. The village keeps two golems at the town hall
and one at every blacksmith, and reforges any that fall a game-day later. `golems off` stops the forging and
sends the standing ones away (they are dismissed, never killed — a golem that *dies* near
the creep can rise as a hostile construct); a golem out in unloaded chunks stays until you
run it again near it. `golems on` brings them back on the keeper's next pass (a post whose golem
fell within the last game-day waits that day out first, and at the town hall both posts do). Same switch as
`villageGolem` in the config, though an edit of the config file alone only stops the forging.
A colony switched off on its own page (or by an operator's `colony <id> set` or `reset` of
`tweaks.villageGolem`) stops forging and sends its own golems in loaded chunks away at once,
the same way.

## `/colonyautopilot settings` and `set`

`settings` lists the config sections; `settings growth` (or any section) lists every key in
it on one short line each — value, range and default — and `settings growth.terraform`
shows one key with what it does. The same information as `config/colonyautopilot-server.toml`,
without opening the file; the town hall page shows one colony's switches and numbers with
their descriptions, which is the easier read (the page shows switches and numbers only: the two
text keys, `research.capstones` and `research.forks`, are changed with `set`, lists in the
file). There are 80 on/off switches and 48 numbers in there; only a handful have their own
command.

**Server default versus a colony's own value.** The town hall page (and `colony set`) give ONE
colony its own value of a key, and a colony's own value outranks the server default. `set`
changes the default only, so a colony switched off on its page stays off; the reply names the
colonies that keep their own value, and `settings` tags such keys `[own value in <colony> (#id: value)]`.
To hand a colony back to the default: the Reset button on its page row, or `/colonyautopilot colony <id> reset <key>`.
On the page, setting a value equal to the server default is the same as Reset; `colony set` pins the
value even then, which is how you exempt a colony before moving the default. A row marked
**GLOBAL** on the page is the server's one value, not the colony's: an operator or the
single-player host who changes it there does exactly what `set` does, chat reply included.

`set <section.key> <value>` (**op**) changes one of them live and writes it to the file at
once: `set growth.terraform false`, `set tweaks.villageGolem false`, `set growth.placementCooldownMinutes 20`.
Values are typed like the key's default and range-checked; lists stay file-only, and the
`meta` keys (the config's own bookkeeping, such as its version) are refused. The reply
says when the change lands: most keys on the system's next pass; the monster spawn cap,
potion durations and the world tweaks (rain work, vanilla patrols, fire tick, mob griefing, spawn chunks)
at once, and a tweak switched off gives the world its own value back. A key written for a mod
that is not installed is still saved (the file holds every key) and the reply names the mod:
`(saved; made for <mod id>, not installed)`. Keys that have their
own command (`master.enabled` — this world's switch — `problems.enabled`,
`tweaks.villageGolem`, `growth.expansion`) go through it, so `set tweaks.villageGolem false`
dismisses the standing golems exactly like `golems off`, and `set growth.expansion false`
answers as `expansion off` does. `settings growth.terraform` shows one key.

## `/colonyautopilot expansion off` and `on`

A gentler brake — the **server default**; a colony that set its own `growth.expansion` on the
town hall's Autopilot page keeps that, and the reply names such colonies. `off` stops the colony placing buildings — no new
production buildings, homes, decorations or guard posts (no Guard Tower or Barracks, not even where buildings stand
unguarded: place those yourself) — while everything else continues: standing
buildings keep upgrading (unless upgrades are switched off, below), repairs still happen, workplaces stay outfitted, and the
guard posts that stand stay staffed. `on` resumes the
growth plan. Persists in the server config.

Building and upgrading have their own switches, per colony, in the page's **Construction**
section: `autoupgrade.buildPlacedHuts` (the first build of every placed hut) and
`autoupgrade.upgradeBuildings` (every level after it), both under `autoupgrade.enabled`.
`set autoupgrade.upgradeBuildings false` leaves every upgrade to the players of every colony
that has no value of its own: huts are still placed and built to level 1, and the growth plan
waits at each of its Town Hall steps until the hall is raised by hand.
`colony <id> set autoupgrade.buildPlacedHuts false` has one colony's huts placed but not built:
each waits for its Build button (switch `growth.expansion` off as well to place them all
yourself, the guard posts included). `autoupgrade.enabled` off stops both halves and the watchdog that restarts stuck builds.
In Progression Mode an unbuilt hut then still counts as one of the builders' jobs, so the colony
soon stops placing; with only `buildPlacedHuts` off a hut counts once you have ordered it, and
the colony keeps placing. (Measured on the test server, the same four game days of a Lost Mesa
City colony in Progression Mode: one hut placed with the master off, eight with the master on
and both halves off.)

Two more keys of 1.4 sit in the **Growth** section. `growth.avoidStructures` (per colony, on):
the colony builds around what the world generated at the surface, a village, a ruin or a tower;
off, that ground is used like any other. `growth.structuresIgnored` (the server's list, in the
file) names structures the colony does not build around even with the switch on, by id or by
tag. It ships with `#minecraft:mineshaft`; add `minecraft:ruined_portal` and a road may run
over a ruined portal.

## `/colonyautopilot chat off` and `on`

The earplugs, **for you only**. A running colony talks a lot — MineColonies announces raids,
research, deaths and citizen news, and the autopilot adds its village milestones on top.
`chat off` mutes it in your own chat: both MineColonies' announcements and the autopilot's
news, the alert horn when your village is attacked or infected included. MineColonies' answers
to your own clicks and commands go with them (a colony founding refused as too close, "no
permission" at a hut, the `/mc` replies): if a click seems to do nothing, turn the chat back
on. Three warnings still come
through, on purpose: the one-time Progression Mode notice, the rednight countdown and the
monster arrivals' countdown (the war's warnings, not village news). So do the autopilot's
direct words to you: the satchel's pointer to the village at your first join, the Spore
grace notice at your first spore creature of your own, and its answers to your own
commands and clicks (the zone marker's, this mod's commands' replies). Nothing else changes — the villages keep doing everything, other
players keep hearing everything, and the log keeps recording everything. `chat on` brings the
chatter back. The choice is saved per player (in your client config) and survives relogs.

## ColonyBucks and Progression Mode

Every colony runs in **Progression Mode** unless its owner or officers switch it off. The
village still plans, places, builds its roads and shapes its ground for free. What the
autopilot used to conjure out of nothing (requested items, the builders' materials, guard
gear, the warehouse's potions and scrolls, gear upgrades, and research) and the cures of its sick and infected are paid for from the colony's treasury in
**ColonyBucks**. Food deliveries are never charged (the pantry and the restaurant menus
included); a research's cost items are charged whatever they are.
A switch you turned off on the page spawns nothing and costs nothing, in Progression Mode or
not. Colonies that stood before this version are in the mode too, and their owner and officers
are told once in chat, the first time one of them is online. A colony founded in Progression
Mode is not told; one founded with the mode off is told once the mode is switched on, as are
colonies that predate it.

- **Prices.** One buck buys 128 items. An iron, diamond or netherite tool, weapon or armour
  piece counts as 16; wood, stone and gold tools, leather, chain and
  gold armour, bows, crossbows, shields, potions and scrolls count 1 each. A research costs
  1 buck per tier (1–6) once it has started, plus the items it needs that the university
  does not already hold; the diamonds, gold and netherite it needs are first carried over from
  the warehouse when it holds them, and not bought. A cure costs its disease's cure items (2 or
  3 items with MineColonies' own diseases); a wipe of the spore's corrosion and kin costs 1
  credit (the infection itself is not cured: its damage tick only slows a villager).
- **Only the colony spends.** You turn the goods you earned into bucks at the exchange, never
  the other way. While Progression Mode is on the Postbox is locked: an order or a stock line
  there is refused in chat, and its old orders are cancelled. A stock line or a field seed you
  set yourself is not supplied either; the colony's own couriers and crafters may still fill
  it. With MineColonies_Tweaks, its "Request Cost?" button at the university is refused the
  same way: a research is the colony's to buy. The restaurants' and nether mines' menus are
  the colony's too: a dish you add or take off is refused in chat, and one listed before the
  mode came on comes off, so a menu keeps the autopilot's staples. A composter's, furnace user's
  or beekeeper's list you edit is served only with what the colony would pick itself (vanilla
  compostables and flowers that are not food, coal and charcoal for fuel): the rest of it waits
  for the colony's stock. Nor is a filled shulker box, chest, barrel or bundle, a spawner, a vault, a trial key,
  a spawn egg or a Mystical Agriculture seed ever supplied. With the mode off the Postbox and the menus work as before.
- **Paying in.** Put ColonyBucks by hand in any rack of the town hall (or of a built
  warehouse), feed the Town Hall block with a hopper or pipe, or sneak-right-click it
  holding them; anyone may pay in with the sneak-click. The bucks citizens pick up are
  collected from the warehouse racks and from their pockets too. They are collected within a
  few seconds while Progression Mode is on; with it off they wait where they are, but bucks
  sneak-clicked onto the hut block are still paid in, and a citizen who dies with bucks on
  him pays them in whatever the mode. The janitor never throws bucks away.
- **Earning.** A hostile mob drops one 1 time in 30 when a player, a colony citizen, or
  what one of them owns (an arrow, a tamed wolf, a K-Turret) kills it. A blow counts for 5
  seconds: a mob you or a guard hit that fire, a fall or lava then finishes still drops one.
  A death no one caused (`/kill`, a fall or lava with no blow before it, a gold farm's piglins
  that were only angry at you, another mod's cull) drops none.
  Looting adds half a percent per level. When one of the colony's citizens (a guard, say)
  makes the kill in Progression Mode, that buck goes straight into his colony's treasury
  instead of onto the ground; the rest of the mob's loot drops as usual. In Progression
  Mode, each raider killed (by anyone) in a MineColonies raid pays one more buck into the
  raided colony's treasury, up to the number of raiders the raid was sent with; the owner and officers
  online hear the total when the raid is over, or the first of them to come online. Bosses (150 health or more, or tagged as bosses:
  the calamities, not the elites), villagers and colony citizens never drop one. Dungeon and structure chests hold 1–4, mineshaft chests 0–2, village chests none;
  loot a datapack rolls as another entity (Spore Inquisition's offerings) holds none.
  The drops and the chests' bucks come even while the autopilot is off; the kill banking
  and raid pay need it on. The item cannot be crafted and survives fire and lava.
- **The exchange.** `/colonyautopilot exchange` (or **Trade** on the town hall's
  **Exchange** tab) opens the ordinary trading screen, its title the colony's treasury in whole
  bucks: 2 diamonds → 1 buck, 1 diamond block →
  5, 16 gold ingots → 1, 1 ancient debris → 1, 1 netherite ingot → 4. Emeralds and nether
  stars are not bought. It only sells bucks; bucks never buy anything back. It serves
  colonies in Progression Mode only. When the treasury's saving-for line names a price with
  goods stocked, the clerk
  says on opening what one is worth to it stocked where that line names and what it fetches here (a diamond: 2.25
  stocked, 0.50 here; for a form the exchange does not buy as it is, an ore say, it says so). The screen closes if
  you walk more than 8 blocks away. While the exchange is on, in Progression Mode the
  exchange's goods and their ore, raw, nugget, dust and block forms, and what the AutoColony Apocalypse modpack's
  machines turn into them (netherite scrap, ancient debris, gilded blackstone, ochrum, horse
  armour, a jukebox, Mystical Agriculture's essences), are priced over their exchange value (a gold block costs four times what nine ingots sell for
  by default, economy.conjureMarkup), so buying
  them from the providence and selling them back gains nothing at the default markup. A builder's need for them is
  taken from the warehouse first when it holds them, while the colony has a courier to carry
  them over, so stocking the warehouse pays in kind and only the rest is bought; with the mode or the
  exchange off they cost what they always did.
- **When the money runs out.** A request waits, and nothing is lost: MineColonies' own couriers
  and crafters can still fill it. A cure waits too: a sick citizen stays sick, and one carrying
  the spore's corrosion, bile or frostbite keeps it, until the treasury can pay. The infection
  itself only slows a villager, paid or not. The town hall's
  **Exchange** tab shows what waits (requests, research and cures), and the colony's owner and
  officers who are online get one reminder a game-day while the treasury is empty. When the
  university's next research costs more than the treasury holds, the colony saves for it: its whole price is set aside, deliveries and the builders spend only what is
  above it, and the research is bought at once when the savings cover it (the **Exchange** tab
  and `/colonyautopilot treasury` show how far it has come).
- **The ledger.** The town hall's **Exchange** tab (the ColonyBucks seal under Settings, beside
  the treasury in whole bucks) and `/colonyautopilot treasury` show today's and
  yesterday's ledger: what came in (kills banked, raid pay, deposits, operator relief) and
  what went out (construction, research, guard gear, tools, repairs, other supply, cures),
  and what players sold at the exchange. (1.4 dropped the gunners' rounds line, so a colony's
  ledger starts afresh once after the update.)
- **One job per builder.** Each built Builder's Hut carries one job at a time, so a poor
  colony never turns into a field of empty construction sites. The plan's opening Builder's
  Huts (three in the default plan) are exempt, so the village gets all its first crews without
  waiting. If you write your own growth plan, list the Builder's Hut early: until one is built,
  nothing else is placed, and the count of that first Builder's Hut step is how many go up
  without waiting.
- **Turning it off.** The colony's owner and officers switch **Progression Mode** off on the
  town hall's autopilot page (the *ColonyBucks* section), unless the server locks it on
  (`economy.progressionModeLocked`). The balance keeps either way. Prices, drop rates, chest
  amounts and the exchange's offers are server settings (`/colonyautopilot settings economy`
  lists them). Anyone with access to a colony's huts reads its treasury with
  `/colonyautopilot treasury [<id>]`; operators (and the single-player host) top it up with
  `colony [<id>] treasury add <n>`.
- **Hands off another colony's huts.** MineColonies_Tweaks' hut pages (maximum stock, item
  and id lists, farm field sizes, and its "Request Cost?" order at the university) need the
  colony's right to
  manage its huts, as MineColonies' own pages do: a Friend or a stranger can no longer void a
  colony's warehouse with a maximum stock of 0, set its lists or order its research. In both
  modes.

---

# Choosing a structure pack

You pick the style pack once, when placing the Town Hall — the whole village is built from
it. Every pack MineColonies ships was checked against the autopilot's growth plan (the 23
packs of MineColonies 1.1.1403, read from its jar; 1.1.1420 gave Desert Oasis' Stable its hut
block and re-saved Ancient Athens' and Minecolonies Original's buildings, adding or removing
none; the names below are the ones the style picker shows). What differs:

1. **Hut coverage** — the autopilot builds a hut from any blueprint in the pack that stands on
   that hut's block. A pack with no blueprint for a hut doesn't break anything (the plan skips
   that building and moves on), but that profession never exists in your village. The
   Graveyard matters only with graves on (`tweaks.noGraves` off).
2. **Ornaments** — once the plan completes, the builders raise up to four small pieces
   (`growth.decorationsTarget`; 13 by 13 blocks or smaller) from the pack's `decorations/`
   folder around the town hall. MineColonies' founding supply camps are never among them, nor
   is a piece that holds water open to one side (an aquarium made for a cave corner, an
   aqueduct segment, a terrace corner with a water channel): built on its own it would pour
   over the village. A pack whose decorations are all larger raises none.
3. **Plantation fields** — the autopilot picks every plantation's fields. In most styles the
   plantation's own hut carries crop plots (sugar cane, cactus and bamboo; Ancient Athens sugar
   cane only, Stalactite Caves none); for the rest it orders a field structure from the pack's
   `agriculture/fields/` for a builder to raise beside the plantation (on ground that is not
   level it grades a gentle plot first; a style that ships no field takes Minecolonies
   Original's, which has one for every plant). Food and cure foods come first — kelp (the
   measles cure), then glow berries, cocoa and sugar cane, then the rest — and only plants whose
   research is done; no plant grows on two plantations while a researched plant grows on none,
   so the default plan's second Plantation, raised at its end, grows what the first does not. A
   newly researched food plant takes the place of a lesser one (cactus, bamboo, the sea and
   nether plants), at most once a game day per plantation. While the autopilot runs the colony
   each plantation's fields tab is kept on manual assignment and the autopilot's choice wins
   over a field picked by hand; with the colony's `farmFields` off, or the autopilot off, the
   plantations are left as they stand, and the tab can switch one back to automatic.

4. **The Stable** — the default plan builds one Stable for the colony's cavalry (riders on trained
   mounts) at 11 citizens, just before the Library, once the University has researched Mounted
   Combat (until then the plan builds on past it). When it is the plan's next building, new guard towers and the
   colony's extra builders' crews wait for it, and while it waits behind an earlier step they take turns
   with the plan; it is placed even while everyone has a job. Where
   no plot fits in the claim it can go at the claim's edge (the colony claims the ground under it), on
   ground levelled or carved for it while Terraform is on, or, where good land lies beyond every
   builder's reach and a citizen is free, near a new Builder's Hut raised toward that land (the land
   is kept for it until it is placed, and it is searched again once that hut has a builder); until one
   of these is possible it is tried again every 40 game-minutes. The Tavern, the Warehouse, the
   Blacksmith's Hut, the Dining Hall, the University (which searches near the Town Hall first, and as
   far out as the others once that ground has refused it, or at once with Terraform off), the Library and the Hospital are built by
   the same rules; none of the eight holds back the village's first guard post. Eleven of the 23 packs have a Stable
   the colony can build on MineColonies 1.1.1420: the
   table's Stable column (Minecolonies Original, Ancient Athens, Urban Birch, Shire, Urban Savanna,
   Space Wars, Nordic Spruce, Colonial, Fortress, Jungle Treehouse and Desert Oasis; on 1.1.1403
   Desert Oasis' cannot be placed). In the others the plan skips it with one
   line in the log and the village builds no Stable itself; the autopilot still researches
   Mounted Combat there, so you can place a Stable of another style with the build tool, and the
   colony then builds, staffs, outfits and upgrades it like its own and (with `research.fullTree`
   on) takes up the mount researches too, each as its buildings allow (below); nothing else
   changes. It is kept out of "Missing huts" because a village without one loses no profession it
   needs. A Stable standing below the Town Hall's level is upgraded with the hall, as the guard
   towers are. What a Stable brings: two trained mounts for each of its levels, a Stablemaster who
   is handed leather horse armour to ready them (he is hired from the citizens without a job, as
   at every other workplace: in a colony where everyone works the Stable waits for one, its mounts
   unready, and the log says so after a game day) (one piece readies a mount; leather you put in
   the Stable's chest yourself is used first), and riders, seated one at a time as a mount is
   ready for each, up to one rider a level (MineColonies sizes the riders' post by the Stable's
   level). A mount that dies is replaced at the next daily round, and a mount standing riderless
   outside is put back in its stall then. The riders patrol the whole town (the colony's
   `growth.cavalryPatrols` switch, **Cavalry patrols** on the Autopilot page, on by default):
   each ride goes to a built building (its patrol point, or a corner of its blueprint's box at
   ground level), never the Stable itself, not the building of the ride before, and never
   further from the Stable than its patrol distance (120 blocks at level 1, 165 at level 2).
   The riders stand at the building for a game-minute, ride home, and ride out again when the
   Stable's patrol interval is over, counted from the moment they leave the building: so they
   stand by the Stable for the interval less the ride home. The interval is the Stable's own
   setting, in its window: the colony writes 2 minutes where it finds MineColonies' 6 and keeps
   any other value you set. The first two rides after a world is loaded are MineColonies' own
   picks, the first always the rider's own spot. The Stable's task setting stays MineColonies'
   (set the task to Guard and they stay home). With the switch off the riders are MineColonies'
   own: it sends cavalry only between Stables and Gate Houses, and the plan builds no Gate
   House, so they keep to their own yard. On a test copy of a village of 35 buildings with a
   level-2 Stable and one rider, watched for 21 game-minutes, the rider rode to the Courier's Hut
   100 blocks out and to the Barracks 124 blocks out, stood at each for about 75 game-seconds and
   came home between them; with the switch off he never went further than 31 blocks from the
   Stable. Where the way home crosses water he swims it, and that comes off his time at the
   Stable. A rider thrown off his mount three times within a
   game-minute at one spot (a gate too low for him) is sent back to the Stable, his mount to its
   stall. The mounts wear the armour of the colony's barding research (the colony's
   `growth.bardingArmour` switch, **Barding armour** on the Autopilot page, on by default):
   leather after Barding, iron after Improved Barding, diamond after Advanced Barding. MineColonies
   itself puts nothing on a mount, its barding is a share taken off every hit (15% a research);
   the piece shows on the horse and its armour points come on top. In Progression Mode the
   treasury buys each piece under guard gear (leather a credit, iron 16 credits, diamond one
   ColonyBuck) and a mount it cannot pay for waits; a mount that dies drops none of it; armour
   you put on a mount yourself is never touched; with the switch off the colony takes off what
   it fitted. The mount researches wait for their buildings: Barding, Improved Barding and Advanced
   Barding need the Stable and the Blacksmith at 3, 4 and 5, and the mounts' health researches
   start with Ivermectin, which needs a Stable and a Hospital of level 2. A standing Hospital and
   a standing Blacksmith are upgraded early, ahead of the guard towers, until each has the level
   the colony's research needs (the Hospital 2 for the mounts' health and 3 for Vaccines, the
   Blacksmith up to 5 for the mount armour and some technology researches) or reaches the Town
   Hall's level, so the Blacksmith is usually raised with the Town Hall, one upgrade a level,
   and the mount armour researches can begin as the Stable rises. The two top ones,
   Bucephalus and Master Barding, are researched only when listed in `research.capstones`.

For the fullest village pick a pack with no missing huts and a good number of small
ornaments: Pagoda, Minecolonies Original (the pack this addon is field-tested with), Ancient
Athens, Caledonia, Medieval Oak or Cavern. Of those six, Minecolonies Original and Ancient
Athens also have a Stable.

The last column is the set of plantation field structures the autopilot can order (the jar's
blueprints, the same in MineColonies 1.1.1368 through 1.1.1403); "Original's" is Minecolonies Original's nine: sugar cane, cactus, bamboo,
kelp, glow berries, cocoa + vines, sea grass + sea pickles, the two nether fungi, the two nether
vines.

| Pack | Missing huts | Stable | Small ornaments | Field structures it adds |
|---|---|---|---|---|
| **Pagoda** | — | — | 60 | Original's (it ships none) |
| **Minecolonies Original** | — | yes | 37 | its nine (above) |
| **Ancient Athens** | — | yes | 27 (of 88 decorations; 528 blueprints, second only to Cavern's 719) | 8: sugar cane, cactus, bamboo, kelp, glow berries, cocoa + vines, sea grass + sea pickles, the two nether vines (its nether field tags no working spot for the two fungi) |
| **Caledonia** | — | — | 20 | Original's (it ships none) |
| **Medieval Oak** | — | — | 19 | Original's (it ships none) |
| **Cavern** | — | — | 13 (its two corner aquaria are left out) | 7 corner fields: sugar cane, cactus, bamboo, cocoa, vines, crimson fungus + weeping vines, warped fungus + twisting vines |
| **Incan** | — | — | 9 (three corner platforms with a water channel are left out) | Original's (it ships none) |
| **Medieval Spruce** | — | — | 7 | Original's (it ships none) |
| **Medieval Birch** | — | — | 7 | Original's (it ships none) |
| **Urban Birch** | — | yes | 6 | Original's (it ships none) |
| **Medieval Dark Oak** | — | — | 6 | Original's (it ships none) |
| **Shire** | — (its Nether Mine is the `mountdoom` blueprint) | yes | 5 | 8: sugar cane, cactus, bamboo, kelp, cocoa + vines, sea grass + sea pickles, the two nether fungi, the two nether vines |
| **Urban Savanna** | — | yes | 5 | 7: sugar cane, cactus, bamboo, kelp, cocoa + vines, sea grass + sea pickles, the two nether fungi (its glow berry and weeping-vines fields tag no working spot, and its twisting-vines field carries a tag MineColonies does not read: the three are left out) |
| **Space Wars** | — | yes | 3 | 9: sugar cane, cactus, bamboo, cocoa, vines, kelp, sea grass + sea pickles, the two nether fungi, the two nether vines (its glow berry field tags no working spot, and is left out) |
| **Nordic Spruce** | — | yes | 2 | 11: sugar cane, cactus, bamboo, cocoa, vines, kelp, sea grass, sea pickles, crimson fungus, warped fungus, weeping vines (its glow berry field tags no working spot, and its warped-vines field carries a tag MineColonies does not read: both are left out) |
| **Colonial** | — | yes | 1 | 8: sugar cane, cactus, bamboo, glow berries, kelp + sea grass + sea pickles, cocoa + vines, crimson + warped fungi + twisting vines, weeping vines |
| **Desert Oasis** | — | yes on MineColonies 1.1.1420; no on 1.1.1403, whose level-1 Stable file has no hut block, so the colony cannot place it | none (its only small pieces are founding camps) | 7: sugar cane, cactus, bamboo, cocoa + vines, glow berries, kelp + sea grass + sea pickles, the four nether plants |
| **Fortress** | — | yes | none (its decorations are all larger) | 9: sugar cane, bamboo, cactus + bamboo, glow berries, kelp, cocoa + vines, sea grass + sea pickles, the two nether fungi, the two nether vines |
| **Jungle Treehouse** | — | yes | none | 8: sugar cane, cactus, bamboo, glow berries, cocoa + vines, kelp + sea grass + sea pickles, crimson + warped fungi + twisting vines, weeping vines |
| **Dark Oak Treehouse** | — | — | none | Original's (it ships none) |
| **Warped Netherlands** | — | — | none | Original's (it ships none) |
| **Lost Mesa City** | Kitchen | — | 4 (its four open aqueduct pieces are left out) | Original's (it ships none) |
| **Stalactite Caves** | Alchemist, Mystical Site, Kitchen, Nether Mine, Graveyard | — | none | Original's (it ships none) |

---

# Playing alongside MineTraps, Create and TaCZ

**MineTraps: a wall for citizens.** Barbed and razor wire, the wire fence, spikes, bear and nail
traps, mines, pitfall traps, ghost blocks, quicksand and toxin are a wall to your citizens and
to nobody else. Citizens plan their way around them and their bodies cannot be pushed, knocked
or squeezed into them; guards still see and shoot through them; players, raiders, monsters and
animals walk in and are hurt as before. So leave a gap, a door or a fence gate in a wire line.
Example: wire across the only road between the Farm and the Dining Hall leaves the farmer
waiting at it until MineColonies' own rescue moves him (about two minutes on the test server;
the colony now has that rescue run about half a minute after a walk finds no way at all, which
was measured in cells, pits and pens and not again with wire).
What the wall does not cover: a citizen already inside wire when it is placed, or teleported
into it, is cut until he walks out; one standing in the middle of a patch of wire (three by
three on the test server) cannot find a way out and dies there; and a cavalry horse is not a
citizen, so it walks into wire and carries its rider in.
The list is the block tag
`minecolonies:dangerousblocks`: a data pack that adds another mod's trap block to it makes that
block a wall to citizens too, from the next `/reload`. There is no switch for it. (Measured on
the test server, a Lost Mesa City world with MineTraps 3.0.2: 0 of 80 walks past a
wire corner were cut, against 8 of 80 without the wall; nobody was cut in three fights beside
wire, against 38 cuts and one death; never seen on a client.)

**Create can supply the colony — today, no setup beyond pointing at it.** MineColonies racks
accept standard item automation, so a Create funnel, chute or belt aimed at a rack inserts
straight into colony storage and the couriers distribute it from there. One rule: feed a
**dedicated rack**, not the warehouse's master block — every insert into the warehouse
itself walks the entire combined inventory, which adds up fast at high tick rates. Keep
moving contraptions clear of colony buildings: neither mod protects against the other, so a
contraption can physically carry a building's blocks away (the autopilot repairs the damage,
but only a few times before it writes the building off as deliberately changed).

**TaCZ guns.** MineColonies' own guards cannot use guns, and since 1.4 Colony Autopilot no
longer supports the gunner guards of the Compatibility addon for MineColonies: the colony's
ranged guards are archers, then marksmen once researched. Your guns take the mod's gun
enchantments (`guns.enchantments`). Your own guns and ammo stay yours: they are crafted only at TaCZ's Gun Smith Table,
which colony crafters cannot be taught. Your bullets respect colony rules — without the
colony's "hurt citizens" permission they simply don't damage citizens.
