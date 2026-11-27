import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.instance.dungeon.time.DungeonTimeUserInformation;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeProgressLoader;
import l1j.server.server.model.world.instance.dungeon.time.progress.CharacterTimeProgress;
import l1j.server.server.storage.CacheD;

/** Requires a disposable MySQL/MariaDB server URL and a user with schema permissions. */
public final class DungeonResetStorageRegression {
    public static void main(String[] args) throws Exception {
        final String schema = "rkern_dungeon_reset_" + UUID.randomUUID().toString().replace("-", "");
        final var poolField = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        poolField.setAccessible(true);
        try (Connection admin = DriverManager.getConnection(args[0], args[1], "")) {
            execute(admin, "CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                execute(admin, "CREATE TABLE tb_index_stamp (idx INT PRIMARY KEY, stamp DATETIME NULL) ENGINE=InnoDB");
                execute(admin, "CREATE TABLE tb_dungeon_time_account_information "
                        + "(owner_info VARCHAR(50), timer_id INT, remain_seconds INT, "
                        + "PRIMARY KEY(owner_info,timer_id)) ENGINE=InnoDB");
                execute(admin, "CREATE TABLE tb_dungeon_time_char_information "
                        + "(owner_info INT, timer_id INT, remain_seconds INT, PRIMARY KEY(owner_info,timer_id)) ENGINE=InnoDB");
                execute(admin, "CREATE TABLE cached_transaction_receipts "
                        + "(tx_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, kind VARCHAR(16) NOT NULL, "
                        + "owner_a INT NOT NULL, owner_b INT NOT NULL) ENGINE=InnoDB");
                execute(admin, "INSERT INTO tb_dungeon_time_account_information VALUES ('fixture',4,28)");
                execute(admin, "INSERT INTO tb_dungeon_time_char_information VALUES (91,7,44)");
                execute(admin, "INSERT INTO tb_index_stamp VALUES (1,'2026-11-25 23:59:00')");
                var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setCatalog(schema);
                // A connection's default schema must stay pinned across borrows.
                config.setConnectionInitSql("USE " + schema);
                config.setMaximumPoolSize(4);
                config.setMinimumIdle(0);
                try (var pool = new HikariDataSource(config)) {
                    poolField.set(null, pool);
                    try {
                        runTest(admin);
                    } finally {
                        CacheD.getInstance().shutdown(5000);
                        poolField.set(null, null);
                    }
                }
            } finally {
                execute(admin, "DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS MariaDB dungeon rollback, retry, duplicate reset, queued save and stale login snapshot");
    }

    private static void runTest(Connection admin) throws Exception {
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(Instant.parse("2026-11-26T23:59:00Z").toEpochMilli());
        long generation = DungeonTimeProgressLoader.generation();
        var old = DungeonTimeProgressLoader.readSnapshot(91, "fixture");
        check(old.size() == 2, "load both timer scopes");
        var oldProgress = (CharacterTimeProgress) old.getFirst();
        var current = DungeonTimeUserInformation.newInstance();
        current.put_dungeon_progress(7, oldProgress);

        execute(admin, "CREATE TRIGGER refuse_reset BEFORE DELETE ON tb_dungeon_time_char_information "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture injected reset failure'");
        try {
            DungeonTimeProgressLoader.resetAll(due);
            throw new AssertionError("reset unexpectedly committed");
        } catch (IllegalStateException expected) {
            check(expected.getCause() instanceof java.sql.SQLException, "DB failure reaches caller");
        }
        check(DungeonTimeProgressLoader.generation() == generation && current.get_progress(7) == oldProgress,
                "failed DB reset preserves memory");
        check(number(admin, "SELECT COUNT(*) FROM tb_dungeon_time_account_information") == 1
                && number(admin, "SELECT COUNT(*) FROM tb_dungeon_time_char_information") == 1
                && number(admin, "SELECT COUNT(*) FROM tb_index_stamp WHERE idx=1 AND DAY(stamp)=25") == 1,
                "both timer tables and stamp rolled back");
        execute(admin, "DROP TRIGGER refuse_reset");

        var workerStarted = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var drained = new CountDownLatch(1);
        var cache = CacheD.getInstance();
        cache.submitDbJobOrdered(91, () -> {
            workerStarted.countDown();
            try { releaseWorker.await(8, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        try {
            check(workerStarted.await(5, TimeUnit.SECONDS), "owner queue blocker started");
            DungeonTimeProgressLoader.update(oldProgress);
            cache.submitDbJobOrdered(91, drained::countDown);
            check(DungeonTimeProgressLoader.resetAll(due), "retry commits");
            check(DungeonTimeProgressLoader.generation() == generation + 1, "one successful new generation");
            check(current.get_progress(7) == null, "retired state refreshed");
            current.put_dungeon_progress(7, oldProgress);
            check(current.get_progress(7) == null, "old login snapshot rejected");
        } finally {
            releaseWorker.countDown();
        }
        check(drained.await(5, TimeUnit.SECONDS), "old owner write drained");
        check(DungeonTimeProgressLoader.readSnapshot(91, "fixture").isEmpty(), "no old progress after retry");
        execute(admin, "INSERT INTO tb_dungeon_time_char_information VALUES (91,7,15)");
        check(!DungeonTimeProgressLoader.resetAll(due), "duplicate boundary is ignored");
        check(DungeonTimeProgressLoader.generation() == generation + 1
                && number(admin, "SELECT remain_seconds FROM tb_dungeon_time_char_information WHERE owner_info=91") == 15,
                "duplicate reset preserves new timer use");
        var fresh = (CharacterTimeProgress) DungeonTimeProgressLoader.readSnapshot(91, "fixture").getFirst();
        fresh.set_remain_seconds(14);
        DungeonTimeProgressLoader.update(fresh);
        var saved = new CountDownLatch(1);
        cache.submitDbJobOrdered(91, saved::countDown);
        check(saved.await(5, TimeUnit.SECONDS)
                && number(admin, "SELECT remain_seconds FROM tb_dungeon_time_char_information WHERE owner_info=91") == 14,
                "new generation timer persists through the same CacheD FIFO");
    }

    private static int number(Connection con, String query) throws Exception {
        try (var stmt = con.createStatement(); var rows = stmt.executeQuery(query)) {
            if (!rows.next()) throw new AssertionError("Missing fixture result");
            return rows.getInt(1);
        }
    }

    private static void execute(Connection con, String sql) throws Exception {
        try (var stmt = con.createStatement()) { stmt.execute(sql); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
