package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CFriendsDb;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1CharName;

public final class CS_AddBuddy {
    private CS_AddBuddy() {}

    public static void handle(CUserSocket client, String charName) {
        if (client == null || charName == null || charName.isEmpty()) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.isGhost() || charName.equalsIgnoreCase(pc.getName())) return;
        final L1CharName character = CharacterTable.getInstance().getCharName(charName);
        if (character == null) {
            // Preserve the previous case-insensitive duplicate result.
            if (CFriendsDb.getInstance().GetFriends(pc.getId()).HasFriend(charName))
                SC_ServerMessage.send(pc, 1052, charName);
            else SC_ServerMessage.send(pc, 109, charName);
            return;
        }
        if (character.getId() == pc.getId()) return;
        CFriendsDb.getInstance().EstablishFriendship(pc.getId(), character.getId(), character.getName(), result -> {
            if (!client.isConnected() || client.getActiveChar() != pc || pc.getNetConnection() != client) return;
            if (result == CFriendsDb.DUPLICATE) SC_ServerMessage.send(pc, 1052, charName);
            else if (result == CFriendsDb.FAILED) SC_SystemMessage.send(pc, "친구 등록에 실패했습니다.");
        });
    }
}
