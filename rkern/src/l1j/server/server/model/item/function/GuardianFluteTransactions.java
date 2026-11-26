package l1j.server.server.model.item.function;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.CommonUtil;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import static l1j.server.server.model.item.function.EnchantBoxRollTables.*;
import static l1j.server.server.model.item.function.ClassGiftBundleTable.*;

final class GuardianFluteTransactions {
    private GuardianFluteTransactions() {}

    static void useGuardianFlute(L1PcInstance pc, L1ItemInstance flute) {
        if (pc == null || flute == null || flute.getItemId() != 40493
                || pc.getInventory().GetByServerID(flute.getId()) != flute) return;
        boolean guardianPresent = false;
        for (l1j.server.server.model.L1Object visible : pc.getKnownObjects()) {
            if (visible instanceof l1j.server.server.model.Instance.L1GuardianInstance guardian
                    && guardian.getNpcTemplate() != null
                    && guardian.getNpcTemplate().get_npcId() == 70850) {
                guardianPresent = true;
                break;
            }
        }
        if (!guardianPresent) {
            l1j.server.server.model.network.core.sc.packet.SC_Sound.send(pc, 165);
            l1j.server.server.model.network.core.sc.packet.SC_Sound.broadcast(pc, 165);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, flute.getId())) {
            if (!tx.debit(flute, 1)) return;
            if (tx.grant(88, 1) == null) {
                SC_SystemMessage.send(pc, "보상을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "플룻 보상이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                l1j.server.server.model.network.core.sc.packet.SC_Sound.send(pc, 165);
                l1j.server.server.model.network.core.sc.packet.SC_Sound.broadcast(pc, 165);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 플룻 보상을 받을 수 없습니다.");
        }
    }

}
