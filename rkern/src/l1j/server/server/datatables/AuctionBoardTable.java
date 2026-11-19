package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1AuctionBoard;

public class AuctionBoardTable {

	private static final Logger _log = Logger.getLogger(AuctionBoardTable.class.getName());

	private static volatile AuctionBoardTable _instance;

	public static AuctionBoardTable getInstance() {
		if (_instance == null) {
			synchronized (AuctionBoardTable.class) {
				if (_instance == null) {
					_instance = new AuctionBoardTable();
				}
			}
		}
		return _instance;
	}

	private final java.util.concurrent.ConcurrentHashMap<Integer,L1AuctionBoard> _boards = new java.util.concurrent.ConcurrentHashMap<>();

	private Calendar timestampToCalendar(Timestamp ts) {
		if (ts == null) {
			return null;
		}
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(ts.getTime());
		return cal;
	}

	private AuctionBoardTable() {
		load();
	}

	private void load() {
		// This is a constructor-only load. Never publish a partial DB image.
		final java.util.HashMap<Integer, L1AuctionBoard> loaded = new java.util.HashMap<>();
		String sql = "SELECT * FROM board_auction ORDER BY house_id";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			while (rs.next()) {
				L1AuctionBoard board = new L1AuctionBoard();
				board.setHouseId(rs.getInt(1));
				board.setHouseName(rs.getString(2));
				board.setHouseArea(rs.getInt(3));
				board.setDeadline(timestampToCalendar(rs.getTimestamp(4)));
				board.setPrice(rs.getInt(5));
				board.setLocation(rs.getString(6));
				board.setOldOwner(rs.getString(7));
				board.setOldOwnerId(rs.getInt(8));
				board.setBidder(rs.getString(9));
				board.setBidderId(rs.getInt(10));
				loaded.put(board.getHouseId(), board);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AuctionBoardTable load error", e);
			throw new IllegalStateException("Auction board DB load failed", e);
		}
		_boards.putAll(loaded);
	}

	public Collection<L1AuctionBoard> getAuctionBoardTableList() {
		return new java.util.ArrayList<>(_boards.values());
	}

	public L1AuctionBoard getAuctionBoardTable(int houseId) {
		return _boards.get(houseId);
	}

    public void putCommitted(L1AuctionBoard board) { _boards.put(board.getHouseId(),board); }
    public void removeCommitted(int id) { _boards.remove(id); }

	public void insertAuctionBoard(L1AuctionBoard board) {
		String sql = "INSERT INTO board_auction SET house_id=?, house_name=?, house_area=?, deadline=?, price=?, location=?, old_owner=?, old_owner_id=?, bidder=?, bidder_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, board.getHouseId());
			pstm.setString(2, board.getHouseName());
			pstm.setInt(3, board.getHouseArea());
			
			Timestamp ts = (board.getDeadline() != null) ? new Timestamp(board.getDeadline().getTimeInMillis()) : null;
			pstm.setTimestamp(4, ts);
			
			pstm.setInt(5, board.getPrice());
			pstm.setString(6, board.getLocation());
			pstm.setString(7, board.getOldOwner());
			pstm.setInt(8, board.getOldOwnerId());
			pstm.setString(9, board.getBidder());
			pstm.setInt(10, board.getBidderId());
			pstm.execute();

			_boards.put(board.getHouseId(), board);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "insertAuctionBoard error", e);
		}
	}

	public void updateAuctionBoard(L1AuctionBoard board) {
		String sql = "UPDATE board_auction SET house_name=?, house_area=?, deadline=?, price=?, location=?, old_owner=?, old_owner_id=?, bidder=?, bidder_id=? WHERE house_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, board.getHouseName());
			pstm.setInt(2, board.getHouseArea());
			
			Timestamp ts = (board.getDeadline() != null) ? new Timestamp(board.getDeadline().getTimeInMillis()) : null;
			pstm.setTimestamp(3, ts);
			
			pstm.setInt(4, board.getPrice());
			pstm.setString(5, board.getLocation());
			pstm.setString(6, board.getOldOwner());
			pstm.setInt(7, board.getOldOwnerId());
			pstm.setString(8, board.getBidder());
			pstm.setInt(9, board.getBidderId());
			pstm.setInt(10, board.getHouseId());
			pstm.execute();

			_boards.put(board.getHouseId(), board);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "updateAuctionBoard error", e);
		}
	}

	public void deleteAuctionBoard(int houseId) {
		String sql = "DELETE FROM board_auction WHERE house_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, houseId);
			pstm.execute();

			_boards.remove(houseId);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "deleteAuctionBoard error", e);
		}
	}
}