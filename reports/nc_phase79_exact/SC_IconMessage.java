package l1j.server.server.model.network.core.sc.packet;

import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_IconMessage {
    private static final int TEXT_MESSAGE = 0x40;
    private static final MJSimpleRgb GM_COLOR = new MJSimpleRgb(249, 68, 253);
    private static final int INDEX_MESSAGE = 0x67;

    private SC_IconMessage() {}


    /** Macro icon uses exactly GM_COLOR, surface 28, duration 60.
     * Oversized writeS2 payload retains the normal direct-writer exception,
     * without falling back to recipient-by-recipient serialization.
     */
    public static byte[] sharedGmWire(String message) {
        final byte[] wire=sharedWire(message,0,GM_COLOR,28,60,false);
        if(wire==null)throw new IllegalStateException("macro icon frame overflow");
        return wire;
    }

    public static boolean sendGm(L1PcInstance pc, String message) {
        return send(pc, message, GM_COLOR, 28, 60);
    }

    public static boolean send(L1PcInstance pc, String message, MJSimpleRgb color, int surface, int duration) {
        return sendInternal(pc, message, 0, color, surface, duration, false);
    }


    /** The existing index-mode S_EXTENDED_PROTOBUF 0x67 frame.
     * No per-recipient writer needed for a fixed siege notice.
     */
    public static byte[] sharedIndexWire(int index,MJSimpleRgb color,int surface,int duration){
        return sharedWire("",index,color,surface,duration,true);
    }

    public static boolean send(L1PcInstance pc, int index, MJSimpleRgb color, int surface, int duration) {
        return sendInternal(pc, "", index, color, surface, duration, true);
    }

    public static void broadcast(String message, MJSimpleRgb color, int surface, int duration) {
        final byte[] wire=sharedWire(message, 0, color, surface, duration, false);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }

    public static void broadcast(int index, MJSimpleRgb color, int surface, int duration) {
        final byte[] wire=sharedWire("", index, color, surface, duration, true);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }

    private static boolean sendInternal(L1PcInstance pc, String message, int index, MJSimpleRgb color,
                                        int surface, int duration, boolean indexMode) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String msg = message == null ? "" : message;
        final byte[] rgb = color == null ? null : color.get_bytes();
        final long needed = 64L + (indexMode ? 0L : ((long) msg.length() << 2))
                + (rgb == null ? 0 : rgb.length);
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), msg, index, rgb, surface, duration, indexMode);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static void writePayload(WriteBuffer buffer, String msg, int index, byte[] rgb,
                                     int surface, int duration, boolean indexMode) {
        buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
        buffer.writeH(indexMode ? INDEX_MESSAGE : TEXT_MESSAGE);
        if (indexMode) {
            buffer.writeC(0x08);
            buffer.writeBit(surface * 2);
            buffer.writeC(0x10);
            buffer.writeBit(index * 2);
            buffer.writeC(0x1A);
            if (rgb != null) buffer.writeByte(rgb);
            buffer.writeC(0x20);
            buffer.writeBit(duration);
        } else {
            buffer.writeBit(0x08);
            buffer.writeBit(surface * 2);
            buffer.writeBit(0x12);
            buffer.writeS2(msg);
            buffer.writeBit(0x1A);
            if (rgb != null) buffer.writeByte(rgb);
            buffer.writeBit(0x20);
            buffer.writeBit(duration);
        }
        buffer.writeH(0);
    }

    private static void putVar(java.nio.ByteBuffer b,long value) {
        if(value<0L)value&=0xffffffffL;
        while((value & ~0x7fL)!=0L){
            b.put((byte)((value&0x7fL)|0x80L));
            value>>>=7;
        }
        b.put((byte)value);
    }

    private static byte[] sharedWire(String msg,int index,MJSimpleRgb color,
            int surface,int duration,boolean indexMode) {
        final String text=msg==null?"":msg;
        final byte[] rgb=color==null?null:color.get_bytes();
        final byte[] bytes=indexMode?null:text.getBytes(java.nio.charset.Charset.forName("MS949"));
        // Source's writeS2() rejects encoded lengths above 255. Fall back
        // to unchanged direct handler, which preserves that validation.
        if(!indexMode && bytes.length>255)return null;
        final long reserve=64L+(indexMode?0L:((long)text.length()<<2))
                           +(rgb==null?0:rgb.length)+2;
        if(reserve>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)reserve)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.position(2);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        b.putShort((short)(indexMode?INDEX_MESSAGE:TEXT_MESSAGE));
        if(indexMode){
            b.put((byte)0x08);putVar(b,surface*2);
            b.put((byte)0x10);putVar(b,index*2);
            b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);putVar(b,duration);
        } else {
            putVar(b,0x08);putVar(b,surface*2);
            putVar(b,0x12);
            b.put((byte)bytes.length).put(bytes);
            putVar(b,0x1a);
            if(rgb!=null)b.put(rgb);
            putVar(b,0x20);putVar(b,duration);
        }
        b.putShort((short)0);
        final int size=b.position();
        b.putShort(0,(short)size);
        return java.util.Arrays.copyOf(b.array(),size);
    }
}
