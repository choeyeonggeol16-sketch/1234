package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

public final class ReceiptCraftingItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ReceiptCraftingItems() {}

	public static void UseDurableCraft(L1PcInstance pc, int inputId, int inputCount,
			int rewardId, Runnable afterCommitted) {
		try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(inputId, inputCount)) {
				SC_SystemMessage.send(pc, "제작 재료가 부족합니다.");
				return;
			}
			if (tx.grant(rewardId, 1) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 제작이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, afterCommitted);
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Item craft prepare failed: " + pc.getId() + " / " + inputId, failure);
			SC_SystemMessage.send(pc, "아이템 제작을 처리하지 못했습니다.");
		}
	}
}
