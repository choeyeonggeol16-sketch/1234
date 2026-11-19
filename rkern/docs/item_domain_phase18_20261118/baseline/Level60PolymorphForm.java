package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level60PolymorphForm {
    private Level60PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL60POLYMORPHFORM
 // 60
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6862;
				} else {
					polyId = 6863;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6864;
				} else {
					polyId = 6865;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6866;
				} else {
					polyId = 6867;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6868;
				} else {
					polyId = 6869;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6870;
				} else {
					polyId = 6871;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7155;
				} else {
					polyId = 7156;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7157;
				} else {
					polyId = 7158;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL60POLYMORPHFORM
        return polyId;
    }
}
