package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Emblem;

public final class CS_EmblemRequest {

    private CS_EmblemRequest() {
    }

    public static void handle(
            CIOClient client,
            int emblemId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        SC_Emblem.send(pc, emblemId);
    }
}