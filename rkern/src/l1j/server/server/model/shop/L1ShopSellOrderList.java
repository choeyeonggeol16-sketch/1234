package l1j.server.server.model.shop;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

record L1ShopSellOrder(L1AssessedItem item, int count) {
}

public final class L1ShopSellOrderList {
    private static final int MAX_SELL_COUNT = 10_000;
    private static final int MAX_SELL_COUNT_NORMAL = 500;
    private static final int EXCEPTION_ITEM_ID = 41246;

    private final L1Shop shop;
    private final L1PcInstance pc;
    private List<L1ShopSellOrder> orders = List.of();
    private boolean invalid;

    L1ShopSellOrderList(L1Shop shop, L1PcInstance pc) {
        this.shop = shop;
        this.pc = pc;
    }

    public void add(int itemObjectId, int count, L1PcInstance owner) {
        if (invalid) return;
        if (pc == null || owner != pc || itemObjectId <= 0 || count <= 0 || count > MAX_SELL_COUNT) {
            invalid = true;
            return;
        }
        L1ItemInstance item = pc.getInventory().GetByServerID(itemObjectId);
        if (!shop.canBuyBack(item) || count > item.getCount() || (!item.isStackable() && count != 1)
                || (count > MAX_SELL_COUNT_NORMAL && item.getItemId() != EXCEPTION_ITEM_ID)) {
            invalid = true;
            return;
        }
        for (L1ShopSellOrder order : orders) {
            if (order.item().getTargetId() == itemObjectId) {
                invalid = true;
                return;
            }
        }
        L1AssessedItem assessed = shop.assessItem(item);
        if (assessed == null) {
            invalid = true;
            return;
        }
        L1ShopSellOrder order = new L1ShopSellOrder(assessed, count);
        if (orders.isEmpty()) {
            orders = List.of(order);
        } else {
            if (!(orders instanceof ArrayList)) orders = new ArrayList<>(orders);
            orders.add(order);
        }
    }

    boolean readyFor(L1Shop owner) { return shop == owner && pc != null && !invalid && !orders.isEmpty(); }
    public int BugOk() { return invalid ? 1 : 0; }
    L1PcInstance getPc() { return pc; }
    List<L1ShopSellOrder> orders() { return orders; }
}
