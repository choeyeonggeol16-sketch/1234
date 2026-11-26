package l1j.server.server.model.item.cooking;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemScript;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.eKind;

/**
 * 요리 단일 Registry.
 *
 * 하나의 Profile 안에서
 *  - 아이템/스킬 바인딩
 *  - 지속시간
 *  - 요리 아이콘 타입
 *  - COOKING/DESSERT 그룹
 *  - 포만감 조건
 *  - 실제 스탯 적용/해제
 * 를 모두 정의한다.
 *
 * Registry 자체는 static 초기화 후 불변이다.
 * 런타임 PC 적용상태는 WeakHashMap에 그룹별 skillId 2개만 저장하며,
 * 모든 접근을 단일 lock으로 직렬화해 누수/경쟁조건을 막는다.
 */
public final class CookingRegistry {

    private CookingRegistry() {}

    public enum Group {
        COOKING,
        DESSERT,
        NONE
    }

    // ---------------------------------------------------------------------
    // 패킷 갱신 마스크
    // ---------------------------------------------------------------------

    private static final int UPD_HP      = 1 << 0;
    private static final int UPD_MP      = 1 << 1;
    private static final int UPD_SPMR    = 1 << 2;
    private static final int UPD_ATTR    = 1 << 3;
    private static final int UPD_STATUS  = 1 << 4;
    private static final int UPD_STATUS2 = 1 << 5;
    private static final int UPD_SPECIAL = 1 << 6;

