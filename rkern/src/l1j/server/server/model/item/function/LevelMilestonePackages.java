package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Original achievement item entrypoint. Receipt ownership remains with the caller. */
final class LevelMilestonePackages {
    private LevelMilestonePackages() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {
            case 714 -> stageLevel82(pc, tx, messages);
            case 715 -> stageLevel84(pc, tx, messages);
            case 716 -> stageLevel86(pc, tx, messages);
            case 717 -> stageLevel88(pc, tx, messages);
            case 721 -> stageLevel89(pc, tx, messages);
            case 722 -> stageLevel90(pc, tx, messages);
            default -> { }
        }
    }

    // Exact class-sensitive rewards, in original order: no changes to receipts.
    private static void stageLevel82(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().HaveItemByClassId(714, 1)) { // 체크 되는 아이템과 수량


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

    private static void stageLevel84(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().HaveItemByClassId(715, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 400254, 5, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100044, 2, 0, 129, 0, true); // 신비한 마법인형 상자(4단계)
				}
			}
			    }

    private static void stageLevel86(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().HaveItemByClassId(716, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 400254, 7, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100045, 3, 0, 129, 0, true); // 신비한 마법인형 상자(5단계)
				}
			}
			    }

    private static void stageLevel88(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().HaveItemByClassId(717, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 400254, 10, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100046, 1, 0, 129, 0, true); // 신비한 마법인형 상자(특수)
					stageReward(tx, messages, 4100164, 1, 0, 129, 0, true); // 전설 변신권 상자
				}
			}
			    }

    private static void stageLevel89(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().HaveItemByClassId(721, 1)) { // 체크 되는 아이템과 수량


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

    private static void stageLevel90(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> messages) {
			if (pc.getInventory().HaveItemByClassId(722, 1)) { // 체크 되는 아이템과 수량


				if (pc.isWarrior()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isKnight()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isDragonknight()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isCrown()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isWizard()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isBlackwizard()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isElf()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
//					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
				if (pc.isDarkelf()) {
					stageReward(tx, messages, 400254, 30, 0, 129, 0, true); // 아데나 수표(1억)
					stageReward(tx, messages, 4100165, 1, 0, 129, 0, true); // 신화 변신권 상자
				}
			}
			    }
}
