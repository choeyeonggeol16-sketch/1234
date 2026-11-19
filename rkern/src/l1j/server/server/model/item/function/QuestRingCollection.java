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
final class QuestRingCollection {
    private QuestRingCollection() {}

    static ItemActionResult handleMixedBagCollection(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        {
            if (!isOwnedScrollTarget(pc, item, targetItem)
                    || targetItem.getItemId() != itemId + 242) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.COMPLETE;
            }
            final int ringId = targetItem.getItemId();
            if (ringId < 41185 || ringId > 41200) return ItemActionResult.COMPLETE;
            final int ringReward = 20435 + (ringId - 41185);
            final int ringGroup = (itemId - 40943) & 3;
            final int successCode = ringGroup == 0 ? 443 : ringGroup == 1 ? 442
                    : ringGroup == 2 ? 441 : 444;
            final int failureCode = ringGroup == 0 ? 447 : ringGroup == 1 ? 446
                    : ringGroup == 2 ? 445 : 448;
            if ((_random.nextInt(99) + 1) < Config.CREATE_CHANCE_PROCESSING_DIAMOND) {
                final String ringName = targetItem.getName();
                UseDurableTwoInput(pc, item, targetItem, ringReward, () -> {
                    try {
                        SC_ServerMessage.send(pc, successCode, ringName);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed ring success notice failed: " + pc.getId(), outputFailure);
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, item);
                    }
                });
            } else {
                final String catalystName = item.getName();
                UseDurableConsume(pc, item, () -> {
                    try {
                        SC_ServerMessage.send(pc, failureCode, catalystName);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed ring failure notice failed: " + pc.getId(), outputFailure);
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, item);
                    }
                });
            }
            return ItemActionResult.STOP;
        }
    }
}