    private enum Option {
        HIT(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addHitup(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeShortHIT(v); }
        },
        DMG(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addDmgup(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeShortDMG(v); }
        },
        BOW_HIT_RATE(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addBowHitRate(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeLongHIT(v); }
        },
        BOW_HIT(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addBowHitup(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeLongHIT(v); }
        },
        BOW_DMG(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addBowDmgup(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeLongDMG(v); }
        },
        HPR(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addHpr(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeAddHPPrecovery(v); }
        },
        MPR(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addMpr(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeAddMPPrecovery(v); }
        },
        MAX_HP(UPD_HP | UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addMaxHp(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeAddMaxHP(v); }
        },
        MAX_MP(UPD_MP | UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addMaxMp(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeMaxMP(v); }
        },
        AC(UPD_STATUS | UPD_STATUS2) {
            @Override void apply(L1PcInstance pc, int v) { pc.addAc(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) {
                if (v != 0) os.writeAddAc(v < 0 ? -v : v);
            }
        },
        MR(UPD_SPMR) {
            @Override void apply(L1PcInstance pc, int v) { pc.AddMDefend(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeAddMR(v); }
        },
        ATTR_RESIS(UPD_ATTR) {
            @Override void apply(L1PcInstance pc, int v) { pc.AddAllNaturalResistance(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) {
                os.writeRegistFire(v);
                os.writeRegistWater(v);
                os.writeRegistWind(v);
                os.writeRegistEarth(v);
            }
        },
        SP(UPD_SPMR) {
            @Override void apply(L1PcInstance pc, int v) { pc.AddStatSp(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeAddSP(v); }
        },
        DMG_RED(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.addDamageReductionByArmor(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeDMGdown(v); }
        },
        PVP_DMG_RED(UPD_STATUS) {
            @Override void apply(L1PcInstance pc, int v) { pc.AddPvpDamageReduction(v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writePVPAddDMGdown(v); }
        },
        ALL_PIERCE(UPD_SPECIAL) {
            @Override void apply(L1PcInstance pc, int v) { pc.AddSpecialPierce(eKind.ALL, v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeaAll_pierce(v); }
        },
        ALL_RESIS(UPD_SPECIAL) {
            @Override void apply(L1PcInstance pc, int v) { pc.AddSpecialResistance(eKind.ALL, v); }
            @Override void writePresent(ItemPresentOutStream os, int v) { os.writeaAll_resis(v); }
        };

        final int updateMask;

        Option(int updateMask) {
            this.updateMask = updateMask;
        }

        abstract void apply(L1PcInstance pc, int v);
        abstract void writePresent(ItemPresentOutStream os, int v);
    }

    private static final class Effect {
        final Option option;
        final int value;

        Effect(Option option, int value) {
            this.option = option;
            this.value = value;
        }
    }

    private static final class Binding {
        final int itemId;
        final int skillId;

        Binding(int itemId, int skillId) {
            this.itemId = itemId;
            this.skillId = skillId;
        }
    }

    private static final class ProfileDraft {
        final String name;
        final int durationSec;
        final int iconType;
        final Group group;
        final boolean requireFood225;
        final List<Effect> effects = new ArrayList<>(8);
        final List<Binding> bindings = new ArrayList<>(2);

        ProfileDraft(String name, int durationSec, int iconType, Group group, boolean requireFood225) {
            this.name = name;
            this.durationSec = durationSec;
            this.iconType = iconType;
            this.group = group;
            this.requireFood225 = requireFood225;
        }

        ProfileDraft add(Option option, int value) {
            if (option == null) throw new NullPointerException("option");
            if (value == 0) return this;

            for (Effect e : effects) {
                if (e.option == option) {
                    throw new IllegalStateException(
                            "Duplicate cooking option: profile=" + name + ", option=" + option);
                }
            }

            effects.add(new Effect(option, value));
            return this;
        }

        ProfileDraft bind(int itemId, int skillId) {
            if (itemId <= 0) throw new IllegalArgumentException("itemId <= 0: " + itemId);
            if (skillId <= 0) throw new IllegalArgumentException("skillId <= 0: " + skillId);
            bindings.add(new Binding(itemId, skillId));
            return this;
        }

        ProfileDraft bindSkillOnly(int skillId) {
            if (skillId <= 0) throw new IllegalArgumentException("skillId <= 0: " + skillId);
            bindings.add(new Binding(0, skillId));
            return this;
        }
    }

    public static final class Profile {
        private final String name;
        private final int durationSec;
        private final int iconType;
        private final Group group;
        private final boolean requireFood225;
        private final Effect[] effects;
        private final int updateMask;

        private Profile(ProfileDraft d) {
            name = d.name;
            durationSec = d.durationSec;
            iconType = d.iconType;
            group = d.group;
            requireFood225 = d.requireFood225;
            effects = d.effects.toArray(new Effect[d.effects.size()]);

            int mask = 0;
            for (Effect e : effects) mask |= e.option.updateMask;
            updateMask = mask;
        }

        public String getName() { return name; }
        public int getDurationSec() { return durationSec; }
        public int getIconType() { return iconType; }
        public Group getGroup() { return group; }
        public boolean isRequireFood225() { return requireFood225; }
    }

    public static final class Def {
        private final int itemId;
        private final int skillId;
        private final Profile profile;

        private Def(int itemId, int skillId, Profile profile) {
            this.itemId = itemId;
            this.skillId = skillId;
            this.profile = profile;
        }

        public int getItemId() { return itemId; }
        public int getSkillId() { return skillId; }
        public int getDurationSec() { return profile.durationSec; }
        public int getIconType() { return profile.iconType; }
        public Group getGroup() { return profile.group; }
        public boolean isRequireFood225() { return profile.requireFood225; }
        public String getName() { return profile.name; }
        public Profile getProfile() { return profile; }
    }

    private static final class BuildResult {
        final Map<Integer, Def> byItem;
        final Map<Integer, Def> bySkill;

        BuildResult(Map<Integer, Def> byItem, Map<Integer, Def> bySkill) {
            this.byItem = byItem;
            this.bySkill = bySkill;
        }
    }

    private static final class Builder {
        private final List<ProfileDraft> drafts = new ArrayList<>(64);

        ProfileDraft profile(String name, int durationSec, int iconType,
                             Group group, boolean requireFood225) {
            if (name == null || name.length() == 0) throw new IllegalArgumentException("empty name");
            if (durationSec <= 0 || durationSec > Integer.MAX_VALUE / 1000) {
                throw new IllegalArgumentException("Invalid cooking duration " + durationSec);
            }
            if (iconType < 0) throw new IllegalArgumentException("iconType < 0: " + iconType);
            if (group == null) throw new NullPointerException("group");

            ProfileDraft d = new ProfileDraft(name, durationSec, iconType, group, requireFood225);
            drafts.add(d);
            return d;
        }

        BuildResult freeze() {
            HashMap<Integer, Def> byItem = new HashMap<>(128);
            HashMap<Integer, Def> bySkill = new HashMap<>(128);

            for (ProfileDraft draft : drafts) {
                if (draft.bindings.isEmpty()) {
                    throw new IllegalStateException("Cooking profile without binding: " + draft.name);
                }

                Profile profile = new Profile(draft);

                for (Binding b : draft.bindings) {
                    Def def = new Def(b.itemId, b.skillId, profile);

                    if (b.itemId > 0) {
                        Def oldItem = byItem.put(b.itemId, def);
                        if (oldItem != null) {
                            throw new IllegalStateException(
                                    "Duplicate cooking itemId=" + b.itemId +
                                    " old=" + oldItem.getName() +
                                    " new=" + profile.name);
                        }
                    }

                    Def oldSkill = bySkill.put(b.skillId, def);
                    if (oldSkill != null) {
                        throw new IllegalStateException(
                                "Duplicate cooking skillId=" + b.skillId +
                                " old=" + oldSkill.getName() +
                                " new=" + profile.name);
                    }
                }
            }

            return new BuildResult(
                    Collections.unmodifiableMap(byItem),
                    Collections.unmodifiableMap(bySkill));
        }
    }

    private static final Map<Integer, Def> BY_ITEM_ID;
    private static final Map<Integer, Def> BY_SKILL_ID;

    static {
        final BuildResult catalog = ItemScript.load("cooking-options.txt", "rkern.cooking.options",
                (source, reader) -> {
                    final Builder builder = new Builder();
                    final ItemScript.RowReader parser = new ItemScript.RowReader() {
                        private ProfileDraft profile;

                        @Override
                        public void read(String[] row) {
                            if ("profile".equals(row[0])) {
                                ItemScript.requireFields(row, 6);
                                if (!"true".equals(row[5]) && !"false".equals(row[5])) {
                                    throw new IllegalArgumentException("Invalid full-food flag " + row[5]);
                                }
                                profile = builder.profile(row[1], Integer.parseInt(row[2]),
                                        Integer.parseInt(row[3]), Group.valueOf(row[4]), Boolean.parseBoolean(row[5]));
                                return;
                            }
                            if (profile == null) throw new IllegalArgumentException("Entry without a cooking profile");
                            switch (row[0]) {
                                case "effect" -> {
                                    ItemScript.requireFields(row, 3);
                                    profile.add(Option.valueOf(row[1]), Integer.parseInt(row[2]));
                                }
                                case "item" -> {
                                    ItemScript.requireFields(row, 3);
                                    profile.bind(Integer.parseInt(row[1]), Integer.parseInt(row[2]));
                                }
                                case "skill" -> {
                                    ItemScript.requireFields(row, 2);
                                    profile.bindSkillOnly(Integer.parseInt(row[1]));
                                }
                                default -> throw new IllegalArgumentException("Unknown cooking entry " + row[0]);
                            }
                        }
                    };
                    ItemScript.readRows(source, reader, parser);
                    if (builder.drafts.isEmpty()) throw new IllegalStateException("Empty cooking script " + source);
                    return builder.freeze();
                });
        BY_ITEM_ID = catalog.byItem;
        BY_SKILL_ID = catalog.bySkill;
    }

    public static void initialize() {
        // Compile the cooking definitions and bind the skill IDs at startup.
    }

    // ---------------------------------------------------------------------
    // 아이템 옵션 표기 (ItemPresentOutStream)
    // ---------------------------------------------------------------------

    /**
     * Registry에 정의된 실제 요리 효과를 아이템 툴팁에 그대로 출력한다.
     * 실제 효과와 표기 효과가 한 정의를 공유하므로 값 불일치를 막는다.
     *
     * @return Registry에 등록된 요리 아이템이면 true
     */
    public static boolean writeItemOptions(ItemPresentOutStream os, int itemId) {
        if (os == null || itemId <= 0) return false;

        Def def = BY_ITEM_ID.get(itemId);
        if (def == null) return false;

        Effect[] effects = def.profile.effects;
        for (int i = 0; i < effects.length; i++) {
            Effect e = effects[i];
            if (e.value != 0) {
                e.option.writePresent(os, e.value);
            }
        }
        return true;
    }

    public static void writeItemDuration(ItemPresentOutStream out, int itemId) {
        final Def definition = byItemId(itemId);
        if (definition == null) return;
        final int seconds = definition.getDurationSec();
        final String duration = seconds % 60 == 0 ? seconds / 60 + "분" : seconds + "초";
        out.writeText("\\fI사용 시간: \\aA" + duration + ".");
    }

    /** Registry에 등록된 요리 아이템인지 확인. */
    public static boolean containsItem(int itemId) {
        return itemId > 0 && BY_ITEM_ID.containsKey(itemId);
    }

    // ---------------------------------------------------------------------
    // 런타임 적용상태
    // ---------------------------------------------------------------------

    private static final Object ACTIVE_LOCK = new Object();
    private static final WeakHashMap<L1PcInstance, ActiveState> ACTIVE = new WeakHashMap<>();

    private static final class ActiveState {
        int cookingSkillId;
        int dessertSkillId;
        java.util.HashSet<Integer> extraSkills;

        boolean isEmpty() {
            return cookingSkillId == 0
                    && dessertSkillId == 0
                    && (extraSkills == null || extraSkills.isEmpty());
        }
    }

    private static int activeSkill(ActiveState s, Group group) {
        if (group == Group.DESSERT) return s.dessertSkillId;
        if (group == Group.COOKING) return s.cookingSkillId;
        return 0;
    }

    private static boolean isExtraActive(ActiveState s, int skillId) {
        return s.extraSkills != null && s.extraSkills.contains(skillId);
    }

    private static void markExtraActive(ActiveState s, int skillId) {
        if (s.extraSkills == null) s.extraSkills = new java.util.HashSet<>(4);
        s.extraSkills.add(skillId);
    }

    private static void unmarkExtraActive(ActiveState s, int skillId) {
        if (s.extraSkills == null) return;
        s.extraSkills.remove(skillId);
        if (s.extraSkills.isEmpty()) s.extraSkills = null;
    }

    private static void setActiveSkill(ActiveState s, Group group, int skillId) {
        if (group == Group.DESSERT) s.dessertSkillId = skillId;
        else if (group == Group.COOKING) s.cookingSkillId = skillId;
    }

    public static Def byItemId(int itemId) {
        return BY_ITEM_ID.get(itemId);
    }

    public static Def bySkillId(int skillId) {
        return BY_SKILL_ID.get(skillId);
    }

    public static boolean containsSkill(int skillId) {
        return BY_SKILL_ID.containsKey(skillId);
    }

    public static int size() {
        return BY_ITEM_ID.size();
    }

    /**
     * 동일 스킬은 재적용하지 않고 true를 반환한다.
     * 같은 그룹에 다른 Registry 스킬이 이미 적용된 상태라면 false.
     */
    public static boolean ensureStarted(L1PcInstance pc, int skillId) {
        if (pc == null) return false;

        Def def = BY_SKILL_ID.get(skillId);
        if (def == null) return false;

        final Group group = def.profile.group;

        synchronized (ACTIVE_LOCK) {
            ActiveState s = ACTIVE.get(pc);
            if (s == null) {
                s = new ActiveState();
                ACTIVE.put(pc, s);
            }

            if (group == Group.NONE) {
                if (isExtraActive(s, skillId)) return true;
                markExtraActive(s, skillId);
            } else {
                int active = activeSkill(s, group);
                if (active == skillId) {
                    syncGroupId(pc, group, skillId);
                    return true;
                }
                if (active != 0) {
                    return false;
                }

                setActiveSkill(s, group, skillId);
            }
        }

        try {
            applyTransactional(pc, def.profile, +1);
        } catch (RuntimeException e) {
            clearActiveMarker(pc, group, skillId);
            throw e;
        } catch (Error e) {
            clearActiveMarker(pc, group, skillId);
            throw e;
        }

        syncGroupId(pc, group, skillId);
        sendUpdates(pc, def.profile.updateMask);
        return true;
    }

    /**
     * 같은 스킬에 대한 중복 종료 호출은 무시한다.
     */
    public static boolean end(L1PcInstance pc, int skillId) {
        if (pc == null) return false;

        Def def = BY_SKILL_ID.get(skillId);
        if (def == null) return false;

        final Group group = def.profile.group;
        boolean marked = false;

        synchronized (ACTIVE_LOCK) {
            ActiveState s = ACTIVE.get(pc);
            if (s != null) {
                if (group == Group.NONE && isExtraActive(s, skillId)) {
                    unmarkExtraActive(s, skillId);
                    marked = true;
                } else if (group != Group.NONE && activeSkill(s, group) == skillId) {
                    setActiveSkill(s, group, 0);
                    marked = true;
                }
                if (marked && s.isEmpty()) ACTIVE.remove(pc);
            }
        }

        if (!marked) {
            return false;
        }

        try {
            applyTransactional(pc, def.profile, -1);
        } catch (RuntimeException e) {
            restoreActiveMarker(pc, group, skillId);
            throw e;
        } catch (Error e) {
            restoreActiveMarker(pc, group, skillId);
            throw e;
        }

        clearGroupIdIfMatches(pc, group, skillId);
        sendUpdates(pc, def.profile.updateMask);
        return true;
    }

    public static int currentSkill(L1PcInstance pc, Group group) {
        if (pc == null || group == null || group == Group.NONE) return 0;
        return group == Group.DESSERT ? pc.getDessertId() : pc.getCookingId();
    }

    public static boolean restoreLogin(L1PcInstance pc, int skillId, int remainSec) {
        if (pc == null || remainSec <= 0) return false;

        Def def = BY_SKILL_ID.get(skillId);
        if (def == null) return false;

        if (!ensureStarted(pc, skillId)) return false;

        SC_PacketBox.send(pc, 53, def.profile.iconType, remainSec);
        return true;
    }

    /** 명시적 로그아웃/캐릭터 폐기 시 호출 가능. WeakHashMap이라 필수는 아님. */
    public static void clearRuntimeState(L1PcInstance pc) {
        if (pc == null) return;
        synchronized (ACTIVE_LOCK) {
            ACTIVE.remove(pc);
        }
    }

    private static void syncGroupId(L1PcInstance pc, Group group, int skillId) {
        if (group == Group.DESSERT) pc.setDessertId(skillId);
        else if (group == Group.COOKING) pc.setCookingId(skillId);
    }

    private static void clearGroupIdIfMatches(L1PcInstance pc, Group group, int skillId) {
        if (group == Group.DESSERT) {
            if (pc.getDessertId() == skillId) pc.setDessertId(0);
        } else if (group == Group.COOKING) {
            if (pc.getCookingId() == skillId) pc.setCookingId(0);
        }
    }

    private static void clearActiveMarker(L1PcInstance pc, Group group, int skillId) {
        synchronized (ACTIVE_LOCK) {
            ActiveState s = ACTIVE.get(pc);
            if (s == null) return;
            if (group == Group.NONE) {
                if (isExtraActive(s, skillId)) unmarkExtraActive(s, skillId);
            } else if (activeSkill(s, group) == skillId) {
                setActiveSkill(s, group, 0);
            }
            if (s.isEmpty()) ACTIVE.remove(pc);
        }
    }

    private static void restoreActiveMarker(L1PcInstance pc, Group group, int skillId) {
        synchronized (ACTIVE_LOCK) {
            ActiveState s = ACTIVE.get(pc);
            if (s == null) {
                s = new ActiveState();
                ACTIVE.put(pc, s);
            }
            if (group == Group.NONE) {
                markExtraActive(s, skillId);
            } else if (activeSkill(s, group) == 0) {
                setActiveSkill(s, group, skillId);
            }
        }
    }

    private static void applyTransactional(L1PcInstance pc, Profile profile, int sign) {
        int applied = 0;

        try {
            for (; applied < profile.effects.length; applied++) {
                Effect e = profile.effects[applied];
                e.option.apply(pc, e.value * sign);
            }
        } catch (RuntimeException ex) {
            rollback(pc, profile.effects, applied - 1, sign);
            throw ex;
        } catch (Error ex) {
            rollback(pc, profile.effects, applied - 1, sign);
            throw ex;
        }
    }

    private static void rollback(L1PcInstance pc, Effect[] effects, int lastApplied, int sign) {
        for (int i = lastApplied; i >= 0; i--) {
            Effect e = effects[i];
            try {
                e.option.apply(pc, e.value * -sign);
            } catch (Throwable ignored) {
                // 최초 예외를 보존한다. 롤백 실패는 서버 로그 상위 레이어에서 확인.
            }
        }
    }

    private static final java.util.logging.Logger COOKING_LOG =
            java.util.logging.Logger.getLogger(CookingRegistry.class.getName());

    // Registry stat mutations have already succeeded by this point. A broken
    // client packet or party refresh cannot undo those in-memory effects.
    // Each notification is independent so later status updates are attempted.
    private static void sendUpdates(L1PcInstance pc, int mask) {
        if ((mask & UPD_HP) != 0) {
            try {
                SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
            } catch (RuntimeException failure) {
                logUpdateFailure("hp", failure);
            }
            if (pc.isInParty()) {
                try {
                    pc.getParty().refreshPartyMemberStatus(pc);
                } catch (RuntimeException failure) {
                    logUpdateFailure("party", failure);
                }
            }
        }
        if ((mask & UPD_MP) != 0) {
            try {
                SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
            } catch (RuntimeException failure) {
                logUpdateFailure("mp", failure);
            }
        }
        if ((mask & UPD_SPMR) != 0) {
            try {
                SC_SPMR.send(pc);
            } catch (RuntimeException failure) {
                logUpdateFailure("spmr", failure);
            }
        }
        if ((mask & UPD_ATTR) != 0) {
            try {
                SC_OwnCharAttrDef.send(pc);
            } catch (RuntimeException failure) {
                logUpdateFailure("attr", failure);
            }
        }
        if ((mask & UPD_STATUS2) != 0) {
            try {
                SC_OwnCharStatus2.send(pc);
            } catch (RuntimeException failure) {
                logUpdateFailure("status2", failure);
            }
        }
        if ((mask & UPD_STATUS) != 0) {
            try {
                SC_OwnCharStatus.send(pc);
            } catch (RuntimeException failure) {
                logUpdateFailure("status", failure);
            }
        }
    }

    private static void logUpdateFailure(String kind, RuntimeException failure) {
        COOKING_LOG.log(java.util.logging.Level.WARNING,
                "Committed cooking stat update packet failed: " + kind, failure);
    }
}
