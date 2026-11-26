package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_ATTENDANCE_BONUS_REQUEST_EXTEND {
    private CS_ATTENDANCE_BONUS_REQUEST_EXTEND() {}

    public static void handleRequest(CUserSocket client, ProtoReader reader) {
        CS_ATTENDANCE_REWARD_REQ.handleRequest(client, reader, false);
    }
}
