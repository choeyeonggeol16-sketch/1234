package l1j.server.server.model.item.function;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.WeaponTransformationTables.*;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class LifeEyeCraftReceipt {
    private LifeEyeCraftReceipt() {}

    static void craftLifeEye(L1PcInstance pc, L1ItemInstance fragment) {
        if (pc == null || fragment == null || fragment.getItemId() != 3000150
                || pc.getInventory().getItem(fragment.getId()) != fragment) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, fragment.getId())) {
            if (!tx.debit(3000150, 100)) {
                SC_SystemMessage.send(pc, "생명의마안 조각 이 부족합니다.");
                return;
            }
            if (tx.grant(410038, 1) == null) {
                SC_SystemMessage.send(pc, "생명의 마안을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "생명의 마안 제작이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() ->
                    l1j.server.server.model.network.core.sc.packet.SC_ChatPacket.sendDirect(
                            pc, pc, "생명의마안 이 제작되었습니다."));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 제작할 수 없습니다.");
        }
    }
}
