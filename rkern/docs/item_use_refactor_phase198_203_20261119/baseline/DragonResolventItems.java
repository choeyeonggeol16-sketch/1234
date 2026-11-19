package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.SpecialConsumableRouter.nowTimestamp;

/** Domain-specific item handler; original transaction ordering preserved. */
final class DragonResolventItems {
    private DragonResolventItems() {}

    static void useDragonResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        if (!ResolventPairRules.validResolventPair(pc, target, solvent, 442455)) return;
        if (target.getItemId() < 4000138 || target.getItemId() > 4000144) return;
        final int count = ResolventTable.getInstance().getCrystalCount(target.getItemId());
        if (count <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        ResolventSettlement.settleResolvent(pc, target, solvent, 40000155, count, 0, false);
    }
}
