package l1j.server.server.model.shop;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.Controller.BugRaceController.BugTicketInfo;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcCashShopInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SurvivalCry;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.npc.shop.NpcShopManager;
import l1j.server.server.model.world.siege.CastleWar;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.TYUtil;

public class L1Shop {
	public static boolean is_normal_shop(L1NpcInstance npc){
		return ShopTable.getInstance().get(npc.getNpcId()) != null;
	}
	public static L1Shop find_shop(L1NpcInstance npc){
		int npcId = npc.getNpcTemplate().get_npcId();
		L1Shop shop = null;
		if (npc instanceof L1NpcShopInstance) {
			shop = NpcShopManager.getInstance().getShop(npcId);
		} else if (npc instanceof L1NpcCashShopInstance) {
			return null; // No catalog for this cash-shop NPC in the supplied schema.
		}else{
			shop = ShopTable.getInstance().get(npcId);
		}
		if (shop == null) 
			System.out.println("엔피시 상점 오류 : 번호" + npc.getNpcId() + " x :" + npc.getX() + " y :" + npc.getY() + " map :" + npc.getMapId());
		return shop;
	}
	
	private final int _npcId;
	private final List<L1ShopItem> _sellingItems;
	private final List<L1ShopItem> _purchasingItems;
    private final List<L1ShopItem> buybackSaleOffers;

	private static final HashMap<Integer, Integer> _checkbox_rules;
	static{
		_checkbox_rules = new HashMap<Integer, Integer>();
		_checkbox_rules.put(4100251, 4100251);
		_checkbox_rules.put(4100252, 4100252);
		_checkbox_rules.put(4100253, 4100253);
		
/*		_checkbox_rules.put(40222, 40222);
		_checkbox_rules.put(41148, 41148);
		_checkbox_rules.put(5559, 5559);
		_checkbox_rules.put(210125, 210125);
		_checkbox_rules.put(4100046, 4100046);
		_checkbox_rules.put(4100079, 4100079);*/
	}
	
