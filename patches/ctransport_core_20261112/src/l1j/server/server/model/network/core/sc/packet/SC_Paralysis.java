package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Paralysis {
    public static final int TYPE_PARALYSIS = 1;
    public static final int TYPE_PARALYSIS2 = 2;
    public static final int TYPE_SLEEP = 3;
    public static final int TYPE_FREEZE = 4;
    public static final int TYPE_STUN = 5;
    public static final int TYPE_BIND = 6;
    public static final int TYPE_TELEPORT_UNLOCK = 7;
    public static final int TYPE_RIP = 8;
    public static final int TYPE_PERADO = 9;

    private SC_Paralysis() {}

    public static boolean send(L1PcInstance pc, int type, boolean flag) {
        return pc != null && send(pc.getNetConnection(), type, flag);
    }

    public static boolean send(L1Character target, int type, boolean flag) {
        return target instanceof L1PcInstance && send((L1PcInstance) target, type, flag);
    }

    public static boolean send(CIOClient client, int type, boolean flag) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(4);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PARALYSE);
            buffer.writeC(resolveSubType(type, flag));
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int resolveSubType(int type, boolean flag) {
        switch (type) {
            case TYPE_PARALYSIS: return flag ? 2 : 3;
            case TYPE_PARALYSIS2: return flag ? 4 : 5;
            case TYPE_TELEPORT_UNLOCK: return 7;
            case TYPE_SLEEP: return flag ? 10 : 11;
            case TYPE_FREEZE: return flag ? 12 : 13;
            case TYPE_STUN: return flag ? 22 : 23;
            case TYPE_BIND: return flag ? 24 : 25;
            case TYPE_RIP: return flag ? 26 : 27;
            case TYPE_PERADO: return flag ? 30 : 31;
            default: return 0;
        }
    }
}
