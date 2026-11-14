package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceState;

/**
 * SC_ATTENDANCE_DATA_UPDATE_EXTEND (0x0223) — 단일 보상 수령 갱신 통보
 * 원본 필드: 1=index int32 / 2=state int32 / 3=group enum
 */
public final class SC_AttendanceDataUpdateExtend extends ProtoPacket {

    private final int _index;
    private final UserAttendanceState _state;
    private final AttendanceGroupType _group;

    public SC_AttendanceDataUpdateExtend(int index, AttendanceGroupType group, UserAttendanceState state) {
        _index = index;
        _group = group;
        _state = state;
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_ATTENDANCE_DATA_UPDATE_EXTEND;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _index);
        w.int32(2, _state.toInt());     // ← state가 2번
        w.enumV(3, _group.toInt());     // ← group이 3번
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _index, UserAttendanceState _state, AttendanceGroupType _group) {
        w.int32(1, _index);
            w.int32(2, _state.toInt());
            w.enumV(3, _group.toInt());
    }

    public static boolean sendFields(L1PcInstance recipient, int _index, UserAttendanceState _state, AttendanceGroupType _group) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _index, _state, _group);
    }

    public static boolean sendFields(CIOClient recipient, int _index, UserAttendanceState _state, AttendanceGroupType _group) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ATTENDANCE_DATA_UPDATE_EXTEND, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _index, _state, _group);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _index, UserAttendanceState _state, AttendanceGroupType _group) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _index, _state, _group);
        return writer.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_DATA_UPDATE_EXTEND);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int index, AttendanceGroupType group, UserAttendanceState state) {
        int proto15_index = 0;
        UserAttendanceState proto15_state = null;
        AttendanceGroupType proto15_group = null;

            proto15_index = index;
            proto15_group = group;
            proto15_state = state;

        return sendFields(protoRecipient, proto15_index, proto15_state, proto15_group);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int index, AttendanceGroupType group, UserAttendanceState state) { return sendCtor(protoRecipient, index, group, state); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int index, AttendanceGroupType group, UserAttendanceState state) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), index, group, state); }

}
