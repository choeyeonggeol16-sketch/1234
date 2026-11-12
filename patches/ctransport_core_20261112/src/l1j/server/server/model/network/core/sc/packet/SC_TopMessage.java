package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.L1World;

public final class SC_TopMessage {
    private SC_TopMessage() {}
    public static boolean send(CIOClient client, String msg) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (msg == null ? 0 : (msg.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(121);
            buffer.writeC(84);
            buffer.writeC(2);
            buffer.writeS(msg == null ? "" : msg);
            buffer.writeC(0);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, String msg) {
        return pc != null && send(pc.getNetConnection(), msg);
    }
    public static void broadcastAll(String msg) {
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers())
            send(pc, msg);
    }

}
