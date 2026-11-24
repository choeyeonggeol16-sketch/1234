package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
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
final class QuestSummoningFragmentCombine {
    private QuestSummoningFragmentCombine() {}

    static ItemActionResult handleSummonFragments(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            if (!isOwnedScrollTarget(pc, item, targetItem)) return ItemActionResult.STOP;
            final int coreId = targetItem.getItemId();
            if (coreId < 41030 || coreId > 41034) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            if ((_random.nextInt(99) + 1) < Config.CREATE_CHANCE_DANTES) {
                UseDurableTwoInput(pc, item, targetItem, coreId + 1,
                        () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
            } else {
                final String coreName = targetItem.getName();
                UseDurableConsumeTwoInput(pc, item, targetItem,
                        () -> {
                            try {
                                SC_ServerMessage.send(pc, 158, coreName);
                            } catch (RuntimeException packetFailure) {
                                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                        "Committed QuestSummoningFragmentCombine notice failed", packetFailure);
                            } finally {
                                ItemActionCompletion.finishCommittedConsumable(pc, item);
                            }
                        });
            }
            return ItemActionResult.STOP;
        }
    }
}
