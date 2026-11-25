package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of beginner, equipment and accessory packages; transaction ownership belongs to the caller. */
final class EquipmentStarterPackages {
    private EquipmentStarterPackages() {}

    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {
		case 3000508://장비상자(초보)
			if (pc.getInventory().HaveItemByClassId(3000508, 1)) { // 체크 되는 아이템과 수량

				
				if (pc.isWarrior()) {
					stageReward(tx, messages, 3000573, 1, 0, 129, 0, true);
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 3000572, 1, 0, 129, 0, true);
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 3000578, 1, 0, 129, 0, true);
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 3000579, 1, 0, 129, 0, true);
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 3000575, 1, 0, 129, 0, true);
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 3000576, 1, 0, 129, 0, true);
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 3000574, 1, 0, 129, 0, true);
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 3000577, 1, 0, 129, 0, true);
				}
			}
			break;

		case 7020://반왕 패키지(7검/8셋/6악세)
			if (pc.getInventory().HaveItemByClassId(7020, 1)) { // 체크 되는 아이템과 수량

				
				if (pc.isWarrior()) {
					stageReward(tx, messages, 4100188, 1, 0, 1, 0, true);
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 4100187, 1, 0, 1, 0, true);
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 4100193, 1, 0, 1, 0, true);
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 4100194, 1, 0, 1, 0, true);
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 4100190, 1, 0, 1, 0, true);
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 4100191, 1, 0, 1, 0, true);
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 4100189, 1, 0, 1, 0, true);
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 4100192, 1, 0, 1, 0, true);
				}
			}
			break;

		case 7021://영웅 패키지(8검/9셋/7악세)
			if (pc.getInventory().HaveItemByClassId(7021, 1)) { // 체크 되는 아이템과 수량

				
				if (pc.isWarrior()) {
					stageReward(tx, messages, 4100196, 1, 0, 1, 0, true);
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 4100195, 1, 0, 1, 0, true);
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 4100201, 1, 0, 1, 0, true);
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 4100202, 1, 0, 1, 0, true);
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 4100198, 1, 0, 1, 0, true);
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 4100199, 1, 0, 1, 0, true);
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 4100197, 1, 0, 1, 0, true);
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 4100200, 1, 0, 1, 0, true);
				}
			}
			break;

		case 4100080:// 악세패키지 상자
			if (pc.getInventory().HaveItemByClassId(4100080, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 4100081, 1, 1, 1, 0, true); // 악세패키지(격수)
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 4100081, 1, 1, 1, 0, true); // 악세패키지(격수)
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 4100081, 1, 1, 1, 0, true); // 악세패키지(격수)
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 4100081, 1, 1, 1, 0, true); // 악세패키지(격수)
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 4100083, 1, 1, 1, 0, true); // 악세패키지(환,마)
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 4100083, 1, 1, 1, 0, true); // 악세패키지(환,마)
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 4100082, 1, 1, 1, 0, true); // 악세패키지(요정)
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 4100081, 1, 1, 1, 0, true); // 악세패키지(격수)
				}
			}
			break;
        }
    }
}
