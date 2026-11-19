package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Existing public fishing item API; routing is allocation-free. */
public final class FishitemService {
    private FishitemService() {}

    public static void clickItem(L1PcInstance pc, int itemId, int fishX, int fishY,
            L1ItemInstance item, int itemObjectId) {
        FishingIslandRodUse.clickItem(pc, itemId, fishX, fishY, item, itemObjectId);
    }

    public static void clickItem1(L1PcInstance pc, int itemId, int fishX, int fishY,
            L1ItemInstance item, int itemObjectId) {
        FishingPondRodUse.clickItem1(pc, itemId, fishX, fishY, item, itemObjectId);
    }
}
