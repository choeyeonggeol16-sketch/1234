package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_CharCreateStatus {
    private SC_CharCreateStatus() {}

    public static boolean send(CIOClient client, int reason) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(12);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CREATE_CHARACTER_CHECK);
            buffer.writeC(reason);
            buffer.writeD(0);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int reason) {
        return pc != null && send(pc.getNetConnection(), reason);
    }
    public static final int REASON_OK = 0x02;
    public static final int REASON_ALREADY_EXSISTS = 0x06;
    public static final int REASON_INVALID_NAME = 0x09;
    public static final int REASON_WRONG_AMOUNT = 0x15;

}
