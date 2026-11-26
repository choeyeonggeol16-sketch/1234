package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_DestroyItem {
    private CS_DestroyItem() {}

    public static void handle(CUserSocket client, int length, int objectId, int count, int[] pairs) {
        User.OnDestroyItemPacket(client, length, objectId, count, pairs);
    }
}
