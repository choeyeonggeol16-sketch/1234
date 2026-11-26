package l1j.server.server.Controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L1GameTime;
import l1j.server.server.model.gametime.L1GameTimeAdapter;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

public class HomeTownTimeController {
	private static final Logger _log = Logger.getLogger(HomeTownTimeController.class.getName());

	private static volatile HomeTownTimeController _instance;

	public static HomeTownTimeController getInstance() {
		if (_instance == null) {
			synchronized (HomeTownTimeController.class) {
				if (_instance == null) {
					_instance = new HomeTownTimeController();
				}
			}
		}
		return _instance;
	}

	private HomeTownTimeController() {
		startListener();
	}

	private static L1TownFixedProcListener _listener;

	private void startListener() {
		if (_listener == null) {
			synchronized (HomeTownTimeController.class) {
				if (_listener == null) {
					_listener = new L1TownFixedProcListener();
					L1GameTimeClock.getInstance().addListener(_listener);
				}
			}
		}
	}

	private class L1TownFixedProcListener extends L1GameTimeAdapter {
		@Override
		public void onDayChanged(L1GameTime time) {
			fixedProc(time);
		}
	}

	private void fixedProc(L1GameTime time) {
		Calendar cal = time.getCalendar();
		if (cal == null) return;
		int day = cal.get(Calendar.DAY_OF_MONTH);

		if (day == 25) {
			monthlyProc();
		} else {
			dailyProc();
		}
	}

	public void dailyProc() {
		_log.info("홈 타운 시스템：일시 처리 개시");
		TownTable.getInstance().updateTaxRate();
		TownTable.getInstance().updateSalesMoneyYesterday();
		TownTable.getInstance().load();
	}

    /** All ten settlements form one DB transaction. Never publish the in-memory
     * resets or leader notifications before that transaction commits. */
    public synchronized void monthlyProc() {
        _log.info("홈 타운 시스템：월시 처리 개시");
        CWorld.getInstance().setProcessingContributionTotal(true);
        try {
            final String[] leaders = new String[11];
            try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                con.setAutoCommit(false);
                try {
                    CTransaction.requireTransactionalTables(con, "characters", "town");
                    for (int townId = 1; townId <= 10; townId++) {
                        leaders[townId] = totalContribution(con, townId);
                    }
                    clearHomeTownID(con);
                    con.commit();
                } catch (SQLException | RuntimeException failure) {
                    try {
                        con.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
            } catch (SQLException | RuntimeException failure) {
                _log.log(Level.SEVERE, "Home town monthly settlement failed; no memory reset", failure);
                return;
            }

            // All SQL state is now committed. The former code reset each town
            // before knowing whether a later town's writes had succeeded.
            for (int townId = 1; townId <= 10; townId++) {
                final String leader = leaders[townId];
                if (leader == null) continue;
                try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_PacketBox.nativeTownLeaderWriter(leader),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                    if (pc != null && pc.getHomeTownId() == townId) {
                        pc.setContribution(0);
                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
                    }
                }
}
            }
            TownTable.getInstance().load();
            for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                if (pc == null) continue;
                if (pc.getHomeTownId() == -1) pc.setHomeTownId(0);
                pc.setContribution(0);
                try {
                    pc.save();
                } catch (Exception e) {
                    _log.log(Level.SEVERE, "monthlyProc pc save error", e);
                }
            }
        } finally {
            CWorld.getInstance().setProcessingContributionTotal(false);
        }
    }

    private static String totalContribution(Connection con, int townId) throws SQLException {
        String leaderName = null;
        int leaderId = 0;
        double total = 0;
        double tax = 0;

        // Serialize concurrent settlements at the same town row. InnoDB is
        // required above; a missing town row aborts the entire settlement.
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT town_fix_tax FROM town WHERE town_id=? FOR UPDATE")) {
            ps.setInt(1, townId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Missing town id=" + townId);
                tax = rs.getDouble(1);
                if (rs.next()) throw new SQLException("Duplicate town id=" + townId);
            }
        }
        // Lock the contributing character rows before reading amounts. A
        // concurrent contribution write must not change the aggregate between
        // the payout calculation and the UPDATE that consumes it.
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT objid,char_name,Contribution FROM characters "
                + "WHERE HomeTownID=? ORDER BY Contribution DESC FOR UPDATE")) {
            ps.setInt(1, townId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final double contribution = rs.getDouble(3);
                    total += contribution;
                    if (leaderName == null && contribution > 0) {
                        leaderId = rs.getInt(1);
                        leaderName = rs.getString(2);
                    }
                }
            }
        }
        final double unit = total > 0 ? Math.floor((tax / total) * 100) / 100 : 0;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE characters SET Pay=Contribution*?,Contribution=0 "
                + "WHERE HomeTownID=? AND Contribution>0")) {
            ps.setDouble(1, unit);
            ps.setInt(2, townId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE town SET leader_id=?,leader_name=?,tax_rate=0,tax_rate_reserved=0,"
                + "sales_money_yesterday=sales_money,sales_money=0,town_tax=0,"
                + "town_fix_tax=0 WHERE town_id=?")) {
            ps.setInt(1, leaderId);
            ps.setString(2, leaderName);
            ps.setInt(3, townId);
            if (ps.executeUpdate() != 1) throw new SQLException("Town update missing id=" + townId);
        }
        return leaderName;
    }

    private static void clearHomeTownID(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE characters SET HomeTownID=0 WHERE HomeTownID=-1")) {
            ps.executeUpdate();
        }
    }

	/**
	 * 보수를 취득해 클리어 한다
	 * 
	 * @return 보수
	 */
    public static int getPay(int objid) {
        int pay = 0;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                CTransaction.requireTransactionalTables(con, "characters");
                // Lock the payout row before reading so two concurrent claims
                // cannot both return the same unpaid balance.
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT Pay FROM characters WHERE objid=? FOR UPDATE")) {
                    ps.setInt(1, objid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Missing home-town payout objid=" + objid);
                        pay = rs.getInt(1);
                    }
                }
                if (pay == 0) {
                    // MySQL may report zero affected rows when setting an
                    // already-zero payout. Nothing needs to be consumed.
                    con.commit();
                    return 0;
                }
                if (pay < 0) throw new SQLException("Invalid home-town payout objid=" + objid);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE characters SET Pay=0 WHERE objid=?")) {
                    ps.setInt(1, objid);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Home-town payout update missing objid=" + objid);
                }
                con.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    con.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException | RuntimeException e) {
            _log.log(Level.SEVERE, "getPay error for objid: " + objid, e);
            return 0;
        }
        return pay;
    }

}