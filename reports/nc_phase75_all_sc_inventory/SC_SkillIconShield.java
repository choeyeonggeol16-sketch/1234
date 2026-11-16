package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SkillIconShield {
    private static final int WIRE_SIZE = 10;

    private SC_SkillIconShield() {}

        private static byte[] sharedWire(int type, int time) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(10)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 10);
        b.put((byte)(Opcodes.S_MAGE_SHIELD));
        b.putShort((short)(time));
        b.put((byte)(type));
        b.putInt((int)(0));
        return b.array();
    }
public static boolean send(CIOClient client, int type, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MAGE_SHIELD);
            buffer.writeH(time);
            buffer.writeC(type);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int type, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), type, time);
    }

    public static void broadcast(L1Character anchor, int type, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(type, time), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int type, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(type, time), true, -1, null);
    }
}
