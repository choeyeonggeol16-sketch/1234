package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.session.CUserSession;

public final class CS_ReadNews {

    private CS_ReadNews() {
    }

    public static void handle(
            CUserSession client) {

        if (client == null) {
            return;
        }

        CS_AuthLogin.sendNotice(
                client,
                client.getAccountName());
    }
}