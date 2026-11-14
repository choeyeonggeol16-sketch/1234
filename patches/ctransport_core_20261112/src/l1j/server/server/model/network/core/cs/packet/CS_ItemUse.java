package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.item.function.ItemUseEquipment;

public final class CS_ItemUse {
    private CS_ItemUse() {}

    public static void handle(CIOClient client, int itemObjid, int targetId,
            int x, int y, String name, int skillId) {
        if (client == null) return;
        User.OnUseItemPacket(client, itemObjid, targetId, x, y, name, skillId);
    }

    public static boolean is_legend_weapon(int itemId) {
        return ItemUseEquipment.is_legend_weapon(itemId);
    }
}
