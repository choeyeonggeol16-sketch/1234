package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_AreaAttackEffect {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private SC_AreaAttackEffect() {}

    public static void sendNoDirAndBroadcast(L1Character caster, ArrayList<L1Character> targets,
                                              int gfx, int action) {
        if (caster == null) return;
        final int id = caster.getId(), x = caster.getX(), y = caster.getY();
        final int heading = caster.getHeading();
        final int count = targets == null ? 0 : targets.size();
        final int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            final L1Character target = targets.get(i);
            if (target != null) ids[i] = target.getId();
        }
        final int seq = SEQUENCE.incrementAndGet();
        if (caster instanceof L1PcInstance)
            send((L1PcInstance) caster, id, x, y, heading, seq, gfx, action, ids);
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(caster))
            send(pc, id, x, y, heading, seq, gfx, action, ids);
    }

    private static boolean send(L1PcInstance pc, int id, int x, int y, int heading,
                                int seq, int gfx, int action, int[] ids) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final long size = 26L + (long) ids.length * 6L;
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
            b.writeC(0);
            b.writeH(0);
            b.writeH(ids.length);
            for (int targetId : ids) {
                b.writeD(targetId);
                b.writeC(0x20);
                b.writeC(ActionCodes.ACTION_Damage);
            }
            b.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
