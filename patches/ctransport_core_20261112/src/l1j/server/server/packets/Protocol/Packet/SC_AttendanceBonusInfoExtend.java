package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceGroupType;

/**
 * SC_ATTENDANCE_BONUS_INFO_EXTEND (0x0220) — 출석 보상 시스템 정보
 *  1 check_interval, 2 reset_period, 3 daily_max_count,
 *  4 weekend_max_count, 5 total_bonus_group_count — 전부 required
 *
 * 내용 고정(로더 설정값) — 서버 시작 시 1회 인코딩 캐시, 전원 Raw 공유
 */
public final class SC_AttendanceBonusInfoExtend extends ProtoPacket {

    private static byte[] _cached;

    private final int _checkInterval;
    private final int _resetPeriod;
    private final int _dailyMaxCount;
    private final int _weekendMaxCount;
    private final int _totalBonusGroupCount;

    private SC_AttendanceBonusInfoExtend(int checkInterval, int resetPeriod,
            int dailyMaxCount, int weekendMaxCount, int totalBonusGroupCount) {
        _checkInterval = checkInterval;
        _resetPeriod = resetPeriod;
        _dailyMaxCount = dailyMaxCount;
        _weekendMaxCount = weekendMaxCount;
        _totalBonusGroupCount = totalBonusGroupCount;
    }

    /** 로더 설정값으로 캐시 구축. 서버 시작/리로드 시 호출 */
    public static void load(){
        _cached = new SC_AttendanceBonusInfoExtend(
                AttendanceLoadManager.ATTEN_CHECK_INTERVAL,
                AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND,
                AttendanceLoadManager.ATTEN_DAILY_MAX_COUNT + 1,   
                AttendanceLoadManager.ATTEN_WEEKEND_MAX_COUNT,
                AttendanceGroupType.values().length
                ).encode();
    }

    /** MJ send() 이관 — 캐시 공유 전송 */
    public static void send(L1PcInstance pc){
        l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _cached);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ATTENDANCE_BONUS_INFO_EXTEND; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _checkInterval)
         .int32(2, _resetPeriod)
         .int32(3, _dailyMaxCount)
         .int32(4, _weekendMaxCount)
         .int32(5, _totalBonusGroupCount);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _checkInterval, int _resetPeriod, int _dailyMaxCount, int _weekendMaxCount, int _totalBonusGroupCount) {
        w.int32(1, _checkInterval).int32(2, _resetPeriod).int32(3, _dailyMaxCount).int32(4, _weekendMaxCount).int32(5, _totalBonusGroupCount);
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _checkInterval, int _resetPeriod, int _dailyMaxCount, int _weekendMaxCount,
            int _totalBonusGroupCount) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _checkInterval, _resetPeriod, _dailyMaxCount,
                _weekendMaxCount, _totalBonusGroupCount);
    }

    public static boolean sendFields(
            CIOClient recipient, int _checkInterval, int _resetPeriod, int _dailyMaxCount, int _weekendMaxCount,
            int _totalBonusGroupCount) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ATTENDANCE_BONUS_INFO_EXTEND, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _checkInterval, _resetPeriod, _dailyMaxCount, _weekendMaxCount, _totalBonusGroupCount);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _checkInterval, int _resetPeriod, int _dailyMaxCount, int _weekendMaxCount, int _totalBonusGroupCount) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _checkInterval, _resetPeriod, _dailyMaxCount, _weekendMaxCount, _totalBonusGroupCount);
        return writer.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_BONUS_INFO_EXTEND);
    }

    private static boolean sendCtor(
            CIOClient protoRecipient, int checkInterval, int resetPeriod, int dailyMaxCount, int weekendMaxCount,
            int totalBonusGroupCount) {
        int proto14_checkInterval = 0;
        int proto14_resetPeriod = 0;
        int proto14_dailyMaxCount = 0;
        int proto14_weekendMaxCount = 0;
        int proto14_totalBonusGroupCount = 0;

            proto14_checkInterval = checkInterval;
            proto14_resetPeriod = resetPeriod;
            proto14_dailyMaxCount = dailyMaxCount;
            proto14_weekendMaxCount = weekendMaxCount;
            proto14_totalBonusGroupCount = totalBonusGroupCount;

        return sendFields(
                protoRecipient, proto14_checkInterval, proto14_resetPeriod, proto14_dailyMaxCount, proto14_weekendMaxCount,
                proto14_totalBonusGroupCount);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int checkInterval, int resetPeriod, int dailyMaxCount, int weekendMaxCount,
            int totalBonusGroupCount) { return sendCtor(protoRecipient, checkInterval, resetPeriod, dailyMaxCount, weekendMaxCount, totalBonusGroupCount); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int checkInterval, int resetPeriod, int dailyMaxCount, int weekendMaxCount,
            int totalBonusGroupCount) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), checkInterval, resetPeriod, dailyMaxCount, weekendMaxCount, totalBonusGroupCount); }

}
