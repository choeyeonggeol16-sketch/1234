package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_Dexup {
    private static final int WIRE_SIZE = 11;

    private SC_Dexup() {}

    public static boolean send(CIOClient client, L1PcInstance actor, int type, int time) {
        if (actor == null) return false;
        final int totalDex = actor.getAbility() == null ? 0 : actor.getAbility().getTotalDex();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MAGE_DEXTERITY);
            buffer.writeH(time);
            buffer.writeC(totalDex);
            buffer.writeC(type);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, L1PcInstance actor, int type, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), actor, type, time);
    }

    public static void broadcast(L1Character anchor, L1PcInstance actor, int type, int time) {
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, actor, type, time);
    }

    public static void broadcastFast(L1Character anchor, L1PcInstance actor, int type, int time) {
        if (anchor == null) return;
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, actor, type, time);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }
}
