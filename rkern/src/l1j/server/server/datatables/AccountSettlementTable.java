package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;

/** Account settlement operations unrelated to the removed cash-trade board. */
public final class AccountSettlementTable {
    private static final Logger LOG = Logger.getLogger(AccountSettlementTable.class.getName());

    private AccountSettlementTable() {}

    public static void deleteMeepByLogin(String login) {
        final String sql = "DELETE FROM meep WHERE login=?";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.execute();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "deleteMeepByLogin error", e);
        }
    }
}
