# Colony Autopilot — player guide

They all live under `/colonyautopilot`. The read-only commands and the item hand-outs work
for any survival player; the ones that change the village for everyone (marked **op**) need
operator permission on a server — in single-player with cheats on, that is you. A style-pack guide
follows the commands.

| Command | What it does |
|---|---|
| `/colonyautopilot status` | One glance at what every colony is doing |
| `/colonyautopilot report` | The full colony debrief — population, plan, orders, guards, research, war, problems |
| `/colonyautopilot problems` | What needs a human — the recent warnings, in-game |
| `/colonyautopilot problems off` / `on` | **op** — Stop / resume keeping the problem ledger (server-wide) |
| `/colonyautopilot off` / `on` | **op** — The whole autopilot: stand down / resume (for this world) |
| `/colonyautopilot expansion off` / `on` | **op** — Stop / resume placing NEW buildings (the server default; a colony with its own `growth.expansion` keeps it) |
| `/colonyautopilot golems off` / `on` | **op** — No more iron golems (the standing ones leave) / bring them back (the server default; a colony with its own `tweaks.villageGolem` keeps its golems) |
| `/colonyautopilot settings [section]` | Every config key with its value, range and default, one line each; `settings <section.key>` shows one key with what it does; a key some colony holds its own value for (set on its page) is tagged with that colony and value (a count past three colonies) |
| `/colonyautopilot set <section.key> <value>` | **op** — Change one config key live; it is written to the file at once. It is the server default: a colony that set the key on its page keeps its own value, and the reply names such colonies with the `colony <id> reset <key>` that hands one back |
| `/colonyautopilot menu` | The autopilot page for the colony you stand in (or the nearest one) — every switch and number for THAT colony (also: town hall → Settings → **Autopilot settings**). Looking needs access to the colony's huts; changing, its owner or officer rank |
| `/colonyautopilot colony [<id>] roads` / `roads lift` | **op** — Lay every finished building's road of one colony again (two a tick, the boot sweep's own pass), for ground you reshaped by hand or a building you took down, in passes until they settle; `lift` takes every road up cleanly and forgets it, and no road is laid — not even a finished hut's — until `roads` or the next boot. `roads` refuses while the autopilot or the colony's `growth.buildPaths` is off; `roads lift` works either way (e.g. to take the roads up before removing the mod). The log names each door, and warns once of any road a citizen cannot walk end to end. Without op, the colony's owner and officers have the same re-lay as **Re-lay roads** under **Trade** on the town hall's **Exchange** tab: refused, with the reason in its tooltip, while the autopilot or the colony's roads are off or a `roads lift` holds (only `roads` lifts that), and within a game-minute of the colony's last re-lay; `roads` itself has no such wait |
| `/colonyautopilot colony [<id>] settings [section]` / `set <key> <value>` / `reset <key>` | **op** — One colony's own settings from chat. Without an id: the colony the player stands in (the console and command blocks give the id); colony ids count per dimension, so give an id from inside that colony's dimension |
| `/colonyautopilot treasury [<id>]` | One colony's money: its ColonyBucks balance in whole bucks, the requests, researches and cures waiting for funds (while something is charged), what it saves for (the research it sets money aside for, else the costliest build or request waiting for funds, with its price and its price with the diamonds, gold and netherite in it stocked), today's and yesterday's ledger (earned by channel, spent by kind), whether it runs in Progression Mode, and where to deposit. Without an id: the colony you stand in. Anyone with access to the colony's huts may look |
| `/colonyautopilot colony [<id>] treasury add <n>` | **op** — Put n ColonyBucks in one colony's treasury, for testing or relief after a loss |
| `/colonyautopilot exchange` | Trade diamonds, gold, ancient debris and netherite for ColonyBucks on the ordinary trading screen, in the colony you stand in (also: **Trade** on the town hall's **Exchange** tab). Anyone with access to the colony's huts may trade, while the colony is in Progression Mode |
| `/colonyautopilot zone wand` / `list` / `clear` | A Zone Marker / this colony's protected zones / **op** — wipe them |
| `/colonyautopilot guide` | Another copy of the field guide |
| `/colonyautopilot chat off` / `on` | Mute / restore colony chatter — for you only (the alert horn, the one-time Progression Mode notice and the rednight countdown still come through) |

## `/colonyautopilot status`

Prints a short report per colony:

- **citizens** — current / beds, plus how many the colony's research allows
- **buildings** — how many stand built and how many are under way
- **work orders** — the construction queue's length
- **researching** — how many researches the university is running
- **growth** — how far through the growth plan the village is, what the next building is,
  and why it is waiting if it is (population gate, research lock, plot parked, missing
  blueprint, or the autopilot / the colony's growth switch being off)

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
the warehouse warns the first time, then only in the debug log.

`problems off` stops keeping the ledger and clears it; `problems on` starts it again. The
toggle is server-wide and is written to the server config on the spot. Either way the server
log still records everything — the ledger is just the in-game window.

## `/colonyautopilot report`

The longer read-only debrief, roughly seven lines per colony: population against beds and
the research cap, the growth line, open work orders split into new builds and upgrades,
the guard mix against the doctrine, research in progress, and the war posture (grace days
left before the rednight when Fungal Infection: Spore is installed). After the colonies,
once for the whole server: how many problems are on record and the latest one (or that the
ledger is off). Open to any player, like `status`.

## `/colonyautopilot off` and `on`

The master switch, **for this world**. `off` stands every subsystem down at once — no
more free materials, no new placements, no auto-research, no golems, no upkeep: the colony
reverts to MineColonies' own rules. What the tweaks changed goes back too: the gamerules
(fire spread, vanilla patrols, the spawn-chunk radius) return to the values this world had
before the mod first changed them — unless you changed one by hand since, then yours stays —
and MineColonies' work-in-rain setting is turned back off if the mod turned it on. Made for
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
run it again near it. `golems on` brings them back on the keeper's next pass. Same switch as
`villageGolem` in the config, though an edit of the config file alone only stops the forging.
A colony switched off on its own page (or by an operator's `colony <id> set` or `reset` of
`tweaks.villageGolem`) stops forging and sends its own golems in loaded chunks away at once,
the same way.

## `/colonyautopilot settings` and `set`

`settings` lists the config sections; `settings growth` (or any section) lists every key in
it on one short line each — value, range and default — and `settings growth.terraform`
shows one key with what it does. The same information as `config/colonyautopilot-server.toml`,
without opening the file; the town hall page shows one colony's switches and numbers with
their descriptions, which is the easier read (lists and text keys live in the file only). There
are 75 on/off switches and 41 numbers in there; only a handful have their own command.

**Server default versus a colony's own value.** The town hall page (and `colony set`) give ONE
colony its own value of a key, and a colony's own value outranks the server default. `set`
changes the default only, so a colony switched off on its page stays off; the reply names the
colonies that keep their own value, and `settings` tags such keys `[own value in <colony> (#id: value)]`.
To hand a colony back to the default: the Reset button on its page row, or `/colonyautopilot colony <id> reset <key>`.
On the page, setting a value equal to the server default is the same as Reset; `colony set` pins the
value even then, which is how you exempt a colony before moving the default.

`set <section.key> <value>` (**op**) changes one of them live and writes it to the file at
once: `set growth.terraform false`, `set tweaks.villageGolem false`, `set growth.placementCooldownMinutes 20`.
Values are typed like the key's default and range-checked; lists stay file-only. The reply
says when the change lands: most keys on the system's next pass; the monster spawn cap,
potion durations and the world tweaks (rain work, vanilla patrols, fire tick, spawn chunks)
at once, and a tweak switched off gives the world its own value back. Keys that have their
own command (`master.enabled` — this world's switch — `problems.enabled`,
`tweaks.villageGolem`) go through it, so `set tweaks.villageGolem false`
dismisses the standing golems exactly like `golems off`. `settings growth.terraform` shows one key.

## `/colonyautopilot expansion off` and `on`

A gentler brake — the **server default**; a colony that set its own `growth.expansion` on the
town hall's Autopilot page keeps that, and the reply names such colonies. `off` stops the village *sprawling* — no new
production buildings, homes or decorations — while everything else continues: standing
buildings keep upgrading, repairs still happen, workplaces stay outfitted, and guard towers
still rise where citizens work unguarded (defense is never switched off). `on` resumes the
growth plan. Persists in the server config.

## `/colonyautopilot chat off` and `on`

The earplugs, **for you only**. A running colony talks a lot — MineColonies announces raids,
research, deaths and citizen news, and the autopilot adds its village milestones on top.
`chat off` mutes it in your own chat: both MineColonies' announcements and the autopilot's
news. Three lines still come through, on purpose: the alert horn when your village is attacked
or infected, the one-time Progression Mode notice, and the rednight countdown (the war's
warning, not village news). Nothing else changes — the villages keep doing everything, other
players keep hearing everything, and the log keeps recording everything. `chat on` brings the
chatter back. The choice is saved per player (in your client config) and survives relogs.

## ColonyBucks and Progression Mode

Every colony runs in **Progression Mode** unless its owner or officers switch it off. The
village still plans, places, builds its roads and shapes its ground for free. What the
autopilot used to conjure out of nothing (requested items, the builders' materials, guard
gear and the gunners' rifles, the warehouse's potions and scrolls, gear upgrades, and
research) and the cures of its sick and infected are paid for from the colony's treasury in
**ColonyBucks**. Food deliveries are never charged (the pantry and the restaurant menus
included); a research's cost items are charged whatever they are.
A switch you turned off on the page spawns nothing and costs nothing, in Progression Mode or
not. Colonies that stood before this version are in the mode too, and their owner and officers
are told once in chat, the first time one of them is online. A colony founded in Progression
Mode is not told; one founded with the mode off is told once the mode is switched on, as are
colonies that predate it.

- **Prices.** One buck buys 128 items. An iron, diamond or netherite tool, weapon or armour
  piece, or a gunner's rifle, counts as 16; wood, stone and gold tools, leather, chain and
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
  the calamities, not the elites), villagers and colony citizens never drop one. Dungeon and structure chests hold 6–15, mineshaft chests 2–5, village chests none;
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
  and crafters can still fill it. A cure waits too: the citizen stays sick or infected until the
  treasury can pay, and an infected one keeps taking the poison's damage. The town hall's
  **Exchange** tab shows what waits (requests, research and cures), and the colony's owner and
  officers who are online get one reminder a game-day while the treasury is empty. When the
  university's next research costs more than the treasury holds, the colony saves for it: its whole price is set aside, deliveries and the builders spend only what is
  above it, and the research is bought at once when the savings cover it (the **Exchange** tab
  and `/colonyautopilot treasury` show how far it has come).
- **The ledger.** The town hall's **Exchange** tab (the ColonyBucks seal under Settings, beside
  the treasury in whole bucks) and `/colonyautopilot treasury` show today's and
  yesterday's ledger: what came in (kills banked, raid pay, deposits, operator relief) and
  what went out (construction, research, guard gear, gunners' rounds, tools, repairs, other
  supply, cures), and what players sold at the exchange.
