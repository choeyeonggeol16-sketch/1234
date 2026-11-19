package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Exp.ExpBonusState;

import static l1j.server.server.model.item.function.DollTickBonuses.calcHpTick;
import static l1j.server.server.model.item.function.DollTickBonuses.calcMpTick;
import l1j.server.server.model.item.function.ItemBonusPrimitiveMaps.IntDoubleMap;
/** Doll EXP bonus and lifecycle bookkeeping, without per-use temporary containers. */
final class DollExperienceBonuses {
    private DollExperienceBonuses() {}
    private static final IntDoubleMap DOLL_ADD = new IntDoubleMap(64);
    static {
// ========== DOLL_ADD ==========
	    // +0.10 group
	    putAll(DOLL_ADD, 0.10,
	            L1DollInstance.DOLLTYPE_SNOWMAN_A,
	            L1DollInstance.DOLLTYPE_SNOWMAN_B,
	            L1DollInstance.DOLLTYPE_SNOWMAN_C,
	            L1DollInstance.DOLLTYPE_자이언트,
	            L1DollInstance.DOLL_mummy,
	            L1DollInstance.DOLLTYPE_축자이언트,
	            L1DollInstance.DOLLTYPE_축머미);

	    DOLL_ADD.put(L1DollInstance.jindeathknight, 0.07);
	    putAll(DOLL_ADD, 0.20,
	            L1DollInstance.DOLLTYPE_DEATHNIGHT,
	            L1DollInstance.DOLLTYPE_축데스);

	    putAll(DOLL_ADD, 0.03,
	            L1DollInstance.DOLLTYPE_인어,
	            L1DollInstance.DOLLTYPE_COBO);

	    DOLL_ADD.put(L1DollInstance.ruler1, 0.30);
	    DOLL_ADD.put(L1DollInstance.ruler5, 0.15);
	    putAll(DOLL_ADD, 0.10, L1DollInstance.ruler2, L1DollInstance.ruler6);
	    putAll(DOLL_ADD, 0.05, L1DollInstance.ruler3, L1DollInstance.ruler7);
	    putAll(DOLL_ADD, 0.02, L1DollInstance.ruler4, L1DollInstance.ruler8);

	    DOLL_ADD.put(L1DollInstance.Antaras, 0.25);

        
    }
static void putAll(IntDoubleMap map, double v, int... keys) {
		for (int k : keys) map.put(k, v);
	}
    public static void onDollSpawned(L1PcInstance pc, L1DollInstance doll) {
        if (pc == null || doll == null) return;
		ExpBonusState st = pc.expBonusState();
        // 중복 적용 방지
        if (doll._dollHpTickDelta != 0 || doll._dollMpTickDelta != 0 || doll._expBonusDeltaExp != 0) return;

		double add = calcDollAdd(doll);
		if (add != 0.0) {
			st.addDollDelta(add);
			doll._expBonusDeltaExp = add;
		}
        final int hp = calcHpTick(doll);
        final int mp = calcMpTick(doll);

        if (hp > 0) {
            pc.addDollHpRegenTick(hp);
            doll._dollHpTickDelta = hp;
        }
        if (mp > 0) {
            pc.addDollMpRegenTick(mp);
            doll._dollMpTickDelta = mp;
        }
    }

    public static void onDollRemoved(L1PcInstance pc, L1DollInstance doll) {
        if (pc == null || doll == null) return;
        
    	ExpBonusState st = pc.expBonusState();
    	
        final int hpPrev = doll._dollHpTickDelta;
        final int mpPrev = doll._dollMpTickDelta;
        double prev = doll._expBonusDeltaExp;
		if (prev != 0.0) {
			st.addDollDelta(-prev);
			doll._expBonusDeltaExp = 0.0;
		}
		
        if (hpPrev != 0) {
            pc.addDollHpRegenTick(-hpPrev);
            doll._dollHpTickDelta = 0;
        }
        if (mpPrev != 0) {
            pc.addDollMpRegenTick(-mpPrev);
            doll._dollMpTickDelta = 0;
        }
    }

 	static double calcDollAdd(L1DollInstance doll) {
 		return DOLL_ADD.getOrZero(doll.getDollType());
 	}
}
