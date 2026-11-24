package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 바닥 아이템 획득 action.
 *
 * target item / search cooldown은 L1NpcInstance primitive state를 사용한다.
 * 이동은 현재 NPC PathFinder/WorldCollision 경로를 그대로 탄다.
 */
public final class CGetItemAction {

    private static final int SEARCH_COOLDOWN_TICKS = 20;
    private static final int MAX_TARGET_DISTANCE = 15;
    private static final int AIR_FOOD_RADIUS = 4;
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
        return dGetItem.dGetItem(creature,
                creature != null ? ActionAccess.targetItem(creature) : null);
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

    public static boolean canSearchFromAir(final L1NpcInstance npc) {
        return npc != null && !npc.isDestroyed() && !npc.isDead()
                && npc.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_FLY
                && npc.getCurrentHp() > 0 && npc.getCurrentHp() < npc.getMaxHp();
    }

    private static boolean isAirFood(final L1ItemInstance item) {
        if (item == null || item.getItem() == null) return false;
        final int type = item.getItem().getType();
        return type == 6 || type == 7;
    }

    /** L1 feeding condition; owner-side selection of an explicit GET_ITEM target.
     * NC MakeGetItem/SetAsGetItem provides the action split, not this L1 food rule.
     */
    static void searchItemFromAir(final L1NpcInstance npc) {
        if (!canSearchFromAir(npc)) return;
        final L1Inventory inventory = npc.getInventory();
        if (inventory == null) return;
        L1ItemInstance found = null;
        int nearest = AIR_FOOD_RADIUS + 1;
        for (L1Object obj : L1World.getInstance().getClusterGrid()
                .queryNearbyReusable(npc, AIR_FOOD_RADIUS)) {
            if (!(obj instanceof L1GroundInventory)) continue;
            final L1GroundInventory ground = (L1GroundInventory) obj;
            for (L1ItemInstance item : ground.getItems()) {
                if (!isAirFood(item) || !isAllowedItem(npc, item)
                        || item.getMapId() != npc.getMapId() || item.getCount() <= 0) continue;
                final int distance = chebyshev(npc.getX(), npc.getY(), item.getX(), item.getY());
                if (distance >= nearest || distance > AIR_FOOD_RADIUS
                        || inventory.checkAddItem(item, item.getCount()) != L1Inventory.OK
                        || ground.getItem(item.getId()) != item) continue;
                found = item;
                nearest = distance;
            }
            if (nearest == 0) break;
        }
        if (found == null || !canSearchFromAir(npc) || !isValidTargetItem(npc, found)
                || !npc.tryLandFromAir()) return;
        ActionAccess.targetItem(npc, found);
        CNpcAI.broadcastAirLanding(npc);
    }

    /** Explicit feeding target must be considered before autonomous roaming. */
    static boolean hasSelectedFood(final L1NpcInstance npc) {
        final L1ItemInstance item = ActionAccess.targetItem(npc);
        if (!isAirFood(item)) return false;
        if (isValidTargetItem(npc, item)) return true;
        if (ActionAccess.targetItem(npc) == item) ActionAccess.targetItem(npc, null);
        return false;
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

        if (!isValidTargetItem(creature, targetItem)
                && ActionAccess.targetItem(creature) == targetItem) {
            ActionAccess.targetItem(creature, null);
            if (ActionAccess.currentAction(creature)
                    == CCreatureController.ACTION_GET_ITEM) {
                clearMoveState(creature);
            }
        }
    }

    /**
     * 범위 안의 바닥 아이템 중 실제로 들 수 있는 가장 가까운 아이템을 고른다.
     * 화면 전체(-1) 첫 항목을 잡지 않으므로 멀리 있는 아이템 때문에 가까운 아이템을 놓치지 않는다.
     */
    public static void searchTarget(final L1NpcInstance creature) {
        searchTarget(creature, MAX_TARGET_DISTANCE);
    }

