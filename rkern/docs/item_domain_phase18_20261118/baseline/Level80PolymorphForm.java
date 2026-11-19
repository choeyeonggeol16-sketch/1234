package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level80PolymorphForm {
    private Level80PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL80POLYMORPHFORM
 // 80
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 11001;
				} else {
					polyId = 11002;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 11003;
				} else {
					polyId = 11004;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 11005;
				} else {
					polyId = 11006;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 11007;
				} else {
					polyId = 11008;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 11009;
				} else {
					polyId = 11010;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 11011;
				} else {
					polyId = 11012;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 11013;
				} else {
					polyId = 11014;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
				// } else if (itemId == 813) { // 수련자 영웅 변신 주문서
				// if (pc.isCrown()) {
				// polyId = 12283;
				// } else if (pc.isKnight()) {
				// polyId = 12283;
				// } else if (pc.isElf()) {
				// polyId = 12314;
				// } else if (pc.isWizard()) {
				// polyId = 12286;
				// } else if (pc.isDarkelf()) {
				// polyId = 12280;
				// } else if (pc.isDragonknight()) {
				// polyId = 12283;
				// } else if (pc.isBlackwizard()) {
				// polyId = 12286;
				// } else if (pc.is전사()) {
				// polyId = 12283;
				// }
			}
		        // END_ORIGINAL_LEVEL80POLYMORPHFORM
        return polyId;
    }
}
