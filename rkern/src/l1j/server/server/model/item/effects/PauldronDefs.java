package l1j.server.server.model.item.effects;

import static l1j.server.server.model.item.ItemStat.*;

import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;

/**
 * 견갑/각반 4종 
 */
public final class PauldronDefs {
    private PauldronDefs() {}

    public static final int RELIC_MR     = 474705;  // 드래곤슬레이어각반
    public static final int RELIC_MELEE  = 474702;  // 지휘관의 견갑
    public static final int RELIC_RANGED = 474703;  // 사이하 견갑
    public static final int RELIC_MAGIC  = 474704;  // 대마법사의 견갑

    private static final int MAX_ENCHANT = 15;   

    public static void load() {
        ItemEffectRegistry.register(RELIC_MR,     relicMr(), "PauldronDefs");
        ItemEffectRegistry.register(RELIC_MELEE,  relicPauldron(HIT),      "PauldronDefs");
        ItemEffectRegistry.register(RELIC_RANGED, relicPauldron(BOW_HIT),  "PauldronDefs");
        ItemEffectRegistry.register(RELIC_MAGIC,  relicPauldron(MAGIC_HIT),"PauldronDefs");
    }

    /** 드래곤슬레이어각반 — MR 특화 */
    private static ItemEffectRegistry.Def relicMr() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def();
        d.addEnchant(-1, MR, 7)
         .addEnchant(-1, PVP_DMG_RED, 2)
         .addEnchant(-1, MAX_MP, 50)
         .addEnchant(-1, MAX_HP, 100)
         .addEnchant(-1, DMG_RED, 1)
         .addEnchant(-1, ALL_RESIS, 2);
        for (int e = 5; e <= MAX_ENCHANT; e++) {  
            d.addEnchant(e, MR, (e - 4) * 2);
            d.addEnchant(e, PVP_DMG_RED, e - 4);
        }
        for (int e = 7; e <= MAX_ENCHANT; e++) {  
            d.addEnchant(e, MAX_HP, (e - 6) * 50);
        }
        return d;
    }

    private static ItemEffectRegistry.Def relicPauldron(ItemStat hitStat) {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def();
        d.addEnchant(-1, hitStat, 2)
         .addEnchant(-1, MAX_MP, 40);
        for (int e = 5; e <= MAX_ENCHANT; e++) {
            d.addEnchant(e, MAX_MP,    Math.min((e - 4) * 10, 60));
            d.addEnchant(e, ALL_RESIS, Math.min(e - 4, 6));
            d.addEnchant(e, MAX_HP,    hpByEnchant(e));
            d.addEnchant(e, PVP_DMG_RED,   pvpByEnchant(e));
        }
        for (int e = 7; e <= MAX_ENCHANT; e++) {   // +7~ 명중 추가
            d.addEnchant(e, hitStat, Math.min(e - 6, 4));
        }
        return d;
    }

    private static int hpByEnchant(int e) {
        if (e == 5) return 50;
        if (e <= 8) return 50 + (e - 5) * 10;
        if (e == 9) return 100;
        return 150;
    }

    private static int pvpByEnchant(int e) {
        if (e == 5) return 3;
        if (e <= 9) return 3 + (e - 5);
        return 10;
    }
}