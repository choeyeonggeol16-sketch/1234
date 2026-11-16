package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_MessageYN {
    private SC_MessageYN() {}

    public static boolean send(L1PcInstance pc, int type) {
        return send(pc, 0, type, "", null, false);
    }
    public static boolean send(L1PcInstance pc, int type, String msg) {
        return send(pc, 0, type, msg, null, false);
    }
    public static boolean send(L1PcInstance pc, int type, String msg1, String msg2) {
        return send(pc, 0, type, msg1, msg2, true);
    }
    public static boolean send(L1PcInstance pc, int idx, int type, String msg) {
        return send(pc, idx, type, msg, null, false);
    }
    private static int maxBytes(String s) {
        return s == null ? 1 : (s.length() * 2) + 1;
    }
    private static boolean send(L1PcInstance pc, int idx, int type, String msg1, String msg2, boolean two) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final long capacity = 16L + maxBytes(msg1) + (two ? maxBytes(msg2) : 0);
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ASK);
            buffer.writeH(0);
            buffer.writeD(idx);
            buffer.writeH(type);
            buffer.writeS(msg1 == null ? "" : msg1);
            if (two) buffer.writeS(msg2 == null ? "" : msg2);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
    public static void broadcast(int type, String message) {
        final byte[] wire=sharedWire(type, message);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }


    /** Exact direct-writer S_ASK frame, including original idx, type and
     * MS949 null-terminated message. The idx is not a per-user token.
     */
    public static byte[] sharedIndexedWire(int idx,int type,String message) {
        final byte[] text=(message==null?"":message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=12+text.length;
        if(size>0xffff)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ASK).putShort((short)0)
         .putInt(idx).putShort((short)type).put(text).put((byte)0);
        return b.array();
    }

    public static byte[] sharedWire(int type, String message) {
        final byte[] text=(message==null ? "" : message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=12+text.length;
        if(size>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ASK).putShort((short)0)
         .putInt(0).putShort((short)type).put(text).put((byte)0);
        return b.array();
    }

}
