package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_CloseList {
    private SC_CloseList() {}

    public static boolean send(CUserSocket client, int objectId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(objectId);
            buffer.writeS("");
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId) {
        return pc != null && send(pc.getNetConnection(), objectId);
    }

}
