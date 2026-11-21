package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.equip.EquipService;



import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.templates.L1Item;




import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.WandInspectionItems.*;
import static l1j.server.server.model.item.function.CombatSkillItemEffects.*;
import static l1j.server.server.model.item.function.DogFightEnhancementItems.*;
import static l1j.server.server.model.item.function.NpcWandAndSpawnItems.*;
import static l1j.server.server.model.item.function.WarriorSkillBookItems.*;
import static l1j.server.server.model.item.function.PcRoomPassTransactions.*;
import static l1j.server.server.model.item.function.CashBuffTransactions.*;
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

public final class EquipmentItemActions {
    private EquipmentItemActions() {}
	public static boolean is_legend_weapon(int itemId) {
		switch (itemId) {
		case 12:
		case 61:
		case 66:
		case 86:
		case 134:
		case 202011:
		case 202012:
		case 202013:
		case 202014:
		case 123777:
		case 123888:
		case 123999:
		case 123666:
			return true;
		}

		return false;
	}
	
	public static boolean canUseByClass(L1PcInstance pc, L1Item item) {
	    return l1j.server.server.model.character.equip.EquipRules.canUseByClass(pc, item);
	}

	// 2) 레벨 제한 메시지 처리(공통). true면 "통과", false면 "막힘(메시지 보냄)".
	public static boolean checkLevelRestriction(L1PcInstance pc, int min, int max) {
	    if (pc == null) return false;

	    if (min != 0 && min > pc.getLevel()) {
	        SC_ServerMessage.send(pc, 318, String.valueOf(min));
	        return false;
	    }
	    if (max != 0 && max < pc.getLevel()) {
	        if (max < 50) SC_PacketBox.send(pc, SC_PacketBox.MSG_LEVEL_OVER, max);
	        else SC_SystemMessage.send(pc, "이 아이템은" + max + "레벨 이하만 사용할 수 있습니다. ");
	        return false;
	    }
	    return true;
	}

	// 3) Type2==1(무기) 처리
	public static void handleWeaponUse(L1PcInstance pc, L1ItemInstance inst) {
	    final L1Item item = inst.getItem();

	    // 원본은 "무기: 아이템에서 min/max"
	    final int min = item.getMinLevel();
	    final int max = item.getMaxLevel();

	    if (!checkLevelRestriction(pc, min, max)) return;

	    if (!canUseByClass(pc, item)) {
	        SC_ServerMessage.send(pc, 264);
	        return;
	    }
	    EquipService.useWeapon(pc, inst);
	}

	// 4) Type2==2(방어구) 처리
	public static void handleArmorUse(L1PcInstance pc, L1ItemInstance inst) {
	    final L1Item item = inst.getItem();

	    if (!canUseByClass(pc, item)) {
	        SC_ServerMessage.send(pc, 264);
	        return;
	    }

	    final L1Armor armor = (L1Armor) item;
	    final int min = armor.getMinLevel();
	    final int max = armor.getMaxLevel();

	    if (!checkLevelRestriction(pc, min, max)) return;

	    EquipService.useArmor(pc, inst);
	}

}
