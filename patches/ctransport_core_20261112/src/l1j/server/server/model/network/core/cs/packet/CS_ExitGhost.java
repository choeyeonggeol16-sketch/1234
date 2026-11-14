package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_ExitGhost {

    private CS_ExitGhost() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || !pc.isGhost()) {

            return;
        }

        pc.endGhost();
    }
}