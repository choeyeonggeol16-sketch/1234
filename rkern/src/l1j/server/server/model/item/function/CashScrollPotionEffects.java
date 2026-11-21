package l1j.server.server.model.item.function;


import l1j.server.server.model.character.skill.SkillHandlers;
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

	private static final int[] CASH_SCROLLS = {
		STATUS_CASHSCROLL, STATUS_CASHSCROLL2, STATUS_CASHSCROLL3,
		STATUS_CASHSCROLL4, STATUS_CASHSCROLL5, STATUS_CASHSCROLL6
	};

	public static void useCashScroll(L1PcInstance pc, int itemId) {
		final int id = switch (itemId) {
		case 410010 -> STATUS_CASHSCROLL;
		case 410011 -> STATUS_CASHSCROLL2;
		case 410012, 30063 -> STATUS_CASHSCROLL3;
		case 4100039 -> STATUS_CASHSCROLL4;
		case 4100041 -> STATUS_CASHSCROLL5;
		case 4100042 -> STATUS_CASHSCROLL6;
		default -> 0;
		};
		if (id == 0) return;
		for (int previous : CASH_SCROLLS) {
			if (!pc.hasSkillEffect(previous)) continue;
			pc.killSkillEffectTimer(previous);
			SkillHandlers.notifyCashScrollStats(pc, previous);
		}

		pc.applySkillModifierFromOwner(id, 0);
		SkillHandlers.notifyCashScrollStats(pc, id);
		SC_SkillSound.send(pc, pc.getId(), id);
		SC_SkillSound.broadcast(pc, pc.getId(), id);
		pc.setSkillEffect(id, 1_800_000);
	}
}
