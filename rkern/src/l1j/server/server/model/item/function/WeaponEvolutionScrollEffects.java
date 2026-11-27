package l1j.server.server.model.item.function;



import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;



import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;


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
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;

/** Effect implementation; public entrypoints remain in SpecialEnchantScrolls. */
final class WeaponEvolutionScrollEffects {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private WeaponEvolutionScrollEffects() {}

    static void 집행진화주문서(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		if (scroll == null || target == null || scroll == target
				|| pc.getInventory().GetByServerID(scroll.getId()) != scroll
				|| pc.getInventory().GetByServerID(target.getId()) != target) return;
		if (target.getBless() >= 128) {
			pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
			return;
		}
		if (target.isEquipped()) {
			SC_ServerMessage.send(pc, 4302);
			return;
		}
		if (target.getEnchantLevel() < Config.qqq) {
			pc.sendPackets("+" + Config.qqq + "이하 인첸트는 진화 불가 합니다.");
			return;
		}

		// The original random <= 100 test is always true for random(100).
		// Preserve the eight 100%-success mappings without the repeated checks.
		final int rewardId;
		switch (target.getItemId()) {
		case 61: rewardId = 14061; break;
		case 12: rewardId = 14012; break;
		case 134: rewardId = 14134; break;
		case 86: rewardId = 14086; break;
		case 202011: rewardId = 2202011; break;
		case 202012: rewardId = 2202012; break;
		case 202013: rewardId = 2202013; break;
		case 202014: rewardId = 2202014; break;
		default: rewardId = 0; break;
		}
		final String name = target.getLogName();
		if (rewardId != 0) {
			// One scroll and one target: one receipt-backed COMMIT, then notify.
			// The old success path removed the scroll twice and could split the
			// item debit and reward INSERT into separate DB operations.
			UseDurableTwoInput(pc, scroll, target, rewardId, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, scroll);
                try {
                    pc.sendPackets(name + "\\aL이 진화에 성공 하였습니다.");
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Weapon evolution success notification failed after commit", notificationFailure);
                }
                try {
                    SC_SkillSound.send(pc, pc.getId(), 8857);
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Weapon evolution success sound failed after commit", notificationFailure);
                }
            });
			return;
		}

		// Preserve the original unsupported-target failure: spend one scroll.
		// Unlike the old live removeItem(), it is durable before the notice.
		try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(scroll, 1)) return;
			tx.onFailure(() -> SC_SystemMessage.send(pc, "진화 주문서 소모를 저장하지 못했습니다."));
			CDB.RequestUseItem(pc, tx, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, scroll);
                try {
                    pc.sendPackets(name + "\\fH을 진화에 실패 하였습니다.");
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Weapon evolution failure notification failed after commit", notificationFailure);
                }
            });
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
					"Item evolution failure prepare failed: " + pc.getId(), failure);
			SC_SystemMessage.send(pc, "진화 주문서를 처리하지 못했습니다.");
		}
	}
}
