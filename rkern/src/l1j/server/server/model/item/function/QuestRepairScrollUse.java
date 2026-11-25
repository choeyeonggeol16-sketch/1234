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
final class QuestRepairScrollUse {
    private QuestRepairScrollUse() {}

    static ItemActionResult handleCombineMapFragments(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            if (targetItem == null || targetItem.getItem().getType2() == 0
                    || targetItem.get_durability() <= 0) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            final L1ItemInstance repairTarget = targetItem;
            final boolean equippedArmor = repairTarget.getItem().getType2() == 2
                    && repairTarget.isEquipped();
            UseDurableRepair(pc, item, repairTarget, () -> {
                try {
                    if (equippedArmor) {
                        pc.addAc(-1);
                        try {
                            SC_OwnCharAttrDef.send(pc);
                        } catch (RuntimeException packetFailure) {
                            CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                    "Whetstone armor status notice failed after commit", packetFailure);
                        }
                    }
                    try {
                        SC_ServerMessage.send(pc, repairTarget.get_durability() == 0 ? 464 : 463,
                                repairTarget.getLogName());
                    } catch (RuntimeException packetFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Whetstone result notice failed after commit", packetFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            return ItemActionResult.STOP;
        }
    }
}
