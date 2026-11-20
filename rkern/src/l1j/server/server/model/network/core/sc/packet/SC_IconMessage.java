package l1j.server.server.model.network.core.sc.packet;

import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_IconMessage {
    private static final int TEXT_MESSAGE = 0x40;
    private static final MJSimpleRgb GM_COLOR = new MJSimpleRgb(249, 68, 253);
    private static final int INDEX_MESSAGE = 0x67;

    private SC_IconMessage() {}


    /** Macro icon uses exactly GM_COLOR, surface 28, duration 60.
     * Oversized writeS2 payload retains the normal direct-writer exception,
     * without falling back to recipient-by-recipient serialization.
     */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeGmWriter(String msg) {
        final String text=msg==null?"":msg;
        final byte[] rgb=GM_COLOR.get_bytes();
        if(!textFitsS2(text)||66L+((long)text.length()<<2)+rgb.length>0xFFFFL)
            throw new IllegalStateException("macro icon frame overflow");
        return b -> {b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)TEXT_MESSAGE);
            putVar(b,0x08);putVar(b,28*2);putVar(b,0x12);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949S2(b,text);
            putVar(b,0x1a);b.put(rgb);putVar(b,0x20);putVar(b,60);b.putShort((short)0);};
    }
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeIndexWriter(int index,MJSimpleRgb color,int surface,int duration) {
        final byte[] rgb=color==null?null:color.get_bytes();
        return b -> {b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)INDEX_MESSAGE);
            b.put((byte)0x08);putVar(b,surface*2);
            b.put((byte)0x10);putVar(b,index*2);b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);putVar(b,duration);b.putShort((short)0);};
    }


    public static boolean sendGm(L1PcInstance pc, String message) {
        return send(pc, message, GM_COLOR, 28, 60);
    }

    public static boolean send(L1PcInstance pc, String message, MJSimpleRgb color, int surface, int duration) {
        return sendInternal(pc, message, 0, color, surface, duration, false);
    }


    
    public static boolean send(L1PcInstance pc, int index, MJSimpleRgb color, int surface, int duration) {
        return sendInternal(pc, "", index, color, surface, duration, true);
    }

    public static void broadcast(String message, MJSimpleRgb color, int surface, int duration) {
        final String text=message==null?"":message;
        // The original sharedWire() rejected writeS2 lengths above 255
        // before sending to any recipient. Native direct encoding must not
        // turn oversized GM/macro messages into uncaught game-thread errors.
        if(!textFitsS2(text)||66L+((long)text.length()<<2)+(color==null?0:color.get_bytes().length)>0xFFFFL)return;
        final byte[] rgb=color==null?null:color.get_bytes();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)TEXT_MESSAGE);
            putVar(b,0x08);putVar(b,surface*2);putVar(b,0x12);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949S2(b,text);
            putVar(b,0x1a);
            if(rgb!=null)b.put(rgb);
            putVar(b,0x20);putVar(b,duration);
            b.putShort((short)0);
        });
    }

    public static void broadcast(int index, MJSimpleRgb color, int surface, int duration) {
        final byte[] rgb=color==null?null:color.get_bytes();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)INDEX_MESSAGE);
            b.put((byte)0x08);putVar(b,surface*2);
            b.put((byte)0x10);putVar(b,index*2);
            b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);putVar(b,duration);
            b.putShort((short)0);
        });
    }

    /** Exact encoded-byte limit, without allocation on the common short path.
     * MS949 emits at most two bytes for one UTF-16 code unit; the long path
     * uses the SAME Java charset replacement policy as the old writer.
     */
    private static boolean textFitsS2(String value) {
        return value.length() <= 127 || value.getBytes(
                java.nio.charset.Charset.forName("MS949")).length <= 255;
    }

    private static boolean sendInternal(L1PcInstance pc, String message, int index, MJSimpleRgb color,
                                        int surface, int duration, boolean indexMode) {
        if (pc == null) return false;
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket;
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

    private static void writePayload(CPacketWriteBuffer buffer, String msg, int index, byte[] rgb,
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

}
