package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
/** Sealed rune reward staging: level 90. Receipt ownership remains with the caller. */
final class SealedRuneLevel90Rewards {
    private SealedRuneLevel90Rewards() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 68088:
			if (pc.getInventory().checkItem(68088, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68096, 1, 0, 1, 0, true); // 마력의 고서(90)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900145, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(90)
				}
			}
			break;


		case 68089:
			if (pc.getInventory().checkItem(68089, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68096, 1, 0, 1, 0, true); // 마력의 고서(90)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900146, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(90)
				}
			}
			break;


		case 68090:
			if (pc.getInventory().checkItem(68090, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68096, 1, 0, 1, 0, true); // 마력의 고서(90)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900147, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(90)
				}
			}
			break;


		case 68091:
			if (pc.getInventory().checkItem(68091, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68096, 1, 0, 1, 0, true); // 마력의 고서(90)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900148, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(90)
				}
			}
			break;


		case 68092:
			if (pc.getInventory().checkItem(68092, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68096, 1, 0, 1, 0, true); // 마력의 고서(90)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900149, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(90)
				}
			}
			break;
            default: break;
        }
    }
}
