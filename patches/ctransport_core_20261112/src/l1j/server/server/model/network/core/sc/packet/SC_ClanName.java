package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_ClanName {
    private SC_ClanName() {}

    public static boolean send(CIOClient client, int objectId, String clanName, int emblemId, int rank) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(16 + (clanName == null ? 0 : (clanName.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE);
            buffer.writeD(objectId);
            buffer.writeS(clanName == null ? "" : clanName);
            buffer.writeC(0);
            buffer.writeD(emblemId);
            buffer.writeC(rank);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, L1PcInstance target, int emblemId, int rank) {
        if (pc == null) return false;
        final String name = target != null && rank > 0 && target.getClanname() != null ? target.getClanname() : "";
        return send(pc.getNetConnection(), target != null ? target.getId() : 0, name, emblemId, rank > 0 ? rank : 0);
    }
}
