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

import java.util.Random;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
/** AttributeEnchantScrollEffects: original item effect implementation, without changing use order. */
final class AttributeEnchantScrollEffects {
    private AttributeEnchantScrollEffects() {}
    private static final Random _random = CreatureItemActions._random;
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;

	public static void AttrEnchant(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance weapon) {
		if (!isOwnedScrollTarget(pc, scroll, weapon) || scroll.isEquipped()
				|| weapon.isEquipped() || weapon.getItem().getType2() != 1) return;
		final int scrollId = scroll.getItemId();
		final int base;
		switch (scrollId) {
		case 210067: base = 0; break;  // fire
		case 210066: base = 5; break;  // water
		case 210064: base = 10; break; // wind
		case 210065: base = 15; break; // earth
		default: return;
		}
		final int attr = weapon.getAttrEnchantLevel();
		if (attr < 0 || attr > 20 || (attr != 0 && (attr <= base || attr > base + 5))) {
			SC_ServerMessage.send(pc, 1294);
			return;
		}
		final int grade = attr == 0 ? 0 : attr - base;
		if (grade >= 5) {
			pc.sendPackets("더 이상 강화할 수 없습니다.");
			return;
		}
		if ((grade == 3 && weapon.getEnchantLevel() < 9
				|| grade == 4 && weapon.getEnchantLevel() < 10)
				&& !is_legend_weapon(weapon.getItemId())) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		// Preserve the exact old roll: nextInt(80)+1 and <30/<20/<10/<7/<4.
		final int chance = _random.nextInt(80) + 1;
		final int limit = grade == 0 ? 30 : grade == 1 ? 20 : grade == 2 ? 10
				: grade == 3 ? 7 : 4;
		final boolean success = chance < limit;
		final int next = base + grade + 1;
		final String weaponName = weapon.getLogName();
		try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(scroll, 1)) return;
			if (success) tx.change(weapon, image -> image.setAttrEnchantLevel(next));
			tx.onFailure(() -> SC_SystemMessage.send(pc, "속성 강화 주문서 저장에 실패했습니다."));
			CDB.RequestUseItem(pc, tx, () -> {
                if (success) {
                    try { SC_PacketBox.send(pc, SC_PacketBox.ITEM_ENCHANT_UPDATE, weapon); }
                    catch (RuntimeException noticeFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed attribute-enchant item notice failed", noticeFailure);
                    }
                }
                try { SC_ServerMessage.send(pc, success ? 1410 : 1411, weaponName); }
                catch (RuntimeException noticeFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Committed attribute-enchant result notice failed", noticeFailure);
                }
			});
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
					"Attribute enchant prepare failed: " + pc.getId() + " / " + scrollId, failure);
			SC_SystemMessage.send(pc, "속성 강화 주문서를 처리하지 못했습니다.");
		}
	}
}
