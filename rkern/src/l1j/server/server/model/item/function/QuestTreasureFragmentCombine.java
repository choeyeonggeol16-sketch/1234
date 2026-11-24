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
final class QuestTreasureFragmentCombine {
    private QuestTreasureFragmentCombine() {}

    static ItemActionResult handleCombineTreasureFragments(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        {
            final int requiredId;
            final int rewardId;
            switch (itemId) {
            case 210073: requiredId = 210074; rewardId = 210075; break;
            case 210077: requiredId = 210078; rewardId = 210079; break;
            case 500205: requiredId = 500204; rewardId = 500208; break;
            default: requiredId = 500206; rewardId = 500209; break;
            }
            if (targetItem == null || targetItem == item
                    || targetItem.getItemId() != requiredId
                    || pc.getInventory().getItem(item.getId()) != item
                    || pc.getInventory().getItem(targetItem.getId()) != targetItem) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }

            UseDurableTwoInput(pc, item, targetItem, rewardId,
                    () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
            return ItemActionResult.STOP;
        }
    }
}
