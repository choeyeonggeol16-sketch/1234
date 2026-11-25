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
final class GemConversionItems {
    private GemConversionItems() {}

    static ItemActionResult handleConvertAinGems(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            if (targetItem == null || targetItem == item
                    || pc.getInventory().GetByServerID(targetItem.getId()) != targetItem
                    || (targetItem.getItemId() != 1000004
                            && targetItem.getItemId() != 410064)) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            UseDurableTwoInput(pc, item, targetItem, 7241, 2,
                    () -> {
                        try {
                            SC_SystemMessage.send(pc,
                                    "드래곤의 다이아몬드/에메랄드를 드래곤의 토파즈 2개로 변환.");
                        } catch (RuntimeException packetFailure) {
                            CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                    "Committed GemConversionItems notice failed", packetFailure);
                        } finally {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                        }
                    });
            return ItemActionResult.STOP;
        }
    }

}
