package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level40PolymorphForm {
    private Level40PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL40POLYMORPHFORM
 // 40
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6832;
				} else {
					polyId = 6833;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6834;
				} else {
					polyId = 6835;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6836;
				} else {
					polyId = 6837;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6838;
				} else {
					polyId = 6839;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6840;
				} else {
					polyId = 6841;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7143;
				} else {
					polyId = 7144;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7145;
				} else {
					polyId = 7146;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL40POLYMORPHFORM
        return polyId;
    }
}
