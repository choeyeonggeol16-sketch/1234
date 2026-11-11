
package l1j.server.server.clientpackets;

import java.util.ArrayList;

import l1j.server.BugRace.BugRaceController;
import l1j.server.DogFightSystem.DogFightController;
import l1j.server.DogFightSystem.DogFightSystemSettings;
import l1j.server.ItemExChangeSystem.S_ItemExSelectPacket;
import l1j.server.Templates.L1PrivateShopBuyList;
import l1j.server.Templates.L1PrivateShopSellList;
import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.datatables.NpcShopTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.PresentWarehouse;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.shop.Shop;
import l1j.server.server.model.shop.L1ShopBuyOrderList;
import l1j.server.server.model.shop.L1ShopSellOrderList;
import l1j.server.server.serverpackets.S_Disconnect; //## (버그 방지) 개인 상점 버그, 창고 복사 버그 방지 
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage; //## 창고 아이템 격납 제한 메시지 출력 위해 임포트 
import manager.SJManager;

public class C_Result extends ClientBasePacket {

	private static final String C_RESULT = "[C] C_Result";

	public C_Result(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);

		int npcObjectId = readD();
		int resultType = readC();
		int size = readC();
		int unknown = readC(); // 이 패킷이 없다면 값이 제대로 들어오지 않으니 지우지 말것!
		
