package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_LFCTrapEffect {
    private SC_LFCTrapEffect() {}
    /** Exact native FIFO frame for S_EFFECT/objectId/gfxId, one per thorn. */
    public static byte[] sharedWire(int objectId,int gfxId) {
        final byte[] wire=new byte[9];
        wire[0]=9;
        wire[2]=(byte)Opcodes.S_EFFECT;
        wire[3]=(byte)objectId;
        wire[4]=(byte)(objectId>>>8);
        wire[5]=(byte)(objectId>>>16);
        wire[6]=(byte)(objectId>>>24);
        wire[7]=(byte)gfxId;
        wire[8]=(byte)(gfxId>>>8);
        return wire;
    }

    public static boolean send(CIOClient client, int objectId, int gfxId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EFFECT);
            buffer.writeD(objectId);
            buffer.writeH(gfxId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int objectId, int gfxId) {
        return pc != null && send(pc.getNetConnection(), objectId, gfxId);
    }
}
