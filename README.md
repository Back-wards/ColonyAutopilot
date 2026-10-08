# Colony Autopilot

**Place a Town Hall. The colony runs the rest.**

Colony Autopilot is an addon for [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) on Minecraft 1.21.1 (NeoForge). It plays the town planner for you: it picks where every building goes, shapes the land, lays lit roads, orders builds and upgrades, hires and equips the citizens, runs the university, repairs raid damage and mans the defences. Your citizens still do the work, by MineColonies' own rules. You keep the parts of MineColonies that are fun: the quests, the raids, the treasury, and watching a village grow out of nothing.

Made by **Backwards** ([CurseForge](https://www.curseforge.com/members/backwards/projects)). Licensed under GPL-3.0.

- **Download the mod:** [Colony Autopilot on CurseForge](https://www.curseforge.com/minecraft/mc-mods/colonyautopilot)
- **Want the full apocalypse?** The [AutoColony Apocalypse](https://www.curseforge.com/minecraft/modpacks/autocolony-apocalypse) modpack bundles this mod with Fungal Infection: Spore, guns, turrets and a 50-day countdown to the end of the world.
- **Bugs and questions:** [open an issue here](https://github.com/Back-wards/ColonyAutopilot/issues). Please do not report Colony Autopilot problems to the MineColonies team.

## What you need

- Minecraft 1.21.1 with NeoForge 21.1 (any 21.1.x build; 21.1.235 and 21.1.251 are the tested ones).
- MineColonies 1.1.1319 or newer, with the libraries it already asks for (Structurize, BlockUI, Domum Ornamentum). Tested with 1.1.1368.
- Put `colonyautopilot-<version>.jar` in the `mods` folder. On a server it goes on the server **and** on every player's client, the same version everywhere.

It works on a colony you already have. If you ever want to remove the mod, run `/colonyautopilot off` first so the colony hands its buildings back to plain MineColonies.

## Your first hour

1. **Join the world.** On your first join you get a starter kit: MineColonies' build tool, a **Zone Marker**, and the **Field Guide**, the in-game book that explains everything on this page in detail. The first player to join a world that has no colony yet also gets a **Town Hall** block, once per world.
2. **Place the Town Hall and confirm the founding** in the window that opens. From this moment the colony is on autopilot.
3. **Watch the village start.** Within a game-minute the first Builder's Hut is placed on flat ground near the hall, its ground levelled and a lamp-lit road laid to its door. The builder gets the materials delivered and starts. A new building follows about every ten game-minutes: a Tavern for recruits, Residences, a Guard Tower, a Farm, a Warehouse, a Courier's Hut, and on through a 69-step plan that ends in a fully grown town.
4. **Read the chat.** The colony tells you when a building finishes, when the town grows, when the Town Hall goes up a level, and once a day it lists anything that needs a human. `/colonyautopilot status` shows what each colony is doing right now.
5. **Keep the treasury full.** In Progression Mode (on by default) the colony pays for its supplies in **ColonyBucks**, a coin you earn by playing. More on that below. If you would rather have the colony supplied for free, switch Progression Mode off on the town hall's Autopilot settings page.

## What the colony does for you

- **Finds or makes the land.** Flat, dry plots first. When none fit, it levels the ground or carves the footprint, so no building is skipped. A ravine between buildings gets a causeway.
- **Builds along a plan and upgrades in order.** The Town Hall goes up as soon as the population allows it, nothing climbs more than one level above the hall, and upgrades of standing buildings come before new ones.
- **Roads and light.** A lamp-lit road from every door into the village, laid again when it breaks, with lamp posts spaced so they light the road without crowding it.
- **Hires and equips everyone.** Empty workplaces are filled from the jobless, tavern visitors are recruited while beds are free, workers get tools and armour matched to the colony's stage, and worn gear is replaced.
- **Feeds and heals.** The hungry are fed and the sick are cured from the colony's stores, or from the treasury when the stores run short.
- **Keeps the warehouse stocked** with staples, destroys the junk that piles up, and teaches the crafters their recipes.
- **Outfits each workplace:** fields for the farm and the plantation, animals for the ranchers, a bee nest, a pond for the fisher, a grove for the forester.
- **Researches.** The university works through MineColonies' research tree on its own, unlocking the buildings the plan needs next.
- **Repairs.** A building that lost eight or more blocks to a raid or an explosion is restored from its blueprint. No builder needed.
- **Keeps the grounds.** Doors dug free, stray trees felled, deadly drops and lava made safe, berry bushes cleared.
- **Defends.** Guard towers go up where buildings stand unguarded, up to twenty. The guards are kept at three fighters to one archer with a druid for every four, and more barracks are built as they need room. Two iron golems watch the Town Hall and one each Blacksmith, re-forged when they fall. An alert horn sounds for everyone online when the village is being killed.
- **Unsticks itself.** A builder whose site stops rising is sent home to find a new way to it. A build that stalls twice goes to another builder. Workers who freeze are fixed and rehired.

Every one of these has its own switch. See *Settings and switches* below.

## Money: ColonyBucks

In Progression Mode the colony is not supplied out of thin air. What it cannot make itself is **bought with ColonyBucks** from its treasury: deliveries, building materials, guard gear and gear upgrades, research, and cures when the stores are empty. Recruits, food deliveries and the outfitting of workplaces (fields, ponds, animals, golems) stay free.

**Earning bucks**

- A hostile mob drops a buck one time in thirty when you, a citizen, or something you own (a golem, a turret, a tamed wolf) kills it. A citizen's kill goes straight into the treasury.
- Dungeon and other structure chests hold six to fifteen bucks; mineshaft chests two to five.
- Every raider killed during a raid pays one buck.
- The **exchange** buys valuables for bucks: two diamonds for one buck, a diamond block for five, sixteen gold ingots for one, one ancient debris for one, a netherite ingot for four. Open it with `/colonyautopilot exchange` or the **Trade** button on the town hall's Exchange tab. It only ever buys. Bucks buy nothing back: only the colony spends them.

**Paying in**

Sneak-right-click the Town Hall block with bucks in your hand. A hopper, a pipe, or the hall's own racks work too.

**When the money runs out**

Nothing happens on credit. Requests, research and cures simply wait, chat says `The colony treasury is empty — waiting for ColonyBucks: 12 requests`, and the moment you pay in, the queue drains. A site waiting for money is never counted as stuck. The colony saves up for research it cannot afford yet and buys it whole.

**Seeing the books**

The town hall's **Exchange** tab (or `/colonyautopilot treasury`) shows the balance, what is waiting, what the colony is saving for, the day's income and spending, the **Trade** button, and a **Re-lay roads** button that redoes every road in the village.

## Keeping your own builds safe

The colony builds around what you make, as long as it knows where that is.

- **Zone Marker.** Right-click two corners with the marker from your kit (or `/colonyautopilot zone wand`) to fence off an area. Inside a zone the colony places nothing, levels nothing, paves nothing, lights nothing, fells nothing and scrubs nothing. Up to 24 zones per colony, each up to 48 blocks on a side. Mark your base before the town reaches it.
- **Left alone on its own:** anything built high over the town, log walls, treehouses, and any block column that holds a chest or a sign.
- **Huts you place yourself** are adopted: built, upgraded, staffed and given a road like any other.

## Settings and switches

Open the Town Hall, go to **Settings**, then **Autopilot settings** (or stand in the colony and run `/colonyautopilot menu`). Every feature of the mod has a row there for *this* colony: switches turn on and off, numbers take a value, and **Reset** puts the default back. Hover a row to read what it does.

Rows marked **global** are server-wide: the prices, the earning rates, the world tweaks and anything else that affects every colony and every player. They cannot be changed from a colony's page, on purpose, so nobody changes the whole server's economy by accident while poking at their own town. Change them in `config/colonyautopilot-server.toml`, or as an operator with `/colonyautopilot set section.key value`. In single-player you are the server, so use the file or the command with cheats on.

Commands a survival player actually uses:

| Command | What it does |
|---|---|
| `/colonyautopilot status` | What every colony is doing and waiting for |
| `/colonyautopilot problems` | The things that need a human |
| `/colonyautopilot treasury` | The balance and the day's ledger |
| `/colonyautopilot exchange` | Sell valuables for bucks |
| `/colonyautopilot chat off` | Silence the colony's chat for yourself |
| `/colonyautopilot expansion off` | No new buildings; upgrades continue |
| `/colonyautopilot off` | Hand the colony back to plain MineColonies |

The full list, including the operator commands, is in [COMMANDS.md](COMMANDS.md).

## Playing with other mods

These are detected automatically. None is required.

- **[Fungal Infection: Spore](https://www.curseforge.com/minecraft/mc-mods/fungal-infection-spore).** The colony gets a grace period of 50 in-game days before any spore creature spawns (set it before creating the world). Guards attack the infection on sight. The infection slows citizens instead of killing them, its other effects are cured, the creep is scrubbed off the colony's land and the ground it ate is filled back in, and spore mobs cannot break the colony's blocks.
- **[Spore Inquisition](https://www.curseforge.com/minecraft/mc-mods/spore-inquisition)** (with Spore). The grace counts down in chat, then the RedNight falls at world spawn and the war begins.
- **[TaCZ](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1)** adds six gun enchantments. With its [MineColonies add-on](https://www.curseforge.com/minecraft/mc-mods/minecolonies-compatibility) too, the colony's ranged guards become gunners with a colony-issued rifle.
- **[K-Turrets](https://www.curseforge.com/minecraft/mc-mods/k-turrets)** (with Spore). Turrets target the whole horde and take no damage.
- **[GuideME](https://www.curseforge.com/minecraft/mc-mods/guideme)**, **[Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli)** or **[Akashic Tome](https://www.curseforge.com/minecraft/mc-mods/akashic-tome)** turn the Field Guide into a searchable illustrated book and put the other mods' guides in your kit.

## Style packs

Every style that ships with MineColonies works, and the town is built in the style you pick at founding. Styles designed around fixed building positions, Fortress above all, look best when you place their buildings by hand and let the colony build, staff and upgrade them.

## When something looks wrong

- Start with `/colonyautopilot problems`. Once a day the colony also lists them in chat. Most say exactly what to do.
- A build that is not rising is not necessarily stuck: it may be waiting for money (chat says so) or for a builder to finish another job. Builders who really are stuck are sent home and, if that fails, the job moves to another builder. You do not need to do anything.
- A citizen who keeps getting stuck at one spot is reported with the coordinates, so you can go and fill the hole or bridge the gap.
- Anything else: [open an issue](https://github.com/Back-wards/ColonyAutopilot/issues) with the mod version, the MineColonies version, and the lines from `logs/latest.log` that mention `ColonyAutopilot`.

## For server owners and builders of the mod

- **Server config:** `config/colonyautopilot-server.toml`, every key commented. It is the default for every colony; each colony's own values live in the world save.
- **The growth plan:** `config/colonyautopilot-growthplan.json`. Edit it to change what gets built, in which order, and at how many citizens.
- **Building from source:** JDK 21, then `./gradlew build` (Windows: `gradlew.bat build`); the jar lands in `build/libs/`. `./gradlew runClient` starts a dev client with MineColonies and its libraries. Optional mods go in `run/mods` yourself.

| Tested with | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 (dev), 21.1.251 (modpack) |
| MineColonies | 1.1.1368 (minimum 1.1.1319) |
| Structurize / BlockUI / Domum Ornamentum | 1.0.832 / 1.0.199 / 1.0.223 |

## Licence and credits

GPL-3.0, for licence compatibility with MineColonies. This repository is the corresponding source of each CurseForge release, one tag per version. Not an official MineColonies project.

Attribution check: at start-up the mod reads the `authors` field of its own `neoforge.mods.toml` (`Maker.java`). In 1.3 a build that no longer names Backwards still registers its items, configs and mixins but starts none of its directors; from the next release it only logs a warning. GPL-3.0 lets you change or remove the check in your own builds.

Third-party assets and their licences are listed in [NOTICE.md](NOTICE.md).
