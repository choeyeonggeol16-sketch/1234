package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsumeTwoInput;
import static l1j.server.server.model.item.function.DurabilityRepairItems.UseDurableRepair;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

/** Dedicated item-use handler extracted from a mixed domain. */
final class QuestAncientBookCombine {
    private QuestAncientBookCombine() {}

    static ItemActionResult handleCombineAncientBooks(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    User.CheckAndSetPendingUseETCItem(pc,
                            l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
                if (!tx.debit(40420, 1)) {
                    SC_SystemMessage.send(pc, "고대인의 주술서 1권 이 부족합니다.");
                    return ItemActionResult.STOP;
                }
                if (!tx.debit(40421, 1)) {
                    SC_SystemMessage.send(pc, "고대인의 주술서 2권 이 부족합니다.");
                    return ItemActionResult.STOP;
                }
                if (!tx.debit(40422, 1)) {
                    SC_SystemMessage.send(pc, "고대인의 주술서 3권 이 부족합니다.");
                    return ItemActionResult.STOP;
                }
                if (!tx.debit(40423, 1)) {
                    SC_SystemMessage.send(pc, "고대인의 주술서 4권 이 부족합니다.");
                    return ItemActionResult.STOP;
                }
                if (tx.grant(702, 1) == null) {
                    SC_ServerMessage.send(pc, 82);
                    return ItemActionResult.STOP;
                }
                tx.onFailure(() -> SC_SystemMessage.send(pc, "연금술사의 돌 제작을 저장하지 못했습니다."));
                CDB.RequestUseItem(pc, tx, () -> {
                    try {
                        SC_ChatPacket.sendDirect(pc, pc, "연금술사의 돌 이 제작되었습니다.");
                    } catch (RuntimeException packetFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed QuestAncientBookCombine notice failed", packetFailure);
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, item);
                    }
                });
            } catch (RuntimeException failure) {
                ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                        "Alchemist stone prepare failed: " + pc.getId(), failure);
                SC_SystemMessage.send(pc, "연금술사의 돌을 제작하지 못했습니다.");
            }
            return ItemActionResult.STOP;
        }
    }
}
