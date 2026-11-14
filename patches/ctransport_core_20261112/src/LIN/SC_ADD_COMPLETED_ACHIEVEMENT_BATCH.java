package LIN;

import LIN.CompletedAchievement;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * SC_ADD_COMPLETED_ACHIEVEMENT_BATCH — 완료 도감(업적) 일괄 전송 (페이지네이션)
 * wire: [1]=total_pages uint32, [2]=current_page uint32, [3]=completed_achievement repeated message
 * 서브메시지 CompletedAchievement: [1]=achievement_id uint32, [2]=completed_time uint64, [3]=get_reward bool
 */
public final class SC_ADD_COMPLETED_ACHIEVEMENT_BATCH extends ProtoPacket {

    /**
     * 서브메시지 CompletedAchievement — required: 세 값 모두 필수
     * (구 CompletedAchievement generated의 와이어 역할 대체)
     */
    public static final class Achievement {
        private final int _achievementId;
        private final long _completedTime;
        private final boolean _getReward;

        public Achievement(int achievementId, long completedTime, boolean getReward) {
            _achievementId = achievementId;
            _completedTime = completedTime;
            _getReward = getReward;
        }

        /** 과도기 브릿지 — 구 도메인 객체에서 값 추출 (구 generated 정리 시 이 메서드도 삭제) */
        public static LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement of(LIN.CompletedAchievement a) {
            return new LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement(a.achievement_id(), a.completed_time(), a.get_reward());
        }

        void body(ProtoWriter w) {
            w.uint32(1, _achievementId);
            w.uint64(2, _completedTime);
            w.bool(3, _getReward);
        }
    }

    private final int _totalPages;
    private final int _currentPage;
    private final List<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> _achievements = new ArrayList<>();

    public SC_ADD_COMPLETED_ACHIEVEMENT_BATCH(int totalPages, int currentPage) {
        _totalPages = totalPages;      // required
        _currentPage = currentPage;    // required
    }

    public LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH addAchievement(int achievementId, long completedTime, boolean getReward) {
        _achievements.add(new LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement(achievementId, completedTime, getReward));
        return this;
    }

    /** 브릿지로 만든 Achievement 직접 추가용 */
    public LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH addAchievement(LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement a) {
        _achievements.add(a);
        return this;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _totalPages);
        w.uint32(2, _currentPage);
        for (LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement a : _achievements) {
            w.msg(3);          // 구: writeMessage(3, val)
            a.body(w);
            w.end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _totalPages, int _currentPage, List<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> _achievements) {
        w.uint32(1, _totalPages);
            w.uint32(2, _currentPage);
            for (LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement a : _achievements) {
                w.msg(3);
                a.body(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _totalPages, int _currentPage, List<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> _achievements) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _totalPages, _currentPage, _achievements);
    }

    public static boolean sendFields(CIOClient recipient, int _totalPages, int _currentPage, List<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> _achievements) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH, Proto.capacityBound(128L + (_achievements == null ? 0L : 64L * _achievements.size())));
        if (writer == null) return false;
        try {
            writeFields(writer, _totalPages, _currentPage, _achievements);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _totalPages, int _currentPage, List<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> _achievements) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _totalPages, _currentPage, _achievements);
        return writer.finishWithTrailer(ProtoMsg.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int totalPages, int currentPage) {
        int proto4_totalPages = 0;
        int proto4_currentPage = 0;
        List<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> proto4_achievements = new ArrayList<>();

            proto4_totalPages = totalPages;
            proto4_currentPage = currentPage;

        return sendFields(protoRecipient, proto4_totalPages, proto4_currentPage, proto4_achievements);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int totalPages, int currentPage) { return sendCtor(protoRecipient, totalPages, currentPage); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int totalPages, int currentPage) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), totalPages, currentPage); }

}
