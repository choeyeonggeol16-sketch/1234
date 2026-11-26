package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of three class-based starter support boxes; transaction ownership belongs to the caller. */
final class StarterSupportPackages {
    private StarterSupportPackages() {}

    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {
		case 4100164:// 반놀자 지원 상자
			if (pc.getInventory().HaveItemByClassId(4100164, 1)) { // 체크 되는 아이템과 수량


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

		case 4100165:// 반하자 지원상자
			if (pc.getInventory().HaveItemByClassId(4100165, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 4200090, 1, 0, 129, 0, true);
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 4200091, 1, 0, 129, 0, true);
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 4200092, 1, 0, 129, 0, true);
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 4200093, 1, 0, 129, 0, true);
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 4200094, 1, 0, 129, 0, true);
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 4200095, 1, 0, 129, 0, true);
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 4200096, 1, 0, 129, 0, true);
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 4200097, 1, 0, 129, 0, true);
				}
			}
			break;

		case 3000035:// 하자 지원 상자
			if (pc.getInventory().HaveItemByClassId(3000035, 1)) { // 체크 되는 아이템과 수량				


				if (pc.isWarrior()) {
					stageReward(tx, messages, 4100087, 1, 0, 129, 0, true);
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 4100088, 1, 0, 129, 0, true);
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 4100089, 1, 0, 129, 0, true);
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 4100090, 1, 0, 129, 0, true);
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 4100091, 1, 0, 129, 0, true);
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 4100092, 1, 0, 129, 0, true);
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 4100093, 1, 0, 129, 0, true);
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 4100094, 1, 0, 129, 0, true);
				}
			}
			break;
        }
    }
}
