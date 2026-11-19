package l1j.server.server.model.item.doll;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;

import static l1j.server.server.model.Instance.L1DollInstance.*;

public final class DollRegistry {
    private static final Logger _log = Logger.getLogger(DollRegistry.class.getName());
    
    public enum MagicMode { 
        MAGIC, 
        MAGIC2 
    }
    
    private interface Value { 
        int get(); 
    }
    
    private static final class FixedValue implements Value { 
        final int v; 
        
        FixedValue(int v) {
            this.v = v;
        } 
        
        public int get() {
            return v;
        } 
    }
    
    private static final class DynamicValue implements Value { 
        final IntSupplier s; 
        
        DynamicValue(IntSupplier s) {
            if (s == null) throw new NullPointerException("supplier");
            this.s = s;
        } 
        
        public int get() {
            return s.getAsInt();
        } 
    }

    public static final class DisplayEntry {
        private final DollOption option; 
        private final Value value;
        
        private DisplayEntry(DollOption option, Value value) {
            this.option = option;
            this.value = value;
        }
        
        public DollOption getOption() {
            return option;
        } 
        
        public int getValue() {
            return value.get();
        }
    }
    
    public static final class Profile {
        private final int dollType; 
        private final String name; 
        private final Value[] runtime;
        private final DisplayEntry[] display; 
        private final DisplayEntry[] direct;
        private final MagicMode magicMode; 
        private final String magicText;
        
        private Profile(int t, String n, Value[] r, DisplayEntry[] d, DisplayEntry[] a, MagicMode mm, String mt) {
            dollType = t;
            name = n;
            runtime = r;
            display = d;
            direct = a;
            magicMode = mm;
            magicText = mt;
        }
        
        public int getDollType() {
            return dollType;
        } 
        
        public String getName() {
            return name;
        }
        
        public int getRuntimeValue(DollOption o) {
            Value v = o == null ? null : runtime[o.ordinal()];
            return v == null ? 0 : v.get();
        }
        
        public int displayCount() {
            return display.length;
        } 
        
        public DisplayEntry displayAt(int i) {
            return display[i];
        }
        
        public MagicMode getMagicMode() {
            return magicMode;
        } 
        
        public String getMagicText() {
            return magicText;
        }
        
        private void applyDirect(L1PcInstance pc, int sign) {
            if (pc == null || sign == 0) return;
            for (DisplayEntry e : direct) {
                int v = e.getValue();
                if (v != 0) e.getOption().apply(pc, v * sign);
            }
        }
    }
    
    public static final class Def {
        private final int itemId, npcId, crystalCost, maxLevelExclusive; 
        private final Profile profile;
        
        private Def(int i, int n, int c, int m, Profile p) {
            itemId = i;
            npcId = n;
            crystalCost = c;
            maxLevelExclusive = m;
            profile = p;
        }
        
        public int getItemId() { return itemId; } 
        public int getNpcId() { return npcId; } 
        public int getDollType() { return profile.getDollType(); }
        public String getName() { return profile.getName(); } 
        public int getCrystalCost() { return crystalCost; } 
        public int getMaxLevelExclusive() { return maxLevelExclusive; } 
        public Profile getProfile() { return profile; }
    }
    
    private static final class OD { 
        Value runtime, display; 
    }
    
    private static final class PD { 
        final int type; 
        final String name; 
        final EnumMap<DollOption, OD> o = new EnumMap<>(DollOption.class); 
        MagicMode mm; 
        String mt; 
        
        PD(int t, String n) {
            type = t;
            name = n;
        } 
        
        OD get(DollOption k) {
            OD d = o.get(k);
            if (d == null) {
                d = new OD();
                o.put(k, d);
            }
            return d;
        } 
    }
    
    private static final class ID { 
        final int item, npc, type, cost, max; 
        
        ID(int i, int n, int t, int c, int m) {
            item = i;
            npc = n;
            type = t;
            cost = c;
            max = m;
        } 
    }
    
    private static final class Builder {
        final Map<Integer, PD> ps = new HashMap<>(); 
        final Map<Integer, ID> is = new HashMap<>();
        
        PB doll(int item, int npc, int type, String name, int cost, int max) {
            if (item <= 0 || npc <= 0 || type <= 0 || type >= 128) throw new IllegalArgumentException();
            if (is.containsKey(item)) throw new IllegalStateException("duplicate itemId=" + item);
            
            PD p = ps.get(type);
            if (p == null) {
                p = new PD(type, name);
                ps.put(type, p);
            }
            is.put(item, new ID(item, npc, type, cost, max));
            return new PB(p);
        }
        
