// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;

final class RecipeErrorFilter extends AbstractFilter
{
    private static final String RECIPE_MANAGER = "net.minecraft.world.item.crafting.RecipeManager";

    private static final String ABSENT_RECIPE = "recipe farmersdelight:";

    private RecipeErrorFilter()
    {
        super(Filter.Result.DENY, Filter.Result.NEUTRAL);
    }

    static void install()
    {
        try
        {
            final RecipeErrorFilter filter = new RecipeErrorFilter();
            filter.start();
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().getRootLogger().addFilter(filter);
            context.updateLoggers();
        }
        catch (final Exception e)
        {
            ColonyAutopilot.LOGGER.info("Could not install the FarmersDelight recipe-error filter — the log will keep the harmless recipe warnings", e);
        }
    }

    @Override
    public Filter.Result filter(final LogEvent event)
    {

        if (event.getLevel() != null && event.getLevel().isMoreSpecificThan(Level.ERROR)
              && RECIPE_MANAGER.equals(event.getLoggerName())
              && event.getMessage() != null
              && event.getMessage().getFormattedMessage().contains(ABSENT_RECIPE))
        {

            if (AutopilotConfig.SPEC.isLoaded() && !AutopilotConfig.MUTE_FD_RECIPE_ERRORS.get())
            {
                return Filter.Result.NEUTRAL;
            }
            return Filter.Result.DENY;
        }
        return Filter.Result.NEUTRAL;
    }
}
