---
navigation:
  title: Commands
  position: 60
---

# Commands

Click any command to run it. The read-only ones (`status`, `report`, `problems`,
`settings`, `zone wand|list`, `guide`, `treasury`), the `exchange` and the per-player
`chat` mute work for anyone (the treasury and the exchange with access to the colony's
huts); the ones that change the village for everyone — marked **op** below — need
operator, and so do the `/function` and `/scoreboard` commands (in single-player:
cheats on).

## Get the items directly

* <CommandLink command="/function inqui:craft_ice_nuke">/function inqui:craft_ice_nuke</CommandLink> — the SZC ice nuke
* The Solar Punch fire nuke has no give-command in Spore Inquisition 4.5 — craft it (iron blocks, blaze rods, slime blocks, TNT); the grid is on [Cleansing Nukes](cleansing-nukes.md).
* <CommandLink command="/give @s sporeinquisition:cryogenic_grenade 1">/give @s sporeinquisition:cryogenic_grenade 1</CommandLink> — the Cryo Grenade (the old Ice Bomb)
* <CommandLink command="/function inqui:gifts/gift1">/function inqui:gifts/gift1</CommandLink> — Magnum Ientaculum (offering I)
* <CommandLink command="/function inqui:gifts/gift2">/function inqui:gifts/gift2</CommandLink> — Cena Suprema (offering II)
* <CommandLink command="/function inqui:gifts/gift3">/function inqui:gifts/gift3</CommandLink> — Desertum Ultimum (offering III)
* <CommandLink command="/function inqui:0_config">/function inqui:0_config</CommandLink> — Spore Inquisition's admin menu
  (offerings, corruption slider, summon, end)

## Autopilot

* <CommandLink command="/colonyautopilot status">/colonyautopilot status</CommandLink> — what the autopilot is doing
* <CommandLink command="/colonyautopilot report">/colonyautopilot report</CommandLink> — the full colony debrief
  (population, plan, work orders, guards, research, war, problems)
* <CommandLink command="/colonyautopilot problems">/colonyautopilot problems</CommandLink> — anything it is stuck on; a spot where citizens keep getting stuck carries a clickable **[go there]** that takes you to it
* <CommandLink command="/colonyautopilot on">/colonyautopilot on</CommandLink> /
  <CommandLink command="/colonyautopilot off">/colonyautopilot off</CommandLink> — all automation in this world at one switch (**op**)
* <CommandLink command="/colonyautopilot expansion off">/colonyautopilot expansion off</CommandLink> — stop NEW buildings
  (upgrades, repairs and defense continue); `expansion on` resumes (**op**). The server default: a
  colony with its own `growth.expansion` keeps it
* <CommandLink command="/colonyautopilot golems off">/colonyautopilot golems off</CommandLink> — no more iron golems,
  and the standing ones leave; `golems on` brings them back (**op**). The server default: a colony
  with its own `tweaks.villageGolem` keeps its golems
* <CommandLink command="/colonyautopilot settings">/colonyautopilot settings</CommandLink> — every setting, by section
  (`settings growth` lists one section, one short line per key; `settings growth.terraform` one
  key with its description); ops change the server default with `set <section.key> <value>`
  (**op**). A colony that set a key on its page keeps its own value: the reply names such
  colonies, and the page's **Reset** (or `colony <id> reset <key>`) hands one back
* <CommandLink command="/colonyautopilot menu">/colonyautopilot menu</CommandLink> — the autopilot page
  for the colony you stand in (or the nearest): every switch and number for THAT colony (also in the town hall:
  Settings → **Autopilot settings**). Anyone with hut access may look; the colony's owner and
  officers may change
* <CommandLink command="/colonyautopilot colony settings">/colonyautopilot colony settings</CommandLink> — one
  colony's own settings from chat: `colony [<id>] settings [section]`, `colony [<id>] set <key> <value>`,
  `colony [<id>] reset <key>` (**op**). Without an id it is the colony you stand in; colony ids count
  per dimension, so give the id of a colony in another dimension from inside that dimension
* <CommandLink command="/colonyautopilot colony roads">/colonyautopilot colony roads</CommandLink> — lay every
  road of the colony again, after you reshape the ground or remove a building; `colony roads lift`
  takes them all up, and none is laid until `roads` or the next boot (**op**). Without op, the
  colony's owner and officers have **Re-lay roads** on the town hall's **Exchange** tab (once a
  game-minute, and not while an operator's `roads lift` holds)
* <CommandLink command="/colonyautopilot exchange">/colonyautopilot exchange</CommandLink> — trade diamonds,
  gold, ancient debris and netherite for ColonyBucks on the trading screen,
  in the colony you stand in (while it is in Progression Mode; also **Trade** on the town
  hall's **Exchange** tab)
* <CommandLink command="/colonyautopilot treasury">/colonyautopilot treasury</CommandLink> — the colony's
  ColonyBucks: the balance in whole bucks, what waits for money, what it saves for (and its price with the goods
  stocked), today's and yesterday's ledger, where to deposit (anyone with access to its huts);
  `colony [<id>] treasury add <n>` puts bucks in (**op**)
* <CommandLink command="/colonyautopilot chat off">/colonyautopilot chat off</CommandLink> — mute colony chatter in
  your chat only; `chat on` restores it
* <CommandLink command="/colonyautopilot zone wand">/colonyautopilot zone wand</CommandLink> — another Zone Marker;
  also `zone list` and `zone clear` (**op**)
* <CommandLink command="/colonyautopilot guide">/colonyautopilot guide</CommandLink> — another copy of this guide

## Infection admin

* <CommandLink command="/function inqui:summon_primordial_mound">/function inqui:summon_primordial_mound</CommandLink> —
  summon the RedNight now (skips the grace period)
* <CommandLink command="/scoreboard players set !finale finalitas -1">/scoreboard players set !finale finalitas -1</CommandLink> —
  END the infection outright
* <CommandLink command="/scoreboard players set !finale proto 0">/scoreboard players set !finale proto 0</CommandLink> —
  reset world corruption to 0

**Remember:** if spore refuses to appear in a fresh world, it is almost always the
addon's **grace period** doing its job — `gracePeriodDays` in
`colonyautopilot-server.toml`, set at world creation. The RedNight falls when
that grace ends, so it needs **1 or more**; **0 means off** — spore spawns from
day one and no RedNight ever comes.
