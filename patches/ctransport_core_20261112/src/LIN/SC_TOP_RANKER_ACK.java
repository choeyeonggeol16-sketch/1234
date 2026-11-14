package LIN;

import LIN.Ranker;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * SC_TOP_RANKER_ACK (0x0088) — 랭커 목록 응답
 *  1 result_code enum [required]  2 version int64
 *  3 class_id  4 total_page  5 current_page
 *  6 rankers repeated (MJ Ranker 데이터 홀더)
 */
public final class SC_TOP_RANKER_ACK extends ProtoPacket {

    public enum ResultCode {
        SUCCESS(0), NO_CHANGE(1), NOW_NOT_SERVICE(2), UNKNOWN_ERROR(100);
        private final int v;
        ResultCode(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private int _resultCode = -1;
    private long _version;
    private int _classId, _totalPage, _currentPage;
    private boolean _hasVersion, _hasPageFields;
    private final List<LIN.Ranker> _rankers = new ArrayList<>();

    // ── 팩토리 ──

    public static LIN.SC_TOP_RANKER_ACK noChange(long version) {
        LIN.SC_TOP_RANKER_ACK ack = new LIN.SC_TOP_RANKER_ACK();
        ack._resultCode = LIN.SC_TOP_RANKER_ACK.ResultCode.NO_CHANGE.toInt();
        ack._version = version;
        ack._hasVersion = true;
        return ack;
    }

    public static LIN.SC_TOP_RANKER_ACK newInstance(int classId, int totalPage, int currentPage) {
        LIN.SC_TOP_RANKER_ACK ack = new LIN.SC_TOP_RANKER_ACK();
        ack._resultCode = LIN.SC_TOP_RANKER_ACK.ResultCode.SUCCESS.toInt();
        ack._classId = classId;
        ack._totalPage = totalPage;
        ack._currentPage = currentPage;
        ack._hasPageFields = true;
        return ack;
    }

    public static LIN.SC_TOP_RANKER_ACK newInstance() {
        return new LIN.SC_TOP_RANKER_ACK();
    }

    // ── 세터 ──

    public LIN.SC_TOP_RANKER_ACK set_result_code(int code) { _resultCode = code; return this; }

    public LIN.SC_TOP_RANKER_ACK set_version(long v)   { _version = v;    _hasVersion = true;    return this; }
    public LIN.SC_TOP_RANKER_ACK set_class_id(int v)   { _classId = v;    _hasPageFields = true; return this; }
    public LIN.SC_TOP_RANKER_ACK set_total_page(int v) { _totalPage = v;  _hasPageFields = true; return this; }
    public LIN.SC_TOP_RANKER_ACK set_current_page(int v){ _currentPage = v; _hasPageFields = true; return this; }

    /** result_code만 필요한 고정 ACK(NOT_SERVICE 등)용 */
    public LIN.SC_TOP_RANKER_ACK resultCodeOnly(int code) { _resultCode = code; return this; }

    // ── rankers ──

    public LIN.SC_TOP_RANKER_ACK add_rankers(LIN.Ranker r) {
        _rankers.add(r); return this;
    }

    public boolean has_rankers() { return !_rankers.isEmpty(); }

    public void clear_rankers() { _rankers.clear(); }

    public List<LIN.Ranker> rankers() {
        return _rankers;
    }

    // ── 직렬화 ──

    @Override
    public int msgId() { return ProtoMsg.SC_TOP_RANKER_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _resultCode);                    // required — 항상 방출
        if (_hasVersion) w.int64(2, _version);
        if (_hasPageFields) {
            w.int32(3, _classId)
             .int32(4, _totalPage)
             .int32(5, _currentPage);
        }
        for (LIN.Ranker r : _rankers) {
            w.msg(6);
            writeRankerBody(w, r);
            w.end();
        }
    }

    private static void writeRankerBody(ProtoWriter w, LIN.Ranker r) {
        if (r.has_rating())        w.int32(1, r.rating());
        if (r.has_rank())          w.int32(2, r.rank());
        if (r.has_previous_rank()) w.int32(3, r.previous_rank());
        if (r.has_class_())         w.int32(4, r.class_());
        if (r.has_name())          w.string(5, r.name());
        if (r.has_uid())           w.int32(6, r.uid());
        if (r.has_score())         w.int32(7, r.score());
        if (r.has_class_rating())  w.int32(8, r.class_rating());
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _resultCode, long _version, int _classId, int _totalPage, int _currentPage, boolean _hasVersion, boolean _hasPageFields, List<LIN.Ranker> _rankers) {
        w.int32(1, _resultCode);
            if (_hasVersion) w.int64(2, _version);
            if (_hasPageFields) {
                w.int32(3, _classId).int32(4, _totalPage).int32(5, _currentPage);
            }
            for (LIN.Ranker r : _rankers) {
                w.msg(6);
                writeRankerBody(w, r);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _resultCode, long _version, int _classId, int _totalPage, int _currentPage, boolean _hasVersion, boolean _hasPageFields, List<LIN.Ranker> _rankers) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _resultCode, _version, _classId, _totalPage,
                _currentPage, _hasVersion, _hasPageFields, _rankers);
    }

    public static boolean sendFields(CIOClient recipient, int _resultCode, long _version, int _classId, int _totalPage, int _currentPage, boolean _hasVersion, boolean _hasPageFields, List<LIN.Ranker> _rankers) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_TOP_RANKER_ACK, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _resultCode, _version, _classId, _totalPage, _currentPage, _hasVersion, _hasPageFields, _rankers);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _resultCode, long _version, int _classId, int _totalPage, int _currentPage, boolean _hasVersion, boolean _hasPageFields, List<LIN.Ranker> _rankers) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _resultCode, _version, _classId, _totalPage, _currentPage, _hasVersion, _hasPageFields, _rankers);
        return writer.finishWithTrailer(ProtoMsg.SC_TOP_RANKER_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto68_resultCode = -1;
        long proto68_version = 0;
        int proto68_classId = 0;
        int proto68_totalPage = 0;
        int proto68_currentPage = 0;
        boolean proto68_hasVersion = false;
        boolean proto68_hasPageFields = false;
        List<LIN.Ranker> proto68_rankers = new ArrayList<>();

        return sendFields(
                protoRecipient, proto68_resultCode, proto68_version, proto68_classId, proto68_totalPage,
                proto68_currentPage, proto68_hasVersion, proto68_hasPageFields, proto68_rankers);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }


    public static boolean sendNoChange(L1PcInstance recipient, long version) {
        return sendNoChange(recipient == null ? null : recipient.getNetConnection(), version);
    }

    public static boolean sendNoChange(CIOClient recipient, long version) {
        ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_TOP_RANKER_ACK, 32);
        if (writer == null) return false;
        try {
            writer.int32(1, LIN.SC_TOP_RANKER_ACK.ResultCode.NO_CHANGE.toInt()).int64(2, version);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }
}
