package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_ReadNews {

    private CS_ReadNews() {
    }

    public static void handle(
            CUserSocket client) {

        if (client == null) {
            return;
        }

        CS_AuthLogin.sendNotice(
                client,
                client.getAccountName());
    }
}