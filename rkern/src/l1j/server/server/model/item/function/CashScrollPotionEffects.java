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

/** CashScrollPotionEffects: original item effect implementation, without changing use order. */
final class CashScrollPotionEffects {
    private CashScrollPotionEffects() {}

	public static void useCashScroll(L1PcInstance pc, int item_id) {
		int time = 1800;
		int scroll = 0;

		if (pc.hasSkillEffect(STATUS_CASHSCROLL)) {
			pc.killSkillEffectTimer(STATUS_CASHSCROLL);
			pc.addMaxHp(-50);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			if (pc.isInParty()) {
				// TODO 파티 프로토
				pc.getParty().refreshPartyMemberStatus(pc);
			}
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		if (pc.hasSkillEffect(STATUS_CASHSCROLL2)) {
			pc.killSkillEffectTimer(STATUS_CASHSCROLL2);
			pc.addMaxMp(-40);
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		if (pc.hasSkillEffect(STATUS_CASHSCROLL3)) {
			pc.killSkillEffectTimer(STATUS_CASHSCROLL3);
			pc.addDmgup(-3);
			pc.addHitup(-3);
			pc.getAbility().addSp(-3);
			SC_SPMR.send(pc);
		}
		if (pc.hasSkillEffect(STATUS_CASHSCROLL4)) {
			pc.killSkillEffectTimer(STATUS_CASHSCROLL4);
			pc.getAbility().addSp(-3);
			pc.addBaseMagicHitUp(-5);
			pc.getResistance().addcalcPcDefense(-3);
			SC_SPMR.send(pc);
		}
		if (pc.hasSkillEffect(STATUS_CASHSCROLL5)) {
			pc.killSkillEffectTimer(STATUS_CASHSCROLL5);
			pc.addBowDmgup(-3);
			pc.addBowHitup(-5);
			pc.getResistance().addcalcPcDefense(-3);
		}
		if (pc.hasSkillEffect(STATUS_CASHSCROLL6)) {
			pc.killSkillEffectTimer(STATUS_CASHSCROLL6);
			pc.addDmgRate(-3);
			pc.addHitup(-5);
			pc.getResistance().addcalcPcDefense(-3);
		}

		if (item_id == 410010) {
			scroll = 7893;
			pc.addMaxHp(50);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			if (pc.isInParty()) {
				// TODO 파티 프로토
				pc.getParty().refreshPartyMemberStatus(pc);
			}
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		} else if (item_id == 410011) {
			scroll = 7894;
			pc.addMaxMp(40);
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		} else if (item_id == 410012 || item_id == 30063) {
			scroll = 7895;
			pc.addDmgup(3);
			pc.addHitup(3);
			pc.getAbility().addSp(3);
			SC_SPMR.send(pc);
		} else if (item_id == 4100039) {
			scroll = 16553;
			pc.getAbility().addSp(3);
			pc.addBaseMagicHitUp(5);
			pc.getResistance().addcalcPcDefense(3);
			SC_SPMR.send(pc);
		} else if (item_id == 4100041) {
			scroll = 16552;
			pc.addBowDmgup(3);
			pc.addBowHitup(5);
			pc.getResistance().addcalcPcDefense(3);
		} else if (item_id == 4100042) {
			scroll = 16551;
			pc.addDmgRate(3);
			pc.addHitup(5);
			pc.getResistance().addcalcPcDefense(3);
		}
		SC_SkillSound.send(pc, pc.getId(), scroll);
		SC_SkillSound.broadcast(pc, pc.getId(), scroll);
		pc.setSkillEffect(scroll, time * 1000);
	}
}
