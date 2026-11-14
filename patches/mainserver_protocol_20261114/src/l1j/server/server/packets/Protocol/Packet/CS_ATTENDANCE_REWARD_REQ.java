package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceState;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceTimeStatus;

/**
 * CS_ATTENDANCE_REWARD_REQ (0x03EE) — 보상 수령 요청
 *  1 attendance_id int32 [required]
 *  2 group_id int32 [required]
 */
public final class CS_ATTENDANCE_REWARD_REQ {

    private CS_ATTENDANCE_REWARD_REQ() {}

    public static void handleRequest(CIOClient client, ProtoReader r) {
        // ── 파싱 ──
       
        int attendanceId = 0;
        int groupId = 0;
        while (!r.isAtEnd()) {
            int tag = r.tag();
            switch (tag >>> 3) {
            case 1:
                attendanceId = r.varint32();
                break;
            case 2:
                groupId = r.varint32();
                break;
            default:
                r.skip(tag & 7);
                break;
            }
        }

        // ── 실행 ──
        L1PcInstance pc = null;
        try {
            pc = client.getActiveChar();
            if (pc == null) return;

            SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
            if (userData == null) return;

            AttendanceGroupType egroup = AttendanceGroupType.fromInt(groupId);
            if (egroup == null) return;

            UserAttendanceDataGroup group = userData.get_groups().get(egroup.toInt());
            if (group == null || group.get_groupData() == null) return;

            int targetIdx = attendanceId - 1;
            if (targetIdx < 0 || targetIdx >= group.get_groupData().size()) {
                System.err.println(String.format("[출첵해킹의심] 범위를 벗어난 인덱스! 유저: %s, ID: %d", pc.getName(), attendanceId));
                return;
            }

            UserAttendanceDataExtend attendance_data = group.get_groupData().get(targetIdx);
            boolean canReward = false;
            synchronized (group) { // 그룹 객체로 락 범위를 최소화
                if (!group.get_resultCode().equals(UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR)
                        && attendance_data.get_state().equals(UserAttendanceState.COMPLETE)) {

                    attendance_data.set_state(UserAttendanceState.CLEAR);

                    if (targetIdx == group.get_groupData().size() - 1) {
                        group.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR);
                    }
                    canReward = true;
                }
            }

            if (!canReward) {
                pc.sendPackets("이미 보상을 받았거나 수령할 수 없는 상태입니다.");
                return;
            }

            egroup.doReward(pc, targetIdx);

            // 갱신 통보
            SC_ATTENDANCE_DATA_UPDATE_EXTEND.sendConstructed(pc, attendanceId, egroup, UserAttendanceState.CLEAR);

            // ACK
            SC_ATTENDANCE_REWARD_ACK.sendConstructed(pc, attendanceId, groupId, UserAttendanceState.CLEAR);

            // DB 동기화
            SC_ATTENDANCE_USER_DATA_EXTEND.update(pc.getAccountName(), userData);

        } catch (Exception e) {
            System.err.println(String.format("[출첵 에러] 유저: %s, 상세내용: %s", (pc != null ? pc.getName() : "Unknown"), e.getMessage()));
            e.printStackTrace();
        }
    }
}