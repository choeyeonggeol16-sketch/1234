package l1j.server.server.model.network.core.sc.packet;


import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;

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
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket;
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

    private static void writeContent(CPacketWriteBuffer buffer, int type, String msg1, String msg2, String msg3, String msg4, String msg5) {
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
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(b -> b.put((byte)Opcodes.S_MESSAGE_CODE)
                .putShort((short)type).put((byte)0));
    }

    


    /**
     * Wire-compatible single-parameter message-code packet for shared fanout.
     * The same MS949 bytes and terminating NUL as CPacketWriteBuffer.writeS.
     */

    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int type) {
        return b -> b.put((byte)Opcodes.S_MESSAGE_CODE)
                     .putShort((short)type).put((byte)0);
    }
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int type,String message) {
        if(message==null)return nativeWriter(type);
        return b -> {
            b.put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)type).put((byte)1);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,message);b.put((byte)0);
        };
    }
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int type,String msg1,String msg2,String msg3) {
        final int n=(msg1==null?0:1)+(msg2==null?0:1)+(msg3==null?0:1);
        return b -> {
            b.put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)type).put((byte)n);
            if(msg1!=null){l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,msg1);b.put((byte)0);}
            if(msg2!=null){l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,msg2);b.put((byte)0);}
            if(msg3!=null){l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,msg3);b.put((byte)0);}
        };
    }



    

}
