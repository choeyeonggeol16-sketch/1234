/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.Warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;

public class ClanWarehouse extends Warehouse {
	private static final long serialVersionUID = 1L;
	private volatile int _warehouse;
	public ClanWarehouse(String clan) {
		super(clan);
	}
	
	@Override
	protected int getMax() {
		return Config.MAX_CLAN_WAREHOUSE_ITEM;
	}
	
	@Override
	public synchronized void loadItems() {
        assertWarehouseActive();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
            assertWarehouseReadsCurrent(CLAN_ROWS);
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM clan_warehouse WHERE clan_name = ?");
			pstm.setString(1, getName());
			rs = pstm.executeQuery();
			java.util.ArrayList<L1ItemInstance> loaded = new java.util.ArrayList<>();
			L1ItemInstance item = null;
			L1Item itemTemplate  = null;
			while (rs.next()) {
				item = new L1ItemInstance();
				int objectId = rs.getInt("id");
				item.setId(objectId);
				int itemId = rs.getInt("item_id");
				itemTemplate = ItemTable.getInstance().getTemplate(itemId);
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
				item.setBless(item.getItem().getBless());
				item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
				item.setSpecialEnchant(rs.getInt("special_enchant"));
                item.setPackage(rs.getInt("package") != 0);
				item.set_bless_level(rs.getInt("bless_level"));
				/**특수 인챈트 시스템**/
				item.set_item_level(rs.getInt("item_level"));
				/**여관 리뉴얼**/
				item.setHotel_Town(rs.getString("Hotel_Town"));
				item.setEndTime(rs.getTimestamp("end_time"));
                loaded.add(item);
			}
            installLoadedSnapshot(loaded);
		} catch (SQLException e) {
            throw new IllegalStateException("Warehouse load failed: " + getName(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	@Override
	public synchronized void insertItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseInsert(CLAN_ROWS, item);
	}

	@Override
	public synchronized void updateItem(L1ItemInstance item) {
        assertWarehouseActive();
        submitWarehouseUpdate(CLAN_ROWS, item);
	}

	@Override
	public synchronized void deleteItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || getItem(item.getId()) != item) {
            throw new IllegalArgumentException("Warehouse item is not owned: " + getName());
        }
        submitWarehouseDelete(CLAN_ROWS, item);
        _items.remove(item);
	}

	public synchronized void deleteAllItems() {
        submitWarehouseDeleteAll(CLAN_ROWS);
	}

	public int getWarehouseUsingChar() {
		return _warehouse;
	}

	public synchronized boolean releaseWarehouseUser(int ownerId) {
		if (ownerId <= 0 || _warehouse != ownerId) return false;
		_warehouse = 0;
		return true;
	}

	public boolean setWarehouseUsingChar(int objid, int prevOwner) {
		synchronized (this) {
			if (objid == _warehouse) {
				return true;
			}

			if (objid != 0 && _warehouse != 0) {
				if (prevOwner != _warehouse)
					return false;
			}

			_warehouse = objid;

			return true;
		}
	}
}
