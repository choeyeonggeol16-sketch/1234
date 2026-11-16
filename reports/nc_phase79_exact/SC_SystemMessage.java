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
        final byte[] wire=sharedWire(message);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }


    /** Map/party recipients: serialize one frame and send to exactly the
     * specified players, with no per-client legacy serialization.
     */
    public static void broadcastRecipients(
            Iterable<? extends L1PcInstance> recipients,String message) {
        final byte[] wire=sharedWire(message);
        if(wire!=null)
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendRecipients(recipients,wire);
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

    

    public static byte[] sharedWire(String message) {
        final byte[] text=(message==null ? "" : message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=5+text.length;
        if(size>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_MESSAGE)
         .put((byte)0x09).put(text).put((byte)0);
        return b.array();
    }

}
