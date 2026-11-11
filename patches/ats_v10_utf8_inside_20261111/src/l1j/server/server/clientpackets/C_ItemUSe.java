
package l1j.server.server.clientpackets;

import java.sql.Connection; 
import java.sql.PreparedStatement; 
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import I1j.server.server.model.Thread.NpcDeleteTimer;
import javolution.util.FastTable;
import l1j.server.L1DatabaseFactory;
import l1j.server.BugRace.L1RaceTicket;
import l1j.server.CTSystem.MJCTHandler;
import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.CharacterAction.Teleport.Teleport;
import l1j.server.DTSSystem.IODTSLoader;
import l1j.server.DogFightSystem.DogFightTicket;
import l1j.server.DungeonTimer.Loader.DungeonTimePotionLoader;
import l1j.server.ExchangeItem.ExchangeItemInfo;
import l1j.server.ExchangeItem.ExchangeItemLoader;
import l1j.server.ExchangeListItem.ExchangeListItemLoader;
import l1j.server.ItemEnchantSystem.MJItemEnchanterLoader;
import l1j.server.ItemExChangeSystem.ItemExChangeLoader;
import l1j.server.RaidSystem.Loader.Dragon_Raid_Creator_Loader;
import l1j.server.Template.LORnd;
import l1j.server.Templates.L1Armor;
import l1j.server.Templates.L1BookMark;
import l1j.server.Templates.L1BuffBead;
import l1j.server.Templates.L1EtcItem;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Pet;
import l1j.server.Templates.L1Skills;
import l1j.server.Templates.L1UserRanking;
import l1j.server.configure.Config;
import l1j.server.server.Account;
import l1j.server.server.ActionCodes;
import l1j.server.server.GameClient;
import l1j.server.server.Controller.EventNpcSpawnCotroller;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.Controller.UserRankingController;
import l1j.server.server.datatables.EtcItemBuffBeadTable;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.ItemSellList;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.L1DragonJewel;
import l1j.server.server.datatables.MapRingTelTable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.EffectInstance;
import l1j.server.server.model.Instance.FurnitureInstance;
import l1j.server.server.model.Instance.L1GuardianInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.item.BlankScroll;
import l1j.server.server.model.item.BlessTypeEnchant;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.model.item.EnchantScroll;
import l1j.server.server.model.item.HealingPotion;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.item.MemorialStone;
import l1j.server.server.model.item.Oman_Teleport_Scroll;
import l1j.server.server.model.item.PolyPotion;
import l1j.server.server.model.item.SpellItem;
import l1j.server.server.model.item.SpellWand;
import l1j.server.server.model.item.TreasureBox;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_AddSkill;
import l1j.server.server.serverpackets.S_AttackPacket;
import l1j.server.server.serverpackets.S_AttackStatus;
import l1j.server.server.serverpackets.S_AuctionSystemBoard;
import l1j.server.server.serverpackets.S_Board;
import l1j.server.server.serverpackets.S_ChangeShape;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_CommonNews;
import l1j.server.server.serverpackets.S_CurseBlind;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_Fishing;
import l1j.server.server.serverpackets.S_IdentifyDesc;
import l1j.server.server.serverpackets.S_ItemName;
import l1j.server.server.serverpackets.S_Letter;
import l1j.server.server.serverpackets.S_Liquor;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_MonsterDropList;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_ReturnedStat;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_ShowPolyList;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconBlessOfEva;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillIconWisdomPotion;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_Sound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_UseMap;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.MessageUtil;
import l1j.server.server.utils.SQLUtil;

public class C_ItemUSe extends ClientBasePacket {

	private static final String C_ITEM_USE = "[C] C_ItemUSe";
	private static Logger _log = Logger.getLogger(C_ItemUSe.class.getName());

	private static Random _random = new Random();

	public C_ItemUSe(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);
		int itemObjid = readD();

