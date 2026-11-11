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
package l1j.server.server.model.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import l1j.server.BugRace.BugRaceController;
import l1j.server.DogFightSystem.DogFightController;
import l1j.server.DogFightSystem.DogFightSystemSettings;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1ShopItem;
import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcShopAdenTypeTable;
import l1j.server.server.datatables.NpcShopAdenTypeTable.L1AdenType;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_Disconnect; //## (버그 방지) 상점 버그 방지
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage; //## A69 소스제공 : 해커스 님 프리미엄 상인 메시지 출력위해 임포트 
import l1j.server.server.utils.IntRange;
import manager.SJManager;

public class Shop {
	private final int _npcId;
	private final List<L1ShopItem> _sellingItems;
	private final List<L1ShopItem> _purchasingItems;

	public Shop(int npcId, List<L1ShopItem> sellingItems, List<L1ShopItem> purchasingItems) {
		if (sellingItems == null || purchasingItems == null) {
			throw new NullPointerException();
		}

		_npcId = npcId;
		_sellingItems = sellingItems;
		_purchasingItems = purchasingItems;
	}

	public int getNpcId() {
		return _npcId;
	}

	public List<L1ShopItem> getSellingItems() {
		return _sellingItems;
	}
	
	public List<L1ShopItem> getBuyingItems() {
		return _purchasingItems;
	}

	/**
	 * 이 상점에서, 지정된 아이템이 매입 가능한 상태일까를 돌려준다.
	 * 
	 * @param item
	 * @return 아이템이 매입 가능하면 true
	 */
	private boolean isPurchaseableItem(ItemInstance item) {
		if (item == null) {
			return false;
		}
		if (item.isEquipped()) { 
			return false;
		}
		if (item.getBless() >= 128) { 
			return false;
		}
		return true;
	}
	public L1ShopItem getSellItem(int itemid) {
		for (L1ShopItem a : _sellingItems) {
			if (a.getItemId() == itemid) {
				return a;
			}
		}
		return null;
	}

	public L1ShopItem getBuyItem(int itemid) {
		for (L1ShopItem a : _purchasingItems) {
			if (a.getItemId() == itemid) {
				return a;
			}
		}
		return null;
	}
	public boolean isSellingItem(int itemid) {
		for (L1ShopItem a : _sellingItems) {
			if (a.getItemId() == itemid) {
				return true;
			}
		}
		return false;
	} 
	private L1ShopItem getPurchasingItem(int itemId, int enchant) {
		for (L1ShopItem shopItem : _purchasingItems) {
			if (shopItem.getItemId() == itemId && shopItem.getEnchant() == enchant) {
				return shopItem;
			}
		}
		return null;
	}

	public L1AssessedItem assessItem(ItemInstance item) {
		L1ShopItem shopItem = getPurchasingItem(item.getItemId(), item.getEnchantLevel());
		if (shopItem == null) {
			return null;
		}
		return new L1AssessedItem(item.getId(), getAssessedPrice(shopItem));
	}

	private int getAssessedPrice(L1ShopItem item) {
		return (int) (item.getPrice() * Config.RATE_SHOP_PURCHASING_PRICE / item.getPackCount());
	}

	/**
	 * 인벤트리내의 매입 가능 아이템을 사정한다.
	 * 
	 * @param inv
	 *            사정 대상의 인벤트리
	 * @return 사정된 매입 가능 아이템의 리스트
	 */
	public List<L1AssessedItem> assessItems(L1PcInventory inv) {
		List<L1AssessedItem> result = new ArrayList<L1AssessedItem>();
		for (L1ShopItem item : _purchasingItems) {
			for (ItemInstance targetItem : inv.findItemsId(item.getItemId())) {
				if (!isPurchaseableItem(targetItem)) {
					continue;
				}
				if (item.getEnchant() == targetItem.getEnchantLevel()) { // 인챈트가 같은 아이템만
					result.add(new L1AssessedItem(targetItem.getId(), getAssessedPrice(item)));
				}
			}
		}
		return result;
	}

