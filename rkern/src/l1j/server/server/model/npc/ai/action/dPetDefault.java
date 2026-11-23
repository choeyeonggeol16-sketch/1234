package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.utils.NpcAiUtil;

/**
 * NC CNPC::dPetDefault (DESIRE_PET_DEFAULT), L1 2D action adapter.
 *
 * Executed by the existing CIO NPC owner through ACTION_COMPANION/noTarget.
 * L1's pet command modes are preserved, not replaced with L2 summon options:
 * 3 = stay, 4 = keep a 4..7 tile band, 5 = guard home, other = follow master.
 * Pet call mode 7 and companion item commands stay in their original owner
 * classes. Hate/ATTACK has already been selected before this idle action.
 *
 * Stateless, allocation-free, no extra scheduler, worker or event queue.
 */
public final class dPetDefault {
    private static final int STAY = 3;
    private static final int EXTEND = 4;
    private static final int ALERT = 5;

    private dPetDefault() {
    }

    /**
     * L1 pet/summon default command (NC contextValue projected to L1 modes).
     *
     * @return true when the old noTarget contract must stop the idle AI;
     *         false while movement/hold/guard is active.
     */
    public static boolean onTick(
            final L1NpcInstance creature,
            final L1Character master,
            final int mode) {

        if (creature == null) {
            return true;
        }
        if (mode == STAY) {
            dFollow.clear(creature);
            return true;
        }
        if (!canFollow(creature, master)) {
            dFollow.clear(creature);
            return true;
        }

        if (mode == EXTEND) {
            // NC dPetDefault contextValue=1 holds its movement position.
            // L1's extend command keeps its original 4..7-tile band.
            dFollow.clear(creature);
            final int distance = creature.getLocation()
                    .getTileLineDistance(master.getLocation());
            if (distance < 4) {
                int dir = creature.targetReverseDirection(
                        master.getX(), master.getY());
                dir = NpcAiUtil.checkObject(
                        creature.getX(), creature.getY(),
                        creature.getMapId(), dir);
                if (dir != -1) {
                    creature.setSleepTime(creature.setDirectionMove(dir));
                }
            } else if (distance > 7) {
                final int dir = creature.moveDirection(
                        master.getX(), master.getY());
                if (dir != -1) {
                    creature.setSleepTime(creature.setDirectionMove(dir));
                }
            }
            return false;
        }

        if (mode == ALERT) {
            dFollow.clear(creature);
            if (Math.abs(creature.getHomeX() - creature.getX()) > 1
                    || Math.abs(creature.getHomeY() - creature.getY()) > 1) {
                final int dir = creature.moveDirection(
                        creature.getHomeX(), creature.getHomeY());
                if (dir == -1) {
                    creature.setHomeX(creature.getX());
                    creature.setHomeY(creature.getY());
                } else {
                    creature.setSleepTime(creature.setDirectionMove(dir));
                }
            }
            return false;
        }

        return dFollow.follow(creature, master);
    }

    /**
     * Companion and magic-doll L1 special modes are handled by their
     * subclasses; default movement uses the same NC pet-default branch.
     */
    public static boolean follow(
            final L1NpcInstance creature, final L1Character master) {
        if (!canFollow(creature, master)) {
            dFollow.clear(creature);
            return true;
        }
        return dFollow.follow(creature, master);
    }

    /**
     * NC dPetDefault checks active/alive pet and owner before movement.
     * L1-specific owner-loss/despawn policy remains in each subclass.
     */
    private static boolean canFollow(
            final L1NpcInstance creature, final L1Character master) {
        return creature != null
                && master != null
                && creature.getMapId() == master.getMapId()
                && !creature.isDead()
                && creature.getCurrentHp() > 0
                && !master.isDead()
                && master.getCurrentHp() > 0;
    }
}
