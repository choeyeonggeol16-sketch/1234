package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_GameOver {
    private SC_GameOver() {}


    /** Same fixed 10-byte packet as S_EVENT 0x45 direct writer. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter() { return b -> b.put((byte)Opcodes.S_EVENT).put((byte)0x45).put((byte)10).put((byte)109).put((byte)85).put((byte)208).put((byte)2).put((byte)220);
    }


    public static boolean send(CUserSocket client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x45);
            buffer.writeC(10);
            buffer.writeC(109);
            buffer.writeC(85);
            buffer.writeC(208);
            buffer.writeC(2);
            buffer.writeC(220);
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
