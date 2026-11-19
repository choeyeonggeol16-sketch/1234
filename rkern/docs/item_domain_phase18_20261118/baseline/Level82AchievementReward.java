package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Dedicated item-use handler extracted from a mixed domain. */
final class Level82AchievementReward {
    private Level82AchievementReward() {}

    static void stage(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().checkItem(714, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 3, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 50021, 20, 0, 129, 0, true); // 봉인 해제 주문서
					stageReward(tx, messages, 400254, 1, 0, 129, 0, true); // 아데나 수표(1억)
				}
			}
			    }
}
