package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_MarkSee {
    private SC_MarkSee() {}
    public static boolean send(L1PcInstance pc, int type, boolean on) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket conn = pc.getNetConnection().getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        List<String> names = null;
        int length = 7;
        if (type == 2 && on) {
            names = new ArrayList<>();
            for (L1Clan clan : L1World.getInstance().getAllClans()) {
                if (clan != null && clan.getClanName() != null) {
                    final String name = clan.getClanName();
                    names.add(name);
                    length += 1 + name.length() * 2;
                }
            }
        }
        if (type == 2) length += 4;
        final CIOFrame frame = conn.beginDirectFrame(length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeH(type);
            if (type == 2) {
                buffer.writeD(names == null ? 0 : names.size());
                if (names != null) {
                    for (String name : names) buffer.writeS(name);
                }
            }
            buffer.writeH(0);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
