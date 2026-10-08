# Colony Autopilot

*Made by **Backwards** - https://www.curseforge.com/members/backwards/projects. Licensed under GPL-3.0.*

Download the mod from its [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/colonyautopilot). This repository is the corresponding source of each CurseForge release, one tag per version.

A companion addon for [MineColonies](https://github.com/ldtteam/minecolonies) that turns a colony
into a **living village**: place the Town Hall, confirm founding, and walk away. The colony places
its own buildings, builds and upgrades them, gathers its own materials at a configurable pace,
repairs itself after raids, and tells you about its milestones in chat.

**Needs:** Minecraft 1.21.1, NeoForge 21.1 (any 21.1.x; 21.1.235 is the dev-tested build) and [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) 1.1.1319 or newer with its libraries. **Install:** drop the jar into `mods/` on the server and on every client, the same version everywhere. **Bugs and questions:** https://github.com/Back-wards/ColonyAutopilot/issues (not MineColonies' tracker). The full version table is under Requirements.

The player keeps the drama: quests, raid defense and the endgame. Research and visitor recruiting
are automated by default (`research.autoResearch`, `research.forks`/`research.capstones` and
`providence.recruitVisitors` hand them back), and the warehouse janitor trims the surplus.

## How it works

| Subsystem | What it does |
|---|---|
| GrowthDirector | Follows an ordered, population-gated growth plan (JSON-configurable) and places the next hut on a paced cooldown; after the plan completes (`growth.endlessExpansion`) it adds homes, and a guard tower for every three, while beds are the cap and jobs outnumber people, then one extra guard tower per `growth.citizensPerExtraProduction` villagers, up to `growth.guardTowerCap` |
| SiteSelector | Scans expanding rings around the town hall for a flat, dry, unclaimed, non-overlapping plot — measured with the rotation the building will actually be built in |
| UpgradeDirector | Queues build/upgrade work orders (lowest level first, builders' huts on ties), capped at one in-flight order per hired builder; files the repairs owed by buildings it opened early (raid damage is DamageWatch's own re-paste) |
| MaterialTrickle | Materializes missing construction materials inside builders' huts at a configurable items/minute |
| ProvidenceSweep | Fulfills any request the colony itself cannot resolve after a delay — the never-stall safety net (in Progression Mode, paid from the treasury, and never a player's own order; while the exchange is on, the exchange's goods at four times their exchange value by default) |
| Milestones | Colony chat on completed constructions and population growth |

All colony logic runs on the server, and it is dedicated-server-safe. MineColonies' own AI keeps
running; the addon works around it and changes a few of its rules, each behind a switch: the
`[tweaks]` section (graves, mourning, food-quality happiness, work in rain, tool tiers, arrival at
lifted huts and more), the guard posts it holds for the towers it staffs, the infirmary's cure for
the sick, MineColonies_Tweaks' and MineColonies_Compatibility's hut pages kept to the colony's
owner and officers while the autopilot is on, and in Progression Mode the locked Postbox (so
nothing a player orders is conjured), the restaurant and nether-mine menus and Tweaks' "Request
Cost?" order.

**Progression Mode** (on for every colony by default): what the autopilot conjures (requested
items, build materials, guard gear, the warehouse's potions and scrolls, gear upgrades and
research) and its cures of the sick and infected are paid for from the colony's treasury in
**ColonyBucks**; food deliveries (the pantry and menus included), placement, roads and
terraforming stay free, while a research's cost items are charged whatever they are. Hostile
mobs drop the bucks (a guard's kill pays straight into the
treasury, and every raider killed in a raid pays one, up to the raid's size), loot chests hold them, and the exchange sells
them for valuables. Put them by hand in any rack of the town hall (or of a built
warehouse), feed the Town Hall block with a hopper or pipe, or sneak-right-click it holding
them. Only the colony spends ColonyBucks: the Postbox is locked while the mode is on. The town hall's **Exchange** tab (the
ColonyBucks seal under Settings) shows the treasury in whole bucks, what waits for it and the day's ledger, opens the
exchange, and lets the colony's owner and officers have every road and its lamps laid again (**Re-lay roads**, once a
game-minute). The colony's owner and officers can switch the mode off on the town hall's autopilot page. Details in [COMMANDS.md](COMMANDS.md).

## Requirements — exact tested versions

| Component | Version |
|---|---|
| Minecraft | **1.21.1** (exactly — not 1.21.0, not 1.21.2+) |
| NeoForge | **21.1.235** (dev-tested; any 21.1.x works for the colony stack. If you also run them: Naturalist needs ≥ 21.1.226, Spore ≥ 21.1.212, Create ≥ 21.1.219) |
| MineColonies | **1.1.1368-1.21.1** (built and tested against; 1.1.1319 or later 1.21.1 builds accepted — the jar compiles to the same bytes against both) |
| Structurize | 1.0.832-1.21.1 (required by MineColonies 1.1.1368; 1.0.810 with 1.1.1319) |
| BlockUI | 1.0.199-1.21.1 (required by MineColonies) |
| Domum Ornamentum | 1.0.223 (required by MineColonies) |
| Multi-Piston | 1.2.51-1.21.1 (required by Structurize) |