		L1PcInstance pc = client.getActiveChar();
		// 피시가 널일때
		if (pc == null) {
			return;
		}  
		ItemInstance useitem = pc.getInventory().getItem(itemObjid);
		// 고스트 상태, 로그인 상태가 아니거나 죽은 상태가 아닐때
		if(pc.isGhost() || pc.isTwoLogin(pc)
				|| pc.isDead() || pc.isPrivateShop() || pc.getTeleport()) { // 텔레포트 처리중 
			return;
		}
		// 버그 체크 
		L1PcInstance 버그체크 = L1World.getInstance().getPlayer(pc.getName());
		if (버그체크 == null && pc.getAccessLevel() != 200) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_7));
			client.kick();
			return;
		}
		// 아이템 사용 가능한 맵이 아닐경우 
		if (!pc.getMap().isUsableItem()) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_5));
			pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false)); 
			return;
		}
		if (Dragon_Raid_Creator_Loader.getInstance().createRaid(pc, useitem)) {
			return;
		}
		// 텔레포트 상태 이상 체크 
		if(CommonUtil.teleporCheck(pc, useitem)) {
			pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false)); 
			return;
		}		
		// 아이템 이 없을때
		if (useitem == null || useitem.getItem() == null 
				|| useitem.getItem() instanceof L1RaceTicket || useitem.getItem() instanceof DogFightTicket) {
			return;
		}
		// 사용 할수 없는 아이템 체크 -1이라면
		if (useitem.getItem().getUseType() == -1) { // none:사용할 수 없는 아이템
			pc.sendPackets(new S_ServerMessage(74, useitem.getLogName())); 
			return;
		}
		// 각 유즈 아이템 타입 
		int useType = useitem.getItem().getUseType();
		// 기마용 투구  횟수 제한  
		if (useitem.getItemId() == ItemId.기마용_투구 && useitem.getChargeCount() <= 0) {
			return;
		}	
		// 아이템 아이디 체크
		int itemId;
		try {
			itemId = useitem.getItem().getItemId(); 
		} catch (Exception e) {
			return;
		}
		// 캐릭터 구술 저장 시스템 
		if (itemId == CTLoadManager.CTSYSTEM_LOAD_ID) {
			MJCTHandler.load(pc, useitem);
			return;
		}
		// 사냥터 충전 아이템 관련 
		if (DungeonTimePotionLoader.getInstance().use_potion(pc, useitem)) { 
			return;
		}
		// 텔레포트 주문서 관련 
		if (IODTSLoader.getInstance().use_item(pc, useitem)) { 
			pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false)); 
			return;
		} 		
		// 아이템 제작 리스트 관련
		if (ExchangeListItemLoader.getInstance().getExchangeItemInfo(pc, useitem)) {
			return;
		}
		int l_id = readD();
		if (ItemExChangeLoader.getInstance().use_item(pc, useitem, l_id)) {
			return;
		} else {
			readP(-4);
		}
		boolean isDelayEffect = false;

		int bmapid = 0, btele = 0, skillid = 0, spellsc_objid = 0, spellsc_x = 0,
				spellsc_y = 0, resid = 0, letterCode = 0, cookLevel = 0, cookNo = 0,
				fishX = 0, fishY = 0;
		String letterReceiver = "";
		byte[] letterText = null;

		ExchangeItemInfo ex_Info = ExchangeItemLoader.getInstance().getExchangeItemInfo(useitem.getItemId());
		if (ex_Info != null) {
			if (pc.getInventory().consumeItem(useitem, ex_Info.get_use_count())) {
				if (LORnd.isWinning(1000000, ex_Info.calc_probability(pc))) {
					ItemInstance ex_item = ItemTable.getInstance().createItem(ex_Info.get_success_item_id());
					ex_item.setCount(ex_Info.get_success_item_count());
					pc.getInventory().storeTradeItem(ex_item);
					pc.sendPackets(String.format("%s의 모습이 찬란히 빛나며 변환에 성공하여 %s를 획득 하였습니다.", ex_Info.get_item_name(), ex_Info.get_success_item_name()));
					return;
				} else {
					pc.sendPackets(String.format("%s의 모습이 어둡게 빛나며 변환에 실패하여 증발 하였습니다.", ex_Info.get_item_name()));
				}
			} else {
				pc.sendPackets(String.format("재료: %s의 %s개가 필요합니다.", ex_Info.get_item_name(), ex_Info.get_use_count()));
				return;
			}
		}

		String s = "";
		int l = 0, itemType = useitem.getItem().getType2();	

		//////////////////////// 기타 아이템 패킷 시작 아이템 타잉으로 구현  ///////////////////////
		if(itemType == L1Item.CATEGORY_ETC) {
			if(useType == L1Item.TYPE_USE_IDENTIFY || useType == L1Item.TYPE_USE_CHOICE) {
				l = readD();
			} else if(useType == L1Item.TYPE_USE_SOSC) {
				s = readS();
			} else if(useType == L1Item.TYPE_USE_BLANK) {
				skillid = readC();
			} else if(useType == L1Item.TYPE_USE_SPELL_BUFF || itemId == ItemId.마법서_블레스드_아머 
					|| itemId == ItemId.마법_주문서_블레스드_아머 || itemId == CTLoadManager.CTSYSTEM_STORE_ID) {
				spellsc_objid = readD();			
			} else if(useType == L1Item.TYPE_USE_SPELL_LONG || useType == L1Item.TYPE_USE_SPELL_SHORT) {
				spellsc_objid = readD();
				spellsc_x = readH();
				spellsc_y = readH();
			} else if(useType == L1Item.TYPE_USE_RES) {
				resid = readD();
			} else if(useType == L1Item.TYPE_USE_LETTER || useType == L1Item.TYPE_USE_LETTER_W 
					||  useType == L1Item.TYPE_USE_CCARD || useType == L1Item.TYPE_USE_CCARD_W 
					|| useType == L1Item.TYPE_USE_VCARD || useType == L1Item.TYPE_USE_VCARD_W 
					|| useType == L1Item.TYPE_USE_WCARD || useType == L1Item.TYPE_USE_WCARD_W) {
				letterCode = readH();
				letterReceiver = readS();
				letterText = readByte();
			} else if(itemId >= ItemId.요리책_1단계 && itemId <= ItemId.요리책_5단계) { 
				cookLevel = readC();
				cookNo = readC();
			} else if(useType == L1Item.TYPE_USE_FISHING_ROD) {
				fishX = readH();
				fishY = readH(); 
			} else if(useType == L1Item.TYPE_USE_NTELE || useType == L1Item.TYPE_USE_BTELE) {
				L1EventNpcSpawn event_map_check = EventNpcSpawnTable.getInstance().getEventMapCheck(pc.getMapId());
				if ((event_map_check != null) && (!pc.isGm())) {
					if (!EventNpcSpawnCotroller.isSpawn(event_map_check)) { 
						pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[사냥터 시간이 아니므로 사용할 수 없습니다]"), 1));
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
						return;
					}
				}
				bmapid = readH();
				btele = readD(); 
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false)); 
			} else if(useType == L1Item.TYPE_USE_MAP) {
				pc.sendPackets(new S_UseMap(pc, useitem.getId(),
						useitem.getItem().getItemId()));
			} else if(useType == L1Item.TYPE_USE_POLY) {	
				PolyPotion polyPotion = PolyPotion.get(itemId);
				if (polyPotion != null) {
					polyPotion.use(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}
			} else if (useType == L1Item.TYPE_USE_NAGIC_DOLL) {
				MagicDoll magicDollItem = MagicDoll.get(itemId);
				if (magicDollItem != null) { 
					magicDollItem.use(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}
			} else if(useType == L1Item.TYPE_USE_DAL || useType == L1Item.TYPE_USE_ZEL || useType == L1Item.TYPE_USE_ACCZEL) {
				l = readD();
				ItemInstance targetItem1 = pc.getInventory().getItem(l);				
				if (targetItem1 == null) {
					return;
				}	
				if (EnchantScroll.getInstance().use(pc, useitem, targetItem1)) {
					pc.getInventory().removeItem(useitem, 1);				
				}
			} else if(useType == L1Item.TYPE_USE_FOOD) {
				if (pc.getFood() < 225) {
					pc.setFood(pc.getFood() + 10);
					if (itemId == ItemId.허브) { // 허브
						pc.setFood(pc.getFood() + 90);
					}
					int foodvolume = (useitem.getItem().getFoodVolume() / 10);
					pc.addFood(foodvolume <= 0 ? 5 : foodvolume);
					pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood()));
					pc.sendPackets(new S_ServerMessage(76, useitem.getItem().getNameId()));
				}
				if (itemId == ItemId.괴물눈_고기) {
					pc.setSkillEffect(SkillId.상태_괴물눈, 0);
					if (pc.hasSkillEffect(SkillId.커스_블라인드) || pc.hasSkillEffect(SkillId.다크니스)) {
						pc.sendPackets(new S_CurseBlind(2));
					}
				} 
				pc.getInventory().removeItem(useitem, 1);
				
			} else if(useType == L1Item.TYPE_USE_OMANTELL) {			
				if (Oman_Teleport_Scroll.getInstance().use(pc, itemId, useitem)) {
					pc.getInventory().removeItem(useitem, 1);				
				}
			} else {
				l = readC();
			}

			int etcType = useitem.getItem().getType();
			// 오류 체크 
			ItemInstance targetItem = pc.getInventory().getItem(l);
			_log.finest("request item use (obj) = " + itemObjid + " action = "+ l + " value = " + s);

			// 아이템 딜레이 시작
			int delay_id = 0;
			delay_id = ((L1EtcItem) useitem.getItem()).get_delayid();
			if (delay_id != 0) {
				if (pc.hasItemDelay(delay_id)) {
					return;
				}
			}
			// 아이템 레벨 설정
			int item_minlvl = ((L1EtcItem) useitem.getItem()).getMinLevel();
			int item_maxlvl = ((L1EtcItem) useitem.getItem()).getMaxLevel();
			if (item_minlvl != 0 && item_minlvl > pc.getLevel() && !pc.isGm()) {
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(318, String.valueOf(item_minlvl)));
				return;
			} else if (item_maxlvl != 0 && item_maxlvl < pc.getLevel() && !pc.isGm()) {
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				pc.sendPackets(new S_ServerMessage(673, String.valueOf(item_maxlvl)));
				return;
			}
			// 재사용 아이템 딜레이 이팩트  체크 
			int delayEffect = ((L1EtcItem) useitem.getItem()).get_delayEffect();
			if (delayEffect > 0) {
				isDelayEffect = true;
				Timestamp lastUsed = useitem.getLastUsed();
				if (lastUsed != null) {
					Calendar cal = Calendar.getInstance();
					if ((cal.getTimeInMillis() - lastUsed.getTime()) / 1000 <= delayEffect) {
						long totalminute = ((delayEffect - (cal.getTimeInMillis() - lastUsed.getTime()) / 1000) / 60);
						long second = ((delayEffect - (cal.getTimeInMillis() - lastUsed.getTime()) / 1000) % 60);

						if(totalminute > 0){
							Calendar nextcal = Calendar.getInstance();
							int hour = (int) (totalminute / 60);
							int minute = (int) (totalminute % 60);
							nextcal.add(Calendar.HOUR_OF_DAY , hour);
							nextcal.add(Calendar.MINUTE , minute);
							pc.sendPackets(new S_SystemMessage(totalminute + "분 " + second + "초" + " 후에 사용할 수 있습니다. "));
						} else {
							pc.sendPackets(new S_SystemMessage(totalminute + "분 " + second + "초" + " 후에 사용할 수 있습니다. "));
						}
						return;
					}
				}
			}

			// 버프 아이템 데이터베이스 설정
			try {
				L1BuffBead bead = EtcItemBuffBeadTable.getInstance().getBuffBeadItem(itemId);
				if (bead != null) {
					if (bead.getUseItemId() > 0) {
						if (!pc.getInventory().consumeItem(bead.getUseItemId(), bead.getUseItemCount())) {
							L1Item consume = ItemTable.getInstance().getTemplate(bead.getUseItemId());
							pc.sendPackets(new S_ServerMessage(337, consume.getNameId()));
							return;
						}
					}
					if (bead.isTimeUse()) {
						Timestamp ts = new Timestamp(System.currentTimeMillis());
						useitem.setLastUsed(ts); 
					}
					pc.setBuffnoch(1);
					SkillUse l1Skilluse = new SkillUse();
					for (int i = 0; i < bead.getBuffId().length; i++) {
						if (pc.hasSkillEffect(bead.getBuffId()[i])) {
							pc.removeSkillEffect(bead.getBuffId()[i]);
						}
						l1Skilluse.handleCommands(pc, bead.getBuffId()[i], pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
					}
					pc.setBuffnoch(0);
					if (bead.isDelete()) {
						pc.getInventory().removeItem(useitem, 1);
					}
					return;
				} 
			} catch (Exception e) {
				e.getStackTrace();
			}
			// etctype 에 따른 설정
			if (etcType == L1Item.TYPE_ETCITEM_ARROW) {
				pc.getInventory().setArrow(useitem.getItem().getItemId());
				pc.sendPackets(new S_ServerMessage(452, useitem.getLogName()));
			} else if (etcType == L1Item.TYPE_ETCITEM_STING) {
				pc.getInventory().setSting(useitem.getItem().getItemId());
				pc.sendPackets(new S_ServerMessage(452, useitem.getLogName()));
			} else if (useType == L1Item.TYPE_ETCITEM_BLESSING) { 
				l = readD();
			} else if (useType == L1Item.TYPE_USE_BLANK) { 
				BlankScroll blanksc = BlankScroll.get(itemId);
				if (blanksc != null) {
					blanksc.use(pc, useitem, skillid);
				} else {
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}
			} else if (etcType == L1Item.TYPE_ETCITEM_TREASURE_BOX) {
				TreasureBox box = TreasureBox.get(itemId); 
				if (pc.getInventory().getWeight100() > 95 || pc.getInventory().getSize() > 175) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_8));
					return;
				} 
				if (box != null) {
					if (box.open(pc)) {
						L1EtcItem temp = (L1EtcItem) useitem.getItem();
						if (temp.get_delayEffect() > 0) {
							isDelayEffect = true;
						} else {
							pc.getInventory().removeItem(useitem.getId(), 1);
						}
					}
				}
			} else if (etcType == L1Item.TYPE_ETCITEM_SPELLSCR || etcType == L1Item.TYPE_ETCITEM_SPELLSCRV) {
				SpellItem spell = SpellItem.get(itemId);
				if (spell != null) {				
					spell.use(pc, useitem, spellsc_objid, spellsc_x, spellsc_y);
				} else {
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				} 
			} else if (etcType == L1Item.TYPE_ETCITEM_JEWEL) {
				L1DragonJewel dragon_jewel = L1DragonJewel.get(itemId);
				if (dragon_jewel != null) {
					dragon_jewel.use(pc, useitem); 
				} else { 
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}
			} else if (etcType == L1Item.TYPE_ETCITEM_LIGHT) { // light 
				if (useitem.getRemainingTime() <= 0 && itemId != ItemId.마법_랜턴) {
					return;
				} 
				if (useitem.isNowLighting()) {
					useitem.setNowLighting(false);
					pc.getLight().turnOnOffLight(); 
					useitem.stopEquipmentTimer(pc);
				} else {
					useitem.setNowLighting(true);
					pc.getLight().turnOnOffLight();  
					useitem.startEquipmentTimer(pc);
				}
				pc.sendPackets(new S_ItemName(useitem));
			} else if(useitem.getItem().getName().startsWith("영혼석(")) {
				MemorialStone memorialstone = MemorialStone.get(itemId);
				if (memorialstone != null) {
					memorialstone.use(pc, useitem); 
				} else { 
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}
			} else if (etcType == L1Item.TYPE_ETCITEM_BLESSING) {
				BlessTypeEnchant blessType = BlessTypeEnchant.get(itemId);
				if (blessType != null) {
					blessType.use(pc, useitem, targetItem); 
				} else {
					if (pc.isGm()) {
						pc.sendPackets(new S_SystemMessage("설정된 아이템이 없습니다."));
					} else {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_39, useitem.getLogName()));
					}
				}
			} else if(etcType == L1Item.TYPE_ETCITEM_FOOD) { //요리 전용 
				Cooking cook = Cooking.get(itemId);
				if (cook != null) {
					if (pc.getFood() < 225) { // 100%
						pc.setFood(pc.getFood() + 225 * 1);//한번에 100%채워지게
						if (pc.getFood() > 225) {
							pc.setFood(225); 
						}
						pc.setFood(pc.getFood() + 225);
						int foodvolume = (useitem.getItem().getFoodVolume() / 10);
						pc.addFood(foodvolume <= 0 ? 5 : foodvolume);
						pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood()));
					}
					cook.use(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}	
			} else if(etcType == L1Item.TYPE_ETCITEM_HEALING_POTION) {
				HealingPotion healingPotion = HealingPotion.get(itemId);
				if (healingPotion != null) {
					healingPotion.use(pc, useitem); 
				}
			} else if(etcType == L1Item.TYPE_ETCITEM_SPELLBOOK) {		
				UseSkillBook(pc, itemId, useitem); 
			}
			
			
			// 아이템 아이디 시작 
			if (itemId == 52063) { // 조우의 이동 기억책
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "book01"));
			}
			
			switch (itemId) {
			case ItemId.성별_전환_물약: 
				int[] MALE_LIST = new int[] { 0, 61, 138, 20071, 2786 };
				int[] FEMALE_LIST = new int[] { 1, 48, 37, 20072, 2796 };
				if (pc.get_sex() == 0) {
					pc.set_sex(1);
					pc.setClassId(FEMALE_LIST[pc.getType()]);
				} else {
					pc.set_sex(0);
					pc.setClassId(MALE_LIST[pc.getType()]);
				}
				pc.setCurrentSprite(pc.getClassId());
				pc.sendPackets(new S_ChangeShape(pc.getId(), pc.getClassId()));
				pc.broadcastPacket(new S_ChangeShape(pc.getId(), pc.getClassId()));
				pc.getInventory().removeItem(useitem, 1);

				break;
			case ItemId.축복_해제_주문서:
				if (targetItem != null) {
					if (targetItem.getBlessType() > 0) {
						if (targetItem.isEquipped()) {
							if (targetItem.getItem().getType2() == L1Item.CATEGORY_WEAPON) {
								UseWeapon(pc, targetItem); 
							} else if (targetItem.getItem().getType2() == L1Item.CATEGORY_ARMOR) {
								UseArmor(pc, targetItem);  
							}
							targetItem.setBlessType(0);
							targetItem.setBlessTypeValue(0);
							targetItem.setBless(1);
							if (targetItem.getItem().getType2() == L1Item.CATEGORY_WEAPON) {
								UseWeapon(pc, targetItem); 
							} else if (targetItem.getItem().getType2() == L1Item.CATEGORY_ARMOR) { 
								UseArmor(pc, targetItem);  
							}
						} else {
							targetItem.setBlessType(0);
							targetItem.setBlessTypeValue(0);
							targetItem.setBless(1);
						}
						pc.getInventory().updateItem(targetItem, L1PcInventory.COL_BLESS_SCROLL);
						pc.getInventory().saveItem(targetItem, L1PcInventory.COL_BLESS_SCROLL);
						pc.getInventory().updateItem(targetItem, L1PcInventory.COL_ENCHANTLVL);
						pc.getInventory().removeItem(useitem, 1);
						pc.sendPackets(new S_SystemMessage(targetItem.getLogName() + "에 축복의 기운이 사라집니다."));
					} else {
						pc.sendPackets(new S_SystemMessage("축복이 부여되지 않았습니다."));
					}
				}	
				break;
			case ItemId.성장_릴:
				int item = targetItem.getItem().getItemId();
				if (item == ItemId.길고_무거운_낙싯대) {
					createNewItem(pc, ItemId.성장_낙싯대, 1);
					pc.getInventory().removeItem(targetItem, 1);
					pc.getInventory().removeItem(useitem, 1);
				}
				if (item == ItemId.성장_낙싯대) {
					if (targetItem.getChargeCount() >= 10000) {
						pc.sendPackets(new S_ServerMessage(3457)); // 더 이상 쾌속 릴을 사용할 수 없습니다.
						return;
					} else if (targetItem.getChargeCount() > 9900) {
						targetItem.setChargeCount(10000);
						pc.getInventory().updateItem(targetItem, L1PcInventory.COL_CHARGE_COUNT);
					} else {
						targetItem.setChargeCount(targetItem.getChargeCount() + 100);
						pc.getInventory().updateItem(targetItem, L1PcInventory.COL_CHARGE_COUNT);
					}
					pc.getInventory().removeItem(useitem, 1);
				}
				break;
			case ItemId.캐릭터_이름_변경:	
				pc.sendPackets(new S_SystemMessage(".이름변경 변경할 닉네임을 기입해주세요."));
				break;
				
			case ItemId.태고_옥쇄:
				if (client.getAccount().getCharSlot() < 8) {
					client.getAccount().setCharSlot(client, client.getAccount().getCharSlot() + 1);
					pc.getInventory().removeItem(useitem, 1);
					pc.sendPackets(new S_ServerMessage(1435));
				} else {
					pc.sendPackets(new S_ServerMessage(1540));
				}
				break;
				
				

				/////////       테베 관련       ////////// 
			case ItemId.균열의_핵: // 균열의 핵
				int itemid = targetItem.getItem().getItemId();
				if (itemid == ItemId.잠긴_하급_오시리스의_보물상자) {
					if (pc.getInventory().checkItem(ItemId.잠긴_하급_오시리스의_보물상자)) {
						pc.getInventory().removeItem(targetItem, 1);
						pc.getInventory().removeItem(useitem, 1);
						pc.getInventory().storeItem(ItemId.열린_하급_오시리스의_보물상자, 1);
					}
				} else if (itemid == ItemId.잠긴_상급_오시리스의_보물상자) {
					if (pc.getInventory().checkItem(ItemId.잠긴_상급_오시리스의_보물상자)) {
						pc.getInventory().removeItem(targetItem, 1);
						pc.getInventory().removeItem(useitem, 1);
						pc.getInventory().storeItem(ItemId.열린_상급_오시리스의_보물상자, 1);
					}
				} else if (itemid == ItemId.잠긴_하급_쿠쿨칸의_보물상자) {
					if (pc.getInventory().checkItem(ItemId.잠긴_하급_쿠쿨칸의_보물상자)) {
						pc.getInventory().removeItem(targetItem, 1);
						pc.getInventory().removeItem(useitem, 1);
						pc.getInventory().storeItem(ItemId.열린_하급_쿠쿨칸의_보물상자, 1);
					}
				} else if (itemid == ItemId.잠긴_상급_쿠쿨칸의_보물상자) {
					if (pc.getInventory().checkItem(ItemId.잠긴_상급_쿠쿨칸의_보물상자)) {
						pc.getInventory().removeItem(targetItem, 1);
						pc.getInventory().removeItem(useitem, 1);
						pc.getInventory().storeItem(ItemId.열린_상급_쿠쿨칸의_보물상자, 1);
					}
				} else {
					pc.sendPackets(new S_ServerMessage(79)); // \f1 아무것도
					                                        // 일어나지  않았습니다.
				}
				break;  

			case ItemId.자동_물약:
				if(Config.AUTOSYM) {
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
					for (ItemInstance items : pc.getInventory().getItems()) {
						for (Integer id : Config.ServerInfo.AUTO_POTION_LIST) {
							if (items.getItemId() == id) {
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
				} else {
					pc.sendPackets(new S_SystemMessage("현재 자동 물약 시스템이 off 상태입니다. "));
				}
				break;
			case ItemId.아이템_스왑:	
				long curtime = System.currentTimeMillis() / 1000;
				if (pc.getEquipChange() + 2 > curtime) { 
					return;
				}
				for (ItemInstance items : pc.getInventory().getItems()) {
					if (items.getItemId() == 20077 || items.getItemId() == 120077 || items.getItemId() == 20062) { // 투망일경우
						if (pc.isInvisble()) {
							pc.getInventory().setEquipped(items, false);
							pc.beginInvisTimer();
						} else {
							pc.getInventory().setEquipped(items, false);
						}
					}
				}
				if (!pc.EquipChange) { // 2번슬롯
					pc.setSlotNumber(1);
					pc.getChangeSlot(1);
					pc.EquipChange = true;
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "장비 셋트 2번으로 변경되었습니다."), 1));
				} else {
					pc.setSlotNumber(0);
					pc.getChangeSlot(0);
					pc.EquipChange = false;
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "장비 셋트 1번으로 변경되었습니다."), 1));
				}
				pc.setEquipChange(curtime);
				break;
			case ItemId.클래스_변경_군주: case ItemId.클래스_변경_기사: 
			case ItemId.클래스_변경_요정: case ItemId.클래스_변경_마법사: case ItemId.클래스_변경_다크엘프:
				if (pc.getClanid() != 0) { 
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[혈맹을 먼저 탈퇴하여 주시기 바랍니다.]"), 1));
					return;
				}
				int class_type = itemId - 51093;
				if (class_type == pc.getType()) { 
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[동일한 클래스로는 변경이 불가합니다.]"), 1));
					return;
				}
				pc.setType(class_type);
				pc.setClassId(L1PcInstance.CLASS_GFX_LIST[pc.getType()][pc.get_sex()]);
				IOReturnedStat(pc);

				DollInstance doll = pc.getMagicDoll();
				if (doll != null) {
					doll.deleteDoll();
				}
				if (pc.getWeapon() != null)
					pc.getInventory().setEquipped(pc.getWeapon(), false, false, false);

				for (ItemInstance armor : pc.getInventory().getItems()) {
					for (int type = 0; type <= 13; type++) {
						if (armor != null) {
							pc.getInventory().setEquipped(armor, false, false, false);

						}
					}
				}
				deleteSpell(pc);
				pc.getSlotItems(0).clear();
				pc.getSlotItems(1).clear();
				pc.setCurrentSprite(pc.getClassId());
				pc.sendPackets(new S_ChangeShape(pc.getId(), pc.getClassId()));
				Broadcaster.broadcastPacket(pc, new S_ChangeShape(pc.getId(), pc.getClassId()));
				pc.getInventory().removeItem(useitem, 1);
				pc.save();
				Thread.sleep(500);
				pc.sendPackets(new S_Disconnect());
				break;
			case ItemId.신화_변신_주문서:
				int polyId = 0;
				switch (pc.getType()) {			
				case 0:
					if (pc.get_sex() == 0)
						polyId = 18368;
					else
						polyId = 18372;
					break;
				case 1:
					if (pc.get_sex() == 0)
						polyId = 18376;
					else
						polyId = 18380;
					break;
				case 2:
					if (pc.get_sex() == 0)
						polyId = 18384;
					else
						polyId = 18388;
					break;
				case 3:
					if (pc.get_sex() == 0)
						polyId = 18392;
					else
						polyId = 18396;
					break;
				case 4:
					if (pc.get_sex() == 0)
						polyId = 18400;
					else
						polyId = 18404;
					break;
				}
				L1PolyMorph.doPoly(pc, polyId, 3600, L1PolyMorph.MORPH_BY_ITEMMAGIC);
				pc.getInventory().removeItem(useitem, 1);
				break;
				
				
			case ItemId.도펠갱어킹_변신_주문서:
				L1PolyMorph.doPoly(pc, 18422, 3600, L1PolyMorph.MORPH_BY_POLYCONTROLER);
				break;
			
				
			case ItemId.인형_번경_주문서:
				int dollId = targetItem.getItem().getItemId(); 
				DollInstance user_doll = pc.getMagicDoll();
				if(user_doll != null) {
					if(user_doll.getItemObjId() == targetItem.getId()) {
						pc.sendPackets(new S_SystemMessage("소환 중인 인형은 변경할 수 없습니다."));
						return;
					}
				}
				MagicDoll md = MagicDoll.get(dollId);
				if (md == null) {
					pc.sendPackets(new S_SystemMessage("변경할 수 없는 아이템입니다."));
					return;
				}
				ArrayList<Integer> createDollList = MagicDoll.getGrade(md.getInfo().getGrade());
				ItemInstance creat = ItemTable.getInstance().createItem(((Integer)createDollList.get(_random.nextInt(createDollList.size()))).intValue());
				pc.getInventory().storeItem(creat);
				pc.sendPackets(new S_ServerMessage(403, creat.getLogName()));
				pc.getInventory().removeItem(targetItem, 1);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.비취_물약: case ItemId.엔트_줄기:
				UsePoisonReleasePotions(pc, useitem);
				break;
			case ItemId.강제_칼질:
				if (pc.isCompulsionAttack()) {
					pc.setCompulsionAttack(false);  
					pc.sendPackets("강제 칼질을 비활성화 합니다.");
				} else {
					if (!pc.isCompulsionAttack()) {
						pc.setCompulsionAttack(true);
					} 
					pc.sendPackets("강제 칼질을 활성화 합니다.");
				}
				break;
			case ItemId.라우풀_물약:
				int pcLawful = pc.getLawful(); 
				if (pcLawful < 32767) {
					if (pcLawful + 30000 <= 32767) {
						pc.setLawful(pc.getLawful() + 30000);
					} else {
						pc.setLawful(32767);
					}
					pc.getInventory().removeItem(useitem, 1);
					pc.save();
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.카오틱_물약:
				if (pc.getLawful() > 0) {
					pc.setLawful(-30000);
					pc.sendPackets(new S_ServerMessage(674));
					pc.getInventory().removeItem(useitem, 1);
					pc.save();
				} else {
					pc.sendPackets(new S_SystemMessage("라우풀 성향에서만 사용하실 수 있습니다."));
				}
				break;
			case ItemId.킬_데스_초기화_주문서: case ItemId.데스_카운트_감소_주문서: case ItemId.킬_카운트_감소_주문서: 
				useKillDeath(pc, itemId, useitem);
				break;
			case CTLoadManager.CTSYSTEM_STORE_ID:
				if (Config.QUIZSYM && !pc.isGm()) {
					if (!pc.isQuizValidated()) {
						pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
						pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 시도해주세요."));
						return;
					}
				}
				Account account = pc.getAccount();
				account.setMJCT_Char_Name(pc.getName());
				C_NewCharSelect.restartProcess(pc);
				pc.getInventory().removeItem(useitem, 1);
				client.sendPacket(new S_CommonNews("캐릭터 봉인."
						+ "\n"
						+ "\n1.봉인 아이템을 사용한 캐릭터는"
						+ "\n  선택 할 수 없습니다."
						+ "\n"
						+ "\n2.봉인 하려는 캐릭터를 선택하면."
						+ "\n  봉인 아이템을 사용한 캐릭터로"
						+ "\n  접속 되며 인벤토리에 봉인 구슬이"
						+ "\n  자동 생성 됩니다."));
				break;
			case ItemId.봉인_주문서:
				if(targetItem.getBless() == 0 || targetItem.getBless() == 1 
				|| targetItem.getBless() == 2 ||  targetItem.getBless() == 3) {
					int Bless = 0;
					switch(targetItem.getBless()){
					case 0: Bless = 128; break; //축
					case 1: Bless = 129; break; //보통
					case 2: Bless = 130; break; //저주
					case 3: Bless = 131; break; //미확인
					}
					targetItem.setBless(Bless);
					pc.getInventory().updateItem(targetItem, L1PcInventory.COL_BLESS);
					pc.getInventory().saveItem(targetItem, L1PcInventory.COL_BLESS);
					pc.getInventory().removeItem(useitem, 1);
				}
				break;
			case ItemId.아데나_1억_수표:
				if (pc.getInventory().checkItemCount(ItemId.아데나) + 100000000 > 1800000000) {
					pc.sendPackets(new S_SystemMessage("소지한 아데나가 너무 많아 변환이 불가능 합니다."));
					return;
				} 
				pc.getInventory().storeItem(ItemId.아데나, 100000000); 
				pc.sendPackets(new S_SystemMessage("수표가 100,000,0000 아데나로 변환 되었습니다."));
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.아데나_수표_변환기:
				if (pc.getInventory().checkItem(ItemId.아데나, 100000000)) {
					pc.getInventory().consumeItem(ItemId.아데나, 100000000);
					pc.getInventory().storeItem(ItemId.아데나_1억_수표, 1);
					pc.sendPackets(new S_SystemMessage("100,000,000 아데나가 수표로 변경 되었습니다."));
				} else {
					pc.sendPackets(new S_SystemMessage("100,000,000 아데나가 부족하여 변환이 불가능 합니다."));
				}
				break;
			case ItemId.엘릭서_힘: case ItemId.엘릭서_콘: case ItemId.엘릭서_덱스:
			case ItemId.엘릭서_인트: case ItemId.엘릭서_위즈: case ItemId.엘릭서_카리:
				useItemElixir(pc, itemId, useitem);
				break;
			case ItemId.드래곤_진주:
				useDragonPearl(pc, useitem);
				break;
			case ItemId.자동_사냥:
				if (pc.getAutoHunt()) {
					pc.EndAutoController();
				} else {
					pc.StartAutoController();
				}
				break;
			case ItemId.인벤_자동_정리:
				InventoryArrangement(pc);
				break;
			case ItemId.회상_촛불:
				if (!pc.getMap().isSafetyZone(pc.getLocation())) {
					pc.sendPackets("안전한 지역에서만 사용할 수 있습니다.");
					return;
				}
				if (pc.getLevel() != pc.getHighLevel()) {
					return;
				}
				if (pc.getLevel() > 49) {
					pc.getInventory().consumeItem(ItemId.회상_촛불, 1);
					int locx2 = 32723 + CommonUtil.random(10);
					int locy2 = 32851 + CommonUtil.random(10);
					pc.teleport(locx2, locy2, (short) 5166, pc.getHeading(), 169, false, false);
					IOReturnedStat(pc);
				} else {
					pc.sendPackets(new S_SystemMessage("스텟초기화는 55레벨 이상만 가능합니다."));
				} 
				break;
			case ItemId.캐릭터_복구_주문서:
				Connection connection = null;
				connection = L1DatabaseFactory.getInstance().getConnection();
				PreparedStatement preparedstatement = connection.prepareStatement(
			"UPDATE characters SET LocX=33432,LocY=32807,MapID=4 WHERE account_name=? and MapID not in (5001,99,997,5166,39,38,34,701,2000)");
				preparedstatement.setString(1, client.getAccountName());
				preparedstatement.execute();
				preparedstatement.close();
				connection.close();
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_SystemMessage("캐릭터 좌표 복구가 완료되었습니다."));
				break;
			case ItemId.라스타바드_정문_이동_주문서:
				int[] loc1 = null;
				loc1 = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_RASTABAD_GATE);
				pc.teleport(loc1[0], loc1[1], (short) loc1[2], pc.getHeading(), 169, true, false);
				break;
			case ItemId.속도_향상_물약: case ItemId.축복_속도_향상_물약: case ItemId.강화_초록_물약: case ItemId.축복_강화_초록_물약:
			case ItemId.농축_속도_향상_물약: case ItemId.와인: case ItemId.위스키: case ItemId.상아탑_속도_향상_물약:
				useGreenPotion(pc, itemId);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.용기_물약: case ItemId.축복_용기_물약: case ItemId.농축_용기_물약: case ItemId.엘븐와퍼: 
			case ItemId.축복_엘븐와퍼: case ItemId.농축_집중_물약: case ItemId.악마_피: case ItemId.농축_악마_피:
				if (pc.isKnight() || pc.isElf() || pc.isCrown()) {
					useBravePotion(pc, itemId, useitem);
					pc.getInventory().removeItem(useitem, 1);
				} 
				break;
			case ItemId.랭킹_조회:
				for (L1Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcId() == 200001) {
							pc.sendPackets(new S_Board(npc));
							break;
						}
					}
				} 
				break;
			case ItemId.공지_사항:
				for (L1Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcId() == 81177) {
							pc.sendPackets(new S_Board(npc));
							break;
						}
					}
				} 
				break;
			case ItemId.아데나_거래:
				for (L1Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcId() == 81134) { 
							pc.sendPackets(new S_AuctionSystemBoard(npc)); 
							break;
						}
					}
				} 
				break;
			case ItemId.소생_주문서: case ItemId.영생_빛: case ItemId.생명_나뭇잎: case ItemId.축복_생명_나뭇잎:	
				MJItemEnchanterLoader.getInstance().do_enchant(pc, useitem, targetItem);
				break;
			case ItemId.송편:
				pc.sendPackets(new S_ServerMessage(338, "$1084"));
				pc.setCurrentMp(pc.getCurrentMp() + 20);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.쑥송편:
			case ItemId.정신력_물약:
				pc.sendPackets(new S_ServerMessage(338, "$1084"));
				pc.setCurrentMp(pc.getCurrentMp() + 50);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.에바_축복: case ItemId.인어_비늘: case ItemId.농축_호흡_물약:
				useBlessOfEva(pc, itemId);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.파란_물약: case ItemId.축복_파란_물약: case ItemId.농축_마력_물약:	
				useBluePotion(pc, itemId);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.지혜_물약: case ItemId.축복_지혜_물약: case ItemId.농축_지혜_물약:
				if (pc.isWizard()) {
					useWisdomPotion(pc, itemId);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.불투명_물약:
				useBlindPotion(pc);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.변신_주문서: case ItemId.축복_변신_주문서: case ItemId.상아탑_변신_주문서: case ItemId.농축_변신_물약:
				if (usePolyScroll(pc, itemId, s)) {	
					pc.getInventory().removeItem(useitem, 1); 
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_10)); 
				}
				break;
			case ItemId.어둠_비늘: case ItemId.열화_비늘: case ItemId.배덕자_비늘: case ItemId.증오_비늘:
			case ItemId.라버본_헤드_변신: case ItemId.라버본_솔져_변신: case ItemId.라버본_나이프_변신:
				usePolyScale(pc, itemId);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.숫돌:
				if (targetItem.getItem().getType2() != 0 && targetItem.getDurability() > 0) {
					String msg0;
					pc.getInventory().recoveryDamage(targetItem);
					msg0 = targetItem.getLogName();
					if (targetItem.getDurability() == 0) {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_11, msg0));
					} else {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_12, msg0)); 
					}
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.상아탑_저주_풀기_주문서: case ItemId.저주_풀기_주문서: case ItemId.축복_저주_풀기_주문서: case ItemId.원주민_토템:
				for (ItemInstance eachItem : pc.getInventory().getItems()) {
					if (eachItem.getItem().getBless() != 2) {
						continue;
					}
					if (!eachItem.isEquipped()
							&& (itemId == ItemId.저주_풀기_주문서 || itemId == ItemId.상아탑_저주_풀기_주문서)) {
						continue;
					}
					int id_normal = eachItem.getItemId() - 200000;
					L1Item template = ItemTable.getInstance().getTemplate(
							id_normal);
					if (template == null) {
						continue;
					}
					if (pc.getInventory().checkItem(id_normal) && template.isStackable()) {
						pc.getInventory().storeItem(id_normal, eachItem.getCount());
						pc.getInventory().removeItem(eachItem, eachItem.getCount());
					} else {
						eachItem.setItem(template);
						pc.getInventory().updateItem(eachItem,L1PcInventory.COL_ITEMID);
						pc.getInventory().saveItem(eachItem,L1PcInventory.COL_ITEMID);
						eachItem.setBless(eachItem.getBless() - 1);
						pc.getInventory().updateItem(eachItem, L1PcInventory.COL_BLESS);
						pc.getInventory().saveItem(eachItem, L1PcInventory.COL_BLESS);
					}
				}
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_13));
				break;
			case ItemId.확인_주문서: case ItemId.상아탑_확인_주문서:
				if (!targetItem.isIdentified()) {
					targetItem.setIdentified(true);
					pc.getInventory().updateItem(targetItem, L1PcInventory.COL_IS_ID);
				}
				pc.sendPackets(new S_IdentifyDesc(targetItem));
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.풀:
				int diaryId = targetItem.getItem().getItemId();
				if (diaryId >= 41038 && 41047 >= diaryId) {
					if (_random.nextInt(100) < 33) {
						createNewItem(pc, diaryId + 10, 1);
					}
					pc.getInventory().removeItem(targetItem, 1);
					pc.getInventory().removeItem(useitem, 1);
				}
				break;
					
			case ItemId.흑마법_가루:
				int historyId = targetItem.getItem().getItemId();
				if (historyId >= 41011 && 41017 >= historyId) {
					if (_random.nextInt(100) < 20) {
						createNewItem(pc, historyId + 8, 1);
					}
					pc.getInventory().removeItem(targetItem, 1);
					pc.getInventory().removeItem(useitem, 1);
				} else if (historyId == 41018) {
					if (_random.nextInt(100) < 15) {
						createNewItem(pc, historyId + 8, 1);
					}
					pc.getInventory().removeItem(targetItem, 1);
					pc.getInventory().removeItem(useitem, 1);
				}
				break;
			case ItemId.소환구_조각:
				int shId = targetItem.getItem().getItemId();
				if (shId == ItemId.소환구_핵) {
					createNewItem(pc, 41031, 1);
					pc.getInventory().removeItem(targetItem, 1);
					pc.getInventory().removeItem(useitem, 1);
				} else if (shId >= 41031 && 41034 >= shId) {
					createNewItem(pc, shId + 1, 1);
					pc.getInventory().removeItem(targetItem, 1);
					pc.getInventory().removeItem(useitem, 1);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.개봉_편지지: case ItemId.개봉_혈맹_편지지:
				pc.sendPackets(new S_Letter(useitem));
				break;
			case ItemId.일반_펫_목걸이: case ItemId.하이펫_목걸이:
				if (pc.getInventory().checkItem(ItemId.호출_피리)) {
					if (withdrawPet(pc, itemObjid)) {
						pc.getInventory().consumeItem(ItemId.호출_피리, 1);
					}
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.펫_호루라기:
				pc.sendPackets(new S_Sound(437));
				pc.broadcastPacket(new S_Sound(437));
				Object[] petList = pc.getPetList().values().toArray();
				for (Object petObject : petList) {
					if (petObject instanceof PetInstance) { // 펫
						PetInstance pet = (PetInstance) petObject;
						pet.call();
					}
				}
				break;
			case ItemId.마법_플롯:
				pc.sendPackets(new S_Sound(165));
				pc.broadcastPacket(new S_Sound(165));
				for (L1Object visible : pc.getKnownObjects()) {
					if (visible instanceof L1GuardianInstance) {
						L1GuardianInstance guardian = (L1GuardianInstance) visible;
						if (guardian.getNpcTemplate().get_npcId() == 70850) { // 빵
							if (createNewItem(pc, 88, 1)) {
								pc.getInventory().removeItem(
										useitem, 1);
							}
						}
					}
				}
				break;
			case ItemId.부활_주문서: case ItemId.축복_부활_주문서:
				useResurrectionScroll(pc, itemId, resid);
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.귀환_주문서: case ItemId.상아탑_귀환_주문서:	
				if (pc.getMap().isEscapable() || pc.isGm()) {
					int[] loc = Getback.GetBack_Location(pc, true);
					L1Location location = new L1Location(loc[0], loc[1], (short) loc[2]);
					location = location.randomLocation(1, 3, true);
					
					pc.teleportScoll(location.getX(), location.getY(), (short) location.getMapId(), pc.getHeading(), true, 169);
					
					pc.getInventory().removeItem(useitem, 1);
				} else {
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_6));
				}
				SkillUtil.cancelBarrier(pc);
				break;
			case ItemId.혈맹_귀환_주문서:
				if (pc.getMap().isEscapable() || pc.isGm()) {
					int castle_id = 0;
					int house_id = 0;
					if (pc.getClanid() != 0) { // 혈맹 소속
						L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
						if (clan != null) {
							castle_id = clan.getCastleId();
							house_id = clan.getHouseId();
						}
					}
					if (castle_id != 0) { // 성 혈맹원일 경우 
						if (pc.getMap().isEscapable() || pc.isGm()) {
							int[] loc = new int[3];
							loc = L1CastleLocation.getCastleLoc(castle_id);
							int locx = loc[0];
							int locy = loc[1];
							short mapid = (short) (loc[2]);
							pc.teleport(locx, locy, mapid, pc.getHeading(),169, true, false);
							pc.getInventory().removeItem(useitem, 1);
						} else {
							pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
							pc.sendPackets(new S_ServerMessage(647));
						}
					} else if (house_id != 0) { // 아지트 소유했을 경우 
						if (pc.getMap().isEscapable() || pc.isGm()) {
							int[] loc = new int[3];
							loc = L1HouseLocation.getHouseLoc(house_id);
							int locx = loc[0];
							int locy = loc[1];
							short mapid = (short) (loc[2]);
							pc.teleport(locx, locy, mapid, pc.getHeading(),169, true, false);
							pc.getInventory().removeItem(useitem, 1);
						} else {
							pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
							pc.sendPackets(new S_ServerMessage(647));
						}
					} else { // 그게 아니면
						if (pc.getHomeTownId() > 0) {
							int[] loc = L1TownLocation.getGetBackLoc(pc
									.getHomeTownId());
							int locx = loc[0];
							int locy = loc[1];
							short mapid = (short) (loc[2]);
							pc.teleport(locx, locy, mapid, pc.getHeading(), 169, true, false);
							pc.getInventory().removeItem(useitem, 1);
						} else {
							int[] loc = Getback.GetBack_Location(pc, true);
							L1Location location = new L1Location(loc[0], loc[1], (short) loc[2]);
							location = location.randomLocation(1, 5, true);
							pc.teleport(location.getX(), location.getY(), (short) location.getMapId() , pc.getHeading(),169, true, false);
							pc.getInventory().removeItem(useitem, 1);
						}
					}
				} else {
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_6));
				}
				// 앱솔루트 해제 
				SkillUtil.cancelBarrier(pc);
				break;
			case ItemId.축복_순간_이동_주문서: case ItemId.순간_이동_주문서: case ItemId.상아탑_순간_이동_주문서: case ItemId.마법_주문서_텔레포트:
				L1BookMark bookm = pc.getBookMark(btele); 
				if (bookm != null) { // 북마크를 취득 할 수 있으면(자) 텔레포트 
					if (pc.getMap().isEscapable() || pc.isGm()) {
						int newX = bookm.getLocX();
						int newY = bookm.getLocY();
						short mapId = bookm.getMapId();

						if (mapId != pc.getMapId() || newX != pc.getX() || newY != pc.getY()) {
							L1Location newLocation = new L1Location(newX, newY, mapId);

							newLocation = L1Location.randomNearLocation(newLocation);
							newX = newLocation.getX();
							newY = newLocation.getY();
						}
						if (L1Map.isTeleportable(newX, newY, mapId)) {
							pc.teleportScoll(newX, newY, mapId, pc.getHeading(), true, 169);
							pc.getInventory().removeItem(useitem, 1);
						} else {
							pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
							pc.sendPackets(new S_SystemMessage("기억된 곳이 귀환 할 수 없는 장소입니다."));
						}
					} else {
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
					}

				} else if (MapRingTelTable.getInstance().isMapZoneChange(pc.getMapId())) {
					ItemInstance findItem = pc.getInventory().findItemId(202811);
					if (findItem != null) {
						Teleport.randomTeleport(pc, true);
						pc.getInventory().removeItem(useitem, 1);
					} else {
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_14));
					}
				} else {
					if (pc.getMap().isTeleportable() || pc.isGm()) {
						if (!L1Map.isTeleportable(pc.getLocation().getX(), pc.getLocation().getY(),
								pc.getLocation().getMapId())) {
							pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
							pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_14));
						} else {
							L1Location newLocation = pc.getLocation().randomLocation(0, 200, true);
							int newX = newLocation.getX();
							int newY = newLocation.getY();
							short mapId = (short) newLocation.getMapId();

							int count = 0;

							while (count++ < 50) {
								if (L1Map.isTeleportable(newX, newY, mapId)) {
									break;
								}
								newLocation = pc.getLocation().randomLocation(0, 200, true);
								newX = newLocation.getX();
								newY = newLocation.getY();
								mapId = (short) newLocation.getMapId();
							}
							if (!L1Map.isTeleportable(pc.getLocation().getX(), pc.getLocation().getY(),
									pc.getLocation().getMapId())) { 
								pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_14));
								pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
							} else { 
								pc.teleportScoll(newX, newY, mapId, pc.getHeading(), true, 169);
								pc.getInventory().removeItem(useitem, 1);
							}
						}
					} else { 
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_14));
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					}
				}
				// 앱솔루트 해제 
				SkillUtil.cancelBarrier(pc);
				break;
			case ItemId.번개_소환_막대: case ItemId.이계_번개_소환_막대: case ItemId.축복_이계_번개_소환_막대:
				SpellWand spell = SpellWand.get(itemId);
				if (spell != null) {
					spell.use(pc, useitem, spellsc_objid, spellsc_x, spellsc_y);
				} else {
					pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
				}
				break;
			case ItemId.저주_순간_이동_주문서:
				pc.teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
				pc.getInventory().removeItem(useitem, 1);
				SkillUtil.cancelBarrier(pc);
				break;
			case ItemId.비밀_방_열쇠:
				if (pc.isKnight()
						&& (pc.getX() >= 32806 && // 오림 방
						pc.getX() <= 32814)
						&& (pc.getY() >= 32798 && pc.getY() <= 32807)
						&& pc.getMapId() == 13) {
					short mapid = 13;
					pc.teleport(32815, 32810, mapid, pc.getHeading(), 169, false, true);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));

				}
				break;
			case ItemId.정령_결정:
				if ((pc.getX() >= 32665 && // 해적섬
				pc.getX() <= 32674)
				&& (pc.getY() >= 32976 && pc.getY() <= 32985)
				&& pc.getMapId() == 440) {
					short mapid = 430;
					pc.teleport(32922, 32812, mapid, pc.getHeading(), 169, true, true);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.단풍_나무_막대: case ItemId.블랙_엔트_껍질:
				S_AttackStatus ss = S_AttackStatus.get(pc, 0, ActionCodes.ACTION_Wand);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
				int chargeCount = useitem.getChargeCount();
				if (chargeCount <= 0 && itemId != ItemId.블랙_엔트_껍질) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
					return;
				} 
				if (pc.getMap().isSafetyZone(pc.getLocation())) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
					return;
				} 

				L1Object target = L1World.getInstance().findObject(spellsc_objid);
				if (target != null) {
					L1Character cha = (L1Character) target;
					polyAction(pc, cha);
					// 앱솔루트 해제 
					SkillUtil.cancelBarrier(pc);
					if (itemId == ItemId.단풍_나무_막대) {
						useitem.setChargeCount(useitem.getChargeCount() - 1);
						pc.getInventory().updateItem(useitem, L1PcInventory.COL_CHARGE_COUNT);
					} else {
						pc.getInventory().removeItem(useitem, 1);
					}
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.마법주사위_2단계:
				int gfxid = 3237 + _random.nextInt(2);
				S_SkillSound s1 = S_SkillSound.get(pc.getId(), gfxid);
				pc.sendPackets(s1);
				pc.broadcastPacket(s1);
				pc.getInventory().consumeItem(ItemId.마력_돌, 1);
				break;
			case ItemId.마법주사위_3단계:
				gfxid = 3241 + _random.nextInt(3);
				S_SkillSound s2 = S_SkillSound.get(pc.getId(), gfxid);
				pc.sendPackets(s2);
				pc.broadcastPacket(s2);
				pc.getInventory().consumeItem(ItemId.마력_돌, 1);
				break;
			case ItemId.마법주사위_4단계:	
				gfxid = 3241 + _random.nextInt(4);
				S_SkillSound s3 = S_SkillSound.get(pc.getId(), gfxid);
				pc.sendPackets(s3);
				pc.broadcastPacket(s3);
				pc.getInventory().consumeItem(ItemId.마력_돌, 1);
				break;
			case ItemId.마법주사위_6단계:	
				gfxid = 3204 + _random.nextInt(6);
				S_SkillSound s4 = S_SkillSound.get(pc.getId(), gfxid);
				pc.sendPackets(s4);
				pc.broadcastPacket(s4);
				pc.getInventory().consumeItem(ItemId.마력_돌, 1);
				break;
			case ItemId.붉은빛_나는_물고기:
				int chance = _random.nextInt(10);
				if (chance >= 0 && chance < 9) {
					createNewItem(pc, 40019, 1);
				} else if (chance >= 9) {
					int gemChance = _random.nextInt(3);
					if (gemChance == 0) {
						createNewItem(pc, 40045, 1);
					} else if (gemChance == 1) {
						createNewItem(pc, 40049, 1);
					} else if (gemChance == 2) {
						createNewItem(pc, 40053, 1);
					}
				}
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.초록빛_나는_물고기:
				int chance1 = _random.nextInt(3);
				if (chance1 >= 0 && chance1 < 9) {
					createNewItem(pc, 40018, 1);
				} else if (chance1 >= 9) {
					int gemChance = _random.nextInt(3);
					if (gemChance == 0) {
						createNewItem(pc, 40047, 1);
					} else if (gemChance == 1) {
						createNewItem(pc, 40051, 1);
					} else if (gemChance == 2) {
						createNewItem(pc, 40055, 1);
					}
				}
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.파란빛_나는_물고기:
				int chance2 = _random.nextInt(3);
				if (chance2 >= 0 && chance2 < 9) {
					createNewItem(pc, 40015, 1);
				} else if (chance2 >= 9) {
					int gemChance = _random.nextInt(3);
					if (gemChance == 0) {
						createNewItem(pc, 40046, 1);
					} else if (gemChance == 1) {
						createNewItem(pc, 40050, 1);
					} else if (gemChance == 2) {
						createNewItem(pc, 40054, 1);
					}
				}
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.흰빛_나는_물고기:
				int chance3 = _random.nextInt(3);
				if (chance3 >= 0 && chance3 < 9) {
					createNewItem(pc, 40021, 1);
				} else if (chance3 >= 9) {
					int gemChance = _random.nextInt(3);
					if (gemChance == 0) {
						createNewItem(pc, 40044, 1);
					} else if (gemChance == 1) {
						createNewItem(pc, 40048, 1);
					} else if (gemChance == 2) {
						createNewItem(pc, 40052, 1);
					}
				}
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.그림자_신전_2층_열쇠:
				if ((pc.getX() >= 32701 && pc.getX() <= 32705) && (pc.getY() >= 32894 && pc.getY() <= 32898)
						&& pc.getMapId() == 522) {
					pc.teleport(((L1EtcItem) useitem.getItem()).get_locx(),
							((L1EtcItem) useitem.getItem()).get_locy(),
							((L1EtcItem) useitem.getItem()).get_mapid(), pc.getHeading(), 169, true, true);
				} else {
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.그림자_신전_3층_열쇠: case ItemId.그림자_신전_3층_열쇠_2: case ItemId.그림자_신전_3층_열쇠_3:	
				if ((pc.getX() >= 32698 && pc.getX() <= 32702) && (pc.getY() >= 32894 && pc.getY() <= 32898)
						&& pc.getMapId() == 523) {
					pc.teleport(((L1EtcItem) useitem.getItem()).get_locx(),
							((L1EtcItem) useitem.getItem()).get_locy(),
							((L1EtcItem) useitem.getItem()).get_mapid(), pc.getHeading(), 169, true, true);
				} else {
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}	
				break;
			case ItemId.완성된_보물_지도:
				if (pc.getInventory().checkItem(ItemId.드레이크_열쇠)) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				} else if ((pc.getX() >= 32856 && pc.getX() <= 32858) && (pc.getY() >= 32857 && pc.getY() <= 32858)
						&& pc.getMapId() == 443) { // 해적섬의 지하 감옥 3층
					pc.teleport(((L1EtcItem) useitem.getItem()).get_locx(),
							((L1EtcItem) useitem.getItem()).get_locy(),
							((L1EtcItem) useitem.getItem()).get_mapid(), pc.getHeading(),169, true, true);
				} else {
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
				}
				break;
			case ItemId.스톰워크:
				if(pc.isGm()) {
					pc.teleport(spellsc_x, spellsc_y, pc.getMapId(), pc.getHeading(), 2235, true, false); 
				} else {
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
				}			
				break;
			case ItemId.길고_무거운_낙싯대: case ItemId.성장_낙싯대: case ItemId.짧고_가벼운_낙싯대: case ItemId.고급_성장_낙싯대:
				if (pc.getMapId() != 5124) {
					pc.sendPackets(new S_ServerMessage(1138));
					return;
				}
				startFishing(pc, useitem, fishX, fishY, itemObjid);
				break;
			case ItemId.용해제:
				useResolvent(pc, targetItem, useitem);
				break;
			case ItemId.물품_판매_인증서: 
				SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
				Date currentTime = new Date();
				String dTime = formatter.format(currentTime);
				if (!targetItem.getItem().isTradable()) {
					pc.sendPackets(new S_SystemMessage("교환불가능 아이템은 판매 할수 없습니다."));
					return;
				}
				if (targetItem.getDurability() > 0) {
					pc.sendPackets(new S_SystemMessage("손상된 아이템은 판매가 불가합니다."));
					return;
				}
				if (targetItem.isEquipped()) {
					pc.sendPackets(new S_SystemMessage("착용중인 아이템은 판매 할수 없습니다."));
					return;
				}
				if (targetItem.getBless() > 3) {
					pc.sendPackets(new S_SystemMessage("봉인된 아이템은 판매 할 수 없습니다."));
					return;
				}
				if (targetItem.getEndTime() != null) {
					pc.sendPackets(new S_SystemMessage("시간제 아이템은 판매 할 수 없습니다."));
					return;
				}

				Object[] dollList1 = pc.getDollList().values().toArray();
				for (Object dollObject : dollList1) {
					if (dollObject instanceof DollInstance) {
						DollInstance doll1 = (DollInstance) dollObject;
						if (targetItem.getId() == doll1.getItemObjId()) {
							pc.sendPackets(new S_SystemMessage("소환중인 인형은 판매할 수 없습니다."));
							return;
						}
					}
				}

				if (targetItem.getItem().getType2() == 1 || targetItem.getItem().getType2() == 2
						|| targetItem.getItem().getType2() == 0 && (targetItem.getItem().getType() == 10)
						|| (targetItem.getItem().getItemId() >= 950000
						&& targetItem.getItem().getItemId() <= 950019)
						|| (targetItem.getItem().getItemId() >= 4100007
						&& targetItem.getItem().getItemId() <= 4100010)
						|| (targetItem.getItem().getItemId() == 900075)
						|| (targetItem.getItem().getItemId() >= 510219
						&& targetItem.getItem().getItemId() <= 510222)
						|| (targetItem.getItem().getItemId() >= 742 && targetItem.getItem().getItemId() <= 746)
						|| (targetItem.getItem().getItemId() >= 755 && targetItem.getItem().getItemId() <= 757)
						|| (targetItem.getItem().getItemId() >= 3000086
						&& targetItem.getItem().getItemId() <= 3000088)
						|| (targetItem.getItem().getItemId() >= 3000351
						&& targetItem.getItem().getItemId() <= 3000352)
						|| (targetItem.getItem().getItemId() == 447016)
						|| (targetItem.getItem().getItemId() == 447017)
						|| (targetItem.getItem().getItemId() == 410173)) {
					int count = pc.getInventory().countItems(ItemId.물품_판매_인증서);
					pc.getInventory().removeItem(useitem, count);
					pc.getInventory().removeItem(targetItem, 1);
					ItemSellList.getInstance().writeTopic(pc, dTime, targetItem, count);

					if (targetItem.getItem().getType2() == 0 && targetItem.getItem().getUseType() == 73) {
						pc.sendPackets(new S_SystemMessage("[" + targetItem.getName() + "\\fH] 물품이 게시판에 등록되었습니다."));
					} else if (targetItem.getItem().getType2() == 1) {
						pc.sendPackets(new S_SystemMessage("[" + targetItem.getLogName() + "\\fH] 물품이 게시판에 등록되었습니다."));
					} else {
						pc.sendPackets(new S_SystemMessage("[" + targetItem.getName() + "\\fH] 물품이 게시판에 등록되었습니다."));
					}
					int 억 = count;
					int A = 억 / 100000000;// 억단위만 표시 1억
					int a = A * 100000000;
					int B = count - a; // 억금액에서 억단위뺀 천만단위금액 5151545151

					int b = B / 10000000; // 천단위만 // 5
					int bb = b * 10000000; // 5천만원 // 5000000

					int c = B - bb; // 15551554 백만단위
					int cc = c / 1000000;
					int ccc = cc * 1000000;

					for (L1PcInstance pc1 : L1World.getInstance().getAllPlayers()) {
						if (pc1.물품멘트) {
							if (targetItem.getItem().getType2() == 0 && targetItem.getItem().getUseType() == 73) {

								pc1.sendPackets(new S_SystemMessage("\\f:========== 해당물품이 등록되었습니다 ========== "));
								pc1.sendPackets(new S_SystemMessage("\\f:판매물품: [" + targetItem.getName() + "\\f:] "));
								if (count >= 100000000) {
									if (B == 0 || B == 0 && ccc == 0) {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + A + "억 "));
									} else if (ccc == 0) {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + A + "억 " + b + "천"));
									} else {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + A + "억 " + b + "천 " + cc + "백"));
									}
								} else {
									if (ccc == 0) {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + b + "천만원 "));
									} else {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + b + "천 " + cc + "백만원"));
									}
								}

							} else if (targetItem.getItem().getType2() == 1) {

								pc1.sendPackets(new S_SystemMessage("\\f:========== 해당물품이 등록되었습니다 ========== "));
								pc1.sendPackets(
										new S_SystemMessage("\\f:판매물품: [" + targetItem.getLogName() + "\\f:] "));
								if (count >= 100000000) {
									if (B == 0 || B == 0 && ccc == 0) {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + A + "억 "));
									} else if (ccc == 0) {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + A + "억 " + b + "천"));
									} else {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + A + "억 " + b + "천 " + cc + "백"));
									}
								} else {
									if (ccc == 0) {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + b + "천만원 "));
									} else {
										pc1.sendPackets(new S_SystemMessage("판매금액: \\aA" + b + "천 " + cc + "백만원"));
									}
								}

							} else {

								if (targetItem.getItem().getType2() == 0
										&& targetItem.getItem().getType() == 10) {
									pc1.sendPackets(new S_SystemMessage("\\f:========== 해당물품이 등록되었습니다 ========== "));
									pc1.sendPackets(
											new S_SystemMessage("\\f:판매물품: [" + targetItem.getName() + "\\f:] "));
									if (count >= 100000000) {
										if (B == 0 || B == 0 && ccc == 0) {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + A + "억 "));
										} else if (ccc == 0) {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + A + "억 " + b + "천"));
										} else {
											pc1.sendPackets(
													new S_SystemMessage("\\f:판매금액: \\aA" + A + "억 " + b + "천 " + cc + "백"));
										}
									} else {
										if (ccc == 0) {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + b + "천만원 "));
										} else {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + b + "천 " + cc + "백만원"));
										}
									}

								} else {
									pc1.sendPackets(new S_SystemMessage("\\f:========== 해당물품이 등록되었습니다 ========== "));
									pc1.sendPackets(
											new S_SystemMessage("\\f:판매물품: [" + targetItem.getLogName() + "\\f:] "));
									if (count >= 100000000) {
										if (B == 0 || B == 0 && ccc == 0) {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + A + "억 "));
										} else if (ccc == 0) {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + A + "억 " + b + "천"));
										} else {
											pc1.sendPackets(
													new S_SystemMessage("\\f:판매금액: \\aA" + A + "억 " + b + "천 " + cc + "백"));
										}
									} else {
										if (ccc == 0) {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + b + "천만원 "));
										} else {
											pc1.sendPackets(new S_SystemMessage("\\f:판매금액: \\aA" + b + "천 " + cc + "백만원"));
										}
									}

								}
							}
						}
					}
				} else {
					pc.sendPackets(new S_SystemMessage("----------------[판매가능품목]----------------"));
					pc.sendPackets(new S_SystemMessage("무기/방어구/3단인형~5단/마법서"));
					pc.sendPackets(new S_SystemMessage("-------------------------------------------"));
					pc.sendPackets(new S_SystemMessage("판매할 수 없는 물품입니다."));
				}
				break;
			case ItemId.장착:    
				for (L1Object object : L1World.getInstance().getVisibleObjects(pc)) {
					if (object instanceof EffectInstance) {
						if (((L1NpcInstance) object).getNpcTemplate().get_npcId() == 81170) {
							// 이미 주위에 모닥불이 있습니다.
							pc.sendPackets(new S_ServerMessage(1162));
							return;
						}
					}
				}
				int[] loc = new int[2];
				loc = pc.getFrontLoc();
				L1EffectSpawn.getInstance().spawnEffect(81170, 600000, loc[0], loc[1], pc.getMapId());
				pc.getInventory().removeItem(useitem, 1);
				break;
			case ItemId.가구_제거_막대:
				useFurnitureRemovalWand(pc, spellsc_objid, useitem);
				break;
			case ItemId.화면_전체_부활:
				int objid = pc.getId();
				pc.setCurrentHp(pc.getMaxHp());
				for (L1PcInstance tg : L1World.getInstance().getVisiblePlayer(pc)) {
					if (tg.getCurrentHp() == 0 && tg.isDead()) {
						tg.setTempID(objid);
						tg.sendPackets(new S_Message_YN(322, "")); 
					} else {
						tg.setCurrentHp(tg.getMaxHp());
						tg.setCurrentMp(tg.getMaxMp());
					}
				}
				break;
			case ItemId.화면_몬스터_정리:	
				for (L1Object obj : L1World.getInstance().getVisibleObjects(pc, 17)) {
					if (obj instanceof MonsterInstance) { // 몬스터라면
						MonsterInstance npc = (MonsterInstance) obj;
						npc.receiveDamage(pc, 50000); // 대미지
					}
				}
				break;
			case ItemId.엔피시_확인_막대:	
				useFieldObjectIdentyWand(pc, spellsc_objid, useitem);
				break;
			case ItemId.엔피시_제거_막대:	
				useFieldObjectRemovalWand(pc, spellsc_objid, useitem);
				break;
			case ItemId.몬스터_드랍_확인:	
				monsterDrobConfItem(pc, spellsc_objid, useitem);
				break;
			default:
				break;
			}

			if (itemId >= 40080 && itemId <= 40085 || itemId >= 40102 && itemId <= 40118
					|| itemId >= 40120 && itemId <= 40123 || itemId == 40101 || itemId == 41159
					|| itemId == 40125 || itemId >= 40801 && itemId <= 40898 || itemId >= 42001
					&& itemId <= 42033 || itemId >= 42035 && itemId <= 42100) {
				int locX = ((L1EtcItem) useitem.getItem()).get_locx();
				int locY = ((L1EtcItem) useitem.getItem()).get_locy();
				short mapId = ((L1EtcItem) useitem.getItem()).get_mapid();
				if (locX != 0 && locY != 0) {
					if (pc.getMap().isEscapable() || pc.isGm()) {  
						pc.teleportScoll(locX, locY, (short) mapId, pc.getHeading(), true, 169);
					//	pc.teleport(locX, locY, mapId, pc.getHeading(), 169, true, false);
						pc.getInventory().removeItem(useitem, 1);  
					} else {
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
						pc.sendPackets(new S_ServerMessage(647));
					}
					// 앱솔루트 해제 
					SkillUtil.cancelBarrier(pc);
				} 
			} else if (itemId >= 40901 && itemId <= 40908) { // 각종 약혼 반지
				L1PcInstance partner = null;
				boolean partner_stat = false;
				if (pc.getPartnerId() != 0) { // 결혼중
					partner = (L1PcInstance) L1World.getInstance()
							.findObject(pc.getPartnerId());
					if (partner != null && partner.getPartnerId() != 0
							&& pc.getPartnerId() == partner.getId()
							&& partner.getPartnerId() == pc.getId()) {
						partner_stat = true;
					}
				} else {
					pc.sendPackets(new S_ServerMessage(662));
					return;
				}
				if (partner_stat) {
					boolean castle_area = L1CastleLocation.checkInAllWarArea(
							partner.getX(), partner.getY(),
							partner.getMapId());
					if ((partner.getMapId() == 0 || partner.getMapId() == 4 || partner
							.getMapId() == 304) && !castle_area) {
						pc.teleport(partner.getX(),
								partner.getY(), partner.getMapId(),
								partner.getHeading(),169, true, false);
					} else {
						pc.sendPackets(new S_ServerMessage(547));
					}
				} else {
					pc.sendPackets(new S_ServerMessage(546)); 
				}
			} else if (itemId >= 40289 && itemId <= 40297 || itemId == 43397) { 
				omanTeleportAmulet(pc, itemId, useitem);	
			} else if (itemId >= 40280 && itemId <= 40288) {
				pc.getInventory().removeItem(useitem, 1);
				ItemInstance item = pc.getInventory().storeItem(itemId + 9, 1);
				if (item != null) {
					pc.sendPackets(new S_ServerMessage(403, item.getLogName()));
				}
			} else if (itemId >= 40136 && itemId <= 40161) { // 불꽃
				int soundid = 3198;
				if (itemId == 40154) {
					soundid = 3198;
				} else if (itemId == 40152) {
					soundid = 2031;
				} else if (itemId == 40141) {
					soundid = 2028;
				} else if (itemId == 40160) {
					soundid = 2030;
				} else if (itemId == 40145) {
					soundid = 2029;
				} else if (itemId == 40159) {
					soundid = 2033;
				} else if (itemId == 40151) {
					soundid = 2032;
				} else if (itemId == 40161) {
					soundid = 2037;
				} else if (itemId == 40142) {
					soundid = 2036;
				} else if (itemId == 40146) {
					soundid = 2039;
				} else if (itemId == 40148) {
					soundid = 2043;
				} else if (itemId == 40143) {
					soundid = 2041;
				} else if (itemId == 40156) {
					soundid = 2042;
				} else if (itemId == 40139) {
					soundid = 2040;
				} else if (itemId == 40137) {
					soundid = 2048;
				} else if (itemId == 40136) {
					soundid = 2046;
				} else if (itemId == 40138) {
					soundid = 2047;
				} else if (itemId == 40140) {
					soundid = 2051;
				} else if (itemId == 40144) {
					soundid = 2053;
				} else if (itemId == 40147) {
					soundid = 2045;
				} else if (itemId == 40149) {
					soundid = 2034;
				} else if (itemId == 40150) {
					soundid = 2055;
				} else if (itemId == 40153) {
					soundid = 2038;
				} else if (itemId == 40155) {
					soundid = 2044;
				} else if (itemId == 40157) {
					soundid = 2035;
				} else if (itemId == 40158) {
					soundid = 2049;
				} else {
					soundid = 3198;
				}

				S_SkillSound s_skillsound = S_SkillSound.get(pc.getId(), soundid);
				pc.sendPackets(s_skillsound);
				pc.broadcastPacket(s_skillsound);
				pc.getInventory().removeItem(useitem, 1);
			} else if (itemId >= 41357 && itemId <= 41382) { // 알파벳 불꽃
				int soundid = itemId - 34946;
				S_SkillSound s_skillsound = S_SkillSound.get(pc.getId(), soundid);
				pc.sendPackets(s_skillsound);
				pc.broadcastPacket(s_skillsound);
				pc.getInventory().removeItem(useitem, 1);
			} else if (itemId == 41146) { // 드로몬드의 초대장
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei001"));
			} else if (itemId == 41209) { // 포피레아의 의뢰서
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei002"));
			} else if (itemId == 41210) { // 연마재
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei003"));
			} else if (itemId == 41212) { // 특제 캔디
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei005"));
			} else if (itemId == 41213) { // 티미의 바스켓
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei006"));
			} else if (itemId == 41214) { // 운의 증거
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei012"));
			} else if (itemId == 41215) { // 지의 증거
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei010"));
			} else if (itemId == 41216) { // 력의 증거
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei011"));
			} else if (itemId == 41222) { // 마슈르
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei008"));
			} else if (itemId == 41223) { // 무기의 파편
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei007"));
			} else if (itemId == 41224) { // 배지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei009"));
			} else if (itemId == 41225) { // 케스킨의 발주서
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei013"));
			} else if (itemId == 41226) { // 파고의 약
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei014"));
			} else if (itemId == 41227) { // 알렉스의 소개장
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei033"));
			} else if (itemId == 41228) { // 율법박사의 부적
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei034"));
			} else if (itemId == 41229) { // 스켈리턴의 머리
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei025"));
			} else if (itemId == 41230) { // 지난에의 편지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei020"));
			} else if (itemId == 41231) { // 맛티에의 편지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei021"));
			} else if (itemId == 41233) { // 케이이에의 편지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei019"));
			} else if (itemId == 41234) { // 뼈가 들어온 봉투
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei023"));
			} else if (itemId == 41235) { // 재료표
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei024"));
			} else if (itemId == 41236) { // 본아챠의 뼈
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei026"));
			} else if (itemId == 41237) { // 스켈리턴 스파이크의 뼈
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei027"));
			} else if (itemId == 41239) { // 브트에의 편지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei018"));
			} else if (itemId == 41240) { // 페다에의 편지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "ei022"));
			} else if (itemId == 41060) { // 노나메의 추천서
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "nonames"));
			} else if (itemId == 41061) { // 조사단의 증서：에르프 지역 두다마라카메
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "kames"));
			} else if (itemId == 41062) { // 조사단의 증서：인간 지역 네르가바크모
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "bakumos"));
			} else if (itemId == 41063) { // 조사단의 증서：정령 지역 두다마라브카
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "bukas"));
			} else if (itemId == 41064) { // 조사단의 증서：오크 지역 네르가후우모
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "huwoomos"));
			} else if (itemId == 41065) { // 조사단의 증서：조사단장 아트바노아
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "noas"));
			} else if (itemId == 41356) { // 파룸의 자원 리스트
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "rparumletter"));
			} else if (itemId == 40701) { // 작은 보물의 지도
				if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 1) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"firsttmap"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 2) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"secondtmapa"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 3) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"secondtmapb"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 4) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"secondtmapc"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 5) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"thirdtmapd"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 6) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"thirdtmape"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 7) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"thirdtmapf"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 8) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"thirdtmapg"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 9) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"thirdtmaph"));
				} else if (pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 10) {
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
							"thirdtmapi"));
				}
			} else if (itemId == 40663) { // 아들의 편지
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "sonsletter"));
			} else if (itemId == 40630) { // 디에고의 낡은 일기
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "diegodiary"));
			} else if (itemId == 41340) { // 용병 단장 티온의 소개장
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "tion"));
			} else if (itemId == 41317) { // 라루손 추천서
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "rarson"));
			} else if (itemId == 41318) { // クエンのメモ
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "kuen"));
			} else if (itemId == 41329) { // 박제 제작 의뢰서
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "anirequest"));
			} else if (itemId == 41346) { // 로빈훗도 메모 1
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
						"robinscroll"));
			} else if (itemId == 41347) { // 로빈훗도 노트 2
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(),
						"robinscroll2"));
			} else if (itemId == 41348) { // 로빈훗도의 소개장
				pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "robinhood"));
			} else if (itemId == 41208) { // 져 가는 영혼
				if ((pc.getX() >= 32844 && pc.getX() <= 32845)
						&& (pc.getY() >= 32693 && pc.getY() <= 32694)
						&& pc.getMapId() == 550) { // 배의 묘지:지상층
					pc.teleport(((L1EtcItem) useitem.getItem()).get_locx(),
							((L1EtcItem) useitem.getItem()).get_locy(),
							((L1EtcItem) useitem.getItem()).get_mapid(), pc.getHeading(),169, true, false);
				} else {
					// \f1 아무것도 일어나지 않았습니다.
					pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
					pc.sendPackets(new S_ServerMessage(79));
				}	
			} else if (itemId >= 41255 && itemId <= 41259) { // 요리책
				if (cookLevel == 0) {
					pc.sendPackets(new S_PacketBox(S_PacketBox.COOK_WINDOW));
				} else {
					makeCooking(pc, cookLevel, cookNo);
				}
			} 
		} else if (itemType == L1Item.CATEGORY_WEAPON) {
			int min = useitem.getItem().getMinLevel();
			int max = useitem.getItem().getMaxLevel();
			if (min != 0 && min > pc.getLevel()) {
				pc.sendPackets(new S_ServerMessage(318, String.valueOf(min)));
			} else if (max != 0 && max < pc.getLevel()) {
				if (max < 50) {
					pc.sendPackets(new S_PacketBox(S_PacketBox.MSG_LEVEL_OVER, max));
				} else {
					pc.sendPackets(new S_SystemMessage("이 아이템은" + max+ "레벨 이하만 사용할 수 있습니다."));
				}
			} else {
				if (pc.isGm()) {
					UseWeapon(pc, useitem);
				} else if (pc.isCrown() && useitem.getItem().isUseRoyal() || pc.isKnight() && useitem.getItem().isUseKnight()
						|| pc.isElf() && useitem.getItem().isUseElf() || pc.isWizard() && useitem.getItem().isUseMage()
						|| pc.isDarkelf() && useitem.getItem().isUseDarkelf()) {
					UseWeapon(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(264));
				}
			}
		} else if (itemType == L1Item.CATEGORY_ARMOR) {
			if (pc.isGm()) {
				UseArmor(pc, useitem);
			} else if (pc.isCrown() && useitem.getItem().isUseRoyal()|| pc.isKnight() && useitem.getItem().isUseKnight() || pc.isElf()
					&& useitem.getItem().isUseElf() || pc.isWizard() && useitem.getItem().isUseMage() || pc.isDarkelf() && useitem.getItem().isUseDarkelf()) {
				int min = ((L1Armor) useitem.getItem()).getMinLevel();
				int max = ((L1Armor) useitem.getItem()).getMaxLevel();
				if (min != 0 && min > pc.getLevel()) {
					pc.sendPackets(new S_ServerMessage(318, String.valueOf(min)));
				} else if (max != 0 && max < pc.getLevel()) {
					if (max < 50) {
						pc.sendPackets(new S_PacketBox(S_PacketBox.MSG_LEVEL_OVER, max));
					} else {
						pc.sendPackets(new S_SystemMessage("이 아이템은" + max+ "레벨 이하만 사용할 수 있습니다."));
					}
				} else {
					UseArmor(pc, useitem);
				}
			} else {
				pc.sendPackets(new S_ServerMessage(264));
			}
		} else {

			if (useitem.getCount() < 1) {
				pc.sendPackets(new S_ServerMessage(329, useitem.getLogName())); 
			} else {
				pc.sendPackets(new S_ServerMessage(74, useitem.getLogName()));
			} 
		} 	
		// 효과 지연이 있는 경우는 현재 시간을 세트
		if (isDelayEffect) {
			Timestamp ts = new Timestamp(System.currentTimeMillis());
			useitem.setLastUsed(ts);

			pc.getInventory().updateItem(useitem, L1PcInventory.COL_DELAY_EFFECT);
			pc.getInventory().saveItem(useitem, L1PcInventory.COL_DELAY_EFFECT);
		}
		L1ItemDelay.onItemUse(client, useitem); // 아이템 지연 개시

	}

	/**
	 * 상태 가속 1단 아이템 관련
	 * @param pc
	 * @param itemId
	 */
	private void useGreenPotion(L1PcInstance pc, int itemId) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);

		int time = 0;
		switch (itemId) {
		case ItemId.상아탑_속도_향상_물약:   
		case ItemId.속도_향상_물약:        time =  300; break;
		case ItemId.축복_속도_향상_물약:     time =  350; break;
		case ItemId.강화_초록_물약:        time =  1800; break;
		case ItemId.축복_강화_초록_물약: 	time =  2100; break;
		case ItemId.농축_속도_향상_물약:     time =  3200; break;
		case ItemId.와인:               time =  600; break;
		case ItemId.위스키:              time =  900; break;
		}
		S_SkillSound s = S_SkillSound.get(pc.getId(), 191);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
		pc.sendPackets(new S_ServerMessage(183));
		
		// XXX:헤이스트아이템 장비시, 취한 상태가 해제되는지 불명
		if (pc.getHasteItemEquipped() > 0) {
			return;
		}
		// 취한 상태를 해제
		pc.setDrink(false);

		// 헤이 파업, 그레이터 헤이 파업과는 중복 하지 않는다
		if (pc.hasSkillEffect(SkillId.헤이스트)) {
			pc.killSkillEffectTimer(SkillId.헤이스트);
			S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 0, 0);
			pc.sendPackets(Haste);
			pc.broadcastPacket(Haste);
			pc.setMoveSpeed(0);
		} else if (pc.hasSkillEffect(SkillId.그레이터_헤이스트)) {
			pc.killSkillEffectTimer(SkillId.그레이터_헤이스트);
			S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 0, 0);
			pc.sendPackets(Haste);
			pc.broadcastPacket(Haste);
			pc.setMoveSpeed(0);
		}
		if (pc.hasSkillEffect(SkillId.슬로우)) { // 슬로우
			pc.killSkillEffectTimer(SkillId.슬로우);
			S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 0, 0);
			pc.sendPackets(Haste);
			pc.broadcastPacket(Haste);
		} else if (pc.hasSkillEffect(SkillId.매스_슬로우)) { // 매스 슬로우
			pc.killSkillEffectTimer(SkillId.매스_슬로우);
			S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 0, 0);
			pc.sendPackets(Haste);
			pc.broadcastPacket(Haste);
		} else if (pc.hasSkillEffect(SkillId.인탱글)) { // 엔탕르
			pc.killSkillEffectTimer(SkillId.인탱글);
			S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 0, 0);
			pc.sendPackets(Haste);
			pc.broadcastPacket(Haste);
		} else {
			S_SkillHaste Haste = S_SkillHaste.get(pc.getId(), 1, time);
			pc.sendPackets(Haste);
			pc.broadcastPacket(Haste);
			pc.setMoveSpeed(1);
			pc.setSkillEffect(SkillId.상태_1단_가속, time * 1000);
		}
	}

	/**
	 * 2단 가속 아이템 관련
	 * @param pc
	 * @param item_id
	 */
	private void useBravePotion(L1PcInstance pc, int itemid, ItemInstance useitem) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);
		// 시간초 설정
		int time = 0;
		switch (itemid) {
		case ItemId.용기_물약:    time =  300;  break;
		case ItemId.축복_용기_물약: time =  350;  break;
		case ItemId.농축_용기_물약: time =  1800; break;
		case ItemId.엘븐와퍼: 	 time =  600;  break;
		case ItemId.축복_엘븐와퍼:  time =  1200;  break;
		case ItemId.농축_집중_물약: time = 1800;  break;
		case ItemId.악마_피:     time =  350;  break;	
		case ItemId.농축_악마_피: time =  1800;  break;
		}
		// 스킬 상태 해제 
		if (pc.hasSkillEffect(SkillId.윈드_워크) || pc.hasSkillEffect(SkillId.홀리_워크)
				|| pc.hasSkillEffect(SkillId.무빙_악셀레이션) || pc.hasSkillEffect(SkillId.상태_요정_2단_가속)) {
			pc.killSkillEffectTimer(SkillId.홀리_워크);
			pc.killSkillEffectTimer(SkillId.윈드_워크);
			pc.killSkillEffectTimer(SkillId.무빙_악셀레이션);
			pc.killSkillEffectTimer(SkillId.상태_요정_2단_가속);
			pc.killSkillEffectTimer(SkillId.상태_2단_가속);
			S_SkillBrave ss = S_SkillBrave.get(pc.getId(), 0, 0);
			pc.sendPackets(ss);
			pc.broadcastPacket(ss);
			pc.setBraveSpeed(0);
		}
		// 클래스 별 아이템 사용 가능 조건
		if(pc.isKnight()) {
			if(itemid == ItemId.용기_물약 || itemid == ItemId.축복_용기_물약 || itemid == ItemId.농축_용기_물약) {
				S_SkillBrave ss = S_SkillBrave.get(pc.getId(), 1, time);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
				pc.setSkillEffect(SkillId.상태_2단_가속, time * 1000);
				S_SkillSound s = S_SkillSound.get(pc.getId(), 751);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
				pc.setBraveSpeed(1);
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
		}
		if(pc.isElf()) {
			if(itemid == ItemId.엘븐와퍼 || itemid == ItemId.축복_엘븐와퍼 || itemid == ItemId.농축_집중_물약) {
				S_SkillBrave ss = S_SkillBrave.get(pc.getId(), 3, time);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
				pc.setSkillEffect(SkillId.상태_요정_2단_가속, time * 1000);	
				S_SkillSound s = S_SkillSound.get(pc.getId(), 751);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
				pc.setBraveSpeed(1); 
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
		}
		if(pc.isCrown()) {
			if(itemid == ItemId.악마_피 || itemid == ItemId.농축_악마_피) {
				S_SkillBrave ss = S_SkillBrave.get(pc.getId(), 1, time);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
				pc.setSkillEffect(SkillId.상태_2단_가속, time * 1000);	
				S_SkillSound s = S_SkillSound.get(pc.getId(), 751);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
				pc.setBraveSpeed(1); 
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
		}
	}
	/**
	 * 파란 물약 효과 처리
	 * @param pc
	 * @param item_id
	 */
	private void useBluePotion(L1PcInstance pc, int itemId) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4)); 
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);
		int time = 0;
		switch (itemId) {
		case ItemId.파란_물약:    time =  600;  break;
		case ItemId.축복_파란_물약: time =  700;  break;
		case ItemId.농축_마력_물약: time =  1200; break;
		}
		if (pc.hasSkillEffect(SkillId.상태_파란물약)) {
			pc.killSkillEffectTimer(SkillId.상태_파란물약);
			pc.addMpr(-2);
		}
		pc.addMpr(2);
		pc.sendPackets(new S_SkillIconGFX(34, time), true);	
		S_SkillSound s = S_SkillSound.get(pc.getId(), 190);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
		pc.setSkillEffect(SkillId.상태_파란물약, time * 1000);
		pc.sendPackets(new S_ServerMessage(1007)); // MP의 회복 속도가 빨라집니다.
	}
	/**
	 * 지혜의 물약 관련 
	 * @param pc
	 * @param item_id
	 */
	private void useWisdomPotion(L1PcInstance pc, int itemId) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4)); 
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);

		int time = 0;
		switch (itemId) {
		case ItemId.지혜_물약:    time =  300;  break;
		case ItemId.축복_지혜_물약: time =  350;  break;
		case ItemId.농축_지혜_물약: time =  1200; break;
		}
		if (pc.hasSkillEffect(SkillId.상태_지혜)) {
			pc.killSkillEffectTimer(SkillId.상태_지혜);
		}
		pc.sendPackets(new S_SkillIconWisdomPotion((int) (time)));
		S_SkillSound s = S_SkillSound.get(pc.getId(), 750);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
		pc.setSkillEffect(SkillId.상태_지혜, time * 1000);
	}

	/**
	 * 물속에서 숨쉬기 관련
	 * @param pc
	 * @param item_id
	 */
	private void useBlessOfEva(L1PcInstance pc, int itemId) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4)); 
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);

		int time = 0;
		switch (itemId) {
		case ItemId.에바_축복:    time =  1800;  break;
		case ItemId.인어_비늘:    time =  300;  break;
		case ItemId.농축_호흡_물약: time =  7200; break;
		}
		if (pc.hasSkillEffect(SkillId.상태_호흡)) {
			int timeSec = pc.getSkillEffectTimeSec(SkillId.상태_호흡);
			time += timeSec;
			if (time > 3600) {
				time = 3600;
			}
		}
		S_SkillSound s = S_SkillSound.get(pc.getId(), 190);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
		pc.setSkillEffect(SkillId.상태_호흡, time * 1000);
		pc.sendPackets(new S_SkillIconBlessOfEva(pc.getId(), time));
	}

	/**
	 * 불투명 물약 관련
	 * @param pc
	 */
	private void useBlindPotion(L1PcInstance pc) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4), true);
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);

		int time = 480;
		if (pc.hasSkillEffect(SkillId.커스_블라인드)) {
			pc.killSkillEffectTimer(SkillId.커스_블라인드);
		} else if (pc.hasSkillEffect(SkillId.다크니스)) {
			pc.killSkillEffectTimer(SkillId.다크니스);
		}
		if (pc.hasSkillEffect(SkillId.상태_괴물눈)) {
			pc.sendPackets(new S_CurseBlind(2));
		} else {
			pc.sendPackets(new S_CurseBlind(1));
		}
		pc.setSkillEffect(SkillId.커스_블라인드, time * 1000);
	}

	/**
	 * 변신 아이템 관련
	 * @param pc
	 * @param item_id
	 * @param s
	 * @return
	 */
	private boolean usePolyScroll(L1PcInstance pc, int item_id, String s) {
		int time = 0;
		if (item_id == ItemId.변신_주문서 || item_id == ItemId.상아탑_변신_주문서) {
			time = 1800;
		} else if (item_id == ItemId.축복_변신_주문서) {
			time = 2100;
		} else if (item_id == ItemId.농축_변신_물약) {  
			time = 3600;
		}
		if (s.equalsIgnoreCase("ranking class polymorph")) { 

			switch (pc.getType()) {
			case 0:
				if (pc.get_sex() == 0)
					s = "rangking prince male";
				else
					s = "rangking prince female";
				break;
			case 1:
				if (pc.get_sex() == 0)
					s = "rangking knight male";
				else
					s = "rangking knight female";
				break;
			case 2:
				if (pc.get_sex() == 0)
					s = "rangking elf male";
				else
					s = "rangking elf female";
				break;
			case 3:
				if (pc.get_sex() == 0)
					s = "rangking wizard male";
				else
					s = "rangking wizard female";
				break;
			case 4:
				if (pc.get_sex() == 0) {
					s = "rangking darkelf male";
				} else {
					s = "rangking darkelf female";
				}			
				break;
			}
		}

		if (s.equalsIgnoreCase("shinhwa class")) {
			if (!pc.getInventory().checkItem(Config.MASTER_POLYID)){
				pc.sendPackets(new S_ChatPacket(pc,String.format("%s", "변신에 필요한 아이템이 없습니다."), 1));
				return false;
			}
			switch (pc.getType()) {			
			case 0:
				if (pc.get_sex() == 0)
					s = "mythical high prince male re";
				else
					s = "mythical high prince female walk polymorph";
				break;
			case 1:
				if (pc.get_sex() == 0)
					s = "Myth_Knight_male";
				else
					s = "Myth_Knight_Female";
				break;
			case 2:
				if (pc.get_sex() == 0)
					s = "thical high elf male walk polymorph";
				else
					s = "mythical high elf female walk polymorph";
				break;
			case 3:
				if (pc.get_sex() == 0)
					s = "mythical high wizard male walk polymorph";
				else
					s = "mythical high wizard female walk polymorph re";
				break;
			case 4:
				if (pc.get_sex() == 0)
					s = "high Doppel_darkelf male_run_walk";
				else
					s = "hight darkelf female_run_walk";
				break;
			}
		} 


		if (s.startsWith("rangking ")) {
			int star = UserRankingController.getInstance().getStarCount(pc.getName());
			L1UserRanking rank = UserRankingController.getInstance().getClassRank(pc.getType(), pc.getName());
			int star2 = 0;
			if (rank != null) {
				star2 = rank.getCurRank();
			}  

			if (star != 11 && star != 10) {
				if (star2 > 3 || star2 == 0) {
					return false;
				}
			}
		}
		L1PolyMorph poly = PolyTable.getInstance().getTemplate(s);  
		if (poly != null || s.equals("")) {
			if (s.equals("")) {
				if (pc.getCurrentSpriteId() == 6034 || pc.getCurrentSpriteId() == 6035) {
					return true; 
				} else { 
					if(pc.hasSkillEffect(SkillId.POLY_BUFF)) {
						pc.removeSkillEffect(SkillId.POLY_BUFF);
					}
					L1PolyMorph.undoPoly(pc);
					return true;
				}
			} else if (poly.getMinLevel() <= pc.getLevel() || pc.isGm()) {
				L1PolyMorph.doPoly(pc, poly.getPolyId(), time, L1PolyMorph.MORPH_BY_ITEMMAGIC);
				if (pc.isGm()){ 
					pc.sendPackets(new S_SystemMessage("\\fY변신이름 " + s + " 번호 " + pc.getCurrentSpriteId())); 
				}
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	/**
	 * 엘릭서 관련 
	 * @param pc
	 * @param itemId
	 * @param useitem
	 */
	private void useItemElixir(L1PcInstance pc, int itemId, ItemInstance useitem) {

		if (itemId == ItemId.엘릭서_힘) {
			if (pc.getAbility().getStr() < 35 && pc.getElixirStats() < 5) {
				pc.getAbility().addStr((byte) 1);
				pc.setElixirStats(pc.getElixirStats() + 1);
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_OwnCharStatus(pc));
				try {
					pc.save();
				} catch (Exception e) {}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_9));
			}
		} else if(itemId == ItemId.엘릭서_콘) {
			if (pc.getAbility().getCon() < 35 && pc.getElixirStats() < 5) {
				pc.getAbility().addCon((byte) 1);
				pc.setElixirStats(pc.getElixirStats() + 1);
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_OwnCharStatus(pc));
				try {
					pc.save();
				} catch (Exception e) {}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_9));
			}
		} else if(itemId == ItemId.엘릭서_덱스) {
			if (pc.getAbility().getDex() < 35 && pc.getElixirStats() < 5) {
				pc.getAbility().addDex((byte) 1);
				pc.resetBaseAc();
				pc.setElixirStats(pc.getElixirStats() + 1);
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_OwnCharStatus(pc));
				try {
					pc.save();
				} catch (Exception e) {}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_9));
			}
		} else if(itemId == ItemId.엘릭서_인트) {
			if (pc.getAbility().getInt() < 35 && pc.getElixirStats() < 5) {
				pc.getAbility().addInt((byte) 1); 
				pc.setElixirStats(pc.getElixirStats() + 1);
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_OwnCharStatus(pc));
				try {
					pc.save();
				} catch (Exception e) {}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_9));
			}
		} else if(itemId == ItemId.엘릭서_위즈) {
			if (pc.getAbility().getWis() < 35 && pc.getElixirStats() < 5) {
				pc.getAbility().addWis((byte) 1);
				pc.resetBaseMr();
				pc.setElixirStats(pc.getElixirStats() + 1);
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_OwnCharStatus(pc));
				try {
					pc.save();
				} catch (Exception e) {}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_9));
			}
		} else if(itemId == ItemId.엘릭서_카리) {
			if (pc.getAbility().getCha() < 35 && pc.getElixirStats() < 5) {
				pc.getAbility().addCha((byte) 1);
				pc.setElixirStats(pc.getElixirStats() + 1);
				pc.getInventory().removeItem(useitem, 1);
				pc.sendPackets(new S_OwnCharStatus(pc));
				try {
					pc.save();
				} catch (Exception e) {}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_9));
			}
		}
	}
	/**
	 * 킬 데스 아이템 관련 
	 * @param pc
	 * @param itemId
	 * @param useitem
	 */
	private void useKillDeath(L1PcInstance pc, int itemId, ItemInstance useitem) {

		if (itemId == ItemId.킬_데스_초기화_주문서) {
			pc.setKills(0);
			pc.setDeaths(0);
			pc.sendPackets(new S_SystemMessage("당신의 킬데스가 초기화 되었습니다."));
			pc.getInventory().removeItem(useitem, 1);
		} else if(itemId == ItemId.데스_카운트_감소_주문서) {
			pc.setDeaths(pc.getDeaths() -1);
			pc.sendPackets(new S_SystemMessage("[데스]가 1 감소 하였습니다."));
			pc.getInventory().removeItem(useitem, 1);
		} else if (itemId == ItemId.킬_카운트_감소_주문서) { // 킬 감소 주문서
			pc.setKills(pc.getKills() -1);
			pc.sendPackets(new S_SystemMessage("[킬]이 1 감소 하였습니다."));
			pc.getInventory().removeItem(useitem, 1);
		}
	}
	/**
	 * 변신 아이템 관련
	 * @param pc
	 * @param itemId
	 */
	private void usePolyScale(L1PcInstance pc, int itemId) {
		int polyId = 0;
		int time = 0;
		if (itemId == ItemId.어둠_비늘) { // 어둠의 비늘
			polyId = 3101;// 변신하는몹
			time = 600;// 시간설정
		} else if (itemId == ItemId.열화_비늘) { // 열화의 비늘
			polyId = 3126;
			time = 600;
		} else if (itemId == ItemId.배덕자_비늘) { // 배덕자의 비늘
			polyId = 3888;
			time = 600;
		} else if (itemId == ItemId.증오_비늘) { // 증오의 비늘
			polyId = 3784;
			time = 600;
		} else if (itemId == ItemId.라버본_헤드_변신) { // 라버본헤드 변신물약
			polyId = 4188;
			time = 900;
		} else if (itemId == ItemId.라버본_솔져_변신) { // 라버본솔져 변신물약
			polyId = 4190;
			time = 900;
		} else if (itemId == ItemId.라버본_나이프_변신) { // 라버본나이프 변신물약
			polyId = 4796;
			time = 900;
		}
		L1PolyMorph.doPoly(pc, polyId, time, L1PolyMorph.MORPH_BY_ITEMMAGIC);
	}

	private void UseArmor(L1PcInstance activeChar, ItemInstance armor) {
		int type = armor.getItem().getType();
		L1PcInventory pcInventory = activeChar.getInventory();
		boolean equipeSpace;
		if (type == L1Item.TYPE_ARMOR_RING) { 
			equipeSpace = pcInventory.getTypeEquipped(L1Item.CATEGORY_ARMOR, L1Item.TYPE_ARMOR_RING) <= 1;
		} else {
			equipeSpace = pcInventory.getTypeEquipped(L1Item.CATEGORY_ARMOR, type) <= 0;
		}
		if (equipeSpace && !armor.isEquipped()) { 
			int polyid = activeChar.getCurrentSpriteId();

			if (!L1PolyMorph.isEquipableArmor(polyid, type)) { // 그 변신에서는 장비 불가
				return;
			}
			if (type == L1Item.TYPE_ARMOR_SHIELD && pcInventory.getTypeEquipped(L1Item.CATEGORY_ARMOR, L1Item.TYPE_ARMOR_GARDER) >= 1
					|| type == L1Item.TYPE_ARMOR_GARDER && pcInventory.getTypeEquipped(L1Item.CATEGORY_ARMOR, L1Item.TYPE_ARMOR_SHIELD) >= 1) {
				activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_21), true);

				return;
			}
			if (type == L1Item.TYPE_ARMOR_SHIELD && activeChar.getWeapon() != null) { 
				if (activeChar.getWeapon().getItem().isTwohandedWeapon()) {
					activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_22), true); 
					return;
				}
			}
			SkillUtil.cancelBarrier(activeChar);
			pcInventory.setEquipped(armor, true);
		} else if (armor.isEquipped()) {
			if (armor.getItem().getBless() == L1Item.BLESS_CURSE) {
				activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_23), true);
				return;
			} 
			pcInventory.setEquipped(armor, false);
		} else { 
			activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_21), true);
		}
		// 세트 장비용 HP, MP, MR갱신
		activeChar.setCurrentHp(activeChar.getCurrentHp());
		activeChar.setCurrentMp(activeChar.getCurrentMp());
		activeChar.sendPackets(new S_OwnCharAttrDef(activeChar), true);
		activeChar.sendPackets(new S_OwnCharStatus(activeChar), true);
		activeChar.sendPackets(new S_SPMR(activeChar), true);
	}

	private void UseWeapon(L1PcInstance activeChar, ItemInstance weapon) {
		L1PcInventory pcInventory = activeChar.getInventory();
		if (activeChar.getWeapon() == null
				|| !activeChar.getWeapon().equals(weapon)) {
			int weapon_type = weapon.getItem().getType();
			int polyid = activeChar.getCurrentSpriteId();

			if (!L1PolyMorph.isEquipableWeapon(polyid, weapon_type)) {
				return;
			}
			if (weapon.getItem().isTwohandedWeapon()
					&& pcInventory.getTypeEquipped(L1Item.CATEGORY_ARMOR, L1Item.TYPE_ARMOR_SHIELD) >= 1) {
				activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_24), true); 
				return;
			}
		}
		SkillUtil.cancelBarrier(activeChar);

		if (activeChar.getWeapon() != null) {
			if (activeChar.getWeapon().getItem().getBless() == L1Item.BLESS_CURSE) { 
				activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_23), true); 
				return;
			}
			if (activeChar.getWeapon().equals(weapon)) {
				pcInventory.setEquipped(activeChar.getWeapon(), false, false,false);
				return;
			} else {
				pcInventory.setEquipped(activeChar.getWeapon(), false, false,true);
			}
		}
		if (weapon.getItemId() == ItemId.저주_악운_단검) {
			activeChar.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_25, weapon.getLogName()), true); 
		}
		pcInventory.setEquipped(weapon, true, false, false);
	}

	private void polyAction(L1PcInstance attacker, L1Character cha) {
		boolean isSameClan = false;
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getClanid() != 0 && attacker.getClanid() == pc.getClanid()) {
				isSameClan = true;
			}
		}
		if (attacker.getId() != cha.getId() && !isSameClan) { // 자신 의외로 다른 클랜
			int probability = 3 * (attacker.getLevel() - cha.getLevel()) + 100 - cha.getResistance().getMr();
			int rnd = _random.nextInt(100) + 1;
			if (rnd > probability) {
				return;
			}
		}

		int[] polyArray = { 29, 945, 947, 979, 1037, 1039, 3860, 3861, 3862,
				3863, 3864, 3865, 3904, 3906, 95, 146, 2374, 2376, 2377, 2378,
				3866, 3867, 3868, 3869, 3870, 3871, 3872, 3873, 3874, 3875,
				3876 };

		int pid = _random.nextInt(polyArray.length);
		int polyId = polyArray[pid];

		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			if (pc.toArmorEquipped().isPolyControlRing()) {
				pc.sendPackets(new S_ShowPolyList(pc.getId()));
				pc.sendPackets(new S_ServerMessage(966)); // string-j.tbl:968행째
			} else {
				L1Skills skillTemp = SkillsTable.getInstance().getTemplate(SkillId.셰이프_체인지);

				L1PolyMorph.doPoly(pc, polyId, skillTemp.getBuffDuration(),
						L1PolyMorph.MORPH_BY_LOGIN);
				if (attacker.getId() != pc.getId()) {
					pc.sendPackets(new S_ServerMessage(241, attacker.getName()), true);
					// %0이당신을변신시켰습니다.
				}
			}
		} else if (cha instanceof MonsterInstance) {
			MonsterInstance mob = (MonsterInstance) cha;
			if (mob.getLevel() < 50) {
				L1Skills skillTemp = SkillsTable.getInstance().getTemplate(SkillId.셰이프_체인지);
				L1PolyMorph.doPoly(mob, polyId, skillTemp.getBuffDuration() , L1PolyMorph.MORPH_BY_ITEMMAGIC);
			}
		}
	}

	private boolean createNewItem(L1PcInstance pc, int item_id, int count) {
		ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			item.setCount(count); 
			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
				item.setIdentified(true);
				pc.getInventory().storeItem(item); 
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_26));
				return false;
			}
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_27, item.getLogName()));
			return true;
		} else {
			return false;
		}
	}
	/**
	 * 부활 주문서 관련
	 * @param pc
	 * @param itemId
	 * @param resid
	 */
	private void useResurrectionScroll(L1PcInstance pc, int itemId, int resid) {

		L1Character resobject = (L1Character) L1World.getInstance().findObject(resid);
		if (resobject != null) {
			if (resobject instanceof L1PcInstance) {
				L1PcInstance target = (L1PcInstance) resobject;
				if (pc.getId() == target.getId()) {
					return;
				}
				int castle_id = L1CastleLocation.getCastleIdByArea(pc);
				if (castle_id != 0) {
					pc.sendPackets(new S_SystemMessage("해당 지역에서는 사용할 수 없습니다."));
					return;
				}
				if (L1World.getInstance().getVisiblePlayer(target, 0).size() > 0) {
					for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(target, 0)) {
						if (!visiblePc.isDead()) {
							pc.sendPackets(new S_ServerMessage(592), true);
							return;
						}
					}
				}
				if (target.getCurrentHp() == 0 && target.isDead()) {
					if (pc.getMap().isUseResurrection()) {
						target.setTempID(pc.getId());
						if (itemId == ItemId.부활_주문서) {
							target.sendPackets(new S_Message_YN(321, ""), true);
						} else if (itemId == ItemId.축복_부활_주문서) {
							target.sendPackets(new S_Message_YN(322, ""), true);
						}
					} else {
						return;
					}
				}
			} else if (resobject instanceof L1NpcInstance) {
				if (!(resobject instanceof L1TowerInstance)) {
					L1NpcInstance npc = (L1NpcInstance) resobject;
					if (npc instanceof L1NpcInstance) {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
						return;
					}
					if (npc instanceof PetInstance
							&& L1World.getInstance().getVisiblePlayer(npc, 0).size() > 0) {

						for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc, 0)) {
							if (!visiblePc.isDead()) {
								pc.sendPackets(new S_ServerMessage(592));
								return;
							}
						}
					}
					if (npc.getCurrentHp() == 0 && npc.isDead()) {
						npc.resurrect(npc.getMaxHp() / 4);
						npc.setResurrect(true);
					}
				}
			}
		}		
	}
	private void omanTeleportAmulet(L1PcInstance pc, int itemId, ItemInstance item) {
		boolean isTeleport = false;
		if (itemId >= 40289 && itemId <= 40297 || itemId == 43397) {
			if (pc.getMap().isEscapable()) {
				isTeleport = true;
			}
//			if (pc.getX() >= 33923 && pc.getX() <= 33934 && pc.getY() >= 33340 && pc.getY() <= 33356 && pc.getMapId() == 4) {
//				isTeleport = true;
//			}
		}
		if (isTeleport) {
			pc.teleport(item.getItem().get_locx(), item.getItem().get_locy(), item.getItem().get_mapid(), pc.getHeading(), 169, true, false);
		} else {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
		}
	}

	private boolean withdrawPet(L1PcInstance pc, int itemObjectId) {
		if (!pc.getMap().isTakePets()) {
			pc.sendPackets(new S_ServerMessage(563));
			// \f1 여기에서는 사용할 수 없습니다.
			return false;
		}

		int petCost = 0;
		Object[] petList = pc.getPetList().values().toArray();
		for (Object pet : petList) {
			if (pet instanceof PetInstance) {
				if (((PetInstance) pet).getItemObjId() == itemObjectId) {
					return false;
				}
			}
			petCost += ((L1NpcInstance) pet).getPetcost();
		}
		int charisma = pc.getAbility().getTotalCha();
		if (pc.isCrown()) { // 군주
			charisma += 6;
		} else if (pc.isElf()) { // 요정
			charisma += 12;
		} else if (pc.isWizard()) { // 마법사
			charisma += 6;
		} else if (pc.isDarkelf()) { // 다크엘프
			charisma += 6;
		}
		charisma -= petCost;
		int petCount = charisma / 6;
		if (petCount == 0) {
			pc.sendPackets(new S_ServerMessage(489)); // 물러가려고 하는 애완동물이 너무 많습니다.
			return false;
		}

		L1Pet l1pet = PetTable.getInstance().getTemplate(itemObjectId);
		if (l1pet != null) {
			L1Npc npcTemp = NpcTable.getInstance().getTemplate(l1pet.get_npcid());
			PetInstance pet = new PetInstance(npcTemp, pc, l1pet);
			pet.setPetcost(6);
		}
		return true;
	}
	/**
	 * 낚시 관련
	 * @param pc
	 * @param item
	 * @param fishX
	 * @param fishY
	 * @param itemObjectId
	 */

	private void startFishing(L1PcInstance pc, ItemInstance item, int fishX, int fishY, int itemObjectId) {
		int rodLength = 0;
		if (item.getItemId() == 41293 || item.getItemId() == 41408 || item.getItemId() == 41409) {
			rodLength = 5;
		} else if (item.getItemId() == 41294) {
			rodLength = 3;
		}

		boolean rodFishing = false;
		int heading = pc.getHeading(); //● 방향: (0.좌상)(1.상)( 2.우상)(3.오른쪽)(4.우하)(5.하)(6.좌하)(7.좌)

		switch(heading){
		case 0: //상좌
			rodFishing = pc.getMap().isFishingZone(pc.getX(), pc.getY()-rodLength);
			break;
		case 1: // 상
			rodFishing = pc.getMap().isFishingZone(pc.getX() + rodLength, pc.getY() - rodLength);
			break;
		case 2: // 우상
			rodFishing = pc.getMap().isFishingZone(pc.getX() + rodLength, pc.getY() - rodLength);
			break;
		case 3: // 오른쪽
			rodFishing = pc.getMap().isFishingZone(pc.getX() + rodLength, pc.getY() + rodLength);
			break;
		case 4: // 우하
			rodFishing = pc.getMap().isFishingZone(pc.getX(), pc.getY() + rodLength);
			break;
		case 5: // 하
			rodFishing = pc.getMap().isFishingZone(pc.getX() - rodLength, pc.getY() + rodLength);
			break;
		case 6: // 좌하
			rodFishing = pc.getMap().isFishingZone(pc.getX() - rodLength, pc.getY());
			break;
		case 7: // 좌
			rodFishing = pc.getMap().isFishingZone(pc.getX() - rodLength, pc.getY() - rodLength);
			break;
		}
		boolean targetFishing = pc.getMap().isFishingZone(fishX, fishY);
		if (rodFishing && targetFishing) { // 물의 타일이 28이다..

			ItemInstance useItem = pc.getInventory().getItem(itemObjectId);
			if (useItem != null) {
				pc._fishingRod = useItem;
			} else {
				pc.sendPackets(new S_ServerMessage(1137));
				return;
			}

			if (pc._fishingRod.getItemId() == 41408 || pc._fishingRod.getItemId() == 41409) {
				L1PolyMorph.undoPoly(pc);
				pc.sendPackets(new S_Fishing(pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY));
				pc.broadcastPacket(new S_Fishing(pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY));
				pc.setFishing(true);
				pc.setFishingRod(item);  
				if (pc._fishingRod.getItemId() == 41408) {
					item.setChargeCount(item.getChargeCount() - 1);
					pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
					pc.setFishingTime(System.currentTimeMillis() + 30 * 1000);
				} else if (pc._fishingRod.getItemId() == 41409) {
					item.setChargeCount(item.getChargeCount() - 1);
					pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
					pc.setFishingTime(System.currentTimeMillis() + 30 * 1000);
					pc.getInventory().consumeItem(41295, 1);
				}
				FishingTimeController.getInstance().addMember(pc);
			} else if (pc.getInventory().consumeItem(41295, 1)) {
				L1PolyMorph.undoPoly(pc); 
				pc.sendPackets(new S_Fishing(pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY));
				pc.broadcastPacket(new S_Fishing(pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY));
				pc.setFishing(true);
				pc.setFishingRod(item);
				pc.setFishingTime(System.currentTimeMillis() + 60 * 1000);
				FishingTimeController.getInstance().addMember(pc);
			} else {
				// 낚시를 하기 위해서는 먹이가 필요합니다.
				pc.sendPackets(new S_ServerMessage(1137));
			}
		} else {
			// 여기에 낚싯대를 던질 수 없습니다.
			pc.sendPackets(new S_ServerMessage(1138));
		}

	}

	/**
	 * 용해제 관련 
	 * @param pc
	 * @param item
	 * @param resolvent
	 */
	private void useResolvent(L1PcInstance pc, ItemInstance item, ItemInstance resolvent) {


		if (item == null || resolvent == null) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			return;
		}
		if (item.getItem().getType2() == 1 || item.getItem().getType2() == 2) { //
			if (item.getEnchantLevel() != 0) { // 강화가 끝난 상태
				pc.sendPackets(new S_ServerMessage(1161));
				return;
			}
			if (item.isEquipped()) { // 장비중
				pc.sendPackets(new S_ServerMessage(1161)); 
				return;
			}
			if (item.getEndTime() != null) {
				pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 불가능합니다."));
				return;
			}
			if (item.getBless() >= 128) { // 봉인중
				pc.sendPackets(new S_ServerMessage(1161)); // 용해할 수 없습니다.
				return;
			}
		}
		int crystalCount = ResolventTable.getInstance().getCrystalCount(item.getItem().getItemId());
		if (crystalCount == 0) {
			pc.sendPackets(new S_ServerMessage(1161)); // 용해할 수 없습니다.
			return;
		}

		int rnd = _random.nextInt(100) + 1;
		if (rnd >= 1 && rnd <= 50) {
			
		} else if (rnd >= 51 && rnd <= 90) {
			pc.getInventory().storeItem(41246, crystalCount);
		} else if (rnd >= 91 && rnd <= 100) {
			pc.getInventory().storeItem(41246, (int) (crystalCount * 1.5));
		}
		if (crystalCount != 0) {
			ItemInstance crystal = ItemTable.getInstance().createItem(41246);
			crystal.setCount(crystalCount);
			if (pc.getInventory().checkAddItem(crystal, 1) == L1Inventory.OK) {
				pc.getInventory().storeItem(crystal);
				pc.sendPackets(new S_ServerMessage(403, crystal.getLogName())); // %0를 손에 넣었습니다.
			} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
				L1World.getInstance().getInventory(pc.getX(), pc.getY(), pc.getMapId()).storeItem(crystal);
			}
		}
		pc.getInventory().removeItem(item, 1);
		pc.getInventory().removeItem(resolvent, 1);
	}

	/**
	 * 요리 제작 관련
	 * @param pc
	 * @param cookLevel
	 * @param cookNo
	 */

	private void makeCooking(L1PcInstance pc, int cookLevel, int cookNo) {
		if (cookLevel == 1) {
			if (!pc.getInventory().checkItem(41255, 1)) {
				pc.sendPackets(new S_ServerMessage(1167));
				return;
			}
		} else if (cookLevel == 2) {
			if (!pc.getInventory().checkItem(41256, 1)) {
				pc.sendPackets(new S_ServerMessage(1167)); 
				return;
			}
		} else if (cookLevel == 3) {
			if (!pc.getInventory().checkItem(41257, 1)) {
				pc.sendPackets(new S_ServerMessage(1167)); 
				return;
			}
		} else if (cookLevel == 4) {
			if (!pc.getInventory().checkItem(41258, 1)) {
				pc.sendPackets(new S_ServerMessage(1167));
				return;
			}
		} else if (cookLevel == 5) {
			if (!pc.getInventory().checkItem(41259, 1)) {
				pc.sendPackets(new S_ServerMessage(1167));
				return;
			}
		}
		boolean isNearFire = false;
		for (L1Object obj : L1World.getInstance().getVisibleObjects(pc, 3)) {
			if (obj instanceof EffectInstance) {
				EffectInstance effect = (EffectInstance) obj;
				if (effect.getCurrentSpriteId() == 5943) {
					isNearFire = true;
					break;
				}
			}
		}
		if (!isNearFire) {
			pc.sendPackets(new S_ServerMessage(1160));
			return;
		}
		if (pc.getMaxWeight() <= pc.getInventory().getWeight()) {
			pc.sendPackets(new S_ServerMessage(1103));
			return;
		}

		int chance = _random.nextInt(100) + 1;
		if (cookLevel == 1 && cookNo == 0) { // 괴물눈 스테이크
			if (pc.getInventory().checkItem(40057, 1)) {
				pc.getInventory().consumeItem(40057, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41277, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41285, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 1) { // 곰 고기
			if (pc.getInventory().checkItem(41275, 1)) {
				pc.getInventory().consumeItem(41275, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41278, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41286, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 2) { // 씨호떡
			if (pc.getInventory().checkItem(41263, 1) && pc.getInventory().checkItem(41265, 1)) {
				pc.getInventory().consumeItem(41263, 1);
				pc.getInventory().consumeItem(41265, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41279, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41287, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 3) { // 개미다리 치즈구이
			if (pc.getInventory().checkItem(41274, 1) && pc.getInventory().checkItem(41267, 1)) {
				pc.getInventory().consumeItem(41274, 1);
				pc.getInventory().consumeItem(41267, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41280, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41288, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 4) { // 과일 샐러드
			if (pc.getInventory().checkItem(40062, 1) && pc.getInventory().checkItem(40069, 1)
					&& pc.getInventory().checkItem(40064, 1)) {
				pc.getInventory().consumeItem(40062, 1);
				pc.getInventory().consumeItem(40069, 1);
				pc.getInventory().consumeItem(40064, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41281, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41289, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 5) { // 과일 탕수육
			if (pc.getInventory().checkItem(40056, 1) && pc.getInventory().checkItem(40060, 1)
					&& pc.getInventory().checkItem(40061, 1)) {
				pc.getInventory().consumeItem(40056, 1);
				pc.getInventory().consumeItem(40060, 1);
				pc.getInventory().consumeItem(40061, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41282, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41290, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 6) { // 멧돼지 꼬치 구이
			if (pc.getInventory().checkItem(41276, 1)) {
				pc.getInventory().consumeItem(41276, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41283, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41291, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		} else if (cookLevel == 1 && cookNo == 7) { // 버섯 스프
			if (pc.getInventory().checkItem(40499, 1) && pc.getInventory().checkItem(40060, 1)) {
				pc.getInventory().consumeItem(40499, 1);
				pc.getInventory().consumeItem(40060, 1);
				if (chance >= 1 && chance <= 90) {
					createNewItem(pc, 41284, 1);
				} else if (chance >= 91 && chance <= 95) {
					createNewItem(pc, 41292, 1);
				} else if (chance >= 96 && chance <= 100) {
					pc.sendPackets(new S_ServerMessage(1101)); // 요리가 실패하였습니다.
				}
			} else {
				pc.sendPackets(new S_ServerMessage(1102)); // 요리를 만들기 위한 재료가
				// 부족합니다.
			}
		}
	}

	/**
	 * 가구 제거 막대 관련
	 * @param pc
	 * @param targetId
	 * @param item
	 */
	private void useFurnitureRemovalWand(L1PcInstance pc, int targetId, ItemInstance item) {
		S_AttackStatus s_attackStatus = S_AttackStatus.get(pc, 0, ActionCodes.ACTION_Wand);
		pc.sendPackets(s_attackStatus);
		pc.broadcastPacket(s_attackStatus);
		int chargeCount = item.getChargeCount();
		if (chargeCount <= 0) {
			return;
		}

		L1Object target = L1World.getInstance().findObject(targetId);
		if (target != null && target instanceof FurnitureInstance) {
			FurnitureInstance furniture = (FurnitureInstance) target;
			furniture.deleteMe();
			FurnitureSpawnTable.getInstance().deleteFurniture(furniture);
			item.setChargeCount(item.getChargeCount() - 1);
			pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
		}
	}
	/**
	 * 회상의 촛불 관련
	 * @param pc
	 */
	private void IOReturnedStat(L1PcInstance pc) {
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
	 * 드래곤의 진주 관련 
	 * @param pc
	 */
	private void useDragonPearl(L1PcInstance pc, ItemInstance useitem) {

		int time = 600 * 1000;
		int stime = ((time / 1000) / 4) - 2;

		if (pc.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}

		if (pc.hasSkillEffect(SkillId.상태_드래곤_진주_3단계)) {
			pc.killSkillEffectTimer(SkillId.상태_드래곤_진주_3단계);
			pc.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, 0, 0));
			pc.broadcastPacket(new S_Liquor(pc.getId(), 0));
			pc.sendPackets(new S_Liquor(pc.getId(), 0));
		}

		pc.setSkillEffect(SkillId.상태_드래곤_진주_3단계, time);
		pc.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, stime, 8));
		pc.sendPackets(new S_Liquor(pc.getId(), 8));
		pc.broadcastPacket(new S_Liquor(pc.getId(), 8));
		pc.getInventory().removeItem(useitem, 1);
		pc.sendPackets(new S_ServerMessage(1065));
		S_SkillSound s = S_SkillSound.get(pc.getId(), 197);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
	}

	/**
	 * 인벤토리 정리 아이템 관련 
	 * @param pc
	 */
	private void InventoryArrangement(L1PcInstance pc) {
		try {
			FastTable<ItemInstance> controll = new FastTable<ItemInstance>();
			FastTable<ItemInstance> eq_weapon = new FastTable<ItemInstance>();
			FastTable<ItemInstance> eq_armor = new FastTable<ItemInstance>();
			FastTable<ItemInstance> weapon = new FastTable<ItemInstance>();
			FastTable<ItemInstance> armor = new FastTable<ItemInstance>();
			FastTable<ItemInstance> etcitem = new FastTable<ItemInstance>();
			for (ItemInstance item : pc.getInventory().getItems()) {
				if (item.getItem().getType2() == 0) {
					if (item.getItem().getType() == 80)
						controll.add(item);
					else
						etcitem.add(item);
				} else if (item.getItem().getType2() == 1) {
					if (item.isEquipped())
						eq_weapon.add(item);
					else
						weapon.add(item);
				} else if (item.getItem().getType2() == 2) {
					if (item.isEquipped())
						eq_armor.add(item);
					else
						armor.add(item);
				}

				pc.getInventory().removeItem(item, item.getCount());
			}

			if (eq_weapon != null) {
				for (ItemInstance weapon_item : eq_weapon) {
					pc.getInventory().storeItemBless(weapon_item);
					pc.getInventory().setEquipped(weapon_item, true);
				}
				eq_weapon.clear();
			}

			if (eq_armor != null) {
				for (ItemInstance armor_item : eq_armor) {
					pc.getInventory().storeItemBless(armor_item);
					pc.getInventory().setEquipped(armor_item, true);
				}
				eq_armor.clear();
			}

			if (etcitem != null) {
				for (ItemInstance etc_item : etcitem) {
					pc.getInventory().storeItemBless(etc_item);
				}
				etcitem.clear();
			}

			if (weapon != null) {
				for (ItemInstance weapon_item : weapon) {
					pc.getInventory().storeItemBless(weapon_item);
				}
				weapon.clear();
			}

			if (armor != null) {
				for (ItemInstance armor_item : armor) {
					pc.getInventory().storeItemBless(armor_item);
				}
				armor.clear();
			}

			if (controll != null) {
				for (ItemInstance control_item : controll) {
					pc.getInventory().storeItemBless(control_item);
				}
				controll.clear();
			}

			pc.save();
			pc.saveInventory();
			pc.sendPackets("인벤토리 정리가 완료 되었습니다.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * 엔피시 확인 막대 관련
	 * @param pc
	 * @param targetId
	 * @param item
	 */
	private void useFieldObjectIdentyWand(L1PcInstance pc, int targetId, ItemInstance item) {
		S_AttackPacket s_attackStatus = S_AttackPacket.get(pc, 0, ActionCodes.ACTION_Wand);
		pc.sendPackets(s_attackStatus);
		pc.broadcastPacket(s_attackStatus);
		L1Object target = L1World.getInstance().findObject(targetId);
		if (target != null && target instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) target;
			pc.sendPackets("번호 : (" + npc.getNpcId() + "-" + targetId + ") // " + "이름 : (" + npc.getName() + ")");
			pc.sendPackets("위치 : " + "(x:" + npc.getX() + "), (y:" + npc.getY() + "), (" + "Map:" + npc.getMapId() + ") // GfxId : (" + npc.getCurrentSpriteId() + ")");
			pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
		}
	}

	/**
	 * 엔피시 제거 막대 관련
	 * @param pc
	 * @param targetId
	 * @param item
	 */
	private void useFieldObjectRemovalWand(L1PcInstance pc, int targetId, ItemInstance item) {
		S_AttackPacket s_attackStatus = S_AttackPacket.get(pc, 0, ActionCodes.ACTION_Wand);
		pc.sendPackets(s_attackStatus);
		pc.broadcastPacket(s_attackStatus);
		L1Object target = L1World.getInstance().findObject(targetId);
		if (target != null && target instanceof L1NpcInstance && !(target instanceof DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) target;
			NpcSpawnTable.getInstance().removeSpawn(npc);
			npc.setRespawn(false);
			new NpcDeleteTimer(npc, 1000).begin();
			pc.sendPackets(new S_SystemMessage(npc.getNpcId() + npc.getName() + " 을(를) 1초 뒤에 삭제 합니다.")); 
			pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
		}
	}

	/**
	 * 몬스터 드랍 확인 막대 관련
	 * @param pc
	 * @param targetId
	 * @param item
	 */
	private void monsterDrobConfItem(L1PcInstance pc, int targetId, ItemInstance item) {
		long curtime = System.currentTimeMillis() / 1000;
		if (pc.getQuizTime() + 2 > curtime) {
			long time = (pc.getQuizTime() + 2) - curtime;
			pc.sendPackets(new S_SystemMessage(time + " 초 후 사용 하시길 바랍니다."));
			return;
		}
		try {
			S_AttackPacket s_attackStatus = S_AttackPacket.get(pc, 0, ActionCodes.ACTION_Wand);
			pc.sendPackets(s_attackStatus);
			Broadcaster.broadcastPacket(pc, s_attackStatus);
			L1Object target = L1World.getInstance().findObject(targetId);
			if (target != null && target instanceof MonsterInstance) {
				MonsterInstance npc = (MonsterInstance) target;
				int transformId = npc.getNpcTemplate().getTransformId();
				if (target != null && transformId == -1) {
					pc.sendPackets(new S_MonsterDropList(npc.getNpcId()));
					pc.setQuizTime(curtime);
				} else {
					pc.sendPackets(new S_MonsterDropList(transformId));
					pc.setQuizTime(curtime);
				}
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage("해당 몬스터 검색에 실패하였습니다."));
		}
	}
	/**
	 * 독 해제 관련 
	 * @param pc
	 * @param item_id
	 */
	private void UsePoisonReleasePotions(L1PcInstance pc, ItemInstance useitem) {

		if (pc.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);
		S_SkillSound s = S_SkillSound.get(pc.getId(), 192);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
		pc.curePoison();
		pc.cureParalaysis();
		pc.getInventory().removeItem(useitem, 1);
	}
	/**
	 * 클래스 변경 관련 
	 * @param pc
	 */
	private static void deleteSpell(L1PcInstance pc) {
		int player = pc.getId();
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_skills WHERE char_obj_id=?");
			pstm.setInt(1, player);
			pstm.execute();
		} catch (Exception e) {
			System.out.println("클래스변경스킬삭제에러 : " + e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	} 
	/**
	 * 클래스 스킬북 
	 * @param pc
	 * @param itemId
	 * @param useitem
	 */
	private void UseSkillBook(L1PcInstance pc, int itemId, ItemInstance useitem) {

		//마법 안배워지는거 넣기
		
		if (itemId > 40169 && itemId <= 40178 || itemId >= 40179 && itemId < 40226 || itemId >= 45000 && itemId <= 45022) { // 마법서
			byte byte0 = 0;
			boolean flag1 = false;
			boolean isLawful = true;
			if (itemId == 45000 || itemId == 45008 || itemId == 45018
					|| itemId == 45021 || itemId == 40171
					|| itemId == 40179 || itemId == 40180
					|| itemId == 40182 || itemId == 40194
					|| itemId == 40197 || itemId == 40202
					|| itemId == 40206 || itemId == 40213
					|| itemId == 40220 || itemId == 40222) {
				byte0 = 1;
			}
			if (itemId == 45009 || itemId == 45010 || itemId == 45019
					|| itemId == 40172 || itemId == 40173
					|| itemId == 40185 || itemId == 40178
					|| itemId == 40186 || itemId == 40192
					|| itemId == 40196 || itemId == 40201
					|| itemId == 40204 || itemId == 40211
					|| itemId == 40221 || itemId == 40225) { 
				byte0 = 2;
			}
			
			int pcX = pc.getX(), pcY = pc.getY(), mapId = pc.getMapId();
			// 로우후르텐풀
			if ((byte0 == 0 || byte0 == 1)
					&& (pcX > 33116 && pcX < 33128 && pcY > 32930 && pcY < 32942 && mapId == 4
					|| pcX > 33135 && pcX < 33147 && pcY > 32235 && pcY < 32247 && mapId == 4
					|| pcX >= 32783 && pcX <= 32803 && pcY >= 32831 && pcY <= 32851 && mapId == 77
					|| mapId == 4)) {
				flag1 = true;
				isLawful = true;
			}
			// 카오틱텐풀
			if ((byte0 == 0 || byte0 == 2)
					&& (pcX > 32880 && pcX < 32892 && pcY > 32646 && pcY < 32658 && mapId == 4
					|| pcX > 33135 && pcX < 33147 && pcY > 32235 && pcY < 32247 && mapId == 4
					|| pcX > 32662 && pcX < 32674 && pcY > 32297 && pcY < 32309 && mapId == 4
					|| mapId == 4)) {
				flag1 = true;
				isLawful = false;
			}
			if (pc.isGm()) {
				SpellBook(pc, useitem, isLawful);
			} else if (flag1) {
				if (pc.isKnight()) {
					if (itemId >= 45000 && itemId <= 45007 && pc.getLevel() >= 50) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45000 && itemId <= 45007) {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));
					} else {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1)); 
					}
				} else if (pc.isCrown() || pc.isDarkelf()) {
					if (itemId >= 45000 && itemId <= 45007 && pc.getLevel() >= 10) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45008 && itemId <= 45015 && pc.getLevel() >= 20) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45008 && itemId <= 45015 || itemId >= 45000 && itemId <= 45007) {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));
					} else {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1)); 
					}
				} else if (pc.isElf()) {
					if (itemId >= 45000 && itemId <= 45007 && pc.getLevel() >= 8) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45008 && itemId <= 45015 && pc.getLevel() >= 16) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45016 && itemId <= 45022 && pc.getLevel() >= 24) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40170 && itemId <= 40177 && pc.getLevel() >= 32) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40178 && itemId <= 40185 && pc.getLevel() >= 40) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40186 && itemId <= 40193 && pc.getLevel() >= 48) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45000 && itemId <= 45022 || itemId >= 40170 && itemId <= 40193) {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));
					} else {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1)); 
					}
				} else if (pc.isWizard()) {
					if (itemId >= 45000 && itemId <= 45007 && pc.getLevel() >= 4) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45008 && itemId <= 45015 && pc.getLevel() >= 8) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 45016 && itemId <= 45022 && pc.getLevel() >= 12) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40170 && itemId <= 40177 && pc.getLevel() >= 16) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40178 && itemId <= 40185 && pc.getLevel() >= 20) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40186 && itemId <= 40193 && pc.getLevel() >= 24) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40194 && itemId <= 40201 && pc.getLevel() >= 28) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40202 && itemId <= 40209 && pc.getLevel() >= 32) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40210 && itemId <= 40217 && pc.getLevel() >= 36) {
						SpellBook(pc, useitem, isLawful);
					} else if (itemId >= 40218 && itemId <= 40225 && pc.getLevel() >= 40) {
						SpellBook(pc, useitem, isLawful);
					} else {
						pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));
					}
				}
			} else if (byte0 == 1 || byte0 == 2) {
				if (pc.getMapId() != 4) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_5)); 
					return;
				}
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1)); 
				S_SkillSound s = S_SkillSound.get(pc.getId(), 10);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
				pc.getInventory().removeItem(useitem, 1); 
			} else {
				pc.sendPackets(new S_ServerMessage(79));
			}
		} else if(pc.isCrown() || pc.isGm()) {
			if(itemId == ItemId.마법서_트루_타겟) {
				if(pc.getLevel() >= 15) {
					CrownSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else if(itemId == ItemId.마법서_콜_클랜) {
				if(pc.getLevel() >= 30) {
					CrownSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else if(itemId == ItemId.마법서_글루잉_오라) {
				if(pc.getLevel() >= 40) {
					CrownSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}	    	
			} else if(itemId == ItemId.마법서_런_클랜) {
				if(pc.getLevel() >= 45) {
					CrownSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else if(itemId == ItemId.마법서_브레이브_오라) {
				if(pc.getLevel() >= 50) {
					CrownSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else if(itemId == ItemId.마법서_샤이닝_오라) {
				if(pc.getLevel() >= 55) {
					CrownSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1)); 
			}		
		} else if(pc.isKnight() || pc.isGm()) {	
			if(itemId == ItemId.기술서_쇼크_스턴 || itemId == ItemId.기술서_리덕션_아머 
					|| itemId == ItemId.기술서_솔리드_캐리지 || itemId == ItemId.기술서_카운터_배리어) {
				if(pc.getLevel() >= 50) {
					KnightSkillBook(pc, useitem);	
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}		
			} else if(itemId == ItemId.기술서_바운스_어택) {
				if(pc.getLevel() >= 60) {
					KnightSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}	
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
		} else if(pc.isDarkelf() || pc.isGm()) {
			if(itemId == ItemId.흑정령_수정_블라인드_하이딩 || itemId == ItemId.흑정령_수정_인챈트_베놈 || itemId == ItemId.흑정령_수정_쉐도우_아머 
					|| itemId == ItemId.흑정령_수정_브링_스톤 || itemId == ItemId.흑정령_수정_드레스_마이티) { 
				if(pc.getLevel() >= 15) {
					DarkeelfSkillBook(pc, useitem);	
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}	
			} else if(itemId == ItemId.흑정령_수정_무빙_악셀레이션 || itemId == ItemId.흑정령_수정_버닝_스피릿츠 || itemId == ItemId.흑정령_수정_다크_블라인드
					|| itemId == ItemId.흑정령_수정_베놈_레지스트 || itemId == ItemId.흑정령_수정_드레스_덱스터리티) {
				if(pc.getLevel() >= 30) {
					DarkeelfSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}			
			} else if(itemId == ItemId.흑정령_수정_더블_브레이크 || itemId == ItemId.흑정령_수정_언케니_닷지
					|| itemId == ItemId.흑정령_수정_쉐도우_팽 || itemId == ItemId.흑정령_수정_아머_브레이크 || itemId == ItemId.흑정령_수정_드레스_이베이젼) {
				if(pc.getLevel() >= 45) {
					DarkeelfSkillBook(pc, useitem);			
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
		} else if(pc.isElf() || pc.isGm()) {
			if(itemId == ItemId.정령_수정_레지스트_매직 || itemId == ItemId.정령_수정_바디_투_마인드 || itemId == ItemId.정령_수정_텔레포트_투_마더) {
				if(pc.getLevel() >= 10) {
					ElfSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}	
			} else if(itemId == ItemId.정령_수정_클리어_마인드 || itemId == ItemId.정령_수정_레지스트_엘리멘트) {
				if(pc.getLevel() >= 20) {
					ElfSkillBook(pc, useitem);
				} else {		
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15)); 
				}
			} else if(itemId == ItemId.정령_수정_리턴_투_네이쳐  || itemId == ItemId.정령_수정_블러드_투_소울 || itemId == ItemId.정령_수정_프로텍션_프롬_엘리멘탈
					|| itemId == ItemId.정령_수정_트리플_애로우 || itemId == ItemId.정령_수정_어스_스킨 || itemId == ItemId.정령_수정_인탱글
					|| itemId == ItemId.정령_수정_워터_라이프 || itemId == ItemId.정령_수정_네이쳐스_터치 || itemId == ItemId.정령_수정_파이어_웨폰 
					|| itemId == ItemId.정령_수정_윈드_샷 || itemId == ItemId.정령_수정_윈드워크) {
				if(pc.getLevel() >= 30) {
					ElfSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));  
				}
			} else if(itemId == ItemId.정령_수정_엘리멘탈_폴다운 || itemId == ItemId.정령_수정_이레이즈_매직 || itemId == ItemId.정령_수정_서먼_레서_엘리멘탈
					|| itemId == ItemId.정령_수정_어스_바인드 || itemId == ItemId.정령_수정_블레스_오브_어스 || itemId == ItemId.정령_수정_블레스_오브_파이어
					|| itemId == ItemId.정령_수정_아이_오브_스톰 || itemId == ItemId.정령_수정_아쿠아_프로텍트) {
				if(pc.getLevel() >= 40) {
					ElfSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));  
				}
			} else if(itemId == ItemId.정령_수정_에이리어_오브_사일런스 || itemId == ItemId.정령_수정_서먼_그레이터_엘리멘탈 || itemId == ItemId.정령_수정_카운터_미러
					|| itemId == ItemId.정령_수정_아이언_스킨 || itemId == ItemId.정령_수정_엑조틱_바이탈라이즈 || itemId == ItemId.정령_수정_네이쳐스_블레싱
					|| itemId == ItemId.정령_수정_버닝_웨폰  || itemId == ItemId.정령_수정_엘리멘탈_파이어 || itemId == ItemId.정령_수정_스톰_샷
					|| itemId == ItemId.정령_수정_윈드_셰클 || itemId == ItemId.정령_수정_소울_오브_프레임 || itemId == ItemId.정령_수정_어디셔널_파이어
					|| itemId == ItemId.정령_수정_폴루트_워터 || itemId == ItemId.정령_수정_스트라이커_게일) {
				if(pc.getLevel() >= 50) {
					ElfSkillBook(pc, useitem);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_15));  
				}
			} else {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			}
		}
	}
	
	private void SpellBook(L1PcInstance pc, ItemInstance item, boolean isLawful) {
		String s = "";
		int i = 0, level1 = 0, level2 = 0, l = 0, i1 = 0,j1 = 0, k1 = 0, l1 = 0, i2 = 0, j2 = 0,
				k2 = 0, l2 = 0, i3 = 0, j3 = 0, k3 = 0, l3 = 0, i4 = 0, j4 = 0, k4 = 0, l4 = 0,
				i5 = 0, j5 = 0, k5 = 0, l5 = 0, i6 = 0;
		for (int skillId = 1; skillId < 81; skillId++) {
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
			String s1 = (new StringBuilder()).append("마법서 (").append(l1skills.getName()).append(")").toString();
			if (item.getItem().getName().equalsIgnoreCase(s1)) {
				int skillLevel = l1skills.getSkillLevel(), i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId(); 
				switch (skillLevel) {
				case 1: level1 = i7;   break;
				case 2: level2 = i7;   break;
				case 3:  l = i7;       break;
				case 4:  i1 = i7;      break;
				case 5:  j1 = i7;      break;
				case 6:  k1 = i7;      break;
				case 7:  l1 = i7;      break;
				case 8:  i2 = i7;      break;
				case 9:  j2 = i7;      break;
				case 10: k2 = i7;      break;
				case 11: l2 = i7;      break;
				case 12: i3 = i7;      break;
				case 13: j3 = i7;      break;
				case 14: k3 = i7;      break;
				case 15: l3 = i7;      break;
				case 16: i4 = i7;      break;
				case 17: j4 = i7;      break;
				case 18: k4 = i7;      break;
				case 19: l4 = i7;      break;
				case 20: i5 = i7;      break;
				case 21: j5 = i7;      break;
				case 22: k5 = i7;      break;
				case 23: l5 = i7;      break;
				case 24: i6 = i7;      break;
				}
			}
		}
		if (pc.isSkillMastery(i)) {
			pc.sendPackets(new S_ServerMessage(3237), true);
			return;
		}
		int objid = pc.getId();
		pc.sendPackets(new S_AddSkill(level1, level2, l, i1, j1, k1, l1, i2, j2, k2, l2, i3, j3, k3, l3, i4, j4, k4, l4, i5, j5, k5, l5, i6), true); 
		S_SkillSound Sound = S_SkillSound.get(objid, isLawful ? 224 : 231);
		pc.sendPackets(Sound);
		pc.broadcastPacket(Sound);
		SkillsTable.getInstance().spellMastery(objid, i, s, 0, 0);
		pc.getInventory().removeItem(item, 1);
	}

	private void DarkeelfSkillBook(L1PcInstance pc, ItemInstance useItem) {
		String s = "";
		int i = 0, j = 0, k = 0, l = 0, i1 = 0, j1 = 0, k1 = 0, l1 = 0, i2 = 0, j2 = 0, k2 = 0,
				l2 = 0, i3 = 0, j3 = 0, k3 = 0, l3 = 0, i4 = 0, j4 = 0, k4 = 0, l4 = 0, i5 = 0, j5 = 0,
				k5 = 0, l5 = 0, i6 = 0;
		for (int j6 = 97; j6 < 112; j6++) {
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(j6);
			String s1 = (new StringBuilder()).append("흑정령의 수정 (").append(l1skills.getName()).append(")").toString();
			if (useItem.getItem().getName().equalsIgnoreCase(s1)) {
				int l6 = l1skills.getSkillLevel();
				int i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId();
				switch (l6) {
				case 1:  j = i7;  break;
				case 2:  k = i7;  break;
				case 3:  l = i7;  break;
				case 4:  i1 = i7; break;
				case 5:  j1 = i7; break;
				case 6:  k1 = i7; break;
				case 7:  l1 = i7; break;
				case 8:  i2 = i7; break;
				case 9:  j2 = i7; break;
				case 10: k2 = i7; break;
				case 11: l2 = i7; break;
				case 12: i3 = i7; break;
				case 13: j3 = i7; break;
				case 14: k3 = i7; break;
				case 15: l3 = i7; break;
				case 16: i4 = i7; break;
				case 17: j4 = i7; break;
				case 18: k4 = i7; break;
				case 19: l4 = i7; break;
				case 20: i5 = i7; break;
				case 21: j5 = i7; break;
				case 22: k5 = i7; break;
				case 23: l5 = i7; break;
				case 24: i6 = i7; break;
				}
			}
		}
		if (pc.isSkillMastery(i)) {
			pc.sendPackets(new S_ServerMessage(3237), true);
			return;
		}
		int k6 = pc.getId();
		pc.sendPackets(new S_AddSkill(j, k, l, i1, j1, k1, l1, i2, j2, k2, l2,i3, j3, k3, l3, i4, j4, k4, l4, i5, j5, k5, l5, i6), true);
		S_SkillSound Sound = S_SkillSound.get(k6, 231);
		pc.sendPackets(Sound);
		pc.broadcastPacket(Sound);
		SkillsTable.getInstance().spellMastery(k6, i, s, 0, 0);
		pc.getInventory().removeItem(useItem, 1);
	}

	private void ElfSkillBook(L1PcInstance pc, ItemInstance useItem) {
		String s = "";
		int i = 0, j = 0, k = 0, l = 0, i1 = 0, j1 = 0, k1 = 0, l1 = 0, i2 = 0, j2 = 0, k2 = 0, l2 = 0, i3 = 0,
				j3 = 0, k3 = 0, l3 = 0, i4 = 0, j4 = 0, k4 = 0, l4 = 0, i5 = 0, j5 = 0, k5 = 0, l5 = 0, i6 = 0;
		for (int j6 = 129; j6 <= 176; j6++) {
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(j6);
			String s1 = (new StringBuilder()).append("정령의 수정 (").append(l1skills.getName()).append(")").toString();
			if (useItem.getItem().getName().equalsIgnoreCase(s1)) {
				if (!pc.isGm() && l1skills.getAttr() != 0 && pc.getElfAttr() != l1skills.getAttr()) {
					if (pc.getElfAttr() == 0 || pc.getElfAttr() == 1 || pc.getElfAttr() == 2 
							|| pc.getElfAttr() == 4 || pc.getElfAttr() == 8) { 
						pc.sendPackets(new S_ServerMessage(79));
						return;
					}
				}
				int l6 = l1skills.getSkillLevel();
				int i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId();
				switch (l6) {
				case 1:  j = i7;  break;
				case 2:  k = i7;  break;
				case 3:  l = i7;  break;
				case 4:  i1 = i7; break;
				case 5:  j1 = i7; break;
				case 6:  k1 = i7; break;
				case 7:  l1 = i7; break;
				case 8:  i2 = i7; break;
				case 9:  j2 = i7; break;
				case 10: k2 = i7; break;
				case 11: l2 = i7; break;
				case 12: i3 = i7; break;
				case 13: j3 = i7; break;
				case 14: k3 = i7; break;
				case 15: l3 = i7; break;
				case 16: i4 = i7; break;
				case 17: j4 = i7; break;
				case 18: k4 = i7; break;
				case 19: l4 = i7; break;
				case 20: i5 = i7; break;
				case 21: j5 = i7; break;
				case 22: k5 = i7; break;
				case 23: l5 = i7; break;
				case 24: i6 = i7; break;
				}
			}
		}
		if (pc.isSkillMastery(i)) {
			pc.sendPackets(new S_ServerMessage(3237), true);
			return;
		}
		int k6 = pc.getId();
		pc.sendPackets(new S_AddSkill(j, k, l, i1, j1, k1, l1, i2, j2, k2, l2,i3, j3, k3, l3, i4, j4, k4, l4, i5, j5, k5, l5, i6), true);
		S_SkillSound s_skillSound = S_SkillSound.get(k6, 224);
		pc.sendPackets(s_skillSound, false);
		pc.broadcastPacket(s_skillSound, true);
		SkillsTable.getInstance().spellMastery(k6, i, s, 0, 0);
		pc.getInventory().removeItem(useItem, 1);
	}

	private void KnightSkillBook(L1PcInstance pc, ItemInstance useItem) {
		String s = "";
		int i = 0, j = 0, k = 0, l = 0, i1 = 0, j1 = 0, k1 = 0, l1 = 0, i2 = 0, j2 = 0, k2 = 0, l2 = 0, i3 = 0,
				j3 = 0, k3 = 0, l3 = 0, i4 = 0, j4 = 0, k4 = 0, l4 = 0, i5 = 0, j5 = 0, k5 = 0, l5 = 0, i6 = 0;
		for (int j6 = 87; j6 <= 91; j6++) {
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(j6);
			String s1 = (new StringBuilder()).append("기술서 (").append(l1skills.getName()).append(")").toString();
			if (useItem.getItem().getName().equalsIgnoreCase(s1)) {
				int l6 = l1skills.getSkillLevel();
				int i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId();
				switch (l6) {
				case 1:  j = i7;  break;
				case 2:  k = i7;  break;
				case 3:  l = i7;  break;
				case 4:  i1 = i7; break;
				case 5:  j1 = i7; break;
				case 6:  k1 = i7; break;
				case 7:  l1 = i7; break;
				case 8:  i2 = i7; break;
				case 9:  j2 = i7; break;
				case 10: k2 = i7; break;
				case 11: l2 = i7; break;
				case 12: i3 = i7; break;
				case 13: j3 = i7; break;
				case 14: k3 = i7; break;
				case 15: l3 = i7; break;
				case 16: i4 = i7; break;
				case 17: j4 = i7; break;
				case 18: k4 = i7; break;
				case 19: l4 = i7; break;
				case 20: i5 = i7; break;
				case 21: j5 = i7; break;
				case 22: k5 = i7; break;
				case 23: l5 = i7; break;
				case 24: i6 = i7; break;
				}
			}
		}
		if (pc.isSkillMastery(i)) {
			pc.sendPackets(new S_ServerMessage(3237), true);
			return;
		}
		int k6 = pc.getId();
		pc.sendPackets(new S_AddSkill(j, k, l, i1, j1, k1, l1, i2, j2, k2, l2,i3, j3, k3, l3, i4, j4, k4, l4, i5, j5, k5, l5, i6), true);
		S_SkillSound s_skillSound = S_SkillSound.get(k6, 224);
		pc.sendPackets(s_skillSound, false);
		pc.broadcastPacket(s_skillSound, true);
		SkillsTable.getInstance().spellMastery(k6, i, s, 0, 0);
		pc.getInventory().removeItem(useItem, 1);
	}

	private void CrownSkillBook(L1PcInstance pc, ItemInstance useItem) {
		String s = "";
		int i = 0, j = 0, k = 0, l = 0, i1 = 0, j1 = 0, k1 = 0, l1 = 0, i2 = 0, j2 = 0, k2 = 0, l2 = 0, i3 = 0,
				j3 = 0, k3 = 0, l3 = 0, i4 = 0, j4 = 0, k4 = 0, l4 = 0, i5 = 0, j5 = 0, k5 = 0, l5 = 0, i6 = 0;

		for (int j6 = 113; j6 < 122 || j6 == 32; j6++) {
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(j6);
			String s1 = (new StringBuilder()).append("마법서 (").append(l1skills.getName()).append(")").toString();
			if (useItem.getItem().getName().equalsIgnoreCase(s1)) {
				int l6 = l1skills.getSkillLevel();
				int i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId(); 
				switch (l6) {
				case 1:  j = i7;  break;
				case 2:  k = i7;  break;
				case 3:  l = i7;  break;
				case 4:  i1 = i7; break;
				case 5:  j1 = i7; break;
				case 6:  k1 = i7; break;
				case 7:  l1 = i7; break;
				case 8:  i2 = i7; break;
				case 9:  j2 = i7; break;
				case 10: k2 = i7; break;
				case 11: l2 = i7; break;
				case 12: i3 = i7; break;
				case 13: j3 = i7; break;
				case 14: k3 = i7; break;
				case 15: l3 = i7; break;
				case 16: i4 = i7; break;
				case 17: j4 = i7; break;
				case 18: k4 = i7; break;
				case 19: l4 = i7; break;
				case 20: i5 = i7; break;
				case 21: j5 = i7; break;
				case 22: k5 = i7; break;
				case 23: l5 = i7; break;
				case 24: i6 = i7; break;
				}
			}
		}
		if (pc.isSkillMastery(i)) {
			pc.sendPackets(new S_ServerMessage(3237), true);
			return;
		}
		int k6 = pc.getId(); 
		pc.sendPackets(new S_AddSkill(j, k, l, i1, j1, k1, l1, i2, j2, k2, l2,i3, j3, k3, l3, i4, j4, k4, l4, i5, j5, k5, l5, i6), true);
		S_SkillSound s_skillSound = S_SkillSound.get(k6, 224);
		pc.sendPackets(s_skillSound, false);
		pc.broadcastPacket(s_skillSound, true);
		SkillsTable.getInstance().spellMastery(k6, i, s, 0, 0);
		pc.getInventory().removeItem(useItem, 1);
	}

	@Override
	public String getType() {
		return C_ITEM_USE;
	}
}
