package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * 바닥 아이템 획득 action.
 *
 * target item / search cooldown은 L1NpcInstance primitive state를 사용한다.
 * 이동은 현재 NPC PathFinder/WorldCollision 경로를 그대로 탄다.
 */
public final class CGetItemAction {

    private static final int SEARCH_COOLDOWN_TICKS = 20;
    private static final int MAX_TARGET_DISTANCE = 15;
    private static final int SOFT_BLOCK_GIVEUP = 6;

    private static final long RETRY_SLEEP_MS = 120L;
    private static final long INVALID_TARGET_SLEEP_MS = 500L;
    private static final long PICKUP_SLEEP_MS = 1000L;

    private CGetItemAction() {
    }

    public static void set(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_GET_ITEM);

        onStart(creature);
    }

    public static void onStart(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        clearMoveState(creature);
    }

    public static byte onTick(final L1NpcInstance creature) {
        if (creature == null) {
            return CCreatureController.RESULT_INVALID;
        }

        final L1ItemInstance targetItem = ActionAccess.targetItem(creature);
        if (targetItem == null) {
            return CCreatureController.RESULT_DONE;
        }

        if (!isValidTargetItem(creature, targetItem)) {
            ActionAccess.targetItem(creature, null);
            ActionAccess.sleep(creature, INVALID_TARGET_SLEEP_MS);
            return CCreatureController.RESULT_DONE;
        }

        creature.onTargetItem();

        return ActionAccess.targetItem(creature) == null
                ? CCreatureController.RESULT_DONE
                : CCreatureController.RESULT_RUNNING;
    }

    public static void onEnd(
            final L1NpcInstance creature,
            final byte result) {

        if (creature == null) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_DONE:
        case CCreatureController.RESULT_INVALID:
            clearMoveState(creature);
            CCreatureController.doPendingAction(creature);
            return;

        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_BLOCKED:
        case CCreatureController.RESULT_STOP:
        default:
            return;
        }
    }

    public static void tickSearchCooldown(final L1NpcInstance creature) {
        final int cooldown = ActionAccess.itemSearchCooldown(creature);
        if (cooldown > 0) {
            ActionAccess.itemSearchCooldown(creature, cooldown - 1);
        }
    }

    public static void checkTarget(final L1NpcInstance creature) {
        final L1ItemInstance targetItem = ActionAccess.targetItem(creature);
        if (targetItem == null) {
            return;
        }

        if (!isValidTargetItem(creature, targetItem)) {
            ActionAccess.targetItem(creature, null);
            clearMoveState(creature);
        }
    }

    /**
     * 범위 안의 바닥 아이템 중 실제로 들 수 있는 가장 가까운 아이템을 고른다.
     * 화면 전체(-1) 첫 항목을 잡지 않으므로 멀리 있는 아이템 때문에 가까운 아이템을 놓치지 않는다.
     */
    public static void searchTarget(final L1NpcInstance creature) {
        if (creature == null
                || ActionAccess.itemSearchCooldown(creature) > 0) {
            return;
        }

        ActionAccess.itemSearchCooldown(creature, SEARCH_COOLDOWN_TICKS);

        final L1Inventory inventory = creature.getInventory();
        if (inventory == null) {
            return;
        }

        L1ItemInstance nearest = null;
        int nearestDistance = MAX_TARGET_DISTANCE + 1;

        for (L1Object obj : L1World.getInstance()
                .getClusterGrid()
                .queryNearbyReusable(
                        creature,
                        MAX_TARGET_DISTANCE)) {

            if (!(obj instanceof L1GroundInventory)) {
                continue;
            }

            final L1GroundInventory ground = (L1GroundInventory) obj;
            if (ground.getSize() <= 0) {
                continue;
            }

            for (L1ItemInstance item : ground.getItems()) {
                if (item == null
                        || item.getMapId() != creature.getMapId()) {
                    continue;
                }

                final int distance = chebyshev(
                        creature.getX(),
                        creature.getY(),
                        item.getX(),
                        item.getY());

                if (distance > MAX_TARGET_DISTANCE
                        || distance >= nearestDistance) {
                    continue;
                }

                if (inventory.checkAddItem(
                        item,
                        item.getCount()) != L1Inventory.OK) {
                    continue;
                }

                nearest = item;
                nearestDistance = distance;

                if (distance == 0) {
                    ActionAccess.targetItem(creature, nearest);
                    return;
                }
            }
        }

        if (nearest != null) {
            ActionAccess.targetItem(creature, nearest);
        }
    }

    /**
     * 같은 tile이면 줍고, 아니면 현재 NPC path/collision 계층으로 한 칸 접근한다.
     */
    public static void onTargetItem(final L1NpcInstance creature) {
        final L1ItemInstance targetItem =
                ActionAccess.targetItem(creature);

        if (targetItem == null) {
            return;
        }

        final int dx =
                Math.abs(creature.getX() - targetItem.getX());

        final int dy =
                Math.abs(creature.getY() - targetItem.getY());

        /*
         * 현재 WorldCollision/TileOccupancy 구조에서는
         * PC/NPC가 점유한 item tile로 직접 들어갈 수 없다.
         *
         * 따라서 같은 타일 또는 인접 1칸이면 획득한다.
         */
        if (dx <= 1 && dy <= 1) {
            creature.pickupTargetItem(targetItem);
            return;
        }

        if (!creature.isMovable()) {
            abandonTarget(
                    creature,
                    INVALID_TARGET_SLEEP_MS);
            return;
        }

        final int dir =
                CMoveAction.direction(
                        creature,
                        targetItem.getX(),
                        targetItem.getY());

        if (dir >= 0 && dir < 8) {
            if (ActionAccess.moveOneStep(
                    creature,
                    dir)) {

                ActionAccess.moveStuck(
                        creature,
                        0);

                return;
            }

            softBlocked(creature);
            return;
        }

        switch (dir) {
        case CMoveAction.DIR_RETRY:
            ActionAccess.sleep(
                    creature,
                    RETRY_SLEEP_MS);
            return;

        case CMoveAction.DIR_SOFT_BLOCK:
            softBlocked(creature);
            return;

        case CMoveAction.DIR_HARD_BLOCK:
        case CMoveAction.DIR_INVALID:
        default:
            abandonTarget(
                    creature,
                    INVALID_TARGET_SLEEP_MS);
            return;
        }
    }

    public static void pickup(
            final L1NpcInstance creature,
            final L1ItemInstance targetItem) {

        if (creature == null || targetItem == null) {
            return;
        }

        final L1Inventory groundInventory =
                L1World.getInstance().findGroundInventory(
                        targetItem.getX(),
                        targetItem.getY(),
                        targetItem.getMapId());

        if (groundInventory == null || groundInventory.getItem(targetItem.getId()) != targetItem) {
            ActionAccess.targetItem(creature, null);
            return;
        }

        final boolean oldGiveItem = targetItem.isGiveItem();
        targetItem.setGiveItem(true);

        final L1ItemInstance item =
                groundInventory.tradeItem(
                        targetItem,
                        targetItem.getCount(),
                        creature.getInventory());

        if (item == null) {
            /* transfer 실패 시 아직 바닥에 남아 있는 exact object만 flag를 복구한다. */
            if (groundInventory.getItem(targetItem.getId()) == targetItem) {
                targetItem.setGiveItem(oldGiveItem);
            }

            ActionAccess.targetItem(creature, null);
            ActionAccess.sleep(creature, INVALID_TARGET_SLEEP_MS);
            return;
        }

        creature.onGetItem(item);
        ActionAccess.targetItem(creature, null);
        ActionAccess.sleep(creature, PICKUP_SLEEP_MS);
    }

    private static void softBlocked(final L1NpcInstance creature) {
        final int stuck = ActionAccess.incrementMoveStuck(creature);

        if (stuck >= SOFT_BLOCK_GIVEUP) {
            abandonTarget(creature, INVALID_TARGET_SLEEP_MS);
            return;
        }

        ActionAccess.sleep(
                creature,
                RETRY_SLEEP_MS
                        + (stuck * 20L)
                        + (creature.getId() & 0x1f));
    }

    private static void abandonTarget(
            final L1NpcInstance creature,
            final long sleepMs) {

        ActionAccess.targetItem(creature, null);
        clearMoveState(creature);
        ActionAccess.sleep(creature, sleepMs);
    }

    private static void clearMoveState(final L1NpcInstance creature) {
        ActionAccess.resetMoveBlockedState(creature);
        CMoveAction.invalidatePath(creature);
    }

    private static boolean isValidTargetItem(
            final L1NpcInstance creature,
            final L1ItemInstance item) {

        if (creature == null
                || item == null
                || item.getMapId() != creature.getMapId()
                || chebyshev(
                        creature.getX(),
                        creature.getY(),
                        item.getX(),
                        item.getY()) > MAX_TARGET_DISTANCE) {
            return false;
        }

        final L1Inventory groundInventory =
                L1World.getInstance().findGroundInventory(
                        item.getX(),
                        item.getY(),
                        item.getMapId());

        /* itemId가 아니라 exact objectId/object identity를 확인한다. */
        return groundInventory != null && groundInventory.getItem(item.getId()) == item;
    }

    private static int chebyshev(
            final int x0,
            final int y0,
            final int x1,
            final int y1) {
        return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    }
}
