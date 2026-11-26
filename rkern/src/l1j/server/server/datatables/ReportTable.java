package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class ReportTable {

	private static final ReportTable INSTANCE = new ReportTable();
    private final ConcurrentHashMap<String, String> reportList = new ConcurrentHashMap<>();

    public static ReportTable getInstance() {
        return INSTANCE;
    }
	public ReportTable() {
		loadReport();
	}
	private void loadReport() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM report");
			rs = pstm.executeQuery();
			while (rs.next()) {
				String objectName = rs.getString("object_name");
				String reporter = rs.getString("reporter");
				reportList.put(objectName, reporter);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public boolean isReport(String objectName) {
		return reportList.get(objectName) != null ? true : false;
	}
    public void reportUpdate(Connection con, String objectName, String reporter, Timestamp date) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO report SET object_name=?, reporter=?, date=?")) {
            ps.setString(1, objectName);
            ps.setString(2, reporter);
            ps.setTimestamp(3, date);
            ps.executeUpdate();
            reportList.put(objectName, reporter);
        }
    }
}
