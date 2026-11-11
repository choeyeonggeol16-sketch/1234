package l1j.server.server.model.Warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.utils.SQLUtil;

public class PresentWarehouse extends Warehouse {
	private static final long serialVersionUID = 1L;
	protected static Logger _log = Logger.getLogger(PresentWarehouse.class.getName());
	
	public PresentWarehouse(String an) {
		super(an);
	}
	
	@Override
	protected int getMax() {
		return 200;
	}
	
	boolean _loaded = false;
	
	@Override
	public void loadItems() {
		synchronized (this) {
			if (_loaded) {
				return;
			}

			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = L1DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT * FROM character_present_warehouse WHERE account_name = ?");
				pstm.setString(1, getName());
				rs = pstm.executeQuery();
				ItemInstance item = null;
				L1Item itemTemplate = null;
				while (rs.next()) {
					item = new ItemInstance();
					int objectId = rs.getInt("id");
					item.setId(objectId);
					itemTemplate = ItemTable.getInstance().getTemplate(rs.getInt("item_id"));				
					item.setItem(itemTemplate);
					item.setCount(rs.getInt("count"));
					item.setEquipped(false);
					item.setEnchantLevel(rs.getInt("enchantlvl"));
					item.setIdentified(rs.getInt("is_id") != 0 ? true : false);
					item.setDurability(rs.getInt("durability"));
					item.setChargeCount(rs.getInt("charge_count"));
					item.setRemainingTime(rs.getInt("remaining_time"));
					item.setLastUsed(rs.getTimestamp("last_used"));
					item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
					item.setBless(rs.getInt("bless"));
					item.setBlessType(rs.getInt("bless_type"));
					item.setBlessTypeValue(rs.getInt("bless_type_value"));
					
					if (item.getItemId() == ItemId.아데나) {
						ItemInstance itemExist = findItemId(item.getItemId());

						if (itemExist != null) { 
							int newCount = itemExist.getCount() + item.getCount();

							if (newCount <= L1Inventory.MAX_AMOUNT) {
								if (newCount < 0) {
									newCount = 0;
								}
								itemExist.setCount(newCount);
								
								updateItem(itemExist);
							}
							deleteItem(item); 
						} else {
							_items.add(item);
							L1World.getInstance().storeObject(item);
						}
					} else {
						_items.add(item);
						L1World.getInstance().storeObject(item);
					}
				}
	
				_loaded = true;

			} catch (SQLException e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(pstm);
				SQLUtil.close(con);
			}
		}
	}

	@Override
	public void insertItem(ItemInstance item) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO character_present_warehouse SET id = ?, account_name = ?, item_id = ?, item_name = ?, count = ?, is_equipped=0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?, last_used = ?, attr_enchantlvl = ?, bless = ?, bless_type=?, bless_type_value=?");
			pstm.setInt(1, item.getId());
			pstm.setString(2, getName());
			pstm.setInt(3, item.getItemId());
			pstm.setString(4, item.getName());
			pstm.setInt(5, item.getCount());
			pstm.setInt(6, item.getEnchantLevel());
			pstm.setInt(7, item.isIdentified() ? 1 : 0);
			pstm.setInt(8, item.getDurability());
			pstm.setInt(9, item.getChargeCount());
			pstm.setInt(10, item.getRemainingTime());
			pstm.setTimestamp(11, item.getLastUsed());
			pstm.setInt(12, item.getAttrEnchantLevel());
			pstm.setInt(13, item.getBless());
			pstm.setInt(14, item.getBlessType());
			pstm.setInt(15, item.getBlessTypeValue());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	@Override
	public synchronized void updateItem(ItemInstance item) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("UPDATE character_present_warehouse SET count = ? WHERE id = ?");
			pstm.setInt(1, item.getCount());
			pstm.setInt(2, item.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	@Override
	public void deleteItem(ItemInstance item) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_present_warehouse WHERE id = ?");
			pstm.setInt(1, item.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con); 
		} 
		_items.remove(_items.indexOf(item));
	}
	
	
	public static void present(String account, int itemid, int enchant, int count) throws Exception {
		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null)
			return;

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			if (account.compareToIgnoreCase("*") == 0) {
				pstm = con.prepareStatement("SELECT * FROM accounts");
			} else {
				pstm = con.prepareStatement("SELECT * FROM accounts WHERE login=?");
				pstm.setString(1, account);
			}
			rs = pstm.executeQuery();

			ArrayList<String> accountList = new ArrayList<String>();
			while (rs.next()) {
				accountList.add(rs.getString("login"));
			}

			present(accountList, itemid, enchant, count);

		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

	}

	public static void present(int minlvl, int maxlvl, int itemid, int enchant, int count) throws Exception {
		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null)
			return;

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			pstm = con.prepareStatement(
					"SELECT distinct(account_name) as account_name FROM characters WHERE level between ? and ?");
			pstm.setInt(1, minlvl);
			pstm.setInt(2, maxlvl);
			rs = pstm.executeQuery();

			ArrayList<String> accountList = new ArrayList<String>();
			while (rs.next()) {
				accountList.add(rs.getString("account_name"));
			}

			present(accountList, itemid, enchant, count);

		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		 
	}
	
	private static void present(ArrayList<String> accountList, int itemid,
			int enchant, int count) throws Exception {

		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null) {
			throw new Exception("존재하지 않는 아이템 ID");
		}
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			con.setAutoCommit(false);
			ItemInstance item = null;
			for (String account : accountList) {
				if (temp.isStackable()) {
					item = ItemTable.getInstance().createItem(itemid);
					item.setEnchantLevel(enchant);
					item.setCount(count);

					pstm = con.prepareStatement("INSERT INTO character_present_warehouse SET id = ?, account_name = ?, item_id = ?, item_name = ?, count = ?, is_equipped=0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?");
					pstm.setInt(1, item.getId());
					pstm.setString(2, account);
					pstm.setInt(3, item.getItemId());
					pstm.setString(4, item.getName());
					pstm.setInt(5, item.getCount());
					pstm.setInt(6, item.getEnchantLevel());
					pstm.setInt(7, item.isIdentified() ? 1 : 0);
					pstm.setInt(8, item.getDurability());
					pstm.setInt(9, item.getChargeCount());
					pstm.setInt(10, item.getRemainingTime());
					pstm.executeUpdate();
				} else {
					item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = ItemTable.getInstance().createItem(itemid);
						item.setEnchantLevel(enchant);

						pstm = con
								.prepareStatement("INSERT INTO character_present_warehouse SET id = ?, account_name = ?, item_id = ?, item_name = ?, count = ?, is_equipped=0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?");
						pstm.setInt(1, item.getId());
						pstm.setString(2, account);
						pstm.setInt(3, item.getItemId());
						pstm.setString(4, item.getName());
						pstm.setInt(5, item.getCount());
						pstm.setInt(6, item.getEnchantLevel());
						pstm.setInt(7, item.isIdentified() ? 1 : 0);
						pstm.setInt(8, item.getDurability());
						pstm.setInt(9, item.getChargeCount());
						pstm.setInt(10, item.getRemainingTime());
						pstm.executeUpdate();
					}
				}
			}

			con.commit();
			con.setAutoCommit(true);
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException ignore) {
				// ignore
			}
			e.printStackTrace();
			throw new Exception(".present 처리중에 에러가 발생했습니다.");
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public static void itemshop(String account, int itemid, int enchantlvl, int count) {	
		java.sql.Connection con = null;
		PreparedStatement pstm = null;
		try {
			PresentWarehouse w = WarehouseManager.getInstance().getPresentWarehouse(account);
			ItemInstance item = null;
			item = ItemTable.getInstance().createItem(itemid);
			w.clearItems();	
			item.setCount(count);			
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO character_present_warehouse SET id = ?, account_name = ?, item_id = ?, item_name = ?, count = ?, is_equipped=0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?, last_used = ?, attr_enchantlvl = ?, bless = ?, bless_type=?, bless_type_value=?");
			pstm.setInt(1, item.getId());
			pstm.setString(2, account);
			pstm.setInt(3, item.getItemId());
			pstm.setString(4, item.getName());
			pstm.setInt(5, item.getCount());
			pstm.setInt(6, item.getEnchantLevel());
			pstm.setInt(7, item.isIdentified() ? 1 : 0);
			pstm.setInt(8, item.getDurability());
			pstm.setInt(9, item.getChargeCount());
			pstm.setInt(10, item.getRemainingTime());
			pstm.setTimestamp(11, item.getLastUsed());
			pstm.setInt(12, item.getAttrEnchantLevel());
			pstm.setInt(13, item.getBless());
			pstm.setInt(14, item.getBlessType());
			pstm.setInt(15, item.getBlessTypeValue());
			pstm.execute();
						 
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
}
