package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;

public final class SC_ExplosionNoti {
    private SC_ExplosionNoti() {}
    public static boolean send(CIOClient client, int objectId, long mills) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(29);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(0x0332);
            buffer.writeC(0x08);
            buffer.writeBit(objectId);
            buffer.writeC(0x10);
            buffer.writeBit(mills);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int objectId, long mills) {
        return pc != null && send(pc.getNetConnection(), objectId, mills);
    }
    public static void broadcast(L1Character anchor, int objectId, long mills) {
        if(anchor==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId,mills), false, -1, null);
    }


    private static void writeVar(java.nio.ByteBuffer b,long value) {
        if(value<0L) value&=0xFFFFFFFFL;
        while((value & ~0x7fL)!=0L) {
            b.put((byte)((value&0x7fL)|0x80L));value>>>=7;
        }
        b.put((byte)(value&0x7fL));
    }
    private static byte[] sharedWire(int objectId,long mills) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(29)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.position(2);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x0332);
        b.put((byte)0x08);writeVar(b,objectId);
        b.put((byte)0x10);writeVar(b,mills);
        b.putShort((short)0);
        final int size=b.position();
        b.putShort(0,(short)size);
        return java.util.Arrays.copyOf(b.array(),size);
    }

}
