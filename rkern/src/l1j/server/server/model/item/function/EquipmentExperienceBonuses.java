package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Exp.ExpBonusState;

import l1j.server.server.model.item.function.ItemBonusPrimitiveMaps.IntDoubleMap;
/** Item EXP bonuses, arka delta and insignia multiplier hooks. */
final class EquipmentExperienceBonuses {
    private EquipmentExperienceBonuses() {}
    private static final IntDoubleMap ARKA_FIXED = new IntDoubleMap(32);
    private static final double[] ARKA_900116_BY_ENCHANT = { 0,0,0,0,0, 0.05, 0.06, 0.08, 0.10 };
private static final int[] INS_IDS = { 900020, 900093, 900094, 900095, 900099 };
 	private static final double[] INS_PER = { 0.01,   0.04,   0.04,   0.04,   0.04   };
 	private static final int[] INS_SLOT = { 1,        2,      3,      4,      5      };


    static {
// ========== ARKA_FIXED ==========
	    // 900033 or 9961 : +0.2
	    ARKA_FIXED.put(900033, 0.2);
	    ARKA_FIXED.put(9961,   0.2);

	    // 555001~3, 666001~3 : +0.1
	    ARKA_FIXED.put(555001, 0.1);
	    ARKA_FIXED.put(555002, 0.1);
	    ARKA_FIXED.put(555003, 0.1);
	    ARKA_FIXED.put(666001, 0.1);
	    ARKA_FIXED.put(666002, 0.1);
	    ARKA_FIXED.put(666003, 0.1);

	    
    }

 	public static void onEquipChanged(L1PcInstance pc, L1ItemInstance item, boolean equipped) {
 		if (pc == null || item == null) return;
 		ExpBonusState st = pc.expBonusState();

 		// 이전 delta 제거
 		double prev = item._expBonusDeltaExp;
 		if (prev != 0.0) {
 			st.addArkaDelta(-prev);
 			item._expBonusDeltaExp = 0.0;
 		}

 		// 해제면 문장 복구만 하고 종료
 		if (!equipped) {
 			resetInsigniaIfMatch(st, item);
 			return;
 		}

 		// 착용이면 arka delta 반영
 		double now = calcArkaDeltaIfAny(item);
 		if (now != 0.0) {
 			st.addArkaDelta(now);
 			item._expBonusDeltaExp = now;
 		}

 		// 문장류면 multiplier 갱신
 		applyInsigniaIfMatch(st, item);
 	}


 	public static void onEnchantChanged(L1PcInstance pc, L1ItemInstance item) {
 		if (pc == null || item == null) return;
 		if (!item.isEquipped()) return;

 		// 착용중이면 equipChanged 재호출로 통일
 		onEquipChanged(pc, item, true);
 	}

 	static double calcArkaDeltaIfAny(L1ItemInstance item) {
 		final int id = item.getItemId();

 		double fixed = ARKA_FIXED.getOrZero(id);
 		if (fixed != 0.0) return fixed;

 		final int ench = item.getEnchantLevel();

 		// 900025 : ench>=8 -> ((ench-7)*2)*0.01
 		if (id == 900025) {
 			if (ench >= 8) return ((ench - 7) * 2) * 0.01;
 			return 0.0;
 		}

 		// 900184 : ench>=7 -> ((ench-6)*2)*0.01
 		if (id == 900184) {
 			if (ench >= 7) return ((ench - 6) * 2) * 0.01;
 			return 0.0;
 		}

 		// 900116 : ench 5=0.05, 6=0.06, 7=0.08, 8=0.10
 		if (id == 900116) {
            if (ench < 5 || ench > 8) return 0.0;
            return ARKA_900116_BY_ENCHANT[ench];
 		}

 		// 900130 : ench<=2 -> 0.02 else ench*0.01
 		if (id == 900130) {
 			if (ench <= 2) return 0.02;
 			return ench * 0.01;
 		}

 		return 0.0;
 	}

    static int tooltipPercent(L1ItemInstance item) {
        double bonus = calcArkaDeltaIfAny(item);
        for (int i = 0; i < INS_IDS.length; i++) {
            if (INS_IDS[i] == item.getItemId()) {
                bonus += INS_PER[i] * item.getEnchantLevel();
                break;
            }
        }
        return (int) Math.round(bonus * 100);
    }

 	static void applyInsigniaIfMatch(ExpBonusState st, L1ItemInstance item) {
 		final int id = item.getItemId();
 		final int ench = item.getEnchantLevel();

 		// 테이블 선형 스캔
 		for (int i = 0; i < INS_IDS.length; i++) {
 			if (INS_IDS[i] != id) continue;

 			double mul = 1.0 + (INS_PER[i] * ench);
 			switch (INS_SLOT[i]) {
 			case 1: st.setIns1(mul); break;
 			case 2: st.setIns2(mul); break;
 			case 3: st.setIns3(mul); break;
 			case 4: st.setIns4(mul); break;
 			case 5: st.setIns5(mul); break;
 			}
 			return;
 		}
 	}


 	static void resetInsigniaIfMatch(ExpBonusState st, L1ItemInstance item) {
 		final int id = item.getItemId();

 		for (int i = 0; i < INS_IDS.length; i++) {
 			if (INS_IDS[i] != id) continue;

 			switch (INS_SLOT[i]) {
 			case 1: st.setIns1(1.0); break;
 			case 2: st.setIns2(1.0); break;
 			case 3: st.setIns3(1.0); break;
 			case 4: st.setIns4(1.0); break;
 			case 5: st.setIns5(1.0); break;
 			}
 			return;
 		}
 	}
}
