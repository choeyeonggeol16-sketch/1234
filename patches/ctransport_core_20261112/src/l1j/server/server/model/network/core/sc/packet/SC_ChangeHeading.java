package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_ChangeHeading {
    private static final int WIRE_SIZE = 2 + 6;

    private SC_ChangeHeading() {}

    public static boolean send(CIOClient client, int objectId, int heading) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_DIRECTION);
            buffer.writeD(objectId);
            buffer.writeC(heading);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int objectId, int heading) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), objectId, heading);
    }

    private static void broadcast0(L1Character anchor, int objectId, int heading, boolean fast) {
        if (anchor == null) return;
        byte[] wire = java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_CHANGE_DIRECTION).putInt(objectId).put((byte) heading).array();
        l1j.server.server.model.network.core.CSharedBroadcast.send(anchor, wire, fast, -1, null,
                client -> send(client, objectId, heading));
    }
    public static void broadcast(L1Character anchor, int objectId, int heading) { broadcast0(anchor, objectId, heading, false); }
    public static void broadcastFast(L1Character anchor, int objectId, int heading) { broadcast0(anchor, objectId, heading, true); }

    public static void broadcast(L1Character object) {
        if (object != null) broadcast(object, object.getId(), object.getHeading());
    }

    public static void broadcastFast(L1Character object) {
        if (object != null) broadcastFast(object, object.getId(), object.getHeading());
    }

}
