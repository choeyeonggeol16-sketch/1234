package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level70PolymorphForm {
    private Level70PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL70POLYMORPHFORM
 // 70
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6882;
				} else {
					polyId = 6883;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6884;
				} else {
					polyId = 6885;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6886;
				} else {
					polyId = 6887;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6888;
				} else {
					polyId = 6889;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6890;
				} else {
					polyId = 6891;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7163;
				} else {
					polyId = 7164;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7165;
				} else {
					polyId = 7166;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL70POLYMORPHFORM
        return polyId;
    }
}
