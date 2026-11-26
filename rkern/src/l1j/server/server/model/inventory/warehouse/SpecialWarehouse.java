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

public class SpecialWarehouse extends Warehouse {
	private static final long serialVersionUID = 1L;
	protected static Logger _log = Logger.getLogger(SpecialWarehouse.class.getName());
	
	public SpecialWarehouse(String an) {
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
            assertWarehouseReadsCurrent(SPECIAL_ROWS);
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_special_warehouse WHERE char_name = ?");
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
			java.util.ArrayList<L1ItemInstance> loaded = new java.util.ArrayList<>();
			L1ItemInstance item = null;
			L1Item itemTemplate = null;
			while (rs.next()) {
				item = new L1ItemInstance();
				int objectId = rs.getInt("id");
				item.setId(objectId);
				itemTemplate = CObjectDB.GetItem(rs.getInt("item_id"));				
                if (itemTemplate == null) throw new SQLException("Missing item template id=" + objectId);
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
                if (itemTemplate == null || item.getCount() <= 0) {
                    throw new SQLException("Invalid warehouse row id=" + objectId);
                }
                loaded.add(item);
			}
        return loaded;
    }


	@Override
	public synchronized void insertItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseInsert(SPECIAL_ROWS, item);
	}

	@Override
	public synchronized void updateItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseUpdate(SPECIAL_ROWS, item);
	}

	@Override
	public synchronized void deleteItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || GetByServerID(item.getId()) != item) {
            throw new IllegalArgumentException("Warehouse item is not owned: " + getName());
        }
        submitWarehouseDelete(SPECIAL_ROWS, item);
        _items.remove(item);
	}


}
