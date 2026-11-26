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
final class ArmorBlessingScrollEffects {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ArmorBlessingScrollEffects() {}

    static void 방어구축복100(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
    if (!isOwnedScrollTarget(pc, scroll, target)) return;
    if (target.getBless() >= 128) {
        pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
        return;
    }
    if (target.getItem().getType2() != 2 || isAccessoryEnchantTarget(target)
            || target.getItem().getType() == 12) {
        pc.sendPackets("방어구에만 강화가 가능합니다 (반지,목걸이,귀걸이 제외)");
        return;
    }
    if (target.isEquipped()) {
        SC_ServerMessage.send(pc, 4302);
        return;
    }
    final int roll = ThreadLocalRandom.current().nextInt(100);
    final int nextLevel;
    if (roll < Config.방어구축복6단) nextLevel = 6; else if (roll < Config.방어구축복5단) nextLevel = 5; else nextLevel = 4;
    final String targetName = target.getLogName();
    try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
            User.CheckAndSetPendingUseETCItem(pc,
                    l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
        if (!tx.debit(scroll, 1)) return;
        if (nextLevel != 0) tx.change(target, image -> {
            image.setBless(0);
            image.set_bless_level(nextLevel);
        });
        tx.onFailure(() -> SC_SystemMessage.send(pc, "방어구 축복 주문서 저장에 실패했습니다."));
        CDB.RequestUseItem(pc, tx, () -> {
            ItemActionCompletion.finishCommittedConsumable(pc, scroll);
            try {
                pc.sendPackets(targetName + (nextLevel != 0
                                    ? "\\aL에 축복의 기운이 스며듭니다."
                                    : "\\fH에 축복의 기운이 스며들지 못했습니다."));
            } catch (RuntimeException notificationFailure) {
                ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
            }
            try {
                if (nextLevel != 0) SC_SkillSound.send(pc, pc.getId(), 8857);
            } catch (RuntimeException notificationFailure) {
                ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
            }
        });
    } catch (RuntimeException failure) {
        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                "Armor blessing prepare failed: " + pc.getId(), failure);
        SC_SystemMessage.send(pc, "방어구 축복 주문서를 처리하지 못했습니다.");
    }
}

    static void 방어구축복(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
    if (!isOwnedScrollTarget(pc, scroll, target)) return;
    if (target.getBless() >= 128) {
        pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
        return;
    }
    if (target.getItem().getType2() != 2 || isAccessoryEnchantTarget(target)
            || target.getItem().getType() == 12) {
        pc.sendPackets("방어구에만 강화가 가능합니다 (반지,목걸이,귀걸이 제외)");
        return;
    }
    if (target.isEquipped()) {
        SC_ServerMessage.send(pc, 4302);
        return;
    }
    final int roll = ThreadLocalRandom.current().nextInt(100);
    final int nextLevel;
    if (roll < Config.방어구축복1단) nextLevel = 1; else if (roll < Config.방어구축복2단) nextLevel = 2; else if (roll < Config.방어구축복3단) nextLevel = 3; else nextLevel = 0;
    final String targetName = target.getLogName();
    try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
            User.CheckAndSetPendingUseETCItem(pc,
                    l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
        if (!tx.debit(scroll, 1)) return;
        if (nextLevel != 0) tx.change(target, image -> {
            image.setBless(0);
            image.set_bless_level(nextLevel);
        });
        tx.onFailure(() -> SC_SystemMessage.send(pc, "방어구 축복 주문서 저장에 실패했습니다."));
        CDB.RequestUseItem(pc, tx, () -> {
            ItemActionCompletion.finishCommittedConsumable(pc, scroll);
            try {
                pc.sendPackets(targetName + (nextLevel != 0
                                    ? "\\aL에 축복의 기운이 스며듭니다."
                                    : "\\fH에 축복의 기운이 스며들지 못했습니다."));
            } catch (RuntimeException notificationFailure) {
                ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
            }
            try {
                if (nextLevel != 0) SC_SkillSound.send(pc, pc.getId(), 8857);
            } catch (RuntimeException notificationFailure) {
                ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Special enchant post-commit notification failed", notificationFailure);
            }
        });
    } catch (RuntimeException failure) {
        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                "Armor blessing prepare failed: " + pc.getId(), failure);
        SC_SystemMessage.send(pc, "방어구 축복 주문서를 처리하지 못했습니다.");
    }
}
}
