package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import static l1j.server.server.model.character.skill.L1SkillId.CURSE_BLIND;
import static l1j.server.server.model.character.skill.L1SkillId.DANCING_BLADES;
import static l1j.server.server.model.character.skill.L1SkillId.DARKNESS;
import static l1j.server.server.model.character.skill.L1SkillId.DECAY_POTION;
import static l1j.server.server.model.character.skill.L1SkillId.EXP_POTION;
import static l1j.server.server.model.character.skill.L1SkillId.FOCUS_WAVE;
import static l1j.server.server.model.character.skill.L1SkillId.GREATER_HASTE;
import static l1j.server.server.model.character.skill.L1SkillId.HASTE;
import static l1j.server.server.model.character.skill.L1SkillId.HOLY_WALK;
import static l1j.server.server.model.character.skill.L1SkillId.HURRICANE;
import static l1j.server.server.model.character.skill.L1SkillId.LINDBIOR_SPIRIT_EFFECT;
import static l1j.server.server.model.character.skill.L1SkillId.MOVING_ACCELERATION;
import static l1j.server.server.model.character.skill.L1SkillId.SAND_STORM;
import static l1j.server.server.model.character.skill.L1SkillId.SLOW;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BLUE_POTION;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BLUE_POTION2;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BRAVE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL2;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL3;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL4;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL5;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL6;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_ELFBRAVE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FLOATING_EYE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FRUIT;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HASTE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_UNDERWATER_BREATH;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_WISDOM_POTION;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;


import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWisdomPotion;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
import l1j.server.server.model.network.core.sc.packet.SC_CurseBlind;



import static l1j.server.server.model.item.function.EnchantmentItems.*;
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

/** ExperiencePotionEffects: original item effect implementation, without changing use order. */
final class ExperiencePotionEffects {
    private ExperiencePotionEffects() {}

	public static void UseExpPotion1(L1PcInstance pc, int item_id) {
		if (pc.hasSkillEffect(71) == true) { // 디케이포션 상태
			SC_ServerMessage.send(pc, 698, "");
			return;
		}
		cancelAbsoluteBarrier(pc);

		int time = 1800;

		pc.setSkillEffect(L1SkillId.EXP_BUFF, time * 1000);
		long s = SC_SkillSound.pack(pc.getId(), 10049);
		SC_SkillSound.sendPacked(pc, s);
		SC_SkillSound.broadcastPacked(pc, s);
		long hasad = pc.getAccount().getBlessOfAin();
		if (hasad < 10000) {
			SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.EXP_BUFF + 1, 5087, true); // 세이프티존
		} else{
			SC_InventoryIcon.icoNew(pc, L1SkillId.EXP_BUFF, 5087, time, true); // 사냥터
		}
		SC_ServerMessage.send(pc, 5087);
	}

	public static void UseExpPotion(L1PcInstance pc, int item_id) {
		if (pc.hasSkillEffect(71) == true) { // 디케이포션 상태
			SC_ServerMessage.send(pc, 698, "");
			// 마력에 의해 아무것도 마실 수가 없습니다.
			return;
		}
		cancelAbsoluteBarrier(pc);

		if (item_id != 210094 && item_id != 30105 && item_id != 3000456)
			return;
		int time = 1800;

		pc.setSkillEffect(L1SkillId.EXP_POTION, time * 1000);
		long s = SC_SkillSound.pack(pc.getId(), 11308);
		SC_SkillSound.sendPacked(pc, s);
		SC_SkillSound.broadcastPacked(pc, s);
		if (pc.getMap().isSafetyZone(pc.getX(), pc.getY()))
			SC_InventoryIcon.iconNewUnLimit(pc, EXP_POTION + 1, 4973, true); // 세이프티존
		else
			SC_InventoryIcon.icoNew(pc, EXP_POTION, 4973, time, true); // 사냥터
		SC_ServerMessage.send(pc, 1313);
	}
}
