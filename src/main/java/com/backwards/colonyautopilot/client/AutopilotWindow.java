// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.client;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.net.SettingsPayloads.ChangeSetting;
import com.backwards.colonyautopilot.net.SettingsPayloads.OpenSettings;
import com.backwards.colonyautopilot.net.SettingsPayloads.Row;
import com.backwards.colonyautopilot.net.SettingsPayloads.SettingsSnapshot;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.controls.Tooltip;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.townhall.WindowAlliancePage;
import com.minecolonies.core.client.gui.townhall.WindowCitizenPage;
import com.minecolonies.core.client.gui.townhall.WindowInfoPage;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import com.minecolonies.core.client.gui.townhall.WindowPermissionsPage;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.client.gui.townhall.WindowStatsPage;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.WindowConstants.*;

public final class AutopilotWindow extends AbstractWindowSkeleton
{
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "gui/autopilot.xml");

    private final ColonyId colony;
    private final String colonyName;
    private final Consumer<SettingsSnapshot> viewer = this::onSnapshot;

    private final ScrollingList sectionList;
    private final ScrollingList settingsList;
    private final Text status;

    private SettingsSnapshot snapshot;
    private final List<String> sections = new ArrayList<>();
    private String section;

    private final List<Row> shown = new ArrayList<>();

    private final Map<Pane, String> paneKeys = new IdentityHashMap<>();
    private final Map<Pane, Tooltip> tooltips = new IdentityHashMap<>();

    private final Map<Pane, String> tooltipKeys = new IdentityHashMap<>();

    private final Map<String, String> pending = new HashMap<>();

    private int inFlight;

    public AutopilotWindow(final IColonyView colonyView, final BuildingTownHall.View townHall)
    {
        super(LAYOUT);
        this.colony = new ColonyId(colonyView.getID(), colonyView.getDimension());
        this.colonyName = colonyView.getName();

        if (townHall != null)
        {
            registerButton(BUTTON_ACTIONS, () -> new WindowMainPage(townHall).open());
            registerButton(BUTTON_INFOPAGE, () -> new WindowInfoPage(townHall).open());
            registerButton(BUTTON_PERMISSIONS, () -> new WindowPermissionsPage(townHall).open());
            registerButton(BUTTON_CITIZENS, () -> new WindowCitizenPage(townHall).open());
            registerButton(BUTTON_STATS, () -> new WindowStatsPage(townHall).open());
            registerButton(BUTTON_SETTINGS, () -> new WindowSettings(townHall).open());
            registerButton(BUTTON_ALLIANCE, () -> new WindowAlliancePage(townHall).open());

            ExchangeWindow.addBookmark(this, townHall);
        }
        else
        {
            for (final String id : new String[] {BUTTON_ACTIONS, BUTTON_INFOPAGE, BUTTON_PERMISSIONS, BUTTON_CITIZENS, BUTTON_STATS, BUTTON_SETTINGS, BUTTON_ALLIANCE})
            {
                findPaneByID(id).hide();
                findPaneByID(id + "0").hide();
            }
        }

        final ButtonImage ribbon = findPaneOfTypeByID(BUTTON_SETTINGS + "1", ButtonImage.class);
        ribbon.setText(Component.translatable("colonyautopilot.gui.title"));
        ribbon.show();

        status = findPaneOfTypeByID("status", Text.class);
        status.setText(Component.translatable("colonyautopilot.gui.loading"));

        sectionList = findPaneOfTypeByID("sections", ScrollingList.class);
        sectionList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return sections.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                final String name = sections.get(index);
                final ButtonImage button = rowPane.findPaneOfTypeByID("section", ButtonImage.class);
                button.setText(Component.translatableWithFallback("colonyautopilot.gui.section." + name, capitalise(name)));
                button.setEnabled(!name.equals(section));
            }
        });
        registerButton("section", button -> {
            final int index = sectionList.getListElementIndexByPane(button);
            if (index >= 0 && index < sections.size())
            {
                section = sections.get(index);
                rebuildShown();
            }
        });

        settingsList = findPaneOfTypeByID("keys", ScrollingList.class);
        settingsList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return shown.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                drawRow(shown.get(index), rowPane);
            }
        });
        registerButton("toggle", button -> {
            final Row row = rowOf(button);
            if (row != null)
            {

                send(row, pending.getOrDefault(row.path(), row.value()).equals("true") ? "false" : "true", false);
            }
        });
        registerButton("apply", button -> {
            final Row row = rowOf(button);
            if (row != null)
            {
                send(row, button.getParent().findPaneOfTypeByID("field", TextField.class).getText().trim(), false);
                Pane.clearFocus();
            }
        });
        registerButton("reset", button -> {
            final Row row = rowOf(button);
            if (row != null)
            {
                send(row, "", true);
            }
        });

        ClientSettingsCache.present(viewer);
        PacketDistributor.sendToServer(new OpenSettings(colony));
    }

    @Override
    public void onClosed()
    {
        super.onClosed();
        ClientSettingsCache.withdraw(viewer);
    }

    @Override
    public boolean onUnhandledKeyTyped(final int ch, final int key)
    {
        if ((key == 257 || key == 335) && Pane.getFocus() instanceof TextField field && "field".equals(field.getID()))
        {
            final int index = settingsList.getListElementIndexByPane(field);
            if (index >= 0 && index < shown.size())
            {
                send(shown.get(index), field.getText().trim(), false);
                Pane.clearFocus();
                return true;
            }
        }
        return super.onUnhandledKeyTyped(ch, key);
    }

    private void onSnapshot(final SettingsSnapshot fresh)
    {
        if (!fresh.colony().equals(colony))
        {
            return;
        }
        snapshot = fresh;

        for (final Row row : fresh.rows())
        {
            if (row.value().equals(pending.get(row.path())))
            {
                pending.remove(row.path());
            }
        }
        inFlight = Math.max(0, inFlight - 1);
        if (inFlight == 0)
        {
            pending.clear();
        }
        if (fresh.rows().isEmpty())
        {

            status.setText(Component.translatable("colonyautopilot.gui.noaccess", colonyName));
            sections.clear();
            section = null;
            rebuildShown();
            return;
        }
        sections.clear();
        for (final Row row : fresh.rows())
        {
            final String name = sectionOf(row.path());
            if (!sections.contains(name))
            {
                sections.add(name);
            }
        }
        if (section == null || !sections.contains(section))
        {
            section = sections.get(0);
        }
        status.setText(Component.translatable(fresh.canEdit() ? "colonyautopilot.gui.editable" : "colonyautopilot.gui.readonly", colonyName));
        rebuildShown();
    }

    private void rebuildShown()
    {
        shown.clear();
        if (snapshot != null && section != null)
        {
            for (final Row row : snapshot.rows())
            {
                if (sectionOf(row.path()).equals(section))
                {
                    shown.add(row);
                }
            }
        }
        settingsList.refreshElementPanes();
    }

    private void drawRow(final Row row, final Pane rowPane)
    {
        final boolean fresh = !row.path().equals(paneKeys.put(rowPane, row.path()));
        final boolean editable = snapshot.canEdit() && !row.serverWide();
        final Text name = rowPane.findPaneOfTypeByID("name", Text.class);
        final MutableComponent label = Component.translatableWithFallback("colonyautopilot.gui.key." + row.path(), humanise(row.path().substring(section.length() + 1)));
        name.setText(row.serverWide() ? label.withStyle(ChatFormatting.DARK_GRAY) : row.overridden() ? label.withStyle(ChatFormatting.DARK_BLUE) : label);

        final ButtonImage toggle = rowPane.findPaneOfTypeByID("toggle", ButtonImage.class);
        final TextField field = rowPane.findPaneOfTypeByID("field", TextField.class);
        final ButtonImage apply = rowPane.findPaneOfTypeByID("apply", ButtonImage.class);
        if (row.type().equals("boolean"))
        {
            toggle.show();
            field.hide();
            apply.hide();

            toggle.setText(Component.translatable(pending.getOrDefault(row.path(), row.value()).equals("true") ? ON : OFF));
            toggle.setEnabled(editable);
        }
        else
        {
            toggle.hide();
            field.show();
            apply.show();
            if (fresh || !field.isFocus())
            {
                field.setText(pending.getOrDefault(row.path(), row.value()));
            }
            field.setEnabled(editable);
            apply.setEnabled(editable);
        }

        final boolean resettable = editable && row.overridden();
        rowPane.findPaneOfTypeByID("reset", ButtonImage.class).setVisible(resettable);

        final Text info = rowPane.findPaneOfTypeByID("info", Text.class);
        info.setVisible(!resettable);
        info.setText(row.serverWide()
          ? Component.translatable("colonyautopilot.gui.server") : row.overridden() && !resettable ? Component.translatable("colonyautopilot.gui.own") : Component.empty());

        final Tooltip tip = tooltips.computeIfAbsent(rowPane, pane -> PaneBuilders.tooltipBuilder().hoverPane(pane.findPaneByID("name")).build());

        final String tipKey = row.path() + '|' + row.value() + '|' + row.def() + '|' + row.overridden() + '|' + row.serverWide();
        if (!tipKey.equals(tooltipKeys.put(rowPane, tipKey)))
        {
            final List<MutableComponent> lines = new ArrayList<>();
            lines.add(Component.literal(row.path()).withStyle(ChatFormatting.GRAY));
            if (!row.comment().isBlank())
            {
                lines.add(Component.literal(row.comment()));
            }
            lines.add(Component.translatable("colonyautopilot.gui.tip.default", row.def()).withStyle(ChatFormatting.GRAY));
            if (!row.min().isEmpty())
            {
                lines.add(Component.translatable("colonyautopilot.gui.tip.range", row.min(), row.max()).withStyle(ChatFormatting.GRAY));
            }
            if (row.serverWide())
            {

                lines.add(Component.translatable(row.path().equals("economy.progressionMode") ? "colonyautopilot.gui.tip.locked"
                                                   : "colonyautopilot.gui.tip.server").withStyle(ChatFormatting.DARK_GRAY));
            }
            else if (row.overridden())
            {
                lines.add(Component.translatable("colonyautopilot.gui.tip.own").withStyle(ChatFormatting.DARK_BLUE));
            }
            tip.setText(lines);
        }
    }

    private Row rowOf(final Button button)
    {
        final int index = settingsList.getListElementIndexByPane(button);
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    private void send(final Row row, final String text, final boolean reset)
    {
        if (reset)
        {

            pending.put(row.path(), row.def());
        }
        else
        {

            pending.put(row.path(), text);
        }
        inFlight++;
        PacketDistributor.sendToServer(new ChangeSetting(colony, row.path(), text, reset));
    }

    private static String sectionOf(final String path)
    {
        final int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }

    private static String capitalise(final String word)
    {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static String humanise(final String key)
    {
        final StringBuilder out = new StringBuilder(key.length() + 4);
        for (int i = 0; i < key.length(); i++)
        {
            final char c = key.charAt(i);
            if (i == 0)
            {
                out.append(Character.toUpperCase(c));
            }
            else if (Character.isUpperCase(c))
            {
                out.append(' ').append(Character.toLowerCase(c));
            }
            else
            {
                out.append(c);
            }
        }
        return out.toString();
    }
}
