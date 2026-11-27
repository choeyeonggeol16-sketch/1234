package l1j.server.server.model;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import l1j.server.DatabaseFactory;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CS_Craft;
import l1j.server.server.storage.CacheD;

/** Uses a disposable schema with the production key layout on the supplied test server. */
public final class GameplayIndexStorageRegression {
    public static void main(String[] args) throws Exception {
        WorldShapeRegression.installMapDefinitions();
        String schema = "rkern_gameplay_" + UUID.randomUUID().toString().replace("-", "");
        var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        field.setAccessible(true);
        try (var admin = DriverManager.getConnection(args[0], args[1], "")) {
            sql(admin, "CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                tables(admin);
                var cfg = new HikariConfig();
                cfg.setJdbcUrl(args[0]);
                cfg.setUsername(args[1]);
                cfg.setConnectionInitSql("USE " + schema);
                cfg.setMaximumPoolSize(4);
                cfg.setMinimumIdle(0);
                try (var pool = new HikariDataSource(cfg)) {
                    field.set(null, pool);
                    try {
                        verifyNames(admin);
                        verifyExclude(admin);
                        verifyBlessings(admin);
                    } finally {
                        CacheD.getInstance().shutdown(5000);
                        field.set(null, null);
                    }
                }
            } finally { sql(admin, "DROP DATABASE " + schema); }
        }
        System.out.println("PASS MariaDB offline name index, rename, durable block, blessing expiry retry and stale identity");
    }

    private static void tables(Connection db) throws Exception {
        sql(db, "CREATE TABLE characters (objid INT PRIMARY KEY, char_name VARCHAR(45) "
                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, KEY(char_name)) ENGINE=InnoDB");
        sql(db, "INSERT INTO characters VALUES (1,'Invoker'),(2,'Mixed'),(3,'Case'),(4,'case'),(5,'한글'),(6,'İ')");
        sql(db, "CREATE TABLE character_exclude (char_id INT, type INT, exclude_id INT, "
                + "exclude_name VARCHAR(45), PRIMARY KEY(char_id,type,exclude_id)) ENGINE=InnoDB");
        sql(db, "CREATE TABLE clan_data (clan_id INT PRIMARY KEY, bless INT, attack INT, defence INT, "
                + "pvpattack INT, pvpdefence INT) ENGINE=InnoDB");
        sql(db, "INSERT INTO clan_data VALUES (77,2,12,1,27,34),(78,1,1,12,27,34)");
    }

    private static void verifyNames(Connection db) throws Exception {
        var names = CharacterTable.getInstance();
        names.loadAllCharName();
        check(names.getCharName("mixed") == null, "exact login lookup preserved");
        check(names.getUniqueCharNameIgnoringCase("mIXed").getId() == 2, "offline case-folded identity");
        check(names.getUniqueCharNameIgnoringCase("한글").getId() == 5
                && names.getUniqueCharNameIgnoringCase("i").getId() == 6, "Korean and Java Unicode case equality");
        check(names.getCharName("Case").getId() == 3 && names.getUniqueCharNameIgnoringCase("CASE") == null,
                "ambiguity cannot select a target");
        check(!names.renameCharacter(2, "other", "Renamed"), "wrong preimage rejected");
        check(!names.renameCharacter(2, "Mixed", "Case"), "occupied name rejected");
        check(names.renameCharacter(2, "Mixed", "Renamed"), "rename committed");
        check(names.getUniqueCharNameIgnoringCase("mixed") == null
                && names.getUniqueCharNameIgnoringCase("RENAMED") == names.getCharNameById(2), "rename index transition");
        sql(db, "INSERT INTO characters VALUES (7,'Renamed')");
        try {
            names.loadAllCharName();
            throw new AssertionError("duplicate exact rows published");
        } catch (IllegalStateException expected) {
            check(names.getCharNameById(7) == null && names.getCharNameById(2) != null, "failed reload preserves names");
        }
        sql(db, "DELETE FROM characters WHERE objid=7");
        sql(db, "DELETE FROM characters WHERE objid=4");
        names.loadAllCharName();
        check(names.getUniqueCharNameIgnoringCase("CASE").getId() == 3, "reload resolves retired collision");
    }

