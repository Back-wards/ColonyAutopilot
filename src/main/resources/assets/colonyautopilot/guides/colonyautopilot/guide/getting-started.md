---
navigation:
  title: Getting Started
  position: 10
---

# Getting Started

On your first join of a world you receive the **founder's satchel**:

* a **Town Hall** block (to the world's first player only, and a lost one is not
  replaced: see *Playing together* below) and the **build tool**. Place the Town Hall
  where the village should grow; everything else follows from it
* the **Zone Marker** wand, while the server has protected zones on: mark ground the
  autopilot must never build on (see [Protected Zones](protected-zones.md))
* this **Field Guide**
* the guide books of the other mods you have, as loose items: by default Ars
  Nouveau's **Tattered Tome**, the AE2 guide and, with Patchouli, the Mystical
  Agriculture guide, each only where its mod is installed. With Akashic Tome
  installed you also get a blank tome: put it in a crafting grid together with a
  book to bind the book into it.
  Mods with no manual of their own are covered in [The Other Mods](mod-primer.md)

Played an older version of the addon? Read *Updating from an older version* at the
end of this page before you start.

## Pick your style

You choose the village's style in the build tool when you place the Town Hall, and
the colony builds every hut from it. (The Town Hall's window can switch the style
later; buildings placed after that follow the new one.) What the choice changes:

* **Missing huts.** Apart from the Stable (next point), most styles have every hut
  the colony builds. Two do not: Lost Mesa City has no Chef's Kitchen, and Stalactite
  Caves has no Chef's Kitchen, Alchemist Tower, Mystical Site or Nether Mine (nor a
  Graveyard, which is built only with graves switched on). The colony skips a hut its
  style lacks, so that job never exists in the village.
* **Cavalry.** Only eleven of the 23 styles have a Stable the colony can build, and only they
  get one built for them: Minecolonies Original, Ancient Athens, Urban Birch, Shire, Urban
  Savanna, Space Wars, Nordic Spruce, Colonial, Fortress, Jungle Treehouse and Desert
  Oasis (on older MineColonies, such as 1.1.1403, Desert Oasis' Stable cannot be
  placed). In any other style the Stable's research is still done, so you can place a
  Stable from one of these styles yourself: see [Fighting Back](fighting-back.md).
* **The fullest village.** For every hut apart from the Stable, and the widest choice
  of the small ornaments the builders raise round the town hall once the plan is done
  (up to four), pick Pagoda, Minecolonies Original, Ancient Athens, Caledonia, Medieval
  Oak or Cavern. Of those, only Minecolonies Original and Ancient Athens also have a
  Stable.
* **Fixed layouts.** Some styles are drawn round fixed building spots, Fortress above
  all. They look best when you place their huts yourself (step 1 below) and let the
  colony build, staff and upgrade them.

## Your first days

1. **Place the Town Hall.** The autopilot places, builds and upgrades every other
   building on its own, following a growth plan: three builders' huts first, then
   the first guard post (the Barracks) while the Town Hall itself is built, then
   tavern, homes, a guard tower, the farm, warehouse and blacksmith, a courier's hut
   and the dining hall, the university, a stable for cavalry where your style has
   one, and the rest. Want a building sooner, or in a spot of your choosing? Place
   its hut yourself with the build tool. The colony adopts it: it builds it, upgrades
   it, staffs it and lays it a road like its own.
2. **Keep the treasury fed.** Citizens are hired, fed, healed, equipped and
   researched automatically, and building materials trickle into the builders' huts
   on their own. But **Progression Mode is on by default**: the colony pays for the
   building materials, deliveries, gear, cures and research the autopilot provides,
   in **ColonyBucks** from its treasury, and a new colony is not told so in chat.
   Food, planning, placing, roads and recruits stay free. Bucks come from monsters,
   from loot chests and from the town hall's **Exchange** tab, which turns diamonds,
   gold, ancient debris and netherite into bucks. Sneak-right-click the Town Hall
   block holding them to pay them in. The colony's owner and officers can switch the
   mode off on its Autopilot page
   (<CommandLink command="/colonyautopilot menu" close={true}>/colonyautopilot menu</CommandLink>)
   to have everything supplied free, unless the server holds it on. Everything about the money
   is in [ColonyBucks](colonybucks.md).
