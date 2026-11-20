package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_Resurrection {
    private SC_Resurrection() {}

public static boolean send(CUserSocket client, int targetId, int type, int userId, int targetClassId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(16);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_RESURRECT);
            buffer.writeD(targetId);
            buffer.writeC(type);
            buffer.writeD(userId);
            buffer.writeD(targetClassId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int targetId, int type, int userId, int targetClassId) {
        return pc != null && send(pc.getNetConnection(), targetId, type, userId, targetClassId);
    }

    public static boolean send(L1PcInstance pc, L1PcInstance target, L1PcInstance user, int type) {
        return send(pc, target != null ? target.getId() : 0, type,
                user != null ? user.getId() : 0,
                target != null ? target.getClassId() : 0);
    }

    public static void broadcast(L1Character anchor, L1PcInstance target, L1PcInstance user, int type) {
        if (anchor == null) return;
        final int targetId = target != null ? target.getId() : 0;
        final int userId = user != null ? user.getId() : 0;
        final int targetClassId = target != null ? target.getClassId() : 0;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_RESURRECTION, Opcodes.S_RESURRECT, targetId, type, userId, targetClassId, false, -1, null);
    }

}
