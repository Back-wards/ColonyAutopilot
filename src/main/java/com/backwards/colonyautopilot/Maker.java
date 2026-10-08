// Colony Autopilot - created by Backwards - https://www.curseforge.com/members/backwards/projects - Copyright (C) 2026 Backwards - GPL-3.0
package com.backwards.colonyautopilot;

import net.neoforged.fml.ModContainer;

final class Maker
{
    private static final int[] MARK = {24, 6, 23, 234, 249, 250, 218, 209, 177};
    private static final int FINGERPRINT = -647214893;

    static final String NAME = unfold();

    private Maker()
    {
    }

    private static String unfold()
    {
        final StringBuilder name = new StringBuilder(MARK.length);
        for (int i = 0; i < MARK.length; i++)
        {
            name.append((char) (MARK[i] ^ ((0x5A + 13 * i) & 0xFF)));
        }
        return name.toString();
    }

    private static int fingerprint(final String text)
    {
        int hash = 17;
        for (int i = 0; i < text.length(); i++)
        {
            hash = hash * 37 + text.charAt(i);
        }
        return hash;
    }

    static boolean intact(final ModContainer container)
    {
        if (fingerprint(NAME) != FINGERPRINT)
        {
            return false;
        }
        final Object declared = container.getModInfo().getConfig().getConfigElement("authors").orElse(null);
        return declared != null && declared.toString().contains(NAME);
    }
}
