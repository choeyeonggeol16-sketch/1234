package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Exp.ExpBonusState;

import l1j.server.server.model.item.function.ItemBonusPrimitiveMaps.IntIntMap;
/** Type/level HP and MP doll tick bonus lookup. */
final class DollTickBonuses {
    private DollTickBonuses() {}
    private static final IntIntMap MP_BY_TYPE = new IntIntMap(128);
    private static final IntIntMap HP_BY_TYPE = new IntIntMap(64);
    private static final IntIntMap MP_BY_LV = new IntIntMap(32);
    private static final IntIntMap HP_BY_LV = new IntIntMap(32);
    static {
// 15
        putAll(MP_BY_TYPE, 15,
                L1DollInstance.DOLLTYPE_SUCCUBUS,
                L1DollInstance.DOLLTYPE_ELDER,
                L1DollInstance.DOLLTYPE_PSY_CHAMPION,
                L1DollInstance.DOLLTYPE_PSY_BIRD,
                L1DollInstance.DOLLTYPE_PSY_GANGNAM_STYLE,
                L1DollInstance.DOLLTYPE_SUCCUBUSQUEEN,        
                L1DollInstance.jindeathknight,
                L1DollInstance.pnix,
                L1DollInstance.brich,
                L1DollInstance.behe,
                L1DollInstance.au,
                L1DollInstance.hal,
                L1DollInstance.DOLLTYPE_흑장로,
                L1DollInstance.DOLL_mummy,
                L1DollInstance.DOLLTYPE_축머미,
                L1DollInstance.Antaras,
                L1DollInstance.DOLLTYPE_COBO,
                L1DollInstance.DOLLTYPE_축서큐퀸,
                L1DollInstance.DOLLTYPE_축흑장로
                
        );

        // 10
        putAll(MP_BY_TYPE, 10,
                L1DollInstance.DOLLTYPE_GREMLIN,
                L1DollInstance.DOLLTYPE_기르타스,
                L1DollInstance.DOLLTYPE_킹버그베어
        );

        // 5
        putAll(MP_BY_TYPE, 5,
                L1DollInstance.DOLLTYPE_HATCHLING,
                L1DollInstance.Papou,
                L1DollInstance.Lynd,
                L1DollInstance.Valakas
        );

        // =========================
        // MP REGEN (레벨 기반)
        // =========================
        MP_BY_LV.put(69, 12);
        MP_BY_LV.put(34, 10);
        MP_BY_LV.put(106, 30);

        // =========================
        // HP REGEN (타입 기반)
        // =========================
        putAll(HP_BY_TYPE, 5,
                L1DollInstance.DOLLTYPE_SEADANCER,
                L1DollInstance.DOLLTYPE_SNOWMAN_C
        );

        // =========================
        // HP REGEN (레벨 기반)
        // =========================
        HP_BY_LV.put(33, 30);
        HP_BY_LV.put(68, 50);
        HP_BY_LV.put(105, 150);
    
    }

    static void putAll(IntIntMap map, int v, int... keys) {
        for (int k : keys) map.put(k, v);
    }

    public static int calcMpTick(L1DollInstance doll) {
        if (doll == null) return 0;

        int v = 0;

        // type 기반
        v = MP_BY_TYPE.getOrZero(doll.getDollType());

        final int lv = doll.getDollLevel();
        final int byLv = MP_BY_LV.getOrZero(lv);
        if (byLv > v) v = byLv;

        return v;
    }

    public static int calcHpTick(L1DollInstance doll) {
        if (doll == null) return 0;

        int v = 0;

        v = HP_BY_TYPE.getOrZero(doll.getDollType());

        final int lv = doll.getDollLevel();
        final int byLv = HP_BY_LV.getOrZero(lv);
        if (byLv > v) v = byLv;

        return v;
    }
}
