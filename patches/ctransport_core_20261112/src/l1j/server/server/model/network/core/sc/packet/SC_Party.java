package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Party {
    private SC_Party() {}

    public static boolean sendHypertext(L1PcInstance receiver, String htmlId, int objId) {
        return sendHypertext0(receiver, htmlId, objId, "", "", 0);
    }

    public static boolean sendHypertext(L1PcInstance receiver, String htmlId, int objId,
                                        String partyName, String partyMembers) {
        return sendHypertext0(receiver, htmlId, objId, partyName, partyMembers, 1);
    }

    private static boolean sendHypertext0(L1PcInstance receiver, String htmlId, int objId,
                                          String partyName, String partyMembers, int type) {
        if (receiver == null) return false;
        final CIOClient socket = receiver.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String html = htmlId == null ? "" : htmlId;
        final String name = partyName == null ? "" : partyName;
        final String members = partyMembers == null ? "" : partyMembers;
        final long needed = 16L + 2L * ((long) html.length() + name.length() + members.length());
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(objId);
            buffer.writeS(html);
            buffer.writeH(type);
            buffer.writeH(2);
            buffer.writeS(name);
            buffer.writeS(members);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendNameColor(L1PcInstance receiver, L1PcInstance subject, int color) {
        if (receiver == null || color < 0 || color > 2) return false;
        final CIOClient socket = receiver.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(16);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x6c);
            buffer.writeD(subject == null ? 0 : subject.getId());
            buffer.writeH(color);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
