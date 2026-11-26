package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.CTrade;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_TradeAccept {
    private CS_TradeAccept() {}
    public static void handle(CUserSocket client) {
        if (client == null) return;
        L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.getNetConnection() != client) return;
        CTrade trade = pc.GetTrade();
        if (trade != null) trade.PressDone(pc);
    }
}
