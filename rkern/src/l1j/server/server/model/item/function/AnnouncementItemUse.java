package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class AnnouncementItemUse {
    private AnnouncementItemUse() {}

    static ItemActionResult handleRequestAnnouncement(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getLevel() >= 82) {
            pc.setTemporaryItemObjectId(item.getId());
            pc.sendPackets("확성할 메시지를 입력해주세요.");
        } else
            pc.sendPackets("레벨 82이하의 용사님은 사용이 불가능 합니다.");
        return ItemActionResult.COMPLETE;
    }
}
