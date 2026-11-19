package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.CommonUtil;

//TODO 중개 거래 게시판
public class AuctionSystemTable {

	private static final Logger _log = Logger.getLogger(AuctionSystemTable.class.getName());

	private volatile static AuctionSystemTable _instance;

	private AuctionSystemTable() {
	}

	public static synchronized AuctionSystemTable getInstance() {
		if (_instance == null) {
			_instance = new AuctionSystemTable();
		}
		return _instance;
	}

	public void writeTopic(L1PcInstance player, String selltype, L1ItemInstance item, int count, int sellcount,
			String bank, String banknumber, String bankname) {
		int counts = 0;
		String selectSql = "SELECT id FROM Auction ORDER BY id DESC LIMIT 1";
		String insertSql = "INSERT INTO Auction SET id=?, name=?, item_id=?, item_name=?, count=?, sellcount=?, AccountName=?, selltype=?, status=?, bank=?, banknumber=?, bankname=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			try (PreparedStatement pstm1 = con.prepareStatement(selectSql);
				 ResultSet rs = pstm1.executeQuery()) {
				if (rs.next()) {
					counts = rs.getInt("id");
				}
			}

			try (PreparedStatement pstm2 = con.prepareStatement(insertSql)) {
				pstm2.setInt(1, counts + 1);
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
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "writeTopic error", e);
		}
	}


	public void AuctionUpdate(int number, String buyer, String buyeraccount, String sellstatus, int status) {
		String sql = "UPDATE Auction SET selltype=?, status=?, buyername=?, buyerAccountName=? WHERE id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, sellstatus);
			pstm.setInt(2, status);
			pstm.setString(3, buyer);
			pstm.setString(4, buyeraccount);
			pstm.setInt(5, number);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AuctionUpdate error", e);
		}
	}

	public void AuctionUpdate33(int itemcount) {
		String sql = "UPDATE royal2 SET count=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, itemcount);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AuctionUpdate33 error", e);
		}
	}

	public void AuctionUpdate44(int itemcount) {
		String sql = "UPDATE royal SET count=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, itemcount);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AuctionUpdate44 error", e);
		}
	}


	public void AuctionComplete(int number) {
		String status = "판매완료";
		String sql = "UPDATE Auction SET selltype=?, status=? WHERE id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, status);
			pstm.setInt(2, 2);
			pstm.setInt(3, number);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AuctionComplete error", e);
		}
	}

	/** 운영자 명령어로 사용할 메서드 **/
	public void deleteTopic(String number) {
		StringTokenizer st = new StringTokenizer(number);
		if (!st.hasMoreTokens()) {
			return;
		}
		try {
			int id = Integer.parseInt(st.nextToken());
			deleteTopic(id);
		} catch (NumberFormatException e) {
			_log.log(Level.SEVERE, "deleteTopic parsing error", e);
		}
	}


	public void deleteTopic(int number) {
		String sql = "DELETE FROM Auction WHERE id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, number);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "deleteTopic error", e);
		}
	}

	public void deleteTopic3(String login) {
		String sql = "DELETE FROM meep WHERE login=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, login);
			pstm.execute();
		} catch (SQLException e) {
		}
	}

	public void deleteTopic4(String login) {
		String sql = "DELETE FROM royal WHERE login=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, login);
			pstm.execute();
		} catch (SQLException e) {
		}
	}

	public void deleteTopic5(String login) {
		String sql = "DELETE FROM royal2 WHERE login=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, login);
			pstm.execute();
		} catch (SQLException e) {
		}
	}
}