package l1j.server.server.utils;

import java.util.concurrent.ThreadLocalRandom;

public class CalcStat {

    private CalcStat() {}

    // =========================
    // Public API (80 tables)
    // =========================
    public static int 근거리대미지(int str) { return GetParamBonus(MELEE_DMG_OFF, str, LEN_80, 0); }
    public static int 근거리명중(int str)   { return GetParamBonus(MELEE_HIT_OFF, str, LEN_80, 0); }
    public static int 근거리치명타(int str) { return stepCrit(str); }

    public static int 원거리대미지(int dex) { return GetParamBonus(RANGE_DMG_OFF, dex, LEN_80, 0); }
    public static int 원거리명중(int dex)   { return GetParamBonus(RANGE_HIT_OFF, dex, LEN_80, 0); }
    public static int 원거리치명타(int dex) { return stepCrit(dex); }

    public static int 마법대미지(int intel) { return GetParamBonus(MAGIC_DMG_OFF, intel, LEN_80, 0); }
    public static int 마법치명타(int intel) { return magicCrit(intel); }

    // =========================
    // Public API (extra tables)
    // =========================
    public static int 원거리회피(int dex) { return GetParamBonus(RANGE_EVADE_OFF, dex, LEN_80, 0); }
    public static int 물리방어력(int dex) { return GetParamBonus(PHYS_DEF_OFF, dex, LEN_80, 0); }

    // 1~45 (기존 defaultOnErr=2 유지)
    public static int 엠소모감소(int inte)    { return GetParamBonus(MP_REDUCE_OFF, inte, LEN_45, 2); }
    public static int 엠회복틱(int wis)       { return GetParamBonus(MP_REGEN_TICK_OFF, wis, LEN_45, 2); }
    public static int 엠피물약회복(int wis)   { return GetParamBonus(MP_POTION_TICK_OFF, wis, LEN_45, 2); }
    public static int 피회복틱(int con)       { return GetParamBonus(HP_REGEN_TICK_OFF, con, LEN_45, 2); }

    // =========================
    // Formula APIs
    // =========================

    /** 클래스 베이스 MR + (wis-10)*4 */
    private static final int[] BASE_MR = {
            10, // 0 군주
            0,  // 1 기사
            25, // 2 요정
            15, // 3 법사
            10, // 4 다엘
            18, // 5 용기사
            20, // 6 환술사
            0   // 7 전사
    };

    public static int 마법방어(int type, int wis) {
        final int t = clampType(type);
        final int base = BASE_MR[t];
        if (wis <= 10) return base;
        return base + ((wis - 10) * 4);
    }

    /** HP 물약 회복 증가(단위:%) = 기존 step */
    public static int 물약회복증가(int con) {
        int i = 0;
        if (con >= 20) i++;
        if (con >= 30) i++;
        if (con >= 35) i++;
        if (con >= 40) i++;
        if (con >= 45) i += 2;
        return i;
    }

    public static int 최대소지무게(int str, int con) {
        int stat = (str + con) >> 1; // /2
        if (stat <= 0) return 1000;
        return 1000 + (stat * 100);
    }

    // Shared parameter-table lookup (CPCParamDB::GetParamBonus role).
    // Each L1 table retains its own range and fallback.
    private static int GetParamBonus(int baseOffset, int stat, int length, int defaultOnErr) {
        int idx = stat - 1;
        if (idx < 0) idx = 0;
        else if (idx >= length) idx = length - 1;

        int p = baseOffset + idx;
        if (p < 0 || p >= DATA.length) return defaultOnErr;
        return DATA[p];
    }

    // 근/원 치명타
    private static int stepCrit(int stat) {
        int c = 0;
        if (stat >= 40) c++;
        if (stat >= 45) c++;
        if (stat >= 50) c++;
        if (stat >= 60) c++;
        if (stat >= 70) c++;
        if (stat >= 75) c++;
        if (stat >= 80) c++;
        return c;
    }

