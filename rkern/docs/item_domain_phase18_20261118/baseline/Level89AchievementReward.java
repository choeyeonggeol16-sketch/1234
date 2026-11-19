package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Dedicated item-use handler extracted from a mixed domain. */
final class Level89AchievementReward {
    private Level89AchievementReward() {}

    static void stage(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().checkItem(721, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
				}
			}
			    }
}
