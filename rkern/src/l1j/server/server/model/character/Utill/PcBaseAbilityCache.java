package l1j.server.server.model.character.Utill;

import l1j.server.server.model.Instance.L1PcInstance;

/**
 * PcBaseAbilityCache
 * - 클래스/레벨 기반 베이스(명중/데미지/치명/PVPDR/매직레벨)만 계산해서 캐시
 * - STR/DEX/INT 테이블은 CalcStat이 담당하므로 여기에는 중복 적용하지 않는다.
 */
public final class PcBaseAbilityCache {

    // One publication carries its own class/level identity. A reader cannot
    // observe a key from one calculation and values from another.
    private static final BaseAbility EMPTY = new BaseAbility(Integer.MIN_VALUE, Integer.MIN_VALUE);
    private volatile BaseAbility base = EMPTY;
    private volatile boolean invalidated = true;

    public BaseAbility get() {
        return base;
    }

    public synchronized void invalidate() {
        invalidated = true;
    }

    public void refresh(final L1PcInstance pc) {
        ValidateBaseData(pc);
    }

    public BaseAbility ValidateBaseData(final L1PcInstance pc) {
        BaseAbility current = base;
        if (pc == null) return current;
        final int level = pc.getLevel();
        final int type = pc.getType();
        if (!invalidated && current.level == level && current.type == type) return current;
        synchronized (this) {
            // Re-read after entering; do not publish a request captured before
            // another refresh while waiting for the monitor.
            final int actualLevel = pc.getLevel();
            final int actualType = pc.getType();
            current = base;
            if (!invalidated && current.level == actualLevel && current.type == actualType) return current;
            final BaseAbility next = new BaseAbility(actualLevel, actualType);
            applyBaseHit(next, actualType, actualLevel);
            applyBaseDmg(next, actualType, actualLevel);
            applyBaseCritical(next, actualType, actualLevel);
            applyBasePvpDr(next, actualType, actualLevel);
            applyMagicLevel(next, actualType, actualLevel);
            base = next;
            invalidated = false;
            return next;
        }
    }

    // ---------------- NO-IF tables ----------------

    private static final int MAX_TYPE = 16;

    private static final int[] HIT_DIV       = new int[MAX_TYPE + 1];
    private static final int[] BOW_HIT_DIV   = new int[MAX_TYPE + 1];
    private static final int[] MAGIC_HIT_DIV = new int[MAX_TYPE + 1];

    private static final int[] DMG_DIV       = new int[MAX_TYPE + 1];
    private static final int[] BOW_DMG_DIV   = new int[MAX_TYPE + 1];
    private static final int[] MAGIC_DMG_DIV = new int[MAX_TYPE + 1];

    private static final int[] CRI_DIV       = new int[MAX_TYPE + 1];
    private static final int[] BOW_CRI_DIV   = new int[MAX_TYPE + 1];
    private static final int[] MAGIC_CRI_DIV = new int[MAX_TYPE + 1];

    private static final double[] PVP_DR_DIV = new double[MAX_TYPE + 1];

    private static final int[] MAGIC_LV_DIV  = new int[MAX_TYPE + 1];
    private static final int[] MAGIC_LV_CAP  = new int[MAX_TYPE + 1];

    static {
        // 0 군주 1 기사 2 요정 3 마법사 4 다크 5 용기사 6 환술 7 전사
        final int CROWN=0, KNIGHT=1, ELF=2, WIZ=3, DARK=4, DK=5, ILL=6, WAR=7;

        setHit(CROWN, 4, 6, 20);
        setHit(KNIGHT,3, 6,100);
        setHit(WAR,   3, 6,100);
        setHit(ELF,   5, 3, 16);
        setHit(DARK,  3, 4, 24);
        setHit(DK,    4, 7, 18);
        setHit(ILL,   5, 8, 12);
        setHit(WIZ,   0, 0,  8);

        setDmg(CROWN,30,40,40);
        setDmg(KNIGHT,10,40,40);
        setDmg(ELF,  30,10,30);
        setDmg(WIZ,  40,80,10);
        setDmg(DARK, 10,20,40);
        setDmg(DK,   10,40,40);
        setDmg(ILL,  10,40,25);
        setDmg(WAR,  10,40,40);

        setCri(CROWN,20,30,80);
        setCri(KNIGHT,20,40,100);
        setCri(ELF,  24,16,30);
        setCri(WIZ,  30,50, 2);
        setCri(DARK, 10,20,30);
        setCri(DK,   20,40,70);
        setCri(ILL,  30,50,20);
        setCri(WAR,   20,40,100);

        setPvpDr(CROWN,4.0);
        setPvpDr(KNIGHT,2.0);
        setPvpDr(WAR,3.0);
        setPvpDr(ELF,4.0);
        setPvpDr(DARK,3.0);
        setPvpDr(DK,3.0);
        setPvpDr(ILL,3.0);
        setPvpDr(WIZ,4.0);

        setMagicLevel(CROWN, 15, 2);
        setMagicLevel(KNIGHT,50, 1);
        setMagicLevel(ELF,   12, 7);
        setMagicLevel(WIZ,   6, 15);
        setMagicLevel(DARK,  15, 2);
        setMagicLevel(DK,    10, 4);
        setMagicLevel(ILL,   8, 11);
        setMagicLevel(WAR,   50, 1);
    }

