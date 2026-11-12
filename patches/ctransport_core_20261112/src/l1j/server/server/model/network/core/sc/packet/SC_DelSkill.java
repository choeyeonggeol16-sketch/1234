package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_DelSkill {
    private SC_DelSkill() {}

    public static boolean send(L1PcInstance pc, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2, int i3, int j3, int k3, int l3, int i4, int j4, int k4, int l4, int i5, int j5, int k5, int l5, int dk3, int bw1, int bw2, int bw3) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket connection = pc.getNetConnection().getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(36);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_REMOVE_SPELL);
            final int upper = i1 + j1 + k1 + l1;
            final int advanced = i2 + j2;
            buffer.writeC(upper > 0 && advanced == 0 ? 50 : advanced > 0 ? 100 : 32);
            buffer.writeC(i);
            buffer.writeC(j);
            buffer.writeC(k);
            buffer.writeC(l);
            buffer.writeC(i1);
            buffer.writeC(j1);
            buffer.writeC(k1);
            buffer.writeC(l1);
            buffer.writeC(i2);
            buffer.writeC(j2);
            buffer.writeC(k2);
            buffer.writeC(l2);
            buffer.writeC(i3);
            buffer.writeC(j3);
            buffer.writeC(k3);
            buffer.writeC(l3);
            buffer.writeC(i4);
            buffer.writeC(j4);
            buffer.writeC(k4);
            buffer.writeC(l4);
            buffer.writeC(i5);
            buffer.writeC(j5);
            buffer.writeC(k5);
            buffer.writeC(l5);
            buffer.writeC(dk3);
            buffer.writeC(bw1);
            buffer.writeC(bw2);
            buffer.writeC(bw3);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
