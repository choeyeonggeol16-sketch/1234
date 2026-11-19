package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
/** Sealed rune reward staging: level 80. Receipt ownership remains with the caller. */
final class SealedRuneLevel80Rewards {
    private SealedRuneLevel80Rewards() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 68078:
			if (pc.getInventory().checkItem(68078, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68094, 1, 0, 1, 0, true); // 마력의 고서(80)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900135, 1, 0, 1, 0, true); // 힘의 엘릭서 룬(80)
				}
			}
			break;


		case 68079:
			if (pc.getInventory().checkItem(68079, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68094, 1, 0, 1, 0, true); // 마력의 고서(80)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900136, 1, 0, 1, 0, true); // 민첩의 엘릭서 룬(80)
				}
			}
			break;


		case 68080:
			if (pc.getInventory().checkItem(68080, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68094, 1, 0, 1, 0, true); // 마력의 고서(80)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900137, 1, 0, 1, 0, true); // 체력의 엘릭서 룬(80)
				}
			}
			break;


		case 68081:
			if (pc.getInventory().checkItem(68081, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68094, 1, 0, 1, 0, true); // 마력의 고서(80)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900138, 1, 0, 1, 0, true); // 지식의 엘릭서 룬(80)
				}
			}
			break;


		case 68082:
			if (pc.getInventory().checkItem(68082, 1)) { // 체크 되는 아이템과 수량

				stageSealedReward(tx, messages, 68094, 1, 0, 1, 0, true); // 마력의 고서(80)
				if (pc.isWarrior()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isKnight()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isDragonknight()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isCrown()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isWizard()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isBlackwizard()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isElf()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
				if (pc.isDarkelf()) {
					stageSealedReward(tx, messages, 900139, 1, 0, 1, 0, true); // 지혜의 엘릭서 룬(80)
				}
			}
			break;
            default: break;
        }
    }
}
