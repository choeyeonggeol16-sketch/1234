package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
/** Sealed rune reward staging: level 70. Receipt ownership remains with the caller. */
final class SealedRuneLevel70Rewards {
    private SealedRuneLevel70Rewards() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 60041:
			if (pc.getInventory().checkItem(60041, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68093, 1, 0, 1, 0, true); // 마력의 고서(70)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222316, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(70레벨)
				}
			}
			break;


		case 60042:
			if (pc.getInventory().checkItem(60042, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68093, 1, 0, 1, 0, true); // 마력의 고서(70)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222312, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
			}
			break;


		case 60043:
			if (pc.getInventory().checkItem(60043, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68093, 1, 0, 1, 0, true); // 마력의 고서(70)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222313, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
			}
			break;


		case 60044:
			if (pc.getInventory().checkItem(60044, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68093, 1, 0, 1, 0, true); // 마력의 고서(70)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222314, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(70레벨)
				}
			}
			break;


		case 60045:
			if (pc.getInventory().checkItem(60045, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68093, 1, 0, 1, 0, true); // 마력의 고서(70)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 222315, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(70레벨)
				}
			}
			break;
            default: break;
        }
    }
}
