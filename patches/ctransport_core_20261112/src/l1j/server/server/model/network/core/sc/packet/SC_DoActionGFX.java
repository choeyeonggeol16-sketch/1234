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

    public static void broadcast(L1Character cha, int actionId) {
        if (cha == null) return;
        final int objectId = cha.getId();
        final byte[] wire = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort((short) 8)
                .put((byte) l1j.server.server.Opcodes.S_ACTION)
                .putInt(objectId).put((byte) actionId).array();
        final L1World world = L1World.getInstance();
        final java.util.List<L1PcInstance> visible = world.borrowVisiblePlayerFast(cha);
        try (var broadcast = new l1j.server.server.model.network.core.CAsyncSendConstBufferExceptSelf(
                wire, objectId, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : visible) {
                final CIOClient client = pc.getNetConnection();
                if (client != null && client.isConnected() && !broadcast.apply(client.getConnection(), pc.getId()))
                    send(client, objectId, actionId);
            }
        } finally { world.releaseVisiblePlayerFast(visible); }
    }

    public static void broadcast(L1PcInstance cha, int actionId) {
        broadcast((L1Character) cha, actionId);
    }
}
