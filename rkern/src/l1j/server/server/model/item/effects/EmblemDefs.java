package l1j.server.server.model.item.effects;

import static l1j.server.server.model.item.ItemStat.*;
import l1j.server.server.model.item.ItemEffectRegistry;

public final class EmblemDefs {
    private EmblemDefs() {}

    public static void load() {
        WarriorsGuardianEmblem();
        ArchersGuardianEmblem();
        SageGuardianEmblem();
        WarriorsEmblem();
        ArchersEmblem();
        SageEmblem();     
        GuardianeEmblem();
    }
    
    private static void WarriorsEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        // 물약 회복량 / 치유 저항 
        int[] rec = { 2, 4, 8, 10, 12, 14, 16, 18}; 
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 1;
            d.addEnchant(lvl, POTION_AMT, rec[i]);
            d.addEnchant(lvl, HEAL_DEF,   rec[i]);
        }

        // 인챈트 4 : 명중 +1, MR +1
        d.addEnchant(4, HIT, 1);
        d.addEnchant(5, HIT, 1);
        d.addEnchant(6, HIT, 2);
        d.addEnchant(7, HIT, 3);
        d.addEnchant(8, HIT, 4);     
        
        d.addEnchant(5, DMG_RATE,  1);
        d.addEnchant(6, DMG_RATE,  2);
        d.addEnchant(7, DMG_RATE,  3);
        d.addEnchant(8, DMG_RATE,  4);
       
        ItemEffectRegistry.register(900127, d);
    }
    
    private static void ArchersEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        // 물약 회복량 / 치유 저항
        int[] rec = { 2, 4, 8, 10, 12, 14, 16, 18};
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 1;
            d.addEnchant(lvl, POTION_AMT, rec[i]);
            d.addEnchant(lvl, HEAL_DEF,   rec[i]);
        }

        // 인챈트 4 : 명중 +1, MR +1
        d.addEnchant(4, BOW_HIT, 1);
        d.addEnchant(5, BOW_HIT, 1);
        d.addEnchant(6, BOW_HIT, 2);
        d.addEnchant(7, BOW_HIT, 3);
        d.addEnchant(8, BOW_HIT, 4);     
        
        d.addEnchant(5, BOW_DMG,  1);
        d.addEnchant(6, BOW_DMG,  2);
        d.addEnchant(7, BOW_DMG,  3);
        d.addEnchant(8, BOW_DMG,  4);
       
        ItemEffectRegistry.register(900128, d);
    }
    
    private static void SageEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        // 물약 회복량 / 치유 저항
        int[] rec = { 2, 4, 8, 10, 12, 14, 16, 18}; 
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 1;
            d.addEnchant(lvl, POTION_AMT, rec[i]);
            d.addEnchant(lvl, HEAL_DEF,   rec[i]);
        }

        // 인챈트 4 : 명중 +1, MR +1
        d.addEnchant(4, MAGIC_HIT, 1);
        d.addEnchant(5, MAGIC_HIT, 1);
        d.addEnchant(6, MAGIC_HIT, 2);
        d.addEnchant(7, MAGIC_HIT, 3);
        d.addEnchant(8, MAGIC_HIT, 4);     
        
        d.addEnchant(5, SP,  1);
        d.addEnchant(6, SP,  2);
        d.addEnchant(7, SP,  3);
        d.addEnchant(8, SP,  4);
       
        ItemEffectRegistry.register(900129, d);
    }

    private static void GuardianeEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        // 경험치 보너스
        int[] rec = { 1, 2, 3, 4, 5, 6, 7, 8}; 
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 1;
            d.addEnchant(lvl, EXP_RATE, rec[i]);
        }

        d.addEnchant(4, MR, 1);
        d.addEnchant(5, MR, 2);
        d.addEnchant(6, MR, 3);
        d.addEnchant(7, MR, 4);
        d.addEnchant(8, MR, 5);     
       
        ItemEffectRegistry.register(900130, d);
    }

    private static void WarriorsGuardianEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        d.addEnchant(5, AC, -2);
        d.addEnchant(6, AC, -2);
        d.addEnchant(7, AC, -2);
        d.addEnchant(8, AC, -2);
        d.addEnchant(9, AC, -2);

        // 물약 회복량 / 치유 저항 : 인챈트 3~9
        int[] rec = { 8, 10, 12, 14, 16, 18, 20 }; // idx 0 = 인챈 3
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 3;
            d.addEnchant(lvl, POTION_AMT, rec[i]);
            d.addEnchant(lvl, HEAL_DEF,   rec[i]);
        }

        // 인챈트 4 : 명중 +1, MR +1
        d.addEnchant(4, HIT, 1);
        d.addEnchant(4, MR,  1);

        // 인챈트 5~9
        int[] dmg = { 1, 2, 3, 4, 5 };       // 근거리 대미지
        int[] mr  = { 4, 6, 8, 10, 12 };     // MR
        int[] bls = { 3, 5, 7, 10, 12};   // 축복 소모 효율
        for (int i = 0; i < 5; i++) {
            int lvl = i + 5;
            d.addEnchant(lvl, DMG_RATE,          dmg[i]);
            d.addEnchant(lvl, HIT,               i + 2);  // 2,3,4,5,6
            d.addEnchant(lvl, MR,                mr[i]);
            d.addEnchant(lvl, BLESS_CONSUMO,     bls[i]);
        }

        ItemEffectRegistry.register(900124, d);
    }
    
    private static void ArchersGuardianEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        d.addEnchant(5, AC, -2);
        d.addEnchant(6, AC, -2);
        d.addEnchant(7, AC, -2);
        d.addEnchant(8, AC, -2);
        d.addEnchant(9, AC, -2);

        // 물약 회복량 / 치유 저항 : 인챈트 5~9
        int[] rec = { 12, 14, 16, 18, 20 }; // idx 0 = 인챈 5
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 5;
            d.addEnchant(lvl, POTION_AMT, rec[i]);
            d.addEnchant(lvl, HEAL_DEF,   rec[i]);
        }

        // 인챈트 4 : 원거리 명중 +1, MR +1
        d.addEnchant(4, BOW_HIT, 1);
        d.addEnchant(4, MR,      1);

        // 인챈트 5~9
        int[] dmg = { 1, 2, 3, 4, 5 };       // 원거리 대미지
        int[] mr  = { 4, 6, 8, 10, 12 };     // MR
        int[] bls = { 3, 5, 7, 10, 12};   // 축복 소모 효율
        for (int i = 0; i < 5; i++) {
            int lvl = i + 5;
            d.addEnchant(lvl, BOW_DMG,  dmg[i]);
            d.addEnchant(lvl, BOW_HIT,  i + 2);
            d.addEnchant(lvl, MR,       mr[i]);
            d.addEnchant(lvl, BLESS_CONSUMO, bls[i]);
        }
        ItemEffectRegistry.register(900125, d);
    }
    
    private static void SageGuardianEmblem() {
        ItemEffectRegistry.Def d = new ItemEffectRegistry.Def(null, true);

        // AC +2 
        d.addEnchant(5, AC, -2);
        d.addEnchant(6, AC, -2);
        d.addEnchant(7, AC, -2);
        d.addEnchant(8, AC, -2);
        d.addEnchant(9, AC, -2);

        // 물약 회복량 / 치유 저항 : 인챈트 5~9
        int[] rec = { 12, 14, 16, 18, 20 }; // idx 0 = 인챈 5
        for (int i = 0; i < rec.length; i++) {
            int lvl = i + 5;
            d.addEnchant(lvl, POTION_AMT, rec[i]);
            d.addEnchant(lvl, HEAL_DEF,   rec[i]);
        }

        // 인챈트 4 : 원거리 명중 +1, MR +1
        d.addEnchant(4, SP, 1);
        d.addEnchant(4, MR, 1);

        // 인챈트 5~9
        int[] dmg = { 1, 2, 3, 4, 5 };       // Sp
        int[] mr  = { 4, 6, 8, 10, 12 };     // MR
        int[] bls = { 3, 5, 7, 10, 12};   // 축복 소모 효율
        for (int i = 0; i < 5; i++) {
            int lvl = i + 5;
            d.addEnchant(lvl, SP,  dmg[i]);
            d.addEnchant(lvl, MAGIC_HIT,  i + 2);
            d.addEnchant(lvl, MR,       mr[i]);
            d.addEnchant(lvl, BLESS_CONSUMO, bls[i]);
        }

        ItemEffectRegistry.register(900126, d);
    }
}