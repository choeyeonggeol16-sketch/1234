package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_FISHING_NOT extends ProtoPacket {

	private final int _time;

	public SC_FISHING_NOT(int time) {
		_time = time;
	}

	public static SC_FISHING_NOT create(int time) {
		return new SC_FISHING_NOT(time);
	}

	@Override
	public int msgId() {
		return ProtoMsg.SC_FISHING_NOT;
	}

	@Override
	protected void body(ProtoWriter w) {
		w.int32(1, 1)
		 .int32(2, _time)
		 .int32(3, 2);
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _time) {
        w.int32(1, 1).int32(2, _time).int32(3, 2);
    }

    public static boolean sendFields(L1PcInstance recipient, int _time) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _time);
    }

    public static boolean sendFields(CIOClient recipient, int _time) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_FISHING_NOT, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _time);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _time) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _time);
        return writer.finishWithTrailer(ProtoMsg.SC_FISHING_NOT);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int time) {
        int proto33_time = 0;

            proto33_time = time;

        return sendFields(protoRecipient, proto33_time);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int time) {
        return sendCtor(protoRecipient, time);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int time) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), time);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int time) { return sendCtor(protoRecipient, time); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int time) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), time); }

}
