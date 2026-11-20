package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_ChangeShape {
    private SC_ChangeShape() {}
    public static boolean send(CUserSocket client, int objectId, int polyId, int weaponState) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(18);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
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
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendExtended(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_CHANGESHAPE, Opcodes.S_POLYMORPH, objectId, polyId, weaponState, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
    public static void broadcastFast(L1Character anchor, int objectId, int polyId, int weaponState) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendExtended(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_CHANGESHAPE, Opcodes.S_POLYMORPH, objectId, polyId, weaponState, 0, 0, 0, 0, 0, 0, 0, 0, true);
    }

}
