package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_ElfIcon {
    private SC_ElfIcon() {}
    public static boolean send(CIOClient client, int a, int b, int c, int d) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x15);
            buffer.writeC(a);
            buffer.writeC(b);
            buffer.writeC(c);
            buffer.writeC(d);
            buffer.writeC(0);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int a, int b, int c, int d) {
        return pc != null && send(pc.getNetConnection(), a, b, c, d);
    }

}
