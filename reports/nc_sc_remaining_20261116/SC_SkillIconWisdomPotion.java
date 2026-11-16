package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SkillIconWisdomPotion {
    private static final int WIRE_SIZE = 7;

    private SC_SkillIconWisdomPotion() {}

        private static byte[] sharedWire(int time) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(7)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 7);
        b.put((byte)(Opcodes.S_EVENT));
        b.put((byte)(0x39));
        b.put((byte)(0x2c));
        b.putShort((short)(time));
        return b.array();
    }
public static boolean send(CIOClient client, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x39);
            buffer.writeC(0x2c);
            buffer.writeH(time);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), time);
    }

    public static void broadcast(L1Character anchor, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(time), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(time), true, -1, null);
    }
}
