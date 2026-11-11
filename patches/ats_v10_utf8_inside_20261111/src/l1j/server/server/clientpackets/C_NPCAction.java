
package l1j.server.server.clientpackets;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.ActionListener.ActionListener;
import l1j.server.ActionListener.ActionListenerLoader;
import l1j.server.DogFightSystem.DogFightSystemSettings;
import l1j.server.DogFightSystem.S_DogFightBoard;
import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.Template.Builder.ServerPacketBuilder;
import l1j.server.Templates.L1Castle;
import l1j.server.Templates.L1House;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Skills;
import l1j.server.Templates.L1Town;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.CraftListTable;
import l1j.server.server.datatables.CraftListTable.CraftMeterialTemp;
import l1j.server.server.datatables.CraftListTable.CraftTemp;
import l1j.server.server.datatables.CastleTable;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1UltimateBattle;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DoorInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.npc.action.L1NpcHtml;
import l1j.server.server.model.shop.L1AssessedItem;
import l1j.server.server.model.shop.Shop;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_ApplyAuction;
import l1j.server.server.serverpackets.S_AuctionBoardRead;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_CloseList;
import l1j.server.server.serverpackets.S_CraftItem;
import l1j.server.server.serverpackets.S_DelSkill;
import l1j.server.server.serverpackets.S_Deposit;
import l1j.server.server.serverpackets.S_Drawal;
import l1j.server.server.serverpackets.S_HouseMap;
import l1j.server.server.serverpackets.S_ItemStatus;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_NoSell;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_PetList;
import l1j.server.server.serverpackets.S_RaceBoard;
import l1j.server.server.serverpackets.S_RetrieveList;
import l1j.server.server.serverpackets.S_RetrievePledgeList;
import l1j.server.server.serverpackets.S_RetrievePresentList;
import l1j.server.server.serverpackets.S_ReturnedStat;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_SelectTarget;
import l1j.server.server.serverpackets.S_SellHouse;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_ShopBuyList;
import l1j.server.server.serverpackets.S_ShopSellList;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_TaxRate;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.MessageUtil;

public class C_NPCAction extends ClientBasePacket {

	private static final String C_NPC_ACTION = "[C] C_NPCAction";
	private static Logger _log = Logger.getLogger(C_NPCAction.class.getName());
	private static Random _random = new Random();

