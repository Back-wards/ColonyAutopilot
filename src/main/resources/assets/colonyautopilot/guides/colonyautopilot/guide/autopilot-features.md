---
navigation:
  title: Autopilot Systems
  position: 20
---

# Autopilot Systems

Everything the addon runs so you don't have to, area by area. You place the Town Hall; the
village plans, builds, staffs, feeds, arms and researches itself. This page says how, and
which switch changes it.

Everything below ships **on** by default, except three switches that wait for you or your
modpack: the colony-wide mob-drop purge (`workshops.purgeMobDrops`), no mob griefing
(`tweaks.disableMobGriefing`) and no pillager patrols (`tweaks.disableVanillaPatrols`). The
Mutant-Spore-Apocalypse pack ships the first two switched on.

An operator can stand the whole autopilot down for a world with the master switch
(`/colonyautopilot off`, **op**): the colony then plays by plain MineColonies rules. A few
things keep working on purpose: ColonyBucks still drop from monsters and lie in loot chests,
trap blocks stay a wall to your citizens, and the calendars that hold the spore horde and the
other monster mods back keep counting.

**Progression Mode** is on by default too: the colony pays **ColonyBucks** from its treasury
for what the autopilot supplies (deliveries, building materials, gear, potions and scrolls,
cures and research), while food, planning, placing, roads and recruits stay free. Below, a
paid system says so in a word; earning, paying in and switching the mode off are in
[ColonyBucks](colonybucks.md).

## Your colony's settings

Most switches and numbers of the autopilot can be set **per colony**. Open the town hall,
turn to the **Exchange** tab (the coin seal) and click **Autopilot settings**, or run
<CommandLink command="/colonyautopilot menu" close={true}>/colonyautopilot menu</CommandLink>
from anywhere in the colony's dimension: it opens the page of the colony you stand in, or of
the nearest one. From the Nether it finds no Overworld colony.

The page turns like the rest of the book: sections on the left leaf, the open section's keys
on the right. Under each name a row says **PER-COLONY** (this colony only) or **GLOBAL**
(every colony on the server). A switch toggles On/Off, a number takes a value and **Set**, and
the **Reset** beside a value you changed puts the server default back (a row you may only look
at says *custom* after PER-COLONY instead). Hover a name for what it does, its default and its
range.

