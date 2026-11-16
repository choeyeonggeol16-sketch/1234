package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.L1World;

public final class SC_TopMessage {
    private SC_TopMessage() {}
    public static boolean send(CIOClient client, String msg) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (msg == null ? 0 : (msg.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(121);
            buffer.writeC(84);
            buffer.writeC(2);
            buffer.writeS(msg == null ? "" : msg);
            buffer.writeC(0);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, String msg) {
        return pc != null && send(pc.getNetConnection(), msg);
    }
    public static void broadcastAll(String msg) {
        final byte[] wire=sharedWire(msg);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }


    private static byte[] sharedWire(String msg) {
        final byte[] text=(msg==null ? "" : msg)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=8+text.length;
        if(size>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)121).put((byte)84).put((byte)2)
         .put(text).put((byte)0).put((byte)0).put((byte)0);
        return b.array();
    }

}
