package l1j.server.server.model.item.function;



import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SupportInstance;



import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.types.Point;




import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.WandInspectionItems.*;
import static l1j.server.server.model.item.function.CombatSkillItemEffects.*;
import static l1j.server.server.model.item.function.DogFightEnhancementItems.*;
import static l1j.server.server.model.item.function.NpcWandAndSpawnItems.*;
import static l1j.server.server.model.item.function.WarriorSkillBookItems.*;
import static l1j.server.server.model.item.function.PcRoomPassTransactions.*;
import static l1j.server.server.model.item.function.CashBuffTransactions.*;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ReceiptCraftingItems.*;
import static l1j.server.server.model.item.function.RevivalScrollCraftingItems.*;
import static l1j.server.server.model.item.function.TowerCharmCraftingItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.PetVoucherTransactions.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.SealedEquipmentRewards.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;

import static l1j.server.server.model.item.function.CreatureItemActions.*;

public final class SupportSummonItems {
    private SupportSummonItems() {}

	public static void useSupport(L1PcInstance pc, int itemId, int itemObjectId) { // 쫄법사
//		if (!pc.getMap().isTakePets()) {
//			pc.sendPackets(new SC_ServerMessage(563)); // \f1 여기에서는 사용할 수 없습니다.
//			return;
//		}
//
//		if (pc.isInWarArea()) {
//			pc.sendPackets(new SC_ServerMessage(563)); // \f1 여기에서는 사용할 수 없습니다.
//			return;
//		}
//
//		if (pc.getMapId() == 5153 || pc.getMapId() == 5140) {
//			pc.sendPackets(new SC_ServerMessage(563)); // \f1 여기에서는 사용할 수 없습니다.
//			return;
//		}
//
//		if (!pc.getInventory().checkItem(40308, 5000)) {
//			pc.sendPackets(new SC_ServerMessage(337, "$4")); // \f1%0이 부족합니다.
//			return;
//		}
//		if (pc.getLevel() > 75) {
//			pc.sendPackets(new SC_SystemMessage("도우미: [75] 레벨까지 사용하실수 있습니다."));
//			return;
//		}
//		boolean isAppear = true;
//		L1SupportInstance support = null;
//		Object[] supportList = pc.getSupportList().values().toArray();
//		for (Object supportObject : supportList) {
//			support = (L1SupportInstance) supportObject;
//			if (support.getItemObjId() == itemObjectId) { // 이미 꺼내고 있는 매직 실업 수당
//				isAppear = false;
//				break;
//			}
//		}
//
//		if (isAppear) {
//			if (supportList.length >= Config.MAX_DOLL_COUNT) {// 더 이상의 monster를
//				// 조종할 수 없습니다.
//				pc.sendPackets(new SC_ServerMessage(319));
//				return;
//			}
//			int npcId = 0;
//			int supportType = 0;
//			if (itemId == 210095) {
//				npcId = 200073;
//				supportType = L1SupportInstance.SUPPORTTYPE_DWARF;
//			}
//
//			L1Npc template = NpcTable.getInstance().getTemplate(npcId);
//			support = new L1SupportInstance(template, pc, supportType, itemObjectId);
//			support.broadcastPacket(new S_SkillSound(support.getId(), 5935));
//			SC_OwnCharStatus.send(pc);
//			pc.getInventory().consumeItem(40308, 1);
//			pc.setSupporting(true);
//		} else {
//			support.broadcastPacket(new S_SkillSound(support.getId(), 5936));
//			support.deleteSupport();
//			SC_OwnCharStatus.send(pc);
//			pc.setSupporting(false);
//		}
	}

	public static void useSupport1(L1PcInstance pc, int itemId, int itemObjectId) { // 쫄법사
		if (!pc.getMap().isTakePets()) {
			SC_ServerMessage.send(pc, 563); // \f1 여기에서는 사용할 수 없습니다.
			return;
		}

		if (pc.isInWarArea()) {
			SC_ServerMessage.send(pc, 563); // \f1 여기에서는 사용할 수 없습니다.
			return;
		}

		if (pc.getMapId() == 5153 || pc.getMapId() == 5140) {
			SC_ServerMessage.send(pc, 563); // \f1 여기에서는 사용할 수 없습니다.
			return;
		}

		if (!pc.getInventory().checkItem(40308, 5000)) {
			SC_ServerMessage.send(pc, 337, "$4"); // \f1%0이 부족합니다.
			return;
		}
		if (pc.getLevel() > 75) {
			SC_SystemMessage.send(pc, "도우미: [75] 레벨까지 사용하실수 있습니다.");
			return;
		}
		boolean isAppear = true;
		L1SupportInstance support = null;
		Object[] supportList = pc.getSupportList().values().toArray();
		for (Object supportObject : supportList) {
			support = (L1SupportInstance) supportObject;
			if (support.getItemObjId() == itemObjectId) { // 이미 꺼내고 있는 매직 실업 수당
				isAppear = false;
				break;
			}
		}

		if (isAppear) {
			if (supportList.length >= Config.MAX_DOLL_COUNT) {// 더 이상의 monster를
				// 조종할 수 없습니다.
				SC_ServerMessage.send(pc, 319);
				return;
			}
			int npcId = 0;
			int supportType = 0;
			if (itemId == 2100950) {
				npcId = 7310105;
				supportType = L1SupportInstance.SUPPORTTYPE_DWARF;
			}

            L1Npc template = CObjectDB.LoadNPC().getTemplate(npcId);
			support = new L1SupportInstance(template, pc, supportType, itemObjectId);
			SC_SkillSound.broadcast(support, support.getId(), 5935);
			SC_OwnCharStatus.send(pc);
			pc.getInventory().consumeItem(40308, 1);
			pc.setSupporting(true);
		} else {
			SC_SkillSound.broadcast(support, support.getId(), 5936);
			support.deleteSupport();
			SC_OwnCharStatus.send(pc);
			pc.setSupporting(false);
		}
	}
}