Everyone with access to the colony's huts may look; the colony's **owner and officers** may
change (MineColonies' own hut-settings rule). The server's
`config/colonyautopilot-server.toml` is the default for every colony; a colony's own values
live in the world save.

About a third of the keys are **GLOBAL**: prices, rates, world tweaks, the spore grace and the
monster arrival days touch every colony and every player, so one colony's owner may not set
them for the others. Only an operator, or the host of a single-player world, may change one:
here on the page, with `/colonyautopilot set section.key value` (**op**), or in
`config/colonyautopilot-server.toml`. In single-player you are the server: the host changes
them right here, cheats on or off, and they are saved in the game profile's config, so they
hold for every world played in that profile. The master switch is the exception: each world
keeps its own.

Lists and texts are not on the page: the research choices (`research.forks`,
`research.capstones`), the structures the colony ignores, the hazard bushes, the exchange's
offers, the ColonyBucks drop lists and a few more. An operator sets them in the config file,
or a text with `/colonyautopilot set`.
<CommandLink command="/colonyautopilot settings" close={true}>/colonyautopilot settings</CommandLink>
lists the config's sections in chat, and
<CommandLink command="/colonyautopilot settings research" close={true}>/colonyautopilot settings research</CommandLink>
(or any other section's name) lists the server's value of every key in it, lists and texts
included.

The menu's bookmarks turn to the town hall's other pages, and for whoever may manage the
colony's huts (its owner and officers) they work from afar too: **Recall** on the citizens page
and the block tool on the permissions page answer from anywhere. Every other hut keeps
MineColonies' 15-block reach.

## Growth

* **Growth plan** — places, builds and upgrades every building in a proven order; the
  University opens the moment the autopilot places it (while **Build placed huts** is on), so
  research never waits. The Town Hall comes first: its next level is ordered the moment the
  plan's citizens for it are there, ahead of every other upgrade and the next new building
  (and the builder's hut it needs, when none stands at that level yet). Eight buildings are
  never given up on: the Tavern, Warehouse, Blacksmith's Hut, Dining Hall, University, Stable
  (where your style has one), Library and Hospital, each once its research is done. When one
  is the plan's next building, new guard towers (never your first) and extra builder's huts
  wait for it (while it waits behind an earlier step they take turns with the plan), it is placed even while everyone has a job, and with no plot near it is
  searched farther out, at the claim's edge, beside good land a new builder's hut is raised
  to reach when a citizen is free (the land is kept for it), or on levelled ground while
  **Terraform** is on; with no ground at all it is tried again every 40 game-minutes while
  later steps go on. A plan file
  left by an older version of the mod keeps its old order and builds no Stable: see
  *Updating from an older version* in [Getting Started](getting-started.md).
* **Your own huts** — want a building sooner, or in a spot of your choosing? Place its hut
  yourself with the build tool. The colony adopts it like one of its own: builds it (while
  **Build placed huts** is on), upgrades it, staffs it and lays it a lit road. It counts
  toward the plan, so the colony places no second one until its plan calls for more.
* **Stuck builds** — a builder whose site stops rising (a ladder he cannot climb past, a
  ledge) is first sent home to find his way again, and a second stall of the same build
  starts it afresh (a decoration or field structure you placed yourself is cancelled instead:
  place it again). A site short of the materials for the work in hand, or whose builder cannot
  be sent home, is started afresh at its first stall. The wait is
  `autoupgrade.stuckOrderGameMinutes`, 30 by default (a game day and a half), three times that
  for a builder still walking about, as one going home to bed does; a sleeping builder never
  counts as stuck.
* **Build or upgrade yourself** — the settings page's **Construction** section has a switch
  for each. **Build placed huts** off: the colony still places its huts, and each waits for
  your Build button. **Upgrade buildings** off: huts are placed and built to level 1 and every
  upgrade is yours; the plan then waits at each Town Hall step until you raise the hall.
  Example: to choose yourself when the Town Hall goes up, switch Upgrade buildings off and
  press its upgrade when you are ready. **Build & upgrade (master)** off stops both, and the
  restart of stuck builds with them; in Progression Mode the colony then soon stops placing
  too, because every unbuilt hut holds one of the builders' jobs.
* **Terraforming** — when no natural plot exists, the colony makes its own space: it levels
  ground, digs the fisher a pond (his hut goes where one fits beside it, on low ground rather
  than a hilltop, and the pond is kept two deep, creep and ice flushed) and plants the forester
  a grove where fewer than four trees or saplings stand within 32 blocks. On hostile terrain it
  force-carves the footprint rather than skip a building. A levelled plot is cleared of
  whatever stands on it, your own blocks included; only a column that holds a chest, a
  furnace, a sign or another such block, or a block nothing can break such as bedrock, is
  spared. Mark a [protected zone](protected-zones.md) over what you want kept. The same
  switch (**Terraform**, per colony) grades the ground round each new building and the town
  hall, smooths the earth between the buildings once a day and fells stray trees there, cuts a
  step down to the water where a bank stands too high for a swimmer to climb out, raises
  an earth causeway across a ravine, caps lava in the claim, makes safe a drop or a pool that
  killed a citizen, fills a small hole in the village where citizens got stuck twice (see
  Stuck in a hole below), and clears the berry bushes (see People below). Off, it does none
  of these.
* **World structures** — what the world generated at the surface (a village, a ruin, a tower)
  is built around like a protected zone: no plot, levelling, road, field, pond, lamp or
  ornament lands on it (**Avoid structures**, per colony). Example: a colony founded beside a
  desert pyramid lays its roads round it. A structure one of the colony's buildings stands on
  is the colony's own: found the Town Hall in a village and the village is built in. Only a
  building that has found no other ground (after a wait of 40 game-minutes, or two failed
  searches with terraforming off; for one of the eight buildings above the first counts as two) may stand on one. Buried structures (mineshafts,
  strongholds) and ones floating in the sky are never counted, nor is a structure made with
  `/place`; `growth.structuresIgnored` in the server's file lists structures the colony does
  not build around, by id or tag (add `minecraft:ruined_portal` and a road may run over a
  ruined portal).
* **Bounded end-state** — guard towers cap at 20 (`growth.guardTowerCap`) and homes stop once
  every job can be staffed. After the plan completes, the village militarises instead of
  sprawling (a guard tower for every 15 villagers) and its builders raise up to 4 small
  decorations round the village centre.
* **Roads and street lamps** — every door gets a road to the town hall, your own huts' too,
  routed over the ground a block up or down at a time, round the buildings, joining another
  road where one leads on; where no such way exists, the road cuts or fills its own stair
  along the slope. Over open water a road is laid level with the water, so a citizen who
  falls in can climb out onto it; only beside a Fisher's Hut's water or the pond dug for it
  does it stay a boardwalk a block up. Where a road steps diagonally, the corner between the
  two steps is laid as road too where nothing stands in the way. No plot is ever placed across another building's doorstep. A road that
  loses more than three of its blocks is laid again within a game day. Lamp posts stand at
  least 15 blocks apart (`growth.lampSpacing`) unless a stretch of road would go dark; a lamp
  post of your own is never taken down. After you reshape the ground or take a building down,
  the owner or an officer can have every road and its lamps laid again at once: **Re-lay
  roads**, under **Trade** on the town hall's **Exchange** tab (at most once a game-minute;
  greyed, with the reason, while the colony's roads are off).
* **Fields & pens** — farms get crop fields, one a level: a farm goes, where the land allows,
  beside ground a field can take as it lies, and its first field is laid there the moment the
  farm is placed. Fields keep off the roads and out of hollows more than a block below the
  farm (while level ground is in reach), the colony's first three grow potato, carrot and
  wheat, and a farm climbs to level 3 ahead of most upgrades. The autopilot picks every
  plantation's fields, food and cure foods first (kelp, then glow berries, cocoa and sugar
  cane), each plant on one plantation: the hut's own plots, and field structures it orders for
  the rest (from the style pack, or from MineColonies' own style when the pack ships none). A
  plantation's fields tab stays on manual while it does. Husbandry huts get founding animal
  pairs, a Stable its trained cavalry mounts (two a level).

## Logistics

* **Material trickle & providence** — construction materials materialise in builders' huts,
  and requests nothing in the colony can fulfil are provided after a delay. Builders gather
  their whole need without waiting on couriers. Providence has a switch per category: food,
  tools, armour and weapons, building materials, guard gear, recruit costs, restaurant menus.
  Its pace (`providence.itemsPerSweep`) is set server-wide. In Progression Mode each delivery
  is paid from the treasury, food excepted, and the smeltery's ore is left to the colony's own
  miner; diamonds, gold and netherite you stock can save the colony that money
  (*Paying in kind* in [ColonyBucks](colonybucks.md#paying-in)).
* **Stock for you: potions and scrolls** — once an Alchemist Tower and a Warehouse stand,
  the warehouse keeps four splash potions of Harming and four potions each of Healing,
  Regeneration and Strength. Once an Enchanter's Tower stands, the warehouse also keeps four
  **Ultrasafe Colony Teleport Scrolls**, and from the tower's level 3 two **Spatial Guard
  Reinforcements Scrolls**, which call your guards to you for a fight. They are there for you:
  take what you need, and the colony tops them up. Before you use a scroll, sneak-right-click a
  hut of your colony with it to set it to that colony. On a night a fight is forecast, the
  warehouse keeps twice as many. In Progression Mode each one is paid from the treasury.
  Switches: **Combat potions** and **Enchanter scrolls** (Providence).
* **Recipes** — every built crafter is taught a starter set of recipes, and the warehouse
  keeps a standing stock of staple goods that keeps those workshops producing. To stop a
  crafter making one of his recipes, switch it off in his recipe list: a recipe you remove is
  taught again at the next start of the world, at the hut's next level-up or when the growth
  plan completes.
* **Quartermaster** — every ten seconds, every worker's tools and every guard's armour are
  upgraded to the best his workplace and the colony's smith allow (stone tools and gold armour
  without a Blacksmith, iron with one, diamond once it and the Town Hall reach level 5; a
  guard's armour no better than his tower's level allows), all at once. A worse piece is
  replaced and destroyed even when it is enchanted, and an empty armour slot is not filled.
  Only a worker's own kit is upgraded, the tools his job uses: a mob's sword that a cook picked
  up is left alone. A knight's and a combat trainee's kit is every vanilla sword he carries,
  and a huscarl's every vanilla axe, one you dropped near him or handed him included. Modded
  armour is never touched. Couriers, the blacksmith and the undertaker are passed over, and so
  are the tools a builder carries for a blueprint; in Progression Mode each upgrade is paid.
* **Warehouse janitor** — keeps the colony's storage from silting shut, about every ten
  minutes. The warehouse keeps one stack of anything that places as a block but food (seeds,
  saplings and torches among them), four stacks of every other stackable item (food included)
  and 24 of each potion; **the surplus is destroyed**. Kept whatever the count: valuables
  (iron, gold, copper, diamonds, emeralds, netherite, lapis, redstone and coal, as ingots,
  nuggets, raw pieces, gems, dust and storage blocks, plus netherite scrap, ancient debris,
  charcoal, and quartz and amethyst shards), tools, weapons and armour, ColonyBucks, the
  warehouse's own minimum-stock lines, what an open build still needs and, in Progression
  Mode, the exchange's goods and their ores. Any other ore, and a quartz or amethyst block, is
  cut to one stack like any block: three stacks of silk-touched iron ore become one. Between
  them, the warehouse's and the Enchanter's Tower's shelves keep two books of each
  enchantment, the highest levels, and destroy the rest: with Sharpness V, IV and I there,
  Sharpness I goes. A book with two or more enchantments is never touched. A builder's hut
  ships its surplus to the warehouse, and workers' pockets are trimmed of rubble. Switch:
  **Warehouse janitor** (Workshops).
* **Mob-drop purge** (**Purge mob drops**, `workshops.purgeMobDrops`: off in the mod, on in
  the Mutant-Spore-Apocalypse pack) — on the janitor's rounds (with **Warehouse janitor** off
  it does not run either), the colony destroys mob junk in **every rack of every building**,
  in each hut block's own slots and in its citizens' pockets: rotten flesh, bones, spider
  eyes, pufferfish, poisonous potatoes, junk plants (grass, ferns, dead bushes, vines, sea
  grass, lily pads), arrows of every kind (tipped and spectral too), and **every damaged sword,
  bow, crossbow, trident or armour piece, enchanted or not, whoever put it there**. With Spore
  installed, **every item of the Spore mod** goes too, damaged or not, wherever the purge looks
  and whatever a build or a recipe needs; so does Mystical Agriculture's inferium essence, with
  that mod installed. Of the rest, what the colony works with is spared: guards, trainees and
  the nether worker keep their worn kit, the Archery's and Combat Academy's racks their worn
  weapons, and the guard huts, the Warehouse and the Fletcher's Hut their arrows. Of the other
  junk (never arrows or damaged gear), a crafter keeps the ingredients of his recipes (the
  crusher its bones), the composter his plants, a plantation and the warehouse the plants the
  colony's fields grow from, and every hut what an open build still needs. A courier's load is
  never searched.

**Keep your own things safe.** The colony counts every rack of its buildings, and every hut
block's own slots, as its own store. With **Purge mob drops** on, leave nothing there that you
want back (ColonyBucks are never destroyed). Example: a worn sword or a stack of tipped arrows
you put down in the town hall's racks beside your ColonyBucks is destroyed at the next pass,
and with Spore installed so are a Gas Mask, Milky Sacks and Spore's armour and weapons, new or
worn. Whatever that switch says, the warehouse still destroys its surplus, the warehouse's and
the Enchanter's Tower's shelves their spare enchanted books, and a builder's hut sends what its
build does not need to the warehouse. Your own inventory, your Ender Chest, and any chest,
barrel or shulker box you set down yourself are never searched, even inside a colony building:
keep your things there. To keep a colony's racks safe for your gear, its owner or an officer
can switch **Purge mob drops** off on its page (Workshops); the war's worn weapons and armour
then pile up in the racks instead.

What you give a citizen for his job becomes his kit: a pickaxe you hand a miner, a sword you
hand a knight, armour you put on a guard. A vanilla piece below what his hut and the colony's
smith allow is swapped and destroyed, enchantments and all. With **No graves** on (the
default), everything he carries but his ColonyBucks is lost with him when he dies.

## People

* **Citizen care** — a starving citizen is fed, from his own food first and the pantry's
  bread otherwise; once a restaurant serves, only one it failed or was too slow to reach. The
  sick are nursed back to health whether or not the hospital works, its healer first. With
  Spore installed, the infection cannot kill a citizen or a visitor: its damage never lands on
  them, it only slows them while it lasts. Spore's other effects (corrosion, bile, frostbite,
  madness, starvation) are wiped as soon as the nurse sees them. The Wither a modded mob
  inflicts is wiped too. In Progression Mode each cure is paid from the treasury, and an unpaid
  one waits.
* **Berry bushes** — bushes that poke walkers to death (the sweet berry bush, Ars Nouveau's
  sourceberry bush) are cleared off the colony's land outside its buildings and your protected
  zones, and at once wherever one kills a villager (`tweaks.hazardBushes`; not with the
  colony's **Terraform** switch off). Keep a berry patch you want inside a zone.
* **Recruits** — a tavern visitor is signed on the moment a bed is free, and his recruit cost
  is provided: it costs the colony nothing, in Progression Mode too. When the village falls 8
  or more beds below its cap, after a massacre or when new homes open at once, travellers are
  summoned to the tavern and signed on one at a time, so new recruits soon replace the fallen.
* **No graves, no mourning** — a citizen dies clean: no grave block, nothing scattered on the
  ground. His kit goes with him, and his replacement is equipped afresh (paid again in
  Progression Mode). Any ColonyBucks he carried go into the treasury, graves or not. The plan
  builds no Graveyard and the University skips the grave research. Nobody downs tools to mourn
  either, where MineColonies would send the dead man's family and housemates wandering the
  graveyard for a whole game day. Switch **No graves** or **No mourning** off (the colony's
  **Tweaks** section) for MineColonies' own way.
* **Wedged workers** — a miner, forester, farmer, fisher, composter, beekeeper, florist or
  courier whose work freezes for days is unstuck, and one who stays frozen is replaced by a
  fresh hire (where hiring is yours, he is kept and only reset). An empty restaurant, or a
  Courier's Hut with no courier left, is staffed from the jobless.
* **Stuck in water** — a citizen who falls into water whose banks stand a block or more above
  it cannot climb out. The colony lifts him onto the nearest dry bank: after 30 seconds where
  no bank is low enough, after a minute afloat without getting anywhere (not a guard on
  Follow within 30 blocks of the player he follows: he swims where that player swims) or two
  game-minutes afloat at all (not during a raid), at once if his air runs out. A rider goes with his mount,
  a colony mount left alone is lifted too where a bank beside the water holds it (out of such
  water, when its air runs out, or after two game-minutes afloat), one no bank holds is taken
  to a dry stall of its built Stable where one has room, its rider still seated, and an iron golem is moved
  after 30 seconds in any water. Nobody is moved inside a
  [protected zone](protected-zones.md), nor the fisherman at his fishing spot.
  Switches: **Job watch** (citizens and mounts) and **Village golem** (golems).
* **Stuck on land** — a citizen who is trying to get somewhere and cannot (walled in, in a
  pit, a ladder gone) is moved on to where he was going after about half a minute, where
  MineColonies alone leaves him standing for two to five minutes. Stuck again on the way to
  the same place within five game-minutes, he is left to MineColonies' own pace that time;
  trapped there again the next morning, he is moved after half a minute again. A rider
  thrown off his mount three times within a game-minute at one spot is sent back to his
  Stable, his mount to its stall.
  Whoever works standing in one place is never moved. Switch: **Job watch**.
* **Stuck in a hole** — a small dry hole in the village (beside a road, or within 8 blocks of
  a built building) where citizens get stuck twice within two game-days is filled with earth
  up to the ground round it. Everyone in it is moved out first: a citizen is sent home, a
  colony mount to its Stable's stall with its rider still seated, any other creature onto the
  fill. A player in it is never moved: the hole waits until he steps out. Left as it is, and
  named by `/colonyautopilot problems` at the third rescue: a hole in a protected zone, a
  building's grounds or site, a field or another colony's land, outside the village, with
  water in it, with a door, a gate, a fence or a wall in its side, with a creature in it that
  has no room on top of the fill, or opening into a cave or a ravine. Draw a protected zone over a cellar you want kept. Switch: **Terraform**.

## Research

* **The whole tree, unattended** — the University researches the militia's Huscarl and
  Marksman first, then whatever unlocks a hut, then guard and combat research, then the rest
  of the tree. Every gate stays: the University's level, the prerequisites, the buildings a
  research asks for, its item costs, and a researcher at work for the normal time. In
  Progression Mode each research is paid. Switches: **Auto research** and **Full tree**
  (Research).
* **Choices made for you** — MineColonies' research has five either-or forks, and each branch
  takes one tier-6 capstone: starting one locks the others out for good. So that nothing ever
  waits on you, the University takes **Parry** (not Dodge), **Avoid** (not Feint), **Quick
  Draw** (not Precise Shot), **Band Aid** (not Resistance) and **Nurture** (not More Books),
  and two capstones: **Guardian Angel II** (every citizen's health raised by 20 in all, ten
  hearts) and **Master Swordsman** (the knights' armour doubled). That makes a knight-heavy
  militia and a fast-growing town. The third capstone, **Madness!** (faster building), comes
  free at founding. The cavalry's two capstones, **Bucephalus** and **Master Barding**, share
  the combat branch with Master Swordsman: to have one researched, an operator lists it in
  Master Swordsman's place. Want another line? Before the University gets there, an operator
  changes `research.forks` or `research.capstones` (one list for every colony on the server),
  in the config file or with `/colonyautopilot set` (**op**); left blank, every such choice is
  yours.
* **Free at founding** — the moment a colony first runs, with no University needed (and free
  in Progression Mode too): every builder-speed research, Madness! included; the Barracks' and
  the Blacksmith's unlocks, so the blacksmith rises early (right after the Warehouse) and the
  guards reach iron gear sooner; the whole population chain past 25 citizens; and the druids'
  full-strength potions.
* **Cavalry** — Mounted Combat, which unlocks the Stable, is researched like any building's
  unlock, in every style: where your style has no Stable the village builds none itself, but
  once that research is done you can place one from a style that has it. The mount
  researches are taken up with the guards' research, but only in a village whose style pack
  ships a Stable, or that already has one (you can still research them at the University
  yourself). Each waits for its buildings: the barding researches up to Advanced Barding
  need the Stable and the Blacksmith at 3, 4 and 5, and the mounts' health researches also
  need a Hospital of level 2. A standing Hospital and a standing Blacksmith are upgraded
  early, ahead of the guard towers, until each has the level the colony's research needs
  (the Hospital 2 for the mounts' health and 3 for Vaccines, the Blacksmith up to 5 for the
  mount armour and some technology researches) or reaches the Town Hall's level, so the
  Blacksmith is usually raised with the Town Hall, one upgrade a level, and the barding
  researches can begin as the Stable rises.
  The two capstones above, Bucephalus and Master Barding, come only when an operator lists one.

## War

* **Guards and the militia, cavalry, iron golems, the creep guard, building repair (a hut the
  creep ate away is raised again), spreader culls, trap blocks as a wall for your citizens
  (MineTraps' wire among them) and, with K-Turrets installed, turret handling** — see
  [Fighting Back](fighting-back.md).
* **Hostile spawn cap** — vanilla hard-codes 70 monsters and the mod keeps that by default
  (`tweaks.hostileSpawnCap`, server-wide). Spore's raids spawn outside the cap anyway, so
  raising it only thickens the background night: an option for operators whose server can
  afford a denser horde.

## While you are away

* **Exploring the same dimension** — the village's chunks, and the fields its farmers and
  planters work with the way to them, stay loaded (**Keep village loaded**,
  `tweaks.keepVillageLoaded`, per colony), so the village keeps working while you are far off.
* **In another dimension** — the game itself stops moving every creature of a dimension that
  has no player in it, unless a chunk there is force-loaded. With Spore Inquisition installed
  (the Mutant-Spore-Apocalypse pack has it), the Overworld always has one: its scripts keep a
  forced area at world spawn, so an Overworld village keeps moving with you in the Nether.
  Without it, and in the other dimensions, the citizens stand still until somebody returns;
  `/forceload add` on one chunk is enough (in the Overworld Spore Inquisition clears such a
  chunk every five minutes, which does no harm there).
* **Nobody online** — the village's chunks still stay loaded, so it can be attacked (wherever
  creatures still move: the rule above holds for an empty server too). The colony itself
  rests: MineColonies runs a colony only while its owner or one of its officers is online, or
  a player stands in it. Until then nothing is built, nobody is hired, the fallen are not
  replaced and no request is served.

## Quality of life

* **No fire spread** — fire does not spread anywhere in the world (`tweaks.disableFireTick`,
  server-wide: the doFireTick gamerule off at every world start). A fire already lit burns
  nothing more, but stays lit until you put it out.
* **Work in the rain** — workers keep working when it rains (`tweaks.workersWorkInRain`,
  server-wide: MineColonies' own setting, turned on at every world start).
* **No food nagging** — citizens no longer complain about plain food or too little variety,
  and their morale is not docked for it (`tweaks.ignoreFoodQuality`, server-wide); they still
  eat.
* **Longer potions** — helpful potions last twice as long (`tweaks.potionDurationMultiplier`,
  2 by default, server-wide): an eight-minute potion lasts sixteen, whether you drink it,
  splash it or tip an arrow with it, the colony's own potions included. Harmful effects, and
  instant Healing and Harming, are unchanged. On a server the tooltip may still show the old
  time; you get the longer one.
* **Any tool, any block** — a worker may mine any block with any tool
  (`tweaks.ignoreToolTier`, server-wide), so a builder never freezes on a block his pickaxe is
  too weak for.
* **Pillager patrols** stay on; if they wedge on the hills and snipe your guards,
  `tweaks.disableVanillaPatrols` turns them off for the world.
* **Mob griefing** can be switched off for the whole world (`tweaks.disableMobGriefing`: off in
  the mod, on in the Mutant-Spore-Apocalypse pack). Creepers, ghasts and endermen then leave
  blocks alone and mobs stop trampling farmland; your citizens keep working. With Spore
  installed the creep still spreads, and of the spore horde only the Proto and the Hive Tumor
  still break out of walls (the Hive Tumor not inside a colony).
* With MineColonies_Tweaks installed, its hut pages (maximum
  stock, item lists, field sizes) need the colony's right to manage its huts, as
  MineColonies' own do: a Friend or a stranger cannot change another colony's.

## What the chat tells you

The autopilot talks to you in chat. Most lines are news; a few ask you to act.

* **Village news**, for everyone online — *The village has completed: Warehouse (now level 1)*
  for each new building, every fifth citizen, a field, a pond, a golem, a recruit, a research
  begun, land cleared or a ravine bridged, a decoration. To quiet one colony's news, switch
  off the **Milestones** section's switch on its page.
* **The alert horn**, in bold red — *Help your village defend!* when four citizens are killed
  within two minutes, and *Your village is infected!* when the creep guard finds the infection
  in town. Each sounds at most once every five minutes, whatever the Milestones switch says.
  Go home.
* **Snags** — *Colony Autopilot hit 2 new snag(s) that need a human's eyes*, at most once a
  game day: run
  <CommandLink command="/colonyautopilot problems" close={true}>/colonyautopilot problems</CommandLink>
  to see them.
* **Money**, for the owner and officers in Progression Mode — at most once a game day while
  work waits, *The colony treasury is empty — waiting for ColonyBucks: 3 requests and 1
  research.* The **Milestones** switch silences this reminder along with the news. After a
  MineColonies raid they hear what it paid into the treasury. See
  [ColonyBucks](colonybucks.md).
* **A fight is forecast for tonight** — MineColonies has a raid set for tonight, or with Spore
  Inquisition the corruption clock nears an ordeal: the warehouse keeps twice its combat
  potions and scrolls.
* **Countdowns** — with Spore Inquisition, *Days before rednight - 50 Days* every five days of
  the grace period ([The Spore Infection](the-infection.md)); with Mutationcraft, *Days before
  the mutants* every five days for the last 25, then *The mutants have come*
  ([The Mutants](mutants.md)).

<CommandLink command="/colonyautopilot chat off" close={true}>/colonyautopilot chat off</CommandLink>
mutes the colony's chatter for you alone: MineColonies' own announcements, the village news,
the money lines, the snags and the horn. The countdowns, the one-time notice a colony founded
with Progression Mode off gets when the mode comes on, and the autopilot's answers to your own
commands still come through. It also hides
MineColonies' answers to your clicks (a refused founding, "no permission"): if a click seems
to do nothing, turn the chat back on with
<CommandLink command="/colonyautopilot chat on" close={true}>/colonyautopilot chat on</CommandLink>.
