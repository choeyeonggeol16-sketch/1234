package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1TreasureBox;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Preserves ammunition selection, starter support, treasure boxes precedence. */
final class ItemActionPreparation {
    private ItemActionPreparation() {}

    static boolean stopsDispatch(L1PcInstance pc, L1ItemInstance item, int itemId) {
        if (item.getItem().getType() == 0) {
            if (pc.getWeapon() == null) {
                pc.getInventory().setArrow(item.getItem().getItemId());
                SC_ItemName.send(pc, item);
            } else if (pc.getWeapon().getItem().getType() != 4) {
                L1ItemInstance arrow = pc.getInventory().findItemId(pc.getInventory().getArrowItemId());
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
        } else if (item.getItem().getType() == 15) {
            final L1ItemInstance previous = pc.getInventory().getSting();
            pc.getInventory().setSting(item.getItem().getItemId());
            if (previous != null) SC_ItemName.send(pc, previous);
            SC_ServerMessage.send(pc, 452, item.getLogName());
            SC_ItemName.send(pc, item);
        } else if (itemId == 3000572 || itemId == 3000573 || itemId == 3000578 || itemId == 3000579
                || itemId == 3000575 || itemId == 3000576 || itemId == 3000574 || itemId == 3000577) {
              StarterSupportHandler.getInstance().handle(pc, itemId, item);
              return true;
        } else if (item.getItem().getType() == 16) {
            L1TreasureBox box = L1TreasureBox.get(itemId);
            if (box != null) box.openAtomic(pc, item);
            else SC_SystemMessage.send(pc, "보물상자 설정을 확인해 주세요.");
            return true;
        }


        return false;
    }
}
