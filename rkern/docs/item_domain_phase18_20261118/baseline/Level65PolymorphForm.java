package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level65PolymorphForm {
    private Level65PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL65POLYMORPHFORM
 // 65
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6872;
				} else {
					polyId = 6873;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6874;
				} else {
					polyId = 6875;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6876;
				} else {
					polyId = 6877;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6878;
				} else {
					polyId = 6879;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6880;
				} else {
					polyId = 6881;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7159;
				} else {
					polyId = 7160;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7161;
				} else {
					polyId = 7162;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL65POLYMORPHFORM
        return polyId;
    }
}