    // 마법 치명타
    private static int magicCrit(int intel) {
        int c = 0;
        if (intel >= 35) c++;
        if (intel >= 40) c++;
        if (intel >= 45) c += 2;
        if (intel >= 50) c++;
        if (intel >= 55) c++;
        if (intel >= 60) c++;
        if (intel >= 65) c++;
        if (intel >= 70) c++;
        return c;
    }

    // =========================
    // Offsets / Lengths
    // =========================
    private static final int LEN_80 = 80;
    private static final int LEN_45 = 45;

    private static final int MELEE_DMG_OFF  = 0 * LEN_80;
    private static final int MELEE_HIT_OFF  = 1 * LEN_80;
    private static final int RANGE_DMG_OFF  = 2 * LEN_80;
    private static final int RANGE_HIT_OFF  = 3 * LEN_80;
    private static final int MAGIC_DMG_OFF  = 4 * LEN_80;

    // 5*80 = 400
    private static final int EXTRA_BASE_OFF = 5 * LEN_80;

    // 80 tables
    private static final int RANGE_EVADE_OFF = EXTRA_BASE_OFF;                 // +80
    private static final int PHYS_DEF_OFF    = RANGE_EVADE_OFF + LEN_80;       // +80

    // 45 tables (연속으로 배치)
    private static final int MP_REDUCE_OFF      = PHYS_DEF_OFF + LEN_80;             // +45
    private static final int MP_REGEN_TICK_OFF  = MP_REDUCE_OFF + LEN_45;            // +45
    private static final int MP_POTION_TICK_OFF = MP_REGEN_TICK_OFF + LEN_45;        // +45
    private static final int HP_REGEN_TICK_OFF  = MP_POTION_TICK_OFF + LEN_45;       // +45

