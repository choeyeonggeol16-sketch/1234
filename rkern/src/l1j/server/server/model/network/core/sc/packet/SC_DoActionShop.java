package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_DoActionShop {
    private SC_DoActionShop() {}
    public static boolean send(L1PcInstance pc, int objectId, int action, byte[] message) {
        return pc != null && send(pc.getNetConnection(), objectId, action, message);
    }
    public static boolean send(CUserSocket client, int objectId, int action, byte[] message) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client;
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(8 + (message == null ? 0 : message.length));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
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
    public static boolean send(CUserSocket client, int objectId, int action, String message) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client;
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(9 + (message == null ? 0 : message.length() * 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
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
        CSharedBroadcast.sendWritten(anchor, b -> { b.put((byte)Opcodes.S_ACTION).putInt(id).put((byte)action); if(message!=null)b.put(message); }, false, -1, null);
    }
    public static void broadcast(L1Character anchor, int id, int action, String message) {
        if(anchor==null)return;
        CSharedBroadcast.sendWritten(anchor, b -> { b.put((byte)Opcodes.S_ACTION).putInt(id).put((byte)action); CSharedBroadcast.writeMS949(b,message); b.put((byte)0); }, false, -1, null);
    }

}
