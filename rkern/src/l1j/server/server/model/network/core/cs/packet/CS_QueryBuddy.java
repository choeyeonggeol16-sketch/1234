package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CFriendsDb;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_Buddy;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_QueryBuddy {
    private CS_QueryBuddy() {}
    public static void handle(CUserSocket client) {
        if (client == null) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;
        CFriendsDb.getInstance().MakeFriendList(pc.getId(), buddy -> {
            if (client.isConnected() && client.getActiveChar() == pc && pc.getNetConnection() == client)
                SC_Buddy.send(client, buddy);
        }, () -> {
            if (client.isConnected() && client.getActiveChar() == pc && pc.getNetConnection() == client)
                SC_SystemMessage.send(pc, "친구 목록을 불러오지 못했습니다.");
        });
    }
}
