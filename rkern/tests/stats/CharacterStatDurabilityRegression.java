import java.io.DataInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_ReturnStatus;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** Run with CioSyntheticPopulation, an isolated JDBC URL, user, and a Net DDL reference schema. */
public final class CharacterStatDurabilityRegression implements AutoCloseable {
    private static final int CRASH_AFTER_COMMIT = 92;
    private static final int BASE_LEVEL = 51;
    private static final int EXP = ExpTable.getExpByLevel(BASE_LEVEL);
    private static final int EXP_BEFORE_LAST_LEVEL = ExpTable.getExpByLevel(BASE_LEVEL - 1);
    private static final String TITLE = "Unrelated title";
    private static final long RESET_BASE = 13L | 9L << 8 | 11L << 16 | 9L << 24 | 11L << 32 | 13L << 40;
    private static final String PASSWORD = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
    private static final String[] TABLES = {
        "accounts", "characters", "cached_transaction_receipts", "character_items", "character_warehouse",
        "character_elf_warehouse", "clan_warehouse", "character_special_warehouse",
        "character_supplementary_service", "character_quests", "character_level_gifts", "mapids"
    };

    private final CioSyntheticPopulation actors = new CioSyntheticPopulation(4);
    private final Set<CUserSocket> attached = Collections.newSetFromMap(new IdentityHashMap<>());
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private final CIOServer server;

    private record Row(int str, int originalStr, int dex, int points, int reset, int exp) {}
    private record Peer(Socket wire, CUserSocket socket, L1PcInstance pc) implements AutoCloseable {
        @Override public void close() throws java.io.IOException { wire.close(); }
    }

