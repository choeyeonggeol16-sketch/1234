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
package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.templates.L1ShopItem;
import l1j.server.server.utils.SQLUtil;

public class ShopTable2 {
	
	private static ShopTable2 _instance;

	private final Map<Integer, L1Shop> _allShops = new HashMap<Integer, L1Shop>();

	public static ShopTable2 getInstance() {
		if (_instance == null) {
			_instance = new ShopTable2();
		}
		return _instance;
	}

	private ShopTable2() {
		loadShops();
	}
	
	public static void reload(){
		ShopTable2 oldInstance = _instance;
		_instance = new ShopTable2();
		oldInstance._allShops.clear();
	}

	private ArrayList<Integer> enumNpcIds() {
		ArrayList<Integer> ids = new ArrayList<Integer>();

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT DISTINCT npc_id FROM shop");
			rs = pstm.executeQuery();
			while (rs.next()) {
				ids.add(rs.getInt("npc_id"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
		return ids;
	}

	private HashMap<Integer, L1ShopItem> _sellings;
	private HashMap<Integer, ArrayList<L1ShopItem>> _purchasings;
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
				if(Config.IS_VALIDSHOP){
					if(sellingPrice > 0){
						item.setNpcId(npcId);
						if(_sellings != null){
							L1ShopItem i = _sellings.get(itemId);
							if(i == null)
								_sellings.put(itemId, item);
							else if(sellingPrice < i.getPrice())
								_sellings.put(itemId, item);
						}
					}
				}
				
				sellingList.add(item);
			}
			if (0 <= purchasingPrice) {
				item = new L1ShopItem(itemId, purchasingPrice, packCount, enchant, timeLimit);
				if(Config.IS_VALIDSHOP){
					if(purchasingPrice > 0){
						item.setNpcId(npcId);
						if(_purchasings != null){
							ArrayList<L1ShopItem> list = _purchasings.get(itemId);
							if(list == null){
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
	public void Reload(int npcid) { //특정 NPC리로드
		ArrayList<Integer> list = enumNpcIds();
		for(Integer i : list){
			if(i != npcid)
				continue;
			
			Selector.exec("select * from shop where npc_id=? order by order_id", new SelectorHandler(){

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
		if(Config.IS_VALIDSHOP){
			_sellings = new HashMap<Integer, L1ShopItem>(1024);
			_purchasings = new HashMap<Integer, ArrayList<L1ShopItem>>(1024);
		}
		
		ArrayList<Integer> list = enumNpcIds();
		for(Integer i : list){
			Selector.exec("select * from shop where npc_id=? order by order_id", new SelectorHandler(){
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
		
		if(Config.IS_VALIDSHOP){
			for(Integer itemId : _purchasings.keySet()){
				L1ShopItem sell = _sellings.get(itemId);
				ArrayList<L1ShopItem> purs = _purchasings.get(itemId);
				if(sell == null || purs == null || purs.size() <= 0)
					continue;
				
				int sellp = sell.getPrice();
				for(L1ShopItem buy : purs){
					if(sellp < buy.getPrice())
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
	/*버경 관련*/
	public void addShop(int npcId, L1Shop shop){		
		_allShops.put(npcId, shop);
	}

	/*버경 관련*/
	public void delShop(int npcId) {
	_allShops.remove(npcId);	
	}
}
