package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_BuilderPacket {
    private final byte[] bytes;

    public SC_BuilderPacket(byte[] bytes) {
        this.bytes = bytes;
    }

    public boolean send(L1PcInstance pc) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        if (bytes == null || bytes.length == 0 || bytes.length > Integer.MAX_VALUE - 2) return false;
        final CIOFrame frame = connection.beginDirectFrame(bytes.length + 2);
        if (frame == null) return false;
        boolean committed = false;
        try {
            frame.buffer().writeBytes(bytes);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
