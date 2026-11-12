package l1j.server.server.model.network.core.sc.packet;

import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

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

    public static boolean sendWhisper(L1PcInstance receiver, String msg, String sender) {
        return sendPrepared(receiver, CHAT_WHISPER, msg, null, sender, 0, false, 0, 0, 0, 0);
    }

    public static boolean sendNotice(L1PcInstance receiver, String msg, String sender) {
        return sendPrepared(receiver, CHAT_PLEDGE_NOTICE, msg, null, sender, 0, false, 0, 0, 0, 0);
    }

    public static boolean send(L1PcInstance receiver, int type, String msg, MJSimpleRgb rgb,
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
        final CIOClient socket = receiver.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String text = msg == null ? "" : msg;
        final long size = 64L + ((long) text.length() << 1)
                + (sender == null ? 0L : ((long) sender.length() << 1))
                + (rgbBytes == null ? 0L : rgbBytes.length);
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), type, text, rgbBytes, sender, flag30,
                    hasObj, objId, x, y, rank);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

	private static void writePayload(WriteBuffer buffer, int type, String msg, byte[] rgbBytes, String sender, int flag30, boolean hasObj, int objId, int x, int y, int rank) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeH(SC_CHAT_MESSAGE_NOTI_PACKET);

		buffer.writeC(0x08);
		buffer.writeBit(0x00);
		buffer.writeC(0x10);
		buffer.writeC(type);
		buffer.writeC(0x1A);
		buffer.writeS2(msg);

		if (rgbBytes != null && rgbBytes.length > 0) {
			buffer.writeC(0x22);
			buffer.writeByte(rgbBytes);
		}

		if (sender != null) {
			buffer.writeC(0x2A);
			buffer.writeS2(sender);
		}

		buffer.writeC(0x30);
		buffer.writeC(flag30);

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
			buffer.writeC(rank);
		}

		buffer.writeH(0x00);
	}
}