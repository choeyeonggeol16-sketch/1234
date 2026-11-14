package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_TelePortUi extends ProtoPacket {

    private final int _objId;
    private final int _mapCount;
    private final String[] _action;
    private final int[] _price;

    public static SC_TelePortUi create(int objId, String[] action, int[] price, int mapCount) {
        return new SC_TelePortUi(objId, action, price, mapCount);
    }

    public SC_TelePortUi(int objId, String[] action, int[] price, int mapCount) {
        _objId = objId;
        _mapCount = Math.max(0, mapCount);
        _action = action != null ? action : new String[0];
        _price = price != null ? price : new int[0];
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_TELEPORT_UI;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _objId);

        final int count = Math.min(_mapCount, Math.min(_action.length, _price.length));

        for (int i = 0; i < count; i++) {
            final String action = _action[i] != null ? _action[i] : "";
            final int price = _price[i];

            w.msg(2);
                w.string(1, action);

                w.msg(2);
                    w.int32(1, 7);
                    w.int32(2, price);
                    w.int32(3, 1);
                w.end();
            w.end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _objId, int _mapCount, String[] _action, int[] _price) {
        w.int32(1, _objId);
            final int count = Math.min(_mapCount, Math.min(_action.length, _price.length));
            for (int i = 0; i < count; i++) {
                final String action = _action[i] != null ? _action[i] : "";
                final int price = _price[i];
                w.msg(2);
                w.string(1, action);
                w.msg(2);
                w.int32(1, 7);
                w.int32(2, price);
                w.int32(3, 1);
                w.end();
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _objId, int _mapCount, String[] _action, int[] _price) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _objId, _mapCount, _action, _price);
    }

    public static boolean sendFields(CIOClient recipient, int _objId, int _mapCount, String[] _action, int[] _price) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_TELEPORT_UI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _objId, _mapCount, _action, _price);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _objId, int _mapCount, String[] _action, int[] _price) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _objId, _mapCount, _action, _price);
        return writer.finishWithTrailer(ProtoMsg.SC_TELEPORT_UI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int objId, String[] action, int[] price, int mapCount) {
        int proto67_objId = 0;
        int proto67_mapCount = 0;
        String[] proto67_action = null;
        int[] proto67_price = null;

            proto67_objId = objId;
            proto67_mapCount = Math.max(0, mapCount);
            proto67_action = action != null ? action : new String[0];
            proto67_price = price != null ? price : new int[0];

        return sendFields(protoRecipient, proto67_objId, proto67_mapCount, proto67_action, proto67_price);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int objId, String[] action, int[] price, int mapCount) {
        return sendCtor(protoRecipient, objId, action, price, mapCount);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int objId, String[] action, int[] price, int mapCount) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), objId, action, price, mapCount);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int objId, String[] action, int[] price, int mapCount) { return sendCtor(protoRecipient, objId, action, price, mapCount); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int objId, String[] action, int[] price, int mapCount) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), objId, action, price, mapCount); }

}
