---
navigation:
  title: Survival Kit
  position: 50
---

# Survival Kit — Cures, Immunity & Counter-Tech

Everything Fungal Infection: Spore and Spore Inquisition give *you* against the
infection; this chapter applies where they are installed. Most of it is ordinary
items you can look up in JEI. The Inquisition's cryo armour (CCM, CCC, CCL and
CCB, and their SubZero versions SCM, SCC, SCL and SCB) is renamed iron, diamond
or gas-mask gear, so look under the plain piece's recipes.

## Immunity: the Gas Mask

The single best piece of anti-infection gear in the game. A **gas mask**, any of
Spore's flesh helmets (**Living Helmet**, **Living Helmet MK2**, **Flesh
Helmet**) or the Inquisition's own **CCM** cryo mask, which is a gas mask
underneath, worn on your head makes the Mycelium debuff **bounce off entirely**:
no infected attack, cloud, burst or block can give it to you. The gas mask has no
armour and never wears out. Put one on before every expedition into creep.

Which helmets count is Spore's **Gas masks** setting in `spore-startup.toml`.
Spore Inquisition puts the gas mask and all three flesh helmets on it; with Spore
alone only the gas mask and the Living Helmet MK2 protect (the CCM counts as a
gas mask either way). The MK2 also shrugs off the first level of Madness.

The CCM also makes every infected within 32 blocks glow. Its upgrade, the
**SCM**, is a diamond helmet that does not block the infection but washes it off
you every moment you wear it, along with Madness, Corrosion, Blindness, Poison,
Wither and most other debuffs, and makes every infected within 64 blocks glow.

<Column fullWidth={true} alignItems="center">
<Recipe id="spore:gas_mask" fallbackText="Gas Mask: five black wool, two sponges and an iron ingot." />
<Recipe id="inqui:mask" fallbackText="" />
<Recipe id="inqui:ice_helmet" fallbackText="" />
</Column>

With Spore Inquisition you see three grids here: Spore's own mask (black wool,
sponges and an iron ingot), the Inquisition's cheaper one (black wool, coal,
string and an iron ingot) and the CCM.

## Cure: the Milky Sack (and plain milk)

Already infected? Eat a **Milky Sack**: it strips *every* harmful effect
(Mycelium, Madness, Corrosion and friends). An ordinary **milk bucket** also
clears the debuff. The one hard rule: **cure the living.** Whatever dies while
infected converts, so curing has to happen before the killing blow, not after.

<Column fullWidth={true} alignItems="center">
<Recipe id="spore:milky_sacks" fallbackText="Milky Sack: a milk bucket over an alveolic sack." />
</Column>

## Cold — the hard counter

Every infected creature, mini-boss and calamity is **freeze-weak**: frost hurts
them five times as much. Cold is not a gimmick here, it is the doctrine:

* **Snowballs actually work**: each hit stacks real frozen ticks on any infected
  target. A snow farm is a munitions plant.
* **Cryo Grenades**: thrown frost that freezes everything around where it lands,
  you excepted. What it does, and its recipe, are on
  [Cleansing Nukes](cleansing-nukes.md).
* The **Cryogenic Aspect** enchantment adds heavy freeze on hit; the frost
  **P.C.I.** weapon multiplies freeze damage against the horde.
* Frost is also how you kill a mound without it calling the Proto. In a cold
  biome, including land an SZC or a Frozen Kiss has frozen, every kill is quiet;
  anywhere else, see the [bestiary](bestiary.md) for the method.

## The CDU — Cryogenic Dispensing Unit

Spore's terrain-cleansing **machine**. Right-click it with an **Ice Canister** to
fuel it: one canister runs it for 10 minutes, and it takes the next only once it
has run dry (the same click while it runs shows how much fuel is left). Every 10
seconds it works a cube about 50 blocks across around itself (40 with Spore
alone):

* **freezes the creep's flesh**: biomass blocks, rooted biomass, fungal shells
  and membranes turn to Freezerburned Biomass, and bile crusts over. With Spore
  Inquisition it also freezes mycelium blocks, infested spawners, crusted bile,
  rooted mycelium and organite, and leaves infested stone and dirt alone. With
  Spore alone it turns infested stone, dirt, sand, gravel, deepslate and the
  rest back into clean blocks instead, and mycelium into dirt,
