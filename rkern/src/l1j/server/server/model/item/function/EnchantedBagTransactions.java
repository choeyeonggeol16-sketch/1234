package l1j.server.server.model.item.function;


import l1j.server.server.datatables.CObjectDB;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

public final class EnchantedBagTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private EnchantedBagTransactions() {}

    public static void UseDurableEnchantedBag(L1PcInstance pc, L1ItemInstance input,
            int rewardId, int enchantLevel) {
        if (pc == null || input == null
                || pc.getInventory().GetByServerID(input.getId()) != input
                || (input.getItemId() != 3000126 && input.getItemId() != 3000127)
                || (input.getItemId() == 3000126 ? rewardId != 900021 : rewardId != 900020)
                || enchantLevel != 3) return;
        l1j.server.server.templates.L1Item definition = CObjectDB.LoadItem().getTemplate(rewardId);
        if (definition == null) return;
        // A detached image is required: createItem() publishes to CWorld and
        // is invalid for a receipt-backed NEW grant.
        L1ItemInstance reward = new L1ItemInstance(definition, 1);
        // grant() snapshots via Warehouse.transferCopy(), which requires a
        // positive ID, but this ID must not already exist in the world.
        reward.setId(IdFactory.getInstance().nextId());
        reward.setBless(definition.getBless());
        reward.setEnchantLevel(enchantLevel);
        reward.setIdentified(true);
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            L1ItemInstance prepared = tx.grant(reward);
            if (prepared == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "문장 주머니 교환이 저장되지 않았습니다."));
            CDB.RequestUseItem(pc, tx, () -> SC_ServerMessage.send(pc, 403, prepared.getLogName()));
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Enchanted bag prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "문장 주머니 사용을 처리하지 못했습니다.");
        }
    }
}
