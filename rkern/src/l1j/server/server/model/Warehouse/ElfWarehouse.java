package l1j.server.server.model.Warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;

public class ElfWarehouse extends Warehouse {
	private static final long serialVersionUID = 1L;
	private static Logger _log = Logger.getLogger(ElfWarehouse.class.getName());
	
	public ElfWarehouse(String an) {
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
            assertWarehouseReadsCurrent(ELF_ROWS);
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_elf_warehouse WHERE account_name = ?");
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
			java.util.ArrayList<L1ItemInstance> loaded = new java.util.ArrayList<>();
			L1ItemInstance item = null;
			L1Item itemTemplate  = null;
			while (rs.next()) {
				item = new L1ItemInstance();
				int objectId = rs.getInt("id");
				item.setId(objectId);
				itemTemplate = ItemTable.getInstance().getTemplate(rs.getInt("item_id"));
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
            installLoadedSnapshot(loaded);

    }


	@Override
	public synchronized void insertItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseInsert(ELF_ROWS, item);
	}

	@Override
	public synchronized void updateItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseUpdate(ELF_ROWS, item);
	}

	@Override
	public synchronized void deleteItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || getItem(item.getId()) != item) {
            throw new IllegalArgumentException("Warehouse item is not owned: " + getName());
        }
        submitWarehouseDelete(ELF_ROWS, item);
        _items.remove(item);
	}


}
