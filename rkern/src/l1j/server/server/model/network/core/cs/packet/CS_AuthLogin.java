package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.authd.CAuthD;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_AuthLogin {
    private CS_AuthLogin() { }

    public static void handle(CUserSocket client, String accountName, String password) {
        CAuthD.requestLogin(client, accountName, password, CS_ReadNews::handle);
    }
}
