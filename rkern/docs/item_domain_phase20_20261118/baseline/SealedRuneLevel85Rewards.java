package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
/** Sealed rune reward staging: level 85. Receipt ownership remains with the caller. */
final class SealedRuneLevel85Rewards {
    private SealedRuneLevel85Rewards() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 68083:
			if (pc.getInventory().checkItem(68083, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68095, 1, 0, 1, 0, true); // 마력의 고서(85)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900140, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(85)
				}
			}
			break;


		case 68084:
			if (pc.getInventory().checkItem(68084, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68095, 1, 0, 1, 0, true); // 마력의 고서(85)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900141, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(85)
				}
			}
			break;


		case 68085:
			if (pc.getInventory().checkItem(68085, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68095, 1, 0, 1, 0, true); // 마력의 고서(85)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900142, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(85)
				}
			}
			break;


		case 68086:
			if (pc.getInventory().checkItem(68086, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68095, 1, 0, 1, 0, true); // 마력의 고서(85)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900143, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(85)
				}
			}
			break;


		case 68087:
			if (pc.getInventory().checkItem(68087, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68095, 1, 0, 1, 0, true); // 마력의 고서(85)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900144, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(85)
				}
			}
			break;
            default: break;
        }
    }
}
