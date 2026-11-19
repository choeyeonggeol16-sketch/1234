package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_WhoAmount {
    private SC_WhoAmount() {}

    public static boolean send(CIOClient client, String amount) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(13 + (amount == null ? 0 : (amount.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MESSAGE_CODE);
            buffer.writeH(0x0051);
            buffer.writeC(0x01);
            buffer.writeS(amount != null ? amount : "");
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, String amount) {
        return pc != null && send(pc.getNetConnection(), amount);
    }

}
