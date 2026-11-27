import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.DataInputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.inventory.CTrade;
import l1j.server.server.model.inventory.CTradeManager;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_AskExchange;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.cs.packet.CS_TradeAccept;
import l1j.server.server.model.network.core.cs.packet.CS_TradeAddItem;
import l1j.server.server.model.network.core.cs.packet.CS_TradeCancel;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** Run in a fresh working directory with isolated JDBC URL, user and reference schema.
 * Compile with SelectorTransportTest and CioSyntheticPopulation. Include rkern/src
 * on the runtime classpath for script resources. Copies table definitions only.
 */
public final class DirectTradeIntegrationRegression {
    private static final String[] TABLES = {
        "accounts", "characters", "character_items", "cached_transaction_receipts", "character_teleport",
        "character_warehouse", "character_elf_warehouse", "character_special_warehouse",
        "character_supplementary_service", "character_package_warehouse", "clan_warehouse", "clan_data",
        "character_quests", "character_level_gifts", "character_pending_credits", "item_key_boss",
        "companion_instance", "companion_class", "letter", "mapids", "etcitem", "weapon", "armor", "notradable"
    };
    private static final int FIRST_TEMPLATE = 40001;
    private static final int FIRST_OBJECT = 30_000_001;
    private static final int[] INITIAL = {10, 2, 3, 15};
    private static HikariDataSource db;

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("expected disposable JDBC URL, user and reference schema");
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), "DisruptorShardCount=2\nNetworkClientPermission=8\n"
                + "NetworkAddress2Account=8\nUsePreLoginTimeout=false\n");
        Files.writeString(Path.of("fixture-etc-base.txt"), FIRST_TEMPLATE + "\n" + (FIRST_TEMPLATE + 1) + "\n");
        Files.createFile(Path.of("fixture-equipment-base.txt"));
        Files.createFile(Path.of("fixture-etc-tooltips.txt"));
        System.setProperty("rkern.etc.base", Path.of("fixture-etc-base.txt").toAbsolutePath().toString());
        System.setProperty("rkern.equipment.base", Path.of("fixture-equipment-base.txt").toAbsolutePath().toString());
        System.setProperty("rkern.etc.tooltips", Path.of("fixture-etc-tooltips.txt").toAbsolutePath().toString());
        Config.CACHE_MAP_FILES = true;
        Config.TIME_ZONE = "UTC";
        Config.MAX_ONLINE_USERS = 8;
        Config.RATE_WEIGHT_LIMIT = 1;
        String schema = "rkern_directtrade_" + UUID.randomUUID().toString().replace("-", "");
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema);
            try {
                for (String table : TABLES)
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(6);
                try (var source = new HikariDataSource(config)) {
                    db = source;
                    var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    pool.setAccessible(true);
                    pool.set(null, source);
                    seed();
                    CTransaction.verifySchema();
                    CompanionClassInfo.do_load();
                    CompanionInstanceCache.do_load();
                    exercise();
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM threads did not deadlock");
        System.out.println("PASS actual direct trade request, two-sided consent, cancellation, concurrent confirmation and durable conservation");
    }

    private static void seed() throws Exception {
        try (var con = db.getConnection(); var ddl = con.createStatement();
             var account = con.prepareStatement("INSERT INTO accounts (id,login,charslot,gamepassword,Shop_open_count) VALUES(?,?,8,0,0)");
             var character = con.prepareStatement("INSERT INTO characters (objid,char_name,account_name,Clanname,notes,ReturnStat) VALUES(?,?,?,'','',0)");
             var item = con.prepareStatement("INSERT INTO character_items (id,char_id,item_id,item_name,count,is_id,bless) VALUES(?,?,?,'Fixture',?,1,1)")) {
            ddl.execute("INSERT INTO etcitem (item_id,name,name_id,item_type,use_type,material,stackable,trade) VALUES "
                    + "(40001,'Fixture A','fixture_a','questitem','none','none',1,0),"
                    + "(40002,'Fixture B','fixture_b','questitem','none','none',1,0)");
            for (int seat = 0; seat < 2; seat++) {
                account.setInt(1, CioSyntheticPopulation.accountId(seat));
                account.setString(2, CioSyntheticPopulation.login(seat));
                account.executeUpdate();
                character.setInt(1, CioSyntheticPopulation.actorId(seat));
                character.setString(2, CioSyntheticPopulation.login(seat));
                character.setString(3, CioSyntheticPopulation.login(seat));
                character.executeUpdate();
                for (int slot = 0; slot < 2; slot++) {
                    item.setInt(1, objectId(seat, slot));
                    item.setInt(2, CioSyntheticPopulation.actorId(seat));
                    item.setInt(3, FIRST_TEMPLATE + slot);
                    item.setInt(4, INITIAL[seat * 2 + slot]);
                    item.executeUpdate();
                }
            }
        }
    }

    private static void exercise() throws Exception {
        CacheD cache = CacheD.getInstance();
        check(cache.flushAll(5000), "initial persistence drain");
        long deadlocks = deadlocks();
        CPacketFilter.getInstance().load();
        var scheduler = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        int shards = scheduler.bindNetworkIngress(config);
        check(shards == 2, "two independent live CIO owners");
        var buffers = new CIOBufferPool(8, 24, 8, 8, shards);
        var actors = new CioSyntheticPopulation(2);
        var map = tradeMap();
        try (var server = new CIOServer(address, 8, shards, 8, buffers, scheduler, config)) {
            try (var first = new Peer(server, actors, map, 0); var second = new Peer(server, actors, map, 1)) {
                check(first.session.affinityShard() != second.session.affinityShard(), "opposed player owners");
                verifyCounts(first, second, 0, INITIAL);

                CTrade canceled = open(first, second);
                var timeoutField = CTrade.class.getDeclaredField("timeout");
                timeoutField.setAccessible(true);
                var index = CServerTimer.class.getDeclaredField("distantIndex");
                index.setAccessible(true);
                var timeout = (CServerTimer) timeoutField.get(canceled);
                check(timeout != null && first.onOwner(() -> index.getInt(timeout))
                        .get(5, TimeUnit.SECONDS) >= 0, "active invitation owns a distant deadline");
                offer(first, second, 0, 2);
                offer(second, first, 1, 3);
                first.action(() -> CS_TradeCancel.handle(first.session));
                terminal(first, second, canceled, 1);
                check(timeout.isCancelled() && timeoutField.get(canceled) == null
                        && first.onOwner(() -> index.getInt(timeout)).get(5, TimeUnit.SECONDS) == -1,
                        "cancel removes the distant deadline immediately");
                verifyCounts(first, second, 0, INITIAL);

                CTrade sequential = open(first, second);
                offer(first, second, 0, 1);
                offer(first, second, 0, 3);
                offer(second, first, 1, 5);
                first.action(() -> CS_TradeAccept.handle(first.session));
                check(sequential.IsStarted() && !sequential.IsResolving(), "one confirmation keeps exchange open");
                verifyCounts(first, second, 0, INITIAL);
                second.action(() -> CS_TradeAccept.handle(second.session));
                terminal(first, second, sequential, 0);
                verifyCounts(first, second, 1, 6, 7, 7, 10);

                CTrade concurrent = open(first, second);
                offer(first, second, 1, 2);
                offer(second, first, 0, 1);
                var simultaneous = new CountDownLatch(2);
                var confirmA = confirm(first, simultaneous);
                var confirmB = confirm(second, simultaneous);
                confirmA.get(5, TimeUnit.SECONDS);
                confirmB.get(5, TimeUnit.SECONDS);
                terminal(first, second, concurrent, 0);
                verifyCounts(first, second, 2, 7, 5, 6, 12);

                CTrade relocated = open(first, second);
                offer(first, second, 0, 1);
                first.action(() -> CS_TradeAccept.handle(first.session));
                second.action(() -> {
                    second.pc.setMap(tradeMap());
                    try { CS_TradeAccept.handle(second.session); }
                    finally { second.pc.setMap(map); }
                });
                terminal(first, second, relocated, 1);
                verifyCounts(first, second, 2, 7, 5, 6, 12);
                for (Peer peer : new Peer[] {first, second}) {
                    for (int slot = 0; slot < 2; slot++)
                        CWorld.getInstance().removeObject(peer.pc.getInventory().GetByServerID(objectId(peer.seat, slot)));
                }
            }
            server.close(12, TimeUnit.SECONDS);
            check(server.awaitTermination(Duration.ofSeconds(5)), "listener closed");
            for (long[] bucket : server.poolSnapshot()) check(bucket[2] == 0, "network lease released");
            actors.assertDrained(server);
            check(deadlocks() == deadlocks, "no SQL deadlock");
        } finally {
            boolean drained = cache.drainBeforeOwnerShutdown(8000);
            CSessionLifecycle.stopAccepting();
            scheduler.shutdown();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "release worker drained");
            check(scheduler.awaitTermination(8, TimeUnit.SECONDS), "CIO owners closed");
            check(drained && cache.shutdown(5000), "CacheD stopped");
        }
    }

    private static L1V1Map tradeMap() {
        return new L1V1Map(1, new byte[8][8], 32, 32, false, true, true, true,
                true, true, true, true, true, true, true, false);
    }

    private static CTrade open(Peer first, Peer second) throws Exception {
        first.action(() -> CS_AskExchange.handle(first.session));
        CTrade trade = first.pc.GetTrade();
        check(trade != null && second.pc.GetTrade() == trade && !trade.IsStarted(), "both actors attached to invitation");
        byte[] request = second.awaitPacket(Opcodes.S_ASK);
        int advertised = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN).getInt(3);
        check(advertised == trade.GetID(), "outbound dialog identifies the live trade");
        second.action(() -> CS_Attr.handle(second.session, 252, advertised, 1, ""));
        first.awaitPacket(Opcodes.S_XCHG_START);
        second.awaitPacket(Opcodes.S_XCHG_START);
        check(trade.IsStarted(), "receiver accepted ordinary request");
        return trade;
    }

    private static void offer(Peer from, Peer to, int item, int quantity) throws Exception {
        from.action(() -> CS_TradeAddItem.handle(from.session, objectId(from.seat, item), quantity));
        check(from.awaitPacket(Opcodes.S_ADD_XCHG)[1] == 0 && to.awaitPacket(Opcodes.S_ADD_XCHG)[1] == 1,
                "both actors see the offer on the correct side");
    }

    private static CompletableFuture<Void> confirm(Peer peer, CountDownLatch barrier) {
        return peer.onOwner(() -> {
            barrier.countDown();
            check(barrier.await(2, TimeUnit.SECONDS), "both owner threads reached confirmation");
            CS_TradeAccept.handle(peer.session);
            return null;
        });
    }

    private static void terminal(Peer first, Peer second, CTrade trade, int status) throws Exception {
        check(first.awaitPacket(Opcodes.S_XCHG_RESULT)[1] == status
                && second.awaitPacket(Opcodes.S_XCHG_RESULT)[1] == status, "terminals match on both actual clients");
        check(CacheD.getInstance().flushAll(6000), "durable replay released both owners");
        check(trade.IsEnded() && CTradeManager.getInstance().FindTrade(trade.GetID()) == null
                && first.pc.GetTrade() == null && second.pc.GetTrade() == null
                && !first.pc.isItemTransferBusy() && !second.pc.isItemTransferBusy(), "no active trade or item gate remains");
    }

    private static void verifyCounts(Peer first, Peer second, int receipts, int... amounts) throws Exception {
        var memory = new ArrayList<Integer>();
        for (Peer peer : new Peer[] {first, second}) {
            for (int slot = 0; slot < 2; slot++)
                memory.add(peer.pc.getInventory().GetByServerID(objectId(peer.seat, slot)).getCount());
        }
        check(memory.equals(Arrays.stream(amounts).boxed().toList()), "live inventory quantities " + memory);
        check(amounts[0] + amounts[2] == INITIAL[0] + INITIAL[2]
                && amounts[1] + amounts[3] == INITIAL[1] + INITIAL[3], "each item class conserved");
        try (var con = db.getConnection(); var query = con.createStatement()) {
            try (var rows = query.executeQuery("SELECT id,char_id,item_id,count FROM character_items ORDER BY id")) {
                for (int i = 0; i < amounts.length; i++) {
                    check(rows.next() && rows.getInt(1) == objectId(i / 2, i % 2)
                            && rows.getInt(2) == CioSyntheticPopulation.actorId(i / 2)
                            && rows.getInt(3) == FIRST_TEMPLATE + i % 2 && rows.getInt(4) == amounts[i],
                            "exact SQL row and quantity at " + i);
                }
                check(!rows.next(), "no extra durable inventory rows");
            }
            try (var rows = query.executeQuery("SELECT kind,owner_a,owner_b FROM cached_transaction_receipts")) {
                int count = 0;
                while (rows.next()) {
                    check("ECONOMY".equals(rows.getString(1))
                            && Math.min(rows.getInt(2), rows.getInt(3)) == first.pc.getId()
                            && Math.max(rows.getInt(2), rows.getInt(3)) == second.pc.getId(), "receipt identifies the exchange");
                    count++;
                }
                check(count == receipts, "exact trade receipt count " + count);
            }
        }
    }

    private static long deadlocks() throws Exception {
        try (var con = db.getConnection(); var query = con.createStatement();
             var rows = query.executeQuery("SHOW GLOBAL STATUS LIKE 'Innodb_deadlocks'")) {
            check(rows.next(), "DB deadlock counter available");
            return rows.getLong(2);
        }
    }

    private static final class Peer implements AutoCloseable {
        final int seat;
        final Socket wire = new Socket();
        final DataInputStream input;
        final CUserSocket session;
        final L1PcInstance pc;
        final SelectorTransportTest.ScalarClient cipher = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());

        Peer(CIOServer server, CioSyntheticPopulation actors, L1V1Map map, int seat) throws Exception {
            this.seat = seat;
            try {
                wire.setSoTimeout(5000);
                wire.connect(server.localAddress(), 5000);
                input = new DataInputStream(wire.getInputStream());
                check(Arrays.equals(readFrame(), Opcodes.S_KEYBYTES), "production initial key");
                session = accepted(server);
                pc = onOwner(() -> {
                    var actor = actors.bind(session, seat);
                    actor.setMap(map);
                    actor.setX(34);
                    actor.setY(34 - seat);
                    actor.setHeading(seat == 0 ? 0 : 4);
                    CWorld.getInstance().addVisibleObject(actor);
                    return actor;
                }).get(5, TimeUnit.SECONDS);
                var items = new MySqlCharactersItemStorage().loadItems(pc.getId());
                action(() -> pc.getInventory().loadItems(items));
            } catch (Exception | Error failure) {
                wire.close();
                throw failure;
            }
        }

        private static CUserSocket accepted(CIOServer server) throws Exception {
            var sockets = CIOServer.class.getDeclaredField("sockets");
            sockets.setAccessible(true);
            synchronized (server) {
                for (Object value : (Set<?>) sockets.get(server)) {
                    if (value instanceof CUserSocket candidate && candidate.isConnected() && candidate.getActiveChar() == null)
                        return candidate;
                }
            }
            throw new AssertionError("accepted fixture socket missing");
        }

        <T> CompletableFuture<T> onOwner(Callable<T> task) {
            var result = new CompletableFuture<T>();
            check(CIOThreadCallback.getInstance().scheduleNetworkTimeout(session.affinityShard(),
                    new CIOThreadCallback.WheelTask() {
                        @Override public boolean isCancelled() { return result.isCancelled(); }
                        @Override public void run() {
                            try { result.complete(task.call()); }
                            catch (Exception | Error error) { result.completeExceptionally(error); }
                        }
                    }, 1), "CIO owns the requested action");
            return result;
        }

        void action(Runnable task) throws Exception {
            onOwner(() -> { task.run(); return null; }).get(5, TimeUnit.SECONDS);
        }

        byte[] awaitPacket(int opcode) throws Exception {
            for (int i = 0; i < 64; i++) {
                byte[] body = cipher.fromServer(readFrame());
                if ((body[0] & 255) == opcode) return body;
            }
            throw new AssertionError("missing trade packet " + opcode);
        }

        private byte[] readFrame() throws Exception {
            int length = input.readUnsignedByte() | input.readUnsignedByte() << 8;
            check(length > 2 && length <= 1024, "fixture frame length " + length);
            byte[] payload = new byte[length - 2];
            input.readFully(payload);
            return payload;
        }

        @Override public void close() throws java.io.IOException { wire.close(); }
    }

    private static int objectId(int seat, int slot) { return FIRST_OBJECT + seat * 2 + slot; }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
