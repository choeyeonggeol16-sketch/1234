import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.basic.buff.CompanionBuffInfo;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.friendship.CompanionFriendShipBonus;
import l1j.server.server.model.character.companion.basic.potion.CompanionPotionInfo;
import l1j.server.server.model.item.function.StatReset;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_ItemUse;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
import l1j.server.server.templates.L1EtcItem;

/** Actual item-use/NPC service entry and MariaDB with TCP-bound synthetic players.
 * Compile with CioSyntheticPopulation. Pass isolated JDBC URL, user and reference
 * schema. Copies DDL only and drops the unique synthetic schema on exit.
 */
public final class StatResetCandleRegression {
    private static final String[] TABLES = {
        "accounts", "characters", "cached_transaction_receipts", "character_items", "character_warehouse",
        "clan_warehouse", "character_elf_warehouse", "character_special_warehouse",
        "character_supplementary_service", "character_quests", "character_level_gifts", "mapids", "skills",
        "tb_mjraid_creator_items", "companion_class", "companion_friend_ship_bonus", "companion_buff",
        "companion_potion_info", "passive_book_mapped", "tb_dungeon_time_potion", "tb_act_listener_linkage_item",
        "tb_designate_teleport_scroll", "pride_items", "castle", "tb_dungeon_time_information", "tb_mjexpampsystem", "etcitem", "weapon", "armor"
    };
    private static final int CANDLE = 200000, RESET_MAP = 5166, SEATS = 4;
    private final HikariDataSource db;
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private final CioSyntheticPopulation actors = new CioSyntheticPopulation(SEATS);
    private final Set<CUserSocket> used = Collections.newSetFromMap(new IdentityHashMap<>());

    private record Peer(Socket wire, CUserSocket socket, L1PcInstance pc, L1ItemInstance candle)
            implements AutoCloseable {
        @Override public void close() throws java.io.IOException { wire.close(); }
    }

