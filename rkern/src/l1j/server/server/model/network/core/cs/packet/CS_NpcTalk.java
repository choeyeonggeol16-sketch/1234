package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.connection.CUserSocket;

/** Client NPC talk entry. Execution belongs to the existing User path. */
public final class CS_NpcTalk {
    private CS_NpcTalk() {}

    public static void handle(CUserSocket client, int objId) {
        User.OnNpcTalkPacket(client, objId);
    }
}
