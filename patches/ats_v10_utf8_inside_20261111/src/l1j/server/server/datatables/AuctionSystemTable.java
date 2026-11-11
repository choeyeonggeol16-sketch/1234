package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

//TODO 중개 거래 게시판
public class AuctionSystemTable {

	private static Logger _log = Logger.getLogger(AuctionSystemTable.class.getName());

	private volatile static AuctionSystemTable _instance;

	private AuctionSystemTable() {
	}

	public static synchronized AuctionSystemTable getInstance() {

		if (_instance == null) {
			_instance = new AuctionSystemTable();
		}
		return _instance;
	}

	public void writeTopic(L1PcInstance player, String selltype, ItemInstance item, int count, int sellcount,
			String bank, String banknumber, String bankname) {
		int counts = 0;

		Connection con = null;
		PreparedStatement pstm1 = null;
		ResultSet rs = null;
		PreparedStatement pstm2 = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm1 = con.prepareStatement("SELECT * FROM Auction ORDER BY id DESC");
			rs = pstm1.executeQuery();
			if (rs.next()) {
				counts = rs.getInt("id");
			}

			pstm2 = con.prepareStatement(
					"INSERT INTO Auction SET id=?, name=?, item_id=?, item_name=?, count=?, sellcount=?, AccountName=?, selltype=?, status=?, bank=?, banknumber=?, bankname=?");
			pstm2.setInt(1, (counts + 1));
			pstm2.setString(2, player.getName());
			pstm2.setInt(3, item.getItemId());
			pstm2.setString(4, item.getName());
			pstm2.setInt(5, count);
			pstm2.setInt(6, sellcount);
			pstm2.setString(7, player.getAccountName());
			pstm2.setString(8, selltype);
			pstm2.setInt(9, 0);
			pstm2.setString(10, bank);
			pstm2.setString(11, banknumber);
			pstm2.setString(12, bankname);
			pstm2.execute();

		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm1);
			SQLUtil.close(pstm2);
			SQLUtil.close(con);
		}
	}

	public void AuctionUpdate(int number, String buyer, String buyeraccount, String sellstatus, int status) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"UPDATE Auction SET selltype=?, status=?, buyername=?, buyerAccountName=? WHERE id=?");
			pstm.setString(1, sellstatus);
			pstm.setInt(2, status);
			pstm.setString(3, buyer);
			pstm.setString(4, buyeraccount);
			pstm.setInt(5, number);
			pstm.execute();

		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public void AuctionComplete(int number) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		String status = "판매완료";
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("UPDATE Auction SET selltype=?, status=? WHERE id=?");
			pstm.setString(1, status);
			pstm.setInt(2, 2);
			pstm.setInt(3, number);
			pstm.execute();
			

		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/** 운영자 명령어로 사용할 메서드 **/
	public void deleteTopic(String number) {
		StringTokenizer st = new StringTokenizer(number);
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			int id = 0;
			id = Integer.parseInt(st.nextToken());
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM Auction WHERE id=?");
			pstm.setInt(1, id);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public void deleteTopic(int number) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {

			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM Auction WHERE id=?");
			pstm.setInt(1, number);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

}