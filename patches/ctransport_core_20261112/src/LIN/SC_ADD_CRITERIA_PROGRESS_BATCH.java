package LIN;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * SC_ADD_CRITERIA_PROGRESS_BATCH — 도감 진행도 일괄 갱신 (페이지네이션)
 * wire: [1]=total_pages uint32, [2]=current_page uint32, [3]=criteria_progress repeated message
 * 서브메시지 CriteriaProgress: [1]=criteria_id uint32, [2]=quantity uint64
 */
public final class SC_ADD_CRITERIA_PROGRESS_BATCH extends ProtoPacket {

    /** 서브메시지 CriteriaProgress — required 계약: 두 값 모두 필수 (구 isInitialized 요소 검사 계승) */
    public static final class Progress {
        private final int _criteriaId;
        private final long _quantity;

        public Progress(int criteriaId, long quantity) {
            _criteriaId = criteriaId;
            _quantity = quantity;
        }

        void body(ProtoWriter w) {
            w.uint32(1, _criteriaId);
            w.uint64(2, _quantity);
        }
    }

    private final int _totalPages;
    private final int _currentPage;
    private final List<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> _progresses = new ArrayList<>();

    public SC_ADD_CRITERIA_PROGRESS_BATCH(int totalPages, int currentPage) {
        _totalPages = totalPages;      // required
        _currentPage = currentPage;    // required
    }

    public LIN.SC_ADD_CRITERIA_PROGRESS_BATCH addProgress(int criteriaId, long quantity) {
        _progresses.add(new LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress(criteriaId, quantity));
        return this;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ADD_CRITERIA_PROGRESS_BATCH; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _totalPages);      // 구: writeUInt32(1, ...)
        w.uint32(2, _currentPage);     // 구: writeUInt32(2, ...)
        for (LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress p : _progresses) {
            w.msg(3);                  // 구: writeMessage(3, val) — tag + 길이 자리 예약
            p.body(w);                 //      서브메시지 본문 기록
            w.end();                   //      길이 백패치
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _totalPages, int _currentPage, List<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> _progresses) {
        w.uint32(1, _totalPages);
            w.uint32(2, _currentPage);
            for (LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress p : _progresses) {
                w.msg(3);
                p.body(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _totalPages, int _currentPage, List<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> _progresses) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _totalPages, _currentPage, _progresses);
    }

    public static boolean sendFields(CIOClient recipient, int _totalPages, int _currentPage, List<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> _progresses) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ADD_CRITERIA_PROGRESS_BATCH, Proto.capacityBound(128L + (_progresses == null ? 0L : 64L * _progresses.size())));
        if (writer == null) return false;
        try {
            writeFields(writer, _totalPages, _currentPage, _progresses);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _totalPages, int _currentPage, List<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> _progresses) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _totalPages, _currentPage, _progresses);
        return writer.finishWithTrailer(ProtoMsg.SC_ADD_CRITERIA_PROGRESS_BATCH);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int totalPages, int currentPage) {
        int proto5_totalPages = 0;
        int proto5_currentPage = 0;
        List<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> proto5_progresses = new ArrayList<>();

            proto5_totalPages = totalPages;
            proto5_currentPage = currentPage;

        return sendFields(protoRecipient, proto5_totalPages, proto5_currentPage, proto5_progresses);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int totalPages, int currentPage) { return sendCtor(protoRecipient, totalPages, currentPage); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int totalPages, int currentPage) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), totalPages, currentPage); }

}
