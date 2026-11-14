package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_SlotChange extends ProtoPacket {

	public static final int SLOT_CHANGE = 32;

	private final int _slotNumber;
	private final int[] _slot0Items;
	private final int[] _slot1Items;
	private final boolean _pcMode;
	private final boolean _valid;

	public static SC_SlotChange create(int type, L1PcInstance pc) {
		return new SC_SlotChange(type, pc);
	}

	public static SC_SlotChange create(int type, int slot) {
		return new SC_SlotChange(type, slot);
	}

	private SC_SlotChange(int type, L1PcInstance pc) {
		_pcMode = true;

		if (type != SLOT_CHANGE || pc == null) {
			_slotNumber = 0;
			_slot0Items = new int[0];
			_slot1Items = new int[0];
			_valid = false;
			return;
		}

		_slotNumber = pc.getSlotNumber();
		_slot0Items = snapshot(pc.getSlotItems(0));
		_slot1Items = snapshot(pc.getSlotItems(1));
		_valid = true;
	}

	private SC_SlotChange(int type, int slot) {
		_slotNumber = slot;
		_slot0Items = new int[0];
		_slot1Items = new int[0];
		_pcMode = false;
		_valid = type == SLOT_CHANGE;
	}

	@Override
	public int msgId() {
		return ProtoMsg.SC_SLOT_CHANGE;
	}

	@Override
	protected void body(ProtoWriter w) {
		if (!_valid) return;

		w.int32(1, _slotNumber);

		if (!_pcMode) return;

		w.msg(2).int32(1, 0);
		for (int itemId : _slot0Items)
			w.int32(2, itemId);
		w.end();

		w.msg(2).int32(1, 1);
		for (int itemId : _slot1Items)
			w.int32(2, itemId);
		w.end();

		w.int32(3, 2)
		 .int32(4, 1);
	}

	private static int[] snapshot(List<Integer> items) {
		if (items == null || items.isEmpty())
			return new int[0];

		final int[] out = new int[items.size()];
		int n = 0;

		for (Integer itemId : items) {
			if (itemId != null) {
				out[n++] = itemId;
			}
		}

		if (n == out.length) {
			return out;
		}
		final int[] trimmed = new int[n];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _slotNumber, int[] _slot0Items, int[] _slot1Items, boolean _pcMode, boolean _valid) {
        if (!_valid) return;
            w.int32(1, _slotNumber);
            if (!_pcMode) return;
            w.msg(2).int32(1, 0);
            for (int itemId : _slot0Items) w.int32(2, itemId);
            w.end();
            w.msg(2).int32(1, 1);
            for (int itemId : _slot1Items) w.int32(2, itemId);
            w.end();
            w.int32(3, 2).int32(4, 1);
    }

    public static boolean sendFields(L1PcInstance recipient, int _slotNumber, int[] _slot0Items, int[] _slot1Items, boolean _pcMode, boolean _valid) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _slotNumber, _slot0Items, _slot1Items, _pcMode, _valid);
    }

    public static boolean sendFields(CIOClient recipient, int _slotNumber, int[] _slot0Items, int[] _slot1Items, boolean _pcMode, boolean _valid) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_SLOT_CHANGE, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _slotNumber, _slot0Items, _slot1Items, _pcMode, _valid);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _slotNumber, int[] _slot0Items, int[] _slot1Items, boolean _pcMode, boolean _valid) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _slotNumber, _slot0Items, _slot1Items, _pcMode, _valid);
        return writer.finishWithTrailer(ProtoMsg.SC_SLOT_CHANGE);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int type, L1PcInstance pc) {
        int proto60_slotNumber = 0;
        int[] proto60_slot0Items = null;
        int[] proto60_slot1Items = null;
        boolean proto60_pcMode = false;
        boolean proto60_valid = false;

            proto60_pcMode = true;
            if (type != SLOT_CHANGE || pc == null) {
                proto60_slotNumber = 0;
                proto60_slot0Items = new int[0];
                proto60_slot1Items = new int[0];
                proto60_valid = false;
                return sendFields(protoRecipient, proto60_slotNumber, proto60_slot0Items, proto60_slot1Items, proto60_pcMode, proto60_valid);
            }
            proto60_slotNumber = pc.getSlotNumber();
            proto60_slot0Items = snapshot(pc.getSlotItems(0));
            proto60_slot1Items = snapshot(pc.getSlotItems(1));
            proto60_valid = true;

        return sendFields(protoRecipient, proto60_slotNumber, proto60_slot0Items, proto60_slot1Items, proto60_pcMode, proto60_valid);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int type, int slot) {
        int proto61_slotNumber = 0;
        int[] proto61_slot0Items = null;
        int[] proto61_slot1Items = null;
        boolean proto61_pcMode = false;
        boolean proto61_valid = false;

            proto61_slotNumber = slot;
            proto61_slot0Items = new int[0];
            proto61_slot1Items = new int[0];
            proto61_pcMode = false;
            proto61_valid = type == SLOT_CHANGE;

        return sendFields(protoRecipient, proto61_slotNumber, proto61_slot0Items, proto61_slot1Items, proto61_pcMode, proto61_valid);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int type, L1PcInstance pc) {
        return sendCtor(protoRecipient, type, pc);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int type, L1PcInstance pc) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), type, pc);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int type, int slot) {
        return sendCtor(protoRecipient, type, slot);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int type, int slot) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), type, slot);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int type, L1PcInstance pc) { return sendCtor(protoRecipient, type, pc); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int type, L1PcInstance pc) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), type, pc); }

    public static boolean sendConstructed(CIOClient protoRecipient, int type, int slot) { return sendCtor(protoRecipient, type, slot); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int type, int slot) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), type, slot); }

}
