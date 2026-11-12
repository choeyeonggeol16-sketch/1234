package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;

public final class SC_Door {
    private SC_Door() {}

    public static boolean send(CIOClient client, int x, int y, int direction, int passable) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_ATTR);
            buffer.writeH(x);
            buffer.writeH(y);
            buffer.writeC(direction);
            buffer.writeC(passable);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int x, int y, int direction, int passable) {
        return pc != null && send(pc.getNetConnection(), x, y, direction, passable);
    }

    public static void broadcast(L1Character anchor, int x, int y, int direction, int passable) {
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, x, y, direction, passable);
    }
}
