package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_ChangeShape {
    private SC_ChangeShape() {}
    private static byte[] sharedWire(int objectId, int polyId, int weaponState) {
        // writeS("abcd") is MS949 ASCII bytes followed by a NUL terminator.
        return java.nio.ByteBuffer.allocate(18).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 18).put((byte) Opcodes.S_POLYMORPH)
                .putInt(objectId).putShort((short) polyId)
                .put((byte) weaponState).put((byte) 0xFF).put((byte) 0xFF)
                .put((byte) 0).put((byte) 'a').put((byte) 'b')
                .put((byte) 'c').put((byte) 'd').put((byte) 0).array();
    }

    public static boolean send(CIOClient client, int objectId, int polyId, int weaponState) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(18);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_POLYMORPH);
            buffer.writeD(objectId);
            buffer.writeH(polyId);
            buffer.writeC(weaponState);
            buffer.writeC(0xFF);
            buffer.writeC(0xFF);
            buffer.writeC(0);
            buffer.writeS("abcd");
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1Character target, int objectId, int polyId, int weaponState) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, polyId, weaponState);
    }
    public static void broadcast(L1Character anchor, int objectId, int polyId, int weaponState) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, polyId, weaponState), false, -1, null);
    }
    public static void broadcastFast(L1Character anchor, int objectId, int polyId, int weaponState) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, polyId, weaponState), true, -1, null);
    }

}
