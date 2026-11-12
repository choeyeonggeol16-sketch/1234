package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_Horun {

    private SC_Horun() {}

    public static boolean send(CIOClient client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(38);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXCHANGEABLE_SPELL_LIST);
            buffer.writeC(0x08);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x01);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x02);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x03);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x04);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x05);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x06);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0x07);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc) {
        return pc != null && send(pc.getNetConnection());
    }

}
