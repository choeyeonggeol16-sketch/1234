package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PolyMorph;

/** Original useLevelPolyScroll item use implementation. */
final class LevelPolymorphScrollEffects {
    private LevelPolymorphScrollEffects() {}

	public static void useLevelPolyScroll(L1PcInstance pc, int itemId) {
		int polyId = 0;
		if (itemId == 210097) {
            polyId = selectLevel30(pc);
        } else if (itemId == 210098) {
            polyId = selectLevel40(pc);
        } else if (itemId == 210099) {
            polyId = selectLevel52(pc);
        } else if (itemId == 210100) {
            polyId = selectLevel55(pc);
        } else if (itemId == 210101) {
            polyId = selectLevel60(pc);
        } else if (itemId == 210102) {
            polyId = selectLevel65(pc);
        } else if (itemId == 210103) {
            polyId = selectLevel70(pc);
        } else if (itemId == 210116) {
            polyId = selectLevel75(pc);
        } else if (itemId == 210117) {
            polyId = selectLevel80(pc);
        }
		L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
	//	L1NeigeBugCheck.shapechange(pc);
	}

    // Original form-selection branches preserved in the same item-use domain.
    private static int selectLevel30(L1PcInstance pc) {
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

    private static int selectLevel40(L1PcInstance pc) {
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

    private static int selectLevel52(L1PcInstance pc) {
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

    private static int selectLevel55(L1PcInstance pc) {
        int polyId = 0;
        // BEGIN_ORIGINAL_LEVEL55POLYMORPHFORM
 // 55
			if (pc.isCrown()) {
				if (pc.get_sex() == 0) {
					polyId = 6852;
				} else {
					polyId = 6853;
				}
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0) {
					polyId = 6854;
				} else {
					polyId = 6855;
				}
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0) {
					polyId = 6856;
				} else {
					polyId = 6857;
				}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0) {
					polyId = 6858;
				} else {
					polyId = 6859;
				}
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0) {
					polyId = 6860;
				} else {
					polyId = 6861;
				}
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0) {
					polyId = 7151;
				} else {
					polyId = 7152;
				}
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0) {
					polyId = 7153;
				} else {
					polyId = 7154;
				}
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0) {
					polyId = 12490;
				} else {
					polyId = 12494;
				}
			}
		        // END_ORIGINAL_LEVEL55POLYMORPHFORM
        return polyId;
    }

    private static int selectLevel60(L1PcInstance pc) {
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

    private static int selectLevel65(L1PcInstance pc) {
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

    private static int selectLevel70(L1PcInstance pc) {
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

    private static int selectLevel75(L1PcInstance pc) {
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

    private static int selectLevel80(L1PcInstance pc) {
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
