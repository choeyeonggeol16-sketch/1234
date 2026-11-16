package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_AttackStatus {
    private SC_AttackStatus() {}

    public static boolean send(L1PcInstance pc, int receiverId, int type) {
        return pc != null && send(pc.getNetConnection(), pc.getId(), pc.getHeading(), receiverId, type);
    }

    public static boolean send(L1PcInstance pc, int attackerId, int heading, int receiverId, int type) {
        return pc != null && send(pc.getNetConnection(), attackerId, heading, receiverId, type);
    }

        private static byte[] sharedWire(int attackerId, int heading, int receiverId, int type) {
        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(19)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 19);
        b.put((byte)(Opcodes.S_ATTACK));
        b.put((byte)(type));
        b.putInt((int)(attackerId));
        b.putInt((int)(receiverId));
        b.put((byte)(1));
        b.put((byte)(0));
        b.put((byte)(heading));
        b.putInt((int)(0));
        return b.array();
    }
public static boolean send(CIOClient client, int attackerId, int heading, int receiverId, int type) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(19);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ATTACK);
            buffer.writeC(type);
            buffer.writeD(attackerId);
            buffer.writeD(receiverId);
            buffer.writeC(1);
            buffer.writeC(0);
            buffer.writeC(heading);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcast(L1Character anchor, int attackerId, int heading, int receiverId, int type) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(attackerId, heading, receiverId, type), false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int attackerId, int heading, int receiverId, int type) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(attackerId, heading, receiverId, type), true, -1, null);
    }

    public static void sendAndBroadcast(L1PcInstance pc, int receiverId, int type, boolean fast) {
        if (pc == null) return;
        final int attackerId = pc.getId();
        final int heading = pc.getHeading();
        send(pc, attackerId, heading, receiverId, type);
        if (fast) broadcastFast(pc, attackerId, heading, receiverId, type);
        else broadcast(pc, attackerId, heading, receiverId, type);
    }
}
