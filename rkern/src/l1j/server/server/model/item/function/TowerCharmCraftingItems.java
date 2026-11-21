package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

public final class TowerCharmCraftingItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private TowerCharmCraftingItems() {}

	public static void UseDurableTowerCharm(L1PcInstance pc, L1ItemInstance input) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(560028, 1) || !tx.debit(830011, 200)
					|| !tx.debit(40308, 500000000) || !tx.debit(input, 1)) {
				SC_SystemMessage.send(pc, "재료가 부족합니다.");
				return;
			}
			if (tx.grant(4100135, 1) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "부적 제작이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, () -> SC_ChatPacket.sendDirect(pc, pc,
					"환상의 지배의 탑 지배 부적을 제작 하였습니다.", 1));
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Tower charm prepare failed: " + pc.getId(), failure);
			SC_SystemMessage.send(pc, "부적 제작을 처리하지 못했습니다.");
		}
	}
}
