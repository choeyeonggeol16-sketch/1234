package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

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
