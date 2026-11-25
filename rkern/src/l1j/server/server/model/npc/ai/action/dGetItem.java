package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * NC CNPC::dGetItem (L2NPC.exe 0x0046bf30), adapted to L1.
 *
 * Native dGetItem sends the pickup request to L2Server (0x17) and later
 * removes the exact GET_ITEM desire and publishes GetItemFinished (0x3c).
 * L1 already owns the pickup request and transaction in CGetItemAction /
 * CInventory.tradeItem; the CIO owner receives the transfer result
 * synchronously and completes the same captured item without a second
 * event queue, worker, item allocation or speculative DB mutation.
 */
public final class dGetItem {
    private static final long FINISHED_MS = 1_000L;
    private static final long FAILED_MS = 500L;

    private dGetItem() {
    }

    /** One native GET_ITEM desire execution on the existing CIO owner. */
    public static byte dGetItem(
            final L1NpcInstance creature,
            final L1ItemInstance capturedItem) {
        if (creature == null) {
            return CCreatureController.RESULT_INVALID;
        }
        if (capturedItem == null
                || CGetItemAction.targetItem(creature) != capturedItem) {
            return CCreatureController.RESULT_DONE;
        }
        if (creature.isDead() || creature.getCurrentHp() <= 0) {
            // NC dGetItem drops pending desires when its actor becomes dead
            // or inactive. Retire only the captured L1 item request.
            CGetItemAction.retireItem(creature, capturedItem);
            return CCreatureController.RESULT_INVALID;
        }
        if (!CGetItemAction.isValidTargetItem(creature, capturedItem)) {
            GetItemFinished(creature, capturedItem, null);
            return CCreatureController.RESULT_DONE;
        }

        // Retains the original L1 virtual hook and path/occupancy checks.
        // A successful transfer invokes GetItemFinished inside pickup().
        creature.onTargetItem();

        // A replaced target is a different desire; do not keep running A.
        return CGetItemAction.targetItem(creature) == capturedItem
                ? CCreatureController.RESULT_RUNNING
                : CCreatureController.RESULT_DONE;
    }

    /**
     * NC GetItemFinished adaptation. A non-null transferred item means that
     * the authoritative inventory transfer committed and the L1 onGetItem
     * callback may now run; null means failure/item disappeared.
     *
     * Crucially, completing item A never erases the newly selected item B.
     */
    public static void GetItemFinished(
            final L1NpcInstance creature,
            final L1ItemInstance requested,
            final L1ItemInstance transferred) {
        GetItemFinished(creature, requested, transferred,
                transferred != null ? transferred.getCount() : 0);
    }

    public static void GetItemFinished(
            final L1NpcInstance creature,
            final L1ItemInstance requested,
            final L1ItemInstance transferred,
            final int acquiredCount) {
        if (creature == null) return;
        try {
            if (transferred != null) {
                creature.onGetItem(transferred, acquiredCount);
            }
        } finally {
            if (requested != null) CGetItemAction.retireItem(creature, requested);
            CCreatureController.sleep(creature,
                    transferred == null ? FAILED_MS : FINISHED_MS);
        }
    }
}
