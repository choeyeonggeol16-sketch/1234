package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_CheckPk {

    private CS_CheckPk() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null) {
            return;
        }

        SC_ServerMessage.send(player, 562, String.valueOf(
                                player.get_PKcount()));
    }
}