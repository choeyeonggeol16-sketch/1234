package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SkillBrave {
    private static final int WIRE_SIZE = 2 + 8;

    private SC_SkillBrave() {}

        private static byte[] sharedWire(int objectId, int mode, int time) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(10)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 10);
        b.put((byte)(Opcodes.S_EMOTION));
        b.putInt((int)(objectId));
        b.put((byte)(mode));
        b.putShort((short)(time));
        return b.array();
    }
public static boolean send(CIOClient client, int objectId, int mode, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EMOTION);
            buffer.writeD(objectId);
            buffer.writeC(mode);
            buffer.writeH(time);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int objectId, int mode, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, mode, time);
    }

    public static void broadcast(L1Character anchor, int objectId, int mode, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, mode, time), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int mode, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, mode, time), true, -1, null);
    }

}