		if(size < 0) {
			return;
		}

		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null) {
			return;
		}
		int level = pc.getLevel();

		int npcId = 0;
		String npcImpl = "";
		boolean isPrivateShop = false;
		// add
		boolean isPrivateNpcShop = false;
		// add
		boolean tradable = true;
		L1Object findObject = L1World.getInstance().findObject(npcObjectId);
		if (findObject != null) {
			int diffLocX = Math.abs(pc.getX() - findObject.getX());
			int diffLocY = Math.abs(pc.getY() - findObject.getY());
			// 3 매스 이상 떨어졌을 경우 액션 무효
			if (diffLocX > 12 || diffLocY > 12) {
				return;
			}
			if (findObject instanceof L1NpcInstance) {
				L1NpcInstance targetNpc = (L1NpcInstance) findObject;
				npcId = targetNpc.getNpcTemplate().get_npcId();
				npcImpl = targetNpc.getNpcTemplate().getImpl();
				if (npcImpl.equals("L1NpcShop")) {
					isPrivateNpcShop = true; 
				} 
	
			} else if (findObject instanceof L1PcInstance) {			
				if(npcObjectId == pc.getId() && resultType == 9){
					S_ItemExSelectPacket select_packet = pc.get_select_item();
					if(select_packet != null){
						int select_index = readD();
						int select_count = readD();
						if(select_count <= 0)
							return;
						select_packet.do_select(pc, select_index);
						select_packet.dispose();
						return;
					}
					pc.sendPackets("시간이 초과되었습니다.");
					return;
				}
				L1PcInstance gm = (L1PcInstance)findObject;
				if(gm.isGm() && resultType == 3) {
					int objectId 	= readD();
					int count 		= readD();
					L1Object tmp 	= L1World.getInstance().findObject(objectId);
					if(tmp != null && tmp instanceof L1PcInstance){
						gm.teleport(tmp.getX(), tmp.getY(), tmp.getMapId(), gm.getHeading(), 169, false, false);
					}else
						return;
				}
				isPrivateShop = true;
			
			} else if (findObject instanceof L1PcInstance) {
				isPrivateShop = true;
			}
		}
		

		if(pc.getOnlineStatus() == 0){
			clientthread.kick();
			return;
		}
		if(size > 1000){
			clientthread.kick();
			return;
		} 
		
		if (Config.QUIZSYM && !pc.isGm()) {
			if (!pc.isQuizValidated()) {
				pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
				pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 구매를 시도해주세요."));
				return;
			}
		}
		
		if (npcObjectId == 1541) { // 아이템 구입
			for (int i = 0; i < size; i++) { 
			    int objectid = readD();
				if (pc.getMapId() == 350 && !pc.isPrivateShop()) {
					ItemInstance item = (ItemInstance) L1World.getInstance().findObject(objectid);
					if (item != null) {
						boolean pc_check = false;
						for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
							if (target.getInventory().findItemObjId(objectid) != null) {
								if (target.getMapId() != 350)
									return;
								pc.teleport(target.getX()-1, target.getY(), target.getMapId(), pc.getHeading(), 169, true, false);
								pc.sendPackets(new S_NPCTalkReturn(target.getId(), "usershop"), true);
								pc_check = true;
							}
						}
						
						if (!pc_check) {
							for (L1Character target : L1World.getInstance().getAllNpc()) {
								if (target instanceof L1NpcShopInstance) {
									L1NpcInstance npc = (L1NpcInstance) target;
									if (npc.getNpcId() == item.getOwnerId()) {
										if (npc != null) {
											if (npc.getMapId() != 350)
												return;
											pc.teleport(npc.getX() - 1, npc.getY(), npc.getMapId(), pc.getHeading(), 169, true, false);
											pc.sendPackets(new S_NPCTalkReturn(npc.getId(), "usershop"), true);
										}
									}
								}
							}
						}
					}
				} else {
					pc.sendPackets("시장에서만 이용 가능합니다.");
				}
			}
		} 
		/********************************************************************************************************		
		 ****************************************** 아이템 구매 *****************************************************
		 *********************************************************************************************************/
		if (resultType == 0 && size != 0 && npcImpl.equalsIgnoreCase("L1Merchant")) { // 아이템 구입
			Shop shop = ShopTable.getInstance().get(npcId);
			if(shop == null){
				System.out.println(String.format("[샵]%d 엔피씨를 찾을 수 없습니다.", npcId));
				return;
			}
			
			if (Config.STANDBY_SERVER){
				pc.sendPackets(new S_SystemMessage("오픈대기중에는 할수없는 행동입니다."));
				return;
			}
			
	
			if (!pc.isGm() && pc.getClan() == null && pc.getLevel() >= Config.ALT_RESULTLEVEL) {
				pc.sendPackets(new S_SystemMessage(Config.ALT_RESULTLEVEL + "레벨 이상은 혈맹이 없으면 상점을 이용할 수 없습니다."));
				return;
			}
			
			L1ShopBuyOrderList orderList = shop.newBuyOrderList();
			
			if (pc.getInventory().getSize() > 180) {
				pc.sendPackets(new S_SystemMessage("소지하고 있는 아이템이 너무 많습니다.")); 
				return;
			}
			
			if (npcId == DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid) {
				if (DogFightController.getInstance().getdogState() != 0) {
					return;
				}
			}
			
			if (shop.getSellingItems().size() < size) {
				System.out.println("상점이 판매하는 아이템 수(" + shop.getSellingItems().size() + ")보다 더 많이 사려고 함.(" + size + ")개");
				pc.getNetConnection().kick();
				pc.getNetConnection().close();
				return;
			} 
			for (int i = 0; i < size; i++) { 
			    int objectid = readD();
			    int count =  readD();

			    if (count <= 0 || count >= 10000) {
					return;
				}   
				
				if (npcId == 70035 || npcId == 70041 || npcId == 70042) {
					int num = objectid;
					if (objectid >= 5 && objectid <= 9) {
						num -= 5;
					}
					else if (objectid >= 10 && objectid <= 13) {
						num -= 10;
					}
				
					else if (objectid >= 14 && objectid <= 19) {
						num -= 15;
					}
				
					BugRaceController.getInstance()._littleBugBear[num].addTotalPrice(shop.getSellingItems().get(objectid).getPrice() * count);
				}
			    
			    orderList.add(objectid, count, pc);
			    
			
			///	System.out.println("vmfktltm2  " +orderNumber );
			}
			int bugok = orderList.BugOk();
			if (bugok == 0) {
				shop.sellItems(pc, orderList);
				// 아이템저장시킴
				pc.saveInventory();
				// 아이템저장시킴
			}
			// 아이템저장시킴
			
			/********************************************************************************************************		
			 ****************************************** 아이템 판매 *****************************************************
			 *********************************************************************************************************/
			
		} else if (resultType == 1 && size != 0 && npcImpl.equalsIgnoreCase("L1Merchant")) { // 아이템 매각
			Shop shop = ShopTable.getInstance().get(npcId);
			L1ShopSellOrderList orderList = shop.newSellOrderList(pc);
			for (int i = 0; i < size; i++) { 
			    int objectid = readD();
			    int count =  readD();
				if (count <= 0) {
					return;
				}
			    orderList.add(objectid, (int) count, pc);
			}
			int bugok = orderList.BugOk();
			if (bugok == 0) {
				shop.buyItems(orderList);
				// 백섭복사 방지 수량성버그방지
				pc.saveInventory();
				// 백섭복사 방지 수량성버그방지
			}
			/********************************************************************************************************		
			 ****************************************** 개인 창고 맡기기 **************************************************
			 *********************************************************************************************************/	
		} else if (resultType == 2 && size != 0  && npcImpl.equalsIgnoreCase("Dwarf") && level >= 5) { // 자신의 창고에 격납
			int objectId, count;
			L1Object object = null;
			ItemInstance item = null;
			for (int i = 0; i < size; i++) {
				tradable = true;
				objectId = readD();
				count = readD();
				object = pc.getInventory().getItem(objectId);
				
				item = (ItemInstance) object;
				if (item == null) return;
				int itemType = item.getItem().getType2();
				//창고불가아이템 디비연동 NoShopAndWare
				int itemId = item.getItem().getItemId();
				if (pc.isTwoLogin(pc)) {
					return;
				}
				if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(itemId))  {// 
					pc.sendPackets(new S_SystemMessage("해당 아이템은 창고 이용을 할 수 없습니다."));
					return;
				}
				if (item.getItem().getMaxUseTime() > 0) {
					pc.sendPackets(new S_SystemMessage("시간제 아이템은 창고이용이 불가능 합니다."));
					return;
				}
 
				if (item == null || item.getCount() < count) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if ((itemType == 1 && item.getCount() != 1) || (itemType == 2 && item.getCount() != 1)) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (objectId != item.getId()) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (!item.isStackable() && count != 1) {	
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (count <= 0 || item.getCount() <= 0) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (item.getCount() > 2000000000) {
					return;
				}
				if (count > 2000000000) {
					return;
				}  
				if(!isAvailableTrade(pc, objectId, item, count)) break;			
				if(count > item.getCount()) count = item.getCount(); 
				//if(!isAvailablePcWeight(pc, item, count)) break;
				/**   창고 맡기기 부분 버그 방지 **/  
				if (!item.getItem().isTradable()) {
					tradable = false; 
					pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));   
				}
				DollInstance doll = pc.getMagicDoll();
				if ((doll != null) && (item.getId() == doll.getItemObjId())) {
					tradable = false;
					pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
					break;
				}
				
				Object[] petlist = pc.getPetList().values().toArray();
				for (Object petObject : petlist) {
					if (petObject instanceof PetInstance) {
						PetInstance pet = (PetInstance) petObject;
						if (item.getId() == pet.getItemObjId()) {
							tradable = false;
							// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
							pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
							break;
						}
					}
				}
				PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
				if(warehouse == null) return;

				if (warehouse.checkAddItemToWarehouse(item, count) == L1Inventory.SIZE_OVER) {
					pc.sendPackets(new S_ServerMessage(75)); // \f1상대가 물건을 너무 가지고 있어 거래할 수 없습니다.
					break;
				}
				if (item.getBless() >= 128) {
					pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
					break;
				}
				/** 시간제아이템 창고불가 */
				if (item.getEndTime() != null){
					pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 맞길수 없습니다."));
					break;
				}
				if (tradable) {
					pc.getInventory().tradeItem(objectId, count, warehouse);	
					pc.getLight().turnOnOffLight();
					SJManager.getInstance().WarehouseAppend(item.getLogName(), count, pc.getName(), 0);
				}
			}
			/********************************************************************************************************		
			 ****************************************** 개인 창고 찾기 **************************************************
			 *********************************************************************************************************/			
			
		} else if (resultType == 3 && size != 0 && npcImpl.equalsIgnoreCase("Dwarf") && level >= 5) { // 자신의 창고로부터 꺼내
			int objectId, count;
			ItemInstance item = null;
			for (int i = 0; i < size; i++) {
				objectId = readD();
				count = readD();
 
				PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
				
				if(warehouse == null) return;
				item = warehouse.getItem(objectId);  
				if (pc.isTwoLogin(pc)) {
					return;
				}
				if (!pc.isGm() && pc.getLevel() < 30) {
					pc.sendPackets(new S_SystemMessage("레벨 30 부터 이용하실수 있습니다."));
					return;
				}
				if (item == null) {
					return;
				}

				if (objectId != item.getId()) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (!item.isStackable() && count != 1) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (count <= 0 || item.getCount() <= 0) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				
				if (count > item.getCount()) {
					count = item.getCount();
				}

				if (item.getCount() <= 0 || count <= 0) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				
				if(item.getCount() > 2000000000){
					return;
				}
				if(count > 2000000000){
					return;
				}
				if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {// 용량 중량 확인 및 메세지 송신
					if (pc.getInventory().consumeItem(ItemId.아데나, 30)) {
						warehouse.tradeItem(item, count, pc.getInventory());
						if (((item.getCount() >= 100) && ((item.getItemId() == 41159) || (item.getItemId() == 40087)
								|| (item.getItemId() == 40074))) || (item.getEnchantLevel() > 0)
								|| ((item.getCount() >= 1000000) && (item.getItemId() == 40308))
								|| ((item.getCount() >= 1000) && (item.getItemId() != 40308)))
							SJManager.getInstance().WarehouseAppend(item.getLogName(), count, pc.getName(), 1);
					} else {
						pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
						break;
					}
				} else {
					pc.sendPackets(new S_ServerMessage(270)); // \f1 가지고 있는 것이 무거워서 거래할 수 없습니다.
					break;
				} 
			}
			/********************************************************************************************************		
			 *************************************** 혈맹 창고 맡기기 *****************************************************
			 *********************************************************************************************************/
		} else if (resultType == 4 && size != 0 && npcImpl.equalsIgnoreCase("Dwarf") && level >= 5) { // 크란 창고에 격납
			int objectId, count;
			if (pc.getClanid() != 0) { // 크란 소속
				for (int i = 0; i < size; i++) {
					tradable = true;
					objectId = readD();
					count = readD();
					L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
					L1Object object = pc.getInventory().getItem(objectId);
					ItemInstance item = (ItemInstance) object;
					if ( item == null ) {
						return;
					}	
					if (pc.isTwoLogin(pc)) {
						return;
					}
					//창고불가아이템 디비연동 NoShopAndWare
					int itemId = item.getItem().getItemId();
					if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(itemId))  {// 
						pc.sendPackets(new S_SystemMessage("해당 아이템은 창고 이용을 할 수 없습니다."));
						return;
					}
					if (item.getItem().getMaxUseTime() > 0) {
						pc.sendPackets(new S_SystemMessage("시간제 아이템은 창고이용이 불가능 합니다."));
						return;
					}
					int itemType = item.getItem().getType2();
					if (clan != null) {
						if (!item.getItem().isTradable()) {
							tradable = false;
							pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 
						}  
						if (item == null || item.getCount() < count) {
							pc.sendPackets(new S_Disconnect());
							return;
						}
						if (objectId != item.getId()) {
							pc.sendPackets(new S_Disconnect());
							return;
						}
						if (!item.isStackable() && count != 1) {
							pc.sendPackets(new S_Disconnect());
							return;
						}							
						if (count <= 0 || item.getCount() <= 0) {
							pc.sendPackets(new S_Disconnect());
							return;
						}
						if ((itemType == 1 && item.getCount() != 1) || (itemType == 2 && item.getCount() != 1)){
							pc.sendPackets(new S_Disconnect());
							return;
						}  
						if(item.getCount() > 2000000000){
							return;
						}
						if(count > 2000000000){
							return;
						}
						if(count > item.getCount())	count = item.getCount();
						if(!isAvailableTrade(pc, objectId, item, count)) break;  
						Object[] petlist = pc.getPetList().values().toArray();
						for (Object petObject : petlist) {
							if (petObject instanceof PetInstance) {
								PetInstance pet = (PetInstance) petObject;
								if (item.getId() == pet.getItemObjId()) {
									tradable = false;
									// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
									pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
									break;
								}
							}
						}
						DollInstance doll = pc.getMagicDoll();
						if ((doll != null) && (item.getId() == doll.getItemObjId())) {
							tradable = false;
							pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
							break;
						}

						if(item.getBless() >= 128){
							pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
							return;
						}
						/** 시간제아이템 창고불가 */
						if (item.getEndTime() != null){
							pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 맞길수 없습니다."));
							return;
						}
						/** 시간제아이템 창고불가 */
						ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
						if (clanWarehouse.checkAddItemToWarehouse(item, count) == L1Inventory.SIZE_OVER) {
							pc.sendPackets(new S_ServerMessage(75)); // \f1상대가 물건을 너무 가지고 있어 거래할 수 없습니다.
							break;
						}
						if (tradable) {
							pc.getInventory().tradeItem(objectId, count, clanWarehouse);
							pc.getLight().turnOnOffLight();
							SJManager.getInstance().EPWarehouseAppend(item.getLogName(), count, pc.getName(), 2);
						}
					}
				}
			} else {
				pc.sendPackets(new S_ServerMessage(208)); // \f1혈맹 창고를 사용하려면  혈맹에 가입하지 않으면 안됩니다.
			}
			/********************************************************************************************************		
			 *************************************** 혈맹 창고 찾기 *****************************************************
			 *********************************************************************************************************/
		} else if (resultType == 5 && size != 0 && npcImpl.equalsIgnoreCase("Dwarf") && level >= 5) { // 크란 창고로부터 꺼내
			int objectId, count;
			ItemInstance item;

			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
			if (clan != null) {
				for (int i = 0; i < size; i++) {
					objectId = readD();
					count = readD();
					item = clanWarehouse.getItem(objectId);   
					
					if ( item == null ) {						
						return; 
					}
					if (pc.isTwoLogin(pc)) {
						return;
					}
					if (objectId != item.getId()) {
						pc.sendPackets(new S_Disconnect());
						return;
					}

					if (!item.isStackable() && count != 1) {
						pc.sendPackets(new S_Disconnect());
						return;
					}
					if (count <= 0 || item.getCount() <= 0 || item.getCount() > 2000000000) {
						pc.sendPackets(new S_Disconnect());
						return;
					}
					if (count >= item.getCount()) {
						count = item.getCount();
					}

					int itemType = item.getItem().getType2();
					if (item.getCount() <= 0 || count <= 0 || (itemType == 1 && item.getCount() != 1)
							|| (itemType == 2 && item.getCount() != 1)) {
						pc.sendPackets(new S_Disconnect());
						return;
					}
					if (item.getCount() > 2000000000) {
						return;
					}
					if (count > 2000000000) {
						return;
					} 
					
					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // 용량 중량 확인 및 메세지 송신
						if (pc.getInventory().consumeItem(ItemId.아데나, 30)) {
							clanWarehouse.tradeItem(item, count, pc.getInventory());
							if ((item.getCount() >= 100 
									&& (item.getItemId() == 41159 
									|| item.getItemId() == 40087
									|| item.getItemId() == 40074))||item.getEnchantLevel() > 0
									|| item.getCount() >= 1000000 && item.getItemId() == 40308
									|| item.getCount() >= 1000 && item.getItemId() != 40308) {
								SJManager.getInstance().EPWarehouseAppend(item.getLogName(), count, pc.getName(), 3);
							} 
						} else {
							pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
							break;
						}
					} else {
						pc.sendPackets(new S_ServerMessage(270)); // \f1 가지고 있는 것이 무거워서 거래할 수 없습니다.
						break;
					} 
				}
				 
				clanWarehouse.setWarehouseUsingChar(0, 0);
			}
			
			/********************************************************************************************************		
			 *************************************** 패키지 창고 찾기  *************************************************
			 *********************************************************************************************************/
		} else if (resultType == 20 && size != 0 && findObject == pc) { // 부가서비스 창고(선물창고)
			
			int objectId, count;
			ItemInstance item;
			for (int i = 0; i < size; i++) {
				objectId = readD();
				count = readD(); 
				PresentWarehouse prewarehouse = WarehouseManager.getInstance().getPresentWarehouse(pc.getAccountName());
				item = prewarehouse.getItem(objectId);

				/**   창고 찾기 부분 버그 방지 **/				
				if ( item == null ) {
					
					return;
				}
				if (pc.isTwoLogin(pc)) {
					return;
				}
				if (objectId != item.getId()) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (!item.isStackable() && count != 1) {
					pc.sendPackets(new S_Disconnect());
					return;
				}			
				if (count <= 0 || item.getCount() <= 0) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (count > item.getCount()) {
					count = item.getCount();
				}
				/**   창고 찾기 부분 버그 방지 **/  
				if (pc.getInventory(). checkAddItem(item, count) == L1Inventory.OK) { // 용량 중량 확인 및 메세지 송신					
  
					prewarehouse.tradeItem(item, count, pc.getInventory());
				} else {
					pc.sendPackets(new S_ServerMessage(270)); // \f1 가지고 있는 것이 무거워서 거래할 수 없습니다.
					break;
				}
			}
			
		} else if (resultType == 5 && size == 0 && npcImpl.equalsIgnoreCase("Dwarf")) { // 크란 창고로부터 꺼내 안에 Cancel, 또는, ESC 키
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			
			ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
			if(clan != null){
				clanWarehouse.setWarehouseUsingChar(0, 0);
			}
			
			/********************************************************************************************************		
			 *************************************** 개인 상점 아이템 구매 *************************************************
			 *********************************************************************************************************/
		} else if (resultType == 0 && size != 0 && isPrivateShop) { // 개인 상점으로부터 아이템 구입
			int order;
			int count;
			int price;
			ArrayList<L1PrivateShopSellList> sellList;
			L1PrivateShopSellList pssl;
			int itemObjectId;
			int sellPrice;
			int sellTotalCount;
			int sellCount;
			ItemInstance item;
			boolean[] isRemoveFromList = new boolean[8];

			L1PcInstance targetPc = null;
			if (findObject instanceof L1PcInstance) {
				targetPc = (L1PcInstance) findObject;
			}
			if (targetPc == null) {
				return;
			}


			sellList = targetPc.getSellList();

			synchronized (sellList) { 
				// 품절이 발생해, 열람중의 아이템수와 리스트수가 다르다
				if (pc.getPartnersPrivateShopItemCount() != sellList.size()) {
					return;
				}
				if (pc.getPartnersPrivateShopItemCount() < sellList.size())
					return;

				if (targetPc.isTradingInPrivateShop()) {
					pc.sendPackets(new S_SystemMessage("이미 다른사람과 거래중 입니다. 다시 이용해주세요."));
					return;
				}

				for (int i = 0; i < size; i++) { // 구입 예정의 상품
					order = readD();
					count = readD();
					pssl = (L1PrivateShopSellList) sellList.get(order);
					itemObjectId = pssl.getItemObjectId();
					sellPrice = pssl.getSellPrice();
					sellTotalCount = pssl.getSellTotalCount(); // 팔 예정의 개수
					sellCount = pssl.getSellCount(); // 판 누계
					item = targetPc.getInventory().getItem(itemObjectId);
					if (item == null) {
						continue;
					}


					if (count > sellTotalCount - sellCount) {
						count = sellTotalCount - sellCount;
					}
					if (count <= 0) {
						continue;
					}
					if (item.isEquipped()) {
						pc.sendPackets(new S_ServerMessage(905, "")); // 장비 하고 있는 아이템 구매못하게.
						continue;
					}

					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // 용량 중량 확인 및 메세지 송신
						for (int j = 0; j < count; j++) { // 오버플로우를 체크
							if (sellPrice * j > 2000000000 || sellPrice * j < 0) {
								pc.sendPackets(new S_ServerMessage(904, // 총판 매가격은%d아데나를 초과할 수 없습니다.
										"2000000000"));
								return;
							}
						}
						price = count * sellPrice;	 

						/** 개인상점 버그방지 **/  					

						if (itemObjectId != item.getId()) {
							pc.sendPackets(new S_Disconnect());
							targetPc.sendPackets(new S_Disconnect());
							return;
						}
						if (!item.isStackable() && count != 1) {
							pc.sendPackets(new S_Disconnect());
							targetPc.sendPackets(new S_Disconnect());
							return;
						}
						if (count <= 0 || item.getCount() <= 0 || item.getCount()<count) {
							pc.sendPackets(new S_Disconnect());
							targetPc.sendPackets(new S_Disconnect());
							return;
						}
						if (count >= item.getCount()) {count = item.getCount();	}

						if (item.isEquipped()) {
							pc.sendPackets(new S_SystemMessage("상대방이 착용중인 아이템입니다."));
							return;
						}				
						if (price <= 0 ||price > 2000000000) return;
						/** 개인상점 버그방지 **/  

						if (pc.getInventory().checkItem(ItemId.아데나, price)) {
							ItemInstance adena = pc.getInventory()
									.findItemId(ItemId.아데나);
							if (targetPc != null && adena != null) {
								if (targetPc.getInventory().tradeItem(item,
										count, pc.getInventory()) == null) {
									return;
								}
								pc.getInventory().tradeItem(adena, price,targetPc.getInventory());
								String message = item.getItem().getName()+ " (" + String.valueOf(count) + ")";								
								targetPc.sendPackets(new S_ServerMessage(877, pc.getName(), message)); // %1%o// %0에 판매했습니다.
								pssl.setSellCount(count + sellCount);							
								sellList.set(order, pssl);
								writeLogbuyPrivateShop(pc, targetPc, item, count, price);
					
								if (pssl.getSellCount() == pssl.getSellTotalCount()) { // 팔 예정의 개수를 팔았다
									isRemoveFromList[order] = true;
								}
								try {
									pc.saveInventory();
									targetPc.saveInventory();
								} catch (Exception e) {
									//_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
								}
							}
						} else {
							pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
							break;
						}
					} else {
						pc.sendPackets(new S_ServerMessage(270)); // \f1 가지고 있는 것이 무거워서 거래할 수 없습니다.
						break;
					}
				}
				// 품절된 아이템을 리스트의 말미로부터 삭제
				for (int i = 7; i >= 0; i--) {
					if (isRemoveFromList[i]) {
						sellList.remove(i);
					}
				}
				targetPc.setTradingInPrivateShop(false);
			}
			
			/********************************************************************************************************		
			 *************************************** npc 상점 아이템 구매 *************************************************
			 *********************************************************************************************************/
		} else if (resultType == 0 && size != 0 && isPrivateNpcShop) {
			Shop shop = NpcShopTable.getInstance().get(npcId);
			L1ShopBuyOrderList orderList = shop.newBuyOrderList();
			int itemNumber;
			long itemcount;
			
			L1Object obj = L1World.getInstance().findObject(npcObjectId);
			if(obj == null || !(obj instanceof L1Character))
				return;


			for (int i = 0; i < size; i++) {
				itemNumber = readD();
				itemcount = readD();
				if (itemcount <= 0) {
					return;
				}
				if (size >= 2) { // 동시에 다른물건을 살수없게 2개가 선택된다면,
					pc.sendPackets(new S_SystemMessage("한번에 서로 다른아이템을 구입할 수 없습니다."));
					return;
				}
				if (pc.getMapId() == 350) {
					if (itemcount > 15) {
						pc.sendPackets(new S_SystemMessage("최대 구매수량 : 잡템류(15) / 장비(1)"));
						return;
					}
				}
				orderList.add(itemNumber, (int) itemcount, pc);
			}
			int bugok = orderList.BugOk();
			if (bugok == 0) {
				shop.sellItems(pc, orderList);
				// 백섭복사 방지 수량성버그방지
				pc.saveInventory();
				// 백섭복사 방지 수량성버그방지
			}
			/********************************************************************************************************		
			 *************************************** Npc 개인 상점 판매 *****************************************************
			 *********************************************************************************************************/			

		} else if (resultType == 1 && size != 0 && isPrivateNpcShop) { // 개인 상점에 아이템 매각
			
			Shop shop = NpcShopTable.getInstance().get(npcId);
			L1ShopSellOrderList orderList = shop.newSellOrderList(pc);
			int itemNumber;
			long itemcount;
			

			L1PcInstance targetPc = null;
			if (findObject instanceof L1PcInstance) {
				targetPc = (L1PcInstance) findObject;
			}
			if (targetPc == null) {
				return;
			}

			for (int i = 0; i < size; i++) {
				itemNumber = readD();
				itemcount = readD();
				if (itemcount <= 0) {
					return;
				}
				orderList.add(itemNumber, (int) itemcount, pc);
			}
			int bugok = orderList.BugOk();
			if (bugok == 0) {
				shop.buyItems(orderList);
				// 백섭복사 방지 수량성버그방지
				pc.saveInventory();
				// 백섭복사 방지 수량성버그방지
			} 
			/********************************************************************************************************		
			 *************************************** 개인 상점 판매 *****************************************************
			 *********************************************************************************************************/	
		} else if (resultType == 1 && size != 0 && isPrivateShop) { // 개인 상점에 아이템 매각
			int count;
			int order;
			ArrayList<L1PrivateShopBuyList> buyList;
			L1PrivateShopBuyList psbl;
			int itemObjectId;
			ItemInstance item = null;
			int buyPrice;
			int buyTotalCount;
			int buyCount;

			boolean[] isRemoveFromList = new boolean[8];

			L1PcInstance targetPc = null;
			if (findObject instanceof L1PcInstance) {
				targetPc = (L1PcInstance) findObject;
			}
			if (targetPc == null) {
				return;
			}
			if (targetPc.isTradingInPrivateShop())
				return;
			
			targetPc.setTradingInPrivateShop(true);
			
			buyList = targetPc.getBuyList();

			synchronized (buyList) {
				for (int i = 0; i < size; i++) {
					itemObjectId = readD();
					count = readCH();
					order = readC();
					item = pc.getInventory().getItem(itemObjectId);
					if (item == null) {
						continue;
					}

					if(item.getBless() >= 128){
						pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
						return;
					}

					psbl = (L1PrivateShopBuyList) buyList.get(order);
					buyPrice = psbl.getBuyPrice();
					buyTotalCount = psbl.getBuyTotalCount(); // 살 예정의 개수
					buyCount = psbl.getBuyCount(); // 산 누계
					if (count > buyTotalCount - buyCount) {
						count = buyTotalCount - buyCount;
					}
					int buyItemObjectId = psbl.getItemObjectId();
					ItemInstance buyItem = targetPc.getInventory().getItem(buyItemObjectId);

					if (buyItem == null) {
						return;
					}
					if (item.isEquipped()) {
						pc.sendPackets(new S_ServerMessage(905)); // 장비 하고 있는 아이템은 판매할 수 없습니다.
						continue;
					}

					if (targetPc.getInventory().checkAddItem(item, count) == L1Inventory.OK) { // 용량 중량 확인 및 메세지 송신
						for (int j = 0; j < count; j++) { // 오버플로우를 체크
							if (buyPrice * j > 2000000000 || buyPrice * j < 0) {
								targetPc.sendPackets(new S_ServerMessage(904, // 총판 매가격은%d아데나를 초과할 수 없습니다.
										"2000000000"));
								return;
							}
						}	  
						/** 버그 방지 **/
						if (itemObjectId != item.getId()) {
							pc.sendPackets(new S_Disconnect());
							targetPc.sendPackets(new S_Disconnect());
							return;
						}

						if (count >= item.getCount()) {
							count = item.getCount();
						}

						if (item.getItemId() != buyItem.getItemId()) return;
						if (!item.isStackable() && count !=1) return;					
						if (item.getCount() <= 0 || count <= 0) return;
						if (buyPrice * count <= 0 || buyPrice * count > 2000000000) return;
						//** 개인상점 부분 비셔스 방어 **//	

						if (targetPc.getInventory().checkItem(ItemId.아데나, count * buyPrice)) {
							ItemInstance adena = targetPc.getInventory().findItemId(ItemId.아데나);
							if (adena != null) {
								targetPc.getInventory().tradeItem(adena, count * buyPrice, pc.getInventory());
								pc.getInventory().tradeItem(item, count, targetPc.getInventory());
								psbl.setBuyCount(count + buyCount);
								buyList.set(order, psbl);
								if (psbl.getBuyCount() == psbl.getBuyTotalCount()) {
									// 살 예정의 개수를 샀다
									isRemoveFromList[order] = true;
								}
								try {
									pc.saveInventory();
									targetPc.saveInventory();
								} catch (Exception e) {
									//								_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
								}
							}
						} else {
							targetPc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
							break;
						}
					} else {
						pc.sendPackets(new S_ServerMessage(271)); // \f1상대가 물건을 너무 가지고 있어 거래할 수 없습니다.
						break;
					}
				}
				// 매점한 아이템을 리스트의 말미로부터 삭제
				for (int i = 7; i >= 0; i--) {
					if (isRemoveFromList[i]) {
						buyList.remove(i);
					}
				}
				targetPc.setTradingInPrivateShop(false);
		}
		}
	}
	
	//창고
	private boolean isAvailableTrade(L1PcInstance pc, int objectId, ItemInstance item, int count) {
		boolean result = true;

		if(item == null)											result = false; 
		if(objectId != item.getId())								result = false;
		if(!item.isStackable() && count != 1)						result = false;
		if(item.getCount() <= 0 || item.getCount() > 2000000000)	result = false;
		if(count <= 0 || count > 2000000000)						result = false;
		if (item.getItemId() == 40312) return false;
		if(!result) {
			pc.sendPackets(new S_Disconnect());
			return false;
		}

		return result;
	}


	@Override
	public String getType() {
		return C_RESULT;
	}
	
	private void writeLogbuyPrivateShop(L1PcInstance pc, L1PcInstance targetPc, ItemInstance item, int count, int price) {
	  
		SJManager.getInstance().ShopAppend(item.getLogName(), count, price, targetPc.getName(), pc.getName());
		 
	}

}