    private static void setHit(int type, int hitDiv, int bowDiv, int magicDiv) {
        HIT_DIV[type]=hitDiv; BOW_HIT_DIV[type]=bowDiv; MAGIC_HIT_DIV[type]=magicDiv;
    }
    private static void setDmg(int type, int dmgDiv, int bowDiv, int magicDiv) {
        DMG_DIV[type]=dmgDiv; BOW_DMG_DIV[type]=bowDiv; MAGIC_DMG_DIV[type]=magicDiv;
    }
    private static void setCri(int type, int criDiv, int bowDiv, int magicDiv) {
        CRI_DIV[type]=criDiv; BOW_CRI_DIV[type]=bowDiv; MAGIC_CRI_DIV[type]=magicDiv;
    }
    private static void setPvpDr(int type, double div) {
        PVP_DR_DIV[type]=div;
    }
    private static void setMagicLevel(int type, int div, int cap) {
        MAGIC_LV_DIV[type] = div;
        MAGIC_LV_CAP[type] = cap;
    }

    private static void applyBaseHit(final BaseAbility base, final int type, final int level) {
        final int hitDiv = safeI(HIT_DIV, type);
        final int bowDiv = safeI(BOW_HIT_DIV, type);
        final int magDiv = safeI(MAGIC_HIT_DIV, type);

        int hit = level;
        if (hitDiv != 0) hit += (level / hitDiv);
        base.hit = hit;

        int bowHit = level;
        if (bowDiv != 0) bowHit += (level / bowDiv);
        base.bowHit = bowHit;

        base.magicHit = (magDiv==0)?0:(level / magDiv);
    }

    private static void applyBaseDmg(final BaseAbility base, final int type, final int level) {
        final int dmgDiv = safeI(DMG_DIV, type);
        final int bowDiv = safeI(BOW_DMG_DIV, type);
        final int magDiv = safeI(MAGIC_DMG_DIV, type);

        base.dmg      = (dmgDiv==0)?0:(level / dmgDiv);
        base.bowDmg   = (bowDiv==0)?0:(level / bowDiv);
        base.magicDmg = (magDiv==0)?0:(level / magDiv);
    }

    private static void applyBaseCritical(final BaseAbility base, final int type, final int level) {
        final int baseValue = (level > 50) ? (level - 50) : 0;

        final int criDiv = safeI(CRI_DIV, type);
        final int bowDiv = safeI(BOW_CRI_DIV, type);
        final int magDiv = safeI(MAGIC_CRI_DIV, type);

        base.cri      = (criDiv==0)?0:(baseValue / criDiv);
        base.bowCri   = (bowDiv==0)?0:(baseValue / bowDiv);
        base.magicCri = (magDiv==0)?0:(baseValue / magDiv);
    }

    private static void applyBasePvpDr(final BaseAbility base, final int type, final int level) {
        final int lv = level - 60;
        final double div = safeD(PVP_DR_DIV, type);

        base.pvpDr = (lv <= 0 || div == 0.0) ? 0 : (int) Math.floor(lv / div);
    }

    private static void applyMagicLevel(final BaseAbility base, final int type, final int level) {
        final int div = safeI(MAGIC_LV_DIV, type);
        final int cap = safeI(MAGIC_LV_CAP, type);

        if (div <= 0 || cap <= 0) {
            base.magicLevel = 0;
            return;
        }

        int ml = level / div;
        if (ml > cap) ml = cap;
        base.magicLevel = ml;
    }

    private static int safeI(final int[] arr, final int idx) {
        return (idx < 0 || idx >= arr.length) ? 0 : arr[idx];
    }
    private static double safeD(final double[] arr, final int idx) {
        return (idx < 0 || idx >= arr.length) ? 0.0 : arr[idx];
    }

    /** Use the same class/level table for the existing CAttackAction hit API. */
    public static int baseHitFor(int type, int level, boolean ranged) {
        final int div = safeI(ranged ? BOW_HIT_DIV : HIT_DIV, type);
        return level + (div == 0 ? 0 : level / div);
    }

    /** 베이스만 담는 구조체 */
    public static final class BaseAbility {
        private final int level, type;

        // Preserve the previous public no-argument constructor for callers.
        public BaseAbility() { this(Integer.MIN_VALUE, Integer.MIN_VALUE); }

        private BaseAbility(int level, int type) {
            this.level = level;
            this.type = type;
        }
        private int dmg, bowDmg, magicDmg;
        private int hit, bowHit, magicHit;
        private int cri, bowCri, magicCri;
        private int pvpDr;
        private int magicLevel;

        public int get근거리대미지(){ return dmg; }
        public int get원거리대미지(){ return bowDmg; }
        public int get마법대미지(){ return magicDmg; }

        public int get근거리명중률(){ return hit; }
        public int get원거리명중률(){ return bowHit; }
        public int get마법적중(){ return magicHit; }

        public int get근거리치명률(){ return cri; }
        public int get원거리치명률(){ return bowCri; }
        public int get마법치명타(){ return magicCri; }

        public int getPVP대미지리덕션(){ return pvpDr; }
        public int get매직레벨(){ return magicLevel; }
    }
}
