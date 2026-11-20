package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CUserSession;

public final class SC_ChatPacket {

    // Direct SEND variants for immediate chat sends; cached/broadcast packets stay immutable.
    public static boolean sendDirect(L1PcInstance recipient, L1PcInstance source, String chat) {
        return sendDirectRaw(recipient, MODE_MSG_TYPE1, Opcodes.S_MESSAGE, 1, 0,
                chat == null ? "" : chat, "", 0, 0);
    }

    public static boolean sendDirect(L1PcInstance recipient, L1PcInstance source, String chat, int test) {
        return sendDirectRaw(recipient, MODE_SAY_TEST, Opcodes.S_SAY, 15,
                source == null ? 0 : source.getId(), chat == null ? "" : chat, "", 0, 0);
    }

    public static boolean sendDirect(L1PcInstance recipient, String chat) {
        return sendDirectRaw(recipient, MODE_MSG_GLOBAL, Opcodes.S_MESSAGE, 0x0F,
                0, chat == null ? "" : chat, "", 0, 0);
    }

    public static boolean sendDirect(L1PcInstance recipient, String chat, int opcode) {
        return sendDirectRaw(recipient, MODE_OPCODE_TYPE2, opcode, 2,
                0, chat == null ? "" : chat, "", 0, 0);
    }

    /** The L1 length field is 16-bit, measured in encoded wire bytes.
     * A prior 4x-char precheck rejected valid long MS949 broadcasts, even
     * when the actual packet fit. Avoid encoding in the common fast case.
     */
    private static boolean fitsBroadcastChat(String text, boolean includeObjectId) {
        final long fixed=5L+(includeObjectId?4L:0L); // header, opcode, type, NUL
        if(fixed+2L*text.length()<=0xFFFFL)return true;
        return fixed+text.getBytes(java.nio.charset.Charset.forName("MS949")).length<=0xFFFFL;
    }

    public static void broadcastMessage(String chat) {
        final String safe=chat==null?"":chat;
        if(!fitsBroadcastChat(safe,false))return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(
                b -> writeNativeChat(b,Opcodes.S_MESSAGE,1,0,safe,false));
    }

    public static void broadcastOpcode(String chat, int opcode) {
        final String safe=chat==null?"":chat;
        if(!fitsBroadcastChat(safe,false))return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(
                b -> writeNativeChat(b,opcode,2,0,safe,false));
    }

    public static boolean sendComplex0(L1PcInstance receiver, L1PcInstance source, String chat, int opcode) {
        if (source == null) return false;
        final String name = source.getName() == null ? "" : source.getName();
        return sendDirectRaw(receiver, MODE_COMPLEX_0, opcode, 0, source.getId(),
                name + ": " + (chat == null ? "" : chat), "", 0, 0);
    }

    public static void broadcastComplex0(L1PcInstance source, String chat, int opcode) {
        if(source==null)return;
        final String name=source.getName()==null?"":source.getName();
        final String formatted=name+": "+(chat==null?"":chat);
        if(!fitsBroadcastChat(formatted,true))return;
        final L1World world=L1World.getInstance();
        final java.util.List<L1PcInstance> visible=world.borrowVisiblePlayerFast(source);
        try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(visible,
                b -> writeNativeChat(b,opcode,0,source.getId(),formatted,true));
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }


