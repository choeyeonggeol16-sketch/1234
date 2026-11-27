package l1j.server.server.model.character.action;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CTrade;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;

/** Reusable execution for private-store requests held by the controller's queue. */
final class CPrivateStoreAction {
    private final CCreatureController controller;

    CPrivateStoreAction(CCreatureController controller) {
        this.controller = controller;
    }

    void execute(L1PcInstance pc, CUserSocket session, int stamp, boolean opening, Runnable transition) {
        if (!isCurrent(pc, session, stamp)) return;
        final var inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange()) {
            if (isCurrent(pc, session, stamp)) SC_SystemMessage.send(pc, opening
                    ? "잠시 후 개인상점을 다시 열어 주세요."
                    : "잠시 후 개인상점을 다시 닫아 주세요.");
            return;
        }
        final var previous = pc.privateStoreState();
        try {
            if (!isCurrent(pc, session, stamp) || (opening && !canStart(pc))) return;
            transition.run();
        } finally {
            try {
                // A presentation failure after successful publication must
                // still end locomotion. A rejected registration keeps it.
                final var current = pc.privateStoreState();
                if (opening && current.open() && current != previous) controller.OnPrivateStoreOpened();
            } finally {
                inventory.endEquipmentChange();
            }
        }
    }

    private static boolean isCurrent(L1PcInstance pc, CUserSocket session, int stamp) {
        return session.ownsReadyGameSession() && pc.isItemTransferSession(session, stamp);
    }

    private boolean canStart(L1PcInstance pc) {
        if (pc.isDead() || pc.isGhost() || pc.get_teleport() || pc.is_non_action()
                || GameUtils.isNonAction(pc) || pc.GetTrade() != null || CTrade.HasPendingTradeBlocker(pc)) return false;
        if (controller.currentActionType() != CCreatureController.ACTION_SKILL) return true;
        SC_SystemMessage.send(pc, "스킬 사용 중에는 개인상점을 열 수 없습니다.");
        return false;
    }
}
