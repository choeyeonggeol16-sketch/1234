package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_MY_RANKING_ACK (0x03FE) — 개인 랭킹 응답
 *  1 result_code  int32 [required]  2 version int64
 *  3 class_rank   4 previous_class_rank
 *  5 all_rank     6 previous_all_rank
 * has-bit 조합 보존: result만 / result+version 없음 
 */
public final class SC_MyRankingAck extends ProtoPacket {

    public enum ResultCode {
        SUCCESS(0), NO_CHANGE(1), NOW_NOT_SERVICE(2), NO_RANK(3), UNKNOWN_ERROR(100);

        private final int v;
        ResultCode(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private final int _resultCode;
    private final long _version;
    private final int _classRank, _prevClassRank, _allRank, _prevAllRank;
    private final boolean _hasRankFields;

    private SC_MyRankingAck(int resultCode, long version,
            int classRank, int prevClassRank, int allRank, int prevAllRank,
            boolean hasRankFields) {
        _resultCode = resultCode;
        _version = version;
        _classRank = classRank;
        _prevClassRank = prevClassRank;
        _allRank = allRank;
        _prevAllRank = prevAllRank;
        _hasRankFields = hasRankFields;
    }

    public static SC_MyRankingAck notService() {
        return new SC_MyRankingAck(ResultCode.NOW_NOT_SERVICE.toInt(), 0, 0, 0, 0, 0, false);
    }

    public static SC_MyRankingAck noChange() {
        return new SC_MyRankingAck(ResultCode.NO_CHANGE.toInt(), 0, 0, 0, 0, 0, false);
    }
    
    public static SC_MyRankingAck success(int classRank, int prevClassRank,
            int allRank, int prevAllRank, long version) {
        return new SC_MyRankingAck(ResultCode.SUCCESS.toInt(), version,
                classRank, prevClassRank, allRank, prevAllRank, true);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_MY_RANKING_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _resultCode);
        if (_hasRankFields) {       
            w.int64(2, _version)
             .int32(3, _classRank)
             .int32(4, _prevClassRank)
             .int32(5, _allRank)
             .int32(6, _prevAllRank);
        }
    }

    public static void send_not_service(L1PcInstance pc) {
        SC_MyRankingAck.sendNotService(pc);
    }

    public static void send(L1PcInstance pc) {
        SC_MyRankingAck.sendNoChange(pc);
    }

    public static void send(L1PcInstance pc, SC_TopRankerNoti noti, long currentVersion) {
    	 if (noti == null || noti.get_class_ranker() == null || noti.get_total_ranker() == null) {
             SC_MyRankingAck.sendNoChange(pc);
             return;
         }
         SC_MyRankingAck.sendSuccess(pc, noti.get_class_ranker().get_rank(), noti.get_class_ranker().get_previous_rank(), noti.get_total_ranker().get_rank(), noti.get_total_ranker().get_previous_rank(), currentVersion);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, int _resultCode, long _version, int _classRank, int _prevClassRank, int _allRank,
            int _prevAllRank, boolean _hasRankFields) {
        w.int32(1, _resultCode);
            if (_hasRankFields) {
                w.int64(2, _version).int32(3, _classRank).int32(4, _prevClassRank).int32(5, _allRank).int32(6, _prevAllRank);
            }
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _resultCode, long _version, int _classRank, int _prevClassRank, int _allRank,
            int _prevAllRank, boolean _hasRankFields) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _resultCode, _version, _classRank, _prevClassRank,
                _allRank, _prevAllRank, _hasRankFields);
    }

    public static boolean sendFields(
            CIOClient recipient, int _resultCode, long _version, int _classRank, int _prevClassRank, int _allRank,
            int _prevAllRank, boolean _hasRankFields) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MY_RANKING_ACK, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _resultCode, _version, _classRank, _prevClassRank, _allRank, _prevAllRank, _hasRankFields);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(
            int _resultCode, long _version, int _classRank, int _prevClassRank, int _allRank, int _prevAllRank,
            boolean _hasRankFields) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _resultCode, _version, _classRank, _prevClassRank, _allRank, _prevAllRank, _hasRankFields);
        return writer.finishWithTrailer(ProtoMsg.SC_MY_RANKING_ACK);
    }

    private static boolean sendCtor(
            CIOClient protoRecipient, int resultCode, long version, int classRank, int prevClassRank, int allRank,
            int prevAllRank, boolean hasRankFields) {
        int proto46_resultCode = 0;
        long proto46_version = 0;
        int proto46_classRank = 0;
        int proto46_prevClassRank = 0;
        int proto46_allRank = 0;
        int proto46_prevAllRank = 0;
        boolean proto46_hasRankFields = false;

            proto46_resultCode = resultCode;
            proto46_version = version;
            proto46_classRank = classRank;
            proto46_prevClassRank = prevClassRank;
            proto46_allRank = allRank;
            proto46_prevAllRank = prevAllRank;
            proto46_hasRankFields = hasRankFields;

        return sendFields(
                protoRecipient, proto46_resultCode, proto46_version, proto46_classRank, proto46_prevClassRank,
                proto46_allRank, proto46_prevAllRank, proto46_hasRankFields);
    }

    public static boolean sendNotService(CIOClient protoRecipient) {
        return sendCtor(protoRecipient, ResultCode.NOW_NOT_SERVICE.toInt(), 0, 0, 0, 0, 0, false);
    }

    public static boolean sendNotService(L1PcInstance protoRecipient) {
        return sendNotService(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }

    public static boolean sendNoChange(CIOClient protoRecipient) {
        return sendCtor(protoRecipient, ResultCode.NO_CHANGE.toInt(), 0, 0, 0, 0, 0, false);
    }

    public static boolean sendNoChange(L1PcInstance protoRecipient) {
        return sendNoChange(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }

    public static boolean sendSuccess(CIOClient protoRecipient, int classRank, int prevClassRank, int allRank, int prevAllRank, long version) {
        return sendCtor(protoRecipient, ResultCode.SUCCESS.toInt(), version, classRank, prevClassRank, allRank, prevAllRank, true);
    }

    public static boolean sendSuccess(L1PcInstance protoRecipient, int classRank, int prevClassRank, int allRank, int prevAllRank, long version) {
        return sendSuccess(protoRecipient == null ? null : protoRecipient.getNetConnection(), classRank, prevClassRank, allRank, prevAllRank, version);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int resultCode, long version, int classRank, int prevClassRank, int allRank,
            int prevAllRank, boolean hasRankFields) { return sendCtor(protoRecipient, resultCode, version, classRank, prevClassRank, allRank, prevAllRank, hasRankFields); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int resultCode, long version, int classRank, int prevClassRank, int allRank,
            int prevAllRank, boolean hasRankFields) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), resultCode, version, classRank, prevClassRank, allRank, prevAllRank, hasRankFields); }

}
