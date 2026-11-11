package l1j.server.server.model.Warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.utils.SQLUtil;

public abstract class Warehouse extends L1Object {
	private static final long serialVersionUID = 1L;
	protected List<ItemInstance> _items = new CopyOnWriteArrayList<ItemInstance>();
	private final String name;

	public Warehouse(String n) {
		super();
		name = n;
	}
	
	public String getName() {
		return name;
	}

	public abstract void loadItems(); 
	public abstract void deleteItem(ItemInstance item);
	public abstract void insertItem(ItemInstance item);
	public abstract void updateItem(ItemInstance findItem);
	protected abstract int getMax();
	
	public ItemInstance findItemId(int id) {
		for (ItemInstance item : _items) {
			if (item.getItem().getItemId() == id) {
				return item;
			}
		}
		return null;
	}

	public ItemInstance storeTradeItem(ItemInstance item) {
		synchronized(this) {
			if (item.isStackable()) {
				ItemInstance findItem = findItemId(item.getItem().getItemId());
				if (findItem != null) {
					findItem.setCount(findItem.getCount() + item.getCount());
					updateItem(findItem);
					return findItem;
				}
			}
			item.setX(getX());
			item.setY(getY());
			item.setMap(getMapId());
			_items.add(item);
			insertItem(item);
			return item;
		}
	}

	public ItemInstance tradeItem(ItemInstance item, int count, L1Inventory inventory) {
		synchronized (this) {
			if (item == null) 						return null;
			if (item.getCount() <= 0 || count <= 0) return null;
			if (item.isEquipped()) 					return null;
			if (item.getCount() < count)			return null;
			if (!checkItem(item.getItem().getItemId(), count)) return null;
			
			ItemInstance carryItem;
			
			
			//엔진관련 버그 방지 추가
			if (item.getCount() <= count || count < 0) {
				deleteItem(item);
				carryItem = item; 
			} 
			else {
				item.setCount(item.getCount() - count);
				updateItem(item);
				carryItem = ItemTable.getInstance().createItem(item.getItem().getItemId());
				carryItem.setCount(count);
				carryItem.setEnchantLevel(item.getEnchantLevel());
				carryItem.setIdentified(item.isIdentified());
				carryItem.setDurability(item.getDurability());
				carryItem.setChargeCount(item.getChargeCount());
				carryItem.setRemainingTime(item.getRemainingTime());
				carryItem.setLastUsed(item.getLastUsed());
				carryItem.setBless(item.getBless());
				carryItem.setAttrEnchantLevel(item.getAttrEnchantLevel()); 
			}
			return inventory.storeTradeItem(carryItem);
		}
	}

	public ItemInstance getItem(int objectId) {
		ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (ItemInstance) itemObject;
			if (item.getId() == objectId) {
				return item;
			}
		}
		return null;
	}

	public List<ItemInstance> getItems() {
		return _items;
	}

	public void clearItems() {
		ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (ItemInstance) itemObject;
			L1World.getInstance().removeObject(item);
		}
		_items.clear();
	}

	public ItemInstance[] findItemsId(int id) {
		ArrayList<ItemInstance> itemList = new ArrayList<ItemInstance>();
		for (ItemInstance item : _items) {
			if (item.getItemId() == id) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new ItemInstance[] {});
	}

	public boolean checkItem(int id, int count) {
		if (count == 0) return true;
		if (ItemTable.getInstance().getTemplate(id).isStackable()) {
			ItemInstance item = findItemId(id);
			if (item != null && item.getCount() >= count) {
				return true;
			}
		} 
		else {
			Object[] itemList = findItemsId(id);
			if (itemList.length >= count) {
				return true;
			}
		}
		return false;
	}

	public int getSize() {
		return _items.size();
	}

	public int checkAddItemToWarehouse(ItemInstance item, int count) {
		if (item == null) 						return -1;
		if (item.getCount() <= 0 || count <= 0) return -1;

		final int OK = 0, SIZE_OVER = 1;
		final int maxSize = getMax(), SIZE = getSize();
		if ( SIZE > maxSize || (SIZE == maxSize && (!item.isStackable() || !checkItem(item.getItem().getItemId(), 1))))  
			return SIZE_OVER;
	
		return OK;
	}
	

	public static void present(String account, int itemid, int enchant,int count) throws Exception {
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
				pstm = con
						.prepareStatement("SELECT * FROM accounts WHERE login=?");
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
	
	public static void present(int minlvl, int maxlvl, int itemid, int enchant,
			int count) throws Exception {
		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null)
			return;

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			pstm = con
					.prepareStatement("SELECT distinct(account_name) as account_name FROM characters WHERE level between ? and ?");
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
}