* **cuts spore foliage** and freezes remains,
* **deletes tendrils** (which no weapon can hurt) **and Scents** in range; the
  Inquisition's cryo chestplates clear tendrils too (see [the bestiary](bestiary.md)),
* stacks **frostbite** on every infected in range (if you wear Spore's flesh
  armour, it also switches that armour's bonus off while you stand there), and
  dusts the area with snow.

Keep it fed and one CDU at the edge of your base rolls the front line back. It
pairs well with the colony's own creep guard: the guard cleans colony chunks, the
CDU holds anywhere you plant it.

<Column fullWidth={true} alignItems="center">
<Recipe id="spore:cdu" fallbackText="C.D.U.: compound plates, an iron trapdoor, a dispenser and iron ingots." />
</Column>

## The flesh arsenal

Spore's own weapons are grown, not forged, and they eat:

* **Four biological guns**: the Acidic Assassin, Bile Blaster, Toxic Terroriser
  and the **Mycelial MistMaker** (a shotgun/chainsaw hybrid that reloads on the
  flesh of its enemies: right-click to deploy the saw, left-click to fire).
  Right-click a piece of food onto a gun in your inventory and it digests it
  into ammo. The Acidic Assassin, Bile Blaster and Toxic Terroriser also reload
  from Concentrated Acid, Bile and Toxin Vials, crafted from three glass bottles
  and a corrosive sack, crusted bile (a bucket of bile with Spore alone) or an
  alveolic sack. They hit hard, but Spore Inquisition cuts their durability to
  15–60 (Spore alone: 300), so carry spares.
* **The Frostwerfer** (Spore Inquisition) — the cold gun, and the one thing here
  that isn't flesh. Craft it from a CDU, a P.C.I., a circuit board and six
  Reinforced Compound Plates; hold right-click to spray a frost stream,
  shift-right-click with ice canisters to refuel. Its recipe is handed to you
  automatically.
* **A full infected melee line**: knives, greatswords, scythes, maces, rapiers
  and more, mutated and strengthened with the agents below.
* **E.G.O. gear** (Spore Inquisition): on Spore's **Surgery Table**, a flesh
  weapon or a piece of Living MK2 armour, with organs and reagents, becomes a
  named E.G.O. version with far better stats and powers of its own (the knife
  *Harvest*, for one, hits for 11 where a plain knife hits for 6). JEI lists
  every recipe under the Surgery Table.
* **TaCZ firearms and Ars Nouveau magic**, where installed, sit happily
  alongside all of it. With TaCZ, Colony Autopilot adds gun enchantments, two of
  them made for this war: **Fungicide** (far more damage to spore creatures) and
  **Cryo Rounds** (hits freeze the target in place); see
  [Fighting Back](fighting-back.md).

<Column fullWidth={true} alignItems="center">
<Recipe id="inqui:frostwerfer" fallbackText="The Frostwerfer needs Spore Inquisition." />
</Column>

## ⚠ Most syringes are NOT medicine

Spore's syringe family looks medical, but it is the **gear-upgrade system**. The
plain **Syringe** draws a Blood Sample out of a creature (and hurts it); the
**Refining**, **Hardening** and **Integrating Agents** and the **Blood Samples**
are used on flesh weapons and armour to mutate and strengthen them. Used on a
living thing instead, a Blood Sample gives a short buff (or poison) and then
**15 seconds of Mycelium Infection**, you included. Upgrade with them; never
"treat" anyone with them. (The **Mutating Syringe** is a creative-mode tool.)

**The five named ones are the exception.** Spore's *Surgery Table* brews them
from organs, the matching reagent and a syringe (Spore Inquisition's recipes):

* **Valetudo** raises your health to 14 hearts,
* **Pertinacia** gives you 8 armour and 3 toughness of your own,
* **Natatio** lets you swim faster, dig at full speed underwater and hold your
  breath longer,
* **Corpulentia** raises your attack damage and attack speed,
* **Celeritas** raises your attack damage and your reach. It sets the same
  attack damage as Corpulentia, so whichever of the two you take second adds
  only its attack speed or reach.

They are re-labelled Blood Samples, so look for the name, not for the word
"syringe". Use each on yourself once: the gain is for good (it survives death)
and a second dose adds nothing. Like every Blood Sample it also gives you its
short effect and 15 seconds of Mycelium, so inject with your gas mask on.
