package l1j.server.server.model.item;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 특수 아이템 효과 정의
 * 장착/해제/툴팁 전부 이 정의 하나에서 파생된다.
 * registry에 없는 아이템은 null 반환 → 아무 동작 없음 
 */
public final class ItemEffectRegistry {

    private static final Logger _log = Logger.getLogger(ItemEffectRegistry.class.getName());

    public static final class Bonus {
        public final int classType;      // -1 = 전 직업 공통
        public final int enchantLevel;   // -1 = 인챈트 무관
        public final ItemStat stat;      // null이면 pierce
        public final int value;
        Bonus(int classType, int enchantLevel, ItemStat stat, int value) {
            this.classType = classType; this.enchantLevel = enchantLevel;
            this.stat = stat; this.value = value;
        }
    }

    public static final class Def {
        private final String tooltipText;         // null = 텍스트 라인 없음
        private final String magicnameText;       // null = 매직네임 없음
        private final boolean alwaysAcLine;       // AC 라인을 인챈트 무관 항상 표기
        private final List<Bonus> bonuses = new ArrayList<>();
        private List<ItemStat> stats;
        private volatile List<Bonus>[] byClass;            // 파생 인덱스 — 루프에서 조건 제거용

        public Def() { 
        	this.tooltipText = null; 
        	this.alwaysAcLine = false; 
        	this.magicnameText = null; 
        }
        public Def(String tooltipText) { 
        	this.tooltipText = tooltipText;
        	this.alwaysAcLine = false; 
         	this.magicnameText = null; 
        }
        public Def(String tooltipText, boolean alwaysAcLine) {
        	this.tooltipText = tooltipText; 
        	this.alwaysAcLine = alwaysAcLine;
          	this.magicnameText = null; 
        }
        public Def(String tooltipText, boolean alwaysAcLine, String magicnameText) {
        	this.tooltipText = tooltipText; 
        	this.alwaysAcLine = alwaysAcLine;
         	this.magicnameText = magicnameText; 
        }

        public Def add(int classType, ItemStat stat, int v)        { bonuses.add(new Bonus(classType, -1, stat, v)); return this; }
        public Def addEnchant(int lvl, ItemStat stat, int v)       { bonuses.add(new Bonus(-1, lvl, stat, v)); return this; }

        /** 클래스별 파생 인덱스 구축 — register()에서 호출, 멱등(재호출 무해) */
        @SuppressWarnings("unchecked")
        void buildIndex() {
            // Most item definitions apply to every class. Share one immutable
            // snapshot instead of retaining eight identical ArrayLists per item.
            final List<Bonus>[] next = new List[8];
            final ArrayList<Bonus> universal = new ArrayList<>();
            final EnumSet<ItemStat> definedStats = EnumSet.noneOf(ItemStat.class);
            int specializedMask = 0;
            for (Bonus b : bonuses) {
                if (b.stat != null) definedStats.add(b.stat);
                if (b.classType == -1) {
                    universal.add(b);
                } else if (b.classType >= 0 && b.classType < 8) {
                    specializedMask |= 1 << b.classType;
                } else {
                    _log.severe("[ItemEffectRegistry] 잘못된 classType=" + b.classType
                            + " (value=" + b.value + ") — 해당 보너스 스킵. 정의 파일 확인 요망");
                }
            }
            final List<Bonus> shared = List.copyOf(universal);
            for (int c = 0; c < 8; c++) {
                if ((specializedMask & (1 << c)) == 0) {
                    next[c] = shared;
                    continue;
                }
                // Preserve exact definition order when common and class-specific
                // bonuses are interleaved; apply() must remain order-equivalent.
                final ArrayList<Bonus> applicable = new ArrayList<>();
                for (Bonus b : bonuses) {
                    if (b.classType == -1 || b.classType == c) applicable.add(b);
                }
                next[c] = applicable;
            }
            stats = List.copyOf(definedStats);
            byClass = next;
        }

        /** 지연 초기화 — byClass 미구축 방어 (register 우회 시에도 안전) */
        private void ensureIndex() {
            if (byClass == null) buildIndex();
        }

