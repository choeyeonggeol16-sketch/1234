package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CUserSession;

public final class SC_GameStart {
    private SC_GameStart() {}


    /** Immutable fixed 10-byte start frame, S_EVENT 0x40 0x05. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter() { return b -> b.put((byte)Opcodes.S_EVENT).put((byte)0x40).put((byte)5).putInt(0).put((byte)0);
    }


    public static boolean send(CUserSession client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x40);
            buffer.writeC(0x05);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc) {
        return pc != null && send(pc.getNetConnection());
    }

}
