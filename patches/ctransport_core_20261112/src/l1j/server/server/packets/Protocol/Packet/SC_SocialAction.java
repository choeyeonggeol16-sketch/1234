package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_SocialAction extends ProtoPacket {

	private final int _id;
	private final int _type;
	private final int _code;

	public static SC_SocialAction get(int id, int type, int code) {
		return new SC_SocialAction(id, type, code);
	}

	public SC_SocialAction(int id, int type, int code) {
		_id = id;
		_type = type;
		_code = code;
	}

	@Override
	public int msgId() {
		return ProtoMsg.SC_SOCIAL_ACTION_NOTI;
	}

	@Override
	protected void body(ProtoWriter w) {
		w.int32(1, _id)
		 .int32(2, _type)
		 .int32(3, _code);
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _id, int _type, int _code) {
        w.int32(1, _id).int32(2, _type).int32(3, _code);
    }

    public static boolean sendFields(L1PcInstance recipient, int _id, int _type, int _code) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _id, _type, _code);
    }

    public static boolean sendFields(CIOClient recipient, int _id, int _type, int _code) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_SOCIAL_ACTION_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _id, _type, _code);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _id, int _type, int _code) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _id, _type, _code);
        return writer.finishWithTrailer(ProtoMsg.SC_SOCIAL_ACTION_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int id, int type, int code) {
        int proto62_id = 0;
        int proto62_type = 0;
        int proto62_code = 0;

            proto62_id = id;
            proto62_type = type;
            proto62_code = code;

        return sendFields(protoRecipient, proto62_id, proto62_type, proto62_code);
    }

    public static boolean sendGet(CIOClient protoRecipient, int id, int type, int code) {
        return sendCtor(protoRecipient, id, type, code);
    }

    public static boolean sendGet(L1PcInstance protoRecipient, int id, int type, int code) {
        return sendGet(protoRecipient == null ? null : protoRecipient.getNetConnection(), id, type, code);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int id, int type, int code) { return sendCtor(protoRecipient, id, type, code); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int id, int type, int code) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), id, type, code); }

}
