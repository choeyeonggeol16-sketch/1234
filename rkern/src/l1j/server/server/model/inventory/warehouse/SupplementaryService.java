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
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;

public class SupplementaryService extends Warehouse {
	private static final long serialVersionUID = 1L;
	protected static Logger _log = Logger.getLogger(SupplementaryService.class
			.getName());

	public SupplementaryService(String an) {
		super(an);
	}

	@Override
	protected int getMax() {
		return Config.MAX_PERSONAL_WAREHOUSE_ITEM;
	}

//	@Override
//	public synchronized boolean checkitem(int itemid, int count) {
//		Connection con = null;
//		PreparedStatement pstm = null;
//		ResultSet rs = null;
//		try {
//			con = L1DatabaseFactory.getInstance().getConnection();
//			pstm = con
//					.prepareStatement("SELECT count FROM character_supplementary_service WHERE account_name = ? AND item_id = ?");
//			pstm.setString(1, getName());
//			pstm.setInt(2, itemid);
//			rs = pstm.executeQuery();
//			while (rs.next()) {
//				if (count <= rs.getInt("count")) {
//					return true;
//				}
//			}
//		} catch (SQLException e) {
//			e.printStackTrace();
//		} finally {
//			SQLUtil.close(rs);
//			SQLUtil.close(pstm);
//			SQLUtil.close(con);
//		}
//		return false;
//	}

	@Override
	public synchronized void loadItems() {
        assertWarehouseActive();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
            assertWarehouseReadsCurrent(SUPPLEMENT_ROWS);
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("SELECT * FROM character_supplementary_service WHERE account_name = ?");
			pstm.setString(1, getName());
			rs = pstm.executeQuery();
			loadItemsFromSnapshot(rs);
		} catch (SQLException e) {
            throw new IllegalStateException("Warehouse load failed: " + getName(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

    public synchronized void loadItemsFromSnapshot(ResultSet rs) throws SQLException {
        if (rs == null) return;
        installLoadedSnapshot(readDetachedSnapshot(rs));
    }

    /** Read detached items only; no warehouse/world mutation or JDBC writes. */
    public static java.util.List<L1ItemInstance> readDetachedSnapshot(ResultSet rs) throws SQLException {
        final WarehouseOptionColumns optionColumns = WarehouseOptionColumns.read(rs);
			java.util.ArrayList<L1ItemInstance> loaded = new java.util.ArrayList<>();
			L1ItemInstance item = null;
			L1Item itemTemplate = null;
			while (rs.next()) {
				item = new L1ItemInstance();
				int objectId = rs.getInt("id");
				item.setId(objectId);
				itemTemplate = CObjectDB.GetItem(rs.getInt("item_id"));				
                if (itemTemplate == null) throw new SQLException("Warehouse item template missing: " + objectId);
				item.setItem(itemTemplate);
				item.setCount(rs.getInt("count"));
                if (item.getCount() <= 0) throw new SQLException("Invalid warehouse count: " + objectId);
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
				item.set_bless_level2(optionColumns.readBlessingQuality(rs));
				

                loaded.add(item);
			}
        return loaded;
    }


	@Override
	public synchronized void insertItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseInsert(SUPPLEMENT_ROWS, item);
	}

	@Override
	public synchronized void updateItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseUpdate(SUPPLEMENT_ROWS, item);
	}

	@Override
	public synchronized void deleteItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || GetByServerID(item.getId()) != item) {
            throw new IllegalArgumentException("Warehouse item is not owned: " + getName());
        }
        submitWarehouseDelete(SUPPLEMENT_ROWS, item);
        _items.remove(item);
	}


}
