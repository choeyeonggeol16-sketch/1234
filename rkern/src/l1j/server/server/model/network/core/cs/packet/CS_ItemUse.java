package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.item.function.EquipmentItemActions;

public final class CS_ItemUse {
    private CS_ItemUse() {}

    public static void handle(CUserSocket client, int itemObjid, int targetId,
            int x, int y, String name, int skillId) {
        if (client == null) return;
        User.OnUseItemPacket(client, itemObjid, targetId, x, y, name, skillId);
    }

    public static boolean is_legend_weapon(int itemId) {
        return EquipmentItemActions.is_legend_weapon(itemId);
    }
}
