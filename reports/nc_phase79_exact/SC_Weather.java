package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Weather {
    private SC_Weather() {}

    public static boolean send(CIOClient client, int weather) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(4);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_WEATHER);
            buffer.writeC(weather);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int weather) {
        return pc != null && send(pc.getNetConnection(), weather);
    }

    public static void broadcastAll(int weather) {
        final byte[] wire=sharedWire(weather);
        
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }


    private static byte[] sharedWire(int weather) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)4).put((byte)Opcodes.S_WEATHER).put((byte)weather);
        return b.array();
    }

}
