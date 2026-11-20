package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_ClanWareHouseHistory {
    private SC_ClanWareHouseHistory() {}

    /** Keep the history window protocol while returning zero rows. */
    public static void send(L1PcInstance pc) {
        if (pc == null || pc.getClanid() <= 0) return;
        final var client = pc.getNetConnection();
        if (client == null || !client.isConnected() || client.getActiveChar() != pc) return;
        final var connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return;
        final var frame = connection.beginDirectFrame(8);
        if (frame == null) return;
        boolean committed = false;
        try {
            final var buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(117);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
