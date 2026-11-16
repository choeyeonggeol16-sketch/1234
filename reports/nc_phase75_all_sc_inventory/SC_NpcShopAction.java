package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_NpcShopAction {

    private SC_NpcShopAction() {}

        private static byte[] sharedWire(int objectId, int action, String shopName) {
        final String text=shopName==null ? "" : shopName;
        final long calculated=10L+((long)text.length()<<1);
        if (calculated > 0xFFFF) return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)calculated)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)calculated).put((byte)Opcodes.S_ACTION)
         .putInt(objectId).put((byte)action);
        for(int i=0;i<text.length();i++) b.putChar(text.charAt(i));
        b.putShort((short)0);
        return b.array();
    }

public static boolean send(CIOClient client, int objectId, int action, String shopName) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10 + (shopName == null ? 0 : (shopName.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ACTION);
            buffer.writeD(objectId);
            buffer.writeC(action);
            buffer.writeSU16(shopName == null ? "" : shopName);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, int action, String shopName) {
        return pc != null && send(pc.getNetConnection(), objectId, action, shopName);
    }
    public static void broadcast(L1Character anchor, int objectId, int action, String shopName) {
        if (anchor == null) return;
        final byte[] wire=sharedWire(objectId, action, shopName);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
    }
}
