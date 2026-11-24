package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_LeavePledge {
    private CS_LeavePledge() {}

    public static void handle(CUserSocket client) {
        L1ClanJoin.LeavePledge(client);
    }

    /** Existing tutorial/automatic-leave callers retain their entry point. */
    public static void leaveClanMember(L1Clan clan, L1PcInstance player) {
        L1ClanJoin.LeaveMember(clan, player);
    }
}
