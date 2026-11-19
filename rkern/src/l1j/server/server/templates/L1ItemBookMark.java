package l1j.server.server.templates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class L1ItemBookMark {

	private static Logger _log = Logger.getLogger(L1ItemBookMark.class.getName());

	private int _charId;
	private int _id;
	private String _name;
	private int _locX;
	private int _locY;
	private short _mapId;
	private int _randomX;
	private int _randomY;
	private int _speed_id;

	public int getSpeed_id() {
		return _speed_id;
	}
	public void setSpeed_id(int i) {
		_speed_id = i;
	}
	public int getId() {
		return _id;
	}
	public void setId(int i) {
		_id = i;
	}
	public int getCharId() {
		return _charId;
	}
	private int _NumId;

	public int getNumId() {
		return _NumId;
	}

	public void setNumId(int i) {
		_NumId = i;
	}

	private int _Temp_id;

	public int getTemp_id() {
		return _Temp_id;
	}

	public void setTemp_id(int i) {
		_Temp_id = i;
	}

	public void setCharId(int i) {
		_charId = i;
	}

	public String getName() {
		return _name;
	}

	public void setName(String s) {
		_name = s;
	}

	public int getLocX() {
		return _locX;
	}

	public void setLocX(int i) {
		_locX = i;
	}

	public int getLocY() {
		return _locY;
	}

	public void setLocY(int i) {
		_locY = i;
	}

	public short getMapId() {
		return _mapId;
	}

	public void setMapId(short i) {
		_mapId = i;
	}

	public int getRandomX() {
		return _randomX;
	}

	public void setRandomX(int i) {
		_randomX = i;
	}

	public int getRandomY() {
		return _randomY;
	}

	public void setRandomY(int i) {
		_randomY = i;
	}

	public L1ItemBookMark() {
	}

    /** Replace the owner's bookmarks and consume the physical storage orb in
     * one receipt. Never clear the live list before the SQL is confirmed. */
    public static void useBookmarkItem(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != 700023 || item.getCount() != 1
                || pc.isItemTransferBusy() || pc.getTradeID() != 0 || pc.isTradeResolving()) return;
        final int charId = pc.getId(), objectId = item.getId(), limit = pc.getMark_count();
        final ArrayList<L1BookMark> prepared = new ArrayList<>();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc,
                    l1j.server.server.storage.CacheD.accountOrderKey("bookmark:" + charId))) {
            if (!tx.debit(item, 1)) return;
            tx.sql(con -> prepared.addAll(replaceItemBookmarks(con, charId, limit, objectId)));
            tx.onFailure(() -> pc.sendPackets("기억 장소를 교체하지 못했습니다. 구슬은 소비되지 않았습니다."));
            tx.commit(() -> {
                pc._bookmarks.clear();
                pc._speedbookmarks.clear();
                for (L1BookMark book : prepared) pc.addBookMark(book);
                l1j.server.server.model.network.core.sc.packet.SC_BookMarkLoad.send(pc);
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Stored bookmark orb was not admitted: " + charId, failure);
            pc.sendPackets("기억 장소를 교체할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** SQL-only helper. Source item ownership/debit is verified by the same
     * enclosing inventory receipt; the returned DTOs are installed on the CIO. */
    public static ArrayList<L1BookMark> replaceItemBookmarks(Connection con, int charId,
            int markLimit, int objectId) throws SQLException {
        l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con,
                "characters", "character_teleport", "character_teleport_item");
        int capacity;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Mark_Count FROM characters WHERE objid=? FOR UPDATE")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Stored bookmark owner no longer exists");
                capacity = Math.min(markLimit, rs.getInt(1));
            }
        }
        ArrayList<L1BookMark> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT name,locx,locy,mapid,randomX,randomY "
                + "FROM character_teleport_item WHERE item_id=? ORDER BY num_id,id FOR UPDATE")) {
            ps.setInt(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (result.size() >= capacity) throw new SQLException("Stored bookmark capacity exceeded");
                    L1BookMark book = new L1BookMark();
                    book.setId(l1j.server.server.IdFactory.getInstance().nextId());
                    book.setCharId(charId); book.setNumId(result.size());
                    book.setTemp_id(book.getNumId()); book.setSpeed_id(-1);
                    book.setName(rs.getString("name")); book.setLocX(rs.getInt("locx"));
                    book.setLocY(rs.getInt("locy")); book.setMapId(rs.getShort("mapid"));
                    book.setRandomX(rs.getInt("randomX")); book.setRandomY(rs.getInt("randomY"));
                    result.add(book);
                }
            }
        }
        if (result.isEmpty()) throw new SQLException("Stored bookmark orb has no persistent destinations");
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_teleport WHERE char_id=?")) {
            ps.setInt(1, charId); ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_teleport "
                + "(id,num_id,speed_id,char_id,name,locx,locy,mapid,randomX,randomY) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            for (L1BookMark book : result) {
                ps.setInt(1, book.getId()); ps.setInt(2, book.getNumId()); ps.setInt(3, -1);
                ps.setInt(4, charId); ps.setString(5, book.getName());
                ps.setInt(6, book.getLocX()); ps.setInt(7, book.getLocY()); ps.setInt(8, book.getMapId());
                ps.setInt(9, book.getRandomX()); ps.setInt(10, book.getRandomY());
                if (ps.executeUpdate() != 1) throw new SQLException("Stored bookmark insert failed");
            }
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_teleport_item WHERE item_id=?")) {
            ps.setInt(1, objectId);
            if (ps.executeUpdate() != result.size()) throw new SQLException("Stored bookmark source changed");
        }
        return result;
    }

    public static void bookmarItemkDB(L1PcInstance pc, L1ItemInstance item) {
        final String sql = "SELECT * FROM character_teleport_item WHERE item_id=? ORDER BY num_id ASC";

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setInt(1, item.getId());

            try (ResultSet rs = pstm.executeQuery()) {
                int i = 0;
                while (rs.next()) {
                    L1ItemBookMark bookmark = new L1ItemBookMark();
                    bookmark.setCharId(rs.getInt("item_id"));
                    bookmark.setId(rs.getInt("id"));
                    bookmark.setNumId(i);
                    bookmark.setTemp_id(i);
                    bookmark.setSpeed_id(rs.getInt("speed_id"));
                    bookmark.setName(rs.getString("name"));
                    bookmark.setLocX(rs.getInt("locx"));
                    bookmark.setLocY(rs.getInt("locy"));
                    bookmark.setMapId(rs.getShort("mapid"));
                    bookmark.setRandomX(rs.getShort("randomX"));
                    bookmark.setRandomY(rs.getShort("randomY"));
                    item.addBookMark(bookmark);
                    i++;
                }
            }
        } catch (SQLException e) {
            _log.log(Level.WARNING, "bookmarks 예외 발생.", e);
        }
    }


	public static synchronized void addBookmark(L1PcInstance pc, L1ItemInstance item, L1BookMark book) {
	    try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            int id;
            try (PreparedStatement ps = con.prepareStatement("SELECT max(id)+1 as newid FROM character_teleport_item");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                id = rs.getInt("newid");
            }
            int numId;
            try (PreparedStatement ps = con.prepareStatement("SELECT max(num_id)+1 as newid FROM character_teleport_item WHERE item_id=?")) {
                ps.setInt(1, pc.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    numId = rs.getInt("newid");
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO character_teleport_item SET id=?,num_id=?,speed_id=?,item_id=?,name=?,locx=?,locy=?,mapid=?")) {
                ps.setInt(1, id);
                ps.setInt(2, numId);
                ps.setInt(3, -1);
                ps.setInt(4, item.getId());
                ps.setString(5, book.getName());
                ps.setInt(6, book.getLocX());
                ps.setInt(7, book.getLocY());
                ps.setInt(8, book.getMapId());
                ps.executeUpdate();
            }
            item._bookmarks.add(new L1ItemBookMark()); 
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "북마크 추가 에러", e);
        }
	}

	public static void deleteBookmarkItem(int itemid) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_teleport_item WHERE item_id=?");
			pstm.setInt(1, itemid);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크의 삭제로 에러가 발생했습니다.", e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

}
