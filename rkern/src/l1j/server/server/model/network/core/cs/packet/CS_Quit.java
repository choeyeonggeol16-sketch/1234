package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.session.CUserSession;

public final class CS_Quit {

    private CS_Quit() {
    }

    public static void handle(
            CUserSession client) {

        if (client == null) {
            return;
        }

        try {

            client.close();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}