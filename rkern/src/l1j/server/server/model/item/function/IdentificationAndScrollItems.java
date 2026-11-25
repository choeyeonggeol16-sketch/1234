package l1j.server.server.model.item.function;

import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_IdentifyDesc;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Item-specific Utility effects; selected once by ItemActionDispatcher. */
/** Identification scrolls use the durable inventory receipt; blank scrolls delegate. */
final class IdentificationAndScrollItems {
    private static final java.util.logging.Logger IDENTIFY_LOG =
            java.util.logging.Logger.getLogger(IdentificationAndScrollItems.class.getName());
    private IdentificationAndScrollItems() {}

    static ItemActionResult handleIdentifyItem(ItemActionContext use) {
        final L1PcInstance pc = use.player();
        final L1ItemInstance scroll = use.item();
        final L1ItemInstance target = use.targetItem();
        if (pc == null || scroll == null || target == null || scroll == target
                || pc.getInventory().GetByServerID(scroll.getId()) != scroll
                || pc.getInventory().GetByServerID(target.getId()) != target)
            return ItemActionResult.COMPLETE;

        // Read the immutable template metadata before queuing the receipt.
        final int addMpr = target.getItem().get_addmpr();
        final int addHpr = target.getItem().get_addhpr();
        final int safeEnchant = target.getItem().get_safeenchant();
        final boolean equipment = target.getItem().getType2() == 1 || target.getItem().getType2() == 2;
        final String description;
        if (!equipment) {
            description = "";
        } else if (safeEnchant == -1) {
            description = "피틱: " + addHpr + " /엠틱: " + addMpr + " /기본 인첸트: 불가능";
        } else {
            description = "피틱: " + addHpr + " /엠틱: " + addMpr + " /기본 인첸트: " + safeEnchant;
        }

        try {
            // Flush earlier pending saves before the receipt's DB preimages.
            // This only enqueues CacheD work; it performs no JDBC on the CIO owner.
            if (scroll.getRecordingColumns() != 0)
                pc.getInventory().saveItem(scroll, CUserInventory.COL_SAVE_ALL);
            if (target.getRecordingColumns() != 0)
                pc.getInventory().saveItem(target, CUserInventory.COL_SAVE_ALL);
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    User.CheckAndSetPendingUseETCItem(pc, CacheD.accountOrderKey("item-use:", pc.getId()))) {
                if (!tx.debit(scroll, 1)) return ItemActionResult.COMPLETE;
                if (!target.isIdentified()) tx.identify(target);
                tx.onFailure(() -> {
                    try {
                        SC_SystemMessage.send(pc, "감정 주문서 저장에 실패했습니다.");
                    } catch (RuntimeException noticeFailure) {
                        IDENTIFY_LOG.log(java.util.logging.Level.WARNING,
                                "Identification failure notice failed", noticeFailure);
                    }
                });
                CDB.RequestUseItem(pc, tx, () -> {
                    // Economy inventory images are already installed. Optional
                    // client output must not turn a committed debit into a failure.
                    try {
                        SC_IdentifyDesc.send(pc, target);
                    } catch (RuntimeException noticeFailure) {
                        IDENTIFY_LOG.log(java.util.logging.Level.WARNING,
                                "Committed identification detail failed", noticeFailure);
                    }
                    try {
                        SC_SystemMessage.send(pc, description);
                    } catch (RuntimeException noticeFailure) {
                        IDENTIFY_LOG.log(java.util.logging.Level.WARNING,
                                "Committed identification description failed", noticeFailure);
                    }
                });
            }
        } catch (RuntimeException failure) {
            IDENTIFY_LOG.log(java.util.logging.Level.WARNING,
                    "Identification prepare failed: " + pc.getId(), failure);
            try {
                SC_SystemMessage.send(pc, "감정 주문서를 사용하지 못했습니다.");
            } catch (RuntimeException noticeFailure) {
                IDENTIFY_LOG.log(java.util.logging.Level.WARNING,
                        "Identification prepare notice failed", noticeFailure);
            }
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleWriteBlankScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int blankScrollSkillId = use.blankScrollSkillId();
        BlankMagicScrolls.write(pc, item, blankScrollSkillId);
        return ItemActionResult.STOP;
    }
}
