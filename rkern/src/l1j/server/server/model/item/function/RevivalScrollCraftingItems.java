package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

public final class RevivalScrollCraftingItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private RevivalScrollCraftingItems() {}

	public static void UseDurableRevive(L1PcInstance pc, int inputId, int rewardId,
			String missingMessage, String successMessage) {
		try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(inputId, 8)) {
				SC_SystemMessage.send(pc, missingMessage);
				return;
			}
			if (tx.grant(rewardId, 1) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "제작을 저장하지 못했습니다. 재료를 확인해 주세요."));
			CDB.RequestUseItem(pc, tx, () -> {
				SC_SystemMessage.send(pc, "아이템 제작에 성공했습니다.");
				SC_SkillSound.send(pc, pc.getId(), 7976);
				SC_SkillSound.broadcast(pc, pc.getId(), 7976);
				SC_ChatPacket.sendDirect(pc, pc, successMessage, 1);
			});
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Item revival prepare failed: " + pc.getId() + " / " + inputId, failure);
			SC_SystemMessage.send(pc, "아이템 제작을 처리하지 못했습니다.");
		}
	}
}
