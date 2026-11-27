package l1j.server.server.model.character.skill;

import java.util.Iterator;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.character.effect.paralysis.CurseParalysis;

/** Shared execution contract for the separate cast, lifetime and stat stages. */
interface CSkillEffect {
    default SkillHandlers.HandlerResult instant(L1SkillUse cast, L1Character target, L1Magic magic,
            int undeadType, Iterator<?> targets) {
        return SkillHandlers.HandlerResult.NOT_HANDLED;
    }

    default boolean checkPumpCondition(L1Character target) { return true; }

    default void pump(long captured, SkillEffectContainer.CCreatureSkillMod modifiers) { }

    default void pumpStart(L1PcInstance target, int skillId, int duration) { }

    default SkillStopHandlers.StopResult pumpEnd(L1Character target, int skillId) {
        return SkillStopHandlers.StopResult.NOT_HANDLED;
    }

    enum Bonus implements CSkillEffect {
        MAX_HP { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.hp += (int) captured;
        }},
        MAX_MP { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.mp += (int) (captured >>> 32);
        }},
        AC { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.ac += (int) captured;
        }},
        MR { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.mr += (int) (captured >>> 32);
        }},
        PRIMARY_STATS { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.addPrimaryLanes(captured, 1);
        }},
        PRIMARY_PENALTY { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.addPrimaryLanes(captured, -1);
        }},
        ELEMENTAL_RESISTANCE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.waterResistance += (int) captured & 0xff;
            out.windResistance += (int) (captured >>> 8) & 0xff;
            out.earthResistance += (int) (captured >>> 16) & 0xff;
            out.fireResistance += (int) (captured >>> 24) & 0xff;
        }},
        ELEMENTAL_WEAKNESS { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            switch ((int) captured) {
                case 1 -> out.earthResistance -= 50;
                case 2 -> out.fireResistance -= 50;
                case 4 -> out.waterResistance -= 50;
                case 8 -> out.windResistance -= 50;
                default -> { }
            }
        }
            @Override public boolean checkPumpCondition(L1Character target) { return true; }
        },
        ER { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.er += (int) captured;
        }},
        DG { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.dg += (int) captured;
        }},
        MELEE_HIT { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.meleeHit += (int) captured;
        }},
        MELEE_DAMAGE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.meleeDamage += (int) (captured >>> 32);
        }},
        RANGED_DAMAGE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.rangedDamage += (int) captured;
        }},
        RANGED_HIT { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.rangedHit += (int) captured;
        }},
        RANGED_CRITICAL { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.rangedCritical += (int) captured;
        }},
        MAGIC_CRITICAL { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.magicCritical += (int) captured;
        }},
        WEAPON_DAMAGE_RATE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.weaponDamageRate += (int) captured;
        }},
        WEAPON_HIT_RATE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.weaponHitRate += (int) captured;
        }},
        BOW_HIT_RATE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.bowHitRate += (int) captured;
        }},
        BOW_DAMAGE_RATE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.bowDamageRate += (int) captured;
        }},
        BASE_MAGIC_HIT { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.baseMagicHit += (int) captured;
        }},
        MAGIC_HIT { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.magicHit += (int) captured;
        }},
        SP { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.sp += (int) captured;
        }},
        HP_REGEN { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.hpRegen += (int) captured;
        }},
        MP_REGEN { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.mpRegen += (int) captured;
        }},
        ARMOR_REDUCTION { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.armorReduction += (int) captured;
        }},
        PVP_DAMAGE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.pvpDamage += (int) captured;
        }},
        PVP_REDUCTION { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.pvpReduction += (int) (captured >>> 32);
        }},
        SPECIAL_RESISTANCE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.specialResistance = SkillEffectContainer.CCreatureSkillMod.addSpecialLanes(out.specialResistance, captured);
        }},
        SPIRIT_PIERCE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.specialResistance = SkillEffectContainer.CCreatureSkillMod.addSpecialLanes(out.specialResistance,
                    ((captured >>> 32) & 0xffL) << 40);
        }},
        IMPACT_PROBABILITY { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.impactProbability += (int) captured;
        }},
        ALL_PIERCE { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.specialResistance = SkillEffectContainer.CCreatureSkillMod.addSpecialLanes(out.specialResistance,
                    (captured & 0xffL) << 48);
        }},
        PVP_MAGIC_REDUCTION { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.pvpMagicReduction += (int) captured;
        }},
        SPECIAL_PIERCE_FOUR { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.specialPierce = SkillEffectContainer.CCreatureSkillMod.addSpecialPierceLanes(
                    out.specialPierce, (int) captured);
        }};

        @Override public boolean checkPumpCondition(L1Character target) { return target instanceof L1PcInstance; }
    }

    /** Signed, independent resistance penalty. Captures the kind and delta at boot. */
    record SpecialPenalty(int kindIndex, int amount) implements CSkillEffect {
        @Override public boolean checkPumpCondition(L1Character target) {
            return target instanceof L1PcInstance;
        }

        @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.addSpecialPenalty(kindIndex, amount);
        }
    }

    record CarryLimit(int amount) implements CSkillEffect {
        @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.carryLimit += amount;
        }
    }

    record Fixed(Bonus bonus, long amount) implements CSkillEffect {
        @Override public boolean checkPumpCondition(L1Character target) {
            return bonus.checkPumpCondition(target);
        }

        @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            bonus.pump(amount, out);
        }
    }

    record UpperWord(Bonus bonus) implements CSkillEffect {
        @Override public boolean checkPumpCondition(L1Character target) { return bonus.checkPumpCondition(target); }

        @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            bonus.pump(captured >>> 32, out);
        }
    }

    record DamagePoison(int interval, int damage, boolean activeEffect, int duration) implements SkillHandlers.SkillHandler {
        @Override public SkillHandlers.HandlerResult handle(L1SkillUse cast, L1Character target,
                L1Magic magic, int undeadType, Iterator<?> targets) {
            if (target != null) l1j.server.server.model.character.effect.poison.DamagePoison.doInfection(cast.hdUser(), target, interval, damage, activeEffect, duration);
            return SkillHandlers.HandlerResult.HANDLED;
        }
    }

    enum Paralysis implements SkillHandlers.SkillHandler {
        CURSE, PETRIFY;

        @Override public SkillHandlers.HandlerResult handle(L1SkillUse cast, L1Character target,
                L1Magic magic, int undeadType, Iterator<?> targets) {
            if (target == null) return SkillHandlers.HandlerResult.HANDLED;
            if (this == CURSE || (!target.hasSkillEffect(L1SkillId.MOB_COCA) && !target.hasSkillEffect(L1SkillId.MOB_BASILL))) {
                CurseParalysis.curse(target, cast.hdSkillId());
            }
            return SkillHandlers.HandlerResult.HANDLED;
        }
    }

    record RestoreMana(int amount) implements SkillHandlers.SkillHandler {
        @Override public SkillHandlers.HandlerResult handle(L1SkillUse cast, L1Character target,
                L1Magic magic, int undeadType, Iterator<?> targets) {
            if (target instanceof L1PcInstance pc) pc.setCurrentMp(pc.getCurrentMp() + amount);
            return SkillHandlers.HandlerResult.HANDLED;
        }
    }

    enum Potential implements CSkillEffect {
        MR { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.mr += (int) out.potentialMagic;
        }},
        SP { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.sp += (int) (out.potentialMagic >>> 32);
        }},
        ER { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.er += (int) out.potentialEvasion;
        }},
        DG { @Override public void pump(long captured, SkillEffectContainer.CCreatureSkillMod out) {
            out.dg += (int) (out.potentialEvasion >>> 32);
        }};
    }

    enum MaxHp {
        PRIDE(4.0, 8854, 5264, 5265),
        GIGANTIC(2.0, 6168, 3918, 3919);

        private final double levelDivisor;
        final int icon;
        final int startMessage;
        final int stopMessage;

        MaxHp(double levelDivisor, int icon, int startMessage, int stopMessage) {
            this.levelDivisor = levelDivisor;
            this.icon = icon;
            this.startMessage = startMessage;
            this.stopMessage = stopMessage;
        }

        long capture(L1PcInstance target) {
            final double percent = target.getLevel() / levelDivisor;
            return (int) Math.round(target.getBaseMaxHp() * (percent * 0.01));
        }
    }
}
