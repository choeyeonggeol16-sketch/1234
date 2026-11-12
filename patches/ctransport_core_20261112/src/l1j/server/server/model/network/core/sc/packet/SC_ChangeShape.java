package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_ChangeShape {
    private SC_ChangeShape() {}
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
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, objectId, polyId, weaponState);
    }
    public static void broadcastFast(L1Character anchor, int objectId, int polyId, int weaponState) {
        if (anchor == null) return;
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, objectId, polyId, weaponState);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }

}
