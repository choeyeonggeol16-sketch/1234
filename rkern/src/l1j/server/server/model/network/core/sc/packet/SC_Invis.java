package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_Invis {
    private SC_Invis() {}

    public static boolean send(L1PcInstance pc, int objectId, int type) {
        return pc != null && send(pc.getNetConnection(), objectId, type);
    }

public static boolean send(CUserSocket client, int objectId, int type) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
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
        CSharedBroadcast.sendFormatted(
                anchor, CSharedBroadcast.FORMAT_INVIS,
                Opcodes.S_INVISIBLE, objectId, type, 0, 0, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return;
        CSharedBroadcast.sendFormatted(
                anchor, CSharedBroadcast.FORMAT_INVIS,
                Opcodes.S_INVISIBLE, objectId, type, 0, 0, true, -1, null);
    }
}