	/**
	 * 플레이어에 아이템을 판매할 수 있는 것을 프로텍션한다.
	 * 
	 * @return 어떠한 이유로써 아이템을 판매할 수 없는 경우, false
	 */
	private boolean ensureSell(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPriceTaxIncluded();
		// 오버플로우 체크
		if (!IntRange.includes(price, 0, 2000000000)) {
			// 총판 매가격은%d아데나를 초과할 수 없습니다.
			pc.sendPackets(new S_ServerMessage(904, "2000000000"));
			return false;
		}
		// 구입할 수 있을까 체크
		if (!pc.getInventory().checkItem(ItemId.아데나, price)) { 
			pc.sendPackets(new S_SystemMessage("아데나가 부족합니다."));
			return false; 
		}
		// 중량 체크
		int currentWeight = pc.getInventory().getWeight() * 1000;
		if (currentWeight + orderList.getTotalWeight() > pc.getMaxWeight() * 1000) {
			// 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			pc.sendPackets(new S_ServerMessage(82));
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		for (L1ShopBuyOrder order : orderList.getList()) {
			L1Item temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().checkItem(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			pc.sendPackets(new S_ServerMessage(263));
			return false;
		}
	//	System.out.println("vmfktltm  " +price );
		
		//## (버그 방지) 상점 버그 방지
		if (price <= 0 || price > 2000000000) {
			

		//	pc.sendPackets(new S_Disconnect());
            return false;
        }
		//## (버그 방지) 상점 버그 방지
		return true;
	}

	/**
	 * 지역세 납세 처리 에덴성·디아드 요새를 제외한 성은 에덴성에 국세로 해서10% 납세한다
	 * 
	 * @param orderList
	 */
	private void payCastleTax(L1ShopBuyOrderList orderList) {
		L1TaxCalculator calc = orderList.getTaxCalculator();

		int price = orderList.getTotalPrice();

		int castleId = L1CastleLocation.getCastleIdByNpcid(_npcId);
		int castleTax = calc.calcCastleTaxPrice(price);
		int nationalTax = calc.calcNationalTaxPrice(price);
		if (castleId == L1CastleLocation.ADEN_CASTLE_ID || castleId == L1CastleLocation.DIAD_CASTLE_ID) {
			castleTax += nationalTax;
			nationalTax = 0;
		}

		if (castleId != 0 && castleTax > 0) {
			CastleWar war = CastleWarBusiness.getInstance().get(castleId);
			int money = war.getPublicMoney();
			if (2000000000 > money + castleTax) {
				money = money + castleTax;
				war.setPublicMoney(money);
				CastleWarBusiness.getInstance().updateCastle(castleId);
			}

			if (nationalTax > 0) {
				war = CastleWarBusiness.getInstance().get(L1CastleLocation.ADEN_CASTLE_ID);
				money = war.getPublicMoney();
				if (2000000000 > money + castleTax) {
					money = money + nationalTax;
					war.setPublicMoney(money);
					CastleWarBusiness.getInstance().updateCastle(L1CastleLocation.ADEN_CASTLE_ID);
				}

			}
		}
	}

	/**
	 * 디아드세 납세 처리
	 * 전쟁세의10%가 디아드 요새의 공금이 된다.
	 * @param orderList
	 */
	private void payDiadTax(L1ShopBuyOrderList orderList) {
		L1TaxCalculator calc = orderList.getTaxCalculator();

		int price = orderList.getTotalPrice();

		int diadTax = calc.calcDiadTaxPrice(price);
		if (diadTax <= 0) {
			return;
		}

		CastleWar war = CastleWarBusiness.getInstance().get(L1CastleLocation.DIAD_CASTLE_ID);
		int money = war.getPublicMoney();
		if (2000000000 > money + diadTax) {
			money = money + diadTax;
			war.setPublicMoney(money);
			CastleWarBusiness.getInstance().updateCastle(L1CastleLocation.DIAD_CASTLE_ID);
		}
	}

	/**
	 * 조세 납세 처리
	 * @param orderList
	 */
	private void payTownTax(L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();

		// 마을의 매상
		if (!L1World.getInstance().isProcessingContributionTotal()) {
			int town_id = L1TownLocation.getTownIdByNpcid(_npcId);
			if (town_id >= 1 && town_id <= 10) {
				TownTable.getInstance().addSalesMoney(town_id, price);
			}
		}
	}

	// XXX 납세 처리는 이 클래스의 책무는 아닌 생각이 들지만 우선
	private void payTax(L1ShopBuyOrderList orderList) {
		payCastleTax(orderList);
		payTownTax(orderList);
		payDiadTax(orderList);
	}

	/**
	 * 판매 거래
	 */
	private void sellItems(L1PcInventory inv, L1ShopBuyOrderList orderList) {
		if (!inv.consumeItem(ItemId.아데나, orderList.getTotalPriceTaxIncluded())) {
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			item = ItemTable.getInstance().createItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setEnchantLevel(enchant);
			item.setCount(amount);
			item.setIdentified(true);
			if (_npcId == 70068 || _npcId == 70020) {
				item.setIdentified(false);
				Random random = new Random();
				int chance = random.nextInt(100) + 1;
				if (chance <= 15) {
					item.setEnchantLevel(-2);
				} else if (chance >= 16 && chance <= 30) {
					item.setEnchantLevel(-1);
				} else if (chance >= 31 && chance <= 70) {
					item.setEnchantLevel(0);
				} else if (chance >= 71 && chance <= 87) {
					item.setEnchantLevel(random.nextInt(2)+1);
				} else if (chance >= 88 && chance <= 97) {
					item.setEnchantLevel(random.nextInt(3)+3);
				} else if (chance >= 98 && chance <= 99) {
					item.setEnchantLevel(6);
				} else if (chance == 100) {
					item.setEnchantLevel(7);
				}
			}		
			if (_npcId == DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid) {
				for (int row = 0; row < 3; row++) {
					if (DogFightController.getInstance()._ticketId[row] == item.getItemId()) {
						DogFightController.getInstance()._ticketCount[row] += amount;
					}
				}
			}
			
			if (_npcId == 81181 || _npcId == 81182 || _npcId == 81183) {
				for (int row = 0; row < 5; row++) {
					if (BugRaceController.getInstance()._ticket[row] == item.getItemId()) {
						BugRaceController.getInstance()._ticketCount[row] += amount;
					}
				}
			} 
			inv.storeItem(item);
		}
	}

	/**
	 * 플레이어에, L1ShopBuyOrderList에 기재된 아이템을 판매한다.
	 * 
	 * @param pc
	 *            판매하는 플레이어
	 * @param orderList
	 *            판매해야 할 아이템이 기재된 L1ShopBuyOrderList
	 */
 

	/**
	 * L1ShopSellOrderList에 기재된 아이템을 매입한다.
	 * 
	 * @param orderList
	 *            매입해야 할 아이템과 가격이 기재된 L1ShopSellOrderList
	 */
	public void sellItems(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		if (pc == null)
			return;

		int npcid = getNpcId();
		L1AdenType at = NpcShopAdenTypeTable.getInstance().getNpcAdenType(npcid);
		if (at != null) {
			if (!ensurePremiumSell(pc, orderList, at)) { 
			}
			sellPremiumItems(pc.getInventory(), orderList, at);
		} else {
			if (!ensureSell(pc, orderList)) {
				return;
			}
			if (getNpcId() >= 4000001 && getNpcId() <= 4000060) { // 영자 엔피씨무인상점
				if (!NoTaxEnsureSell(pc, orderList)) {
					return;
				} 
				NpcShopSellItems(pc, pc.getInventory(), orderList);
				return;
			}
			
			sellItems(pc.getInventory(), orderList);
			payTax(orderList);
		}
	}

	public void buyItems(L1ShopSellOrderList orderList) {

		L1PcInventory inv = orderList.getPc().getInventory();
		int totalPrice = 0;
		L1Object object = null;
		ItemInstance item = null;

		int adenaId = ItemId.아데나;
		String adena_name = "아데나";
		int npcid = getNpcId();
		L1AdenType at = NpcShopAdenTypeTable.getInstance().getNpcAdenType(npcid);
		L1Npc npc = NpcTable.getInstance().getTemplate(getNpcId());
		if (at != null) {
			adenaId = at.getAdenType();
			adena_name = at.getAdenName();
		}
		
		for (L1ShopSellOrder order : orderList.getList()) {
			object = inv.getItem(order.getItem().getTargetId());
			item = (ItemInstance) object;
			if (item.getItem().getBless() < 128) {
				int price = order.getItem().getAssessedPrice() * order.getCount();
				if(adenaId == ItemId.아데나){
					if(inv.checkItemCount(ItemId.아데나) + price > 1800000000 || inv.checkItemCount(ItemId.아데나) + price < 0){
						inv.getOwner().sendPackets(new S_SystemMessage("\\f3[!] 판매불가 : 소지아데나 20억 초과(" + item.getName() + ")"));
						continue;
					}
				}
				int count = inv.removeItem(order.getItem().getTargetId(), order.getCount());
				totalPrice += order.getItem().getAssessedPrice() * count;
				SJManager.getInstance().ShopAppend(item.getName(), item.getCount(), totalPrice, "상점판매(" + adena_name + ")",
						orderList.getPc().getName() + " => " + npc.get_name());
 
			}
		}

		totalPrice = IntRange.ensure(totalPrice, 0, 2000000000);

		if (0 < totalPrice) {
				inv.storeItem(adenaId, totalPrice);
		}
	}

	public L1ShopBuyOrderList newBuyOrderList() {
		return new L1ShopBuyOrderList(this);
	}

	public L1ShopSellOrderList newSellOrderList(L1PcInstance pc) {
		return new L1ShopSellOrderList(this, pc);
	}
 
	
	
	
	private boolean ensurePremiumSell(L1PcInstance pc, L1ShopBuyOrderList orderList, L1AdenType at) {
		int price = orderList.getTotalPrice();
		int FeatherCount = at.getMaxUse();
		int aden_type = at.getAdenType();
		// 오버플로우 체크
		if (!IntRange.includes(price, 0, FeatherCount)) {
			pc.sendPackets(new S_SystemMessage(at.getAdenName() + "은 한번에 " + FeatherCount + "개 이상 사용할 수 없습니다."));
			return false;
		}
		// 구입할 수 있을까 체크
		if (!pc.getInventory().checkItem(aden_type, price)) {
			pc.sendPackets(new S_SystemMessage(at.getAdenName() + "이(가) 부족합니다."));
			return false;
		}
		// 중량 체크
		int currentWeight = pc.getInventory().getWeight() * 1000;
		if (currentWeight + orderList.getTotalWeight() > pc.getMaxWeight() * 1000) {
			// 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			pc.sendPackets(new S_ServerMessage(82));
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().checkItem(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			pc.sendPackets(new S_ServerMessage(263));
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			pc.sendPackets(new S_Disconnect());
			return false;
		}
		return true;
	}
	
	
	
	private void sellPremiumItems(L1PcInventory inv, L1ShopBuyOrderList orderList, L1AdenType at) { 
		int aden_type = at.getAdenType();
		int price = orderList.getTotalPrice();
		int FeatherCount = at.getMaxUse(); 
		
		if (!IntRange.includes(price, 0, FeatherCount)) { 
			return;
		}
		
		if (!inv.consumeItem(aden_type, orderList.getTotalPrice())) {
			inv.getOwner().sendPackets(new S_SystemMessage("구입에 필요한 " + at.getAdenName() + " 을(를) 소비할 수 없었습니다."));
			return;
		}

		ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchantLevel = order.getItem().getEnchant();
			item = ItemTable.getInstance().createItem(itemId);
			if(item == null) {
				System.out.println("아이템 목록에 없는것을 구매하려합니다 [아이템번호 : " + itemId);
				return;
			}
			
			item.setCount(amount);
			item.setEnchantLevel(enchantLevel);
			item.setIdentified(true);
			//item.setPackage(pack);
			inv.storeItem(item);
		} 
	}	
	
	private boolean NoTaxEnsureSell(L1PcInstance pc, L1ShopBuyOrderList orderList) {

		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 2000000000)) {
			pc.sendPackets(new S_ServerMessage(904, "2000000000"));
			return false;
		}
		if (!pc.getInventory().checkItem(ItemId.아데나, price)) {
			pc.sendPackets(new S_ServerMessage(189));
			return false;
		}
		int currentWeight = pc.getInventory().getWeight() * 1000;
		if (currentWeight + orderList.getTotalWeight() > pc.getMaxWeight() * 1000) {
			pc.sendPackets(new S_ServerMessage(82));
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().checkItem(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			pc.sendPackets(new S_ServerMessage(263));
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			pc.sendPackets(new S_Disconnect());
			return false;
		}
		return true;
	}
	

	private void NpcShopSellItems(L1PcInstance pc, L1PcInventory inv, L1ShopBuyOrderList orderList) {
		L1NpcShopInstance npc1 = null;

		npc1 = L1World.getInstance().getNpcShop(getNpcId());
		if (npc1 == null) {
			pc.sendPackets(new S_SystemMessage("잘못된 접근입니다."));
			return;
		}
		if (npc1.isTradingInPrivateShop()) {
			pc.sendPackets(new S_SystemMessage("다른 사람과 거래중 입니다. 다시 이용해주세요."));
			return;
		}
		if (!inv.consumeItem(40308, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		/** 2016.11.25 MJ 앱센터 시세 **/
		L1NpcInstance npc = L1World.getInstance().findNpc(getNpcId());
		/** 2016.11.25 MJ 앱센터 시세 **/

		ItemInstance item = null;

		List<L1ShopItem> sellList = getSellingItems();
		synchronized (sellList) {
			if (pc.getPartnersPrivateShopItemCount() != sellList.size()) {
				return;
			}
			if (pc.getPartnersPrivateShopItemCount() < sellList.size()) {
				return;
			}
			npc1.setTradingInPrivateShop(true);
			try {
				boolean[] isRemoveFromList = new boolean[8];
				for (L1ShopBuyOrder order : orderList.getList()) {
					int orderid = order.getOrderNumber();
					int itemId = order.getItem().getItemId();
					int amount = order.getCount();
					int enchant = order.getItem().getEnchant();
					int remaindcount = ((L1ShopItem) getSellingItems().get(orderid)).getCount();
					if (remaindcount < amount) {
						npc1.setTradingInPrivateShop(false);
						return;
					}
					item = ItemTable.getInstance().createItem(itemId);
					if (getSellingItems().contains(item)) {
						npc1.setTradingInPrivateShop(false);
						return;
					}
					item.setCount(amount);
					item.setIdentified(true);
					item.setEnchantLevel(enchant);
					if (remaindcount == amount)
						isRemoveFromList[orderid] = true;
					else {
						((L1ShopItem) _sellingItems.get(orderid)).setCount(remaindcount - amount);
					}
					inv.storeItem(item);
					SJManager.getInstance().ShopAppend(item.getName(), item.getCount(),orderList.getTotalPriceTaxIncluded(), "상점구입(무인)",npc.getName() + " => " + inv.getOwner().getName());
					  
				}
				for (int i = 7; i >= 0; i--)
					if (isRemoveFromList[i]) {
						_sellingItems.remove(i);
					} 
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				npc1.setTradingInPrivateShop(false);
			}
		}
	}
}