    // Original sharedWire() is kept as a test oracle and for 1:1 callers.
    // The broadcast itself always writes directly into the pooled native slab.
    private static void writeNativeChat(java.nio.ByteBuffer b,int opcode,
            int type,int objectId,String text,boolean includeObjectId) {
        b.put((byte)opcode).put((byte)type);
        if(includeObjectId)b.putInt(objectId);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,text);
        b.put((byte)0);
    }

    /** Full immutable frame matching MODE_MSG_TYPE1, MODE_OPCODE_TYPE2,
     * MODE_SAY_TEST and MODE_COMPLEX_0: S_*(opcode), C(type),
     * optional D(objectId), MS949 nul-terminated text. Native transport
     * applies receiver-specific encryption after enqueue.
     */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int opcode,int type,int objectId,String text,boolean includeObjectId) {
        final String value=text==null?"":text;
        if(!fitsBroadcastChat(value,includeObjectId))return null;
        return b -> writeNativeChat(b,opcode,type,objectId,value,includeObjectId);
    }
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeTargetWriter(String targetName,int type,String chat) {
        final String value=(targetName==null?"":targetName)+": "+(chat==null?"":chat);
        if(!fitsBroadcastChat(value,false))return null;
        return nativeWriter(Opcodes.S_MESSAGE,type,0,value,false);
    }



    
    private static boolean sendDirectRaw(L1PcInstance recipient, int mode, int opcode,
            int type, int objectId, String text1, String text2, int x, int y) {
        if (recipient == null || mode == 0) return false;
        final CUserSession socket = recipient.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket c = socket.getConnection();
        if (c == null || !c.isClientConnected()) return false;
        final long capacity = 32L + 4L * ((long) text1.length() + text2.length());
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = c.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            writePayload(buffer, mode, opcode, type, objectId, text1, text2, x, y);
            committed = c.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) c.abortDirectFrame(frame);
        }
    }

	// 패킷 직렬화 서브 모드 정의
	private static final int MODE_TARGET_MSG   = 1;
	private static final int MODE_TELL         = 2;
	private static final int MODE_MSG_TYPE1    = 3;
	private static final int MODE_MSG_GLOBAL   = 4;
	private static final int MODE_SAY_TEST     = 5;
	private static final int MODE_OPCODE_TYPE2 = 6;

	// 복합 채팅 서브 모드
	private static final int MODE_COMPLEX_0     = 10; // opcode + type + id + text
	private static final int MODE_COMPLEX_2     = 12; // opcode + type + id + text + x + y
	private static final int MODE_COMPLEX_SIMPLE = 13; // opcode + type + text
	private static final int MODE_COMPLEX_14    = 14; // opcode + type + id + text
	private static final int MODE_COMPLEX_16    = 16; // opcode + text1 + text2

    private SC_ChatPacket() {}


    
    public static boolean sendTargetMsg(L1PcInstance receiver, String targetName, int type, String chat) {
        final String formatted = "[" + (targetName == null ? "" : targetName) + "] " + (chat == null ? "" : chat);
        return sendDirectRaw(receiver, MODE_TARGET_MSG, Opcodes.S_MESSAGE, type, 0, formatted, "", 0, 0);
    }

	private static void writePayload(CPacketWriteBuffer buffer, int mode, int opcode, int type, int objectId, String text1, String text2, int x, int y) {
		if (mode == 0) {
			return;
		}

		switch (mode) {
		case MODE_TARGET_MSG:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeS(text1);
			break;

		case MODE_TELL:
			buffer.writeC(opcode);
			buffer.writeS(text1);
			buffer.writeS(text2);
			break;

		case MODE_MSG_TYPE1:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeS(text1);
			break;

		case MODE_MSG_GLOBAL:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeD(objectId);
			buffer.writeS(text1);
			break;

		case MODE_SAY_TEST:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeD(objectId);
			buffer.writeS(text1);
			break;

		case MODE_OPCODE_TYPE2:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeS(text1);
			break;

		case MODE_COMPLEX_0:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeD(objectId);
			buffer.writeS(text1);
			break;

		case MODE_COMPLEX_2:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeD(objectId);
			buffer.writeS(text1);
			buffer.writeH(x);
			buffer.writeH(y);
			break;

		case MODE_COMPLEX_SIMPLE:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeS(text1);
			break;

		case MODE_COMPLEX_14:
			buffer.writeC(opcode);
			buffer.writeC(type);
			buffer.writeD(objectId);
			buffer.writeS(text1);
			break;

		case MODE_COMPLEX_16:
			buffer.writeC(opcode);
			buffer.writeS(text1);
			buffer.writeS(text2);
			break;

		default:
			break;
		}
	}


}