	public L1Shop(int npcId, List<L1ShopItem> sellingItems, List<L1ShopItem> purchasingItems) {
		if (sellingItems == null || purchasingItems == null) {
			throw new NullPointerException();
		}
		_npcId = npcId;
		_sellingItems = sellingItems;
		_purchasingItems = purchasingItems;
        buybackSaleOffers = captureBuybackSaleOffers();
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

	private boolean isPurchaseableItem(L1ItemInstance item) {
		if (item == null) {
			return false;
		}
		if (item.isEquipped()) {
			return false;
		}
		if (item.getBless() >= 128) {
			return false;
		}
		/** 인형 착용 여부 **/
		if (item.isDollOn()) {
			return false;
		}
		return true;
	}
	
	public L1ShopItem getSellItem(int itemid){
		for(L1ShopItem a : _sellingItems){
			if(a.getItemId() == itemid){
				return a;
			}
		}
		return null;
	}
	
	public L1ShopItem getBuyItem(int itemid){
		for(L1ShopItem a : _purchasingItems){
			if(a.getItemId() == itemid){
				return a;
			}
		}
		return null;
	}
	
	public boolean isSellingItem(int itemid){
		for(L1ShopItem a : _sellingItems){
			if(a.getItemId() == itemid){
				return true;
			}
		}
		return false;
	}

	private L1ShopItem getPurchasingItem(int itemId, int enchant) {
		for (L1ShopItem shopItem : _purchasingItems) {
			if (shopItem != null && shopItem.getItemId() == itemId && shopItem.getEnchant() == enchant) {
				return shopItem;
			}
		}
		return null;
	}

	public L1ShopItem getSellingItem(int itemId) {
		for (L1ShopItem shopItem : _sellingItems) {
			if (shopItem.getItemId() == itemId) {
				return shopItem;
			}
		}
		return null;
	}

	public L1AssessedItem assessItem(L1ItemInstance item) {
		final int price = assessOwnedUnitPrice(item);
		return price < 0 ? null : new L1AssessedItem(item.getId(), price);
	}

    /** NPC buyback quote per item, shared by both list formats and settlement. */
    public int getAssessedPrice(L1ShopItem item) {
        if (item == null) return -1;
        return assessBuybackOffer(getPurchasingItem(item.getItemId(), item.getEnchant()));
    }

    private int assessOwnedUnitPrice(L1ItemInstance item) {
        if (!isPurchaseableItem(item)) return -1;
        return assessBuybackOffer(getPurchasingItem(item.getItemId(), item.getEnchantLevel()));
    }

    private int assessBuybackOffer(L1ShopItem item) {
        if (item == null || item.getPrice() <= 0 || item.getPackCount() <= 0) return -1;
        double price = item.getPrice() * Config.RATE_SHOP_PURCHASING_PRICE / item.getPackCount();
        if (!Double.isFinite(price) || price <= 0D) return -1;
        for (L1ShopItem offer : buybackSaleOffers) {
            if (offer.getItemId() != item.getItemId() || offer.getEnchant() != item.getEnchant()) continue;
            final long packagePrice = L1ShopBuyOrderList.sellingPackagePrice(offer);
            if (packagePrice > 0) {
                // Compare untaxed units in the same currency, even while the sale stock is exhausted.
                price = Math.min(price, packagePrice / Math.max(1, offer.getPackCount()));
            }
        }
        // A zero quote cannot settle without taking the item for free.
        final long wholePrice = (long) price;
        return wholePrice > 0 && wholePrice <= 2_000_000_000L ? (int) wholePrice : -1;
    }

    private List<L1ShopItem> captureBuybackSaleOffers() {
        if (_sellingItems.isEmpty() || _purchasingItems.isEmpty() || !hasComparableBuybackCurrency())
            return List.of();
        final List<L1ShopItem> offers = new ArrayList<>();
        for (L1ShopItem offer : _sellingItems) {
            if (offer != null && offer.getItem() != null && offer.getPrice() > 0
                    && getPurchasingItem(offer.getItemId(), offer.getEnchant()) != null) offers.add(offer);
        }
        // Stock changes can remove catalog rows without changing the configured buyback ceiling.
        return List.copyOf(offers);
    }

    private boolean hasComparableBuybackCurrency() {
        // See prepareSellItems and prepareBuyItems for the actual checkout currencies.
        if ((_npcId >= 5000001 && _npcId <= 5000004) || (_npcId >= 5000006 && _npcId <= 5000018)
                || (_npcId >= 66110 && _npcId <= 66120)) return false;
        return switch (_npcId) {
            case 2, 3, 4, 5, 224, 519, 520, 521, 523, 2998, 2999, 5072, 5073,
                 70017, 70035, 70041, 70042, 91245, 200005, 200060, 200061, 200062, 200063,
                 900047, 900107, 5000000, 6200010, 7200002, 7310101, 7310113, 7310119,
                 7310120, 7310124, 7310126, 7310128, 7310200, 7310201, 7310202,
                 7320124, 7320222, 7320223, 7320250, 8500140, 460000360, 460000361 -> false;
            default -> true;
        };
    }

	public List<L1AssessedItem> assessItems(CUserInventory inv) {
		List<L1AssessedItem> result = new ArrayList<L1AssessedItem>();
		for (L1ShopItem item : _purchasingItems) {
			if (item == null) {
				continue;
			}
			final var owned = inv.GetItemListByClassID(item.getItemId());
			if (owned.length == 0) continue;
			final int price = getAssessedPrice(item);
			if (price < 0) continue;
			for (L1ItemInstance targetItem : owned) {
				if (!isPurchaseableItem(targetItem)) {
					continue;
				}
				if (item.getEnchant() == targetItem.getEnchantLevel()) { // 인챈트가 같은 아이템만
					result.add(new L1AssessedItem(targetItem.getId(), price));
				}
			}
		}
		return result;
	}

	private boolean ensureSell(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPriceTaxIncluded();

		if (!IntRange.includes(price, 0, 2000000000)) {
			SC_ServerMessage.send(pc, 904, "2000000000");
			return false;
		}
		
		if (!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA, price)) {
			SC_ServerMessage.send(pc, 189);
			return false;
		}
		
		
		
		long currentWeight = pc.getInventory().GetCarryingWeight() * 2000L;// 구매 무게
		if ((currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 3000L))// 맥스
																					// 무게에
																					// 따른
		{
			SC_ServerMessage.send(pc, 82);
			return false;
		}

		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		// ## (버그 방지) 상점 버그 방지
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		
		int aaaa = Config.마일리지값 > 0 ? price / Config.마일리지값 : 0;
		int dd =  pc.getInventory().GetUnequippedItemAmount(40308) - price ;
	
		  int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.마일리지엔피씨);
		  if (Config.마일리지지급여부){
	        for (int i = 0; i < bossIds.length; i++) {
	          if (getNpcId()  == bossIds[i]) {
	        	  
	        		if (dd >= 0 ) {
	    				if (aaaa > 0 ) {
	    				
	    					shopMessage(pc, "\\fR[마일리지] 획득 : " + aaaa + "개");
	    					grantPrepared(pc.getInventory(), 131300, aaaa );
	          
	        		}
	          }
	        }
		  }
		  }
		return true;
	}

	

	

	private void payTax(L1ShopBuyOrderList orderList) {
        L1TaxCalculator calc=orderList.getTaxCalculator();
        int price=orderList.getTotalPrice(), castleId=L1CastleLocation.getCastleIdByNpcid(_npcId);
        int castleTax=calc.calcCastleTaxPrice(price), nationalTax=calc.calcNationalTaxPrice(price);
        if(castleId==L1CastleLocation.ADEN_CASTLE_ID || castleId==L1CastleLocation.DIAD_CASTLE_ID) {
            castleTax=Math.addExact(castleTax,nationalTax);nationalTax=0;
        }
        java.util.Map<Integer,Integer> taxes=new java.util.TreeMap<>();
        if(castleId!=0 && castleTax>0) taxes.merge(castleId,castleTax,Math::addExact);
        if(castleId!=0 && nationalTax>0) taxes.merge(L1CastleLocation.ADEN_CASTLE_ID,nationalTax,Math::addExact);
        int diadTax=calc.calcDiadTaxPrice(price);
        if(diadTax>0)taxes.merge(L1CastleLocation.DIAD_CASTLE_ID,diadTax,Math::addExact);
        for(java.util.Map.Entry<Integer,Integer> tax:taxes.entrySet()) {
            final CastleWar war=CastleWarBusiness.getInstance().get(tax.getKey());
            if(war==null)throw new IllegalStateException("Tax castle missing");
            final int before=war.getPublicMoney(),delta=tax.getValue();
            final long after=(long)before+delta;
            if(before<0 || delta<0 || after>2000000000L)throw new IllegalStateException("Tax treasury limit");
            prepared().tx.requireTables("castle");
            prepared().tx.sql(con -> {
                try(java.sql.PreparedStatement ps=con.prepareStatement(
                        "UPDATE castle SET public_money=? WHERE castle_id=? AND public_money=?")) {
                    ps.setInt(1,(int)after);ps.setInt(2,war.getCastleId());ps.setInt(3,before);
                    if(ps.executeUpdate()!=1)throw new java.sql.SQLException("Tax treasury changed");
                }
            });
            afterShopState(() -> war.setPublicMoney((int)after));
        }
    }

	private void sellItems(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, L1ItemId.ADENA, orderList.getTotalPriceTaxIncluded())) {
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		
		L1ItemInstance item = null;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			if (_npcId == 70035 || _npcId == 70041 || _npcId == 70042) {
				BugTicketInfo tInfo = BugRaceController.getInstance().find_ticket_info(itemId);
				if(tInfo != null){
					itemId = tInfo.converter_itemid;
				}
			}
			
			item = detachedShopItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			if (_npcId == 70068 || _npcId == 70020 || _npcId == 70056) {// 인챈상점
				//item.setIdentified(false);
				int chance = random.nextInt(150) + 1;
				if (chance <= 15) {
					item.setEnchantLevel(-2);
				} else if (chance >= 16 && chance <= 30) {
					item.setEnchantLevel(-1);
				} else if (chance >= 31 && chance <= 89) {
					item.setEnchantLevel(0);
				} else if (chance >= 90 && chance <= 141) {
					item.setEnchantLevel(random.nextInt(2) + 1);
				} else if (chance >= 142 && chance <= 147) {
					item.setEnchantLevel(random.nextInt(3) + 3);
				} else if (chance >= 148 && chance <= 149) {
					item.setEnchantLevel(6);
				} else if (chance == 150) {
					item.setEnchantLevel(7);
				}
			}
			if (_npcId == 810150) {
				if (Config.무기고급인첸트 == 10) {
					item.setEnchantLevel(Config.무기고급인첸트);
				} else if (Config.무기고급인첸트 == 9) {
					item.setEnchantLevel(9);
				} else if (Config.무기고급인첸트 == 8) {
					item.setEnchantLevel(8);
				} else if (Config.무기고급인첸트 == 7) {
					item.setEnchantLevel(7);
				} else if (Config.무기고급인첸트 == 6) {
					item.setEnchantLevel(6);
				} else if (Config.무기고급인첸트 == 5) {
					item.setEnchantLevel(5);
				} else if (Config.무기고급인첸트 == 4) {
					item.setEnchantLevel(4);
				} else if (Config.무기고급인첸트 == 3) {
					item.setEnchantLevel(3);
				} else if (Config.무기고급인첸트 == 2) {
					item.setEnchantLevel(2);
				} else if (Config.무기고급인첸트 == 1) {
					item.setEnchantLevel(1);
				}
			}
			if (_npcId >= 810151 && _npcId <= 810159) {
				if (Config.방어구인첸트 == 10) {
					item.setEnchantLevel(10);
				} else if (Config.방어구인첸트 == 9) {
					item.setEnchantLevel(9);
				} else if (Config.방어구인첸트 == 8) {
					item.setEnchantLevel(8);
				} else if (Config.방어구인첸트 == 7) {
					item.setEnchantLevel(7);
				} else if (Config.방어구인첸트 == 6) {
					item.setEnchantLevel(6);
				} else if (Config.방어구인첸트 == 5) {
					item.setEnchantLevel(5);
				} else if (Config.방어구인첸트 == 4) {
					item.setEnchantLevel(4);
				} else if (Config.방어구인첸트 == 3) {
					item.setEnchantLevel(3);
				} else if (Config.방어구인첸트 == 2) {
					item.setEnchantLevel(2);
				} else if (Config.방어구인첸트 == 1) {
					item.setEnchantLevel(1);
				}
			}
			if (_npcId == 810160) {
				if (Config.악세사리 == 10) {
					item.setEnchantLevel(10);
				} else if (Config.악세사리 == 9) {
					item.setEnchantLevel(9);
				} else if (Config.악세사리 == 8) {
					item.setEnchantLevel(8);
				} else if (Config.악세사리 == 7) {
					item.setEnchantLevel(7);
				} else if (Config.악세사리 == 6) {
					item.setEnchantLevel(6);
				} else if (Config.악세사리 == 5) {
					item.setEnchantLevel(5);
				} else if (Config.악세사리 == 4) {
					item.setEnchantLevel(4);
				} else if (Config.악세사리 == 3) {
					item.setEnchantLevel(3);
				} else if (Config.악세사리 == 2) {
					item.setEnchantLevel(2);
				} else if (Config.악세사리 == 1) {
					item.setEnchantLevel(1);
				}
			}
			if (_npcId == 810161) {
				if (Config.룸티스 == 10) {
					item.setEnchantLevel(10);
				} else if (Config.룸티스 == 9) {
					item.setEnchantLevel(9);
				} else if (Config.룸티스 == 8) {
					item.setEnchantLevel(8);
				} else if (Config.룸티스 == 7) {
					item.setEnchantLevel(7);
				} else if (Config.룸티스 == 6) {
					item.setEnchantLevel(6);
				} else if (Config.룸티스 == 5) {
					item.setEnchantLevel(5);
				} else if (Config.룸티스 == 4) {
					item.setEnchantLevel(4);
				} else if (Config.룸티스 == 3) {
					item.setEnchantLevel(3);
				} else if (Config.룸티스 == 2) {
					item.setEnchantLevel(2);
				} else if (Config.룸티스 == 1) {
					item.setEnchantLevel(1);
				}
			}
			if (_npcId == 7310103 || _npcId == 7310199) {// 행운의 랜덤 상점

				int chance1 = random.nextInt(100) + 1;
				if (chance1 < 60) {
					item.setEnchantLevel(7);
				} else if (chance1 >= 60 && chance1 <= 70) {
					item.setEnchantLevel(8);
				} else if (chance1 >= 80 && chance1 <= 90) {
					item.setEnchantLevel(9);
				} else if (chance1 >= 95 && chance1 <= 100) {
					item.setEnchantLevel(10);
				}
			}
			if (_npcId == 7310200 ) {// SGmall
				//item.setIdentified(false);
				L1ItemInstance changeitem =inv.get_etc_itemid(400254);
				
				int bless = changeitem.get_bless_level();
				int enchantlv = changeitem.getEnchantLevel();
					item.setEnchantLevel(enchantlv);
					item.set_bless_level(bless);
			}
			if (_npcId == 7310201) {// SGmall
				//item.setIdentified(false);
				L1ItemInstance changeitem = inv.GetFirstItemByClassID(446656);
				
				int bless = changeitem.get_bless_level();
				int enchantlv = changeitem.getEnchantLevel();
					item.setEnchantLevel(enchantlv);
					item.set_bless_level(bless);
			}
			if (_npcId == 7310202 ) {// SGmall
				//item.setIdentified(false);
				if(inv.GetFirstItemByClassID(446657) != null) {
				L1ItemInstance changeitem = inv.GetFirstItemByClassID(446657);
				
				int bless = changeitem.get_bless_level();
				int enchantlv = changeitem.getEnchantLevel();
					item.setEnchantLevel(enchantlv);
					item.set_bless_level(bless);
				}
			}
			if (_npcId == 900173) {// 인챈상점
				//item.setIdentified(false);
				int chance = random.nextInt(170) + 1;
				if (chance <= 20) {
					item.setEnchantLevel(-2);
				} else if (chance >= 25 && chance <= 35) {
					item.setEnchantLevel(-1);
				} else if (chance >= 40 && chance <= 55) {
					item.setEnchantLevel(0);
				} else if (chance >= 60 && chance <= 70) {
					item.setEnchantLevel(1);
				} else if (chance >= 100 && chance <= 120) {
					item.setEnchantLevel(2);
				} else if (chance >= 150 && chance <= 170) {
					item.setEnchantLevel(3);
				}
			}
			if (_npcId == 7310125) {// 인챈상점
				//item.setIdentified(false);
				int chance = random.nextInt(120) + 1;
				if (chance < 20) {
					item.setEnchantLevel(-2);
				} else if (chance >= 20 && chance <= 30) {
					item.setEnchantLevel(-1);
				} else if (chance > 30 && chance <= 50) {
					item.setEnchantLevel(0);
				} else if (chance > 50 && chance <= 60) {
					item.setEnchantLevel(1);
				} else if (chance > 60 && chance <= 70) {
					item.setEnchantLevel(2);
				} else if (chance > 70 && chance <= 80) {
					item.setEnchantLevel(3);
				} else if (chance > 90 && chance <= 100) {
					item.setEnchantLevel(4);
				} else if (chance > 100 && chance <= 110) {
					item.setEnchantLevel(5);
				} else if (chance > 110 && chance <= 115) {
					item.setEnchantLevel(6);
				} else if (chance > 115 && chance <= 120) {
					item.setEnchantLevel(7);
				}
			}
			if (_npcId == 7310104) {// 마족
				//item.setIdentified(false);
				int chance1 = random.nextInt(100) + 2;
				if (chance1 <= 15) {
					item.setEnchantLevel(-3);
				} else if (chance1 >= 31 && chance1 <= 40) {
					item.setEnchantLevel(-2);
				} else if (chance1 >= 41 && chance1 <= 50) {
					item.setEnchantLevel(-1);
				} else if (chance1 >= 10 && chance1 <= 20) {
					item.setEnchantLevel(0);
				} else if (chance1 >= 21 && chance1 <= 30) {
					item.setEnchantLevel(random.nextInt(2) + 1);
				} else if (chance1 >= 51 && chance1 <= 60) {
					item.setEnchantLevel(random.nextInt(3) + 3);
				} else if (chance1 >= 61 && chance1 <= 70) {
					item.setEnchantLevel(2);
				} else if (chance1 >= 71 && chance1 <= 80) {
					item.setEnchantLevel(3);
				} else if (chance1 >= 81 && chance1 <= 90) {
					item.setEnchantLevel(4);
				} else if (chance1 == 100) {
					item.setEnchantLevel(5);
				}
			}
				
				
			
				/** 아덴 인챈상점 추가 **/
			/*} else if (_npcId == 200004 || _npcId == 900171 || _npcId == 81008 || _npcId == 7320053 || _npcId == 526 || _npcId == 531 || _npcId == 70010
					|| _npcId == 200005 || _npcId == 7320222 || _npcId == 7320223 || _npcId == 2020561 || _npcId ==7321110
					|| _npcId ==7321111 || _npcId ==7321112 || _npcId ==7321113 || _npcId ==7321114 || _npcId ==7321115
					|| _npcId ==7321116 || _npcId ==7321117 || _npcId ==7321118 || _npcId == 1001 || _npcId ==1002 ||  _npcId ==1004 || _npcId ==200003) {
				item.setEnchantLevel(enchant);
			}*/
			// 배당을 측정하기 위한 추가 부분
			if (_npcId == 70035 || _npcId == 70041 || _npcId == 70042) {
				final int ticketId = item.getItemId();
                afterShop(() -> BugRaceController.getInstance().on_buy_ticket(ticketId, amount));
/*				for (int row = 0; row < 5; row++) {
					if (BugRaceController.getInstance()._ticketId[row] == item.getItemId()) {
						BugRaceController.getInstance()._ticketCount[row] += amount;
					}
				}*/
			}
			item = grantPrepared(inv, item);
			
		}
	}

	/** [ 구매할때 깃털상점 추가 [2] **/
	private void prepareSellItems(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		if (_npcId == 7320055) return;
		
		   if ((pc == null) ) {
			      return;
			    }

		
		if (getNpcId() == 200060 || getNpcId() == 200061 || getNpcId() == 200062 || getNpcId() == 200063 /*|| getNpcId() == 7310103*/ || getNpcId() == 523
				|| getNpcId() == 5000000 || getNpcId() == 900047 || getNpcId() == 5072 || getNpcId() == 5073 || getNpcId() == 519 || getNpcId()== 224
				|| getNpcId() == 7310101 || getNpcId() == 7310113 || getNpcId() == 7320124|| getNpcId() == 7310200|| getNpcId() == 7310201|| getNpcId() == 7310202) {
			if (!ensurePremiumSell(pc, orderList)) {
				return;
			}
			sellPremiumItems(pc.getInventory(), orderList);
			return;
		}
		
		
		if (getNpcId() == 5000006) {
			if (!ensureMarkSell1(pc, orderList)) {
				return;
			}
			sellMarkItems1(pc.getInventory(), orderList);
			return;
		}
		//TODO 행베리
		if (getNpcId() == 7000077) {
			if (!ensureMarkSell2(pc, orderList)) {
				return;
			}
			sellMarkItems2(pc.getInventory(), orderList);
			return;
		}
		//TODO 판도라의 증서
		if (this.getNpcId() == 91245) {
			if (!this.ensure_cert(pc, orderList)) {
				return;
			}
			this.sell_cert(pc.getInventory(), orderList);
			return;
		}
		//TODO 오림의 가넷
		if (this.getNpcId() == 70017 || this.getNpcId() == 460000360 || this.getNpcId() == 460000361) {
			if (!this.ensure_cert3(pc, orderList)) {
				return;
			}
			this.sell_cert3(pc.getInventory(), orderList);
			return;
		}
		//TODO 하딘의 가넷
		if (this.getNpcId() == 7320250) {
			if (!this.ensure_Hardin(pc, orderList)) {
				return;
			}
			this.sell_Hardin1(pc.getInventory(), orderList);
			return;
		}
		//TODO 군터의 인장
		if (this.getNpcId() == 8500140) {
			if (!this.ensure_certRNS(pc, orderList)) {
				return;
			}
			this.sell_certRNS(pc.getInventory(), orderList);
			return;
		}
		//TODO 이벤트기간에 구매
		if (this.getNpcId() == 200005 || this.getNpcId() == 7320222 || this.getNpcId() == 7320223 || this.getNpcId() == 6200010) {
			if (!this.ensure_cert6(pc, orderList)) {
				return;
			}
			this.sell_cert6(pc.getInventory(), orderList);
			return;
		}
		//TODO 종이학
		if (this.getNpcId() == 5 || this.getNpcId() == 7310119 || this.getNpcId() == 7310120 || this.getNpcId() == 7310126 || this.getNpcId() == 7310124) {
			if (!this.ensure_cert1(pc, orderList)) {
				return;
			}
			this.sell_cert1(pc.getInventory(), orderList);
			return;
		}
		if (this.getNpcId() == 5000007 || this.getNpcId() == 5000008 || this.getNpcId() == 5000009 || this.getNpcId() == 5000010
				|| this.getNpcId() >= 5000001 && this.getNpcId() <= 5000004
				|| this.getNpcId() >= 5000011 && this.getNpcId() <= 5000018) {
			if (!this.ensure_cert7(pc, orderList)) {
				return;
			}
			this.sell_cert5(pc.getInventory(), orderList);
			return;
		}
		if (this.getNpcId() == 520) {
			if (!this.ensure_cert4(pc, orderList)) {
				return;
			}
			this.sell_cert1(pc.getInventory(), orderList);
			return;
		}
		if (this.getNpcId() >= 66110 && this.getNpcId() <= 66120) {
			if (!this.혈맹포인트1(pc, orderList)) {
				return;
			}
			this.혈맹포인트(pc, orderList);
			return;
		}
		
		//TODO 콜로세움
		if (this.getNpcId() == 2999 || this.getNpcId() == 2998) {
			if (!this.ensure_Colosseum(pc, orderList)) {
				return;
			}
			this.sell_Colosseum(pc.getInventory(), orderList);
			return;
		}
		//TODO 여의주
		if (this.getNpcId() == 7310128) {
			if (!this.ensure_cert2(pc, orderList)) {
				return;
			}
			this.sell_cert2(pc.getInventory(), orderList);
			return;
		}
		//TODO 루돌프의 종
		if (this.getNpcId() == 521) {
			if (!this.ensure_cert5(pc, orderList)) {
				return;
			}
			this.sell_cert4(pc.getInventory(), orderList);
			return;
		}
		//TODO 탐(TAM)상인
		if (getNpcId() == 7200002) {
			if (!탐상인1(pc, orderList)) {
				return;
			}
			탐상인2(pc, pc.getInventory(), orderList);
			return;
		}
		// 영자 엔피씨무인상점
		if (getNpcId() >= 4000001 && getNpcId() <= 4000061 || getNpcId() == 7320087 || getNpcId() == 7320122 || getNpcId() == 7320123
				|| getNpcId() == 7320157) {
			if (!NoTaxEnsureSell(pc, orderList)) {
				return;
			}
			NpcShopSellItems(pc.getInventory(), orderList);
			return;

		}
		/** 패키지상점 **/
		// 1차 코인 상인
		if (getNpcId() >= 6100000 && getNpcId() <= 6100011) {
			if (!ensureCashSell1(pc, orderList, getNpcId())) {
				return;
			}
			sellCashItems1(pc, pc.getInventory(), orderList, getNpcId());
			return;
		}

		// 2차 코인 상인
		if (getNpcId() >= 6100012 && getNpcId() <= 6100023) {
			if (!ensureCashSell2(pc, orderList, getNpcId())) {
				return;
			}
			sellCashItems2(pc, pc.getInventory(), orderList, getNpcId());
			return;
		}

		// 3차 코인 상인
		if (getNpcId() >= 6100024 && getNpcId() <= 6100035) {
			if (!ensureCashSell3(pc, orderList, getNpcId())) {
				return;
			}
			sellCashItems3(pc, pc.getInventory(), orderList, getNpcId());
			return;
		}
		/** 패키지상점 **/

		// 장비 코인상점 (종합)
		if (getNpcId() >= 2 && getNpcId() <= 4) {
			if (!ensureCashSell4(pc, orderList, getNpcId())) {
				return;
			}
			sellCashItems4(pc, pc.getInventory(), orderList, getNpcId());
			return;
		}

		// 신묘년 이벤트
		if (getNpcId() == 900107) {
			if (!ensureMarkSell(pc, orderList)) {
				return;
			}
			sellMarkItems(pc.getInventory(), orderList);
			return;
		}
		if (!ensureSell(pc, orderList)) {
			return;
		} else {
			sellItems(pc.getInventory(), orderList);
			payTax(orderList);
		}

	}

	private void prepareBuyItems(L1ShopSellOrderList orderList) {
		if (_npcId == 7320055) return;
		CUserInventory inv = orderList.getPc().getInventory();
        
        // Validate the complete order before removing any item. In particular,
        // widening AFTER an int multiplication does not detect price overflow.
        long projectedTotal = 0L;
        HashMap<Integer, Integer> requested = new HashMap<Integer, Integer>();
        for (L1ShopSellOrder order : orderList.getList()) {
            L1ItemInstance offered = inv.GetByServerID(order.getItem().getTargetId());
            final int price = assessOwnedUnitPrice(offered);
            if (price <= 0 || price != order.getItem().getAssessedPrice() || order.getCount() <= 0) return;
            final long quantity = (long) requested.getOrDefault(offered.getId(), 0) + order.getCount();
            if (quantity > offered.getCount() || (!offered.isStackable() && quantity != 1)) return;
            requested.put(offered.getId(), (int) quantity);
            final long linePrice = (long) price * order.getCount();
            if (linePrice < 0 || (projectedTotal += linePrice) > 2000000000L) return;
        }
		int totalPrice = 0;
		L1Object object = null;
		L1ItemInstance item = null;
		int adenaId = 40308;


		for (L1ShopSellOrder order : orderList.getList()) {
			object = inv.GetByServerID(order.getItem().getTargetId());
			item = (L1ItemInstance) object;
			if (item == null)
				continue;
			if (item.getItem().getBless() < 128) {
                final long linePrice = (long) order.getItem().getAssessedPrice() * order.getCount();
                if (linePrice < 0 || (long) totalPrice + linePrice > 2000000000L) return;
                int count = removePrepared(inv, item, order.getCount());
                totalPrice += (int) ((long) order.getItem().getAssessedPrice() * count);
			}
		}

		if(item == null){
			return;
		}
		
		int ccc = totalPrice / 100000000 ;
		int aa = ccc * 100000000;
		L1ItemInstance itemExist = inv.GetFirstItemByClassID(40308);
		
		
		if (totalPrice >= 100000000) {
		//if (!(getNpcId() == 70035 || getNpcId() == 170041)) {
		totalPrice -= aa;
		shopMessage(inv.getOwner(), "\\fR1억 이상 판매가격 수표 변환 : "+ccc+"장 변환"); 
		shopMessage(inv.getOwner(), "\\fR억단위 제외한 금액은 아데나로 드렸습니다."); 
		grantPrepared(inv, 400254, ccc);
	//}
	}	
		if (itemExist != null && ((long) itemExist.getCount() + totalPrice) > 1900000000L) {
			removePrepared(inv, itemExist, 1900000000);
			grantPrepared(inv, 400254, 19);
			shopMessage(inv.getOwner(), "\\fR19억을 초과하여 1억수표 19장으로 변환되었습니다.");
			
			
		}
		
		
		

		totalPrice = IntRange.ensure(totalPrice, 0, 2000000000);
		if (0 < totalPrice) {
			


			/** 패키지상점 **/
			if (getNpcId() >= 6100000 && getNpcId() <= 6100035) {
				grantPrepared(inv, getNpcId() - 5299999, totalPrice);
			} else if (_npcId == 7000077) {// 행베리
				if (0 < totalPrice) {
					grantPrepared(inv, 41302, totalPrice);
				}
				
		
				
				
				
			} else {
				grantPrepared(inv, L1ItemId.ADENA, totalPrice);
			}
		}
	}

    private static final ThreadLocal<ShopPreparation> SHOP_PREPARATION = new ThreadLocal<>();
    private static final class ShopPreparation {
        final L1InventoryTransaction tx;
        final java.util.ArrayList<Runnable> after = new java.util.ArrayList<>();
        final java.util.ArrayList<Runnable> state = new java.util.ArrayList<>();
        int grants;
        ShopPreparation(L1InventoryTransaction tx) { this.tx=tx; }
        void complete() {
            for(Runnable action:state)action.run();
            for(Runnable action:after)try{action.run();}catch(RuntimeException failure){
                java.util.logging.Logger.getLogger(L1Shop.class.getName()).log(java.util.logging.Level.WARNING,"Committed NPC shop notification failed",failure);
            }
        }
    }
    private static ShopPreparation prepared() {
        ShopPreparation batch=SHOP_PREPARATION.get();
        if(batch==null)throw new IllegalStateException("NPC shop transaction required");
        return batch;
    }
    private static void afterShop(Runnable action) { prepared().after.add(action); }
    private static void afterShopState(Runnable action) { prepared().state.add(action); }
    private static void shopMessage(L1PcInstance pc,String message) { afterShop(() -> SC_SystemMessage.send(pc,message)); }
    private static boolean debitPrepared(CInventory inventory,int template,int count) {
        return prepared().tx.debit(template,count);
    }
    private static int removePrepared(CInventory inventory,L1ItemInstance item,int count) {
        if(!prepared().tx.debit(item,count))throw new IllegalStateException("NPC sale quantity changed");
        return count;
    }
    private static L1ItemInstance grantPrepared(CInventory inventory,L1ItemInstance item) {
        L1ItemInstance result=prepared().tx.grant(item);
        if(result==null)throw new IllegalStateException("NPC grant capacity/configuration");
        prepared().grants++;
        return result;
    }
    private static L1ItemInstance grantPrepared(CInventory inventory,int template,int count) {
        L1ItemInstance result=prepared().tx.grant(template,count);
        if(result==null)throw new IllegalStateException("NPC grant capacity/configuration");
        prepared().grants++;
        return result;
    }
    private static L1ItemInstance grantPrepared(CInventory inventory,int template,int count,boolean identified) {
        L1ItemInstance item=detachedShopItem(template);item.setCount(count);item.setIdentified(identified);
        return grantPrepared(inventory,item);
    }
    private static L1ItemInstance detachedShopItem(int templateId) {
        L1Item template=CObjectDB.GetItem(templateId);
        if(template==null)throw new IllegalStateException("Missing NPC item template");
        L1ItemInstance item=new L1ItemInstance(template,1);
        item.setId(l1j.server.server.IdFactory.getInstance().nextId());
        item.setBless(template.getBless());item.setRemainingTime(template.getMaxUseTime());
        return item;
    }
    private void stageTamPoint(L1PcInstance pc, int price) {
        final l1j.server.server.Account account = pc.getAccount();
        final int before = account.getTamPoint();
        if (price <= 0 || before < price) throw new IllegalStateException("Account currency insufficient");
        prepared().tx.requireTables("accounts");
        prepared().tx.sql(con -> {
            try (java.sql.PreparedStatement ps = con.prepareStatement(
                    "UPDATE accounts SET Tam_Point=? WHERE login=? AND Tam_Point=?")) {
                ps.setInt(1, before - price);
                ps.setString(2, account.getName());
                ps.setInt(3, before);
                if (ps.executeUpdate() != 1) throw new java.sql.SQLException("Account currency changed");
            }
        });
        afterShopState(() -> account.addTamPoint(-price));
    }
    public void sellItems(L1PcInstance pc,L1ShopBuyOrderList orderList) {
        sellItems(pc,orderList,null);
    }
    /** Optional caller audit/notification, only after a nonempty purchase commits. */
    public void sellItems(L1PcInstance pc,L1ShopBuyOrderList orderList,Runnable afterCommitted) {
        if(_npcId == 7320055 || pc==null || orderList==null || orderList.getList().isEmpty() || orderList.BugOk()!=0)return;
        try {
            final List<L1ShopBuyOrder> orders = orderList.getList();
            if (orders.size() == 1) {
                if (orders.getFirst().getCount() <= 0) throw new IllegalArgumentException("Invalid NPC purchase quantity");
            } else {
                java.util.Map<Integer,Integer> quantities = new java.util.TreeMap<>();
                for (L1ShopBuyOrder order : orders) {
                    if (order.getCount() <= 0) throw new IllegalArgumentException("Invalid NPC purchase quantity");
                    quantities.merge(order.getItem().getItemId(), order.getCount(), Math::addExact);
                }
            }

            int key=CacheD.accountOrderKey("$castle-treasury-global");
            if(_npcId==7200002)key=CacheD.accountOrderKey(pc.getAccountName());
            if(_npcId>=66110 && _npcId<=66120) {if(pc.getClan()==null)return;key=pc.getClanid();}
            try(L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,key)) {
                ShopPreparation batch=new ShopPreparation(tx);SHOP_PREPARATION.set(batch);
                try {

                    prepareSellItems(pc,orderList);
                    if(batch.grants>0 && tx.hasChanges()) {
                        if(afterCommitted!=null)batch.after.add(afterCommitted);
                        tx.commit(batch::complete);
                    }
                }
                finally {SHOP_PREPARATION.remove();}
            }
        } catch(RuntimeException failure) {SC_SystemMessage.send(pc,"상점 거래를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");}
    }
    public void buyItems(L1ShopSellOrderList orderList) {
        if(_npcId == 7320055 || orderList==null || orderList.getPc()==null || orderList.getList().isEmpty() || orderList.BugOk()!=0)return;
        L1PcInstance pc=orderList.getPc();
        try(L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,CacheD.accountOrderKey("npc-sale:"+_npcId))) {
            ShopPreparation batch=new ShopPreparation(tx);SHOP_PREPARATION.set(batch);
            try {prepareBuyItems(orderList);if(tx.hasChanges())tx.commit(batch::complete);}
            finally {SHOP_PREPARATION.remove();}
        } catch(RuntimeException failure) {SC_SystemMessage.send(pc,"판매를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요.");}
    }

	public L1ShopBuyOrderList newBuyOrderList() {
		return new L1ShopBuyOrderList(this);
	}

	public L1ShopSellOrderList newSellOrderList(L1PcInstance pc) {
		return new L1ShopSellOrderList(this, pc);
	}


	/** 깃털 상점관련 **/
	private void sellPremiumItems(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 41159, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 픽시의 깃털을 소비할 수 없었습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int enchant = order.getItem().getEnchant();
			int amount = order.getCount();
			ThreadLocalRandom random = ThreadLocalRandom.current();
			item = detachedShopItem(itemId);
			item.setIdentified(true);
			if (getSellingItems().contains(item)) {
				return;
			}

			if (_npcId == 7310103) {// 행운의 랜덤 상점
				item.setIdentified(false);
				int chance1 = random.nextInt(100) + 1;
				if (chance1 <= 50) {
					item.setEnchantLevel(7);
				} else if (chance1 >= 60 && chance1 <= 70) {
					item.setEnchantLevel(8);
				} else if (chance1 >= 80 && chance1 <= 90) {
					item.setEnchantLevel(9);
				} else if (chance1 >= 95 && chance1 <= 100) {
					item.setEnchantLevel(10);
				} else {
					item.setEnchantLevel(7);
				}

				/** 깃털 인챈상점 추가 [1] **/
			} else if (_npcId == 5073 || _npcId == 7310101 || _npcId == 7310113 || _npcId == 519  || _npcId == 224 || _npcId == 523) {
				item.setIdentified(true);
				item.setEnchantLevel(enchant);
				item.setCount(amount);// 추가
			}
			item = grantPrepared(inv, item);
		}
	}

	// 프리미엄 상인으로 부터 아이템을 살수 있는지 체크//
	private boolean ensurePremiumSell(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		int FeatherCount = Config.FEATHER_SHOP_NUM;
		// 오버플로우 체크
		if (!IntRange.includes(price, 0, FeatherCount)) {
			SC_SystemMessage.send(pc, "픽시의 깃털은 한번에 " + Config.FEATHER_SHOP_NUM + "개 이상 사용할 수 없습니다.");
			return false;
		}
		// 구입할 수 있을까 체크
		if (!pc.getInventory().HaveItemByClassId(41159, price)) {
			SC_SystemMessage.send(pc, "픽시의 깃털이 부족합니다.");
			return false;
		}
		// 중량 체크
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private void sellMarkItems1(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000032, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 징표를 소비할 수 없습니다.");

		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}

	private boolean NoTaxEnsureSell(L1PcInstance pc, L1ShopBuyOrderList orderList) {

		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 2000000000)) {
			SC_ServerMessage.send(pc, 904, "2000000000");
			return false;
		}
		if (!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA, price)) {
			SC_ServerMessage.send(pc, 189);
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private synchronized void NpcShopSellItems_for_locked(CUserInventory inv, L1ShopBuyOrderList orderList) {
		int sellings_price = orderList.getTotalPrice();
		if(inv.GetUnequippedItemAmount(L1ItemId.ADENA) < sellings_price){
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		
		/** 2016.11.24 server 앱센터 시세 **/
		L1NpcInstance npc = L1World.getInstance().findNpc(getNpcId());
		/** 2016.11.24 server 앱센터 시세 **/
		
		for (L1ShopBuyOrder order : orderList.getList()) {
			int orderid = order.getOrderNumber();
			int amount = order.getCount();
			int remaindcount = getSellingItems().get(orderid).getCount();
			if (remaindcount < amount)
				return;
		}
		if (!debitPrepared(inv, L1ItemId.ADENA, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		L1ItemInstance item = null;
		boolean[] isRemoveFromList = new boolean[_sellingItems.size()];
		for (L1ShopBuyOrder order : orderList.getList()) {
			int orderid = order.getOrderNumber();
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			int remaindcount = getSellingItems().get(orderid).getCount();
			if (remaindcount < amount)
				return;
			item = detachedShopItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			if (remaindcount == amount) {
				isRemoveFromList[orderid] = true;
			} else
				_sellingItems.get(orderid).setCount(remaindcount - amount);
			grantPrepared(inv, item);
			for (int i = isRemoveFromList.length - 1; i >= 0; i--) {
				if (isRemoveFromList[i]) {
					_sellingItems.remove(i);
				}
			}
			
			/** 2016.11.24 server 앱센터 시세 **/
			npc.updateSellings(item.getId(), item.getCount());
			/** 2016.11.24 server 앱센터 시세 **/			
		}
	}
	
	private void NpcShopSellItems(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if(Config.IS_SELLINGS_SHOP_LOCK){
			NpcShopSellItems_for_locked(inv, orderList);
			return;
		}
		
		int sellings_price = orderList.getTotalPrice();
		if(inv.GetUnequippedItemAmount(L1ItemId.ADENA) < sellings_price){
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		
		/** 2016.11.24 server 앱센터 시세 **/
		L1NpcInstance npc = L1World.getInstance().findNpc(getNpcId());
		/** 2016.11.24 server 앱센터 시세 **/
		
		for (L1ShopBuyOrder order : orderList.getList()) {
			int orderid = order.getOrderNumber();
			int amount = order.getCount();
			int remaindcount = getSellingItems().get(orderid).getCount();
			if (remaindcount < amount)
				return;
		}
		if (!debitPrepared(inv, L1ItemId.ADENA, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 아데나를 소비 할 수 없습니다.");
		}
		L1ItemInstance item = null;
		boolean[] isRemoveFromList = new boolean[_sellingItems.size()];
		for (L1ShopBuyOrder order : orderList.getList()) {
			int orderid = order.getOrderNumber();
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			int remaindcount = getSellingItems().get(orderid).getCount();
			if (remaindcount < amount)
				return;
			item = detachedShopItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			if (remaindcount == amount) {
				isRemoveFromList[orderid] = true;
			} else
				_sellingItems.get(orderid).setCount(remaindcount - amount);
			grantPrepared(inv, item);
			for (int i = isRemoveFromList.length - 1; i >= 0; i--) {
				if (isRemoveFromList[i]) {
					_sellingItems.remove(i);
				}
			}
			
			/** 2016.11.24 server 앱센터 시세 **/
			npc.updateSellings(item.getId(), item.getCount());
			/** 2016.11.24 server 앱센터 시세 **/			
		}
	}
	
	

	private void sellMarkItems(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 410093, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 만월의정기를 소비할 수 없습니다.");
		} // 신묘년
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}

	private boolean ensureMarkSell2(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 행베리
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 1000000000)) {
			SC_SystemMessage.send(pc, "베리는 한번에 10억개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(41302, price)) {
			SC_ChatPacket.sendDirect(pc, pc, "베리가 부족합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private void sell_cert1(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000156, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 종이학을 소비할 수 없습니다.");
		} // 종이학
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			ThreadLocalRandom random = ThreadLocalRandom.current();
			item = detachedShopItem(itemId);
			item.setIdentified(true);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			
			if (_npcId == 7310126) {// 행운의 랜덤 상점
				item.setIdentified(false);
				int chance2 = random.nextInt(150) + 1;
				if (chance2 <= 15) {
					item.setEnchantLevel(-2);
				} else if (chance2 >= 20 && chance2 <= 30) {
					item.setEnchantLevel(-1);
				} else if (chance2 >= 31 && chance2 <= 40) {
					item.setEnchantLevel(0);
				} else if (chance2 >= 51 && chance2 <= 60) {
					item.setEnchantLevel(random.nextInt(1) + 2);
				} else if (chance2 >= 70 && chance2 <= 80) {
					item.setEnchantLevel(random.nextInt(2) + 3);
				} else if (chance2 >= 90 && chance2 <= 110) {
					item.setEnchantLevel(5);
				} else if (chance2 == 148) {
					item.setEnchantLevel(6);
				}
				//TODO 종이학 인챈아이템 구매시 NPC등록
		/*	}else if(_npcId == 5000017){
				item.setEnchantLevel(order.getItem().getEnchant());*/
			}
			item = grantPrepared(inv, item);
		}
	}

	private void sell_cert2(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000180, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 여의주을 소비할 수 없습니다.");
		} // 여의주
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			ThreadLocalRandom random = ThreadLocalRandom.current();
			item = detachedShopItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			if (_npcId == 010) {// 행운의 랜덤 상점
				item.setIdentified(false);
				int chance1 = random.nextInt(150) + 1;
				if (chance1 <= 15) {
					item.setEnchantLevel(-3);
				} else if (chance1 >= 31 && chance1 <= 40) {
					item.setEnchantLevel(-2);
				} else if (chance1 >= 21 && chance1 <= 30) {
					item.setEnchantLevel(-1);
				} else if (chance1 >= 15 && chance1 <= 30) {
					item.setEnchantLevel(0);
				} else if (chance1 >= 41 && chance1 <= 50) {
					item.setEnchantLevel(random.nextInt(2) + 1);
				} else if (chance1 >= 71 && chance1 <= 81) {
					item.setEnchantLevel(random.nextInt(2) + 3);
				} else if (chance1 >= 110 && chance1 <= 120) {
					item.setEnchantLevel(3);
				} else if (chance1 == 140) {
					item.setEnchantLevel(4);
				}
			}

			if (_npcId == 010) {// 행운의 랜덤 상점
				item.setIdentified(false);
				int chance2 = random.nextInt(150) + 1;
				if (chance2 <= 15) {
					item.setEnchantLevel(-2);
				} else if (chance2 >= 20 && chance2 <= 30) {
					item.setEnchantLevel(-1);
				} else if (chance2 >= 31 && chance2 <= 40) {
					item.setEnchantLevel(0);
				} else if (chance2 >= 51 && chance2 <= 60) {
					item.setEnchantLevel(random.nextInt(1) + 2);
				} else if (chance2 >= 70 && chance2 <= 80) {
					item.setEnchantLevel(random.nextInt(2) + 3);
				} else if (chance2 >= 90 && chance2 <= 110) {
					item.setEnchantLevel(5);
				} else if (chance2 == 148) {
					item.setEnchantLevel(6);
				}

				/** 여의주 인챈상점 추가 [1] **/
			}
			item = grantPrepared(inv, item);
		}
	}

	private void sell_Colosseum(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 710, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 콜로세움 코인를 소비할 수 없습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int enchant = order.getItem().getEnchant();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			} else if (_npcId == 2999 || _npcId == 2998) {
				item.setIdentified(true);
				item.setEnchantLevel(enchant);
				item.setCount(amount);
			}
			item = grantPrepared(inv, item);
		}
	}
	
	private void 혈맹포인트(L1PcInstance pc, L1ShopBuyOrderList orderList) {
        final l1j.server.server.model.L1Clan clan = pc.getClan();
        if (clan == null) throw new IllegalStateException("Clan missing");
        final int price = orderList.getTotalPrice();
        if (price <= 0) throw new IllegalStateException("Invalid clan point price");
        ClanTable.stageWarPointDelta(prepared().tx, clan, -price);
        afterShopState(() -> clan.addWarPointCommitted(-price));
        afterShop(() -> SC_SurvivalCry.send(pc,2));
        for (L1ShopBuyOrder order: orderList.getList()) {
            L1ItemInstance item=detachedShopItem(order.getItem().getItemId());
            item.setCount(order.getCount());item.setEnchantLevel(order.getItem().getEnchant());item.setIdentified(true);
            grantPrepared(pc.getInventory(),item);
        }
    }
	
	private boolean 혈맹포인트1(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 썸타는 상점
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (pc.getClanid() != 0) {
		if (pc.getClan().getWarPoint() < price) {
			SC_SystemMessage.send(pc, "혈맹포인트가 부족 합니다.");
			return false;
		}
		} else {
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	
	

	
	private boolean ensure_cert1(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 썸타는 상점
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000156, price)) {
			SC_SystemMessage.send(pc, "종이학이 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	private boolean ensure_cert4(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 썸타는 상점
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000156, price)) {
			SC_SystemMessage.send(pc, "LFC종이학이 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	private boolean ensure_cert7(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000156, price)) {
			SC_SystemMessage.send(pc, "\\f2미리보기 입니다. 구매는 (메티스)에게 문의 바랍니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	private boolean ensure_cert2(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 10000개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000180, price)) {
			SC_SystemMessage.send(pc, "여의주가 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}	
	private void sell_cert5(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000156, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 종이학을 소비할 수 없습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setIdentified(true);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			
			if(_npcId == 5000009 || _npcId == 5000007 || _npcId == 5000008 || _npcId == 5000010
					|| _npcId >= 5000001 && _npcId <= 5000004 || _npcId >= 5000011 && _npcId <= 5000018){
				item.setEnchantLevel(order.getItem().getEnchant());
			}
			item = grantPrepared(inv, item);
		}
	}
	private boolean ensure_Colosseum(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "구입: 한번에 10000개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(710, price)) {
			SC_SystemMessage.send(pc, "구입: 콜로세움 코인가 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}	

	
	private boolean ensure_cert5(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "구입: 한번에 10000개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(812, price)) {
			SC_SystemMessage.send(pc, "루돌프의 종 이 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private void sell_cert(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 90738, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 판도라의 증서 를 소비할 수 없습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}
	private void sell_certRNS(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000367, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 군터의 인장 를 소비할 수 없습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}
	private void sell_Hardin1(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000520, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 하딘의 가넷 을 소비할 수 없습니다.");
		} // 하딘의 가넷
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}
	private void sell_cert3(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 3000246, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 오림의 가넷 을 소비할 수 없습니다.");
		} // 오림의 가넷
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			
			if(_npcId == 70017 || _npcId == 460000360 || _npcId == 460000361){
				item.setEnchantLevel(order.getItem().getEnchant());
			}
			
			
			grantPrepared(inv, item);
		}
	}
	private void sell_cert6(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 400254, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 아데나 수표(1억) 를 소비할 수 없습니다.");
//			throw new IllegalStateException("구입에 필요한 아데나를 소비할 수 없습니다.");
		}
		
		
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			
			if(_npcId == 200005 || _npcId == 7320222 || _npcId == 7320223 || _npcId == 6200010){
				item.setEnchantLevel(order.getItem().getEnchant());
			}
			grantPrepared(inv, item);
		}
	}

	private boolean ensure_cert(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "구입: 한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(90738, price)) {
			SC_SystemMessage.send(pc, "구입: 판도라의 증서가 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	
	private boolean ensure_certRNS(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "구입: 한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000367, price)) {
			SC_SystemMessage.send(pc, "군터의 인장이 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private boolean ensure_Hardin(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 하딘의 가넷
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000520, price)) {
			SC_SystemMessage.send(pc, "하딘의 가넷이 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private boolean ensure_cert3(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 오림의 가넷
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 10000000)) {
			SC_SystemMessage.send(pc, "한번에 1,000만개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000246, price)) {
			SC_SystemMessage.send(pc, "오림의 가넷이 부족 합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	
	private boolean ensure_cert6(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 1000000000)) {
			SC_SystemMessage.send(pc, "한번에 10억 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(400254, price)) {
//			pc.sendPackets(new SC_ServerMessage(189));
			SC_SystemMessage.send(pc, "아데나수표(1억)이 부족 합니다.");
			return false;
		}
		
		L1ItemInstance item = pc.getInventory().GetFirstItemByClassID(400254);
		if (item.getBless() >= 128) {
			SC_SystemMessage.send(pc, "봉인된 수표로는 상점이용을 할 수 없습니다."); 
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "봉인된 수표로는 상점이용을 할 수 없습니다.");
			return false;
		}
		
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if(Config.IS_CHECKBOX_RULES_USE && _checkbox_rules.containsKey(temp.getItemId())){
				pc.sendPackets(String.format("%s는 이벤트기간에만 구매가능 합니다.", temp.getName()));
				return false;
			}
			
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private void sellMarkItems2(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 41302, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 베리를 소비할 수 없습니다.");
		} // 행베리
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}

	private boolean 탐상인1(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 9000000)) {
			SC_SystemMessage.send(pc, "TAM은 한번에 '900'만개 이상 사용할 수 없습니다.");
			return false;
		}
		if (pc.getAccount().getTamPoint() < price) {
			SC_SystemMessage.send(pc, "TAM이 부족합니다.");
			return false;
		}
		return ensureCommon(pc, orderList, price, 9000000, null,
				() -> SC_SystemMessage.send(pc, "TAM은 한번에 '900'만개 이상 사용할 수 없습니다."));
	}

	private void 탐상인2(L1PcInstance pc, CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (orderList.getTotalPrice() > pc.getAccount().getTamPoint()) {
			throw new IllegalStateException("구입에 필요한 TAM을 소비할 수 없습니다.");
		}
		stageTamPoint(pc, orderList.getTotalPrice());
        afterShop(() -> SC_ActionUI.send(pc, SC_ActionUI.TAM, pc.getAccount().getTamPoint()));

		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			L1ItemInstance item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			grantPrepared(inv, item);
		}
	}

	private void sellCashItems1(L1PcInstance pc, CUserInventory inv, L1ShopBuyOrderList orderList, int npcId) {
		if (!debitPrepared(inv, npcId - 5299999, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 안전코인을 소비할 수 없었습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			item.setPackage(true);
			grantPrepared(inv, item);
		}
	}

	private void sellCashItems2(L1PcInstance pc, CUserInventory inv, L1ShopBuyOrderList orderList, int npcId) {
		if (!debitPrepared(inv, npcId - 5299999, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 안전코인을 소비할 수 없었습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			item.setPackage(true);
			grantPrepared(inv, item);
		}
	}

	private void sellCashItems3(L1PcInstance pc, CUserInventory inv, L1ShopBuyOrderList orderList, int npcId) {
		if (!debitPrepared(inv, npcId - 5299999, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 안전코인을 소비할 수 없었습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			item.setPackage(true);
			grantPrepared(inv, item);
		}
	}

	private void sellCashItems4(L1PcInstance pc, CUserInventory inv, L1ShopBuyOrderList orderList, int npcId) {
		if (!debitPrepared(inv, 747, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 장비 코인을 소비할 수 없었습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			int enchant = order.getItem().getEnchant();
			item = detachedShopItem(itemId);
			item.setCount(amount);
			item.setIdentified(true);
			item.setEnchantLevel(enchant);
			item.setPackage(true);
			grantPrepared(inv, item);
		}
	}

	private boolean ensureCashSell1(L1PcInstance pc, L1ShopBuyOrderList orderList, int npcId) {
		int price = orderList.getTotalPrice();
		// 9000001 - 5299999 =
		if (!pc.getInventory().HaveItemByClassId(npcId - 5299999, price)) {
			SC_SystemMessage.send(pc, "\\aA[GM]: \\aG추가 구입은 \\aA'메티스'\\aG 문의주세요.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			// 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		for (L1ShopBuyOrder order : orderList.getList()) {
			L1Item temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private boolean ensureCashSell2(L1PcInstance pc, L1ShopBuyOrderList orderList, int npcId) {
		int price = orderList.getTotalPrice();

		if (!pc.getInventory().HaveItemByClassId(npcId - 5299999, price)) {
			SC_SystemMessage.send(pc, "\\aA[GM]: \\aG추가 구입은 \\aA'메티스'\\aG 문의주세요.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			// 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		for (L1ShopBuyOrder order : orderList.getList()) {
			L1Item temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private boolean ensureCashSell3(L1PcInstance pc, L1ShopBuyOrderList orderList, int npcId) {
		int price = orderList.getTotalPrice();

		if (!pc.getInventory().HaveItemByClassId(npcId - 5299999, price)) {
			SC_SystemMessage.send(pc, "\\aA[GM]: \\aG추가 구입은 \\aA'메티스'\\aG 문의주세요.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			// 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		for (L1ShopBuyOrder order : orderList.getList()) {
			L1Item temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	private void sell_cert4(CUserInventory inv, L1ShopBuyOrderList orderList) {
		if (!debitPrepared(inv, 812, orderList.getTotalPrice())) {
			throw new IllegalStateException("구입에 필요한 루돌프의 종 을 소비할 수 없습니다.");
		}
		L1ItemInstance item = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			int itemId = order.getItem().getItemId();
			int amount = order.getCount();
			item = detachedShopItem(itemId);
			if (getSellingItems().contains(item)) {
				return;
			}
			item.setCount(amount);
			item = grantPrepared(inv, item);
		}
	}
	private boolean ensureCashSell4(L1PcInstance pc, L1ShopBuyOrderList orderList, int npcId) {
		int price = orderList.getTotalPrice();

		if (!pc.getInventory().HaveItemByClassId(747, price)) {
			SC_SystemMessage.send(pc, "장비코인이 부족합니다.");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			// 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		// 개수 체크
		int totalCount = pc.getInventory().getSize();
		for (L1ShopBuyOrder order : orderList.getList()) {
			L1Item temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			// \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private boolean ensureMarkSell(L1PcInstance pc, L1ShopBuyOrderList orderList) {
		// 신묘년
		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 1000)) {
			SC_SystemMessage.send(pc, "만월의 정기는 한번에 1,000개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(410093, price)) {
			SC_ServerMessage.send(pc, 337, "$10196");
			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}

	private boolean ensureMarkSell1(L1PcInstance pc, L1ShopBuyOrderList orderList) {

		int price = orderList.getTotalPrice();
		if (!IntRange.includes(price, 0, 50000)) {
			SC_ChatPacket.sendDirect(pc, pc, "한번에 50,000개 이상 사용할 수 없습니다.");
			return false;
		}

		if (!pc.getInventory().HaveItemByClassId(3000032, price)) {
			SC_ChatPacket.sendDirect(pc, pc, "징표가 부족합니다.");

			return false;
		}
		long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
		if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
			SC_ServerMessage.send(pc, 82);
			return false;
		}
		int totalCount = pc.getInventory().getSize();
		L1Item temp = null;
		for (L1ShopBuyOrder order : orderList.getList()) {
			temp = order.getItem().getItem();
			if (temp.isStackable()) {
				if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) {
					totalCount += 1;
				}
			} else {
				totalCount += 1;
			}
		}
		if (totalCount > 180) {
			SC_ServerMessage.send(pc, 263);
			return false;
		}
		if (price <= 0 || price > 2000000000) {
			SC_Disconnect.send(pc);
			return false;
		}
		return true;
	}
	

    // =========================================================
    //  공통 ensure 헬퍼 (중복 제거용)
    // =========================================================
    private boolean ensureCommon(
            L1PcInstance pc,
            L1ShopBuyOrderList orderList,
            int price,
            int maxUseInclusive,
            Runnable lackCurrencyPacket,
            Runnable overLimitPacket
    ) {
        if (!IntRange.includes(price, 0, maxUseInclusive)) {
            if (overLimitPacket != null) overLimitPacket.run();
            return false;
        }

        // 중량 체크
        long currentWeight = pc.getInventory().GetCarryingWeight() * 1000L;
        if (currentWeight + orderList.getTotalWeightLong() > pc.getMaxWeight() * 1000L) {
            SC_ServerMessage.send(pc, 82);
            return false;
        }

        // 인벤 칸 체크
        int totalCount = pc.getInventory().getSize();
        for (L1ShopBuyOrder order : orderList.getList()) {
            L1Item temp = order.getItem().getItem();
            if (temp.isStackable()) {
                if (!pc.getInventory().HaveItemByClassId(temp.getItemId())) totalCount += 1;
            } else {
                totalCount += 1;
            }
        }
        if (totalCount > 180) {
            SC_ServerMessage.send(pc, 263);
            return false;
        }

        // 가격 sanity
        if (price <= 0 || price > 2000000000) {
            SC_Disconnect.send(pc);
            return false;
        }

        // currency 부족 패킷
        if (lackCurrencyPacket != null) {
            // caller가 currency check를 실패했을 때 호출하도록 쓰는 형태도 있는데,
            // 여기서는 공통 ensure만 제공하므로 caller에서 필요시 직접 호출해도 됨.
        }

        return true;
    }
}
