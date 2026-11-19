package l1j.server.server.templates;
import l1j.server.server.model.network.core.sc.packet.SC_Bookmarks;

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
		if (pc == null) return;
		
		String sql1 = "SELECT * FROM character_teleport WHERE char_id = ? ORDER BY num_id ASC";
		String sql2 = "SELECT * FROM character_teleport WHERE char_id = ? AND speed_id > -1 ORDER BY speed_id ASC";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql1)) {
			
			pstm.setInt(1, pc.getId());
			try (ResultSet rs = pstm.executeQuery()) {
				int i = 0;
				while (rs.next()) {
					L1BookMark bookmark = new L1BookMark();
					bookmark.setCharId(rs.getInt("char_id"));
					bookmark.setId(rs.getInt("id"));
					bookmark.setNumId(i);
					bookmark.setTemp_id(i);
					bookmark.setSpeed_id(rs.getInt("speed_id"));
					bookmark.setName(rs.getString("name"));
					bookmark.setLocX(rs.getInt("locx"));
					bookmark.setLocY(rs.getInt("locy"));
					bookmark.setMapId(rs.getShort("mapid"));
					bookmark.setRandomX(rs.getInt("randomX"));
					bookmark.setRandomY(rs.getInt("randomY"));
					pc._bookmarks.add(bookmark);
					i++;
				}
			}

			try (PreparedStatement pstm1 = con.prepareStatement(sql2)) {
				pstm1.setInt(1, pc.getId());
				try (ResultSet rs1 = pstm1.executeQuery()) {
					while (rs1.next()) {
						int numId = rs1.getInt("num_id");
						if (numId >= 0 && numId < pc._bookmarks.size()) {
							L1BookMark bm = pc._bookmarks.get(numId);
							if (bm != null) {
								pc._speedbookmarks.add(bm);
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			_log.log(Level.WARNING, "bookmarks 예외 발생.", e);
		}
	}

    public static ArrayList<L1BookMark> readSnapshot(int characterId) {
        ArrayList<L1BookMark> bookmarks = new ArrayList<>();
        ArrayList<Integer> speedIndices = new ArrayList<>();
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM character_teleport WHERE char_id = ? ORDER BY num_id ASC")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    int i = 0;
                    while (rs.next()) {
                        L1BookMark bookmark = new L1BookMark();
                        bookmark.setCharId(rs.getInt("char_id"));
                        bookmark.setId(rs.getInt("id"));
                        bookmark.setNumId(i);
                        bookmark.setTemp_id(i);
                        bookmark.setSpeed_id(rs.getInt("speed_id"));
                        bookmark.setName(rs.getString("name"));
                        bookmark.setLocX(rs.getInt("locx"));
                        bookmark.setLocY(rs.getInt("locy"));
                        bookmark.setMapId(rs.getShort("mapid"));
                        bookmark.setRandomX(rs.getInt("randomX"));
                        bookmark.setRandomY(rs.getInt("randomY"));
                        bookmarks.add(bookmark);
                        i++;
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT num_id FROM character_teleport WHERE char_id = ? AND speed_id > -1 ORDER BY speed_id ASC")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) speedIndices.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Bookmark DB read failed: " + characterId, e);
        }
        // Preserve original speed-bookmark ORDER BY speed_id and num_id indexing.
        // Second list is encoded as negative-index markers after the bookmarks.
        final int originalCount = bookmarks.size();
        for (int i = 0; i < speedIndices.size(); i++) {
            int index = speedIndices.get(i);
            if (index >= 0 && index < originalCount) {
                L1BookMark bookmark = bookmarks.get(index);
                if (bookmark != null) bookmarks.add(bookmark);
            }
        }
        return bookmarks;
    }

    public static void applySnapshot(L1PcInstance pc, ArrayList<L1BookMark> bookmarks) {
        if (pc == null || bookmarks == null) return;
        // The same bookmark may appear in speed entries; avoid inserting duplicates.
        int originalCount = 0;
        for (int i = 0, n = bookmarks.size(); i < n; i++) {
            L1BookMark bookmark = bookmarks.get(i);
            if (bookmark.getNumId() == originalCount) originalCount++;
            else break;
        }
        for (int i = 0; i < originalCount; i++) pc._bookmarks.add(bookmarks.get(i));
        for (int i = originalCount, n = bookmarks.size(); i < n; i++) pc._speedbookmarks.add(bookmarks.get(i));
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
        if (pc != null && pc.isItemTransferBusy()) return;
		if (pc == null || s == null) return;
		L1BookMark book = pc.getBookMark(s);
		if (book == null) return;

		String sql = "DELETE FROM character_teleport WHERE id = ? AND char_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, book.getId());
			pstm.setInt(2, pc.getId());
			pstm.execute();

			int del_id = book.getNumId();
			for (L1BookMark books : pc.getBookMarkArray()) {
				if (book == books) {
					pc._bookmarks.remove(book);
				}
				if (books.getNumId() > del_id) {
					books.setNumId(books.getNumId() - 1);
					books.setTemp_id(books.getTemp_id() - 1);
				}
			}
			if (pc._speedbookmarks.contains(book)) {
				int speedDelId = book.getSpeed_id();
				for (L1BookMark books : pc.getSpeedBookMarkArray()) {
					if (book == books) {
						pc._speedbookmarks.remove(book);
					}
					if (books.getSpeed_id() > speedDelId) {
						books.setSpeed_id(books.getSpeed_id() - 1);
					}
				}
			}

			WriteBookmark(pc);
			pc._bookmarks.clear();
			pc._speedbookmarks.clear();
			bookmarkDB(pc);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크의 삭제로 에러가 발생했습니다.", e);
		}
	}

	public static void addBookmark(L1PcInstance pc, String s) {
        if (pc != null && pc.isItemTransferBusy()) return;
		if (pc == null) return;
		if (!pc.getMap().isMarkable() && !pc.isGm()) {
			SC_ServerMessage.send(pc, 214);
			return;
		}
		if (pc._bookmarks.size() >= pc.getMark_count()) {
			if (pc.getMark_count() == 100) {
				SC_ServerMessage.send(pc, 2930);
				return;
			} else {
				SC_ServerMessage.send(pc, 676);
				return;
			}
		}
		if (pc.getBookMark(s) != null) {
			SC_ServerMessage.send(pc, 1655); // 같은 기억명으로 저장할 수 없습니다.
			return;
		}

		String numSql = "SELECT COALESCE(MAX(num_id), -1) + 1 AS new_num FROM character_teleport WHERE char_id = ?";
		String insertSql = "INSERT INTO character_teleport (id, num_id, speed_id, char_id, name, locx, locy, mapid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			int numId = 0;
			try (PreparedStatement pstm = con.prepareStatement(numSql)) {
				pstm.setInt(1, pc.getId());
				try (ResultSet rs = pstm.executeQuery()) {
					if (rs.next()) numId = rs.getInt("new_num");
				}
			}

			final int nextId = l1j.server.server.IdFactory.getInstance().nextId();

			try (PreparedStatement pstm = con.prepareStatement(insertSql)) {
				pstm.setInt(1, nextId);
				pstm.setInt(2, numId);
				pstm.setInt(3, -1);
				pstm.setInt(4, pc.getId());
				pstm.setString(5, s);
				pstm.setInt(6, pc.getX());
				pstm.setInt(7, pc.getY());
				pstm.setInt(8, pc.getMapId());
				pstm.execute();
			}

			L1BookMark bookmark = new L1BookMark();
			bookmark.setId(nextId);
			bookmark.setNumId(numId);
			bookmark.setTemp_id(numId);
			bookmark.setSpeed_id(-1);
			bookmark.setCharId(pc.getId());
			bookmark.setName(s);
			bookmark.setLocX(pc.getX());
			bookmark.setLocY(pc.getY());
			bookmark.setMapId(pc.getMapId());

			pc._bookmarks.add(bookmark);
			SC_Bookmarks.send(pc, s, bookmark.getMapId(), bookmark.getLocX(), bookmark.getLocY(), bookmark.getNumId());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크의 추가로 에러가 발생했습니다.", e);
		}
	}

    public static void WriteBookmark(L1PcInstance pc) {
        if (pc != null && pc.isItemTransferBusy()) return;
        if (pc == null || pc.getBookMarkSize() <= 0) return;
        final int charId = pc.getId();
        final int[] values = new int[pc._bookmarks.size() * 3];
        int count = 0;
        for (int i = 0, n = pc._bookmarks.size(); i < n; i++) {
            L1BookMark bm = pc._bookmarks.get(i);
            if (bm != null && bm.getTemp_id() > 0) {
                values[count++] = bm.getTemp_id();
                values[count++] = pc._speedbookmarks.contains(bm) ? bm.getSpeed_id() : -1;
                values[count++] = bm.getId();
            }
        }
        if (count == 0) return;
        final int length = count;
        CacheD.getInstance().submitDbJobOrdered(charId, () -> {
            final String sql = "UPDATE character_teleport SET num_id = ?, speed_id = ? WHERE id = ? AND char_id = ?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                    PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < length; i += 3) {
                    ps.setInt(1, values[i]);
                    ps.setInt(2, values[i + 1]);
                    ps.setInt(3, values[i + 2]);
                    ps.setInt(4, charId);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                _log.log(Level.WARNING, "WriteBookmark error: " + charId, e);
                throw new IllegalStateException("Bookmark DB save failed: " + charId, e);
            }
        });
    }

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
                for (L1BookMark book : prepared) {
                    pc.addBookMark(book);
                    SC_Bookmarks.send(pc, book.getName(), book.getMapId(), book.getLocX(), book.getLocY(), book.getId());
                }
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
        if (pc != null && pc.isItemTransferBusy()) return;
		if (pc == null) return;
		String sql = "DELETE FROM character_teleport WHERE char_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, pc.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크 삭제 에러", e);
		}
	}


	public static void addBookmarkItem(L1PcInstance pc, L1ItemBookMark item) {
        if (pc != null && pc.isItemTransferBusy()) return;
		if (pc == null || item == null) return;
		
		String numSql = "SELECT COALESCE(MAX(num_id), -1) + 1 AS new_num FROM character_teleport WHERE char_id = ?";
		String insertSql = "INSERT INTO character_teleport (id, num_id, speed_id, char_id, name, locx, locy, mapid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			int numId = 0;
			try (PreparedStatement pstm = con.prepareStatement(numSql)) {
				pstm.setInt(1, pc.getId());
				try (ResultSet rs = pstm.executeQuery()) {
					if (rs.next()) numId = rs.getInt("new_num");
				}
			}

			final int nextId = l1j.server.server.IdFactory.getInstance().nextId();

			try (PreparedStatement pstm = con.prepareStatement(insertSql)) {
				pstm.setInt(1, nextId);
				pstm.setInt(2, numId);
				pstm.setInt(3, -1);
				pstm.setInt(4, pc.getId());
				pstm.setString(5, item.getName());
				pstm.setInt(6, item.getLocX());
				pstm.setInt(7, item.getLocY());
				pstm.setInt(8, item.getMapId());
				pstm.execute();
			}

			L1BookMark bookmark = new L1BookMark();
			bookmark.setId(nextId);
			bookmark.setNumId(numId);
			bookmark.setTemp_id(numId);
			bookmark.setSpeed_id(-1);
			bookmark.setCharId(pc.getId());
			bookmark.setName(item.getName());
			bookmark.setLocX(item.getLocX());
			bookmark.setLocY(item.getLocY());
			bookmark.setMapId(item.getMapId());
			pc._bookmarks.add(bookmark);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "북마크 아이템 추가 에러", e);
		}
	}
}