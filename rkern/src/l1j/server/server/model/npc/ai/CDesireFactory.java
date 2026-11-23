package l1j.server.server.model.npc.ai;

import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.L1Character;

/**
 * reference server CDesireFactory::MakeUseSkill, adapted to primitive slots in CDesirePQ.
 * reference server MakeUseSkill initializes state 10, target ID, skill ID, move type,
 * boost, flag and desire value. L1 retains its 2-D skill executor.
 */
public final class CDesireFactory {
    private CDesireFactory() { }

    /**
     * CDesireFactory::MakeAttack (0x0041B520), represented by primitive
     * CDesirePQ slots rather than allocating a CDesire object per event.
     * This is the verified state/target/value registration path only.
     */
    public static boolean MakeAttack(
            final L1MonsterInstance npc,
            final L1Character target,
            final float desireValue,
            final long nowMs) {
        return CDesirePQ.addAttackDesire(
                npc, target, desireValue, nowMs);
    }

    /**
     * CDesireFactory::MakeAttack(int, NpcAttackMoveType, bool,
     * float, float, int) at 0x0041B520.
     * The two trailing numeric fields are copied as supplied by reference server.
     * L1's existing attack event adapters do not infer their 3D meanings.
     */
    public static boolean MakeAttack(
            final L1MonsterInstance npc,
            final L1Character target,
            final int moveType,
            final boolean flag,
            final float desireValue,
            final float auxiliaryFloat,
            final int auxiliaryInt,
            final long nowMs) {
        return CDesirePQ.addAttackDesire(npc, target, moveType,
                flag, desireValue, auxiliaryFloat, auxiliaryInt, nowMs);
    }

    public static boolean MakeUseSkill(
            final L1MonsterInstance npc,
            final int targetId,
            final int skillId,
            final int moveType,
            final int flags,
            final float desireValue,
            final float boost,
            final int extra) {

        if (npc == null || skillId <= 0 || targetId < 0
                || npc.isDead() || npc.getCurrentHp() <= 0) {
            return false;
        }
        if (targetId != 0) {
            final L1Character target = npc.getTarget() != null
                    && npc.getTarget().getId() == targetId
                    ? npc.getTarget() : null;
            // An unrelated target is resolved and checked on dispatch.
            if (target != null && (target.isDead()
                    || target.getMapId() != npc.getMapId()
                    || (target.isInvisble()
                            && !npc.getNpcTemplate().is_agrocoi()))) {
                return false;
            }
        }
        final boolean added = CDesirePQ.addUseSkillDesire(
                npc, targetId, skillId, moveType, flags,
                desireValue, boost, extra,
                System.currentTimeMillis());
        if (added) {
            NpcServer.getInstance().wake(npc);
        }
        return added;
    }
}
