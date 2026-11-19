package l1j.server.server.model.item;

import java.util.ArrayList;
import java.util.EnumMap;
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

    /** 툴팁 렌더 섹션 — 패킷의 어느 위치에 쓸지 구분 */
    public enum TooltipSection { EFFECT, POTION, AC_LINE }

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
        private List<Bonus>[] byClass;            // 파생 인덱스 — 루프에서 조건 제거용

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
            byClass = new List[8];   // 클래스 0~7
            for (int c = 0; c < 8; c++) byClass[c] = new ArrayList<>();
            for (Bonus b : bonuses) {
                if (b.classType == -1) {
                    for (int c = 0; c < 8; c++) byClass[c].add(b);
                } else if (b.classType >= 0 && b.classType < 8) {
                    byClass[b.classType].add(b);
                } else {
                    _log.severe("[ItemEffectRegistry] 잘못된 classType=" + b.classType
                            + " (value=" + b.value + ") — 해당 보너스 스킵. 정의 파일 확인 요망");
                }
            }
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
            ensureIndex();
            final List<Bonus> source = (classType >= 0 && classType < byClass.length)
                    ? byClass[classType] : bonuses;
            for (Bonus b : source) {
                if (b.classType != -1 && b.classType != classType) continue;   // 폴백 경로용 재필터
                if (b.enchantLevel != -1 && b.enchantLevel != enchantLevel) continue;
                final int v = b.value * sign;
                if (b.stat != null) b.stat.apply(pc, v);
            }
        }

        public String getMagicname() { return magicnameText; }
        public String getTooltipText() { return tooltipText; }
        public boolean isAlwaysAcLine() { return alwaysAcLine; }
        public List<Bonus> allBonuses() { return bonuses; }

        /** AC 라인 표시값(절대값) — 해당 인챈트 정의 없으면 0 */
        public int acLineValue(int enchantLevel) {
            for (Bonus b : bonuses) {
                if (b.enchantLevel == enchantLevel && b.stat == ItemStat.AC) return Math.abs(b.value);
            }
            return 0;
        }
        
        public Map<ItemStat, Integer> aggregatedStatsFor(TooltipSection section, int classType, int enchantLevel) {
            ensureIndex();
            Map<ItemStat, Integer> out = new EnumMap<>(ItemStat.class);
            final List<Bonus> source = (classType >= 0 && classType < byClass.length) ? byClass[classType] : bonuses;
            for (Bonus b : source) {
                if (b.classType != -1 && b.classType != classType) continue;
                if (b.enchantLevel != -1 && b.enchantLevel != enchantLevel) continue;
                if (b.stat == null) continue;
                switch (section) {
                case EFFECT:
                    if (b.stat.wt == ItemStat.WType.B || b.stat.wt == ItemStat.WType.H)
                        out.merge(b.stat, b.value, Integer::sum);
                    break;
                case POTION:
                    if (b.stat.wt == ItemStat.WType.POTION || b.stat.wt == ItemStat.WType.HEAL)
                        out.merge(b.stat, b.value, Integer::sum);
                    break;
                default: break;
                }
            }
            out.values().removeIf(v -> v == 0);
            return out;
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