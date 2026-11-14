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
 * CS_ATTENDANCE_BONUS_REQUEST_EXTEND (0x0222) — 보상 수령 요청
 *  1 index int32 [required]
 *  2 group enum [required]
 */
public final class CS_ATTENDANCE_BONUS_REQUEST_EXTEND {

	private CS_ATTENDANCE_BONUS_REQUEST_EXTEND() {}

	public static void handle(CIOClient client, ProtoReader r) {
		// ── 파싱 ──
		int index = 0;
		LIN.AttendanceGroupType group = null;
		while (!r.isAtEnd()) {
			int tag = r.tag();
			switch (tag >>> 3) {
			case 1:
				index = r.varint32();
				break;
			case 2:
				group = LIN.AttendanceGroupType.fromInt(r.varint32());
				break;
			default:
				r.skip(tag & 7);
				break;
			}
		}

		// ── 실행 ──
		try {
			L1PcInstance pc = client.getActiveChar();
			if (pc == null) return;

			LIN.SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
			if (userData == null || group == null) return;

			LIN.UserAttendanceDataGroup g = userData.groups().get(group.toInt());
			if (g == null) return;

			int targetIndex = index - 1;
			if (targetIndex < 0 || targetIndex >= g.groupData().size()) return;

			LIN.UserAttendanceDataExtend data = g.groupData().get(targetIndex);

			boolean canProceed = false;
			synchronized (g) {
				if (g.resultCode() != LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR && data.state() == LIN.UserAttendanceState.COMPLETE) {
					data.set_state(LIN.UserAttendanceState.CLEAR);
					if (targetIndex == g.groupData().size() - 1) {
						g.set_resultCode(LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR);
					}
					canProceed = true;
				}
			}

			if (!canProceed) {
				pc.sendPackets("이미 보상을 받았거나 수령 불가능한 상태입니다.");
				return;
			}

			// 아이템 지급
			group.doReward(pc, targetIndex);

			// 갱신 패킷 전송
			LIN.SC_ATTENDANCE_DATA_UPDATE_EXTEND.sendConstructed(pc, index, group, LIN.UserAttendanceState.CLEAR);

			// DB 동기화
			LIN.SC_ATTENDANCE_USER_DATA_EXTEND.update(pc.getAccountName(), userData);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}