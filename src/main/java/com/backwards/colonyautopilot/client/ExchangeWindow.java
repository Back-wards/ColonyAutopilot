// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot.client;

import com.backwards.colonyautopilot.ColonyAutopilot;
import com.backwards.colonyautopilot.Treasury;
import com.backwards.colonyautopilot.net.ExchangePayloads.OpenExchange;
import com.backwards.colonyautopilot.net.ExchangePayloads.RelayRoads;
import com.backwards.colonyautopilot.net.SettingsPayloads.OpenSettings;
import com.backwards.colonyautopilot.net.SettingsPayloads.SettingsSnapshot;
import com.ldtteam.blockui.Loader;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.Tooltip;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ExchangeWindow extends AbstractWindowTownHall
{

    private static final String PAGE = "colonyautopilot_exchange.xml";

    private static final ResourceLocation BOOKMARK = ResourceLocation.fromNamespaceAndPath(ColonyAutopilot.MOD_ID, "gui/townhall_exchange_tab.xml");
    private static final String ID = "exchange";

    private static final String[][] BOOKMARKS = {{"actions", "actionsExt"}, {"infopage", "infoExt"}, {"permissions", "permissionsExt"},
      {"citizens", "citizensExt"}, {"happiness", "happinessExt"}, {"alliances", "alliancesExt"}, {"settings", "settingsExt"}, {ID, ID + "Ext"}};

    private static final int FIRST_SEAL_Y = 66;
    private static final int SEAL_STEP = 22;

    private static final int TEXT_WIDTH = 138;

    private final ColonyId colony;
    private final String colonyName;
    private final Consumer<SettingsSnapshot> viewer = this::onSnapshot;

    private final Text balance;
    private final ButtonImage trade;
    private final ButtonImage roads;

    private final Tooltip roadsTip;
    private final ScrollingList summaryList;
    private final List<Component> summaryRows = new ArrayList<>();
    private final ScrollingList ledgerList;
    private final List<Component> ledgerRows = new ArrayList<>();

    public ExchangeWindow(final BuildingTownHall.View townHall)
    {
        super(townHall, PAGE);
        final IColonyView colonyView = buildingView.getColony();
        this.colony = new ColonyId(colonyView.getID(), colonyView.getDimension());
        this.colonyName = colonyView.getName();
        respace(this);

        balance = findPaneOfTypeByID("balance", Text.class);
        trade = findPaneOfTypeByID("trade", ButtonImage.class);

        registerButton("trade", () -> PacketDistributor.sendToServer(new OpenExchange(colony)));
        roads = findPaneOfTypeByID("relayroads", ButtonImage.class);

        registerButton("relayroads", () -> PacketDistributor.sendToServer(new RelayRoads(colony)));
        roadsTip = PaneBuilders.tooltipBuilder().hoverPane(roads).build();
        addLine(summaryRows, Component.translatable("colonyautopilot.gui.exchange.loading"));
        summaryList = findPaneOfTypeByID("summary", ScrollingList.class);
        summaryList.setDataProvider(provider(summaryRows));
        ledgerList = findPaneOfTypeByID("ledger", ScrollingList.class);
        ledgerList.setDataProvider(provider(ledgerRows));

        ClientSettingsCache.present(viewer);
        PacketDistributor.sendToServer(new OpenSettings(colony));
    }

    @Override
    protected String getWindowId()
    {
        return ID;
    }

    @Override
    public void onClosed()
    {
        super.onClosed();
        ClientSettingsCache.withdraw(viewer);
    }

    public static void addBookmark(final AbstractWindowSkeleton window, final BuildingTownHall.View townHall)
    {
        Loader.createFromXMLFile(BOOKMARK, window);
        respace(window);
        window.registerButton(ID, () -> new ExchangeWindow(townHall).open());
    }

    private static void respace(final AbstractWindowSkeleton window)
    {
        for (int i = 0; i < BOOKMARKS.length; i++)
        {
            final Pane seal = window.findPaneByID(BOOKMARKS[i][0]);
            if (seal == null)
            {
                continue;
            }
            final int shift = FIRST_SEAL_Y + i * SEAL_STEP - seal.getY();
            for (final Pane pane : new Pane[] {seal, window.findPaneByID(BOOKMARKS[i][0] + "0"), window.findPaneByID(BOOKMARKS[i][1])})
            {
                if (pane != null)
                {
                    pane.moveBy(0, shift);
                }
            }
        }
    }

    private static ScrollingList.DataProvider provider(final List<Component> lines)
    {
        return new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return lines.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                rowPane.findPaneOfTypeByID("line", Text.class).setText(lines.get(index));
            }
        };
    }

    private void onSnapshot(final SettingsSnapshot fresh)
    {
        if (!fresh.colony().equals(colony))
        {
            return;
        }
        summaryRows.clear();
        ledgerRows.clear();

        final boolean seen = !fresh.rows().isEmpty();
        balance.setVisible(seen);
        trade.setVisible(seen && fresh.exchange());

        roads.setVisible(seen);
        roads.setEnabled(fresh.roads().isEmpty());
        roadsTip.setText(fresh.roads().isEmpty() ? Component.translatable("colonyautopilot.gui.roads.tip") : Component.literal(fresh.roads()));
        if (seen)
        {
            drawTreasury(fresh);
        }
        else
        {
            addLine(summaryRows, Component.translatable("colonyautopilot.gui.exchange.noaccess", colonyName));
        }
        summaryList.refreshElementPanes();
        ledgerList.refreshElementPanes();
    }

    private void drawTreasury(final SettingsSnapshot fresh)
    {
        balance.setText(Component.translatable("colonyautopilot.gui.treasury", fresh.treasuryBucks()));
        if (!fresh.progression())
        {
            addLine(summaryRows, Component.translatable("colonyautopilot.gui.free"));
        }
        else
        {
            addLine(summaryRows, Component.translatable("colonyautopilot.gui.waiting", fresh.waitingRequests(), fresh.waitingResearch(), fresh.waitingCures()));
            if (!fresh.placementHold().isEmpty())
            {
                addLine(summaryRows, Component.literal(fresh.placementHold()).withStyle(ChatFormatting.GRAY));
            }
            if (!fresh.saving().isEmpty())
            {
                addLine(summaryRows, Component.translatable("colonyautopilot.gui.saving", fresh.savedBucks(), fresh.savingBucks(), fresh.saving()));
            }
            else if (!fresh.goal().isEmpty())
            {
                addLine(summaryRows, Component.translatable("colonyautopilot.gui.goal", fresh.goal(), bucksText(fresh.goalPrice(), Math.max(1, fresh.perBuck()))));
            }
            else
            {
                addLine(summaryRows, Component.translatable("colonyautopilot.gui.saving.none"));
            }
        }

        if (fresh.progression() && !fresh.goal().isEmpty() && fresh.goalStocked() < fresh.goalPrice())
        {
            final ResourceLocation good = fresh.goalGood().isEmpty() ? null : ResourceLocation.tryParse(fresh.goalGood());
            final Component goodName = good == null ? Component.literal("?")
                                         : new ItemStack(BuiltInRegistries.ITEM.get(good)).getHoverName();
            addLine(summaryRows, Component.translatable("colonyautopilot.gui.goal.stocked", bucksText(fresh.goalStocked(), Math.max(1, fresh.perBuck())),
              goodName, fresh.goalWhere()).withStyle(ChatFormatting.DARK_GREEN));
        }

        final int lines = Treasury.Line.values().length;
        final long[] ledger = fresh.ledger();
        if (ledger.length == 1 + 2 * lines && fresh.perBuck() > 0)
        {
            ledgerDay(ledger, 1, Component.translatable("colonyautopilot.gui.ledger.today", ledger[0]), fresh.perBuck());

            if (ledger[0] > 0)
            {
                ledgerDay(ledger, 1 + lines, Component.translatable("colonyautopilot.gui.ledger.yesterday", ledger[0] - 1), fresh.perBuck());
            }
        }
    }

    private void ledgerDay(final long[] ledger, final int from, final Component heading, final int perBuck)
    {
        addLine(ledgerRows, heading.copy().withStyle(ChatFormatting.DARK_BLUE));
        long earned = 0;
        long spent = 0;
        for (final Treasury.Line line : Treasury.Line.values())
        {
            final long tally = ledger[from + line.ordinal()];
            if (line != Treasury.Line.EXCHANGE)
            {
                if (line.income)
                {
                    earned += tally;
                }
                else
                {
                    spent += tally;
                }
            }
        }
        final long exchanged = ledger[from + Treasury.Line.EXCHANGE.ordinal()];
        if (earned == 0 && spent == 0 && exchanged == 0)
        {
            addLine(ledgerRows, Component.translatable("colonyautopilot.gui.ledger.none"));
            return;
        }
        addLine(ledgerRows, Component.translatable("colonyautopilot.gui.ledger.earned", bucksText(earned, perBuck)));
        ledgerLines(ledger, from, true, perBuck);
        addLine(ledgerRows, Component.translatable("colonyautopilot.gui.ledger.spent", bucksText(spent, perBuck)));
        ledgerLines(ledger, from, false, perBuck);
        if (exchanged > 0)
        {
            addLine(ledgerRows, Component.translatable("colonyautopilot.gui.ledger.exchange", bucksText(exchanged, perBuck)).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void addLine(final List<Component> target, final Component line)
    {
        for (final FormattedText part : Minecraft.getInstance().font.getSplitter().splitLines(line, TEXT_WIDTH, Style.EMPTY))
        {
            target.add(Component.literal(part.getString()).withStyle(line.getStyle()));
        }
    }

    private void ledgerLines(final long[] ledger, final int from, final boolean income, final int perBuck)
    {
        for (final Treasury.Line line : Treasury.Line.values())
        {
            final long tally = ledger[from + line.ordinal()];
            if (line.income == income && line != Treasury.Line.EXCHANGE && tally > 0)
            {
                addLine(ledgerRows, Component.literal("  ").append(Component.translatable("colonyautopilot.gui.ledger." + line.name().toLowerCase(java.util.Locale.ROOT),
                  bucksText(tally, perBuck))));
            }
        }
    }

    private static String bucksText(final long credits, final int perBuck)
    {
        return String.format(java.util.Locale.ROOT, "%.2f", credits / (double) perBuck);
    }
}
