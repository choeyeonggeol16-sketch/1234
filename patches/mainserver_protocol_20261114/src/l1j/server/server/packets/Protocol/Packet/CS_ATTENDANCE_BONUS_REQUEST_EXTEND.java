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
 * CS_ATTENDANCE_BONUS_REQUEST_EXTEND (0x0222) — 보상 수령 요청
 *  1 index int32 [required]
 *  2 group enum [required]
 */
public final class CS_ATTENDANCE_BONUS_REQUEST_EXTEND {

	private CS_ATTENDANCE_BONUS_REQUEST_EXTEND() {}

	public static void handleRequest(CIOClient client, ProtoReader r) {
		// ── 파싱 ──
		int index = 0;
		AttendanceGroupType group = null;
		while (!r.isAtEnd()) {
			int tag = r.tag();
			switch (tag >>> 3) {
			case 1:
				index = r.varint32();
				break;
			case 2:
				group = AttendanceGroupType.fromInt(r.varint32());
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

			SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
			if (userData == null || group == null) return;

			UserAttendanceDataGroup g = userData.get_groups().get(group.toInt());
			if (g == null) return;

			int targetIndex = index - 1;
			if (targetIndex < 0 || targetIndex >= g.get_groupData().size()) return;

			UserAttendanceDataExtend data = g.get_groupData().get(targetIndex);

			boolean canProceed = false;
			synchronized (g) {
				if (g.get_resultCode() != UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR && data.get_state() == UserAttendanceState.COMPLETE) {
					data.set_state(UserAttendanceState.CLEAR);
					if (targetIndex == g.get_groupData().size() - 1) {
						g.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR);
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
			SC_ATTENDANCE_DATA_UPDATE_EXTEND.sendConstructed(pc, index, group, UserAttendanceState.CLEAR);

			// DB 동기화
			SC_ATTENDANCE_USER_DATA_EXTEND.update(pc.getAccountName(), userData);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}