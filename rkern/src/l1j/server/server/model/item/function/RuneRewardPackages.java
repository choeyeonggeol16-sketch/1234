package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
final class RuneRewardPackages {
    private RuneRewardPackages() {}

    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {

        switch (itemId) {
            case 68088, 68089, 68090, 68091, 68092 -> stageLevel90(pc, itemId, tx, messages);
            case 68083, 68084, 68085, 68086, 68087 -> stageLevel85(pc, itemId, tx, messages);
            case 68078, 68079, 68080, 68081, 68082 -> stageLevel80(pc, itemId, tx, messages);
            case 60041, 60042, 60043, 60044, 60045 -> stageLevel70(pc, itemId, tx, messages);
            case 60036, 60037, 60038, 60039, 60040 -> stageLevel55(pc, itemId, tx, messages);
            default -> { }
        }
    }

    // Level 55 reward conditions and receipt order remain unchanged.
    private static void stageLevel55(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 60036:
			if (pc.getInventory().HaveItemByClassId(60036, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60037, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60038, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60039, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60040, 1)) { // 체크 되는 아이템과 수량

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

    // Level 70 reward conditions and receipt order remain unchanged.
    private static void stageLevel70(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 60041:
			if (pc.getInventory().HaveItemByClassId(60041, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60042, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60043, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60044, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(60045, 1)) { // 체크 되는 아이템과 수량

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

    // Level 80 reward conditions and receipt order remain unchanged.
    private static void stageLevel80(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 68078:
			if (pc.getInventory().HaveItemByClassId(68078, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68079, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68080, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68081, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68082, 1)) { // 체크 되는 아이템과 수량

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

    // Level 85 reward conditions and receipt order remain unchanged.
    private static void stageLevel85(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 68083:
			if (pc.getInventory().HaveItemByClassId(68083, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68084, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68085, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68086, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68087, 1)) { // 체크 되는 아이템과 수량

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

    // Level 90 reward conditions and receipt order remain unchanged.
    private static void stageLevel90(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {

		case 68088:
			if (pc.getInventory().HaveItemByClassId(68088, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68089, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68090, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68091, 1)) { // 체크 되는 아이템과 수량

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
			if (pc.getInventory().HaveItemByClassId(68092, 1)) { // 체크 되는 아이템과 수량

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
