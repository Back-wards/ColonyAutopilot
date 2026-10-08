// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.mixin.client;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.client.AutopilotWindow;
import com.ldtteam.blockui.Loader;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WindowSettings.class)
public abstract class WindowSettingsMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void colonyautopilot$addAutopilotButton(final BuildingTownHall.View townHall, final CallbackInfo ci)
    {
        final AbstractWindowSkeleton window = (AbstractWindowSkeleton) (Object) this;
        Loader.createFromXMLFile(ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "gui/townhall_button.xml"), window);
        window.registerButton("colonyautopilot", () -> new AutopilotWindow(townHall.getColony(), townHall).open());
    }
}