        R freeze() {
            Profile[] bt = new Profile[128];
            for (PD p : ps.values()) {
                Value[] rv = new Value[DollOption.values().length];
                int dc = 0, ac = 0;
                
                for (Map.Entry<DollOption, OD> e : p.o.entrySet()) {
                    if (e.getValue().runtime != null) {
                        rv[e.getKey().ordinal()] = e.getValue().runtime;
                        if (e.getKey().isDirectCapable()) ac++;
                    }
                    if (e.getValue().display != null) dc++;
                }
                
                DisplayEntry[] de = new DisplayEntry[dc], ae = new DisplayEntry[ac];
                int di = 0, ai = 0;
                
                for (DollOption k : DollOption.values()) {
                    OD d = p.o.get(k);
                    if (d == null) continue;
                    
                    if (d.display != null) de[di++] = new DisplayEntry(k, d.display);
                    if (d.runtime != null && k.isDirectCapable()) ae[ai++] = new DisplayEntry(k, d.runtime);
                    
                    if (d.runtime != null && d.display != null) {
                        int a = d.runtime.get(), b = d.display.get();
                        if (a != b) _log.warning("[DOLL LEGACY SPLIT] type=" + p.type + " name=" + p.name + " option=" + k + " runtime=" + a + " display=" + b);
                    } else if (d.runtime == null && d.display != null && k.isDirectCapable()) {
                        _log.warning("[DOLL DISPLAY ONLY] type=" + p.type + " name=" + p.name + " option=" + k + " display=" + d.display.get());
                    }
                }
                bt[p.type] = new Profile(p.type, p.name, rv, de, ae, p.mm, p.mt);
            }
            
            Map<Integer, Def> bi = new HashMap<>(is.size() * 2);
            for (ID d : is.values()) {
                Profile p = bt[d.type];
                if (p == null) throw new IllegalStateException("no profile type=" + d.type);
                bi.put(d.item, new Def(d.item, d.npc, d.cost, d.max, p));
            }
            return new R(Collections.unmodifiableMap(bi), bt);
        }
    }
    
    private static final class PB { 
        final PD p; 
        
        PB(PD p) {
            this.p = p;
        } 
        
        Value f(int v) { return new FixedValue(v); } 
        Value d(IntSupplier s) { return new DynamicValue(s); } 
        
        void check(DollOption o) {
            if (!o.canDisplay()) throw new IllegalStateException("no tooltip writer: " + o + " type=" + p.type);
        }
        
        PB both(DollOption o, int v) {
            check(o);
            OD x = p.get(o);
            if (x.runtime != null || x.display != null) dup(o);
            Value z = f(v);
            x.runtime = z;
            x.display = z;
            return this;
        }
        
        PB dynamicBoth(DollOption o, IntSupplier s) {
            check(o);
            OD x = p.get(o);
            if (x.runtime != null || x.display != null) dup(o);
            Value z = d(s);
            x.runtime = z;
            x.display = z;
            return this;
        }
        
        PB runtimeOnly(DollOption o, int v) {
            OD x = p.get(o);
            if (x.runtime != null) dup(o);
            x.runtime = f(v);
            return this;
        }
         
        PB magic(MagicMode m, String s) {
            if (p.mt != null) throw new IllegalStateException("duplicate magic type=" + p.type);
            p.mm = m;
            p.mt = s;
            return this;
        } 
        
        void dup(DollOption o) {
            throw new IllegalStateException("duplicate option type=" + p.type + " option=" + o);
        }
    }
    
    private static final class R { 
        final Map<Integer, Def> bi; 
        final Profile[] bt; 
        
        R(Map<Integer, Def> i, Profile[] t) {
            bi = i;
            bt = t;
        } 
    }
    
    private static final Map<Integer, Def> BY_ITEM_ID; 
    private static final Profile[] BY_TYPE;
    
