import java.io.DataInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.NoTradable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.character.animation.SpriteInformation;
import l1j.server.server.model.character.animation.loader.SpriteInformationLoader;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_PersonalShop;
import l1j.server.server.model.network.core.cs.packet.CS_QueryPersonalShop;
import l1j.server.server.model.network.core.cs.packet.CS_Result;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** Live private-shop packet handlers on ready TCP sessions and their CIO owners.
 * Run from a scratch directory with CioSyntheticPopulation, server classes, and
 * rkern/src resources on the classpath. Pass isolated JDBC URL, user, and
 * installed template schema. Only table definitions are copied.
 */
public final class PrivateShopSettlementRegression {
    private static final int GOLD = 40308, SALE = 40001, ORDER = 40002;
    private static final int SALE_STACK = 1200020, ORDER_STACK_A = 1200021, ORDER_STACK_B = 1200022,
            CUSTOMER_ORDER_STACK = 1200023;
    private static final String[] TABLES = {
        "cached_transaction_receipts", "accounts", "characters", "character_items", "character_shop", "character_quests",
        "character_level_gifts", "character_teleport", "character_warehouse", "character_elf_warehouse",
        "character_special_warehouse", "character_package_warehouse", "character_supplementary_service",
        "clan_data", "clan_warehouse", "companion_instance", "companion_class", "letter", "mapids",
        "etcitem", "weapon", "armor", "notradable"
    };
    private static final L1V1Map MARKET = new L1V1Map(800, new byte[8][8], 32, 32, false, true, true, true,
            true, true, true, true, true, true, true, false);
    private final HikariDataSource db;
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private final CioSyntheticPopulation actors = new CioSyntheticPopulation(3);
    private final Set<CUserSocket> accepted = new HashSet<>();

    private record Peer(Socket wire, CUserSocket socket, L1PcInstance pc) implements AutoCloseable {
        @Override public void close() throws IOException { wire.close(); }
    }

