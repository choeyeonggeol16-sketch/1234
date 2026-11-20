package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

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
            CUserSocket client,
            int objectId,
            int actionId) {

        if (client == null || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection = client.getConnection();
        return connection != null && connection.sendAction(objectId, actionId);
    }

    public static void broadcast(L1Character cha, int actionId) {
        if (cha == null) return;
        // NC-style one owned plaintext frame, fanout with existing L1 AOI/FIFO.
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAction(
                cha, cha.getId(), actionId);
    }

    public static void broadcast(L1PcInstance cha, int actionId) {
        broadcast((L1Character) cha, actionId);
    }


    /** Immutable action frame usable across several recipients. */

    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int objectId,int actionId) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_ACTION)
            .putInt(objectId).put((byte)actionId);
    }


}
