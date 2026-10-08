// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GrowthPlanLoader
{

    private static final class JsonStep
    {
        String hut;
        String name;
        int minCitizens;
        int targetCount = 1;
        int targetLevel = 0;
    }

    private GrowthPlanLoader()
    {
    }

    static List<GrowthDirector.ResolvedStep> loadOrCreateDefault()
    {
        final Path file = FMLPaths.CONFIGDIR.get().resolve("colonyautopilot-growthplan.json");
        try
        {
            if (!Files.exists(file))
            {
                writeDefault(file);
            }
            try (Reader reader = Files.newBufferedReader(file))
            {
                final JsonStep[] steps = new Gson().fromJson(reader, JsonStep[].class);
                if (steps == null)
                {
                    ColonyAutopilot.LOGGER.warn("Growth plan {} held no entries — using the built-in growth plan", file.getFileName());
                    return defaultPlan();
                }
                final List<GrowthDirector.ResolvedStep> plan = new ArrayList<>();
                for (final JsonStep step : steps)
                {
                    if (step == null)
                    {
                        continue;
                    }
                    final Block block;
                    try
                    {
                        block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(step.hut)).orElse(null);
                    }
                    catch (final Exception e)
                    {

                        ColonyAutopilot.LOGGER.warn("Growth plan entry '{}' has no valid hut id — skipped", step.hut);
                        continue;
                    }
                    if (!(block instanceof AbstractBlockHut))
                    {
                        ColonyAutopilot.LOGGER.warn("Growth plan entry '{}' is not a MineColonies hut block — skipped", step.hut);
                        continue;
                    }
                    plan.add(new GrowthDirector.ResolvedStep(block,
                      step.name == null ? step.hut : step.name, Math.max(0, step.minCitizens), Math.max(1, step.targetCount),
                      Math.max(0, step.targetLevel)));
                }
                if (plan.isEmpty())
                {
                    ColonyAutopilot.LOGGER.warn("Growth plan {} resolved to no steps — the growth director stays idle (placement AND daily tending); to stop only new buildings use growth.expansion instead",
                      file.getFileName());
                }
                ColonyAutopilot.LOGGER.info("Growth plan loaded: {} steps from {}", plan.size(), file.getFileName());
                return plan;
            }
        }
        catch (final Exception e)
        {
            ColonyAutopilot.LOGGER.warn("Could not load {} — using the built-in growth plan", file, e);
            return defaultPlan();
        }
    }

    private static void writeDefault(final Path file) throws Exception
    {
        final List<JsonStep> steps = new ArrayList<>();
        for (final GrowthDirector.ResolvedStep step : defaultPlan())
        {
            final JsonStep json = new JsonStep();
            json.hut = BuiltInRegistries.BLOCK.getKey(step.hut()).toString();
            json.name = step.name();
            json.minCitizens = step.minCitizens();
            json.targetCount = step.targetCount();
            json.targetLevel = step.targetLevel();
            steps.add(json);
        }
        try (Writer writer = Files.newBufferedWriter(file))
        {

            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(steps, writer);
        }
        ColonyAutopilot.LOGGER.info("Wrote default growth plan to {}", file);
    }

    private static List<GrowthDirector.ResolvedStep> defaultPlan()
    {
        final List<GrowthDirector.ResolvedStep> steps = new ArrayList<>(List.of(

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTownHall, "Town Hall", 0, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutBuilder, "Builder's Hut", 0, 3),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTownHall, "Town Hall", 0, 1, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTavern, "Tavern", 0, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 0, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutGuardTower, "Guard Tower", 0, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 4, 2),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutFarmer, "Farm", 4, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutWareHouse, "Warehouse", 4, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutDeliveryman, "Courier's Hut", 5, 1),

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutCook, "Dining Hall", 5, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutUniversity, "University", 6, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 6, 3),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutLumberjack, "Lumberjack's Hut", 6, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutFisherman, "Fisher's Hut", 6, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutSawmill, "Sawmill", 7, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutBaker, "Bakery", 7, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 8, 4),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutMiner, "Mine", 8, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutSmeltery, "Smeltery", 8, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutGuardTower, "Guard Tower", 8, 2),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 9, 5),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutStonemason, "Stonemason's Hut", 9, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutStoneSmeltery, "Stone Smeltery", 9, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTownHall, "Town Hall", 10, 1, 2),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 10, 6),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutBlacksmith, "Blacksmith's Hut", 10, 1),

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutBarracks, "Barracks", 10, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutComposter, "Composter's Hut", 11, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutLibrary, "Library", 11, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 12, 7),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHospital, "Hospital", 12, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutSchool, "School", 12, 1),

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutDeliveryman, "Courier's Hut", 12, 2),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutGuardTower, "Guard Tower", 12, 3),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 13, 8),

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutShepherd, "Shepherd's Hut", 13, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutCowboy, "Cowhand's Hut", 13, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutChickenHerder, "Chicken Farmer's Hut", 13, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutSwineHerder, "Swineherd's Hut", 13, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 14, 9),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutKitchen, "Kitchen", 14, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutMechanic, "Mechanic's Hut", 14, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutFletcher, "Fletcher's Hut", 14, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 15, 10),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutGlassblower, "Glassblower's Hut", 15, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutDyer, "Dyer's Hut", 15, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutSifter, "Sifter's Hut", 15, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutCrusher, "Crusher's Hut", 15, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 16, 11),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutPlantation, "Plantation", 16, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutBeekeeper, "Apiary", 16, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutRabbitHutch, "Rabbit Hutch", 16, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutFlorist, "Flower Shop", 16, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutConcreteMixer, "Concrete Mixer's Hut", 17, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTownHall, "Town Hall", 18, 1, 3),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 18, 12),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutBarracks, "Barracks", 18, 2),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTownHall, "Town Hall", 20, 1, 4),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 20, 13),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutArchery, "Archery Range", 20, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutCombatAcademy, "Combat Academy", 20, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutEnchanter, "Enchanter's Tower", 20, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutTownHall, "Town Hall", 22, 1, 5),

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutHome, "Residence", 22, 13),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutMysticalSite, "Mystical Site", 22, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutAlchemist, "Alchemist Tower", 22, 1),
          new GrowthDirector.ResolvedStep(ModBlocks.blockHutNetherWorker, "Nether Mine", 22, 1),

          new GrowthDirector.ResolvedStep(ModBlocks.blockHutPlantation, "Plantation", 22, 2)));

        return AutopilotConfig.live(AutopilotConfig.NO_GRAVES) ? steps : withGraveyard(steps);
    }

    static List<GrowthDirector.ResolvedStep> withGraveyard(final List<GrowthDirector.ResolvedStep> plan)
    {
        for (final GrowthDirector.ResolvedStep step : plan)
        {
            if (step.hut() == ModBlocks.blockHutGraveyard)
            {
                return plan;
            }
        }
        final List<GrowthDirector.ResolvedStep> steps = new ArrayList<>(plan);
        final GrowthDirector.ResolvedStep graveyard = new GrowthDirector.ResolvedStep(ModBlocks.blockHutGraveyard, "Graveyard", 17, 1);
        for (int i = 0; i < steps.size(); i++)
        {
            if (steps.get(i).hut() == ModBlocks.blockHutConcreteMixer)
            {
                steps.add(i + 1, graveyard);
                return steps;
            }
        }
        steps.add(graveyard);
        return steps;
    }
}
