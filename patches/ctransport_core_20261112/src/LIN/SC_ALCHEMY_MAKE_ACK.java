package LIN;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_ALCHEMY_MAKE_ACK (0x007D) — 인형합성 결과
 *  1 result_code enum [required]
 *  2 output_items repeated OutputItem { 1 item_id, 2 icon_id, 3 bless_code }
 */
public final class SC_ALCHEMY_MAKE_ACK extends ProtoPacket {

    public enum ResultCode {
        RC_SUCCESS(0),
        RC_FAIL(1),
        RC_ERROR_INVALID_INPUT(2),
        RC_ERROR_INVEN_OVER(3),
        RC_ERROR_WEIGHT_OVER(4),
        RC_ERROR_ALCHEMY_DOES_NOT_EXIST(5),
        RC_ERROR_NO_REQUIRED_ITEM(6),
        RC_ERROR_BLOCKED_ALCHEMY_ID(7),
        RC_ERROR_CURRENTLY_CLOSED(8),
        RC_ERROR_PETBALL_SUMMONING(9),
        RC_HYPER_SUCCESS(10),
        RC_ERROR_UNKNOWN(9999);

        private final int value;
        private byte[] cachedBytes;

        ResultCode(int val){ this.value = val; }
        public int toInt(){ return value; }
        public boolean equals(LIN.SC_ALCHEMY_MAKE_ACK.ResultCode v){ return value == v.value; }

        public void sendCachedMessage(L1PcInstance pc){
            if(cachedBytes == null){
                cachedBytes = new LIN.SC_ALCHEMY_MAKE_ACK(this).encode();
            }
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, cachedBytes);
        }
    }

    private final LIN.SC_ALCHEMY_MAKE_ACK.ResultCode _resultCode;
    private final java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> _outputItems;

    private SC_ALCHEMY_MAKE_ACK(LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> outputItems){
        _resultCode = resultCode;
        _outputItems = outputItems;
    }

    /** 결과코드만 (에러 응답용) */
    private SC_ALCHEMY_MAKE_ACK(LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode){
        this(resultCode, null);
    }

    /** 결과코드 + 아이템 정보 (성공 응답용) */
    public static LIN.SC_ALCHEMY_MAKE_ACK success(LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, L1ItemInstance item){
        java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> items = new java.util.LinkedList<>();
        items.add(new LIN.SC_ALCHEMY_MAKE_ACK.OutputItem(item.getId(), item.get_gfxid(), item.getBless()));
        return new LIN.SC_ALCHEMY_MAKE_ACK(resultCode, items);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ALCHEMY_MAKE_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _resultCode.toInt());     // required — 항상
        if (_outputItems != null) {
            for (LIN.SC_ALCHEMY_MAKE_ACK.OutputItem item : _outputItems) {
                w.msg(2);
                w.int32(1, item._itemId)
                 .int32(2, item._iconId)
                 .int32(3, item._blessCode);
                w.end();
            }
        }
    }

    private static final class OutputItem {
        final int _itemId, _iconId, _blessCode;

        OutputItem(int itemId, int iconId, int blessCode){
            _itemId = itemId;
            _iconId = iconId;
            _blessCode = blessCode;
        }
    }

    /** REQ 쪽 편의 — 결과코드만 캐시 전송 */
    static void sendCached(LIN.SC_ALCHEMY_MAKE_ACK.ResultCode code, L1PcInstance pc){
        code.sendCachedMessage(pc);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode _resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> _outputItems) {
        w.int32(1, _resultCode.toInt());
            if (_outputItems != null) {
                for (LIN.SC_ALCHEMY_MAKE_ACK.OutputItem item : _outputItems) {
                    w.msg(2);
                    w.int32(1, item._itemId).int32(2, item._iconId).int32(3, item._blessCode);
                    w.end();
                }
            }
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode _resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> _outputItems) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _resultCode, _outputItems);
    }

    public static boolean sendFields(CIOClient recipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode _resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> _outputItems) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ALCHEMY_MAKE_ACK, Proto.capacityBound(128L + (_outputItems == null ? 0L : 64L * _outputItems.size())));
        if (writer == null) return false;
        try {
            writeFields(writer, _resultCode, _outputItems);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.SC_ALCHEMY_MAKE_ACK.ResultCode _resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> _outputItems) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _resultCode, _outputItems);
        return writer.finishWithTrailer(ProtoMsg.SC_ALCHEMY_MAKE_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> outputItems) {
        LIN.SC_ALCHEMY_MAKE_ACK.ResultCode proto10_resultCode = null;
        java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> proto10_outputItems = null;

            proto10_resultCode = resultCode;
            proto10_outputItems = outputItems;

        return sendFields(protoRecipient, proto10_resultCode, proto10_outputItems);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode) {
        return sendCtor(protoRecipient, resultCode, null);
    }

    public static boolean sendSuccess(CIOClient protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, L1ItemInstance item) {
        java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> items = new java.util.LinkedList<>();
                items.add(new LIN.SC_ALCHEMY_MAKE_ACK.OutputItem(item.getId(), item.get_gfxid(), item.getBless()));
                return sendCtor(protoRecipient, resultCode, items);
    }

    public static boolean sendSuccess(L1PcInstance protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, L1ItemInstance item) {
        return sendSuccess(protoRecipient == null ? null : protoRecipient.getNetConnection(), resultCode, item);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> outputItems) { return sendCtor(protoRecipient, resultCode, outputItems); }

    public static boolean sendConstructed(L1PcInstance protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode, java.util.LinkedList<LIN.SC_ALCHEMY_MAKE_ACK.OutputItem> outputItems) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), resultCode, outputItems); }

    public static boolean sendConstructed(CIOClient protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode) { return sendCtor(protoRecipient, resultCode); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, LIN.SC_ALCHEMY_MAKE_ACK.ResultCode resultCode) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), resultCode); }

}
