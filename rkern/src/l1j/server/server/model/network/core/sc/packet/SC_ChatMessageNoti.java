package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.shared.SimpleRgb;

public final class SC_ChatMessageNoti {
    private static final int SC_CHAT_MESSAGE_NOTI_PACKET = 0x0204;
public static final int CHAT_NORMAL 			= 0x00;
	public static final int CHAT_WHISPER 			= 0x01;
	public static final int CHAT_SHOUT 				= 0x02;
	public static final int CHAT_WORLD 				= 0x03;
	public static final int CHAT_PLEDGE 			= 0x04;
	public static final int CHAT_HUNT_PARTY 		= 0x0B;
	public static final int CHAT_TRADE 				= 0x0C;
	public static final int CHAT_PLEDGE_PRINCE 		= 0x0D;
	public static final int CHAT_CHAT_PARTY 		= 0x0E;
	public static final int CHAT_PLEDGE_ALLIANCE	= 0x0F;
	public static final int CHAT_PLEDGE_NOTICE 		= 0x11;
	public static final int CHAT_CLASS 				= 0x16;
	public static final int CHAT_TEAM 				= 0x1D;
	public static final int CHAT_ARENA_TEAM 		= 0x1E;
	public static final int CHAT_ARENA_OBSERVER 	= 0x1F;
	public static final int CHAT_ROOM_ARENA_ALL 	= 0x20;

    private SC_ChatMessageNoti() {}


    /** Only the fixed macro form (flag30=0, no RGB/object/rank) is shared. */
    public static NativeWireWriter nativeMacroWriter(int type, String message, String sender) {
        final byte[] text = ProtoBroadcastFields.encode(message);
        final byte[] from = sender == null ? null : ProtoBroadcastFields.encode(sender);
        if (text == null || (sender != null && from == null)
                || frameSize(type, text, null, from, 0, false, 0, 0, 0, 0)
                   > CIOBufferPool.MAX_WIRE_BYTES) return null;
        return out -> {
            out.put((byte) Opcodes.S_EXTENDED_PROTOBUF).putShort((short) SC_CHAT_MESSAGE_NOTI_PACKET)
                    .put((byte) 0x08).put((byte) 0).put((byte) 0x10);
            ProtoBroadcastFields.putVarint(out, type);
            ProtoBroadcastFields.putBytes(out, 0x1a, text);
            ProtoBroadcastFields.putBytes(out, 0x2a, from);
            out.put((byte) 0x30).put((byte) 0).putShort((short) 0);
        };
    }


    public static boolean sendWhisper(L1PcInstance receiver, String msg, String sender) {
        return sendPrepared(receiver, CHAT_WHISPER, msg, null, sender, 0, false, 0, 0, 0, 0);
    }

    public static boolean sendNotice(L1PcInstance receiver, String msg, String sender) {
        return sendPrepared(receiver, CHAT_PLEDGE_NOTICE, msg, null, sender, 0, false, 0, 0, 0, 0);
    }

    public static boolean send(L1PcInstance receiver, int type, String msg, SimpleRgb rgb,
                               String sender, L1Object obj, int rank) {
        final byte[] rgbBytes = rgb == null ? null : rgb.get_bytes();
        final boolean hasObj = obj != null;
        return sendPrepared(receiver, type, msg, rgbBytes, sender, 1, hasObj,
                hasObj ? obj.getId() : 0, hasObj ? obj.getX() : 0,
                hasObj ? obj.getY() : 0, rank);
    }

    private static boolean sendPrepared(L1PcInstance receiver, int type, String msg, byte[] rgbBytes,
                                        String sender, int flag30, boolean hasObj,
                                        int objId, int x, int y, int rank) {
        if (receiver == null) return false;
        final CUserSocket connection = receiver.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;
        final byte[] text = ProtoBroadcastFields.encode(msg);
        final byte[] from = sender == null ? null : ProtoBroadcastFields.encode(sender);
        if (text == null || (sender != null && from == null)) return false;
        final int size = frameSize(type, text, rgbBytes, from, flag30, hasObj, objId, x, y, rank);
        if (size > CIOBufferPool.MAX_WIRE_BYTES) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), type, text, rgbBytes, from, flag30,
                    hasObj, objId, x, y, rank);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int frameSize(int type, byte[] text, byte[] rgb, byte[] sender,
                                 int flag30, boolean hasObj, int objId, int x, int y, int rank) {
        int size = 11 + ProtoBroadcastFields.varintSize(type) + ProtoBroadcastFields.varintSize(flag30)
                + ProtoBroadcastFields.bytesSize(text) + ProtoBroadcastFields.bytesSize(rgb)
                + ProtoBroadcastFields.bytesSize(sender);
        if (hasObj) size += 3 + ProtoBroadcastFields.varintSize(objId)
                + ProtoBroadcastFields.varintSize(x) + ProtoBroadcastFields.varintSize(y);
        if (rank > 0) size += 1 + ProtoBroadcastFields.varintSize(rank);
        return size;
    }

	private static void writePayload(Assemble buffer, int type, byte[] text, byte[] rgbBytes, byte[] sender, int flag30, boolean hasObj, int objId, int x, int y, int rank) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeH(SC_CHAT_MESSAGE_NOTI_PACKET);

		buffer.writeC(0x08);
		buffer.writeBit(0x00);
		buffer.writeC(0x10);
		buffer.writeBit(type);
		ProtoBroadcastFields.writeBytes(buffer, 0x1a, text);
		ProtoBroadcastFields.writeBytes(buffer, 0x22, rgbBytes);
		ProtoBroadcastFields.writeBytes(buffer, 0x2a, sender);

		buffer.writeC(0x30);
		buffer.writeBit(flag30);

		if (hasObj) {
			buffer.writeC(0x38);
			buffer.writeBit(objId);
			buffer.writeC(0x40);
			buffer.writeBit(x);
			buffer.writeC(0x48);
			buffer.writeBit(y);
		}

		if (rank > 0) {
			buffer.writeC(0x50);
			buffer.writeBit(rank);
		}

		buffer.writeH(0x00);
	}
}
