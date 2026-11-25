package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.CUserInventory;

/** Legacy normal-return charge, last-used and item-delay tail. Early exits never call this. */
final class ItemActionCompletion {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ItemActionCompletion.class.getName());
    private ItemActionCompletion() {}

    // The inventory method first checks the transfer gate, then only updates
    // packet presentation for these columns. Preserve gate failures, but a
    // disconnected client must not cancel already submitted persistence.
    private static void notifyItemState(L1PcInstance pc, L1ItemInstance item, int column) {
        try {
            pc.getInventory().updateItem(item, column);
        } catch (RuntimeException notificationFailure) {
            if (notificationFailure instanceof IllegalStateException
                    && "Item mutation refused while CacheD transfer pending".equals(notificationFailure.getMessage()))
                throw notificationFailure;
            LOG.log(java.util.logging.Level.WARNING,
                    "Item completion notification failed", notificationFailure);
        }
    }

    /** Called only by the committed-item callback; never touch a debit image
     * while CacheD is still verifying its preimage. Removed last units have no
     * surviving inventory row on which to persist a timestamp.
     */
    static void finishCommittedConsumable(L1PcInstance pc, L1ItemInstance input) {
        if (input.getItem().getType2() == 0
                && ((l1j.server.server.templates.L1EtcItem) input.getItem()).get_delayEffect() > 0) {
            L1ItemInstance live = pc.getInventory().GetByServerID(input.getId());
            if (live == input) {
                final Timestamp oldLastUsed = live.getLastUsed();
                live.setLastUsed(new Timestamp(System.currentTimeMillis()));
                try {
                    pc.getInventory().saveItem(live, CUserInventory.COL_DELAY_EFFECT);
                } catch (RuntimeException submissionFailure) {
                    live.setLastUsed(oldLastUsed);
                    throw submissionFailure;
                }
                notifyItemState(pc, live, CUserInventory.COL_DELAY_EFFECT);
            }
        }
        L1ItemDelay.onItemUse(pc, input);
    }

    static void finish(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        boolean isDelayEffect = use.delayEffect();
        // Legacy handlers may have removed the last unit before returning
        // COMPLETE. Never dirty a deleted inventory row; keep the use delay.
        if (isDelayEffect && pc.getInventory().GetByServerID(item.getId()) == item) {
            if (itemId == 410008 || itemId == 40414 || itemId == 700012 || itemId == 30043 || itemId == 30045
                     || itemId == 30026 || itemId == 4100131 || itemId == 4100132) {
                final int chargeCount = item.getChargeCount();
                if (chargeCount <= 1) {
                    // Do not mutate the live charge if deletion is rejected.
                    if (pc.getInventory().DeleteItemAmount(item, 1) != 1) return;
                    item.setChargeCount(0);
                } else {
                    item.setChargeCount(chargeCount - 1);
                    final Timestamp oldLastUsed = item.getLastUsed();
                    item.setLastUsed(new Timestamp(System.currentTimeMillis()));
                    try {
                        // Both fields changed. Persisting just the charge count
                        // silently lost the updated last-use timestamp.
                        pc.getInventory().saveItem(item,
                                CUserInventory.COL_CHARGE_COUNT | CUserInventory.COL_DELAY_EFFECT);
                    } catch (RuntimeException submissionFailure) {
                        item.setChargeCount(chargeCount);
                        item.setLastUsed(oldLastUsed);
                        throw submissionFailure;
                    }
                    notifyItemState(pc, item, CUserInventory.COL_CHARGE_COUNT);
                }
            } else {
                final Timestamp oldLastUsed = item.getLastUsed();
                item.setLastUsed(new Timestamp(System.currentTimeMillis()));
                try {
                    pc.getInventory().saveItem(item, CUserInventory.COL_DELAY_EFFECT);
                } catch (RuntimeException submissionFailure) {
                    item.setLastUsed(oldLastUsed);
                    throw submissionFailure;
                }
                notifyItemState(pc, item, CUserInventory.COL_DELAY_EFFECT);
            }
        }
        L1ItemDelay.onItemUse(pc, item);
    }
}
