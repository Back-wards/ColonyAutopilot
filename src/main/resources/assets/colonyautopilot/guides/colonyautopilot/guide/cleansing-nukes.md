---
navigation:
  title: Cleansing Nukes
  position: 55
---

# Cleansing Nukes

The craftable counter-weapons of Spore Inquisition; this chapter applies where it
is installed. The three nukes are renamed player heads: they have no entry of
their own in JEI's item list or the creative search, so their recipes live here.
**To use a nuke: drop it on the ground (default Q) and get well clear; it goes off
about a minute later (the Frozen Kiss after 30 seconds).** The numbers on this
page are Spore Inquisition 5's. Read SOLAR PUNCH's entry before you use that one:
it kills you within 50 blocks, its fire can still kill you out to about 110, and
it is not safe near the town. The Cryo Grenade is a normal item (it is in JEI,
under Spore Inquisition) and is **thrown** with right-click.

## SZC — the Ice Nuke

The weapon that cuts world corruption the most: **30 points, 7.5%** per use
(4.5: 75 points, about 19%). Short of an operator's command or the finale
itself, only the three nukes ever lower corruption; everything else only pushes
it up.

A minute after you drop it, it freezes every fungus creature within ~500 blocks
solid, turns the fungal blocks across the same range to blue ice and makes the
land there an Ice Spikes biome for good. The infected ground counts as fungal
blocks too, so creep-covered dirt and stone become blue ice, not earth, and
nothing in Colony Autopilot turns that ice back.

It hits you too. Everyone within 500 blocks is blinded for ten seconds, a
thunderstorm rolls in, and **the moment it goes off every dropped item in the
loaded world is deleted**: pick up your loot first, and never drop a second nuke
before the first has fired.

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:ice_nuke" fallbackText="The SZC needs Spore Inquisition." />
</Column>

**Note:** the freeze is enormous, and the game can stall for several minutes
while it works. Let it finish.

## FROZEN KISS — the small ice nuke (new in Spore Inquisition 5)

The SZC in miniature. Drop it and step back: 30 seconds later it freezes every
fungus creature within 100 blocks for about a minute, turns the fungal blocks of
a 200×200 area to packed ice (creep-covered ground included), makes that area
snowy taiga for good and takes **6 points (1.5%)** off world corruption. Players
within 125 blocks are blinded for a few seconds.

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:ice_kiss" fallbackText="The Frozen Kiss needs Spore Inquisition 5 or newer." />
</Column>

## SOLAR PUNCH — the Fire Nuke

A bomb, not a cleanser. When it goes off, everything alive within 50 blocks of it
on every side takes 200 damage that armour does not soften: it kills you
outright, and every animal, pet and golem with you. A citizen loses only a fifth
of his health to the blast, because MineColonies caps any single hit at that,
but the fire of the second blast (below) hits him again every half second:
citizens within about 60 blocks still die.

**Be more than 110 blocks away, and never use it near the town.** Where mob
griefing is on, it also digs a crater 100 blocks across through stone, planks
and iron blocks alike and sets it alight. Colony Autopilot leaves mob griefing
on unless `tweaks.disableMobGriefing` is set (the Mutant-Spore-Apocalypse pack
sets it, so there is no crater there). Colony Autopilot's guard against spore
demolition does not cover this bomb: where there is a crater, it cuts through a
village like anywhere else.

Either way, a second blast starts 50 blocks underground and swells for 30
seconds. It reaches the surface after about 26 seconds, and in its last seconds
everything alive within about 110 blocks is set burning, takes fire damage every
half second and is weakened for a minute. Patches of open ground within about
95 blocks catch fire or fill with Spore's acid, and the water inside the blast
boils away. With Colony Autopilot's fire tweak on (`tweaks.disableFireTick`, its
default), those fires neither spread nor burn out: put them out yourself.

It also clears the bile within 50 blocks and trims corruption slightly
(2 points, 0.5%).

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:nuke" fallbackText="The Solar Punch needs Spore Inquisition." />
</Column>

## Cryo Grenade — the old Ice Bomb

**Right-click to throw it.** Where it lands, it freezes **every living thing
within 25 blocks except you**: infected, citizens, friends and livestock alike.
The frost slows them for 19 seconds and does about 9 damage through armour (a
chicken or a rabbit dies of it; leather armour stops the damage). Infected caught
in it take five times that frost damage, and the common ones also take Frostbite
damage for 45 seconds (tougher kinds, from evolved infected and mounds up to
calamities, shrug the Frostbite off).

It also turns the fungal blocks within 25 blocks to Freezerburned Biomass and
clears the spore foliage. Before it does, it melts away any Freezerburned Biomass
already in its reach, so a second grenade on the same spot leaves holes in the
ground.

So throw it out at the horde, not onto your own wall or pens. It does not change
corruption: it is a tactical tool for breaking a push, or for starting the frost
on a mound before you kill it (see [the bestiary](bestiary.md) for how to finish
it quietly).

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:ice_bomb" fallbackText="The Cryo Grenade needs Spore Inquisition." />
</Column>

Prefer commands? The Cryo Grenade can be handed over directly — see
[Commands](commands.md). The three nukes have no give-command in Spore
Inquisition 5; craft them from the grids above.
