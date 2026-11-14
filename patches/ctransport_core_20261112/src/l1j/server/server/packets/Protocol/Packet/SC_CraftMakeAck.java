package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItem;
import l1j.server.server.packets.Protocol.Packet.Craft.eCraftMakeReqResultType;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_CRAFT_MAKE_ACK (0x003B) — 제작 결과.
 * wire: 1=eResult(varint), 2=repeated output_item, 3=limit_craft_info(미전송)
 * 아이템은 Craft.CraftOutputItem을 직접 직렬화 (inner 변환 클래스 불필요)
 */
public final class SC_CraftMakeAck extends ProtoPacket {

    private final int _eResult;
    private final CraftOutputItem _item;   // null = 결과 아이템 없음 (실패)

    public SC_CraftMakeAck(int eResult, CraftOutputItem item) {
        _eResult = eResult;
        _item = item;
    }

    /** 편의 전송 */
    public static void send(L1PcInstance pc, eCraftMakeReqResultType eResult, CraftOutputItem item) {
        if (pc == null) return;
        SC_CraftMakeAck.sendConstructed(pc, eResult.toInt(), item);
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_CRAFT_MAKE_ACK;   // 0x003B
    }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _eResult);

        if (_item != null) {
            w.msg(2);
            w.int32(1, _item.get_nameId());
            w.int32(2, _item.get_count());
            w.int32(3, _item.get_slot());
            w.int32(4, _item.get_enchant());
            w.int32(5, _item.get_bless());
            w.int32(6, _item.get_elementalType());
            w.int32(7, _item.get_elementalLevel());
            w.string(8, _item.get_desc());          // null이면 ProtoWriter가 0-length 처리
            w.int32(9, _item.get_systemDesc());
            w.int32(10, _item.get_broadcastDesc());
            w.int32(11, _item.get_iconId());
            w.string(12, _item.get_url());
            w.end();
        }
        // field 3(limit_craft_info) 미전송 — 클라가 기본값 처리
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _eResult, CraftOutputItem _item) {
        w.int32(1, _eResult);
            if (_item != null) {
                w.msg(2);
                w.int32(1, _item.get_nameId());
                w.int32(2, _item.get_count());
                w.int32(3, _item.get_slot());
                w.int32(4, _item.get_enchant());
                w.int32(5, _item.get_bless());
                w.int32(6, _item.get_elementalType());
                w.int32(7, _item.get_elementalLevel());
                w.string(8, _item.get_desc());
                w.int32(9, _item.get_systemDesc());
                w.int32(10, _item.get_broadcastDesc());
                w.int32(11, _item.get_iconId());
                w.string(12, _item.get_url());
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _eResult, CraftOutputItem _item) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _eResult, _item);
    }

    public static boolean sendFields(CIOClient recipient, int _eResult, CraftOutputItem _item) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_CRAFT_MAKE_ACK, Proto.capacityBound(1024L + (_item == null || _item.get_desc() == null ? 0L : 2L * _item.get_desc().length()) + (_item == null || _item.get_url() == null ? 0L : 2L * _item.get_url().length())));
        if (writer == null) return false;
        try {
            writeFields(writer, _eResult, _item);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _eResult, CraftOutputItem _item) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _eResult, _item);
        return writer.finishWithTrailer(ProtoMsg.SC_CRAFT_MAKE_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int eResult, CraftOutputItem item) {
        int proto28_eResult = 0;
        CraftOutputItem proto28_item = null;

            proto28_eResult = eResult;
            proto28_item = item;

        return sendFields(protoRecipient, proto28_eResult, proto28_item);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int eResult, CraftOutputItem item) { return sendCtor(protoRecipient, eResult, item); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int eResult, CraftOutputItem item) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), eResult, item); }

}
