package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_WarStartMessage {
    private static final String[] names = {
        "켄트성", "오크 요새", "윈다우드성", "기란성", "하이네성", "지저성", "아덴성"
    };
    private SC_WarStartMessage() {}

    public static String[] snapshot() {
        synchronized (names) {
            for (L1Clan clan : L1World.getInstance().getAllClans()) {
                final int index = clan.getCastleId() - 1;
                if (index > 0 && index < names.length) names[index] = clan.getClanName();
            }
            return names.clone();
        }
    }

    public static boolean send(L1PcInstance pc, String[] clanNames) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket conn = pc.getNetConnection().getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        int length = 5;
        for (String name : clanNames) length += 1 + (name == null ? 0 : name.length() * 2);
        final CIOFrame frame = conn.beginDirectFrame(length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x4E);
            buffer.writeC(0x07);
            for (String name : clanNames) buffer.writeS(name);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
