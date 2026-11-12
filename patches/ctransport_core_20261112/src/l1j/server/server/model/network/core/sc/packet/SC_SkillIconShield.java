package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_SkillIconShield {
    private static final int WIRE_SIZE = 10;

    private SC_SkillIconShield() {}

    public static boolean send(CIOClient client, int type, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MAGE_SHIELD);
            buffer.writeH(time);
            buffer.writeC(type);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int type, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), type, time);
    }

    public static void broadcast(L1Character anchor, int type, int time) {
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, type, time);
    }

    public static void broadcastFast(L1Character anchor, int type, int time) {
        if (anchor == null) return;
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, type, time);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }
}
