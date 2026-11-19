package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
/** Sealed rune reward staging: level 55. Receipt ownership remains with the caller. */
final class SealedRuneLevel55Rewards {
    private SealedRuneLevel55Rewards() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 60036:
			if (pc.getInventory().checkItem(60036, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 60033, 1, 0, 1, 0, true); // 빛바랜 고서
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); //힘의 엘릭서 룬: 55레벨
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222299, 1, 0, 1, 0, true); // 힘의 엘릭서 룬: 55레벨
				}
			}
			break;


		case 60037:
			if (pc.getInventory().checkItem(60037, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 60033, 1, 0, 1, 0, true); // 빛바랜 고서
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222295, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬: 55레벨
				}
			}
			break;


		case 60038:
			if (pc.getInventory().checkItem(60038, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 60033, 1, 0, 1, 0, true); // 빛바랜 고서
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222296, 1, 0, 1, 0, true); // 체력의 엘릭서 룬: 55레벨
				}
			}
			break;


		case 60039:
			if (pc.getInventory().checkItem(60039, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 60033, 1, 0, 1, 0, true); // 빛바랜 고서
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222297, 1, 0, 1, 0, true); // 지식의 엘릭서 룬: 55레벨
				}
			}
			break;


		case 60040:
			if (pc.getInventory().checkItem(60040, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 60033, 1, 0, 1, 0, true); // 빛바랜 고서
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222298, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬: 55레벨
				}
			}
			break;
            default: break;
        }
    }
}
