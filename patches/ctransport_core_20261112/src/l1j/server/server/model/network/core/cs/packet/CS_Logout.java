package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.Controller.LoginController;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionState;

public final class CS_Logout {

    private CS_Logout() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        try {
            if (client.getActiveChar() != null) {

                client.kick();
                client.close();

                System.out.println(
                        "─────────────────────────────────");

                System.out.println(
                        "버그 의심됨 -- 계정명 "
                                + client.getAccountName());

                System.out.println(
                        "─────────────────────────────────");

                return;
            }
            client.setConnectionState(ConnectionState.CONNECTED);
            LoginController.getInstance().logout(client);

        } catch (Exception ignored) {
        }
    }
}