    public static void main(String[] args) throws Exception {
        setup();
        if (args.length == 5 && args[3].equals("crash")) {
            check(args[2].matches("rkern_stat_crash_[a-f0-9]{32}"), "child uses private crash fixture");
            final int seat = Integer.parseInt(args[4]);
            check(seat == 0 || seat == 1, "child fixture seat");
            try (var pool = install(args[0], args[1], args[2], true);
                 var fixture = new CharacterStatDurabilityRegression();
                 var peer = fixture.connect(seat)) {
                fixture.on(peer.socket(), () -> {
                    if (seat == 0) check(peer.pc().onStat("str"), "investment admitted");
                    else {
                        stageReset(peer);
                        finishReset(peer);
                    }
                    return null;
                });
                fixture.await(peer);
                throw new AssertionError("receipt returned without halting child after commit");
            }
        }
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Expected isolated JDBC URL, user and Net DDL reference schema");
        final String schema = "rkern_stat_crash_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], PASSWORD); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema);
            try {
                for (String table : TABLES)
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                admin.setCatalog(schema);
                seed(admin);
                runChild(args[0], args[1], schema, 0);
                runChild(args[0], args[1], schema, 1);
                try (var pool = install(args[0], args[1], schema, false);
                     var fixture = new CharacterStatDurabilityRegression()) {
                    fixture.verifyRestart(admin);
                    fixture.verifyRollback(admin, 2);
                    fixture.verifyRollback(admin, 3);
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS stat investment/reset post-COMMIT crash, deduplication, SQL rollback and retry");
    }

    private static void setup() {
        Config.CACHE_MAP_FILES = true;
        Config.TIME_ZONE = "UTC";
        l1j.server.server.model.gametime.CL2Time.prepare();
        Config.MAX_ONLINE_USERS = 8;
        Config.GMCODE = 9999;
        Config.SUB_GMCODE = 8888;
        CPacketFilter.getInstance().load();
    }

    private CharacterStatDurabilityRegression() throws Exception {
        check(cache.flushAll(5000), "stat receipt schema ready");
        final var address = new InetSocketAddress("127.0.0.1", 0);
        final var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        final int shards = cio.bindNetworkIngress(config);
        server = new CIOServer(address, 8, shards, 8, new CIOBufferPool(8, 32, 8, 8, shards), cio, config);
    }

    private void verifyRestart(Connection admin) throws Exception {
        check(row(admin, 0).equals(new Row(18,17,13,1,0,EXP)), "investment committed before abrupt exit");
        check(row(admin, 1).equals(new Row(14,13,9,1,0,EXP)), "reset committed before abrupt exit");
        for (int seat = 0; seat < 2; seat++) {
            final int owner = CioSyntheticPopulation.actorId(seat);
            final String receipt = receipt(admin, seat);
            check(receipt != null, "single durable receipt");
            CTransaction.persistEconomy(receipt, owner, 0, new CTransaction.Mutation[] { con -> {
                throw new AssertionError("duplicate receipt reapplied a stat mutation");
            }});
            try (var peer = connect(seat)) {
                final boolean investment = seat == 0;
                on(peer.socket(), () -> {
                    check(peer.pc().GetStatStr() == (investment ? 18 : 14) && peer.pc().getBonusStats() == 1,
                            "fresh game actor loads exact permanent stat and spent point");
                    if (investment) check(!peer.pc().onStat("str"), "retry cannot use previously spent sole point");
                    else finishReset(peer);
                    return null;
                });
                await(peer);
                check(receipt.equals(receipt(admin, seat)), "stale retry adds no receipt");
            }
        }
    }

    private void verifyRollback(Connection admin, int seat) throws Exception {
        final boolean investment = seat == 2;
        final Row before = row(admin, seat);
        final int beforeAc = storedAc(admin, seat);
        final Row beforeLive = new Row(before.str(), before.originalStr(), before.dex(), before.points(), before.reset(), EXP);
        try (var peer = connect(seat); var ddl = admin.createStatement()) {
            final var beforeReceipt = on(peer.socket(), () -> new CharacterSnapshot(peer.pc()));
            on(peer.socket(), () -> {
                if (!investment) stageReset(peer);
                final int token = peer.pc().tryReserveItemTransfer();
                check(token != 0, "fixture holds existing operation gate");
                try {
                    if (investment) check(!peer.pc().onStat("dex"), "busy mutation rejected");
                    else finishReset(peer);
                } finally { peer.pc().endItemTransfer(token); }
                checkLiveBefore(peer.pc(), beforeLive);
                return null;
            });
            check(receipt(admin, seat) == null && row(admin, seat).equals(before) && storedAc(admin, seat) == beforeAc,
                    "admission rejection leaves durable state");
            ddl.execute("CREATE TRIGGER fixture_reject_stat BEFORE UPDATE ON characters FOR EACH ROW "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected stat SQL rejection'");
            try {
                attempt(peer, investment);
                await(peer);
                check(receipt(admin, seat) == null && row(admin, seat).equals(before) && storedAc(admin, seat) == beforeAc,
                        "confirmed rollback includes receipt row and character values");
                on(peer.socket(), () -> {
                    checkLiveBefore(peer.pc(), beforeLive);
                    if (!investment) check(peer.pc().rst != null && peer.pc().rst.allocatedLevelPoints() == 1,
                            "failed reset retains the one detached choice for retry");
                    return null;
                });
            } finally { ddl.execute("DROP TRIGGER fixture_reject_stat"); }
            attempt(peer, investment);
            await(peer);
            final Row after = investment ? new Row(17,17,14,1,0,EXP) : new Row(14,13,9,1,0,EXP);
            check(row(admin, seat).equals(after) && receipt(admin, seat) != null
                    && storedAc(admin, seat) == peer.pc().getAc(), "successful retry commits derived AC and stats once");
            on(peer.socket(), () -> {
                checkLiveBefore(peer.pc(), after);
                if (!investment) check(peer.pc().rst == null, "successful reset closed the preview");
                return null;
            });
            // A delayed callback from an older receipt cannot roll back the snapshot fence.
            cache.publishKarmaReceipt(peer.pc().getId(), peer.pc().getCacheDSessionEpoch(),
                    beforeReceipt.characterReceiptRevision);
            check(!cache.saveCharacter(beforeReceipt), "pre-commit full snapshot rejected after receipt publication");
            var loaded = new MySqlCharacterStorage().loadCharacter(peer.pc().getName());
            check(loaded.GetStatStr() == after.str() && loaded.getTitle().equals(TITLE),
                    "fresh load sees only the intended field changes");
            if (investment) {
                check(before.exp() == EXP_BEFORE_LAST_LEVEL && loaded.getLevel() == BASE_LEVEL
                        && loaded.getHighLevel() == BASE_LEVEL
                        && loaded.getBaseMaxHp() == peer.pc().getBaseMaxHp()
                        && loaded.getBaseMaxMp() == peer.pc().getBaseMaxMp()
                        && loaded.encodeHpMpGrowth().equals(peer.pc().encodeHpMpGrowth()),
                        "first qualifying level, vitals and growth persist without waiting for an autosave");
            }
        }
    }

    private void attempt(Peer peer, boolean investment) throws Exception {
        on(peer.socket(), () -> {
            if (investment) check(peer.pc().onStat("dex"), "valid investment accepted for durable processing");
            else finishReset(peer);
            return null;
        });
    }

    private void await(Peer peer) throws Exception {
        check(cache.flushDbOwner(peer.pc().getId(), 7000), "durable owner queue drained");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((peer.pc().hasReservedItemTransfer() || peer.pc().hasItemTransferContinuation())
                && System.nanoTime() < deadline) Thread.sleep(1);
        on(peer.socket(), () -> {
            check(!peer.pc().hasReservedItemTransfer() && !peer.pc().hasItemTransferContinuation(),
                    "owner gate released after terminal processing");
            return null;
        });
    }

    private static void checkLiveBefore(L1PcInstance pc, Row expected) {
        check(pc.GetStatStr() == expected.str() && pc.GetOriginalStatStr() == expected.originalStr()
                && pc.GetStatDex() == expected.dex()
                && pc.getBonusStats() == expected.points() && pc.getReturnStat() == expected.reset()
                && pc.getExp() == expected.exp(), "live state matches expected durable generation");
    }

    private static void stageReset(Peer peer) {
        CS_ReturnStatus.handle(peer.socket(), 1, 0, 0, RESET_BASE);
        for (int i = 1; i < BASE_LEVEL; i++) CS_ReturnStatus.handle(peer.socket(), 2, 0, 0, 0);
        check(peer.pc().rst != null && peer.pc().rst.level == BASE_LEVEL
                && peer.pc().rst.allocatedLevelPoints() == 0, "regular reset built detached level history");
    }

    private static void finishReset(Peer peer) {
        CS_ReturnStatus.handle(peer.socket(), 2, 8, 1, 0);
    }

    private Peer connect(int seat) throws Exception {
        final L1PcInstance source = new MySqlCharacterStorage().loadCharacter(CioSyntheticPopulation.characterName(seat));
        check(source != null && source.getLevel() == (seat == 2 ? BASE_LEVEL - 1 : BASE_LEVEL),
                "fixture character loaded from isolated DB");
        final Socket wire = new Socket();
        try {
            wire.connect(server.localAddress(), 4000);
            wire.setSoTimeout(4000);
            var input = new DataInputStream(wire.getInputStream());
            int keyLength = input.readUnsignedByte() | input.readUnsignedByte() << 8;
            check(keyLength == Opcodes.S_KEYBYTES.length + 2
                    && Arrays.equals(input.readNBytes(keyLength - 2), Opcodes.S_KEYBYTES), "ready TCP handshake");
            final CUserSocket socket;
            synchronized (server) {
                var sockets = CIOServer.class.getDeclaredField("sockets");
                sockets.setAccessible(true);
                socket = ((Set<?>) sockets.get(server)).stream().filter(CUserSocket.class::isInstance)
                        .map(CUserSocket.class::cast).filter(s -> s.isConnected() && !attached.contains(s))
                        .findFirst().orElseThrow();
                attached.add(socket);
            }
            final L1PcInstance pc = on(socket, () -> {
                final L1PcInstance actor = actors.bind(socket, seat);
                actor.setType(source.getType());
                actor.setClassId(source.getClassId());
                actor.setExp(source.getExp());
                actor.setLevel(source.getLevel());
                actor.setHighLevel(source.getHighLevel());
                actor.ResetPrimaryStats(source.GetOriginalStatStr(), source.GetOriginalStatDex(), source.GetOriginalStatCon(),
                        source.GetOriginalStatInt(), source.GetOriginalStatWis(), source.GetOriginalStatCha());
                actor.SetStatStr(source.GetStatStr());
                actor.SetStatDex(source.GetStatDex());
                actor.SetStatCon(source.GetStatCon());
                actor.SetStatInt(source.GetStatInt());
                actor.SetStatWis(source.GetStatWis());
                actor.SetStatCha(source.GetStatCha());
                actor.setBonusStats(source.getBonusStats());
                actor.setElixirStats(source.getElixirStats());
                actor.addBaseMaxHp((short) source.getBaseMaxHp());
                actor.addBaseMaxMp(source.getBaseMaxMp());
                actor.restoreHpMpGrowth(source.encodeHpMpGrowth());
                actor.setCurrentHp(source.getCurrentHp());
                actor.setCurrentMp(source.getCurrentMp());
                actor.setReturnStat(source.getReturnStat());
                if (seat == 2) {
                    actor.applyHpMpLevelTransition(BASE_LEVEL - 1, BASE_LEVEL);
                    actor.setExp(EXP);
                    actor.setLevel(BASE_LEVEL);
                    actor.setHighLevel(BASE_LEVEL);
                }
                return actor;
            });
            return new Peer(wire, socket, pc);
        } catch (Exception | Error failure) {
            wire.close();
            throw failure;
        }
    }

    private <T> T on(CUserSocket socket, Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        cio.completeOnShard(socket.affinityShard(), () -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }, failure -> { if (failure != null) result.completeExceptionally(failure); });
        return result.get(7, TimeUnit.SECONDS);
    }

    private static void seed(Connection admin) throws SQLException {
        try (var sql = admin.createStatement()) {
            for (int seat = 0; seat < 4; seat++) {
                final String name = CioSyntheticPopulation.characterName(seat);
                final int level = seat == 2 ? BASE_LEVEL - 1 : BASE_LEVEL;
                final int exp = seat == 2 ? EXP_BEFORE_LAST_LEVEL : EXP;
                sql.executeUpdate("INSERT INTO accounts(id,login,charslot,gamepassword,Shop_open_count) VALUES("
                        + CioSyntheticPopulation.accountId(seat) + ",'" + name + "',8,0,0)");
                sql.executeUpdate("INSERT INTO characters(objid,account_name,char_name,Clanname,notes,Title,ReturnStat,level,HighLevel,"
                        + "Exp,Type,Class,MaxHp,CurHp,MaxMp,CurMp,BaseStr,BaseCon,BaseDex,BaseIntel,BaseWis,BaseCha,"
                        + "Str,Con,Dex,Intel,Wis,Cha,BonusStatus,ElixirStatus,hp_mp_growth) VALUES("
                        + CioSyntheticPopulation.actorId(seat) + ",'" + name + "','" + name + "','','','" + TITLE + "',"
                        + (seat % 2 == 0 ? 0 : EXP) + "," + level + "," + level + "," + exp
                        + ",0,0,280,90,100,30,17,16,13,12,14,13,17,16,13,12,14,13,0,0,'1:" + level + "')");
            }
        }
    }

    private static Row row(Connection con, int seat) throws SQLException {
        try (var sql = con.createStatement(); var rows = sql.executeQuery(
                "SELECT Str,BaseStr,Dex,BonusStatus,ReturnStat,Exp FROM characters WHERE objid=" + CioSyntheticPopulation.actorId(seat))) {
            check(rows.next(), "read one isolated fixture character");
            return new Row(rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getInt(4), rows.getInt(5), rows.getInt(6));
        }
    }

    private static int storedAc(Connection con, int seat) throws SQLException {
        try (var sql = con.createStatement(); var rows = sql.executeQuery(
                "SELECT Ac FROM characters WHERE objid=" + CioSyntheticPopulation.actorId(seat))) {
            check(rows.next(), "read offline display AC");
            return rows.getInt(1);
        }
    }

    private static String receipt(Connection con, int seat) throws SQLException {
        try (var sql = con.createStatement(); var rows = sql.executeQuery(
                "SELECT tx_id FROM cached_transaction_receipts WHERE owner_a=" + CioSyntheticPopulation.actorId(seat))) {
            if (!rows.next()) return null;
            final String id = rows.getString(1);
            check(!rows.next(), "only one receipt for fixture stat mutation");
            return id;
        }
    }

    private static void runChild(String jdbc, String user, String schema, int seat) throws Exception {
        var args = new ArrayList<>(Arrays.asList(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-preview", "-XX:ActiveProcessorCount=2", "-Xmx256m"));
        final String socket = System.getProperty("rkern.net.fixture.socket");
        if (socket != null) args.add("-Drkern.net.fixture.socket=" + socket);
        args.addAll(Arrays.asList("-cp", System.getProperty("java.class.path"),
                CharacterStatDurabilityRegression.class.getName(), jdbc, user, schema, "crash", Integer.toString(seat)));
        final Process child = new ProcessBuilder(args).inheritIO().start();
        try {
            check(child.waitFor(20, TimeUnit.SECONDS) && child.exitValue() == CRASH_AFTER_COMMIT,
                    "child exited at its confirmed SQL commit boundary for seat " + seat);
        } finally { child.destroyForcibly(); }
    }

    private static HikariDataSource install(String jdbc, String user, String schema, boolean halt) throws Exception {
        var config = new HikariConfig();
        config.setJdbcUrl(jdbc);
        config.setUsername(user);
        config.setPassword(PASSWORD);
        config.setCatalog(schema);
        config.setMaximumPoolSize(5);
        var pool = halt ? new HikariDataSource(config) {
            @Override public Connection getConnection() throws SQLException {
                return haltOnStatCommit(super.getConnection());
            }
        } : new HikariDataSource(config);
        var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        field.setAccessible(true);
        field.set(null, pool);
        return pool;
    }

    private static Connection haltOnStatCommit(Connection db) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                new java.lang.reflect.InvocationHandler() {
                    private boolean receipt, characterWrite;
                    @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        final String call = method.getName();
                        if (call.equals("prepareStatement")) {
                            final String sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("insert into cached_transaction_receipts")) receipt = true;
                            if (sql.startsWith("update characters set ")) characterWrite = true;
                        }
                        try {
                            final Object answer = method.invoke(db, args);
                            if ((call.equals("commit") || call.equals("setAutoCommit") && Boolean.TRUE.equals(args[0]))
                                    && receipt && characterWrite)
                                Runtime.getRuntime().halt(CRASH_AFTER_COMMIT);
                            return answer;
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    }
                });
    }

    @Override public void close() throws Exception {
        try {
            server.close();
            actors.assertDrained(server);
        } finally {
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session cleanup drained");
            boolean drained = cache.drainBeforeOwnerShutdown(6000);
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO shutdown");
            check(drained && cache.shutdown(5000), "CacheD shutdown");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
