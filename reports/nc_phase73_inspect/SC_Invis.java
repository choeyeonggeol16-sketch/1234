package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Invis {
    private SC_Invis() {}

    public static boolean send(L1PcInstance pc, int objectId, int type) {
        return pc != null && send(pc.getNetConnection(), objectId, type);
    }

        private static byte[] sharedWire(int objectId, int type) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 8);
        b.put((byte)(Opcodes.S_INVISIBLE));
        b.putInt((int)(objectId));
        b.put((byte)(type));
        return b.array();
    }
public static boolean send(CIOClient client, int objectId, int type) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_INVISIBLE);
            buffer.writeD(objectId);
            buffer.writeC(type);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), true, -1, null);
    }
}
