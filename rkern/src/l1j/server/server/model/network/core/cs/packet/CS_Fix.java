package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_Fix {

    private CS_Fix() {
    }

    public static void handle(
            CUserSocket client,
            int itemObjectId,
            int npcObjectId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (npcObjectId != 0) {

            repairItem(
                    pc,
                    itemObjectId,
                    npcObjectId);

            return;
        }

 
        // Old pet retrieval is retired; normal equipment repair remains above.
    }

    private static void repairItem(
            L1PcInstance pc,
            int itemObjectId,
            int npcObjectId) {

        final L1Object object = CWorld.getInstance().findObject(npcObjectId);
        if (!(object instanceof L1NpcInstance npc) || npc.getMapId() != pc.getMapId()
                || Math.abs(pc.getX() - npc.getX()) > 3 || Math.abs(pc.getY() - npc.getY()) > 3) return;

        final CUserInventory inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange()) return;
        try {
            final L1ItemInstance item = inventory.GetByServerID(itemObjectId);
            if (item == null || item.get_durability() <= 0) return;
            final int durability = item.get_durability();
            if (!inventory.DeleteItemInInventory(L1ItemId.ADENA, durability * 200)) {
                SC_ServerMessage.send(pc, 189);
                return;
            }
            item.set_durability(0);
            inventory.updateDurability(item, durability);
        } finally {
            inventory.endEquipmentChange();
        }
    }

}
