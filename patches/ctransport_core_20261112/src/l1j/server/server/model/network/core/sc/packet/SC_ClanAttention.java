package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_ClanAttention {
    private SC_ClanAttention() {}

    public static boolean sendMent(L1PcInstance pc, boolean on, String name) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket connection = pc.getNetConnection().getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (name == null ? 0 : name.length() * 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeC(on ? 32 : 31);
            buffer.writeH(269);
            buffer.writeS(name == null ? "" : name);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendList(L1PcInstance pc, int count, List<String> source) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket connection = pc.getNetConnection().getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final ArrayList<String> snapshot = new ArrayList<>();
        int size = 2 + 1 + 2 + 4;
        if (source != null) {
            synchronized (source) {
                for (String name : source) {
                    if (name == null) continue;
                    snapshot.add(name);
                    size += 1 + name.length() * 2;
                }
            }
        }
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeH(2);
            buffer.writeD(count);
            for (int i = 0, n = snapshot.size(); i < n; i++) buffer.writeS(snapshot.get(i));
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
