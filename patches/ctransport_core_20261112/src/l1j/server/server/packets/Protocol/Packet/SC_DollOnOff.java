package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_DollOnOff extends ProtoPacket {

	private final int _dollTime;
	private final int _dollType;
	private final int _itemId;
	private final byte[] _statusBytes;

	public static SC_DollOnOff create(L1DollInstance doll, L1ItemInstance item, int dollTime, boolean onoff) {
		final int time   = onoff ? dollTime : 0;
		final int type   = onoff && doll != null ? doll.getDollType() : 0;
		final int itemId = onoff && item != null ? item.getId() : 0;
		final byte[] status = item != null ? item.getStatusBytes() : null;

		return new SC_DollOnOff(time, type, itemId, status);
	}

	private SC_DollOnOff(int dollTime, int dollType, int itemId, byte[] statusBytes) {
		_dollTime = dollTime;
		_dollType = dollType;
		_itemId = itemId;
		_statusBytes = statusBytes;
	}

	@Override
	public int msgId() {
		return ProtoMsg.SC_DOLL_ON_OFF;
	}

	@Override
	protected void body(ProtoWriter w) {
		w.int32(1, _dollTime)
		 .int32(2, _dollType)
		 .int32(3, _itemId);

		if (_statusBytes != null && _statusBytes.length > 0)
			w.bytes(4, _statusBytes);
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _dollTime, int _dollType, int _itemId, byte[] _statusBytes) {
        w.int32(1, _dollTime).int32(2, _dollType).int32(3, _itemId);
            if (_statusBytes != null && _statusBytes.length > 0) w.bytes(4, _statusBytes);
    }

    public static boolean sendFields(L1PcInstance recipient, int _dollTime, int _dollType, int _itemId, byte[] _statusBytes) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _dollTime, _dollType, _itemId, _statusBytes);
    }

    public static boolean sendFields(CIOClient recipient, int _dollTime, int _dollType, int _itemId, byte[] _statusBytes) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_DOLL_ON_OFF, Proto.capacityBound(1024L + (_statusBytes == null ? 0L : _statusBytes.length)));
        if (writer == null) return false;
        try {
            writeFields(writer, _dollTime, _dollType, _itemId, _statusBytes);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _dollTime, int _dollType, int _itemId, byte[] _statusBytes) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _dollTime, _dollType, _itemId, _statusBytes);
        return writer.finishWithTrailer(ProtoMsg.SC_DOLL_ON_OFF);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int dollTime, int dollType, int itemId, byte[] statusBytes) {
        int proto30_dollTime = 0;
        int proto30_dollType = 0;
        int proto30_itemId = 0;
        byte[] proto30_statusBytes = null;

            proto30_dollTime = dollTime;
            proto30_dollType = dollType;
            proto30_itemId = itemId;
            proto30_statusBytes = statusBytes;

        return sendFields(protoRecipient, proto30_dollTime, proto30_dollType, proto30_itemId, proto30_statusBytes);
    }

    public static boolean sendCreate(CIOClient protoRecipient, L1DollInstance doll, L1ItemInstance item, int dollTime, boolean onoff) {
        final int time   = onoff ? dollTime : 0;
                final int type   = onoff && doll != null ? doll.getDollType() : 0;
                final int itemId = onoff && item != null ? item.getId() : 0;
                final byte[] status = item != null ? item.getStatusBytes() : null;

                return sendCtor(protoRecipient, time, type, itemId, status);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, L1DollInstance doll, L1ItemInstance item, int dollTime, boolean onoff) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), doll, item, dollTime, onoff);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int dollTime, int dollType, int itemId, byte[] statusBytes) { return sendCtor(protoRecipient, dollTime, dollType, itemId, statusBytes); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int dollTime, int dollType, int itemId, byte[] statusBytes) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), dollTime, dollType, itemId, statusBytes); }

}
