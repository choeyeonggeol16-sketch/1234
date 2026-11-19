package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PolyMorph;

/** Original useLevelPolyScroll item use implementation. */
final class LevelPolymorphScrollEffects {
    private LevelPolymorphScrollEffects() {}

	public static void useLevelPolyScroll(L1PcInstance pc, int itemId) {
		int polyId = 0;
		if (itemId == 210097) {
            polyId = Level30PolymorphForm.select(pc);
        } else if (itemId == 210098) {
            polyId = Level40PolymorphForm.select(pc);
        } else if (itemId == 210099) {
            polyId = Level52PolymorphForm.select(pc);
        } else if (itemId == 210100) {
            polyId = Level55PolymorphForm.select(pc);
        } else if (itemId == 210101) {
            polyId = Level60PolymorphForm.select(pc);
        } else if (itemId == 210102) {
            polyId = Level65PolymorphForm.select(pc);
        } else if (itemId == 210103) {
            polyId = Level70PolymorphForm.select(pc);
        } else if (itemId == 210116) {
            polyId = Level75PolymorphForm.select(pc);
        } else if (itemId == 210117) {
            polyId = Level80PolymorphForm.select(pc);
        }
		L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
	//	L1NeigeBugCheck.shapechange(pc);
	}
}
