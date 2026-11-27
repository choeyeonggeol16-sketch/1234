package l1j.server.server.model.item.function;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariDataSource;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.function.ElixirStatTransactions.Stat;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.CharacterStorage.LoginVitals;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** Requires an isolated MariaDB/MySQL instance and a ready-to-import Net schema as a DDL template. */
public final class CharacterStatStorageRegression {
    private static final int OWNER = 117731;
    private static final String NAME = "StatStorageFixture";
    private static final String[] TABLES = {"cached_transaction_receipts", "character_items",
            "character_warehouse", "clan_warehouse", "character_elf_warehouse",
            "character_special_warehouse", "character_supplementary_service", "characters",
            "character_quests", "character_level_gifts", "mapids"};
    private static final String PASSWORD = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");

    public static void main(String[] args) throws Exception {
        if (args.length == 5 && args[3].equals("crash")) {
            check(args[2].matches("rkern_stat_store_[a-f0-9]{32}"), "private crash fixture");
            try (var db = install(args[0], args[1], args[2])) {
                consume(args[4], Stat.STR, 16, 0);
                Runtime.getRuntime().halt(91); // committed, process exits before game-owner publication
            }
        }
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("isolated JDBC URL, user and DDL reference schema required");
        String schema = "rkern_stat_store_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], PASSWORD); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema);
            try {
                for (String table : TABLES)
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                admin.setCatalog(schema);
                ddl.execute("CREATE TABLE consumables(units INT NOT NULL) ENGINE=InnoDB");
                ddl.execute("INSERT INTO consumables VALUES(6)");
                ddl.execute("INSERT INTO characters(account_name,objid,char_name,Clanname,notes,ReturnStat,level,HighLevel,"
                        + "Exp,Type,Class,MaxHp,CurHp,MaxMp,CurMp,BaseStr,BaseCon,BaseDex,BaseIntel,BaseWis,BaseCha,"
                        + "Str,Con,Dex,Intel,Wis,Cha,BonusStatus,ElixirStatus) "
                        + "VALUES('statfixture'," + OWNER + ",'" + NAME + "','','',0,80,80,"
                        + ExpTable.getExpByLevel(80) + ",3,734,500,900,300,700,10,11,12,13,14,15,16,17,18,19,20,21,3,0)");
                try (var pool = install(args[0], args[1], schema)) {
                    run(admin, args[0], args[1], schema);
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS six elixir receipts, rollback, abrupt pre-replay exit, duplicate replay, stale snapshots and login vitals");
    }

    private static HikariDataSource install(String url, String user, String schema) throws Exception {
        var pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(PASSWORD);
        pool.setCatalog(schema);
        pool.setMaximumPoolSize(3);
        var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        field.setAccessible(true);
        field.set(null, pool);
        return pool;
    }

    private static void run(Connection admin, String url, String user, String schema) throws Exception {
        Config.CACHE_MAP_FILES = true;
        Config.GMCODE = 9999;
        Config.SUB_GMCODE = 8888;
        var cache = CacheD.getInstance();
        check(cache.flushAll(5_000L), "schema ready");
        var storage = new MySqlCharacterStorage();
        var vitals = new LoginVitals();
        var pc = storage.loadCharacter(NAME, vitals);
        check(pc != null && pc.getLevel() == 80 && pc.getHighLevel() == 80 && pc.getBonusStats() == 3,
                "initial level and bonus");
        int hp = pc.getCurrentHp(), mp = pc.getCurrentMp();
        check(hp < 900 && mp < 700, "initial detached caps");
        pc.addMaxHp(500);
        pc.addMaxMp(500);
        pc.setCurrentHp(hp - 9);
        pc.setCurrentMp(mp - 7);
        vitals.restore(pc);
        vitals.restore(pc);
        check(pc.getCurrentHp() == 891 && pc.getCurrentMp() == 693, "restore once, retain intervening damage and spell cost (hp=" + pc.getCurrentHp() + ", mp=" + pc.getCurrentMp() + ")");
        var smaller = new LoginVitals();
        var partial = storage.loadCharacter(NAME, smaller);
        int availableHp = partial.getMaxHp();
        partial.addMaxHp(20);
        smaller.restore(partial);
        check(partial.getCurrentHp() == availableHp + 20 && partial.getCurrentMp() == partial.getMaxMp(),
                "missing buff respects new effective maxima");
        var dead = new LoginVitals();
        partial = storage.loadCharacter(NAME, dead);
        partial.addMaxHp(500);
        partial.setCurrentHp(0);
        dead.restore(partial);
        check(partial.getCurrentHp() == 0, "world-entry death is retained");

        String tx = UUID.randomUUID().toString();
        var invocation = new java.util.ArrayList<String>();
        invocation.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        invocation.add("--enable-preview");
        String socket = System.getProperty("rkern.net.fixture.socket");
        if (socket != null) invocation.add("-Drkern.net.fixture.socket=" + socket);
        invocation.addAll(Arrays.asList("-cp", System.getProperty("java.class.path"),
                CharacterStatStorageRegression.class.getName(), url, user, schema, "crash", tx));
        var child = new ProcessBuilder(invocation).inheritIO().start();
        try {
            check(child.waitFor(20, TimeUnit.SECONDS) && child.exitValue() == 91, "child exited after DB commit");
        } finally { child.destroyForcibly(); }
        consume(tx, Stat.STR, 16, 0);
        var fresh = storage.loadCharacter(NAME);
        check(Stat.STR.value(fresh) == 17 && fresh.getElixirStats() == 1 && scalar(admin, "SELECT units FROM consumables") == 5,
                "fresh actor loads durable result once after process exit");
        fresh.setCacheDSessionEpoch(cache.registerLoadedCharacterSession(fresh.getAccountName(), NAME, OWNER));

        for (int i = 1; i < 6; i++) {
            final Stat stat = Stat.values()[i];
            int previous = stat.value(fresh);
            check(previous == 16 + i, "selected SQL column");
            if (i == 1) {
                try {
                    consume(UUID.randomUUID().toString(), stat, previous + 1, i);
                    throw new AssertionError("missing preimage failure");
                } catch (SQLException expected) {
                    check(scalar(admin, "SELECT units FROM consumables") == 5, "rollback includes consumable");
                }
            }
            try (var executor = Executors.newSingleThreadExecutor()) {
                var captured = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                var delayed = executor.submit(() -> {
                    var old = new CharacterSnapshot(fresh);
                    captured.countDown();
                    check(release.await(6, TimeUnit.SECONDS), "allow delayed save");
                    return cache.saveCharacter(old);
                });
                try {
                    check(captured.await(4, TimeUnit.SECONDS), "pre-receipt snapshot captured");
                    consume(UUID.randomUUID().toString(), stat, previous, i);
                    ElixirStatTransactions.applyCommittedStat(fresh, stat, previous, i);
                } finally { release.countDown(); }
                check(!delayed.get(4, TimeUnit.SECONDS), "stale snapshot rejected after owner publication");
            }
            if (stat == Stat.DEX) check(scalar(admin, "SELECT Ac FROM characters") == fresh.getAc(),
                    "DEX elixir stored final AC in its own transaction");
            check(cache.saveCharacter(new CharacterSnapshot(fresh)) && cache.flushCharacter(NAME, 5_000L),
                    "fresh snapshot persisted");
            var restored = storage.loadCharacter(NAME);
            check(stat.value(restored) == previous + 1 && restored.getElixirStats() == i + 1,
                    "SQL restored stat and count together");
        }
        L1PcInstance end = storage.loadCharacter(NAME);
        check(Arrays.equals(new int[]{end.GetOriginalStatStr(), end.GetOriginalStatCon(), end.GetOriginalStatDex(),
                end.GetOriginalStatInt(), end.GetOriginalStatWis(), end.GetOriginalStatCha()},
                new int[]{10,11,12,13,14,15}), "all originals survived every full save");
        for (Stat stat : Stat.values()) check(stat.value(end) == 17 + stat.ordinal(), "six pure values");
        check(end.getBonusStats() == 3 && end.getElixirStats() == 6 && end.getLevel() == 80
                && scalar(admin, "SELECT units FROM consumables") == 0
                && scalar(admin, "SELECT COUNT(*) FROM cached_transaction_receipts") == 6, "final conservation");
        // The gate, stale snapshot and reload assertions for ordinary investment
        // now run in CreatureStatWireRegression durable and CharacterStatDurabilityRegression.
        check(cache.shutdown(5_000L), "DB worker shutdown");
    }

    private static void consume(String tx, Stat stat, int original, int count) throws SQLException {
        int ac = stat == Stat.DEX ? new MySqlCharacterStorage().loadCharacter(NAME).projectEffectiveAc(original + 1, 80) : 0;
        CTransaction.persistEconomy(tx, OWNER, 0, new CTransaction.Mutation[]{con -> {
            try (var sql = con.createStatement()) {
                if (sql.executeUpdate("UPDATE consumables SET units=units-1 WHERE units>0") != 1)
                    throw new SQLException("no consumable");
            }
        }, con -> stat.persist(con, OWNER, 80, original, count, ac)});
    }

    private static int scalar(Connection con, String query) throws SQLException {
        try (var sql = con.createStatement(); var rows = sql.executeQuery(query)) {
            check(rows.next(), "scalar result");
            return rows.getInt(1);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
