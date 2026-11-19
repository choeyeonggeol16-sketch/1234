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
final class ResolventPairRules {
    private ResolventPairRules() {}

    static boolean validResolventPair(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent, int expectedId) {
        if (pc == null) return false;
        if (target == null || solvent == null) {
            SC_ServerMessage.send(pc, 79);
            return false;
        }
        if (solvent.getItemId() != expectedId || target == solvent) {
            SC_ServerMessage.send(pc, 1161);
            return false;
        }
        return true;
    }
}
