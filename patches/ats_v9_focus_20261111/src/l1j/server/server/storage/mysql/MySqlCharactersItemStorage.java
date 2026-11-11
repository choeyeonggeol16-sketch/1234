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
package l1j.server.server.storage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.utils.SQLUtil;

public class MySqlCharactersItemStorage extends CharactersItemStorage {

	private static final Logger _log = Logger.getLogger(MySqlCharactersItemStorage.class.getName());

	@Override
	public ArrayList<ItemInstance> loadItems(int objId) throws Exception {
		ArrayList<ItemInstance> items = null;

		Connection con = null; 
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			items = new ArrayList<ItemInstance>();
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_items WHERE char_id = ?");
			pstm.setInt(1, objId);

			ItemInstance item;
			rs = pstm.executeQuery();
			while (rs.next()) {
				int itemId = rs.getInt("item_id");
				L1Item itemTemplate = ItemTable.getInstance().getTemplate(itemId);
				if (itemTemplate == null) {
					_log.warning(String.format("item id:%d not found", itemId));
					continue;
				}
				item = new ItemInstance();
				item.setId(rs.getInt("id"));
				item.setItem(itemTemplate);
				item.setCount(rs.getInt("count"));
				item.setEquipped(rs.getInt("Is_equipped") != 0 ? true : false);
				item.setEnchantLevel(rs.getInt("enchantlvl"));
				item.setIdentified(rs.getInt("is_id") != 0 ? true : false);
				item.setDurability(rs.getInt("durability"));
				item.setChargeCount(rs.getInt("charge_count"));
				item.setRemainingTime(rs.getInt("remaining_time"));
				item.setLastUsed(rs.getTimestamp("last_used"));
				item.setBless(rs.getInt("bless"));
				item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
				item.setEndTime(rs.getTimestamp("end_time"));
				item.setBlessType(rs.getInt("bless_type"));
				item.setBlessTypeValue(rs.getInt("bless_type_value"));
				item.getLastStatus().updateAll();
				items.add(item);
			}
		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return items;
	}

