package l1j.server.server.Controller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.RaceTable;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Racer;

/** Run from a scratch directory with an empty config/bug_race.properties.
 * Set RKERN_STORAGE_TEST_PASSWORD if the disposable DB server requires it.
 */
public final class BugRacePersistenceRegression {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: BugRacePersistenceRegression JDBC_URL USER");
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final String schema = "rkern_bugrace_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema + " DEFAULT CHARACTER SET utf8mb4");
            try {
                ddl.execute("CREATE TABLE " + schema + ".util_racer (`레이서번호` INT UNSIGNED PRIMARY KEY, "
                        + "`승리횟수` INT UNSIGNED NOT NULL, `패횟수` INT UNSIGNED NOT NULL) ENGINE=InnoDB");
                ddl.execute("CREATE TABLE " + schema + ".bug_history (round INT PRIMARY KEY, winner_id INT, "
                        + "winner_name VARCHAR(50), total_ticket_count INT, winner_ticket_count INT, "
                        + "winner_ration DOUBLE(10,0), total_price INT, winner_price INT) ENGINE=InnoDB");
                ddl.execute("INSERT INTO " + schema + ".util_racer VALUES (7, 25, 31), (8, 12, 19)");
                final HikariConfig config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(2);
                try (var pool = new HikariDataSource(config); var observer = pool.getConnection()) {
                    require(schema.equals(observer.getCatalog()), "fixture catalog");
                    var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    field.setAccessible(true);
                    field.set(null, pool);
                    try {
                        exercise(observer);
                        verifyControllerSave();
                    } finally {
                        try {
                            require(CacheD.getInstance().shutdown(5_000L), "fixture CacheD shutdown");
                        } finally {
                            field.set(null, null);
                        }
                    }
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        System.out.println("BugRace persistence: rejection, cold start, partial drain, history conflict and public save passed");
    }

    private static void exercise(Connection connection) throws Exception {
        var racer = new RaceTable().getTemplate(7);
        require(racer.getWinCount() == 25 && racer.getLoseCount() == 31, "cold-start totals");
        final ArrayDeque<Runnable> admitted = new ArrayDeque<>();
        final boolean[] saturated = {true};
        final var persistence = new BugRacePersistence(work -> {
            if (saturated[0]) throw new RejectedExecutionException("synthetic saturation");
            require(admitted.isEmpty(), "only one CacheD slot");
            admitted.add(work);
        });

        final var result = new BugRacePersistence.History(14, 2, "racer", 8, 3, 3.4, 4000, 10);
        persistence.saveRacer(7, racer.getWinCount() + 1, racer.getLoseCount());
        persistence.saveHistory(result);
        persistence.saveRacer(8, 12, 20); // A loser arrived before the race was stopped.
        require(!persistence.canStartRace() && !persistence.awaitDrained(1), "saturated writes remain pending");
        require(number(connection, "SELECT COUNT(*) FROM bug_history") == 0, "no JDBC from rejection caller");
        require(number(connection, "SELECT 승리횟수 FROM util_racer WHERE 레이서번호=7") == 25,
                "rejection caller did not advance totals");

        saturated[0] = false;
        persistence.trySubmit();
        admitted.removeFirst().run();
        persistence.trySubmit();
        // Advance absolute totals on the same racer while the older snapshot is in flight.
        persistence.saveRacer(7, 26, 33);
        persistence.saveRacer(7, 25, 32);
        admitted.removeFirst().run();
        while (!persistence.awaitDrained(1)) admitted.removeFirst().run();

        require(persistence.canStartRace(), "partial race drain releases the next round");
        require(number(connection, "SELECT 승리횟수 FROM util_racer WHERE 레이서번호=7") == 26, "one win");
        require(number(connection, "SELECT 패횟수 FROM util_racer WHERE 레이서번호=7") == 33, "new loss totals");
        require(number(connection, "SELECT 패횟수 FROM util_racer WHERE 레이서번호=8") == 20, "partial round loser");
        require(number(connection, "SELECT COUNT(*) FROM bug_history") == 1, "one history");

        persistence.saveHistory(result);
        admitted.removeFirst().run();
        require(persistence.canStartRace(), "same duplicate is a successful retry with stored rounding");
        persistence.saveHistory(new BugRacePersistence.History(14, 1, "other", 8, 3, 3.4, 4000, 10));
        admitted.removeFirst().run();
        require(persistence.awaitDrained(1) && !persistence.canStartRace(), "conflict releases slot and stops new races");
        require(number(connection, "SELECT winner_id FROM bug_history WHERE round=14") == 2, "conflict preserves history");

        for (int neighbour = 3; neighbour <= 4; neighbour++) {
            final int round = neighbour + 9;
            require(new BugRacePersistence.History(round, 2, "racer", 8, 3, neighbour, 4000, 10)
                    .save(connection), "fixture tie neighbour");
            final var midpoint = new BugRacePersistence(admitted::add);
            midpoint.saveHistory(new BugRacePersistence.History(round, 2, "racer", 8, 3, 3.5, 4000, 10));
            admitted.removeFirst().run();
            require(midpoint.awaitDrained(1) && !midpoint.canStartRace(), "both midpoint neighbours are ambiguous");
        }

        try (var ddl = connection.createStatement()) {
            ddl.execute("ALTER TABLE bug_history MODIFY winner_ration DOUBLE");
        }
        final var precise = new BugRacePersistence(admitted::add);
        precise.saveHistory(new BugRacePersistence.History(14, 2, "racer", 8, 3, 3.25, 4000, 10));
        admitted.removeFirst().run();
        require(precise.awaitDrained(1) && !precise.canStartRace(), "full-precision offers cannot round");

        final var deleted = new BugRacePersistence(admitted::add);
        deleted.saveRacer(9, 1, 0);
        admitted.removeFirst().run();
        require(deleted.awaitDrained(1) && !deleted.canStartRace(), "missing racer releases slot and stops new races");
    }

    private static void verifyControllerSave() throws Exception {
        final BugRaceController race = BugRaceController.getInstance();
        require(race._round == 14, "controller recovers last persisted round");
        final L1Racer imported = new L1Racer();
        imported.setWinCount(27);
        imported.setLoseCount(32);
        race.SaveAllRacer(imported, 7);
        race.stop();
        require(race.awaitPersistence(5_000L), "real CacheD accepts and drains controller save after stop");
        final L1Racer reloaded = new RaceTable().getTemplate(7);
        require(reloaded.getWinCount() == 27 && reloaded.getLoseCount() == 33, "public save and restart totals");
    }

    private static int number(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            require(rows.next(), "fixture row");
            return rows.getInt(1);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
