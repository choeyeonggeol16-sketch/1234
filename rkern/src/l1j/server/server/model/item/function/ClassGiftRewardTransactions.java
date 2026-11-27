package l1j.server.server.model.item.function;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.CommonUtil;


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

final class ClassGiftRewardTransactions {
    private ClassGiftRewardTransactions() {}

    static void useClassGiftBundle(L1PcInstance pc, L1ItemInstance voucher) {
        if (pc == null || voucher == null || voucher.getItemId() != 5922794
                || pc.getInventory().GetByServerID(voucher.getId()) != voucher) return;
        final int classType = pc.getType();
        final int classReward;
        switch (classType) {
        case 0: case 1: case 4: case 5: case 7:
            classReward = 600209;
            break;
        case 2:
            classReward = pc.GetStatStr() < 40 ? 600210 : 600209;
            break;
        case 3: case 6:
            classReward = 600211;
            break;
        default:
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, voucher.getId())) {
            if (!tx.debit(voucher, 1)) return;
            if (tx.grant(classReward, 1) == null) {
                SC_SystemMessage.send(pc, "보상 아이템을 소지할 수 없습니다.");
                return;
            }
            for (int reward : CLASS_GIFT_COMMON) {
                if (tx.grant(reward, 1) == null) {
                    SC_SystemMessage.send(pc, "보상 아이템을 소지할 수 없습니다.");
                    return;
                }
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "클래스 보상이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> { });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 클래스 보상을 받을 수 없습니다.");
        }
    }

}