    private static void verifyExclude(Connection db) throws Exception {
        var pc = new L1PcInstance();
        pc.setId(1);
        pc.setName("Invoker");
        var lists = SpamTable.getInstance().getExcludeTable(1);
        var handle = CS_Craft.class.getDeclaredMethod("handleExclude", L1PcInstance.class, int.class, int.class, String.class);
        handle.setAccessible(true);
        handle.invoke(null, pc, 1, 0, "iNVOKER");
        handle.invoke(null, pc, 1, 3, "Renamed");
        awaitOwner(1);
        check(number(db, "SELECT COUNT(*) FROM character_exclude") == 0, "self and invalid subtype rejected");
        sql(db, "CREATE TRIGGER reject_block BEFORE INSERT ON character_exclude "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture blocked write'");
        handle.invoke(null, pc, 1, 0, "rENAMED");
        awaitDbFailure("fixture blocked write");
        check(!lists.contains(0, "Renamed") && number(db, "SELECT COUNT(*) FROM character_exclude") == 0,
                "pending block is not published");
        sql(db, "DROP TRIGGER reject_block");
        awaitOwner(1);
        check(lists.contains(0, "Renamed") && number(db, "SELECT exclude_id FROM character_exclude") == 2,
                "canonical block after commit");
        handle.invoke(null, pc, 1, 0, "renamed");
        awaitOwner(1);
        check(!lists.contains(0, "Renamed") && number(db, "SELECT COUNT(*) FROM character_exclude") == 0,
                "case-insensitive toggle deletes the same row");
    }

    private static void verifyBlessings(Connection db) throws Exception {
        var world = CWorld.getInstance();
        var clan = clan(77, 2);
        world.storeClan(clan);
        var tick = AuraController.class.getDeclaredMethod("tickClanBuff");
        tick.setAccessible(true);
        sql(db, "CREATE TRIGGER reject_expiry BEFORE UPDATE ON clan_data "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture blocked expiry'");
        tick.invoke(AuraController.getInstance());
        awaitDbFailure("fixture blocked expiry");
        check(clan.hasPendingBlessingExpiry() && number(db, "SELECT bless FROM clan_data WHERE clan_id=77") == 2,
                "expiry waits for durable write");
        sql(db, "DROP TRIGGER reject_expiry");
        awaitOwner(77);
        check(clan.getBless() == 0 && !world.getActiveClanBlessings().contains(clan)
                && number(db, "SELECT bless+defence FROM clan_data WHERE clan_id=77") == 0
                && number(db, "SELECT attack+pvpattack+pvpdefence FROM clan_data WHERE clan_id=77") == 73,
                "only the expired slot and active blessing are cleared");

        var old = clan(78, 1);
        world.storeClan(old);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        CacheD.getInstance().submitDbJobOrdered(78, () -> {
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        var replacement = clan(78, 1);
        try {
            check(started.await(4, TimeUnit.SECONDS), "queued expiry race fixture active");
            tick.invoke(AuraController.getInstance());
            check(old.hasPendingBlessingExpiry(), "old expiry queued");
            world.storeClan(replacement);
        } finally { release.countDown(); }
        awaitOwner(78);
        check(number(db, "SELECT bless FROM clan_data WHERE clan_id=78") == 1
                && replacement.getBless() == 1 && world.getActiveClanBlessings().contains(replacement),
                "old expiry cannot clear same-ID replacement");
        sql(db, "UPDATE clan_data SET attack=100 WHERE clan_id=78");
        ClanTable.checkpointActiveBlessings(world, CacheD.getInstance());
        awaitOwner(78);
        check(number(db, "SELECT attack FROM clan_data WHERE clan_id=78") == 1
                && number(db, "SELECT defence FROM clan_data WHERE clan_id=78") == 12,
                "shutdown saves the current duration without touching other slots");
        world.clearClan();
    }

    private static L1Clan clan(int id, int bless) {
        var clan = new L1Clan();
        clan.setClanId(id);
        clan.setClanName("fixture" + id);
        clan.setBuffTime(1, 1, 27, 34);
        clan.setBless(bless);
        return clan;
    }

    private static void awaitDbFailure(String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() - deadline < 0) {
            for (Throwable cause = CacheD.getInstance().lastFailure(); cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains(message)) return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("SQL failure was not exercised: " + message);
    }

    private static void awaitOwner(int owner) throws Exception {
        var done = new CountDownLatch(1);
        CacheD.getInstance().submitDbJobOrdered(owner, done::countDown);
        check(done.await(7, TimeUnit.SECONDS), "owner FIFO drained");
    }

    private static void sql(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement()) { statement.execute(sql); }
    }

    private static int number(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            if (!rows.next()) throw new AssertionError("missing fixture row");
            return rows.getInt(1);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
