package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_GMHtml {
    private SC_GMHtml() {}

    public static boolean send(CIOClient client, String s1, String s2) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(24 + ((s1 == null ? 0 : s1.length()) << 1) + ((s2 == null ? 0 : s2.length()) << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(0);
            buffer.writeS("withdraw");
            buffer.writeH(1);
            buffer.writeH(2);
            buffer.writeS(s1 == null ? "" : s1);
            buffer.writeS(s2 == null ? "" : s2);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, String s1, String s2) {
        return pc != null && send(pc.getNetConnection(), s1, s2);
    }

    public static boolean send(L1PcInstance pc, int objectId, String html) {
        return pc != null && send(pc.getNetConnection(), objectId, html);
    }
    public static boolean send(CIOClient client, int objectId, String html) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(16 + (html == null ? 0 : (html.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(objectId);
            buffer.writeS("hsiw");
            buffer.writeS(html == null ? "" : html);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
