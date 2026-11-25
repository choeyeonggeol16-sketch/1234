package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** Ammunition choice only; the caller retains the normal dispatch and completion policy. */
final class AmmunitionSelectionItems {
    private AmmunitionSelectionItems() {}

    static void selectArrow(L1PcInstance pc, L1ItemInstance item) {
        if (pc.getWeapon() == null) {
            pc.getInventory().setArrow(item.getItem().getItemId());
            SC_ItemName.send(pc, item);
        } else if (pc.getWeapon().getItem().getType() != 4) {
            L1ItemInstance arrow = pc.getInventory().GetFirstItemByClassID(pc.getInventory().getArrowItemId());
            if (arrow != null) {
                pc.getInventory().setArrow(0);
                SC_ItemName.send(pc, arrow);
                SC_ItemName.send(pc, item);
            }
        } else {
            // getArrow() can already locate a preferred stack; do not
            // perform the same inventory lookup a second time.
            final L1ItemInstance previous = pc.getInventory().getArrow();
            pc.getInventory().setArrow(item.getItem().getItemId());
            if (previous != null) SC_ItemName.send(pc, previous);
            SC_ServerMessage.send(pc, 452, item.getLogName());
            SC_ItemName.send(pc, item);
        }
    }

    static void selectSting(L1PcInstance pc, L1ItemInstance item) {
        final L1ItemInstance previous = pc.getInventory().getSting();
        pc.getInventory().setSting(item.getItem().getItemId());
        if (previous != null) SC_ItemName.send(pc, previous);
        SC_ServerMessage.send(pc, 452, item.getLogName());
        SC_ItemName.send(pc, item);
    }
}
