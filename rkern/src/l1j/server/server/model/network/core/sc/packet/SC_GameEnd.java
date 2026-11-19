package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_GameEnd {
    private SC_GameEnd() {}


    /** Same fixed 10-byte packet as S_EVENT 0x46 direct writer. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter() { return b -> b.put((byte)Opcodes.S_EVENT).put((byte)0x46).put((byte)147).put((byte)92).put((byte)151).put((byte)220).put((byte)42).put((byte)74);
    }


    public static boolean send(CIOClient client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x46);
            buffer.writeC(147);
            buffer.writeC(92);
            buffer.writeC(151);
            buffer.writeC(220);
            buffer.writeC(42);
            buffer.writeC(74);
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
