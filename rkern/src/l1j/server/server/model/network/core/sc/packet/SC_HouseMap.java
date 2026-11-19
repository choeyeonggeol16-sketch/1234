package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_HouseMap {

    private SC_HouseMap() {}

    public static boolean send(CIOClient client, int objectId, String houseNumber) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(11);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            int number = 0;
            try {
                if (houseNumber != null && !houseNumber.isEmpty()) number = Integer.parseInt(houseNumber.trim());
            } catch (NumberFormatException ignored) {}
            buffer.writeC(Opcodes.S_AGIT_MAP);
            buffer.writeD(objectId);
            buffer.writeD(number);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, String houseNumber) {
        return pc != null && send(pc.getNetConnection(), objectId, houseNumber);
    }
}
