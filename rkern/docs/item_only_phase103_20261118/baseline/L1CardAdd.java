package l1j.server.server.model.item.Card;

import java.util.HashMap;
import java.util.Map;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 변신 카드 컬렉션 및 수호성 스킬 등록 핸들러
 */
public class L1CardAdd {

    @FunctionalInterface
    interface CardUpdate {
        int apply(Account acc);
    }

    // 각 카테고리별 매핑 맵
    private static final Map<Integer, CardUpdate> LEGEND1_MAP = new HashMap<>();
    private static final Map<Integer, CardUpdate> LEGEND2_MAP = new HashMap<>();
    private static final Map<Integer, CardUpdate> HERO1_MAP = new HashMap<>();
    private static final Map<Integer, CardUpdate> HERO2_MAP = new HashMap<>();
    private static final Map<Integer, CardUpdate> MYTH_MAP = new HashMap<>();

    static {
        // Legend 1 (875640306 ~ 314)
        LEGEND1_MAP.put(875640306, acc -> { acc.setInfo_ds_poly(acc.getInfo_ds_poly() + 1); return acc.getInfo_ds_poly(); });
        LEGEND1_MAP.put(875640307, acc -> { acc.setInfo_dru_poly(acc.getInfo_dru_poly() + 1); return acc.getInfo_dru_poly(); });
        LEGEND1_MAP.put(875640308, acc -> { acc.setInfo_ga_poly(acc.getInfo_ga_poly() + 1); return acc.getInfo_ga_poly(); });
        LEGEND1_MAP.put(875640309, acc -> { acc.setInfo_ger_poly(acc.getInfo_ger_poly() + 1); return acc.getInfo_ger_poly(); });
        LEGEND1_MAP.put(875640310, acc -> { acc.setInfo_dev_poly(acc.getInfo_dev_poly() + 1); return acc.getInfo_dev_poly(); });
        LEGEND1_MAP.put(875640311, acc -> { acc.setInfo_pen_poly(acc.getInfo_pen_poly() + 1); return acc.getInfo_pen_poly(); });
        LEGEND1_MAP.put(875640312, acc -> { acc.setInfo_atun_poly(acc.getInfo_atun_poly() + 1); return acc.getInfo_atun_poly(); });
        LEGEND1_MAP.put(875640313, acc -> { acc.setInfo_moon_poly(acc.getInfo_moon_poly() + 1); return acc.getInfo_moon_poly(); });
        LEGEND1_MAP.put(875640314, acc -> { acc.setInfo_mar_poly(acc.getInfo_mar_poly() + 1); return acc.getInfo_mar_poly(); });

        // Legend 2 (875640365 ~ 378)
        LEGEND2_MAP.put(875640365, acc -> { acc.setlegend11(acc.getlegend11() + 1); return acc.getlegend11(); });
        LEGEND2_MAP.put(875640366, acc -> { acc.setlegend01(acc.getlegend01() + 1); return acc.getlegend01(); });
        LEGEND2_MAP.put(875640367, acc -> { acc.setlegend12(acc.getlegend12() + 1); return acc.getlegend12(); });
        LEGEND2_MAP.put(875640368, acc -> { acc.setlegend02(acc.getlegend02() + 1); return acc.getlegend02(); });
        LEGEND2_MAP.put(875640369, acc -> { acc.setlegend03(acc.getlegend03() + 1); return acc.getlegend03(); });
        LEGEND2_MAP.put(875640370, acc -> { acc.setlegend13(acc.getlegend13() + 1); return acc.getlegend13(); });
        LEGEND2_MAP.put(875640371, acc -> { acc.setlegend04(acc.getlegend04() + 1); return acc.getlegend04(); });
        LEGEND2_MAP.put(875640372, acc -> { acc.setlegend05(acc.getlegend05() + 1); return acc.getlegend05(); });
        LEGEND2_MAP.put(875640373, acc -> { acc.setlegend06(acc.getlegend06() + 1); return acc.getlegend06(); });
        LEGEND2_MAP.put(875640374, acc -> { acc.setlegend07(acc.getlegend07() + 1); return acc.getlegend07(); });
        LEGEND2_MAP.put(875640375, acc -> { acc.setlegend08(acc.getlegend08() + 1); return acc.getlegend08(); });
        LEGEND2_MAP.put(875640376, acc -> { acc.setlegend09(acc.getlegend09() + 1); return acc.getlegend09(); });
        LEGEND2_MAP.put(875640377, acc -> { acc.setlegend14(acc.getlegend14() + 1); return acc.getlegend14(); });
        LEGEND2_MAP.put(875640378, acc -> { acc.setlegend10(acc.getlegend10() + 1); return acc.getlegend10(); });

        // Hero 1
        HERO1_MAP.put(875640315, acc -> { acc.setInfo_heaven_poly(acc.getInfo_heaven_poly()+1); return acc.getInfo_heaven_poly(); });
        HERO1_MAP.put(875640316, acc -> { acc.setInfo_val_poly(acc.getInfo_val_poly()+1); return acc.getInfo_val_poly(); });
        HERO1_MAP.put(875640317, acc -> { acc.setInfo_larry_poly(acc.getInfo_larry_poly()+1); return acc.getInfo_larry_poly(); });
        HERO1_MAP.put(875640318, acc -> { acc.setInfo_gunter_poly(acc.getInfo_gunter_poly()+1); return acc.getInfo_gunter_poly(); });
        HERO1_MAP.put(875640319, acc -> { acc.setInfo_depor_poly(acc.getInfo_depor_poly()+1); return acc.getInfo_depor_poly(); });
        HERO1_MAP.put(875640320, acc -> { acc.setInfo_deathred_poly(acc.getInfo_deathred_poly()+1); return acc.getInfo_deathred_poly(); });
        HERO1_MAP.put(875640321, acc -> { acc.setInfo_ecil_poly(acc.getInfo_ecil_poly()+1); return acc.getInfo_ecil_poly(); });
        HERO1_MAP.put(875640322, acc -> { acc.setInfo_assm_poly(acc.getInfo_assm_poly()+1); return acc.getInfo_assm_poly(); });
        HERO1_MAP.put(875640323, acc -> { acc.setInfo_kerep_poly(acc.getInfo_kerep_poly()+1); return acc.getInfo_kerep_poly(); });
        HERO1_MAP.put(875640324, acc -> { acc.setInfo_jowo_poly(acc.getInfo_jowo_poly()+1); return acc.getInfo_jowo_poly(); });
        HERO1_MAP.put(875640325, acc -> { acc.setInfo_unik_poly(acc.getInfo_unik_poly()+1); return acc.getInfo_unik_poly(); });
        HERO1_MAP.put(875640326, acc -> { acc.setInfo_unim_poly(acc.getInfo_unim_poly()+1); return acc.getInfo_unim_poly(); });
        HERO1_MAP.put(875640327, acc -> { acc.setInfo_unir_poly(acc.getInfo_unir_poly()+1); return acc.getInfo_unir_poly(); });
        HERO1_MAP.put(875640328, acc -> { acc.setInfo_yozil_poly(acc.getInfo_yozil_poly()+1); return acc.getInfo_yozil_poly(); });
        HERO1_MAP.put(875640329, acc -> { acc.setInfo_owen_poly(acc.getInfo_owen_poly()+1); return acc.getInfo_owen_poly(); });
        HERO1_MAP.put(875640330, acc -> { acc.setInfo_helv_poly(acc.getInfo_helv_poly()+1); return acc.getInfo_helv_poly(); });

        // Hero 2
        HERO2_MAP.put(875640345, acc -> { acc.sethero05(acc.gethero05()+1); return acc.gethero05(); });
        HERO2_MAP.put(875640346, acc -> { acc.sethero06(acc.gethero06()+1); return acc.gethero06(); });
        HERO2_MAP.put(875640347, acc -> { acc.sethero03(acc.gethero03()+1); return acc.gethero03(); });
        HERO2_MAP.put(875640348, acc -> { acc.sethero04(acc.gethero04()+1); return acc.gethero04(); });
        HERO2_MAP.put(875640349, acc -> { acc.sethero13(acc.gethero13()+1); return acc.gethero13(); });
        HERO2_MAP.put(875640350, acc -> { acc.sethero14(acc.gethero14()+1); return acc.gethero14(); });
        HERO2_MAP.put(875640351, acc -> { acc.sethero07(acc.gethero07()+1); return acc.gethero07(); });
        HERO2_MAP.put(875640352, acc -> { acc.sethero08(acc.gethero08()+1); return acc.gethero08(); });
        HERO2_MAP.put(875640353, acc -> { acc.sethero09(acc.gethero09()+1); return acc.gethero09(); });
        HERO2_MAP.put(875640354, acc -> { acc.sethero17(acc.gethero17()+1); return acc.gethero17(); });
        HERO2_MAP.put(875640355, acc -> { acc.sethero15(acc.gethero15()+1); return acc.gethero15(); });
        HERO2_MAP.put(875640356, acc -> { acc.sethero18(acc.gethero18()+1); return acc.gethero18(); });
        HERO2_MAP.put(875640357, acc -> { acc.sethero16(acc.gethero16()+1); return acc.gethero16(); });
        HERO2_MAP.put(875640358, acc -> { acc.sethero01(acc.gethero01()+1); return acc.gethero01(); });
        HERO2_MAP.put(875640359, acc -> { acc.sethero19(acc.gethero19()+1); return acc.gethero19(); });
        HERO2_MAP.put(875640360, acc -> { acc.sethero02(acc.gethero02()+1); return acc.gethero02(); });
        HERO2_MAP.put(875640361, acc -> { acc.sethero10(acc.gethero10()+1); return acc.gethero10(); });
        HERO2_MAP.put(875640362, acc -> { acc.sethero11(acc.gethero11()+1); return acc.gethero11(); });
        HERO2_MAP.put(875640363, acc -> { acc.sethero20(acc.gethero20()+1); return acc.gethero20(); });
        HERO2_MAP.put(875640364, acc -> { acc.sethero12(acc.gethero12()+1); return acc.gethero12(); });

        // Myth
        MYTH_MAP.put(875640296, acc -> { acc.setInfo_class_poly(acc.getInfo_class_poly()+1); return acc.getInfo_class_poly(); });
        MYTH_MAP.put(875640297, acc -> { acc.setInfo_ki_poly(acc.getInfo_ki_poly()+1); return acc.getInfo_ki_poly(); });
        MYTH_MAP.put(875640298, acc -> { acc.setInfo_el_poly(acc.getInfo_el_poly()+1); return acc.getInfo_el_poly(); });
        MYTH_MAP.put(875640299, acc -> { acc.setInfo_wi_poly(acc.getInfo_wi_poly()+1); return acc.getInfo_wi_poly(); });
        MYTH_MAP.put(875640300, acc -> { acc.setInfo_da_poly(acc.getInfo_da_poly()+1); return acc.getInfo_da_poly(); });
        MYTH_MAP.put(875640301, acc -> { acc.setInfo_wa_poly(acc.getInfo_wa_poly()+1); return acc.getInfo_wa_poly(); });
        MYTH_MAP.put(875640302, acc -> { acc.setInfo_il_poly(acc.getInfo_il_poly()+1); return acc.getInfo_il_poly(); });
        MYTH_MAP.put(875640303, acc -> { acc.setInfo_drag_poly(acc.getInfo_drag_poly()+1); return acc.getInfo_drag_poly(); });
        MYTH_MAP.put(875640304, acc -> { acc.setInfo_dsn_poly(acc.getInfo_dsn_poly()+1); return acc.getInfo_dsn_poly(); });
        MYTH_MAP.put(875640305, acc -> { acc.setInfo_bapo_poly(acc.getInfo_bapo_poly()+1); return acc.getInfo_bapo_poly(); });
        MYTH_MAP.put(875640380, acc -> { acc.setmyth01(acc.getmyth01()+1); return acc.getmyth01(); });
    }

