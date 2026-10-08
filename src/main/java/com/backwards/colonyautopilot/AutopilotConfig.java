// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AutopilotConfig
{
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue MUTE_COLONY_CHAT;

    public static final ModConfigSpec.BooleanValue MASTER_ENABLED;
    public static final ModConfigSpec.BooleanValue EXPANSION_ENABLED;
    public static final ModConfigSpec.BooleanValue AUTO_UPGRADE_ENABLED;
    public static final ModConfigSpec.IntValue UPGRADE_CHECK_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue STUCK_ORDER_GAME_MINUTES;
    public static final ModConfigSpec.BooleanValue TRICKLE_ENABLED;
    public static final ModConfigSpec.IntValue TRICKLE_ITEMS_PER_MINUTE;
    public static final ModConfigSpec.BooleanValue GROWTH_ENABLED;
    public static final ModConfigSpec.BooleanValue GROWTH_KEEP_SETTINGS;
    public static final ModConfigSpec.BooleanValue GROWTH_POND_FIELD_UPKEEP;
    public static final ModConfigSpec.IntValue PLACEMENT_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue SEARCH_RADIUS_BLOCKS;
    public static final ModConfigSpec.IntValue SITE_PADDING_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_SURFACE_VARIANCE;
    public static final ModConfigSpec.IntValue MAX_ELEVATION_DELTA;
    public static final ModConfigSpec.BooleanValue BUILD_PATHS;
    public static final ModConfigSpec.BooleanValue LIGHT_VILLAGE;
    public static final ModConfigSpec.IntValue LAMP_SPACING;
    public static final ModConfigSpec.BooleanValue TERRAFORM_ENABLED;
    public static final ModConfigSpec.BooleanValue BLUEPRINT_GROUND_OFFSET;
    public static final ModConfigSpec.BooleanValue INSTANT_UNIVERSITY;
    public static final ModConfigSpec.BooleanValue ENDLESS_EXPANSION;
    public static final ModConfigSpec.BooleanValue UPGRADES_BEFORE_EXPANSION;
    public static final ModConfigSpec.IntValue CITIZENS_PER_EXTRA_PRODUCTION;
    public static final ModConfigSpec.IntValue GUARDS_PER_DRUID;

    public static final String ISSUED_GUN_TAG = "colonyautopilot:issued";
    public static final ModConfigSpec.IntValue GUARD_TOWER_CAP;
    public static final ModConfigSpec.BooleanValue FARM_FIELDS;
    public static final ModConfigSpec.BooleanValue STARTER_ANIMALS;
    public static final ModConfigSpec.BooleanValue ENCHANTER_STATIONS;
    public static final ModConfigSpec.BooleanValue JOB_WATCH;
    public static final ModConfigSpec.IntValue DECORATIONS_TARGET;
    public static final ModConfigSpec.BooleanValue ZONES_ENABLED;
    public static final ModConfigSpec.IntValue ZONE_MAX_EDGE;
    public static final ModConfigSpec.IntValue ZONE_MAX_PER_COLONY;
    public static final ModConfigSpec.BooleanValue WAREHOUSE_JANITOR;
    public static final ModConfigSpec.BooleanValue PURGE_MOB_DROPS;
    public static final ModConfigSpec.BooleanValue AUTO_RESEARCH;
    public static final ModConfigSpec.BooleanValue RESEARCH_FULL_TREE;
    public static final ModConfigSpec.ConfigValue<String> RESEARCH_CAPSTONES;
    public static final ModConfigSpec.ConfigValue<String> RESEARCH_FORKS;
    public static final ModConfigSpec.IntValue KNIGHTS_PER_ARCHER;
    public static final ModConfigSpec.BooleanValue RESEARCH_FREE_BUILD_SPEED;
    public static final ModConfigSpec.BooleanValue RESEARCH_FREE_BARRACKS;
    public static final ModConfigSpec.BooleanValue RESEARCH_FREE_POPULATION;
    public static final ModConfigSpec.BooleanValue RESEARCH_FREE_DRUID;
    public static final ModConfigSpec.BooleanValue RECRUIT_VISITORS;
    public static final ModConfigSpec.IntValue RECRUIT_BURST_BELOW_CAP;
    public static final ModConfigSpec.BooleanValue ANTICIPATE_THREATS;
    public static final ModConfigSpec.BooleanValue PROBLEM_CHAT;
    public static final ModConfigSpec.BooleanValue FRONTIER_TICKETS;
    public static final ModConfigSpec.BooleanValue BORDER_SIEGE_BREAKER;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SPORE_DEMOLITION_RIGHTS;
    public static final ModConfigSpec.BooleanValue KEEP_VISITORS_COMING;
    public static final ModConfigSpec.BooleanValue QUIET_ARRIVAL_DEATHS;
    public static final ModConfigSpec.BooleanValue COMBAT_POTIONS;
    public static final ModConfigSpec.BooleanValue ENCHANTER_SCROLLS;
    public static final ModConfigSpec.BooleanValue TEACH_RECIPES;
    public static final ModConfigSpec.BooleanValue WAREHOUSE_STOCK;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_ENABLED;
    public static final ModConfigSpec.IntValue PROVIDENCE_DELAY_SECONDS;
    public static final ModConfigSpec.BooleanValue PANTRY_ENABLED;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_FOOD;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_TOOLS;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_ARMOUR_WEAPONS;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_BUILD_MATERIALS;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_GUARD_GEAR;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_RECRUITS;
    public static final ModConfigSpec.BooleanValue PROVIDENCE_RESTAURANT_MENUS;
    public static final ModConfigSpec.IntValue PROVIDENCE_ITEMS_PER_SWEEP;
    public static final ModConfigSpec.BooleanValue INFIRMARY_ENABLED;
    public static final ModConfigSpec.BooleanValue QUARTERMASTER;
    public static final ModConfigSpec.BooleanValue AUTO_REPAIR_DAMAGE;
    public static final ModConfigSpec.IntValue DAMAGE_THRESHOLD_BLOCKS;
    public static final ModConfigSpec.IntValue DAMAGE_RESCAN_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue MILESTONES_ENABLED;
    public static final ModConfigSpec.BooleanValue PROBLEMS_ENABLED;
    public static final ModConfigSpec.BooleanValue SPORES_CURE_INFECTION;
    public static final ModConfigSpec.IntValue SPORES_FESTER_TICKS;
    public static final ModConfigSpec.IntValue CONFIG_VERSION;

    private static final int CURRENT_CONFIG_VERSION = 3;
    public static final ModConfigSpec.BooleanValue SPORES_TURRETS_TARGET_INFECTED;
    public static final ModConfigSpec.BooleanValue SPORES_TURRETS_INVULNERABLE;
    public static final ModConfigSpec.IntValue SPORES_GRACE_DAYS;
    public static final ModConfigSpec.BooleanValue SPORES_CREEP_GUARD;
    public static final ModConfigSpec.BooleanValue GUN_ENCHANTMENTS;
    public static final ModConfigSpec.BooleanValue FULL_AUTO_GUNNERS;
    public static final ModConfigSpec.BooleanValue GUNNER_GUARDS;
    public static final ModConfigSpec.BooleanValue SPAWN_STARTER_KIT;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SPAWN_TOME_BOOKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SPAWN_PATCHOULI_BOOKS;
    public static final ModConfigSpec.BooleanValue WORKERS_WORK_IN_RAIN;
    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_PATROLS;
    public static final ModConfigSpec.BooleanValue KEEP_VILLAGE_LOADED;
    public static final ModConfigSpec.BooleanValue VILLAGE_GOLEM;
    public static final ModConfigSpec.BooleanValue MUTE_FD_RECIPE_ERRORS;
    public static final ModConfigSpec.BooleanValue DISABLE_FIRE_TICK;
    public static final ModConfigSpec.BooleanValue IGNORE_FOOD_QUALITY;
    public static final ModConfigSpec.BooleanValue IGNORE_TOOL_TIER;
    public static final ModConfigSpec.BooleanValue REACH_HUT_ANY_HEIGHT;
    public static final ModConfigSpec.BooleanValue FISHER_HOME_POND;
    public static final ModConfigSpec.IntValue HOSTILE_SPAWN_CAP;
    public static final ModConfigSpec.DoubleValue POTION_DURATION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue NO_MOURNING;
    public static final ModConfigSpec.BooleanValue NO_GRAVES;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> HAZARD_BUSHES;
    public static final ModConfigSpec.IntValue SPAWN_CHUNK_RADIUS;
    public static final ModConfigSpec.IntValue WORK_BUDGET_PER_TICK;

    public static final ModConfigSpec.BooleanValue ECONOMY_PROGRESSION_MODE;
    public static final ModConfigSpec.BooleanValue ECONOMY_PROGRESSION_MODE_LOCKED;
    public static final ModConfigSpec.IntValue ECONOMY_ITEMS_PER_BUCK;
    public static final ModConfigSpec.IntValue ECONOMY_GEAR_WEIGHT;
    public static final ModConfigSpec.IntValue ECONOMY_RESEARCH_FEE_PER_TIER;
    public static final ModConfigSpec.IntValue ECONOMY_JOBS_PER_BUILDER;
    public static final ModConfigSpec.IntValue ECONOMY_UPGRADES_PER_PLACEMENT;
    public static final ModConfigSpec.DoubleValue ECONOMY_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue ECONOMY_LOOTING_BONUS;
    public static final ModConfigSpec.IntValue ECONOMY_BOSS_HEALTH;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ECONOMY_DROP_INCLUDE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ECONOMY_DROP_EXCLUDE;
    public static final ModConfigSpec.IntValue ECONOMY_CHEST_MIN;
    public static final ModConfigSpec.IntValue ECONOMY_CHEST_MAX;
    public static final ModConfigSpec.IntValue ECONOMY_MINESHAFT_MIN;
    public static final ModConfigSpec.IntValue ECONOMY_MINESHAFT_MAX;
    public static final ModConfigSpec.BooleanValue ECONOMY_EXCHANGE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ECONOMY_OFFERS;
    public static final ModConfigSpec.DoubleValue ECONOMY_CONJURE_MARKUP;

    static
    {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("master");
        MASTER_ENABLED = builder
          .comment("The whole autopilot at one switch. Off, every subsystem stands down — no free materials,",
            "no placements, no research, no golems, nothing — the colony reverts to plain vanilla",
            "MineColonies rules, and the gamerules and MineColonies setting the tweaks changed are given",
            "back. This is the DEFAULT for every world: /colonyautopilot on|off switches one world (for",
            "handing a built-up village back to survival play) and keeps that choice in the world's own",
            "save, where it wins over this value. Three things keep working while it is off, on purpose:",
            "spores.gracePeriodDays still holds new spore spawns off and still delivers the rednight when",
            "it ends (it paces the world, not the colony - set it to 0 to release them), the tools a",
            "player holds (the Zone Marker, the guide) - marking a zone still takes the autopilot's own",
            "lamp posts off that ground - and ColonyBucks still drop from hostile mobs and lie in loot",
            "chests: that is world loot, not colony behaviour (the money always exists).")
          .define("enabled", true);
        builder.pop();

        builder.push("economy");
        ECONOMY_PROGRESSION_MODE = builder
          .comment("Progression Mode: the colony pays in ColonyBucks for what the autopilot would otherwise conjure",
            "out of nothing - request deliveries, the builders' trickle, guard gear and rifles, the quartermaster's",
            "upgrades, the warehouse's potions and scrolls, cures, and research (its cost items plus a fee per tier).",
            "Only the colony spends them: the Postbox is locked, and nothing a player orders is conjured.",
            "Food deliveries are never charged (pantry and menus included); a research's cost items are",
            "charged whatever they are. Planning, placing, roads, terraforming, recruits and the free",
            "research grants stay free. Deposit bucks in the town hall block; hostile mobs drop them, loot",
            "chests hold them, and the Exchange on this page sells them. A request the treasury cannot pay",
            "stays open - MineColonies' own couriers and crafters may still fill it. A module switched off",
            "elsewhere on this page spawns nothing and is charged nothing either way. Off: the full",
            "autocolony, everything free apart from the changes that hold in both modes (listed in",
            "documentation.md section 5.8); the treasury is kept for when it comes back on. On for every colony by",
            "default, old ones included - the full autopilot felt 'too cheat' to players who wanted a",
            "progression. A colony founded in Progression Mode is not told; one founded with the mode off is",
            "told once the mode is switched on, as are colonies that predate it (owner and officers, in chat).")
          .define("progressionMode", true);
        ECONOMY_PROGRESSION_MODE_LOCKED = builder
          .comment("The server's word over the colonies' (pack ruling: the server is the first authority): true holds",
            "EVERY colony in Progression Mode - the page shows the switch greyed as global, and a colony's own",
            "'off' is kept in its save but not run, so it is back if this goes false again. Unlocked, a colony with",
            "the mode off gets the exchange's goods free, and they can be sold once its mode is back on, or at once",
            "at another colony's exchange: only this closes that (documentation.md section 5.8).")
          .define("progressionModeLocked", false);
        ECONOMY_ITEMS_PER_BUCK = builder
          .comment("Items one ColonyBuck pays for. The treasury counts items, not bucks: a deposit adds bucks times",
            "this, a delivery of 37 items takes 37, so nothing is rounded away; the treasury is shown in whole",
            "bucks. Lowered, whatever is priced in items costs more bucks (goods, gear at gearWeight items a piece,",
            "cures), while what is priced in bucks stays (research fees per tier, the exchange's offers, drops,",
            "chest loot, raid pay); the exchange's markup holds at any value. At 128 a stack of 64 is half a buck.")
          .defineInRange("itemsPerBuck", 128, 1, 4096);
        ECONOMY_GEAR_WEIGHT = builder
          .comment("What one piece of gear from IRON tier up - an iron, diamond, netherite or modded-tier tool, sword",
            "or armour piece, or a guard's TaCZ rifle - costs, in items: a diamond pickaxe is not one cobblestone.",
            "Wood, stone and gold tools, leather, chain and gold armour, bows, crossbows, shields, tridents and",
            "every consumable (potions, food, scrolls, even those that stack to 1) count one item each.")
          .defineInRange("gearWeight", 16, 1, 1024);
        ECONOMY_RESEARCH_FEE_PER_TIER = builder
          .comment("ColonyBucks the university pays to START a research, times its tier (1 to 6), on top of its cost",
            "items, which are charged whatever they are. A research the treasury cannot pay waits and the next",
            "one is tried. The free grants (research.free*) stay free: they are the autopilot's doctrine, not",
            "supply. 0: only the cost items are charged.")
          .defineInRange("researchFeePerTier", 1, 0, 64);
        ECONOMY_JOBS_PER_BUILDER = builder
          .comment("Progression Mode only: construction jobs in flight (huts placed but not built, upgrades under",
            "way; repairs, removals and decorations not counted) allowed per built builder's hut. Without a cap a",
            "starved colony turns into a field of empty construction sites nobody can pay to finish. The first",
            "builder's hut may always be placed, and the plan is held, never skipped.")
          .defineInRange("jobsPerBuilder", 1, 1, 16);
        ECONOMY_UPGRADES_PER_PLACEMENT = builder
          .comment("Progression Mode only, town hall level 3 and up: upgrades started between two new placements",
            "while both are due (at town hall level 1-2 they alternate, one of each). Upgrades are as important",
            "as new buildings, and past the founding more so. 0: a due placement never waits for upgrades.")
          .defineInRange("upgradesPerPlacement", 2, 0, 16);
        ECONOMY_DROP_CHANCE = builder
          .comment("Chance a hostile mob drops one ColonyBuck when it is killed. A kill needs a killer: a player, a",
            "colony citizen, or what one of them owns and sent (an arrow, a tamed wolf, a K-Turret). A blow counts",
            "for 100 ticks, so a mob hit and then finished by fire, a fall or lava pays; a death no one caused",
            "(/kill, a cull, a fall with no blow before it, a mob only angry at a player) and a despawn drop none.",
            "Hostile: a monster by class or spawn category, or a mob attacking a player when it died (modded mobs",
            "that skip the vanilla classes). Bosses never drop, nor do villagers and MineColonies' citizens, guards",
            "and visitors; doMobLoot false stops the drops like any mob loot. Drops and chest bucks happen with the",
            "master switch off too: the money always exists. 0.033 is about one kill in thirty (the owner's flat",
            "test, 2026-09-28: 'around 1 in 30'; 0.05 earlier that day, 0.10 before it, when kills paid faster than",
            "the war cost; 0.02 before that, when mob drops paid 2-21% of what a colony spends). In Progression",
            "Mode a colony citizen's kill (a guard's) pays its buck into his colony's treasury instead.")
          .defineInRange("dropChance", 0.033, 0.0, 1.0);
        ECONOMY_LOOTING_BONUS = builder
          .comment("Added to dropChance per level of Looting on the killer's weapon.")
          .defineInRange("lootingBonus", 0.005, 0.0, 1.0);
        ECONOMY_BOSS_HEALTH = builder
          .comment("A mob with this much max health or more is a boss and never drops a ColonyBuck, with or without",
            "the c:bosses tag - a modded boss is not always tagged. (Health points: 150 is 75 hearts; a zombie",
            "has 20.) At 150 the elites pay - Spore's Inquisitor, Usurper and Vigil, late MineColonies raiders -",
            "while the calamities and anything tagged c:bosses still do not; a mob of exactly 150 is a boss.")
          .defineInRange("bossHealth", 150, 1, 100000);
        ECONOMY_DROP_INCLUDE = builder
          .comment("Entity ids that drop ColonyBucks although nothing marks them hostile (\"somemod:crawler\"). The",
            "boss rule still holds.")
          .defineListAllowEmpty("dropInclude", java.util.List.of(), () -> "", AutopilotConfig::isEntityId);
        ECONOMY_DROP_EXCLUDE = builder
          .comment("Entity ids that never drop ColonyBucks, hostile or not.")
          .defineListAllowEmpty("dropExclude", java.util.List.of(), () -> "", AutopilotConfig::isEntityId);
        ECONOMY_CHEST_MIN = builder
          .comment("ColonyBucks in a loot chest when its loot is generated: every loot table under chests/ -",
            "dungeons, temples, strongholds, trial chambers, the spawn bonus chest, modded structures alike - gets",
            "one stack of chestMin to chestMax. Mineshaft chests use mineshaftMin/Max, village chests hold none.",
            "A roll made as any entity but a player (a datapack's scripted loot, such as Spore Inquisition's",
            "offerings) pays none.")
          .defineInRange("chestMin", 6, 0, 64);
        ECONOMY_CHEST_MAX = builder
          .comment("The most ColonyBucks one loot chest holds (see chestMin). A chestMin above this gives chestMin.")
          .defineInRange("chestMax", 15, 0, 64);
        ECONOMY_MINESHAFT_MIN = builder
          .comment("The fewest ColonyBucks in a mineshaft chest: mineshafts are full of chests, so each one pays less.")
          .defineInRange("mineshaftMin", 2, 0, 64);
        ECONOMY_MINESHAFT_MAX = builder
          .comment("The most ColonyBucks in a mineshaft chest. A mineshaftMin above this gives mineshaftMin.")
          .defineInRange("mineshaftMax", 5, 0, 64);
        ECONOMY_EXCHANGE = builder
          .comment("The Exchange: the Trade button on the town hall's Exchange tab and '/colonyautopilot exchange' open a",
            "trading screen that sells ColonyBucks for valuables (economy.offers). One way only: bucks never buy",
            "items back. It serves colonies in Progression Mode only. Off: the button is hidden, the command",
            "refuses, and the exchange's goods cost what they always did.",
            "On, in Progression Mode the exchange's goods and their ore, raw, nugget, dust and block forms (and",
            "what the pack's machines turn into them: netherite scrap, ancient debris, gilded blackstone, ochrum,",
            "horse armour, a jukebox, Mystical Agriculture's essences) are priced over their exchange value",
            "(a gold block costs four times what nine ingots sell for: economy.conjureMarkup), so buying them from the",
            "providence and selling them back gains nothing; with the mode off they cost what they always did.")
          .define("exchange", true);
        ECONOMY_OFFERS = builder
          .comment("The Exchange's offers, one string each: \"<item id> x<count> -> <bucks>\". Fixed prices, unlimited",
            "uses, no price drift. Emeralds and nether stars are left out on purpose: villagers trade emeralds for",
            "food the colony is fed free, and a wither summoned from 7 credits of skulls and soul sand drops a star.",
            "Half what they paid until the owner's 2026-09-28 test ('colony bucks in the exchange are too cheap'): a",
            "diamond was 1 buck, a diamond block 10, 8 gold ingots 1, ancient debris 2, a netherite ingot 8; the",
            "markup (economy.conjureMarkup) doubled with them, so the colony pays for the goods what it did. A",
            "file that lists the old offers keeps them until it is edited.",
            "Read when the server (or a single-player world) starts: an edit takes effect at the next start.")
          .defineListAllowEmpty("offers", java.util.List.of("minecraft:diamond x2 -> 1", "minecraft:diamond_block x1 -> 5",
            "minecraft:gold_ingot x16 -> 1", "minecraft:ancient_debris x1 -> 1", "minecraft:netherite_ingot x1 -> 4"),
            () -> "", o -> o instanceof String);
        ECONOMY_CONJURE_MARKUP = builder
          .comment("Multiplies what the exchange's goods and their forms cost to conjure in Progression Mode (while",
            "economy.exchange is on: off, nothing is priced as a good), over the floor at which buying them and",
            "selling them back breaks even: 4 makes a diamond cost about two bucks (2 until the owner's 2026-09-28",
            "test halved the exchange's offers: doubled with them, it keeps the colony's prices). 1 is the bare floor for all",
            "but the gold ores: a Fortune III pick and Mekanism's dissolution turn one into about 7.3 ingots, so",
            "below about 1.83 a conjured gold ore sells back for more than it cost. The exchange's prices",
            "(economy.offers) are unchanged.")
          .defineInRange("conjureMarkup", 4.0, 1.0, 16.0);
        builder.pop();

        builder.push("autoupgrade");
        AUTO_UPGRADE_ENABLED = builder
          .comment("Automatically queue build/upgrade work orders for colony buildings. Off also stops the",
            "stuck-order watchdog, the fast re-staffing of empty workplaces (MineColonies' own auto-hire still",
            "runs) and the drain that files the repairs owed by an early open, and the University the autopilot",
            "places is set down unbuilt like any other hut, not opened at level 1 (growth.instantUniversity).")
          .define("enabled", true);
        UPGRADE_CHECK_INTERVAL_SECONDS = builder
          .comment("How often every colony is re-checked for its next upgrade, in game-time seconds (scales with /tick rate).")
          .defineInRange("checkIntervalSeconds", 30, 5, 3600);
        STUCK_ORDER_GAME_MINUTES = builder
          .comment("Watchdog, in game-minutes (1,200 ticks each, so 20 is one Minecraft day; scales with /tick rate).",
            "A claimed construction order that places no block for this long while its builder stands still and no",
            "materials arrive, for 3x this while he walks about, or for 6x this whatever else moves, first has its",
            "builder sent home to find his way again (the order stands); a second such stall cancels and re-issues",
            "the order fresh, the manual town-hall cancel automated. A sleeping builder never counts as standing",
            "still. Healthy builds never trigger it (a moving blueprint cursor resets every clock). 0 disables.")
          .defineInRange("stuckOrderGameMinutes", 30, 0, 1440);
        builder.pop();

        builder.push("trickle");
        TRICKLE_ENABLED = builder
          .comment("Slowly materialize missing construction materials inside builders' huts.")
          .define("enabled", true);
        TRICKLE_ITEMS_PER_MINUTE = builder
          .comment("Items per game-time minute (scales with /tick rate) added to each builder's hut that is actively constructing.")
          .defineInRange("itemsPerMinute", 32, 1, 1024);
        builder.pop();

        builder.push("growth");
        GROWTH_KEEP_SETTINGS = builder
          .comment("Keep each hut's settings the way the autopilot wants them (leaves and replanting for the",
            "forester, breeding, guards retreating when hurt, trainee hiring, automatic work-order claiming",
            "for builders, a sane fill block for the miner). Off: the huts keep whatever you set.")
          .define("keepSettings", true);
        GROWTH_POND_FIELD_UPKEEP = builder
          .comment("Tend the fisher's pond and the farmer's fields on every grounds patrol (about every 30 seconds",
            "of game time: re-water, re-rim, clear what fell in, thaw). Off: the patrol writes no more blocks",
            "there. A pond or field is still dug or laid when its hut is completed, and a pond that is GONE",
            "(filled in, frozen through) is dug again at a hut completion or a server start - the fisher needs one.")
          .define("pondFieldUpkeep", true);
        GROWTH_ENABLED = builder
          .comment("The growth director's evaluation: placing buildings from the growth plan AND the upkeep",
            "that rides its pass — guard staffing, trainee promotion, the daily tending round (doorstep",
            "dig-outs, the job watch, settings, the grounds sweep), the town-hall apron, orphan-road",
            "lifting and the boot road heal. Off, those stop; what answers an event keeps running — a",
            "finished build still gets its road re-carved and its workplace outfitted (fields, animals,",
            "the fisher's pond), and the death safeguards still act. To stop only NEW buildings use",
            "'expansion' below (/colonyautopilot expansion off).")
          .define("enabled", true);
        EXPANSION_ENABLED = builder
          .comment("Place NEW buildings at all. Off (/colonyautopilot expansion off), the village stops",
            "sprawling — no more production buildings, homes or decorations — while defense stays",
            "covered (guard towers still rise where citizens work unguarded) and everything already",
            "standing keeps getting upgraded, repaired and outfitted.")
          .define("expansion", true);
        PLACEMENT_COOLDOWN_MINUTES = builder
          .comment("Minimum game-time minutes (scales with /tick rate) between two automatic building placements per colony — the village's growth pace.")
          .defineInRange("placementCooldownMinutes", 10, 1, 1440);
        SEARCH_RADIUS_BLOCKS = builder
          .comment("How far from the town hall the site search may roam.")
          .defineInRange("searchRadiusBlocks", 80, 16, 200);
        SITE_PADDING_BLOCKS = builder
          .comment("Empty blocks kept between building footprints.")
          .defineInRange("sitePaddingBlocks", 3, 0, 8);
        MAX_SURFACE_VARIANCE = builder
          .comment("Maximum terrain height difference across a candidate plot.")
          .defineInRange("maxSurfaceVariance", 3, 1, 16);
        MAX_ELEVATION_DELTA = builder
          .comment("Preferred maximum height difference between a new plot and the town hall.",
            "Plots further off-level (up a mountainside, down a gorge) are only used when nothing",
            "within the limit exists, so colonies founded against mountains still grow.")
          .defineInRange("maxElevationDelta", 10, 0, 64);
        BUILD_PATHS = builder
          .comment("Carve a walkable dirt path from every building's door toward the town hall - the huts you place",
            "yourself included, once they are built (fewer stuck citizens on uneven terrain). A road that has",
            "lost more than 3 of its blocks to bare earth, air or water over solid ground is carved again (checked",
            "once a game-day; one still as short after that waits until the count changes), and the creep guard",
            "lays a road block (and a lamp post's) back as it cures it.")
          .define("buildPaths", true);
        LIGHT_VILLAGE = builder
          .comment("Keep the village grounds lit: wherever the ground around a building is dark enough for monsters",
            "to spawn (block light 0) and no lamp post stands within growth.lampSpacing, a fence-and-glowstone",
            "lamp post goes up, and the village's posts that stand nearer each other than that are thinned. Off:",
            "the lamplighter raises and takes down nothing (the roads still lay their own posts).")
          .define("lightVillage", true);
        LAMP_SPACING = builder
          .comment("The fewest blocks between two of the village's lamp posts on one level. A road lays a new post",
            "only where none stands this near, and the lamplighter takes down a post that stands nearer another",
            "- unless a road would be left dark without it, so a road stays lit at any spacing (where a lane",
            "would be in no post's light it gets its post). Posts in a building's grounds, a zone or another",
            "colony's claim, posts without the village's cobblestone footing (yours) and a post a road's record",
            "ends on (where roads meet) are never taken down. Open ground is lit only to this spacing: wider",
            "leaves dark patches between the buildings.")
          .defineInRange("lampSpacing", 15, 5, 64);
        TERRAFORM_ENABLED = builder
          .comment("The colony makes its own space: when a building repeatedly finds no natural plot, level the",
            "least-uneven candidate area (cut and fill, natural blocks only) and build there — when the",
            "fisher's hut has no fishable water nearby, dig it a pond — and when the forester's hut has",
            "no trees in reach, plant it a starter grove.")
          .define("terraform", true);
        BLUEPRINT_GROUND_OFFSET = builder
          .comment("Place each hut (and each field and ornament) at its blueprint's own ground line: the mark the",
            "blueprint carries, or for an unmarked hut the line read from its layers. Off: every hut block on the",
            "surface. Buildings already standing never move. Some styles mark that line inside the",
            "blueprint (Caledonia stands most hut blocks 1-2 blocks above it, its stone smeltery below it) and",
            "MineColonies' build tool honours the mark; 1.1.64 ignored it and sank whole villages. Most level-1",
            "blueprints in MineColonies' own packs carry no mark at all (856 of 1,361 - all four Medieval packs",
            "bar a handful of files each) and the build tool sinks those too, so since 1.1.66 an unmarked hut's",
            "line is read from its layers: the highest layer whose outer ring holds no air, once the scan has",
            "passed outside air, is the ground (0-16 layers under the hut block, else the surface). Right on the",
            "mark for 90% of the marked level-1 blueprints.")
          .define("blueprintGroundOffset", true);
        INSTANT_UNIVERSITY = builder
          .comment("OPEN the University the moment the AUTOPILOT places it, while autoupgrade.enabled is on: level 1",
            "at once, so research starts (half the plan waits on it) while builders raise its walls through a",
            "repair order; one you place is built the ordinary way. While auto-upgrade and auto-research",
            "run, any University, yours too, opens early to the depth wanted research needs, and the deepest",
            "home to the level population research needs, capped at town hall+1 and builders within 100",
            "blocks. Off: levels wait for ordinary upgrades.")
          .define("instantUniversity", true);
        ENDLESS_EXPANSION = builder
          .comment("After the growth plan completes, keep adding homes (and guard towers) whenever the village is at bed capacity, until land runs out.")
          .define("endlessExpansion", true);
        FRONTIER_TICKETS = builder
          .comment("Let the plot search see one chunk ring beyond the built village: short-lived chunk tickets",
            "on the immediate frontier while a placement is hunting for ground, so unattended growth is not",
            "fenced in by the loaded-chunk border when the player is away. Tickets are temporary and few —",
            "this widens the search by one ring, it does not keep the frontier permanently loaded.")
          .define("frontierTickets", true);
        UPGRADES_BEFORE_EXPANSION = builder
          .comment("The village deepens before it sprawls: endless expansion waits until every standing building",
            "is upgraded as far as vanilla allows (blueprint max, research caps). A hut you tore down and a",
            "graveyard under tweaks.noGraves are never upgraded, so they are never waited on.")
          .define("upgradeBeforeExpansion", true);
        CITIZENS_PER_EXTRA_PRODUCTION = builder
          .comment("Once the growth plan completes, the village militarises instead of sprawling: one extra",
            "guard tower per this many villagers. (The key name predates the militarisation",
            "change and is kept so existing configs carry over.)")
          .defineInRange("citizensPerExtraProduction", 15, 5, 100);
        GUARDS_PER_DRUID = builder
          .comment("The colony fields one DRUID for every this-many knight/archer guards — a 1-in-N militia",
            "medic-and-debuffer that throws Slowness/Weakness at the infected and buffs the guards. The",
            "autopilot keeps them armed with the reagent that upgrades their potions (see research.freeDruid)",
            "and garrisons them into the barracks towers in rotation with the melee/ranged guards. 0 disables",
            "druid auto-assignment.")
          .defineInRange("guardsPerDruid", 4, 0, 64);
        KNIGHTS_PER_ARCHER = builder
          .comment("The militia's fighter mix: this many MELEE guards for every one RANGED guard (druids ride",
            "their own growth.guardsPerDruid ratio on top); 1 gives an even split. Applies to garrison hiring,",
            "standalone guard-tower doctrine, and the once-a-day rebalance alike. WHO fills the seats:",
            "MELEE - knights, and once the Huscarl is researched (MineColonies 1.1.1368+) one huscarl for",
            "every two knights: he cuts through armour but never blocks, the knights hold the shield wall.",
            "RANGED - with TaCZ and the 'Compatibility addon for MineColonies' installed a GUNNER, armed by",
            "the providence with a plain AK-47 and kept in rounds; once the Marksman is researched, gunner",
            "and marksman are hired turn about. Without the gun mods, or with guns.gunnerGuards off for the",
            "colony: archers until the Marksman is researched, marksmen after. Archers the doctrine has retired are never dismissed - the daily",
            "rebalance converts them in the Barracks, a Guard Tower is posted afresh once its archer falls.",
            "An issued rifle cannot be enchanted - table, anvil or the colony's Enchanter - so every tower",
            "level accepts it; with graves on it and its rounds die with the gunner outside Progression Mode (in",
            "it, paid for, they stay in his grave). The autopilot HOLDS",
            "every guard post of the towers it staffs (they read Locked in the hire window) and seats every",
            "guard itself by MineColonies' own hiring rules, so MineColonies' own hiring - which knows no",
            "doctrine - never fills one. A Barracks Tower where you set any post to manual or automatic",
            "hiring is yours. '/colonyautopilot off' (or growth off) hands every held post back - run it",
            "before removing the mod, or the held posts stay shut. Once a game day the",
            "mix is rebalanced in full: every move it needs that day, not one. SCHOOLS: whenever the autopilot seats",
            "a fighter it takes the best pupil of the matching school before a jobless citizen - the Archery's",
            "for a gunner, marksman or archer post, the Combat Academy's for a knight or huscarl post - and",
            "once a day a graduate MORE than two levels of the post's primary skill better than its weakest",
            "guard swaps into a full post of his school's kind - never at a Guard Tower the autopilot handed",
            "back to you, nor at a Barracks Tower you staff by hand.",
            "If you later REMOVE the gun mods, a Guard Tower that was posted",
            "for a gunner keeps its other posts closed: reopen one in its hire window.")
          .defineInRange("knightsPerArcher", 3, 1, 16);
        GUARD_TOWER_CAP = builder
          .comment("Hard ceiling on how many STANDALONE guard towers the village will raise — the coverage",
            "valve and the post-plan militarisation valve both stop here. Barracks and barracks towers are",
            "NOT counted and keep their own growth. Together with the residence cap (homes stop once every",
            "job the colony has can be staffed) this gives the village a bounded end-state instead of",
            "sprawling forever.")
          .defineInRange("guardTowerCap", 20, 0, 200);
        FARM_FIELDS = builder
          .comment("Lay out crop fields beside each Farm, the first on its natural plot as the Farm is placed",
            "(scarecrow plots, seeds set, off the roads; the colony's first fields potato, carrot and wheat,",
            "then what the village needs most: wheat, potato, carrot, beetroot 3 : 2 : 1 : 0.5), and choose",
            "every built Plantation's fields: kelp, then glow berries, cocoa and sugar cane, then the rest, each",
            "plant on one plantation, ordering the field structures it lacks (a plantation's fields tab stays",
            "on manual assignment while this is on). A farm or plantation without fields farms nothing, and",
            "only a player would otherwise place them.")
          .define("farmFields", true);
        STARTER_ANIMALS = builder
          .comment("Stock each built husbandry hut (shepherd, cowhand, chicken, swineherd, rabbit) with its",
            "founding pair of animals, topping it back up whenever fewer than two of its kind stand inside its",
            "grounds (only into a sealed paddock: a fenceless style, or a gap in the fence, gets none), and set",
            "the apiary up with a",
            "registered bee nest, bees and flowers — herders breed animals, they cannot summon them.")
          .define("starterAnimals", true);
        ENCHANTER_STATIONS = builder
          .comment("Keep the Enchanter's draining stations assigned automatically: every staffed worker",
            "building except builders and guards (they roam — a visit only succeeds when the worker is",
            "near their hut, and a failed visit still burns that station for the day) and the enchanter",
            "itself. Without stations the enchanter idles on a BLOCKING complaint, and only a player",
            "could click them in. Off, station picks stay the player's.")
          .define("enchanterStations", true);
        JOB_WATCH = builder
          .comment("Watch staffed gatherers (mine, lumberjack, farm, fishery, composter, apiary, florist) and",
            "couriers whose production statistics freeze for days — the wedge class that never complains — and",
            "unstick them: the mine gets its current level repaired first, a courier with deliveries queued is",
            "recalled to the warehouse first (pack emptied, tasks handed back), and a worker who stays frozen is",
            "dismissed for a fresh hire (a rehire fully resets a jammed worker's AI). Off, wedged workers idle",
            "until a player notices them.")
          .define("jobWatch", true);
        DECORATIONS_TARGET = builder
          .comment("Once the growth plan completes, have the builders raise up to this many small decorations",
            "(wells, fountains, gardens — whatever the colony's own style pack ships) around the village",
            "center. 0 disables.")
          .defineInRange("decorationsTarget", 4, 0, 32);
        builder.pop();

        builder.push("zones");
        ZONES_ENABLED = builder
          .comment("Protected zones: areas the player marks with the Zone Marker wand (get one with '/colonyautopilot",
            "zone wand'; '/colonyautopilot zone list' shows them) that the autopilot builds AROUND. It never places a",
            "building, lays a field, carves a road, grades terrain or fells a tree inside one, and the creep guard",
            "scrubs nothing there, so a player can raise their own spore defenses on ground the village will",
            "leave be. Off, existing marks are kept but no longer enforced.")
          .define("enabled", true);
        ZONE_MAX_EDGE = builder
          .comment("Largest a single zone may be on each side, in blocks. A corner pair wider than this is",
            "clamped down (the player is told), so one giant zone can't smother the whole claim.")
          .defineInRange("maxEdge", 48, 1, 256);
        ZONE_MAX_PER_COLONY = builder
          .comment("How many zones one colony may hold. Further marks are refused until one is deleted.")
          .defineInRange("maxPerColony", 24, 1, 256);
        builder.pop();

        builder.push("research");
        AUTO_RESEARCH = builder
          .comment("Let the university research whatever unlocks the growth plan's buildings, the population chain",
            "past the 25-citizen cap and the militia's Huscarl and Marksman, through the vanilla",
            "tree with every gate intact (university level, prerequisites, building requirements, item costs,",
            "a researcher working for the normal duration). The rest of the tree is research.fullTree's, which",
            "rides this switch: off, the university researches nothing by itself. The free grants",
            "(research.free*) run either way.")
          .define("autoResearch", true);
        RESEARCH_FULL_TREE = builder
          .comment("Research the rest of the tree too, guard and combat research first (the building unlocks, the",
            "population chain past the 25-citizen cap and the militia's Huscarl and Marksman are researched",
            "whatever this says). This switch never picks a mutually-exclusive fork or a",
            "one-per-branch tier-6 capstone by itself: research.forks and research.capstones do, and only while",
            "this switch is on. Their defaults decide every fork and three capstones; blank lists leave those",
            "choices to the player.")
          .define("fullTree", true);
        RESEARCH_CAPSTONES = builder
          .comment("Tier-6 branch CAPSTONES for the autopilot to research too (comma-separated), honoured while",
            "research.fullTree is on. Each branch allows only ONE — picking one PERMANENTLY locks that branch's",
            "other capstones — so each id listed here is a standing choice made for the player, and the default",
            "list makes three; blank leaves them all to the player. List the ones you want as",
            "\"minecolonies:<branch>/<name>\", comma-separated. The",
            "default is a knight-doctrine survival set: +6 max health to every citizen (civilian/guardianangel2),",
            "the knights' armor mastery (combat/masterswordsman — its parry prerequisite line is a fork choice,",
            "see research.forks), and faster building (technology/madness).",
            "Blank = pursue none. Only ONE per branch is honoured; a second in the same branch can never complete.")
          .define("capstones", "minecolonies:civilian/guardianangel2,minecolonies:combat/masterswordsman,minecolonies:technology/madness");
        RESEARCH_FORKS = builder
          .comment("Standing choices at MUTUALLY-EXCLUSIVE research forks (comma-separated research ids), honoured",
            "while research.fullTree is on. Starting one child of an exclusive fork locks its siblings out forever,",
            "so a fork is left to the player unless a child is listed here — listing one makes it YOUR standing",
            "choice and the autopilot researches that line.",
            "The default set arms a knight-heavy militia (the pack's doctrine): combat/parry (knight armor line, feeds",
            "the masterswordsman capstone; locks out dodge = the archers' armor line), combat/avoid (guards flee",
            "faster when hurt; locks out feint), combat/quickdraw (knight melee damage line; locks out preciseshot",
            "= archer damage), civilian/bandaid (citizens regenerate faster; locks out resistance), and",
            "civilian/nurture (children grow up faster — the war colony's population engine; locks out morebooks",
            "= faster school learning). This default set decides EVERY exclusive fork in MineColonies 1.1.1319",
            "and 1.1.1368 — with the three capstones above, no research choice is ever left waiting on the",
            "player (total autonomy). Blank = the autopilot decides no forks and every either/or stays yours.")
          .define("forks", "minecolonies:combat/parry,minecolonies:combat/avoid,minecolonies:combat/quickdraw,minecolonies:civilian/bandaid,minecolonies:civilian/nurture");
        RESEARCH_FREE_BUILD_SPEED = builder
          .comment("The village is born knowing how to build: every research granting builder block-place or",
            "block-break speed (plus its prerequisites) is completed FREE the moment a colony first",
            "evaluates — no university needed. If a speed research sits behind a mutually-exclusive",
            "fork, granting it decides that fork.")
          .define("freeBuildSpeed", true);
        RESEARCH_FREE_BARRACKS = builder
          .comment("The barracks is pre-unlocked: its unlock research (Tactic Training) and every prerequisite",
            "is completed FREE the moment a colony first evaluates, so the guard barracks — the colony's",
            "defensive backbone — can be built without waiting for the university to research it.")
          .define("freeBarracks", true);
        RESEARCH_FREE_POPULATION = builder
          .comment("The population-cap research is pre-unlocked: the whole CITIZEN_CAP chain (which raises",
            "max citizens past the default 25) is completed FREE at colony birth, so the village grows",
            "as fast as its beds allow instead of stalling at 25 until the university grinds up to it.")
          .define("freePopulation", true);
        RESEARCH_FREE_DRUID = builder
          .comment("The druids fight the infection at full strength from the start: their unlock research",
            "(Druid Potions / 'effects/consumepotions', normally deep in the combat tree behind Barracks",
            "level 3) is completed FREE at colony birth, and — paired with the reagent the autopilot keeps",
            "them supplied — every druid throws LEVEL-III splash potions (Slowness and Weakness on the",
            "infected, Strength/Resistance/Instant-Health on the guards) instead of the weak level-I default.",
            "Does nothing without druids (see growth.guardsPerDruid).")
          .define("freeDruid", true);
        builder.pop();

        builder.push("workshops");
        TEACH_RECIPES = builder
          .comment("Teach each built crafter a starter set of real vanilla recipes (planks, stone bricks, iron",
            "tools, bread...) and switch on producers that ship idle (crusher daily limit, composter list,",
            "the apiary's breeding-flower list - off, a beekeeper waits on flowers until one is added in his",
            "hut's GUI) — construction and tool requests then get produced in-colony instead of conjured.")
          .define("teachRecipes", true);
        WAREHOUSE_STOCK = builder
          .comment("Keep a standing minimum stock of staple goods in the warehouse; shortfalls become requests",
            "that only the taught workshops can fulfill, which keeps them producing into the warehouse.")
          .define("warehouseStock", true);
        WAREHOUSE_JANITOR = builder
          .comment("Keep storage from silting shut: on every pass, VOID what a warehouse holds above one stack of each",
            "placeable block and four stacks of every other stackable item (food included, like anything else;",
            "potions keep 24). Kept whatever the count: valuables, gear, ColonyBucks, the warehouse's own",
            "minimum-stock lines, what open builds still need and, in Progression Mode while economy.exchange is",
            "on, the exchange's goods, its ores included. It also caps enchanted books on the warehouse's and",
            "enchanter's shelves, ships one builder's hut's surplus to the warehouse, and trims rubble from",
            "workers' pockets — a clogged builder stops working entirely ('Please make space in my",
            "chests/racks'). A runaway food loop would otherwise fill the racks. Junk VOIDING (rotten flesh,",
            "bones, damaged gear...) is the separate, opt-in 'purgeMobDrops' below.")
          .define("warehouseJanitor", true);
        PURGE_MOB_DROPS = builder
          .comment("Void mob-drop detritus COLONY-WIDE on every janitor pass — rotten flesh, bones, spider eyes,",
            "junk plants, loose arrows and every DAMAGED weapon or armor piece, in every standing building's",
            "racks and every citizen's pockets. Opt-in. Off, the janitor keeps only its ordinary tidy, and",
            "NOTHING then bounds damaged weapons and armor (the stack caps skip damageable items): leave it on if",
            "guards fight. Spared: couriers are never searched (what they carry is a delivery); guard huts, the",
            "warehouse and the fletcher keep their arrows; guards, trainees and the nether worker keep their worn",
            "kit, and so do the Archery's and Combat Academy's racks; the composter keeps his plants and the",
            "crusher its bones; anything an open build still needs is spared.")
          .define("purgeMobDrops", false);
        builder.pop();

        builder.push("providence");
        PROVIDENCE_ENABLED = builder
          .comment("Conjure the items of requests nothing in the colony can fulfill, after a delay. Off, it also stops",
            "the builders' material trickle, the quartermaster, the pantry, the infirmary, recruiting and the",
            "visitor timer, the threat brace, the warehouse's potions and scrolls, and the restaurant menus (in",
            "Progression Mode the menus and the Postbox lock hold regardless). In Progression Mode what a player",
            "ordered (a stock line or a seed he set) and the smeltery's call for ore are left to the colony's own",
            "economy.")
          .define("enabled", true);
        PROVIDENCE_DELAY_SECONDS = builder
          .comment("How long a request must sit unfulfillable before the colony provides for itself (tools, food, anything workers",
            "are stuck on), in game-time seconds (scales with /tick rate).")
          .defineInRange("delaySeconds", 120, 30, 3600);
        PANTRY_ENABLED = builder
          .comment("Feed starving citizens — from their own provisions first, pantry bread otherwise — until a",
            "restaurant serves (citizen hunger never reaches the request system without one, and a wedged",
            "eat-AI starves citizens even with full pockets). With a restaurant serving it only catches a",
            "citizen the restaurant failed - one at saturation 2 or less, under the 2.5 at which MineColonies",
            "sends him to eat there - and feeds him full.")
          .define("pantry", true);

        PROVIDENCE_FOOD = builder
          .comment("Conjure FOOD for stranded requests (the pantry and the restaurant menus have their own switches).")
          .define("food", true);
        PROVIDENCE_TOOLS = builder
          .comment("Conjure TOOLS (pickaxe, axe, shovel, hoe, sword ladder) for stranded requests, and let the",
            "quartermaster upgrade the tools workers hold.")
          .define("tools", true);
        PROVIDENCE_ARMOUR_WEAPONS = builder
          .comment("Conjure ARMOUR and WEAPONS (bow, crossbow, shield, trident) for stranded requests, and let",
            "the quartermaster upgrade the armour guards wear.")
          .define("armourWeapons", true);
        PROVIDENCE_BUILD_MATERIALS = builder
          .comment("Supply BUILDING MATERIALS: the trickle into the builders' racks (trickle.*) and stranded",
            "builder requests.")
          .define("buildMaterials", true);
        PROVIDENCE_GUARD_GEAR = builder
          .comment("Provide for GUARD buildings at once and without limit: their gear requests, and with the",
            "gun mods the gunner's issued rifle and rounds.")
          .define("guardGear", true);
        PROVIDENCE_RECRUITS = builder
          .comment("Recruit tavern visitors at the providence's expense: their recruit cost is conjured, never",
            "taken from the colony. Off: the autopilot recruits nobody (recruitVisitors and the burst then do",
            "nothing) - hire them yourself at the tavern.")
          .define("recruits", true);
        PROVIDENCE_RESTAURANT_MENUS = builder
          .comment("Keep the restaurant menus set to dishes the citizens will eat, adjusted as homes level up, and the",
            "nether mines' menus stocked with trip rations. A home of level 3 or more eats only food of nutrition",
            "level+1 or more; five of the nine staples reach 6, so homes up to level 5 are fed. In Progression Mode",
            "the menus are the colony's whatever this says: kept to the autopilot's staples, and a player's edit is",
            "refused (a dish he listed was conjured free).")
          .define("restaurantMenus", true);
        PROVIDENCE_ITEMS_PER_SWEEP = builder
          .comment("How many stranded requests the providence answers per colony per sweep (every 10 seconds of",
            "game time). Guard buildings and tool requests are served outside this count.")
          .defineInRange("itemsPerSweep", 3, 1, 16);
        INFIRMARY_ENABLED = builder
          .comment("Nurse sick citizens back to health (cure items provided) whether or not the hospital works, while",
            "providence.enabled is on — a sick citizen stops",
            "working entirely, and a colony-wide epidemic can take the hospital's own healer down with",
            "everyone else (a sick healer cures nobody, himself included). In Progression Mode a cure costs",
            "its disease's cure items, and the Wither a modded mob inflicts is wiped for one credit (the milk);",
            "unpaid, a cure waits, the citizen sick. The healer is cured first so the vanilla cure path comes",
            "back; the hospital keeps working in parallel when it can.")
          .define("infirmary", true);
        RECRUIT_VISITORS = builder
          .comment("Recruit a tavern visitor into the colony (recruit cost paid by providence) the moment the",
            "village has room for one: every sweep, for as long as a bed is free and a visitor is willing.")
          .define("recruitVisitors", true);
        RECRUIT_BURST_BELOW_CAP = builder
          .comment("Fill the village in a burst when it has fallen this many beds below its cap (after a massacre,",
            "or when new homes open at once): travellers are summoned to the tavern and signed on one a sweep,",
            "instead of waiting for MineColonies to send one every 25 seconds. Needs recruitVisitors and a",
            "built Tavern. 0 disables the burst; smaller deficits fill at the tavern's own pace.")
          .defineInRange("recruitBurstBelowCap", 8, 0, 1000);
        ANTICIPATE_THREATS = builder
          .comment("Read the war forecasts and brace BEFORE the fight lands. While a fight is forecast (MineColonies",
            "has a raid set for tonight, or with Spore Inquisition the corruption clock nears an ordeal",
            "escalation), the sweeps double the combat-potion and scroll top-ups where an Alchemist or Enchanter",
            "can supply them, and the militia gets one extra rebalance that day (guard gear is always conjured",
            "the moment a tower asks for it).",
            "Stocks the DEFENDERS only — the attackers and the night's danger are never touched.")
          .define("anticipateThreats", true);
        KEEP_VISITORS_COMING = builder
          .comment("Keep tavern visitors arriving: every sweep clears the tavern's timer between arrivals, so a free",
            "visitor slot refills at the tavern's next tick instead of after its own spacing, which grows as the",
            "village fills; the tavern's own cap (three visitors per tavern level) still bounds how many stand",
            "there. Off, the tavern keeps its own pace. The penalty this was written against - no new visitors",
            "for a while after a visitor dies ('word has got around that your village is not safe') - never",
            "takes effect in MineColonies 1.1.1319 or 1.1.1368, so while this is on the tavern's notice of a",
            "visitor's death, with its false warning, is kept out of chat.")
          .define("keepVisitorsComing", true);
        QUIET_ARRIVAL_DEATHS = builder
          .comment("Arrivals never stop, so under a siege recruits can arrive and fall one after another. On, a",
            "citizen the autopilot recruited less than a game day ago dies without MineColonies' death",
            "announcement (a tavern visitor's death is already silent while keepVisitorsComing is on). The",
            "death itself is untouched — statistics, mourning and the grave all still happen.")
          .define("quietArrivalDeaths", true);
        COMBAT_POTIONS = builder
          .comment("Once an Alchemist's Tower stands, keep a small standing stock of combat potions in the",
            "warehouse — splash Harming to hurl at the infected, plus Healing / Regeneration / Strength for",
            "the fight — so the player has a ready anti-infection supply from their own colony. Kept",
            "deliberately small (potions are one-per-slot in vanilla); the warehouse janitor caps any surplus.")
          .define("combatPotions", true);
        ENCHANTER_SCROLLS = builder
          .comment("Once the Enchanter's Tower stands, keep a small standing stock of his scrolls in the",
            "warehouse for the player: 4 Scrolls of Colony Teleportation, plus 2 Scrolls of Guard Help",
            "(the whole on-duty guard force ports to you for a fight) once the tower reaches level 3 —",
            "the level the enchanter himself would need to craft them.")
          .define("enchanterScrolls", true);
        QUARTERMASTER = builder
          .comment("Keep every worker's kit as good as the village can make it: one citizen per colony per round",
            "gets their strictly-worse tool or armor replaced with the best the workplace and the colony's",
            "crafters allow; the old piece is retired. Off: workers keep what they were issued. In Progression",
            "Mode each upgrade is paid from the treasury, couriers and the blacksmith are passed over, and a",
            "worker who already holds a tool of a kind at the best level gets no second one. Vanilla never",
            "upgrades working gear, so a stone pickaxe survives into the diamond age and guards keep their first",
            "armor forever; in the mode a courier's tools are goods in transit and the smith's are what he forges.")
          .define("quartermaster", true);
        builder.pop();

        builder.push("repair");
        AUTO_REPAIR_DAMAGE = builder
          .comment("Watch built buildings for missing structure (explosions, fire, creep, accidents) and re-paste",
            "the missing blocks straight from the blueprint, no builder asked: intact blocks (racks and their",
            "stock included) and any other block standing in a cell (a player's chest, a changed block) are",
            "never touched. Off: no scan and no re-paste. In Progression Mode, while the exchange is on, a repair",
            "pays for the exchange's goods it pastes and leaves out those the treasury cannot pay for; ordinary",
            "blocks are never charged.",
            "With the Spore mod: spores.creepGuard leans on this - the guard airs infected blocks out of a",
            "building and THIS puts the real ones back, so with it off a building the creep reached stays",
            "holed. (The guard's watch over hut blocks the creep eats runs either way.) The goods are paid for",
            "infection or not: the scan cures an infected cell of them to air, for the repair to pay for.",
            "Builders only ever REPAIR a building whose structure is owed from an early open (the instant",
            "University, a home opened early for research), which this does not gate: that structure is built",
            "and paid like any construction.")
          .define("autoRepairDamage", true);
        DAMAGE_THRESHOLD_BLOCKS = builder
          .comment("How many solid blueprint blocks must be missing before a building counts as damaged.")
          .defineInRange("damageThresholdBlocks", 8, 1, 256);
        DAMAGE_RESCAN_COOLDOWN_TICKS = builder
          .comment("Server ticks before the sweep re-scans the same building; this is the spore-cure and damage cadence.",
            "Lower is more responsive under a spore assault (slightly more CPU). Old lazy value was 24000 (one day).")
          .defineInRange("rescanCooldownTicks", 1200, 20, 24000);
        builder.pop();

        builder.push("milestones");
        MILESTONES_ENABLED = builder
          .comment("Broadcast colony chat messages on completed constructions and population growth.")
          .define("enabled", true);
        PROBLEM_CHAT = builder
          .comment("Announce NEW needs-eyes problems in chat to every online player (at most one line a game day,",
            "counting the new problems) instead of leaving them buried in the log where no survival player",
            "looks — /colonyautopilot problems still lists everything on demand. The server's",
            "milestones.enabled=false mutes these pushes too; a colony's own milestones switch does not.")
          .define("problemChat", true);
        builder.pop();

        builder.push("spores");
        SPORE_DEMOLITION_RIGHTS = builder
          .comment("The spore entities allowed to BREAK BLOCKS — everyone else in the horde keeps its full",
            "DAMAGE but loses its demolition: explosions from unlisted spore mobs (the Howitzer's shells",
            "cratered the village live — pack ruling) destroy no blocks, and their other block-breaking is",
            "refused (for the creep's painters - mound, GastGeber, Proto, hive tumour - only inside a colony),",
            "wherever spores.creepGuard is on (the colony's own switch in its claim, the server's in the wild).",
            "Ships with only the Proto Hivemind; the final-boss offerings summon the same spore:proto entity,",
            "so the finale keeps its teeth. The creep's GROUND PAINTING is a separate mechanic this list never",
            "touches: the painters keep painting creep, inside a colony too, where the creep guard culls them and",
            "scrubs the ground; an unlisted one just can't blast craters anymore. Each entry is a full entity id",
            "(\"spore:proto\"): one that is not is dropped at load with a logged correction, and a list left",
            "empty by that returns to the default.")
          .defineListAllowEmpty("demolitionRights", java.util.List.of("spore:proto"), () -> "", AutopilotConfig::isEntityId);
        BORDER_SIEGE_BREAKER = builder
          .comment("When the colony border is under SUSTAINED creep pressure — the same edge chunks keep",
            "re-infecting patrol after patrol — cull the spore SPREADERS in a narrow halo just beyond the",
            "claim so the siege source dies instead of feeding forever. Inside the claim nothing changes;",
            "the halo opens only under sustained pressure and never touches the bosses.")
          .define("borderSiegeBreaker", true);
        SPORES_CURE_INFECTION = builder
          .comment("With the 'Fungal Infection: Spore' mod installed: the infection's damage tick never lands on a",
            "citizen or visitor — Slowness I stands in for it, refreshed as long as the Mycelium Infection is on",
            "them (Spore Inquisition's wastes biome re-infects everything in it every minute, so inside it the",
            "tick killed a villager in 80 seconds and a cure could only cost). The infection itself stays, and",
            "the Marker and Uneasy that come with it; the nursing round wipes every OTHER spore effect off",
            "living citizens (corrosion, bile, frostbite, madness, starvation), since no medicine in",
            "MineColonies or vanilla knows them — in Progression Mode one credit a cure, waiting unpaid.",
            "Does nothing without that mod.")
          .define("cureInfection", true);
        SPORES_FESTER_TICKS = builder
          .comment("How long (game ticks) a citizen's spore effects beyond the infection (corrosion, bile, frostbite,",
            "madness, starvation) are left to FESTER before the nurse wipes them. 0, the default, cures on",
            "sight (the nurse looks every 40 ticks / 2s). Above 0 they get that long to tick — 240 is a real",
            "chance for corrosion to kill — and then every such effect is cleared at once; a new one starts a",
            "fresh window. The Mycelium Infection itself is never wiped (cureInfection above: its tick only",
            "slows). The default was 240 through 1.1.65. A config file from before 1.1.66 that still holds",
            "the old 240 is switched to 0 once, at the next world start (meta.configVersion); set 240 again",
            "afterwards and it stays. Scales with /tick rate like the rest of the mod.")
          .defineInRange("festerTicks", 0, 0, 24000);
        SPORES_TURRETS_TARGET_INFECTED = builder
          .comment("With BOTH 'Fungal Infection: Spore' and 'K-Turrets' installed: teach every turret and",
            "drone to target the infection's whole horde (the spore:fungus_entities tag, mounds",
            "included). Turrets pick their default targets by spawn category and Spore registers",
            "every creature as MISC — a 'friendly' category — so without this bridge the turrets",
            "stand idle while the infected walk past. Does nothing unless both mods are present.")
          .define("turretsTargetInfected", true);
        SPORES_GRACE_DAYS = builder
          .comment("The quiet years: while the world is younger than this many game-time days, NEW spore",
            "creatures simply do not spawn (mounds, apostles and raid summons included) — the colony",
            "gets room to grow before the infection begins. Entities already saved in the world keep",
            "loading normally. Game-time days scale with /tick rate (a day is 24000 ticks however",
            "fast they pass). Does nothing without the Spore mod.",
            "DEFAULT 50: with Spore Inquisition installed the once-per-world rednight is re-delivered",
            "the moment the grace ends, so 50 gives the colony ~50 game-days of head start BEFORE the",
            "apocalypse (a day-1 rednight mauls a young village). Every 5 days the chat counts it down",
            "for everyone online — 'Days before rednight - 50 Days' — until it falls; the server's",
            "milestones.enabled=false silences the countdown, and a player's '/colonyautopilot chat off' does",
            "not (the war's warning, like the alert horn). Set 1 to TEST the rednight on",
            "the first night. IMPORTANT — 0 disables the grace AND the rednight: at 0 this addon steps",
            "aside for SI's own natural drop, which does not fire in this pack, so 0 means NO rednight",
            "at all. Keep 1+ to get the rednight. Set this at world CREATION (a world whose rednight",
            "already lives would get a second one later).")
          .defineInRange("gracePeriodDays", 50, 0, 10000);
        SPORES_CREEP_GUARD = builder
          .comment("The creep guard: every grounds patrol scrubs a slice of the colony's claimed, loaded ground.",
            "Infested ground goes back through the infection's own conversions; any other infection block goes",
            "to air inside a building, where DamageWatch puts the blueprint's block back",
            "(repair.autoRepairDamage: keep it on with this), and on open ground to air above the natural ground",
            "and dirt below it. Off: no scrub, spreader cull or hut sentinel, and the creep shield and crater",
            "guard stand down. A protected zone is left alone. The scrub reaches 48 blocks under the surface;",
            "a growth that stood in water leaves water, and a biomass tower or lump clears to air down to the",
            "ground around it, so a mound leaves no crater and a tower no pillar. Each patrol remembers the",
            "ground it finds clean, and where the creep",
            "has eaten below that it fills back up to it: a root mass leaves no pit, and water that ran into a",
            "hole there is earth again. Under a standing building the ground is its foundation: the infection",
            "there turns to earth, and where the creep is at work a hollow under the building is filled down to",
            "solid ground (24 blocks at most; a mine, a protected zone, and a hollow ending on a chest, torch or",
            "ladder are left). The conversions are exact for stone, bricks, sand, gravel, clay and",
            "laboratory blocks, but Spore folds podzol, coarse and rooted dirt into infested dirt and grass into",
            "mycelium, so those come back as dirt (soul soil as soul sand), inside buildings too. The spreaders",
            "(mound, GastGeber, tendril, scamper) are culled inside the claim; bosses and the rednight's own",
            "mound are spared. A Spore lamp, laboratory block or machine a player set is decor, never scrubbed,",
            "and never taken for the creep that erased a hut the guard raises again.",
            "Neither Spore mod offers a per-area switch.")
          .define("creepGuard", true);
        SPORES_TURRETS_INVULNERABLE = builder
          .comment("With BOTH 'Fungal Infection: Spore' and 'K-Turrets' installed: make every K-Turret",
            "immune to ALL damage — Spore's Mycelium Infection debuff (which a turret's machine body",
            "otherwise accepts and slowly dies to), plus mobs, fire and falls. A turret is then removed",
            "only the intended way, through its own menu; the void and /kill still work as an admin hatch.",
            "Does nothing unless K-Turrets is present.")
          .define("turretsInvulnerable", true);
        builder.pop();

        builder.push("guns");
        GUN_ENCHANTMENTS = builder
          .comment("With 'Timeless & Classics Guns: Zero' (TaCZ) installed: guns take enchantments like any",
            "weapon — at the enchanting table, or from a book at the anvil — and keep their attachments",
            "and ammunition when they do. High Caliber (more damage) or Fungicide (far more, spore",
            "creatures only), Marksman (stronger headshots), Shrapnel (the hit also wounds hostile mobs",
            "packed around the target), Cryo Rounds (freezes) or Incendiary (burns). Explosive rounds'",
            "blast damage is not affected. Off (or the master off): the enchanting table refuses guns and",
            "enchantments already on a gun do nothing - a book still goes on at the anvil, it just sleeps.",
            "Does nothing without TaCZ.")
          .define("enchantments", true);
        FULL_AUTO_GUNNERS = builder
          .comment("With TaCZ and the 'Compatibility addon for MineColonies' installed: a gunner guard whose gun",
            "is set to AUTO keeps the trigger held. MineColonies lets a guard attack four times a second and",
            "the add-on fires one round per attack - 240 a minute from a 600-rpm rifle. On, the rifle keeps",
            "firing between those attacks at the gun's OWN rate of fire (never faster), for as long as the",
            "guard's target lives and stands in his line of sight. He burns through rounds faster for it;",
            "the providence keeps him supplied. Semi-automatic and burst guns are left alone.")
          .define("fullAutoGunners", true);
        GUNNER_GUARDS = builder
          .comment("With TaCZ and the 'Compatibility addon for MineColonies' installed: the militia's ranged seats",
            "are GUNNERS (rifles and rounds issued by the providence; gunner and marksman turn about once the",
            "Marksman is researched). Off: no gunner is hired - the ranged seat is the marksman's where",
            "researched and the archer's otherwise, exactly as without the gun mods - and the gunners already",
            "standing are moved to those posts by the next daily rebalance, all in one day. The rifles go with",
            "the job: nobody is issued one. Per colony, so one village can field bows while another keeps guns.",
            "Does nothing without the gun mods.")
          .define("gunnerGuards", true);
        builder.pop();

        builder.push("problems");
        PROBLEMS_ENABLED = builder
          .comment("Keep the problem ledger: every WARN the autopilot logs (a parked watchdog, a crippled",
            "field, a defective blueprint) is remembered in memory for '/colonyautopilot problems', so a",
            "survival player can see what needs eyes without reading the server log. Pure bookkeeping —",
            "changes nothing in the world. Toggled in-game with /colonyautopilot problems on|off.")
          .define("enabled", true);
        builder.pop();

        builder.push("tweaks");
        SPAWN_STARTER_KIT = builder
          .comment("The founder's satchel: the FIRST player to join a world that has no colony yet gets a Town",
            "Hall block (the one manual act the autopilot asks for), once per world and never replaced. Every",
            "player's first join hands them Structurize's build tool, the Zone Marker wand, a blank Akashic Tome",
            "(when that mod is installed) and the pack's guide books as loose items (see spawnTomeBooks) —",
            "sneak-right-click a book onto the tome to bind it. Later joiners get a chat pointer to the village",
            "instead of a Town Hall.")
          .define("spawnStarterKit", true);
        SPAWN_TOME_BOOKS = builder
          .comment("Guide-book item ids handed to the player as LOOSE items beside the blank tome —",
            "binding is manual by design (a component-built pre-bound tome refused to morph",
            "in-game). Unknown ids are skipped, so extend freely as the pack gains guide books.")
          .defineListAllowEmpty("spawnTomeBooks", java.util.List.of("ae2:guide", "ars_nouveau:worn_notebook"), () -> "", o -> o instanceof String);
        SPAWN_PATCHOULI_BOOKS = builder
          .comment("Patchouli BOOK ids (not item ids) also handed out with the satchel — every Patchouli",
            "manual is one shared item (patchouli:guide_book) stamped with the book's id, which the",
            "plain item list above cannot express. Ignored when Patchouli isn't installed, and a book whose mod",
            "(the id's namespace) is not installed is skipped; a wrong id for an installed mod still gives a",
            "book that says 'Invalid book', so keep this list to books the pack ships.")
          .defineListAllowEmpty("spawnPatchouliBooks",
            java.util.List.of("mysticalagriculture:guide", "immortuoscalyx:genetic_explanation_material"),
            () -> "", o -> o instanceof String);
        WORKERS_WORK_IN_RAIN = builder
          .comment("Turn on MineColonies' 'workers always work in rain' setting on world start. Switched off (or",
            "the master off), the setting is turned back off — only if this tweak turned it on.")
          .define("workersWorkInRain", true);
        DISABLE_VANILLA_PATROLS = builder
          .comment("Turn off vanilla pillager patrol spawning per world start (doPatrolSpawning=false).",
            "Off by default since 1.3: patrols are part of the game. The switch is here because patrols",
            "were seen wandering in, wedging on high ground and sniping golems and guards from standstill",
            "(observed live) while the colony has its own raid system for drama. Switched off (or the",
            "master off), the world gets its own value back, unless someone changed the gamerule since.",
            "A config file from before 1.3 that still holds the old default (true) is switched to false",
            "once, at the next world start (meta.configVersion); set true again afterwards and it stays.")
          .define("disableVanillaPatrols", false);
        KEEP_VILLAGE_LOADED = builder
          .comment("Keep every colony's built-up area chunk-loaded and ticking, so the village keeps living,",
            "building and growing while the player is away exploring.")
          .define("keepVillageLoaded", true);
        VILLAGE_GOLEM = builder
          .comment("Once the town hall is built, keep TWO iron golems patrolling it (one per side) — plus one",
            "around every built blacksmith (each blacksmith is a guard post). Civilians flee by design; the",
            "golems fight for them. A golem that falls is reforged one game-day after its fall (a post's first",
            "golem comes at once). Off (the colony's page, or an operator's colony set/reset), its golems in",
            "loaded chunks leave at once;",
            "'/colonyautopilot golems off' does so for every colony without its own 'on'. Golems in unloaded",
            "chunks stay; a file edit only stops forging.")
          .define("villageGolem", true);
        MUTE_FD_RECIPE_ERRORS = builder
          .comment("Hide the harmless startup ERRORs left behind by removing FarmersDelight from the pack:",
            "Spore Inquisition ships optional FarmersDelight-compat recipes, and without that mod the",
            "'farmersdelight:cooking' recipe serializer no longer exists, so Minecraft logs ~23 'Parsing",
            "error loading recipe farmersdelight:...' lines at every data-pack load. They change nothing —",
            "those recipes were never craftable here — but they bury real errors. On, a log filter drops",
            "exactly those lines (RecipeManager errors in the farmersdelight namespace) and nothing else.",
            "Off, they are logged again from the next /reload; the load at world start is always muted, because",
            "the server config is not read until the server starts. Does nothing if FarmersDelight is present.")
          .define("muteFarmersDelightRecipeErrors", true);
        DISABLE_FIRE_TICK = builder
          .comment("Ship with vanilla fire SPREAD off (the doFireTick gamerule = false, set once per world start).",
            "A colony is mostly wood; a lightning strike, a stray creeper or a lava spill can otherwise burn",
            "building to building while the player is away, and an autocolony cannot fight its own fires. Blocks",
            "already alight just burn out in place instead of spreading. Applies at world start like the other",
            "gamerule tweaks. Switched off (or the master off), the world gets its own value back, unless someone",
            "changed the gamerule since; a world that already had fire spread off is never touched.")
          .define("disableFireTick", true);
        IGNORE_TOOL_TIER = builder
          .comment("Any tool mines any block: MineColonies refuses to let a worker touch a block whose required",
            "pickaxe tier is above the tool in hand, and the builder then freezes the whole work order (a",
            "modded block like Create's zinc, with no MineColonies setting to relax it). On, the required",
            "tier reads as 0 for every worker; durability and pickaxe-vs-axe choice stay vanilla (a worker",
            "holding two pickaxes picks the lower one, as MineColonies takes the cheapest that qualifies).",
            "Off, or with the master switch off, vanilla MineColonies tool rules apply.")
          .define("ignoreToolTier", true);
        REACH_HUT_ANY_HEIGHT = builder
          .comment("A citizen has reached a building once he stands inside its own box (which holds the ground under a",
            "lifted hut), or, on a walk of his job, once that walk has ended anywhere under or over the building,",
            "two to 24 blocks above or below the hut block (a lifted hut, not a cave far beneath it). Off, or with",
            "the master switch off, MineColonies' own 3D test applies. MineColonies counts him arrived only",
            "within 4 blocks of the hut block in 3D, so a hut block 4 or more blocks overhead can only be reached",
            "from the one column under it: styles whose huts stand high off the ground (Dark Oak Treehouse",
            "places them 4 up) left every builder stuck under his own unbuilt hut for good, and the colony never",
            "started. Nobody is stopped while still walking, and a walk that ends short on the hut's own level is",
            "left to MineColonies. A job walk to a hut block counts the same way (a guard's patrol point); the",
            "walks off the job - to eat, sleep, the hospital, a homeless citizen's way home - keep MineColonies'",
            "own test and its rescue of a stuck citizen, and walks to any other block are untouched.")
          .define("reachHutFromAnyHeight", true);
        FISHER_HOME_POND = builder
          .comment("A fisher fishes the pond the village dug beside his hut and searches no further (without a dug",
            "pond - terraforming off, or no room - any pond within 24 blocks of the hut and 4 of its ground",
            "will do). The village always digs him one: natural water nearby can be filled in as it grows. MineColonies",
            "otherwise has him collect up to twenty ponds, 150 blocks out, and pick one at random a trip -",
            "and its search calls a lake at the foot of a cliff reachable because he can drop into it: he",
            "cannot walk back out, and every trip there ends in two stuck rescues. While he knows no such",
            "pond, and with this off, MineColonies' own search applies.")
          .define("fisherKeepsToHomePond", true);
        IGNORE_FOOD_QUALITY = builder
          .comment("Silence MineColonies' food-quality / food-diversity complaints (an autocolony does not need",
            "high-quality food): the citizen interactions 'I wish the dining hall menu would contain some better",
            "food options' and '...a bit more variety' (plus their urgent escalations) are cleared as they appear,",
            "and the food-quality HAPPINESS penalty is neutralized so morale is not docked for a plain diet.",
            "Citizens are still fed and still eat; only the nag and the penalty go. Off, MineColonies' normal",
            "tiered-food expectations apply.")
          .define("ignoreFoodQuality", true);
        HOSTILE_SPAWN_CAP = builder
          .comment("Vanilla hard-codes the hostile ('monster') spawn cap at 70 — no gamerule, no setting. Both",
            "spawn ceilings read that one number (the world-wide cap of 70 x loaded spawnable chunks / 289,",
            "and the per-player cap of 70 monsters near any one player), so this key moves the whole night",
            "coherently, applied at world start. Ships at vanilla's 70: the pack ran 100 through 1.1.61 so",
            "the spore horde, vanilla night mobs and the pack's mob mods wouldn't starve each other for",
            "spawn slots, but the thicker NATURAL horde stacking on top of a spore raid lagged the live",
            "server (2026-07-13) — the spore war's own mounds and raids spawn directly and never counted",
            "against this cap, so raising it buys extra background monsters, not a bigger war. Raise it",
            "only if /spark tps stays healthy on raid nights.")
          .defineInRange("hostileSpawnCap", 70, 10, 1000);
        POTION_DURATION_MULTIPLIER = builder
          .comment("Multiply the duration of every beneficial or neutral brewable potion (pack request, 2026-07-10:",
            "'vanilla potions last longer'). Applied once per world start to the potion registry's effect",
            "templates, so drink, splash, lingering and tipped-arrow forms all scale together — modded brews",
            "included, the colony's own combat potions among them. HARMFUL effects (Poison, Slowness,",
            "Weakness...) stay vanilla-length so an enemy-thrown debuff doesn't grow with the multiplier",
            "(trade-off: the player's own harmful splashes stay vanilla too). Instant effects (Healing/",
            "Harming) are untouched. Ships at 2.0 (an eight-minute potion lasts sixteen); set 1.0 to restore",
            "vanilla. On a dedicated server, clients' potion TOOLTIPS may show the unscaled time — the effect",
            "you actually get is the scaled one.")
          .defineInRange("potionDurationMultiplier", 2.0, 1.0, 10.0);
        NO_MOURNING = builder
          .comment("Citizens never stop work to mourn. MineColonies has no setting for its mourning system:",
            "a death sends every relative and housemate of the deceased graveyard-wandering for a FULL",
            "game-day starting at the next dawn — and in this pack night mobs are MEANT to kill citizens,",
            "so one bad night can idle half the village. On, the addon clears the mourning marks through",
            "MineColonies' own API before dawn can act on them (a death seconds before dawn may show a",
            "moment of grief, then everyone returns to work). The small one-off happiness penalty for a",
            "death is untouched. Off, vanilla mourning applies.")
          .define("noMourning", true);
        NO_GRAVES = builder
          .comment("Citizens die clean: no grave block, no dropped or scattered items. A citizen's kit goes with him",
            "(colony-issued: the providence re-equips the replacement, in Progression Mode paid for again) and",
            "the ColonyBucks he carried go to the treasury. The graveyard leaves the build plan and the grave",
            "research is pruned; recruits replace the fallen. Off, graves rise as MineColonies makes them, the",
            "bucks still going to the treasury and, outside Progression Mode, the issued rifle and rounds taken",
            "back. The death message, statistics, mourning rules and recruitment are untouched either way.",
            "MineColonies' grave",
            "system misfires too many ways for an unattended war village (observed live server): a death in",
            "lava or with no clear ground nearby spawns NO grave and the kit vanishes anyway; a spawned",
            "grave is a breakable block that mobs and the creep knock over, spraying items across the",
            "fight; and an expired grave dumps everything on the ground. A colony's own 'off' walks the plan",
            "with a Graveyard step at once; the server's own value is written into",
            "colonyautopilot-growthplan.json only when the file is generated, and an existing file is never",
            "rewritten: delete it to regenerate (a flip takes effect on the next world load).")
          .define("noGraves", true);
        HAZARD_BUSHES = builder
          .comment("Blocks that hurt whoever walks through them, which the grounds patrol removes from every colony's",
            "claim: in loaded chunks, a few chunks a patrol (the whole claim about once a game day), never inside a",
            "building's box (a blueprint's own bushes are the building's), a protected zone or a player's build high",
            "over the village. When a citizen or visitor dies to one (a damage type with the same id as a listed block,",
            "as the sweet berry bush's and Ars Nouveau's sourceberry bush's are), every listed block within 2 of the",
            "death spot goes at once, a building's box included, a zone still not. Removed blocks drop nothing; a",
            "berry patch you want to keep belongs in a protected zone. Ids of mods not installed are skipped. Empty,",
            "nothing is removed; nor for a colony with its terraforming off. Ars Nouveau's sourceberry bushes killed",
            "38 tavern visitors and a builder in one Peaceful night of the owner's test (MineColonies does not",
            "announce a visitor's death).")
          .defineListAllowEmpty("hazardBushes", java.util.List.of("minecraft:sweet_berry_bush", "ars_nouveau:sourceberry_bush"),
            () -> "", AutopilotConfig::isEntityId);
        SPAWN_CHUNK_RADIUS = builder
          .comment("Chunks kept permanently loaded around the WORLD SPAWN (vanilla's spawnChunkRadius",
            "gamerule, applied per world start). The rednight lands at world spawn — Spore Inquisition's",
            "once-per-world drop and the addon's grace-period return both summon there — and the mound",
            "only grows, the war only escalates, while that ground ticks. Vanilla 1.20.5+ keeps a bare",
            "radius-2 ring loaded, and a village founded away from spawn leaves nobody there to load",
            "more: the apocalypse sits frozen (observed live server). 10 gives a 19x19-chunk ENTITY-ticking",
            "core (the mound and its raids live there; block ticks reach 21x21 — a real but affordable",
            "cost). This config owns the rule: an op's manual /gamerule spawnChunkRadius is reset to this",
            "value at the next world start — change this setting, not the gamerule. 0 (or the master off)",
            "gives the world back the radius it had before the mod changed it. From Spore Inquisition 4.5",
            "the corruption clock also needs somebody online (any dimension), so an idle dedicated server",
            "no longer escalates even with this ground loaded: loaded ground is necessary, not sufficient.")
          .defineInRange("spawnChunkRadius", 10, 0, 32);
        WORK_BUDGET_PER_TICK = builder
          .comment("Block reads and writes per server tick shared by every land-work drain of the autopilot",
            "(terraforming, grounds sweeps, ravine surveys, damage scans). A colony's terraforming and sweeps",
            "stay well under it. A damage scan of a large building (a footprint over about 500 cells: many town",
            "halls, warehouses, barracks, universities) spends it every tick of that walk, walking fewer layers",
            "a tick. Several colonies share it first come, first served: a cap against stacking, not a fair",
            "share. Server-wide.")
          .defineInRange("workBudgetPerTick", 2000, 200, 20000);
        builder.pop();

        builder.push("meta");
        CONFIG_VERSION = builder
          .comment("Written by the mod - leave it alone. A changed DEFAULT only ever reaches a new config file;",
            "an existing file keeps the value it has. When a default changes for a reason that matters to",
            "every world, a file from before the change is moved over once, at the next world start, and",
            "this number goes up so it never happens twice. 1 -> 2 (1.1.66): spores.festerTicks 240, the",
            "old default, becomes 0. 2 -> 3 (1.3): tweaks.disableVanillaPatrols true, the old default,",
            "becomes false. Whatever you set afterwards is yours and is not touched again.")
          .defineInRange("configVersion", 1, 1, 1000);
        builder.pop();

        SPEC = builder.build();

        final ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("chat");
        MUTE_COLONY_CHAT = client
          .comment("Mute all colony chatter in YOUR chat only: MineColonies announcements (raids, research,",
            "deaths, citizen news) and the autopilot's village milestones. Toggled in-game with",
            "/colonyautopilot chat on|off. Other players are unaffected. Three lines still come through, on",
            "purpose: the alert horn, the one-time Progression Mode notice and the rednight countdown.")
          .define("muteColonyChat", false);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private static boolean isEntityId(final Object entry)
    {
        return entry instanceof String id && id.indexOf(':') > 0 && net.minecraft.resources.ResourceLocation.tryParse(id) != null;
    }

    public static boolean live(final ModConfigSpec.BooleanValue feature)
    {
        return masterOn() && feature.get();
    }

    public static boolean live(final com.minecolonies.api.colony.IColony colony, final ModConfigSpec.BooleanValue feature)
    {
        return masterOn() && get(colony, feature);
    }

    public static boolean get(final com.minecolonies.api.colony.IColony colony, final ModConfigSpec.BooleanValue value)
    {
        return (Boolean) ColonySettings.effective(colony, ColonySettings.key(pathOf(value)));
    }

    public static int get(final com.minecolonies.api.colony.IColony colony, final ModConfigSpec.IntValue value)
    {
        return (Integer) ColonySettings.effective(colony, ColonySettings.key(pathOf(value)));
    }

    public static double get(final com.minecolonies.api.colony.IColony colony, final ModConfigSpec.DoubleValue value)
    {
        return (Double) ColonySettings.effective(colony, ColonySettings.key(pathOf(value)));
    }

    public static boolean progressionMode(final com.minecolonies.api.colony.IColony colony)
    {
        return get(colony, ECONOMY_PROGRESSION_MODE);
    }

    static String pathOf(final ModConfigSpec.ConfigValue<?> value)
    {
        return String.join(".", value.getPath());
    }

    private static volatile Boolean worldMaster;

    public static boolean masterOn()
    {
        final Boolean world = worldMaster;
        return world != null ? world : MASTER_ENABLED.get();
    }

    static void setWorldMaster(final Boolean enabled)
    {
        worldMaster = enabled;
    }

    static void migrate()
    {
        if (!SPEC.isLoaded() || CONFIG_VERSION.get() >= CURRENT_CONFIG_VERSION)
        {
            return;
        }

        final int from = CONFIG_VERSION.get();
        if (from < 2 && SPORES_FESTER_TICKS.get() == 240)
        {
            SPORES_FESTER_TICKS.set(0);
            ColonyAutopilot.LOGGER.info("Config brought up to 1.1.66: spores.festerTicks 240 (the old default) -> 0 - infected citizens are now cured on sight. Set it back to 240 for the old fester window; it will not be changed again.");
        }
        if (from < 3 && DISABLE_VANILLA_PATROLS.get())
        {
            DISABLE_VANILLA_PATROLS.set(false);
            ColonyAutopilot.LOGGER.info("Config brought up to 1.3: tweaks.disableVanillaPatrols true (the old default) -> false - vanilla pillager patrols are no longer turned off at world start. Set it back to true to keep them away; it will not be changed again.");
        }
        CONFIG_VERSION.set(CURRENT_CONFIG_VERSION);
        SPEC.save();
    }

    private AutopilotConfig()
    {
    }
}