    private PrivateShopSettlementRegression(HikariDataSource db) { this.db = db; }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Expected isolated JDBC URL, user and reference schema");
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        String schema = "rkern_privateshop_" + UUID.randomUUID().toString().replace("-", "");
        Path etc = Files.createTempFile("private-shop-etc", ".txt");
        Path equipment = Files.createTempFile("private-shop-equipment", ".txt");
        try (var admin = DriverManager.getConnection(args[0], args[1], password); var ddl = admin.createStatement()) {
            Files.writeString(etc, GOLD + "\n" + SALE + "\n" + ORDER + "\n");
            System.setProperty("rkern.etc.base", etc.toString());
            System.setProperty("rkern.equipment.base", equipment.toString());
            ddl.execute("CREATE DATABASE " + schema + " DEFAULT CHARACTER SET utf8mb4");
            try {
                for (String table : TABLES)
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                final var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(6);
                try (var pool = new HikariDataSource(config)) {
                    var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    field.setAccessible(true);
                    field.set(null, pool);
                    try {
                        new PrivateShopSettlementRegression(pool).run();
                    } finally {
                        field.set(null, null);
                    }
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        } finally {
            Files.deleteIfExists(etc);
            Files.deleteIfExists(equipment);
        }
    }

    private void run() throws Exception {
        Config.MAX_ONLINE_USERS = 8;
        Config.PC_SHOP = 100;
        Config.RATE_WEIGHT_LIMIT = 1;
        Config.CACHE_MAP_FILES = true;
        CPacketFilter.getInstance().load();
        CompanionClassInfo.do_load();
        CompanionInstanceCache.do_load();
        try (var con = db.getConnection(); var sql = con.createStatement()) {
            var sprites = new TIntObjectHashMap<SpriteInformation>();
            try (var row = sql.executeQuery("SELECT 0 AS spr_id, 1 AS width, 1 AS height, 0 AS action_count")) {
                require(row.next(), "fixture sprite row");
                sprites.put(0, SpriteInformation.create(row));
            }
            var catalog = SpriteInformationLoader.class.getDeclaredField("_sprInfos");
            catalog.setAccessible(true);
            catalog.set(SpriteInformationLoader.getInstance(), sprites);
            sql.execute("INSERT INTO etcitem(item_id,name,name_id,item_type,use_type,material,stackable) VALUES "
                    + "(40308,'Fixture adena','Fixture adena','other','none','gold',1),"
                    + "(40001,'Fixture stock','Fixture stock','other','none','cloth',1),"
                    + "(40002,'Fixture order','Fixture order','other','none','cloth',1)");
            try (var account = con.prepareStatement(
                        "INSERT INTO accounts(id,login,charslot,gamepassword,Shop_open_count) VALUES(?,?,8,0,0)");
                    var actor = con.prepareStatement(
                        "INSERT INTO characters(objid,char_name,account_name,Clanname,notes,ReturnStat) VALUES(?,?,?,'','',0)")) {
                for (int seat = 0; seat < 3; seat++) {
                    account.setInt(1, CioSyntheticPopulation.accountId(seat));
                    account.setString(2, CioSyntheticPopulation.login(seat));
                    account.addBatch();
                    actor.setInt(1, CioSyntheticPopulation.actorId(seat));
                    actor.setString(2, CioSyntheticPopulation.characterName(seat));
                    actor.setString(3, CioSyntheticPopulation.login(seat));
                    actor.addBatch();
                }
                account.executeBatch();
                actor.executeBatch();
            }
            try (var item = con.prepareStatement(
                    "INSERT INTO character_items(id,char_id,item_id,item_name,count,is_id) VALUES(?,?,?,'Fixture',?,1)")) {
                seed(item, 1200010, 0, GOLD, 1000);
                seed(item, 1200011, 1, GOLD, 200);
                seed(item, 1200012, 2, GOLD, 200);
                seed(item, SALE_STACK, 0, SALE, 10);
                seed(item, ORDER_STACK_A, 0, ORDER, 6);
                seed(item, ORDER_STACK_B, 0, ORDER, 4);
                seed(item, CUSTOMER_ORDER_STACK, 1, ORDER, 8);
                item.executeBatch();
            }
        }
        require(cache.flushAll(4_000L), "transaction schema ready");
        final var address = new InetSocketAddress("127.0.0.1", 0);
        final var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        final int shards = cio.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(8, 16, 8, 8, shards);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, cio, config)) {
            try (var shop = peer(server, 0); var customer = peer(server, 1); var other = peer(server, 2)) {
                actors.assertReady(server);
                try {
                    verifyShop(shop, customer, other);
                } finally {
                    for (Peer peer : new Peer[] {shop, customer, other})
                        on(peer.socket, () -> peer.pc.getInventory().clearItems()).get(5, TimeUnit.SECONDS);
                }
            }
            server.close();
            actors.assertDrained(server);
            for (long[] bucket : server.poolSnapshot()) require(bucket[2] == 0, "all direct buffer leases retired");
        } finally {
            CSessionLifecycle.stopAccepting();
            require(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session workers drained");
            boolean drained = cache.drainBeforeOwnerShutdown(6_000L);
            cio.shutdown();
            require(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO owner workers stopped");
            require(drained && cache.shutdown(5_000L), "CacheD drained at shutdown");
        }
        require(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "no JVM deadlock");
    }

    private void verifyShop(Peer shop, Peer customer, Peer other) throws Exception {
        final int shopId = shop.pc.getId();
        final byte[] listing = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 1).putInt(SALE_STACK).putInt(7).putInt(10)
                .putShort((short) 2).putInt(ORDER_STACK_A).putInt(5).putInt(6)
                .putInt(ORDER_STACK_B).putInt(8).putInt(4).array();
        on(shop.socket, () -> CS_PersonalShop.handle(shop.socket, 0, listing)).get(5, TimeUnit.SECONDS);
        flush();
        require(shop.pc.isPrivateShop() && listingCount(shopId, SALE_STACK) == 10
                && listingCount(shopId, ORDER_STACK_A) == 6 && listingCount(shopId, ORDER_STACK_B) == 4
                && scalar("SELECT SUM(Shop_open_count) FROM accounts") == 1,
                "classic open published both sides and persisted the account's shop counter");
        assertView(customer, shop, 0);
        var view = customer.socket.getPlayerShopView();
        require(view.size() == 1 && view.listingId(0) == SALE_STACK && view.count(0) == 10 && view.price(0) == 7,
                "customer sale listing matches the packet writer and DB");
        purchase(customer, shop, 0, 3).get(5, TimeUnit.SECONDS);
        flush();
        require(receipts() == 1 && listingCount(shopId, SALE_STACK) == 7, "first partial buy persisted");
        amounts(shop, 1021, 7, 10);
        amounts(customer, 179, 3, 8);

        assertView(customer, shop, 1);
        view = customer.socket.getPlayerShopView();
        require(view.size() == 2 && view.customerItemId(0) == CUSTOMER_ORDER_STACK
                && view.customerItemId(1) == CUSTOMER_ORDER_STACK && view.listingId(0) == ORDER_STACK_A
                && view.listingId(1) == ORDER_STACK_B && view.price(0) == 5 && view.price(1) == 8,
                "two displayed orders can use one customer's stack");
        final long second = sellRow(CUSTOMER_ORDER_STACK, 3, 1);
        final byte[] rest = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(second).array();
        on(customer.socket, () -> CS_Result.handle(customer.socket, shopId, 1, 2, 1,
                sellRow(CUSTOMER_ORDER_STACK, 2, 0), rest)).get(5, TimeUnit.SECONDS);
        flush();
        require(receipts() == 2 && listingCount(shopId, ORDER_STACK_A) == 4
                && listingCount(shopId, ORDER_STACK_B) == 1, "two different orders independently decrement");
        amounts(shop, 987, 7, 15);
        amounts(customer, 213, 3, 3);

        assertView(customer, shop, 1);
        on(customer.socket, () -> CS_Result.handle(customer.socket, shopId, 1, 1, 1,
                sellRow(CUSTOMER_ORDER_STACK, 1, 1), null)).get(5, TimeUnit.SECONDS);
        flush();
        require(receipts() == 3 && listingCount(shopId, ORDER_STACK_B) == 0, "last unit deletes fulfilled order");
        amounts(shop, 979, 7, 16);
        amounts(customer, 221, 3, 2);

        assertView(customer, shop, 1);
        final var buyOrders = customer.socket.getPlayerShopView();
        require(buyOrders.size() == 1, "one unfilled order remains");
        purchase(customer, shop, 0, 1).get(5, TimeUnit.SECONDS);
        flush();
        require(receipts() == 3 && customer.socket.getPlayerShopView() == buyOrders,
                "delayed sale-side request preserves newer order-side view");
        assertView(customer, shop, 0);
        var restrictions = NoTradable.getInstance();
        restrictions.storeId(SALE);
        try {
            require(restrictions.isNoTradable(SALE), "fixture restriction persisted");
            purchase(customer, shop, 0, 1).get(5, TimeUnit.SECONDS);
            flush();
            require(receipts() == 3 && listingCount(shopId, SALE_STACK) == 7,
                    "new operator restriction blocks the once-visible trade");
            amounts(shop, 979, 7, 16);
            amounts(customer, 221, 3, 2);
        } finally {
            restrictions.deleteId(SALE);
        }

        assertView(customer, shop, 0);
        assertView(other, shop, 0);
        try (var hold = db.getConnection(); var lock = hold.prepareStatement(
                "SELECT count FROM character_shop WHERE objid=? AND item_objid=? FOR UPDATE")) {
            hold.setAutoCommit(false);
            lock.setInt(1, shopId);
            lock.setInt(2, SALE_STACK);
            try (var row = lock.executeQuery()) {
                require(row.next() && row.getInt(1) == 7, "test holds remaining stock's DB row");
            }
            try {
                purchase(customer, shop, 0, 4).get(5, TimeUnit.SECONDS);
                require(shop.pc.hasReservedItemTransfer() && cache.hasPendingOwnerApply(shopId),
                        "settlement pending on real stock lock");
                CompletableFuture.allOf(purchase(other, shop, 0, 4),
                        on(shop.socket, () -> CS_PersonalShop.handle(shop.socket, 1, null))).get(5, TimeUnit.SECONDS);
                require(shop.pc.isPrivateShop() && receipts() == 3 && listingCount(shopId, SALE_STACK) == 7,
                        "pending purchase cannot be overtaken by other purchase or close");
            } finally {
                hold.rollback();
            }
        }
        flush();
        require(receipts() == 4 && listingCount(shopId, SALE_STACK) == 3, "only one purchaser consumed stock");
        amounts(shop, 1007, 3, 16);
        amounts(customer, 193, 7, 2);
        amounts(other, 200, 0, 0);
        on(shop.socket, () -> CS_PersonalShop.handle(shop.socket, 1, null)).get(5, TimeUnit.SECONDS);
        flush();
        require(!shop.pc.isPrivateShop() && scalar("SELECT COUNT(*) FROM character_shop") == 0,
                "normal close retires both directions and persisted rows");

        for (Peer peer : new Peer[] {shop, customer, other}) {
            var reloaded = new MySqlCharactersItemStorage().loadItems(peer.pc.getId());
            int gold = amount(peer, GOLD), sale = amount(peer, SALE), order = amount(peer, ORDER);
            on(peer.socket, () -> {
                peer.pc.getInventory().clearItems();
                peer.pc.getInventory().loadItems(reloaded);
            }).get(5, TimeUnit.SECONDS);
            amounts(peer, gold, sale, order);
        }
        require(scalar("SELECT SUM(count) FROM character_items WHERE item_id=40308") == 1400
                && scalar("SELECT SUM(count) FROM character_items WHERE item_id=40001") == 10
                && scalar("SELECT SUM(count) FROM character_items WHERE item_id=40002") == 18,
                "SQL and reloaded inventories conserve stock and currency");
        System.out.println("PASS private shop: listing, partial buy, two orders from one stack, single sell, "
                + "stale side and operator restriction, two buyers and close, 4 exact receipts, reload and conservation");
    }

