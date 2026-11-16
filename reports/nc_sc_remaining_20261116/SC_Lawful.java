package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Lawful {
    private static final int WIRE_SIZE = 13;

    private SC_Lawful() {}

        private static byte[] sharedWire(int objectId, int lawful) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(13)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 13);
        b.put((byte)(Opcodes.S_CHANGE_ALIGNMENT));
        b.putInt((int)(objectId));
        b.putShort((short)(lawful));
        b.putInt((int)(0));
        return b.array();
    }
public static boolean send(CIOClient client, int objectId, int lawful) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_ALIGNMENT);
            buffer.writeD(objectId);
            buffer.writeH(lawful);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int objectId, int lawful) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, lawful);
    }

    public static void broadcast(L1Character anchor, int objectId, int lawful) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, lawful), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int lawful) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, lawful), true, -1, null);
    }
}
