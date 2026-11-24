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
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import static l1j.server.server.model.item.function.EnchantBoxRollTables.*;
import static l1j.server.server.model.item.function.ClassGiftBundleTable.*;

final class MoneyBoxTransactions {
    private MoneyBoxTransactions() {}

    static void useRandomMoneyBox(L1PcInstance pc, L1ItemInstance box) {
        if (pc == null || box == null) return;
        final int id = box.getItemId();
        final int rewardId = id == 3000194 ? 40308 : 41302;
        final int roll = CommonUtil.random(100);
        final int count;
        switch (id) {
        case 3000194:
            count = roll <= 10 ? CommonUtil.random(10000, 15000)
                    : CommonUtil.random(15000, 20000);
            break;
        case 41299:
            count = roll <= 7 ? CommonUtil.random(500000, 600000)
                    : CommonUtil.random(300000, 400000);
            break;
        case 41300:
            count = roll <= 7 ? CommonUtil.random(5000000, 6000000)
                    : CommonUtil.random(3000000, 4000000);
            break;
        case 41303:
            count = roll <= 7 ? CommonUtil.random(600000, 700000)
                    : CommonUtil.random(400000, 500000);
            break;
        case 41304:
            count = roll <= 7 ? CommonUtil.random(6000000, 7000000)
                    : CommonUtil.random(4000000, 5000000);
            break;
        default: return;
        }
        // Preserve the original <=0.3 check against the integer 0..99 roll.
        final boolean attendanceBonus = id == 3000194 && CommonUtil.random(100) <= 0.3d;
        if (count <= 0) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, box.getId())) {
            if (!tx.debit(box, 1)) return;
            if (tx.grant(rewardId, count) == null) {
                SC_SystemMessage.send(pc, "보상을 소지할 수 없습니다.");
                return;
            }
            if (attendanceBonus && tx.grant(3000176, CommonUtil.random(1, 1)) == null) {
                SC_SystemMessage.send(pc, "보상을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "상자 보상이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> { });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 상자 보상을 소지할 수 없습니다.");
        }
    }

    static void useBurnedCharm(L1PcInstance pc, L1ItemInstance charm) {
        if (pc == null || charm == null || pc.getInventory().getItem(charm.getId()) != charm) return;
        final int id = charm.getItemId();
        final int min;
        final int max;
        if (id == 4100120) {
            min = Config.불멸가호아덴;
            max = Config.불멸가호아덴1;
        } else if (id == 4100119) {
            min = Config.불멸가호아덴2;
            max = Config.불멸가호아덴22;
        } else return;
        if (min <= 0 || max < min || max > L1Inventory.MAX_AMOUNT) {
            SC_SystemMessage.send(pc, "불멸의 가호 보상 설정을 확인해 주세요.");
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, charm.getId())) {
            if (!tx.debit(charm, 1)) return;
            final int reward = CommonUtil.random(min, max);
            if (tx.grant(40308, reward) == null) {
                SC_SystemMessage.send(pc, "아데나를 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "불멸의 가호 보상이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> { });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 보상을 소지할 수 없습니다.");
        }
    }

}
