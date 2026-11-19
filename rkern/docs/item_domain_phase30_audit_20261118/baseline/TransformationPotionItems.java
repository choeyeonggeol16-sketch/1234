package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShowPolyList;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.TransformationItems.useLevelPolyScroll;
import static l1j.server.server.model.item.function.TransformationItems.usePolyPotion;
import static l1j.server.server.model.item.function.TransformationItems.usePolyScroll;

/** Item-specific action effects dispatched by item ID. */
final class TransformationPotionItems {
    private TransformationPotionItems() {}

    static ItemActionResult handlePolyPotion(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        usePolyPotion(pc, itemId);
        pc.getInventory().removeItem(item, 1);
        return ItemActionResult.COMPLETE;
    }

}
