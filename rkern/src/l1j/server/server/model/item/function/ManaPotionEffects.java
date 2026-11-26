package l1j.server.server.model.item.function;



import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.DECAY_POTION;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BLUE_POTION;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BLUE_POTION2;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_WISDOM_POTION;

import l1j.server.server.model.Instance.L1PcInstance;



import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWisdomPotion;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;



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

/** ManaPotionEffects: original item effect implementation, without changing use order. */
final class ManaPotionEffects {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ManaPotionEffects.class.getName());
    private ManaPotionEffects() {}

	public static void useBluePotion(L1PcInstance pc, int itemId) {
		if (pc.hasSkillEffect(DECAY_POTION)) {// 디케이 포션
			sendMessage(pc, 698);
			return;
		}
		int time = 0;
		switch (itemId) {
		case 30083:
		case 40015:
		case 40736:
			time = 600;
			break;
		case 140015:
			time = 700;
			break;
		case 41142:
			time = 300;
			break;
		case 210114:
			time = 1800;
			break;
		default:
			return; // An unknown item must not create a permanent (zero-time) buff.
		}
		cancelAbsoluteBarrier(pc); // 앱솔루트 해제
		// The two blue-potion variants are mutually exclusive.
		// Remove the previous timer before publishing the replacement icon.
		if (pc.hasSkillEffect(STATUS_BLUE_POTION)) pc.removeSkillEffect(STATUS_BLUE_POTION);
		if (pc.hasSkillEffect(STATUS_BLUE_POTION2)) pc.removeSkillEffect(STATUS_BLUE_POTION2);
		sendBlueIcon(pc, 34, time, true);
		sendSound(pc, pc.getId(), 190);
		broadcastSound(pc, pc.getId(), 190);
		if (itemId == 41142) {
			pc.setSkillEffect(STATUS_BLUE_POTION2, time * 1000);
		} else {
			pc.setSkillEffect(STATUS_BLUE_POTION, time * 1000);
		}
		sendMessage(pc, 1007);// MP 회복 속도가 빨라집니다.
	}

	public static void useWisdomPotion(L1PcInstance pc, int item_id) {
		if (pc.hasSkillEffect(71) == true) { // 디케이포션 상태
			sendMessage(pc, 698); // \f1마력에 의해 아무것도 마실 수가
			// 없습니다.
			return;
		}
		int time = 0; // 시간은 4의 배수로 하는 것
		switch (item_id) {
		case 40016:
		case 30089:
			time = 300;
			break;
		case 140016:
			time = 360;
			break;
		case 210113:
			time = 1000;
			break;
		default:
			return;
		}

		// Only a recognized potion may cancel an existing barrier.
		cancelAbsoluteBarrier(pc);
		// Idempotent refresh; do not mutate the item's base stats directly.
		pc.applySkillModifierFromOwner(STATUS_WISDOM_POTION, 0);
		sendWisdomIcon(pc, (int) (time));
		sendSound(pc, pc.getId(), 750);
		broadcastSound(pc, pc.getId(), 750);
		pc.setSkillEffect(STATUS_WISDOM_POTION, time * 1000);

	}

    private static void noticeFailure(String packet, RuntimeException failure) {
        LOG.log(java.util.logging.Level.WARNING, "Mana potion " + packet + " delivery failed", failure);
    }
    private static void sendMessage(L1PcInstance pc, int message) {
        try { SC_ServerMessage.send(pc, message); }
        catch (RuntimeException e) { noticeFailure("message", e); }
    }
    private static void sendBlueIcon(L1PcInstance pc, int icon, int time, boolean flag) {
        try { SC_SkillIconGFX.send(pc, icon, time, flag); }
        catch (RuntimeException e) { noticeFailure("blue icon", e); }
    }
    private static void sendWisdomIcon(L1PcInstance pc, int time) {
        try { SC_SkillIconWisdomPotion.send(pc, time); }
        catch (RuntimeException e) { noticeFailure("wisdom icon", e); }
    }
    private static void sendSound(L1PcInstance pc, int id, int sound) {
        try { SC_SkillSound.send(pc, id, sound); }
        catch (RuntimeException e) { noticeFailure("sound", e); }
    }
    private static void broadcastSound(L1PcInstance pc, int id, int sound) {
        try { SC_SkillSound.broadcast(pc, id, sound); }
        catch (RuntimeException e) { noticeFailure("sound broadcast", e); }
    }
}