    private Peer peer(CIOServer server, int seat) throws Exception {
        final var wire = new Socket();
        try {
            wire.connect(server.localAddress(), 4_000);
            wire.setSoTimeout(4_000);
            final var receive = new DataInputStream(wire.getInputStream());
            final int frame = receive.readUnsignedByte() | receive.readUnsignedByte() << 8;
            require(frame > 2 && receive.readNBytes(frame - 2).length == frame - 2, "real TCP key frame");
            final var field = CIOServer.class.getDeclaredField("sockets");
            field.setAccessible(true);
            final CUserSocket socket;
            synchronized (server) {
                socket = ((Set<?>) field.get(server)).stream().filter(CUserSocket.class::isInstance)
                        .map(CUserSocket.class::cast).filter(s -> !accepted.contains(s) && s.isConnected()).findFirst()
                        .orElseThrow();
                accepted.add(socket);
            }
            final var loaded = new MySqlCharactersItemStorage().loadItems(CioSyntheticPopulation.actorId(seat));
            final var published = new CompletableFuture<L1PcInstance>();
            on(socket, () -> {
                var pc = actors.bind(socket, seat);
                pc.setMap(MARKET);
                pc.setX(34);
                pc.setY(34);
                pc.setCurrentSprite(pc.getClassId());
                pc.getInventory().loadItems(loaded);
                published.complete(pc);
            }).get(5, TimeUnit.SECONDS);
            return new Peer(wire, socket, published.get(1, TimeUnit.SECONDS));
        } catch (Exception | Error failure) {
            wire.close();
            throw failure;
        }
    }

