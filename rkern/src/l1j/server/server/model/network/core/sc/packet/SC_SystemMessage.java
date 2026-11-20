package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.session.CUserSession;

public final class SC_SystemMessage {

    public static boolean send(L1PcInstance pc, String text) {
        return send(pc, text, false);
    }

    public static boolean send(L1PcInstance pc, String text, boolean nameId) {
        if (pc == null) return false;
        final CUserSession socket = pc.getNetConnection();
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
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_MESSAGE).put((byte)0x09);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,message);
            b.put((byte)0);
        });
    }


    /** Map/party recipients: serialize one frame and send to exactly the
     * specified players, with no per-client legacy serialization.
     */
    public static void broadcastRecipients(
            Iterable<? extends L1PcInstance> recipients,String message) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(recipients,b -> {
            b.put((byte)Opcodes.S_MESSAGE).put((byte)0x09);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,message);
            b.put((byte)0);
        });
    }

    private static void writeContent(CPacketWriteBuffer buffer, String msg, boolean nameId) {
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

    


    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(String message) {
        final String text=message==null?"":message;
        return b -> {
            b.put((byte)Opcodes.S_MESSAGE).put((byte)0x09);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,text);
            b.put((byte)0);
        };
    }


}
