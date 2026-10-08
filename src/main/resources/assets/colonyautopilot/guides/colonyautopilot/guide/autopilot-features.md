---
navigation:
  title: Autopilot Systems
  position: 80
---

# Autopilot Systems

Everything the addon runs so you don't have to. Everything below ships **on** by default — with
one exception, the colony-wide mob-drop purge (`purgeMobDrops`, off unless you switch it
on; this pack ships it on) — and stands down together with
<CommandLink command="/colonyautopilot off">/colonyautopilot off</CommandLink> (the ColonyBucks
loot excepted).

## Your colony's settings

Every switch and number of the autopilot can be set **per colony**: open the town hall,
turn to **Settings**, and click **Autopilot settings** — or run
<CommandLink command="/colonyautopilot menu">/colonyautopilot menu</CommandLink> where you stand.
The page turns like the rest of the book: sections on the left leaf, the open section's
keys on the right. A switch toggles On/Off, a number takes a value and **Set**, and the
**Reset** beside a value you changed puts the server default back (a row you may only look at says *custom* instead).
A row that says *global* is the same for every colony on the server and cannot be changed
here: change it in `config/colonyautopilot-server.toml`, or as an operator with
<CommandLink command="/colonyautopilot set">/colonyautopilot set section.key value</CommandLink>.
Those keys are the server's on purpose: prices, rates, world tweaks and the spore grace touch
every colony and every player, so one colony's owner may not set them for the others, and
nobody changes them by accident from a colony's page. In singleplayer you are the server:
use the file, or the command with cheats on. Hover a name for what it does, its default
and its range.

Everyone with access to the colony's huts may look; the colony's **owner and officers**
may change (MineColonies' own hut-settings rule). The server's
`config/colonyautopilot-server.toml` stays the default for every colony; a colony's own
values live in the world save. A few keys are server-wide — the master switch, the world
and gamerule tweaks, the gun-enchantment and full-auto keys, the spore-grace and turret
keys, the supply's pace and the ColonyBucks prices and rates, the switch that locks
Progression Mode on (the Progression Mode row then reads global too), and a few housekeeping
keys (the upgrade clock, the problem ledger and its chat line, zones per colony, frontier
tickets, the per-tick work budget) — and the page marks them "global". Gunner guards is set
per colony.
<CommandLink command="/colonyautopilot settings">/colonyautopilot settings</CommandLink> still lists the
server defaults in chat.

## Progression Mode and ColonyBucks

On by default for every colony. The village keeps planning, placing, paving and shaping its
ground for free. What the autopilot conjures (requested items, build materials, guard gear,
warehouse potions and scrolls, gear upgrades and research) and the cures of the sick and
infected are paid for from the colony's treasury in **ColonyBucks**. Food deliveries are always
free, the pantry and menus included; a research's cost items are paid whatever they are, and so
are a cure's.

* **Only the colony spends** — you turn the goods you earn into ColonyBucks at the
  exchange, never the other way. While the mode is on the Postbox is locked (an order is
  refused in chat), and a stock line or field seed you set yourself is not supplied: the
  colony's couriers and crafters may still fill it. MineColonies_Tweaks' "Request Cost?"
  button at the university is refused too: a research is the colony's to buy (with the
  mode off it works as before, for the colony's owner and officers only). The
  restaurants' and nether mines' menus are the colony's: an edit is refused in chat, and a
  dish listed before the mode came on comes off, so a menu keeps the autopilot's staples. A
  composter's, furnace user's or beekeeper's list you edit is served only with what the colony
  would pick itself (vanilla compostables and flowers that are not food, coal and charcoal):
  the rest waits for the colony's stock. A filled shulker box, chest, barrel or
  bundle, a spawner, a vault, a trial key, a spawn egg or a Mystical Agriculture seed is never
  supplied. With the mode
  off the Postbox and the menus work as before.
* **Pay in** — put ColonyBucks by hand in any rack of the town hall (or of a built
  warehouse), feed the Town Hall block with a hopper or pipe, or sneak-right-click it
  holding them. One buck buys 128 items, and an iron, diamond or netherite
  tool, weapon or armour piece counts as 16. A research also costs 1 buck per tier when it
  starts; the diamonds, gold and netherite it needs come from the warehouse first when it
  holds them, paid in kind.
* **Earn** — hostile mobs drop one 1 time in 30 when a player, a citizen, or what one of
  them owns (an arrow, a tamed wolf, a K-Turret) kills them, even while the autopilot is
  off. A blow counts for 5 seconds, so a mob you hit that fire, a fall or lava finishes still
  drops one; a death no one caused (/kill, a fall with no blow before it) drops none. The elites (Inquisitor,
  Usurper, Vigil), an ordinary Proto and a Hive Tumor pay too; the calamities, anything with
  150 health or more (a Proto the Rednight has boosted) and the tagged bosses (the Wither, the
  dragon) do not. A buck from a kill one of the colony's citizens made (a guard's)
  goes straight into its treasury in Progression Mode; the rest of the loot drops as usual.
  In Progression Mode each raider killed in a MineColonies raid pays one more buck into
  the raided colony's treasury (up to the raid's size), and the officers hear the total
  when it ends.
  Dungeon and structure chests hold 6–15, mineshaft chests 2–5. Village chests give none.