    /** 공통 컬렉션 처리 로직 */
    private static void processCollection(L1PcInstance pc, Map<Integer, CardUpdate> map, int type) {
        L1ItemInstance itemc = pc.getCollecItem();
        if (itemc == null) return;

        int itemId = itemc.getItem().getItemId();
        String itemname = itemc.getItem().getNameId();
        CardUpdate action = map.get(itemId);

        if (action != null) {
            int coll = action.apply(pc.getAccount());
            pc.getInventory().removeItem(itemc, 1);

            // DB 업데이트 분기
            if (type == 1) pc.getAccount().updateLegend();
            else if (type == 2) pc.getAccount().updatelegend2();
            else if (type == 3) pc.getAccount().updateHero();
            else if (type == 4) pc.getAccount().updateHero2();
            else if (type == 5) pc.getAccount().updateMyth();

            // 신규 등록 시 컬렉션 점수 추가
            if (coll == 1) {
                if (type <= 2) pc.getAccount().setInfo_col_l(pc.getAccount().getInfo_col_l() + 1);
                else if (type <= 4) pc.getAccount().setInfo_col_h(pc.getAccount().getInfo_col_h() + 1);
                else pc.getAccount().setInfo_col_m(pc.getAccount().getInfo_col_m() + 1);
                
                pc.getAccount().updateCollection();
                SC_SystemMessage.send(pc, itemname + "(이)가 변신컬렉션에 등록되었습니다.");
            } else {
                SC_SystemMessage.send(pc, itemname + "(이)가 변신컬렉션에 등록되었습니다. (현재 " + coll + "장)");
            }

            // 리스트 갱신 호출
            if (type <= 2) L1CardCollection.legendjoin(pc);
            else if (type <= 4) L1CardCollection.herojoin(pc);
            else L1CardCollection.mythjoin(pc);
        }
        pc.setCollecItem(null);
    }

