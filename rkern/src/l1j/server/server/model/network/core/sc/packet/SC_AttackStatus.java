package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CUserSession;

public final class SC_AttackStatus {
    private SC_AttackStatus() {}

    public static boolean send(L1PcInstance pc, int receiverId, int type) {
        return pc != null && send(pc.getNetConnection(), pc.getId(), pc.getHeading(), receiverId, type);
    }

    public static boolean send(L1PcInstance pc, int attackerId, int heading, int receiverId, int type) {
        return pc != null && send(pc.getNetConnection(), attackerId, heading, receiverId, type);
    }

public static boolean send(CUserSession client, int attackerId, int heading, int receiverId, int type) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(19);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ATTACK);
            buffer.writeC(type);
            buffer.writeD(attackerId);
            buffer.writeD(receiverId);
            buffer.writeC(1);
            buffer.writeC(0);
            buffer.writeC(heading);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcast(L1Character anchor, int attackerId, int heading, int receiverId, int type) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendExtended(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_ATTACKSTATUS, Opcodes.S_ATTACK, attackerId, heading, receiverId, type, 0, 0, 0, 0, 0, 0, 0, false);
    }

    public static void broadcastFast(L1Character anchor, int attackerId, int heading, int receiverId, int type) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendExtended(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_ATTACKSTATUS, Opcodes.S_ATTACK, attackerId, heading, receiverId, type, 0, 0, 0, 0, 0, 0, 0, true);
    }

    public static void sendAndBroadcast(L1PcInstance pc, int receiverId, int type, boolean fast) {
        if (pc == null) return;
        final int attackerId = pc.getId();
        final int heading = pc.getHeading();
        send(pc, attackerId, heading, receiverId, type);
        if (fast) broadcastFast(pc, attackerId, heading, receiverId, type);
        else broadcast(pc, attackerId, heading, receiverId, type);
    }
}
