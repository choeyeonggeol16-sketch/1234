package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SystemMessage {

    public static boolean send(L1PcInstance pc, String text) {
        return send(pc, text, false);
    }

    public static boolean send(L1PcInstance pc, String text, boolean nameId) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String value = text == null ? "" : text;
        final long needed = 16L + ((long) value.length() << 1);
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeContent(frame.buffer(), value, nameId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcast(String message) {
        for (L1PcInstance pc : l1j.server.server.model.L1World.getInstance().getAllPlayers()) {
            if (pc != null) send(pc, message);
        }
    }

    private static void writeContent(WriteBuffer buffer, String msg, boolean nameId) {
        if (!nameId) {
            buffer.writeC(Opcodes.S_MESSAGE);
            buffer.writeC(0x09);
            buffer.writeS(msg);
        } else {
            buffer.writeC(Opcodes.S_SAY_CODE);
            buffer.writeC(2);
            buffer.writeD(0);
            buffer.writeS(msg);
        }
    }

    
}
