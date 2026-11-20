package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_ReturnSummon {

    private CS_ReturnSummon() {
    }

    public static void handle(CUserSocket client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        // A late/unsolicited client ACK must not reuse an old teleport destination.
        if (pc == null || !pc.get_teleport()) {
            return;
        }

        if (!pc.tpThreadDelay(200)) {
            pc.tpEndFinish();
        }
    }
}
