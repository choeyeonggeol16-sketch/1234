package l1j.server.server.model.item.function;


import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.MJCommons;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;

import static l1j.server.server.model.item.function.CreatureItemActions.*;

public final class RevivalScrollCraftingItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private RevivalScrollCraftingItems() {}

	public static void UseDurableRevive(L1PcInstance pc, int inputId, int rewardId,
			String missingMessage, String successMessage) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
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
