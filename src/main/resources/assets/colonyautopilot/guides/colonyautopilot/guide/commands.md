---
navigation:
  title: Commands
  position: 80
---

# Commands

Click any command to run it: the book closes and the answer appears in chat.

Anyone may run the read-only ones (`status`, `report`, `problems`, `settings`, `zone list`,
`treasury`), the hand-outs (`zone wand`, `guide`), `menu`, the `exchange` and the per-player
`chat` mute; the treasury, the menu and the exchange need access to that colony's huts
(Friend rank and up). The ones that change the village for everyone, marked **op** below, need
operator, or in single-player the host of the world (cheats on or off). Operator commands that
switch or reshape something are written out instead of linked, so a stray click cannot run them:
type them in chat. The `/give`, `/function` and `/scoreboard` commands need operator too (in
single-player: cheats on).

## Autopilot

* <CommandLink command="/colonyautopilot status" close={true}>/colonyautopilot status</CommandLink> — what the autopilot is doing
* <CommandLink command="/colonyautopilot report" close={true}>/colonyautopilot report</CommandLink> — the full colony debrief
  (population, plan, work orders, guards, research, war, problems). Both take a colony's
  number to print that colony only (`status 3`); each colony's header shows its number
* <CommandLink command="/colonyautopilot problems" close={true}>/colonyautopilot problems</CommandLink> — anything it is stuck on; a reported
  stuck spot carries a clickable **[go there]** that takes you to it (Friend rank
  and up in that colony, or an operator). `/colonyautopilot problems off` stops keeping the list
  and clears it; `problems on` starts it again (**op**)
* `/colonyautopilot on` / `/colonyautopilot off` — all automation in this world at one switch
  (**op**). **Removing the mod?** Run `/colonyautopilot off` first: it hands every guard post the
  autopilot holds back to MineColonies' own hiring, and gives the world back the gamerules it
  changed: fire spread, the spawn-chunk radius (with Spore Inquisition), and mob griefing or
  pillager patrols where its settings turn those off. Without it those posts stay shut and those
  gamerules stay as the mod set them
* `/colonyautopilot expansion off` — stop ALL new buildings, guard towers and the Barracks
  included (upgrades, repairs and outfitting go on); `expansion on` resumes (**op**). It sets the
  server default: a colony with its own `growth.expansion` keeps it
* `/colonyautopilot golems off` — the autopilot forges no more iron golems, and its own golems in
  loaded ground leave (golems you built stay; one of its golems out in unloaded ground stays until
  you run this again near it); `golems on` brings them back (**op**). It sets the server
  default: a colony with its own `tweaks.villageGolem` keeps its golems
* <CommandLink command="/colonyautopilot settings" close={true}>/colonyautopilot settings</CommandLink> — the config's sections; `settings growth`
  lists one section, one line per key with its value, range and default;
  `settings growth.terraform` shows one key and what it does. Ops change the server default with
  `/colonyautopilot set <section.key> <value>` (**op**; lists are edited in
  `config/colonyautopilot-server.toml`). A colony that set a key on its own page keeps its own
  value: the reply names such colonies, and the page's **Reset** (or `colony <id> reset <key>`)
  hands one back
* <CommandLink command="/colonyautopilot menu" close={true}>/colonyautopilot menu</CommandLink> — the autopilot page for the colony you stand
  in (or the nearest): every switch and number for THAT colony (also in the town hall:
  **Exchange** tab → **Autopilot settings**). Anyone with hut access may look; the colony's owner
  and officers may change its per-colony rows, and an operator or the single-player host its
  global ones. What the rows do: [Autopilot Systems](autopilot-features.md)
* <CommandLink command="/colonyautopilot colony settings" close={true}>/colonyautopilot colony settings</CommandLink> — one colony's own
  settings from chat (**op**, the listing too; without op, open `menu` above):
  `colony [<id>] settings [section]`, `colony [<id>] set <key> <value>`,
  `colony [<id>] reset <key>`. Without an id it is the colony you stand in. Colony ids
  count per dimension: an id means the colony in your own dimension first, and you only have to
  run it from inside the right dimension when two other dimensions share that id
* `/colonyautopilot colony roads` — lay every road of the colony again, after you reshape the
  ground or remove a building; `colony roads lift` takes them all up, and none is laid until
  `roads` or the next boot (**op**). Without op, the colony's owner and officers have **Re-lay
  roads** on the town hall's **Exchange** tab (once a game-minute, and not while an operator's
  `roads lift` holds)
