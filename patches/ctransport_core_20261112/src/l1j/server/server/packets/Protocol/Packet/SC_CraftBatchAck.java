package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_CRAFT_BATCH_ACK (0x005D) — 배치 제작 결과. 
 * wire: field1 = eResult(enum→varint), field2 = batch_transaction_id(varint)
 */
public final class SC_CraftBatchAck extends ProtoPacket {

    public static final int RP_SUCCESS = 0;
    public static final int RP_FAILURE = 1;

    private final int _eResult;
    private final int _batchTransactionId;

    public SC_CraftBatchAck(int eResult, int batchTransactionId) {
        _eResult = eResult;
        _batchTransactionId = batchTransactionId;
    }

    /** 편의 전송 */
    public static void send(L1PcInstance pc, int eResult, int batchTransactionId) {
        if (pc == null) return;
        SC_CraftBatchAck.sendConstructed(pc, eResult, batchTransactionId);
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_CRAFT_BATCH_ACK;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _eResult);              // enum == int32 (varint) — MJ writeEnum과 동일 wire
        w.int32(2, _batchTransactionId);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _eResult, int _batchTransactionId) {
        w.int32(1, _eResult);
            w.int32(2, _batchTransactionId);
    }

    public static boolean sendFields(L1PcInstance recipient, int _eResult, int _batchTransactionId) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _eResult, _batchTransactionId);
    }

    public static boolean sendFields(CIOClient recipient, int _eResult, int _batchTransactionId) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_CRAFT_BATCH_ACK, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _eResult, _batchTransactionId);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _eResult, int _batchTransactionId) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _eResult, _batchTransactionId);
        return writer.finishWithTrailer(ProtoMsg.SC_CRAFT_BATCH_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int eResult, int batchTransactionId) {
        int proto27_eResult = 0;
        int proto27_batchTransactionId = 0;

            proto27_eResult = eResult;
            proto27_batchTransactionId = batchTransactionId;

        return sendFields(protoRecipient, proto27_eResult, proto27_batchTransactionId);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int eResult, int batchTransactionId) { return sendCtor(protoRecipient, eResult, batchTransactionId); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int eResult, int batchTransactionId) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), eResult, batchTransactionId); }

}
