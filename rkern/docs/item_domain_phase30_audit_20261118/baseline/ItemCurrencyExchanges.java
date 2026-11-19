package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardAdd;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;

/** Item-specific action effects dispatched by item ID. */
final class ItemCurrencyExchanges {
    private ItemCurrencyExchanges() {}

    static ItemActionResult handleConvertAdena(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        AdenaCurrencyConversionReceipt.convertAdena(pc, item);
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleExchangeAdenaVoucher(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        SpecialConsumableRouter.use(pc, item);
        return ItemActionResult.STOP;
    }

}