**Install:** drop `colonyautopilot-<version>.jar` into the instance's `mods/` folder alongside
MineColonies and its libraries above, on NeoForge 21.1.x for Minecraft 1.21.1. Install it on the
server AND on every client that joins, at the same version: the mod registers items (the Zone
Marker and ColonyBucks) and a network channel, and NeoForge refuses a client without them or with
another version. On the client it also brings the town hall's Autopilot page,
`/colonyautopilot menu` and the `/colonyautopilot chat` mute.

## Protected zones — reserve ground the autopilot builds around

Out of the box the autopilot only knows its *own* buildings and fields; it does not treat
player-placed blocks as off-limits (terraforming flattens plain blocks, and a chosen plot is
cleared before the blueprint pastes). **Protected zones** are how you fence off ground
for your own builds — spore defenses, a bunker, anything: the autopilot then places no building,
field or road, grades no terrain and fells no tree inside one, and its creep guard scrubs nothing
there.

- **Zone Marker wand** — handed to every player on first join (beside the build tool; the
  Town Hall itself goes only to the world's founder), craftable as a Build Tool ringed by
  8 sticks, or `/colonyautopilot zone wand`.
  Right-click two corners to create a zone; sneak-right-click inside one to delete it (both need
  the colony's right to manage its huts, Officer rank by default). While held, it draws your
  zones as green wireframe boxes (single-player; the zones are enforced server-side regardless).
- **Commands** — `/colonyautopilot zone list | clear` (per colony; `clear`, which removes every
  zone of the colony at once, is op-only).
- **Config** `[zones]` — `enabled` (true), `maxEdge` (48, clamps an oversize selection),
  `maxPerColony` (24).

## Fungal Infection: Spore (optional apocalypse)

With [Fungal Infection: Spore](https://modrinth.com/mod/fungal-infectionspore) 2.2.0j+
installed (needs NeoForge **21.1.212+**), the addon quietly extends itself — no hard
dependency, pure runtime lookups:

- **The guards fight the infection.** MineColonies guards only attack mobs on their hostile
  list, and Spore's creatures aren't on it — out of the box every guard type ignores the
  infection until personally bitten. The addon tags Spore's own creature list into
  MineColonies' `minecolonies:hostile` tag, so knights, rangers and druids engage
  the infected proactively (a data tag — inert without Spore installed).
- **The village floor heals.** The infection's soil conversions (infested dirt, sand, red
  sand, gravel, clay, soul sand, rooted mycelium — and vanilla mycelium, which is what
  infected grass becomes) count as earth: the daily grounds sweep cuts the creep back to
  clean dirt, doorstep dig-outs clear infested burial, and the farmer's field physical
  cleanses infested farmland. The creep guard fills the ground it cures back up to where it
  last found it clean (a root mass leaves no crater), refills what the creep hollowed under a
  building, and lays the village's roads back; a road that has lost more than three blocks is
  carved again within a game-day.
- **The living are treated.** No medicine in MineColonies or vanilla recognizes Spore's
  Mycelium Infection — an infected citizen would stand debuffed at his station, producing
  nothing, until it killed him. The village nursing round draws the infection out of living
  citizens (in Progression Mode at the price of the dearest MineColonies disease's cure). Toggle:
  `spores.cureInfection` in the server config.
- **The quiet years (grace period).** `spores.gracePeriodDays` (server config, **default `50`**)
  holds the infection off while the world is younger than that many game-time days: **NO new
  spore creatures spawn — mounds, apostles, raid summons, and even manually `/summon`ed or
  spawn-egg'd spore mobs** (you hear the spawn sound, but the mob is culled the same tick) —
  giving the colony room to grow before the apocalypse begins. With **Spore Inquisition**
  installed it also sets **when** the once-per-world **rednight** lands — the default `50` gives
  the colony ~50 game-days to reach pop 25+ and research its combat/population chain first, and
  every 5 days the chat counts it down for everyone online (`Days before rednight - 50 Days`)
  until it falls (the server's `milestones.enabled = false` silences the countdown; a player's
  `/colonyautopilot chat off` does not, as it is the war's warning, like the alert horn); set
  `1` to test the rednight on the very first night. **`0` disables the grace *and* the rednight**
  (at `0` the addon steps aside for Spore Inquisition's own drop; the AutoColony Apocalypse modpack disables that, see below), so keep
  `1`+ for the rednight. ⚠ **Set it at world CREATION.** And if spore ever seems *broken*
  mid-game — nothing spawns, spawn eggs do nothing — **check this setting FIRST**: a large grace
  is almost always why (it is NOT a mod conflict).
- **The finale is player-triggered.** The [AutoColony Apocalypse](https://www.curseforge.com/minecraft/modpacks/autocolony-apocalypse) modpack ships [Spore Inquisition](https://www.curseforge.com/minecraft/mc-mods/spore-inquisition) with
  `automaticFinalitas = false` (in `spore-inquisition-server.toml`), so world corruption caps at
  99% and the apocalypse never auto-ends — *you* choose when to fight the final boss by making an
  **offering** to the RedNight. To end the infection: craft an offering head (food +
  `spore:biomass` — Tier 1 "Magnum Ientaculum" +25, Tier 2 "Cena Suprema" +50, Tier 3 "Desertum
  ultimum" +100), **drop it onto the RedNight's biomass ground** (a `#spore:biomass_to_membrane`
  block) to raise corruption to 99%, then present one more to begin **Finalitas** (→ the
  `inqui:finalitas` arena → kill the "Nunny" Proto-Gravemind). Admin shortcuts:
  `/function inqui:0_config` or `/scoreboard players set !finale finalitas -1`. Full step-by-step
  on the in-game guide's Offerings & the Finale page (`/colonyautopilot guide`) and in the
  Spore/SI codebook.

## Building

JDK 21 required.

```
./gradlew build      # jar lands in build/libs/ (Windows: gradlew.bat build)
./gradlew runClient  # dev client with MineColonies and its libraries; put optional mods in run/mods yourself
```

## Commands

In-game controls: `/colonyautopilot status` and `report` (what every colony is doing),
`problems` (what needs a human), `settings [section]` (every key with its value, range and
default; `settings <section.key>` says what a key does), the per-player `chat on|off` mute,
`zone wand|list`, `guide`, `exchange` (valuables
for ColonyBucks) and `treasury [<id>]` (a colony's ColonyBucks; these two need access to that
colony's huts) — all open to any survival player. The switches that change the village for
everyone need op (in single-player, cheats): the master `on|off`, `expansion on|off`,
`golems on|off`, `problems on|off`, `set <section.key> <value>`,
`colony [<id>] settings|set|reset` (one colony's own settings),
`colony [<id>] treasury add <n>` (credit a colony's ColonyBucks) and `zone clear`.
`menu` opens the autopilot page of the colony you stand in, or the nearest
(client-side; looking needs access to the colony's huts, changing its owner or officer rank). Details in [COMMANDS.md](COMMANDS.md).

## Configuration

`config/colonyautopilot-server.toml` — the default for every colony; since 1.3 each colony can
carry its own values (town hall → Settings → **Autopilot settings**, or `/colonyautopilot menu`;
owner and officers may change, everyone with hut access may look; a few keys are server-wide
and say so). The sections: `autoupgrade` (enable, check interval),
`trickle` (enable, items/minute), `growth` (enable, placement cooldown minutes = the village's
pace, search radius, plot padding, terrain tolerance, endless expansion, the daily hut-settings
profile and pond/field upkeep), `providence` (enable, delay seconds, items per sweep, and a
switch per category: food, tools, armour/weapons, building materials, guard gear, recruit
costs, restaurant menus), `repair` + `milestones` + `problems` (enables), `spores` (cureInfection,
**gracePeriodDays** — the Fungal Infection: Spore extras; `gracePeriodDays`
holds off ALL spore spawning for N game-time days [default 50 — set at world creation; 1 drops the rednight on the first night, 0 = no rednight], and with
Spore Inquisition installed it sets when rednight lands), `workshops` (recipe teaching, warehouse
stock and janitor; `purgeMobDrops` — the colony-wide mob-drop purge — is opt-in), `tweaks`
(workersWorkInRain, vanilla patrols, fire tick, spawn cap, chunk-keeping, `villageGolem`,
`workBudgetPerTick` — the land-work cap shared by every colony's terraforming — and
more), and `economy` (Progression Mode, per colony; server-wide: what a buck buys, gear weight,
research fee, drop and chest rates, the exchange's offers, the builder cap).
`/colonyautopilot settings <section>` prints any section in-game.

`config/colonyautopilot-growthplan.json` — the growth plan, written with defaults on first run.
Ordered entries of `{hut, name, minCitizens, targetCount, targetLevel}`; `targetCount` is
cumulative per hut type, `minCitizens` gates that entry and everything after it. `targetLevel` > 0
makes the entry a level rung: the plan holds until a building of that hut stands at that level. The
default plan uses rungs for the Town Hall, levels 1 to 5 (at 0, 10, 18, 20 and 22 citizens), and the
autopilot drives the hall's own upgrade while a hall rung holds; a rung on any other hut only waits
for the ordinary upgrade ladder, which caps huts at the Town Hall's level + 1. Other levels are not
planned: everything upgrades to max over time. In Progression Mode list the Builder's Hut early (the
default plan has it second): until one is built, every other placement is held.

## License

GPL-3.0, for license compatibility with MineColonies. Not an official MineColonies project.

Attribution check: at start-up the mod reads the `authors` field of its own `neoforge.mods.toml` (`Maker.java`). In 1.3 a build that no longer names Backwards still registers its items, configs and mixins but starts none of its directors (`ColonyAutopilot.java`); from the next release it only logs a warning. GPL-3.0 lets you change or remove the check in your own builds.
