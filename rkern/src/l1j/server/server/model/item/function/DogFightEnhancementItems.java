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

import l1j.server.Config;
import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

public final class DogFightEnhancementItems {
    private DogFightEnhancementItems() {}
	public static void 투견조작(L1PcInstance pc, int targetId, L1ItemInstance item) {
		SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
		SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
		L1Object target = CWorld.getInstance().findObject(targetId);
		
		if (target != null && target instanceof DogFightInstance) {
			
			((DogFightInstance)target).set_min_damage(((DogFightInstance)target).get_min_damage() + Config.투견최소랜덤데미지);
			((DogFightInstance)target).set_max_damage(((DogFightInstance)target).get_max_damage() + Config.투견최대랜덤데미지);
			((DogFightInstance)target).set_attackdelay_reduce(((DogFightInstance)target).get_attackdelay_reduce() + Config.투견공속);
			SC_ChatPacket.sendDirect(pc, pc, "해당 투견의 [공격력: 최소범위 +"+Config.투견최소랜덤데미지+" / 최대범위 +"+Config.투견최대랜덤데미지+"] [공속: "+Config.투견공속+"] 증가하였습니다");
			
			pc.getInventory().updateItem(item, CUserInventory.COL_CHARGE_COUNT);
		} else {
			SC_ChatPacket.sendDirect(pc, pc, "투견에만 사용가능합니다.");
		}
	}
}
