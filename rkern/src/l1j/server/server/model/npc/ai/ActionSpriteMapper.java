package l1j.server.server.model.npc.ai;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.animation.EActionCodes;

/**
 * 스프라이트/몹 속성 기반 공격 액션 자동 판별 
 */
public final class ActionSpriteMapper {

    private ActionSpriteMapper() {
    }

    public static EActionCodes forAttack(final L1NpcInstance npc) {
        if (npc == null || npc.getNpcTemplate() == null) {
            return EActionCodes.attack;
        }

        return npc.getNpcTemplate().get_ranged() > 5
                ? EActionCodes.attack_bow
                : EActionCodes.attack;
    }
}