package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SkillIconWindShackle {
    private static final int WIRE_SIZE = 11;

    private SC_SkillIconWindShackle() {}

        private static byte[] sharedWire(int objectId, int time) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(11)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 11);
        b.put((byte)(Opcodes.S_EVENT));
        b.put((byte)(0x2c));
        b.putInt((int)(objectId));
        b.put((byte)(0));
        b.put((byte)(0x0a));
        b.put((byte)(0x80));
        return b.array();
    }
public static boolean send(CIOClient client, int objectId, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x2c);
            buffer.writeD(objectId);
            buffer.writeC(0);
            buffer.writeC(0x0a);
            buffer.writeC(0x80);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int objectId, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, time);
    }

    public static void broadcast(L1Character anchor, int objectId, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, time), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, time), true, -1, null);
    }
}
