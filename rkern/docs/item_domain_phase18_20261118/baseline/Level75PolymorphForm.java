package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;

/** Exact Phase 9 form selection for this level scroll; no state or allocation. */
final class Level75PolymorphForm {
    private Level75PolymorphForm() {}

    static int select(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL75POLYMORPHFORM
 // 75
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 10987;
				} else {
					polyId = 10988;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 10989;
				} else {
					polyId = 10990;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 10991;
				} else {
					polyId = 10992;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 10993;
				} else {
					polyId = 10994;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 10995;
				} else {
					polyId = 10996;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 10997;
				} else {
					polyId = 10998;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 10999;
				} else {
					polyId = 11000;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL75POLYMORPHFORM
        return polyId;
    }
}