    public static void legend1(L1PcInstance pc) { processCollection(pc, LEGEND1_MAP, 1); }
    public static void legend2(L1PcInstance pc) { processCollection(pc, LEGEND2_MAP, 2); }
    public static void hero1(L1PcInstance pc)   { processCollection(pc, HERO1_MAP, 3); }
    public static void hero2(L1PcInstance pc)   { processCollection(pc, HERO2_MAP, 4); }
    public static void myth(L1PcInstance pc)    { processCollection(pc, MYTH_MAP, 5); }

    /** 수호성 스킬 트리 등록 */
    public static void SkillTree(L1PcInstance pc) {
        L1ItemInstance itemc = pc.getCollecItem();
        if (itemc == null) return;

        int itemId = itemc.getItem().getItemId();
        String itemname = itemc.getItem().getNameId();
        Account acc = pc.getAccount();
        int codenum = 0;

        // 중복 체크 및 등록 로직
        switch (itemId) {
            case 875640331: if (acc.getTree_iceq() != 0) return; acc.setTree_iceq(1); codenum = 1; break;
            case 875640332: if (acc.getTree_fireq() != 0) return; acc.setTree_fireq(1); codenum = 1; break;
            case 875640333: if (acc.getTree_final() != 0) return; acc.setTree_final(1); codenum = 1; break;
            case 875640334: if (acc.getTree_ices() != 0) return; acc.setTree_ices(1); codenum = 1; break;
            case 875640335: if (acc.getTree_rain() != 0) return; acc.setTree_rain(1); codenum = 1; break;
            case 875640336: if (acc.getTree_cri() != 0) return; acc.setTree_cri(1); codenum = 2; break;
            case 875640337: if (acc.getTree_crim1() != 0) return; acc.setTree_crim1(1); codenum = 2; break;
            case 875640338: if (acc.getTree_crim2() != 0) return; acc.setTree_crim2(1); codenum = 2; break;
            case 875640339: if (acc.getTree_ecil1() != 0) return; acc.setTree_ecil1(1); codenum = 3; break;
            case 875640340: if (acc.getTree_ecil2() != 0) return; acc.setTree_ecil2(1); codenum = 3; break;
            case 875640341: if (acc.getTree_ecil3() != 0) return; acc.setTree_ecil3(1); codenum = 3; break;
            case 875640446: if (acc.getTree_atun1() != 0) return; acc.setTree_atun1(1); codenum = 4; break;
            case 875640447: if (acc.getTree_atun2() != 0) return; acc.setTree_atun2(1); codenum = 4; break;
            case 875640448: if (acc.getTree_atun3() != 0) return; acc.setTree_atun3(1); codenum = 4; break;
        }

        if (codenum == 0) {
            SC_SystemMessage.send(pc, "이미 추가된 수호성 스킬입니다.");
            pc.setCollecItem(null);
            return;
        }

        pc.getInventory().removeItem(itemc, 1);
        acc.updateTree();
        if (codenum == 1) L1SkillTree.Info(pc);
        else if (codenum == 2) L1SkillTree.Info2(pc);
        else if (codenum == 3) L1SkillTree.Info3(pc);
        else { acc.updateTree2(); L1SkillTree.Info5(pc); }

        SC_SystemMessage.send(pc, itemname + "(이)가 수호성에 등록되었습니다.");
        pc.setCollecItem(null);
    }
}