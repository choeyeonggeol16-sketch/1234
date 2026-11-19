package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_OwnCharStatus2 {
    private SC_OwnCharStatus2() {}

    public static boolean send(L1PcInstance pc) {
        if (pc == null) return false;
        int str = 0, intel = 0, wis = 0, dex = 0, con = 0, cha = 0;
        if (pc.getAbility() != null) {
            str = pc.getAbility().getTotalStr();
            intel = pc.getAbility().getTotalInt();
            wis = pc.getAbility().getTotalWis();
            dex = pc.getAbility().getTotalDex();
            con = pc.getAbility().getTotalCon();
            cha = pc.getAbility().getTotalCha();
        }
        final int weight = pc.getInventory() != null ? pc.getInventory().getWeight100() : 0;
        return send(pc.getNetConnection(), str, intel, wis, dex, con, cha, weight);
    }

    private static boolean send(CIOClient client, int str, int intel, int wis, int dex,
                                int con, int cha, int weight) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ABILITY_SCORES);
            buffer.writeC(str);
            buffer.writeC(intel);
            buffer.writeC(wis);
            buffer.writeC(dex);
            buffer.writeC(con);
            buffer.writeC(cha);
            buffer.writeC(weight);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
