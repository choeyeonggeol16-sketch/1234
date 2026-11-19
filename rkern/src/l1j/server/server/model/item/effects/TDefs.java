package l1j.server.server.model.item.effects;

import static l1j.server.server.model.item.ItemStat.*;

import l1j.server.server.model.item.ItemEffectRegistry;

/**
 * 티셔츠
 */
public final class TDefs {

    private TDefs() {}

    public static void load() {
        MaestroTshirt();
    }

    private static void MaestroTshirt() {

        ItemEffectRegistry.Def d =
                new ItemEffectRegistry.Def(null, false);

        for (int enchant = 0; enchant <= 11; enchant++) {

            int mr = 12;
            int allResis = 1;
            int dmgRed = 0;
            int pvpDmgRed = 0;

            if (enchant >= 6) {
                int bonus = enchant - 5;

                mr += bonus;
                allResis += bonus;
                dmgRed = bonus;
                pvpDmgRed = bonus;
            }

            d.addEnchant(enchant, MR, mr);
            d.addEnchant(enchant, ALL_RESIS, allResis);

            if (dmgRed > 0) {
                d.addEnchant(enchant, DMG_RED, dmgRed);
            }

            if (pvpDmgRed > 0) {
                d.addEnchant(enchant, PVP_DMG_RED, pvpDmgRed);
            }
        }

        ItemEffectRegistry.register(998171, d);
    }
}