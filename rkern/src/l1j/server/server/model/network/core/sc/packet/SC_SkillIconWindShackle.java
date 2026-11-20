package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_SkillIconWindShackle {
    private static final int WIRE_SIZE = 11;

    private SC_SkillIconWindShackle() {}

public static boolean send(CUserSocket client, int objectId, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
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
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILLICONWINDSHACKLE, Opcodes.S_EVENT, objectId, 0, 0, 0, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int time) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILLICONWINDSHACKLE, Opcodes.S_EVENT, objectId, 0, 0, 0, true, -1, null);
    }
}
