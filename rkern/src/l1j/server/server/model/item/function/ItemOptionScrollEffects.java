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
final class ItemOptionScrollEffects {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ItemOptionScrollEffects() {}

    static void 옵션부여(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
    if (!isOwnedScrollTarget(pc, scroll, target)) return;
    if (target.getBless() >= 128) {
        pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
        return;
    }
    if (target.getItem().getType2() != 2) {
        pc.sendPackets("방어구 및 악세에만 사용이 가능합니다.");
        return;
    }
    if (target.isEquipped()) {
        SC_ServerMessage.send(pc, 4302);
        return;
    }
    final boolean success = ThreadLocalRandom.current().nextInt(100) < Config.랜덤부여주문서;
    final int nextLevel = success ? 10 + ThreadLocalRandom.current().nextInt(17) : 0;
    final String name = target.getLogName();
    try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
            User.CheckAndSetPendingUseETCItem(pc,
                    l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
        if (!tx.debit(scroll, 1)) return;
        if (success) tx.change(target, image -> {
            image.setBless(0);
            image.set_bless_level(nextLevel);
        });
        tx.onFailure(() -> SC_SystemMessage.send(pc, "옵션부여 주문서 저장에 실패했습니다."));
        CDB.RequestUseItem(pc, tx, () -> {
            ItemActionCompletion.finishCommittedConsumable(pc, scroll);
            try {
                pc.sendPackets(name + (success
                                    ? "\\aL에 축복의 기운이 스며듭니다."
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
                "Option scroll prepare failed: " + pc.getId(), failure);
        SC_SystemMessage.send(pc, "옵션부여 주문서를 처리하지 못했습니다.");
    }
}

    static void 품질부여주문서(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
    if (!isOwnedScrollTarget(pc, scroll, target)) return;
    if (target.getBless() >= 128) {
        pc.sendPackets("봉인된 아이템에는 사용할 수 없습니다.");
        return;
    }
    if (target.getItem().getMaterial() == 8) {
        pc.sendPackets("인형에는 품질부여가 불가능 합니다.");
        return;
    }
    if (target.isEquipped()) {
        SC_ServerMessage.send(pc, 4302);
        return;
    }
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final boolean gate = rnd.nextInt(100) < Config.랜덤부여주문서;
    final int bonus = gate ? qualityBonus(rnd.nextInt(100)) : -1;
    final int nextQuality = bonus < 0 ? -1 : rnd.nextInt(20) + bonus;
    final boolean success = nextQuality >= 0 && nextQuality > target.get_bless_level2();
    final String name = target.getLogName();
    try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
            User.CheckAndSetPendingUseETCItem(pc,
                    l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
        if (!tx.debit(scroll, 1)) return;
        if (success) tx.change(target, image -> image.set_bless_level2(nextQuality));
        tx.onFailure(() -> SC_SystemMessage.send(pc, "품질부여 주문서 저장에 실패했습니다."));
        CDB.RequestUseItem(pc, tx, () -> {
            ItemActionCompletion.finishCommittedConsumable(pc, scroll);
            try {
                pc.sendPackets(name + (success
                                    ? "\\aL의 품질강화에 성공했습니다.품질 : " + nextQuality
                                    : "\\fH의 품질강화에 실패하였습니다."));
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
                "Quality scroll prepare failed: " + pc.getId(), failure);
        SC_SystemMessage.send(pc, "품질부여 주문서를 처리하지 못했습니다.");
    }
}
}
