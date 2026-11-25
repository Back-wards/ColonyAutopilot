---
navigation:
  title: Offerings & the Finale
  position: 60
---

# Offerings & the Finale

How the apocalypse ends where Spore Inquisition is installed, and what it costs
your town.

By Spore Inquisition's default (`automaticFinalitas = true` in
`spore-inquisition-server.toml`) it ends by itself: corruption climbs to 100%,
the Final Bell tolls when the half-hourly corruption check first finds it there,
and half an hour after that the finale starts. Switched off (the
Mutant-Spore-Apocalypse pack ships it off), the apocalypse **never ends on its
own**: corruption caps at 99% and waits for you, and you start the finale with
an offering to the RedNight. There are three, from small to large, and the one
that brings corruption to the cap begins it. Each offering **raises**
corruption and summons an escort of calamity bosses: risk and reward climb
together. Colony Autopilot never starts the finale for you.

## Read this before the last offering

**Starting the finale kills the town.** About 72 seconds after it begins, Spore
Inquisition throws every creature and loose object in the loaded world into the
void, in every dimension: all of them but the players and the RedNight's own
mound. It does so however the finale starts, by offering or by itself. Every
citizen, visitor, guard, golem, farm animal and pet dies there, and item frames,
armour stands, minecarts and dropped items are gone. Colony Autopilot keeps its
villages loaded, so being far away saves nothing, and on a server one player's
last offering does it to every colony, not only his own. The buildings stay, new
settlers move in and the golems are forged again, but the citizens you had, with
their names and skills, do not come back.

The purge happens while the lore plays, some fifteen seconds before you are taken into
the arena: if you stand in the town you watch it empty, and if you are away you
find it when you return after a win.

Colony Autopilot does not stop this: it is the other mod's ending. So decide
first whether the town is yours to spend. With `automaticFinalitas` off, the
offering that begins the finale is **the one that brings corruption to the cap,
not only one given at 99%**; with it on, an offering that lifts corruption to
100% rings the Final Bell, and the finale follows within the hour (the numbers
are under [The Finale](offerings.md#the-finale)).

## The ritual

Drop the offering (default Q) so that it lands on a **biomass block**: any Block
of Biomass (plain, sickened, gastric or calcified), rooted biomass or a fungal
shell will do, wherever it lies, and the offering is taken within five seconds.
No mound has to be near. Do not carry the heads loose across infected ground:
one dropped by accident on biomass is an offering made.

The gifts go to whoever stands nearest the offering, and with them comes
**Symbiosis** (30 minutes for a Magnum, an hour for a Cena, and with no timer
after a Desertum, though dying or drinking milk still ends it): membranes let
you through and the Proto, Hive Tumors and Outpost Watchers stop seeing you,
but you get hungry faster. The escort is summoned at the RedNight's mound at
world spawn, not where you stand: if your town is near spawn, that is where the
bosses land.

The RedNight's gifts carry no ColonyBucks. The amounts below are Spore
Inquisition 5's defaults (`offering1Amount` to `offering3Amount` in
`spore-inquisition-server.toml`), in points out of the meter's 400. A config
file first written by Spore Inquisition 4.5 keeps 4.5's 25, 50 and 100: the
value in your own file is the one that applies.

## Magnum Ientaculum (+10 points, 2.5%)

Summons a **Sieger**, and gives the nearest player a haul of organs and gear.

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:gift1" fallbackText="This offering needs Spore Inquisition." />
</Column>

## Cena Suprema (+20 points, 5%)

Summons a **Sieger + GazenBreacher**, and gives a bigger haul.

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:gift2" fallbackText="This offering needs Spore Inquisition." />
</Column>

## Desertum ultimum (+40 points, 10%)

Summons a **Sieger + GazenBreacher + Hindenburg + Howitzer**, and gives the
nearest player a full Living armour set, a greatsword, an infected bow, a shield
and a MistMaker, all enchanted, plus the Red Fixer's loot and Symbiosis with no
timer (until you die or drink milk).

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:gift3" fallbackText="This offering needs Spore Inquisition." />
</Column>

## The Finale

With `automaticFinalitas` off, the finale begins with **the offering that brings
corruption to the cap** (99%, 396 of the 400 points), not only with one given at
99%. With the default amounts that is a Magnum from 96.5%, a Cena from 94% and a
Desertum from 89% (with 4.5's 25, 50 and 100: 92.75%, 86.5% and 74%): past
that line a Desertum "for the best loot"
starts the finale on the spot, and with it the purge described at the top of
this page. With `automaticFinalitas` on, an offering only adds its points, and
the Final Bell starts the finale as described at the top.

After the lore plays out (~90 seconds) you are teleported into the arena
dimension, far out near 5000/-5000, inside a shell of brain remnants you dig
your way out of; your respawn point is set there. **It starts empty.** Four
Guardians stand between you and the end — Abundantia (Famine), Vis (Feebleness),
Spes (Despair) and Amor (Hatred). The first wakes within 20 minutes and the chat
names the spot ("You can feel a strange presence in 0/500"), some 7,000 blocks
away: bring a way to travel. Kill a Guardian and it drops a glowing **eye key**:
**right-click the eye**, or nothing else happens — using it is what wakes the
next Guardian. Use the fourth eye (Amor) and the RedNight's tower rises, with
the **"Nunny"** — the RedNight's own Proto — inside. **Kill it and reach the
exit**: win, and the infection ends. Budget a long session, and expect the game
to hang for half a minute or more when the tower and its mountain appear: let
it finish.

Read the Proto's entry in [the bestiary](bestiary.md) before you go: it cuts
every hit above 20 down to 20, and small hits can make it plant pods of
mini-bosses. Bring damage you can keep up; a few heavy blows draw fewer adds
than automatic fire.

Impatient? The finale and corruption can also be driven directly by
[command](commands.md).
