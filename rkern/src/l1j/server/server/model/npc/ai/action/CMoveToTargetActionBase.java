package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;

/**
 * reference server CMoveToTargetActionBase::ComputeDistance role, adapted to L1 tiles.
 *
 * The reference server CCreature virtual +0x4A8 computes a two-int reach interval from
 * creature radii and target/action state. L1 does not use reference server world units,
 * and movement occupancy/reservation remains with SetMoving.
 *
 * Keep the existing L1 attack-position and ranged/LOS behavior unchanged:
 * when a target is within raw range but LOS is not yet valid, request an
 * approach step rather than making the chase permanently AT_DISTANCE.
 */
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
