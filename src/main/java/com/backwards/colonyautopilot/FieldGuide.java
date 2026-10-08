// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

final class FieldGuide
{
    private static final String TITLE = "Colony Autopilot Guide";
    private static final String AUTHOR = "Colony Autopilot";

    private FieldGuide()
    {
    }

    static ItemStack book()
    {

        final List<Filterable<Component>> pages = List.of(
          page("FIELD GUIDE",
            "Place the Town Hall and walk away — the village builds itself.\n\n"
              + "Most Spore items are renamed player-heads, hidden from JEI. Craft them (next pages) or use the commands at the back."),

          page("THE INFECTION",
            "A fungal plague. Mounds & tendrils grow 'creep' and spawn infected mobs; the mycelium "
              + "debuff drains you, and if it kills you, you turn."),

          page("STOPPING IT",
            "SPREAD IS LOCAL — only from a mound/tendril in loaded chunks. KILL THE MOUND to stop "
              + "it. Cold is its weakness."),

          page("SZC — ICE NUKE",
            "[N][D][N]\n[D][*][D]\n[N][D][N]\n\n"
              + "N Netherite Ingot\nD Diamond Block\n* Nether Star\n\n"
              + "Drop it, wait ~1 min: freezes a huge area, cuts corruption ~19%."),

          page("SOLAR PUNCH",
            "[I][R][I]\n[S][*][S]\n[I][R][I]\n\n"
              + "I Iron Block\nR Blaze Rod\nS Slime Block\n* TNT\n\n"
              + "Drop it, wait ~1 min: a big blast; cuts corruption ~1%."),

          page("CRYO GRENADE",
            "[P][R][P]\n[P][*][P]\n[P][R][P]\n\n"
              + "P Compound Plate\nR Redstone\n* Ice Canister\n\n"
              + "THROW it: freezes blocks & mobs (~50x50). No corruption change."),

          page("MAGNUM +25",
            "[Pie][Cod][Mut]\n[Brd][*][Bef]\n[Stw][Cak][Sal]\n\n"
              + "* Spore Biomass (center); the rest are the cooked foods shown.\n\n"
              + "USE: drop (Q) on the giant mound's biomass floor."),

          page("CENA +50",
            "[GCr][NWt][GMl]\n[GAp][*][GAp]\n[GMl][NWt][GCr]\n\n"
              + "GCr Golden Carrot\nNWt Nether Wart\nGMl Glistering Melon\nGAp Golden Apple\n* Biomass"),

          page("DESERTUM +100",
            "[DBr][DBr][DBr]\n[EAp][*][EAp]\n[Cak][Cak][Cak]\n\n"
              + "DBr Dragon's Breath\nEAp Ench. Golden Apple\nCak Cake  * Biomass"),

          page("THE FINAL FIGHT",
            "At 99% corruption, ONE more offering opens the arena.\n\n"
              + "You land in an EMPTY arena."),

          page("THE GUARDIANS",
            "4 Guardians: Abundantia, Vis, Spes, Amor.\n\n"
              + "The first wakes within 20 min; chat names the spot."),

          page("THE EYE KEYS",
            "Each drops an EYE. RIGHT-CLICK it or the next one never wakes.\n\n"
              + "After the 4th, the tower rises. Kill the 'Nunny' Proto to end it."),

          page("GET ITEMS",
            "Cheats on.\n\n"
              + "/function inqui:craft_ice_nuke  (SZC)\n\n"
              + "/give @s sporeinquisition:cryogenic_grenade\n\n"
              + "Solar Punch: craft it, no command."),

          page("OFFERINGS",
            "/function inqui:gifts/gift1  (Magnum)\n\n"
              + "/function inqui:gifts/gift2  (Cena)\n\n"
              + "/function inqui:gifts/gift3  (Desertum)"),

          page("MENU",
            "/function inqui:0_config\n\n"
              + "Opens the Inquisition menu: offerings, corruption slider, summon, end."),

          page("AUTOPILOT",
            "/colonyautopilot ...\n"
              + "on | off | guide\n"
              + "expansion on|off\n"
              + "golems on|off\n"
              + "status | report\n"
              + "treasury | exchange\n"
              + "problems [on|off]\n"
              + "settings [section]\n"
              + "set <key> <value>\n"
              + "chat on|off\n"
              + "zone wand|list|clear"),

          page("INFECTION",
            "Summon the RedNight:\n/function inqui:summon_primordial_mound\n\n"
              + "END the infection:\n/scoreboard players set !finale finalitas -1"),

          page("CLEANUP",
            "Clear corruption:\n/scoreboard players set !finale proto 0")
        );

        final ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
          new WrittenBookContent(Filterable.passThrough(TITLE), AUTHOR, 0, pages, true));
        return book;
    }

    private static Filterable<Component> page(final String heading, final String body)
    {
        return Filterable.passThrough(
          Component.empty()
            .append(Component.literal(heading + "\n\n").withStyle(ChatFormatting.BOLD))
            .append(Component.literal(body)));
    }
}
