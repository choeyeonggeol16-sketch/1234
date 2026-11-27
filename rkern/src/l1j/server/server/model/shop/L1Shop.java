package l1j.server.server.model.shop;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcCashShopInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.item.NpcReissuedItemPolicy;
import l1j.server.server.templates.L1ShopItem;

/** Ordered NPC catalog. Client row numbers are scoped to this catalog revision. */
public final class L1Shop {
    private static final Logger LOG = Logger.getLogger(L1Shop.class.getName());
    private static final AtomicLong REVISIONS = new AtomicLong();

    private final long revision = REVISIONS.incrementAndGet();
    private final int npcId;
    private final ShopPolicy policy;
    private final List<L1ShopItem> selling;
    private final List<L1ShopItem> purchasing;
    private final Map<Long, L1ShopItem> purchaseOffers;
    private final List<L1ShopItem> buybackCeilings;
    private final ShopStock stock;

    public L1Shop(int npcId, List<L1ShopItem> selling, List<L1ShopItem> purchasing) {
        this.npcId = npcId;
        policy = ShopPolicy.forNpc(npcId);
        this.selling = List.copyOf(selling);
        if (purchasing.isEmpty()) {
            purchaseOffers = Map.of();
            this.purchasing = List.of();
        } else {
            Map<Long, L1ShopItem> offers = new LinkedHashMap<>();
            for (L1ShopItem item : purchasing)
                offers.putIfAbsent(key(item.getItemId(), item.getEnchant()), item);
            this.purchasing = List.copyOf(offers.values());
            purchaseOffers = Map.copyOf(offers);
        }
        buybackCeilings = policy.capsBuyback(npcId) && !purchaseOffers.isEmpty()
                ? this.selling.stream()
                        .filter(item -> purchaseOffers.containsKey(key(item.getItemId(), item.getEnchant())))
                        .toList()
                : List.of();
        stock = policy == ShopPolicy.STOCK ? new ShopStock(this.selling) : null;
    }

    public static boolean is_normal_shop(L1NpcInstance npc) {
        return !(npc instanceof L1NpcShopInstance) && ShopTable.getInstance().get(npc.getNpcId()) != null;
    }

    public static L1Shop find_shop(L1NpcInstance npc) {
        if (npc instanceof L1NpcCashShopInstance) return null;
        int npcId = npc.getNpcId();
        L1Shop shop = npc instanceof L1NpcShopInstance
                ? NpcShopManager.getInstance().getShop(npcId) : ShopTable.getInstance().get(npcId);
        if (shop == null) LOG.warning("NPC shop catalog missing for template " + npcId);
        return shop;
    }

    public int getNpcId() { return npcId; }
    long revision() { return revision; }
    ShopPolicy policy() { return policy; }
    ShopStock stock() { return stock; }

    public List<L1ShopItem> getSellingItems() { return selling; }
    public List<L1ShopItem> getBuyingItems() { return purchasing; }

    public L1ShopItem getSellItem(int itemId) { return find(selling, itemId); }
    public L1ShopItem getSellingItem(int itemId) { return getSellItem(itemId); }
    public L1ShopItem getBuyItem(int itemId) { return find(purchasing, itemId); }
    public boolean isSellingItem(int itemId) { return getSellItem(itemId) != null; }

    private static L1ShopItem find(List<L1ShopItem> items, int itemId) {
        for (L1ShopItem item : items) if (item.getItemId() == itemId) return item;
        return null;
    }

    private static long key(int itemId, int enchant) {
        return ((long) itemId << 32) | Integer.toUnsignedLong(enchant);
    }

    public boolean canOffer(L1ShopItem offer, L1PcInstance pc) {
        if (offer == null || offer.getItem() == null || (stock != null && offer.getCount() <= 0)) return false;
        if (!offer.isTimeLimit() || pc == null || pc.getFishingShopBuyTime_1() <= 0) return true;
        ZoneId zone = ZoneId.systemDefault();
        return !Instant.ofEpochMilli(pc.getFishingShopBuyTime_1()).atZone(zone).toLocalDate()
                .equals(Instant.now().atZone(zone).toLocalDate());
    }

