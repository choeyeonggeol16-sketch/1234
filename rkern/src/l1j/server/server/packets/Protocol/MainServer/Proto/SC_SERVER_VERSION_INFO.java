package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoMsg;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;

/** Initial version reply, encoded directly without a message instance. */
public final class SC_SERVER_VERSION_INFO {
    private static final int STATUS_START_TIME = (int) (System.currentTimeMillis() / 1000L);
    private static final long DENIAL_GRACE_MILLIS = 3000L;

    private SC_SERVER_VERSION_INFO() {}

    public static void send(CUserSocket client) {
        sendVersion(client, true);
    }

    public static void denial(CUserSocket client) {
        if (sendVersion(client, false)) {
            // Stop further requests, retain the grace period, and drain the
            // accepted response through the existing socket deadline service.
            client.requestClientCloseAfter(DENIAL_GRACE_MILLIS);
        }
    }

    private static boolean sendVersion(CUserSocket client, boolean accept) {
        final ProtoWriter writer = Proto.begin(client, ProtoMsg.SC_SERVER_VERSION_INFO, 256);
        if (writer == null) return false;
        try {
            if (accept) {
                writer.uint32(1, 0)                         // version_check
                        .uint32(2, 0)                      // server_id
                        .int32(3, CPacketFilter.NS_CLIENT_VERSION)
                        .int32(4, CPacketFilter.NS_CLIENT_VERSION)
                        .int32(5, CPacketFilter.NS_AUTHSERVER_VERSION)
                        .int32(6, CPacketFilter.NS_CLIENT_VERSION)
                        .int32(7, STATUS_START_TIME)
                        .int32(8, 0)                       // english_only_config
                        .int32(9, 0)                       // country_code
                        .int32(10, CPacketFilter.NS_CLIENT_SETTING_SWITCH)
                        .int32(11, (int) (System.currentTimeMillis() / 1000L))
                        .int32(12, CPacketFilter.NS_CACHESERVER_VERSION)
                        .int32(13, CPacketFilter.NS_TAMSERVER_VERSION)
                        .int32(14, CPacketFilter.NS_ARCASERVER_VERSION)
                        .int32(15, CPacketFilter.NS_HIBREED_INTERSERVER_VERSION)
                        .int32(16, CPacketFilter.NS_ARENACOSERVER_VERSION)
                        .int32(17, 0);                      // server_type
            } else {
                writer.uint32(1, 1);                       // version_check: denied
            }
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }
}
