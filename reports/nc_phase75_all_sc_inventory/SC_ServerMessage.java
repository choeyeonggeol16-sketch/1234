package l1j.server.server.model.network.core.sc.packet;


import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_ServerMessage {

	public static final int NO_PLEDGE = 208;
	public static final int CANNOT_GLOBAL = 195;
	public static final int CANNOT_BOOKMARK_LOCATION = 214;
	public static final int USER_NOT_ON = 73;
	public static final int NOT_ENOUGH_MP = 278;
	public static final int YOU_FEEL_BETTER = 77;
	public static final int YOUR_WEAPON_BLESSING = 693;
	public static final int YOUR_Are_Slowed = 29;

    public static boolean send(L1PcInstance pc, int type) {
        return send(pc, type, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance pc, int type, String msg1) {
        return send(pc, type, msg1, null, null, null, null);
    }

    public static boolean send(L1PcInstance pc, int type, String msg1, String msg2) {
        return send(pc, type, msg1, msg2, null, null, null);
    }

    public static boolean send(L1PcInstance pc, int type, String msg1, String msg2, String msg3) {
        return send(pc, type, msg1, msg2, msg3, null, null);
    }

    public static boolean send(L1PcInstance pc, int type, String msg1, String msg2, String msg3, String msg4) {
        return send(pc, type, msg1, msg2, msg3, msg4, null);
    }

    public static boolean send(L1PcInstance pc, int type, String msg1, String msg2, String msg3, String msg4, String msg5) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        long needed = 16L;
        if (msg1 != null) needed += ((long) msg1.length() << 1) + 2L;
        if (msg2 != null) needed += ((long) msg2.length() << 1) + 2L;
        if (msg3 != null) needed += ((long) msg3.length() << 1) + 2L;
        if (msg4 != null) needed += ((long) msg4.length() << 1) + 2L;
        if (msg5 != null) needed += ((long) msg5.length() << 1) + 2L;
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeContent(frame.buffer(), type, msg1, msg2, msg3, msg4, msg5);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static void writeContent(WriteBuffer buffer, int type, String msg1, String msg2, String msg3, String msg4, String msg5) {
        buffer.writeC(Opcodes.S_MESSAGE_CODE);
        buffer.writeH(type);
        final int count = (msg1 != null ? 1 : 0) + (msg2 != null ? 1 : 0) + (msg3 != null ? 1 : 0) + (msg4 != null ? 1 : 0) + (msg5 != null ? 1 : 0);
        buffer.writeC(count);
        if (msg1 != null) buffer.writeS(msg1);
        if (msg2 != null) buffer.writeS(msg2);
        if (msg3 != null) buffer.writeS(msg3);
        if (msg4 != null) buffer.writeS(msg4);
        if (msg5 != null) buffer.writeS(msg5);
    }

    public static void broadcast(int type) {
        final byte[] wire=sharedWire(type);
        
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }

    


    /**
     * Wire-compatible single-parameter message-code packet for shared fanout.
     * The same MS949 bytes and terminating NUL as WriteBuffer.writeS.
     */
    public static byte[] sharedWire(int type, String message) {
        if(message==null)return sharedWire(type);
        final byte[] encoded=message.getBytes(
                java.nio.charset.Charset.forName("MS949"));
        final int length=7+encoded.length;
        if(length>0xffff)return null;
        final byte[] wire=new byte[length];
        wire[0]=(byte)length;
        wire[1]=(byte)(length>>>8);
        wire[2]=(byte)Opcodes.S_MESSAGE_CODE;
        wire[3]=(byte)type;
        wire[4]=(byte)(type>>>8);
        wire[5]=1;
        System.arraycopy(encoded,0,wire,6,encoded.length);
        return wire;
    }


    /** Shared S_MESSAGE_CODE constructor with exactly the three original
     * optional MS949 NUL-terminated arguments and the same argument count.
     */
    public static byte[] sharedWire(int type,String msg1,String msg2,String msg3) {
        final java.nio.charset.Charset charset=
                java.nio.charset.Charset.forName("MS949");
        final byte[] b1=msg1==null?null:msg1.getBytes(charset);
        final byte[] b2=msg2==null?null:msg2.getBytes(charset);
        final byte[] b3=msg3==null?null:msg3.getBytes(charset);
        final long needed=6L+(b1==null?0L:b1.length+1L)
                            +(b2==null?0L:b2.length+1L)
                            +(b3==null?0L:b3.length+1L);
        if(needed>0xffffL)return null;
        final byte[] wire=new byte[(int)needed];
        wire[0]=(byte)needed;
        wire[1]=(byte)(needed>>>8);
        wire[2]=(byte)Opcodes.S_MESSAGE_CODE;
        wire[3]=(byte)type;
        wire[4]=(byte)(type>>>8);
        wire[5]=(byte)((b1==null?0:1)+(b2==null?0:1)+(b3==null?0:1));
        int offset=6;
        if(b1!=null){System.arraycopy(b1,0,wire,offset,b1.length);offset+=b1.length+1;}
        if(b2!=null){System.arraycopy(b2,0,wire,offset,b2.length);offset+=b2.length+1;}
        if(b3!=null){System.arraycopy(b3,0,wire,offset,b3.length);offset+=b3.length+1;}
        if(offset!=wire.length)throw new IllegalStateException("server message size");
        return wire;
    }

    public static byte[] sharedWire(int type) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(6)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)6).put((byte)Opcodes.S_MESSAGE_CODE);
        b.putShort((short)type).put((byte)0);
        return b.array();
    }

}
