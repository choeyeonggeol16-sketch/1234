package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_BanMember {
    private CS_BanMember() {}

    public static void handle(CUserSocket client, String targetName) {
        L1ClanJoin.BanMember(client, targetName);
    }
}
