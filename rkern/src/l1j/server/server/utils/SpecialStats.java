package l1j.server.server.utils;

import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 아이템 보유 특수 저항/관통 옵션 컨테이너.
 */
public final class SpecialStats {

    private final int[] _resist = new int[6]; 
    private final int[] _pierce = new int[6];

    public void putResist(int wireValue, int v) {
        if (v > 0) _resist[wireValue] = v;
    }
    public void putPierce(int wireValue, int v) {
        if (v > 0) _pierce[wireValue] = v;
    }

    public int getResist(eKind kind)      { return _resist[kind.value()]; }
    public int getPierce(eKind kind)      { return _pierce[kind.value()]; }

    public boolean isEmpty() {
        for (int v : _resist) if (v != 0) return false;
        for (int v : _pierce) if (v != 0) return false;
        return true;
    }

    /** 장착(+1)/해제(-1) — 캐릭터 Resistance에 그대로 흘려보냄 */
    public void equipTo(L1PcInstance pc, int sign) {
        for (eKind k : eKind.values()) {
            int v = _resist[k.value()];
            if (v != 0) pc.AddSpecialResistance(k, v * sign);
        }
        for (eKind k : eKind.values()) {
            int v = _pierce[k.value()];
            if (v != 0) pc.AddSpecialPierce(k, v * sign);
        }
    }

    /** 툴팁용 — 보유 저항 나열 */
    public void appendResistText(StringBuilder sb) {
        boolean first = true;
        for (eKind k : eKind.values()) {
            int v = _resist[k.value()];
            if (v == 0) continue;
            if (!first) sb.append(", ");
            sb.append(label(k)).append("+").append(v);
            first = false;
        }
    }

    public static String label(eKind k) {
        switch (k) {
        case ABILITY: return "기술";
        case SPIRIT: return "정령";
        case DRAGON_SPELL: return "용언";
        case FEAR: return "공포";
        case ALL: return "전체";
        }
        return "?";
    }
}