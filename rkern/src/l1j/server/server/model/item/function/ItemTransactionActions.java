package l1j.server.server.model.item.function;

import java.util.logging.Level;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Shared admission, debit and failure handling; effects remain in their
 * original handlers. A prepared debit never changes the live input item.
 */
public final class ItemTransactionActions {
    private ItemTransactionActions() {}

    public static void consume(L1PcInstance pc, L1ItemInstance input, Runnable effect) {
        if (!owned(pc, input)) return;
        consumeInputs(pc, input, null, effect);
    }

    public static void consumeTwo(L1PcInstance pc, L1ItemInstance first,
                                  L1ItemInstance second, Runnable effect) {
        if (first == second || !owned(pc, first) || !owned(pc, second)) return;
        consumeInputs(pc, first, second, effect);
    }

    // A rolled-back receipt has already released its owner gate. A disconnected
    // client's error packet must never turn the confirmed rollback callback
    // itself into another failure.
    static void failureNotice(L1PcInstance pc, String message) {
        try {
            SC_SystemMessage.send(pc, message);
        } catch (RuntimeException notificationFailure) {
            CreatureItemActions.ITEM_USE2_LOG.log(Level.WARNING,
                    "Item rollback notification failed: " + pc.getId(), notificationFailure);
        }
    }

    private static boolean owned(L1PcInstance pc, L1ItemInstance input) {
        return pc != null && input != null && pc.getInventory().GetByServerID(input.getId()) == input;
    }

    private static void consumeInputs(L1PcInstance pc, L1ItemInstance first,
                                      L1ItemInstance second, Runnable effect) {
        try {
            // Older legacy item actions can leave a changed live stack waiting
            // for the periodic inventory save. Submit only the input rows that
            // are actually dirty, BEFORE reserving the economy FIFO owner lane.
            // Their CacheD updates then commit ahead of the receipt's preimage
            // SELECT; this does not run SQL on the CIO/game thread.
            if (first.getRecordingColumns() != 0)
                pc.getInventory().saveItem(first, CUserInventory.COL_SAVE_ALL);
            if (second != null && second.getRecordingColumns() != 0)
                pc.getInventory().saveItem(second, CUserInventory.COL_SAVE_ALL);
        } catch (RuntimeException failure) {
            CreatureItemActions.ITEM_USE2_LOG.log(Level.WARNING,
                    "Item preimage persistence admission failed: " + pc.getId(), failure);
            failureNotice(pc, "아이템 저장 준비에 실패했습니다.");
            return;
        }
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(first, 1) || (second != null && !tx.debit(second, 1))) return;
            tx.onFailure(() -> failureNotice(pc, "재료 소모 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, effect);
        } catch (RuntimeException failure) {
            CreatureItemActions.ITEM_USE2_LOG.log(Level.WARNING,
                    "Item consume prepare failed: " + pc.getId(), failure);
            failureNotice(pc, "아이템 사용을 처리하지 못했습니다.");
        }
    }
}
