package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Item-specific effect extracted without changing its original execution body. */
final class LanternOilReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    private LanternOilReceipt() {}

    static void useDurableLanternOil(L1PcInstance pc, L1ItemInstance oil) {
        if (pc == null || oil == null || pc.getInventory().GetByServerID(oil.getId()) != oil) return;
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(oil, 1)) return;
            final L1ItemInstance lamp = pc.getInventory().GetFirstItemByClassIDs(40002, 7005);
            if (lamp != null) tx.changeRemainingTime(lamp, oil.getItem().getLightFuel());
            tx.onFailure(() -> SC_SystemMessage.send(pc, "랜턴오일 사용 내역을 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (lamp != null) {
                    SC_ItemName.send(pc, lamp);
                    SC_ServerMessage.send(pc, 230);
                }
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Lantern oil prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "랜턴오일을 사용하지 못했습니다.");
        }
    }
}
