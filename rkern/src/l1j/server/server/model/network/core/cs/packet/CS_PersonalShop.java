package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_PersonalShop {
    private CS_PersonalShop() {}

    public static void handle(CUserSocket client, int type, byte[] data) {
        User.OnPrivateStorePacket(client, type, data);
    }
}
