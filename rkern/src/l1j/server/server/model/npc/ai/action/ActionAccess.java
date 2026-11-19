package l1j.server.server.model.npc.ai.action;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * NPC action 코드가 owner의 primitive 상태에 접근하는 저수준 통로.
 *
 * 인스턴스를 만들지 않으며 모든 메서드는 단순 static access다.
 */
public final class ActionAccess {

    private ActionAccess() {
    }

    public static byte currentAction(final L1NpcInstance npc) {
        return npc._aiAction;
    }

    public static void currentAction(
            final L1NpcInstance npc,
            final byte action) {
        npc._aiAction = action;
    }

    public static byte pendingAction(final L1NpcInstance npc) {
        return npc._pendingAiAction;
    }

    public static void pendingAction(
            final L1NpcInstance npc,
            final byte action) {
        npc._pendingAiAction = action;
    }

    /*
     * mobSkill accessor 제거.
     *
     * NPC는 더 이상 L1MobSkillUse 객체를 소유하지 않는다.
     * 스킬 실행은 L1MobSkillUse.skillUse(npc, target) static 경로를 사용한다.
     */

    public static boolean tryMoveToTargetTeleport(
            final L1NpcInstance npc,
            final L1Character target) {

        if (target == null) {
            return false;
        }

        final int distance =
                npc.getLocation().getTileDistance(target.getLocation());

        if (npc.firstFound
                && npc.getNpcTemplate().is_teleport()
                && distance > 3
                && distance < 15) {

            if (npc.nearTeleport(target.getX(), target.getY())) {
                npc.firstFound = false;
                return true;
            }
        }

        if (npc.getNpcTemplate().is_teleport()
                && 20 > ThreadLocalRandom.current().nextInt(100)
                && npc.getCurrentMp() >= 10
                && distance > 6
                && distance < 15) {

            return npc.nearTeleport(target.getX(), target.getY());
        }

        return false;
    }

    public static boolean moveOneStep(
            final L1NpcInstance npc,
            final int dir) {

        if (dir < 0 || dir > 7) {
            return false;
        }

        final int beforeX = npc.getX();
        final int beforeY = npc.getY();

        npc.setSleepTime(npc.setDirectionMove(dir));

        return beforeX != npc.getX()
                || beforeY != npc.getY();
    }

    public static int incrementMoveStuck(final L1NpcInstance npc) {
        return ++npc._stuckCount;
    }

    public static int moveStuck(final L1NpcInstance npc) {
        return npc._stuckCount;
    }

    public static void moveStuck(
            final L1NpcInstance npc,
            final int value) {
        npc._stuckCount = value;
    }

    public static void resetMoveBlockedState(final L1NpcInstance npc) {
        npc._stuckCount = 0;
        npc._pathRetryAfter = 0L;
    }

    public static long pathRetryAfter(final L1NpcInstance npc) {
        return npc._pathRetryAfter;
    }

    public static void clearPathFailureState(final L1NpcInstance npc) {
        npc._pathRetryAfter = 0L;
    }

    public static void markPathFailed(
            final L1NpcInstance npc,
            final long retryAt) {
        npc._pathRetryAfter = retryAt;
    }

    public static boolean followMaster(final L1NpcInstance npc) {
        return npc.noTargetMaster();
    }

    public static boolean followGroup(
            final L1NpcInstance npc,
            final L1NpcInstance leader) {
        return npc.noTargetGroup(leader);
    }

    public static void moveRandom(final L1NpcInstance npc) {
        npc.noTargetNormal();
    }

    public static int randomMoveDistance(final L1NpcInstance npc) {
        return npc._randomMoveDistance;
    }

    public static int randomMoveDirection(final L1NpcInstance npc) {
        return npc._randomMoveDirection;
    }

    public static void randomMoveState(
            final L1NpcInstance npc,
            final int distance,
            final int direction) {
        npc._randomMoveDistance = distance;
        npc._randomMoveDirection = direction;
    }

    public static void directionMove(
            final L1NpcInstance npc,
            final int dir) {
        npc.setSleepTime(npc.setDirectionMove(dir));
    }

    public static L1ItemInstance targetItem(final L1NpcInstance npc) {
        return npc._targetItem;
    }

    public static void targetItem(
            final L1NpcInstance npc,
            final L1ItemInstance item) {
        npc._targetItem = item;
    }

    public static int itemSearchCooldown(final L1NpcInstance npc) {
        return npc._itemSearchCooldown;
    }

    public static void itemSearchCooldown(
            final L1NpcInstance npc,
            final int cooldown) {
        npc._itemSearchCooldown = cooldown;
    }

    public static void sleep(
            final L1NpcInstance npc,
            final long sleepTime) {
        npc.setSleepTime(sleepTime);
    }
}
