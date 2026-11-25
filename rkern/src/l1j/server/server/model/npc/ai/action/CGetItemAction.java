package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.npc.ai.CDesireFactory;
import l1j.server.server.model.npc.ai.CDesirePQ;
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
    // L1 policy: outrank ordinary wandering (5), never an active combat target.
    private static final float GET_ITEM_VALUE = 6.0f;
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
        if (creature instanceof L1MonsterInstance monster) {
            final L1ItemInstance item = targetItem(monster);
            if (item != null) {
                CDesireFactory.MakeGetItem(monster, item, GET_ITEM_VALUE,
                        System.currentTimeMillis());
            }
            CDesirePQ.SetAsGetItem(monster, CDesirePQ.getItemDesire(monster));
        }
    }

    public static void onStart(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        clearMoveState(creature);
    }

    public static byte onTick(final L1NpcInstance creature) {
        if (creature instanceof L1MonsterInstance monster) {
            final L1ItemInstance captured = CDesirePQ.getItemAction(monster);
            if (captured == null) return CCreatureController.RESULT_DONE;
            if (CDesirePQ.topState(monster, System.currentTimeMillis())
                    != CDesirePQ.PS_GET_ITEM) return CCreatureController.RESULT_DONE;
            return dGetItem.dGetItem(monster, captured);
        }
        return dGetItem.dGetItem(creature, creature != null ? targetItem(creature) : null);
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
        final CInventory inventory = npc.getInventory();
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
                        || inventory.checkAddItem(item, item.getCount()) != CInventory.OK
                        || ground.getItem(item.getId()) != item) continue;
                found = item;
                nearest = distance;
            }
            if (nearest == 0) break;
        }
        if (found == null || !canSearchFromAir(npc) || !isValidTargetItem(npc, found)
                || !npc.tryLandFromAir()) return;
        selectItem(npc, found, System.currentTimeMillis());
        CNpcAI.broadcastAirLanding(npc);
    }

    /** Existing owner cadence, including MOVE_AROUND, admits one GET_ITEM key. */
    static boolean prepareMonsterItem(final L1MonsterInstance npc, final long nowMs) {
        if (npc.isDead() || npc.isDestroyed() || npc.getCurrentHp() <= 0
                || npc.getTarget() != null || npc._desireBlockedAttackTargetId != 0) return false;
        L1ItemInstance item = targetItem(npc);
        if (!npc.isPickupItem() && !isAirFood(item)) {
            if (item != null) retireItem(npc, item);
            return false;
        }
        tickSearchCooldown(npc);
        checkTarget(npc);
        if (targetItem(npc) == null && npc.isPickupItem()) {
            npc.searchTargetItem();
        }
        item = targetItem(npc);
        return item != null && CDesireFactory.MakeGetItem(npc, item, GET_ITEM_VALUE, nowMs)
                && CDesirePQ.topState(npc, nowMs) == CDesirePQ.PS_GET_ITEM;
    }

    private static void selectItem(final L1NpcInstance npc,
            final L1ItemInstance item, final long nowMs) {
        if (npc instanceof L1MonsterInstance monster) {
            CDesireFactory.MakeGetItem(monster, item, GET_ITEM_VALUE, nowMs);
        } else {
            targetItem(npc, item);
        }
    }

    static void retireItem(final L1NpcInstance npc, final L1ItemInstance item) {
        if (npc instanceof L1MonsterInstance monster) CDesirePQ.RemoveGetItem(monster, item);
        if (targetItem(npc) == item) targetItem(npc, null);
    }

    static void onInterrupted(final L1NpcInstance npc) {
        if (npc instanceof L1MonsterInstance monster) CDesirePQ.SetAsGetItem(monster, null);
        clearMoveState(npc);
    }

    public static void tickSearchCooldown(final L1NpcInstance creature) {
        final int cooldown = CGetItemAction.itemSearchCooldown(creature);
        if (cooldown > 0) {
            CGetItemAction.itemSearchCooldown(creature, cooldown - 1);
        }
    }

    public static void checkTarget(final L1NpcInstance creature) {
        final L1ItemInstance targetItem = CGetItemAction.targetItem(creature);
        if (targetItem == null) {
            return;
        }

        if (!isValidTargetItem(creature, targetItem)
                && CGetItemAction.targetItem(creature) == targetItem) {
            retireItem(creature, targetItem);
            if (CCreatureController.currentAction(creature)
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
                || CGetItemAction.itemSearchCooldown(creature) > 0) {
            return;
        }

        CGetItemAction.itemSearchCooldown(creature, SEARCH_COOLDOWN_TICKS);

        final CInventory inventory = creature.getInventory();
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
                        || item.getCount() <= 0
                        || ground.getItem(item.getId()) != item
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
                        item.getCount()) != CInventory.OK) {
                    continue;
                }

                nearest = item;
                nearestDistance = distance;

                if (distance == 0) {
                    selectItem(creature, nearest, System.currentTimeMillis());
                    return;
                }
            }
        }

        if (nearest != null) {
            selectItem(creature, nearest, System.currentTimeMillis());
        }
    }

    /**
     * 같은 tile이면 줍고, 아니면 현재 NPC path/collision 계층으로 한 칸 접근한다.
     */
    public static void onTargetItem(final L1NpcInstance creature) {
        final L1ItemInstance targetItem =
                CGetItemAction.targetItem(creature);

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
            if (CGetItemAction.targetItem(creature) == targetItem) {
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

        // Monster GET_ITEM uses the same NC-style point/segment mover as
        // scripted movement. Pickup adjacency is checked before every step.
        // Keep pet/companion's established command movement unchanged.
        if (creature instanceof L1MonsterInstance) {
            final int oldX = creature.getX(), oldY = creature.getY();
            final byte move = SetMoving.tickToPoint(creature,
                    targetItem.getX(), targetItem.getY());
            if (targetItem(creature) != targetItem) return;
            switch (move) {
            case SetMoving.RUNNING:
                if (creature.getX() != oldX || creature.getY() != oldY)
                    CMoveAction.moveStuck(creature, 0);
                return;
            case SetMoving.AT_DISTANCE:
                creature.pickupTargetItem(targetItem);
                return;
            case SetMoving.DYNAMIC_BLOCKED:
                softBlocked(creature);
                return;
            default:
                abandonTarget(creature, INVALID_TARGET_SLEEP_MS);
                return;
            }
        }

        final int dir =
                CMoveAction.direction(
                        creature,
                        targetItem.getX(),
                        targetItem.getY());

        if (dir >= 0 && dir < 8) {
            if (CGetItemAction.targetItem(creature) != targetItem) {
                return;
            }
            if (CMoveAction.moveOneStep(
                    creature,
                    dir)) {

                CMoveAction.moveStuck(
                        creature,
                        0);

                return;
            }

            softBlocked(creature);
            return;
        }

        switch (dir) {
        case CMoveAction.DIR_RETRY:
            CCreatureController.sleep(
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
                || CGetItemAction.targetItem(creature) != targetItem) {
            return;
        }

        // The candidate can become forbidden (owner changed / pet item)
        // between discovery and pickup. Never bypass the L1 restriction.
        if (!isAllowedItem(creature, targetItem)) {
            dGetItem.GetItemFinished(creature, targetItem, null);
            return;
        }

        final CInventory groundInventory =
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

        final CInventory destination = creature.getInventory();
        if (destination == null) {
            dGetItem.GetItemFinished(creature, targetItem, null);
            return;
        }

        final int acquiredCount;
        final L1ItemInstance transferred;
        synchronized (groundInventory) {
            if (groundInventory.getItem(targetItem.getId()) != targetItem
                    || !isAllowedItem(creature, targetItem)) {
                acquiredCount = 0;
                transferred = null;
            } else {
                acquiredCount = targetItem.getCount();
                final boolean oldGiveItem = targetItem.isGiveItem();
                try {
                    targetItem.setGiveItem(true);
                    transferred = groundInventory.tradeItem(targetItem, acquiredCount, destination);
                } catch (IllegalStateException pendingTransfer) {
                    if (groundInventory.getItem(targetItem.getId()) == targetItem)
                        targetItem.setGiveItem(oldGiveItem);
                    if (!"Item mutation refused while CacheD transfer pending".equals(pendingTransfer.getMessage()))
                        throw pendingTransfer;
                    CCreatureController.sleep(creature, INVALID_TARGET_SLEEP_MS);
                    return;
                } catch (RuntimeException | Error failure) {
                    if (groundInventory.getItem(targetItem.getId()) == targetItem)
                        targetItem.setGiveItem(oldGiveItem);
                    throw failure;
                }
                if (transferred == null && groundInventory.getItem(targetItem.getId()) == targetItem)
                    targetItem.setGiveItem(oldGiveItem);
            }
        }

        // Synchronous L1 commit now supplies NC GetItemFinished outcome.
        // onGetItem only runs after successful inventory transfer.
        dGetItem.GetItemFinished(creature, targetItem, transferred, acquiredCount);
    }

    private static void softBlocked(final L1NpcInstance creature) {
        final int stuck = CMoveAction.incrementMoveStuck(creature);

        if (stuck >= SOFT_BLOCK_GIVEUP) {
            abandonTarget(creature, INVALID_TARGET_SLEEP_MS);
            return;
        }

        CCreatureController.sleep(
                creature,
                RETRY_SLEEP_MS
                        + (stuck * 20L)
                        + (creature.getId() & 0x1f));
    }

    private static void abandonTarget(
            final L1NpcInstance creature,
            final long sleepMs) {

        retireItem(creature, targetItem(creature));
        clearMoveState(creature);
        CCreatureController.sleep(creature, sleepMs);
    }

    private static void clearMoveState(final L1NpcInstance creature) {
        if (creature instanceof L1MonsterInstance) SetMoving.clear(creature);
        CMoveAction.resetMoveBlockedState(creature);
        CMoveAction.invalidatePath(creature);
    }

    static boolean isValidTargetItem(
            final L1NpcInstance creature,
            final L1ItemInstance item) {

        if (creature == null
                || item == null || item.getCount() <= 0
                || !isAllowedItem(creature, item)
                || item.getMapId() != creature.getMapId()
                || chebyshev(
                        creature.getX(),
                        creature.getY(),
                        item.getX(),
                        item.getY()) > MAX_TARGET_DISTANCE) {
            return false;
        }

        final CInventory groundInventory =
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
        if (!item.TryPickContributedItem(creature)) return false;
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
}
