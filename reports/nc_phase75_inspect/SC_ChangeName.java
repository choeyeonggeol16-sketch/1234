package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_ChangeName {
    private SC_ChangeName() {}

        private static byte[] sharedWire(int objectId, String name) {
        final byte[] body = (name == null ? "" : name)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size = 8 + body.length;
        if (size > 0xFFFF) return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_CHANGE_DESC)
         .putInt(objectId).put(body).put((byte)0);
        return b.array();
    }

public static boolean send(CIOClient client, int objectId, String name) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (name == null ? 0 : (name.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_DESC);
            buffer.writeD(objectId);
            buffer.writeS(name != null ? name : "");
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, String name) {
        return pc != null && send(pc.getNetConnection(), objectId, name);
    }

    public static void broadcast(L1Character anchor, int objectId, String name) {
        if (anchor == null) return;
        final byte[] wire=sharedWire(objectId, name);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, String name) {
        if (anchor == null) return;
        final byte[] wire=sharedWire(objectId, name);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, true, -1, null);
    }

}
