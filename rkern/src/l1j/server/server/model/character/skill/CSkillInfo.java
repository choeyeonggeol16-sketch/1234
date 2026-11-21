package l1j.server.server.model.character.skill;

import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.utils.eKind;

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
        registerManaged(N_BUFF_PVP_DMG, SkillHandlers::startInventoryResistanceBuff, PVP_DAMAGE, PVP_REDUCTION);
        registerManaged(N_BUFF_STUN, SkillHandlers::startInventoryResistanceBuff, SPECIAL_RESISTANCE);
        registerManaged(N_BUFF_HOLD, SkillHandlers::startInventoryResistanceBuff, SPECIAL_RESISTANCE);
        registerManaged(CLEAR_MIND, SkillHandlers::startClearMind, PRIMARY_STATS);
        registerManaged(PHYSICAL_ENCHANT_STR, SkillHandlers::startPhysicalEnchant, PRIMARY_STATS);
        registerManaged(PHYSICAL_ENCHANT_DEX, SkillHandlers::startPhysicalEnchant, PRIMARY_STATS);
        registerManaged(RESIST_ELEMENTAL, SkillHandlers::startCommonStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(ELEMENTAL_PROTECTION, SkillHandlers::startCommonStatBuff, ELEMENTAL_RESISTANCE, SPIRIT_PIERCE);
        registerManaged(INSIGHT, SkillHandlers::startCommonStatBuff, PRIMARY_STATS);
        registerManaged(MIRROR_IMAGE, SkillHandlers::startCommonStatBuff, DG);
        registerManaged(UNCANNY_DODGE, SkillHandlers::startCommonStatBuff, DG);
        registerManaged(기간틱, null, MAX_HP);
        registerManaged(프라이드, null, MAX_HP);
        registerManaged(포텐셜, null, MAX_HP, MAX_MP, CSkillEffect.Potential.MR,
                CSkillEffect.Potential.SP, CSkillEffect.Potential.ER, CSkillEffect.Potential.DG);
        registerManaged(God_buff, SkillHandlers::startGodBuff, MELEE_HIT,
                new CSkillEffect.Fixed(AC, -2), new CSkillEffect.Fixed(MAX_HP, 20),
                new CSkillEffect.Fixed(MAX_MP, 13L << 32), specialResistance(eKind.SPIRIT, 10));
        registerManaged(SCALES_FIRE_DRAGON, SkillHandlers::startScalesFireDragon, MELEE_HIT,
                specialResistance(eKind.ABILITY, 5), specialResistance(eKind.DRAGON_SPELL, 5),
                specialResistance(eKind.FEAR, 5));
        registerManaged(BRAVE_AURA, SkillHandlers::startBraveAura);
        registerManaged(IRON_SKIN, SkillHandlers::startArmorBuff, AC);
        registerManaged(FIRE_SHIELD, SkillHandlers::startArmorBuff, AC);
        registerManaged(BLESSED_ARMOR, SkillHandlers::startArmorBuff, AC);
        registerManaged(BOUNCE_ATTACK, SkillHandlers::startCommonStatBuff, MELEE_HIT);
        registerManaged(PATIENCE, SkillHandlers::startCommonStatBuff, ARMOR_REDUCTION);
        registerManaged(BERSERKERS, SkillHandlers::startBerserkers,
                new CSkillEffect.Fixed(AC, 10), MELEE_HIT, MELEE_DAMAGE);
        registerManagedWithFallback(GUARD_BREAK, SkillHandlers::startGuardBreak,
                SkillHandlers::castGuardBreakOnNpc, AC);
        registerManaged(WEAKNESS, SkillHandlers::startCommonStatBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(MOB_WEAKNESS_1, SkillHandlers::startCommonStatBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(DISEASE, SkillHandlers::startDisease, new CSkillEffect.Fixed(AC, 12), MELEE_HIT, MELEE_DAMAGE);
        registerManaged(MOB_DISEASE_1, SkillHandlers::startDisease, new CSkillEffect.Fixed(AC, 12), MELEE_HIT, MELEE_DAMAGE);
        registerManaged(MOB_DISEASE_30, SkillHandlers::startDisease, new CSkillEffect.Fixed(AC, 12), MELEE_HIT, MELEE_DAMAGE);
        registerManaged(FEAR, SkillHandlers::startCommonStatBuff, DG);
        registerManaged(HORROR_OF_DEATH, SkillHandlers::startCommonStatBuff, PRIMARY_PENALTY);
        registerManaged(PANIC, SkillHandlers::startCommonStatBuff, PRIMARY_PENALTY);
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
        registerManagedWithFallback(skillId, start, null, effects);
    }

    private static void registerManagedWithFallback(int skillId, PumpStartEffect start,
            SkillHandlers.SkillHandler fallback, CSkillEffect... effects) {
        CSkillInfo prior = get(skillId);
        if (prior == null || prior.stop == null || prior.cast != null)
            throw new IllegalStateException("Invalid managed skill definition " + skillId);
        put(skillId, new CSkillInfo(fallback, prior.stop, start, effects, false));
    }

    private static CSkillEffect specialResistance(eKind kind, int amount) {
        final int shift = (kind.value() - 1) << 3;
        return new CSkillEffect.Fixed(SPECIAL_RESISTANCE, (long) amount << shift);
    }

    SkillHandlers.HandlerResult activateSkillEffects(L1SkillUse su, L1Character target, L1Magic magic,
            int undeadType, Iterator<?> targets) {
        if (start != null && target instanceof L1PcInstance pc) {
            int skillId = su.hdSkillId();
            int duration = skillId == SHINING_AURA || skillId == SHIELD
                    || skillId == DRESS_DEXTERITY || skillId == DRESS_MIGHTY || skillId == GLOWING_AURA
                    || skillId == PHYSICAL_ENCHANT_STR || skillId == PHYSICAL_ENCHANT_DEX || skillId == BRAVE_AURA
                    || skillId == BLESSED_ARMOR || skillId == IRON_SKIN || skillId == FIRE_SHIELD
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
        return info != null && info.effects != null;
    }

    public static boolean submitSupportBerserkers(L1PcInstance target) {
        // The negative duration selects the support NPC's distinct attack bonuses.
        return target.submitSkillPumpStart(BERSERKERS, -320);
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
            case BOUNCE_ATTACK -> 6;
            case PATIENCE -> 2;
            case BERSERKERS -> (2L << 32) | 8;
            case WEAKNESS, MOB_WEAKNESS_1 -> (-5L << 32) | Integer.toUnsignedLong(-1);
            case DISEASE, MOB_DISEASE_1, MOB_DISEASE_30 -> Integer.toUnsignedLong(-6);
            case FEAR -> -25;
            case HORROR_OF_DEATH -> 0x030003L;
            case PANIC -> 0x010101010101L;
            case CLEAR_MIND -> 0x00010101;
            case PHYSICAL_ENCHANT_STR -> 5;
            case PHYSICAL_ENCHANT_DEX -> 5 << 8;
            case RESIST_ELEMENTAL -> 0x0a0a0a0a;
            case ELEMENTAL_PROTECTION -> (3L << 32) | switch (target.getElfAttr()) {
                case 1 -> 50L << 16;
                case 2 -> 50L << 24;
                case 4 -> 50L;
                case 8 -> 50L << 8;
                default -> 0L;
            };
            case INSIGHT -> 0x0101010101L;
            case MIRROR_IMAGE, UNCANNY_DODGE -> 30;
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
