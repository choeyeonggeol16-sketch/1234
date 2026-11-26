package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.IPacketWriter;

public final class SC_Disconnect {
    private SC_Disconnect() {}
    private static final IPacketWriter<CUserSocket> CLOSE_PACKET = new ClosePacket();

    private static final class ClosePacket extends IPacketWriter<CUserSocket> implements IPacketWriter.DirectTail {
        @Override public int maximumWireBytes() { return 9; }
        @Override protected boolean write(CUserSocket client, Assemble buffer) {
            writeBody(buffer);
            return true;
        }
    }

    public static IPacketWriter<CUserSocket> closePacket() { return CLOSE_PACKET; }

    private static void writeBody(Assemble buffer) {
        buffer.writeC(Opcodes.S_KICK);
        buffer.writeH(500);
        buffer.writeD(0);
    }

    public static boolean send(L1PcInstance pc) {
        return pc != null && send(pc.getNetConnection());
    }

    public static boolean send(CUserSocket client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeBody(frame.buffer());
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
