package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.templates.L1ShopItem;

public class ShopTable {
	private static final Logger _log = Logger.getLogger(ShopTable.class.getName());
	private static volatile ShopTable _instance;

	private final TIntObjectMap<L1Shop> _allShops = new TIntObjectHashMap<L1Shop>(256);

	public static ShopTable getInstance() {
		ShopTable current = _instance;
		if (current != null) return current;
		synchronized (ShopTable.class) {
			current = _instance;
			if (current == null) {
				current = new ShopTable();
				_instance = current;
			}
		}
		return current;
	}

	private ShopTable() {
		loadShops();
	}
	
	public static synchronized void reload() {
		ShopTable replacement = new ShopTable();
		_instance = replacement;
	}

	private TIntArrayList enumNpcIds() {
		TIntArrayList ids = new TIntArrayList();
		String sql = "SELECT DISTINCT npc_id FROM shop";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				ids.add(rs.getInt("npc_id"));
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ShopTable enumNpcIds error", e);
		}
		return ids;
	}

	private TIntObjectMap<L1ShopItem> _sellings;
	private TIntObjectMap<ArrayList<L1ShopItem>> _purchasings;

	private L1Shop loadShop(int npcId, ResultSet rs) throws SQLException {
		List<L1ShopItem> sellingList = new ArrayList<L1ShopItem>();
		List<L1ShopItem> purchasingList = new ArrayList<L1ShopItem>();
		
		L1ShopItem item = null;
		while (rs.next()) {
			int itemId = rs.getInt("item_id");
			int sellingPrice = rs.getInt("selling_price");
			int purchasingPrice = rs.getInt("purchasing_price");
			int packCount = rs.getInt("pack_count");
			int enchant = rs.getInt("enchant");
			boolean timeLimit = Boolean.valueOf(rs.getString("time_limit"));
			packCount = packCount == 0 ? 1 : packCount;

			if (0 <= sellingPrice) {
				item = new L1ShopItem(itemId, sellingPrice, packCount, enchant, timeLimit);
				if (Config.IS_VALIDSHOP) {
					if (sellingPrice > 0) {
						item.setNpcId(npcId);
						if (_sellings != null) {
							L1ShopItem i = _sellings.get(itemId);
							if (i == null)
								_sellings.put(itemId, item);
							else if (sellingPrice < i.getPrice())
								_sellings.put(itemId, item);
						}
					}
				}
				sellingList.add(item);
			}
			if (0 <= purchasingPrice) {
				item = new L1ShopItem(itemId, purchasingPrice, packCount, enchant, timeLimit);
				if (Config.IS_VALIDSHOP) {
					if (purchasingPrice > 0) {
						item.setNpcId(npcId);
						if (_purchasings != null) {
							ArrayList<L1ShopItem> list = _purchasings.get(itemId);
							if (list == null) {
								list = new ArrayList<L1ShopItem>(8);
								_purchasings.put(itemId, list);
							}
							list.add(item);
						}
					}
				}
				purchasingList.add(item);
			}
		}
		return new L1Shop(npcId, sellingList, purchasingList);
	}

	public void Reload(int npcid) {
		TIntArrayList list = enumNpcIds();
		for (int i : list.toArray()) {
			if (i != npcid)
				continue;
			
			Selector.exec("SELECT * FROM shop WHERE npc_id=? ORDER BY order_id", new SelectorHandler(){
				@Override
				public void handle(PreparedStatement pstm) throws Exception {
					pstm.setInt(1, i);
				}

				@Override
				public void result(ResultSet rs) throws Exception {
					_allShops.put(i, loadShop(i, rs));
				}
			});
		}
	}

	private void loadShops() {
		if (Config.IS_VALIDSHOP) {
			_sellings = new TIntObjectHashMap<L1ShopItem>(1024);
			_purchasings = new TIntObjectHashMap<ArrayList<L1ShopItem>>(1024);
		}	
		TIntArrayList list = enumNpcIds();
		for (int i : list.toArray()) {
			Selector.exec("SELECT * FROM shop WHERE npc_id=? ORDER BY order_id", new SelectorHandler(){
				@Override
				public void handle(PreparedStatement pstm) throws Exception {
					pstm.setInt(1, i);
				}
				@Override
				public void result(ResultSet rs) throws Exception {
					_allShops.put(i, loadShop(i, rs));
				}
			});
		}
		
		if (Config.IS_VALIDSHOP) {
			for (int itemId : _purchasings.keys()) {
				L1ShopItem sell = _sellings.get(itemId);
				ArrayList<L1ShopItem> purs = _purchasings.get(itemId);
				if (sell == null || purs == null || purs.size() <= 0)
					continue;
				
				int sellp = sell.getPrice();
				for (L1ShopItem buy : purs) {
					if (sellp < buy.getPrice())
						System.out.println(String.format("[판매가보다 매입가가 큰 상점 항목 발견, 엔피씨:%d 아이템:%d, 판매:%d, 매입:%d]", buy.getNpcId(), itemId, sellp, buy.getPrice()));
				}
				purs.clear();
			}
			_sellings.clear(); _sellings = null;
			_purchasings.clear(); _purchasings = null;
		}
	}

	public L1Shop get(int npcId) {
		return _allShops.get(npcId);
	}

	/* 버경 관련 */
	public void addShop(int npcId, L1Shop shop) {		
		_allShops.put(npcId, shop);
	}

	/* 버경 관련 */
	public void delShop(int npcId) {
		_allShops.remove(npcId);	
	}
}