package LIN;

import LIN.UserAttendanceState;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_ATTENDANCE_REWARD_ACK (0x03EF) — 보상 수령 응답
 *  1 attendance_id int32
 *  2 group_id int32
 *  3 status enum
 *  ⚠ 필드번호는 MJ 원본 SC_ATTENDANCE_REWARD_ACK의 writeTo 대조 (오류잡을 때 같이)
 */
public final class SC_ATTENDANCE_REWARD_ACK extends ProtoPacket {

    private final int _attendanceId;
    private final int _groupId;
    private final LIN.UserAttendanceState _status;

    public SC_ATTENDANCE_REWARD_ACK(int attendanceId, int groupId, LIN.UserAttendanceState status) {
        _attendanceId = attendanceId;
        _groupId = groupId;
        _status = status;
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_ATTENDANCE_REWARD_ACK;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _attendanceId);
        w.int32(2, _groupId);
        w.enumV(3, _status.toInt());
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _attendanceId, int _groupId, LIN.UserAttendanceState _status) {
        w.int32(1, _attendanceId);
            w.int32(2, _groupId);
            w.enumV(3, _status.toInt());
    }

    public static boolean sendFields(L1PcInstance recipient, int _attendanceId, int _groupId, LIN.UserAttendanceState _status) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _attendanceId, _groupId, _status);
    }

    public static boolean sendFields(CIOClient recipient, int _attendanceId, int _groupId, LIN.UserAttendanceState _status) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ATTENDANCE_REWARD_ACK, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _attendanceId, _groupId, _status);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _attendanceId, int _groupId, LIN.UserAttendanceState _status) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _attendanceId, _groupId, _status);
        return writer.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_REWARD_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int attendanceId, int groupId, LIN.UserAttendanceState status) {
        int proto16_attendanceId = 0;
        int proto16_groupId = 0;
        LIN.UserAttendanceState proto16_status = null;

            proto16_attendanceId = attendanceId;
            proto16_groupId = groupId;
            proto16_status = status;

        return sendFields(protoRecipient, proto16_attendanceId, proto16_groupId, proto16_status);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int attendanceId, int groupId, LIN.UserAttendanceState status) { return sendCtor(protoRecipient, attendanceId, groupId, status); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int attendanceId, int groupId, LIN.UserAttendanceState status) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), attendanceId, groupId, status); }

}