    private CompletableFuture<Void> on(CUserSocket socket, Runnable action) {
        final var result = new CompletableFuture<Void>();
        cio.completeOnShard(socket.affinityShard(), action, failure -> {
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        });
        return result;
    }

    private void assertView(Peer customer, Peer shop, int type) throws Exception {
        on(customer.socket, () -> CS_QueryPersonalShop.handle(customer.socket, type, shop.pc.getId()))
                .get(5, TimeUnit.SECONDS);
        final var view = customer.socket.getPlayerShopView();
        require(view != null && view.matches(shop.pc, type == 1), "shop view produced by real list query side " + type);
    }

    private CompletableFuture<Void> purchase(Peer customer, Peer shop, int row, int count) {
        return on(customer.socket, () -> CS_Result.handle(customer.socket, shop.pc.getId(), 0, 1, 1,
                (long) count << 32 | row, null));
    }

    private static long sellRow(int itemId, int count, int row) {
        return (long) row << 56 | (long) count << 32 | Integer.toUnsignedLong(itemId);
    }

    private void flush() throws Exception {
        require(cache.flushAll(6_000L), "durable jobs and owner replays complete");
    }

    private int listingCount(int shopId, int itemId) throws Exception {
        return scalar("SELECT COALESCE(SUM(count),0) FROM character_shop WHERE objid=" + shopId + " AND item_objid=" + itemId);
    }

    private int receipts() throws Exception { return scalar("SELECT COUNT(*) FROM cached_transaction_receipts"); }

    private int scalar(String query) throws Exception {
        try (var con = db.getConnection(); var sql = con.createStatement(); var row = sql.executeQuery(query)) {
            require(row.next(), "aggregate SQL result");
            return row.getInt(1);
        }
    }

    private static void seed(java.sql.PreparedStatement item, int id, int seat, int template, int count) throws Exception {
        item.setInt(1, id);
        item.setInt(2, CioSyntheticPopulation.actorId(seat));
        item.setInt(3, template);
        item.setInt(4, count);
        item.addBatch();
    }

    private static int amount(Peer peer, int id) { return peer.pc.getInventory().GetItemAmount(id); }

    private static void amounts(Peer peer, int gold, int sale, int order) {
        require(amount(peer, GOLD) == gold && amount(peer, SALE) == sale && amount(peer, ORDER) == order,
                "actor " + peer.pc.getId() + " balances " + amount(peer, GOLD) + "/" + amount(peer, SALE)
                        + "/" + amount(peer, ORDER) + " expected " + gold + "/" + sale + "/" + order);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
