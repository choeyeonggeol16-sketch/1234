package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_ChangeAttr {
    private SC_ChangeAttr() {}

    public static boolean send(L1PcInstance pc, int x, int y, boolean width, boolean close) {
        if (pc == null) return false;
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_ATTR);
            buffer.writeH(x);
            buffer.writeH(y);
            buffer.writeB(width);
            buffer.writeB(close);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }


    /** Same nine wire bytes as the individual direct SC_ChangeAttr sender. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int x,int y,boolean width,boolean close) {
        return b -> b.put((byte)Opcodes.S_CHANGE_ATTR).putShort((short)x).putShort((short)y)
            .put((byte)(width?1:0)).put((byte)(close?1:0));
    }


}