- **One job per builder.** Each built Builder's Hut carries one job at a time, so a poor
  colony never turns into a field of empty construction sites. If you write your own growth
  plan, list the Builder's Hut early: until one is built, nothing else is placed.
- **Turning it off.** The colony's owner and officers switch **Progression Mode** off on the
  town hall's autopilot page (the *ColonyBucks* section), unless the server locks it on
  (`economy.progressionModeLocked`). The balance keeps either way. Prices, drop rates, chest
  amounts and the exchange's offers are server settings (`/colonyautopilot settings economy`
  lists them). Anyone with access to a colony's huts reads its treasury with
  `/colonyautopilot treasury [<id>]`; operators top it up with
  `colony [<id>] treasury add <n>`.
- **Hands off another colony's huts.** MineColonies_Tweaks' and MineColonies_Compatibility's
  hut pages (maximum stock, item and id lists, farm field sizes, the teach and inventory
  screens, and Tweaks' "Request Cost?" order at the university) need the colony's right to
  manage its huts, as MineColonies' own pages do: a Friend or a stranger can no longer void a
  colony's warehouse with a maximum stock of 0, set its lists or order its research. In both
  modes.

---

# Choosing a structure pack

You pick the style pack once, when placing the Town Hall — the whole village is built from
it. Every pack MineColonies ships was checked against the autopilot's growth plan
(MineColonies 1.1.1319 through 1.1.1368 ship the same packs; the names below are the ones the
style picker shows). What differs:

1. **Hut coverage** — the autopilot builds a hut from any blueprint in the pack that stands on
   that hut's block. A pack with no blueprint for a hut doesn't break anything (the plan skips
   that building and moves on), but that profession never exists in your village. The
   Graveyard matters only with graves on (`tweaks.noGraves` off).
2. **Ornaments** — once the plan completes, the builders raise up to four small pieces
   (`growth.decorationsTarget`; 13 by 13 blocks or smaller) from the pack's `decorations/`
   folder around the town hall. MineColonies' founding supply camps are never among them, and
   a pack whose decorations are all larger raises none.
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

For the fullest village pick a pack with no missing huts and a good number of small
ornaments: Pagoda, Minecolonies Original (the pack this addon is field-tested with), Ancient
Athens, Caledonia or Cavern.

The last column is the set of plantation field structures the autopilot can order (the jar's
1.1.1368 blueprints); "Original's" is Minecolonies Original's nine: sugar cane, cactus, bamboo,
kelp, glow berries, cocoa + vines, sea grass + sea pickles, the two nether fungi, the two nether
vines.

| Pack | Missing huts | Small ornaments | Field structures it adds |
|---|---|---|---|
| **Pagoda** | — | 60 | Original's (it ships none) |
| **Minecolonies Original** | — | 37 | its nine (above) |
| **Ancient Athens** | — | 27 (of 88 decorations; 528 blueprints, second only to Cavern's 719) | 8: sugar cane, cactus, bamboo, kelp, glow berries, cocoa + vines, sea grass + sea pickles, the two nether vines (its nether field tags no working spot for the two fungi) |
| **Caledonia** | — | 20 | Original's (it ships none) |
| **Cavern** | — | 15 | 7 corner fields: sugar cane, cactus, bamboo, cocoa, vines, crimson fungus + weeping vines, warped fungus + twisting vines |
| **Incan** | — | 12 | Original's (it ships none) |
| **Medieval Spruce** | — | 7 | Original's (it ships none) |
| **Urban Birch** | — | 6 | Original's (it ships none) |
| **Shire** | — (its Nether Mine is the `mountdoom` blueprint) | 5 | 8: sugar cane, cactus, bamboo, kelp, cocoa + vines, sea grass + sea pickles, the two nether fungi, the two nether vines |
| **Urban Savanna** | — | 5 | 7: sugar cane, cactus, bamboo, kelp, cocoa + vines, sea grass + sea pickles, the two nether fungi (its glow berry and weeping-vines fields tag no working spot, and its twisting-vines field carries a tag MineColonies does not read: the three are left out) |
| **Space Wars** | — | 3 | 9: sugar cane, cactus, bamboo, cocoa, vines, kelp, sea grass + sea pickles, the two nether fungi, the two nether vines (its glow berry field tags no working spot, and is left out) |
| **Nordic Spruce** | — | 2 | 11: sugar cane, cactus, bamboo, cocoa, vines, kelp, sea grass, sea pickles, crimson fungus, warped fungus, weeping vines (its glow berry field tags no working spot, and its warped-vines field carries a tag MineColonies does not read: both are left out) |
| **Colonial** | — | 1 | 8: sugar cane, cactus, bamboo, glow berries, kelp + sea grass + sea pickles, cocoa + vines, crimson + warped fungi + twisting vines, weeping vines |
| **Desert Oasis** | — | none (its only small pieces are founding camps) | 7: sugar cane, cactus, bamboo, cocoa + vines, glow berries, kelp + sea grass + sea pickles, the four nether plants |
| **Fortress** | — | none (its decorations are all larger) | 9: sugar cane, bamboo, cactus + bamboo, glow berries, kelp, cocoa + vines, sea grass + sea pickles, the two nether fungi, the two nether vines |
| **Jungle Treehouse** | — | none | 8: sugar cane, cactus, bamboo, glow berries, cocoa + vines, kelp + sea grass + sea pickles, crimson + warped fungi + twisting vines, weeping vines |
| **Dark Oak Treehouse** | — | none | Original's (it ships none) |
| **Warped Netherlands** | — | none | Original's (it ships none) |
| **Medieval Oak** | Alchemist | 19 | Original's (it ships none) |
| **Lost Mesa City** | Kitchen | 8 | Original's (it ships none) |
| **Medieval Birch** | Alchemist | 7 | Original's (it ships none) |
| **Medieval Dark Oak** | Alchemist | 6 | Original's (it ships none) |
| **Stalactite Caves** | Alchemist, Mystical Site, Kitchen, Nether Mine, Graveyard | none | Original's (it ships none) |

---

# Playing alongside Create and TaCZ

**Create can supply the colony — today, no setup beyond pointing at it.** MineColonies racks
accept standard item automation, so a Create funnel, chute or belt aimed at a rack inserts
straight into colony storage and the couriers distribute it from there. One rule: feed a
**dedicated rack**, not the warehouse's master block — every insert into the warehouse
itself walks the entire combined inventory, which adds up fast at high tick rates. Keep
moving contraptions clear of colony buildings: neither mod protects against the other, so a
contraption can physically carry a building's blocks away (the autopilot repairs the damage,
but only a few times before it writes the building off as deliberately changed).

**TaCZ guns.** MineColonies' own knights and archers cannot use guns, but with the
Compatibility addon for MineColonies the colony's ranged guards are gunners: the autopilot
issues each one a rifle and keeps him in rounds (in Progression Mode the treasury pays for
both, the rifle weighted as gear). Switch it off per colony with `guns.gunnerGuards` (Guns →
Gunner guards on the town hall's autopilot page) and the ranged seats go back to marksmen and
archers. Your own guns and ammo stay yours: they are crafted only at TaCZ's Gun Smith Table,
which colony crafters cannot be taught. Your bullets respect colony rules — without the
colony's "hurt citizens" permission they simply don't damage citizens.