    static { 
        Builder b = new Builder();
        
        b.doll(210070, 200017, DOLLTYPE_STONEGOLEM, "돌 골렘", 50, 0)
                .both(DollOption.DAMAGE_REDUCTION, 1);
                
        b.doll(41250, 80108, DOLLTYPE_WAREWOLF, "늑대인간", 50, 0)
                .both(DollOption.PERCENT_DMG, 15);
                
        b.doll(41248, 80106, DOLLTYPE_BUGBEAR, "버그베어", 50, 0)
                .both(DollOption.WEIGHT_REDUCTION, 10);
                
        b.doll(210072, 200019, DOLLTYPE_CRUSTACEA, "크러스트 시안", 50, 0)
                .both(DollOption.PERCENT_DMG, 15);
                
        b.doll(210096, 200074, DOLLTYPE_SNOWMAN, "에티", 50, 0)
                .both(DollOption.AC, 3)
                .runtimeOnly(DollOption.SPIRIT_RESIS, 7);
                
        b.doll(740, 507, DOLLTYPE_MOKAK, "목각", 50, 0)
                .both(DollOption.MAX_HP, 50);
                
        b.doll(41249, 80107, DOLLTYPE_SUCCUBUS, "서큐버스", 50, 0)
                .both(DollOption.MP_UP, 15);
                
        b.doll(210071, 200018, DOLLTYPE_ELDER, "장로", 50, 0)
                .both(DollOption.MP_UP, 15);
                
        b.doll(210105, 200012, DOLLTYPE_COCA, "코카트리스", 50, 0)
                .both(DollOption.BOW_DMG, 1)
                .both(DollOption.BOW_HIT, 1);
                
        b.doll(750, 900233, DOLLTYPE_SNOWMAN_NEW, "눈사람", 50, 0)
                .runtimeOnly(DollOption.MELEE_DMG, 1)
                .runtimeOnly(DollOption.MELEE_HIT, 1);
                
        b.doll(510216, 900226, DOLLTYPE_SNOWMAN_A, "눈사람(A)", 50, 0)
                .runtimeOnly(DollOption.BOW_HIT, 5);
                
        b.doll(510217, 900227, DOLLTYPE_SNOWMAN_B, "눈사람(B)", 50, 0);
        b.doll(510218, 900228, DOLLTYPE_SNOWMAN_C, "눈사람(C)", 50, 0);
        
        b.doll(410172, 81212, DOLLTYPE_인어, "인어", 50, 0)
                .both(DollOption.EXP_RATE, 1);
                
        b.doll(741, 508, DOLLTYPE_LAVAGOLREM, "라바 골렘", 50, 0)
                .both(DollOption.MELEE_DMG, 1)
                .both(DollOption.DAMAGE_REDUCTION, 1);
                
        b.doll(510219, 900229, DOLLTYPE_자이언트, "자이언트", 50, 0)
                .both(DollOption.DAMAGE_REDUCTION, 1)
                .both(DollOption.BLESS_RATE, 5)
                .both(DollOption.EXP_RATE, 10);
                
        b.doll(510221, 900231, DOLLTYPE_흑장로, "흑장로", 50, 0)
                .both(DollOption.MP_UP, 15)
                .magic(MagicMode.MAGIC2, "콜 라이트닝");
                
        b.doll(510222, 900232, DOLLTYPE_SUCCUBUSQUEEN, "서큐버스 퀸", 50, 0)
                .both(DollOption.SP, 1)
                .both(DollOption.MP_UP, 15);
                
        b.doll(447017, 900225, DOLLTYPE_DRAKE, "드레이크", 50, 0)
                .both(DollOption.BOW_DMG, 2)
                .both(DollOption.MP_UP, 6);
                
        b.doll(410173, 81213, DOLLTYPE_킹버그베어, "킹 버그베어", 50, 0)
                .both(DollOption.ABILITY_RESIS, 8)
                .both(DollOption.MP_UP, 10);
                
        b.doll(742, 509, DOLLTYPE_DIAMONDGOLREM, "다이아몬드 골렘", 50, 0)
                .both(DollOption.DAMAGE_REDUCTION, 2);
                
        b.doll(447016, 900224, DOLLTYPE_LICH, "리치", 50, 0)
                .both(DollOption.MAX_HP, 80)
                .both(DollOption.SP, 2);
                
        b.doll(510220, 900230, DOLLTYPE_사이클롭스, "사이클롭스", 50, 0)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.MELEE_HIT, 2)
                .both(DollOption.ABILITY_RESIS, 12);
                
