// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class OptionalTargetsPlugin implements IMixinConfigPlugin
{

    private static final String[][] OPTIONAL = {
      {"steve_gall.minecolonies_tweaks.", "minecolonies_tweaks"}};

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName)
    {
        for (final String[] optional : OPTIONAL)
        {
            if (targetClassName.startsWith(optional[0]))
            {

                try
                {
                    return LoadingModList.get().getModFileById(optional[1]) != null;
                }
                catch (final RuntimeException | LinkageError e)
                {
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public void onLoad(final String mixinPackage)
    {
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo)
    {
    }
}
