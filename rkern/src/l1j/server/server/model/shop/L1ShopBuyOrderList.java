package l1j.server.server.model.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.templates.L1ShopItem;

public final class L1ShopBuyOrderList {
    private static final int MAX_BUY_COUNT = 9999;
    private static final long MAX_WEIGHT = (long) Integer.MAX_VALUE * 3000;

    private final L1Shop shop;
    private final L1TaxCalculator taxes;
    private List<L1ShopBuyOrder> orders = List.of();
    private boolean invalid;
    private int nonStackables;
    private long totalWeight;
    private int totalPrice;
    private int totalTaxedPrice;

    L1ShopBuyOrderList(L1Shop shop) {
        this.shop = shop;
        taxes = new L1TaxCalculator(shop.getNpcId());
    }

    public void add(int row, int packages, L1PcInstance pc) {
        if (invalid) return;
        List<L1ShopItem> catalog = shop.getSellingItems();
        if (row < 0 || row >= catalog.size() || packages <= 0 || packages > MAX_BUY_COUNT) {
            invalid = true;
            return;
        }
        L1ShopItem offer = catalog.get(row);
        if (!shop.canOffer(offer, pc)) {
            invalid = true;
            return;
        }

        int unitPrice = shop.baseSalePrice(offer);
        int taxedUnit = shop.checkoutPrice(unitPrice, taxes);
        long quantity = (long) packages * Math.max(1, offer.getPackCount());
        int weight = offer.getItem().getWeight();
        long base = totalPrice + (long) unitPrice * packages;
        long taxed = totalTaxedPrice + (long) taxedUnit * packages;
        boolean stackable = offer.getItem().isStackable();
        if (unitPrice <= 0 || taxedUnit <= 0 || quantity > CInventory.MAX_AMOUNT || weight < 0
                || base > CInventory.MAX_AMOUNT || taxed > CInventory.MAX_AMOUNT
                || (!stackable && nonStackables + quantity > CUserInventory.MAX_SIZE)) {
            invalid = true;
            return;
        }
        long nextWeight = totalWeight + quantity * weight;
        if (nextWeight > MAX_WEIGHT) {
            invalid = true;
            return;
        }
        totalWeight = nextWeight;
        totalPrice = (int) base;
        totalTaxedPrice = (int) taxed;
        if (!stackable) nonStackables += (int) quantity;

        // Individual equipment draws keep their own enchant roll and receipt row.
        int entries = stackable ? 1 : (int) quantity;
        int amount = stackable ? (int) quantity : 1;
        if (entries == 1 && orders.isEmpty()) {
            orders = List.of(new L1ShopBuyOrder(offer, amount, row));
            return;
        }
        if (!(orders instanceof ArrayList)) {
            var expanded = new ArrayList<L1ShopBuyOrder>(Math.max(8, orders.size() + entries));
            expanded.addAll(orders);
            orders = expanded;
        }
        for (int i = 0; i < entries; i++) orders.add(new L1ShopBuyOrder(offer, amount, row));
    }

    boolean readyFor(L1Shop owner) { return shop == owner && !invalid && !orders.isEmpty(); }
    public int BugOk() { return invalid ? 1 : 0; }
    public List<L1ShopBuyOrder> getList() { return Collections.unmodifiableList(orders); }
    public L1ShopItem lastOffer() { return orders.getLast().getItem(); }
    List<L1ShopBuyOrder> orders() { return orders; }
    public int getTotalWeight() { return (int) Math.min(totalWeight, Integer.MAX_VALUE); }
    public long getTotalWeightLong() { return totalWeight; }
    public int getTotalPrice() { return totalPrice; }
    public int getTotalPriceTaxIncluded() { return totalTaxedPrice; }
    L1TaxCalculator getTaxCalculator() { return taxes; }
}
