package l1j.server.server.model.character.skill;

import java.util.Iterator;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;

/** Shared execution contract for the separate cast, lifetime and stat stages. */
interface CSkillEffect {
    default SkillHandlers.HandlerResult instant(L1SkillUse cast, L1Character target, L1Magic magic,
            int undeadType, Iterator<?> targets) {
        return SkillHandlers.HandlerResult.NOT_HANDLED;
    }

    default boolean checkPumpCondition(L1Character target) { return true; }

    default void pump(long captured, SkillEffectContainer.SkillModifiers modifiers) { }

    default void pumpStart(L1PcInstance target, int skillId, int duration) { }

    default SkillStopHandlers.StopResult pumpEnd(L1Character target, int skillId) {
        return SkillStopHandlers.StopResult.NOT_HANDLED;
    }

    enum Bonus implements CSkillEffect {
        MAX_HP { @Override public void pump(long captured, SkillEffectContainer.SkillModifiers out) {
            out.hp += (int) captured;
        }},
        MAX_MP { @Override public void pump(long captured, SkillEffectContainer.SkillModifiers out) {
            out.mp += (int) (captured >>> 32);
        }},
        AC { @Override public void pump(long captured, SkillEffectContainer.SkillModifiers out) {
            out.ac += (int) captured;
        }},
        MR { @Override public void pump(long captured, SkillEffectContainer.SkillModifiers out) {
            out.mr += (int) (captured >>> 32);
        }},
        PRIMARY_STATS { @Override public void pump(long captured, SkillEffectContainer.SkillModifiers out) {
            out.primaryStats += (int) captured;
        }},
        ELEMENTAL_RESISTANCE { @Override public void pump(long captured, SkillEffectContainer.SkillModifiers out) {
            out.elementalResistance += (int) captured;
        }};

        @Override public boolean checkPumpCondition(L1Character target) { return target instanceof L1PcInstance; }
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
