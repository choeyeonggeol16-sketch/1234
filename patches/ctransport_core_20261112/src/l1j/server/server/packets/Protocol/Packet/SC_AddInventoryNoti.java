package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import java.util.LinkedList;
import java.util.List;

import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo;

/**
 * 인벤토리 아이템 추가 통지 (0x024C)
 * repeated ItemInfo(1) + on_start(bool 2) + owner_oid(uint32 3)
 */
public final class SC_AddInventoryNoti extends ProtoPacket {

	private final List<ItemInfo> _items = new LinkedList<>();

	private final List<byte[]> _rawItems = new LinkedList<>();

	private Boolean _onStart;    // null = 미전송
	private Integer _ownerOid;   // null = 미전송

	public SC_AddInventoryNoti add_encoded_item(byte[] encodedBody) {
		_rawItems.add(encodedBody);
		return this;
	}

	public SC_AddInventoryNoti add_item_info(ItemInfo item) {
		_items.add(item);
		return this;
	}

	public SC_AddInventoryNoti set_on_start(boolean v)  { _onStart = v; return this; }
	public SC_AddInventoryNoti set_owner_oid(int v)     { _ownerOid = v; return this; }

	@Override
	public int msgId() { return ProtoMsg.SC_ADD_INVENTORY_NOTI; }

	@Override
	protected void body(ProtoWriter w) {
		for (ItemInfo item : _items) {
			w.msg(1);
			item.body(w);
			w.end();
		}
		for (byte[] raw : _rawItems) {
			w.bytes(1, raw);
		}
		if (_onStart != null)  w.bool(2, _onStart);
		if (_ownerOid != null) w.uint32(3, _ownerOid);
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, long presentMask, List<ItemInfo> _items, List<byte[]> _rawItems, boolean _onStart, int _ownerOid) {
        for (ItemInfo item : _items) {
                w.msg(1);
                item.body(w);
                w.end();
            }
            for (byte[] raw : _rawItems) {
                w.bytes(1, raw);
            }
            if ((presentMask & (1L << 0)) != 0) w.bool(2, _onStart);
            if ((presentMask & (1L << 1)) != 0) w.uint32(3, _ownerOid);
    }

    public static boolean sendFields(L1PcInstance recipient, long presentMask, List<ItemInfo> _items, List<byte[]> _rawItems, boolean _onStart, int _ownerOid) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), presentMask, _items, _rawItems, _onStart, _ownerOid);
    }

    public static boolean sendFields(CIOClient recipient, long presentMask, List<ItemInfo> _items, List<byte[]> _rawItems, boolean _onStart, int _ownerOid) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ADD_INVENTORY_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, presentMask, _items, _rawItems, _onStart, _ownerOid);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(long presentMask, List<ItemInfo> _items, List<byte[]> _rawItems, boolean _onStart, int _ownerOid) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, presentMask, _items, _rawItems, _onStart, _ownerOid);
        return writer.finishWithTrailer(ProtoMsg.SC_ADD_INVENTORY_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        long proto6_present = 0L;
        List<ItemInfo> proto6_items = new LinkedList<>();
        List<byte[]> proto6_rawItems = new LinkedList<>();
        boolean proto6_onStart = false;
        int proto6_ownerOid = 0;

        return sendFields(protoRecipient, proto6_present, proto6_items, proto6_rawItems, proto6_onStart, proto6_ownerOid);
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }


    public static boolean sendItem(L1PcInstance recipient,
            l1j.server.server.model.Instance.L1ItemInstance item, boolean onStart, int ownerOid) {
        return ItemInfo.sendAddedItem(recipient, item, onStart, ownerOid);
    }

    public static boolean sendEncodedItem(L1PcInstance recipient, byte[] body, boolean onStart, int ownerOid) {
        return sendEncodedItem(recipient == null ? null : recipient.getNetConnection(), body, onStart, ownerOid);
    }

    public static boolean sendEncodedItem(CIOClient recipient, byte[] body, boolean onStart, int ownerOid) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ADD_INVENTORY_NOTI,
                Proto.capacityBound(32L + body.length));
        if (writer == null) return false;
        try {
            writer.bytes(1, body).bool(2, onStart).uint32(3, ownerOid);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    /** Existing login snapshot, written as a range without packet/list wrappers. */
    public static boolean sendEncoded(L1PcInstance recipient, byte[][] bodies,
            int from, int to, boolean onStart, int ownerOid) {
        return sendEncoded(recipient == null ? null : recipient.getNetConnection(), bodies, from, to, onStart, ownerOid);
    }

    public static boolean sendEncoded(CIOClient recipient, byte[][] bodies,
            int from, int to, boolean onStart, int ownerOid) {
        java.util.Objects.checkFromToIndex(from, to, bodies.length);
        long capacity = 32L;
        for (int i = from; i < to; i++)
            if (bodies[i] != null) capacity += ItemInfo.encodedWireSize(bodies[i]);
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ADD_INVENTORY_NOTI,
                Proto.capacityBound(capacity));
        if (writer == null) return false;
        try {
            for (int i = from; i < to; i++)
                if (bodies[i] != null) writer.bytes(1, bodies[i]);
            writer.bool(2, onStart).uint32(3, ownerOid);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }
}