3. **Mark your ground.** If you build a base near the colony, surround it with
   Zone Marker zones early: the autopilot refuses to build inside them, and a plot it
   levels is cleared of whatever stands there, your own blocks included. With
   MineTraps installed, its wire and traps are a wall to your colonists and to nobody
   else: wire the border if you like, but leave a gap, a door or a fence gate in
   every line (see [Fighting Back](fighting-back.md#what-you-bring)).
4. **Mind the calendar.** With Fungal Infection: Spore installed the world starts at
   peace: for the first **22 game-days** (about seven hours of play) no spore
   creature appears on its own. With Spore
   Inquisition installed the chat counts the days down, and when they end the
   **RedNight** falls once over world spawn and the infection begins; it waits while
   the world is on Peaceful or nobody is online. The rest is under
   [The quiet years](the-infection.md#the-quiet-years). The colony grows and
   researches on its own, and a colony of 25 citizens or more with its combat research
   done tends to survive the RedNight ([Fighting Back](fighting-back.md)). Spend the
   quiet gearing yourself: gas mask, cold, guns.
5. **The mutants keep a calendar too.** With Mutationcraft installed its creatures do
   not spawn naturally until their day: by default **day 30** for the first mutants,
   parasites and hazmat teams, **day 50** for advanced mutants, **day 70** for elite
   ones and **day 90** for the bosses. The chat counts the first arrival down for its
   last 25 days; the later tiers come unannounced. Only natural spawns wait. More in
   [The Mutants](mutants.md). (Born in Chaos, if installed, waits until day 25 the
   same way.)

## While you wait

Explore. The world out there has dungeons, structure bosses and loot, and the
village's ground stays loaded while you are gone. With Spore Inquisition installed
an Overworld village keeps moving even while nobody is in the Overworld, so it can
be attacked while you are in the Nether or offline. What keeps working and what
waits is under [While you are away](autopilot-features.md#while-you-are-away).
Night mobs **will** kill the odd citizen; that is the intended pressure, and the
colony replaces its losses.

## Playing together

Only the first player to join a world gets the Town Hall. Everyone who joins later
gets the rest of the satchel and a line in chat: ask the founder for colony
permissions (Town Hall > Permissions > promote to **Officer**). What each MineColonies
rank may do with the autopilot, by default (the owner can change a rank's rights on
that same Permissions tab):

* **Friend**: open the colony's huts, so look at its Autopilot page, read its
  treasury, trade at its Exchange, and use the **[go there]** link on a stuck spot.
* **Officer** (and the owner): all of that, plus change the colony's own settings
  (Progression Mode among them), mark and remove protected zones, press **Re-lay
  roads** on the Exchange tab, and place huts of their own. The owner and the
  officers online also hear the colony's money news in chat.
* **Operators** (in single-player, the world's host): change the server-wide
  settings and run the commands marked **op** on the [Commands](commands.md) page.

The colony builds and hires only while its owner or an officer is online, or a player
is in the village. Example: make the friend who plays while you are offline an
Officer, and the village keeps growing while they play.

## When something looks wrong

* **Ask the colony first.**
  <CommandLink command="/colonyautopilot problems" close={true}>/colonyautopilot problems</CommandLink>
  lists what needs your eyes, and most entries say what to do. At most once a game
  day the chat also tells everyone online how many new problems turned up.
* **Nothing is rising?** A building that is not going up is usually waiting, not
  stuck: for ColonyBucks (the owner and officers hear "The colony treasury is empty"
  in chat, and
  <CommandLink command="/colonyautopilot treasury" close={true}>/colonyautopilot treasury</CommandLink>
  lists what waits), or for a free builder (in Progression Mode each built Builder's
  Hut takes one construction job at a time). A builder who really is stuck is sent
  home after a while, and if he stalls again the job is started afresh; you need do
  nothing. The exception is a decoration or field you placed yourself: if its build
  keeps stalling it is cancelled, and you place it again.
* **Citizens stuck at one spot?** The colony reports a spot where its people keep
  getting stuck, with its coordinates and a **[go there]** link (Friend rank and up in
  that colony, or an operator): go and fill the hole or bridge the gap. The entry
  says so when the spot lies inside a building, in one a builder is still working
  on, or on a site not built yet, and it
  names a trap block that stands close by: citizens never walk through traps, so a
  line of them needs a gap, a door or a fence gate.
* **A worker who does nothing for days?** The colony watches its miners, foresters,
  farmers, fishers, composters, beekeepers, florists and couriers: one whose work
  freezes for days is reset, or dismissed and replaced by a fresh hire.
* **No cavalry?** The Stable comes at 11 citizens, just before the Library, once the
  University has researched Mounted Combat; once the plan has reached it,
  `/colonyautopilot status` names it and why it waits. Still none: your style has no Stable
  (*Pick your style* above: place one from another style yourself once Mounted Combat
  is researched), or your plan file is older than the Stable (*Updating
  from an older version* below).
* **No spore creatures yet?** The grace period is holding them: see step 4 above.
* **Nothing built while nobody is online?** That is MineColonies' own rule: see
  *Playing together* above.

## Updating from an older version

Each new version of the addon is best started on a new world.

The growth plan lives in a file, `config/colonyautopilot-growthplan.json` (on a
server, in the server's own config folder). The file belongs to the game install, not to the
world, and it is written only when it is missing, so a plan file an older version
wrote stays, with its old order, even in a new world. One from before 1.4 has no
Stable step and never builds a Stable. If you played an older version, **delete the
file before you open a world**: the current plan is written in its place the next
time a world starts.

Edited the plan yourself? Keep your file and add a Stable step to it instead: copy
the Library's entry (at 11 citizens in the default plan) to just above it, and in
the copy change the hut to `minecolonies:blockhutstable` and the name to `Stable`.
