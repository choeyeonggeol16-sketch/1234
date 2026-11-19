package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_ShowPolyList {
    private SC_ShowPolyList() {}

    public static boolean send(CIOClient client, int objId, String html) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (html == null ? 0 : (html.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(objId);
            buffer.writeS(html == null ? "" : html);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objId) {
        return pc != null && send(pc.getNetConnection(), objId, "monlist");
    }

    public static boolean send(L1PcInstance pc, int objId, String html) {
        return pc != null && send(pc.getNetConnection(), objId, html);
    }
}
