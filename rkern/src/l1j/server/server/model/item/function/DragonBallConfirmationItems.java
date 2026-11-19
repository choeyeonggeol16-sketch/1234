package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;

/** Legacy Dragon Ball confirmation; the reply handler still owns any effects. */
final class DragonBallConfirmationItems {
    private DragonBallConfirmationItems() {}

    static ItemActionResult requestConfirmation(ItemActionContext use) {
        SC_MessageYN.send(use.player(), 944, "드래곤볼");
        return ItemActionResult.COMPLETE;
    }
}
