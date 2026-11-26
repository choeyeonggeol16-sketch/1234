package l1j.server.server.model.inventory;

import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.inventory.warehouse.PrivateWarehouse;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;
import l1j.server.server.templates.L1EtcItem;

/** Run in a fresh JVM with an isolated JDBC server URL, user and installed template
 * schema name. Copies table definitions only; all writes use a fresh scratch schema. */
public final class WarehouseDisconnectRegression {
    private static final String[] TABLES = {
        "cached_transaction_receipts", "character_items", "character_warehouse",
        "clan_warehouse", "character_elf_warehouse", "character_special_warehouse",
        "character_supplementary_service", "characters", "character_quests",
        "character_level_gifts", "mapids"
    };
    private static final int FIRST_ID = 1999986700;
    private static HikariDataSource db;

    /** Observe when terminal release calls logout, without starting unrelated game saves. */
    private static final class Pc extends L1PcInstance {
        final CountDownLatch loggedOut = new CountDownLatch(1);
        volatile boolean loggedOutWithGate;
        volatile boolean loggedOutWithContinuation;

        @Override public int getMaxWeight() { return 100000; }
        @Override public void logout() {
            loggedOutWithGate = hasReservedItemTransfer();
            loggedOutWithContinuation = hasItemTransferContinuation();
            loggedOut.countDown();
        }
        @Override public boolean isLogoutStarted() { return loggedOut.getCount() == 0; }
        @Override public boolean isLogoutComplete() { return isLogoutStarted(); }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    private static int count(String table, String where) throws SQLException {
        try (var c = db.getConnection(); var statement = c.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table + where)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static CUserSocket accepted(CIOServer server, Socket client) throws Exception {
        client.connect(server.localAddress());
        client.setSoTimeout(4000);
        var input = new DataInputStream(client.getInputStream());
        int size = input.readUnsignedByte() | input.readUnsignedByte() << 8;
        check(size > 2 && input.readNBytes(size - 2).length == size - 2, "server greeting");
        synchronized (server) {
            for (Object connection : (Set<?>) field(CIOServer.class, "sockets").get(server)) {
                if (connection instanceof CUserSocket socket && socket.isConnected()) return socket;
            }
        }
        throw new AssertionError("missing live fixture connection");
    }

    private static Pc publish(CUserSocket socket, int sequence, String login) {
        Pc pc = new Pc();
        pc.setId(FIRST_ID + sequence);
        pc.setName("WarehouseFixture" + sequence);
        pc.setAccountName(login);
        pc.setMap(new L1V1Map(1, new byte[8][8], 32, 32, false, true, true, true,
                true, true, true, true, true, true, true, false));
        pc.setX(34);
        pc.setY(34);
        Account account = new Account();
        account.setAccountId(778 + sequence);
        account.setName(login);
        socket.setAccount(account);
        socket.setActiveChar(pc);
        pc.setNetConnection(socket);
        CWorld.getInstance().storeObject(pc);
        CIOThreadCallback.getInstance().registerPc(pc);
        pc.setCacheDSessionEpoch(CacheD.getInstance().registerLoadedCharacterSession(login, pc.getName(), pc.getId()));
        return pc;
    }

    private static void transfer(CIOServer server, int sequence, boolean withdraw, boolean batch) throws Exception {
        CacheD cache = CacheD.getInstance();
        try (var client = new Socket()) {
            CUserSocket socket = accepted(server, client);
            String login = "whcheck" + sequence;
            Pc pc = publish(socket, sequence, login);
            CUserInventory inventory = pc.getInventory();
            PrivateWarehouse house = new PrivateWarehouse(login);
            int size = batch ? 2 : 1;
            int[] ids = new int[size], amounts = new int[size];
            var items = new ArrayList<L1ItemInstance>(size);
            try {
                for (int i = 0; i < size; i++) {
                    L1EtcItem template = new L1EtcItem();
                    template.setItemId(301 + i);
                    template.setName("fixture" + i);
                    var item = new L1ItemInstance(template, 1);
                    ids[i] = FIRST_ID + 90 + sequence * 10 + i;
                    amounts[i] = 1;
                    item.setId(ids[i]);
                    item.getLastStatus().updateAll();
                    items.add(item);
                    if (withdraw) {
                        house.insertItem(item);
                    } else {
                        MySqlCharactersItemStorage.storeSnapshot(ItemSnapshot.capture(item, pc.getId()));
                        inventory._items.Push(item);
                        inventory.addItemCountCache(item.getItemId(), 1);
                        item._cha = pc;
                        cache.registerLoadedItemOwner(pc.getId(), ids[i]);
                        CWorld.getInstance().storeObject(item);
                    }
                }
                if (withdraw) {
                    check(cache.flushAll(4000), "warehouse seeds");
                    house.installLoadedSnapshot(items);
                }
                String to = withdraw ? "character_items" : "character_warehouse";
                String from = withdraw ? "character_warehouse" : "character_items";
                String where = " WHERE id BETWEEN " + ids[0] + " AND " + ids[size - 1];
                int receipts = count("cached_transaction_receipts", "");
                var effects = new AtomicInteger();

                // Hold only the original session monitor. JDBC can commit, but
                // the real owner cannot reapply the inventory until after close.
                synchronized (socket) {
                    boolean admitted;
                    if (withdraw) {
                        admitted = batch ? inventory.tradeWarehouseWithdrawalBatch(ids, amounts, house, 0, 0,
                                        null, effects::incrementAndGet)
                                : house.tradeItem(items.getFirst(), 1, inventory, 0, 0, effects::incrementAndGet) != null;
                    } else {
                        admitted = batch ? inventory.tradeWarehouseDepositBatch(ids, amounts, house, effects::incrementAndGet)
                                : inventory.tradeItem(items.getFirst(), 1, house, effects::incrementAndGet) != null;
                    }
                    check(admitted, "warehouse admission");
                    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                    while (count(to, where) != size && System.nanoTime() < until) Thread.sleep(2);
                    check(count(to, where) == size && effects.get() == 0 && pc.isItemTransferBusy(),
                            "SQL committed and game replay still pending");
                    socket.close();
                    check(!socket.isConnected(), "socket closed before replay");
                }
                check(pc.loggedOut.await(4, TimeUnit.SECONDS), "logout resumed");
                check(effects.get() == 1, "warehouse completion count was " + effects.get());
                check(!pc.loggedOutWithGate, "logout began with a reserved item transfer");
                check(!pc.loggedOutWithContinuation, "logout began with a running receipt effect");
                check(cache.flushAll(4000), "CacheD did not drain after logout: " + cache.lastFailure());
                check(count(from, where) == 0 && count(to, where) == size
                        && count("cached_transaction_receipts", "") == receipts + 1, "exact durable rows after logout");
                for (int id : ids) {
                    check((house.GetByServerID(id) != null) != withdraw
                            && (inventory.GetByServerID(id) != null) == withdraw, "game and SQL agree");
                }
            } finally {
                socket.close();
                CWorld world = CWorld.getInstance();
                for (L1ItemInstance item : items) world.removeObject(item);
                world.removeObject(pc);
            }
        }
    }

    private static void inventoryDebit(CIOServer server) throws Exception {
        CacheD cache = CacheD.getInstance();
        try (var peer = new Socket()) {
            CUserSocket socket = accepted(server, peer);
            Pc pc = publish(socket, 4, "debitcheck");
            L1EtcItem template = new L1EtcItem();
            template.setItemId(40308);
            template.setName("fixture currency");
            template.set_stackable(true);
            L1ItemInstance item = new L1ItemInstance(template, 100);
            item.setId(FIRST_ID + 480);
            CUserInventory inventory = pc.getInventory();
            int receipts = count("cached_transaction_receipts", "");
            AtomicInteger effects = new AtomicInteger();
            try {
                MySqlCharactersItemStorage.storeSnapshot(ItemSnapshot.capture(item, pc.getId()));
                inventory._items.Push(item);
                inventory.addItemCountCache(item.getItemId(), item.getCount());
                item._cha = pc;
                item.getLastStatus().updateAll();
                cache.registerLoadedItemOwner(pc.getId(), item.getId());
                CWorld.getInstance().storeObject(item);
                String where = " WHERE id=" + item.getId() + " AND char_id=" + pc.getId() + " AND count=93";
                CountDownLatch committed = new CountDownLatch(1);
                CountDownLatch allowReplay = new CountDownLatch(1);
                try {
                    try (var tx = L1InventoryTransaction.begin(pc, item.getId())) {
                        check(tx.debit(item, 7), "ordinary inventory debit admitted");
                        tx.onDurableCommit(() -> {
                            committed.countDown();
                            try {
                                check(allowReplay.await(8, TimeUnit.SECONDS), "release replay");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                        });
                        tx.commitAfterInventoryRelease(() -> {
                            check(!pc.isLogoutStarted() && item.getCount() == 93, "effect sees live replay");
                            effects.incrementAndGet();
                        });
                    }
                    check(committed.await(4, TimeUnit.SECONDS) && count("character_items", where) == 1
                            && item.getCount() == 100, "durable before replay");
                    peer.close();
                    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                    while (!socket.isCloseComplete() && System.nanoTime() < until) Thread.sleep(2);
                    check(socket.isCloseComplete() && !pc.isLogoutStarted(), "retained disconnected actor");
                } finally {
                    allowReplay.countDown();
                }
                check(pc.loggedOut.await(4, TimeUnit.SECONDS) && cache.flushAll(4000), "debit logout drained");
                check(effects.get() == 1 && item.getCount() == 93 && count("character_items", where) == 1
                        && count("cached_transaction_receipts", "") == receipts + 1, "one debit and one effect");
            } finally {
                socket.close();
                CWorld.getInstance().removeObject(item);
                CWorld.getInstance().removeObject(pc);
            }
        }
    }

    private static void verify() throws Exception {
        Config.MAX_ONLINE_USERS = 8;
        Config.MAX_PERSONAL_WAREHOUSE_ITEM = 150;
        Config.RATE_WEIGHT_LIMIT = 1;
        Config.CACHE_MAP_FILES = true;
        CompanionClassInfo.do_load();
        CPacketFilter.getInstance().load();
        CIOThreadCallback owners = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        int shards = owners.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(8, 16, 8, 8, shards);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, owners, config)) {
            transfer(server, 0, false, false);
            transfer(server, 1, true, false);
            transfer(server, 2, false, true);
            transfer(server, 3, true, true);
            inventoryDebit(server);
        } finally {
            owners.shutdown();
            check(owners.awaitTermination(5, TimeUnit.SECONDS), "CIO stopped");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Expected isolated JDBC URL, user, and template schema name");
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        String schema = "rkern_warehouse_replay_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password); var ddl = admin.createStatement()) {
            ddl.executeUpdate("CREATE DATABASE " + schema);
            try {
                for (String table : TABLES)
                    ddl.executeUpdate("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                ddl.executeUpdate("CREATE TABLE " + schema + ".companion_class (class_id INT PRIMARY KEY)");
                var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setCatalog(schema);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setMaximumPoolSize(5);
                try (var pool = new HikariDataSource(config)) {
                    db = pool;
                    field(DatabaseFactory.class, "DATABASE_POOL").set(null, db);
                    check(CacheD.getInstance().flushAll(4000), "transaction schema ready");
                    verify();
                }
            } finally {
                ddl.executeUpdate("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS warehouse moves and inventory debit after commit and disconnect");
    }
}
