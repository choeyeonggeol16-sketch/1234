package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;

public final class SC_OnlyEffect {
    private SC_OnlyEffect() {}

    private static byte[] sharedWire(int objectId, int effectId) {
        return java.nio.ByteBuffer.allocate(9).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 9).put((byte) Opcodes.S_EFFECT)
                .putInt(objectId).putShort((short) effectId).array();
    }

    public static boolean send(L1Character character, int objectId, int effectId) {
        return character instanceof L1PcInstance && send((L1PcInstance) character, objectId, effectId);
    }

    public static boolean send(L1PcInstance pc, int objectId, int effectId) {
        return pc != null && send(pc.getNetConnection(), objectId, effectId);
    }

    public static boolean send(CIOClient client, int objectId, int effectId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EFFECT);
            buffer.writeD(objectId);
            buffer.writeH(effectId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcast(L1Character anchor, int objectId, int effectId) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, effectId), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int effectId) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, effectId), true, -1, null);
    }
}
