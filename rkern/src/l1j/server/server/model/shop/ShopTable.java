package l1j.server.server.model.shop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.hash.TIntIntHashMap;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.templates.L1ShopItem;

public final class ShopTable {
    private static final Logger LOG = Logger.getLogger(ShopTable.class.getName());
    private static volatile ShopTable instance;
    private final ConcurrentMap<Integer, CBuySell> shops = new ConcurrentHashMap<>();

    private ShopTable() {}

    public static ShopTable getInstance() {
        ShopTable current = instance;
        if (current != null) return current;
        synchronized (ShopTable.class) {
            if (instance == null) {
                try {
                    instance = load();
                } catch (SQLException failure) {
                    LOG.log(Level.SEVERE, "Initial NPC shop catalog could not load", failure);
                    instance = new ShopTable();
                }
            }
            return instance;
        }
    }

    public static synchronized void reload() {
        try {
            instance = load();
        } catch (SQLException failure) {
            LOG.log(Level.WARNING, "NPC shop catalog reload failed", failure);
        }
    }

    private static ShopTable load() throws SQLException {
        ShopTable result = new ShopTable();
        try (var con = DatabaseFactory.getInstance().getConnection();
             var ps = con.prepareStatement("SELECT npc_id, item_id, selling_price, purchasing_price, "
                     + "pack_count, enchant, time_limit FROM shop ORDER BY npc_id, order_id");
             var rows = ps.executeQuery()) {
            int npcId = -1;
            List<L1ShopItem> sales = new ArrayList<>();
            List<L1ShopItem> purchases = new ArrayList<>();
            while (rows.next()) {
                int next = rows.getInt("npc_id");
                if (npcId != next) {
                    if (npcId != -1) {
                        result.addShop(npcId, new CBuySell(npcId, sales, purchases));
                        sales.clear();
                        purchases.clear();
                    }
                    npcId = next;
                }
                readOffer(rows, sales, purchases);
            }
            if (npcId != -1) result.addShop(npcId, new CBuySell(npcId, sales, purchases));
        }
        if (Config.IS_VALIDSHOP) result.reportBuybackPrices();
        return result;
    }

    private static void readOffer(ResultSet rows, List<L1ShopItem> sales, List<L1ShopItem> purchases)
            throws SQLException {
        int itemId = rows.getInt("item_id");
        int selling = rows.getInt("selling_price");
        int purchasing = rows.getInt("purchasing_price");
        int packCount = Math.max(1, rows.getInt("pack_count"));
        int enchant = rows.getInt("enchant");
        boolean limited = rows.getBoolean("time_limit");
        if (selling >= 0) sales.add(new L1ShopItem(itemId, selling, packCount, enchant, limited));
        if (purchasing >= 0) purchases.add(new L1ShopItem(itemId, purchasing, packCount, enchant, limited));
    }

    public void Reload(int npcId) {
        synchronized (ShopTable.class) {
            try (var con = DatabaseFactory.getInstance().getConnection();
                 var ps = con.prepareStatement("SELECT item_id, selling_price, purchasing_price, "
                         + "pack_count, enchant, time_limit FROM shop WHERE npc_id=? ORDER BY order_id")) {
                ps.setInt(1, npcId);
                List<L1ShopItem> sales = new ArrayList<>();
                List<L1ShopItem> purchases = new ArrayList<>();
                try (var rows = ps.executeQuery()) {
                    while (rows.next()) readOffer(rows, sales, purchases);
                }
                ShopTable current = getInstance();
                if (sales.isEmpty() && purchases.isEmpty()) current.delShop(npcId);
                else current.addShop(npcId, new CBuySell(npcId, sales, purchases));
            } catch (SQLException failure) {
                LOG.log(Level.WARNING, "NPC shop reload failed for " + npcId, failure);
            }
        }
    }

    private void reportBuybackPrices() {
        TIntIntHashMap sales = new TIntIntHashMap();
        for (CBuySell shop : shops.values()) {
            if (!shop.policy().capsBuyback(shop.getNpcId())
                    || shop.policy().buybackCurrency(shop.getNpcId()) != L1ItemId.ADENA) continue;
            for (L1ShopItem offer : shop.getSellingItems()) {
                if (offer.getPrice() <= 0) continue;
                int existing = sales.putIfAbsent(offer.getItemId(), offer.getPrice());
                if (existing > offer.getPrice()) sales.put(offer.getItemId(), offer.getPrice());
            }
        }
        for (CBuySell shop : shops.values()) {
            if (!shop.policy().capsBuyback(shop.getNpcId())
                    || shop.policy().buybackCurrency(shop.getNpcId()) != L1ItemId.ADENA) continue;
            for (L1ShopItem offer : shop.getBuyingItems()) {
                int selling = sales.get(offer.getItemId());
                if (selling > 0 && selling < offer.getPrice())
                    LOG.warning("NPC " + shop.getNpcId() + " item " + offer.getItemId()
                            + " buyback " + offer.getPrice() + " exceeds catalog sale " + selling);
            }
        }
    }

    public CBuySell get(int npcId) { return shops.get(npcId); }
    public void addShop(int npcId, CBuySell shop) { shops.put(npcId, shop); }
    public void delShop(int npcId) { shops.remove(npcId); }
}
