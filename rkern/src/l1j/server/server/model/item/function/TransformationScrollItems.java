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
final class TransformationScrollItems {
    private TransformationScrollItems() {}

    static ItemActionResult handlePolyScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        String text = use.text();
        if (usePolyScroll(pc, itemId, text)) {
            pc.getInventory().removeItem(item, 1);
        } else {
            SC_ServerMessage.send(pc, 181);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleEventPolyScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (!Config.POLY_EVENT) {
            SC_SystemMessage.send(pc, "변신이벤트중일때만 사용가능합니다.");
            return ItemActionResult.STOP;
        }
        UseDurableConsume(pc, item, () -> {
            try {
                SC_ShowPolyList.send(pc, pc.getId());
            } catch (RuntimeException outputFailure) {
                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                        "Committed event poly list failed: " + pc.getId(), outputFailure);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleBlessedPolyScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        String text = use.text();
        if (usePolyScroll(pc, itemId, text)) {
            pc.getInventory().removeItem(item, 1);
        } else {
            SC_ServerMessage.send(pc, 181);

        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleTrainingPolyScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (!(pc.getMapId() == 7783 || pc.getMapId() == 12152 || pc.getMapId() == 12149
                || pc.getMapId() == 3 || pc.getMapId() == 12154 || pc.getMapId() == 12358
                || pc.getMapId() == 12153 || pc.getMapId() == 12146 || pc.getMapId() == 12147
                || pc.getMapId() == 12148 || pc.getMapId() == 12258 || pc.getMapId() == 12150
                || pc.getMapId() == 12257 || pc.getMapId() == 12358)) {
            SC_SystemMessage.send(pc, "클라우디아 맵에서만 사용이 가능합니다.");
            return ItemActionResult.STOP;
        }
        if (pc.getLevel() >= 83) {
            pc.sendPackets("해당 아이템은 82 레벨까지만 사용 가능합니다.");
            return ItemActionResult.STOP;
        }
        UseDurableConsume(pc, item, () -> { usePolyPotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleLevelPolyScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        UseDurableConsume(pc, item, () -> { useLevelPolyScroll(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
        return ItemActionResult.STOP;
    }

    /** Fixed-form polymorph potions selected by the item dispatcher. */
    static ItemActionResult handleFixedPolyPotion(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.getLevel() >= 83) {
            pc.sendPackets("해당 아이템은 82 레벨까지만 사용 가능합니다.");
            return ItemActionResult.STOP;
        }
        final int itemId = use.itemId();
        final L1ItemInstance item = use.item();
        UseDurableConsume(pc, item, () -> { usePolyPotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
        return ItemActionResult.STOP;
    }
}