* **Exchange** — the town hall's **Exchange** tab (the ColonyBucks seal under Settings) and its **Trade**
  button, or <CommandLink command="/colonyautopilot exchange">/colonyautopilot exchange</CommandLink>,
  trade diamonds, gold, ancient debris and netherite for bucks, for a colony in Progression Mode:
  2 diamonds for 1 buck, a diamond block for 5, 16 gold ingots for 1, ancient debris for 1,
  a netherite ingot for 4.
  When the treasury's saving-for line names a price with goods stocked, the clerk says what
  one is worth to it stocked where that line names (a diamond: 2.25 bucks) against what it
  fetches here (0.50; for an ore the exchange does not buy as it is, it says so).
  While the exchange is on, in Progression Mode those, and their ores, raw pieces, nuggets,
  dusts and blocks, and what the pack's machines turn into them (netherite scrap, a jukebox,
  horse armour, ochrum, Mystical Agriculture's essences), are priced over their exchange value (a gold block costs
  four times what nine ingots sell for by default, economy.conjureMarkup), so buying
  them from the providence and selling them back gains
  nothing at the default markup; with the mode or the exchange off they cost what they always did.
* **Short of money** — requests wait (nothing is lost), and each built Builder's Hut carries one
  job at a time. The colony saves for the university's next research: its price is set aside
  from every other spender and it is bought at once when the savings cover it. The town hall's
  **Exchange** tab (the treasury in whole bucks, and the day's ledger: what came in and what went out) and
  <CommandLink command="/colonyautopilot treasury">/colonyautopilot treasury</CommandLink> show
  what waits. Switch **Progression Mode** off on the town hall's autopilot page for the free
  autocolony, apart from the changes that hold in both modes (listed in documentation.md
  section 5.8), unless the server locks it on.

## Growth

* **Growth plan** — places, builds and upgrades every building in a proven order;
  the University opens the moment the autopilot places it (while automatic upgrades are on) so research never waits.
  The Town Hall comes first: its next level is ordered the moment the plan's citizens for
  it are there, ahead of every other upgrade and the next new building (and the builder's hut
  it needs, when none in reach stands at that level yet).
  A builder whose site stops rising (a ladder he cannot climb past, a ledge) is first sent
  home to find his way again, and only a second stall of the same build starts it afresh
  (`autoupgrade.stuckOrderGameMinutes`, 30 game-minutes; a sleeping builder is never
  counted as stuck).
* **Terraforming** — when no natural plot exists the colony makes its own space:
  levels ground, digs the fisher a pond (his hut goes where one fits beside it, on
  low ground rather than a hilltop, and it is kept two deep, creep and ice
  flushed), plants the forester a grove. On hostile
  terrain it force-carves the footprint rather than skip a building.
* **Bounded end-state** — guard towers cap at 20 and homes stop once every job can
  be staffed; after the plan completes the village militarises instead of
  sprawling. Roads routed over the ground (a block up or down a column, round
  the buildings, joining where another road leads on; where no such way exists
  the road cuts or fills its own stair sideways along the face; no plot is ever
  placed across another building's doorstep; a road that loses more than three
  of its blocks is laid again within a game-day, and where roads join, a block
  of the old line another road ends on is laid again), street lamps (no two nearer
  than 15 blocks, `growth.lampSpacing`, unless a stretch of road would go dark:
  crowded ones are taken down) and small decorations round the village off. The colony's owner and officers can have
  every road and its street lamps laid again at once, after reshaping the ground
  or taking a building down: **Re-lay roads**, under **Trade** on the town hall's
  **Exchange** tab (at most once a game-minute; greyed, with the reason, while the
  colony's roads are off).
* **Fields & pens** — farms get crop fields, one a level: a farm goes, where the
  land allows, beside ground a field can take as it lies, and its first field is
  laid there the moment the farm is placed; fields keep off the roads, the
  colony's first three grow potato, carrot and wheat, and a farm climbs to
  level 3 ahead of most upgrades. The autopilot picks every plantation's
  fields, food and cure foods first (kelp, then glow berries, cocoa and sugar
  cane), each plant on one plantation: the hut's own plots, and field
  structures it orders for the rest (from the style pack, or from MineColonies'
  own style when the pack ships none). A plantation's fields tab stays on manual
  while it does. Husbandry huts get founding animal pairs.

## Logistics

* **Material trickle & providence** — construction materials materialise in
  builders' huts; requests nothing in the colony can fulfil are provided after a
  delay. Builders gather their whole need without waiting on couriers, save that in
  Progression Mode, while the colony has a courier, the diamonds, gold and netherite the
  warehouse holds are carried over by the couriers instead of bought (stock them there to pay
  in kind). Providence
  has a switch per category — food, tools, armour and weapons, building materials,
  guard gear, recruit costs, restaurant menus. Its pace (`itemsPerSweep`) is set server-wide.
  In Progression Mode each delivery is paid in ColonyBucks, food excepted, and while the
  exchange is on its goods cost four times their exchange value by default; the smeltery's ore
  is left to the colony's own miner.
* **Warehouse janitor** — standing stock management: four stacks of anything, food
  included; one of building materials; valuables (the exchange's ores too, in
  Progression Mode with the exchange on), gear, ColonyBucks and what an open
  build still needs untouched — plus, with `purgeMobDrops`
  on, a colony-wide purge of mob junk (rotten flesh, bones, arrows, spider eyes,
  spore drops, damaged mob-dropped weapons and armor). Guards keep their ammunition
  and worn kit; the combat and archery trainees and the nether worker keep their
  worn weapons too, on them and on the training huts' racks; a plantation and the
  warehouse keep the vines and sea grass the colony's plantation fields plant from.
* **Quartermaster** — every worker's tool and every guard's armor is upgraded to
  the best the village can craft; enchanted and modded gear is never touched. In
  Progression Mode couriers and the blacksmith are passed over.

## People

* **Citizen care** — the hungry are fed, the sick are nursed, the spore's
  corrosion and kin are wiped for a credit (the infection itself stays: its
  damage tick only slows a villager), and the Wither a modded mob inflicts is
  wiped for a credit, the milk (in Progression Mode the treasury pays for each
  cure, and an unpaid one waits); fallen citizens' huts rise again. Berry bushes that poke walkers
  to death (the sweet berry bush, Ars Nouveau's sourceberry bush) are cleared
  off the colony's land outside its buildings and your protected zones, and at
  once wherever one kills a villager (`tweaks.hazardBushes`; keep a berry patch
  you want inside a zone). Visitors are recruited when
  beds are free; wedged workers are unstuck.
* **Research** — the university researches the whole tree unattended, including
  the three tier-6 capstones (+6 health for all citizens, double-life guard
  armour, faster building).

## War

* **Creep guard, building re-paste, spreader culls, K-Turret handling, golem
  patrols** — see [Fighting Back](fighting-back.md).
* **Hostile spawn cap** — vanilla hard-codes 70 monsters and the pack keeps
  that (`tweaks.hostileSpawnCap`); spore raids spawn outside the cap anyway, so
  raising it only thickens the background night — an option for operators whose
  tps can afford a denser horde.

## Quality of life

* Fire cannot spread through the colony (doFireTick off), workers work in rain,
  food-quality nagging is silenced, and FarmersDelight recipe noise from Spore
  Inquisition is muted. Vanilla pillager patrols stay on; if they wedge on the
  hills and snipe your guards, `tweaks.disableVanillaPatrols` turns them off.
* MineColonies_Tweaks' and MineColonies_Compatibility's hut pages (maximum stock, item
  lists, field sizes, teach screens) need the colony's right to manage its huts, as
  MineColonies' own do: a Friend or a stranger can no longer change another colony's.
