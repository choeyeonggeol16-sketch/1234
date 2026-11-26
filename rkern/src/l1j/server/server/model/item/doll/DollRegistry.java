package l1j.server.server.model.item.doll;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemScript;

public final class DollRegistry {
    private static final Logger _log = Logger.getLogger(DollRegistry.class.getName());
    
    public enum MagicMode { 
        MAGIC, 
        MAGIC2 
    }
    
    public enum CombatProc {
        NONE, DEATH, BLACK, WOLF, DESEL, HAL, AU, BEHE,
        RULER1, RULER1_DAILY, RULER2, RULER2_DAILY, RULER3, RULER3_DAILY, RULER4, RULER4_DAILY
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
        private final boolean dynamicDirect;
        private final MagicMode magicMode; 
        private final String magicText;
        private final CombatProc proc;
        private final int hpTick;
        private final int mpTick;
        
        private Profile(int t, String n, Value[] r, DisplayEntry[] d, DisplayEntry[] a, MagicMode mm, String mt,
                CombatProc proc, int hpTick, int mpTick) {
            dollType = t;
            name = n;
            runtime = r;
            display = d;
            direct = a;
            boolean dynamic = false;
            for (DisplayEntry entry : direct) dynamic |= entry.value instanceof DynamicValue;
            dynamicDirect = dynamic;
            magicMode = mm;
            magicText = mt;
            this.proc = proc;
            this.hpTick = Math.max(0, hpTick);
            this.mpTick = Math.max(0, mpTick);
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
        
        public int hpTick() { return hpTick; }
        public int mpTick() { return mpTick; }

        private int[] applyDirect(L1PcInstance pc, int sign, int[] snapshot) {
            if (sign > 0 && dynamicDirect) snapshot = new int[direct.length];
            for (int i = 0; i < direct.length; i++) {
                final DisplayEntry entry = direct[i];
                final int value = sign < 0 && snapshot != null ? snapshot[i] : entry.getValue();
                if (sign > 0 && snapshot != null) snapshot[i] = value;
                if (value != 0) entry.getOption().apply(pc, value * sign);
            }
            return sign > 0 ? snapshot : null;
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
        CombatProc proc = CombatProc.NONE;
        int hpTick = -1;
        int mpTick = -1;
        
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
            if (item <= 0 || npc <= 0 || type <= 0 || type >= 128 || name.isBlank() || cost < 0 || max < 0) {
                throw new IllegalArgumentException("Invalid doll definition for item " + item);
            }
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
                bt[p.type] = new Profile(p.type, p.name, rv, de, ae, p.mm, p.mt, p.proc, p.hpTick, p.mpTick);
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
        final R catalog = ItemScript.load("doll-options.txt", "rkern.doll.options", (source, reader) -> {
            final Builder builder = new Builder();
            final ItemScript.RowReader parser = new ItemScript.RowReader() {
                private PB profile;

                @Override
                public void read(String[] row) {
                    if ("doll".equals(row[0])) {
                        ItemScript.requireFields(row, 7);
                        profile = builder.doll(Integer.parseInt(row[1]), Integer.parseInt(row[2]),
                                Integer.parseInt(row[3]), row[4], Integer.parseInt(row[5]), Integer.parseInt(row[6]));
                        return;
                    }
                    if (profile == null) throw new IllegalArgumentException("Entry without a doll profile");
                    if ("proc".equals(row[0])) {
                        ItemScript.requireFields(row, 2);
                        if (profile.p.proc != CombatProc.NONE) throw new IllegalArgumentException("Duplicate doll proc");
                        profile.p.proc = CombatProc.valueOf(row[1]);
                        return;
                    }
                    if (row[0].equals("hp-tick") || row[0].equals("mp-tick")) {
                        ItemScript.requireFields(row, 2);
                        final int value = Integer.parseInt(row[1]);
                        final boolean hp = row[0].equals("hp-tick");
                        if (value < 0 || (hp ? profile.p.hpTick : profile.p.mpTick) >= 0) {
                            throw new IllegalArgumentException("Invalid or duplicate " + row[0]);
                        }
                        if (hp) profile.p.hpTick = value;
                        else profile.p.mpTick = value;
                        return;
                    }
                    ItemScript.requireFields(row, 3);
                    switch (row[0]) {
                        case "both" -> profile.both(DollOption.valueOf(row[1]), Integer.parseInt(row[2]));
                        case "runtime" -> profile.runtimeOnly(DollOption.valueOf(row[1]), Integer.parseInt(row[2]));
                        case "setting" -> profile.dynamicBoth(DollOption.valueOf(row[1]), configuration(row[2]));
                        case "magic" -> profile.magic(MagicMode.valueOf(row[1]), row[2]);
                        default -> throw new IllegalArgumentException("Unknown doll entry " + row[0]);
                    }
                }
            };
            ItemScript.readRows(source, reader, parser);
            if (builder.is.isEmpty()) throw new IllegalStateException("Empty doll script " + source);
            return builder.freeze();
        });
        BY_ITEM_ID = catalog.bi;
        BY_TYPE = catalog.bt;
    }

    public static void initialize() {
        // Compile the doll definitions at startup.
    }

    private static IntSupplier configuration(String key) {
        return switch (key) {
            case "드슬pvp뎀" -> () -> Config.드슬pvp뎀;
            case "드슬pvp리덕" -> () -> Config.드슬pvp리덕;
            case "드슬근뎀" -> () -> Config.드슬근뎀;
            case "드슬근명" -> () -> Config.드슬근명;
            case "드슬마법명중" -> () -> Config.드슬마법명중;
            case "드슬맥스엠피" -> () -> Config.드슬맥스엠피;
            case "드슬맥스피" -> () -> Config.드슬맥스피;
            case "드슬스펠" -> () -> Config.드슬스펠;
            case "드슬올레즈" -> () -> Config.드슬올레즈;
            case "드슬올적중" -> () -> Config.드슬올적중;
            case "드슬원뎀" -> () -> Config.드슬원뎀;
            case "드슬원명" -> () -> Config.드슬원명;
            case "드슬추가경험치" -> () -> Config.드슬추가경험치;
            case "드슬추방" -> () -> Config.드슬추방;
            case "드슬축복효율" -> () -> Config.드슬축복효율;
            case "베히pvp뎀" -> () -> Config.베히pvp뎀;
            case "베히pvp리덕" -> () -> Config.베히pvp리덕;
            case "베히근뎀" -> () -> Config.베히근뎀;
            case "베히근명" -> () -> Config.베히근명;
            case "베히마법명중" -> () -> Config.베히마법명중;
            case "베히맥스엠피" -> () -> Config.베히맥스엠피;
            case "베히맥스피" -> () -> Config.베히맥스피;
            case "베히스펠" -> () -> Config.베히스펠;
            case "베히올레즈" -> () -> Config.베히올레즈;
            case "베히올적중" -> () -> Config.베히올적중;
            case "베히원뎀" -> () -> Config.베히원뎀;
            case "베히원명" -> () -> Config.베히원명;
            case "베히추가경험치" -> () -> Config.베히추가경험치;
            case "베히추방" -> () -> Config.베히추방;
            case "베히축복효율" -> () -> Config.베히축복효율;
            case "아우라pvp뎀" -> () -> Config.아우라pvp뎀;
            case "아우라pvp리덕" -> () -> Config.아우라pvp리덕;
            case "아우라근뎀" -> () -> Config.아우라근뎀;
            case "아우라근명" -> () -> Config.아우라근명;
            case "아우라마법명중" -> () -> Config.아우라마법명중;
            case "아우라맥스엠피" -> () -> Config.아우라맥스엠피;
            case "아우라맥스피" -> () -> Config.아우라맥스피;
            case "아우라스펠" -> () -> Config.아우라스펠;
            case "아우라올레즈" -> () -> Config.아우라올레즈;
            case "아우라올적중" -> () -> Config.아우라올적중;
            case "아우라원뎀" -> () -> Config.아우라원뎀;
            case "아우라원명" -> () -> Config.아우라원명;
            case "아우라추가경험치" -> () -> Config.아우라추가경험치;
            case "아우라추방" -> () -> Config.아우라추방;
            case "아우라축복효율" -> () -> Config.아우라축복효율;
            case "할파스pvp뎀" -> () -> Config.할파스pvp뎀;
            case "할파스pvp리덕" -> () -> Config.할파스pvp리덕;
            case "할파스근뎀" -> () -> Config.할파스근뎀;
            case "할파스근명" -> () -> Config.할파스근명;
            case "할파스마법명중" -> () -> Config.할파스마법명중;
            case "할파스맥스엠피" -> () -> Config.할파스맥스엠피;
            case "할파스맥스피" -> () -> Config.할파스맥스피;
            case "할파스스펠" -> () -> Config.할파스스펠;
            case "할파스올레즈" -> () -> Config.할파스올레즈;
            case "할파스올적중" -> () -> Config.할파스올적중;
            case "할파스원뎀" -> () -> Config.할파스원뎀;
            case "할파스원명" -> () -> Config.할파스원명;
            case "할파스추가경험치" -> () -> Config.할파스추가경험치;
            case "할파스추방" -> () -> Config.할파스추방;
            case "할파스축복효율" -> () -> Config.할파스축복효율;
            default -> throw new IllegalArgumentException("Unknown doll config setting " + key);
        };
    }

    private DollRegistry() {}
    
    public static Def byItemId(int itemId) {
        return BY_ITEM_ID.get(itemId);
    }
    
    public static Profile byType(int type) {
        return type > 0 && type < BY_TYPE.length ? BY_TYPE[type] : null;
    }
    
    /** Returns the applied values only for profiles that contain a mutable setting. */
    public static int[] applyDirect(L1PcInstance pc, int type, int sign, int[] snapshot) {
        final Profile profile = byType(type);
        return profile == null || pc == null || sign == 0 ? null : profile.applyDirect(pc, sign, snapshot);
    }

    public static int runtimeValue(int type, DollOption option) {
        Profile p = byType(type);
        return p == null ? 0 : p.getRuntimeValue(option);
    }
    
    public static CombatProc combatProc(int type) {
        final Profile profile = byType(type);
        return profile == null ? CombatProc.NONE : profile.proc;
    }

    public static int size() {
        return BY_ITEM_ID.size();
    }
}