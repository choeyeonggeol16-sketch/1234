package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_HowManyMake {

    private SC_HowManyMake() {}

    public static boolean send(CIOClient client, int objectId, int max, String htmlId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(36 + (htmlId == null ? 0 : (htmlId.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT_INPUT);
            buffer.writeD(objectId);
            buffer.writeD(0);
            buffer.writeD(0);
            buffer.writeD(0);
            buffer.writeD(max);
            buffer.writeH(0);
            buffer.writeS("request");
            buffer.writeS(htmlId == null ? "" : htmlId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, int max, String htmlId) {
        return pc != null && send(pc.getNetConnection(), objectId, max, htmlId);
    }
}
