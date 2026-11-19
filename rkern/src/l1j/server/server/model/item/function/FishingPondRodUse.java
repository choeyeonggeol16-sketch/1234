package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.FishingUseRules.*;
import static l1j.server.server.model.item.function.FishingRodSession.doStartFishing;

final class FishingPondRodUse {
    private FishingPondRodUse() {}
    private static final int MAP_FISHING = 5490;
    private static final int TILE_WATER = 28;

    public static void clickItem1(
            L1PcInstance pc,
            int itemId,
            int fishX,
            int fishY,
            L1ItemInstance item,
            int itemObjectId) {

        if (pc == null || item == null) {
            return;
        }

        if (!preCheckCommon(pc)) {
            return;
        }

        if (pc.getMapId() != MAP_FISHING
                || fishX <= 32704
                || fishX >= 32831
                || fishY <= 32768
                || fishY >= 32895) {

            SC_ServerMessage.send(pc, 1138);
            return;
        }

        if (tileDistance(pc.getX(), pc.getY(), fishX, fishY) > 13) {
            SC_SystemMessage.send(pc, "조금 더 가까이서 던져주세요.");
            return;
        }

        if (requiresCharge(itemId) && safeCharge(item) <= 0) {
            return;
        }

        if (!checkInventory(pc)) {
            return;
        }

        // 실제 낚싯대를 던지는 목적지만 확인한다.
        if (getOriginalTile(MAP_FISHING, fishX, fishY) != TILE_WATER) {
            SC_ServerMessage.send(pc, 1138);
            return;
        }

        doStartFishing(pc, fishX, fishY, itemObjectId);
    }
}