    int baseSalePrice(L1ShopItem offer) {
        double price = offer.getPrice() * Config.RATE_SHOP_SELLING_PRICE;
        return Double.isFinite(price) && price >= 1 && price <= CInventory.MAX_AMOUNT ? (int) price : -1;
    }

    public int checkoutPrice(L1ShopItem offer, L1TaxCalculator taxes) {
        int base = baseSalePrice(offer);
        return checkoutPrice(base, taxes);
    }

    int checkoutPrice(int base, L1TaxCalculator taxes) {
        if (base <= 0) return -1;
        long price = policy == ShopPolicy.ADENA ? taxes.layTaxLong(base) : base;
        return price > 0 && price <= CInventory.MAX_AMOUNT ? (int) price : -1;
    }

    public boolean canBuyBack(L1ItemInstance item) {
        return item != null && item.getItem() != null && item.getCount() > 0
                && !item.isEquipped() && !item.isDollOn() && item.getBless() < 128
                && item.getItem().getBless() < 128 && !NpcReissuedItemPolicy.isProtected(item.getItemId())
                && (policy != ShopPolicy.PACKAGE || item.isPackage());
    }

    int assessOwnedUnitPrice(L1ItemInstance item) {
        if (!canBuyBack(item)) return -1;
        return assessBuybackOffer(purchaseOffers.get(key(item.getItemId(), item.getEnchantLevel())));
    }

    public L1AssessedItem assessItem(L1ItemInstance item) {
        int price = assessOwnedUnitPrice(item);
        return price < 0 ? null : new L1AssessedItem(item.getId(), price);
    }

    public int getAssessedPrice(L1ShopItem offer) {
        return offer == null ? -1 : assessBuybackOffer(purchaseOffers.get(key(offer.getItemId(), offer.getEnchant())));
    }

    private int assessBuybackOffer(L1ShopItem item) {
        if (item == null || item.getPrice() <= 0 || item.getPackCount() <= 0) return -1;
        double price = item.getPrice() * Config.RATE_SHOP_PURCHASING_PRICE / item.getPackCount();
        if (!Double.isFinite(price) || price <= 0) return -1;
        // Exhausted stock still sets the price ceiling for the same item and enchantment.
        for (L1ShopItem offer : buybackCeilings) {
            if (offer.getItemId() != item.getItemId() || offer.getEnchant() != item.getEnchant()) continue;
            int salePrice = baseSalePrice(offer);
            if (salePrice > 0) price = Math.min(price, salePrice / Math.max(1, offer.getPackCount()));
        }
        long rounded = (long) price;
        return rounded > 0 && rounded <= CInventory.MAX_AMOUNT ? (int) rounded : -1;
    }

    public List<L1AssessedItem> assessItems(CUserInventory inventory) {
        List<L1AssessedItem> result = null;
        for (L1ShopItem offer : purchasing) {
            if (NpcReissuedItemPolicy.isProtected(offer.getItemId())) continue;
            int price = assessBuybackOffer(offer);
            if (price < 0) continue;
            for (L1ItemInstance item : inventory.GetUnequippedItemListByClassID(offer.getItemId())) {
                if (item.getEnchantLevel() != offer.getEnchant() || !canBuyBack(item)) continue;
                if (result == null) result = new ArrayList<>();
                result.add(new L1AssessedItem(item.getId(), price));
            }
        }
        return result == null ? List.of() : result;
    }

    public L1ShopBuyOrderList newBuyOrderList() { return new L1ShopBuyOrderList(this); }
    public L1ShopSellOrderList newSellOrderList(L1PcInstance pc) { return new L1ShopSellOrderList(this, pc); }

    public void sellItems(L1PcInstance pc, L1ShopBuyOrderList orderList) { sellItems(pc, orderList, null); }

    public void sellItems(L1PcInstance pc, L1ShopBuyOrderList orderList, Runnable afterCommitted) {
        if (npcId != 7320055 && pc != null && orderList != null && orderList.readyFor(this))
            NpcShopSettlement.purchase(this, pc, orderList, afterCommitted);
    }

    public void buyItems(L1ShopSellOrderList orderList) {
        if (npcId != 7320055 && orderList != null && orderList.readyFor(this))
            NpcShopSettlement.buyback(this, orderList);
    }
}
