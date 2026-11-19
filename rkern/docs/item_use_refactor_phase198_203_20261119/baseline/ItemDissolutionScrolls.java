package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsumeTwoInput;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;
import static l1j.server.server.model.item.function.ResolventItemFacade.useResolvent;

/** Item-specific action effects dispatched by item ID. */
final class ItemDissolutionScrolls {
    private ItemDissolutionScrolls() {}

    static ItemActionResult handleResolvent(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        OrdinaryResolventItems.useResolvent(pc, targetItem, item);
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleDragonResolvent(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        DragonResolventItems.useDragonResolvent(pc, targetItem, item);
        return ItemActionResult.STOP;
    }}
