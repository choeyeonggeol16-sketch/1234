package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level52PolymorphForm {
    private Level52PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL52POLYMORPHFORM
 // 52
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6842;
				} else {
					polyId = 6843;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6844;
				} else {
					polyId = 6845;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6846;
				} else {
					polyId = 6847;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6848;
				} else {
					polyId = 6849;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6850;
				} else {
					polyId = 6851;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7147;
				} else {
					polyId = 7148;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7149;
				} else {
					polyId = 7150;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL52POLYMORPHFORM
        return polyId;
    }
}
