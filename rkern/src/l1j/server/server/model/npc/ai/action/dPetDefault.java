package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.utils.NpcAiUtil;

public final class dPetDefault {
    private static final int STAY = 3;
    private static final int EXTEND = 4;
    private static final int ALERT = 5;

    private dPetDefault() {
    }

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
            // Native dPetDefault returns 0 for this pass, not a permanent
            // AI uninstall. L1's CIO owner needs to retry after the master
            // recovers (the subclass already handles lost-map/stay policy).
            if (master != null
                    && master.getMapId() == creature.getMapId()) {
                CCreatureController.sleep(creature, 500L);
                return false;
            }
            return true;
        }

        if (mode == EXTEND) {
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
            } else {
                CCreatureController.sleep(creature, 500L);
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
            } else {
                CCreatureController.sleep(creature, 500L);
            }
            return false;
        }

        return follow(creature, master);
    }

    public static boolean follow(
            final L1NpcInstance creature, final L1Character master) {
        if (creature == null) return true;
        if (!canFollow(creature, master)) {
            dFollow.clear(creature);
            if (master != null && master.getMapId() == creature.getMapId()) {
                CCreatureController.sleep(creature, 500L);
                return false;
            }
            return true;
        }

        final boolean close = creature.getLocation()
                .getTileLineDistance(master.getLocation()) <= CMoveAction.FOLLOW_RADIUS;
        final boolean stopped = dFollow.follow(creature, master);
        if (close && !stopped) {
            CCreatureController.sleep(creature, 500L);
        }
        return stopped;
    }

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
