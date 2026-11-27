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
import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.CacheD;
import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsumeTwoInput;
import static l1j.server.server.model.item.function.DurabilityRepairItems.UseDurableRepair;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

/** Dedicated item-use handler extracted from a mixed domain. */
final class QuestEarringCollection {
    private QuestEarringCollection() {}

    static ItemActionResult handleBagCollection(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        {
            if (!isOwnedScrollTarget(pc, item, targetItem)
                    || targetItem.getItemId() != itemId + 230) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            final int earingId = targetItem.getItemId();
            int earingReward = -1;
            switch (earingId) {
                case 41161: earingReward = 21014; break;
                case 41162: earingReward = 21006; break;
                case 41163: earingReward = 21007; break;
                case 41164: earingReward = 21015; break;
                case 41165: earingReward = 21009; break;
                case 41166: earingReward = 21008; break;
                case 41167: earingReward = 21016; break;
                case 41168: earingReward = 21012; break;
                case 41169: earingReward = 21010; break;
                case 41170: earingReward = 21017; break;
                case 41171: earingReward = 21013; break;
                case 41172: earingReward = 21011; break;
                default: break;
            }
            if (earingReward <= 0) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            if ((GameRandom.nextInt(99) + 1) < Config.CREATE_CHANCE_PROCESSING) {
                UseDurableTwoInput(pc, item, targetItem, earingReward,
                        () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
            } else {
                final String failedName = targetItem.getName();
                UseDurableConsumeTwoInput(pc, item, targetItem,
                        () -> {
                            try {
                                SC_ServerMessage.send(pc, 158, failedName);
                            } catch (RuntimeException packetFailure) {
                                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                        "Committed QuestEarringCollection notice failed", packetFailure);
                            } finally {
                                ItemActionCompletion.finishCommittedConsumable(pc, item);
                            }
                        });
            }
            return ItemActionResult.STOP;
        }
    }
}
