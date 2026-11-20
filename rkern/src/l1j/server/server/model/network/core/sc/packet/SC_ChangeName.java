package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_ChangeName {
    private SC_ChangeName() {}

public static boolean send(CUserSocket client, int objectId, String name) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (name == null ? 0 : (name.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_DESC);
            buffer.writeD(objectId);
            buffer.writeS(name != null ? name : "");
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, String name) {
        return pc != null && send(pc.getNetConnection(), objectId, name);
    }

    public static void broadcast(L1Character anchor, int objectId, String name) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWritten(anchor, b -> { b.put((byte)Opcodes.S_CHANGE_DESC).putInt(objectId); l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,name); b.put((byte)0); }, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int objectId, String name) {
        if (anchor == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWritten(anchor, b -> { b.put((byte)Opcodes.S_CHANGE_DESC).putInt(objectId); l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,name); b.put((byte)0); }, true, -1, null);
    }

}
