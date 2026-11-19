package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level30PolymorphForm {
    private Level30PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL30POLYMORPHFORM
 // 30
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6822;
				} else {
					polyId = 6823;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6824;
				} else {
					polyId = 6825;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6826;
				} else {
					polyId = 6827;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6828;
				} else {
					polyId = 6829;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6830;
				} else {
					polyId = 6831;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7139;
				} else {
					polyId = 7140;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7141;
				} else {
					polyId = 7142;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL30POLYMORPHFORM
        return polyId;
    }
}
