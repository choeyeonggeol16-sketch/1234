package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import l1j.server.Config;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Domain-specific item handler; original transaction ordering preserved. */
final class DurableSkillReagentTransaction {
    private DurableSkillReagentTransaction() {}

    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    static void useDurableSkillReagent(L1PcInstance pc, int materialId,
            int amount, Runnable afterCommitted) {
        if (pc == null || amount <= 0) return;
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(materialId, amount)) {
                SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "마법재료 소모를 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, afterCommitted);
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Skill material transaction prepare failed: "
                    + pc.getId() + "/" + materialId, failure);
            SC_SystemMessage.send(pc, "마법재료 사용을 처리하지 못했습니다.");
        }
    }
}
