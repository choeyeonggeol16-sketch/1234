package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_COMPLETED_ACHIEVEMENT_REWARD_ACK — 도감(업적) 보상 수령 결과
 * wire: [1]=result enum(varint), [2]=achievement_id uint32
 */
public final class SC_CompletedAchievementRewardAck extends ProtoPacket {

    public enum eResultCode {
        REQUEST_REWARD_SUCCESS(0),
        REQUEST_REWARD_FAIL(1),
        REQUEST_REWARD_FAIL_ALREADY_GET_REWARD(2),
        REQUEST_REWARD_FAIL_NOT_COMPLETED(3);

        private final int value;
        eResultCode(int val) { value = val; }
        public int toInt() { return value; }

        public static eResultCode fromInt(int i) {
            switch (i) {
            case 0: return REQUEST_REWARD_SUCCESS;
            case 1: return REQUEST_REWARD_FAIL;
            case 2: return REQUEST_REWARD_FAIL_ALREADY_GET_REWARD;
            case 3: return REQUEST_REWARD_FAIL_NOT_COMPLETED;
            default: return null;
            }
        }
    }

    private final int _result;          // 와이어는 varint라 int 보관
    private final int _achievementId;

    public SC_CompletedAchievementRewardAck(eResultCode result, int achievementId) {
        _result = result != null ? result.toInt() : 0;   // 구 fromInt null 방어 계승
        _achievementId = achievementId;
    }

    /** 구 static send() 대응 — 호출부 시그니처 동일 (pc, id, result) */
    public static void send(L1PcInstance pc, int achievementId, eResultCode result) {
        SC_CompletedAchievementRewardAck.sendConstructed(pc, result, achievementId);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.enumV(1, _result);          // 구: writeEnum(1, _result.toInt())
        w.uint32(2, _achievementId);  // 구: writeUInt32(2, ...)
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _result, int _achievementId) {
        w.enumV(1, _result);
            w.uint32(2, _achievementId);
    }

    public static boolean sendFields(L1PcInstance recipient, int _result, int _achievementId) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _result, _achievementId);
    }

    public static boolean sendFields(CIOClient recipient, int _result, int _achievementId) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _result, _achievementId);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _result, int _achievementId) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _result, _achievementId);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient, eResultCode result, int achievementId) {
        int proto26_result = 0;
        int proto26_achievementId = 0;

            proto26_result = result != null ? result.toInt() : 0;
            proto26_achievementId = achievementId;

        return sendFields(protoRecipient, proto26_result, proto26_achievementId);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, eResultCode result, int achievementId) { return sendCtor(protoRecipient, result, achievementId); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, eResultCode result, int achievementId) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), result, achievementId); }

}
