package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_CastleMaster {

    private SC_CastleMaster() {}


    /** S_CASTLE_OWNER frame identical to the eight-byte direct writer. */

    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int type,int objectId) {
        return b -> b.put((byte)Opcodes.S_CASTLE_OWNER)
            .put((byte)type).putInt(objectId);
    }


    public static boolean send(CUserSocket client, int type, int objectId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CASTLE_OWNER);
            buffer.writeC(type);
            buffer.writeD(objectId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int type, int objectId) {
        return pc != null && send(pc.getNetConnection(), type, objectId);
    }

}
