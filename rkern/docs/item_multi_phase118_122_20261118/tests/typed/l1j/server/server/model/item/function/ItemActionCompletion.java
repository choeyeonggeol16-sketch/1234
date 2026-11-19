package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.L1PcInventory;

/** Legacy normal-return charge, last-used and item-delay tail. Early exits never call this. */
final class ItemActionCompletion {
    private ItemActionCompletion() {}

    /** Called only by the committed-item callback; never touch a debit image
     * while CacheD is still verifying its preimage. Removed last units have no
     * surviving inventory row on which to persist a timestamp.
     */
    static void finishCommittedConsumable(L1PcInstance pc, L1ItemInstance input) {
        if (input.getItem().getType2() == 0
                && ((l1j.server.server.templates.L1EtcItem) input.getItem()).get_delayEffect() > 0) {
            L1ItemInstance live = pc.getInventory().getItem(input.getId());
            if (live == input) {
                live.setLastUsed(new Timestamp(System.currentTimeMillis()));
                pc.getInventory().updateItem(live, L1PcInventory.COL_DELAY_EFFECT);
                pc.getInventory().saveItem(live, L1PcInventory.COL_DELAY_EFFECT);
            }
        }
        L1ItemDelay.onItemUse(pc, input);
    }

    static void finish(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        boolean isDelayEffect = use.delayEffect();
        if (isDelayEffect) {
            if (itemId == 410008 || itemId == 40414 || itemId == 700012 || itemId == 30043 || itemId == 30045
                     || itemId == 30026 || itemId == 4100131 || itemId == 4100132) {
                final int chargeCount = item.getChargeCount();
                item.setChargeCount(chargeCount - 1);
                if (chargeCount <= 1) {
                    // The last charge removes the item: no timestamp is stored.
                    pc.getInventory().removeItem(item, 1);
                } else {
                    item.setLastUsed(new Timestamp(System.currentTimeMillis()));
                    pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
                    pc.getInventory().saveItem(item, L1PcInventory.COL_CHARGE_COUNT);
                }
            } else {
                Timestamp ts = new Timestamp(System.currentTimeMillis());
                item.setLastUsed(ts);
                pc.getInventory().updateItem(item, L1PcInventory.COL_DELAY_EFFECT);
                pc.getInventory().saveItem(item, L1PcInventory.COL_DELAY_EFFECT);
            }
        }
        L1ItemDelay.onItemUse(pc, item);
    }
}