        /**
         * 장착(+1)/해제(-1). 같은 정의를 역적용하므로 음수를 손으로 쓰는 실수가 구조적으로 불가능.
         * classType이 0~7 범위 밖(-1 등, 소유자 불명)이면 전 직업 공통 보너스만 적용.
         */
        public void apply(L1PcInstance pc, int classType, int enchantLevel, int sign) {
            applyOrCollect(pc, classType, enchantLevel, sign, null);
        }

        public void collectModifiers(int classType, int enchantLevel, int[] totals) {
            applyOrCollect(null, classType, enchantLevel, 1, totals);
        }

        private void applyOrCollect(L1PcInstance pc, int classType, int enchantLevel, int sign, int[] totals) {
            ensureIndex();
            final List<Bonus> source = (classType >= 0 && classType < byClass.length)
                    ? byClass[classType] : bonuses;
            for (int i = 0; i < source.size(); i++) {
                final Bonus b = source.get(i);
                if (b.classType != -1 && b.classType != classType) continue;   // 폴백 경로용 재필터
                if (b.enchantLevel != -1 && b.enchantLevel != enchantLevel) continue;
                if (b.stat == null) continue;
                final int value = b.value * sign;
                if (totals == null) b.stat.apply(pc, value);
                else totals[b.stat.ordinal()] += value;
            }
        }

        public boolean addEnchantDelta(int classType, int oldEnchant, int enchant, int[] changes) {
            final List<ItemStat> defined = stats();
            boolean changed = false;
            for (int i = 0; i < defined.size(); i++) {
                final ItemStat stat = defined.get(i);
                final int delta = statValue(stat, classType, enchant) - statValue(stat, classType, oldEnchant);
                if (delta == 0) continue;
                changes[stat.ordinal()] += delta;
                changed = true;
            }
            return changed;
        }

        public List<ItemStat> stats() {
            ensureIndex();
            return stats;
        }

        public int statValue(ItemStat stat, int classType, int enchantLevel) {
            ensureIndex();
            final List<Bonus> source = classType >= 0 && classType < byClass.length
                    ? byClass[classType] : bonuses;
            int sum = 0;
            for (int i = 0; i < source.size(); i++) {
                final Bonus bonus = source.get(i);
                if (bonus.stat == stat && (bonus.classType == -1 || bonus.classType == classType)
                        && (bonus.enchantLevel == -1 || bonus.enchantLevel == enchantLevel)) {
                    sum += bonus.value;
                }
            }
            return sum;
        }

        public String getMagicname() { return magicnameText; }
        public String getTooltipText() { return tooltipText; }
        public boolean isAlwaysAcLine() { return alwaysAcLine; }
        public List<Bonus> allBonuses() { return bonuses; }

        /**
         * Actual equipped AC contribution, not the first matching definition.
         * Multiple AC bonuses and class-specific modifiers must be summed
         * identically by the equip and display paths.
         */
        public int acLineValue(int classType, int enchantLevel) {
            return Math.abs(statValue(ItemStat.AC, classType, enchantLevel));
        }

        /** Compatibility for callers without an owner: common bonuses only. */
        public int acLineValue(int enchantLevel) {
            return acLineValue(-1, enchantLevel);
        }
        

    }

    private static final Map<Integer, Def> DEFS = new ConcurrentHashMap<>();

    public static Def get(int itemId) { return DEFS.get(itemId); }

    public static int size() { return DEFS.size(); }

    /**
     * 아이템 효과 정의 등록.
     * 중복 등록은 조용한 덮어쓰기가 아니라 경고 — 카테고리 파일 분리 운영 시 실수 조기 발견용.
     */
    public static void register(int itemId, Def def, String source) {
        def.buildIndex();
        final Def prev = DEFS.put(itemId, def);
        if (prev != null) {
            _log.warning("[ItemEffectRegistry] ★중복 등록★ itemId=" + itemId
                    + " 새 정의(" + source + ")가 기존 정의를 덮어씀 — 확인 필요");
        }
        _log.fine("[ItemEffectRegistry] 등록: itemId=" + itemId
                + " 보너스 " + def.allBonuses().size() + "건 (" + source + ")");
    }

    /** source 없이 등록 (간단 호출용) */
    public static void register(int itemId, Def def) {
        register(itemId, def, "?");
    }
    
    
}
