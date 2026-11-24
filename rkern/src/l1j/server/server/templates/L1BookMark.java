package l1j.server.server.templates;
import l1j.server.server.model.character.bookmark.CUserBookmark;
import l1j.server.server.model.network.core.sc.packet.SC_BookMarkLoad;

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
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class L1BookMark {

	private static Logger _log = Logger.getLogger(L1BookMark.class.getName());

	private int _charId;
	private int _id;
	private String _name;
	private int _locX;
	private int _locY;
	private short _mapId;
	private int _randomX;
	private int _randomY;
	private int _speed_id;
	private int _NumId;
	private int _Temp_id;

	public int getSpeed_id() { return _speed_id; }
	public void setSpeed_id(int i) { _speed_id = i; }

	public int getId() { return _id; }
	public void setId(int i) { _id = i; }

	public int getCharId() { return _charId; }
	public void setCharId(int i) { _charId = i; }

	public int getNumId() { return _NumId; }
	public void setNumId(int i) { _NumId = i; }

	public int getTemp_id() { return _Temp_id; }
	public void setTemp_id(int i) { _Temp_id = i; }

	public String getName() { return _name; }
	public void setName(String s) { _name = s; }

	public int getLocX() { return _locX; }
	public void setLocX(int i) { _locX = i; }

	public int getLocY() { return _locY; }
	public void setLocY(int i) { _locY = i; }

	public short getMapId() { return _mapId; }
	public void setMapId(short i) { _mapId = i; }

	public int getRandomX() { return _randomX; }
	public void setRandomX(int i) { _randomX = i; }

	public int getRandomY() { return _randomY; }
	public void setRandomY(int i) { _randomY = i; }

	public L1BookMark() {}

	public static void bookmarkDB(L1PcInstance pc) {
        if (pc != null) applySnapshot(pc, readSnapshot(pc.getId()));
    }

    public static ArrayList<L1BookMark> readSnapshot(int characterId) {
        final ArrayList<L1BookMark> bookmarks = new ArrayList<>();
        // One consistent read, ordered by persistent display position and ID.
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id,char_id,num_id,speed_id,name,locx,locy,mapid,randomX,randomY "
                     + "FROM character_teleport WHERE char_id=? ORDER BY num_id,id")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    L1BookMark book = new L1BookMark();
                    book.setId(rs.getInt("id"));
                    book.setCharId(rs.getInt("char_id"));
                    book.setNumId(bookmarks.size());
                    book.setTemp_id(rs.getInt("num_id"));
                    book.setSpeed_id(rs.getInt("speed_id"));
                    book.setName(rs.getString("name"));
                    book.setLocX(rs.getInt("locx"));
                    book.setLocY(rs.getInt("locy"));
                    book.setMapId(rs.getShort("mapid"));
                    book.setRandomX(rs.getInt("randomX"));
                    book.setRandomY(rs.getInt("randomY"));
                    bookmarks.add(book);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Bookmark DB read failed: " + characterId, e);
        }
        return bookmarks;
    }

    public static void applySnapshot(L1PcInstance pc, ArrayList<L1BookMark> bookmarks) {
        if (pc != null && bookmarks != null) pc.getUserBookmark().OnLoadList(bookmarks);
    }

	public static void insertBookmark(L1BookMark bookmark) {
		if (bookmark == null) return;
		
		String sql = "INSERT INTO character_teleport (id, char_id, name, locx, locy, mapid, randomX, randomY) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		
		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			final int nextId = l1j.server.server.IdFactory.getInstance().nextId();
			bookmark.setId(nextId);

			try (PreparedStatement pstm = con.prepareStatement(sql)) {
				pstm.setInt(1, nextId);
				pstm.setInt(2, bookmark.getCharId());
				pstm.setString(3, bookmark.getName());
				pstm.setInt(4, bookmark.getLocX());
				pstm.setInt(5, bookmark.getLocY());
				pstm.setInt(6, bookmark.getMapId());
				pstm.setInt(7, bookmark.getRandomX());
				pstm.setInt(8, bookmark.getRandomY());
				pstm.executeUpdate();
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크의 추가로 에러가 발생했습니다.", e);
		}
	}

	public static void deleteBookmark(L1PcInstance pc, String s) {
        if (pc != null) pc.getUserBookmark().DeleteSlot(pc, s);
    }

    public static void addBookmark(L1PcInstance pc, String s) {
        if (pc != null) pc.getUserBookmark().SaveSlot(pc, s);
    }

    /** Compatibility for old callers: each layout is now committed at request time. */
    @Deprecated
    public static void WriteBookmark(L1PcInstance pc) { }

    public static void Bookmarkitem(L1PcInstance pc, L1ItemInstance useItem, int obj_id, boolean isChange) {
        if (pc == null || useItem == null || obj_id != useItem.getItemId()
                || pc.isItemTransferBusy() || pc.getTradeID() != 0 || pc.isTradeResolving()) return;
        final int charId = pc.getId(), markLimit = pc.getMark_count();
        final ArrayList<L1BookMark> prepared = new ArrayList<>();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc,
                    CacheD.accountOrderKey("bookmark:" + charId))) {
            if (!tx.debit(useItem, 1)) return;
            tx.sql(con -> prepared.addAll(insertItemBookmarks(con, charId, markLimit, obj_id)));
            tx.onFailure(() -> pc.sendPackets("기억 장소를 저장하지 못했습니다. 구슬은 소비되지 않았습니다."));
            tx.commit(() -> {
                for (L1BookMark book : prepared) pc.addBookMark(book);
                SC_BookMarkLoad.send(pc);
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Bookmark item was not admitted: " + charId, failure);
            pc.sendPackets("기억 장소를 저장할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** Connection-bound SQL only. The caller includes the orb debit and this
     * insertion in one economy receipt; no live PC or socket is accessed here.
     */
    public static ArrayList<L1BookMark> insertItemBookmarks(Connection con, int charId,
            int markLimit, int templateId) throws SQLException {
        if (con.getAutoCommit()) throw new SQLException("Bookmark insertion requires the caller's transaction");
        l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con,
                "characters", "character_teleport");
        int capacity;
        // Lock the persistent owner even when its bookmark list is empty.
        // Competing orb uses serialize before observing capacity/next num_id.
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Mark_Count FROM characters WHERE objid=? FOR UPDATE")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Bookmark owner no longer exists");
                capacity = Math.min(markLimit, rs.getInt(1));
            }
        }
        int count = 0, nextNumber = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id,num_id FROM character_teleport WHERE char_id=? FOR UPDATE")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    count++;
                    int number = rs.getInt("num_id");
                    if (number == Integer.MAX_VALUE) throw new SQLException("Bookmark slot overflow");
                    nextNumber = Math.max(nextNumber, number + 1);
                }
            }
        }
        ArrayList<L1BookMark> prepared = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT name,locx,locy,mapid,randomX,randomY FROM beginner_addteleport WHERE item_obj_id=? ORDER BY num_id ASC")) {
            ps.setInt(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ((long) count + prepared.size() >= capacity || nextNumber == Integer.MAX_VALUE)
                        throw new SQLException("Bookmark capacity exceeded");
                    L1BookMark book = new L1BookMark();
                    book.setId(l1j.server.server.IdFactory.getInstance().nextId());
                    book.setCharId(charId);
                    book.setNumId(nextNumber++);
                    book.setTemp_id(book.getNumId());
                    book.setSpeed_id(-1);
                    book.setName(rs.getString("name"));
                    book.setLocX(rs.getInt("locx"));
                    book.setLocY(rs.getInt("locy"));
                    book.setMapId(rs.getShort("mapid"));
                    book.setRandomX(rs.getInt("randomX"));
                    book.setRandomY(rs.getInt("randomY"));
                    prepared.add(book);
                }
            }
        }
        if (prepared.isEmpty()) throw new SQLException("Bookmark orb has no configured destinations");
        // Plain INSERT: a collision must roll back, never overwrite another PC.
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO character_teleport(id,num_id,speed_id,char_id,name,locx,locy,mapid,randomX,randomY) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            for (L1BookMark book : prepared) {
                ps.setInt(1, book.getId()); ps.setInt(2, book.getNumId()); ps.setInt(3, -1);
                ps.setInt(4, charId); ps.setString(5, book.getName());
                ps.setInt(6, book.getLocX()); ps.setInt(7, book.getLocY()); ps.setInt(8, book.getMapId());
                ps.setInt(9, book.getRandomX()); ps.setInt(10, book.getRandomY());
                if (ps.executeUpdate() != 1) throw new SQLException("Bookmark insert did not create exactly one row");
            }
        }
        return prepared;
    }

	public static ArrayList<L1BookMark> ShowBookmarkitem(L1PcInstance pc, int obj_id) {
		ArrayList<L1BookMark> new_list = new ArrayList<>();
		String sql = "SELECT * FROM beginner_addteleport WHERE item_obj_id = ? ORDER BY num_id ASC";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, obj_id);
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					L1BookMark bookmark = new L1BookMark();
					bookmark.setId(rs.getInt("id"));
					bookmark.setNumId(rs.getInt("num_id"));
					bookmark.setSpeed_id(-1);
					bookmark.setCharId(pc != null ? pc.getId() : 0);
					bookmark.setName(rs.getString("name"));
					bookmark.setLocX(rs.getInt("locx"));
					bookmark.setLocY(rs.getInt("locy"));
					bookmark.setMapId(rs.getShort("mapid"));
					bookmark.setRandomX(rs.getInt("randomX"));
					bookmark.setRandomY(rs.getInt("randomY"));
					new_list.add(bookmark);
				}
			}
		} catch (SQLException e) {
			_log.log(Level.WARNING, "ShowBookmarkitem 예외 발생.", e);
		}
		return new_list;
	}

	public static void deleteBookmarkItem(int obj_id) {
		String sql = "DELETE FROM character_teleport WHERE item_obj_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, obj_id);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크 삭제 에러", e);
		}
	}

	public static void deleteBookmarkItem(L1PcInstance pc) {
        if (pc != null) pc.getUserBookmark().DeleteAllSlots(pc);
    }


	public static void addBookmarkItem(L1PcInstance pc, L1ItemBookMark item) {
        if (pc != null && item != null) pc.getUserBookmark().SaveItemSlot(pc, item);
    }

}