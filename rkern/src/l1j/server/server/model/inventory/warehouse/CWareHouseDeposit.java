package l1j.server.server.model.inventory.warehouse;

import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Game-server warehouse deposit policy. CacheD remains the transfer owner. */
final class CWareHouseDeposit {
    private CWareHouseDeposit() {}

    static boolean IsDeposit(int kind) {
        return kind == 2 || kind == 4 || kind == 8;
    }

    static void Request(L1PcInstance pc, int kind, Warehouse warehouse,
            L1ItemInstance item, int count, long now) {
        if (CanDeposit(pc, kind, warehouse, item, count, now))
            pc.getInventory().tradeItem(item.getId(), count, warehouse, null);
    }

    static void Request(L1PcInstance pc, Warehouse warehouse, int[] ids, int[] counts) {
        if (!pc.getInventory().tradeWarehouseDepositBatch(ids, counts, warehouse, null))
            SC_SystemMessage.send(pc, "창고 아이템을 한 번에 맡길 수 없습니다. 아이템과 창고 공간을 확인해 주세요.");
    }

    static boolean CanDeposit(L1PcInstance pc, int kind, Warehouse warehouse,
            L1ItemInstance item, int count, long now) {
        if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(item.getItemId())) {
            SC_SystemMessage.send(pc, "해당 아이템은 창고 이용을 할 수 없습니다.");
            return false;
        }
        if (kind != 8 && item.getItemdelay3() >= now) return false;
        if (item.isEquipped() || !item.getItem().isTradable() || (kind != 8 && item.getBless() >= 128)
                || !CompanionInstanceCache.is_companion_oblivion(item.getId())
                || User.IsCompanionItem(pc, item.getId())) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return false;
        }
        if (User.IsDollItem(pc, item.getId())) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 소환 된 인형은 맡길수가 없습니다.");
            return false;
        }
        if (warehouse.CheckAddableToWarehouse(item, count) == CInventory.SIZE_OVER) {
            SC_ServerMessage.send(pc, 75);
            return false;
        }
        return true;
    }

}
