package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_Trap {
    private SC_Trap() {}
    public static boolean send(CIOClient client, int x, int y, int objectId, String name) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(49 + (name == null ? 0 : (name.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PUT_OBJECT);
            buffer.writeH(x);
            buffer.writeH(y);
            buffer.writeD(objectId);
            buffer.writeH(7);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeD(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeS(name == null ? "" : name);
            buffer.writeC(0);
            buffer.writeD(0);
            buffer.writeD(0);
            buffer.writeC(255);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeH(65535);
            buffer.writeD(0);
            buffer.writeC(8);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int x, int y, int objectId, String name) {
        return pc != null && send(pc.getNetConnection(), x, y, objectId, name);
    }

}
