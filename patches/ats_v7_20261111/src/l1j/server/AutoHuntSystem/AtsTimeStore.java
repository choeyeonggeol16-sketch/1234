package l1j.server.AutoHuntSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import l1j.server.L1DatabaseFactory;

/** MySQL/InnoDB. The migration must be applied before enabling the new build. */
final class AtsTimeStore implements AtsBudget.Store {
    private final int characterId;
    private final long initialMillis;
    AtsTimeStore(int characterId, long initialMillis) { this.characterId = characterId; this.initialMillis = initialMillis; }
    @Override public AtsBudget.Reservation reserve(long requested) throws Exception {
        try (Connection con = L1DatabaseFactory.getInstance().getAtsConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement q = con.prepareStatement(
                        "INSERT IGNORE INTO character_ats_time (char_id, remaining_millis) VALUES (?, ?)")) {
                    q.setQueryTimeout(3); q.setInt(1, characterId); q.setLong(2, initialMillis); q.executeUpdate();
                }
                long remaining;
                try (PreparedStatement q = con.prepareStatement(
                        "SELECT remaining_millis FROM character_ats_time WHERE char_id=? FOR UPDATE")) {
                    q.setQueryTimeout(3); q.setInt(1, characterId);
                    try (ResultSet rows = q.executeQuery()) {
                        if (!rows.next()) throw new IllegalStateException("Missing ATS row");
                        remaining = rows.getLong(1);
                    }
                }
                if (remaining < 0 || remaining > AtsRules.MAX_MILLIS) throw new IllegalStateException("ATS balance out of range");
                long credit = Math.min(requested, remaining);
                if (credit > 0) try (PreparedStatement q = con.prepareStatement(
                        "UPDATE character_ats_time SET remaining_millis=? WHERE char_id=?")) {
                    q.setQueryTimeout(3); q.setLong(1, remaining - credit); q.setInt(2, characterId);
                    if (q.executeUpdate() != 1) throw new IllegalStateException("ATS reserve update failed");
                }
                con.commit(); return new AtsBudget.Reservation(credit, remaining - credit);
            } catch (Exception failure) { con.rollback(); throw failure; }
            finally { con.setAutoCommit(true); }
        }
    }
    @Override public void refund(long unused) throws Exception {
        if (unused <= 0) return;
        try (Connection con = L1DatabaseFactory.getInstance().getAtsConnection();
                PreparedStatement q = con.prepareStatement(
                    "UPDATE character_ats_time SET remaining_millis=LEAST(?,remaining_millis+?) WHERE char_id=?")) {
            q.setQueryTimeout(3); q.setLong(1, AtsRules.MAX_MILLIS); q.setLong(2, unused); q.setInt(3, characterId);
            if (q.executeUpdate() != 1) throw new IllegalStateException("ATS refund failed");
        }
    }
}
