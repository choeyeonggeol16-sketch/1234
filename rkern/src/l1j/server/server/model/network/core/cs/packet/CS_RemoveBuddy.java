package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CFriendsDb;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_RemoveBuddy {
    private CS_RemoveBuddy() {}
    public static void handle(CUserSocket client, String charName) {
        if (client == null || charName == null || charName.isEmpty()) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;
        CFriendsDb.getInstance().BreakFriendship(pc.getId(), charName, result -> {
            if (result == CFriendsDb.FAILED && client.isCurrentCharacter(pc))
                SC_SystemMessage.send(pc, "친구 삭제에 실패했습니다.");
        });
    }
}
