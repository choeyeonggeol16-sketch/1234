package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_DoActionShop {
    private SC_DoActionShop() {}
    public static boolean send(L1PcInstance pc, int objectId, int action, byte[] message) {
        return pc != null && send(pc.getNetConnection(), objectId, action, message);
    }
    public static boolean send(CIOClient client, int objectId, int action, byte[] message) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(8 + (message == null ? 0 : message.length));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ACTION);
            buffer.writeD(objectId);
            buffer.writeC(action);
            if (message != null) buffer.writeByte(message);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int objectId, int action, String message) {
        return pc != null && send(pc.getNetConnection(), objectId, action, message);
    }
    public static boolean send(CIOClient client, int objectId, int action, String message) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(9 + (message == null ? 0 : message.length() * 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ACTION);
            buffer.writeD(objectId);
            buffer.writeC(action);
            buffer.writeS(message == null ? "" : message);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
    public static void broadcast(L1Character anchor, int id, int action, byte[] message) {
        if(anchor==null)return;
        final byte[] wire=sharedWire(id,action,message);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
    }
    public static void broadcast(L1Character anchor, int id, int action, String message) {
        if(anchor==null)return;
        final byte[] wire=sharedWire(id,action,message);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
    }

    private static byte[] sharedWire(int id,int action,byte[] message) {
        final int size=8+(message==null?0:message.length);
        if(size>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ACTION)
         .putInt(id).put((byte)action);
        if(message!=null)b.put(message);
        return b.array();
    }
    private static byte[] sharedWire(int id,int action,String message) {
        final byte[] data=(message==null?"":message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=9+data.length;
        if(size>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ACTION)
         .putInt(id).put((byte)action).put(data).put((byte)0);
        return b.array();
    }

}