	@Override
	public void storeItem(int objId, ItemInstance item) throws Exception {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"INSERT INTO character_items SET id = ?, item_id = ?, char_id = ?, item_name = ?, count = ?, is_equipped = 0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?, last_used = ?, bless = ?, attr_enchantlvl = ?, end_time=?,bless_type=?,bless_type_value=?");
			pstm.setInt(1, item.getId());
			pstm.setInt(2, item.getItem().getItemId());
			pstm.setInt(3, objId);
			pstm.setString(4, item.getItem().getName());
			pstm.setInt(5, item.getCount());
			pstm.setInt(6, item.getEnchantLevel());
			pstm.setInt(7, item.isIdentified() ? 1 : 0);
			pstm.setInt(8, item.getDurability());
			pstm.setInt(9, item.getChargeCount());
			pstm.setInt(10, item.getRemainingTime());
			pstm.setTimestamp(11, item.getLastUsed());
			pstm.setInt(12, item.getBless());
			pstm.setInt(13, item.getAttrEnchantLevel());
			pstm.setTimestamp(14, item.getEndTime());
			pstm.setInt(15, item.getBlessType());
			pstm.setInt(16, item.getBlessTypeValue());
			pstm.execute();

		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		item.getLastStatus().updateAll();
	}

	@Override
	public void deleteItem(ItemInstance item) throws Exception {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_items WHERE id = ?");
			pstm.setInt(1, item.getId());
			pstm.execute();
		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	@Override
	public void updateItemId(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET item_id = ? WHERE id = ?", item.getItemId());
		item.getLastStatus().updateItemId();
	}

	@Override
	public void updateItemCount(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET count = ? WHERE id = ?", item.getCount());
		item.getLastStatus().updateCount();
	}

	@Override
	public void updateItemDurability(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET durability = ? WHERE id = ?", item.getDurability());
		item.getLastStatus().updateDuraility();
	}

	@Override
	public void updateItemChargeCount(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET charge_count = ? WHERE id = ?", item.getChargeCount());
		item.getLastStatus().updateChargeCount();
	}

	@Override
	public void updateItemRemainingTime(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET remaining_time = ? WHERE id = ?",
				item.getRemainingTime());
		item.getLastStatus().updateRemainingTime();
	}
	@Override
	public void updateItemEnchantLevel(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET enchantlvl = ? WHERE id = ?", item.getEnchantLevel());
		item.getLastStatus().updateEnchantLevel();
	}

	@Override
	public void updateItemEquipped(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET is_equipped = ? WHERE id = ?",
				(item.isEquipped() ? 1 : 0));
		item.getLastStatus().updateEquipped();
	}

	@Override
	public void updateItemIdentified(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET is_id = ? WHERE id = ?", (item.isIdentified() ? 1 : 0));
		item.getLastStatus().updateIdentified();
	}

	@Override
	public void updateItemDelayEffect(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET last_used = ? WHERE id = ?", item.getLastUsed());
		item.getLastStatus().updateLastUsed();
	}
	
	@Override
	public void updateItemBless(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET bless = ? WHERE id = ?", item.getBless());
		item.getLastStatus().updateBless();
	}

	@Override
	public void updateItemAttrEnchantLevel(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET attr_enchantlvl = ? WHERE id = ?",
				item.getAttrEnchantLevel());
		item.getLastStatus().updateAttrEnchantLevel();
	}
	@Override
	public void updateItemEndTime(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET end_time = ? WHERE id = ?", item.getEndTime());
		item.getLastStatus().updateEndTime();
	} 
	@Override
	public void updateBlessType(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET bless_type = ? WHERE id = ?", item.getBlessType());
		item.getLastStatus().updateBlessType();
	}

	@Override
	public void updateBlessTypeValue(ItemInstance item) throws Exception {
		executeUpdate(item.getId(), "UPDATE character_items SET bless_type_value = ? WHERE id = ?", item.getBlessTypeValue());
		item.getLastStatus().updateBlessTypeValue();
	}

	@Override
	public int getItemCount(int objId) throws Exception {
		int count = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_items WHERE char_id = ?");
			pstm.setInt(1, objId);
			rs = pstm.executeQuery();
			while (rs.next()) {
				count++;
			}
		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return count;
	}

	private void executeUpdate(int objId, String sql, int updateNum) throws SQLException {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(sql.toString());
			pstm.setInt(1, updateNum);
			pstm.setInt(2, objId);
			pstm.execute();
		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void executeUpdate(int objId, String sql, Timestamp ts) throws SQLException {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(sql.toString());
			pstm.setTimestamp(1, ts);
			pstm.setInt(2, objId);
			pstm.execute();
		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	@Override
	public void updateItemAll(ItemInstance item) throws Exception {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("UPDATE character_items SET " +
					"item_id = ?" +
					",count = ?" +
					",durability = ?" +
					",charge_count = ?" +
					",remaining_time = ?" +
					",enchantlvl = ?" +
					",is_equipped = ?" +
					",is_id = ?" +
					",last_used = ?" +
					",bless = ?" +
					",attr_enchantlvl = ?" +
					",end_time = ?,bless_type = ?, bless_type_value = ?" +
					" WHERE id = ?");
			pstm.setInt(1, item.getItemId());
			pstm.setInt(2, item.getCount());
			pstm.setInt(3, item.getDurability());
			pstm.setInt(4, item.getChargeCount());
			pstm.setInt(5, item.getRemainingTime());
			pstm.setInt(6, item.getEnchantLevel());
			pstm.setInt(7, (item.isEquipped() ? 1 : 0));
			pstm.setInt(8, (item.isIdentified() ? 1 : 0));
			pstm.setTimestamp(9, item.getLastUsed());
			pstm.setInt(10, item.getBless());
			pstm.setInt(11, item.getAttrEnchantLevel()); 
			pstm.setTimestamp(12, item.getEndTime());
			pstm.setInt(13, item.getBlessType());
			pstm.setInt(14, item.getBlessTypeValue());
			pstm.setInt(15, item.getId());

			pstm.execute();

			item.getLastStatus().updateItemId();
			item.getLastStatus().updateCount();
			item.getLastStatus().updateDuraility();
			item.getLastStatus().updateChargeCount();
			item.getLastStatus().updateRemainingTime();
			item.getLastStatus().updateEnchantLevel();
			item.getLastStatus().updateEquipped();
			item.getLastStatus().updateIdentified();
			item.getLastStatus().updateLastUsed();
			item.getLastStatus().updateBless();
			item.getLastStatus().updateAttrEnchantLevel();
			item.getLastStatus().updateEndTime();
			item.getLastStatus().updateBlessType();
			item.getLastStatus().updateBlessTypeValue();
		} catch (SQLException e) {
			throw e;
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	 
}
