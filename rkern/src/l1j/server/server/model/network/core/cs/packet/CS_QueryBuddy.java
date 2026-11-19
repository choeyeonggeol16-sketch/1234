package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.BuddyTable;
import l1j.server.server.model.L1Buddy;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Buddy;

public final class CS_QueryBuddy {

    private CS_QueryBuddy() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1Buddy buddy =
                BuddyTable
                        .getInstance()
                        .getBuddyTable(
                                pc.getId());

        SC_Buddy.send(pc, buddy);
    }
}