package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_Poison {
    private static final int WIRE_SIZE = 9;

    private SC_Poison() {}

public static boolean send(CUserSocket client, int objectId, int type) {
        if (type < 0 || type > 2) throw new IllegalArgumentException("부정한 인수입니다. type = " + type);
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_POISON);
            buffer.writeD(objectId);
            buffer.writeC(type == 1 ? 1 : 0);
            buffer.writeC(type == 2 ? 1 : 0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int objectId, int type) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, type);
    }

    public static void broadcast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return; 
        if(type < 0 || type > 2)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_POISON,
                Opcodes.S_POISON, objectId, type, 0, 0, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return; 
        if(type < 0 || type > 2)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_POISON,
                Opcodes.S_POISON, objectId, type, 0, 0, true, -1, null);
    }
}