	public C_NPCAction(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);
		int objid = readD();
		String s = readS();
		if (s.equalsIgnoreCase("deadTrans") || s.equalsIgnoreCase("pvpSet") || s.equalsIgnoreCase("ShowHPMPRecovery")
				|| s.equalsIgnoreCase("showDisableEffectIcon") || s.equalsIgnoreCase("showDungeonTimeLimit")) {
			return;
		}
		String s2 = null;
		if (s.equalsIgnoreCase("select") || s.equalsIgnoreCase("map") || s.equalsIgnoreCase("apply")) {
			s2 = readS();
		} else if (s.equalsIgnoreCase("ent")) {
			L1Object obj = L1World.getInstance().findObject(objid);
			if (obj != null && obj instanceof L1NpcInstance) {
				final int PET_MATCH_MANAGER = 80088;
				if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == PET_MATCH_MANAGER) {
					s2 = readS();
				}
			}
		}

		int[] materials = null;
		int[] counts = null;
		int[] createitem = null;
		int[] createcount = null;
		String htmlid = null;
		String success_htmlid = null;
		String failure_htmlid = null;
		String[] htmldata = null;

		L1PcInstance pc = client.getActiveChar();

		if (pc == null) {
			return;
		}
		// 자동 물약
		if (auto_potion(pc, s)) {
			return;
		}

		L1PcInstance target;
		L1Object obj = L1World.getInstance().findObject(objid);

		if (obj != null) {
			if (obj instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				int difflocx = Math.abs(pc.getX() - npc.getX());
				int difflocy = Math.abs(pc.getY() - npc.getY());
				if (!(obj instanceof PetInstance) && !(obj instanceof SummonInstance)) {
					if (difflocx > 6 || difflocy > 6) {
						return;
					}
				}
				npc.onFinalAction(pc, s);
			} else if (obj instanceof L1PcInstance) {
				target = (L1PcInstance) obj;
				if (target.getInventory().checkItem(52063)&& useJoeTeleportBook1(target, 52063, s)) { //조우의 이동 기억책
				}
				if (s.matches("[0-9]+")) {
					summonMonster(target, s);
				} else {
					L1PolyMorph.handleCommands(target, s);
				}
				return;
			}
		} else {
		//	 _log.warning("object not found, oid ");
		}

		if (pc.isGm()) {
			pc.sendPackets(new S_SystemMessage(String.format("\\fY액션: %d \\fUs: %s \\fR s2: %s", ((L1NpcInstance) obj).getNpcTemplate().get_npcId(), s, s2)));
			// System.out.println(String.format("npcid: %d, s: %s, s2: %s", ((L1NpcInstance)obj).getNpcTemplate().get_npcId(), s, s2));
		}
		// 제작 자리 
		if(s.startsWith("제작시작")) {
			if(pc.Craft_Npcid == 0 || pc.Craft_Orderid == -1) {
				pc.sendPackets(new S_SystemMessage("오류입니다. 다시 시도해주세요."));
				return;
			}		
			CraftTemp temp = CraftListTable.getInstance().getCraftTemp(pc.Craft_Npcid, pc.Craft_Orderid);
			if(temp == null) {
				pc.sendPackets(new S_SystemMessage("제작 리스트 오류입니다. 운영자에게 문의해주세요."));
				return;
			}
			// 재료 확인
			boolean isMeterial = true;
			for(CraftMeterialTemp mtemp : temp._MeterialList) {
				L1Item tempitem = ItemTable.getInstance().getTemplate(mtemp.itemid);
				ItemInstance item = ItemTable.getInstance().createItem(tempitem);
				if(item == null) {
					pc.sendPackets(new S_SystemMessage("재료번호:" + mtemp.itemid + "은 존재하지 않는 아이템입니다."));
					return;
				}

				String itemName = "";
				if(mtemp.bless == 0) {
					itemName += "축복받은 ";
				}
				if(mtemp.enchant != 0) {
					itemName += "+" + mtemp.enchant + " ";
				}
				itemName += item.getName();

				//겹치는 아이템
				if(item.isStackable()) {
					ItemInstance meter_item = pc.getInventory().findItemIdBless(mtemp.itemid, mtemp.bless);
					if(meter_item == null) {
						pc.sendPackets(new S_SystemMessage("[" + itemName + "] " + mtemp.count + "개 부족합니다."));
						isMeterial = false;
					}else {
						if(mtemp.count > meter_item.getCount()) {
							int consumeCount = mtemp.count - meter_item.getCount();
							pc.sendPackets(new S_SystemMessage("[" + itemName + "] " + consumeCount + "개 부족합니다."));
							isMeterial = false;
						}
					}
				} else {
					ItemInstance[] items = pc.getInventory().findItemsIdNotEquippedCraftBless(mtemp.itemid, mtemp.enchant, mtemp.bless);
					if(items.length == 0) {
						pc.sendPackets(new S_SystemMessage("[" + itemName + "] " + mtemp.count + "개 부족합니다."));
						isMeterial = false;
					}else if(mtemp.count > items.length) {
						int consumeCount = mtemp.count - items.length;
						pc.sendPackets(new S_SystemMessage("[" + itemName + "] " + consumeCount + "개 부족합니다."));
						isMeterial = false;
					}
				}
			}
			if(!isMeterial) {
				return;
			}
			// 재료 소모 
			for(CraftMeterialTemp mtemp : temp._MeterialList) {
				L1Item item = ItemTable.getInstance().getTemplate(mtemp.itemid);
				if(item == null) {
					pc.sendPackets(new S_SystemMessage("재료번호:" + mtemp.itemid + "은 존재하지 않는 아이템입니다."));
					return;
				}

				if(item.isStackable()) {
					ItemInstance meter_item = pc.getInventory().findItemIdBless(mtemp.itemid, mtemp.bless);
					pc.getInventory().removeItem(meter_item, mtemp.count);
				}else {
					ItemInstance[] items = pc.getInventory().findItemsIdNotEquippedCraftBless(mtemp.itemid, mtemp.enchant, mtemp.bless);
					for(int i = 0; i < mtemp.count; i++) {
						pc.getInventory().removeItem(items[i], 1);
					}
				}
			}

			int rnd = _random.nextInt(100) + 1;
			if(temp.create_chance < rnd) {
				//실패시
				pc.sendPackets(new S_SystemMessage("제작에 실패하였습니다."));

				//실패시 돌려줄 아이템이 있다면
				if(temp.reitem_id != 0 && temp.reitem_count != 0) {
					ItemInstance reitem= pc.getInventory().storeItem(temp.reitem_id, temp.reitem_count);
					pc.sendPackets(new S_SystemMessage("제작: " + reitem.getName() + "(" + temp.reitem_count + ") 를 돌려받았습니다."));
				}

				return;
			}
			// 아이템 생성
			L1Item tempitem = ItemTable.getInstance().getTemplate(temp.create_id);
			if(tempitem.isStackable()) {
				ItemInstance craftitem = pc.getInventory().storeItem(temp.create_id, temp.create_count);
				craftitem.setIdentified(true);
				craftitem.setBless(temp.create_bless);
				pc.getInventory().updateItem(craftitem, L1PcInventory.COL_BLESS);
				pc.getInventory().saveItem(craftitem, L1PcInventory.COL_BLESS);
				pc.sendPackets(new S_ItemStatus(pc, craftitem));

				String itemName = "";
				if(temp.create_bless == 0) {
					itemName += "축복받은 ";
				}
				if(temp.create_enchant != 0) {
					itemName += "+" + temp.create_enchant + " ";
				}
				itemName += craftitem.getName();

				pc.sendPackets(new S_SystemMessage(itemName + "(" + temp.create_count + ") 제작하였습니다."));
			} else {
				if(temp.create_count > 1) {
					ItemInstance craftitem = null;
					for(int i = 0; i < temp.create_count; i++) {
						craftitem = pc.getInventory().storeItem(temp.create_id, 1, temp.create_enchant);
						craftitem.setBless(temp.create_bless);
						pc.getInventory().updateItem(craftitem, L1PcInventory.COL_BLESS);
						pc.getInventory().saveItem(craftitem, L1PcInventory.COL_BLESS);
						craftitem.setIdentified(true);
						pc.getInventory().updateItem(craftitem, L1PcInventory.COL_IS_ID);
					}

					String itemName = "";
					if(temp.create_bless == 0) {
						itemName += "축복받은 ";
					}
					if(temp.create_enchant != 0) {
						itemName += "+" + temp.create_enchant + " ";
					}
					itemName += craftitem.getName();

					pc.sendPackets(new S_SystemMessage(itemName + "(" + temp.create_count + ") 제작하였습니다."));
				} else {
					ItemInstance craftitem = pc.getInventory().storeItem(temp.create_id, 1, temp.create_enchant);
					craftitem.setBless(temp.create_bless);
					pc.getInventory().updateItem(craftitem, L1PcInventory.COL_BLESS);
					pc.getInventory().saveItem(craftitem, L1PcInventory.COL_BLESS);
					craftitem.setIdentified(true);
					pc.getInventory().updateItem(craftitem, L1PcInventory.COL_IS_ID);

					String itemName = "";
					if(temp.create_bless == 0) {
						itemName += "축복받은 ";
					}
					if(temp.create_enchant != 0) {
						itemName += "+" + temp.create_enchant + " ";
					}
					itemName += craftitem.getName();

					pc.sendPackets(new S_SystemMessage(itemName + "(" + temp.create_count + ") 제작하였습니다."));
				}
			}
		} else if(s.startsWith("제작")) {
			if (obj != null && obj instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String orders = s.replace("제작", "");
				int npcid = ((L1NpcInstance) obj).getNpcId();
				int orderid = Integer.parseInt(orders);

				pc.Craft_Npcid = npcid;
				pc.Craft_Orderid = orderid;

				pc.sendPackets(new S_CraftItem(pc, npc.getId()));
			}
			return;
		}
		// XML화 된 액션
		L1NpcAction action = NpcActionTable.getInstance().get(s, pc, obj);
		if (action != null) {
			L1NpcHtml result = action.execute(s, pc, obj, readByte());
			if (result != null) {
				pc.sendPackets(new S_NPCTalkReturn(obj.getId(), result));
			}
			return;
		}
		/** 켄트 **/
		if (s.equalsIgnoreCase("andyn3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(
					b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("andyn3")
					.addH(0x01)
					.addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(1)))
					.build(true));
			b.close();
			b.dispose();
			/** 우드벡 **/
		} else if (s.equalsIgnoreCase("vola3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(
					b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("vola3")
					.addH(0x01).addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(3)))
					.build(true));
			b.close();
			b.dispose();
			/** 기란 **/
		} else if (s.equalsIgnoreCase("s_merchant3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(
					b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("s_merchant3")
					.addH(0x01)
					.addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(4)))
					.build(true));
			b.close();
			b.dispose();
		} else if (s.equalsIgnoreCase("vergil3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("vergil3")
					.addH(0x01).addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(4)))
					.build(true));
			b.close();
			b.dispose();
			/** 하이네 **/
		} else if (s.equalsIgnoreCase("shivan3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(
					b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("shivan3")
					.addH(0x01)
					.addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(5)))
					.build(true));
			b.close();
			b.dispose();
			/** 웰던 **/
		} else if (s.equalsIgnoreCase("ralf6")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("ralf6")
					.addH(0x01)
					.addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(6)))
					.build(true));
			b.close();
			b.dispose();
			/** 아덴 **/
		} else if (s.equalsIgnoreCase("defman3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(
					b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("defman3")
					.addH(0x01)
					.addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(6)))
					.build(true));
			b.close();
			b.dispose();
		} else if (s.equalsIgnoreCase("fega3")) {
			ServerPacketBuilder b = new ServerPacketBuilder(16);
			pc.sendPackets(
					b.addC(Opcodes.S_OPCODE_SHOWHTML)
					.addD(obj.getId())
					.addS("fega3")
					.addH(0x01)
					.addH(1)
					.addS(Integer.toString(CastleWarBusiness.getInstance().getTaxRate(6)))
					.build(true));
			b.close();
			b.dispose();
		}
		if (s.equalsIgnoreCase("buy")) {
			L1NpcInstance npc = (L1NpcInstance) obj;
			if (isNpcSellOnly(npc)) {
				return;
			}
			// 판매 리스트 표시
			pc.sendPackets(new S_ShopSellList(pc, objid));
		} else if (s.equalsIgnoreCase("sell")) {
			int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
			if (npcid == 50527 || npcid == 50505 || npcid == 50519 || npcid == 50545 || npcid == 50531
					|| npcid == 50529 || npcid == 50516 || npcid == 50538 || npcid == 50518 || npcid == 50509
					|| npcid == 50536 || npcid == 50520 || npcid == 50543 || npcid == 50526 || npcid == 50512
					|| npcid == 50510 || npcid == 50504 || npcid == 50525 || npcid == 50534 || npcid == 50540
					|| npcid == 50515 || npcid == 50513 || npcid == 50528 || npcid == 50533 || npcid == 50542
					|| npcid == 50511 || npcid == 50501 || npcid == 50503 || npcid == 50508 || npcid == 50514
					|| npcid == 50532 || npcid == 50544 || npcid == 50524 || npcid == 50535 || npcid == 50521
					|| npcid == 50517 || npcid == 50537 || npcid == 50539 || npcid == 50507 || npcid == 50530
					|| npcid == 50502 || npcid == 50506 || npcid == 50522 || npcid == 50541 || npcid == 50523
					|| npcid == 50620 || npcid == 50623 || npcid == 50619 || npcid == 50621 || npcid == 50622
					|| npcid == 50624 || npcid == 50617 || npcid == 50614 || npcid == 50618 || npcid == 50616
					|| npcid == 50615) { // 아지트의 NPC
				String sellHouseMessage = sellHouse(pc, objid, npcid);
				if (sellHouseMessage != null) {
					htmlid = sellHouseMessage;
				}	
				
			} else {
				if (npcid == DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						Shop shop = ShopTable.getInstance().get(npc.getNpcId());
						if (shop == null) {
							pc.sendPackets(new S_NoSell(npc));
							return;
						}
						List<L1AssessedItem> assessedItems = shop.assessItems(pc.getInventory());
						if (assessedItems.isEmpty()) {
							pc.sendPackets(new S_NoSell(npc));
							return;
						} else {
							pc.sendPackets(new S_ShopBuyList(objid, pc));
						}
					}
					return;
				}
				// 매입 리스트 표시
				pc.sendPackets(new S_ShopBuyList(objid, pc));
			}
			
		} else if (s.equalsIgnoreCase("retrieve")) { // 「개인 창고：아이템을 받는다」
			if (pc.isTwoLogin(pc)) {
				return;
			}
			if (pc.getLevel() >= 5) {	
				pc.sendPackets(new S_RetrieveList(objid, pc));
			}
		} else if (s.equalsIgnoreCase("retrieve-aib")) {// 패키지창고
		      if (pc.isTwoLogin(pc)) {
		        return;
		      }
		      if (pc.getLevel() >= 5) {
		        pc.sendPackets(new S_RetrievePresentList(pc.getId(), pc));
		      }
		} else if (s.equalsIgnoreCase("retrieve-pledge")) { // 「혈맹 창고：짐을 받는다」
			if (pc.isTwoLogin(pc)) {
				return;
			}
			if (pc.getLevel() >= 5) {
				if (pc.getClanid() == 0) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_37));
					return;
				}
				int rank = pc.getClanRank();
				if (rank != L1Clan.CLAN_RANK_PUBLIC && rank != L1Clan.CLAN_RANK_GUARDIAN && rank != L1Clan.CLAN_RANK_PRINCE) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_36));
					return;
				}
				if (rank != L1Clan.CLAN_RANK_PRINCE && pc.getTitle().equalsIgnoreCase("")) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_36));
					return;
				} 
				pc.sendPackets(new S_RetrievePledgeList(objid, pc));
			}
		} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70012 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70019
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70031 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70054
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70065 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70070 
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70075 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70084
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70096) {
			if (s.equalsIgnoreCase("room")) {
				if (pc.getInventory().checkItem(40312))
					htmlid = "inn5";
				else if (pc.getInventory().findItemId(40308).getCount() >= 300) {
					materials = (new int[] { 40308 });
					counts = (new int[] { 300 });
					createitem = (new int[] { 40312 });
					createcount = (new int[] { 1 });
					htmlid = "inn4";
				} else {
					htmlid = "inn3";
				}
			} else if (s.equalsIgnoreCase("room")) {
				if (pc.getInventory().checkItem(40312))
					htmlid = "inn5";
				else if (pc.getInventory().findItemId(40308).getCount() >= 600) {
					materials = (new int[] { 40308 });
					counts = (new int[] { 600 });
					createitem = (new int[] { 49016 });
					createcount = (new int[] { 1 });
					htmlid = "inn4";
				} else {
					htmlid = "inn3";
				}
				
			} else if (s.equalsIgnoreCase("room")) {
				int k1 = 0;
				try {
					k1 = pc.getLawful();
				} catch (Exception exception) {
				}
				if (k1 >= 0) {
					htmlid = "inn2";
					htmldata = (new String[] { "여관주인", "300" });
				} else {
					htmlid = "inn1";
				}
			} else if (s.equalsIgnoreCase("hall")) {
				int k1 = 0;
				int c1 = 0;
				try {
					k1 = pc.getLawful();
					c1 = pc.getClassId();
				} catch (Exception exception1) {
				}
				if (k1 >= 0) {
					if (c1 == 0 || c1 == 1) {
						htmlid = "inn4";
						htmldata = (new String[] { "여관주인", "600" });
					} else {
						htmlid = "inn10";
					}
				} else {
					htmlid = "inn11";
				}
			} else if (s.equalsIgnoreCase("return")) {
				if (pc.getInventory().checkItem(40312)) {
					int ct = pc.getInventory().findItemId(40312).getCount();
					int cash = ct * 60;
					materials = (new int[] { 40312 });
					counts = (new int[] { ct });
					createitem = (new int[] { 40308 });
					createcount = (new int[] { cash });
					htmlid = "inn20";
					String count = Integer.toString(cash);
					htmldata = (new String[] { "여관주인", count });
				} else if (pc.getInventory().checkItem(40312)) {
					int ct = pc.getInventory().findItemId(40312).getCount();
					int cash = ct * 120;
					materials = (new int[] { 49016 });
					counts = (new int[] { ct });
					createitem = (new int[] { 40308 });
					createcount = (new int[] { cash });
					htmlid = "inn20";
					String count = Integer.toString(cash);
					htmldata = (new String[] { "여관주인", count });
				} else {
					htmlid = "inn7";
				}
			} else if (s.equalsIgnoreCase("enter")) {
				int nowX = pc.getX();
				int nowY = pc.getY();
				short map = pc.getMapId();
				if (pc.getInventory().checkItem(40312)) {
					if (map == 0) {
						pc.teleport(32746, 32803, (short) 16384, pc.getHeading(), 169, false, false);
					}	
					else if (map > 0)
						if (nowX < 32641 && nowX > 32621 && nowY < 32760 && nowY > 32740)
							pc.teleport(32744, 32803, (short) 17408, pc.getHeading(), 169, false, false);
						else if (nowX < 32638 && nowX > 32618 && nowY < 33177 && nowY > 33157)
							pc.teleport(32745, 32803, (short) 18432, pc.getHeading(), 169, false, false);
						else if (nowX < 33995 && nowX > 33975 && nowY < 33322 && nowY > 33302)
							pc.teleport(32745, 32803, (short) 19456, pc.getHeading(), 169, false, false);
						else if (nowX < 33447 && nowX > 33427 && nowY < 32799 && nowY > 32779)
							pc.teleport(32745, 32803, (short) 20480, pc.getHeading(), 169, false, false);
						else if (nowX < 33615 && nowX > 33595 && nowY < 33285 && nowY > 33265)
							pc.teleport(32745, 32803, (short) 21504, pc.getHeading(), 169, false, false);
						else if (nowX < 33126 && nowX > 33106 && nowY < 33389 && nowY > 33369)
							pc.teleport(32745, 32803, (short) 22528, pc.getHeading(), 169, false, false);
						else if (nowX < 34078 && nowX > 34058 && nowY < 32264 && nowY > 32244)
							pc.teleport(32745, 32803, (short) 24576, pc.getHeading(), 169, false, false);
				} else if (pc.getInventory().checkItem(40312)) {
					if (map == 0)
						pc.teleport(32744, 32808, (short) 16896, pc.getHeading(), 169, false, false);
					else if (map > 0)
						if (nowX < 32641 && nowX > 32621 && nowY < 32760 && nowY > 32740)
							pc.teleport(32745, 32807, (short) 18944, pc.getHeading(), 169, false, false);
						else if (nowX < 32638 && nowX > 32618 && nowY < 33177 && nowY > 33157)
							pc.teleport(32745, 32807, (short) 19968, pc.getHeading(), 169, false, false);
						else if (nowX < 33995 && nowX > 33975 && nowY < 33322 && nowY > 33302)
							pc.teleport(32745, 32807, (short) 20992, pc.getHeading(), 169, false, false);
						else if (nowX < 33447 && nowX > 33427 && nowY < 32799 && nowY > 32779)
							pc.teleport(32745, 32807, (short) 22016, pc.getHeading(), 169, false, false);
						else if (nowX < 33615 && nowX > 33595 && nowY < 33285 && nowY > 33265)
							pc.teleport(32745, 32807, (short) 23040, pc.getHeading(), 169, false, false);
						else if (nowX < 33126 && nowX > 33106 && nowY < 33389 && nowY > 33369)
							pc.teleport(32745, 32807, (short) 24064, pc.getHeading(), 169, false, false);
						else if (nowX < 34078 && nowX > 34058 && nowY < 32264 && nowY > 32244)
							pc.teleport(32745, 32807, (short) 25088, pc.getHeading(), 169, false, false);
				} else {
					htmlid = "inn9";
				}
			}		
		} else if (s.equalsIgnoreCase("fix")) { // 무기를 수리한다		
		} else if (s.equalsIgnoreCase("room")) { // 방을 빌린다
		} else if (s.equalsIgnoreCase("hall") && obj instanceof L1MerchantInstance) { // 홀을 빌린다
		} else if (s.equalsIgnoreCase("return")) { // 방·홀을 돌려준다
		} else if (s.equalsIgnoreCase("enter")) { // 방·홀에 들어온다
		} else if (s.equalsIgnoreCase("openigate")) { // 게이트키퍼 / 성문을 연다
			L1NpcInstance npc = (L1NpcInstance) obj;
			openCloseGate(pc, npc.getNpcTemplate().get_npcId(), true);
			htmlid = ""; // 윈도우를 지운다		
		} else if (s.equalsIgnoreCase("closeigate")) { // 게이트키퍼 / 성문을 닫는다
			L1NpcInstance npc = (L1NpcInstance) obj;
			openCloseGate(pc, npc.getNpcTemplate().get_npcId(), false);
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("askwartime")) { // 근위병 / 다음의 공성 싸움의 시간을 묻는다
			L1NpcInstance npc = (L1NpcInstance) obj;
			if (npc.getNpcTemplate().get_npcId() == 60514) { // 켄트성근위병
				htmldata = makeWarTimeStrings(L1CastleLocation.KENT_CASTLE_ID);
				htmlid = "ktguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 60560) { // 오크 근위병
				htmldata = makeWarTimeStrings(L1CastleLocation.OT_CASTLE_ID);
				htmlid = "orcguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 60552) { // 윈다웃드성근위병
				htmldata = makeWarTimeStrings(L1CastleLocation.WW_CASTLE_ID);
				htmlid = "wdguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 60524 || // 기란거리 입구 근위병(활)
					npc.getNpcTemplate().get_npcId() == 60525 || // 기란거리 입구 근위병
					npc.getNpcTemplate().get_npcId() == 60529) { // 기란성근위병
				htmldata = makeWarTimeStrings(L1CastleLocation.GIRAN_CASTLE_ID);
				htmlid = "grguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 70857) { // Heine성Heine 가이드
				htmldata = makeWarTimeStrings(L1CastleLocation.HEINE_CASTLE_ID);
				htmlid = "heguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 60530 || // 드워후성드워후가드
					npc.getNpcTemplate().get_npcId() == 60531) {
				htmldata = makeWarTimeStrings(L1CastleLocation.DOWA_CASTLE_ID);
				htmlid = "dcguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 60533 || // 에덴성가이드
					npc.getNpcTemplate().get_npcId() == 60534) {
				htmldata = makeWarTimeStrings(L1CastleLocation.ADEN_CASTLE_ID);
				htmlid = "adguard7";
			} else if (npc.getNpcTemplate().get_npcId() == 81156) { // 에덴 정찰병(디아드 요새)
				htmldata = makeWarTimeStrings(L1CastleLocation.DIAD_CASTLE_ID);
				htmlid = "dfguard3";
			}
		} else if (s.equalsIgnoreCase("inex")) { // 수입/지출의 보고를 받는다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int castle_id = clan.getCastleId();
				if (castle_id != 0) { // 성주 크란
					L1Castle l1castle = CastleTable.getInstance().getCastleTable(castle_id);
					pc.sendPackets(new S_ServerMessage(309, // %0의 정산 총액은%1아데나입니다.
							l1castle.getName(), String.valueOf(l1castle.getPublicMoney())));
					htmlid = ""; // 윈도우를 지운다
				}
			}
		} else if (s.equalsIgnoreCase("tax")) { // 세율을 조절한다
			pc.sendPackets(new S_TaxRate(pc.getId()));
		} else if (s.equalsIgnoreCase("withdrawal")) { // 자금을 인출한다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int castle_id = clan.getCastleId();
				if (castle_id != 0) { // 성주 크란
					L1Castle l1castle = CastleTable.getInstance().getCastleTable(castle_id);
					pc.sendPackets(new S_Drawal(pc.getId(), l1castle.getPublicMoney()));
				}
			}
		} else if (s.equalsIgnoreCase("cdeposit")) { // 자금을 입금한다
			pc.sendPackets(new S_Deposit(pc.getId()));
		} else if (s.equalsIgnoreCase("employ")) { // 용병의 고용
		} else if (s.equalsIgnoreCase("arrange")) { // 고용한 용병의 배치
		} else if (s.equalsIgnoreCase("castlegate")) { // 성문을 관리한다
		} else if (s.equalsIgnoreCase("encw")) { // 무기 전문가 / 무기의 강화 마법을 받는다
			if (pc.getWeapon() == null) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			} else {
				for (ItemInstance item : pc.getInventory().getItems()) {
					if (pc.getWeapon().equals(item)) {
						SkillUse l1skilluse = new SkillUse();
						l1skilluse.handleCommands(pc, SkillId.인챈트_웨폰, item.getId(), 0, 0, null, 0, SkillUse.TYPE_SPELLSC);
						break;
					}
				}
			}
			htmlid = ""; // 윈도우를 지운다		
		} else if (s.equalsIgnoreCase("enca")) { // 방어용 기구 전문가 / 방어용 기구의 강화 마법을 받는다
			ItemInstance item = pc.getInventory().getItemEquipped(2, 2);
			if (item != null) {
				SkillUse l1skilluse = new SkillUse();
				l1skilluse.handleCommands(pc, SkillId.블레스드_아머, item.getId(), 0, 0, null, 0,
						SkillUse.TYPE_SPELLSC);
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("depositnpc")) { // 「동물을 맡긴다」
			Object[] petList = pc.getPetList().values().toArray();
			for (Object petObject : petList) {
				if (petObject instanceof PetInstance) { // 펫
					PetInstance pet = (PetInstance) petObject;
					pet.collect();
					pc.getPetList().remove(pet.getId());
					pet.deleteMe();
					pet.clearSkillEffectTimer();
				}
			}
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("withdrawnpc")) { // 「동물을 받는다」
			pc.sendPackets(new S_PetList(objid, pc));
		} else if (s.equalsIgnoreCase("changename")) { // 「이름을 결정한다」
			pc.setTempID(objid); // 펫의 오브젝트 ID를 보존해 둔다
			pc.sendPackets(new S_Message_YN(325, "")); // 동물의 이름을 결정해 주세요：
		} else if (s.equalsIgnoreCase("attackchr")) {
			if (obj instanceof L1Character) {
				L1Character cha = (L1Character) obj;
				pc.sendPackets(new S_SelectTarget(cha.getId()));
			}
		} else if (s.equalsIgnoreCase("select")) { // 경매 게시판의 리스트를 클릭
			pc.sendPackets(new S_AuctionBoardRead(objid, s2));
		} else if (s.equalsIgnoreCase("map")) { // 아지트의 위치를 확인한다
			pc.sendPackets(new S_HouseMap(objid, s2));
		} else if (s.equalsIgnoreCase("apply")) { // 경매에 참가한다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				if (pc.isCrown() && pc.getId() == clan.getLeaderId()) { // 군주, 한편, 혈맹주
					if (pc.getLevel() >= 15) {
						if (clan.getHouseId() == 0) {
							pc.sendPackets(new S_ApplyAuction(objid, s2));
						} else {
							pc.sendPackets(new S_ServerMessage(521)); // 벌써 집을 소유하고 있습니다.
							htmlid = ""; // 윈도우를 지운다
						}
					} else {
						pc.sendPackets(new S_ServerMessage(519)); // 레벨 15 미만의 군주는 경매에 참가할 수 없습니다.
						htmlid = ""; // 윈도우를 지운다
					}
				} else {
					pc.sendPackets(new S_ServerMessage(518)); // 이 인스트럭션은 혈맹의 군주만을 이용할 수 있습니다.
					htmlid = ""; // 윈도우를 지운다
				}
			} else {
				pc.sendPackets(new S_ServerMessage(518)); // 이 인스트럭션은 혈맹의 군주만을 이용할 수 있습니다.
				htmlid = ""; // 윈도우를 지운다
			}
		} else if (s.equalsIgnoreCase("open") // 문을 연다
				|| s.equalsIgnoreCase("close")) { // 문을 닫는다
			L1NpcInstance npc = (L1NpcInstance) obj;
			openCloseDoor(pc, npc, s);
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("expel")) { // 외부의 인간을 내쫓는다
			L1NpcInstance npc = (L1NpcInstance) obj;
			expelOtherClan(pc, npc.getNpcTemplate().get_npcId());
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("pay")) { // 세금을 납부한다
			L1NpcInstance npc = (L1NpcInstance) obj;
			htmldata = makeHouseTaxStrings(pc, npc);
			htmlid = "agpay";
		} else if (s.equalsIgnoreCase("payfee")) { // 세금을 납부한다
			L1NpcInstance npc = (L1NpcInstance) obj;
			payFee(pc, npc);
			htmlid = "";
		} else if (s.equalsIgnoreCase("name")) { // 가의 이름을 결정한다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int houseId = clan.getHouseId();
				if (houseId != 0) {
					L1House house = HouseTable.getInstance().getHouseTable(houseId);
					int keeperId = house.getKeeperId();
					L1NpcInstance npc = (L1NpcInstance) obj;
					if (npc.getNpcTemplate().get_npcId() == keeperId) {
						pc.setTempID(houseId); // 아지트 ID를 보존해 둔다
						pc.sendPackets(new S_Message_YN(512, "")); // 가의 이름은?
					}
				}
			}
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("rem")) { // 집안의 가구를 모두 없앤다
		} else if (s.equalsIgnoreCase("tel0") // 텔레포트 한다(창고)
				|| s.equalsIgnoreCase("tel1") // 텔레포트 한다(애완동물 보관소)
				|| s.equalsIgnoreCase("tel2") // 텔레포트 한다(속죄의 사자)
				|| s.equalsIgnoreCase("tel3")) { // 텔레포트 한다(기란 시장)
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int houseId = clan.getHouseId();
				if (houseId != 0) {
					L1House house = HouseTable.getInstance().getHouseTable(houseId);
					int keeperId = house.getKeeperId();
					L1NpcInstance npc = (L1NpcInstance) obj;
					if (npc.getNpcTemplate().get_npcId() == keeperId) {
						int[] loc = new int[3];
						if (s.equalsIgnoreCase("tel0")) {
							loc = L1HouseLocation.getHouseTeleportLoc(houseId, 0);
						} else if (s.equalsIgnoreCase("tel1")) {
							loc = L1HouseLocation.getHouseTeleportLoc(houseId, 1);
						} else if (s.equalsIgnoreCase("tel2")) {
							loc = L1HouseLocation.getHouseTeleportLoc(houseId, 2);
						} else if (s.equalsIgnoreCase("tel3")) {
							loc = L1HouseLocation.getHouseTeleportLoc(houseId, 3);
						}
						pc.teleport(loc[0], loc[1], (short) loc[2], pc.getHeading(), 169, true, false);
					}
				}
			}
			htmlid = ""; // 윈도우를 지운다
		} else if (s.equalsIgnoreCase("upgrade")) { // 지하 아지트를 만든다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int houseId = clan.getHouseId();
				if (houseId != 0) {
					L1House house = HouseTable.getInstance().getHouseTable(houseId);
					int keeperId = house.getKeeperId();
					L1NpcInstance npc = (L1NpcInstance) obj;
					if (npc.getNpcTemplate().get_npcId() == keeperId) {
						if (pc.isCrown() && pc.getId() == clan.getLeaderId()) { // 군주, 한편, 혈맹주
							if (house.isPurchaseBasement()) {
								// 이미 지하 아지트를 소유하고 있습니다.
								pc.sendPackets(new S_ServerMessage(1135));
							} else {
								if (pc.getInventory().consumeItem(ItemId.아데나, 5000000)) {
									house.setPurchaseBasement(true);
									HouseTable.getInstance().updateHouse(house); // DB에 기입해
									// 지하 아지트가 생성되었습니다.
									pc.sendPackets(new S_ServerMessage(1099));
								} else {
									// \f1아데나가 부족합니다.
									pc.sendPackets(new S_ServerMessage(189));
								}
							}
						} else {
							// 이 명령은 혈맹의 군주만을 이용할 수 있습니다.
							pc.sendPackets(new S_ServerMessage(518));
						}
					}
				}
			}
			htmlid = ""; // 윈도우를 지운다
		}
		else if (s.equalsIgnoreCase("hall") && obj instanceof L1HousekeeperInstance) { // 지하 아지트에 텔레포트 한다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int houseId = clan.getHouseId();
				if (houseId != 0) {
					L1House house = HouseTable.getInstance().getHouseTable(houseId);
					int keeperId = house.getKeeperId();
					L1NpcInstance npc = (L1NpcInstance) obj;
					if (npc.getNpcTemplate().get_npcId() == keeperId) {
						if (house.isPurchaseBasement()) {
							int[] loc = new int[3];
							loc = L1HouseLocation.getBasementLoc(houseId);
							pc.teleport(loc[0], loc[1], (short) loc[2], pc.getHeading(), 169, true, false);
						} else {
							// 지하 아지트가 없기 때문에, 텔레포트 할 수 없습니다.
							pc.sendPackets(new S_ServerMessage(1098));
						}
					}
				}
			}
			htmlid = ""; // 윈도우를 지운다
			// ElfAttr:0.무속성, 1.땅속성, 2.불속성, 4.물속성, 8.바람 속성
		} 
		else if (s.equalsIgnoreCase("fire")) {// 엘프의 속성 변경 「불의 계열을 배운다」	
			if (pc.isElf()) {
				if (pc.getElfAttr() != 0) {
					return;
				}
				pc.setElfAttr(2);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				pc.sendPackets(new S_SkillIconGFX(15, 1)); // 체의 구석구석에 화의 정령력이 스며들어 옵니다.
				htmlid = ""; // 윈도우를 지운다
			}
		} 
		else if (s.equalsIgnoreCase("water")) { // 에르프의 속성 변경 「물의 계열을 배운다」
			if (pc.isElf()) {
				if (pc.getElfAttr() != 0) {
					return;
				}
				pc.setElfAttr(4);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				pc.sendPackets(new S_SkillIconGFX(15, 2)); // 체의 구석구석에 물의 정령력이 스며들어 옵니다.
				htmlid = ""; // 윈도우를 지운다
			}
		} 
		else if (s.equalsIgnoreCase("air")) { // 에르프의 속성 변경 「바람의 계열을 배운다」
			if (pc.isElf()) {
				if (pc.getElfAttr() != 0) {
					return;
				}
				pc.setElfAttr(8);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				pc.sendPackets(new S_SkillIconGFX(15, 3)); // 체의 구석구석에 바람의 정령력이 스며들어 옵니다.
				htmlid = ""; // 윈도우를 지운다
			}
		} 
		else if (s.equalsIgnoreCase("earth")) { // 에르프의 속성 변경 「땅의 계열을 배운다」
			if (pc.isElf()) {
				if (pc.getElfAttr() != 0) {
					return;
				}
				pc.setElfAttr(1);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				pc.sendPackets(new S_SkillIconGFX(15, 4)); // 체의 구석구석에 땅의 정령력이 스며들어 옵니다.
				htmlid = ""; // 윈도우를 지운다
			}
		} 
		else if (s.equalsIgnoreCase("init")) { // 엘프의 속성 변경 「정령력을 제거한다」
			if (pc.isElf()) {
				if (pc.getElfAttr() == 0) {
					return;
				}
				for (int cnt = 129; cnt <= 176; cnt++) {// 전에르프 마법을 체크		
					L1Skills l1skills1 = SkillsTable.getInstance().getTemplate(cnt);
					int skill_attr = l1skills1.getAttr();
					if (skill_attr != 0) {// 무속성 마법 이외의 엘프 마법을 DB로부터 삭제한다
						SkillsTable.getInstance().spellLost(pc.getId(), l1skills1.getSkillId());
					}
				}
				// 일렉트로닉 멘탈 프로텍션에 의해 상승하고 있는 속성 방어를 리셋트
				if (pc.hasSkillEffect(SkillId.프로텍션_프롬_엘리멘탈)) {
					pc.removeSkillEffect(SkillId.프로텍션_프롬_엘리멘탈);
				}
				pc.sendPackets(new S_DelSkill(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 248, 252, 252, 255, 0, 0)); // 무속성																									// 삭제한다
				pc.setElfAttr(0);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				pc.sendPackets(new S_ServerMessage(678));
				htmlid = ""; // 윈도우를 지운다
			}
		} 
		else if(s.equalsIgnoreCase("exp")) { // 「경험치를 회복한다」
			if (pc.getExpRes() == 1) {
				int cost = 0;
				int level = pc.getLevel();
				int lawful = pc.getLawful();
				if (level < 45) {
					cost = level * level * 100;
				} else {
					cost = level * level * 200;
				}
				if (lawful >= 0) {
					cost = (cost / 2);
				}
				pc.sendPackets(new S_Message_YN(738, String.valueOf(cost))); // 경험치를 회복하려면%0의 아데나가 필요합니다.경험치를 회복합니까?
			} else {
				pc.sendPackets(new S_ServerMessage(739)); // 지금은 경험치를 회복할 수가 없습니다.
				htmlid = ""; // 윈도우를 지운다
			}
		} 
		else if(s.equalsIgnoreCase("pk")) { // 「속죄 한다」
			if (pc.getLawful() < 30000) {
				pc.sendPackets(new S_ServerMessage(559)); // \f1 아직 죄 풀어에 충분한 젱교를 실시하고 있지 않습니다.
			} else if (pc.get_PKcount() < 5) {
				pc.sendPackets(new S_ServerMessage(560)); // \f1 아직 죄청등 해를 할 필요는 없습니다.
			} else {
				if (pc.getInventory().consumeItem(ItemId.아데나, 700000)) {
					pc.set_PKcount(pc.get_PKcount() - 5);
					pc.sendPackets(new S_ServerMessage(561, String.valueOf(pc.get_PKcount()))); // PK회수가%0가 되었습니다.
				} else {
					pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
				}
			}
			// 윈도우를 지운다
			htmlid = "";
		} 
		else if(s.equalsIgnoreCase("ent")) { // 유령의 집 관람 입장
			int npcId = ((L1NpcInstance) obj).getNpcId();
			if (npcId == 80085 || npcId == 80086 || npcId == 80087) {
				htmlid = enterHauntedHouse(pc);
			} else if (npcId == 50038 || npcId == 50042 || npcId == 50029 || npcId == 50019 || npcId == 50062) { 
				htmlid = watchUb(pc, npcId);
			} else if (npcId == 81186) { 
				if (pc.getLevel() != pc.getHighLevel()) {
					pc.sendPackets("레벨이 다운된 캐릭입니다. 레벨업 후 이용하세요.");
					return;
				}
				if (pc.getLevel() > 51) {
					if (pc.getInventory().checkItem(49029)) {
						pc.getInventory().consumeItem(49029, 1);
						int locx2 = 32723 + CommonUtil.random(10);
						int locy2 = 32851 + CommonUtil.random(10);
						pc.teleport(locx2, locy2, (short) 5166, pc.getHeading(), 169, true, false);
						MJReturnedStat(pc);
						htmlid = "";
					} else {
						pc.sendPackets(new S_ServerMessage(1290));
					}
				} else
					pc.sendPackets(new S_SystemMessage("스텟 초기화는 52레벨 이상만 가능합니다."));

			} else {
				htmlid = enterUb(pc, npcId);
			}
		} 
		else if(s.equalsIgnoreCase("par")) { // UB관련 「얼티메이트 배틀에 참가한다」부관리인 경유
			htmlid = enterUb(pc, ((L1NpcInstance) obj).getNpcId());
		} 
		else if(s.equalsIgnoreCase("info")) { // 「정보를 확인한다」 「경기 정보를 확인한다」
			int npcId = ((L1NpcInstance) obj).getNpcId();
			if (npcId == 80085 || npcId == 80086 || npcId == 80087) {
			} else {
				htmlid = "colos2";
			}
		} else if(s.equalsIgnoreCase("sco")) { // UB관련 「고득점자 일람을 확인한다」
			htmldata = new String[10];
			htmlid = "colos3";
		} else if(s.equalsIgnoreCase("haste")) { // 헤이 파업사
			L1NpcInstance npc = (L1NpcInstance) obj;
			int npcid = npc.getNpcTemplate().get_npcId();
			if (npcid == 70514 && pc.getLevel() <= 13) {
				pc.sendPackets(new S_ServerMessage(183));
				S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 1, 1600);
				pc.sendPackets(Haste);
				pc.broadcastPacket(Haste); 
				pc.sendPackets(S_SkillSound.get(pc.getId(), 755));
				pc.setMoveSpeed(1);
				pc.setSkillEffect(SkillId.상태_1단_가속, 1600 * 1000);
				htmlid = ""; // 윈도우를 지운다
			}
		} else if(s.equalsIgnoreCase("skeleton nbmorph")) { // 변신 관련
			poly(client, 2374);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("lycanthrope nbmorph")) {
			poly(client, 3874);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("shelob nbmorph")) {
			poly(client, 95);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("ghoul nbmorph")) {
			poly(client, 3873);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("ghast nbmorph")) {
			poly(client, 3875);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("atuba orc nbmorph")) {
			poly(client, 3868);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("skeleton axeman nbmorph")) {
			poly(client, 2376);
			htmlid = ""; // 윈도우를 지운다
		} else if(s.equalsIgnoreCase("troll nbmorph")) {
			poly(client, 3878);
			htmlid = ""; // 윈도우를 지운다
		}// 장로 노나메
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71038) {
			// 「편지를 받는다」
			if (s.equalsIgnoreCase("A")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				ItemInstance item = pc.getInventory().storeItem(41060, 1); // 노나메의 추천서
				String npcName = npc.getNpcTemplate().get_name();
				String itemName = item.getItem().getName();
				pc.sendPackets(new S_ServerMessage(143, npcName, itemName)); // \f1%0이%1를 주었습니다.
				htmlid = "orcfnoname9";
			}
			// 「조사를 그만둡니다」
			else if (s.equalsIgnoreCase("Z")) {
				if (pc.getInventory().consumeItem(41060, 1)) {
					htmlid = "orcfnoname11";
				}
			}
		}
		// 두다마라브우
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71039) {
			// 「알았습니다, 그 자리곳에 보내 주세요」
			if (s.equalsIgnoreCase("teleportURL")) {
				htmlid = "orcfbuwoo2";
			}
		}
		// 조사단장 아트바노아
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71040) {
			// 「해 봅니다」
			if (s.equalsIgnoreCase("A")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				ItemInstance item = pc.getInventory().storeItem(41065, 1); // 조사단의 증서
				String npcName = npc.getNpcTemplate().get_name();
				String itemName = item.getItem().getName();
				pc.sendPackets(new S_ServerMessage(143, npcName, itemName)); // \f1%0이%1를 주었습니다.
				htmlid = "orcfnoa4";
			}
			// 「조사를 그만둡니다」
			else if (s.equalsIgnoreCase("Z")) {
				if (pc.getInventory().consumeItem(41065, 1)) {
					htmlid = "orcfnoa7";
				}
			}
		}
		// 네르가후우모
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71041) {
			// 「조사를 합니다」
			if (s.equalsIgnoreCase("A")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				ItemInstance item = pc.getInventory().storeItem(41064, 1); // 조사단의 증서
				String npcName = npc.getNpcTemplate().get_name();
				String itemName = item.getItem().getName();
				pc.sendPackets(new S_ServerMessage(143, npcName, itemName)); // \f1%0이%1를 주었습니다.
				htmlid = "orcfhuwoomo4";
			}
			// 「조사를 그만둡니다」
			else if (s.equalsIgnoreCase("Z")) {
				if (pc.getInventory().consumeItem(41064, 1)) {
					htmlid = "orcfhuwoomo6";
				}
			}
		}
		// 네르가바크모
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71042) {
			// 「조사를 합니다」
			if (s.equalsIgnoreCase("A")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				ItemInstance item = pc.getInventory().storeItem(41062, 1); // 조사단의 증서
				String npcName = npc.getNpcTemplate().get_name();
				String itemName = item.getItem().getName();
				pc.sendPackets(new S_ServerMessage(143, npcName, itemName)); // \f1%0이%1를 주었습니다.
				htmlid = "orcfbakumo4";
			}
			// 「조사를 그만둡니다」
			else if (s.equalsIgnoreCase("Z")) {
				if (pc.getInventory().consumeItem(41062, 1)) {
					htmlid = "orcfbakumo6";
				}
			}
		}
		// 두다마라브카
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71043) {
			// 「조사를 합니다」
			if (s.equalsIgnoreCase("A")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				ItemInstance item = pc.getInventory().storeItem(41063, 1); // 조사단의 증서
				String npcName = npc.getNpcTemplate().get_name();
				String itemName = item.getItem().getName();
				pc.sendPackets(new S_ServerMessage(143, npcName, itemName)); // \f1%0이%1를 주었습니다.
				htmlid = "orcfbuka4";
			}
			// 「조사를 그만둡니다」
			else if (s.equalsIgnoreCase("Z")) {
				if (pc.getInventory().consumeItem(41063, 1)) {
					htmlid = "orcfbuka6";
				}
			}
		}
		// 두다마라카메
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71044) {
			// 「조사를 합니다」
			if (s.equalsIgnoreCase("A")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				ItemInstance item = pc.getInventory().storeItem(41061, 1); // 조사단의 증서
				String npcName = npc.getNpcTemplate().get_name();
				String itemName = item.getItem().getName();
				pc.sendPackets(new S_ServerMessage(143, npcName, itemName)); // \f1%0이%1를 주었습니다.
				htmlid = "orcfkame4";
			}
			// 「조사를 그만둡니다」
			else if (s.equalsIgnoreCase("Z")) {
				if (pc.getInventory().consumeItem(41061, 1)) {
					htmlid = "orcfkame6";
				}
			}
		} 	// 포워르
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71078) {
			// 「들어가 본다」
			if (s.equalsIgnoreCase("teleportURL")) {
				htmlid = "usender2";
			}
		}
		
		//테베
	else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4200003
			|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4200007) {
		if (s.equalsIgnoreCase("B")) {
			if(pc.getInventory().checkItem(ItemId.시간의_균열_파편)){
				pc.getInventory().consumeItem(ItemId.시간의_균열_파편, 1);
				pc.teleport(33970, 33246, (short) 4, pc.getHeading(), 169, true, false);			
			}else{
				htmlid = "joegolem20";
			}
		}
	}
	else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4212000) { // npcid			
		// 마력의 단검
		if (s.equalsIgnoreCase("A")) {
			if(pc.getInventory().MakeCheckEnchant(5, 7) 
					&& pc.getInventory().MakeCheckEnchant(6, 7) 
					&& pc.getInventory().checkItem(41246, 1000) 
					&& pc.getInventory().checkItem(ItemId.용기의_결정, 10)){					
				pc.getInventory().MakeDeleteEnchant(5, 7);
				pc.getInventory().MakeDeleteEnchant(6, 7);
				pc.getInventory().consumeItem(41246, 1000);
				pc.getInventory().consumeItem(ItemId.용기의_결정, 10);
				pc.getInventory().storeItem(263, 1);
				htmlid="joegolem9";
			} else {
				htmlid="joegolem15";
			}
		}
		// 광풍의 도끼
		if (s.equalsIgnoreCase("B")) { 
			if(pc.getInventory().MakeCheckEnchant(145, 7) 
					&& pc.getInventory().MakeCheckEnchant(148, 7) 
					&& pc.getInventory().checkItem(41246, 1000) 
					&& pc.getInventory().checkItem(ItemId.용기의_결정, 10)){					
				pc.getInventory().MakeDeleteEnchant(145, 7);
				pc.getInventory().MakeDeleteEnchant(148, 7);
				pc.getInventory().consumeItem(41246, 1000);
				pc.getInventory().consumeItem(ItemId.용기의_결정, 10);
				pc.getInventory().storeItem(266, 1);
				htmlid="joegolem10";
			} else {
				htmlid="joegolem15";
			}
		}
		// 파멸의 대검 
		if (s.equalsIgnoreCase("C")) { 
			if(pc.getInventory().MakeCheckEnchant(52, 7) 
					&& pc.getInventory().MakeCheckEnchant(64, 7) 
					&& pc.getInventory().checkItem(41246, 1000) 
					&& pc.getInventory().checkItem(ItemId.용기의_결정, 10)){					
				pc.getInventory().MakeDeleteEnchant(52, 7);
				pc.getInventory().MakeDeleteEnchant(64, 7);
				pc.getInventory().consumeItem(41246, 1000);
				pc.getInventory().consumeItem(ItemId.용기의_결정, 10);
				pc.getInventory().storeItem(262, 1);
				htmlid="joegolem11";
			} else {
				htmlid="joegolem15";
			}
		}
		// 아크메이지의 지팡이 
		if (s.equalsIgnoreCase("D")) { 
			if(pc.getInventory().MakeCheckEnchant(125, 7) 
					&& pc.getInventory().MakeCheckEnchant(129, 7) 
					&& pc.getInventory().checkItem(41246, 1000) 
					&& pc.getInventory().checkItem(ItemId.용기의_결정, 10)){					
				pc.getInventory().MakeDeleteEnchant(125, 7);
				pc.getInventory().MakeDeleteEnchant(129, 7);
				pc.getInventory().consumeItem(41246, 1000);
				pc.getInventory().consumeItem(ItemId.용기의_결정, 10);
				pc.getInventory().storeItem(264, 1);
				htmlid="joegolem12";
			} else {
				htmlid="joegolem15";
			}
		}
		// 혹한의 창
		if (s.equalsIgnoreCase("E")) { 
			if(pc.getInventory().MakeCheckEnchant(99, 7) 
					&& pc.getInventory().MakeCheckEnchant(104, 7) 
					&& pc.getInventory().checkItem(41246, 1000) 
					&& pc.getInventory().checkItem(ItemId.용기의_결정, 10)){					
				pc.getInventory().MakeDeleteEnchant(99, 7);
				pc.getInventory().MakeDeleteEnchant(104, 7);
				pc.getInventory().consumeItem(41246, 1000);
				pc.getInventory().consumeItem(ItemId.용기의_결정, 10);
				pc.getInventory().storeItem(265, 1);
				htmlid="joegolem13";
			} else {
				htmlid="joegolem15";
			}
		}
		// 뇌신검
		if (s.equalsIgnoreCase("F")) { 
			if(pc.getInventory().MakeCheckEnchant(32, 7) 
					&& pc.getInventory().MakeCheckEnchant(42, 7) 
					&& pc.getInventory().checkItem(41246, 1000) 
					&& pc.getInventory().checkItem(ItemId.용기의_결정, 10)){					
				pc.getInventory().MakeDeleteEnchant(32, 7);
				pc.getInventory().MakeDeleteEnchant(42, 7);
				pc.getInventory().consumeItem(41246, 1000);
				pc.getInventory().consumeItem(ItemId.용기의_결정, 10);
				pc.getInventory().storeItem(261, 1);
				htmlid="joegolem14";
			} else {
				htmlid="joegolem15";
			}
	
		}
		
	}
	
			// 조우의 돌골렘	
		
		// 치안 단장 아미스
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71080) {
			// 「내가 도웁시다」
			if (s.equalsIgnoreCase("teleportURL")) {
				htmlid = "amisoo2";
			}
		}
		// 공간의 일그러짐
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80048) {
			// 「들어가 본다」
			if (s.equalsIgnoreCase("1")) {  //들어가본다
				pc.teleport(32676, 32960, (short) 521, pc.getHeading(), 169, true, false);			
			} else if (s.equalsIgnoreCase("3")) { // 건드려본다
				pc.teleport(32536, 32955, (short) 777, pc.getHeading(), 169, true, false);
			}
			htmlid = ""; // 윈도우를 지운다
		}
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 10000272) { // 말하는 섬 던전 입장
			// 「들어가 본다」
			if (s.equalsIgnoreCase("1")) {
				pc.teleport(32676, 32960, (short) 521, pc.getHeading(), 169, true, false);
			}
			htmlid = ""; // 윈도우를 지운다
		}
		// 요동하는 사람
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80049) {
			// 「바르로그의 의지를 맞아들인다」
			if (s.equalsIgnoreCase("1")) {
				if (pc.getKarma() <= -10000000) {
					pc.setKarma(1000000);
					// 바르로그의 웃음소리가 뇌리를 강타합니다.
					pc.sendPackets(new S_ServerMessage(1078));
					htmlid = "betray13";
				}
			}
		}
		// 야히의 집정관
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80050) {
			// 「 나의 영혼은 야히님에게…」
			if (s.equalsIgnoreCase("1")) {
				htmlid = "meet105";
			}
			// 「 나의 영혼을 걸쳐 야히님이 충성을 맹세합니다…」
			else if (s.equalsIgnoreCase("2")) {
				if (pc.getInventory().checkItem(40718)) { // 블래드 크리스탈의 조각
					htmlid = "meet106";
				} else {
					htmlid = "meet110";
				}
			}
			// 「블래드 크리스탈의 조각을 1개 바칩니다」
			else if (s.equalsIgnoreCase("a")) {
				if (pc.getInventory().consumeItem(40718, 1)) {
					pc.addKarma((int) (-100 * Config.RATE_KARMA));
					// 야히의 모습이 점점 근처에 느껴집니다.
					pc.sendPackets(new S_ServerMessage(1079));
					htmlid = "meet107";
				} else {
					htmlid = "meet104";
				}
			}
			// 「블래드 크리스탈의 조각을 10개 바칩니다」
			else if (s.equalsIgnoreCase("b")) {
				if (pc.getInventory().consumeItem(40718, 10)) {
					pc.addKarma((int) (-1000 * Config.RATE_KARMA));
					// 야히의 모습이 점점 근처에 느껴집니다.
					pc.sendPackets(new S_ServerMessage(1079));
					htmlid = "meet108";
				} else {
					htmlid = "meet104";
				}
			}
			// 「블래드 크리스탈의 조각을 100개 바칩니다」
			else if (s.equalsIgnoreCase("c")) {
				if (pc.getInventory().consumeItem(40718, 100)) {
					pc.addKarma((int) (-10000 * Config.RATE_KARMA));
					// 야히의 모습이 점점 근처에 느껴집니다.
					pc.sendPackets(new S_ServerMessage(1079));
					htmlid = "meet109";
				} else {
					htmlid = "meet104";
				}
			}
			// 「야히님이 대면시쳐 주세요」
			else if (s.equalsIgnoreCase("d")) {
				if (pc.getInventory().checkItem(40615) // 그림자의 신전 2층의 열쇠
						|| pc.getInventory().checkItem(40616)) { // 그림자의 신전 3층의 열쇠
					htmlid = "";
				} else {
					pc.teleport(32683, 32895, (short) 608, pc.getHeading(), 169, true, false);
				}
			}
		} 
		// 야히의 대장간
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80053) {
			int karmaLevel = pc.getKarmaLevel();
			// 「재료 모든 것을 준비할 수 있었습니다」
			if (s.equalsIgnoreCase("a")) {
				// 바르로그의 투 핸드 소도 / 야히의 대장간
				int aliceMaterialId = 0;
				int[] aliceMaterialIdList = { 40991, 196, 197, 198, 199, 200, 201, 202, 203 };
				for (int id : aliceMaterialIdList) {
					if (pc.getInventory().checkItem(id)) {
						aliceMaterialId = id;
						break;
					}
				}
				if (aliceMaterialId == 0) {
					htmlid = "alice_no";
				} else if (aliceMaterialId == 40991) {
					if (karmaLevel <= -1) {
						materials = new int[] { 40995, 40718, 40991 };
						counts = new int[] { 100, 100, 1 };
						createitem = new int[] { 196 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_1";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "aliceyet";
					}
				} else if (aliceMaterialId == 196) {
					if (karmaLevel <= -2) {
						materials = new int[] { 40997, 40718, 196 };
						counts = new int[] { 100, 100, 1 };
						createitem = new int[] { 197 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_2";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_1";
					}
				} else if (aliceMaterialId == 197) {
					if (karmaLevel <= -3) {
						materials = new int[] { 40990, 40718, 197 };
						counts = new int[] { 100, 100, 1 };
						createitem = new int[] { 198 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_3";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_2";
					}
				} else if (aliceMaterialId == 198) {
					if (karmaLevel <= -4) {
						materials = new int[] { 40994, 40718, 198 };
						counts = new int[] { 50, 100, 1 };
						createitem = new int[] { 199 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_4";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_3";
					}
				} else if (aliceMaterialId == 199) {
					if (karmaLevel <= -5) {
						materials = new int[] { 40993, 40718, 199 };
						counts = new int[] { 50, 100, 1 };
						createitem = new int[] { 200 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_5";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_4";
					}
				} else if (aliceMaterialId == 200) {
					if (karmaLevel <= -6) {
						materials = new int[] { 40998, 40718, 200 };
						counts = new int[] { 50, 100, 1 };
						createitem = new int[] { 201 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_6";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_5";
					}
				} else if (aliceMaterialId == 201) {
					if (karmaLevel <= -7) {
						materials = new int[] { 40996, 40718, 201 };
						counts = new int[] { 10, 100, 1 };
						createitem = new int[] { 202 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_7";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_6";
					}
				} else if (aliceMaterialId == 202) {
					if (karmaLevel <= -8) {
						materials = new int[] { 40992, 40718, 202 };
						counts = new int[] { 10, 100, 1 };
						createitem = new int[] { 203 };
						createcount = new int[] { 1 };
						success_htmlid = "alice_8";
						failure_htmlid = "alice_no";
					} else {
						htmlid = "alice_7";
					}
				} else if (aliceMaterialId == 203) {
					htmlid = "alice_8";
				}
			}
		}
		// 야히의 보좌관
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80055) {
			L1NpcInstance npc = (L1NpcInstance) obj;
			htmlid = getYaheeAmulet(pc, npc, s);
		}
		// 업의 관리자
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80056) {
			L1NpcInstance npc = (L1NpcInstance) obj;
			if (pc.getKarma() <= -10000000) {
				getBloodCrystalByKarma(pc, npc, s);
			}
			htmlid = "";
		}
		// 차원의 문(발록 방)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80063) {
			// 「안에 들어온다」
			if (s.equalsIgnoreCase("a")) {
				if (pc.getInventory().checkItem(40921)) { // 원소의 지배자
					pc.teleport(32674, 32832, (short) 603,  pc.getHeading(), 169, true, false);
				} else {
					htmlid = "gpass02";
				}
			}
		}
		// 바르로그의 집정관
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80064) {
			// 「 나의 영원의 주는 바르로그님 뿐입니다…」
			if (s.equalsIgnoreCase("1")) {
				htmlid = "meet005";
			}
			// 「 나의 영혼을 걸쳐 바르로그님이 충성을 맹세합니다…」
			else if (s.equalsIgnoreCase("2")) {
				if (pc.getInventory().checkItem(40678)) { // 서울 크리스탈의 조각
					htmlid = "meet006";
				} else {
					htmlid = "meet010";
				}
			}
			// 「서울 크리스탈의 조각을 1개 바칩니다」
			else if (s.equalsIgnoreCase("a")) {
				if (pc.getInventory().consumeItem(40678, 1)) {
					pc.addKarma((int) (100 * Config.RATE_KARMA));
					// 바르로그의 웃음소리가 뇌리를 강타합니다.
					pc.sendPackets(new S_ServerMessage(1078));
					htmlid = "meet007";
				} else {
					htmlid = "meet004";
				}
			}
			// 「서울 크리스탈의 조각을 10개 바칩니다」
			else if (s.equalsIgnoreCase("b")) {
				if (pc.getInventory().consumeItem(40678, 10)) {
					pc.addKarma((int) (1000 * Config.RATE_KARMA));
					// 바르로그의 웃음소리가 뇌리를 강타합니다.
					pc.sendPackets(new S_ServerMessage(1078));
					htmlid = "meet008";
				} else {
					htmlid = "meet004";
				}
			}
			// 「서울 크리스탈의 조각을 100개 바칩니다」
			else if (s.equalsIgnoreCase("c")) {
				if (pc.getInventory().consumeItem(40678, 100)) {
					pc.addKarma((int) (10000 * Config.RATE_KARMA));
					// 바르로그의 웃음소리가 뇌리를 강타합니다.
					pc.sendPackets(new S_ServerMessage(1078));
					htmlid = "meet009";
				} else {
					htmlid = "meet004";
				}
			}
			// 「바르로그님이 대면시쳐 주세요」
			else if (s.equalsIgnoreCase("d")) {
				if (pc.getInventory().checkItem(40909) // 지의 통행증
						|| pc.getInventory().checkItem(40910) // 수의 통행증
						|| pc.getInventory().checkItem(40911) // 불의 통행증
						|| pc.getInventory().checkItem(40912) // 풍의 통행증
						|| pc.getInventory().checkItem(40913) // 지의 인장
						|| pc.getInventory().checkItem(40914) // 수의 인장
						|| pc.getInventory().checkItem(40915) // 불의 인장
						|| pc.getInventory().checkItem(40916) // 풍의 인장
						|| pc.getInventory().checkItem(40917) // 지의 지배자
						|| pc.getInventory().checkItem(40918) // 수의 지배자
						|| pc.getInventory().checkItem(40919) // 불의 지배자
						|| pc.getInventory().checkItem(40920) // 풍의 지배자
						|| pc.getInventory().checkItem(40921)) { // 원소의 지배자
					htmlid = "";
				} else {
					pc.teleport(32674, 32832, (short) 602, pc.getHeading(), 169, true, false);
				}
			}		
		} 
		// 흔들거리는 사람
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80066) {
			// 「카헬의 의지를 받아들인다」
			if (s.equalsIgnoreCase("1")) {
				if (pc.getKarma() >= 10000000) {
					pc.setKarma(-1000000);
					// 야히의 모습이 점점 근처에 느껴집니다.
					pc.sendPackets(new S_ServerMessage(1079));
					htmlid = "betray03";
				}
			}
		}
		// 바르로그의 보좌관
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80071) {
			L1NpcInstance npc = (L1NpcInstance) obj;
			htmlid = getBarlogEarring(pc, npc, s);
		}
		// 바르로그의 대장간
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80072) {
			int karmaLevel = pc.getKarmaLevel();
			if (s.equalsIgnoreCase("0")) {
				htmlid = "lsmitha";
			} else if (s.equalsIgnoreCase("1")) {
				htmlid = "lsmithb";
			} else if (s.equalsIgnoreCase("2")) {
				htmlid = "lsmithc";
			} else if (s.equalsIgnoreCase("3")) {
				htmlid = "lsmithd";
			} else if (s.equalsIgnoreCase("4")) {
				htmlid = "lsmithe";
			} else if (s.equalsIgnoreCase("5")) {
				htmlid = "lsmithf";
			} else if (s.equalsIgnoreCase("6")) {
				htmlid = "";
			} else if (s.equalsIgnoreCase("7")) {
				htmlid = "lsmithg";
			} else if (s.equalsIgnoreCase("8")) {
				htmlid = "lsmithh";
			}
			// 야히의 셔츠 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("a") && karmaLevel >= 1) {
				materials = new int[] { 20158, 40669, 40678 };
				counts = new int[] { 1, 50, 100 };
				createitem = new int[] { 20083 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithaa";
			}
			// 야히의 아모 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("b") && karmaLevel >= 2) {
				materials = new int[] { 20144, 40672, 40678 };
				counts = new int[] { 1, 50, 100 };
				createitem = new int[] { 20131 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithbb";
			}
			// 야히의 아모 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("c") && karmaLevel >= 3) {
				materials = new int[] { 20075, 40671, 40678 };
				counts = new int[] { 1, 50, 100 };
				createitem = new int[] { 20069 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithcc";
			}
			// 야히의 글로브 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("d") && karmaLevel >= 4) {
				materials = new int[] { 20183, 40674, 40678 };
				counts = new int[] { 1, 20, 100 };
				createitem = new int[] { 20179 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithdd";
			}
			// 야히의 부츠 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("e") && karmaLevel >= 5) {
				materials = new int[] { 20190, 40674, 40678 };
				counts = new int[] { 1, 40, 100 };
				createitem = new int[] { 20209 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithee";
			}
			// 야히의 링 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("f") && karmaLevel >= 6) {
				materials = new int[] { 20078, 40674, 40678 };
				counts = new int[] { 1, 5, 100 };
				createitem = new int[] { 20290 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithff";
			}
			// 야히의 아뮤렛트 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("g") && karmaLevel >= 7) {
				materials = new int[] { 20078, 40670, 40678 };
				counts = new int[] { 1, 1, 100 };
				createitem = new int[] { 20261 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithgg";
			}
			// 야히의 헤룸 / 바르로그의 대장간
			else if (s.equalsIgnoreCase("h") && karmaLevel >= 8) {
				materials = new int[] { 40719, 40673, 40678 };
				counts = new int[] { 1, 1, 100 };
				createitem = new int[] { 20031 };
				createcount = new int[] { 1 };
				success_htmlid = "";
				failure_htmlid = "lsmithhh";
			}
		}
		
		// 업의 관리자
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80074) {
			L1NpcInstance npc = (L1NpcInstance) obj;
			if (pc.getKarma() >= 10000000) {
				getSoulCrystalByKarma(pc, npc, s);
			}
			htmlid = "";
		} 
		// 아르폰스
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80057) {
			htmlid = karmaLevelToHtmlId(pc.getKarmaLevel());
			htmldata = new String[] { String.valueOf(pc.getKarmaPercent()) };
		}
		// 차원의 문(흙풍수불)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80059
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80060
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80061
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80062) {
			htmlid = talkToDimensionDoor(pc, (L1NpcInstance) obj, s);
		}	
		// 버프도우미
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200014) {
					if (pc._젠도르빈줌갯수 > 0) {
						int itemid = 0;
						int aden = 50;
						int emptyScroll = 40090;
						if (s.equalsIgnoreCase("A")) {
							itemid = 40860;
						} else if (s.equalsIgnoreCase("B")) {
							itemid = 40861;
						} else if (s.equalsIgnoreCase("C")) {
							itemid = 40862;
						} else if (s.equalsIgnoreCase("D")) {
							itemid = 40866;
						} else if (s.equalsIgnoreCase("E")) {
							itemid = 40859;
						} else if (s.equalsIgnoreCase("F")) { // 디크리즈
							aden = 100;
							itemid = 40872;
							emptyScroll = 40091;
						} else if (s.equalsIgnoreCase("G")) { // 디텍
							aden = 100;
							itemid = 40871;
							emptyScroll = 40091;
						} else if (s.equalsIgnoreCase("H")) { // 인첸트웨폰
							aden = 100;
							itemid = 40870;
							emptyScroll = 40091;
						} else if (s.equalsIgnoreCase("I")) { // 큐어 포이즌
							aden = 100;
							itemid = 40867;
							emptyScroll = 40091;
						} else if (s.equalsIgnoreCase("J")) { // 파이어 애로우
							aden = 100;
							itemid = 40873;
							emptyScroll = 40091;
						} else if (s.equalsIgnoreCase("K")) { // 라이트닝
							aden = 100;
							itemid = 40875;
							emptyScroll = 40092;
						} else if (s.equalsIgnoreCase("L")) { // 블레스드 아머
							aden = 100;
							itemid = 40879;
							emptyScroll = 40092;
						} else if (s.equalsIgnoreCase("M")) { // 익스트라 힐
							aden = 100;
							itemid = 40877;
							emptyScroll = 40092;
						} else if (s.equalsIgnoreCase("N")) { // 프로즌 클라우드
							aden = 100;
							itemid = 40880;
							emptyScroll = 40092;
						} else if (s.equalsIgnoreCase("O")) { // 턴언데드
							aden = 100;
							itemid = 40876;
							emptyScroll = 40092;
						} else if (s.equalsIgnoreCase("P")) { // 메디테이션
							aden = 200;
							itemid = 40890;
							emptyScroll = 40093;
						} else if (s.equalsIgnoreCase("Q")) { // 파이어볼
							aden = 200;
							itemid = 40883;
							emptyScroll = 40093;
						} else if (s.equalsIgnoreCase("R")) { // 인첸트 덱스터리
							aden = 200;
							itemid = 40884;
							emptyScroll = 40093;
						} else if (s.equalsIgnoreCase("S")) { // 카운터 매직
							aden = 200;
							itemid = 40889;
							emptyScroll = 40093;
						} else if (s.equalsIgnoreCase("T")) { // 슬로우
							aden = 200;
							itemid = 40887;
							emptyScroll = 40093;
						} else if (s.equalsIgnoreCase("U")) { // 그힐
							aden = 200;
							itemid = 40893;
							emptyScroll = 40094;
						} else if (s.equalsIgnoreCase("V")) { // 리무브 커스
							aden = 200;
							itemid = 40895;
							emptyScroll = 40094;
						} else if (s.equalsIgnoreCase("W")) { // 마나드레인
							aden = 200;
							itemid = 40897;
							emptyScroll = 40094;
						} else if (s.equalsIgnoreCase("X")) { // 콘오브콜드
							aden = 200;
							itemid = 40896;
							emptyScroll = 40094;
						} else if (s.equalsIgnoreCase("Y")) { // 콜라이트닝
							aden = 200;
							itemid = 40892;
							emptyScroll = 40094;
						}

						if (!pc.getInventory().checkItem(40308, aden * pc._젠도르빈줌갯수)) // 아덴체크
							htmlid = "bs_m6";
						else if (!pc.getInventory().checkItem(emptyScroll, pc._젠도르빈줌갯수)) // 빈줌
																							// 체크
							htmlid = "bs_m2";
						else if ((itemid == 40887 || itemid == 40889) && !pc.getInventory().consumeItem(40318, pc._젠도르빈줌갯수)) // 마돌
																																// 체크
							htmlid = "bs_m2";
						else if (itemid != 0) {
							pc.getInventory().consumeItem(40308, aden * pc._젠도르빈줌갯수);
							pc.getInventory().consumeItem(emptyScroll, pc._젠도르빈줌갯수);
							ItemInstance item = pc.getInventory().storeItem(itemid, pc._젠도르빈줌갯수);
							pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getName(),
									item.getName() + " (" + pc._젠도르빈줌갯수 + ")"), true);
							htmlid = "bs_m1";
						}
					} else {
						if (s.equalsIgnoreCase("a")) { // 근거리 버프
							if (pc.getInventory().checkItem(40308, 30000)) {
								if (pc.getLevel() >= 5) {
									pc.getInventory().consumeItem(40308, 30000);
									int[] allBuffSkill = { SkillId.인챈트_덱스터리티, SkillId.인챈트_마이티, SkillId.블레스_웨폰, SkillId.어드밴스_스피릿, SkillId.파이어_웨폰, SkillId.어스_스킨 };
									pc.setBuffnoch(1);
									SkillUse l1skilluse = new SkillUse();
									for (int i = 0; i < allBuffSkill.length; i++) {
										l1skilluse.handleCommands(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
									}
									htmlid = "";
								} else {
									pc.sendPackets(new S_SystemMessage("5레벨 이상부터 사용 가능합니다."));
								}					
							} else {
								pc.sendPackets(new S_SystemMessage("3만 아데나가 부족합니다."));
							}
						} else if (s.equalsIgnoreCase("b")) { //원거리버프
							if (pc.getInventory().checkItem(40308, 30000)) {
								if (pc.getLevel() >= 5) {
									pc.getInventory().consumeItem(40308, 30000);
									int[] allBuffSkill = { SkillId.인챈트_덱스터리티, SkillId.인챈트_마이티, SkillId.블레스_웨폰, SkillId.어드밴스_스피릿, SkillId.어스_스킨, SkillId.윈드_샷 };
									pc.setBuffnoch(1);
									SkillUse l1skilluse = new SkillUse();
									for (int i = 0; i < allBuffSkill.length; i++) {
										l1skilluse.handleCommands(pc, allBuffSkill[i],pc.getId(), pc.getX(), pc.getY(), null, 0,
												SkillUse.TYPE_GMBUFF);
									}
									htmlid = "";
								} else {
									pc.sendPackets(new S_SystemMessage("5레벨 이상부터 사용 가능합니다."));
								}
							} else {
								pc.sendPackets(new S_SystemMessage("3만 아데나가 부족합니다."));
							}
							
						}
						else if (s.equalsIgnoreCase("1")) {// 빈줌 1개
							htmlid = "bs_m4";
							htmldata = new String[] { "50", "100", "100", "200", "200", "1" };
							pc._젠도르빈줌갯수 = 1;
						} else if (s.equalsIgnoreCase("2")) {// 빈줌 5개
							htmlid = "bs_m4";
							htmldata = new String[] { "250", "500", "500", "1000", "1000", "5" };
							pc._젠도르빈줌갯수 = 5;
						} else if (s.equalsIgnoreCase("3")) {// 빈줌 10개
							htmlid = "bs_m4";
							htmldata = new String[] { "500", "1000", "1000", "2000", "2000", "10" };
							pc._젠도르빈줌갯수 = 10;
						} else if (s.equalsIgnoreCase("4")) {// 빈줌 100개
							htmlid = "bs_m4";
							htmldata = new String[] { "5000", "10000", "10000", "20000", "20000", "100" };
							pc._젠도르빈줌갯수 = 100;
						} else if (s.equalsIgnoreCase("5")) {// 빈줌 500개
							htmlid = "bs_m4";
							htmldata = new String[] { "25000", "50000", "50000", "50000", "50000", "500" };
							pc._젠도르빈줌갯수 = 500;
						}
					}
				}
		// 안타라스 레이드 크레이 버프
		else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == L1Npc.크레이) {
			if (s.equalsIgnoreCase("a")) {
				new SkillUse().handleCommands(pc, SkillId.안타라스_레이드_버프_크레이, pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
				htmlid = "grayknight2";
			}
		}
		// 젠도르
		else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == L1Npc.젠도르) {
			if(s.equalsIgnoreCase("a")) {
				if(pc.getLevel() >= 30) {
					if (pc.getInventory().checkItem(ItemId.아데나, 30000)) {
						pc.getInventory().consumeItem(ItemId.아데나, 30000);
						int[] allBuffSkill = {SkillId.인챈트_덱스터리티, SkillId.인챈트_마이티, SkillId.블레스_웨폰, SkillId.리무브_커스, SkillId.파이어_웨폰};			
						SkillUse l1skilluse = new SkillUse();
						for (int i = 0; i < allBuffSkill.length; i++) {
							l1skilluse.handleCommands(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
						}		
					} else {
						pc.sendPackets(new S_SystemMessage("아데나가 부족합니다."));
					}
					htmlid = "";
				}  else {
					pc.sendPackets(new S_SystemMessage("30레벨 이상이 되면 버프를 받을수 있습니다."));
				}
			} else if(s.equalsIgnoreCase("b")) {
				if(pc.getLevel() >= 30) {
					if (pc.getInventory().checkItem(ItemId.아데나, 30000)) {
						pc.getInventory().consumeItem(ItemId.아데나, 30000);
						int[] allBuffSkill = {SkillId.인챈트_덱스터리티, SkillId.인챈트_마이티, SkillId.블레스_웨폰, SkillId.리무브_커스, SkillId.윈드_샷};			
						SkillUse l1skilluse = new SkillUse();
						for (int i = 0; i < allBuffSkill.length; i++) {
							l1skilluse.handleCommands(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
						}		
					} else {
						pc.sendPackets(new S_SystemMessage("아데나가 부족합니다."));
					}
					htmlid = "";
				}  else {
					pc.sendPackets(new S_SystemMessage("30레벨 이상이 되면 버프를 받을수 있습니다."));
				}			
			} else {
				htmlid = "";
			}
		}
		// 일반 퀘스트 / 라이라
		else if (s.equalsIgnoreCase("contract1")) {
			pc.getQuest().set_step(L1Quest.QUEST_LYRA, 1);
			htmlid = "lyraev2";
		} 
		else if (s.equalsIgnoreCase("contract1yes") || // 라이라 Yes
				s.equalsIgnoreCase("contract1no")) { // 라이라 No

			if (s.equalsIgnoreCase("contract1yes")) {
				htmlid = "lyraev5";
			} else if (s.equalsIgnoreCase("contract1no")) {
				pc.getQuest().set_step(L1Quest.QUEST_LYRA, 0);
				htmlid = "lyraev4";
			}
			int totem = 0;
			if (pc.getInventory().checkItem(40131)) {
				totem++;
			}
			if (pc.getInventory().checkItem(40132)) {
				totem++;
			}
			if (pc.getInventory().checkItem(40133)) {
				totem++;
			}
			if (pc.getInventory().checkItem(40134)) {
				totem++;
			}
			if (pc.getInventory().checkItem(40135)) {
				totem++;
			}
			if (totem != 0) {
				materials = new int[totem];
				counts = new int[totem];
				createitem = new int[totem];
				createcount = new int[totem];

				totem = 0;
				if (pc.getInventory().checkItem(40131)) {
					ItemInstance l1iteminstance = pc.getInventory().findItemId(40131);
					int i1 = l1iteminstance.getCount();
					materials[totem] = 40131;
					counts[totem] = i1;
					createitem[totem] = ItemId.아데나;
					createcount[totem] = i1 * 50;
					totem++;
				}
				if (pc.getInventory().checkItem(40132)) {
					ItemInstance l1iteminstance = pc.getInventory().findItemId(40132);
					int i1 = l1iteminstance.getCount();
					materials[totem] = 40132;
					counts[totem] = i1;
					createitem[totem] = ItemId.아데나;
					createcount[totem] = i1 * 100;
					totem++;
				}
				if (pc.getInventory().checkItem(40133)) {
					ItemInstance l1iteminstance = pc.getInventory().findItemId(40133);
					int i1 = l1iteminstance.getCount();
					materials[totem] = 40133;
					counts[totem] = i1;
					createitem[totem] = ItemId.아데나;
					createcount[totem] = i1 * 50;
					totem++;
				}
				if (pc.getInventory().checkItem(40134)) {
					ItemInstance l1iteminstance = pc.getInventory().findItemId(40134);
					int i1 = l1iteminstance.getCount();
					materials[totem] = 40134;
					counts[totem] = i1;
					createitem[totem] = ItemId.아데나;
					createcount[totem] = i1 * 30;
					totem++;
				}
				if (pc.getInventory().checkItem(40135)) {
					ItemInstance l1iteminstance = pc.getInventory().findItemId(40135);
					int i1 = l1iteminstance.getCount();
					materials[totem] = 40135;
					counts[totem] = i1;
					createitem[totem] = ItemId.아데나;
					createcount[totem] = i1 * 200;
					totem++;
				}
			}
		}
		// 판도라, 코르드, 바르심, 메린, 그렌
		else if (s.equalsIgnoreCase("pandora6") || s.equalsIgnoreCase("cold6") || s.equalsIgnoreCase("balsim3")
				|| s.equalsIgnoreCase("mellin3") || s.equalsIgnoreCase("glen3")) {
			htmlid = s;
			int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
			int taxRatesCastle = L1CastleLocation.getCastleTaxRateByNpcId(npcid);
			htmldata = new String[] { String.valueOf(taxRatesCastle) };
		}
		// 타운 마스터(이 마을의 주민에게 등록한다)
		else if (s.equalsIgnoreCase("set")) {
			if (obj instanceof L1NpcInstance) {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				int town_id = L1TownLocation.getTownIdByNpcid(npcid);

				if (town_id >= 1 && town_id <= 10) {
					if (pc.getHomeTownId() == -1) {
						// \f1 새롭게 주민 등록을 행하려면 시간이 걸립니다.시간을 두고 나서 또 등록해 주세요.
						pc.sendPackets(new S_ServerMessage(759));
						htmlid = "";
					} else if (pc.getHomeTownId() > 0) {
						// 이미 등록하고 있다
						if (pc.getHomeTownId() != town_id) {
							L1Town town = TownTable.getInstance().getTownTable(pc.getHomeTownId());
							if (town != null) {
								// 현재, 당신이 주민 등록 하고 있는 장소는%0입니다.
								pc.sendPackets(new S_ServerMessage(758, town.get_name()));
							}
							htmlid = "";
						} else {
							// 있을 수 없어?
							htmlid = "";
						}
					} else if (pc.getHomeTownId() == 0) {
						// 등록
						if (pc.getLevel() < 10) {
							// \f1주민 등록이 생기는 것은 레벨 10이상의 캐릭터입니다.
							pc.sendPackets(new S_ServerMessage(757));
							htmlid = "";
						} else {
							int level = pc.getLevel();
							int cost = level * level * 10;
							if (pc.getInventory().consumeItem(ItemId.아데나, cost)) {
								pc.setHomeTownId(town_id);
								pc.setContribution(0); // 만약을 위해
								pc.save();
							} else {
								// 아데나가 부족합니다.
								pc.sendPackets(new S_ServerMessage(337, "$4"));
							}
							htmlid = "";
						}
					}
				}
			}
		}
		// 타운 마스터(주민 등록을 취소한다)
		else if (s.equalsIgnoreCase("clear")) {
			if (obj instanceof L1NpcInstance) {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				int town_id = L1TownLocation.getTownIdByNpcid(npcid);
				if (town_id > 0) {
					if (pc.getHomeTownId() > 0) {
						if (pc.getHomeTownId() == town_id) {
							pc.setHomeTownId(-1);
							pc.setContribution(0); // 공헌도 클리어
							pc.save();
						} else {
							// \f1당신은 다른 마을의 주민입니다.
							pc.sendPackets(new S_ServerMessage(756));
						}
					}
					htmlid = "";
				}
			}
		}
		// 타운 마스터(마을의 촌장이 누군가를 (듣)묻는다)
		else if (s.equalsIgnoreCase("ask")) {
			if (obj instanceof L1NpcInstance) {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				int town_id = L1TownLocation.getTownIdByNpcid(npcid);

				if (town_id >= 1 && town_id <= 10) {
					L1Town town = TownTable.getInstance().getTownTable(town_id);
					String leader = town.get_leader_name();
					if (leader != null && leader.length() != 0) {
						htmlid = "owner";
						htmldata = new String[] { leader };
					} else {
						htmlid = "noowner";
					}
				}
			}
		}
		// 타운 어드바이저
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70534
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70556
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70572
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70631
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70663
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70761
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70788
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70806
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70830
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70876) {
			// 타운 어드바이저(수입에 관한 보고)
			if (s.equalsIgnoreCase("r")) {
				if (obj instanceof L1NpcInstance) {
				}
			}
			// 타운 어드바이저(세율 변경)
			else if (s.equalsIgnoreCase("t")) {}
			// 타운 어드바이저(보수를 받는다)
			else if (s.equalsIgnoreCase("c")) {}
		}	
		// 루케 인(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71055) {
			// 아이템을 받는다
			if (s.equalsIgnoreCase("0")) {
				final int[] item_ids = { 40701, };
				final int[] item_amounts = { 1, };
				for (int i = 0; i < item_ids.length; i++) {
					ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
							item.getItem().getName()));
				}
				pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 1);
				htmlid = "lukein8";
			} else if (s.equalsIgnoreCase("2")) {
				htmlid = "lukein12";
				pc.getQuest().set_step(L1Quest.QUEST_RESTA, 3);
			}
		}
		// 작은 상자-1번째
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71063) {
			if (s.equalsIgnoreCase("0")) {
				materials = new int[] { 40701 }; // 작은 보물의 지도
				counts = new int[] { 1 };
				createitem = new int[] { 40702 }; // 작은 봉투
				createcount = new int[] { 1 };
				htmlid = "maptbox1";
				pc.getQuest().set_end(L1Quest.QUEST_TBOX1);
				int[] nextbox = { 1, 2, 3 };
				int pid = _random.nextInt(nextbox.length);
				int nb = nextbox[pid];
				if (nb == 1) { // b지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 2);
				} else if (nb == 2) { // c지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 3);
				} else if (nb == 3) { // d지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 4);
				}
			}
		}
		// 작은 상자-2번째
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71064
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71065
				|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71066) {
			if (s.equalsIgnoreCase("0")) {
				materials = new int[] { 40701 }; // 작은 보물의 지도
				counts = new int[] { 1 };
				createitem = new int[] { 40702 }; // 작은 봉투
				createcount = new int[] { 1 };
				htmlid = "maptbox1";
				pc.getQuest().set_end(L1Quest.QUEST_TBOX2);
				int[] nextbox2 = { 1, 2, 3, 4, 5, 6 };
				int pid = _random.nextInt(nextbox2.length);
				int nb2 = nextbox2[pid];
				if (nb2 == 1) { // e지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 5);
				} else if (nb2 == 2) { // f지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 6);
				} else if (nb2 == 3) { // g지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 7);
				} else if (nb2 == 4) { // h지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 8);
				} else if (nb2 == 5) { // i지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 9);
				} else if (nb2 == 6) { // j지점
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 10);
				}
			}
		}
		// 시미즈(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71056) {
			// 아들을 찾는다
			if (s.equalsIgnoreCase("a")) {
				pc.getQuest().set_step(L1Quest.QUEST_SIMIZZ, 1);
				htmlid = "SIMIZZ7";
			} else if (s.equalsIgnoreCase("b")) {
				if (pc.getInventory().checkItem(40661) && pc.getInventory().checkItem(40662)
						&& pc.getInventory().checkItem(40663)) {
					htmlid = "SIMIZZ8";
					pc.getQuest().set_step(L1Quest.QUEST_SIMIZZ, 2);
					materials = new int[] { 40661, 40662, 40663 };
					counts = new int[] { 1, 1, 1 };
					createitem = new int[] { 20044 };
					createcount = new int[] { 1 };
				} else {
					htmlid = "SIMIZZ9";
				}
			} else if (s.equalsIgnoreCase("d")) {
				htmlid = "SIMIZZ12";
				pc.getQuest().set_step(L1Quest.QUEST_SIMIZZ, L1Quest.QUEST_END);
			}
		}
		// 도일(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71057) {
			// 러쉬에 대해 듣는다
			if (s.equalsIgnoreCase("3")) {
				htmlid = "doil4";
			} else if (s.equalsIgnoreCase("6")) {
				htmlid = "doil6";
			} else if (s.equalsIgnoreCase("1")) {
				if (pc.getInventory().checkItem(40714)) {
					htmlid = "doil8";
					materials = new int[] { 40714 };
					counts = new int[] { 1 };
					createitem = new int[] { 40647 };
					createcount = new int[] { 1 };
					pc.getQuest().set_step(L1Quest.QUEST_DOIL, L1Quest.QUEST_END);
				} else {
					htmlid = "doil7";
				}
			}
		}
		// 루디 안(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71059) {
			// 루디 안의 부탁을 받아들인다
			if (s.equalsIgnoreCase("A")) {
				htmlid = "rudian6";
				final int[] item_ids = { 40700 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
							item.getItem().getName()));
				}
				pc.getQuest().set_step(L1Quest.QUEST_RUDIAN, 1);
			} else if (s.equalsIgnoreCase("B")) {
				if (pc.getInventory().checkItem(40710)) {
					htmlid = "rudian8";
					materials = new int[] { 40700, 40710 };
					counts = new int[] { 1, 1 };
					createitem = new int[] { 40647 };
					createcount = new int[] { 1 };
					pc.getQuest().set_step(L1Quest.QUEST_RUDIAN, L1Quest.QUEST_END);
				} else {
					htmlid = "rudian9";
				}
			}
		}
		// 레스타(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71060) {
			// 동료들에 대해
			if (s.equalsIgnoreCase("A")) {
				if (pc.getQuest().get_step(L1Quest.QUEST_RUDIAN) == L1Quest.QUEST_END) {
					htmlid = "resta6";
				} else {
					htmlid = "resta4";
				}
			} else if (s.equalsIgnoreCase("B")) {
				htmlid = "resta10";
				pc.getQuest().set_step(L1Quest.QUEST_RESTA, 2);
			}
		}
		// 카좀스(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71061) {
			// 지도를 조합해 주세요
			if (s.equalsIgnoreCase("A")) {
				if (pc.getInventory().checkItem(40647, 3)) {
					htmlid = "cadmus6";
					pc.getInventory().consumeItem(40647, 3);
					pc.getQuest().set_step(L1Quest.QUEST_CADMUS, 2);
				} else {
					htmlid = "cadmus5";
					pc.getQuest().set_step(L1Quest.QUEST_CADMUS, 1);
				}
			}
		}
		// 카밋트(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71062) {
			// 할아버지가 기다리고 있으니 함께 오세요
			if (s.equalsIgnoreCase("start")) {
				htmlid = "kamit2";
				final int[] item_ids = { 40711 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
							item.getItem().getName()));
					pc.getQuest().set_step(L1Quest.QUEST_CADMUS, 3);
				}
			}
		}
		// 카미라(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71036) {
			if (s.equalsIgnoreCase("a")) {
				htmlid = "kamyla7";
				pc.getQuest().set_step(L1Quest.QUEST_KAMYLA, 1);
			} else if (s.equalsIgnoreCase("c")) {
				htmlid = "kamyla10";
				pc.getInventory().consumeItem(40644, 1);
				pc.getQuest().set_step(L1Quest.QUEST_KAMYLA, 3);
			} else if (s.equalsIgnoreCase("e")) {
				htmlid = "kamyla13";
				pc.getInventory().consumeItem(40630, 1);
				pc.getQuest().set_step(L1Quest.QUEST_KAMYLA, 4);
			}
			//추가작업
		} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4204000) { // 달장퀘스트 로빈후드
			if (s.equals("A")) { /*robinhood1~7*/
				if (pc.getInventory().checkItem(40028)) { /*사과주스 체크*/
					pc.getInventory().consumeItem(40028,1); /*사과주스 소비*/
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 1); /*1단계 완료*/
					htmlid = "robinhood4";
				} else {
					htmlid = "robinhood19";    
				}
			} else if (s.equals("B")) { /*robinhood8*/
				final int[] item_ids = { 41346, 41348 };
				final int[] item_amounts = { 1, 1, };
				ItemInstance item = null;
				for (int i = 0; i < item_ids.length; i++) {
					item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					pc.sendPackets(new S_ServerMessage(403, item.getLogName()));
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 2);
					htmlid = "robinhood13";
				}
			} else if (s.equals("C")) { /*robinhood9*/
				if (pc.getInventory().checkItem(41346) 
						&& pc.getInventory().checkItem(41351)
						&& pc.getInventory().checkItem(41352, 4)
						&& pc.getInventory().checkItem(40618, 30)
						&& pc.getInventory().checkItem(40643, 30)
						&& pc.getInventory().checkItem(40645, 30)
						&& pc.getInventory().checkItem(40651, 30)
						&& pc.getInventory().checkItem(40676, 30)) {
					pc.getInventory().consumeItem(41346, 1); /*메모장, 정기, 유뿔, 불, 물, 바람, 대지 어둠숨결*/
					pc.getInventory().consumeItem(41351, 1);
					pc.getInventory().consumeItem(41352, 4);
					pc.getInventory().consumeItem(40651, 30);
					pc.getInventory().consumeItem(40643, 30);
					pc.getInventory().consumeItem(40645, 30);
					pc.getInventory().consumeItem(40618, 30);
					pc.getInventory().consumeItem(40676, 30);
					final int[] item_ids = { 41350, 41347 };
					final int[] item_amounts = { 1, 1, };					
					ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						pc.sendPackets(new S_ServerMessage(403, item.getLogName()));
					}
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 7); /*7단계 완료*/
					htmlid = "robinhood10"; /*나머지 재료를 찾아오게..*/
				} else {
					htmlid = "robinhood15"; /*달빛정기, 유뿔 가져왔는가*/ 
				}
			} else if (s.equals("E")) { /*robinhood11*/
				if (pc.getInventory().checkItem(41350) 
						&& pc.getInventory().checkItem(41347)
						&& pc.getInventory().checkItem(40491, 30) 
						&& pc.getInventory().checkItem(40495, 40)
						&& pc.getInventory().checkItem(100) 
						&& pc.getInventory().checkItem(40509, 12)
						&& pc.getInventory().checkItem(40052) 
						&& pc.getInventory().checkItem(40053)
						&& pc.getInventory().checkItem(40054) 
						&& pc.getInventory().checkItem(40055)) { 
					pc.getInventory().consumeItem(41350, 1); /*반지, 메모지, 그리폰깃털, 미스릴실, 오리뿔, 오판, 최고급보석1개씩*/     
					pc.getInventory().consumeItem(41347, 1);
					pc.getInventory().consumeItem(40491, 30);
					pc.getInventory().consumeItem(40495, 40);
					pc.getInventory().consumeItem(100, 1);
					pc.getInventory().consumeItem(40509, 12);
					pc.getInventory().consumeItem(40052, 1);
					pc.getInventory().consumeItem(40053, 1);
					pc.getInventory().consumeItem(40054, 1);
					pc.getInventory().consumeItem(40055, 1);
					final int[] item_ids = { 205 };
					final int[] item_amounts = { 1 };					
					ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						pc.sendPackets(new S_ServerMessage(143, 
								((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName()));
					}
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 0); /*퀘스트 리셋*/
					htmlid = "robinhood12"; /*완성이야*/
				} else {
					htmlid = "robinhood17"; /*재료가 부족한걸*/
				}
			}
		} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4210000) {
			if (s.equals("A")) { /*zybril1 ~ zybril6*/
				if (pc.getInventory().checkItem(41348)) { /*소개장*/
					pc.getInventory().consumeItem(41348, 1);
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 3); /*3단계 완료*/
					htmlid = "zybril13"; /*아 그 활쟁이*/
				} else {
					htmlid = "zybril11"; /*편지는 어디에?*/
				}
			} else if (s.equals("B")) { /*zybril7*/
				if (pc.getInventory().checkItem(40048, 10) && pc.getInventory().checkItem(40049, 10)
						&& pc.getInventory().checkItem(40050, 10) && pc.getInventory().checkItem(40051, 10)) { 
					pc.getInventory().consumeItem(40048, 10); /*고다, 고루, 고사, 고에*/
					pc.getInventory().consumeItem(40049, 10);
					pc.getInventory().consumeItem(40050, 10);
					pc.getInventory().consumeItem(40051, 10);
					final int[] item_ids = { 41353 };
					final int[] item_amounts = { 1 };
					@SuppressWarnings("unused")
					ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						pc.sendPackets(new S_SystemMessage("에바의 단검을 얻었습니다."));
					}
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 4); /*4단계 완료*/
					htmlid = "zybril12"; /*기부금을 받을게요*/
				} else {
					htmlid = "";
				}
			} else if (s.equals("C")) { /*zybril8*/
				if (pc.getInventory().checkItem(40514, 10) && pc.getInventory().checkItem(41353, 1)) { 
					pc.getInventory().consumeItem(40514, 10); /*정령의 눈물, 에바의 단검*/
					pc.getInventory().consumeItem(41353, 1);
					final int[] item_ids = { 41354 };
					final int[] item_amounts = { 1 };
					@SuppressWarnings("unused")
					ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]); 
						pc.sendPackets(new S_SystemMessage("신성한 에바의 물을 얻었습니다."));
					}
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 5); /*5단계 완료*/
					htmlid = "zybril9"; /*고마워요 한가지부탁더*/
				} else {
					htmlid = "zybril13"; /*정령의눈물이 필요합니다..*/
				}
			} else if (s.equals("D")) { /*zybril18*/
				if (pc.getInventory().checkItem(41349)) { /*사엘의 반지*/
					pc.getInventory().consumeItem(41349, 1);
					final int[] item_ids = { 41351 };
					final int[] item_amounts = { 1 };
					@SuppressWarnings("unused")
					ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]); 
						pc.sendPackets(new S_SystemMessage("달빛의 정기를 얻었습니다."));
					}
					pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 6); /*6단계 완료*/
					htmlid = "zybril10"; /*달빛의 정기를 받으세요*/
				} else {
					htmlid = "zybril14"; /*어떻게 믿죠?*/
				}
			}  
			//추가작업
		}else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71179) {
			if (s.equalsIgnoreCase("A")) { // 복원된 고대의 목걸이	
				Random random = new Random();
				if (pc.getInventory().checkItem(49035, 1) && pc.getInventory().checkItem(49036, 1)
						&& pc.getInventory().checkItem(49037, 1) && pc.getInventory().checkItem(41139, 1)) { 																								// 확인
					if (random.nextInt(10) > 7) // 30%의 확률로 성공
					{
						materials = new int[] { 49035, 49036, 49037, 41139 };
						counts = new int[] { 1, 1, 1, 1 };
						createitem = new int[] { 41140 }; // 복원된 고대의 목걸이
						createcount = new int[] { 1 };
					} else { // 실패의 경우 아이템만 사라짐
						pc.getInventory().consumeItem(49035, 1);
						pc.getInventory().consumeItem(49036, 1);
						pc.getInventory().consumeItem(49037, 1);
						pc.getInventory().consumeItem(41139, 1);
						pc.sendPackets(new S_SystemMessage("복원된 고대의 목걸이의 제작에 실패했습니다."));
					}
				} else { // 재료가 부족한 경우
					pc.sendPackets(new S_SystemMessage("제작에 필요한 재료가 부족합니다."));
				}
			} else if (s.equalsIgnoreCase("B")) {// 빛나는 고대의 목걸이 제작을 부탁한다.		
				Random random = new Random();
				if (pc.getInventory().checkItem(49038, 1) && pc.getInventory().checkItem(41140, 1)) { // 다이아몬드와 복원된 목걸이
					if (random.nextInt(10) > 7) // 30%의 확률로 성공시
					{
						materials = new int[] { 49038, 41140 };
						counts = new int[] { 1, 1 };
						createitem = new int[] { 20422 };
						createcount = new int[] { 1 };
					} else { // 실패시 아이템만 증발
						pc.getInventory().consumeItem(49038, 1);
						pc.getInventory().consumeItem(41140, 1);
						pc.sendPackets(new S_SystemMessage("빛나는 고대의 목걸이의 제작에 실패했습니다"));
					}
				} else { // 재료가 부족한 경우
					pc.sendPackets(new S_SystemMessage("제작에 필요한 재료가 부족합니다."));
				}
			}
		}	// 프랑코(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71089) {
			// 카미라에 당신의 결백을 증명합시다
			if (s.equalsIgnoreCase("a")) {
				htmlid = "francu10";
				final int[] item_ids = { 40644 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
							item.getItem().getName()));
					pc.getQuest().set_step(L1Quest.QUEST_KAMYLA, 2);
				}
			}
		}
		// 리자드맨 장로(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71074) {
			// 그 전사는 지금 어디등에 에 있습니까?
			if (s.equalsIgnoreCase("A")) {
				htmlid = "lelder5";
				pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 1);
				// 보물을 되찾아 옵니다
			} else if (s.equalsIgnoreCase("B")) {
				htmlid = "lelder10";
				pc.getInventory().consumeItem(40633, 1);
				pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 3);
			}
		}
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4220012) {
			if (s.equalsIgnoreCase("0")) {
				if (pc.getInventory().checkItem(41159, 45)) {
					pc.getInventory().consumeItem(41159, 45);
					pc.getInventory().storeItem(437023, 1);
					pc.sendPackets(new S_ServerMessage(403, "$8538"));
				} else {
					htmlid = "suschef5";
				}
			} else if (s.equalsIgnoreCase("1")) {
				if (pc.getInventory().checkItem(437027, 1) || pc.getInventory().checkItem(437028, 1)
						|| pc.getInventory().checkItem(437029, 1) ||pc.getInventory().checkItem(437030, 1)) {
					if (pc.getInventory().checkItem(437027, 1)) {
						pc.getInventory().consumeItem(437027, 1);
						pc.getInventory().storeItem(437031, 1);
						pc.sendPackets(new S_ServerMessage(403, "$8539"));
						htmlid = "fortunea0";
					}
					if (pc.getInventory().checkItem(437028, 1)) {
						pc.getInventory().consumeItem(437028, 1);
						pc.getInventory().storeItem(437032, 1);
						pc.sendPackets(new S_ServerMessage(403, "$8539"));
						int html = _random.nextInt(10);
						htmlid = "fortuneb" + html;
					}
					if (pc.getInventory().checkItem(437029, 1)) {
						pc.getInventory().consumeItem(437029, 1);
						pc.getInventory().storeItem(437033, 1);
						pc.sendPackets(new S_ServerMessage(403, "$8539"));
						int html = _random.nextInt(30);
						htmlid = "fortunec" + html;
					}
					if (pc.getInventory().checkItem(437030, 1)) {
						pc.getInventory().consumeItem(437030, 1);
						pc.getInventory().storeItem(437034, 1);
						pc.sendPackets(new S_ServerMessage(403, "$8539"));
						htmlid = "forthned0";
					}
				} else if (checkmemo(pc)) {
						htmlid = "";
				} else {
					htmlid = "suschef4";
				}
			}
		}
		// 지친 리자드맨 전사(해적섬)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71075) {
			// 리자드만의 보고서
			if (s.equalsIgnoreCase("start")) {
				htmlid = "llizard2";
				final int[] item_ids = { 40633 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
							item.getItem().getName()));
					pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 2);
				}
			} else {
			}
		}
		// 점성술사 케프리샤
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80079) {
			// 케프리샤와 영혼의 계약을 맺는다
			if (s.equalsIgnoreCase("0")) {
				if (!pc.getInventory().checkItem(41312)) { // 점성술사의 항아리
					ItemInstance item = pc.getInventory().storeItem(41312, 1);
					if (item != null) {
						pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
								item.getItem().getName())); // \f1%0이%1를 주었습니다.
						pc.getQuest().set_step(L1Quest.QUEST_KEPLISHA, L1Quest.QUEST_END);
					}
					htmlid = "keplisha7";
				}
			}
			// 원조금을 내 운세를 본다
			else if (s.equalsIgnoreCase("1")) {
				if (!pc.getInventory().checkItem(41314)) { // 점성술사의 부적
					if (pc.getInventory().checkItem(ItemId.아데나, 1000)) {
						materials = new int[] { ItemId.아데나, 41313 }; // 아데나, 점성술사의 구슬
						counts = new int[] { 1000, 1 };
						createitem = new int[] { 41314 }; // 점성술사의 부적
						createcount = new int[] { 1 };
						int htmlA = _random.nextInt(3) + 1;
						int htmlB = _random.nextInt(100) + 1;
						switch (htmlA) {
						case 1:
							htmlid = "horosa" + htmlB; // horosa1 ~ horosa100
							break;
						case 2:
							htmlid = "horosb" + htmlB; // horosb1 ~ horosb100
							break;
						case 3:
							htmlid = "horosc" + htmlB; // horosc1 ~ horosc100
							break;
						default:
							break;
						}
					} else {
						htmlid = "keplisha8";
					}
				}
			}
			// 케프리샤로부터 축복을 받는다
			else if (s.equalsIgnoreCase("2")) {
				if (pc.getCurrentSpriteId() != pc.getClassId()) {
					htmlid = "keplisha9";
				} else {
					if (pc.getInventory().checkItem(41314)) { // 점성술사의 부적
						pc.getInventory().consumeItem(41314, 1); // 점성술사의 부적
						int html = _random.nextInt(9) + 1;
						int PolyId = 6180 + _random.nextInt(64);
						poly(client, PolyId);
						switch (html) {
						case 1:
							htmlid = "horomon11";
							break;
						case 2:
							htmlid = "horomon12";
							break;
						case 3:
							htmlid = "horomon13";
							break;
						case 4:
							htmlid = "horomon21";
							break;
						case 5:
							htmlid = "horomon22";
							break;
						case 6:
							htmlid = "horomon23";
							break;
						case 7:
							htmlid = "horomon31";
							break;
						case 8:
							htmlid = "horomon32";
							break;
						case 9:
							htmlid = "horomon33";
							break;
						default:
							break;
						}
					}
				}
			}
			// 항아리를 나누어 계약을 파기한다
			else if (s.equalsIgnoreCase("3")) {
				if (pc.getInventory().checkItem(41312)) { // 점성술사의 항아리
					pc.getInventory().consumeItem(41312, 1);
					htmlid = "";
				}
				if (pc.getInventory().checkItem(41313)) { // 점성술사의 구슬
					pc.getInventory().consumeItem(41313, 1);
					htmlid = "";
				}
				if (pc.getInventory().checkItem(41314)) { // 점성술사의 부적
					pc.getInventory().consumeItem(41314, 1);
					htmlid = "";
				}
			}
		}
		// 낚시꼬마(IN)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80082) {
			// 「길고 무거운 낚싯대」
			if (s.equalsIgnoreCase("a")) {
				if (pc.getInventory().checkItem(ItemId.아데나, 1000)) {
					L1PolyMorph.undoPoly(pc);
					pc.teleport(32811, 32808, (short) 5124, pc.getHeading(), 169, true, false);
				} 
				else {
					htmlid = "fk_in_1";
				}
				// 「짧고 가벼운 낚싯대」
			}
		}
		// 낚시꼬마(OUT)
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80083) {
			// 「낚시를 멈추어 밖에 나온다」
			if (s.equalsIgnoreCase("a")) {
				pc.teleport(32608, 32773, (short) 4, pc.getHeading(), 169, true, false);
			}
		}
		// 수상한 오크 상인 파룸
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80084) {
			// 「자원 리스트를 받는다」
			if (s.equalsIgnoreCase("q")) {
				if (pc.getInventory().checkItem(41356, 1)) {
					htmlid = "rparum4";
				} else {
					ItemInstance item = pc.getInventory().storeItem(41356, 1);
					if (item != null) {
						pc.sendPackets(new S_ServerMessage(143, ((L1NpcInstance) obj).getNpcTemplate().get_name(),
								item.getItem().getName())); // \f1%0%s 당신에게 %1%o 주었습니다.
					}
					htmlid = "rparum3";
				}
			}
		}
		// 에덴 기마 단원
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80105) {
			// 「새로운 힘을 관있다」
			if (s.equalsIgnoreCase("c")) {
				if (pc.isCrown()) {
					if (pc.getInventory().checkItem(20383, 1)) {
						if (pc.getInventory().checkItem(ItemId.아데나, 100000)) {
							ItemInstance item = pc.getInventory().findItemId(20383);
							if (item != null && item.getChargeCount() != 50) {
								item.setChargeCount(50);
								pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
								pc.getInventory().consumeItem(ItemId.아데나, 100000);
								htmlid = "";
							}
						} else {
							pc.sendPackets(new S_ServerMessage(337, "$4")); // 아데나가 부족합니다.
						}
					}
				}
			}
		} 
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid) {
			if (s.equals("status")) {
				pc.sendPackets(new S_DogFightBoard(1));
			}
		} 
		else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 81181) {
			if (s.equals("status")) {
				pc.sendPackets(new S_RaceBoard(1, pc.isGm()));
			}
		} else {
			int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
			ActionListener listener = null;
			listener = ActionListenerLoader.getInstance().findListener(npcid, s);
			if (listener != null) {
				htmlid = listener.to_action(pc, obj); 
				DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_map_id(pc.getMapId());
				if (dtInfo != null) {
					pc.send_dungeon_progress(dtInfo); 

				}
			}
		}	
		if (htmlid != null && htmlid.equalsIgnoreCase("colos2")) {
			htmldata = makeUbInfoStrings(((L1NpcInstance) obj).getNpcTemplate().get_npcId());
		}
		if (createitem != null) { // 아이템 정제
			boolean isCreate = true;
			for (int j = 0; j < materials.length; j++) {
				if (!pc.getInventory().checkItemNotEquipped(materials[j], counts[j])) {
					L1Item temp = ItemTable.getInstance().getTemplate(materials[j]);
					pc.sendPackets(new S_ServerMessage(337, temp.getName())); // \f1%0이 부족합니다.
					isCreate = false;
				}
			}
			if (isCreate) {
				// 용량과 중량의 계산
				int create_count = 0; // 아이템의 개수(전만물은 1개)
				int create_weight = 0;
				for (int k = 0; k < createitem.length; k++) {
					L1Item temp = ItemTable.getInstance().getTemplate(createitem[k]);
					if (temp.isStackable()) {
						if (!pc.getInventory().checkItem(createitem[k])) {
							create_count += 1;
						}
					} else {
						create_count += createcount[k];
					}
					create_weight += temp.getWeight() * createcount[k] / 1000;
				}
				// 용량 확인
				if (pc.getInventory().getSize() + create_count > 180) {
					pc.sendPackets(new S_ServerMessage(263)); // \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
					return;
				}
				// 중량 확인
				if (pc.getMaxWeight() < pc.getInventory().getWeight() + create_weight) {
					pc.sendPackets(new S_ServerMessage(82)); // 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
					return;
				}
				// 재료 소비
				for (int j = 0; j < materials.length; j++) {
					pc.getInventory().consumeItem(materials[j], counts[j]);
				}
				ItemInstance item = null;
				for (int k = 0; k < createitem.length; k++) {
					 item = pc.getInventory().storeItem(createitem[k], createcount[k]);
					if (item != null) {
						String itemName = ItemTable.getInstance().getTemplate(createitem[k]).getName();
						String createrName = "";
						if (obj instanceof L1NpcInstance) {
							createrName = ((L1NpcInstance) obj).getNpcTemplate().get_name();
						}
						if (createcount[k] > 1) {
							pc.sendPackets(
									new S_ServerMessage(143, createrName, itemName + " (" + createcount[k] + ")")); // \f1%0이%1를
							// 주었습니다.
						} else {
							pc.sendPackets(new S_ServerMessage(143, createrName, itemName)); // \f1%0이%1를 주었습니다.
						}
					}
				}
				if (success_htmlid != null) { // html 지정이 있는 경우는 표시
					pc.sendPackets(new S_NPCTalkReturn(objid, success_htmlid, htmldata));
				}
			} else { // 정제 실패
				if (failure_htmlid != null) { // html 지정이 있는 경우는 표시
					pc.sendPackets(new S_NPCTalkReturn(objid, failure_htmlid, htmldata));
				}
			}
		}
		if (htmlid != null) { // html 지정이 있는 경우는 표시
			pc.sendPackets(new S_NPCTalkReturn(objid, htmlid, htmldata));
		}
	}
	
	private boolean useJoeTeleportBook1(L1PcInstance pc, int itemId, String s) {   //조우의 이동 기억책
		boolean isUseItem = false;
		
		ItemInstance item = pc.getInventory().findItemId(52063);               
		
		if(CommonUtil.teleporCheck(pc, item)) {
			pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false)); 
			return false;
		}
		
		if (s.equals("tel_001")) { //기란마을
			if(pc.getMapId()== 70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
		//	if (pc.getHuntCount() != 0) {  // 수배확인 
				pc.teleport(33443 , 32800 , (short) 4, pc.getHeading(), 169, true, false); //기란마을
				isUseItem = true;
		//	} else {
		//		pc.sendPackets(new S_SystemMessage("\\fY수배시 이동 가능 합니다."));
		//	}
		} else if (s.equals("tel_002")) {   //아덴 성당
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
		//	if (pc.getHuntCount() != 0 && pc.getInventory().checkItem(40308, 10000)) {   //수배 및 아데나 확인 
				pc.teleport(33950 , 33364 , (short) 4, pc.getHeading(), 169, true, false); //아덴 성당
				isUseItem = true;
		//	} else {
		//		pc.sendPackets(new S_SystemMessage("\\fY수배 및 1만 아데나 이동 가능 합니다."));
		//	}
		} else if (s.equals("tel_003")) {  //말하는섬
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
	//	  if (pc.getInventory().checkItem(40308, 10000)) {   //아데나 확인 
			  pc.teleport(32573 , 32944 , (short) 0, pc.getHeading(), 169, true, false); // 말하는섬
			  isUseItem = true;
		//  } else {
		//	pc.sendPackets(new S_SystemMessage("아데나가 부족하여 이동이 불가능 합니다."));
		 // }
		}  else if (s.equals("tel_004")) {  //켄트성 5시 
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33079 , 32769 , (short) 4, pc.getHeading(), 169, true, false); //켄트성 5시 
			isUseItem = true;
		} else if (s.equals("tel_005")) {     //켄트성7시
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33153 , 32831 , (short) 4, pc.getHeading(), 169, true, false); //켄트성7시
			isUseItem = true;
			
		} else if (s.equals("tel_006")) {  //욕망의 동굴 
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32757 ,33461 , (short) 4, pc.getHeading(), 169, true, false); //욕망의 동굴 
			
		} else if (s.equals("tel_007")) { //그림자신전
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(34265 , 32190 , (short) 4, pc.getHeading(), 169, true, false); // //그림자신전
			isUseItem = true;
		}else if (s.equals("tel_008")) {     //황혼의산맥
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(34253 , 33453 , (short) 4, pc.getHeading(), 169, true, false); //황혼의산맥
			isUseItem = true;
		} else if (s.equals("tel_009")) {   //화룡의 둥지 입구
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				isUseItem = false;
			}
			pc.teleport(33568 , 32408 , (short) 4, pc.getHeading(), 169, true, false); //화룡의 둥지 입구
			isUseItem = true;
		} else if (s.equals("tel_010")) {     //화룡의 둥지 중앙
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				isUseItem = false;
			}
			// pc.sendPackets(new S_SystemMessage("\\fY지금은 이용할 수 없는 컨텐츠 입니다."));
			pc.teleport(33669 , 32334 , (short) 4, pc.getHeading(), 169, true, false); //화룡의 둥지 중앙
																										
			isUseItem = true;
		} else if (s.equals("tel_011")) {      // 용계 삼거리
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				isUseItem = false;
			}
			// pc.sendPackets(new S_SystemMessage("\\fY지금은 이용할 수 없는 컨텐츠 입니다."));
			pc.teleport(33335 , 32449 , (short) 4, pc.getHeading(), 169, true, false);  // 용계 삼거리
																										// 거인의무덤
			isUseItem = true;
		} else if (s.equals("tel_012")) {   //용계 작뼈
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			// pc.sendPackets(new S_SystemMessage("\\fY지금은 이용할 수 없는 컨텐츠 입니다."));
			pc.teleport(33257 , 32412 , (short) 4, pc.getHeading(), 169, true, false); //용계 작뼈																										
			isUseItem = true;
		} else if (s.equals("tel_013")) {  //용뼈중앙
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33384 , 32350 , (short) 4, pc.getHeading(), 169, true, false);  //용뼈중앙																									
			isUseItem = true;
			
		} else if (s.equals("tel_014")) {  //용던1번
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33349 , 32353 , (short) 4, pc.getHeading(), 169, true, false); //용던1번
			isUseItem = true;
			
		} else if (s.equals("tel_015")) {  //용던2번
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33394 , 32328 , (short) 4, pc.getHeading(), 169, true, false); //용던2번
			isUseItem = true;
			
		} else if (s.equals("tel_016")) {  //용던3번
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33366 , 32383 , (short) 4, pc.getHeading(), 169, true, false); //용던3번
			isUseItem = true;
		} else if (s.equals("tel_017")) {    //용던4번
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33412 ,32413 , (short) 4, pc.getHeading(), 169, true, false); //용던4번
			isUseItem = true;
			
		} else if (s.equals("tel_018")) {  //몽환의섬
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33978 , 32927 , (short) 4, pc.getHeading(), 169, true, false); //몽환의섬
			isUseItem = true;
			
		} else if (s.equals("tel_019")) {  //에바의 왕국
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(33627 , 33508 , (short) 4, pc.getHeading(), 169, true, false);  //에바의 왕국
			isUseItem = true;
		} else if (s.equals("tel_020")) {   //수련던전3층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32807 , 32812 , (short) 27, pc.getHeading(), 169, true, false); //수련던전3층
			isUseItem = true;
		} else if (s.equals("tel_021")) {     //수련던전4층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32799 , 32798 , (short) 28, pc.getHeading(), 169, true, false); //수련던전4층
			isUseItem = true;
			
		} else if (s.equals("tel_022")) {  //글루디오 던전3층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32799 , 32755 , (short) 9, pc.getHeading(), 169, true, false); //글루디오 던전3층
			isUseItem = true;
			
		} else if (s.equals("tel_023")) {         //글루디오 던전5층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32728 , 32724 , (short) 11, pc.getHeading(), 169, true, false);  //글루디오 던전5층
			isUseItem = true;
		} else if (s.equals("tel_024")) {         //침공로1층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32760 , 32822  , (short) 307 , pc.getHeading(), 169, true, false);  //침공로1층
			isUseItem = true;
		} else if (s.equals("tel_025")) {              //침공로2층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32750 , 32825 , (short) 308 , pc.getHeading(), 169, true, false); //침공로2층
			isUseItem = true;
		} else if (s.equals("tel_026")) {          //침공로3층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32754 , 32838  , (short) 309 , pc.getHeading(), 169, true, false);   //침공로3층
			isUseItem = true;
			
		} else if (s.equals("tel_027")) {         //상아탑입구
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(34041 , 32155  , (short) 4 , pc.getHeading(), 169, true, false);//상아탑입구
			isUseItem = true;
		} else if (s.equals("tel_028")) {      //상아탑5층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32771 , 32805  , (short) 79 , pc.getHeading(), 169, true, false); //상아탑5층
			isUseItem = true;
			
		} else if (s.equals("tel_029")) {     //상아탑8층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32736 , 32799  , (short) 82 , pc.getHeading(), 169, true, false);  //상아탑8층
			isUseItem = true;
			
		} else if (s.equals("tel_030")) {         //용던3층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32709 , 32818  , (short) 32 , pc.getHeading(), 169, true, false);  //용던3층
			isUseItem = true;
		
		}  else if (s.equals("tel_031")) {           //용던5층
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			pc.teleport(32746 , 32802  , (short) 35 , pc.getHeading(), 169, true, false); //용던5층
			isUseItem = true;
			
		} else if (s.equals("tel_032")) {         //정령의 무덤
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			if (pc.getInventory().checkItem(40308, 100000)) { // 아데나 확인
				pc.getInventory().consumeItem(40308, 100000);
				pc.sendPackets(new S_SystemMessage("100,000 아데나를 소모하였습니다."));
				pc.teleport(32924, 32801, (short) 430, pc.getHeading(), 169, true, false);          //정령의 무덤
				isUseItem = true;
			} else {
				pc.sendPackets(new S_SystemMessage("100,000 아데나가 부족하여 이동이 불가능 합니다."));
			}
				  
		} else if (s.equals("tel_033")) {           //지저 호수
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			if (pc.getInventory().checkItem(40308, 100000)) { // 아데나 확인
				pc.getInventory().consumeItem(40308, 100000);
				pc.sendPackets(new S_SystemMessage("100,000 아데나를 소모하였습니다."));
				pc.teleport(32924, 32801, (short) 430, pc.getHeading(), 169, true, false);           //지저 호수
				isUseItem = true;
			} else {
				pc.sendPackets(new S_SystemMessage("100,000 아데나가 부족하여 이동이 불가능 합니다."));
			}
			
		} else if (s.equals("tel_034")) { // 대공동 은둔자 지역
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			if (pc.getInventory().checkItem(40308, 100000)) { // 아데나 확인
				pc.getInventory().consumeItem(40308, 100000);
				pc.sendPackets(new S_SystemMessage("100,000 아데나를 소모하였습니다."));
				pc.teleport(32598, 32922, (short) 400, pc.getHeading(), 169, true, false); // 대공동 은둔자 지역
				isUseItem = true;
			} else {
				pc.sendPackets(new S_SystemMessage("100,000 아데나가 부족하여 이동이 불가능 합니다."));
			}
			
		} else if (s.equals("tel_035")) { //마족 
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			if (pc.getInventory().checkItem(40308, 100000)) { // 아데나 확인
				pc.getInventory().consumeItem(40308, 100000);
				pc.sendPackets(new S_SystemMessage("100,000 아데나를 소모하였습니다."));
				pc.teleport(32929, 32994, (short) 410, pc.getHeading(), 169, true, false); //마족
				isUseItem = true;
			} else {
				pc.sendPackets(new S_SystemMessage("100,000 아데나가 부족하여 이동이 불가능 합니다."));
			}
			
		} else if (s.equals("tel_036")) { //라스타바드 정문
			if(pc.getMapId()==70){
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(647));
				return isUseItem;
			}
			if (pc.getInventory().checkItem(40308, 100000)) { // 아데나 확인
				pc.getInventory().consumeItem(40308, 100000);
				pc.sendPackets(new S_SystemMessage("100,000 아데나를 소모하였습니다."));
				pc.teleport(32674, 32806, (short) 450, pc.getHeading(), 169, true, false);  //라스타바드 정문
				isUseItem = true;
			} else {
				pc.sendPackets(new S_SystemMessage("100,000 아데나가 부족하여 이동이 불가능 합니다."));
			}
		}
		if (isUseItem) {
			pc.getInventory().removeItem(item, 1);
		}
		return isUseItem;
	}
	
	private boolean checkmemo(L1PcInstance pc) {
		if (pc.getInventory().checkItem(437031)) {
			pc.getInventory().consumeItem(437031, 1);
			new SkillUse().handleCommands(pc, SkillId.운세_버프_A, pc.getId(), pc.getX(), pc.getY(), null, 0,
					SkillUse.TYPE_SPELLSC);
			return true;
		} else if (pc.getInventory().checkItem(437032)) {
			pc.getInventory().consumeItem(437032, 1);
			new SkillUse().handleCommands(pc, SkillId.운세_버프_B, pc.getId(), pc.getX(), pc.getY(), null, 0,
					SkillUse.TYPE_SPELLSC);
			return true;
		} else if (pc.getInventory().checkItem(437033)) {
			pc.getInventory().consumeItem(437033, 1);
			new SkillUse().handleCommands(pc, SkillId.운세_버프_C, pc.getId(), pc.getX(), pc.getY(), null, 0,
					SkillUse.TYPE_SPELLSC);
			return true;
		} else if (pc.getInventory().checkItem(437034)) {
			pc.getInventory().consumeItem(437034, 1);
			new SkillUse().handleCommands(pc, SkillId.운세_버프_D, pc.getId(), pc.getX(), pc.getY(), null, 0,
					SkillUse.TYPE_SPELLSC);
			return true;
		}
		return false;
	}
	// 자동 물약 관련 
	private boolean auto_potion(L1PcInstance pc, String code) {	
		boolean refresh = false;

		ArrayList<Integer> potions = new ArrayList<Integer>(); 
		for (ItemInstance item : pc.getInventory().getItems()) {
			for (Integer id : Config.ServerInfo.AUTO_POTION_LIST) {
				if (item.getItemId() == id) {
					potions.add(id);
				}
			}
		}
		if (code.equalsIgnoreCase("potion_onoff")) {
			if (pc._AutoPotionController != null) {
				pc.EndAutoPotionController();
				pc.sendPackets("자동 물약을 종료합니다.");
			} else {
				pc.StartAutoPotionController();
				pc.sendPackets("자동 물약을 사용합니다.");
			}
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_alarm_onoff")) {
			if (pc.isAlarmPotion()) {
				pc.setAlarmPotion(false);
				pc.sendPackets("물약 갯수 50개 미만 알람 설정을 끕니다.");
			} else {
				pc.sendPackets("물약 갯수 50개 미만 알람 설정을 켭니다.");
				pc.setAlarmPotion(true);
			}
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_10")) {
			pc.setAutoPotion_Percent(10);
			pc.sendPackets("자동 물약 사용의 퍼센트를 10%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_20")) {
			pc.setAutoPotion_Percent(20);
			pc.sendPackets("자동 물약 사용의 퍼센트를 20%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_30")) {
			pc.setAutoPotion_Percent(30);
			pc.sendPackets("자동 물약 사용의 퍼센트를 30%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_40")) {
			pc.setAutoPotion_Percent(40);
			pc.sendPackets("자동 물약 사용의 퍼센트를 40%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_50")) {
			pc.setAutoPotion_Percent(50);
			pc.sendPackets("자동 물약 사용의 퍼센트를 50%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_60")) {
			pc.setAutoPotion_Percent(60);
			pc.sendPackets("자동 물약 사용의 퍼센트를 60%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_70")) {
			pc.setAutoPotion_Percent(70);
			pc.sendPackets("자동 물약 사용의 퍼센트를 70%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_80")) {
			pc.setAutoPotion_Percent(80);
			pc.sendPackets("자동 물약 사용의 퍼센트를 80%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_90")) {
			pc.setAutoPotion_Percent(90);
			pc.sendPackets("자동 물약 사용의 퍼센트를 90%로 맞춥니다.");
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_1st")) {
			if (potions.size() > 0) {
				L1Item temp = ItemTable.getInstance().getTemplate(potions.get(0));
				pc.setAutoPotion(potions.get(0));
				pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
			} else {
				pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
			}
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_2nd")) {
			if (potions.size() > 1) {
				L1Item temp = ItemTable.getInstance().getTemplate(potions.get(1));
				pc.setAutoPotion(potions.get(1));
				pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
			} else {
				pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
			}
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_3rd")) {
			if (potions.size() > 2) {
				L1Item temp = ItemTable.getInstance().getTemplate(potions.get(2));
				pc.setAutoPotion(potions.get(2));
				pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
			} else {
				pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
			}
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_4th")) {
			if (potions.size() > 3) {
				L1Item temp = ItemTable.getInstance().getTemplate(potions.get(3));
				pc.setAutoPotion(potions.get(3));
				pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
			} else {
				pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
			}
			refresh = true;
		} else if (code.equalsIgnoreCase("potion_5th")) {
			if (potions.size() > 4) {
				L1Item temp = ItemTable.getInstance().getTemplate(potions.get(4));
				pc.setAutoPotion(potions.get(4));
				pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
			} else {
				pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
			}
			refresh = true;
		}

		if (refresh) {
			ArrayList<String> result = new ArrayList<String>();
			L1Item temp = ItemTable.getInstance().getTemplate(pc.getAutoPotion());
			String potion_type = "없음";
			if (temp != null) {
				potion_type = temp.getName();
			}
			result.add(pc.getAutoPotionUse() ? "ON" : "OFF");
			result.add(potion_type);
			result.add(String.valueOf(pc.getAutoPotion_Percent() + "%"));
			result.add(pc.isAlarmPotion() ? "ON" : "OFF");

			int size = 0;
			for (ItemInstance item : pc.getInventory().getItems()) {
				for (Integer id : Config.ServerInfo.AUTO_POTION_LIST) {
					if (item.getItemId() == id) {
						L1Item potion = ItemTable.getInstance().getTemplate(id);
						result.add(potion.getName());
					} else {
						size++;
					}
				}
			}

			for (int i = 0; i < size; i++) {
				result.add("없음");
			}

			pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "autoinfo", (String[]) result.toArray(new String[result.size()])));
		}
		return refresh;
	}
	/**
	 * 아이템 구매 판매 관련 
	 * @param npc
	 * @return
	 */
	private boolean isNpcSellOnly(L1NpcInstance npc) {
		int npcId = npc.getNpcTemplate().get_npcId();
		String npcName = npc.getNpcTemplate().get_name();
		if (npcId == 70027 // 디오
				|| "에덴상단".equals(npcName)) {
			return true;
		}
		return false;
	}
	/**
	 * 엔피시를 이용한 회상의촛불 관련
	 * @param pc
	 */
	private void MJReturnedStat(L1PcInstance pc) {
		SkillUse l1skilluse = new SkillUse();
		l1skilluse.handleCommands(pc, SkillId.캔슬레이션, pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_LOGIN);
		pc.getInventory().takeoffEquip(945);
		pc.sendPackets(S_CharVisualUpdate.get(pc));
		pc.setReturnStat(pc.getExp());
		pc.sendPackets(new S_SPMR(pc));
		pc.sendPackets(new S_OwnCharAttrDef(pc));
		pc.sendPackets(new S_OwnCharStatus(pc));
		pc.setReturnStatus(1);
		pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));
		try {
			pc.save();
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	/**
	 * 아지트 관련 
	 * @param pc
	 * @param objectId
	 * @param npcId
	 * @return
	 */
	private String sellHouse(L1PcInstance pc, int objectId, int npcId) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
		if (clan == null) {
			return ""; // 윈도우를 지운다
		}
		int houseId = clan.getHouseId();
		if (houseId == 0) {
			return ""; // 윈도우를 지운다
		}
		L1House house = HouseTable.getInstance().getHouseTable(houseId);
		int keeperId = house.getKeeperId();
		if (npcId != keeperId) {
			return ""; // 윈도우를 지운다
		}
		if (!pc.isCrown()) {
			pc.sendPackets(new S_ServerMessage(518)); // 이 인스트럭션은 혈맹의 군주만을 이용할 수 있습니다.
			return ""; // 윈도우를 지운다
		}
		if (pc.getId() != clan.getLeaderId()) {
			pc.sendPackets(new S_ServerMessage(518)); // 이 인스트럭션은 혈맹의 군주만을 이용할 수 있습니다.
			return ""; // 윈도우를 지운다
		}
		if (house.isOnSale()) {
			return "agonsale";
		}
		pc.sendPackets(new S_SellHouse(objectId, String.valueOf(houseId)));
		return null;
	}
	/**
	 * 성문 열기 닫기 관련 
	 * @param pc
	 * @param keeperId
	 * @param isOpen
	 */
	private void openCloseGate(L1PcInstance pc, int keeperId, boolean isOpen) {
		boolean isNowWar = false;
		int pcCastleId = 0;
		if (pc.getClanid() != 0) {
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				pcCastleId = clan.getCastleId();
			}
		}
		if (pcCastleId == 0) { // KKK 혈맹이 없거나, 성혈이 아닌경우
			return;
		}
		if (keeperId == 70656 || keeperId == 70549 || keeperId == 70985) {
			if (isExistDefenseClan(L1CastleLocation.KENT_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.KENT_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.KENT_CASTLE_ID);
		} else if (keeperId == 70600) { // OT
			if (isExistDefenseClan(L1CastleLocation.OT_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.OT_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.OT_CASTLE_ID);
		} else if (keeperId == 70778 || keeperId == 70987 || keeperId == 70687) {
			if (isExistDefenseClan(L1CastleLocation.WW_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.WW_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.WW_CASTLE_ID);
		} else if (keeperId == 70817 || keeperId == 70800 || keeperId == 70988
				|| keeperId == 70990 || keeperId == 70989 || keeperId == 70991) {
			if (isExistDefenseClan(L1CastleLocation.GIRAN_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.GIRAN_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.GIRAN_CASTLE_ID);

		} else if (keeperId == 70863 || keeperId == 70992 || keeperId == 70862) {
			if (isExistDefenseClan(L1CastleLocation.HEINE_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.HEINE_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.HEINE_CASTLE_ID);
		} else if (keeperId == 70995 || keeperId == 70994 || keeperId == 70993) {
			if (isExistDefenseClan(L1CastleLocation.DOWA_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.DOWA_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.DOWA_CASTLE_ID);
		} else if (keeperId == 70996) {
			if (isExistDefenseClan(L1CastleLocation.ADEN_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.ADEN_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.ADEN_CASTLE_ID);
		}
		for (DoorInstance door : DoorSpawnTable.getInstance().getDoorList()) {
			if (door.getKeeperId() == keeperId) {
				if (isNowWar && door.getMaxHp() > 1) {
				} else {
					if (isOpen) {
						door.open();
					} else {
						door.close();
					}
				}
			}
		}		
	}
	private boolean isExistDefenseClan(int castleId) {
		boolean isExistDefenseClan = false;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (castleId == clan.getCastleId()) {
				isExistDefenseClan = true;
				break;
			}
		}
		return isExistDefenseClan;
	}

	private String[] makeWarTimeStrings(int castleId) {
		L1Castle castle = CastleTable.getInstance().getCastleTable(castleId);
		if (castle == null) {
			return null;
		}
		Calendar warTime = castle.getWarTime();
		int year = warTime.get(Calendar.YEAR);
		int month = warTime.get(Calendar.MONTH) + 1;
		int day = warTime.get(Calendar.DATE);
		int hour = warTime.get(Calendar.HOUR_OF_DAY);
		int minute = warTime.get(Calendar.MINUTE);
		String[] result;
		if (castleId == L1CastleLocation.OT_CASTLE_ID) {
			result = new String[] { String.valueOf(year), String.valueOf(month), String.valueOf(day),
					String.valueOf(hour), String.valueOf(minute) };
		} else {
			result = new String[] { "", String.valueOf(year), String.valueOf(month), String.valueOf(day),
					String.valueOf(hour), String.valueOf(minute) };
		}
		return result;
	}
	private static final String[] summonstr_list = new String[] { "7", "263", "519", "8", "264", "520", "9", "265", "521", "10", "266", "522",
		"11", "267", "523", "12", "268", "524", "13", "269", "525", "14", "270", "526", "15", "271", "527",
		"16", "17", "18", "274" };
	
	private static final int[] summonid_list = new int[] {
		45140, 45027, 45033, // 28 홉고블린,아이들린,임프
		45098, 45143, 45138, // 32 간디오크,두다마라오크,오크스카우터
		45163, 45139, 45197, // 36 와일드 팡, 와일드 베놈, 와일드 매직
		81086, 81087, 200140, // 40 버그베어,킹버그베어,아이스맨
		200141, 200142, 200143, // 44 매드 리자드맨,해비 리자드맨, 하이 리자드맨
		200144, 200145, 200146, // 48 사라만다, 불타는전사,불타는궁수
		81100, 81096, 200147, // 52 다이어베어,다이어울프,다이어벳
		200148, 200149, 200150, // 56 거대 수호개미,산성개미무리,거대 산성개미
		200151, 200152, 200153, // 60 헬크리쳐, 그레이트 다크 엘리맨탈, 오튜
		200154, // 64 헬바운드
		81103, // 68 도플갱어
		81104 // 72 그레이트 미노타우르스, 쿠거
		//	200155,
       }; 
	private static final int[] summonlvl_list = new int[] { 28, 28, 28, 32, 32, 32, 36, 36, 36, 40, 40, 40, 44, 44, 44, 48, 48, 48, 52, 52, 52,
		56, 56, 56, 60, 60, 60, 64, 68, 72, 72 };// 술자 레벨제한
    private static final int[] summoncha_list = new int[] { 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
    	6, 6, 6, 6, 6, 6, 6, 6, 8, 8, 8, 10, 10, 10, 16, 16,
		16, 21, 28, 34, 34};// 카리
	/**
	 * 소환 조종반지 서먼 관련
	 * @param pc
	 * @param s
	 */
	private void summonMonster(L1PcInstance pc, String s) {
		int summonid = 0;
		int levelrange = 0;
		int summoncost = 0;

		for (int loop = 0; loop < summonstr_list.length; loop++) {
			if (s.equalsIgnoreCase(summonstr_list[loop])) {
				summonid = summonid_list[loop];
				levelrange = summonlvl_list[loop];
				summoncost = summoncha_list[loop];
				break;
			}
		}
		// Lv부족
		if (pc.getLevel() < levelrange) {
			pc.sendPackets(new S_ServerMessage(743));
			return;
		}

		int petcost = 0;
		Object[] petlist = pc.getPetList().values().toArray();
		for (Object pet : petlist) {
			petcost += ((L1NpcInstance) pet).getPetcost();
		}
		// 이미 애완동물이 있는 경우는, 돕페르겐가보스, 크가는 호출할 수 없다
		if ((summonid == 81103 || summonid == 81104) && petcost != 0) {
			pc.sendPackets(new S_CloseList(pc.getId()));
			return;
		}
		int charisma = pc.getAbility().getCha() + 6 - petcost;
		int summoncount = charisma / summoncost;
		L1Npc npcTemp = NpcTable.getInstance().getTemplate(summonid);
		for (int cnt = 0; cnt < summoncount; cnt++) {
			SummonInstance summon = new SummonInstance(npcTemp, pc);
			if (summonid == 81103 || summonid == 81104) {
				summon.setPetcost(pc.getAbility().getCha() + 7);
			} else {
				summon.setPetcost(summoncost);
			}
		}
		pc.sendPackets(new S_CloseList(pc.getId()));
	}

	private void openCloseDoor(L1PcInstance pc, L1NpcInstance npc, String s) {

		L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				int keeperId = house.getKeeperId();
				if (npc.getNpcTemplate().get_npcId() == keeperId) {
					DoorInstance door1 = null;
					DoorInstance door2 = null;
					DoorInstance door3 = null;
					DoorInstance door4 = null;
					for (DoorInstance door : DoorSpawnTable.getInstance()
							.getDoorList()) {
						if (door.getKeeperId() == keeperId) {
							if (door1 == null) {
								door1 = door;
								continue;
							}
							if (door2 == null) {
								door2 = door;
								continue;
							}
							if (door3 == null) {
								door3 = door;
								continue;
							}
							if (door4 == null) {
								door4 = door;
								break;
							}
						}
					}
					if (door1 != null) {
						if (s.equalsIgnoreCase("open")) {
							door1.open();
						} else if (s.equalsIgnoreCase("close")) {
							door1.close();
						}
					}
					if (door2 != null) {
						if (s.equalsIgnoreCase("open")) {
							door2.open();
						} else if (s.equalsIgnoreCase("close")) {
							door2.close();
						}
					}
					if (door3 != null) {
						if (s.equalsIgnoreCase("open")) {
							door3.open();
						} else if (s.equalsIgnoreCase("close")) {
							door3.close();
						}
					}
					if (door4 != null) {
						if (s.equalsIgnoreCase("open")) {
							door4.open();
						} else if (s.equalsIgnoreCase("close")) {
							door4.close();
						}
					}
				}
			}
		}
	}
	private void expelOtherClan(L1PcInstance clanPc, int keeperId) {
		int houseId = 0;
		for (L1House house : HouseTable.getInstance().getHouseTableList()) {
			if (house.getKeeperId() == keeperId) {
				houseId = house.getHouseId();
			}
		}
		if (houseId == 0) {
			return;
		}

		int[] loc = new int[3];
		for (L1Object object : L1World.getInstance().getObject()) {
			if (object instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) object;
				if (L1HouseLocation.isInHouseLoc(houseId, pc.getX(), pc.getY(), pc.getMapId())
						&& clanPc.getClanid() != pc.getClanid()) {
					loc = L1HouseLocation.getHouseTeleportLoc(houseId, 0);
					if (pc != null) {
						pc.teleport(loc[0], loc[1], (short) loc[2], pc.getHeading(), 169, true, false);
					}
				}
			}
		}
	}
	private String[] makeHouseTaxStrings(L1PcInstance pc, L1NpcInstance npc) {
		String name = npc.getNpcTemplate().get_name();
		String[] result;
		result = new String[] { name, "2000", "1", "1", "00" };
		L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				int keeperId = house.getKeeperId();
				if (npc.getNpcTemplate().get_npcId() == keeperId) {
					Calendar cal = house.getTaxDeadline();
					int month = cal.get(Calendar.MONTH) + 1;
					int day = cal.get(Calendar.DATE);
					int hour = cal.get(Calendar.HOUR_OF_DAY);
					result = new String[] { name, "2000", String.valueOf(month), String.valueOf(day),
							String.valueOf(hour) };
				}
			}
		}
		return result;
	}
	private void payFee(L1PcInstance pc, L1NpcInstance npc) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				int keeperId = house.getKeeperId();
				if (npc.getNpcTemplate().get_npcId() == keeperId) {
					if (pc.getInventory().checkItem(ItemId.아데나, 2000)) {
						pc.getInventory().consumeItem(ItemId.아데나, 2000);
						TimeZone tz = TimeZone.getTimeZone(Config.TIME_ZONE);
						Calendar cal = Calendar.getInstance(tz);
						cal.add(Calendar.DATE, Config.HOUSE_TAX_INTERVAL);
						cal.set(Calendar.MINUTE, 0); // 분 , 초는 잘라서 버림
						cal.set(Calendar.SECOND, 0);
						house.setTaxDeadline(cal);
						HouseTable.getInstance().updateHouse(house); // DB에 기입해
					} else {
						pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 충분치 않습니다.
					}
				}
			}
		}
	}
	private String getYaheeAmulet(L1PcInstance pc, L1NpcInstance npc, String s) {
	    int[] amuletIdList = { 20358, 20359, 20360, 20361, 20362, 20363, 20364, 20365 };
		int amuletId = 0;
		ItemInstance item = null;
		int karmalevel = pc.getKarmaLevel();
		int bug = 0;
		String htmlid = null;
		if (s.equalsIgnoreCase("1")) {
			bug = -1;
			amuletId = amuletIdList[0];
		} else if (s.equalsIgnoreCase("2")) {
			bug = -2;
			amuletId = amuletIdList[1];
		} else if (s.equalsIgnoreCase("3")) {
			bug = -3;
			amuletId = amuletIdList[2];
		} else if (s.equalsIgnoreCase("4")) {
			bug = -4;
			amuletId = amuletIdList[3];
		} else if (s.equalsIgnoreCase("5")) {
			bug = -5;
			amuletId = amuletIdList[4];
		} else if (s.equalsIgnoreCase("6")) {
			bug = -6;
			amuletId = amuletIdList[5];
		} else if (s.equalsIgnoreCase("7")) {
			bug = -7;
			amuletId = amuletIdList[6];
		} else if (s.equalsIgnoreCase("8")) {
			bug = -8;
			amuletId = amuletIdList[7];
		}
		if (amuletId != 0) {
			if (karmalevel == bug) {
				item = pc.getInventory().storeItem(amuletId, 1);
				if (item != null) {
					pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를 주었습니다.
				}
			} else {
				pc.sendPackets(new S_SystemMessage("현재 우호도가 부족합니다."));
			}
			for (int id : amuletIdList) {
				if (id == amuletId) {
					break;
				}
				if (pc.getInventory().checkItem(id)) {
					pc.getInventory().consumeItem(id, 1);
				}
			}
			htmlid = "";
		}
		return htmlid;
	}
	private void getBloodCrystalByKarma(L1PcInstance pc, L1NpcInstance npc, String s) {
		ItemInstance item = null;

		// 「블래드 크리스탈의 조각을 1개 주세요」
		if (s.equalsIgnoreCase("1")) {
			pc.addKarma((int) (500 * Config.RATE_KARMA));
			item = pc.getInventory().storeItem(40718, 1);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			// 야히의 모습을 기억하는 것이 어려워집니다.
			pc.sendPackets(new S_ServerMessage(1081));
		}
		// 「블래드 크리스탈의 조각을 10개 주세요」
		else if (s.equalsIgnoreCase("2")) {
			pc.addKarma((int) (5000 * Config.RATE_KARMA));
			item = pc.getInventory().storeItem(40718, 10);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			// 야히의 모습을 기억하는 것이 어려워집니다.
			pc.sendPackets(new S_ServerMessage(1081));
		}
		// 「블래드 크리스탈의 조각을 100개 주세요」
		else if (s.equalsIgnoreCase("3")) {
			pc.addKarma((int) (50000 * Config.RATE_KARMA));
			item = pc.getInventory().storeItem(40718, 100);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			// 야히의 모습을 기억하는 것이 어려워집니다.
			pc.sendPackets(new S_ServerMessage(1081));
		}
	}
	private String getBarlogEarring(L1PcInstance pc, L1NpcInstance npc, String s) {
		int[] earringIdList = { 21020, 21021, 21022, 21023, 21024, 21025, 21026, 21027 };
		int earringId = 0;
		int karmalevel = pc.getKarmaLevel();
		int bug = 0;
		ItemInstance item = null;
		String htmlid = null;
		if (s.equalsIgnoreCase("1")) {
			earringId = earringIdList[0];
			bug = 1;
		} else if (s.equalsIgnoreCase("2")) {
			earringId = earringIdList[1];
			bug = 2;
		} else if (s.equalsIgnoreCase("3")) {
			earringId = earringIdList[2];
			bug = 3;
		} else if (s.equalsIgnoreCase("4")) {
			earringId = earringIdList[3];
			bug = 4;
		} else if (s.equalsIgnoreCase("5")) {
			earringId = earringIdList[4];
			bug = 5;
		} else if (s.equalsIgnoreCase("6")) {
			earringId = earringIdList[5];
			bug = 6;
		} else if (s.equalsIgnoreCase("7")) {
			earringId = earringIdList[6];
			bug = 7;
		} else if (s.equalsIgnoreCase("8")) {
			earringId = earringIdList[7];
			bug = 8;
		}
		if (earringId != 0) {
			if (karmalevel == bug) {
				item = pc.getInventory().storeItem(earringId, 1);
				if (item != null) {
					pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName()));
				}
			} else {
				pc.sendPackets(new S_SystemMessage("현재 우호도가 부족합니다."));
			}

			for (int id : earringIdList) {
				if (id == earringId) {
					break;
				}
				if (pc.getInventory().checkItem(id)) {
					pc.getInventory().consumeItem(id, 1);
				}
			}
			htmlid = "";
		}
		return htmlid;
	}
	private void getSoulCrystalByKarma(L1PcInstance pc, L1NpcInstance npc, String s) {
		ItemInstance item = null;

		// 「서울 크리스탈의 조각을 1개 주세요」
		if (s.equalsIgnoreCase("1")) {
			pc.addKarma((int) (-500 * Config.RATE_KARMA));
			item = pc.getInventory().storeItem(40678, 1);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			// 바르로그의 냉소를 느낌 오한이 달립니다.
			pc.sendPackets(new S_ServerMessage(1080));
		}
		// 「서울 크리스탈의 조각을 10개 주세요」
		else if (s.equalsIgnoreCase("2")) {
			pc.addKarma((int) (-5000 * Config.RATE_KARMA));
			item = pc.getInventory().storeItem(40678, 10);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			// 바르로그의 냉소를 느낌 오한이 달립니다.
			pc.sendPackets(new S_ServerMessage(1080));
		}
		// 「서울 크리스탈의 조각을 100개 주세요」
		else if (s.equalsIgnoreCase("3")) {
			pc.addKarma((int) (-50000 * Config.RATE_KARMA));
			item = pc.getInventory().storeItem(40678, 100);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			// 바르로그의 냉소를 느낌 오한이 달립니다.
			pc.sendPackets(new S_ServerMessage(1080));
		}
	}
	private void poly(GameClient clientthread, int polyId) {
		L1PcInstance pc = clientthread.getActiveChar();

		if (pc.getInventory().checkItem(ItemId.아데나, 100)) { // check
			pc.getInventory().consumeItem(ItemId.아데나, 100); // del

			L1PolyMorph.doPoly(pc, polyId, 1800,
					L1PolyMorph.MORPH_BY_LOGIN);
		} else {
			pc.sendPackets(new S_ServerMessage(337, "$4")); // 아데나가 부족합니다.
		}
	}
	private String enterUb(L1PcInstance pc, int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		if (!ub.isActive() || !ub.canPcEnter(pc)) { // 시간외
			return "colos2";
		}
		if (ub.isNowUb()) { // 경기중
			return "colos1";
		}
		if (ub.getMembersCount() >= ub.getMaxPlayer()) { // 정원 오버
			return "colos4";
		}

		ub.addMember(pc); // 멤버에게 추가
		L1Location loc = ub.getLocation().randomLocation(10, false);
		pc.teleport(loc.getX(), loc.getY(), ub.getMapId(), pc.getHeading(), 169, true, false);
		return "";
	}
	private String enterHauntedHouse(L1PcInstance pc) {
		if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_PLAYING) { // 경기중
			pc.sendPackets(new S_ServerMessage(1182)); // 이미 게임이 진행 되었네.
			return "";
		}
		if (L1HauntedHouse.getInstance().getMembersCount() >= 10) { // 정원 오버
			pc.sendPackets(new S_ServerMessage(1184)); // 이미 유령의 집이 포화 상태라네.
			return "";
		}

		L1HauntedHouse.getInstance().addMember(pc); // 멤버에게 추가
		pc.teleport(32722, 32830, (short) 5140, pc.getHeading(), 169, true, false);
		return "";
	}

	private String watchUb(L1PcInstance pc, int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		L1Location loc = ub.getLocation();
		if (pc.getInventory().consumeItem(ItemId.아데나, 100)) {
			try {
				pc.save();
				pc.beginGhost(loc.getX(), loc.getY(), (short) loc.getMapId(), true);
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		} else {
			pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
		}
		return "";
	}
	private String[] makeUbInfoStrings(int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		return ub.makeUbInfoStrings();
	}

	private String karmaLevelToHtmlId(int level) {
		if (level == 0 || level < -7 || 7 < level) {
			return "";
		}
		String htmlid = "";
		if (0 < level) {
			htmlid = "vbk" + level;
		} else if (level < 0) {
			htmlid = "vyk" + Math.abs(level);
		}
		return htmlid;
	}
	private String talkToDimensionDoor(L1PcInstance pc, L1NpcInstance npc, String s) {
		String htmlid = "";
		int protectionId = 0;
		int sealId = 0;
		int locX = 0;
		int locY = 0;
		short mapId = 0;
		if (npc.getNpcTemplate().get_npcId() == 80059) { // 차원의 문(토)
			protectionId = 40909;
			sealId = 40913;
			locX = 32773;
			locY = 32835;
			mapId = 607;
		} else if (npc.getNpcTemplate().get_npcId() == 80060) { // 차NpcInstanceprotectionId = 40912;
			sealId = 40916;
			locX = 32757;
			locY = 32842;
			mapId = 606;
		} else if (npc.getNpcTemplate().get_npcId() == 80061) { // 차원의 문(수)
			protectionId = 40910;
			sealId = 40914;
			locX = 32830;
			locY = 32822;
			mapId = 604;
		} else if (npc.getNpcTemplate().get_npcId() == 80062) { // 차원의 문(화)
			protectionId = 40911;
			sealId = 40915;
			locX = 32835;
			locY = 32822;
			mapId = 605;
		}

		// 「안에 들어와 본다」 「원소의 지배자를 접근해 본다」 「통행증을 사용한다」 「통과한다」
		if (s.equalsIgnoreCase("a")) {
			pc.teleport(locX, locY, mapId, pc.getHeading(), 169, true, false);
			htmlid = "";
		}
		// 「그림으로부터 돌출부분을 없앤다」
		else if (s.equalsIgnoreCase("b")) {
			ItemInstance item = pc.getInventory().storeItem(protectionId, 1);
			if (item != null) {
				pc.sendPackets(new S_ServerMessage(143, npc.getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를
				// 주었습니다.
			}
			htmlid = "";
		}
		// 「통행증을 버려, 이 땅을 포기한다」
		else if (s.equalsIgnoreCase("c")) {
			htmlid = "wpass07";
		}
		// 「계속한다」
		else if (s.equalsIgnoreCase("d")) {
			if (pc.getInventory().checkItem(sealId)) { // 지의 인장
				ItemInstance item = pc.getInventory().findItemId(sealId);
				pc.getInventory().consumeItem(sealId, item.getCount());
			}
		}
		// 「그대로 한다」 「당황해 줍는다」
		else if (s.equalsIgnoreCase("e")) {
			htmlid = "";
		}
		// 「사라지도록(듯이) 한다」
		else if (s.equalsIgnoreCase("f")) {
			if (pc.getInventory().checkItem(protectionId)) { // 지의 통행증
				pc.getInventory().consumeItem(protectionId, 1);
			}
			if (pc.getInventory().checkItem(sealId)) { // 지의 인장
				ItemInstance item = pc.getInventory().findItemId(sealId);
				pc.getInventory().consumeItem(sealId, item.getCount());
			}
			htmlid = "";
		}
		return htmlid;
	}
	@Override
	public String getType() {
		return C_NPC_ACTION;
	}

}
