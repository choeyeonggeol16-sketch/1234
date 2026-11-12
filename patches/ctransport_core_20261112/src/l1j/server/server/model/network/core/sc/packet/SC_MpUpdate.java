package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_MpUpdate {
    private static final int WIRE_SIZE = 2 + 5;

    private SC_MpUpdate() {
    }

    public static boolean send(CIOClient client, int currentMp, int maxMp) {
        if (client == null || !client.isConnected()) {
            return false;
        }
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) {
            return false;
        }
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) {
            return false;
        }
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MANA_POINT);
            buffer.writeH(clamp(currentMp));
            buffer.writeH(clamp(maxMp));
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    public static boolean send(L1PcInstance pc, int currentMp, int maxMp) {
        return pc != null && send(pc.getNetConnection(), currentMp, maxMp);
    }

    private static int clamp(int value) {
        return value < 1 ? 1 : value > 32767 ? 32767 : value;
    }
}
