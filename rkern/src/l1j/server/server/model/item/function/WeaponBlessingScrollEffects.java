package l1j.server.server.model.item.function;



import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.util.concurrent.ThreadLocalRandom;
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
final class WeaponBlessingScrollEffects {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private WeaponBlessingScrollEffects() {}

    static void 무기축복(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        if (!isOwnedScrollTarget(pc, scroll, target)) return;
        if (target.getBless() >= 128) {
            pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
            return;
        }
        if (target.getItem().getType2() != 1) {
            pc.sendPackets("무기에만 사용할 수 있습니다.");
            return;
        }
        if (target.isEquipped()) {
            SC_ServerMessage.send(pc, 4302);
            return;
        }
        final int level = target.get_bless_level();
        if (level == 5) {
            pc.sendPackets("더 이상 강화는 불가능합니다.");
            return;
        }
        if (level < 0 || level > 5) return;
        final int roll = ThreadLocalRandom.current().nextInt(100);
        final boolean success = (level == 0 ? roll < Config.축복1단
                : level == 1 ? roll < Config.축복2단
                : level == 2 ? roll < Config.축복3단
                : level == 3 ? roll < Config.축복4단
                : roll < Config.축복5단);
        final String itemName = target.getLogName();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            if (success) tx.change(target, image -> {
                image.setBless(0);
                image.set_bless_level(level + 1);
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "무기 축복 주문서 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, scroll);
                try {
                    pc.sendPackets(itemName + (success
                                            ? "\\aL에 축복의 기운 " + (level + 1) + "단계가 스며듭니다."
                                            : "\\fH에 축복의 기운이 스며들지 못했습니다."));
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
                }
                try {
                    if (success) SC_SkillSound.send(pc, pc.getId(), 8857);
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
                }
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Weapon blessing prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "무기 축복 주문서를 처리하지 못했습니다.");
        }
    }

    static void 무기축복100(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        if (!isOwnedScrollTarget(pc, scroll, target)) return;
        if (target.getBless() >= 128) {
            pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
            return;
        }
        if (target.getItem().getType2() != 1) {
            pc.sendPackets("무기에만 사용할 수 있습니다.");
            return;
        }
        if (target.isEquipped()) {
            SC_ServerMessage.send(pc, 4302);
            return;
        }
        final int level = target.get_bless_level();
        if (level == 5) {
            pc.sendPackets("더 이상 강화는 불가능합니다.");
            return;
        }
        if (level < 0 || level > 5) return;
        final String itemName = target.getLogName();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            tx.change(target, image -> {
                image.setBless(0);
                image.set_bless_level(level + 1);
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "무기 축복 주문서 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, scroll);
                try {
                    pc.sendPackets(itemName + "\\aL에 축복의 기운 " + (level + 1) + "단계가 스며듭니다.");
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
                }
                try {
                    SC_SkillSound.send(pc, pc.getId(), 8857);
                } catch (RuntimeException notificationFailure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
                }
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Weapon blessing prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "무기 축복 주문서를 처리하지 못했습니다.");
        }
    }
}
