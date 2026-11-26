package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;

public final class CDB {
    private CDB() {}

    // The existing transaction writes through CacheD. Its callback is invoked
    // after durable commit and inventory replay on the original CIO owner.
    public static void RequestUseItem(L1PcInstance pc, L1InventoryTransaction tx,
            Runnable afterCommitted) {
        // Effects can unequip gear, start a new receipt or save character
        // progression. Run them on the validated CIO owner only AFTER both the
        // inventory reservation and CacheD's owner-apply barrier are retired.
        tx.commitAfterInventoryRelease(() -> User.OnReplyUseItemPacket(pc, afterCommitted));
    }
}
