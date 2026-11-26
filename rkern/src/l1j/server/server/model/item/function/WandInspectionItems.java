package l1j.server.server.model.item.function;


import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;
import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.CreatureItemActions.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.PetVoucherTransactions.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.ReceiptCraftingItems.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.RevivalScrollCraftingItems.*;
import static l1j.server.server.model.item.function.SealedEquipmentRewards.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.TowerCharmCraftingItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.TransformationItems.*;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Serchdrop2;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class WandInspectionItems {
    private WandInspectionItems() {}
	public static void useFieldObjectRemovalWand1(L1PcInstance pc, int targetId, L1ItemInstance item) {
		SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
		SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
		L1Object target = L1World.getInstance().findObject(targetId);
		if (target != null && target instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) target;
			SC_SystemMessage.send(pc, "\\f;NPC이름: \\aA"+npc.getName());
			SC_SystemMessage.send(pc, "\\f;NPC번호: \\aA"+npc.getNpcId()+"  \\f;이미지번호(GfxId) : \\aA"+npc.getCurrentSpriteId());
			SC_SystemMessage.send(pc, "\\f;해당위치: \\aAX - " + npc.getX() + " \\f;/ \\aAY - " + npc.getY() + " \\f;/ \\aAMap - " + npc.getMapId());
			
			
//			(new SC_ChatPacket(pc,"번호 : (" + npc.getNpcId() + "-" + targetId + ") // " + "이름 : (" + npc.getName() + ")")).send(pc);
//			(new SC_ChatPacket(pc, "위치 : " + "(x:" + npc.getX() + "), (y:" + npc.getY() + "), (" + "Map:"+ npc.getMapId() + ") // GfxId : (" + npc.getCurrentSpriteId() + ")")).send(pc);
//			
//			
//			(new SC_ChatPacket(pc,"번호 : (" + npc.getNpcId() + "-" + targetId + ") // " + "이름 : (" + npc.getName() + ")")).send(pc);
//			(new SC_ChatPacket(pc, "위치 : " + "(x:" + npc.getX() + "), (y:" + npc.getY() + "), (" + "Map:"+ npc.getMapId() + ") // GfxId : (" + npc.getCurrentSpriteId() + ")")).send(pc);
			pc.getInventory().updateItem(item, CUserInventory.COL_CHARGE_COUNT);
		}
	}

	public static void serchdroplist2(L1PcInstance pc, int targetId, L1ItemInstance item) {
		long curtime = System.currentTimeMillis() / 1000;

		SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
		SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
		L1Object target = L1World.getInstance().findObject(targetId);
		if (target != null && target instanceof L1MonsterInstance) {
			L1MonsterInstance npc = (L1MonsterInstance) target;

			int transformId = npc.getNpcTemplate().getTransformId();
			if (transformId == -1) {
				SC_Serchdrop2.send(pc, npc.getNpcId());
				pc.setQuizTime(curtime);
			} else {
				SC_Serchdrop2.send(pc, transformId);
				pc.setQuizTime(curtime);
			}
		}
	}
}
