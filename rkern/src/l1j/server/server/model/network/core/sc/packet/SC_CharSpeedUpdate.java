package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_CharSpeedUpdate {
    private SC_CharSpeedUpdate() {}

public static boolean send(CUserSocket client, int objectId, int speed, int level) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_LEVEL);
            buffer.writeD(objectId);
            buffer.writeC(speed);
            buffer.writeC(level);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, int speed, int level) {
        return pc != null && send(pc.getNetConnection(), objectId, speed, level);
    }

    public static void broadcast(L1Character anchor, int objectId, int speed, int level) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_CHAR_SPEED,
                Opcodes.S_CHANGE_LEVEL, objectId, speed, level, 0, false, -1, null);
    }

}
