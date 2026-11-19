package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.FishingUseRules.*;
import static l1j.server.server.model.item.function.FishingRodSession.doStartFishing;

final class FishingIslandRodUse {
    private FishingIslandRodUse() {}
    private static final int MAP_TALKING_ISLAND = 4;

    public static void clickItem(L1PcInstance pc, int itemId, int fishX, int fishY, L1ItemInstance item, int itemObjectId) {
        if (pc == null || item == null) return;

        if (!preCheckCommon(pc)) return;

        if (pc.getMapId() != MAP_TALKING_ISLAND) {
            SC_ServerMessage.send(pc, 1138);
            return;
        }

        if (tileDistance(pc.getX(), pc.getY(), fishX, fishY) > 13) {
            SC_SystemMessage.send(pc, "조금 더 가까이서 던져주세요.");
            return;
        }

        if (pc.hasSkillEffect(L1SkillId.SHAPE_CHANGE)) {
            SC_SystemMessage.send(pc, "변신중엔 낚시가 불가능합니다.");
            return;
        }
        if (pc.hasSkillEffect(L1SkillId.INVISIBILITY)) {
            SC_SystemMessage.send(pc, "투명상태에서는 낚시를 할 수 없습니다.");
            return;
        }

        if (requiresCharge(itemId) && safeCharge(item) <= 0) return;

        if (!checkInventory(pc)) return;

        final int baseX = 33602, baseY = 33245, px = 5, py = 5;
        if (!inBox(fishX, fishY, baseX, baseY, px, py)) {
            SC_ServerMessage.send(pc, 1138);
            return;
        }

        doStartFishing(pc, fishX, fishY, itemObjectId);
    }
}
