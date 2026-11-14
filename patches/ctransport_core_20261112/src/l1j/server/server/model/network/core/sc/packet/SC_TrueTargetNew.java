package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_TrueTargetNew {
    private SC_TrueTargetNew() {}

    public static boolean send(CIOClient client, int targetId, boolean active) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(18);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0xc2);
            buffer.writeD(targetId);
            buffer.writeC(0x4f);
            buffer.writeC(0x33);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeD(active ? 1 : 0);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int targetId, boolean active) {
        return pc != null && send(pc.getNetConnection(), targetId, active);
    }
    public static void broadcastFast(l1j.server.server.model.L1Character anchor, int targetId, boolean active) {
        if (anchor == null) return;
        {
            final l1j.server.server.model.L1World __cioWorld0 = l1j.server.server.model.L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, targetId, active);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }

}
