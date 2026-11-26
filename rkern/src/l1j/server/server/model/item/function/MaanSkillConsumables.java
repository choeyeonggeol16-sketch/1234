package l1j.server.server.model.item.function;

import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.SpecialConsumableRouter.nowTimestamp;
import l1j.server.server.model.item.function.SpecialConsumableRouter.Result;

final class MaanSkillConsumables {
    private MaanSkillConsumables() {}

    static Result useLifeMaanOnce(L1PcInstance pc, L1ItemInstance item) {
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) return Result.RETURN;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 사용이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                castSelf(pc, L1SkillId.LIFE_MAAN);
                L1ItemDelay.onItemUse(pc, item);
            });
        } catch (IllegalStateException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중입니다. 다시 시도해 주세요.");
        }
        return Result.RETURN;
    }

    static void castAndTouch(
            L1PcInstance pc,
            L1ItemInstance item,
            int skillId) {

        castSelf(pc, skillId);
        item.setLastUsed(nowTimestamp());
    }

    static void castSelf(
            L1PcInstance pc,
            int skillId) {

        L1SkillUse.cast(
                pc,
                skillId,
                pc.getId(),
                pc.getX(),
                pc.getY(),
                null,
                0,
                L1SkillUse.TYPE_GMBUFF);
    }
}
