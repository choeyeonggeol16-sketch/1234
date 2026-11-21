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
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
/** OrimProtectionScrolls: original item effect implementation, without changing use order. */
final class OrimProtectionScrolls {
    private OrimProtectionScrolls() {}

	public static void enchant_bless_orim(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		if (target_enchant >= Config.악세사리) {
			pc.sendPackets(String.format("악세사리는 +%d 이상은 인챈할 수 없습니다.", Config.악세사리));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0
				: target_enchant >= Config.bless_orim.length ? Config.bless_orim.length - 1 : target_enchant;
		if (GameRandom.isWinning(100, Config.bless_orim[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}
}