    /**
     * Shared GET_ITEM candidate selection. Pet and companion command scans
     * keep their original ten-tile radius and owner/item restrictions.
     * Uses the existing reusable cluster-grid iteration (no temporary list).
     */
    public static void searchTarget(
            final L1NpcInstance creature, final int maxDistance) {
        if (creature == null || maxDistance <= 0
                || ActionAccess.itemSearchCooldown(creature) > 0) {
            return;
        }

        ActionAccess.itemSearchCooldown(creature, SEARCH_COOLDOWN_TICKS);

        final L1Inventory inventory = creature.getInventory();
        if (inventory == null) {
            return;
        }

        L1ItemInstance nearest = null;
        int nearestDistance = maxDistance + 1;

        for (L1Object obj : L1World.getInstance()
                .getClusterGrid()
                .queryNearbyReusable(
                        creature,
                        maxDistance)) {

            if (!(obj instanceof L1GroundInventory)) {
                continue;
            }

            final L1GroundInventory ground = (L1GroundInventory) obj;
            if (ground.getSize() <= 0) {
                continue;
            }

            for (L1ItemInstance item : ground.getItems()) {
                if (item == null
                        || item.getMapId() != creature.getMapId()
                        || !isAllowedItem(creature, item)) {
                    continue;
                }

                final int distance = chebyshev(
                        creature.getX(),
                        creature.getY(),
                        item.getX(),
                        item.getY());

                if (distance > maxDistance
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

        if (targetItem == null
                || !isAllowedItem(creature, targetItem)) {
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
            if (ActionAccess.targetItem(creature) == targetItem) {
                creature.pickupTargetItem(targetItem);
            }
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
            if (ActionAccess.targetItem(creature) != targetItem) {
                return;
            }
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

        if (creature == null || targetItem == null
                || ActionAccess.targetItem(creature) != targetItem) {
            return;
        }

        // The candidate can become forbidden (owner changed / pet item)
        // between discovery and pickup. Never bypass the L1 restriction.
        if (!isAllowedItem(creature, targetItem)) {
            dGetItem.GetItemFinished(creature, targetItem, null);
            return;
        }

        final L1Inventory groundInventory =
                L1World.getInstance().findGroundInventory(
                        targetItem.getX(),
                        targetItem.getY(),
                        targetItem.getMapId());

        // NC dGetItem completes only its captured GET_ITEM key. A stale
        // pointer must never consume a replacement object with the same ID.
        if (groundInventory == null
                || groundInventory.getItem(targetItem.getId()) != targetItem) {
            dGetItem.GetItemFinished(creature, targetItem, null);
            return;
        }

        final L1Inventory destination = creature.getInventory();
        if (destination == null) {
            dGetItem.GetItemFinished(creature, targetItem, null);
            return;
        }

        final boolean oldGiveItem = targetItem.isGiveItem();
        final L1ItemInstance transferred;
        try {
            targetItem.setGiveItem(true);
            // Existing locked L1Inventory transfer rechecks exact source
            // identity, capacity and CacheD pending-transaction gates.
            transferred = groundInventory.tradeItem(
                    targetItem, targetItem.getCount(), destination);
        } catch (IllegalStateException pendingTransfer) {
            if (groundInventory.getItem(targetItem.getId()) == targetItem) {
                targetItem.setGiveItem(oldGiveItem);
            }
            // Only the known pre-COMMIT CacheD admission rejection is
            // retriable. Propagate unexpected transaction-state failures;
            // swallowing them could conceal a partial item mutation.
            if (!"Item mutation refused while CacheD transfer pending"
                    .equals(pendingTransfer.getMessage())) {
                throw pendingTransfer;
            }
            ActionAccess.sleep(creature, INVALID_TARGET_SLEEP_MS);
            return;
        } catch (RuntimeException | Error failure) {
            if (groundInventory.getItem(targetItem.getId()) == targetItem) {
                targetItem.setGiveItem(oldGiveItem);
            }
            throw failure;
        }

        if (transferred == null
                && groundInventory.getItem(targetItem.getId()) == targetItem) {
            targetItem.setGiveItem(oldGiveItem);
        }

        // Synchronous L1 commit now supplies NC GetItemFinished outcome.
        // onGetItem only runs after successful inventory transfer.
        dGetItem.GetItemFinished(creature, targetItem, transferred);
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

    static boolean isValidTargetItem(
            final L1NpcInstance creature,
            final L1ItemInstance item) {

        if (creature == null
                || item == null
                || !isAllowedItem(creature, item)
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

    /** L1 pet item restrictions and companion's owner-only ground loot. */
    static boolean isAllowedItem(
            final L1NpcInstance creature, final L1ItemInstance item) {
        if (creature == null || item == null || item.getItem() == null) {
            return false;
        }
        if (creature instanceof L1PetInstance
                && item.getItem().isUseHighPet()) {
            return false;
        }
        if (creature instanceof L1CompanionInstance) {
            final L1PcInstance master =
                    ((L1CompanionInstance) creature).get_master();
            if (master == null) return false;
            final L1PcInstance owner = item.getItemOwner();
            return owner == null || owner.getId() == master.getId();
        }
        return true;
    }

    private static int chebyshev(
            final int x0,
            final int y0,
            final int x1,
            final int y1) {
        return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    }
}