    private record Stored(int exp, int marker, int count, int receipts) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Expected isolated JDBC URL, user and reference schema");
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final String schema = "rkern_candle_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema);
            try {
                for (String table : TABLES)
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setCatalog(schema);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setMaximumPoolSize(5);
                try (var db = new HikariDataSource(config)) {
                    field(DatabaseFactory.class, "DATABASE_POOL").set(null, db);
                    try { new StatResetCandleRegression(db).run(); }
                    finally { field(DatabaseFactory.class, "DATABASE_POOL").set(null, null); }
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS stat reset candle: atomic marker/debit, live replay, policy, duplicate and rollback");
    }

    private StatResetCandleRegression(HikariDataSource db) { this.db = db; }

    private void run() throws Exception {
        Config.CACHE_MAP_FILES = true;
        Config.MAX_ONLINE_USERS = 8;
        for (Runnable load : List.<Runnable>of(CompanionClassInfo::do_load, CompanionFriendShipBonus::do_load,
                CompanionBuffInfo::do_load, CompanionPotionInfo::do_load)) load.run();
        CPacketFilter.getInstance().load();
        L1WorldMap.getInstance().addMap(map(RESET_MAP, 32704, 32792, 96, true));
        require(cache.flushAll(5_000), "CacheD probe drained");
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        int shards = cio.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(16, 32, 16, 16, shards);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, cio, config)) {
            try (var direct = peer(server, 0, 51, true); var npc = peer(server, 1, 55, false);
                 var denied = peer(server, 2, 50, true); var failed = peer(server, 3, 55, true)) {
                actors.assertReady(server);
                verifyDenied(denied);
                verifyRollback(failed);
                verifyDurableBeforeOwnerReplay(direct);
                checkOpen(direct, false);
                checkStored(direct, 1, 1, true);
                on(direct.socket, () -> StatReset.useCandle(direct.pc, direct.candle));
                require(cache.flushAll(5_000), "repeat drain");
                checkStored(direct, 1, 1, true);
                on(npc.socket, () -> StatReset.useNpcCandle(npc.pc));
                checkOpen(npc, true);
                checkStored(npc, 1, 2, true);
            }
            server.close();
            actors.assertDrained(server);
        } finally {
            CSessionLifecycle.stopAccepting();
            require(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session releases drained");
            final boolean dbDrained = cache.drainBeforeOwnerShutdown(5_000);
            cio.shutdown();
            require(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO owners stopped");
            require(dbDrained && cache.shutdown(5_000), "CacheD stopped");
        }
    }

    private void verifyDenied(Peer peer) throws Exception {
        on(peer.socket, () -> click(peer));
        require(cache.flushAll(5_000), "level rejection drained");
        checkStored(peer, 2, 0, false);
        on(peer.socket, () -> {
            level(peer.pc, 54);
            StatReset.useNpcCandle(peer.pc);
            peer.pc.setMap(map(1, 32, 32, 8, false));
            click(peer);
            peer.pc.setMap(map(1, 32, 32, 8, true));
            peer.pc.setHighLevel(55);
            click(peer);
        });
        require(cache.flushAll(5_000), "policy rejections drained");
        var stored = stored(peer);
        require(stored.marker == 0 && stored.count == 2 && stored.receipts == 0,
                "NPC level, unsafe map and de-level guard preserve candle and marker");
    }

    private void verifyRollback(Peer peer) throws Exception {
        try (var con = db.getConnection(); var sql = con.createStatement()) {
            sql.execute("CREATE TRIGGER fixture_fail_marker BEFORE UPDATE ON characters FOR EACH ROW "
                    + "BEGIN IF OLD.objid=" + peer.pc.getId() + " AND NEW.ReturnStat<>OLD.ReturnStat THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rejects reset marker'; END IF; END");
            try {
                on(peer.socket, () -> click(peer));
                require(cache.flushAll(5_000), "failed transaction released its durable reservation");
                checkStored(peer, 2, 0, false);
                on(peer.socket, () -> require(peer.pc.getReturnStat() == 0 && peer.candle.getCount() == 2
                        && peer.pc.get_teleport_map() == 0, "rollback publishes neither item debit nor reset"));
            } finally {
                sql.execute("DROP TRIGGER fixture_fail_marker");
            }
        }
    }

    private void verifyDurableBeforeOwnerReplay(Peer peer) throws Exception {
        CountDownLatch submitted = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<Void> blocked = submit(peer.socket, () -> {
            try {
                require(peer.pc.getMap().isSafetyZone(peer.pc.getLocation()), "direct reset fixture was in a safe zone");
                require(peer.pc.getLevel() == 51 && peer.pc.getHighLevel() == 51, "direct reset fixture level");
                require(peer.pc.getCurrentHp() > 0, "direct reset fixture is alive");
                require(peer.pc.getInventory().GetByServerID(peer.candle.getId()) == peer.candle,
                        "direct reset fixture owns the candle");
                click(peer);
            } finally {
                submitted.countDown();
            }
            try { require(release.await(4, TimeUnit.SECONDS), "fixture released owner pause"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        });
        try {
            require(submitted.await(2, TimeUnit.SECONDS), "item click submitted from CIO owner");
            if (blocked.isDone()) blocked.get();
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            Stored durable;
            do {
                durable = stored(peer);
                if (durable.marker == peer.pc.getExp() && durable.count == 1) break;
                Thread.sleep(5);
            } while (System.nanoTime() < until);
            require(durable.marker == peer.pc.getExp() && durable.count == 1 && durable.receipts == 1,
                    "durable reset can be recovered before the CIO replays live state: " + durable);
            require(peer.pc.getReturnStat() == 0 && peer.candle.getCount() == 2,
                    "no live mutation before the original owner receives replay");
        } finally {
            release.countDown();
        }
        blocked.get(4, TimeUnit.SECONDS);
    }

    private void checkOpen(Peer peer, boolean npc) throws Exception {
        require(cache.flushAll(5_000), "durable reset receipt and replay drained");
        final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        do {
            final boolean[] ready = {false};
            on(peer.socket, () -> ready[0] = peer.pc.getMapId() == RESET_MAP && !peer.pc.get_teleport());
            if (ready[0]) break;
            Thread.sleep(10);
        } while (System.nanoTime() < until);
        on(peer.socket, () -> {
            require(peer.pc.getReturnStat() == peer.pc.getExp() && peer.candle.getCount() == 1,
                    "reset marker and candle reflected in live owner");
            final int x = peer.pc.getX(), y = peer.pc.getY();
            require(peer.pc.getMapId() == RESET_MAP && !peer.pc.get_teleport() && (npc
                    ? Math.abs(x - 32723) <= 10 && Math.abs(y - 32815) <= 10 && peer.pc.getHeading() == 3
                    : x >= 32723 && x < 32733 && y >= 32851 && y < 32861 && peer.pc.getHeading() == 3),
                    "preserved physical entry destination and heading");
        });
    }

    private void checkStored(Peer peer, int count, int receipts, boolean pending) throws Exception {
        Stored value = stored(peer);
        require(value.exp == peer.pc.getExp() && value.marker == (pending ? value.exp : 0)
                && value.count == count && value.receipts == receipts, "coherent SQL character/candle: " + value);
    }

    private Stored stored(Peer peer) throws Exception {
        try (var con = db.getConnection(); var ps = con.prepareStatement(
                "SELECT c.Exp,c.ReturnStat,i.count,(SELECT COUNT(*) FROM cached_transaction_receipts) "
                        + "FROM characters c JOIN character_items i ON c.objid=i.char_id WHERE i.id=?")) {
            ps.setInt(1, peer.candle.getId());
            try (var row = ps.executeQuery()) {
                require(row.next(), "synthetic character and item rows exist");
                return new Stored(row.getInt(1), row.getInt(2), row.getInt(3), row.getInt(4));
            }
        }
    }

    private Peer peer(CIOServer server, int seat, int atLevel, boolean safe) throws Exception {
        final Socket wire = new Socket();
        try {
            wire.connect(server.localAddress(), 4_000);
            wire.setSoTimeout(4_000);
            var input = new DataInputStream(wire.getInputStream());
            int length = input.readUnsignedByte() | input.readUnsignedByte() << 8;
            require(length == Opcodes.S_KEYBYTES.length + 2
                    && Arrays.equals(input.readNBytes(length - 2), Opcodes.S_KEYBYTES), "TCP key received");
            final CUserSocket socket;
            synchronized (server) {
                socket = ((Set<?>) field(CIOServer.class, "sockets").get(server)).stream()
                        .filter(CUserSocket.class::isInstance).map(CUserSocket.class::cast)
                        .filter(candidate -> candidate.isConnected() && !used.contains(candidate)).findFirst().orElseThrow();
                used.add(socket);
            }
            int exp = ExpTable.getExpByLevel(atLevel);
            try (var con = db.getConnection(); var ps = con.prepareStatement(
                    "INSERT INTO characters(objid,char_name,account_name,Clanname,notes,ReturnStat,level,HighLevel,Exp) "
                            + "VALUES(?,?,?,'','',0,?,?,?)")) {
                ps.setInt(1, CioSyntheticPopulation.actorId(seat));
                ps.setString(2, CioSyntheticPopulation.characterName(seat));
                ps.setString(3, CioSyntheticPopulation.login(seat));
                ps.setInt(4, atLevel);
                ps.setInt(5, atLevel);
                ps.setInt(6, exp);
                ps.executeUpdate();
            }
            var template = new L1EtcItem();
            template.setItemId(CANDLE);
            template.setName("fixture reset candle");
            template.set_stackable(true);
            template.setType(2);
            template.setUseType(0);
            var item = new L1ItemInstance(template, 2);
            item.setId(190000 + seat);
            item.getLastStatus().updateAll();
            MySqlCharactersItemStorage.storeSnapshot(MySqlCharactersItemStorage.ItemSnapshot.capture(
                    item, CioSyntheticPopulation.actorId(seat)));
            final var published = new CompletableFuture<L1PcInstance>();
            on(socket, () -> {
                L1PcInstance player = actors.bind(socket, seat);
                level(player, atLevel);
                player.setClassId(L1PcInstance.CLASSID_PRINCE);
                player.setType(0);
                player.setMap(map(1, 32, 32, 8, safe));
                player.setX(34);
                player.setY(34);
                player.setHeading(3);
                player.setMaxHp(30);
                player.setCurrentHp(10);
                player.getInventory().loadItems(List.of(item));
                cache.registerLoadedItemOwner(player.getId(), item.getId());
                published.complete(player);
            });
            return new Peer(wire, socket, published.get(1, TimeUnit.SECONDS), item);
        } catch (Exception | Error failure) {
            wire.close();
            throw failure;
        }
    }

    private static void level(L1PcInstance pc, int level) {
        pc.setExp(ExpTable.getExpByLevel(level));
        pc.setLevel(level);
        pc.setHighLevel(level);
    }

    private static L1V1Map map(int id, int x, int y, int width, boolean safe) {
        byte[][] tiles = new byte[width][width];
        if (safe) for (byte[] line : tiles) Arrays.fill(line, (byte) 0x10);
        return new L1V1Map(id, tiles, x, y, false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static void click(Peer peer) {
        CS_ItemUse.handle(peer.socket, peer.candle.getId(), 0, 0, 0, "", 0);
    }

    private CompletableFuture<Void> submit(CUserSocket socket, Runnable action) {
        var result = new CompletableFuture<Void>();
        cio.completeOnShard(socket.affinityShard(), action, failure -> {
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        });
        return result;
    }

    private void on(CUserSocket socket, Runnable action) throws Exception {
        submit(socket, action).get(5, TimeUnit.SECONDS);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
