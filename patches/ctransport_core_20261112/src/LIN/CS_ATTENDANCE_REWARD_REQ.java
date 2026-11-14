package LIN;

import LIN.AttendanceGroupType;
import LIN.UserAttendanceDataExtend;
import LIN.UserAttendanceDataGroup;
import LIN.UserAttendanceState;
import LIN.UserAttendanceTimeStatus;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoReader;


/**
 * CS_ATTENDANCE_REWARD_REQ (0x03EE) — 보상 수령 요청
 *  1 attendance_id int32 [required]
 *  2 group_id int32 [required]
 */
public final class CS_ATTENDANCE_REWARD_REQ {

    private CS_ATTENDANCE_REWARD_REQ() {}

    public static void handle(CIOClient client, ProtoReader r) {
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

            LIN.SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
            if (userData == null) return;

            LIN.AttendanceGroupType egroup = LIN.AttendanceGroupType.fromInt(groupId);
            if (egroup == null) return;

            LIN.UserAttendanceDataGroup group = userData.groups().get(egroup.toInt());
            if (group == null || group.groupData() == null) return;

            int targetIdx = attendanceId - 1;
            if (targetIdx < 0 || targetIdx >= group.groupData().size()) {
                System.err.println(String.format("[출첵해킹의심] 범위를 벗어난 인덱스! 유저: %s, ID: %d", pc.getName(), attendanceId));
                return;
            }

            LIN.UserAttendanceDataExtend attendance_data = group.groupData().get(targetIdx);
            boolean canReward = false;
            synchronized (group) { // 그룹 객체로 락 범위를 최소화
                if (!group.resultCode().equals(LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR)
                        && attendance_data.state().equals(LIN.UserAttendanceState.COMPLETE)) {

                    attendance_data.set_state(LIN.UserAttendanceState.CLEAR);

                    if (targetIdx == group.groupData().size() - 1) {
                        group.set_resultCode(LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR);
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
            LIN.SC_ATTENDANCE_DATA_UPDATE_EXTEND.sendConstructed(pc, attendanceId, egroup, LIN.UserAttendanceState.CLEAR);

            // ACK
            LIN.SC_ATTENDANCE_REWARD_ACK.sendConstructed(pc, attendanceId, groupId, LIN.UserAttendanceState.CLEAR);

            // DB 동기화
            LIN.SC_ATTENDANCE_USER_DATA_EXTEND.update(pc.getAccountName(), userData);

        } catch (Exception e) {
            System.err.println(String.format("[출첵 에러] 유저: %s, 상세내용: %s", (pc != null ? pc.getName() : "Unknown"), e.getMessage()));
            e.printStackTrace();
        }
    }
}