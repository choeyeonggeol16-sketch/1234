package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.tam.TamFruitUseService;

/** Item-specific Utility effects; selected once by ItemActionDispatcher. */
/** Extracted CharacterServiceItems handlers; effect, consumption and transaction order unchanged. */
final class CharacterServiceItems {
    private CharacterServiceItems() {}

    static ItemActionResult handleStoreClassChange(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int storedItemObjectId = use.storedItemObjectId();
        if (pc.getId() == 0) return ItemActionResult.STOP;

         int day = (itemId == 600226) ? 3 : (itemId == 3000235) ? 7 : 30;

        TamFruitUseService.useFruit(pc, storedItemObjectId, item, day,
                () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleResetStats(ItemActionContext use) {
        StatReset.useCandle(use.player(), use.item());
        return ItemActionResult.STOP;
    }
}