        b.doll(743, 510, DOLLTYPE_NIGHTBALD, "나이트발드", 50, 0)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.MELEE_HIT, 2)
                .both(DollOption.ABILITY_PIERCE, 5);
                
        b.doll(744, 511, DOLLTYPE_SIER, "시어", 50, 0)
                .both(DollOption.BOW_DMG, 5)
                .both(DollOption.HP_UP, 30);
                
        b.doll(3000086, 7310082, DOLL_Iris, "아이리스", 50, 0)
                .both(DollOption.PC_DEFENSE, 3)
                .both(DollOption.FOU_DMG, 10);
                
        b.doll(3000087, 7310083, DOLL_vampire, "뱀파이어", 50, 0)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.MELEE_HIT, 2)
                .both(DollOption.FEAR_PIERCE, 3)
                .both(DollOption.ABILITY_RESIS, 5);
                
        b.doll(3000351, 7320069, DOLL_mummy, "머미로드", 50, 0)
                .both(DollOption.BLESS_RATE, 8)
                .both(DollOption.DAMAGE_REDUCTION, 2)
                .both(DollOption.EXP_RATE, 10)
                .both(DollOption.MPR, 15);
                
        b.doll(745, 512, DOLLTYPE_DEMON, "데몬", 50, 0)
                .both(DollOption.ABILITY_RESIS, 12)
                .both(DollOption.ABILITY_PIERCE, 10);
                
        b.doll(746, 513, DOLLTYPE_DEATHNIGHT, "데스나이트", 50, 0)
                .both(DollOption.BLESS_RATE, 10)
                .both(DollOption.DAMAGE_REDUCTION, 5)
                .both(DollOption.EXP_RATE, 20)
                .magic(MagicMode.MAGIC2, "헬 파이어");
                
        b.doll(3000088, 7310084, DOLL_barranca, "바란카", 50, 0)
                .both(DollOption.ABILITY_RESIS, 12)
                .both(DollOption.SPIRIT_PIERCE, 10);
                
        b.doll(3000352, 7320070, DOLL_corruption, "타락", 50, 0)
                .both(DollOption.SP, 3)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.DRAGON_PIERCE, 5)
                .both(DollOption.MAGIC_HIT, 5);
                
        b.doll(755, 514, DOLL_Baphomet, "바포메트", 50, 0)
                .both(DollOption.FEAR_PIERCE, 5)
                .both(DollOption.ABILITY_RESIS, 10);
                
        b.doll(756, 515, DOLL_icequeen, "얼음여왕", 50, 0)
                .both(DollOption.BOW_DMG, 5)
                .both(DollOption.BOW_HIT, 5)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.SPIRIT_PIERCE, 5);
                
        b.doll(757, 516, DOLL_Kouts, "커츠", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PC_DEFENSE, 3)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.DRAGON_PIERCE, 5)
                .both(DollOption.FOU_DMG, 10);
                
        b.doll(4100007, 7320200, Antaras, "안타라스", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 4)
                .both(DollOption.PVP_DMG_RED, 2)
                .both(DollOption.ALL_RESIS, 8)
                .both(DollOption.BLESS_RATE, 7)
                .both(DollOption.DAMAGE_REDUCTION, 6)
                .both(DollOption.EXP_RATE, 25)
                .both(DollOption.MP_UP, 15)
                .magic(MagicMode.MAGIC, "\\fe드래곤 피어");
                
        b.doll(4100008, 7320201, Papou, "파푸리온", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 4)
                .both(DollOption.PVP_DMG_RED, 2)
                .both(DollOption.ALL_RESIS, 8)
                .both(DollOption.ALL_PIERCE, 3)
                .both(DollOption.SP, 4)
                .both(DollOption.MAGIC_HIT, 8)
                .both(DollOption.MP_UP, 5)
                .magic(MagicMode.MAGIC2, "\\fe드래곤 피어");
                
        b.doll(4100009, 7320202, Lynd, "린드비오르", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 4)
                .both(DollOption.PVP_DMG_RED, 2)
                .both(DollOption.ALL_RESIS, 8)
                .both(DollOption.ALL_PIERCE, 3)
                .both(DollOption.BOW_HIT, 8)
                .both(DollOption.BOW_DMG, 4)
                .both(DollOption.MP_UP, 5)
                .magic(MagicMode.MAGIC2, "\\fe드래곤 피어");
                
        b.doll(4100010, 7320203, Valakas, "발라카스", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 4)
                .both(DollOption.PVP_DMG_RED, 2)
                .both(DollOption.ALL_RESIS, 8)
                .both(DollOption.ALL_PIERCE, 3)
                .both(DollOption.MELEE_DMG, 4)
                .both(DollOption.MELEE_HIT, 8)
                .both(DollOption.MP_UP, 5)
                .magic(MagicMode.MAGIC2, "\\fe드래곤 피어");
                
        b.doll(4100134, 7320261, jindeathknight, "진 데스나이트", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.ALL_RESIS, 9)
                .both(DollOption.ALL_PIERCE, 4)
                .both(DollOption.MELEE_DMG, 5)
                .both(DollOption.MELEE_HIT, 9)
                .both(DollOption.SP, 6)
                .both(DollOption.BOW_HIT, 8)
                .both(DollOption.BOW_DMG, 4)
                .both(DollOption.MAGIC_HIT, 8)
                .both(DollOption.PVP_DMG, 3)
                .both(DollOption.PVP_DMG_RED, 5)
                .both(DollOption.BLESS_RATE, 10)
                .both(DollOption.EXP_RATE, 20);
                
        b.doll(4200010, 7420201, pnix, "피닉스", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 3)
                .both(DollOption.PVP_DMG_RED, 5)
                .both(DollOption.ALL_RESIS, 9)
                .both(DollOption.ALL_PIERCE, 4)
                .both(DollOption.BOW_HIT, 8)
                .both(DollOption.BOW_DMG, 4)
                .both(DollOption.BLESS_RATE, 10)
                .both(DollOption.EXP_RATE, 20)
                .both(DollOption.MELEE_DMG, 5)
                .both(DollOption.MELEE_HIT, 9)
                .both(DollOption.SP, 6)
                .both(DollOption.MAGIC_HIT, 8);
                
        b.doll(4200020, 7420202, brich, "블랙리치", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 3)
                .both(DollOption.PVP_DMG_RED, 5)
                .both(DollOption.SP, 6)
                .both(DollOption.ALL_RESIS, 9)
                .both(DollOption.ALL_PIERCE, 4)
                .both(DollOption.MAGIC_HIT, 8)
                .both(DollOption.BLESS_RATE, 10)
                .both(DollOption.EXP_RATE, 20)
                .both(DollOption.MELEE_DMG, 5)
                .both(DollOption.MELEE_HIT, 9)
                .both(DollOption.BOW_HIT, 8)
                .both(DollOption.BOW_DMG, 4);
                
        b.doll(4200030, 7420203, behe, "베히모스", 50, 0)
                .dynamicBoth(DollOption.AC, () -> Config.베히추방)
                .dynamicBoth(DollOption.ALL_RESIS, () -> Config.베히올레즈)
                .dynamicBoth(DollOption.ALL_PIERCE, () -> Config.베히올적중)
                .dynamicBoth(DollOption.MELEE_DMG, () -> Config.베히근뎀)
                .dynamicBoth(DollOption.MELEE_HIT, () -> Config.베히근명)
                .dynamicBoth(DollOption.SP, () -> Config.베히스펠)
                .dynamicBoth(DollOption.BOW_HIT, () -> Config.베히원명)
                .dynamicBoth(DollOption.BOW_DMG, () -> Config.베히원뎀)
                .dynamicBoth(DollOption.PVP_DMG, () -> Config.베히pvp뎀)
                .dynamicBoth(DollOption.MAX_HP, () -> Config.베히맥스피)
                .dynamicBoth(DollOption.MAX_MP, () -> Config.베히맥스엠피)
                .dynamicBoth(DollOption.MAGIC_HIT, () -> Config.베히마법명중)
                .dynamicBoth(DollOption.PVP_DMG_RED, () -> Config.베히pvp리덕)
                .dynamicBoth(DollOption.BLESS_RATE, () -> Config.베히축복효율)
                .dynamicBoth(DollOption.EXP_RATE, () -> Config.베히추가경험치)
                .magic(MagicMode.MAGIC, "베히모스의 질투");
                
        b.doll(4200040, 7420204, au, "아우라키아", 50, 0)
                .dynamicBoth(DollOption.AC, () -> Config.아우라추방)
                .dynamicBoth(DollOption.ALL_RESIS, () -> Config.아우라올레즈)
                .dynamicBoth(DollOption.ALL_PIERCE, () -> Config.아우라올적중)
                .dynamicBoth(DollOption.MELEE_DMG, () -> Config.아우라근뎀)
                .dynamicBoth(DollOption.MELEE_HIT, () -> Config.아우라근명)
                .dynamicBoth(DollOption.SP, () -> Config.아우라스펠)
                .dynamicBoth(DollOption.BOW_HIT, () -> Config.아우라원명)
                .dynamicBoth(DollOption.BOW_DMG, () -> Config.아우라원뎀)
                .dynamicBoth(DollOption.PVP_DMG, () -> Config.아우라pvp뎀)
                .dynamicBoth(DollOption.MAX_HP, () -> Config.아우라맥스피)
                .dynamicBoth(DollOption.MAX_MP, () -> Config.아우라맥스엠피)
                .dynamicBoth(DollOption.MAGIC_HIT, () -> Config.아우라마법명중)
                .dynamicBoth(DollOption.PVP_DMG_RED, () -> Config.아우라pvp리덕)
                .dynamicBoth(DollOption.BLESS_RATE, () -> Config.아우라축복효율)
                .dynamicBoth(DollOption.EXP_RATE, () -> Config.아우라추가경험치)
                .magic(MagicMode.MAGIC, "아우라키아의 심판");
                
        b.doll(4200050, 7420205, hal, "할파스", 50, 0)
                .dynamicBoth(DollOption.AC, () -> Config.할파스추방)
                .dynamicBoth(DollOption.ALL_RESIS, () -> Config.할파스올레즈)
                .dynamicBoth(DollOption.ALL_PIERCE, () -> Config.할파스올적중)
                .dynamicBoth(DollOption.MELEE_DMG, () -> Config.할파스근뎀)
                .dynamicBoth(DollOption.MELEE_HIT, () -> Config.할파스근명)
                .dynamicBoth(DollOption.SP, () -> Config.할파스스펠)
                .dynamicBoth(DollOption.BOW_HIT, () -> Config.할파스원명)
                .dynamicBoth(DollOption.BOW_DMG, () -> Config.할파스원뎀)
                .dynamicBoth(DollOption.PVP_DMG, () -> Config.할파스pvp뎀)
                .dynamicBoth(DollOption.MAX_HP, () -> Config.할파스맥스피)
                .dynamicBoth(DollOption.MAX_MP, () -> Config.할파스맥스엠피)
                .dynamicBoth(DollOption.MAGIC_HIT, () -> Config.할파스마법명중)
                .dynamicBoth(DollOption.PVP_DMG_RED, () -> Config.할파스pvp리덕)
                .dynamicBoth(DollOption.BLESS_RATE, () -> Config.할파스축복효율)
                .dynamicBoth(DollOption.EXP_RATE, () -> Config.할파스추가경험치)
                .magic(MagicMode.MAGIC, "할파스의 분노");
                
        b.doll(4200060, 7420206, desel, "드래곤슬레이어", 50, 0)
                .dynamicBoth(DollOption.AC, () -> Config.드슬추방)
                .dynamicBoth(DollOption.ALL_RESIS, () -> Config.드슬올레즈)
                .dynamicBoth(DollOption.ALL_PIERCE, () -> Config.드슬올적중)
                .dynamicBoth(DollOption.MELEE_DMG, () -> Config.드슬근뎀)
                .dynamicBoth(DollOption.MELEE_HIT, () -> Config.드슬근명)
                .dynamicBoth(DollOption.SP, () -> Config.드슬스펠)
                .dynamicBoth(DollOption.BOW_HIT, () -> Config.드슬원명)
                .dynamicBoth(DollOption.BOW_DMG, () -> Config.드슬원뎀)
                .dynamicBoth(DollOption.PVP_DMG, () -> Config.드슬pvp뎀)
                .dynamicBoth(DollOption.MAX_HP, () -> Config.드슬맥스피)
                .dynamicBoth(DollOption.MAX_MP, () -> Config.드슬맥스엠피)
                .dynamicBoth(DollOption.MAGIC_HIT, () -> Config.드슬마법명중)
                .dynamicBoth(DollOption.PVP_DMG_RED, () -> Config.드슬pvp리덕)
                .dynamicBoth(DollOption.BLESS_RATE, () -> Config.드슬축복효율)
                .dynamicBoth(DollOption.EXP_RATE, () -> Config.드슬추가경험치)
                .magic(MagicMode.MAGIC, "드래곤슬레이어의 절단");
                
        b.doll(4200051, 7420207, DOLLTYPE_기르타스, "기르타스", 50, 0)
                .both(DollOption.AC, 10)
                .both(DollOption.ALL_RESIS, 10)
                .both(DollOption.ALL_PIERCE, 10)
                .both(DollOption.MELEE_DMG, 10)
                .both(DollOption.MELEE_HIT, 10)
                .both(DollOption.MELEE_CRIT, 10)
                .both(DollOption.PVP_DMG, 8)
                .both(DollOption.PVP_DMG_RED, 8)
                .both(DollOption.DAMAGE_REDUCTION, 8)
                .both(DollOption.BLESS_RATE, 10)
                .both(DollOption.MPR, 10)
                .both(DollOption.EXP_RATE, 50);
                
        b.doll(950000, 410124, DOLLTYPE_축서큐퀸, "서큐버스 퀸", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.SP, 1)
                .both(DollOption.MP_UP, 15);
                
        b.doll(950001, 410125, DOLLTYPE_축흑장로, "흑장로", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.MP_UP, 15)
                .magic(MagicMode.MAGIC2, "콜 라이트닝");
                
        b.doll(950002, 410126, DOLLTYPE_축자이언트, "자이언트", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.BLESS_RATE, 5)
                .both(DollOption.DAMAGE_REDUCTION, 1)
                .both(DollOption.EXP_RATE, 10);
                
        b.doll(950003, 410127, DOLLTYPE_축드레이크, "드레이크", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.BOW_DMG, 2)
                .both(DollOption.MP_UP, 6);
                
        b.doll(950004, 410128, DOLLTYPE_축킹버그, "킹 버그베어", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.ABILITY_RESIS, 8)
                .both(DollOption.MP_UP, 10);
                
        b.doll(950005, 410129, DOLLTYPE_축다이아골렘, "다이아몬드 골렘", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.DAMAGE_REDUCTION, 2);
                
        b.doll(950006, 410130, DOLLTYPE_축사이클롭스, "사이클롭스", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.MELEE_HIT, 2)
                .both(DollOption.ABILITY_RESIS, 12);
                
        b.doll(950007, 410131, DOLLTYPE_축리치, "리치", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.MAX_HP, 80)
                .both(DollOption.SP, 2);
                
        b.doll(950008, 410132, DOLLTYPE_축나발, "나이트발드", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.MELEE_HIT, 2)
                .both(DollOption.ABILITY_PIERCE, 5);
                
        b.doll(950009, 410133, DOLLTYPE_축시어, "시어", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.BOW_DMG, 5)
                .both(DollOption.HP_UP, 30);
                
        b.doll(950010, 410134, DOLLTYPE_축아이리스, "아이리스", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PC_DEFENSE, 3)
                .both(DollOption.FOU_DMG, 10);
                
        b.doll(950011, 410135, DOLLTYPE_축뱀파, "뱀파이어", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.MELEE_HIT, 2)
                .both(DollOption.FEAR_PIERCE, 3)
                .both(DollOption.ABILITY_RESIS, 5);
                
        b.doll(950012, 410136, DOLLTYPE_축머미, "머미로드", 50, 0)
                .both(DollOption.AC, 2)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.BLESS_RATE, 8)
                .both(DollOption.DAMAGE_REDUCTION, 2)
                .both(DollOption.EXP_RATE, 10)
                .both(DollOption.MPR, 15);
                
        b.doll(950013, 410137, DOLLTYPE_축데몬, "데몬", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.ABILITY_RESIS, 12)
                .both(DollOption.ABILITY_PIERCE, 10);
                
        b.doll(950014, 420138, DOLLTYPE_축데스, "데스나이트", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.BLESS_RATE, 10)
                .both(DollOption.DAMAGE_REDUCTION, 5)
                .both(DollOption.EXP_RATE, 20)
                .magic(MagicMode.MAGIC2, "헬 파이어");
                
        b.doll(950015, 410139, DOLLTYPE_축바란카, "바란카", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.ABILITY_RESIS, 12)
                .both(DollOption.SPIRIT_PIERCE, 10);
                
        b.doll(950016, 410140, DOLLTYPE_축타락, "타락", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.SP, 3)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.DRAGON_PIERCE, 5)
                .both(DollOption.MAGIC_HIT, 5);
                
        b.doll(950017, 410141, DOLLTYPE_축바포, "바포메트", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.FEAR_PIERCE, 5)
                .both(DollOption.ABILITY_RESIS, 10);
                
        b.doll(950018, 410142, DOLLTYPE_축얼녀, "얼음여왕", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.BOW_DMG, 5)
                .both(DollOption.BOW_HIT, 5)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.SPIRIT_PIERCE, 5);
                
        b.doll(950019, 410143, DOLLTYPE_축커츠, "커츠", 50, 0)
                .both(DollOption.AC, 5)
                .both(DollOption.PVP_DMG, 2)
                .both(DollOption.PVP_DMG_RED, 4)
                .both(DollOption.PC_DEFENSE, 3)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.DRAGON_PIERCE, 5)
                .both(DollOption.FOU_DMG, 10);
                
        b.doll(210086, 200068, DOLLTYPE_SEADANCER, "시댄서", 50, 0);
        
        b.doll(500214, 900179, DOLLTYPE_SKELETON, "스파토이", 50, 0)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.BLESS_RATE, 5);
                
        b.doll(500215, 900180, DOLLTYPE_SCARECROW, "허수아비", 50, 0)
                .both(DollOption.BOW_DMG, 2)
                .both(DollOption.BOW_HIT, 2)
                .both(DollOption.MAX_HP, 50)
                .both(DollOption.MAX_MP, 30);
                
        b.doll(500213, 900178, DOLLTYPE_ETHYNE, "에틴", 50, 0);
        
        b.doll(30022, 5074, DOLLTYPE_PIXIE_BLAG, "블레그", 50, 0)
                .both(DollOption.SP, 1)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.BOW_DMG, 2);
                
        b.doll(30023, 5075, DOLLTYPE_PIXIE_LESDAG, "레더그", 50, 0)
                .both(DollOption.SP, 1)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.BOW_DMG, 2);
                
        b.doll(30024, 5076, DOLLTYPE_PIXIE_ELREGEU, "엘레그", 50, 0)
                .both(DollOption.SP, 1)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.BOW_DMG, 2);
                
        b.doll(30025, 5077, DOLLTYPE_PIXIE_GREG, "그레그", 50, 0)
                .both(DollOption.SP, 1)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.BOW_DMG, 2);
                
        b.doll(210106, 200008, DOLLTYPE_HATCHLING, "해츨링", 500, 0);
        b.doll(210107, 200009, DOLLTYPE_HATCHLING, "해츨링", 500, 0);
        b.doll(210108, 200010, DOLLTYPE_HATCHLING, "해츨링", 500, 0);
        b.doll(210109, 200011, DOLLTYPE_HATCHLING, "해츨링", 500, 0);
        
        b.doll(447012, 900220, DOLLTYPE_PSY_CHAMPION, "싸이 챔피언", 50, 0)
                .both(DollOption.MAX_HP, 30)
                .both(DollOption.MELEE_DMG, 2);
                
        b.doll(447013, 900221, DOLLTYPE_PSY_BIRD, "싸이 버드", 50, 0)
                .both(DollOption.BOW_HIT, 2)
                .both(DollOption.MAX_HP, 30);
                
        b.doll(447014, 900222, DOLLTYPE_PSY_GANGNAM_STYLE, "싸이 강남스타일", 50, 0)
                .both(DollOption.SP, 1)
                .both(DollOption.MAX_HP, 30);
                
        b.doll(447015, 900223, DOLLTYPE_GREMLIN, "그렘린", 50, 0)
                .both(DollOption.MAX_HP, 30)
                .both(DollOption.SP, 1)
                .both(DollOption.MELEE_DMG, 2)
                .both(DollOption.BOW_DMG, 2);
                
        b.doll(500212, 900176, DOLLTYPE_COBO, "다이노스", 50, 0)
                .both(DollOption.SP, 1);
                
        b.doll(758, 503, ruler1, "지배자의 현신 1등급", 50, 0)
                .both(DollOption.AC, 3)
                .both(DollOption.SP, 3)
                .both(DollOption.BLESS_RATE, 30)
                .both(DollOption.EXP_RATE, 30)
                .both(DollOption.ABILITY_PIERCE, 10)
                .both(DollOption.FEAR_PIERCE, 10)
                .both(DollOption.ABILITY_RESIS, 10)
                .both(DollOption.FEAR_RESIS, 10)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(759, 504, ruler2, "지배자의 현신 2등급", 50, 0)
                .both(DollOption.BLESS_RATE, 4)
                .both(DollOption.DAMAGE_REDUCTION, 3)
                .both(DollOption.EXP_RATE, 10)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(760, 505, ruler3, "지배자의 현신 3등급", 50, 0)
                .both(DollOption.BLESS_RATE, 3)
                .both(DollOption.DAMAGE_REDUCTION, 2)
                .both(DollOption.EXP_RATE, 5)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(761, 506, ruler4, "지배자의 현신 4등급", 50, 0)
                .both(DollOption.BLESS_RATE, 2)
                .both(DollOption.EXP_RATE, 2)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(772, 537, ruler5, "지배자의 현신 1등급(1일)", 50, 0)
                .both(DollOption.DAMAGE_REDUCTION, 4)
                .both(DollOption.EXP_RATE, 15)
                .both(DollOption.BLESS_RATE, 5)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(773, 538, ruler6, "지배자의 현신 2등급(1일)", 50, 0)
                .both(DollOption.DAMAGE_REDUCTION, 3)
                .both(DollOption.EXP_RATE, 10)
                .both(DollOption.BLESS_RATE, 4)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(774, 539, ruler7, "지배자의 현신 3등급(1일)", 50, 0)
                .both(DollOption.DAMAGE_REDUCTION, 2)
                .both(DollOption.EXP_RATE, 5)
                .both(DollOption.BLESS_RATE, 3)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(775, 540, ruler8, "지배자의 현신 4등급(1일)", 50, 0)
                .both(DollOption.EXP_RATE, 2)
                .both(DollOption.BLESS_RATE, 2)
                .magic(MagicMode.MAGIC2, "파이어 밤");
                
        b.doll(42654, 203073, 수련자마법인형, "수련자 마법인형", 50, 82)
                .both(DollOption.AC, 1)
                .both(DollOption.PC_DEFENSE, 1);
                
        R r = b.freeze(); 
        BY_ITEM_ID = r.bi; 
        BY_TYPE = r.bt; 
        
     //   _log.info("[DollRegistry] loaded items=" + BY_ITEM_ID.size()); 
    }
    
    private DollRegistry() {}
    
    public static Def byItemId(int itemId) {
        return BY_ITEM_ID.get(itemId);
    }
    
    public static Profile byType(int type) {
        return type > 0 && type < BY_TYPE.length ? BY_TYPE[type] : null;
    }
    
    public static boolean applyDirect(L1PcInstance pc, int type, int sign) {
        Profile p = byType(type);
        if (p == null) return false;
        
        p.applyDirect(pc, sign);
        return true;
    }
    
    public static int runtimeValue(int type, DollOption option) {
        Profile p = byType(type);
        return p == null ? 0 : p.getRuntimeValue(option);
    }
    
    public static int size() {
        return BY_ITEM_ID.size();
    }
}