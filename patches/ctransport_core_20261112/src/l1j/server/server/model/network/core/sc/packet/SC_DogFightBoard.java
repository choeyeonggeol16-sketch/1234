package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import l1j.server.DogFight.Game.MJDogFightGame;
import l1j.server.DogFight.History.MJDogFightHistory;
import l1j.server.DogFight.History.MJDogFightTicketInfo;
import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_DogFightBoard {
    private SC_DogFightBoard() {}

    public static boolean send(L1PcInstance pc, boolean isGm, ArrayList<MJDogFightTicketInfo> tickets) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        // Serialize a stable field snapshot; preserve the old 15-variable hypertext layout.
        final int count = tickets == null ? 0 : tickets.size();
        if (count > 8192) return false;
        final int maxFields = count == 2 ? 17 : count * 4 + Math.max(0, 5 - count) * 3;
        final String[] fields = new String[maxFields];
        int used = 0;
        if (tickets != null) {
            if (count == 2) {
                for (int i = 0; i < 3; ++i) fields[used++] = "-";
                used = append(fields, used, tickets.get(0), isGm);
                for (int i = 0; i < 3; ++i) fields[used++] = "-";
                used = append(fields, used, tickets.get(1), isGm);
                for (int i = 0; i < 3; ++i) fields[used++] = "-";
            } else {
                for (int i = 0; i < count; ++i) used = append(fields, used, tickets.get(i), isGm);
                for (int i = count; i < 5; ++i) {
                    fields[used++] = "-";
                    fields[used++] = "-";
                    fields[used++] = "-";
                }
            }
        }

        long capacity = 2L + 1 + 4 + 7 + 1 + 2;
        for (int i = 0; i < used; ++i) capacity += 1L + 2L * fields[i].length();
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(1);
            buffer.writeS("maeno4");
            buffer.writeC(0);
            buffer.writeH(15);
            for (int i = 0; i < used; ++i) buffer.writeS(fields[i]);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int append(String[] fields, int pos, MJDogFightTicketInfo info, boolean isGm) {
        if (isGm) {
            fields[pos++] = String.format("[%s]", info.get_corner_name());
            fields[pos++] = String.format("%.2f%%", info.get_dividend());
            fields[pos++] = String.format("%.1f%%", info.get_win_rate());
            final MJDogFightInstance fight = MJDogFightGame.getDogFight(info.get_corner_id());
            if (fight != null) fields[pos++] = String.format("[%d]", fight.getTotalPrice());
        } else {
            fields[pos++] = MJDogFightHistory.getInstance().get_leader_name(info.get_corner_id())
                    .replace("[Ȧ]", "").replace("[¦]", "");
            fields[pos++] = String.format("[%s]", info.get_corner_name());
            fields[pos++] = String.format("%.1f%%", info.get_win_rate());
        }
        return pos;
    }
}