* <CommandLink command="/colonyautopilot exchange" close={true}>/colonyautopilot exchange</CommandLink> — trade diamonds, gold, ancient
  debris and netherite for [ColonyBucks](colonybucks.md) on a trading screen, in the colony you
  stand in (while it is in Progression Mode and the autopilot and `economy.exchange` are on; also
  **Trade** on the town hall's **Exchange** tab)
* <CommandLink command="/colonyautopilot treasury" close={true}>/colonyautopilot treasury</CommandLink> — the colony's ColonyBucks: the
  balance in whole bucks, what waits for money, what it saves for (and its price with the goods
  stocked), today's and yesterday's ledger, and where to deposit (anyone with access to its
  huts). `treasury 3` reads colony 3 without standing in it; `colony [<id>] treasury add <n>`
  puts bucks in (**op**)
* <CommandLink command="/colonyautopilot chat off" close={true}>/colonyautopilot chat off</CommandLink> — mute colony chatter in your
  chat only, the alert horn included; the countdowns and the autopilot's answers to your own
  commands still come through (the full list: [Autopilot Systems](autopilot-features.md)).
  MineColonies' answers to your own clicks and commands are muted too, so if a click seems to do
  nothing, turn the chat back on: <CommandLink command="/colonyautopilot chat on" close={true}>/colonyautopilot chat on</CommandLink>
* <CommandLink command="/colonyautopilot zone wand" close={true}>/colonyautopilot zone wand</CommandLink> — another Zone Marker;
  `zone list` lists the protected zones of the colony you stand in, and `zone clear` deletes
  them all (**op**)
* <CommandLink command="/colonyautopilot guide" close={true}>/colonyautopilot guide</CommandLink> — another copy of this guide

## Get the items directly

With Spore Inquisition installed:

* The three nukes (SZC, Solar Punch, Frozen Kiss) have no give-command in Spore Inquisition 5:
  craft them from the grids on [Cleansing Nukes](cleansing-nukes.md). (4.5:
  `/function inqui:craft_ice_nuke` hands you the SZC.)
* <CommandLink command="/give @s sporeinquisition:cryogenic_grenade 1" close={true}>/give @s sporeinquisition:cryogenic_grenade 1</CommandLink> — the Cryo Grenade (the old Ice Bomb)
* **Before you use the offerings below:** the offering that brings corruption to its cap
  starts the finale, and the finale throws every citizen, golem, animal and pet of every
  loaded colony into the void. Read the top of [Offerings & the Finale](offerings.md) first.
* <CommandLink command="/function inqui:gifts/gift1" close={true}>/function inqui:gifts/gift1</CommandLink> — Magnum Ientaculum (offering I)
* <CommandLink command="/function inqui:gifts/gift2" close={true}>/function inqui:gifts/gift2</CommandLink> — Cena Suprema (offering II)
* <CommandLink command="/function inqui:gifts/gift3" close={true}>/function inqui:gifts/gift3</CommandLink> — Desertum ultimum (offering III)

## Infection admin

These need Spore Inquisition; the grace setting works with Spore alone too.

* `/colonyautopilot set spores.gracePeriodDays 1` — end the grace period now (**op**); type 1,
  not 0, or no RedNight comes. When it falls: [The quiet years](the-infection.md#the-quiet-years).
* **Don't** summon the RedNight with `/function inqui:summon_primordial_mound` while the grace
  runs: what its mound spawns is still held, and a second RedNight comes when the grace ends.
* `/scoreboard players set !finale finalitas -1` — END the infection outright
* `/scoreboard players set !finale proto 0` — reset world corruption to 0
* <CommandLink command="/function inqui:0_config" close={true}>/function inqui:0_config</CommandLink> — Spore Inquisition's admin menu,
  printed in chat to everyone online: the corruption slider (0–80%), the ordeals, summoning the
  RedNight (the same mound as above: not during the grace) or The Fixer, ending or restarting
  the infection, and the three offerings (its "Tier 3" is offering I, Magnum Ientaculum)

**Remember:** if spore refuses to appear in a new world, that is almost always the addon's grace
period doing its job (`spores.gracePeriodDays`, counted from the world's first tick): see
[The Spore Infection](the-infection.md).
