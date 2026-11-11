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
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.BonusItem.BonusItemInfo;
import l1j.server.BonusItem.BonusItemLoader;
import l1j.server.DTSSystem.MJPoint;
import l1j.server.Template.LORnd;
import l1j.server.Templates.L1Drop;
import l1j.server.Templates.L1Item;
import l1j.server.configure.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class DropTable {

	private static Logger _log = Logger.getLogger(DropTable.class.getName());

	private static DropTable _instance;

	private final HashMap<Integer, ArrayList<L1Drop>> _droplists; // monster 마다의 드롭 리스트

	public static DropTable getInstance() {
		if (_instance == null) {
			_instance = new DropTable();
		}
		return _instance;
	}

	private DropTable() {
		_droplists = allDropList();
	}
	
	public static void reload() {
		DropTable oldInstance = _instance;
		_instance = new DropTable();
		oldInstance._droplists.clear();
	}

	
	public ArrayList<L1Drop> getDropList(int monid) {
		return _droplists.get(monid);
	}

	public boolean isDropListItem(int monid, int itemid) {
		ArrayList<L1Drop> drop = getDropList(monid);
		for (L1Drop d : drop) {
			if (d.getItemid() == itemid) {
				return true;
			}
		}
		return false;
	}
	public L1Drop getDrop(int monid, int itemid) {
		ArrayList<L1Drop> drop = getDropList(monid);
		for (L1Drop d : drop) {
			if (d.getItemid() == itemid) {
				return d;
			}
		}
		return null;
	}	
	private HashMap<Integer, ArrayList<L1Drop>> allDropList() {
		HashMap<Integer, ArrayList<L1Drop>> droplistMap = new HashMap<Integer, ArrayList<L1Drop>>();

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
	
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from droplist");
			rs = pstm.executeQuery();
			L1Drop drop = null;
			while (rs.next()) { 
				int mobId = rs.getInt("mobId");
				int itemId = rs.getInt("itemId");  
				int min = rs.getInt("min");
				int max = rs.getInt("max");
				int chance = rs.getInt("chance");
				int enchant = rs.getInt("Enchant");
				int range = rs.getInt("range");
				boolean isErr = false;
				if (NpcTable.getInstance().getTemplate(mobId) == null) {
					System.out.println("[오류] droplist : 몬스터가 NPC 테이블에 존재 하지않 습니다..(" + mobId + ")");
					isErr = true;
				}
				if (ItemTable.getInstance().getTemplate(itemId) == null) {
					System.out.println("[오류] droplist : 드랍템이 아이템 리스트에 존재 하지 않습니다..(" + itemId + ")");
					isErr = true;
				}
				if (isErr) {
					continue;
				}

				drop = new L1Drop(mobId, itemId, min, max, chance, enchant, range);

				ArrayList<L1Drop> dropList = droplistMap.get(drop.getMobid());
				if (dropList == null) {
					dropList = new ArrayList<L1Drop>();
					droplistMap.put(Integer.valueOf(drop.getMobid()), dropList);
				}
				dropList.add(drop);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return droplistMap;
	}

	// 인벤트리에 드롭을 설정
	public void setDrop(L1NpcInstance npc, L1Inventory inventory) {
		// 드롭 리스트의 취득
		int mobId = npc.getNpcTemplate().get_npcId();
		ArrayList<L1Drop> dropList = _droplists.get(mobId);
		if (dropList == null) {
			return;
		}

		// 레이트 취득
		double droprate = Config.RATE_DROP_ITEMS;
		if (droprate <= 0) {
			droprate = 0;
		}
		double adenarate = Config.RATE_DROP_ADENA;
		if (adenarate <= 0) {
			adenarate = 0;
		}
		if (droprate <= 0 && adenarate <= 0) {
			return;
		}

		int itemId;
		int itemCount;
		int addCount;
		int randomChance;
		int enchantlvl;
		ItemInstance item;
		Random random = new Random(System.nanoTime()); 
		for (L1Drop drop : dropList) {
			// 드롭 아이템의 취득
			itemId = drop.getItemid();
			if (adenarate == 0 && itemId == ItemId.아데나) {
				continue; // 아데나레이트 0으로 드롭이 아데나의 경우는 스르
			}

			// 드롭 찬스 판정
			randomChance = random.nextInt(0xf4240) + 1;
			double rateOfMapId = MapsTable.getInstance().getDropRate(npc.getMapId());
			double rateOfItem = DropItemTable.getInstance().getDropRate(itemId);
			if (droprate == 0 || drop.getChance() * droprate * rateOfMapId * rateOfItem < randomChance) {
				continue;
			}
			
			// 드롭 개수를 설정
			double amount = DropItemTable.getInstance().getDropAmount(itemId);
			int min = (int) (drop.getMin() * amount);
			int max = (int) (drop.getMax() * amount);

			itemCount = min;
			addCount = max - min + 1;
			if (addCount > 1) {
				itemCount += random.nextInt(addCount);
			}
			if (itemId == ItemId.아데나) { // 드롭이 아데나의 경우는 아데나레이트를 건다
				itemCount *= adenarate;
			} else {
				if (itemCount > max) {
					itemCount = min;
					System.out.println("[오류] droplist : 몬스터번호 : " + npc.getNpcId() + " 맥스보다 많이 드랍할려고해서 민값으로 처리");
				}
			}

			if (itemCount < 0) {
				itemCount = 0;
			}
			if (itemCount > 2000000000) {
				itemCount = 2000000000;
			}

			L1Item Litem = ItemTable.getInstance().getTemplate(itemId);
			// 아이템의 생성
			if (Litem != null) { 
				if (Litem.isStackable()) {
					// 아이템의 생성
					
					item = ItemTable.getInstance().createItem(itemId);
					enchantlvl = drop.getEnchant();    
					if (item == null) continue;
					// 혹시 모를 중복 템 
					if (inventory.getItems().contains(item)) {
						continue;
					}
					/** 최초 소지하고있던 아데나 체크 */ 
					item.setRange(drop.getRange());
					item.setCount(itemCount);
					if (drop.getEnchant() != 0) {
						System.out.println("[오류] droplist : 겹쳐지는 아이템에 인첸됨.("+ item.getItemId() + ")");
						item.setEnchantLevel(enchantlvl);
					}
					// 아이템 격납
					inventory.storeItem(item);
				} else {
					for (int i = 0; i < itemCount; ++i) {
						// 아이템의 생성
						item = ItemTable.getInstance().createItem(itemId);
						item.setCount(1);
						enchantlvl = drop.getEnchant();    
						if (drop.getEnchant() != 0) {
							item.setEnchantLevel(enchantlvl);
						}
						// 아이템 격납
						inventory.storeItem(item);
					}
				}
			}
		}
	}

	// 드롭을 분배
	public void dropShare(L1NpcInstance npc, ArrayList<?> acquisitorList, ArrayList<?> hateList, L1PcInstance pc) {
		if (Config.STANDBY_SERVER) {
			return;
		} 
		L1Inventory inventory = npc.getInventory();
		if (inventory.getItems() == null || inventory.getItems().size() <= 0) {   
			return;
		} 
		
		if (pc.getInventory().getItems().size() >= 180) {
			pc.sendPackets(new S_ServerMessage(263));
			pc.Full_Inventory = true;
		} else {
			pc.Full_Inventory = false;
		}
		
		
		if (acquisitorList.size() != hateList.size()) { 
			return;
		} 
		int mobId = npc.getNpcTemplate().get_npcId();
		
		// 헤이트의 합계를 취득
		int totalHate = 0;
		L1Character acquisitor;
		for (int i = hateList.size() - 1; i >= 0; i--) {
			acquisitor = (L1Character) acquisitorList.get(i);
			// 오토 루팅 2의 경우는 사몬 및 애완동물은 생략한다 
			if ((Config.AUTO_LOOT == 0) && (acquisitor instanceof SummonInstance || acquisitor instanceof PetInstance)) {
				acquisitorList.remove(i);
				hateList.remove(i); 
			} else if (acquisitor != null && acquisitor.getMapId() == npc.getMapId() && acquisitor.getLocation().getTileLineDistance(npc.getLocation()) <= Config.LOOTING_RANGE) {
				totalHate += (Integer) hateList.get(i); 
			} else {
				// null였거나 죽기도 하고 멀었으면 배제
				acquisitorList.remove(i);
				hateList.remove(i); 
			}
		}

		// 드롭의 분배
		ItemInstance item;
		L1Inventory targetInventory = null;  
		L1PcInstance player; 
		Random random = new Random();
		int randomInt;
		int chanceHate;
		for (int i = inventory.getSize(); i > 0; i--) { 
			item = null;
			try {
			item = inventory.getItems().get(0); 
			if (item == null) continue;  

			} catch (Exception e) {
				System.out.println("드랍리스트 오류 표시 아이디 :" + npc.getNpcId() + " [이름] :" + npc.getName() + " [템번호]  " + item.getItemId());
			}
			int itemId = item.getItem().getItemId(); 
			if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light계 아이템
				item.setNowLighting(false);
			}
			
			double DollAdena = MagicDoll.getAdenaBonus(pc);
			if (item != null && item.getItemId() == ItemId.아데나) {
				if (DollAdena > 0.0D) {
					int 아데나 = (int) (item.getCount() * DollAdena);
					item.setCount(아데나);
				}
			}
			  
			if ((Config.AUTO_LOOT != 0 || AutoLoot.getInstance().isAutoLoot(itemId)) && !pc.Full_Inventory && totalHate > 0) { 
				randomInt = random.nextInt(totalHate);
				chanceHate = 0;
				for (int j = hateList.size() - 1; j >= 0; j--) {
					chanceHate += (Integer) hateList.get(j);
					if (chanceHate > randomInt) {
						acquisitor = (L1Character) acquisitorList.get(j);

						if (acquisitor.getInventory().checkAddItem(item, item.getCount()) == L1Inventory.OK) {
							targetInventory = acquisitor.getInventory();
							if (acquisitor instanceof L1PcInstance) {
								player = (L1PcInstance) acquisitor;
								
								ItemInstance l1iteminstance = player .getInventory().findItemId(ItemId.아데나); // 소지 아데나를 체크
								if (l1iteminstance != null && l1iteminstance.getCount() > 2000000000) {
									MJPoint pt = MJPoint.newInstance(acquisitor.getX(), acquisitor.getY(), item.getRange(), (short)  acquisitor.getMapId(), 50);
									targetInventory = L1World.getInstance().getInventory(pt.x, pt.y, pt.mapId);
									player.sendPackets(new S_SystemMessage("아데나가 20억 초과합니다. 수표로 변환해주세요."));
								} else {
									if (player.isInParty()) { // 파티의 경우
										if (player.getLocation().getTileLineDistance(pc.getLocation()) < Config.LOOTING_RANGE) {
											// 자동분배 타입인가?
											if (player.getParty().getLeader().getPartyType() == 1) { 
												List<L1PcInstance> _membersList = new ArrayList<L1PcInstance>();
												_membersList.add(player);
												for (L1PcInstance realUser : L1World.getInstance().getVisiblePlayer(player, Config.LOOTING_RANGE)) {
													if (player.getParty().isMember(realUser) && player.getId() != realUser.getId()) {
														_membersList.add(realUser);
													}
												}
												// 랜덤으로 누구 한테 갈껀지 ㅋ
												int luckuyNum = random.nextInt(_membersList.size());
												L1PcInstance luckyUser = _membersList.get(luckuyNum);
												// 아데나 인가?
												if (item.getItemId() == ItemId.아데나) {
													int dropitem = item.getCount(); 
													int divAden = dropitem / _membersList.size();
													if (_membersList.size() > 1) {
														int modNum = dropitem % _membersList.size();
														if (modNum == 0) {
															for (int row = 0; row < _membersList.size(); row++) {
																inventory.tradeItem(item, divAden, _membersList.get(row).getInventory());
																if (_membersList.get(row).RootMent) { 
																	_membersList.get(row).sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(),_membersList.get(row).getName()));
																}
																if (_membersList.get(row).getId() != player.getId()){  
																	player.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(),_membersList.get(row).getName()));
																}
																if (player.getId() != _membersList.get(row).getId()){ 
																	_membersList.get(row).sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(),player.getName()));
																}
															}
														} else {
															if (dropitem < _membersList.size()) {
																// 그럴일은 없겠지만.. 아데나가 파티원의 수보다 적게 나왔을때 랜덤으로 지급
																inventory.tradeItem(item, dropitem, luckyUser.getInventory());
															} else {
																for (int row = 0; row < _membersList.size(); row++) {
																	if (player.getId() == _membersList.get(row).getId()) {
																		inventory.tradeItem(item, divAden + modNum, player.getInventory());
																	} else {
																		inventory.tradeItem(item, divAden, _membersList.get(row).getInventory());
																	}
																}
															}
														}
													} else { // 파티원이 멀리있고 혼자있을때
														inventory.tradeItem(item, dropitem, player.getInventory());
														if(player.RootMent){
															player.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(),player.getName()));
														}
													}
												} else {// 아니면 다른 아이템인가?
													inventory.tradeItem(item, item.getCount(), luckyUser.getInventory());
													for (L1PcInstance partymember : player.getParty().getMembers()) {
														if (partymember.RootMent) {
															partymember.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(), luckyUser.getName()));
														}
													}
												}
											} else { // 아니면 그냥인가?
												inventory.tradeItem(item, item.getCount(), player.getInventory());
												for (L1PcInstance partymember : player.getParty().getMembers()) {
													if (partymember.RootMent) {
														partymember.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(),player.getName()));
													}
												}
											}
										} else {
											if (player.RootMent) { // 솔로의 경우
												player.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(), player.getName()));
											}	
										}
												// 아이템저장시킴
									} else if (player.RootMent) { // 솔로의 경우 
										player.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(), player.getName()));
									}
								}
							}
						} else {
							player = (L1PcInstance) acquisitor;
							if (player.isInParty()) { // 파티의 경우
								for (L1PcInstance partymember : player.getParty().getMembers()) {
									targetInventory = partymember.getInventory();
									if (partymember.RootMent) {
										partymember.sendPackets(new S_SystemMessage(npc.getName()+ " -> "+ item.getLogName()+ " [ "+ partymember.getName()+ " ]"+ " 획득"));
									}
								}
							} else if (player.RootMent) { // 솔로의 경우
								if(targetInventory == player.getInventory()){
									player.sendPackets(new S_ServerMessage(813, npc.getName(), item.getLogName(), player.getName()));
								}
							} 
							
							//아이템착용시 특화드랍
							try {
								// 혼자
								if (pc.getParty() == null) {
									BonusItemInfo bonusInfo = calcBonusItem(pc, npc, item); 
									if (bonusInfo != null) {
										tradeBonusItem(pc, npc, item, bonusInfo, targetInventory, false); 
									}
								
								// 파티중
								} else {
									BonusItemInfo minBonusInfo = null;
									boolean isAllOwner = true;
									L1PcInstance[] partyPlayer = pc.getParty().getMembers();
									for (int k = 0; k < partyPlayer.length; k++) {
										BonusItemInfo bonusInfo = calcBonusItem(partyPlayer[k], npc, item);
										if (bonusInfo == null) {
											isAllOwner = false;
											break;
										} else {
											if (minBonusInfo == null) minBonusInfo = bonusInfo;
											else {
												if (bonusInfo.get_item_enchant() < minBonusInfo.get_item_enchant()) {
													minBonusInfo = bonusInfo;
												}
											}
										}
									}
									
									if (isAllOwner) {
										tradeBonusItem(pc, npc, item, minBonusInfo, targetInventory, true);
									}  
								}
							} catch (Exception e) {
								e.printStackTrace();
							} 
							
						}
						break;
					}
				}			
			
			} else { // Non 오토 루팅 

				/** 악령의씨앗 무조건오토루팅 **/
				if (itemId == 810008) {
					return; 
				}
				item.setDropMobId(mobId);

				int maxHatePc = -1;
				int maxHate = -1;

				for (int j = hateList.size() - 1; j >= 0; j--) {
					if (maxHate < (Integer) hateList.get(j)) {
						maxHatePc = j;
						maxHate = (Integer) hateList.get(j);
					}
				}
 
				if (maxHatePc != -1 && acquisitorList.get(maxHatePc) instanceof L1PcInstance) {
					item.startItemOwnerTimer((L1PcInstance) acquisitorList.get(maxHatePc));
				} else {
					item.startItemOwnerTimer(pc);
				}
				List<Integer> dirList = new ArrayList<Integer>();
				for (int j = 0; j < 8; j++) {
					dirList.add(j);
				}
				int x = 0;
				int y = 0;
				int dir = 0;
				do {
					if (dirList.size() == 0) {
						x = 0;
						y = 0;
						break;
					}
					randomInt = random.nextInt(dirList.size());
					dir = dirList.get(randomInt);
					dirList.remove(randomInt);
					switch (dir) {
					case 0:
						x = 0;
						y = -1;
						break;
					case 1:
						x = 1;
						y = -1;
						break;
					case 2:
						x = 1;
						y = 0;
						break;
					case 3:
						x = 1;
						y = 1;
						break;
					case 4:
						x = 0;
						y = 1;
						break;
					case 5:
						x = -1;
						y = 1;
						break;
					case 6:
						x = -1;
						y = 0;
						break;
					case 7:
						x = -1;
						y = -1;
						break;
					}
				} while (!MJPoint.isValidPosition(npc.getMap(),  npc.getX() + x, npc.getY() + y));
				MJPoint pt = MJPoint.newInstance(npc.getX(), npc.getY(), item.getRange(), (short)  npc.getMapId(), 50);
				targetInventory = L1World.getInstance().getInventory(pt.x, pt.y, pt.mapId);
			}
			
			inventory.tradeItem(item, item.getCount(), targetInventory);
		}
		npc.getLight().turnOnOffLight();
	}
	
	
	private BonusItemInfo calcBonusItem(L1PcInstance pc, L1NpcInstance npc, ItemInstance item) {
		BonusItemInfo result = null;
		List<ItemInstance> items = pc.getInventory().getItems();
		DollInstance doll = pc.getMagicDoll();
		for (ItemInstance bitem : items) {
			
			int enchant = bitem.getEnchantLevel();
			BonusItemInfo bInfo = BonusItemLoader.getInstance().getBonusItemInfo(bitem.getItemId(), enchant);
			if (bInfo != null) {
				if (bInfo.isEquipType() && bitem.isEquipped() || doll != null && doll.getItemObjId() == bitem.getId()) {
					if (bInfo != null) {
						if (bInfo.get_mapids() == null) {
							continue;
						}
						if (bInfo.isMap(pc)) {
							result = bInfo;
							break;
						}
					}
				}
				if (!bInfo.isEquipType() && !bitem.isEquipped() ) {
					if (bInfo != null) {
						if (bInfo.get_mapids() == null) {
							continue;
						}
						if (bInfo.isMap(pc)) {
							result = bInfo;
							break;
						}
					}
				}
			}
		}

		return result;
	}

	private void tradeBonusItem(L1PcInstance pc, L1NpcInstance npc, ItemInstance item, BonusItemInfo bonusInfo, L1Inventory inventory, boolean partycheck) {
		try {
			if (!npc.isResurrect() && pc.getInventory().checkItem(item.getItemId())) {
				if (LORnd.isWinning(1000000, bonusInfo.calc_probability(pc))) {
					ItemInstance bonusitem = ItemTable.getInstance().createItem(bonusInfo.get_bonus_id());
					if (bonusitem != null) {
						bonusitem.setCount(bonusInfo.calc_count(pc));
						
						ItemInstance inventoryItem = pc.getInventory().findItemId(40308);
						int inventoryItemCount = 0;
						if (inventoryItem != null) {
							inventoryItemCount = inventoryItem.getCount();
						}
						if (inventoryItemCount > 1800000000) {
							if (pc.getInventory().consumeItem(40308, 1500000000)) {
								pc.getInventory().storeItem(400254, 15);
							} 
						}
						
						
						inventory.storeTradeItem(bonusitem);
						if (pc.RootMent) { // 솔로의 경우
							pc.sendPackets(
									new S_ServerMessage(143, npc.getNpcTemplate().get_name(), bonusitem.getLogName())); // \f1%0이%1를
																													// 주었습니다.
						}
					}
				}
			}
		} catch (Exception e) {
//			System.out.println("보너스 아이템오류 : " + bonusInfo.get_bonus_id() + " <<< 에러아이템번호 ");
		}
	}

}