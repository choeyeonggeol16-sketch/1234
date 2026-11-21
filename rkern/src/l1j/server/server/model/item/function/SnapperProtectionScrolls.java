package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.CashBuffTransactions.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.CombatSkillItemEffects.*;
import static l1j.server.server.model.item.function.CreatureItemActions.*;
import static l1j.server.server.model.item.function.DogFightEnhancementItems.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.EnchantmentItems.SuccessEnchant;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.NpcWandAndSpawnItems.*;
import static l1j.server.server.model.item.function.PcRoomPassTransactions.*;
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
import static l1j.server.server.model.item.function.WandInspectionItems.*;
import static l1j.server.server.model.item.function.WarriorSkillBookItems.*;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.shared.GameRandom;
/** SnapperProtectionScrolls: original item effect implementation, without changing use order. */
final class SnapperProtectionScrolls {
    private SnapperProtectionScrolls() {}

	public static void enchant_snapper(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, scroll, target)) return;
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets("스냅퍼보호주문서는 +" + (3) + " 사용이 가능합니다.");
			return;
		}*/
		if (target_enchant >= Config.SnapperLevel) {
			pc.sendPackets("스냅퍼류 악세사리는 +" + (Config.SnapperLevel) + " 이상 보호주문서로 인챈트할 수 없습니다.");
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0
				: target_enchant >= Config.Sanpper.length ? Config.Sanpper.length - 1 : target_enchant;
		ItemReceiptOperations.UseProtectionEnchant(pc, scroll, target,
				GameRandom.isWinning(100, Config.Sanpper[index]), true);
	}

	public static void enchant_snapper1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, scroll, target)) return;
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets("스냅퍼보호주문서는 +" + (3) + " 사용이 가능합니다.");
			return;
		}*/
		if (target_enchant >= Config.SnapperLevel) {
			pc.sendPackets("스냅퍼류 악세사리는 +" + (Config.SnapperLevel) + " 이상 인챈트할 수 없습니다.");
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}
		ItemReceiptOperations.UseProtectionEnchant(pc, scroll, target, true, false);
	}
}
