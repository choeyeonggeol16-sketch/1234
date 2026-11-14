package LIN;

import LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem;
import LIN.SC_CRAFT_MAKE_ACK.eCraftMakeReqResultType;
import LIN.SC_CRAFT_MAKE_ACK;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_CRAFT_MAKE_ACK (0x003B) — 제작 결과.
 * wire: 1=eResult(varint), 2=repeated output_item, 3=limit_craft_info(미전송)
 * 아이템은 Craft.CraftOutputItem을 직접 직렬화 (inner 변환 클래스 불필요)
 */
public final class SC_CRAFT_MAKE_ACK extends ProtoPacket {

    private final int _eResult;
    private final LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem _item;   // null = 결과 아이템 없음 (실패)

    public SC_CRAFT_MAKE_ACK(int eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem item) {
        _eResult = eResult;
        _item = item;
    }

    /** 편의 전송 */
    public static void send(L1PcInstance pc, LIN.SC_CRAFT_MAKE_ACK.eCraftMakeReqResultType eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem item) {
        if (pc == null) return;
        LIN.SC_CRAFT_MAKE_ACK.sendConstructed(pc, eResult.toInt(), item);
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
            w.int32(1, _item.name_id());
            w.int32(2, _item.count());
            w.int32(3, _item.slot());
            w.int32(4, _item.enchant());
            w.int32(5, _item.bless());
            w.int32(6, _item.elemental_type());
            w.int32(7, _item.elemental_level());
            w.string(8, _item.desc());          // null이면 ProtoWriter가 0-length 처리
            w.int32(9, _item.system_desc());
            w.int32(10, _item.broadcast_desc());
            w.int32(11, _item.iconId());
            w.string(12, _item.url());
            w.end();
        }
        // field 3(limit_craft_info) 미전송 — 클라가 기본값 처리
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem _item) {
        w.int32(1, _eResult);
            if (_item != null) {
                w.msg(2);
                w.int32(1, _item.name_id());
                w.int32(2, _item.count());
                w.int32(3, _item.slot());
                w.int32(4, _item.enchant());
                w.int32(5, _item.bless());
                w.int32(6, _item.elemental_type());
                w.int32(7, _item.elemental_level());
                w.string(8, _item.desc());
                w.int32(9, _item.system_desc());
                w.int32(10, _item.broadcast_desc());
                w.int32(11, _item.iconId());
                w.string(12, _item.url());
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem _item) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _eResult, _item);
    }

    public static boolean sendFields(CIOClient recipient, int _eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem _item) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_CRAFT_MAKE_ACK, Proto.capacityBound(1024L + (_item == null || _item.desc() == null ? 0L : 2L * _item.desc().length()) + (_item == null || _item.url() == null ? 0L : 2L * _item.url().length())));
        if (writer == null) return false;
        try {
            writeFields(writer, _eResult, _item);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem _item) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _eResult, _item);
        return writer.finishWithTrailer(ProtoMsg.SC_CRAFT_MAKE_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem item) {
        int proto28_eResult = 0;
        LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem proto28_item = null;

            proto28_eResult = eResult;
            proto28_item = item;

        return sendFields(protoRecipient, proto28_eResult, proto28_item);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem item) { return sendCtor(protoRecipient, eResult, item); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int eResult, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem item) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), eResult, item); }



    // Descriptor: LIN.SC_CRAFT_MAKE_ACK.eCraftMakeReqResultType
    public static enum eCraftMakeReqResultType {
        RP_SUCCESS(0),
        RP_FAILURE(1),
        RP_ERROR_NO_REQUIRED_ITEM(2),
        RP_ERROR_SUCCESS_COUNT_EXCEED(3),
        RP_ERROR_BLOCKED_CRAFT_ID(4),
        RP_ERROR_WEIGHT_OVER(5),
        RP_ERROR_INVEN_OVER(6),
        RP_ERROR_INVALID_NPC(7),
        RP_ERROR_NPC_OUT_OF_RANGE(8),
        RP_ERROR_CRAFT_DOES_NOT_EXIST(9),
        RP_ERROR_INVALID_INPUT(10),
        RP_ERROR_TOO_MANY_MAKE_REQ(11);

        private final int _value;

        eCraftMakeReqResultType(int val) {
            _value = val;
        }

        public int toInt() {
            return _value;
        }

        /** 에러코드 전송 — 결과 아이템 없는 ACK */
        public void sendCachedMessage(L1PcInstance pc) {
            LIN.SC_CRAFT_MAKE_ACK.send(pc, this, null);
        }
    }
}
