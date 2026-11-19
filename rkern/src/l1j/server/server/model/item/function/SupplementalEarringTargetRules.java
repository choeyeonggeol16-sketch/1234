package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** Verify targeted earrings against live inventory identity. */
final class SupplementalEarringTargetRules {
    private SupplementalEarringTargetRules() {}

    static boolean allow(L1PcInstance pc, L1ItemInstance source,
                         L1ItemInstance target, int itemId) {


		// Targeted earrings must be a live inventory object, not a null or stale id.
		if (itemId >= 40925 && itemId <= 40929
				&& (target == null || target.getItem() == null
						|| pc.getInventory().getItem(target.getId()) != target
						|| target == source)) {
			SC_ServerMessage.send(pc, 79);
			return false;
		}
        return true;
    }
}
