package l1j.server.server.model.character.skill;

import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.network.thread.CIOThreadCallback;

import static l1j.server.server.model.character.skill.CSkillEffect.Bonus.*;
import static l1j.server.server.model.character.skill.L1SkillId.*;

/** One immutable runtime definition per ID. L1Skills still owns the game metadata. */
public final class CSkillInfo {
    private static final int PAGED_LIMIT = 32768;
    private static final int PAGE_SHIFT = 5;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final CSkillInfo[][] pages = new CSkillInfo[PAGED_LIMIT >>> PAGE_SHIFT][];
    private static final Int2ObjectOpenHashMap<CSkillInfo> sparse = new Int2ObjectOpenHashMap<>(48);

    private static boolean frozen;

    static {
        SkillHandlers.registerEffects();
        SkillStopHandlers.registerEffects();
        registerManaged(ADVANCE_SPIRIT, SkillHandlers::startAdvanceSpirit, MAX_HP, MAX_MP);
        registerManaged(PRIDE, SkillHandlers::startScaledHp, MAX_HP);
        registerManaged(GIGANTIC, SkillHandlers::startScaledHp, MAX_HP);
        registerManaged(SHINING_AURA, SkillHandlers::startShiningShield, AC);
        registerManaged(SHIELD, SkillHandlers::startShiningShield, AC);
        registerManaged(SHADOW_ARMOR, SkillHandlers::startShadowArmor, MR);
        registerManaged(IllUSION_DIAMONDGOLEM, SkillHandlers::startIllusionDiamondGolem, AC);
        registerManaged(SCALES_EARTH_DRAGON, SkillHandlers::startScalesEarthDragon, AC, MR);
        registerManaged(N_BUFF_STR, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_DEX, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_INT, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_WIS, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_WATER, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_WIND, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_EARTH, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_FIRE, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_ALL_RESIST, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(DRESS_MIGHTY, SkillHandlers::startDressStatBuff, PRIMARY_STATS);
        registerManaged(DRESS_DEXTERITY, SkillHandlers::startDressStatBuff, PRIMARY_STATS);
        registerManaged(DRESS_EVASION, SkillHandlers::startEvasionBuff, ER);
        registerManaged(AQUA_PROTECTER, SkillHandlers::startEvasionBuff, ER);
        registerManaged(SOLID_CARRIAGE, SkillHandlers::startEvasionBuff, ER);
        registerManaged(SCALES_WATER_DRAGON, SkillHandlers::startScalesEvasion, ER);
        registerManaged(SCALES_RINDVIOR_DRAGON, SkillHandlers::startScalesEvasion, DG);
        registerManaged(FREEZEENG_ARMOR, SkillHandlers::startEvasionBuff, ER);
        registerManaged(SHINING_ARMOR, SkillHandlers::startEvasionBuff, ER);
        registerManaged(RESIST_MAGIC, SkillHandlers::startResistMagic, MR);
        registerManaged(GLOWING_AURA, SkillHandlers::startMeleeBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(IllUSION_OGRE, SkillHandlers::startMeleeBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(ENCHANT_ACURUCY, SkillHandlers::startMeleeBuff, MELEE_HIT);
        registerManaged(N_BUFF_DMG, SkillHandlers::startInventoryDamageBuff, MELEE_DAMAGE, RANGED_DAMAGE);
        registerManaged(N_BUFF_SP, SkillHandlers::startSpellPowerBuff, SP);
        registerManaged(IllUSION_LICH, SkillHandlers::startSpellPowerBuff, SP);
        registerManaged(N_BUFF_HPMP, SkillHandlers::startInventoryHealthBuff,
                MAX_HP, MAX_MP, new CSkillEffect.CarryLimit(3));
        registerManaged(N_BUFF_REDUCT, SkillHandlers::startInventoryArmorBuff, ARMOR_REDUCTION);
        frozen = true;
    }

    private final SkillHandlers.SkillHandler cast;
    private final SkillStopHandlers.StopHandler stop;
    private final PumpStartEffect start;
    private final CSkillEffect[] effects;
    private final boolean instant;

    @FunctionalInterface
    interface PumpStartEffect extends CSkillEffect {
        void apply(L1PcInstance target, int skillId, int duration);

        @Override default void pumpStart(L1PcInstance target, int skillId, int duration) {
            apply(target, skillId, duration);
        }
    }

    private CSkillInfo(SkillHandlers.SkillHandler cast, SkillStopHandlers.StopHandler stop,
            PumpStartEffect start, CSkillEffect[] effects, boolean instant) {
        this.cast = cast;
        this.stop = stop;
        this.start = start;
        this.effects = effects;
        this.instant = instant;
    }

    static CSkillInfo get(int skillId) {
        if (skillId < 0) return null;
        if (skillId >= PAGED_LIMIT) return sparse.get(skillId);
        final CSkillInfo[] page = pages[skillId >>> PAGE_SHIFT];
        return page == null ? null : page[skillId & (PAGE_SIZE - 1)];
    }

    private static void put(int skillId, CSkillInfo info) {
        if (skillId >= PAGED_LIMIT) {
            sparse.put(skillId, info);
        } else {
            final int index = skillId >>> PAGE_SHIFT;
            CSkillInfo[] page = pages[index];
            if (page == null) pages[index] = page = new CSkillInfo[PAGE_SIZE];
            page[skillId & (PAGE_SIZE - 1)] = info;
        }
    }

    static void registerInstant(int skillId, SkillHandlers.SkillHandler cast) {
        registerCast(skillId, cast, true);
    }

    static void registerCast(int skillId, SkillHandlers.SkillHandler cast, boolean instant) {
        if (frozen || skillId < 0 || cast == null) return;
        if (get(skillId) != null) throw new IllegalStateException("Duplicate skill cast " + skillId);
        put(skillId, new CSkillInfo(cast, null, null, null, instant));
    }

    static void registerPumpEnd(int skillId, SkillStopHandlers.StopHandler stop) {
        if (frozen || skillId < 0 || stop == null) return;
        CSkillInfo prior = get(skillId);
        if (prior != null && prior.stop != null) throw new IllegalStateException("Duplicate skill end " + skillId);
        put(skillId, new CSkillInfo(prior == null ? null : prior.cast, stop, null, null,
                prior != null && prior.instant));
    }

    private static void registerManaged(int skillId, PumpStartEffect start, CSkillEffect... effects) {
        CSkillInfo prior = get(skillId);
        if (prior == null || prior.stop == null || prior.cast != null)
            throw new IllegalStateException("Invalid managed skill definition " + skillId);
        put(skillId, new CSkillInfo(null, prior.stop, start, effects, false));
    }

    SkillHandlers.HandlerResult activateSkillEffects(L1SkillUse su, L1Character target, L1Magic magic,
            int undeadType, Iterator<?> targets) {
        if (start != null) {
            if (!(target instanceof L1PcInstance pc)) return SkillHandlers.HandlerResult.NOT_HANDLED;
            int skillId = su.hdSkillId();
            int duration = skillId == SHINING_AURA || skillId == SHIELD
                    || skillId == DRESS_DEXTERITY || skillId == DRESS_MIGHTY || skillId == GLOWING_AURA
                    ? su.hdGetBuffIconDuration() : SkillHandlers.durationSecForBuff(su, skillId);
            return pc.submitSkillPumpStart(skillId, duration)
                    ? SkillHandlers.HandlerResult.HANDLED : SkillHandlers.HandlerResult.CONTINUE;
        }
        if (cast == null) return SkillHandlers.HandlerResult.NOT_HANDLED;
        return instant ? cast.instant(su, target, magic, undeadType, targets)
                : cast.handle(su, target, magic, undeadType, targets);
    }

    public static void pumpStartFromOwner(L1PcInstance target, int skillId, int duration) {
        CSkillInfo info = get(skillId);
        if (info == null || info.start == null) return;
        try {
            info.start.pumpStart(target, skillId, duration);
        } catch (RuntimeException | Error failure) {
            if (!target.hasSkillEffect(skillId)) target.removeSkillModifierFromOwner(skillId);
            throw failure;
        }
    }

    public static boolean isManaged(int skillId) {
        CSkillInfo info = get(skillId);
        return info != null && info.start != null;
    }

    /** Shared stat application for casts and login restores with distinct timer and packet rules. */
    public static void applyFixedModifierFromOwner(L1PcInstance target, int skillId) {
        final long captured = switch (skillId) {
            case AQUA_PROTECTER, FREEZEENG_ARMOR -> 5;
            case SHINING_ARMOR -> 10;
            case SOLID_CARRIAGE -> 15;
            case DRESS_EVASION -> 18;
            case RESIST_MAGIC -> 10L << 32;
            case GLOWING_AURA -> (5L << 32) | 5;
            case IllUSION_OGRE -> (4L << 32) | 4;
            case ENCHANT_ACURUCY -> 5;
            default -> throw new IllegalArgumentException("No fixed modifier for " + skillId);
        };
        target.applySkillModifierFromOwner(skillId, captured);
    }

    void pump(L1Character target, long captured, SkillEffectContainer.SkillModifiers modifiers) {
        for (CSkillEffect effect : effects) {
            if (effect.checkPumpCondition(target)) effect.pump(captured, modifiers);
        }
    }

    boolean hasCast() { return cast != null || start != null; }

    SkillStopHandlers.StopHandler stopHandler() { return stop; }

    public static boolean grantSupportAdvance(L1PcInstance target, int seconds) {
        return CIOThreadCallback.getInstance().submitSupportAdvance(target, seconds);
    }
}
