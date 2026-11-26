package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.inventory.CTrade;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_TradeStatus;

public final class CS_TradeCancel {
    private CS_TradeCancel() {}
    public static void handle(CUserSocket client) {
        if (client == null) return;
        L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.getNetConnection() != client) return;
        CTrade trade = pc.GetTrade();
        if (trade != null) trade.Canceled(pc);
        else SC_TradeStatus.send(client, 1);
    }
}
