---
navigation:
  title: ColonyBucks
  position: 15
---

# ColonyBucks and Progression Mode

Out of the box your colony runs in **Progression Mode**. The autopilot still plans,
places and builds everything, but it no longer pulls supplies out of thin air: the
colony buys them with **ColonyBucks** from its own treasury. You earn the bucks
(monsters drop them, loot chests hold them, the exchange pays them for diamonds and
gold) and pay them in at the town hall. The colony does the spending.

Example: a builder needs 64 cobblestone the colony doesn't have. The autopilot
delivers it and takes half a buck from the treasury. If the treasury is empty, the
delivery waits until you pay some in. So if nothing seems to be building, look here
first.

**Paid from the treasury:**

* deliveries of what workers ask for and nobody in the colony can make
* the builders' materials
* guard gear and the quartermaster's tool and armour upgrades
* the potions and scrolls the warehouse keeps for you (see
  [Autopilot Systems](autopilot-features.md))
* cures for sick and infected citizens
* research: its cost items, plus a fee
* the gold, diamond and netherite blocks a repair puts back (see
  [Fighting Back](fighting-back.md))

**Always free:** food deliveries (the pantry and the restaurants' menus included),
planning and placing buildings, roads, terraforming, new recruits, and the research
the colony is given at its founding. Food that a research or a cure needs is charged
like anything else.

## Only the colony spends

Bucks flow one way: you turn what you earn into ColonyBucks, and only the colony
spends them. You can't buy items with them. So while Progression Mode is on:

* The **Postbox is locked**. An order is refused in chat (*The Postbox is locked in
  Progression Mode: only the colony spends ColonyBucks.*), and any order it still had
  open is cancelled.
* A **stock line** you set on a hut, or a **seed** you choose for a farmer's field
  that the autopilot doesn't sow itself, is never bought for you. The colony's
  couriers and crafters may still fill it from stock.
* The **restaurants' and nether mines' menus** are the colony's. An edit is refused
  in chat, and a dish listed before the mode came on comes off, so each menu keeps
  the autopilot's staples.
* From a **composter's, furnace user's or beekeeper's list** you edit, the colony buys
  only what it would pick itself: vanilla compostables and flowers that are not food,
  coal and charcoal. The rest waits for the colony's own stock.
* Some things are never bought at all: ColonyBucks themselves, a filled shulker box,
  chest, barrel or bundle, a spawner, a trial spawner, a vault, a trial key or a
  spawn egg (and, with Mystical Agriculture installed, its seeds).
* With MineColonies_Tweaks installed, its "Request Cost?" button at the university is
  refused too: a research is the colony's to buy.

Switch the mode off and the Postbox, the menus, the lists and the Tweaks button work
as before (the Tweaks button for the colony's owner and officers only).

## Earning

ColonyBucks have no recipe. They come from monsters, from loot chests and from the
exchange.

**Monsters.** A hostile mob drops a ColonyBuck about 1 time in 30 when it is killed
(`economy.dropChance`, 0.033). Looting adds half a percent per level
(`economy.lootingBonus`), so with Looting III it is about 1 in 21.

* The kill needs a killer: you, a citizen, or something one of you owns and sent
  (an arrow, a tamed wolf, or with K-Turrets installed a turret you own). A blow
  counts for 5 seconds, so a mob you hit that then dies in fire, lava or a fall still
  pays. A death nobody caused (`/kill`, a mob crushed in a wall, a fall with no blow
  before it) pays nothing.
* Hostile means a monster, or any mob that was attacking a player when it died.
  Babies pay like adults. Villagers, and the colony's citizens, guards and visitors,
  never pay.
* Big mobs never pay: anything with 150 health or more (`economy.bossHealth`), and
  the tagged bosses such as the Wither and the Ender Dragon. With Spore installed,
  the Inquisitor, the Usurper, the Vigil, an ordinary Proto and a Hive Tumor all pay;
  the calamities, and a Proto the RedNight has boosted, do not.
* With the `doMobLoot` game rule off, nothing drops, bucks included.
* The Mutant-Spore-Apocalypse pack also lists a few creatures that never pay
  (`economy.dropExclude`): Mutationcraft's spikes and its rotten skeletons (the ones
  that wake the Intoxicator), and Ars Nouveau's summoned skeletons and vexes.

**Your guards' kills.** In Progression Mode a buck from a citizen's kill (usually a
guard's) doesn't fall to the ground: it goes straight into the treasury. The rest of
the loot drops as usual.

**Raids.** In Progression Mode each raider killed during a MineColonies raid, by
anyone, pays one buck into the raided colony's treasury, up to the number of raiders
the raid sent. That comes on top of the raider's own chance to drop a buck. When the
raid ends, the owner and officers online hear the total: *The raid paid 12
ColonyBucks into the treasury.*

**Loot chests.** Nearly every loot chest in the world holds 1 to 4 bucks
(`economy.chestMin`, `economy.chestMax`): dungeons, temples, strongholds, trial
chambers, the bonus chest and most modded structures. A modded chest whose loot the
mod does not file as chest loot holds none: many of Towns and Towers' outpost chests,
for one. Mineshaft chests hold 0 to 2 (`economy.mineshaftMin`,
`economy.mineshaftMax`), village chests none.

**Good to know:**

* ColonyBucks don't burn. A stack dropped in fire or lava survives, like netherite.
* A citizen who dies with bucks in his pockets pays them into the treasury.

## Paying in

Bucks in your pocket do nothing: pay them in at the town hall. There are three ways:

* put them by hand in any rack of the town hall, or of a built warehouse
* feed the Town Hall block with a hopper or a pipe
* sneak-right-click the Town Hall block holding them. The action bar confirms it:
  *12 ColonyBucks paid into the treasury of Rivertown*

The colony empties those racks every few seconds. It empties its citizens' pockets
too, so a buck a worker picks up reaches the treasury on its own. Anyone may pay in
with a sneak-right-click, member of the colony or not.

**What a buck buys.** One ColonyBuck pays for 128 items (`economy.itemsPerBuck`). An
iron, diamond or netherite tool, weapon or armour piece counts as 16 items
(`economy.gearWeight`); wood, stone and gold tools, leather, chain and gold armour,
bows, shields, potions and scrolls count as one each. So:

* a stack of 64 cobblestone costs half a buck
* an iron pickaxe costs an eighth of a buck, a full set of iron armour half a buck
* a diamond costs 2.25 bucks (see *The exchange* below)
* a research costs its items, plus 1 buck per tier when it starts
  (`economy.researchFeePerTier`): a tier-4 research costs 4 bucks on top

The treasury keeps the change: a delivery of 37 items takes 37 of a buck's 128. The
town hall shows whole bucks only, so a treasury that reads 0 can still pay for small
deliveries.

**Paying in kind.** Diamonds, gold and netherite you stock save the colony money:

* A research uses what the University's racks hold first, and before it buys any
  diamonds, gold or netherite it takes those the warehouse holds.
* While the colony has a courier, the builders get the diamonds, gold and netherite
  the warehouse holds carried over, instead of bought.

## The exchange

The exchange turns your valuables into bucks. Open the town hall, turn to the
**Exchange** tab and press **Trade**, or run
<CommandLink command="/colonyautopilot exchange" close={true}>/colonyautopilot exchange</CommandLink> while you stand in the colony.
A trading screen opens, like a villager's, and its title shows the colony and its
treasury (*Rivertown: 12 bucks*).

The default offers (`economy.offers`):

* 2 diamonds for 1 buck
* 1 diamond block for 5 bucks
* 16 gold ingots for 1 buck
* 1 ancient debris for 1 buck
* 1 netherite ingot for 4 bucks

The prices are fixed and the exchange never runs out. It only buys: nothing can be
bought back. The bucks land in your inventory, so pay them in afterwards.

Anyone with access to the colony's huts (Friend rank and up) may trade, while the
colony is in Progression Mode and the server has the exchange on
(`economy.exchange`). Walk more than 8 blocks from where you opened it and the screen
closes.

**Stock rather than sell.** In Progression Mode the colony pays four times the
exchange's best price when it buys these goods itself (`economy.conjureMarkup`, 4).
For a diamond the best price is its share of a diamond block: nine diamonds sell as a
block for 5 bucks, a little over half a buck each. So a diamond the colony buys costs
it 2.25 bucks, while a loose one sells here for 0.50. When the colony needs diamonds
or gold, a diamond you stock is worth four and a half times what the exchange pays.
The **clerk** tells you when this matters: as you open the exchange, a gold line in
chat names what the colony waits on and what one of the goods it needs is worth to it
stocked, against what it fetches here.

The markup covers each good in every form: ores, raw pieces, nuggets, dusts and
blocks, and items with some of it inside, such as netherite scrap, ancient debris,
gilded blackstone, a jukebox and horse armour (and Create's ochrum and Mystical
Agriculture's essences where those mods are installed). With the exchange switched
off (`economy.exchange`) they cost the colony what any other item does.

## When things wait

Nothing is lost when the treasury runs dry. Things wait.

* A request stays open. The colony's couriers and crafters may still fill it;
  otherwise it is bought once the money comes in. Nothing comes on credit, and nothing
  comes in part.
* A build waits for its materials, a research for its price, a sick citizen for his
  cure.
* **One job per builder.** However much the treasury holds, each built Builder's Hut
  carries one construction job at a time (`economy.jobsPerBuilder`): a hut placed but
  not yet built, or an upgrade under way. Repairs, removals and decorations don't
  count. Example: with two Builder's Huts built and two jobs in hand, the colony
  places no new hut and starts no upgrade until one is finished. The plan's first
  Builder's Huts (three by default) always go up.
* **Taking turns.** Money or not, while both are due, new buildings and upgrades take
  turns up to town hall level 2; from level 3 the colony starts two upgrades for each
  new building (`economy.upgradesPerPlacement`), and at most one while the village is
  short of building land (three buildings in a row found no plot, until a plot is
  found again). A building whose land is being cleared keeps its place in the turns.
* **Saving for research.** The colony saves for the university's next research: its
  whole price is set aside, every other spender waits on the rest, and the research is
  bought as soon as the savings cover it. A cure never waits for the savings.
* While work waits and the treasury holds less than a whole buck, the owner and
  officers online hear it, at most once a game day: *The colony treasury is empty —
  waiting for ColonyBucks: 3 requests and 1 research.* The colony's milestones switch
  (`milestones.enabled`) quiets it.

## Reading the books

The town hall's **Exchange** tab (the coin seal, the book's last bookmark, below
Settings) is the colony's account book. Anyone with access to the colony's huts may
read it. The left page shows:

* **Treasury**: the balance in whole bucks
* **Waiting for funds**: how many requests, researches and cures wait for money
* when the builders' cap holds back the next building, why (*placement held: 2 of 2
  builder jobs in flight*)
* what the colony saves for (*Saving 3 of 5 ColonyBucks for the research ...*), or
  else the costliest thing that waits (*Waiting on 64x Cobblestone: 0.50
  ColonyBucks*), and in green what it would cost if you stocked the diamonds or gold
  it needs
* the **Trade**, **Re-lay roads** and **Autopilot settings** buttons

The right page is **the daily ledger**, for today and yesterday, in bucks to two
decimals: what came in (kills banked, raid pay, deposits, operator relief), what went
out (construction, research, guard gear, tools, other supply,
repairs, cures), and, apart from these, what players sold at the exchange: those bucks
went to the players, and show up under deposits once they pay them in.

<CommandLink command="/colonyautopilot treasury" close={true}>/colonyautopilot treasury</CommandLink> prints the same in chat
for the colony you stand in, and where to pay in; `/colonyautopilot treasury 3` reads
colony 3 from anywhere. An operator can add bucks
with `/colonyautopilot colony treasury add 10` (**op**); they show on the ledger as
operator relief. More commands are on the [Commands](commands.md) page.

## Switching it off

Progression Mode is a switch per colony. The colony's owner and officers turn it off
on the town hall's autopilot page: **Exchange** tab, **Autopilot settings** (or
<CommandLink command="/colonyautopilot menu" close={true}>/colonyautopilot menu</CommandLink>),
section **ColonyBucks**, row **Progression Mode**.

With it off, the colony is supplied free again, the full autopilot:

* nothing is charged, and the Postbox, the menus and the item lists work as before
* the exchange closes for that colony
* the treasury keeps its balance for when the mode comes back
* ColonyBucks still drop and lie in chests. Bucks you put in the racks wait there
  until the mode is back on; a sneak-right-click on the Town Hall block still pays in,
  and so does a dead citizen's pocket. The ledger keeps counting what comes in.

Switch it back on and everything on this page applies again. A colony founded with
the mode off hears about it once, the first time the mode comes on: one line in chat
to its owner and officers.

**Server lock.** An operator can hold every colony in the mode with
`economy.progressionModeLocked` (server-wide, off by default), in the config file or
with `/colonyautopilot set economy.progressionModeLocked true` (**op**). The row then
reads **GLOBAL · locked**, and nobody can switch it off there. A colony's own "off" is
kept, and comes back if the lock is lifted.

**The master switch.** With the whole autopilot off (`/colonyautopilot off`, **op**)
the colony is plain MineColonies again: the treasury takes no deposits and the
exchange is closed until it is back on. ColonyBucks still drop and lie in chests.
