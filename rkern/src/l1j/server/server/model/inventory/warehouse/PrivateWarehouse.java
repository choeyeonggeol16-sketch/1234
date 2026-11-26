package l1j.server.server.model.inventory.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;

public class PrivateWarehouse extends Warehouse {
	private static final long serialVersionUID = 1L;
	protected static Logger _log = Logger.getLogger(PrivateWarehouse.class.getName());
	
	public PrivateWarehouse(String an) {
		super(an);
	}
	
	@Override
	protected int getMax() {
		return Config.MAX_PERSONAL_WAREHOUSE_ITEM;
	}
	
	@Override
	public synchronized void loadItems() {
        assertWarehouseActive();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
            if (!CacheD.getInstance().flushDbOwner(CacheD.accountOrderKey(getName()), 0L)) {
                throw new SQLException("Pending warehouse writes not committed for " + getName());
            }
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_warehouse WHERE account_name = ?");
			pstm.setString(1, getName());
			rs = pstm.executeQuery();
			loadItemsFromSnapshot(rs);
		} catch (SQLException e) {
			throw new IllegalStateException("PrivateWarehouse load failed: account=" + getName(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

    public synchronized void loadItemsFromSnapshot(ResultSet rs) throws SQLException {
        assertWarehouseActive();
        if (rs == null) return;
        installLoadedSnapshot(readDetachedSnapshot(rs));
    }

    /** Read detached items only; no warehouse/world mutation or JDBC writes. */
    public static java.util.List<L1ItemInstance> readDetachedSnapshot(ResultSet rs) throws SQLException {
			java.util.ArrayList<L1ItemInstance> loaded = new java.util.ArrayList<>();
			L1ItemInstance item = null;
			L1Item itemTemplate = null;
			while (rs.next()) {
				item = new L1ItemInstance();
				int objectId = rs.getInt("id");
				item.setId(objectId);
				itemTemplate = CObjectDB.GetItem(rs.getInt("item_id"));
                if (itemTemplate == null) {
                    throw new SQLException("Warehouse item template missing: " + objectId);
                }
				item.setItem(itemTemplate);
				item.setCount(rs.getInt("count"));
				item.setEquipped(false);
				item.setEnchantLevel(rs.getInt("enchantlvl"));
				item.setIdentified(rs.getInt("is_id") != 0 ? true : false);
				item.set_durability(rs.getInt("durability"));
				item.setChargeCount(rs.getInt("charge_count"));
				item.setRemainingTime(rs.getInt("remaining_time"));
				item.setLastUsed(rs.getTimestamp("last_used"));
				item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
				item.setSpecialEnchant(rs.getInt("special_enchant"));
                item.setPackage(rs.getInt("package") != 0);
				item.setBless(rs.getInt("bless"));
				item.set_bless_level(rs.getInt("bless_level"));
				/**특수 인챈트 시스템**/
				item.set_item_level(rs.getInt("item_level"));
				/**여관 리뉴얼**/
				item.setHotel_Town(rs.getString("Hotel_Town"));
				item.setEndTime(rs.getTimestamp("end_time"));
                // Loading must not perform new JDBC deletes/updates. Keep each
                // persisted item row intact (including duplicate ADENA IDs).
                // The old code deleted a duplicate before verifying that its
                // combined count was representable, permanently losing it.
                if (item.getCount() <= 0) {
                    throw new SQLException("Invalid warehouse amount: " + objectId);
                }
                loaded.add(item);
			}
        return loaded;
    }


    private static final class Row {
        final int id, itemId, count, enchant, identified, durability, charge, remaining;
        final int attr, bless, special, pack, blessLevel, itemLevel;
        final String name, hotel, account;
        final java.sql.Timestamp used, end;

        Row(String account, L1ItemInstance item) {
            this.account = account;
            id = item.getId();
            itemId = item.getItemId();
            name = item.getName();
            count = item.getCount();
            enchant = item.getEnchantLevel();
            identified = item.isIdentified() ? 1 : 0;
            durability = item.get_durability();
            charge = item.getChargeCount();
            remaining = item.getRemainingTime();
            attr = item.getAttrEnchantLevel();
            bless = item.getBless();
            special = item.getSpecialEnchant();
            pack = item.isPackage() ? 1 : 0;
            blessLevel = item.get_bless_level();
            itemLevel = item.get_item_level();
            hotel = item.getHotel_Town();
            used = copyTimestamp(item.getLastUsed());
            end = copyTimestamp(item.getEndTime());
        }
    }

    private static java.sql.Timestamp copyTimestamp(java.sql.Timestamp t) {
        if (t == null) return null;
        java.sql.Timestamp copy = new java.sql.Timestamp(t.getTime());
        copy.setNanos(t.getNanos());
        return copy;
    }

    private static void insertMoveRow(Connection con, Row row) throws SQLException {
        final String sql = "INSERT INTO character_warehouse SET id=?,account_name=?,item_id=?,item_name=?,count=?,"
                + "is_equipped=0,enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,"
                + "last_used=?,attr_enchantlvl=?,bless=?,special_enchant=?,package=?,bless_level=?,"
                + "item_level=?,Hotel_Town=?,end_time=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 0;
            ps.setInt(++i, row.id);
            ps.setString(++i, row.account);
            ps.setInt(++i, row.itemId);
            ps.setString(++i, row.name);
            ps.setInt(++i, row.count);
            ps.setInt(++i, row.enchant);
            ps.setInt(++i, row.identified);
            ps.setInt(++i, row.durability);
            ps.setInt(++i, row.charge);
            ps.setInt(++i, row.remaining);
            ps.setTimestamp(++i, row.used);
            ps.setInt(++i, row.attr);
            ps.setInt(++i, row.bless);
            ps.setInt(++i, row.special);
            ps.setInt(++i, row.pack);
            ps.setInt(++i, row.blessLevel);
            ps.setInt(++i, row.itemLevel);
            ps.setString(++i, row.hotel);
            ps.setTimestamp(++i, row.end);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Private warehouse transfer INSERT failed id=" + row.id);
        }
    }

    private static void verifyMoveRow(Connection con, String account, int id,
                                      int expectedItemId, int expectedCount) throws SQLException {
        if (expectedCount <= 0) throw new SQLException("Missing private warehouse original count: " + id);
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_id,count FROM character_warehouse WHERE id=? AND account_name=? FOR UPDATE")) {
            ps.setInt(1, id);
            ps.setString(2, account);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != expectedItemId || rs.getInt(2) != expectedCount)
                    throw new SQLException("Private warehouse move source/type/count mismatch: " + id);
            }
        }
    }

    private static void updateMoveRow(Connection con, String account, int id,
                                      int itemId, int previousCount, int count) throws SQLException {
        verifyMoveRow(con, account, id, itemId, previousCount);
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE character_warehouse SET count=? WHERE id=? AND account_name=?"
                + " AND item_id=? AND count=?")) {
            ps.setInt(1, count);
            ps.setInt(2, id);
            ps.setString(3, account);
            ps.setInt(4, itemId);
            ps.setInt(5, previousCount);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Private warehouse move count/owner changed: " + id);
        }
    }

    private static void deleteMoveRow(Connection con, String account, int id, int itemId,
                                      int previousCount) throws SQLException {
        verifyMoveRow(con, account, id, itemId, previousCount);
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM character_warehouse WHERE id=? AND account_name=?")) {
            ps.setInt(1, id);
            ps.setString(2, account);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Private warehouse move delete not applied: " + id);
        }
    }

    private static void insertRow(Row row) {
        final String sql = "INSERT INTO character_warehouse SET id=?,account_name=?,item_id=?,item_name=?,count=?,"
                + "is_equipped=0,enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,"
                + "last_used=?,attr_enchantlvl=?,bless=?,special_enchant=?,package=?,bless_level=?,"
                + "item_level=?,Hotel_Town=?,end_time=?";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 0;
            ps.setInt(++i, row.id);
            ps.setString(++i, row.account);
            ps.setInt(++i, row.itemId);
            ps.setString(++i, row.name);
            ps.setInt(++i, row.count);
            ps.setInt(++i, row.enchant);
            ps.setInt(++i, row.identified);
            ps.setInt(++i, row.durability);
            ps.setInt(++i, row.charge);
            ps.setInt(++i, row.remaining);
            ps.setTimestamp(++i, row.used);
            ps.setInt(++i, row.attr);
            ps.setInt(++i, row.bless);
            ps.setInt(++i, row.special);
            ps.setInt(++i, row.pack);
            ps.setInt(++i, row.blessLevel);
            ps.setInt(++i, row.itemLevel);
            ps.setString(++i, row.hotel);
            ps.setTimestamp(++i, row.end);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Replays after an ambiguous COMMIT may encounter the same row.
            // Only treat the row as already stored when owner, item and count
            // match; never change an unrelated account's existing row.
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT account_name,item_id,count,enchantlvl,is_id,durability,charge_count,"
                                 + "remaining_time,attr_enchantlvl,bless,special_enchant,package,"
                                 + "bless_level,item_level,Hotel_Town,last_used,end_time "
                                 + "FROM character_warehouse WHERE id=?")) {
                ps.setInt(1, row.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && row.account.equals(rs.getString(1))
                            && row.itemId == rs.getInt(2) && row.count == rs.getInt(3)
                            && row.enchant == rs.getInt(4) && row.identified == rs.getInt(5)
                            && row.durability == rs.getInt(6) && row.charge == rs.getInt(7)
                            && row.remaining == rs.getInt(8) && row.attr == rs.getInt(9)
                            && row.bless == rs.getInt(10) && row.special == rs.getInt(11)
                            && row.pack == rs.getInt(12) && row.blessLevel == rs.getInt(13)
                            && row.itemLevel == rs.getInt(14)
                            && java.util.Objects.equals(row.hotel, rs.getString(15))
                            && java.util.Objects.equals(row.used, rs.getTimestamp(16))
                            && java.util.Objects.equals(row.end, rs.getTimestamp(17))) return;
                }
            } catch (SQLException checkFailure) {
                e.addSuppressed(checkFailure);
            }
            throw new IllegalStateException("Warehouse INSERT unresolved: account=" + row.account
                    + " item=" + row.id, e);
        }
    }

    private static void updateRow(String account, int id, int count) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE character_warehouse SET count=? WHERE id=? AND account_name=?")) {
            ps.setInt(1, count);
            ps.setInt(2, id);
            ps.setString(3, account);
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT count FROM character_warehouse WHERE id=? AND account_name=?")) {
                    check.setInt(1, id);
                    check.setString(2, account);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next() || rs.getInt(1) != count)
                            throw new SQLException("Warehouse UPDATE missing or mismatched: " + id);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Warehouse UPDATE failed: account=" + account + " id=" + id, e);
        }
    }

    private static void deleteRow(String account, int id) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM character_warehouse WHERE id=? AND account_name=?")) {
            ps.setInt(1, id);
            ps.setString(2, account);
            // A replay after a successful DELETE may affect zero rows. But a
            // row belonging to another account must never be acknowledged.
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT account_name FROM character_warehouse WHERE id=?")) {
                    check.setInt(1, id);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            throw new SQLException("Warehouse DELETE ownership mismatch: " + id);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Warehouse DELETE failed: account=" + account + " id=" + id, e);
        }
    }

    @Override
    public synchronized void insertItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || item.getId() <= 0 || item.getCount() <= 0) {
            throw new IllegalArgumentException("Invalid warehouse item");
        }
        final Row snapshot = new Row(getName(), item);
        if (CacheD.stageWarehouseMoveSql(con -> insertMoveRow(con, snapshot))) return;
        CacheD.getInstance().submitDbJobRetryOrdered(CacheD.accountOrderKey(snapshot.account),
                () -> insertRow(snapshot));
    }

    @Override
    public synchronized void updateItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || item.getId() <= 0 || item.getCount() <= 0) {
            throw new IllegalArgumentException("Invalid warehouse item count");
        }
        final String account = getName();
        final int id = item.getId();
        final int count = item.getCount();
        final int itemId = item.getItemId();
        final int previousCount = CacheD.warehouseMoveOriginalCount(id);
        if (CacheD.stageWarehouseMoveSql(con -> updateMoveRow(con, account, id, itemId, previousCount, count))) return;
        CacheD.getInstance().submitDbJobRetryOrdered(CacheD.accountOrderKey(account),
                () -> updateRow(account, id, count));
    }

    synchronized void preparePrivateTransferDelete(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || GetByServerID(item.getId()) != item)
            throw new IllegalStateException("Private warehouse move stale source");
        final String owner = getName();
        final int id = item.getId(), itemId = item.getItemId();
        final int before = CacheD.warehouseMoveOriginalCount(id);
        if (!CacheD.stageWarehouseMoveSql(con -> deleteMoveRow(con, owner, id, itemId, before)))
            throw new IllegalStateException("No warehouse SQL transaction scope");
    }

    @Override
    public synchronized void deleteItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || GetByServerID(item.getId()) != item) {
            throw new IllegalArgumentException("Warehouse item is not owned by account=" + getName());
        }
        final String account = getName();
        final int id = item.getId();
        final int itemId = item.getItemId();
        final int previousCount = CacheD.warehouseMoveOriginalCount(id);
        if (CacheD.stageWarehouseMoveSql(con -> deleteMoveRow(con, account, id, itemId, previousCount))) {
            _items.remove(item);
            return;
        }
        CacheD.getInstance().submitDbJobRetryOrdered(CacheD.accountOrderKey(account),
                () -> deleteRow(account, id));
        _items.remove(item);
    }
}