    // =========================
    // Packed tables (byte)
    // =========================
    private static final byte[] DATA = new byte[] {

            // =========================
            // MELEE_DMG (1~80) 근거리 대미지
            // =========================
            2,2,2,2,2,2,2,2,2,3,
            3,4,4,5,5,6,6,7,7,8,
            8,9,9,10,11,12,12,13,13,14,
            14,15,15,16,17,18,18,19,19,20,
            20,21,21,22,25,26,26,27,27,28,
            28,29,29,30,30,31,31,32,32,33,
            33,34,34,35,35,36,36,37,37,38,
            38,39,39,40,40,41,41,42,42,43,

            // =========================
            // MELEE_HIT (1~80) 근거리 명중
            // =========================
            5,5,5,5,5,5,5,5,6,6,
            7,8,8,9,10,10,11,12,12,13,
            14,14,15,16,17,18,19,19,20,21,
            21,22,23,23,25,26,26,27,28,28,
            29,30,30,31,35,35,36,37,37,38,
            39,39,40,41,41,42,43,43,44,45,
            45,46,46,47,48,49,49,50,51,51,
            52,53,53,54,55,55,56,57,57,58,

            // =========================
            // RANGE_DMG (1~80) 원거리 대미지
            // =========================
            2,2,2,2,2,2,2,2,3,3,
            3,4,4,4,5,5,5,6,6,6,
            7,7,7,8,9,9,10,10,10,11,
            11,11,12,12,13,14,14,14,15,15,
            15,16,16,16,20,20,20,21,21,21,
            22,22,22,23,23,23,24,24,24,25,
            25,25,26,26,26,27,27,27,28,28,
            28,29,29,29,30,30,30,31,31,31,

            // =========================
            // RANGE_HIT (1~80) 원거리 명중
            // =========================
            -3,-3,-3,-3,-3,-3,-3,-2,-1,0,
            1,2,3,4,5,6,7,8,9,10,
            11,12,13,14,16,17,18,19,20,21,
            22,23,24,25,27,28,29,30,31,32,
            33,34,35,36,40,41,42,43,44,45,
            46,47,48,49,50,51,52,53,54,55,
            56,57,58,59,60,61,62,63,64,65,
            66,67,68,69,70,71,72,73,74,75,

            // =========================
            // MAGIC_DMG (1~80) 마법 대미지
            // =========================
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,1,1,1,1,1,2,
            2,2,2,2,4,4,4,4,4,5,
            5,5,5,5,7,7,7,7,7,8,
            8,8,8,8,12,12,12,12,12,13,
            13,13,13,13,14,14,14,14,14,15,
            15,15,15,15,16,16,16,16,16,17,
            17,17,17,17,18,18,18,18,18,19,

            // =========================
            // RANGE_EVADE (1~80) 원거리 회피
            // =========================
            3,3,3,3,3,3,3,4,4,5,
            5,6,6,7,7,8,8,9,9,10,
            10,11,11,12,12,13,13,14,14,15,
            15,16,16,17,17,18,18,19,19,20,
            20,21,21,22,22,23,23,24,24,25,
            25,26,26,27,27,28,28,29,29,30,
            30,31,31,32,32,33,33,34,34,35,
            35,36,36,37,37,38,38,39,39,40,

            // =========================
            // PHYS_DEF (1~80)  "물리_방어력"
            // =========================
            -2,-2,-2,-2,-2,-2,-2,-2,-3,-3,
            -3,-4,-4,-4,-5,-5,-5,-6,-6,-6,
            -7,-7,-7,-8,-8,-8,-9,-9,-9,-10,
            -10,-10,-11,-11,-11,-12,-12,-12,-13,-13,
            -13,-14,-14,-14,-15,-15,-15,-16,-16,-16,
            -17,-17,-17,-18,-18,-18,-19,-19,-19,-20,
            -20,-20,-21,-21,-21,-22,-22,-22,-23,-23,
            -23,-24,-24,-24,-25,-25,-25,-26,-26,-26,

            // =========================
            // MP_REDUCE (1~45)  "엠소모감소"
            // =========================
            5,5,5,5,5,5,5,5,6,6,
            7,8,8,9,10,10,11,12,12,13,
            14,14,15,16,16,17,18,18,19,20,
            20,21,22,22,23,24,24,25,26,26,
            27,28,28,29,30,

            // =========================
            // MP_REGEN_TICK (1~45)  "엠회복틱"
            // =========================
            1,1,1,1,1,1,1,1,1,2,
            2,2,2,2,3,3,3,3,3,4,
            4,4,4,4,6,6,6,6,6,7,
            7,7,7,7,9,9,9,9,9,10,
            10,10,10,10,14,

            // =========================
            // MP_POTION_TICK (1~45)  "엠피물약회복"
            // =========================
            1,1,1,1,1,1,1,1,1,1,
            1,2,2,2,3,4,4,5,5,6,
            6,7,7,8,9,10,10,11,11,12,
            12,13,13,14,15,16,16,17,17,18,
            18,19,19,20,23,

            // =========================
            // HP_REGEN_TICK (1~45)  "피회복틱"
            // =========================
            5,5,5,5,5,5,5,5,5,5,
            5,6,6,7,7,8,8,9,9,10,
            10,11,11,12,13,14,14,15,15,16,
            16,17,17,18,19,20,20,21,21,22,
            22,23,23,24,27,
    };

    /**
     * 마법 적중 새로 구현
     * @param Int
     * @return
     */
    public static int 마법적중(int Int) {
        if (Int < 9) return -4;
        int temp = (Int - 6) / 3 - 4;
        if (Int >= 45) return temp + 3;
        if (Int >= 35) return temp + 2;
        if (Int >= 25) return temp + 1;
        return temp;
    }

    /**
     * 마법 보너스 새로 구현
     * @param chartype
     * @param Int
     * @return
     */
    
    private static final int CLASS_WIZARD = 3;
    private static final int CLASS_ILLUSIONIST = 6;
    
