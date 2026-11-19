package l1j.server.server.model.item.cooking;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class L1Cooking {

	private L1Cooking() {}

    public static void useCookingItem(L1PcInstance pc, L1ItemInstance item) {
        CookingUseService.useCookingItem(pc, item);
    }

    public static void eatCooking(L1PcInstance pc, int cookingId, int timeSec) {
        CookingUseService.startCookingSkill(pc, cookingId, timeSec);
    }
}