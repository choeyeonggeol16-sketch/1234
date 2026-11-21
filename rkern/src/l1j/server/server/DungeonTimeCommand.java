package l1j.server.server;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.dungeon.time.DungeonTimeInformation;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeInformationLoader;
import l1j.server.server.model.world.instance.dungeon.time.progress.DungeonTimeProgress;

/**
 * [19fix-1] .던전 명령 - 던전 이용시간 표시.
 *
 * 원래 UserCommands/GMCommands 의 switch 에 case "던전" 으로 연결할 예정이었으나
 * 두 클래스는 src 와 SGmall.jar 가 불일치(jar 에만 존재하는 멤버 다수)하여
 * src 컴파일 클래스 주입이 금지됨. 본 클래스는 신규 클래스이므로 jar 주입이 안전하며,
 * 메인이 jar 내 UserCommands.handleCommands / GMCommands 스위치에
 * DungeonTimeCommand.showDungeonTime(pc) 호출(INVOKESTATIC)만 바이트코드로 연결하면 된다.
 *
 * 로직은 SC_USER_PLAY_INFO_NOTI.send() (17~39행)와 동일:
 * DungeonTimeInformationLoader.get_enumerator() 순회, is_presentation 만 표시,
 * 남은초 = progress.get_remain_seconds() (진행기록 없으면 amount_seconds 풀시간).
 */
public class DungeonTimeCommand {

	private DungeonTimeCommand() {
	}

	// [19fix-1] 던전 이용시간 출력 (유저/GM 공용)
	public static void showDungeonTime(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		SC_SystemMessage.send(pc, "=== [ 던전 잔여 시간 현황 ] ===");

		for (DungeonTimeInformation dtInfo : DungeonTimeInformationLoader.getInstance().get_enumerator()) {
			if (dtInfo == null || !dtInfo.get_is_presentation()) {
				continue;
			}

			long remain = dtInfo.get_amount_seconds(); // 진행 기록이 없으면 최대 허용 시간
			DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
			if (progress != null) {
				remain = progress.get_remain_seconds();
			}
			if (remain < 0) {
				remain = 0;
			}

			// 남은 시간 계산 (시간, 분, 초)
			long rh = remain / 3600L;
			long rm = (remain % 3600L) / 60L;
			long rs = remain % 60L;

			// 한도 시간 계산 (시간, 분)
			long amount = dtInfo.get_amount_seconds();
			long ah = amount / 3600L;
			long am = (amount % 3600L) / 60L;

			StringBuilder sb = new StringBuilder(128);
			sb.append("").append(dtInfo.get_description()).append(" : ");

			// 남은 시간 포맷팅 적용
			if (rh > 0) sb.append(rh).append("시간 ");
			if (rm > 0 || rh > 0) sb.append(rm).append("분 ");
			sb.append(rs).append("초");

			sb.append(" (총 한도: ");
			if (ah > 0) sb.append(ah).append("시간 ");
			if (am > 0 || ah == 0) sb.append(am).append("분)");

			SC_SystemMessage.send(pc, sb.toString());
		}

		SC_SystemMessage.send(pc, "초기화 시간 : 매일 오전 09:00");
	}
}
