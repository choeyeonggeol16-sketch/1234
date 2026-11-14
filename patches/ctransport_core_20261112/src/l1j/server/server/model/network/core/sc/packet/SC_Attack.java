package l1j.server.server.model.network.core.sc.packet;

import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Attack {
    private static final AtomicInteger SEQ = new AtomicInteger();
    private SC_Attack() {}

    public static void sendAndBroadcastFast(L1PcInstance pc, int targetId, int effect,
                                            int damage, int heading, int endX, int endY) {
        if (pc == null) return;
        final int attackerId = pc.getId();
        final int x = pc.getX();
        final int y = pc.getY();
        final int seq = SEQ.incrementAndGet();
        send(pc, attackerId, targetId, damage, heading, seq, effect, x, y, endX, endY);
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(pc);
            try {
                for (L1PcInstance other : __cioVisible0) {
                    send(other, attackerId, targetId, damage, heading, seq, effect, x, y, endX, endY);
                }
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }

    public static boolean send(L1PcInstance pc, int attackerId, int targetId,
                               int damage, int heading, int seq, int effect,
                               int x, int y, int endX, int endY) {
        return pc != null && send(pc.getNetConnection(), attackerId, targetId, damage, heading,
                                  seq, effect, x, y, endX, endY);
    }

    public static boolean send(CIOClient client, int attackerId, int targetId,
                               int damage, int heading, int seq, int effect,
                               int x, int y, int endX, int endY) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(39);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ATTACK);
            buffer.writeC(1);
            buffer.writeD(attackerId);
            buffer.writeD(targetId);
            buffer.writeH(damage);
            buffer.writeC(heading);
            buffer.writeD(seq);
            buffer.writeH(effect);
            buffer.writeC(0);
            buffer.writeH(x);
            buffer.writeH(y);
            buffer.writeH(endX);
            buffer.writeH(endY);
            buffer.writeD(0);
            buffer.writeH(0);
            buffer.writeC(0);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
