package LIN;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.safe.NetworkSafeLoadManager;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoWriter;


/** Initial version reply, encoded directly without a message instance. */
public final class SC_SERVER_VERSION_INFO {
    private static final int STATUS_START_TIME = (int) (System.currentTimeMillis() / 1000L);
    private static final long DENIAL_GRACE_MILLIS = 3000L;

    private SC_SERVER_VERSION_INFO() {}

    public static void send(CIOClient client) {
        sendVersion(client, true);
    }

    public static void denial(CIOClient client) {
        if (sendVersion(client, false)) {
            // Stop further requests, retain the grace period, and drain the
            // accepted response through the existing socket deadline service.
            client.getConnection().requestClientCloseAfter(DENIAL_GRACE_MILLIS);
        }
    }

    private static boolean sendVersion(CIOClient client, boolean accept) {
        final ProtoWriter writer = Proto.begin(client, ProtoMsg.SC_SERVER_VERSION_INFO, 256);
        if (writer == null) return false;
        try {
            if (accept) {
                writer.uint32(1, 0)                         // version_check
                        .uint32(2, 0)                      // server_id
                        .int32(3, NetworkSafeLoadManager.NS_CLIENT_VERSION)
                        .int32(4, NetworkSafeLoadManager.NS_CLIENT_VERSION)
                        .int32(5, NetworkSafeLoadManager.NS_AUTHSERVER_VERSION)
                        .int32(6, NetworkSafeLoadManager.NS_CLIENT_VERSION)
                        .int32(7, STATUS_START_TIME)
                        .int32(8, 0)                       // english_only_config
                        .int32(9, 0)                       // country_code
                        .int32(10, NetworkSafeLoadManager.NS_CLIENT_SETTING_SWITCH)
                        .int32(11, (int) (System.currentTimeMillis() / 1000L))
                        .int32(12, NetworkSafeLoadManager.NS_CACHESERVER_VERSION)
                        .int32(13, NetworkSafeLoadManager.NS_TAMSERVER_VERSION)
                        .int32(14, NetworkSafeLoadManager.NS_ARCASERVER_VERSION)
                        .int32(15, NetworkSafeLoadManager.NS_HIBREED_INTERSERVER_VERSION)
                        .int32(16, NetworkSafeLoadManager.NS_ARENACOSERVER_VERSION)
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
