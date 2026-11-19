package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Preserve original level/elixir-count checks and message precedence. */
final class SupplementalElixirEligibility {
    private SupplementalElixirEligibility() {}

    static boolean allow(L1PcInstance pc, int itemId) {


		if (itemId >= 40033 && itemId <= 40038) {
			int currentElixirCount = pc.getElixirStats(); // 현재까지 먹은 엘릭서 개수
			int pcLevel = pc.getLevel();
			int maxAllowed = 0;

			// 레벨별 허용 개수 계산
			if (pcLevel >= 80) maxAllowed = 6;
			else if (pcLevel >= 75) maxAllowed = 5;
			else if (pcLevel >= 70) maxAllowed = 4;
			else if (pcLevel >= 65) maxAllowed = 3;
			else if (pcLevel >= 60) maxAllowed = 2;
			else if (pcLevel >= 55) maxAllowed = 1;

			// 1. 최소 레벨 미달 체크
			if (pcLevel < 55) {
				SC_SystemMessage.send(pc, "엘릭서는 55레벨부터 복용 가능합니다.");
				return false;
			}

			// 2. 현재 레벨에서 먹을 수 있는 개수 초과 체크
			if (currentElixirCount >= maxAllowed) {
				SC_ServerMessage.send(pc, 4472); 
				return false;
			}

			// 3. 전체 최대 개수(6개) 이미 달성 체크
			if (currentElixirCount >= 6) {
				SC_SystemMessage.send(pc, "엘릭서는 최대 6개까지만 복용 가능합니다.");
				return false;
			}
		}
        return true;
    }
}
