"""Exercise both directions of the production player-shop receipt handler."""
from pathlib import Path
import os
import subprocess
import tempfile

source = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model"
tx = (source / "network/core/cs/packet/PlayerShopTransactions.java").read_text(encoding="utf-8")
view = (source / "shop/PlayerShopView.java").read_text(encoding="utf-8")
packet = (source / "network/core/cs/packet/CS_Result.java").read_text(encoding="utf-8")
packed = packet[packet.index("\tstatic int param1("):packet.rfind("}")]
fixture = r'''
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.*;

public final class MarketCheck {
    static int checks;
    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    static final class Config { static boolean STANDBY_SERVER; static int PC_SHOP = 80; }
    static final class NpcActionRange { static int get() { return 6; } }
    static final class L1Inventory { static int MAX_AMOUNT = 2_000_000_000; }
    static final class L1ItemId { static int ADENA = 40308; }
    static final class CompanionInstanceCache {
        static boolean is_companion_oblivion(int id) { return id != 701; }
    }
    static final class CS_PersonalShop {
        static boolean isPetItem(L1PcInstance pc, int id) { return false; }
        static boolean isDollItem(L1PcInstance pc, int id) { return false; }
    }
    static final class SC_ServerMessage {
        static void send(L1PcInstance pc, int id, String... text) { pc.notices++; }
    }
    static final class SC_SystemMessage {
        static void send(L1PcInstance pc, String message) { pc.notices++; }
    }
    static final class LoggerInstance {
        static LoggerInstance getInstance() { return new LoggerInstance(); }
        void 개인상점구매(boolean ok, L1PcInstance buyer, L1PcInstance shop, L1ItemInstance item, int quantity) {}
    }

    static final class L1World {
        static final L1World WORLD = new L1World();
        final Map<Long, L1PcInstance> players = new HashMap<>();
        static L1World getInstance() { return WORLD; }
        long smartIdOf(L1PcInstance pc) { return pc.identity; }
        L1PcInstance findObjectBySmartId(long id) { return players.get(id); }
    }

    static final class CS_Result {
''' + packed + r'''
    }

    record ItemType(String getName) {
        boolean isTradable() { return !getName.equals("untradable"); }
    }

    static final class L1ItemInstance {
        final int id, type;
        final ItemType template;
        int count;
        L1ItemInstance(int id, int type, int count) { this(id, type, count, "tradeable"); }
        L1ItemInstance(int id, int type, int count, String name) {
            this.id = id; this.type = type; this.count = count; this.template = new ItemType(name);
        }
        int getId() { return id; }
        int getItemId() { return type; }
        int getEnchantLevel() { return 0; }
        int getAttrEnchantLevel() { return 0; }
        int getCount() { return count; }
        int getBless() { return 0; }
        long getItemdelay3() { return 0; }
        boolean isStackable() { return true; }
        boolean isEquipped() { return false; }
        ItemType getItem() { return template; }
    }

    static final class Inventory {
        final Map<Integer, L1ItemInstance> items = new HashMap<>();
        void add(L1ItemInstance item) { items.put(item.id, item); }
        L1ItemInstance getItem(int id) { return items.get(id); }
        L1ItemInstance findItemId(int type) {
            for (var item : items.values()) if (item.type == type) return item;
            return null;
        }
    }

    static final class CIOClient {
        PlayerShopView view;
        PlayerShopView getPlayerShopView() { return view; }
        void setPlayerShopView(PlayerShopView view) { this.view = view; }
    }

    static final class L1PcInstance {
        final int id;
        final long identity;
        final Inventory inventory = new Inventory();
        ArrayList<DynamicShopItem> sales = new ArrayList<>();
        final ArrayList<DynamicShopItem> purchases = new ArrayList<>();
        final CIOClient client = new CIOClient();
        String account;
        int version = 1, map = 800, notices;
        boolean open = true;
        L1PcInstance(int id) {
            this.id = id; this.identity = 1000L + id; account = "a" + id;
            L1World.getInstance().players.put(identity, this);
        }
        int getId() { return id; }
        int getX() { return 123; }
        int getY() { return 456; }
        int getMapId() { return map; }
        int getLevel() { return 20; }
        int getClanid() { return 1; }
        int getPrivateShopVersion() { return version; }
        boolean isGm() { return false; }
        boolean isDead() { return false; }
        boolean isGhost() { return false; }
        boolean isPrivateShop() { return open; }
        String getName() { return "pc" + id; }
        String getAccountName() { return account; }
        void sendPackets(String message) { notices++; }
        Inventory getInventory() { return inventory; }
        CIOClient getNetConnection() { return client; }
        ArrayList<DynamicShopItem> getSellings() { return sales; }
        ArrayList<DynamicShopItem> getPurchasings() { return purchases; }
        void applyCommittedShopQuantity(boolean purchase, DynamicShopItem row, int expected, int count) {
            var listings = purchase ? purchases : sales;
            check(listings.contains(row) && row.count == expected && count <= expected);
            row.count -= count;
            if (row.count == 0) listings.remove(row);
        }
    }

    static final class DynamicShopItem {
        int objId, price, count, itemId, enchant, attr, iden;
        boolean isPurchase;
        DynamicShopItem(int id, int price, int count, int type, boolean purchase) {
            objId = id; this.price = price; this.count = count; itemId = type; isPurchase = purchase;
        }
    }

    static final class DynamicShopStorage {
        static DynamicShopItem copyListing(DynamicShopItem source) {
            return new DynamicShopItem(source.objId, source.price, source.count, source.itemId, source.isPurchase);
        }
        static void consumeListings(Object connection, int id, DynamicShopItem[] expected, int[] counts) {
            for (int i = 0; i < expected.length; i++) consumeListing(connection, id, expected[i], counts[i]);
        }
        static void consumeListing(Object connection, int id, DynamicShopItem expected, int count) {
            check(connection != null && id > 0 && expected != null && count > 0);
            sqlRows++;
        }
    }

    record Transfer(L1PcInstance from, L1PcInstance to, L1ItemInstance item, int count) {}

    static final class L1InventoryTransaction implements AutoCloseable {
        final ArrayList<Transfer> ops = new ArrayList<>();
        Consumer<Object> sql;
        Runnable committed, rollback;
        static L1InventoryTransaction begin(L1PcInstance first, L1PcInstance other) {
            return new L1InventoryTransaction();
        }
        L1ItemInstance transfer(L1PcInstance from, L1ItemInstance item, int count, L1PcInstance to) {
            ops.add(new Transfer(from, to, item, count));
            return item;
        }
        void requireTables(String names) { check(names.equals("character_shop")); }
        void sql(Consumer<Object> sql) { this.sql = sql; }
        void onFailure(Runnable failure) { rollback = failure; }
        void commit(Runnable action) { committed = action; pending = this; }
        void confirm() {
            sql.accept(this);
            for (var move : ops) {
                move.item.count -= move.count;
                var dest = move.to.inventory.findItemId(move.item.type);
                if (dest == null) move.to.inventory.add(new L1ItemInstance(move.item.id, move.item.type, move.count));
                else dest.count += move.count;
            }
            committed.run();
        }
        @Override public void close() {}
    }

    static L1InventoryTransaction pending;
    static int sqlRows;

    static long buy(int row, int units) { return (long) units << 32 | row; }
    static long sell(int id, int row, int units) { return (long) row << 56 | (long) units << 32 | id; }
    static byte[] tail(long row) {
        return java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(row).array();
    }

    static void execute(L1PcInstance customer, L1PcInstance shop, boolean selling, long first, byte[] rest) {
        pending = null;
        PlayerShopTransactions.execute(customer, shop, selling, rest == null ? 1 : 2, first, rest);
    }

    public static void main(String[] args) {
        var customer = new L1PcInstance(11);
        var shop = new L1PcInstance(12);
        var wallet = new L1ItemInstance(100, L1ItemId.ADENA, 5000);
        var ore = new L1ItemInstance(200, 8254, 10);
        customer.inventory.add(wallet);
        shop.inventory.add(ore);
        var entry = new DynamicShopItem(200, 20, 8, 8254, false);
        // The displayed row zero is not the first underlying listing.
        shop.sales.add(new DynamicShopItem(201, 90, 23, 9876, false));
        shop.sales.add(entry);
        var view = new PlayerShopView(shop, false, 2);
        view.add(200, 0, 20, 8);
        customer.client.view = view;
        execute(customer, shop, false, buy(0, 5), tail(buy(0, 4)));
        check(pending == null);
        execute(customer, shop, false, buy(0, 3), tail(buy(0, 4)));
        check(pending != null && ore.count == 10 && entry.count == 8 && wallet.count == 5000 && sqlRows == 0);
        pending.confirm();
        check(ore.count == 3 && entry.count == 1 && wallet.count == 4860 && sqlRows == 1);
        check(customer.inventory.findItemId(8254).count == 7);
        execute(customer, shop, false, buy(0, 2), null);
        check(pending == null);
        shop.version++;
        execute(customer, shop, false, buy(0, 1), null);
        check(pending == null && customer.client.view == null);

        var purchase = new DynamicShopItem(300, 15, 4, 8254, true);
        shop.purchases.add(purchase);
        int customerOre = customer.inventory.findItemId(8254).id;
        view = new PlayerShopView(shop, true, 1);
        view.add(300, customerOre, 15, 4);
        customer.client.view = view;
        execute(customer, shop, true, sell(777, 0, 2), null);
        check(pending == null);
        execute(customer, shop, true, sell(customerOre, 0, 4), null);
        check(pending != null && purchase.count == 4 && wallet.count == 4860);
        pending.rollback.run();
        check(purchase.count == 4 && wallet.count == 4860 && sqlRows == 1);
        execute(customer, shop, true, sell(customerOre, 0, 2), null);
        pending.confirm();
        check(purchase.count == 2 && wallet.count == 4890 && sqlRows == 2);
        customer.map = 4;
        execute(customer, shop, true, sell(customerOre, 0, 1), null);
        check(pending == null);
        customer.map = shop.map;
        customer.account = shop.account;
        execute(customer, shop, true, sell(customerOre, 0, 1), null);
        check(pending == null);
        customer.account = "customer";
        shop.open = true;
        var newView = new PlayerShopView(shop, false, 1);
        newView.add(200, 0, 20, 1);
        customer.client.view = newView;
        execute(customer, shop, false, buy(0, 1), null);
        check(pending != null);
        var staleList = shop.sales;
        shop.sales = new ArrayList<>();
        try {
            pending.confirm();
            throw new AssertionError("expected stale owner replay to fail");
        } catch (IllegalStateException expected) {
            check(staleList.contains(entry) && entry.count == 1);
            check(shop.sales.isEmpty() && wallet.count == 4870 && sqlRows == 3);
        }
        System.out.println("PASS player shop receipt checks=" + checks);
    }
''' + tx[tx.index("final class PlayerShopTransactions"):].replace("final class PlayerShopTransactions", "static final class PlayerShopTransactions", 1) + view[view.index("public final class PlayerShopView"):].replace("public final class PlayerShopView", "static final class PlayerShopView", 1) + "}"
with tempfile.TemporaryDirectory() as path:
    file = Path(path, "MarketCheck.java")
    file.write_text(fixture, encoding="utf-8")
    dependency = os.environ.get("FASTUTIL_JAR", "/tmp/rkern-n9-deps/fastutil-8.5.15.jar")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", dependency, "-d", path, str(file)], check=True)
    subprocess.run(["java", "-cp", os.pathsep.join([path, dependency]), "MarketCheck"], check=True)
