package l1j.server.server.model.item.effects;

import static l1j.server.server.model.item.ItemStat.*;
import l1j.server.server.model.item.ItemEffectRegistry;

public final class EarringDefs {
    private EarringDefs() {}

    public static void load() {
    	LoomtisBlueEarring();
    	BlessLoomtisBlueEarring();
    }

    private static void LoomtisBlueEarring() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);
        int[] rec = { 2, 6, 8, 10, 12, 14, 16, 18, 20, 22 };
        for (int lvl = 0; lvl < rec.length; lvl++) {
            d.addEnchant(lvl, POTION_AMT, rec[lvl]);
            d.addEnchant(lvl, HEAL_DEF,   rec[lvl]);
        }
        d.addEnchant(5, AC, -1);
        d.addEnchant(6, AC, -2);
        d.addEnchant(7, AC, -2);
        d.addEnchant(8, AC, -3);
        d.addEnchant(9, AC, -5);
        ItemEffectRegistry.register(22230, d);
    }
    
    private static void BlessLoomtisBlueEarring() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);
        int[] rec = { 2, 6, 8, 12, 14, 16, 18, 20, 22, 24 };
        for (int lvl = 0; lvl < rec.length; lvl++) {
            d.addEnchant(lvl, POTION_AMT, rec[lvl]);
            d.addEnchant(lvl, HEAL_DEF,   rec[lvl]);
        }
        d.addEnchant(4, AC, -1);
        d.addEnchant(5, AC, -2);
        d.addEnchant(6, AC, -2);
        d.addEnchant(7, AC, -3);
        d.addEnchant(8, AC, -4);
        d.addEnchant(9, AC, -6);
        ItemEffectRegistry.register(222338, d);
    }
}