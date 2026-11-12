package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOClient;

/**
 * NC-style direct serializer for S_DoActionGFX.
 *
 * Wire:
 *   [2-byte length]
 *   [1-byte S_ACTION]
 *   [4-byte objectId]
 *   [1-byte actionId]
 *
 * Total wire size = 8 bytes.
 */
public final class SC_DoActionGFX {

    private SC_DoActionGFX() {
    }

    /**
     * Single-recipient direct send.
     *
     * true  = direct frame published
     * false = direct path unavailable
     */
    public static boolean send(
            CIOClient client,
            int objectId,
            int actionId) {

        if (client == null || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection = client.getConnection();
        return connection != null && connection.sendAction(objectId, actionId);
    }

    /**
     * Visible-player broadcast.
     *
     * Call-site stays fixed:
     *
     *   SC_DoActionGFX.broadcast(this, actionId);
     *
     * The serializer is thread-agnostic: affinity CIO producers use the SPSC
     * fast path and all other producers use the pre-created external MPSC path.
     */
    public static void broadcast(
            L1Character cha,
            int actionId) {

        if (cha == null) {
            return;
        }

        final int objectId =
                cha.getId();

        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(cha);
            try {
                for (L1PcInstance pc : __cioVisible0) {
        
                    final CIOClient client =
                            pc.getNetConnection();
        
                    if (client == null
                            || !client.isConnected()) {
                        continue;
                    }
        
                    send(
                            client,
                            objectId,
                            actionId);
                }
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }
    
    public static void broadcast(
            L1PcInstance cha,
            int actionId) {

        if (cha == null) {
            return;
        }

        final int objectId =
                cha.getId();

        {
            final L1World __cioWorld1 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible1 = __cioWorld1.borrowVisiblePlayerFast(cha);
            try {
                for (L1PcInstance pc : __cioVisible1) {
        
                    final CIOClient client =
                            pc.getNetConnection();
        
                    if (client == null
                            || !client.isConnected()) {
                        continue;
                    }
        
                    send(
                            client,
                            objectId,
                            actionId);
                }
            } finally {
                __cioWorld1.releaseVisiblePlayerFast(__cioVisible1);
            }
        }
    }
}
