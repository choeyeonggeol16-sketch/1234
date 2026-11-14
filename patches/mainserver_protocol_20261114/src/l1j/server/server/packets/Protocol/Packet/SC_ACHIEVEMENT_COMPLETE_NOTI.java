package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.CompletedAchievement;

/**
 * SC_ACHIEVEMENT_COMPLETE_NOTI — 업적 완료 알림
 * wire: [1]=achievement_id uint32, [2]=completed_time uint64
 */
public final class SC_ACHIEVEMENT_COMPLETE_NOTI extends ProtoPacket {

    private final int _achievementId;
    private final long _completedTime;

    public SC_ACHIEVEMENT_COMPLETE_NOTI(int achievementId, long completedTime) {
        _achievementId = achievementId;
        _completedTime = completedTime;
    }

    public static SC_ACHIEVEMENT_COMPLETE_NOTI of(CompletedAchievement a) {
        return new SC_ACHIEVEMENT_COMPLETE_NOTI(a.get_achievement_id(), a.get_completed_time());
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_ACHIEVEMENT_COMPLETE_NOTI;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _achievementId);   
        w.uint64(2, _completedTime); 
    }



    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _achievementId, long _completedTime) {
        w.uint32(1, _achievementId);
            w.uint64(2, _completedTime);
    }

    public static boolean sendFields(L1PcInstance recipient, int _achievementId, long _completedTime) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _achievementId, _completedTime);
    }

    public static boolean sendFields(CIOClient recipient, int _achievementId, long _completedTime) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ACHIEVEMENT_COMPLETE_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _achievementId, _completedTime);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _achievementId, long _completedTime) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _achievementId, _completedTime);
        return writer.finishWithTrailer(ProtoMsg.SC_ACHIEVEMENT_COMPLETE_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int achievementId, long completedTime) {
        int proto1_achievementId = 0;
        long proto1_completedTime = 0;

            proto1_achievementId = achievementId;
            proto1_completedTime = completedTime;

        return sendFields(protoRecipient, proto1_achievementId, proto1_completedTime);
    }

    public static boolean sendOf(CIOClient protoRecipient, CompletedAchievement a) {
        return sendCtor(protoRecipient, a.get_achievement_id(), a.get_completed_time());
    }

    public static boolean sendOf(L1PcInstance protoRecipient, CompletedAchievement a) {
        return sendOf(protoRecipient == null ? null : protoRecipient.getNetConnection(), a);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int achievementId, long completedTime) { return sendCtor(protoRecipient, achievementId, completedTime); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int achievementId, long completedTime) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), achievementId, completedTime); }

}
