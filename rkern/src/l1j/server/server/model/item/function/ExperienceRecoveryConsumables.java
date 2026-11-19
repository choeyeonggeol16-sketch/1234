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
import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;

/** Item-specific action effects dispatched by item ID. */
final class ExperienceRecoveryConsumables {
    private ExperienceRecoveryConsumables() {}

    static ItemActionResult handleOpenExperienceBox(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            long now = System.currentTimeMillis() / 1000;
            if (pc.getQuizTime() + 1 > now) {
                long sec = (pc.getQuizTime() + 1) - now;
                SC_ChatPacket.sendDirect(pc, pc, sec + "초 후에 가능합니다.");
                return ItemActionResult.STOP;
            }
            UseDurableConsume(pc, item, () -> {
                try {
                    pc.setCurrentMp(pc.getCurrentMp() + 300);
                    try {
                        SC_SystemMessage.send(pc, "당신의 MP가 회복 되었습니다.");
                    } catch (RuntimeException outputFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Experience box notice failed after commit: " + pc.getId(), outputFailure);
                    }
                    try {
                        SC_SkillSound.send(pc, pc.getId(), 190);
                    } catch (RuntimeException outputFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Experience box sound failed after commit: " + pc.getId(), outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            return ItemActionResult.STOP;
        }
    }

    static ItemActionResult handleRecoveryVoucherNotice(ItemActionContext use) {
        SC_SystemMessage.send(use.player(), "아덴 성당에서 경험치를 무료로 복구할 수 있습니다.");
        return ItemActionResult.COMPLETE;
    }

}
