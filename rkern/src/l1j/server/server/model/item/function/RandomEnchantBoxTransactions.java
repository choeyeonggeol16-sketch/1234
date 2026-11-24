package l1j.server.server.model.item.function;
import l1j.server.server.datatables.CObjectDB;
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

final class RandomEnchantBoxTransactions {
    private RandomEnchantBoxTransactions() {}

    static void useRandomEnchantBox(L1PcInstance pc, L1ItemInstance box) {
        if (pc == null || box == null || pc.getInventory().getItem(box.getId()) != box) return;
        final int[] rewards;
        final int[] enchants;
        final int boxId = box.getItemId();
        switch (boxId) {
        case 3000039:
            rewards = ENCHANT_BOX_3000039_REWARDS;
            enchants = ENCHANT_BOX_3000039_ENCHANTS;
            break;
        case 3000169:
            rewards = ENCHANT_BOX_3000169_REWARDS;
            enchants = ENCHANT_BOX_3000169_ENCHANTS;
            break;
        case 718:
            rewards = ENCHANT_BOX_718_REWARDS;
            enchants = ENCHANT_BOX_718_ENCHANTS;
            break;
        default:
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, box.getId())) {
            if (!tx.debit(box, 1)) return;
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            final int itemId = rewards[random.nextInt(rewards.length)];
            final int enchant = enchants[random.nextInt(enchants.length)];
            final L1Item template = CObjectDB.LoadItem().getTemplate(itemId);
            if (template == null) {
                SC_SystemMessage.send(pc, "상자 보상 아이템 설정을 확인해 주세요.");
                return;
            }
            // Build the reward with its enchant before granting, so a stack
            // merge cannot be performed using a different enchant level.
            final L1ItemInstance rewardImage = new L1ItemInstance(template, 1);
            rewardImage.setId(IdFactory.getInstance().nextId());
            rewardImage.setBless(template.getBless());
            rewardImage.setIdentified(true);
            rewardImage.setEnchantLevel(enchant);
            final L1ItemInstance reward = tx.grant(rewardImage);
            if (reward == null) {
                SC_SystemMessage.send(pc, "보상을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "상자 보상이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (boxId == 3000039) {
                    SC_SystemMessage.send(pc, "아이템을 [획득] 하였습니다.");
                } else {
                    SC_SkillSound.send(pc, pc.getId(), 11308);
                }
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 상자 보상을 소지할 수 없습니다.");
        }
    }}
