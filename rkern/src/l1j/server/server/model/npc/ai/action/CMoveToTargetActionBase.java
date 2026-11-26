package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;

final class CMoveToTargetActionBase {
    private CMoveToTargetActionBase() {
    }

    static int computeDistance(final L1NpcInstance creature,
                               final L1Character target) {
        final int ranged = Math.max(1, creature.getNpcTemplate().get_ranged());
        final int currentDistance = creature.getLocation()
                .getTileLineDistance(target.getLocation());
        return currentDistance <= ranged
                ? Math.max(1, currentDistance - 1)
                : ranged;
    }
}
