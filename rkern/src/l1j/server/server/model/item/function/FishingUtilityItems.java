package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Item-specific Utility effects; selected once by ItemActionDispatcher. */
/** Extracted FishingUtilityItems handlers; effect, consumption and transaction order unchanged. */
final class FishingUtilityItems {
    private FishingUtilityItems() {}

    static ItemActionResult handleStartFishing(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int fishingX = use.fishingX();
        int fishingY = use.fishingY();
        if (Config.ALT_FISHEVENT) {
            FishitemService.clickItem(pc, itemId, fishingX, fishingY, item, itemObjectId);
        } else {
            FishitemService.clickItem1(pc, itemId, fishingX, fishingY, item, itemObjectId);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleUpgradeFishingReel(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            if (!isOwnedScrollTarget(pc, item, targetItem)) return ItemActionResult.STOP;
            final int reelTargetId = targetItem.getItemId();
            if (reelTargetId == 41293) {
                UseDurableTwoInput(pc, item, targetItem, 600229,
                        () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
            } else if (reelTargetId == 600229) {
                final int charge = targetItem.getChargeCount();
                if (charge >= 10000) {
                    SC_ServerMessage.send(pc, 3457);
                    return ItemActionResult.STOP;
                }
                final int newCharge = Math.min(10000, charge + 100);
                try (l1j.server.server.model.L1InventoryTransaction tx =
                        User.CheckAndSetPendingUseETCItem(pc,
                                CacheD.accountOrderKey("item-use:", pc.getId()))) {
                    if (!tx.debit(item, 1)) return ItemActionResult.STOP;
                    tx.changeChargeCount(targetItem, newCharge);
                    tx.onFailure(() -> SC_SystemMessage.send(pc, "성장의 릴 충전 저장에 실패했습니다."));
                    CDB.RequestUseItem(pc, tx,
                            () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
                } catch (RuntimeException failure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Growth reel charge prepare failed: " + pc.getId(), failure);
                    SC_SystemMessage.send(pc, "성장의 릴 충전을 처리하지 못했습니다.");
                }
            }
            return ItemActionResult.STOP;
        }
    }
}
