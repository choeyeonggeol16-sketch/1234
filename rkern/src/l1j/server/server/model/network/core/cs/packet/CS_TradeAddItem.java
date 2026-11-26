package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.CTrade;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_TradeAddItem {
    private CS_TradeAddItem() {}
    public static void handle(CUserSocket client, int itemObjectId, int requestedCount) {
        if (client == null) return;
        L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.getNetConnection() != client) return;
        CTrade trade = pc.GetTrade();
        if (trade != null) trade.RequestAddItems(pc, itemObjectId, requestedCount);
    }
}
