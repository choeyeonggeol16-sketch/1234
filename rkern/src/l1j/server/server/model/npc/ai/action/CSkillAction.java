package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1MobSkillUse;
import l1j.server.server.model.character.effect.action.EffectModel;
import l1j.server.server.model.character.effect.action.loader.EffectModelLoader;
import l1j.server.server.model.npc.ai.CDesirePQ;

/**
 * NPC combat skill/effect action primitive.
 *
 * The old CAttackAction embedded this exact Effect -> Skill decision. It is
 * split out so combat ability execution has its own reference design action boundary,
 * while preserving the existing probability/order/sleep semantics.
 *
 * It intentionally does not introduce a new persistent current-action state;
 * L1MobSkillUse already owns the existing cast-complete contract and wakes AI
 * through L1NpcInstance.endCast().
 */
public final class CSkillAction {

    private CSkillAction() {
    }

    /** Execute exactly the skill selected by the reference design desire queue. */
    public static void onDesireTick(final L1NpcInstance creature) {
        if (creature == null
                || ActionAccess.currentAction(creature)
                        != CCreatureController.ACTION_USE_SKILL) {
            return;
        }
        if (!(creature instanceof L1MonsterInstance)) {
            CCreatureController.think(creature);
            return;
        }
        final L1MonsterInstance monster = (L1MonsterInstance) creature;
        final int skillId = CDesirePQ.activeSkillId(monster);
        final int targetId = CDesirePQ.activeSkillTargetId(monster);
        final L1Character target = CDesirePQ.activeSkillTarget(monster);
        if (skillId > 0 && target != null
                && !monster.isDead()
                && monster.getCurrentHp() > 0
                && monster.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_NONE
                && !target.isDead()
                && target.getCurrentHp() > 0
                && target.getMapId() == monster.getMapId()
                // A targetId of zero is the reference design self-cast sentinel.
                // Enemy visibility filtering must not suppress a self skill.
                && (targetId == 0 || !target.isInvisble()
                        || monster.getNpcTemplate().is_agrocoi())
                && L1MobSkillUse.skillUseSelected(monster, target, skillId)) {
            ActionAccess.sleep(monster, L1MobSkillUse.getSleepTime(monster));
        }
        // dUseSkill finishes this desire; ATTACK / CHASE stays queued.
        CDesirePQ.removeUseSkillDesire(monster, targetId, skillId);
        if (ActionAccess.currentAction(creature)
                == CCreatureController.ACTION_USE_SKILL) {
            CCreatureController.think(creature);
        }
    }

    public static boolean tryUse(
            final L1NpcInstance creature,
            final L1Character target) {

        if (creature == null || target == null
                || creature.isDead()
                || creature.getCurrentHp() <= 0
                || creature.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE
                || target.isDead()
                || target.getCurrentHp() <= 0
                || target.getMapId() != creature.getMapId()
                || (target.isInvisble() && !creature.getNpcTemplate().is_agrocoi())) {
            return false;
        }

        final EffectModel model =
                EffectModelLoader.getInstance()
                        .get(creature.getNpcId());

        if (model != null && model.isEffectSet()) {
            if (!model.runEffect(creature)) return false;
            ActionAccess.sleep(creature, L1MobSkillUse.getSleepTime(creature) + 500L);
            return true;
        }

        if (L1MobSkillUse.skillUse(creature, target)) {
            ActionAccess.sleep(
                    creature,
                    L1MobSkillUse.getSleepTime(creature));

            return true;
        }

        return false;
    }
}