    public static int 마법보너스(int chartype, int Int) {
        final boolean isMageOrIllu = (chartype == CLASS_WIZARD || chartype == CLASS_ILLUSIONIST);

        if (isMageOrIllu) {
            if (Int < 16) return 4;
            int mb = (Int / 4) + 1;
            return (mb > 21) ? 21 : mb;
        }
        if (Int < 11) return 2;
        int mb = (Int / 4);
        return (Int >= 90) ? 21 : mb;
    }

    // =========================================================
    // Const / Clamp
    // =========================================================
    private static final int TYPE_MAX = 7;
    private static final int CON_MAX  = 64;

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }

    private static int clampType(int t) { return clamp(t, 0, TYPE_MAX); }
    private static int clampCon(int c)  { return clamp(c, 0, CON_MAX); }

    // =========================================================
    // RNG (ThreadLocalRandom)
    // =========================================================
    private static ThreadLocalRandom tlr() {
        return ThreadLocalRandom.current();
    }
    private static int nextInt(int bound) {
        return tlr().nextInt(bound);
    }
    // =========================================================
    // Tables
    // =========================================================
    // HP base per CON; MP growth is computed by mpGrowthBound.
    private static final short[] HP_BASE = new short[CON_MAX + 1];

    static {
        buildHpBase();
    }

    // =========================================================
    // Public APIs
    // =========================================================
    public static int increaseMp(int type, byte Wis) {
        final int min = MinincreaseMp(type, Wis & 0xFF);
        final int max = MaxincreaseMp(type, Wis & 0xFF);
        return min + nextInt(max - min + 1);
    }

    public static int MinincreaseMp(int type, int Wis) {
        return mpGrowthBound(type, Wis, 5);
    }

    public static int MaxincreaseMp(int type, int Wis) {
        return mpGrowthBound(type, Wis, 3);
    }

    public static short increaseHp(int type, byte Con) {
        return (short) (PureHp(type, Con & 0xFF) + nextInt(2));
    }

    public static short PureHp(int type, int Con) {
        final int t = clampType(type);
        final int con = clampCon(Con);

        int hp = (HP_BASE[con] & 0xFFFF);

        if (t == 1 || t == 7) hp += 5;
        else if (t == 2) hp -= 2;
        else if (t == 3) hp -= 5;
        else if (t == 4) hp -= 1;
        else if (t == 5) hp += 1;
        else if (t == 6) hp -= 3;

        return (short) hp;
    }

    // Phil workbook Data!P6:W7: rational class factors + fixed class bonus.
    private static final int[] MP_NUMERATOR = {1, 2, 3, 2, 3, 7, 17, 2};
    private static final int[] MP_DENOMINATOR = {1, 3, 2, 1, 2, 10, 10, 3};
    private static final int[] MP_CLASS_ADD = {1, 0, 1, 2, 1, 1, 1, 0};
    private static int mpGrowthBound(int type, int wis, int divisor) {
        int t = clampType(type);
        int pureWis = Math.max(0, Math.min(127, wis));
        return ((pureWis / divisor) * MP_NUMERATOR[t]) / MP_DENOMINATOR[t] + MP_CLASS_ADD[t];
    }
    // Preserve this server's existing creation baselines; reset and creation
    // must share them. Research-sheet fixed initial MP is not a migration rule.
    private static final short[] INITIAL_HP = {14, 16, 15, 12, 12, 16, 14, 16};
    public static short initialHp(int type) { return INITIAL_HP[clampType(type)]; }
    public static short initialMp(int type, int pureWis) {
        return (short) switch (clampType(type)) {
        case 0 -> pureWis >= 16 ? 4 : pureWis >= 12 ? 3 : 2;
        case 1, 7 -> pureWis >= 12 ? 2 : 1;
        case 2 -> pureWis >= 16 ? 6 : 4;
        case 3 -> pureWis >= 16 ? 8 : 6;
        case 4 -> pureWis >= 16 ? 6 : pureWis >= 12 ? 4 : 3;
        case 5 -> 2;
        case 6 -> pureWis >= 16 ? 6 : 5;
        default -> throw new AssertionError();
        };
    }

    public static int pureHpMpBonus(int pureStat) {
        return (pureStat >= 25 ? 50 : 0) + (pureStat >= 35 ? 100 : 0) + (pureStat >= 45 ? 150 : 0);
    }
    public static int pureWisRegenBonus(int pureWis) {
        return pureWis >= 45 ? 5 : pureWis >= 35 ? 2 : pureWis >= 25 ? 1 : 0;
    }
    public static int naturalMpRegen(int level, int totalWis, int pureWis) {
        return Math.max(1, Math.max(0, totalWis) / 5 + Math.max(0, level) / 40 + pureWisRegenBonus(pureWis));
    }
    /** Adopt integer floor for odd half-ticks; independently gated for HP/MP. */
    public static int naturalRegenAfterWeight(int normal, boolean overweight, int pureStat) {
        if (normal <= 0) return 0;
        return !overweight ? normal : pureStat >= 45 ? normal / 2 : 0;
    }

    // =========================================================
    // Build: HP base table (con -> base)
    // =========================================================
    private static void buildHpBase() {
        for (int c = 0; c <= CON_MAX; c++) {
            HP_BASE[c] = (short) hpBaseFromCon(c);
        }
    }

    private static int hpBaseFromCon(int con) {
        if (con <= 12) return 12;
        if (con <= 25) return con;
        if (con <= 26) return 25;
        if (con <= 28) return 26;
        if (con <= 30) return 27;
        if (con <= 32) return 28;
        if (con <= 34) return 29;
        if (con <= 36) return 30;
        if (con <= 38) return 31;
        if (con <= 40) return 32;
        if (con <= 42) return 33;
        if (con <= 44) return 34;
        if (con <= 46) return 35;
        if (con <= 48) return 36;
        if (con <= 50) return 37;
        if (con <= 52) return 38;
        if (con <= 54) return 39;
        if (con <= 56) return 40;
        if (con <= 58) return 41;
        if (con <= 60) return 42;
        if (con <= 62) return 43;
        if (con <= 64) return 44;
        return 45;
    }

    // Legacy public API aliases. All game-source callers use the Korean table APIs
    // directly; these delegates protect extensions compiled against old CalcStat.
    @Deprecated public static int calcDmgup(int str) { return 근거리대미지(str); }
    @Deprecated public static int calcHitup(int str) { return 근거리명중(str); }
    @Deprecated public static int calcDmgCritical(int str) { return 근거리치명타(str); }
    @Deprecated public static int calcBowDmgup(int dex) { return 원거리대미지(dex); }
    @Deprecated public static int calcBowHitup(int dex) { return 원거리명중(dex); }
    @Deprecated public static int calcBowCritical(int dex) { return 원거리치명타(dex); }
    @Deprecated public static int calcAc(int dex) { return 물리방어력(dex); }
    @Deprecated public static int calcMagicDmg(int intel) { return 마법대미지(intel); }
    @Deprecated public static int calcMagicHitUp(int intel) { return 마법적중(intel); }
    @Deprecated public static int calcMagicCritical(int intel) { return 마법치명타(intel); }
    @Deprecated public static int calcMagicBonus(int type, int intel) { return 마법보너스(type, intel); }
    @Deprecated public static int calcDecreaseMp(int intel) { return 엠소모감소(intel); }
    @Deprecated public static int calcMpr(int wis) { return 엠회복틱(wis); }
    @Deprecated public static int calcMprPotion(int wis) { return 엠피물약회복(wis); }
    @Deprecated public static int calcStatMr(int type, int wis) { return 마법방어(type, wis); }
    @Deprecated public static int calcHpr(int con) { return 피회복틱(con); }
    @Deprecated public static double calcHprPotion(int con) { return 물약회복증가(con); }
    @Deprecated public static int getMaxWeight(int str, int con) { return 최대소지무게(str, con); }
    @Deprecated public static int get_consume_mp(int intel) { return 엠소모감소(intel); }
}
