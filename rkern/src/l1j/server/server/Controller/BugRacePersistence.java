package l1j.server.server.Controller;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;

/** Keeps at most one unsaved history and one set of totals per configured racer. */
final class BugRacePersistence {
    private static final Logger LOG = Logger.getLogger(BugRacePersistence.class.getName());
    private static final int ORDER_KEY = CacheD.accountOrderKey("$bug-race");
    private static final long ADMISSION_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);

    private final Consumer<Runnable> submit;
    private final LinkedHashMap<Integer, RacerTotals> racers = new LinkedHashMap<>();
    private History history;
    private boolean failed;
    private boolean inFlight;

    BugRacePersistence() {
        this(action -> CacheD.getInstance().submitDbJobRetryOrdered(ORDER_KEY, action));
    }

    BugRacePersistence(Consumer<Runnable> submit) {
        this.submit = submit;
    }

    synchronized boolean canStartRace() {
        return !failed && isDrained();
    }

    private boolean isDrained() {
        return history == null && racers.isEmpty();
    }

    synchronized void saveRacer(int racerId, int wins, int losses) {
        final RacerTotals previous = racers.get(racerId);
        if (previous == null || wins > previous.wins || losses > previous.losses) {
            racers.put(racerId, previous == null ? new RacerTotals(racerId, wins, losses)
                    : new RacerTotals(racerId, Math.max(wins, previous.wins), Math.max(losses, previous.losses)));
        }
        trySubmit();
    }

    synchronized void saveHistory(History result) {
        if (history != null) throw new IllegalStateException("Previous race history is unresolved");
        history = result;
        trySubmit();
    }

    synchronized void trySubmit() {
        if (inFlight || isDrained()) return;
        final History pendingHistory = history;
        final RacerTotals pendingRacer = pendingHistory == null ? racers.firstEntry().getValue() : null;
        inFlight = true;
        try {
            submit.accept(() -> persist(pendingHistory, pendingRacer));
        } catch (RejectedExecutionException rejected) {
            // The clock and shutdown drain retry admission without running JDBC.
            inFlight = false;
        }
    }

    // Permanently rejected records stop this minigame, but never hold up server shutdown.
    synchronized boolean awaitDrained(long timeoutMillis) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!isDrained()) {
            trySubmit();
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) return false;
            TimeUnit.NANOSECONDS.timedWait(this, Math.min(remaining, ADMISSION_RETRY_NANOS));
        }
        return true;
    }

    private void persist(History pendingHistory, RacerTotals pendingRacer) {
        final boolean valid;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            valid = pendingHistory == null ? pendingRacer.save(con) : pendingHistory.save(con);
        } catch (SQLException e) {
            // The CacheD job retains FIFO ownership and repeats these idempotent writes.
            throw new IllegalStateException("BugRace result persistence failed", e);
        }
        synchronized (this) {
            if (pendingHistory == null) racers.remove(pendingRacer.racerId, pendingRacer);
            else history = null;
            failed |= !valid;
            inFlight = false;
            notifyAll();
        }
        if (!valid) {
            if (pendingHistory == null) {
                LOG.log(Level.SEVERE, "BugRace racer {0} is missing from storage; further races are suspended",
                        pendingRacer.racerId);
            } else {
                LOG.log(Level.SEVERE, "BugRace round {0} conflicts with stored history; further races are suspended",
                        pendingHistory.round);
            }
        }
    }

    private record RacerTotals(int racerId, int wins, int losses) {
        boolean save(Connection con) throws SQLException {
            try (PreparedStatement statement = con.prepareStatement(
                    "UPDATE util_racer SET 승리횟수=GREATEST(승리횟수, ?), 패횟수=GREATEST(패횟수, ?) WHERE 레이서번호=?")) {
                statement.setInt(1, wins);
                statement.setInt(2, losses);
                statement.setInt(3, racerId);
                if (statement.executeUpdate() != 0) return true;
            }
            // Drivers configured for changed-row counts also return zero for a confirmed retry.
            try (PreparedStatement statement = con.prepareStatement(
                    "SELECT 1 FROM util_racer WHERE 레이서번호=?")) {
                statement.setInt(1, racerId);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next();
                }
            }
        }
    }

    record History(int round, int winnerId, String winnerName, int totalTickets, int winnerTickets,
                   double winnerRate, int totalPrice, int winnerPrice) {
        boolean save(Connection con) throws SQLException {
            try (PreparedStatement statement = con.prepareStatement(
                    "INSERT INTO bug_history (round, winner_id, winner_name, total_ticket_count, winner_ticket_count, "
                            + "winner_ration, total_price, winner_price) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setInt(1, round);
                statement.setInt(2, winnerId);
                statement.setString(3, winnerName);
                statement.setInt(4, totalTickets);
                statement.setInt(5, winnerTickets);
                statement.setDouble(6, winnerRate);
                statement.setInt(7, totalPrice);
                statement.setInt(8, winnerPrice);
                statement.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (e.getErrorCode() != 1062) throw e;
            }

            // An ACK may have been lost. The round is unique in the Net schema.
            // Check the declared scale because DOUBLE and DOUBLE(10,0) can store different values.
            try (PreparedStatement statement = con.prepareStatement(
                    "SELECT winner_id, winner_name, total_ticket_count, winner_ticket_count, "
                            + "winner_ration, total_price, winner_price, (SELECT NUMERIC_SCALE "
                            + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                            + "AND TABLE_NAME='bug_history' AND COLUMN_NAME='winner_ration') "
                            + "FROM bug_history WHERE round=?")) {
                statement.setInt(1, round);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) throw new SQLException("Duplicate BugRace history disappeared: " + round);
                    final BigDecimal storedRate = row.getBigDecimal(5);
                    return sameStoredRate(storedRate, row.getObject(8, Integer.class))
                            && Integer.valueOf(winnerId).equals(row.getObject(1, Integer.class))
                            && Objects.equals(winnerName, row.getString(2))
                            && Integer.valueOf(totalTickets).equals(row.getObject(3, Integer.class))
                            && Integer.valueOf(winnerTickets).equals(row.getObject(4, Integer.class))
                            && Integer.valueOf(totalPrice).equals(row.getObject(6, Integer.class))
                            && Integer.valueOf(winnerPrice).equals(row.getObject(7, Integer.class));
                }
            }
        }

        private boolean sameStoredRate(BigDecimal storedRate, Integer scale) {
            if (storedRate == null) return false;
            if (storedRate.doubleValue() == winnerRate) return true;
            if (scale == null || scale < 0) return false;
            // Only confirm a unique rounded value. At a midpoint, drivers and
            // database versions may store different neighbours.
            final BigDecimal halfUnit = BigDecimal.valueOf(5L, scale + 1);
            return storedRate.subtract(BigDecimal.valueOf(winnerRate)).abs().compareTo(halfUnit) < 0;
        }
    }
}
