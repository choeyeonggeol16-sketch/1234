package l1j.server.server.model.network.core.sc.packet;

import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_RangeSkill {
    public static final int TYPE_NODIR = 0;
    public static final int TYPE_DIR = 8;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final int[] EMPTY = new int[0];

    private SC_RangeSkill() {}

    public static void sendAndBroadcastFast(L1Character caster, L1Character[] targets,
                                            int count, int gfx, int action, int type) {
        dispatch(caster, targets, count, gfx, action, type, true);
    }

    public static void broadcastFast(L1Character caster, L1Character[] targets,
                                     int count, int gfx, int action, int type) {
        dispatch(caster, targets, count, gfx, action, type, false);
    }

    private static void dispatch(L1Character caster, L1Character[] targets, int count,
                                 int gfx, int action, int type, boolean toSelf) {
        if (caster == null) return;
        final int x = caster.getX(), y = caster.getY(), id = caster.getId();
        final int limit = targets == null || count <= 0 ? 0 : Math.min(count, targets.length);
        int heading = caster.getHeading();
        if (type == TYPE_DIR) {
            for (int i = 0; i < limit; i++) {
                final L1Character t = targets[i];
                if (t == null) continue;
                heading = calcHeading(x, y, t.getX(), t.getY());
                try { caster.setHeading(heading); } catch (Throwable ignored) {}
                break;
            }
        }
        final int[] ids = limit <= 0 ? EMPTY : new int[limit];
        int used = 0;
        for (int i = 0; i < limit; i++) {
            final L1Character t = targets[i];
            if (t != null) ids[used++] = t.getId();
        }
        final int seq = SEQUENCE.incrementAndGet();
        if (toSelf && caster instanceof L1PcInstance) {
            send((L1PcInstance) caster, id, x, y, heading, seq, gfx, action, type, ids, used);
        }
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(caster);
            try {
                for (L1PcInstance pc : __cioVisible0) {
                    send(pc, id, x, y, heading, seq, gfx, action, type, ids, used);
                }
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }

    private static int calcHeading(int x, int y, int tx, int ty) {
        if (tx > x) return ty > y ? 3 : ty < y ? 1 : 2;
        if (tx < x) return ty > y ? 5 : ty < y ? 7 : 6;
        if (ty < y) return 0;
        if (ty > y) return 4;
        return 0;
    }

    private static boolean send(L1PcInstance pc, int id, int x, int y, int heading,
                                int seq, int gfx, int action, int type, int[] ids, int count) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final long size = 24L + (long) count * 6L;
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer b = frame.buffer();
            b.writeC(Opcodes.S_ATTACK_MANY);
            b.writeC(action);
            b.writeD(id);
            b.writeH(x);
            b.writeH(y);
            b.writeC(heading);
            b.writeD(seq);
            b.writeH(gfx);
            b.writeC(type);
            b.writeH(0);
            b.writeH(count);
            for (int i = 0; i < count; i++) {
                b.writeD(ids[i]);
                b.writeC(32);
                b.writeC(action);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
