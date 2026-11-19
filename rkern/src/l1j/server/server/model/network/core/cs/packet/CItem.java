package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CItem {
    private CItem() {}

    static boolean CheckItemUseCondition(L1PcInstance pc, L1ItemInstance item) {
        if (item == null || item.getItem() == null || item.getCount() <= 0
                || pc.getInventory().getItem(item.getId()) != item) {
            return false;
        }
        if (item.getItem().getUseType() == -1) {
            SC_ServerMessage.send(pc, 74, item.getLogName());
            return false;
        }
        return true;
    }
}
