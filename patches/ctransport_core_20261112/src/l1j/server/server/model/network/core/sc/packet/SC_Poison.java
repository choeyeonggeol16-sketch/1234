package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_Poison {
    private static final int WIRE_SIZE = 9;

    private SC_Poison() {}

    public static boolean send(CIOClient client, int objectId, int type) {
        if (type < 0 || type > 2) throw new IllegalArgumentException("부정한 인수입니다. type = " + type);
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_POISON);
            buffer.writeD(objectId);
            buffer.writeC(type == 1 ? 1 : 0);
            buffer.writeC(type == 2 ? 1 : 0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int objectId, int type) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, type);
    }

    public static void broadcast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, objectId, type);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int type) {
        if (anchor == null) return;
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, objectId, type);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }
}
