package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.LoggerInstance;


import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;
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

import static l1j.server.server.model.item.function.EnchantmentItems.SuccessEnchant;
/** ShoulderProtectionScrolls: original item effect implementation, without changing use order. */
final class ShoulderProtectionScrolls {
    private ShoulderProtectionScrolls() {}

	public static void enchant_should(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, scroll, target)) return;
		int target_enchant = target.getEnchantLevel();
		if (target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets("빛나는 아덴 용사 보호주문서는 +" + (3) + " 사용이 가능합니다.");
			return;
		}*/
		if (target_enchant >= Config.Should_Level) {
			pc.sendPackets("빛나는 아덴 용사 방어구 +" + (Config.Should_Level) + " 이상 보호주문서로 인챈트할 수 없습니다.");
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0 : target_enchant >= Config.Should.length ? Config.Should.length - 1 : target_enchant;
		ItemReceiptOperations.UseProtectionEnchant(pc, scroll, target,
				MJRnd.isWinning(100, Config.Should[index]), true);
	}
}
