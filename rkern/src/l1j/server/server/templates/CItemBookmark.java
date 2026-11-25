package l1j.server.server.templates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.character.bookmark.CUserBookmark;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class CItemBookmark {

	private static Logger _log = Logger.getLogger(CItemBookmark.class.getName());

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

	public CItemBookmark() {
	}

    /** Replace the owner's bookmarks and consume the physical storage orb in
     * one receipt. Never clear the live list before the SQL is confirmed. */
    public static void UseBookmarkItem(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != 700023 || item.getCount() != 1
                || pc.isItemTransferBusy() || pc.getTradeID() != 0 || pc.isTradeResolving()) return;
        final int charId = pc.getId(), objectId = item.getId(), limit = pc.getMark_count();
        final ArrayList<BookmarkSlot> prepared = new ArrayList<>();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc,
                    l1j.server.server.storage.CacheD.accountOrderKey("bookmark:" + charId))) {
            if (!tx.debit(item, 1)) return;
            tx.sql(con -> prepared.addAll(ReplaceItemSlots(con, charId, limit, objectId)));
            tx.onFailure(() -> pc.sendPackets("기억 장소를 교체하지 못했습니다. 구슬은 소비되지 않았습니다."));
            tx.commit(() -> {
                pc.getUserBookmark().OnLoadList(prepared);
                l1j.server.server.model.network.core.sc.packet.SC_BookMarkLoad.send(pc);
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Stored bookmark orb was not admitted: " + charId, failure);
            pc.sendPackets("기억 장소를 교체할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** SQL-only helper. Source item ownership/debit is verified by the same
     * enclosing inventory receipt; the returned DTOs are installed on the CIO. */
    public static ArrayList<BookmarkSlot> ReplaceItemSlots(Connection con, int charId,
            int markLimit, int objectId) throws SQLException {
        l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con,
                "characters", "character_teleport", "character_teleport_item");
        int capacity;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Mark_Count FROM characters WHERE objid=? FOR UPDATE")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Stored bookmark owner no longer exists");
                capacity = Math.min(CUserBookmark.MAX_SLOT, Math.min(markLimit, rs.getInt(1)));
            }
        }
        ArrayList<BookmarkSlot> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT name,locx,locy,mapid,randomX,randomY,speed_id "
                + "FROM character_teleport_item WHERE item_id=? ORDER BY num_id,id FOR UPDATE")) {
            ps.setInt(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (result.size() >= capacity) throw new SQLException("Stored bookmark capacity exceeded");
                    BookmarkSlot book = new BookmarkSlot();
                    book.setId(l1j.server.server.IdFactory.getInstance().nextId());
                    book.setCharId(charId); book.setNumId(result.size());
                    book.setTemp_id(book.getNumId()); book.setSpeed_id(rs.getInt("speed_id"));
                    book.setName(rs.getString("name")); book.setLocX(rs.getInt("locx"));
                    book.setLocY(rs.getInt("locy")); book.setMapId(rs.getShort("mapid"));
                    book.setRandomX(rs.getInt("randomX")); book.setRandomY(rs.getInt("randomY"));
                    result.add(book);
                }
            }
        }
        if (result.isEmpty()) throw new SQLException("Stored bookmark orb has no persistent destinations");
        int speedMask = 0;
        for (BookmarkSlot b : result) {
            int speed = b.getSpeed_id();
            if (speed < 0 || speed >= CUserBookmark.SHORTCUTS || (speedMask & (1 << speed)) != 0)
                b.setSpeed_id(-1);
            else speedMask |= 1 << speed;
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_teleport WHERE char_id=?")) {
            ps.setInt(1, charId); ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_teleport "
                + "(id,num_id,speed_id,char_id,name,locx,locy,mapid,randomX,randomY) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            for (BookmarkSlot book : result) {
                ps.setInt(1, book.getId()); ps.setInt(2, book.getNumId()); ps.setInt(3, book.getSpeed_id());
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

    public static void LoadItemSlots(L1PcInstance pc, L1ItemInstance item) {
        final String sql = "SELECT * FROM character_teleport_item WHERE item_id=? ORDER BY num_id ASC,id ASC";

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setInt(1, item.getId());

            final ArrayList<CItemBookmark> loaded = new ArrayList<>();
            try (ResultSet rs = pstm.executeQuery()) {
                int i = 0;
                while (rs.next()) {
                    CItemBookmark bookmark = new CItemBookmark();
                    bookmark.setCharId(rs.getInt("item_id"));
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
                    loaded.add(bookmark);
                    i++;
                }
            }
            item.getBookMark().clear();
            item.getBookMark().addAll(loaded);
        } catch (SQLException e) {
            _log.log(Level.WARNING, "bookmarks 예외 발생.", e);
        }
    }


    /** L1-specific bookmark export: the physical token, new orb and every
     * destination row belong to the same durable inventory receipt. */
    public static void SaveItemSlots(L1PcInstance pc, L1ItemInstance source) {
        if (pc == null || source == null || source.getItemId() != 3000208
                || pc.isLogoutStarted() || pc.isDead() || pc.isGhost()
                || pc.isItemTransferBusy() || pc.getTradeID() != 0 || pc.isTradeResolving()
                || pc.getInventory().GetByServerID(source.getId()) != source) return;
        final BookmarkSlot[] slots = pc.getBookMarkArray();
        if (slots.length == 0 || slots.length > CUserBookmark.MAX_SLOT) return;
        Arrays.sort(slots, Comparator.comparingInt(BookmarkSlot::getTemp_id)
                .thenComparingInt(BookmarkSlot::getId));
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("bookmark:" + pc.getId()))) {
            if (!tx.debit(source, 1)) return;
            final L1ItemInstance grant = tx.grant(700023, 1);
            if (grant == null || grant.isStackable() || grant.getCount() != 1)
                throw new IllegalStateException("Saved bookmark orb must be a unique item");
            final int objectId = grant.getId();
            final ArrayList<CItemBookmark> rows = new ArrayList<>(slots.length);
            for (int n = 0; n < slots.length; n++) {
                final BookmarkSlot b = slots[n];
                final CItemBookmark row = new CItemBookmark();
                row.setId(IdFactory.getInstance().nextId());
                row.setCharId(objectId); row.setNumId(n); row.setTemp_id(n);
                row.setName(b.getName()); row.setSpeed_id(b.getSpeed_id());
                row.setLocX(b.getLocX()); row.setLocY(b.getLocY()); row.setMapId(b.getMapId());
                row.setRandomX(b.getRandomX()); row.setRandomY(b.getRandomY());
                rows.add(row);
            }
            tx.sql(con -> {
                CTransaction.requireTransactionalTables(con, "character_teleport_item");
                WriteSavedOrb(con, objectId, rows);
            });
            tx.onFailure(() -> pc.sendPackets("기억 저장에 실패했습니다. 구슬은 소비되지 않았습니다."));
            tx.commit(() -> {
                L1ItemInstance item = pc.getInventory().GetByServerID(objectId);
                if (item != null && item.getItemId() == 700023) {
                    item.getBookMark().clear();
                    item.getBookMark().addAll(rows);
                }
            });
        } catch (RuntimeException e) {
            _log.log(Level.WARNING, "Bookmark export not admitted: " + pc.getId(), e);
            pc.sendPackets("기억 저장을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    static void WriteSavedOrb(Connection con, int objectId, ArrayList<CItemBookmark> rows)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO character_teleport_item(id,num_id,speed_id,item_id,name,locx,locy,mapid,randomX,randomY) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            for (CItemBookmark b : rows) {
                ps.setInt(1, b.getId()); ps.setInt(2, b.getNumId()); ps.setInt(3, b.getSpeed_id());
                ps.setInt(4, objectId); ps.setString(5, b.getName());
                ps.setInt(6, b.getLocX()); ps.setInt(7, b.getLocY()); ps.setInt(8, b.getMapId());
                ps.setInt(9, b.getRandomX()); ps.setInt(10, b.getRandomY());
                if (ps.executeUpdate() != 1) throw new SQLException("Saved bookmark insert failed");
            }
        }
    }

	public static void DeleteItemSlots(int itemid) {
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
