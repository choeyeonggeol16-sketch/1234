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

/** ProtectivePotionEffects: original item effect implementation, without changing use order. */
final class ProtectivePotionEffects {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ProtectivePotionEffects.class.getName());
    private ProtectivePotionEffects() {}

	public static void useBlessOfEva(L1PcInstance pc, int item_id) {
		if (pc.hasSkillEffect(71) == true) { // 디케이포션 상태
			sendMessage(pc, 698); // \f1마력에 의해 아무것도 마실 수가
			// 없습니다.
			return;
		}

		// 아브소르트바리아의 해제
		cancelAbsoluteBarrier(pc);

		int time = 0;
		switch (item_id) {
		case 210115:// 농축 호흡의 물약
			time = 7200;
			break;
		case 40032:// 에바의축복
			time = 1800;
			break;
		case 40041:
			time = 300;
			break;
		case 41344:
			time = 2100;
			break;
		default:
			return;
		}
		if (pc.hasSkillEffect(STATUS_UNDERWATER_BREATH)) {
			int timeSec = pc.getSkillEffectTimeSec(STATUS_UNDERWATER_BREATH);
			time += timeSec;
			if (time > 3600) {
				time = 3600;
			}
		}
		sendBreathIcon(pc, pc.getId(), time);
		sendSound(pc, pc.getId(), 190);
		broadcastSound(pc, pc.getId(), 190);
		pc.setSkillEffect(STATUS_UNDERWATER_BREATH, time * 1000);
	}

	public static void useBlindPotion(L1PcInstance pc) {
		if (pc.hasSkillEffect(DECAY_POTION)) {
			sendMessage(pc, 698); // \f1마력에 의해 아무것도 마실 수가
			// 없습니다.
			return;
		}

		// 아브소르트바리아의 해제
		cancelAbsoluteBarrier(pc);
		boolean before_floatingEye = pc.isFloatingEye();
		int time = 480;
		if (pc.hasSkillEffect(CURSE_BLIND)) {
			pc.killSkillEffectTimer(CURSE_BLIND);
		} else if (pc.hasSkillEffect(DARKNESS)) {
			pc.killSkillEffectTimer(DARKNESS);
		} else if (pc.hasSkillEffect(LINDBIOR_SPIRIT_EFFECT)) {
			pc.killSkillEffectTimer(LINDBIOR_SPIRIT_EFFECT);
		}	
		if (pc.hasSkillEffect(STATUS_FLOATING_EYE)) {
			sendBlind(pc, 2);
		} else {
			sendBlind(pc, 1);
		}
		pc.setSkillEffect(CURSE_BLIND, time * 1000);
		if (!before_floatingEye && pc.hasSkillEffect(L1SkillId.STATUS_FLOATING_EYE)) {
			try {
                pc.showFloatingEyeToInvis();
            } catch (RuntimeException failure) {
                noticeFailure("floating-eye reveal", failure);
            }
		}
	}

    private static void noticeFailure(String packet, RuntimeException failure) {
        LOG.log(java.util.logging.Level.WARNING, "Protection potion " + packet + " delivery failed", failure);
    }
    private static void sendMessage(L1PcInstance pc, int message) {
        try { SC_ServerMessage.send(pc, message); }
        catch (RuntimeException e) { noticeFailure("message", e); }
    }
    private static void sendBreathIcon(L1PcInstance pc, int id, int time) {
        try { SC_SkillIconBlessOfEva.send(pc, id, time); }
        catch (RuntimeException e) { noticeFailure("breath icon", e); }
    }
    private static void sendSound(L1PcInstance pc, int id, int sound) {
        try { SC_SkillSound.send(pc, id, sound); }
        catch (RuntimeException e) { noticeFailure("sound", e); }
    }
    private static void broadcastSound(L1PcInstance pc, int id, int sound) {
        try { SC_SkillSound.broadcast(pc, id, sound); }
        catch (RuntimeException e) { noticeFailure("sound broadcast", e); }
    }
    private static void sendBlind(L1PcInstance pc, int mode) {
        try { SC_CurseBlind.send(pc, mode); }
        catch (RuntimeException e) { noticeFailure("blind", e); }
    }
}
