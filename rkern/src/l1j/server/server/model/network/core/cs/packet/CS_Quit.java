package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_Quit {

    private CS_Quit() {
    }

    public static void handle(
            CUserSocket client) {

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