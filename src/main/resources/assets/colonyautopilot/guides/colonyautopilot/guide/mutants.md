---
navigation:
  title: The Mutants
  position: 65
---

# The Mutants — Mutationcraft

Where Mutationcraft is installed, a second plague comes for your colony, older
and hungrier than the fungus: the **mutagen**. It turns animals, villagers and
people into **assimilated** horrors that grow, breed parasites and evolve.
Mutationcraft has no book of its own; this chapter is it.

## When they come

Nothing from Mutationcraft spawns naturally until its day. The days below are
the defaults (the `arrivals.*` settings, so your server's may differ). The chat
counts the first arrival down every 5 days over its last 25, like the RedNight;
the later tiers come without a warning.

* **Day 30 — the common mutants**: assimilated villagers, pillagers,
  pigs, cows, sheep, horses, donkeys, wolves, foxes, bears, spiders, witches,
  piglins, wandering traders, humans, roamers and jockeys; the **parasites**
  (miters, necroptors, reductors); the rotten skeleton; and the hazmat teams
  that hunt them.
* **Day 50 — advanced**: the three **Human Stages**, the assimilated
  enderman and evoker, the carnivorae.
* **Day 70 — elite**: the assimilated creeper, the flayer, the
  parasitic ram and roller, the light and medium hooks.
* **Day 90 — bosses**: the corrosion queen, the human herder, the
  resenter, the parasitic shooter, the heavy hook.

Only natural spawns wait. A dungeon spawner can hold **miters** from the first
day (before the common mutants' day they stay miters), and spawn eggs and
`/summon` work on any day. The days hold even with the autopilot switched off.

If Born in Chaos is installed, its creatures are held back the same way, until
day 25 (`arrivals.bornInChaosDay`), and counted down in chat.

## Mutagen Sickness — the real threat

* A mutant's hit has a **40% chance** to give it, for five minutes. Killing a
  mutant can give it too.
* On its own it does nothing. But **be killed by a mutant while sick and you rise
  as an Assimilated Roamer** — a mutant bearing your name.
* An animal or villager that dies sick — or simply stays sick until it wears off —
  **turns** into its assimilated form. Watch the colony's cows, sheep and pigs
  after a mutant gets into the pens.
* **Milk does not cure it.** A Totem of Undying that saves you clears it.
* Colonists can catch it but never turn.

## They grow

* Most villagers, pillagers and animals a mutant kills — and zombies, witches,
  spiders and endermen — get up again as mutants.
* An **Assimilated Roamer** develops into a **Developed Roamer** after two
  minutes.
* **Human Stages** evolve every five minutes, 1 → 2 → 3. Their bites make you
  **Bleed**: worse while you move.
* A dying mutant can release **Miters**; a Miter becomes a **Necroptor** in two
  minutes. A Necroptor that kills five zombies becomes a **Reductor**, and an
  Assimilated Human that kills five skeletons becomes a **Flayer**.
* **Hooks** take root and summon miters and necroptors; light hooks grow into
  medium (five minutes) and heavy ones (ten more). A dying necroptor sometimes
  leaves a light hook behind, but not where four hooks already stand, and a
  hook with two dozen mutants around it waits until some of them fall (the
  defaults, `arrivals.hookLimit` and `arrivals.hookCrowdLimit`): a nest is a
  siege, not an endless one.
* By Mutationcraft's defaults a hook plants a putrid block under itself and
  putrid ground spreads. Walking on it now and then hurts you and gives you
  Mutagen Sickness (animals and villagers too), and a mutant on it is enraged.
  The Mutant-Spore-Apocalypse pack switches both off.
* Before a tier's day nothing grows or turns into it, and the hooks and parasites
  Mutationcraft spawns by itself never bring it early: a medium hook, for one,
  stays medium until the bosses' day.

## Know these

* **Assimilated Creeper** — explodes when it is nearly dead. With
  `tweaks.disableMobGriefing` on (off by default; the Mutant-Spore-Apocalypse
  pack switches it on) the blast breaks no blocks, but it still hurts.
* **Assimilated Enderman** — teleports to you, and brings other mutants with it.
* **Assimilated Evoker** — keeps up to six vexes around it.
* **Carnivorae** — keeps up to four miters around it.
* **Corrosion Queen** — her blows bring **Corrosion** (your armour slowly melts);
  her spells slow you and raise poisoned and blood spikes around you.
* **The Intoxicator** — a boss. It sleeps inside a **Rotten Skeleton**, a still,
  rotting figure: right-click one and it wakes. It belongs to no tier, so it can be
  woken from the common mutants' day, once rotten skeletons are about. Bring friends.

## Fighting back

* **Fire.** Burning mutants take **double damage**.
* **Flamethrower** — crafted from putrid hearts and brains, iron and scrap metal,
  or, rarely, taken from a fallen Hazmat Flamethrower. Sneak + right-click to load
  fuel; it overheats after five seconds of spraying.
* **Mutation Bane** — a sword enchantment (up to II) for extra damage to mutants.
  It cannot share a sword with Smite.
* **Totem of Immunity** — right-click for **20 seconds of invulnerability**,
  then a ten-minute cooldown. Netherite, gold, iron and an emerald.
* **Mutagen Serum** — right-click an assimilated villager, trader or animal with
  it to turn it back. Four necroptor membranes around a glass bottle; scientists
  carry it.
* Putrid flesh, hearts and brains from the fallen, scrap metal (looted, or two
  iron ingots and two iron nuggets) and netherite (one ingot for an infected
  weapon, two for a rusted metal armour piece) — JEI has every recipe.

## Friends and the colony

* **Hazmat teams** — guards, leaders, medics and flamethrowers hunt mutants, with
  scientists and a helicopter that drops more of them. They leave you alone unless
  you hit them.
* Your **guards** attack mutants on sight.
* The other monsters hate them: every hostile mob — zombies, skeletons, spiders,
  the Spore infected — attacks a mutant on sight, and mutants fight back. Mutants
  go for almost every living thing: animals, villagers and colonists too.
* Mutants pay [ColonyBucks](colonybucks.md) like any other hostile. The one
  exception is the Intoxicator: with 350 health it is one of the big mobs (150
  health or more) that never pay. The Mutant-Spore-Apocalypse pack also lists the
  rotten skeleton and the Corrosion Queen's spikes as never paying.
