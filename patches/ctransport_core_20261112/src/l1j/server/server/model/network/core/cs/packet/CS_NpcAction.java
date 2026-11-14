package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ApplyAuction;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_NoSell;
import l1j.server.server.model.network.core.sc.packet.SC_SellHouse;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.sc.packet.SC_Drawal;

import l1j.server.server.model.network.core.sc.packet.SC_Deposit;

import l1j.server.server.model.network.core.sc.packet.SC_TaxRate;

import l1j.server.server.model.network.core.sc.packet.SC_HouseMap;

import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.server.model.network.core.sc.packet.SC_SelectTarget;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.DogFight.MJDogFightSettings;
import l1j.server.DogFight.History.MJDogFightHistory;
import l1j.server.IndunSystem.BossRaid.BossRaid;
import l1j.server.IndunSystem.BossRaid.BossRaidSystem;
import l1j.server.IndunSystem.BossRaid2.Queen1;
import l1j.server.IndunSystem.BossRaid2.QueenSystem2;
import l1j.server.IndunSystem.Hadin.HadinSystem;
import l1j.server.IndunSystem.Ice.Demon.DemonSystem;
import l1j.server.IndunSystem.Ice.Queen.QueenSystem;
import l1j.server.MJActionListener.ActionListener;
import l1j.server.MJActionListener.ActionListenerLoader;
import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJINNSystem.Loader.MJINNHelperLoader;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.Builder.MJServerPacketBuilder;
import l1j.server.MJTemplate.MJClassesType.EClassesType;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.NeigeCard;
import l1j.server.server.NeigeCard2;
import l1j.server.server.NeigeCard3;
import l1j.server.server.NeigeGM;
import l1j.server.server.NeigeLow;
import l1j.server.server.NeigeNormal;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1DeathMatch;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PetMatch;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1Racing;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1UltimateBattle;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.npc.action.ActionControlHandler;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.shop.L1AssessedItem;
import l1j.server.server.model.shop.L1Shop;
import LIN.SC_PERSONAL_SHOP_ITEM_LIST_NOTI;
import LIN.SC_WAREHOUSE_ITEM_LIST_NOTI;

import l1j.server.server.model.network.core.sc.packet.SC_AuctionBoardRead;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ClanWareHouseHistory;
import l1j.server.server.model.network.core.sc.packet.SC_ClassShop;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_PetList;
import l1j.server.server.model.network.core.sc.packet.SC_PremiumShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_RaceBoard;
import l1j.server.server.model.network.core.sc.packet.SC_RetrieveList;

import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShopBuyList;
import l1j.server.server.model.network.core.sc.packet.SC_ShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1PetType;
import l1j.server.server.templates.L1Town;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.L1SpawnUtil;

import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
public final class CS_NpcAction {

	private static final byte[] EMPTY_BYTES = new byte[0];

	/*
	 * 원본도 static Random 하나를 공유했다.
	 * packet wrapper만 제거하고 random semantics는 그대로 유지.
	 */
	private static final Random _random = new Random(System.nanoTime());

	private CS_NpcAction() {
	}

    private static boolean sendTaxScreen(L1PcInstance pc, int objId, String page, int castleId) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String value = Integer.toString(MJCastleWarBusiness.getInstance().getTaxRate(castleId));
        final long size = 24L + 4L * (page.length() + value.length());
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int)size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(objId);
            buffer.writeS(page);
            buffer.writeH(1);
            buffer.writeH(1);
            buffer.writeS(value);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

	public static void handle(
			CIOClient client,
			int objid,
			String s,
			String s2,
			byte[] actionData) {

		if (client == null) return;

		// 게임중이 아니면
		if (client.getConnectionState() != ConnectionState.IN_GAME) {
			return;
		}

		try {
			L1PcInstance pc = client.getActiveChar();
			if (pc == null) return;
			
			if (s == null) return;

			if (s.equalsIgnoreCase("deadTrans") || s.equalsIgnoreCase("pvpSet") || s.equalsIgnoreCase("ShowHPMPRecovery")
					|| s.equalsIgnoreCase("showDisableEffectIcon") || s.equalsIgnoreCase("showDungeonTimeLimit")) {
				return;
			}

			if(s.contains("nenome") || s.contains("nenormal")){
				SC_SkillSound.send(pc, pc.getId(), 21174);
				if(s.contains("nenormal")){
					if (!(pc.getInventory().checkItem(875640379, 1))) {
						SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
						return;
					}
					if(pc.isInvisble()){
						pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
						return;
					}
					pc.getInventory().consumeItem(875640379, 1);
				}
				NeigeNormal.Normal(pc, s);
				return;
			}
			if(s.contains("nelome") || s.contains("nelowp")){
				SC_SkillSound.send(pc, pc.getId(), 21174);
				if(s.contains("nelowp")){
					if(pc.getLevel() < 48){
						SC_ChatPacket.sendDirect(pc, pc, "\\aG희귀 등급 변신은 48레벨 부터 변신할 수 있습니다.");
						return;
					}
					if (!(pc.getInventory().checkItem(875640379, 1))) {
						SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
						return;
					}
					if(pc.isInvisble()){
						pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
						return;
					}
					pc.getInventory().consumeItem(875640379, 1);
				}
				NeigeLow.Low(pc, s);
				return;
			}
			if(s.contains("rnlpoly") || s.contains("rnalce")){
				SC_SkillSound.send(pc, pc.getId(), 21174);
				NeigeCard.Neige(pc, s);
				return;
			}
			if(s.contains("rnll")){
				SC_SkillSound.send(pc, pc.getId(), 21174);
				NeigeCard2.NeigeLegend(pc, s);
				return;
			}
			if(s.contains("rnmy")){
				SC_SkillSound.send(pc, pc.getId(), 21174);
				NeigeCard3.NeigeMyth(pc, s);
				return;
			}
			if(s.contains("rsnmy")){
				SC_SkillSound.send(pc, pc.getId(), 633);
				NeigeCard3.NeigeMyth(pc, s);
				return;
			}
			if(s.contains("neigma")){
				if(!pc.isGm()){
					pc.sendPackets("관리자만 이용할 수 있습니다.");
					return;
				}else{
				SC_SkillSound.send(pc, pc.getId(), 21174);
				NeigeGM.getInstance().gm(pc, s);
				}
				return;
			}
			
			// Retired legacy auto commands: do not pass them to NPC actions.
            if (s.startsWith("ps_potion_") || s.startsWith("ps_autobuff_")
                    || s.matches("ps_[0-9]+")) return;
			
			// 각종 서포터 명령어 액션 핸들러
			if (ActionControlHandler.getInstance().handleAction(pc, s)) {
	            return;
	        }

			int[] materials = null;
			int[] counts = null;
			int[] createitem = null;
			int[] createcount = null;

			String htmlid = null;
			String success_htmlid = null;
			String failure_htmlid = null;
			String[] htmldata = null;

			
			if (pc == null || pc.isGhost() || pc.isPrivateShop() || pc.is무인상점() || pc.isAutoClanjoin())
				return;

		//	System.out.println("Action : " + s);
			L1PcInstance target;
			L1Object obj = L1World.getInstance().findObject(objid);
			if (obj != null) {
				if (obj instanceof L1NpcInstance) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					int difflocx = Math.abs(pc.getX() - npc.getX());
					int difflocy = Math.abs(pc.getY() - npc.getY());
					int nd = Config.네쥬던전초기화;
					
					// 보안 체크: 맵 일치 및 거리 확인
					if (pc.getMapId() != npc.getMapId()) return;
					if (Math.abs(pc.getX() - npc.getX()) > 6 || Math.abs(pc.getY() - npc.getY()) > 6) return;
					
	//
					if(npc.getNpcId() == 7000020 || npc.getNpcId() == 460000378 || npc.getNpcId() == 460000379){ //기감 시간 초기화 에러 방지 neige
						if(pc.getAccount().gethero21() != nd ){
							if(Config.네쥬던전체킹 == 1){
								DungeonTimeProgressLoader.delete(pc);
								pc.initialize_dungeon_progress();
								System.out.println("던전체킹 1 , 네쥬 기감 초기화 : "+pc.getName());
							}else{
								DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(1);
								DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
								if(progress != null){
									progress.set_remain_seconds(dtInfo.get_amount_seconds());
									DungeonTimeProgressLoader.update(progress);
									System.out.println("던전체킹 2 , 네쥬 기감 초기화 : "+pc.getName());
								}
							}
							pc.getAccount().sethero21(nd);
							pc.getAccount().updateHero21();
						}
					}
					npc.onFinalAction(pc, s);
					if(s.contains("buy")){SC_SkillSound.send(pc, pc.getId(), 21129);}
					if(s.contains("sell")){SC_SkillSound.send(pc, pc.getId(), 21129);}
					if (pc.isGm()) {
						SC_SystemMessage.send(pc, "\\f:[NPC번호]  \\aA" + npc.getNpcId() + "     \\f:[액션]  \\aA" + s);
						SC_SystemMessage.send(pc, "\\f:[obj번호]  \\aA" + objid);
						
					}
				} else if (obj instanceof L1PcInstance) {
					target = (L1PcInstance) obj;
					if (s.matches("[0-9]+")) {
						summonMonster(target, s);
					} else {
						if (target.isMagicItem()) { // 변신 마법서
							L1PolyMorph.MagicBookPoly(target, s, 1200);
							target.setMagicItem(false);
						} else {
							L1PolyMorph.handleCommands(target, s);
						}
					}
					return;
				}
			} else {
				return;
			}
			
			if(obj.instanceOf(MJL1Type.L1TYPE_NPC)){
				if(MJINNHelperLoader.getInstance().onAction((L1NpcInstance)obj, pc, s))
					return;
			}
			
			L1NpcAction action = NpcActionTable.getInstance().get(s, pc, obj);
			if (action != null) {
				L1NpcHtml result = action.execute(s, pc, obj, actionData != null ? actionData : EMPTY_BYTES);
				if (result != null) {
					SC_NPCTalkReturn.send(pc, obj.getId(), result);
				}
				return;
			}

            // Castle tax NPC screens: serialize the hypertext reply directly.
            if (s.equalsIgnoreCase("andyn3")) {
                sendTaxScreen(pc, obj.getId(), "andyn3", 1);
            } else if (s.equalsIgnoreCase("vola3")) {
                sendTaxScreen(pc, obj.getId(), "vola3", 3);
            } else if (s.equalsIgnoreCase("s_merchant3")) {
                sendTaxScreen(pc, obj.getId(), "s_merchant3", 4);
            } else if (s.equalsIgnoreCase("vergil3")) {
                sendTaxScreen(pc, obj.getId(), "vergil3", 4);
            } else if (s.equalsIgnoreCase("shivan3")) {
                sendTaxScreen(pc, obj.getId(), "shivan3", 5);
            } else if (s.equalsIgnoreCase("ralf6")) {
                sendTaxScreen(pc, obj.getId(), "ralf6", 6);
            } else if (s.equalsIgnoreCase("defman3")) {
                sendTaxScreen(pc, obj.getId(), "defman3", 6);
            } else if (s.equalsIgnoreCase("fega3")) {
                sendTaxScreen(pc, obj.getId(), "fega3", 6);
            }

			if (s.equalsIgnoreCase("mayer3")) {
				htmlid = "mayer3";
			}
			if (s.equalsIgnoreCase("buy")) {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				if (pc.getInventory().getWeight100() > 90 && !pc.is_top_ranker()) {
					SC_ServerMessage.send(pc, 3561);
					return;
				}
				if(npcid == MJDogFightSettings.CASTER_NPC_ID){
					MJDogFightHistory.getInstance().on_show_ticket_view(pc, (L1NpcInstance)obj);
					return;
				}
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (isNpcSellOnly(npc)) {
					return;
				}
				/** 프리미엄 상점목록 상점 추가 [3] **/
				if (npcid == 5 || npcid == 200000 || npcid == 200060 || npcid == 200061 || npcid == 200062 || npcid == 200063
						|| npcid == 5000000 || npcid == 5000006 || npcid == 900047 || npcid == 5072 || npcid == 5073 || npcid == 523
						|| npcid == 7000077 || npcid == 900107 || npcid == 7200002 || npcid == 6000002 || npcid == 7310101 || npcid == 8500140
						/*|| npcid == 7310103*/ || npcid == 7310113 || npcid == 91245 || npcid == 7310119 || npcid == 7310120
						|| npcid == 7310126 || npcid == 7310124 || npcid == 7310128 || npcid == 70017 || npcid == 200005 || npcid == 7320250
						|| npcid == 7320222 || npcid == 7320223 || npcid == 7320055 || npcid == 460000361 || npcid == 460000360
						|| npcid == 7320124 || npcid == 519 || npcid == 520 || npcid == 521 || npcid == 224 || npcid == 2999 || npcid == 2998) {
					SC_PremiumShopSellList.send(pc, npcid, objid);
					return;
				} else if (npcid == 81005 ||npcid == 81008 || npcid == 200004 || npcid == 900171 || npcid == 70010 || npcid == 81006
						|| npcid == 5000009 || npcid == 5000007 || npcid == 5000008 || npcid == 5000010 || npcid == 779199
						|| npcid >= 5000001 && npcid <= 5000004 || npcid >= 5000011 && npcid <= 5000018 
						|| npcid == 900173 || npcid == 7310103 || npcid == 7310200 || npcid == 7310201|| npcid == 7310202 || npcid == 7310104 || npcid == 7310199 || npcid == 7310102 || npcid == 7310125) {
					LIN.SC_PERSONAL_SHOP_ITEM_LIST_NOTI.do_send_for_npc(pc, objid, LIN.SC_PERSONAL_SHOP_ITEM_LIST_NOTI.ePersonalShopType.TRADE_BUY);
					return;
				}
				if (npcid == 7320121 || npcid == 7320085) {
					SC_ClassShop.send(pc, objid);
					return;
				}
				if (npcid == 2020561) {// 훈련 교관 레벨 제한
					SC_ClassShop.send(pc, objid);
					return;
				}
				if (npcid == 415333) {// 방어구 상점
					SC_ClassShop.send(pc, objid);
					return;				
				}
				SC_ShopSellList.send(objid, pc);
			} else if (s.equalsIgnoreCase("pssellps") || s.equalsIgnoreCase("sell")) {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				if (npcid == 70523 || npcid == 70805) {
					htmlid = "ladar2";
				} else if (npcid == 70537 || npcid == 70807) {
					htmlid = "farlin2";
				} else if (npcid == 70525 || npcid == 70804) {
					htmlid = "lien2";
				} else if (npcid == 50527 || npcid == 50505 || npcid == 50519 || npcid == 50545 || npcid == 50531
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
						|| npcid == 50615 || npcid == 50626 || npcid == 50627 || npcid == 50628 || npcid == 50629
						|| npcid == 50630 || npcid == 50631) {
					String sellHouseMessage = sellHouse(pc, objid, npcid);
					if (sellHouseMessage != null) {
						htmlid = sellHouseMessage;
					}
				} else {
					if(npcid == MJDogFightSettings.CASTER_NPC_ID){
						MJDogFightHistory.getInstance().on_show_sells_view(pc, (L1NpcInstance) obj);
						return;
					}
					
					if (npcid == 70041 || npcid == 70042 || npcid == 70035) {
						if (obj instanceof L1NpcInstance) {
							L1NpcInstance npc = (L1NpcInstance) obj;
							L1Shop shop = ShopTable.getInstance().get(npc.getNpcId());
							if (shop == null) {
								SC_NoSell.send(pc, npc);
								return;
							}
							List<L1AssessedItem> assessedItems = shop.assessItems(pc.getInventory());
							if (assessedItems.isEmpty()) {
								SC_NoSell.send(pc, npc);
								return;
							} else {
								SC_ShopBuyList.send(objid, pc);
							}
						}
						return;
					}
					SC_ShopBuyList.send(objid, pc);
				}
			} else if (s.equalsIgnoreCase("history")) {
				if (pc.getClanid() > 0)
					SC_ClanWareHouseHistory.sendAsync(pc);
				else
					SC_SystemMessage.send(pc, "혈맹 가입후 이용해 주세요.");
			} else if (s.equalsIgnoreCase("retrieve")) {
				
				if (pc.getLevel() >= 5) {
					LIN.SC_WAREHOUSE_ITEM_LIST_NOTI.send_user_warehouse_items(pc, objid);
				}
			} else if (s.equalsIgnoreCase("retrieve-elven")) {
				if (pc.getInventory().getSize() > 180){
					pc.sendPackets("\\f2인벤토리에 아이템이 180개 이하일때만 이용가능 합니다.   현재:(\\aG"+pc.getInventory().getSize()+"/\\aA150)\\f2 소지중입니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "인벤토리에 아이템이 180개 이하일때만 이용가능 합니다. 현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					return;
				}
				if (pc.getLevel() >= 5 && pc.isElf()) {
					if (SC_RetrieveList.sendElf(pc, objid))
                        htmlid = "noitemret";
				}
			} else if (s.equalsIgnoreCase("retrieve-aib")) {
				if (SC_RetrieveList.sendPackage(pc, objid))
                    htmlid = "noitemret";
			} else if (s.equalsIgnoreCase("retrieve-char")) { // 특수창고
				if (pc.getInventory().getSize() > 180){
					pc.sendPackets("\\f2인벤토리에 아이템이 180개 이하일때만 이용가능 합니다.   현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "인벤토리에 아이템이 180개 이하일때만 이용가능 합니다. 현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					return;
				}
				if (pc.get_SpecialSize() == 0) {
					SC_ServerMessage.send(pc, 1623);
					return;
				}
			} else if (s.equalsIgnoreCase("retrieve-pledge")) {
				if (pc.getInventory().getSize() > 150){
					pc.sendPackets("\\f2인벤토리에 아이템이 180개 이하일때만 이용가능 합니다.   현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "인벤토리에 아이템이 180개 이하일때만 이용가능 합니다. 현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					return;
				}
				if (pc.getLevel() >= 5) {
					if (pc.getClanid() == 0) {
						SC_SystemMessage.send(pc, "혈맹창고를 사용하려면 혈맹이 있어야 합니다.");
						return;
					}
					if (pc.getClanRank() == L1Clan.수련) {
						SC_ServerMessage.send(pc, 728);
						return;
					}
					LIN.SC_WAREHOUSE_ITEM_LIST_NOTI.send_user_pledge_warehouse_items(pc, objid);
					return;
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200007) {//오류
				if (s.equalsIgnoreCase("1")) {
					htmlid = "camus5";
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getInventory().consumeItem(L1ItemId.ADENA, 10000)) {
						int[] prismIds = { 210106, 210107, 210108, 210109 };
						int idx = _random.nextInt(prismIds.length);
						pc.getInventory().storeItem(prismIds[idx], 1);
						L1ItemInstance item = ItemTable.getInstance().createItem(prismIds[idx]);
						item.setCount(1);
						SC_ServerMessage.send(pc, 403, item.getLogName());
						htmlid = "camus2";
					} else {
						htmlid = "camus3";
					}
				}
				// 결혼반지 충전
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70702) {
				if (s.equalsIgnoreCase("chg")) {
					L1ItemInstance ring = null;
					if (!pc.getInventory().consumeItem(L1ItemId.ADENA, 1000)) {
						SC_SystemMessage.send(pc, "아데나가 부족합니다.");
						return;
					}
					if (pc.getPartnerId() != 0) {
						ring = pc.getInventory().findItemId(40906);

						if (ring == null) {
							ring = pc.getInventory().findItemId(40905);
						}
						if (ring == null) {
							ring = pc.getInventory().findItemId(40904);
						}
						if (ring == null) {
							ring = pc.getInventory().findItemId(40903);
						}

						if (ring != null) {
							ring.setChargeCount(ring.getItem().getMaxChargeCount());
							pc.getInventory().updateItem(ring, L1PcInventory.COL_CHARGE_COUNT);

							SC_SystemMessage.send(pc, "결혼 반지가 충전되었습니다.");
						}
					}
					SC_SystemMessage.send(pc, "기부해주셔서 감사합니다.");
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900106) {// 달나라
				if (s.equalsIgnoreCase("0")) {
					if (pc.getLevel() > 30) {
						if (!pc.getInventory().checkItem(22253, 1)) {
							if (pc.getInventory().checkItem(40308, 5000)) {
								pc.getInventory().consumeItem(40308, 5000);
								pc.getInventory().storeItem(22253, 1);
								htmlid = "rabbita5";
							} else {
								htmlid = "rabbita4";
							}
						} else {
							htmlid = "rabbita3";
						}
					} else {
						htmlid = "rabbita2";
					}
				}

				// 호슈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 1000004) {
				if (s.equalsIgnoreCase("1")) { // 샌드웜 영역 이동
					if (pc.getInventory().checkItem(40308, 10000)) {
						pc.getInventory().consumeItem(40308, 10000);
						// L1Teleport.teleport(pc, 32789, 33186, (short) 4, 5,
						// true);
						pc.start_teleport(32789, 33186, 4, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "hyosue1";
					}
				}

			} else if (obj instanceof L1HousekeeperInstance && s.equalsIgnoreCase("g")) {
				if (s.equalsIgnoreCase("g")) {
					L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
					int houseId = clan.getHouseId();
					if (houseId != 0) {
						L1House house = HouseTable.getInstance().getHouseTable(houseId);
						int keeperId = house.getKeeperId();
						int rank = pc.getClanRank();
						if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == keeperId && house.isPurchaseBasement()) {
							if (clan.getUnderDungeon() == 0) {
								if (clan.getBlessCount() >= 99000000
										&& (rank == 3 || rank == 4 || rank == 6 || rank == 9 || rank == 10)) {
									pc.setTempID(pc.getId());
									// SC_MessageYN.send(pc, 4703, "");//
									// 통로를 열기 위해 "축복의 기운" 9,900이 필요합니다. 사용
									// 하시겠습니까?//주석
									SC_SystemMessage.send(pc, "리뉴얼 공사로인해 이용 불가능합니다.");// 추가
									return;// 추가
								} else {
									htmlid = "agit2";
								}
							} else if (clan.getUnderDungeon() == 1) {
								if (clan.getUnderMapid() != 0) {
									// pc.start_teleport(32922, 32860,
									// clan.getUnderMapid(), 5, 169, true, false);
									SC_SystemMessage.send(pc, "리뉴얼 공사로인해 이용 불가능합니다.");// 추가
									return;// 추가
								}
							} else {
								SC_SystemMessage.send(pc, "지하통로 를 클리어 하였습니다. 내일 이용하세요");
							}
						} else {
							htmlid = "agit2";
						}
					}
				}
			} else if (s.equalsIgnoreCase("get")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70099
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70796) {
					L1ItemInstance item = pc.getInventory().storeItem(20081, 1);
					String npcName = npc.getNpcTemplate().get_name();
					String itemName = item.getItem().getName();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					pc.getQuest().set_end(L1Quest.QUEST_OILSKINMANT);
					htmlid = "";
				} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70528
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70546
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70567
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70594
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70654
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70748
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70774
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70799
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70815
						|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70860) {

					if (pc.getHomeTownId() > 0) {

					} else {

					}
				}
			} else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 8500147 && s.equalsIgnoreCase("B")){
				L1CompanionInstance companion = pc.get_companion();
				if(companion == null){
					pc.sendPackets("펫을 소환한 후 시도해주세요.");
					return;
				}
				companion.do_traning(false);
				return;
			} else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 8500150){
				L1CompanionInstance companion = pc.get_companion();
				if(companion == null){
					pc.sendPackets("펫을 소환한 후 시도해주세요.");
					return;
				}
				if(s.equalsIgnoreCase("a")){
					companion.do_restore_keep_exp(false);
					return;
				}else if(s.equalsIgnoreCase("b")){
					companion.do_oblivion(false);
				}
			} else if (s.equalsIgnoreCase("fix")) {

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70012
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70019
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70031
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70054
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70065
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70070
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70075
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70084
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70096) {
				SC_SkillSound.send(pc, pc.getId(), 612);
				if (s.equalsIgnoreCase("room")) {
					if (pc.getInventory().checkItem(40312))
						
						htmlid = "inn5";
					else {
						L1ItemInstance item = pc.getInventory().findItemId(40308);
						if(item != null && item.getCount() >= 300){
							materials = (new int[] { 40308 });
							counts = (new int[] { 300 });
							createitem = (new int[] { 40312 });
							createcount = (new int[] { 1 });
							htmlid = "inn4";						
						}else
							htmlid = "inn3";
					}
				} else if (s.equalsIgnoreCase("room")) {
					if (pc.getInventory().checkItem(40312))
						htmlid = "inn5";
					else {
						L1ItemInstance item = pc.getInventory().findItemId(40308);
						if(item != null && item.getCount() >= 600){
							materials = (new int[] { 40308 });
							counts = (new int[] { 600 });
							createitem = (new int[] { 40312 });
							createcount = (new int[] { 1 });
							htmlid = "inn4";						
						}else
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
						// htmlid = "inn7";
						SC_SystemMessage.send(pc, "대여한 방 또는 홀이 없습니다.");
					}
				} else if (s.equalsIgnoreCase("enter")) {
					int nowX = pc.getX();
					int nowY = pc.getY();
					short map = pc.getMapId();
					if (pc.getInventory().checkItem(40312)) {
						if (map == 0)
							pc.start_teleport(32746, 32803, 16384, 5, 169, false, false);
						else if (map > 0)
							if (nowX < 32641 && nowX > 32621 && nowY < 32760 && nowY > 32740)
								pc.start_teleport(32744, 32803, 17408, 5, 169, false, false);
							else if (nowX < 32638 && nowX > 32618 && nowY < 33177 && nowY > 33157)
								pc.start_teleport(32745, 32803, 20480, 5, 169, false, false);
							else if (nowX < 33995 && nowX > 33975 && nowY < 33322 && nowY > 33302)
								pc.start_teleport(32745, 32803, 24576, 5, 169, false, false);
							else if (nowX < 33447 && nowX > 33427 && nowY < 32799 && nowY > 32779)
								pc.start_teleport(32740, 32803, 18432, 5, 169, false, false);
							else if (nowX < 33615 && nowX > 33595 && nowY < 33285 && nowY > 33265)
								pc.start_teleport(32745, 32803, 22528, 5, 169, false, false);
							else if (nowX < 33126 && nowX > 33106 && nowY < 33389 && nowY > 33369)
								pc.start_teleport(32745, 32803, 21504, 5, 169, false, false);
							else if (nowX < 34078 && nowX > 34058 && nowY < 32264 && nowY > 32244)
								pc.start_teleport(32745, 32803, 19456, 5, 169, false, false);
					} else if (pc.getInventory().checkItem(40312)) {
						if (map == 0)
							pc.start_teleport(32744, 32808, 16896, 5, 169, false, false);
						else if (map > 0)
							if (nowX < 32641 && nowX > 32621 && nowY < 32760 && nowY > 32740)
								pc.start_teleport(32745, 32807, 18944, 5, 169, false, false);
							else if (nowX < 32638 && nowX > 32618 && nowY < 33177 && nowY > 33157)
								pc.start_teleport(32745, 32807, 19968, 5, 169, false, false);
							else if (nowX < 33995 && nowX > 33975 && nowY < 33322 && nowY > 33302)
								pc.start_teleport(32745, 32807, 20992, 5, 169, false, false);
							else if (nowX < 33447 && nowX > 33427 && nowY < 32799 && nowY > 32779)
								pc.start_teleport(32745, 32807, 22016, 5, 169, false, false);
							else if (nowX < 33615 && nowX > 33595 && nowY < 33285 && nowY > 33265)
								pc.start_teleport(32745, 32807, 23040, 5, 169, false, false);
							else if (nowX < 33126 && nowX > 33106 && nowY < 33389 && nowY > 33369)
								pc.start_teleport(32745, 32807, 24064, 5, 169, false, false);
							else if (nowX < 34078 && nowX > 34058 && nowY < 32264 && nowY > 32244)
								pc.start_teleport(32745, 32807, 25088, 5, 169, false, false);
					} else {
						SC_SystemMessage.send(pc, "대여한 방 또는 홀이 없습니다.");
					}
				}
			} else if (s.equalsIgnoreCase("hall") && obj instanceof L1MerchantInstance) {

			} else if (s.equalsIgnoreCase("return")) {

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900136) {// 말섬 인던 유키
				if (s.equalsIgnoreCase("enter")) {
					if (pc.isInParty()) {
						if (pc.getParty().isLeader(pc)) {
							if (pc.getParty().getNumOfMembers() >= 1) {
								boolean ck = true;
								for (L1PcInstance Ppc : pc.getParty().getMembers()) {
									if (pc.getMapId() != Ppc.getMapId()) {
										SC_SystemMessage.send(pc, "파티원이 다 모이지 않았습니다.");
										ck = false;
										break;
									}
								}
								if (ck) {
									if (pc.getMapId() == 9100)
										htmlid = "";
									else
										HadinSystem.getInstance().startHadin(pc);
									L1PolyMorph.undoPoly(pc);
									L1World.getInstance().broadcastServerMessage("\\aD" + pc.getName() + " 님이 동료들과 함께 과거로 여행을 떠났습니다.");
								}
								htmlid = "";
							} else if (pc.getMapId() == 9100) {
								htmlid = "id1_1";
							} else {
								htmlid = "id0_1";
							}
						} else if (pc.getMapId() == 9100)
							htmlid = "id1_2";
						else {
							htmlid = "id0_2";
						}
					} else if (pc.getMapId() == 9100)
						htmlid = "id1_2";
					else {
						htmlid = "id0_2";
					}
				}
			} else if (s.equalsIgnoreCase("openigate")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				openCloseGate(pc, npc.getNpcTemplate().get_npcId(), true);
				htmlid = "";
			} else if (s.equalsIgnoreCase("closeigate")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				openCloseGate(pc, npc.getNpcTemplate().get_npcId(), false);
				htmlid = "";
			} else if (s.equalsIgnoreCase("askwartime")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (npc.getNpcTemplate().get_npcId() == 60514) {
					htmldata = makeWarTimeStrings(L1CastleLocation.KENT_CASTLE_ID);
					htmlid = "ktguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 60560) {
					htmldata = makeWarTimeStrings(L1CastleLocation.OT_CASTLE_ID);
					htmlid = "orcguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 60552 || npc.getNpcTemplate().get_npcId() == 5155) {
					htmldata = makeWarTimeStrings(L1CastleLocation.WW_CASTLE_ID);
					htmlid = "wdguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 60524 || npc.getNpcTemplate().get_npcId() == 60525
						|| npc.getNpcTemplate().get_npcId() == 60529 || npc.getNpcTemplate().get_npcId() == 7320232) {
					htmldata = makeWarTimeStrings(L1CastleLocation.GIRAN_CASTLE_ID);
					htmlid = "grguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 70857) {
					htmldata = makeWarTimeStrings(L1CastleLocation.HEINE_CASTLE_ID);
					htmlid = "heguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 60530 || npc.getNpcTemplate().get_npcId() == 60531) {
					htmldata = makeWarTimeStrings(L1CastleLocation.DOWA_CASTLE_ID);
					htmlid = "dcguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 60533 || npc.getNpcTemplate().get_npcId() == 60534) {
					htmldata = makeWarTimeStrings(L1CastleLocation.ADEN_CASTLE_ID);
					htmlid = "adguard7";
				} else if (npc.getNpcTemplate().get_npcId() == 81156) {
					htmldata = makeWarTimeStrings(L1CastleLocation.DIAD_CASTLE_ID);
					htmlid = "dfguard3";
				}
			} else if (s.equalsIgnoreCase("inex")) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					int castle_id = clan.getCastleId();
					if (castle_id != 0) {
						MJCastleWar war = MJCastleWarBusiness.getInstance().get(castle_id);
						SC_ServerMessage.send(pc, 309, war.getCastleName(), String.valueOf(war.getPublicMoney()));
						htmlid = "";
					}
				}
			} else if (s.equalsIgnoreCase("tax")) {
				SC_TaxRate.send(pc, pc.getId(), MJCastleWarBusiness.getInstance().getTaxRate(pc.getClan().getCastleId()));
			} else if (s.equalsIgnoreCase("withdrawal")) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					int castle_id = clan.getCastleId();
					if (castle_id != 0) {
						SC_Drawal.send(pc, pc.getId(), MJCastleWarBusiness.getInstance().getPublicMoney(castle_id));
					}
				}
			} else if (s.equalsIgnoreCase("cdeposit")) {
				SC_Deposit.send(pc, pc.getId());
			} else if (s.equalsIgnoreCase("employ")) {
				if ((pc.getClanRank() != L1Clan.군주) && (pc.getClanRank() != L1Clan.수호) && (pc.getClanRank() != L1Clan.부군주)
						&& (pc.getClanRank() != L1Clan.CLAN_RANK_LEAGUE_GUARDIAN)) {
					SC_SystemMessage.send(pc, "용병 구입은 수호기사 이상만 가능합니다.");
					return;
				}
				if (pc.getPetList().size() > 0) {
					SC_SystemMessage.send(pc, "이미 무언가를 끌고다니시면 이용 불가합니다.");
					return;
				}
				if (!pc.getInventory().consumeItem(40308, 300000)) {
					SC_SystemMessage.send(pc, "용병 구입은 아데나 30만 입니다.");
					return;
				}
				boolean isNowWar = false;
				int castleId = L1CastleLocation.getCastleIdByArea((L1NpcInstance) obj);
				if (castleId > 0) {
					isNowWar = MJCastleWarBusiness.getInstance().isNowWar(castleId);
				}
				if (isNowWar) {
					SC_SystemMessage.send(pc, "해당성의 공성전이 진행중에는 이용 불가능합니다.");
					return;
				}
				L1Npc npcTemp = NpcTable.getInstance().getTemplate(7320000);
				L1SummonInstance summon = new L1SummonInstance(npcTemp, pc);
				summon.setPetcost(6);
				SC_CloseList.send(pc, pc.getId());
				SC_ChatPacket.sendDirect(pc, pc, "용병이 고용 되었습니다. 1시간 동안 이용가능합니다", 1);
			} else if (s.equalsIgnoreCase("arrange")) {
				SC_SystemMessage.send(pc, "현재 리뉴얼 중입니다. 조만간 이용해보실수 있습니다.");
			} else if (s.equalsIgnoreCase("castlegate")) {
				repairGate(pc);
				htmlid = "";
			} else if (s.equalsIgnoreCase("encw")) {
				if (pc.getWeapon() == null) {
					SC_ServerMessage.send(pc, 79);
				} else {
					
					if (pc.getInventory().checkItem(40308, 100)) {
						for (L1ItemInstance item : pc.getInventory().getItems()) {
							if (pc.getWeapon().equals(item)) {
								
								L1SkillUse.cast(pc, L1SkillId.ENCHANT_WEAPON, item.getId(), 0, 0, null, 0,
										L1SkillUse.TYPE_SPELLSC);
								
								pc.getInventory().consumeItem(40308, 100);
								break;
							}
						}
					} else {
						SC_SystemMessage.send(pc, "아데나가 부족합니다.");
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("demand")) {
				if ((pc.getClanRank() != L1Clan.군주) && (pc.getClanRank() != L1Clan.수호) && (pc.getClanRank() != L1Clan.부군주)
						&& (pc.getClanRank() != L1Clan.CLAN_RANK_LEAGUE_GUARDIAN)) {
					SC_SystemMessage.send(pc, "용병 구입은 수호기사 이상만 가능합니다.");
					return;
				}
				if (pc.getPetList().size() > 0) {
					SC_SystemMessage.send(pc, "이미 무언가를 끌고다니시면 이용 불가합니다.");
					return;
				}
				if (!pc.getInventory().consumeItem(40308, 300000)) {
					SC_SystemMessage.send(pc, "용병 구입은 아데나 30만 입니다.");
					return;
				}
				boolean isNowWar = false;
				int castleId = L1CastleLocation.getCastleIdByArea((L1NpcInstance) obj);
				if (castleId > 0) {
					isNowWar = MJCastleWarBusiness.getInstance().isNowWar(castleId);
				}
				if (isNowWar) {
					SC_SystemMessage.send(pc, "해당성의 공성전이 진행중에는 이용 불가능합니다.");
					return;
				}
				L1Npc npcTemp = NpcTable.getInstance().getTemplate(7320031);
				L1SummonInstance summon = new L1SummonInstance(npcTemp, pc);
				summon.setPetcost(6);
				SC_CloseList.send(pc, pc.getId());
				SC_ChatPacket.sendDirect(pc, pc, "켄트성 용병이 고용 되었습니다. 1시간 동안 이용가능합니다", 1);
			} else if (s.equalsIgnoreCase("enca")) {
				L1ItemInstance item = pc.getInventory().getItemEquipped(2, 2);
				if (pc.getInventory().checkItem(40308, 100)) {
					if (item != null) {
						
						L1SkillUse.cast(pc, L1SkillId.BLESSED_ARMOR, item.getId(), 0, 0, null, 0,
								L1SkillUse.TYPE_SPELLSC);
						
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 79);
					}
				} else {
					SC_SystemMessage.send(pc, "아데나가 부족합니다.");
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("depositnpc")) {
				Object[] petList = pc.getPetList().values().toArray();
				L1PetInstance pet = null;
				for (Object petObject : petList) {
					if (petObject instanceof L1PetInstance) {
						pet = (L1PetInstance) petObject;
						if (pet.getArmor() != null) {
							pet.removePetArmor(pet.getArmor());
						}
						if (pet.getWeapon() != null) {
							pet.removePetWeapon(pet.getWeapon());
						}
						/** ui6 관련 펫파티,컨트롤 **/
						l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, pet, false);
						
						pet.collect();
						pet.deleteMe();
						pc.getPetList().remove(Integer.valueOf(pet.getId()));
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("dismissnpc")) {
				SC_SystemMessage.send(pc, "당분간 양도는 불가능 합니다.");
			} else if (s.equalsIgnoreCase("withdrawnpc")) {
				List<L1ItemInstance> amuletList = new ArrayList<L1ItemInstance>();
				L1ItemInstance item = null;
				for (Object itemObject : pc.getInventory().getItems()) {
					item = (L1ItemInstance) itemObject;
					if (item.getItem().getItemId() == 40314 || item.getItem().getItemId() == 40316) {
						amuletList.add(item);
					}
				}
				if (amuletList.size() <= 0) {
					SC_SystemMessage.send(pc, "보유중인 펫이 없습니다.");
				} else {
					SC_PetList.send(pc, objid);
				}
				
			} else if (s.equalsIgnoreCase("changename")) {
				pc.setTempID(objid);
				SC_MessageYN.send(pc, 325, "");
			} else if (s.equalsIgnoreCase("attackchr")) {
				if (pc.getZoneType() != 1) {
					if (obj instanceof L1Character) {
						L1Character cha = (L1Character) obj;
						SC_SelectTarget.send(pc, cha.getId());
					}
				} else {
					if (obj instanceof L1Character) {
						L1Character cha = (L1Character) obj;
						SC_SelectTarget.send(pc, cha.getId());
					}
				}
			} else if (s.equalsIgnoreCase("select")) {
				SC_AuctionBoardRead.sendAsync(pc, objid, s2);
			} else if (s.equalsIgnoreCase("map")) {
				SC_HouseMap.send(pc, objid, s2);
			} else if (s.equalsIgnoreCase("apply")) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					if (pc.isCrown() && pc.getId() == clan.getLeaderId()) {
						if (pc.getLevel() >= 15) {
							if (clan.getHouseId() == 0) {
								SC_ApplyAuction.send(pc, objid, s2);
							} else {
								SC_ServerMessage.send(pc, 521);
								htmlid = "";
							}
						} else {
							SC_ServerMessage.send(pc, 519);
							htmlid = "";
						}
					} else {
						SC_ServerMessage.send(pc, 518);
						htmlid = "";
					}
				} else {
					SC_ServerMessage.send(pc, 518);
					htmlid = "";
				}
			} else if (s.equalsIgnoreCase("open") || s.equalsIgnoreCase("close")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				openCloseDoor(pc, npc, s);
				htmlid = "";
			} else if (s.equalsIgnoreCase("expel")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				expelOtherClan(pc, npc.getNpcTemplate().get_npcId());
				htmlid = "";
			} else if (s.equalsIgnoreCase("pay")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				htmldata = makeHouseTaxStrings(pc, npc);
				htmlid = "agpay";
			} else if (s.equalsIgnoreCase("payfee")) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				payFee(pc, npc);
				htmlid = "";
			} else if (s.equalsIgnoreCase("name")) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					int houseId = clan.getHouseId();
					if (houseId != 0 && pc.isCrown() && clan.getLeaderId() == pc.getId()) {
						L1House house = HouseTable.getInstance().getHouseTable(houseId);
						if (house != null && !house.isOnSale() && obj instanceof L1NpcInstance npc
								&& npc.getNpcTemplate().get_npcId() == house.getKeeperId()) {
							pc.setTempID(houseId);
							SC_MessageYN.send(pc, 512, "");
						}
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("rem")) {
			} else if (s.equalsIgnoreCase("tel0") || s.equalsIgnoreCase("tel1") || s.equalsIgnoreCase("tel2")
					|| s.equalsIgnoreCase("tel3")) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
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
							pc.start_teleport(loc[0], loc[1], loc[2], 5, 169, true, false);
						}
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("upgrade")) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					int houseId = clan.getHouseId();
					if (houseId != 0) {
						L1House house = HouseTable.getInstance().getHouseTable(houseId);
						int keeperId = house.getKeeperId();
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcTemplate().get_npcId() == keeperId) {
							if (pc.isCrown() && pc.getId() == clan.getLeaderId()) {
								if (house.isPurchaseBasement()) {
									SC_ServerMessage.send(pc, 1135);
								} else {
									if (l1j.server.server.model.L1AuctionTransaction.upgradeBasement(pc, house)) {
										SC_ServerMessage.send(pc, 1099);
									} else {
										SC_SystemMessage.send(pc, "지하실 확장을 완료하지 못했습니다. 아데나와 소유권을 확인해 주세요.");
									}
								}
							} else {
								SC_ServerMessage.send(pc, 518);
							}
						}
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("hall") && obj instanceof L1HousekeeperInstance) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
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
								pc.start_teleport(loc[0], loc[1], loc[2], 5, 169, true, false);
							} else {
								SC_ServerMessage.send(pc, 1098);
							}
						}
					}
				}
				htmlid = "";
			}
			
			else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 9291) {
				if (s.equalsIgnoreCase("fire")) {
					if (pc.isElf()) {
						if (pc.getElfAttr() != 0) {
							if (pc.getElfAttr() == 4) {
								SC_SystemMessage.send(pc, "물 정령의 속성을 먼저 제거하여주세요.");
								htmlid = "";
							} else if (pc.getElfAttr() == 8) {
								SC_SystemMessage.send(pc, "바람 정령의 속성을 먼저 제거하여주세요.");
								htmlid = "";
							} else if (pc.getElfAttr() == 1) {
								SC_SystemMessage.send(pc, "땅 정령의 속성을 먼저 제거하여주세요.");
								htmlid = "";
							} else if (pc.getElfAttr() == 2) {
								SC_SystemMessage.send(pc, "동일 속성의 정령력은 선택할 수 없습니다.");
								htmlid = "";
							}
							return;
						}
						pc.setElfAttr(2);
						SC_SkillIconGFX.send(pc, 15, 1);
						SC_SkillSound.send(pc, pc.getId(), 1822);
						htmlid = "";
					} else {
						SC_SystemMessage.send(pc, "해당 메뉴를 사용할 수 없는 클래스입니다.");
						htmlid = "";
					}

			/*else if (s.equalsIgnoreCase("fire")) {
				if (pc.isElf()) {
					if (pc.getElfAttr() != 0) {
						if (pc.getElfAttr() == 4) {
							SC_SystemMessage.send(pc, "물 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 8) {
							SC_SystemMessage.send(pc, "바람 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 1) {
							SC_SystemMessage.send(pc, "땅 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 2) {
							SC_SystemMessage.send(pc, "동일 속성의 정령력은 선택할 수 없습니다.");
							htmlid = "";
						}
						return;
					}
					pc.setElfAttr(2);
					SC_SkillIconGFX.send(pc, 15, 1);
					pc.sendPackets(new S_SkillSound(pc.getId(), 1822));
					htmlid = "";
				} else {
					SC_SystemMessage.send(pc, "해당 메뉴를 사용할 수 없는 클래스입니다.");
					htmlid = "";
				}*/
			} else if (s.equalsIgnoreCase("water")) {
				if (pc.isElf()) {
					if (pc.getElfAttr() != 0) {
						if (pc.getElfAttr() == 2) {
							SC_SystemMessage.send(pc, "불 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 8) {
							SC_SystemMessage.send(pc, "바람 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 1) {
							SC_SystemMessage.send(pc, "땅 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 4) {
							SC_SystemMessage.send(pc, "동일 속성의 정령력은 선택할 수 없습니다.");
							htmlid = "";
						}
						return;
					}
					pc.setElfAttr(4);
					SC_SkillIconGFX.send(pc, 15, 2);
					SC_SkillSound.send(pc, pc.getId(), 1823);
					htmlid = "";
				} else {
					SC_SystemMessage.send(pc, "해당 메뉴를 사용할 수 없는 클래스입니다.");
					htmlid = "";
				}
			} else if (s.equalsIgnoreCase("air")) {
				if (pc.isElf()) {
					if (pc.getElfAttr() != 0) {
						if (pc.getElfAttr() == 2) {
							SC_SystemMessage.send(pc, "불 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 4) {
							SC_SystemMessage.send(pc, "물 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 1) {
							SC_SystemMessage.send(pc, "땅 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 8) {
							SC_SystemMessage.send(pc, "동일 속성의 정령력은 선택할 수 없습니다.");
							htmlid = "";
						}
						return;
					}
					pc.setElfAttr(8);
					SC_SkillIconGFX.send(pc, 15, 3);
					SC_SkillSound.send(pc, pc.getId(), 1825);
					htmlid = "";
				} else {
					SC_SystemMessage.send(pc, "해당 메뉴를 사용할 수 없는 클래스입니다.");
					htmlid = "";
				}
			} else if (s.equalsIgnoreCase("earth")) {
				if (pc.isElf()) {
					if (pc.getElfAttr() != 0) {
						if (pc.getElfAttr() == 2) {
							SC_SystemMessage.send(pc, "불 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 4) {
							SC_SystemMessage.send(pc, "물 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 8) {
							SC_SystemMessage.send(pc, "바람 정령의 속성을 먼저 제거하여주세요.");
							htmlid = "";
						} else if (pc.getElfAttr() == 1) {
							SC_SystemMessage.send(pc, "동일 속성의 정령력은 선택할 수 없습니다.");
							htmlid = "";
						}
						return;
					}
					pc.setElfAttr(1);
					SC_SkillIconGFX.send(pc, 15, 4);
					SC_SkillSound.send(pc, pc.getId(), 1824);
					htmlid = "";
				} else {
					SC_SystemMessage.send(pc, "해당 메뉴를 사용할 수 없는 클래스입니다.");
					htmlid = "";
				}
			} else if (s.equalsIgnoreCase("init")) {
				if (pc.isElf()) {
					if (pc.getElfAttr() == 0) {
						SC_SystemMessage.send(pc, "부여받은 정령의 기운이 없습니다.");
						htmlid = "";
						return;
					}
					htmlid = "ellyonne11";
				} else {
					htmlid = "siabeth6";
				}
			} else if (s.equalsIgnoreCase("count")) {
				htmlid = "ellyonne12";
				int aden_count = 500000;
				if (pc.getElfAttrInitCount() > 1) {
					aden_count = 500000 * pc.getElfAttrInitCount();
				}
				DecimalFormat df = new DecimalFormat("#,##0");
				htmldata = (new String[] { String.valueOf(df.format(aden_count)) });
				
			} else if (s.equalsIgnoreCase("money")) {
				if (pc.isElf()) {
					int aden_count = 500000;
					if (pc.getElfAttrInitCount() > 1) {
						aden_count = 500000 * pc.getElfAttrInitCount();
					}
					if (pc.getInventory().consumeItem(40308, aden_count)) {
						if (pc.hasSkillEffect(L1SkillId.ELEMENTAL_PROTECTION)) {
							pc.removeSkillEffect(L1SkillId.ELEMENTAL_PROTECTION);
						}
						pc.setElfAttr(0);
						pc.addElfAttrInitCount(1);
						SC_SkillIconGFX.send(pc, 15, 0);
						htmlid = "";
					} else {
						SC_ServerMessage.send(pc, 189);
						htmlid = "ellyonne13";
					}
				} else {
					htmlid = "siabeth6";
				}
			} else if (s.equalsIgnoreCase("bm")) { // 회상의 촛불 2개로 정령력 제거
				if (pc.isElf()){
					if(pc.getInventory().consumeItem(200000, 2)){
						if (pc.hasSkillEffect(L1SkillId.ELEMENTAL_PROTECTION)) {
							pc.removeSkillEffect(L1SkillId.ELEMENTAL_PROTECTION);
						}
						pc.setElfAttr(0);
						SC_SkillIconGFX.send(pc, 15, 0);
						htmlid = "";
					} else {
						SC_ServerMessage.send(pc, 4540);
						htmlid = "ellyonne13";
					}
				} else {
					htmlid = "siabeth6";
				}
			}
			} else if (s.equalsIgnoreCase("exp")) {// 신녀 아가타
				if (pc.getExpRes() == 1) {
					int cost = 0;
					int level = pc.getLevel();
					int lawful = pc.getLawful();
					if (level < 45) {
						cost = level * level * 50;
					} else {
						cost = level * level * 150;
					}
					if (lawful >= 0) {
						cost = (int) (cost * 0.7);
					}
					SC_MessageYN.send(pc, 738, String.valueOf(cost));
				} else {
					SC_ServerMessage.send(pc, 739);
					htmlid = "";
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("material")) { // 구호의 증서
				if (pc.getInventory().checkItem(3000049, 1)) {
					if (pc.getExpRes() == 1) {
						SC_MessageYN.send(pc, 2551, "");
					} else {
						SC_ServerMessage.send(pc, 739);
						htmlid = "";
					}
				} else {
					pc.sendPackets("구호 증서가 부족 합니다.");
//					pc.sendPackets(new SC_NpcChatPacket(npc, "구호 증서가 필요합니다.", 0));
					htmlid = "";
				}
			} else if (s.equalsIgnoreCase("pk")) {
				if (pc.getLawful() < 30000) {
					SC_ServerMessage.send(pc, 559);
				} else if (pc.get_PKcount() < 5) {
					SC_ServerMessage.send(pc, 560);
				} else {
					if (pc.getInventory().consumeItem(L1ItemId.ADENA, 700000)) {
						pc.set_PKcount(pc.get_PKcount() - 5);
						SC_ServerMessage.send(pc, 561, String.valueOf(pc.get_PKcount()));
					} else {
						SC_ServerMessage.send(pc, 189);
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("ent")) {
				int npcId = ((L1NpcInstance) obj).getNpcId();
				if (npcId == 80085) {
					htmlid = enterHauntedHouse(pc);
				} else if (npcId == 80086 || npcId == 80087) {
					htmlid = enterDeathMatch(pc);
				} else if (npcId == 50037) {
					htmlid = enterUb(pc, npcId);
				} else if (npcId == 80088) {
					htmlid = enterPetMatch(pc, Integer.valueOf(s2));
				} else if (npcId == 300000) { // 펫 레이싱
					htmlid = enterPe(pc, npcId);
				} else if (npcId == 200014) {
					if (pc.getLevel() != pc.getHighLevel()) {
						SC_SystemMessage.send(pc, "레벨이 다운된 캐릭입니다. 레벨업 후 이용하세요.");
						return;
					}
					if (pc.getLevel() > 54) {
						if (pc.getInventory().checkItem(200000)) {
							pc.getInventory().consumeItem(200000, 1);
							pc.start_teleport(32723 + CommonUtil.random(-10, 10), 32815 + CommonUtil.random(-10, 10), 5166,
									5, 169, true, false);
							StatInitialize(pc);
							htmlid = "";
						} else {
							SC_ServerMessage.send(pc, 1290);
						}
					} else
						SC_SystemMessage.send(pc, "스텟초기화는 55레벨 이상만 가능합니다.");

				} else if (npcId == 50038 || npcId == 50042 || npcId == 50029 || npcId == 50019 || npcId == 50062) {
					SC_ChatPacket.sendDirect(pc, pc, "무한대전 관람모드 비활성화 상태.");
				} else {
					htmlid = enterUb(pc, npcId);
				}
			} else if (s.equalsIgnoreCase("par")) {
				htmlid = enterUb(pc, ((L1NpcInstance) obj).getNpcId());
				//pc.sendPackets("이용이 불가능 합니다.");
			} else if (s.equalsIgnoreCase("info")) {
				int npcId = ((L1NpcInstance) obj).getNpcId();
				if (npcId == 80085 || npcId == 80086 || npcId == 80087) {
				} else {
					htmlid = "colos2";
				}
			} else if (s.equalsIgnoreCase("sco")) {
				htmldata = new String[10];
				htmlid = "colos3";
					//TODO 헤이스트 사
			} else if (s.equalsIgnoreCase("haste")) {
				if (pc.hasSkillEffect(L1SkillId.HASTE))
					pc.removeSkillEffect(L1SkillId.HASTE);
				
				L1SkillUse.cast(pc, L1SkillId.HASTE, pc.getId(), pc.getX(), pc.getY(), null, 0,L1SkillUse.TYPE_GMBUFF);
				
					//TODO 변신술사
			} else if (s.equalsIgnoreCase("Src1")) {// 진데스
				if (pc.getLevel() > 82) {
					pc.sendPackets("레벨83 이상인 용사에게는 해줄수가 없소!");
					return;
				}
				if (pc.getInventory().checkItem(40308, 100)) {
					poly(client, 13152);
					pc.getInventory().consumeItem(40308, 100);
				} else {
					SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("Src2")) {// 진 쉐도
				if (pc.getLevel() > 82) {
					pc.sendPackets("레벨83 이상인 용사에게는 해줄수가 없소!");
					return;
				}
				if (pc.getInventory().checkItem(40308, 100)) {
					poly(client, 15868);
					pc.getInventory().consumeItem(40308, 100);
				} else {
					SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("Src3")) {// 진 랜스
				if (pc.getLevel() > 82) {
					pc.sendPackets("레벨83 이상인 용사에게는 해줄수가 없소!");
					return;
				}
				if (pc.getInventory().checkItem(40308, 100)) {
					poly(client, 15539);
					pc.getInventory().consumeItem(40308, 100);
				} else {
					SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("Src4")) {// 오웬
				if (pc.getLevel() > 82) {
					pc.sendPackets("레벨83 이상인 용사에게는 해줄수가 없소!");
					return;
				}
				if (pc.getInventory().checkItem(40308, 100)) {
					poly(client, 13635);
					pc.getInventory().consumeItem(40308, 100);
				} else {
					SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("Src5")) {// 진 바포
				if (pc.getLevel() > 82) {
					pc.sendPackets("레벨83 이상인 용사에게는 해줄수가 없소!");
					return;
				}
				if (pc.getInventory().checkItem(40308, 100)) {
					poly(client, 15550);
					pc.getInventory().consumeItem(40308, 100);
				} else {
					SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
				}
				htmlid = "";
		/*	} else if (s.equalsIgnoreCase("shelob nbmorph")) {
				if (pc.getLevel() < 90) {
					if (pc.getInventory().checkItem(40308, 100)) {
						poly(client, 95);
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("ghoul nbmorph")) {
				if (pc.getLevel() < 90) {
					if (pc.getInventory().checkItem(40308, 100)) {
						poly(client, 3873);
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("ghast nbmorph")) {
				if (pc.getLevel() < 90) {
					if (pc.getInventory().checkItem(40308, 100)) {
						poly(client, 3875);
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("atuba orc nbmorph")) {
				if (pc.getLevel() < 90) {
					if (pc.getInventory().checkItem(40308, 100)) {
						poly(client, 3868);
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("skeleton axeman nbmorph")) {
				if (pc.getLevel() < 90) {
					if (pc.getInventory().checkItem(40308, 100)) {
						poly(client, 2376);
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
					}
				}
				htmlid = "";
			} else if (s.equalsIgnoreCase("troll nbmorph")) {
				if (pc.getLevel() < 90) {
					if (pc.getInventory().checkItem(40308, 100)) {
						poly(client, 3878);
						pc.getInventory().consumeItem(40308, 100);
					} else {
						SC_ServerMessage.send(pc, 189); // \f1아데나가 부족합니다.
					}
				}
				htmlid = "";*/
				
			} else if (s.equalsIgnoreCase("contract1")) {
				pc.getQuest().set_step(L1Quest.QUEST_LYRA, 1);
				htmlid = "lyraev2";
			} else if (s.equalsIgnoreCase("contract1yes") || s.equalsIgnoreCase("contract1no")) {

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
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40131);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40131;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 50;
						totem++;
					}
					if (pc.getInventory().checkItem(40132)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40132);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40132;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 100;
						totem++;
					}
					if (pc.getInventory().checkItem(40133)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40133);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40133;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 50;
						totem++;
					}
					if (pc.getInventory().checkItem(40134)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40134);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40134;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 30;
						totem++;
					}
					if (pc.getInventory().checkItem(40135)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40135);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40135;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 200;
						totem++;
					}
				}
			} else if (s.equalsIgnoreCase("pandora6") || s.equalsIgnoreCase("cold6") || s.equalsIgnoreCase("balsim3")
					|| s.equalsIgnoreCase("mellin3") || s.equalsIgnoreCase("glen3")) {
				htmlid = s;
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				int taxRatesCastle = L1CastleLocation.getCastleTaxRateByNpcId(npcid);
				htmldata = new String[] { String.valueOf(taxRatesCastle) };
			} else if (s.equalsIgnoreCase("set")) {
				if (obj instanceof L1NpcInstance) {
					int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
					int town_id = L1TownLocation.getTownIdByNpcid(npcid);

					if (town_id >= 1 && town_id <= 10) {
						if (pc.getHomeTownId() == -1) {
							SC_ServerMessage.send(pc, 759);
							htmlid = "";
						} else if (pc.getHomeTownId() > 0) {
							if (pc.getHomeTownId() != town_id) {
								L1Town town = TownTable.getInstance().getTownTable(pc.getHomeTownId());
								if (town != null) {
									SC_ServerMessage.send(pc, 758, town.get_name());
								}
								htmlid = "";
							} else {
								htmlid = "";
							}
						} else if (pc.getHomeTownId() == 0) {
							if (pc.getLevel() < 10) {
								SC_ServerMessage.send(pc, 757);
								htmlid = "";
							} else {
								int level = pc.getLevel();
								int cost = level * level * 10;
								if (pc.getInventory().consumeItem(L1ItemId.ADENA, cost)) {
									pc.setHomeTownId(town_id);
									pc.setContribution(0);
									pc.save();
								} else {
									SC_ServerMessage.send(pc, 337, "$4");
								}
								htmlid = "";
							}
						}
					}
				}
			} else if (s.equalsIgnoreCase("Tgiran")) {
				pc.start_teleport(32683, 32895, 608, 5, 169, true, false);
				
			} else if (s.equalsIgnoreCase("clear")) {
				if (obj instanceof L1NpcInstance) {
					int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
					int town_id = L1TownLocation.getTownIdByNpcid(npcid);
					if (town_id > 0) {
						if (pc.getHomeTownId() > 0) {
							if (pc.getHomeTownId() == town_id) {
								pc.setHomeTownId(-1);
								pc.setContribution(0);
							} else {
								SC_ServerMessage.send(pc, 756);
							}
						}
						htmlid = "";
					}
				}
			} else if (s.equalsIgnoreCase("ask")) {
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
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71038) {
				if (s.equalsIgnoreCase("A")) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					L1ItemInstance item = pc.getInventory().storeItem(41060, 1);
					String itemName = item.getItem().getName();
					String npcName = npc.getNpcTemplate().get_name();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					htmlid = "orcfnoname9";
				} else if (s.equalsIgnoreCase("Z")) {
					if (pc.getInventory().consumeItem(41060, 1)) {
						htmlid = "orcfnoname11";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71039) {
				if (s.equalsIgnoreCase("teleportURL")) {
					htmlid = "orcfbuwoo2";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71040) {
				if (s.equalsIgnoreCase("A")) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					L1ItemInstance item = pc.getInventory().storeItem(41065, 1);
					String itemName = item.getItem().getName();
					String npcName = npc.getNpcTemplate().get_name();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					htmlid = "orcfnoa4";
				} else if (s.equalsIgnoreCase("Z")) {
					if (pc.getInventory().consumeItem(41065, 1)) {
						htmlid = "orcfnoa7";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71041) {
				if (s.equalsIgnoreCase("A")) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					L1ItemInstance item = pc.getInventory().storeItem(41064, 1);
					String itemName = item.getItem().getName();
					String npcName = npc.getNpcTemplate().get_name();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					htmlid = "orcfhuwoomo4";
				} else if (s.equalsIgnoreCase("Z")) {
					if (pc.getInventory().consumeItem(41064, 1)) {
						htmlid = "orcfhuwoomo6";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71042) {
				if (s.equalsIgnoreCase("A")) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					L1ItemInstance item = pc.getInventory().storeItem(41062, 1);
					String itemName = item.getItem().getName();
					String npcName = npc.getNpcTemplate().get_name();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					htmlid = "orcfbakumo4";
				} else if (s.equalsIgnoreCase("Z")) {
					if (pc.getInventory().consumeItem(41062, 1)) {
						htmlid = "orcfbakumo6";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71043) {
				if (s.equalsIgnoreCase("A")) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					L1ItemInstance item = pc.getInventory().storeItem(41063, 1);
					String itemName = item.getItem().getName();
					String npcName = npc.getNpcTemplate().get_name();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					htmlid = "orcfbuka4";
				} else if (s.equalsIgnoreCase("Z")) {
					if (pc.getInventory().consumeItem(41063, 1)) {
						htmlid = "orcfbuka6";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71044) {
				if (s.equalsIgnoreCase("A")) {
					L1NpcInstance npc = (L1NpcInstance) obj;
					L1ItemInstance item = pc.getInventory().storeItem(41061, 1);
					String itemName = item.getItem().getName();
					String npcName = npc.getNpcTemplate().get_name();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
					htmlid = "orcfkame4";
				} else if (s.equalsIgnoreCase("Z")) {
					if (pc.getInventory().consumeItem(41061, 1)) {
						htmlid = "orcfkame6";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71078) {
				if (s.equalsIgnoreCase("teleportURL")) {
					htmlid = "usender2";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71080) {
				if (s.equalsIgnoreCase("teleportURL")) {
					// htmlid = "amisoo2";
					SC_SystemMessage.send(pc, "하이네성 던전은 폐쇠 되었습니다.");
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80049) {
				if (s.equalsIgnoreCase("1")) {
					if (pc.getKarma() <= -10000000) {
						pc.setKarma(1000000);
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "betray13";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80050) {
				if (s.equalsIgnoreCase("1")) {
					htmlid = "meet105";
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getInventory().checkItem(40718)) {
						htmlid = "meet106";
					} else {
						htmlid = "meet110";
					}
				} else if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().consumeItem(40718, 1)) {
						pc.addKarma((int) (-100 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "meet107";
					} else {
						htmlid = "meet104";
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().consumeItem(40718, 10)) {
						pc.addKarma((int) (-1000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "meet108";
					} else {
						htmlid = "meet104";
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().consumeItem(40718, 100)) {
						pc.addKarma((int) (-10000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "meet109";
					} else {
						htmlid = "meet104";
					}
				} else if (s.equalsIgnoreCase("d")) {
					if (pc.getInventory().checkItem(40066)) {
						htmlid = "";
					} else {
						if (pc.getKarmaLevel() < -1) {
							pc.start_teleport(32683, 32895, 608, 5, 169, true, false);
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80052) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
						SC_ServerMessage.send(pc, 79);
					} else {
						pc.setSkillEffect(STATUS_CURSE_BARLOG, 1020 * 1000); // 1020
						SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 1, 1020);
						SC_SkillSound.send(pc, pc.getId(), 750);
						SC_SkillSound.broadcast(pc, pc.getId(), 750);
//						pc.sendPackets(new SC_ServerMessage(1127));
						 SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "야히의 군사들은 이곳 저곳에 배치가 되어있습니다.");
						pc.sendPackets("권한 부여: 거대한 마족 공격 가능(발록)");
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80053) {
				int karmaLevel = pc.getKarmaLevel();
				if (s.equalsIgnoreCase("a")) {
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
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71168) {
				// 지.상.의.타.락.을.막.기.위.해.서.라.도.
				// 그.대.와.나.더.이.상.적.일.수.만.은.없.으.리.
				// 어.서.나.를.그.곳.으.로
				if (s.equalsIgnoreCase("a")) {
					// L1Teleport.teleport(pc, 32648, 32921, (short) 535, 5, true);
					pc.start_teleport(32648, 32921, 535, 5, 169, true, false);
					pc.getInventory().consumeItem(41028, 1);
				} else {
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80056) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.getKarma() <= -10000000) {
					getBloodCrystalByKarma(pc, npc, s);
				}
				htmlid = "";
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80063) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().checkItem(40921)) {
						pc.getInventory().consumeItem(40921, 1);
						pc.start_teleport(32674, 32832, 603, 2, 169, true, false);
					} else {
						htmlid = "gpass02";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80064) {
				if (s.equalsIgnoreCase("1")) {
					htmlid = "meet005";
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getInventory().checkItem(40678)) {
						htmlid = "meet006";
					} else {
						htmlid = "meet010";
					}
				} else if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().consumeItem(40678, 1)) {
						pc.addKarma((int) (100 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "meet007";
					} else {
						htmlid = "meet004";
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().consumeItem(40678, 10)) {
						pc.addKarma((int) (1000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "meet008";
					} else {
						htmlid = "meet004";
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().consumeItem(40678, 100)) {
						pc.addKarma((int) (10000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "meet009";
					} else {
						htmlid = "meet004";
					}
				} else if (s.equalsIgnoreCase("d")) {
					if (pc.getInventory().checkItem(40066) // 잠시 송편으로 대체하자! 송편이있을경우
															// 안날라가지게~
					/*
					 * || pc.getInventory().checkItem(40910) ||
					 * pc.getInventory().checkItem(40911) ||
					 * pc.getInventory().checkItem(40912) ||
					 * pc.getInventory().checkItem(40913) ||
					 * pc.getInventory().checkItem(40914) ||
					 * pc.getInventory().checkItem(40915) ||
					 * pc.getInventory().checkItem(40916) ||
					 * pc.getInventory().checkItem(40917) ||
					 * pc.getInventory().checkItem(40918) ||
					 * pc.getInventory().checkItem(40919) ||
					 * pc.getInventory().checkItem(40920) ||
					 * pc.getInventory().checkItem(40921)
					 */) {
						htmlid = "";
					} else {
						if (pc.getKarmaLevel() > 1) {
							// L1Teleport.teleport(pc, 32674, 32832, (short) 602, 2,
							// true);
							pc.start_teleport(32674, 32832, 602, 2, 169, true, false);
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80066) {
				if (s.equalsIgnoreCase("1")) {
					if (pc.getKarma() >= 10000000) {
						pc.setKarma(-1000000);
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "betray03";
					}
				}
			} 

			else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80073) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
						SC_ServerMessage.send(pc, 79);
					} else {
						pc.setSkillEffect(STATUS_CURSE_YAHEE, 1020 * 1000);
						SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 2, 1020);
						SC_SkillSound.send(pc, pc.getId(), 750);
						SC_SkillSound.broadcast(pc, pc.getId(), 750);
//						pc.sendPackets(new SC_ServerMessage(1127));
						pc.sendPackets("권한 부여: 거대한 마족 공격 가능(혼돈-죽음-타락-야히)");
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80072) {
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
				} else if (s.equalsIgnoreCase("a") && karmaLevel >= 1) {
					materials = new int[] { 20158, 40669, 40678 };
					counts = new int[] { 1, 50, 100 };
					createitem = new int[] { 20083 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithaa";
				} else if (s.equalsIgnoreCase("b") && karmaLevel >= 2) {
					materials = new int[] { 20144, 40672, 40678 };
					counts = new int[] { 1, 50, 100 };
					createitem = new int[] { 20131 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithbb";
				} else if (s.equalsIgnoreCase("c") && karmaLevel >= 3) {
					materials = new int[] { 20075, 40671, 40678 };
					counts = new int[] { 1, 50, 100 };
					createitem = new int[] { 20069 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithcc";
				} else if (s.equalsIgnoreCase("d") && karmaLevel >= 4) {
					materials = new int[] { 20183, 40674, 40678 };
					counts = new int[] { 1, 20, 100 };
					createitem = new int[] { 20179 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithdd";
				} else if (s.equalsIgnoreCase("e") && karmaLevel >= 5) {
					materials = new int[] { 20190, 40674, 40678 };
					counts = new int[] { 1, 40, 100 };
					createitem = new int[] { 20209 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithee";
				} else if (s.equalsIgnoreCase("f") && karmaLevel >= 6) {
					materials = new int[] { 20078, 40674, 40678 };
					counts = new int[] { 1, 5, 100 };
					createitem = new int[] { 20290 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithff";
				} else if (s.equalsIgnoreCase("g") && karmaLevel >= 7) {
					materials = new int[] { 20078, 40670, 40678 };
					counts = new int[] { 1, 1, 100 };
					createitem = new int[] { 20261 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithgg";
				} else if (s.equalsIgnoreCase("h") && karmaLevel >= 8) {
					materials = new int[] { 40719, 40673, 40678 };
					counts = new int[] { 1, 1, 100 };
					createitem = new int[] { 20031 };
					createcount = new int[] { 1 };
					success_htmlid = "";
					failure_htmlid = "lsmithhh";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 205) {// 테베문지기
//				if (s.equalsIgnoreCase("e") && CrockController.getInstance().isBoss()) {
//					if (CrockController.getInstance().add(pc)) {
//						if (pc.getInventory().checkItem(100036, 1)) {
//							pc.getInventory().consumeItem(100036, 1);
//							pc.start_teleport(32735, 32831, 782, 5, 169, true, false);
//						}
//					}
//				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 7000021) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.isInvisble()) {
					SC_NpcChatPacket.sendNpc(pc, npc, "투명 상태에서 받을 수 없어요.", 0);
					return;
				}
				if (s.equalsIgnoreCase("1")) {
					comaCheck(pc, 3, objid);
				} else if (s.equalsIgnoreCase("2")) {
					comaCheck(pc, 5, objid);
				} else if (s.equalsIgnoreCase("a")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 1);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("b")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 2);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("c")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 3);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("d")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 4);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("e")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 5);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("f")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 1);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("g")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 2);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("h")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 3);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("i")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 4);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("j")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 5);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("k")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 1);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("l")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 2);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("m")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 3);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("n")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 4);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("o")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 5);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("p")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 1);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("q")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 2);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("s")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 3);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("t")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 4);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("u")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 5);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("v")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 1);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("w")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 2);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("x")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 3);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("y")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 4);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("z")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 5);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("3")) {
					resetPiece(pc);
					selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("4")) {
					giveComaBuff(pc, objid);
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 500062) {// 조우
																						// 티칼
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				if (s.equalsIgnoreCase("A")) {
					if (pc.getInventory().checkItem(210081, 100)) {
						pc.getInventory().consumeItem(210081, 100);
						L1ItemInstance item = pc.getInventory().storeItem(210082, 1);
						String itemName = item.getItem().getName();
						SC_ServerMessage.send(pc, 143, npcName, itemName);
						htmlid = "joegolem18";
					} else {
						htmlid = "joegolem19";
					}
				} else if (s.equalsIgnoreCase("B")) {
					pc.start_teleport(34090, 33168, 4, 5, 169, true, false);
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 500063) {// 티칼
//				if (s.equalsIgnoreCase("e") && CrockController.getInstance().isBoss())
//					if (pc.getInventory().checkItem(500210, 1)) {
//						pc.getInventory().consumeItem(500210);
//						CrockController.getInstance().add(pc);
//						pc.start_teleport(32732, 32862, 784, 5, 169, true, false);
//					}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 500064) {// 티칼
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				if (s.equalsIgnoreCase("1") && !pc.getInventory().checkItem(500211, 1)) {
					L1ItemInstance item = pc.getInventory().storeItem(500211, 1);
					String itemName = item.getItem().getName();
					SC_ServerMessage.send(pc, 143, npcName, itemName);
				}
				// 라폰스시작
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80074) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.getKarma() >= 10000000) {
					getSoulCrystalByKarma(pc, npc, s);
				}
				htmlid = "";
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50007) { // 에스메랄다
				int[][] ghostloc = {
						{ 32642, 34249, 32878, 33114, 32734, 32736, 32737, 32769, 33931, 33051, 32870, 33972, 33427, 33594, 33446, 32741 },
						{ 32956, 33453, 32653, 32939, 32831, 32814, 32684, 32800, 33347, 32340, 33254, 33363, 32814, 33244, 32757, 32284, },
						{ 0, 4, 4, 4, 278, 62, 63, 77, 4, 4, 4, 4, 4, 4, 4, 4, } };
				if (s.equalsIgnoreCase("journey")) {

					int rndIndex = ThreadLocalRandom.current().nextInt(ghostloc[0].length);	    
				    int locX = ghostloc[0][rndIndex];
				    int locY = ghostloc[1][rndIndex];
				    short mapId = (short) ghostloc[2][rndIndex];	    
				    pc.beginGhost(locX, locY, mapId, true, 8);
				
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80057) {
				htmlid = karmaLevelToHtmlId(pc.getKarmaLevel());
				htmldata = new String[] { String.valueOf(pc.getKarmaPercent()) };
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80059
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80060
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80061
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80062) {
				htmlid = talkToDimensionDoor(pc, (L1NpcInstance) obj, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 81124) {
				if (s.equalsIgnoreCase("1")) {
					poly(client, 4002);
					htmlid = "";
				} else if (s.equalsIgnoreCase("2")) {
					poly(client, 4004);
					htmlid = "";
				} else if (s.equalsIgnoreCase("3")) {
					poly(client, 4950);
					htmlid = "";
				}
			} else if (s.equalsIgnoreCase("contract1")) {
				pc.getQuest().set_step(L1Quest.QUEST_LYRA, 1);
				htmlid = "lyraev2";
			} else if (s.equalsIgnoreCase("contract1yes") || s.equalsIgnoreCase("contract1no")) {

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
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40131);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40131;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 50;
						totem++;
					}
					if (pc.getInventory().checkItem(40132)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40132);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40132;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 100;
						totem++;
					}
					if (pc.getInventory().checkItem(40133)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40133);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40133;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 50;
						totem++;
					}
					if (pc.getInventory().checkItem(40134)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40134);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40134;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 30;
						totem++;
					}
					if (pc.getInventory().checkItem(40135)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40135);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40135;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = i1 * 200;
						totem++;
					}
				}
			} else if (s.equalsIgnoreCase("pandora6") || s.equalsIgnoreCase("cold6") || s.equalsIgnoreCase("balsim3")
					|| s.equalsIgnoreCase("mellin3") || s.equalsIgnoreCase("glen3")) {
				htmlid = s;
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				int taxRatesCastle = L1CastleLocation.getCastleTaxRateByNpcId(npcid);
				htmldata = new String[] { String.valueOf(taxRatesCastle) };
			} else if (s.equalsIgnoreCase("set")) {
				if (obj instanceof L1NpcInstance) {
					int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
					int town_id = L1TownLocation.getTownIdByNpcid(npcid);

					if (town_id >= 1 && town_id <= 10) {
						if (pc.getHomeTownId() == -1) {
							SC_ServerMessage.send(pc, 759);
							htmlid = "";
						} else if (pc.getHomeTownId() > 0) {
							if (pc.getHomeTownId() != town_id) {
								L1Town town = TownTable.getInstance().getTownTable(pc.getHomeTownId());
								if (town != null) {
									SC_ServerMessage.send(pc, 758, town.get_name());
								}
								htmlid = "";
							} else {
								htmlid = "";
							}
						} else if (pc.getHomeTownId() == 0) {
							if (pc.getLevel() < 10) {
								SC_ServerMessage.send(pc, 757);
								htmlid = "";
							} else {
								int level = pc.getLevel();
								int cost = level * level * 10;
								if (pc.getInventory().consumeItem(L1ItemId.ADENA, cost)) {
									pc.setHomeTownId(town_id);
									pc.setContribution(0);
									pc.save();
								} else {
									SC_ServerMessage.send(pc, 337, "$4");
								}
								htmlid = "";
							}
						}
					}
				}
			} else if (s.equalsIgnoreCase("clear")) {
				if (obj instanceof L1NpcInstance) {
					int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
					int town_id = L1TownLocation.getTownIdByNpcid(npcid);
					if (town_id > 0) {
						if (pc.getHomeTownId() > 0) {
							if (pc.getHomeTownId() == town_id) {
								pc.setHomeTownId(-1);
								pc.setContribution(0);
							} else {
								SC_ServerMessage.send(pc, 756);
							}
						}
						htmlid = "";
					}
				}
			} else if (s.equalsIgnoreCase("ask")) {
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
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70534
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70556
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70572
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70631
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70663
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70761
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70788
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70806
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70830
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70876) {
				if (s.equalsIgnoreCase("r")) {
					if (obj instanceof L1NpcInstance) {
					}
				} else if (s.equalsIgnoreCase("t")) {

				} else if (s.equalsIgnoreCase("c")) {

				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71005) {
				if (s.equalsIgnoreCase("0")) {
					if (!pc.getInventory().checkItem(41209)) {
						final int[] item_ids = { 41209, };
						final int[] item_amounts = { 1, };
						for (int i = 0; i < item_ids.length; i++) {
							L1ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
							SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
						}
					}
					htmlid = "";
				} else if (s.equalsIgnoreCase("1")) {
					if (pc.getInventory().consumeItem(41213, 1)) {
						final int[] item_ids = { 40029, };
						final int[] item_amounts = { 20, };
						L1ItemInstance item = null;
						for (int i = 0; i < item_ids.length; i++) {
							item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
							SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName() + " (" + item_amounts[i] + ")");
						}
						htmlid = "";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71006) {
				if (s.equalsIgnoreCase("0")) {
					if (pc.getLevel() > 25) {
						htmlid = "jpe0057";
					} else if (pc.getInventory().checkItem(41213)) {
						htmlid = "jpe0056";
					} else if (pc.getInventory().checkItem(41210) || pc.getInventory().checkItem(41211)) {
						htmlid = "jpe0055";
					} else if (pc.getInventory().checkItem(41209)) {
						htmlid = "jpe0054";
					} else if (pc.getInventory().checkItem(41212)) {
						htmlid = "jpe0056";
						materials = new int[] { 41212 };
						counts = new int[] { 1 };
						createitem = new int[] { 41213 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "jpe0057";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70512) {
				if (s.equalsIgnoreCase("0") || s.equalsIgnoreCase("fullheal")) {
					int hp = _random.nextInt(21) + 70;
					pc.setCurrentHp(pc.getCurrentHp() + hp);
					SC_ServerMessage.send(pc, 77);
					SC_SkillSound.send(pc, pc.getId(), 830);
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71025) {
				if (s.equalsIgnoreCase("0")) {
					final int[] item_ids = { 41225, };
					final int[] item_amounts = { 1, };
					for (int i = 0; i < item_ids.length; i++) {
						L1ItemInstance item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
					}
					htmlid = "jpe0083";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 454512) {
				
				if (s.equalsIgnoreCase("a")) {
					pc.start_teleport(32734, 32859, 13005, 5, 169, true, false);
					htmlid = "";
				}
				
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 454513) {
				if (s.equalsIgnoreCase("a")) {
					pc.setCurrentHp(pc.getMaxHp() / 5);
					pc.setCurrentMp(pc.getMaxMp() / 5);
					pc.start_teleport(33431, 32798, 4, 5, 169, true, false);
					htmlid = "";
				}
					
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 454514) {
				
				if (s.equalsIgnoreCase("a")) { 
					
					int hp = pc.getMaxHp() - pc.getCurrentHp();
					int mp = pc.getMaxMp() - pc.getCurrentMp();
					
					pc.setCurrentHp(pc.getCurrentHp() + hp);
					pc.setCurrentMp(pc.getCurrentMp() + mp);
					SC_SkillSound.send(pc, pc.getId(), 832);
					htmlid = "";
					
				}
				
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71055) {
				if (s.equalsIgnoreCase("0")) {
					final int[] item_ids = { 40701, };
					final int[] item_amounts = { 1, };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
					}
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 1);
					htmlid = "lukein8";
				}
				if (s.equalsIgnoreCase("1")) {
					pc.getQuest().set_end(L1Quest.QUEST_TBOX3);
					materials = new int[] { 40716 }; // 할아버지의 보물
					counts = new int[] { 1 };
					createitem = new int[] { 20269 }; // 해골목걸이
					createcount = new int[] { 1 };
					htmlid = "lukein0";
				} else if (s.equalsIgnoreCase("2")) {
					htmlid = "lukein12";
					pc.getQuest().set_step(L1Quest.QUEST_RESTA, 3);
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71063) {
				if (s.equalsIgnoreCase("0")) {
					materials = new int[] { 40701 };
					counts = new int[] { 1 };
					createitem = new int[] { 40702 };
					createcount = new int[] { 1 };
					htmlid = "maptbox1";
					pc.getQuest().set_end(L1Quest.QUEST_TBOX1);
					int[] nextbox = { 1, 2, 3 };
					int pid = _random.nextInt(nextbox.length);
					int nb = nextbox[pid];
					if (nb == 1) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 2);
					} else if (nb == 2) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 3);
					} else if (nb == 3) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 4);
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71064
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71065
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71066) {
				if (s.equalsIgnoreCase("0")) {
					materials = new int[] { 40701 };
					counts = new int[] { 1 };
					createitem = new int[] { 40702 };
					createcount = new int[] { 1 };
					htmlid = "maptbox1";
					pc.getQuest().set_end(L1Quest.QUEST_TBOX2);
					int[] nextbox2 = { 1, 2, 3, 4, 5, 6 };
					int pid = _random.nextInt(nextbox2.length);
					int nb2 = nextbox2[pid];
					if (nb2 == 1) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 5);
					} else if (nb2 == 2) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 6);
					} else if (nb2 == 3) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 7);
					} else if (nb2 == 4) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 8);
					} else if (nb2 == 5) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 9);
					} else if (nb2 == 6) {
						pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 10);
					}
				}

				// 작은 상자-3번째
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71067
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71068
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71069
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71070
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71071
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71072) {
				if (s.equalsIgnoreCase("0")) {
					htmlid = "maptboxi";
					materials = new int[] { 40701 }; // 작은 보물의 지도
					counts = new int[] { 1 };
					createitem = new int[] { 40716 }; // 할아버지의 보물
					createcount = new int[] { 1 };
					pc.getQuest().set_end(L1Quest.QUEST_TBOX3);
					pc.getQuest().set_step(L1Quest.QUEST_LUKEIN1, 11);
				}

				// 시미즈(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71056) {
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

				// 도일(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71057) {
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

				// 루디 안(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 83014) {
				// 러쉬에 대해 듣는다
				if (s.equalsIgnoreCase("1")) {
					if (!pc.getInventory().checkItem(510012, 300) && !pc.getInventory().checkItem(510013, 300) && !pc.getInventory().checkItem(510014, 300)) {
						pc.sendPackets("매입가능한 품목이 부족 하다구!");
						return;
					}
					if (pc.getInventory().checkItem(510012, 300)) {
						pc.getInventory().consumeItem(510012, 300);
						pc.getInventory().storeItem(400254, 1);			
						pc.sendPackets("미소피아(성장의축복) 300개 매입 완료 / 수표 1장 지급");
					} 
					if (pc.getInventory().checkItem(510013, 300)) {
						pc.getInventory().consumeItem(510013, 300);
						pc.getInventory().storeItem(400254, 1);			
						pc.sendPackets("미소피아(방어의축복) 300개 매입 완료 / 수표 1장 지급");
					}
					if (pc.getInventory().checkItem(510014, 300)) {
						pc.getInventory().consumeItem(510014, 300);
						pc.getInventory().storeItem(400254, 1);		
						pc.sendPackets("미소피아(공격의축복) 300개 매입 완료 / 수표 1장 지급");
					} 
				} else if (s.equalsIgnoreCase("2")) {
					if (!pc.getInventory().checkItem(3000128, 300) && !pc.getInventory().checkItem(3000129, 300) && !pc.getInventory().checkItem(3000130, 300)) {
						pc.sendPackets("매입가능한 품목이 부족 하다구!");
						return;
					}
					if (pc.getInventory().checkItem(3000128, 300)) {
						pc.getInventory().consumeItem(3000128, 300);
						pc.getInventory().storeItem(400254, 1);			
						pc.sendPackets("메티스의 축복 주문서 300개 매입 완료 / 수표 1장 지급");
					} 
					if (pc.getInventory().checkItem(3000129, 300)) {
						pc.getInventory().consumeItem(3000129, 300);
						pc.getInventory().storeItem(400254, 1);			
						pc.sendPackets("메티스의 정성스런 스프 300개 매입 완료 / 수표 1장 지급");
					}
					if (pc.getInventory().checkItem(3000130, 300)) {
						pc.getInventory().consumeItem(3000130, 300);
						pc.getInventory().storeItem(400254, 1);		
						pc.sendPackets("메티스의 정성스런 요리 300개 매입 완료 / 수표 1장 지급");
					} 
				} else if (s.equalsIgnoreCase("3")) {
					if (pc.getInventory().checkItem(30104, 300)) {
						pc.getInventory().consumeItem(30104, 300);
						pc.getInventory().storeItem(400254, 1);			
						pc.sendPackets("코마의 축복 코인 300개 매입 완료 / 수표 1장 지급");
					} else {
						pc.sendPackets("매입가능한 품목이 부족 하다구!");
					}
				
					
				}

				// 루디 안(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71059) {
				// 루디 안의 부탁을 받아들인다
				if (s.equalsIgnoreCase("A")) {
					htmlid = "rudian6";
					final int[] item_ids = { 40700 };
					final int[] item_amounts = { 1 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
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

				// 레스타(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71060) {
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

				// 카좀스(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71061) {
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
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71036) {
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
				} else if (s.equalsIgnoreCase("i")) {
					htmlid = "kamyla25";
				} else if (s.equalsIgnoreCase("b")) { // 카 미라(흐랑코의 미궁)
					if (pc.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 1) {
						// L1Teleport.teleport(pc, 32679, 32742, (short) 482, 5,
						// true);
						pc.start_teleport(32679, 32742, 482, 5, 169, true, false);
					}
				} else if (s.equalsIgnoreCase("d")) { // 카 미라(디에고가 닫힌 뇌)
					if (pc.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 3) {
						// L1Teleport.teleport(pc, 32736, 32800, (short) 483, 5,
						// true);
						pc.start_teleport(32736, 32800, 483, 5, 169, true, false);
					}
				} else if (s.equalsIgnoreCase("f")) { // 카 미라(호세 지하소굴)
					if (pc.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 4) {
						// L1Teleport.teleport(pc, 32746, 32807, (short) 484, 5,
						// true);
						pc.start_teleport(32746, 32807, 484, 5, 169, true, false);
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71089) {
				if (s.equalsIgnoreCase("a")) {
					htmlid = "francu10";
					final int[] item_ids = { 40644 };
					final int[] item_amounts = { 1 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
						pc.getQuest().set_step(L1Quest.QUEST_KAMYLA, 2);
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71090) {
				if (s.equalsIgnoreCase("a")) {
					htmlid = "";
					final int[] item_ids = { 246, 247, 248, 249, 40660 };
					final int[] item_amounts = { 1, 1, 1, 1, 5 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
						pc.getQuest().set_step(L1Quest.QUEST_CRYSTAL, 1);
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().checkEquipped(246) || pc.getInventory().checkEquipped(247)
							|| pc.getInventory().checkEquipped(248) || pc.getInventory().checkEquipped(249)) {
						htmlid = "jcrystal5";
					} else if (pc.getInventory().checkItem(40660)) {
						htmlid = "jcrystal4";
					} else {
						pc.getInventory().consumeItem(246, 1);
						pc.getInventory().consumeItem(247, 1);
						pc.getInventory().consumeItem(248, 1);
						pc.getInventory().consumeItem(249, 1);
						pc.getInventory().consumeItem(40620, 1);
						pc.getQuest().set_step(L1Quest.QUEST_CRYSTAL, 2);
						pc.start_teleport(32801, 32895, 483, 5, 169, true, false);
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().checkEquipped(246) || pc.getInventory().checkEquipped(247)
							|| pc.getInventory().checkEquipped(248) || pc.getInventory().checkEquipped(249)) {
						htmlid = "jcrystal5";
					} else {
						pc.getInventory().checkItem(40660);
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40660);
						int sc = l1iteminstance.getCount();
						if (sc > 0) {
							pc.getInventory().consumeItem(40660, sc);
						} else {
						}
						pc.getInventory().consumeItem(246, 1);
						pc.getInventory().consumeItem(247, 1);
						pc.getInventory().consumeItem(248, 1);
						pc.getInventory().consumeItem(249, 1);
						pc.getInventory().consumeItem(40620, 1);
						pc.getQuest().set_step(L1Quest.QUEST_CRYSTAL, 0);
						pc.start_teleport(32736, 32800, 483, 5, 169, true, false);
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71091) {
				if (s.equalsIgnoreCase("a")) {
					htmlid = "";
					pc.getInventory().consumeItem(40654, 1);
					pc.getQuest().set_step(L1Quest.QUEST_CRYSTAL, L1Quest.QUEST_END);
					pc.start_teleport(32744, 32927, 483, 5, 169, true, false);
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71074) {
				if (s.equalsIgnoreCase("A")) {
					htmlid = "lelder5";
					pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 1);
				} else if (s.equalsIgnoreCase("B")) {
					htmlid = "lelder10";
					pc.getInventory().consumeItem(40633, 1);
					pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 3);
				} else if (s.equalsIgnoreCase("C")) {
					htmlid = "lelder13";
					materials = new int[] { 40634 };
					counts = new int[] { 1 };
					createitem = new int[] { 20167 }; // 리자드망로브
					createcount = new int[] { 1 };
					pc.getQuest().set_step(L1Quest.QUEST_LIZARD, L1Quest.QUEST_END);
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71198) {
				if (s.equalsIgnoreCase("A")) {
					if (pc.getQuest().get_step(71198) != 0 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(41339, 5)) {
						L1ItemInstance item = ItemTable.getInstance().createItem(41340);
						if (item != null) {
							if (pc.getInventory().checkAddItem(item, 1) == 0) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							}
						}
						pc.getQuest().set_step(71198, 1);
						htmlid = "tion4";
					} else {
						htmlid = "tion9";
					}
				} else if (s.equalsIgnoreCase("B")) {
					if (pc.getQuest().get_step(71198) != 1 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(41341, 1)) {
						pc.getQuest().set_step(71198, 2);
						htmlid = "tion5";
					} else {
						htmlid = "tion10";
					}
				} else if (s.equalsIgnoreCase("C")) {
					if (pc.getQuest().get_step(71198) != 2 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(41343, 1)) {
						L1ItemInstance item = ItemTable.getInstance().createItem(21057);
						if (item != null) {
							if (pc.getInventory().checkAddItem(item, 1) == 0) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							}
						}
						pc.getQuest().set_step(71198, 3);
						htmlid = "tion6";
					} else {
						htmlid = "tion12";
					}
				} else if (s.equalsIgnoreCase("D")) {
					if (pc.getQuest().get_step(71198) != 3 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(41344, 1)) {
						L1ItemInstance item = ItemTable.getInstance().createItem(21058);
						if (item != null) {
							pc.getInventory().consumeItem(21057, 1);
							if (pc.getInventory().checkAddItem(item, 1) == 0) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							}
						}
						pc.getQuest().set_step(71198, 4);
						htmlid = "tion7";
					} else {
						htmlid = "tion13";
					}
				} else if (s.equalsIgnoreCase("E")) {
					if (pc.getQuest().get_step(71198) != 4 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(41345, 1)) {
						L1ItemInstance item = ItemTable.getInstance().createItem(21059);
						if (item != null) {
							pc.getInventory().consumeItem(21058, 1);
							if (pc.getInventory().checkAddItem(item, 1) == 0) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							}
						}
						pc.getQuest().set_step(71198, 0);
						pc.getQuest().set_step(71199, 0);
						htmlid = "tion8";
					} else {
						htmlid = "tion15";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71199) {
				if (s.equalsIgnoreCase("A")) {
					if (pc.getQuest().get_step(71199) != 0 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().checkItem(41340, 1)) {
						pc.getQuest().set_step(71199, 1);
						htmlid = "jeron2";
					} else {
						htmlid = "jeron10";
					}
				} else if (s.equalsIgnoreCase("B")) {
					if (pc.getQuest().get_step(71199) != 1 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(40308, 1000000)) {
						L1ItemInstance item = ItemTable.getInstance().createItem(41341);
						if (item != null) {
							if (pc.getInventory().checkAddItem(item, 1) == 0) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							}
						}
						pc.getInventory().consumeItem(41340, 1);
						pc.getQuest().set_step(71199, 255);
						htmlid = "jeron6";
					} else {
						htmlid = "jeron8";
					}
				} else if (s.equalsIgnoreCase("C")) {
					if (pc.getQuest().get_step(71199) != 1 || pc.getInventory().checkItem(21059, 1)) {
						return;
					}
					if (pc.getInventory().consumeItem(41342, 1)) {
						L1ItemInstance item = ItemTable.getInstance().createItem(41341);
						if (item != null) {
							if (pc.getInventory().checkAddItem(item, 1) == 0) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							}
						}
						pc.getInventory().consumeItem(41340, 1);
						pc.getQuest().set_step(71199, 255);
						htmlid = "jeron5";
					} else {
						htmlid = "jeron9";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80079) {
				if (s.equalsIgnoreCase("0")) {
					if (!pc.getInventory().checkItem(41312)) {
						L1ItemInstance item = pc.getInventory().storeItem(41312, 1);
						if (item != null) {
							SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
							pc.getQuest().set_step(L1Quest.QUEST_KEPLISHA, L1Quest.QUEST_END);
						}
						htmlid = "keplisha7";
					}
				} else if (s.equalsIgnoreCase("1")) {
					if (!pc.getInventory().checkItem(41314)) {
						if (pc.getInventory().checkItem(L1ItemId.ADENA, 1000)) {
							materials = new int[] { L1ItemId.ADENA, 41313 };
							counts = new int[] { 1000, 1 };
							createitem = new int[] { 41314 };
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
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getCurrentSpriteId() != pc.getClassId()) {
						htmlid = "keplisha9";
					} else {
						if (pc.getInventory().checkItem(41314)) {
							pc.getInventory().consumeItem(41314, 1);
							int html = _random.nextInt(9) + 1;
							int PolyId = 6180 + _random.nextInt(40);
							polyByKeplisha(client, PolyId);
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
				} else if (s.equalsIgnoreCase("3")) {
					if (pc.getInventory().checkItem(41312)) {
						pc.getInventory().consumeItem(41312, 1);
						htmlid = "";
					}
					if (pc.getInventory().checkItem(41313)) {
						pc.getInventory().consumeItem(41313, 1);
						htmlid = "";
					}
					if (pc.getInventory().checkItem(41314)) {
						pc.getInventory().consumeItem(41314, 1);
						htmlid = "";
					}
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71181) { // 에마이
				if (s.equalsIgnoreCase("A")) { // 곰인형
					if (pc.getInventory().consumeItem(41093, 1)) {
						pc.getInventory().storeItem(41097, 1);
						htmlid = "my5";
					} else {
						htmlid = "my4";
					}
				} else if (s.equalsIgnoreCase("B")) { // 향수
					if (pc.getInventory().consumeItem(41094, 1)) {
						pc.getInventory().storeItem(41918, 1);
						htmlid = "my6";
					} else {
						htmlid = "my4";
					}
				} else if (s.equalsIgnoreCase("C")) { // 드레스
					if (pc.getInventory().consumeItem(41095, 1)) {
						pc.getInventory().storeItem(41919, 1);
						htmlid = "my7";
					} else {
						htmlid = "my4";
					}
				} else if (s.equalsIgnoreCase("D")) { // 반지
					if (pc.getInventory().consumeItem(41096, 1)) {
						pc.getInventory().storeItem(41920, 1);
						htmlid = "my8";
					} else {
						htmlid = "my4";
					}
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80084) {
				if (s.equalsIgnoreCase("q")) {
					if (pc.getInventory().checkItem(41356, 1)) {
						htmlid = "rparum4";
					} else {
						L1ItemInstance item = pc.getInventory().storeItem(41356, 1);
						if (item != null) {
							SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
						}
						htmlid = "rparum3";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80105) {
				if (s.equalsIgnoreCase("c")) {
					if (pc.isCrown()) {
						if (pc.getInventory().checkItem(20383, 1)) {
							if (pc.getInventory().checkItem(L1ItemId.ADENA, 100000)) {
								L1ItemInstance item = pc.getInventory().findItemId(20383);
								if (item != null && item.getChargeCount() != 50) {
									item.setChargeCount(50);
									pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
									pc.getInventory().consumeItem(L1ItemId.ADENA, 100000);
									htmlid = "";
								}
							} else {
								SC_ServerMessage.send(pc, 337, "$4");
							}
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4202000) { // 용기사
				// 피에나
				if (s.equalsIgnoreCase("teleportURL") && pc.isDragonknight()) {
					htmlid = "feaena3";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4201000) { // 환술사
				// 아샤
				if (s.equalsIgnoreCase("teleportURL") && pc.isBlackwizard()) {
					htmlid = "asha3";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200015) {
				if (s.equalsIgnoreCase("0")) {
					if (pc.getInventory().checkItem(200000)) {
						htmlid = "candleg3";
					} else {
						pc.getInventory().storeItem(200000, 1);
						htmlid = "candleg2";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200016) {
				if (s.equalsIgnoreCase("B")) {
					if (pc.getInventory().checkItem(210081)) {
						pc.getInventory().consumeItem(210081, 1);
						// L1Teleport.teleport(pc, 32618, 32736, (short) 4, 4,
						// true);
						pc.start_teleport(32618, 32736, 4, 5, 169, true, false);
					} else {
						htmlid = "joegolem20";
					}
				}
			}

			else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3000003) {
				if (s.equalsIgnoreCase("a")) {
					htmlid = "prokel3";
					pc.getInventory().storeItem(210087, 1);
					pc.getQuest().set_step(L1Quest.QUEST_LEVEL15, 1);
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().checkItem(210088) || pc.getInventory().checkItem(210089)
							|| pc.getInventory().checkItem(210090)) {
						htmlid = "prokel5";
						pc.getInventory().consumeItem(210088, 1);
						pc.getInventory().consumeItem(210089, 1);
						pc.getInventory().consumeItem(210090, 1);
						pc.getInventory().storeItem(502, 1);
						pc.getInventory().storeItem(210020, 1);
						pc.getQuest().set_step(L1Quest.QUEST_LEVEL15, 255);
					} else {
						htmlid = "prokel6";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3100004) {
				if (s.equalsIgnoreCase("a")) {
					htmlid = "silrein4";
					pc.getInventory().storeItem(210092, 5);
					pc.getInventory().storeItem(210093, 1);
					pc.getQuest().set_step(L1Quest.QUEST_LEVEL15, 1);
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().checkItem(210091, 10) || pc.getInventory().checkItem(40510)
							|| pc.getInventory().checkItem(40511) || pc.getInventory().checkItem(40512)
							|| pc.getInventory().checkItem(41080)) {
						htmlid = "silrein7";
						pc.getInventory().consumeItem(210091, 10);
						pc.getInventory().consumeItem(40510, 1);
						pc.getInventory().consumeItem(40511, 1);
						pc.getInventory().consumeItem(40512, 1);
						pc.getInventory().consumeItem(41080, 1);
						pc.getInventory().storeItem(505, 1);
						pc.getInventory().storeItem(210004, 1);
						pc.getQuest().set_step(L1Quest.QUEST_LEVEL15, 255);
					} else {
						htmlid = "silrein8";
					}
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3200014) {
				if (s.equalsIgnoreCase("buy 5")) {
					petbuy(client, 46044, 41159, 1000);
					htmlid = "subsusp3";
				} else if (s.equalsIgnoreCase("buy 6")) {
					petbuy(client, 46042, 41159, 1000);
					htmlid = "subsusp4";
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200075) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				int count = 1;
				int itemid = 0;
				if (pc.getLevel() >= 30 && pc.getLevel() < 40) {
					itemid = 210097;
				} else if (pc.getLevel() >= 40 && pc.getLevel() < 52) {
					itemid = 210098;
				} else if (pc.getLevel() >= 52 && pc.getLevel() < 55) {
					itemid = 210099;
				} else if (pc.getLevel() >= 55 && pc.getLevel() < 60) {
					itemid = 210100;
				} else if (pc.getLevel() >= 60 && pc.getLevel() < 65) {
					itemid = 210101;
				} else if (pc.getLevel() >= 65 && pc.getLevel() < 70) {
					itemid = 210102;
				} else if (pc.getLevel() >= 70 && pc.getLevel() < 75) {
					itemid = 210103;
				} else if (pc.getLevel() >= 75 && pc.getLevel() < 80) {
					itemid = 210116;
				} else if (pc.getLevel() >= 80) {
					itemid = 210117;
				}
				if (s.equalsIgnoreCase("0"))
					count = 1;
				else if (s.equalsIgnoreCase("1"))
					count = 2;
				else if (s.equalsIgnoreCase("2"))
					count = 3;
				else if (s.equalsIgnoreCase("3"))
					count = 4;
				else if (s.equalsIgnoreCase("4"))
					count = 5;
				else if (s.equalsIgnoreCase("5"))
					count = 6;
				else if (s.equalsIgnoreCase("6"))
					count = 7;
				else if (s.equalsIgnoreCase("7"))
					count = 8;
				else if (s.equalsIgnoreCase("8"))
					count = 9;
				else if (s.equalsIgnoreCase("9"))
					count = 10;
				else if (s.equalsIgnoreCase(":"))
					count = 11;
				else if (s.equalsIgnoreCase(";"))
					count = 12;
				else if (s.equalsIgnoreCase("<"))
					count = 13;
				else if (s.equalsIgnoreCase("="))
					count = 14;
				else if (s.equalsIgnoreCase(">"))
					count = 15;
				else if (s.equalsIgnoreCase("?"))
					count = 16;
				else if (s.equalsIgnoreCase("@"))
					count = 17;
				else if (s.equalsIgnoreCase("A"))
					count = 18;
				else if (s.equalsIgnoreCase("B"))
					count = 19;
				else if (s.equalsIgnoreCase("C"))
					count = 20;
				else
					count = 1;
				int adena = count * 2500;
				if (pc.getInventory().checkItem(40308, adena)) {
					pc.getInventory().consumeItem(40308, adena);
					L1ItemInstance item = pc.getInventory().storeItem(itemid, count);
					if (item != null) {
						String itemName = item.getItem().getName();
						String npcName = npc.getNpcTemplate().get_name();
						SC_ServerMessage.send(pc, 143, npcName, itemName);
					}
					htmlid = "sharna3";
				} else {
					htmlid = "sharna5";
				}

				// 이리스
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71126) {
				/*int TeleportType = 0, TeleportX = 0, TeleportY = 0, TeleportMap = 0;
				if (s.equals("0")) { // 중앙광장 외각 부근 이동
					TeleportType = 1;
					TeleportX = 32746;
					TeleportY = 32764;
					TeleportMap = 479;
				} else if (s.equals("1")) { // 명법군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32788;
					TeleportY = 32682;
					TeleportMap = 479;
				} else if (s.equals("2")) { // 마령군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32922;
					TeleportY = 32701;
					TeleportMap = 479;
				} else if (s.equals("3")) { // 마수군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32910;
					TeleportY = 32822;
					TeleportMap = 479;
				} else if (s.equals("4")) { // 암살군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32739;
					TeleportY = 32843;
					TeleportMap = 479;
				} else*/ if (s.equalsIgnoreCase("B")) {
					if (pc.getInventory().checkItem(41007, 1)) {
						htmlid = "eris10";
					} else {
						L1NpcInstance npc = (L1NpcInstance) obj;
						String npcName = npc.getNpcTemplate().get_name();
						L1ItemInstance item = pc.getInventory().storeItem(41007, 1);
						String itemName = item.getItem().getName();
						SC_ServerMessage.send(pc, 143, npcName, itemName);
						htmlid = "eris6";
					}
				} else if (s.equalsIgnoreCase("C")) {
					if (pc.getInventory().checkItem(41009, 1)) {
						htmlid = "eris10";
					} else {
						L1NpcInstance npc = (L1NpcInstance) obj;
						L1ItemInstance item = pc.getInventory().storeItem(41009, 1);
						String itemName = item.getItem().getName();
						String npcName = npc.getNpcTemplate().get_name();
						SC_ServerMessage.send(pc, 143, npcName, itemName);
						htmlid = "eris8";
					}
				} else if (s.equalsIgnoreCase("A")) {
					if (pc.getInventory().checkItem(41007, 1)) {
						if (pc.getInventory().checkItem(40969, 20)) {
							htmlid = "eris18";
							materials = new int[] { 40969, 41007 };
							counts = new int[] { 20, 1 };
							createitem = new int[] { 41008 };
							createcount = new int[] { 1 };
						} else {
							htmlid = "eris5";
						}
					} else {
						htmlid = "eris2";
					}
				} else if (s.equalsIgnoreCase("E")) {
					if (pc.getInventory().checkItem(41010, 1)) {
						htmlid = "eris19";
					} else {
						htmlid = "eris7";
					}
				} else if (s.equalsIgnoreCase("D")) {
					if (pc.getInventory().checkItem(41010, 1)) {
						htmlid = "eris19";
					} else {
						if (pc.getInventory().checkItem(41009, 1)) {
							if (pc.getInventory().checkItem(40959, 1)) {
								htmlid = "eris17";
								materials = new int[] { 40959, 41009 };
								counts = new int[] { 1, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40960, 1)) {
								htmlid = "eris16";
								materials = new int[] { 40960, 41009 };
								counts = new int[] { 1, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40961, 1)) {
								htmlid = "eris15";
								materials = new int[] { 40961, 41009 };
								counts = new int[] { 1, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40962, 1)) {
								htmlid = "eris14";
								materials = new int[] { 40962, 41009 };
								counts = new int[] { 1, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40635, 10)) {
								htmlid = "eris12";
								materials = new int[] { 40635, 41009 };
								counts = new int[] { 10, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40638, 10)) {
								htmlid = "eris11";
								materials = new int[] { 40638, 41009 };
								counts = new int[] { 10, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40642, 10)) {
								htmlid = "eris13";
								materials = new int[] { 40642, 41009 };
								counts = new int[] { 10, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else if (pc.getInventory().checkItem(40667, 10)) {
								htmlid = "eris13";
								materials = new int[] { 40667, 41009 };
								counts = new int[] { 10, 1 };
								createitem = new int[] { 41010 };
								createcount = new int[] { 1 };
							} else {
								htmlid = "eris8";
							}
						} else {
							htmlid = "eris7";
						}
					}
				}
				/*if (TeleportType == 1) {
					if (pc.getRadungeonTime() >= 120) {
						SC_ChatPacket.sendDirect(pc, pc, "라스타바드 던전 시간이 만료 되었습니다.");
						return;
					} else {
						// L1Teleport.teleport(pc, TeleportX, TeleportY, (short)
						// TeleportMap, 0, true);
						pc.start_teleport(TeleportX, TeleportY, TeleportMap, 5, 169, true, false);
					}
				}*/

				// 넘어진 항해사
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80076) {
				if (s.equalsIgnoreCase("A")) {
					int[] diaryno = { 49082, 49083 };
					int pid = _random.nextInt(diaryno.length);
					int di = diaryno[pid];
					if (di == 49082) { // 홀수 페이지 뽑아라
						htmlid = "voyager6a";
						L1NpcInstance npc = (L1NpcInstance) obj;
						L1ItemInstance item = pc.getInventory().storeItem(di, 1);
						String itemName = item.getItem().getName();
						String npcName = npc.getNpcTemplate().get_name();
						SC_ServerMessage.send(pc, 143, npcName, itemName);
					} else if (di == 49083) { // 짝수 페이지 뽑아라
						htmlid = "voyager6b";
						L1NpcInstance npc = (L1NpcInstance) obj;
						L1ItemInstance item = pc.getInventory().storeItem(di, 1);
						String itemName = item.getItem().getName();
						String npcName = npc.getNpcTemplate().get_name();
						SC_ServerMessage.send(pc, 143, npcName, itemName);
					}
				}
				// 연금 술사 페리타
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71128) {
				if (s.equals("A")) {
					if (pc.getInventory().checkItem(41010, 1)) { // 이리스의 추천서
						htmlid = "perita2";
					} else {
						htmlid = "perita3";
					}
				} else if (s.equals("p")) {
					// 저주해진 블랙 귀 링 판별
					if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(40988, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
						htmlid = "perita43";
					} else if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
						htmlid = "perita44";
					} else if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(40988, 1)) { // 나이트 클래스
						htmlid = "perita45";
					} else if (pc.getInventory().checkItem(40988, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
						htmlid = "perita47";
					} else if (pc.getInventory().checkItem(40987, 1)) { // 위저드 클래스
						htmlid = "perita46";
					} else if (pc.getInventory().checkItem(40988, 1)) { // 나이트 클래스
						htmlid = "perita49";
					} else if (pc.getInventory().checkItem(40987, 1)) { // 워리아크라스
						htmlid = "perita48";
					} else {
						htmlid = "perita50";
					}
				} else if (s.equals("q")) {
					// 블랙 귀 링 판별
					if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41174, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
						htmlid = "perita54";
					} else if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
						htmlid = "perita55";
					} else if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41174, 1)) { // 나이트 클래스
						htmlid = "perita56";
					} else if (pc.getInventory().checkItem(41174, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
						htmlid = "perita58";
					} else if (pc.getInventory().checkItem(41174, 1)) { // 위저드 클래스
						htmlid = "perita57";
					} else if (pc.getInventory().checkItem(41175, 1)) { // 나이트 클래스
						htmlid = "perita60";
					} else if (pc.getInventory().checkItem(41176, 1)) { // 워리아크라스
						htmlid = "perita59";
					} else {
						htmlid = "perita61";
					}
				} else if (s.equals("s")) {
					// 신비적인 블랙 귀 링 판별
					if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41162, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita62";
					} else if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita63";
					} else if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41162, 1)) { // 나이트 클래스
						htmlid = "perita64";
					} else if (pc.getInventory().checkItem(41162, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita66";
					} else if (pc.getInventory().checkItem(41161, 1)) { // 위저드 클래스
						htmlid = "perita65";
					} else if (pc.getInventory().checkItem(41162, 1)) { // 나이트 클래스
						htmlid = "perita68";
					} else if (pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita67";
					} else {
						htmlid = "perita69";
					}
				} else if (s.equals("B")) {
					// 정화의 일부
					if (pc.getInventory().checkItem(40651, 10) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 10) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 10) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 10) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 10) // 어둠의 숨결
							&& pc.getInventory().checkItem(40442, 5) // 프롭브의 위액
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita7";
						materials = new int[] { 40651, 40643, 40618, 40645, 40676, 40442, 40051 };
						counts = new int[] { 10, 10, 10, 10, 20, 5, 1 };
						createitem = new int[] { 40925 }; // 정화의 일부
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita8";
					}
				} else if (s.equals("G") || s.equals("h") || s.equals("i")) {
					// 신비적인 일부：1 단계
					if (pc.getInventory().checkItem(40651, 5) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 5) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 5) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 5) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 5) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 5) // 어둠의 광석
							&& pc.getInventory().checkItem(40049, 3) // 고급 루비
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita27";
						materials = new int[] { 40651, 40643, 40618, 40645, 40676, 40675, 40049, 40051 };
						counts = new int[] { 5, 5, 5, 5, 10, 10, 3, 1 };
						createitem = new int[] { 40926 }; // 신비적인 일부：1 단계
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita28";
					}
				} else if (s.equals("H") || s.equals("j") || s.equals("k")) {
					// 신비적인 일부：2 단계
					if (pc.getInventory().checkItem(40651, 10) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 10) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 10) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 10) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 20) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 10) // 어둠의 광석
							&& pc.getInventory().checkItem(40048, 3) // 고급 다이아몬드
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita29";
						materials = new int[] { 40651, 40643, 40618, 40645, 40676, 40675, 40048, 40051 };
						counts = new int[] { 10, 10, 10, 10, 20, 10, 3, 1 };
						createitem = new int[] { 40927 }; // 신비적인 일부：2 단계
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita30";
					}
				} else if (s.equals("I") || s.equals("l") || s.equals("m")) {
					// 신비적인 일부：3 단계
					if (pc.getInventory().checkItem(40651, 20) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 20) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 20) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 20) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 30) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 10) // 어둠의 광석
							&& pc.getInventory().checkItem(40050, 3) // 고급 사파이어
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita31";
						materials = new int[] { 40651, 40643, 40618, 40645, 40676, 40675, 40050, 40051 };
						counts = new int[] { 20, 20, 20, 20, 30, 10, 3, 1 };
						createitem = new int[] { 40928 }; // 신비적인 일부：3 단계
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita32";
					}
				} else if (s.equals("J") || s.equals("n") || s.equals("o")) {
					// 신비적인 일부：4 단계
					if (pc.getInventory().checkItem(40651, 30) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 30) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 30) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 30) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 30) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 20) // 어둠의 광석
							&& pc.getInventory().checkItem(40052, 1) // 최고급 다이아몬드
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita33";
						materials = new int[] { 40651, 40643, 40618, 40645, 40676, 40675, 40052, 40051 };
						counts = new int[] { 30, 30, 30, 30, 30, 20, 1, 1 };
						createitem = new int[] { 40928 }; // 신비적인 일부：4 단계
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita34";
					}
				} else if (s.equals("K")) { // 1 단계 귀 링(영혼의 귀 링)
					int earinga = 0;
					int earingb = 0;
					if (pc.getInventory().checkEquipped(21014) || pc.getInventory().checkEquipped(21006)
							|| pc.getInventory().checkEquipped(21007)) {
						htmlid = "perita36";
					} else if (pc.getInventory().checkItem(21014, 1)) { // 위저드 클래스
						earinga = 21014;
						earingb = 41176;
					} else if (pc.getInventory().checkItem(21006, 1)) { // 나이트 클래스
						earinga = 21006;
						earingb = 41177;
					} else if (pc.getInventory().checkItem(21007, 1)) { // 워리아크라스
						earinga = 21007;
						earingb = 41178;
					} else {
						htmlid = "perita36";
					}
					if (earinga > 0) {
						materials = new int[] { earinga };
						counts = new int[] { 1 };
						createitem = new int[] { earingb };
						createcount = new int[] { 1 };
					}
				} else if (s.equals("L")) { // 2 단계 귀 링(지혜의 귀 링)
					if (pc.getInventory().checkEquipped(21015)) {
						htmlid = "perita22";
					} else if (pc.getInventory().checkItem(21015, 1)) {
						materials = new int[] { 21015 };
						counts = new int[] { 1 };
						createitem = new int[] { 41179 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita22";
					}
				} else if (s.equals("M")) { // 3 단계 귀 링(진실의 귀 링)
					if (pc.getInventory().checkEquipped(21016)) {
						htmlid = "perita26";
					} else if (pc.getInventory().checkItem(21016, 1)) {
						materials = new int[] { 21016 };
						counts = new int[] { 1 };
						createitem = new int[] { 41182 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita26";
					}
				} else if (s.equals("b")) { // 2 단계 귀 링(정열의 귀 링)
					if (pc.getInventory().checkEquipped(21009)) {
						htmlid = "perita39";
					} else if (pc.getInventory().checkItem(21009, 1)) {
						materials = new int[] { 21009 };
						counts = new int[] { 1 };
						createitem = new int[] { 41180 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita39";
					}
				} else if (s.equals("d")) { // 3 단계 귀 링(명예의 귀 링)
					if (pc.getInventory().checkEquipped(21012)) {
						htmlid = "perita41";
					} else if (pc.getInventory().checkItem(21012, 1)) {
						materials = new int[] { 21012 };
						counts = new int[] { 1 };
						createitem = new int[] { 41183 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita41";
					}
				} else if (s.equals("a")) { // 2 단계 귀 링(분노의 귀 링)
					if (pc.getInventory().checkEquipped(21008)) {
						htmlid = "perita38";
					} else if (pc.getInventory().checkItem(21008, 1)) {
						materials = new int[] { 21008 };
						counts = new int[] { 1 };
						createitem = new int[] { 41181 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita38";
					}
				} else if (s.equals("c")) { // 3 단계 귀 링(용맹의 귀 링)
					if (pc.getInventory().checkEquipped(21010)) {
						htmlid = "perita40";
					} else if (pc.getInventory().checkItem(21010, 1)) {
						materials = new int[] { 21010 };
						counts = new int[] { 1 };
						createitem = new int[] { 41184 };
						createcount = new int[] { 1 };
					} else {
						htmlid = "perita40";
					}
				}

				// 보석 세공인 룸스
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71129) {
				if (s.equals("Z")) {
					htmlid = "rumtis2";
				} else if (s.equals("Y")) {
					if (pc.getInventory().checkItem(41010, 1)) { // 이리스의 추천서
						htmlid = "rumtis3";
					} else {
						htmlid = "rumtis4";
					}
				} else if (s.equals("q")) {
					htmlid = "rumtis92";
				} else if (s.equals("A")) {
					if (pc.getInventory().checkItem(41161, 1)) {
						// 신비적인 블랙 귀 링
						htmlid = "rumtis6";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("B")) {
					if (pc.getInventory().checkItem(41164, 1)) {
						// 신비적인 위저드 귀 링
						htmlid = "rumtis7";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("C")) {
					if (pc.getInventory().checkItem(41167, 1)) {
						// 신비적인 회색 위저드 귀 링
						htmlid = "rumtis8";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("T")) {
					if (pc.getInventory().checkItem(41167, 1)) {
						// 신비적인 화이트 위저드 귀 링
						htmlid = "rumtis9";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("w")) {
					if (pc.getInventory().checkItem(41162, 1)) {
						// 신비적인 블랙 귀 링
						htmlid = "rumtis14";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("x")) {
					if (pc.getInventory().checkItem(41165, 1)) {
						// 신비적인 나이트 귀 링
						htmlid = "rumtis15";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("y")) {
					if (pc.getInventory().checkItem(41168, 1)) {
						// 신비적인 회색 나이트 귀 링
						htmlid = "rumtis16";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("z")) {
					if (pc.getInventory().checkItem(41171, 1)) {
						// 신비적인 화이트 나이트 귀 링
						htmlid = "rumtis17";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("U")) {
					if (pc.getInventory().checkItem(41163, 1)) {
						// 신비적인 블랙 귀 링
						htmlid = "rumtis10";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("V")) {
					if (pc.getInventory().checkItem(41166, 1)) {
						// 미스테리아스워리아이아링
						htmlid = "rumtis11";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("W")) {
					if (pc.getInventory().checkItem(41169, 1)) {
						// 미스테리아스그레이워리아이아링
						htmlid = "rumtis12";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("X")) {
					if (pc.getInventory().checkItem(41172, 1)) {
						// 미스테리아스화이워리아이아링
						htmlid = "rumtis13";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("D") || s.equals("E") || s.equals("F") || s.equals("G")) {
					int insn = 0;
					int bacn = 0;
					int me = 0;
					int mr = 0;
					int mj = 0;
					int an = 0;
					int men = 0;
					int mrn = 0;
					int mjn = 0;
					int ann = 0;
					if (pc.getInventory().checkItem(40959, 1) // 명법군왕의 인장
							&& pc.getInventory().checkItem(40960, 1) // 마령군왕의 인장
							&& pc.getInventory().checkItem(40961, 1) // 마수군왕의 인장
							&& pc.getInventory().checkItem(40962, 1)) { // 암살군왕의 인장
						insn = 1;
						me = 40959;
						mr = 40960;
						mj = 40961;
						an = 40962;
						men = 1;
						mrn = 1;
						mjn = 1;
						ann = 1;
					} else if (pc.getInventory().checkItem(40642, 10) // 명법군의 배지
							&& pc.getInventory().checkItem(40635, 10) // 마령군의 배지
							&& pc.getInventory().checkItem(40638, 10) // 마수군의 배지
							&& pc.getInventory().checkItem(40667, 10)) { // 암살군의 배지
						bacn = 1;
						me = 40642;
						mr = 40635;
						mj = 40638;
						an = 40667;
						men = 10;
						mrn = 10;
						mjn = 10;
						ann = 10;
					}
					if (pc.getInventory().checkItem(40046, 1) // 사파이어
							&& pc.getInventory().checkItem(40618, 5) // 대지의 숨결
							&& pc.getInventory().checkItem(40643, 5) // 수의 숨결
							&& pc.getInventory().checkItem(40645, 5) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40651, 5) // 불의 숨결
							&& pc.getInventory().checkItem(40676, 5)) { // 어둠의 숨결
						if (insn == 1 || bacn == 1) {
							htmlid = "rumtis60";
							materials = new int[] { me, mr, mj, an, 40046, 40618, 40643, 40651, 40676 };
							counts = new int[] { men, mrn, mjn, ann, 1, 5, 5, 5, 5, 5 };
							createitem = new int[] { 40926 }; // 가공된 사파이어：1 단계
							createcount = new int[] { 1 };
						} else {
							htmlid = "rumtis18";
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70644 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70538
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70560 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70667
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70725 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70790
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70884) {
					if (s.equalsIgnoreCase("request blood of evil")) {
						SC_SystemMessage.send(pc, "아직 시기가 아닌 것 같습니다.");
						htmlid = "";
//						return;
					}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71119) {
				if (s.equalsIgnoreCase("request las history book")) {
					materials = new int[] { 41019, 41020, 41021, 41022, 41023, 41024, 41025, 41026 };
					counts = new int[] { 1, 1, 1, 1, 1, 1, 1, 1 };
					createitem = new int[] { 41027 };
					createcount = new int[] { 1 };
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71170) {
				if (s.equalsIgnoreCase("request las weapon manual")) {
					materials = new int[] { 41027 };
					counts = new int[] { 1 };
					createitem = new int[] { 40965 };
					createcount = new int[] { 1 };
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 6000015) {
				if (s.equalsIgnoreCase("1")) {
					pc.start_teleport(33966, 33253, 4, 5, 169, true, false);
				} else if (s.equalsIgnoreCase("a") && pc.getInventory().checkItem(41158, 10)) {
					pc.start_teleport(32800, 32800, 110, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 10);
				} else if (s.equalsIgnoreCase("b") && pc.getInventory().checkItem(41158, 20)) {
					pc.start_teleport(32800, 32800, 120, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 20);
				} else if (s.equalsIgnoreCase("c") && pc.getInventory().checkItem(41158, 30)) {
					pc.start_teleport(32800, 32800, 130, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 30);
				} else if (s.equalsIgnoreCase("d") && pc.getInventory().checkItem(41158, 40)) {
					pc.start_teleport(32800, 32800, 140, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 40);
				} else if (s.equalsIgnoreCase("e") && pc.getInventory().checkItem(41158, 50)) {
					pc.start_teleport(32796, 32796, 150, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 50);
				} else if (s.equalsIgnoreCase("f") && pc.getInventory().checkItem(41158, 60)) {
					pc.start_teleport(32720, 32821, 160, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 60);
				} else if (s.equalsIgnoreCase("g") && pc.getInventory().checkItem(41158, 70)) {
					pc.start_teleport(32720, 32821, 170, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 70);
				} else if (s.equalsIgnoreCase("h") && pc.getInventory().checkItem(41158, 80)) {
					pc.start_teleport(32724, 32822, 180, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 80);
				} else if (s.equalsIgnoreCase("i") && pc.getInventory().checkItem(41158, 90)) {
					pc.start_teleport(32722, 32827, 190, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 90);
				} else if (s.equalsIgnoreCase("j") && pc.getInventory().checkItem(41158, 100)) {
					pc.start_teleport(32731, 32856, 200, 5, 169, true, false);
					pc.getInventory().consumeItem(41158, 100);
				}
				// 첩보원(욕망의 동굴측)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80067) {
				if (s.equalsIgnoreCase("n")) {
					htmlid = "";
					poly(client, 6034);
					final int[] item_ids = { 41132, 41133, 41134 };
					final int[] item_amounts = { 1, 1, 1 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
						pc.getQuest().set_step(L1Quest.QUEST_DESIRE, 1);
					}
					// 「그런 임무는 그만둔다」
				} else if (s.equalsIgnoreCase("d")) {
					htmlid = "minicod09";
					pc.getInventory().consumeItem(41130, 1);
					pc.getInventory().consumeItem(41131, 1);
					// 「초기화한다」
				} else if (s.equalsIgnoreCase("k")) {
					htmlid = "";
					pc.getInventory().consumeItem(41132, 1); // 핏자국의 타락 한 가루
					pc.getInventory().consumeItem(41133, 1); // 핏자국의 무력 한 가루
					pc.getInventory().consumeItem(41134, 1); // 핏자국의 아집 한 가루
					pc.getInventory().consumeItem(41135, 1); // 카헬의 타락 한 정수
					pc.getInventory().consumeItem(41136, 1); // 카헬의 무력 한 정수
					pc.getInventory().consumeItem(41137, 1); // 카헬의 아집 한 정수
					pc.getInventory().consumeItem(41138, 1); // 카헬의 정수
					pc.getQuest().set_step(L1Quest.QUEST_DESIRE, 0); // 정수를 건네준다
				} else if (s.equalsIgnoreCase("e")) {
					if (pc.getQuest().get_step(L1Quest.QUEST_DESIRE) == L1Quest.QUEST_END || pc.getKarmaLevel() >= 1) {
						htmlid = "";
					} else {
						if (pc.getInventory().checkItem(41138)) {
							htmlid = "";
							pc.addKarma((int) (1600 * Config.RATE_KARMA));
							SC_Karma.send(pc);
							pc.getInventory().consumeItem(41130, 1); // 핏자국의 계약서
							pc.getInventory().consumeItem(41131, 1); // 핏자국의 지령서
							pc.getInventory().consumeItem(41138, 1); // 카헬의 정수
							pc.getQuest().set_step(L1Quest.QUEST_DESIRE, L1Quest.QUEST_END);
						} else {
							htmlid = "minicod04";
						}
					}
					// 선물을 받는다
				} else if (s.equalsIgnoreCase("g")) {
					htmlid = "";
					final int[] item_ids = { 41130 }; // 핏자국의 계약서
					final int[] item_amounts = { 1 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
					}
				}
				/** 드루가 리뉴얼 **/
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900005) {// 드루가의
				L1ItemInstance item = null;
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().checkItem(L1ItemId.DRUGA_POKET)) { // 이미
						htmlid = "veil3";
					} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 10000000)) {// 1000만
						pc.getInventory().consumeItem(L1ItemId.ADENA, 10000000);
						item = pc.getInventory().storeItem(14000000, 1);
						SC_ServerMessage.send(pc, 403, item.getLogName());
						htmlid = "veil8";
					} else {
						htmlid = "veil4";
					}
				}
				if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().checkItem(L1ItemId.DRUGA_POKET)) { // 이미
						htmlid = "veil3";
					} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 10000000)) {// 1000만
						pc.getInventory().consumeItem(L1ItemId.ADENA, 10000000);
						item = pc.getInventory().storeItem(1400001, 1);
						SC_ServerMessage.send(pc, 403, item.getLogName());
						htmlid = "veil8";
					} else {
						htmlid = "veil4";
					}
				}
				if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().checkItem(L1ItemId.DRUGA_POKET)) { // 이미
						htmlid = "veil3";
					} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 10000000)) {// 1000만
						pc.getInventory().consumeItem(L1ItemId.ADENA, 10000000);
						item = pc.getInventory().storeItem(1400002, 1);
						SC_ServerMessage.send(pc, 403, item.getLogName());
						htmlid = "veil8";
					} else {
						htmlid = "veil4";
					}
				}
				if (s.equalsIgnoreCase("d")) {
					if (pc.getInventory().checkItem(L1ItemId.DRUGA_POKET)) { // 이미
						htmlid = "veil3";
					} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 10000000)) {// 1000만
						pc.getInventory().consumeItem(L1ItemId.ADENA, 10000000);
						item = pc.getInventory().storeItem(1400003, 1);
						SC_ServerMessage.send(pc, 403, item.getLogName());
						htmlid = "veil8";
					} else {
						htmlid = "veil4";
					}
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900017) {// 반쿠
				if (s.equalsIgnoreCase("buy 7")) {
					petbuy(client, 900043, 410045, 1);// 녹색 해츨링 7
				} else if (s.equalsIgnoreCase("buy 8")) {
					petbuy(client, 900044, 410046, 1);// 황색 해츨링 8
				}
				htmlid = "";
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900048) {// 수상한 요리사
				if (s.equalsIgnoreCase("0")) {
					if (pc.getInventory().checkItem(41921, 45)) {
						pc.getInventory().consumeItem(41921, 45);
						pc.getInventory().storeItem(410055, 1);
						SC_ServerMessage.send(pc, 403, "$8538");
					} else {
						htmlid = "suschef5";
					}
				} else if (s.equalsIgnoreCase("1")) {
					if (pc.getInventory().checkItem(410057, 1)) {// A
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_B))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_B);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_C))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_C);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_D))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_D);
						pc.getInventory().consumeItem(410057, 1);
						
						L1SkillUse.cast(pc, L1SkillId.FEATHER_BUFF_A, pc.getId(), pc.getX(), pc.getY(),
								null, 0, L1SkillUse.TYPE_GMBUFF);
						SC_SkillSound.broadcast(pc, pc.getId(), 7947);
						htmlid = "";
					} else if (pc.getInventory().checkItem(410058, 1)) {// B
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_A))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_A);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_C))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_C);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_D))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_D);
						pc.getInventory().consumeItem(410058, 1);
						
						L1SkillUse.cast(pc, L1SkillId.FEATHER_BUFF_B, pc.getId(), pc.getX(), pc.getY(),
								null, 0, L1SkillUse.TYPE_GMBUFF);
					
						SC_SkillSound.broadcast(pc, pc.getId(), 7948);
						htmlid = "";
					} else if (pc.getInventory().checkItem(410059, 1)) {// C
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_B))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_B);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_A))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_A);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_D))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_D);
						pc.getInventory().consumeItem(410059, 1);
						
						L1SkillUse.cast(pc, L1SkillId.FEATHER_BUFF_C, pc.getId(), pc.getX(), pc.getY(),
								null, 0, L1SkillUse.TYPE_GMBUFF);
						SC_SkillSound.broadcast(pc, pc.getId(), 7949);
						htmlid = "";
					} else if (pc.getInventory().checkItem(410060, 1)) {// D
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_B))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_B);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_C))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_C);
						if (pc.hasSkillEffect(L1SkillId.FEATHER_BUFF_A))
							pc.removeSkillEffect(L1SkillId.FEATHER_BUFF_A);
						pc.getInventory().consumeItem(410060, 1);
						
						L1SkillUse.cast(pc, L1SkillId.FEATHER_BUFF_D, pc.getId(), pc.getX(), pc.getY(),
								null, 0, L1SkillUse.TYPE_GMBUFF);
						
						SC_SkillSound.broadcast(pc, pc.getId(), 7950);
						htmlid = "";
					} else {
						htmlid = "suschef4";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900000) {// 슈에르메
				// System.out.println("슈에르메");
				L1ItemInstance item = null;
				/** 표식으로 마안해제 **/
				if (s.equalsIgnoreCase("H")) { // 지룡의 마안 표식
					if (pc.getInventory().consumeItem(410162, 32) && pc.getInventory().consumeItem(40308, 100000)) {
						pc.getInventory().storeItem(410034, 1);
					} else {
						htmlid = "sherme10";
					}
				} else if (s.equalsIgnoreCase("I")) { // 수룡의 마안 표식
					if (pc.getInventory().consumeItem(410163, 32) && pc.getInventory().consumeItem(40308, 100000)) {
						pc.getInventory().storeItem(410032, 1);
					} else {
						htmlid = "sherme10";
					}
				} else if (s.equalsIgnoreCase("J")) { // 화룡의 마안 표식
					if (pc.getInventory().consumeItem(410164, 32) && pc.getInventory().consumeItem(40308, 100000)) {
						pc.getInventory().storeItem(410035, 1);
					} else {
						htmlid = "sherme10";
					}
				} else if (s.equalsIgnoreCase("K")) { // 풍룡의 마안 표식
					if (pc.getInventory().consumeItem(410165, 32) && pc.getInventory().consumeItem(40308, 100000)) {
						pc.getInventory().storeItem(410033, 1);
					} else {
						htmlid = "sherme10";
					}
					/** 봉인된 마안류 해제 **/
					if (s.equalsIgnoreCase("A")) {
						if (pc.getInventory().checkItem(410034)) {// 지룡마안
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 100000)
								&& pc.getInventory().checkItem(410030)) {// 봉인 지룡
							pc.getInventory().consumeItem(410030, 1);// 봉인 지룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 100000);
							item = pc.getInventory().storeItem(410034, 1);
							SC_ServerMessage.send(pc, 403, item.getLogName());
							htmlid = "";
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					} else if (s.equalsIgnoreCase("B")) {
						if (pc.getInventory().checkItem(410032)) {// 수룡 마안
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 100000)
								&& pc.getInventory().checkItem(410028)) {// 봉인 수룡
							pc.getInventory().consumeItem(410028, 1);// 봉인 수룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 100000);
							item = pc.getInventory().storeItem(410032, 1);
							SC_ServerMessage.send(pc, 403, item.getLogName());
							htmlid = "";
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					} else if (s.equalsIgnoreCase("C")) {
						if (pc.getInventory().checkItem(410035)) {// 화룡마안
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 100000)
								&& pc.getInventory().checkItem(410031)) {// 봉인 화룡
							pc.getInventory().consumeItem(410031, 1);// 봉인 화룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 100000);
							item = pc.getInventory().storeItem(410035, 1);
							SC_ServerMessage.send(pc, 403, item.getLogName());
							htmlid = "";
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					} else if (s.equalsIgnoreCase("D")) {
						if (pc.getInventory().checkItem(410033)) {// 풍룡 마안
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 100000)
								&& pc.getInventory().checkItem(410029)) {// 봉인 풍룡
							pc.getInventory().consumeItem(410029, 1);// 봉인 풍룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 100000);
							item = pc.getInventory().storeItem(410033, 1);// 풍룡
							SC_ServerMessage.send(pc, 403, item.getLogName());
							htmlid = "";
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					} else if (s.equalsIgnoreCase("E")) {// 탄생의 마안
						int chance = _random.nextInt(100) + 1;
						if (pc.getInventory().checkItem(410036)) {// 탄생
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 200000)
								&& pc.getInventory().checkItem(410034)// 지룡
								&& pc.getInventory().checkItem(410032)) {// 수룡
							pc.getInventory().consumeItem(410034, 1);// 지룡
							pc.getInventory().consumeItem(410032, 1);// 수룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 200000);
							if (chance <= 40) {
								item = pc.getInventory().storeItem(410036, 1);// 탄생
								SC_ServerMessage.send(pc, 403, item.getLogName());
								htmlid = "";
							} else {
								htmlid = "sherme5";
							}
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					} else if (s.equalsIgnoreCase("F")) {// 형상의 마안
						int chance = _random.nextInt(100) + 1;
						if (pc.getInventory().checkItem(410037)) {// 형상
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 200000)
								&& pc.getInventory().checkItem(410036)// 탄생
								&& pc.getInventory().checkItem(410033)) {// 풍룡
							pc.getInventory().consumeItem(410036, 1);// 탄생
							pc.getInventory().consumeItem(410033, 1);// 풍룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 200000);
							if (chance <= 30) {
								item = pc.getInventory().storeItem(410037, 1);// 형상
								SC_ServerMessage.send(pc, 403, item.getLogName());
								htmlid = "";
							} else {
								htmlid = "sherme5";
							}
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					} else if (s.equalsIgnoreCase("G")) {// 생명의 마안
						int chance = _random.nextInt(100) + 1;
						if (pc.getInventory().checkItem(410038)) {// 생명의마안
							htmlid = "sherme0";
						} else if (pc.getInventory().checkItem(L1ItemId.ADENA, 200000)
								&& pc.getInventory().checkItem(410037)// 형상
								&& pc.getInventory().checkItem(410035)) {// 화룡
							pc.getInventory().consumeItem(410037, 1);// 형상
							pc.getInventory().consumeItem(410035, 1);// 화룡
							pc.getInventory().consumeItem(L1ItemId.ADENA, 200000);
							if (chance <= 20) {
								item = pc.getInventory().storeItem(410038, 1);// 생명
								SC_ServerMessage.send(pc, 403, item.getLogName());
								htmlid = "";
							} else {
								htmlid = "sherme5";
							}
						} else {
							htmlid = "sherme1"; // 돈이 없거나 마안이 없다.
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900105) { // 마에노브
				if (s.equalsIgnoreCase("A")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40104, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40104, 2);
						// L1Teleport.teleport(pc, 32744, 32862, (short) 116, 5,
						// true);
						pc.start_teleport(32744, 32862, 116, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("B")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40105, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40105, 2);
						// L1Teleport.teleport(pc, 32745, 32862, (short) 126, 5,
						// true);
						pc.start_teleport(32745, 32862, 126, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("C")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40106, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40106, 2);
						// L1Teleport.teleport(pc, 32744, 32863, (short) 136, 5,
						// true);
						pc.start_teleport(32744, 32863, 136, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("D")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40107, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40107, 2);
						// L1Teleport.teleport(pc, 32744, 32863, (short) 146, 5,
						// true);
						pc.start_teleport(32744, 32863, 146, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("E")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40108, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40108, 2);
						// L1Teleport.teleport(pc, 32738, 32798, (short) 156, 5,
						// true);
						pc.start_teleport(32738, 32798, 156, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("F")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40109, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40109, 2);
						// L1Teleport.teleport(pc, 32737, 32797, (short) 166, 5,
						// true);
						pc.start_teleport(32737, 32797, 166, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("G")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40110, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40110, 2);
						// L1Teleport.teleport(pc, 32738, 32798, (short) 176, 5,
						// true);
						pc.start_teleport(32738, 32798, 176, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("H")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40111, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40111, 2);
						// L1Teleport.teleport(pc, 32738, 32798, (short) 186, 5,
						// true);
						pc.start_teleport(32738, 32798, 186, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				} else if (s.equalsIgnoreCase("I")) {
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(40112, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(40112, 2);
						// L1Teleport.teleport(pc, 32738, 32798, (short) 196, 5,
						// true);
						pc.start_teleport(32738, 32798, 196, 5, 169, true, false);
						htmlid = "";
					} else {
						htmlid = "maetnob2";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 900090
					&& ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 900098) { // 오만의탑
																						// 리뉴얼
																						// 마에노브의불꽃
																						// 16
																						// ~
																						// 96
				int locx = 0, locy = 0, checkitem = 0;
				if (s.equalsIgnoreCase("A")) {
					switch (((L1NpcInstance) obj).getNpcTemplate().get_npcId()) {
					case 900090:// 16층 -> 20층
						locx = 32754;
						locy = 32747;
						checkitem = 40104;
						break;
					case 900091:// 26층 -> 30층
						locx = 32754;
						locy = 32747;
						checkitem = 40105;
						break;
					case 900092:// 36층 -> 40층
						locx = 32754;
						locy = 32747;
						checkitem = 40106;
						break;
					case 900093:// 46층 -> 50층
						locx = 32754;
						locy = 32747;
						checkitem = 40107;
						break;
					case 900094:// 56층 -> 60층
						locx = 32635;
						locy = 32790;
						checkitem = 40108;
						break;
					case 900095:// 66층 -> 70층
						locx = 32637;
						locy = 32791;
						checkitem = 40109;
						break;
					case 900096:// 76층 -> 80층
						locx = 32637;
						locy = 32791;
						checkitem = 40110;
						break;
					case 900097:// 86층 -> 90층
						locx = 32637;
						locy = 32791;
						checkitem = 40111;
						break;
					case 900098:// 96층 -> 100층
						locx = 32732;
						locy = 32857;
						checkitem = 40112;
						break;
					default:
						break;
					}
					if (pc.getInventory().checkItem(40308, 300) && pc.getInventory().checkItem(checkitem, 2)) {
						pc.getInventory().consumeItem(40308, 300);
						pc.getInventory().consumeItem(checkitem, 2);
						// L1Teleport.teleport(pc, locx, locy, (short)
						// (pc.getMapId() + 4), pc.getHeading(), true);
						pc.start_teleport(locx, locy, pc.getMapId() + 4, pc.getHeading(), 169, true, false);
					} else {
						htmlid = "omantel2";
					}
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900143) { // 말섬 인던 보조원
				if (s.equalsIgnoreCase("a")) {
					// L1Teleport.teleport(pc, 32583, 32924, (short) 0, 5, true);
					pc.start_teleport(32583, 32924, 0, 5, 169, true, false);
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70009) { // 게렝
				htmlid = 게렝(pc, s);
				// 클라우디아 수정요청 //엔피시 번호확인하세요
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900146) { // 말섬 인던 휴그린트
				if (s.equalsIgnoreCase("0")) {
					if (!pc.getInventory().checkItem(L1ItemId.MAGIC_BREATH, 1)) {
						if (pc.getInventory().checkItem(40308, 1000)) {
							pc.getInventory().consumeItem(40308, 1000);
							pc.getInventory().storeItem(L1ItemId.MAGIC_BREATH, 1); // 마력의 숨결
							htmlid = "hugrint2";
						} else {
							htmlid = "hugrint3"; // 아덴부족
						}
					} else {
						htmlid = "hugrint4"; // 숨결이미 가지고있음
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70842) { // 마르바
				htmlid = 마르바(pc, obj, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70840) { //로빈후드
				htmlid = 로빈후드(pc, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 600005) {// 지브릴
				htmlid = 지브릴(pc, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71179) { // 디에츠(빛고목제작)
				if (s.equalsIgnoreCase("A")) {// 복원된 고대의 목걸이
					Random random = new Random();
					if (pc.getInventory().checkItem(49028, 1) && pc.getInventory().checkItem(49029, 1)
							&& pc.getInventory().checkItem(49030, 1) && pc.getInventory().checkItem(41139, 1)) { // 보석과
																													// 볼품없는
						// 목걸이 확인
						if (random.nextInt(10) > 6) {
							materials = new int[] { 49028, 49029, 49030, 41139 };
							counts = new int[] { 1, 1, 1, 1 };
							createitem = new int[] { 41140 }; // 복원된 고대의 목걸이
							createcount = new int[] { 1 };
							htmlid = "dh8";
						} else { // 실패의 경우 아이템만 사라짐
							materials = new int[] { 49028, 49029, 49030, 41139 };
							counts = new int[] { 1, 1, 1, 1 };
							createitem = new int[] { 410027 }; // 보석 가루
							createcount = new int[] { 5 };
							htmlid = "dh7";
						}
					} else { // 재료가 부족한 경우
						htmlid = "dh6";
					}
				} else if (s.equalsIgnoreCase("B")) {// 빛나는 고대의 목걸이 제작을 부탁한다.
					Random random = new Random();
					if (pc.getInventory().checkItem(49027, 1) && pc.getInventory().checkItem(41140, 1)) { // 다이아몬드와
						// 복원된 목걸이
						if (random.nextInt(10) > 7) {
							materials = new int[] { 49027, 41140 };
							counts = new int[] { 1, 1 };
							createitem = new int[] { 20422 }; // 빛나는 고대 목걸이
							createcount = new int[] { 1 };
							htmlid = "dh9";
						} else {
							materials = new int[] { 49027, 41140 };
							counts = new int[] { 1, 1 };
							createitem = new int[] { 410027 }; // 보석가루
							createcount = new int[] { 5 };
							htmlid = "dh7";
						}
					} else { // 재료가 부족한 경우
						htmlid = "dh6";
					}
				}
				// 첩보원(그림자의 신전측)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 81202) {
				// 「화가 나지만 승낙한다」
				if (s.equalsIgnoreCase("n")) {
					htmlid = "";
					poly(client, 6035);
					final int[] item_ids = { 41123, 41124, 41125 };
					final int[] item_amounts = { 1, 1, 1 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
						pc.getQuest().set_step(L1Quest.QUEST_SHADOWS, 1);
					}
					// 「그런 임무는 그만둔다」
				} else if (s.equalsIgnoreCase("d")) {
					htmlid = "minitos09";
					pc.getInventory().consumeItem(41121, 1);
					pc.getInventory().consumeItem(41122, 1);
					// 「초기화한다」
				} else if (s.equalsIgnoreCase("k")) {
					htmlid = "";
					pc.getInventory().consumeItem(41123, 1); // 카헬의 타락 한 가루
					pc.getInventory().consumeItem(41124, 1); // 카헬의 무력 한 가루
					pc.getInventory().consumeItem(41125, 1); // 카헬의 아집 한 가루
					pc.getInventory().consumeItem(41126, 1); // 핏자국의 타락 한 정수
					pc.getInventory().consumeItem(41127, 1); // 핏자국의 무력 한 정수
					pc.getInventory().consumeItem(41128, 1); // 핏자국의 아집 한 정수
					pc.getInventory().consumeItem(41129, 1); // 핏자국의 정수
					pc.getQuest().set_step(L1Quest.QUEST_SHADOWS, 0);
					// 정수를 건네준다
				} else if (s.equalsIgnoreCase("e")) {
					if (pc.getQuest().get_step(L1Quest.QUEST_SHADOWS) == L1Quest.QUEST_END || pc.getKarmaLevel() >= 1) {
						htmlid = "";
					} else {
						if (pc.getInventory().checkItem(41129)) {
							htmlid = "";
							pc.addKarma((int) (-1600 * Config.RATE_KARMA));
							SC_Karma.send(pc);
							pc.getInventory().consumeItem(41121, 1); // 카헬의 계약서
							pc.getInventory().consumeItem(41122, 1); // 카헬의 지령서
							pc.getInventory().consumeItem(41129, 1); // 핏자국의 정수
							pc.getQuest().set_step(L1Quest.QUEST_SHADOWS, L1Quest.QUEST_END);
						} else {
							htmlid = "minitos04";
						}
					}
					// 재빠르게 받는다
				} else if (s.equalsIgnoreCase("g")) {
					htmlid = "";
					final int[] item_ids = { 41121 }; // 카헬의 계약서
					final int[] item_amounts = { 1 };
					L1ItemInstance item = null;
					for (int i = 0; i < item_ids.length; i++) {
						item = pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
						SC_ServerMessage.send(pc, 143, ((L1NpcInstance) obj).getNpcTemplate().get_name(), item.getItem().getName());
					}
				}
				// 초보자 도우미
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 9274) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				if (s.equals("A")) { // 상아탑 방어구가 필요합니다.
					if (pc.isKnight() || pc.isCrown() || pc.isDarkelf() || pc.isDragonknight()) {
						int check = 0;
						int[] item = new int[] { 22300, 22301, 22302, 22303, 22304 };
						for (int i = 0; i < item.length; i++) {
							if (!pc.getInventory().checkItem(item[i], 1)) {
								createNewItem(pc, npcName, item[i], 1, 0);
								check = 1;
							}
						}
						if (check == 1) {
							htmlid = "tutorrw5";
						} else {
							htmlid = "tutorrw6";
						}
					} else {
						int check = 0;
						int[] item = new int[] { 22306, 22307, 22308, 22309, 22310 };
						for (int i = 0; i < item.length; i++) {
							if (!pc.getInventory().checkItem(item[i], 1)) {
								createNewItem(pc, npcName, item[i], 1, 0);
								check = 1;
							}
						}
						if (check == 1) {
							htmlid = "tutorrw5";
						} else {
							htmlid = "tutorrw6";
						}
					}
				}
			} else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == MJDogFightSettings.CASTER_NPC_ID){
				if (s.equals("status")) {
					MJDogFightHistory.getInstance().on_show_ticket_ration_view(pc, (L1NpcInstance) obj);
				}
				// 버경 승률처리 start
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70035
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70041
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70042) {
				if (s.equals("status")) {
					SC_RaceBoard.send(pc, 1, pc.isGm());
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 777849) { // 킬톤 (호랑이)
				if (s.equalsIgnoreCase("0")) { // 킬톤의 계약서
					if (pc.getInventory().checkItem(40308, 500000)) {
						pc.getInventory().consumeItem(40308, 500000);
						pc.getInventory().storeItem(87050, 1);
						SC_SystemMessage.send(pc, "킬톤이 당신에게 킬톤의 계약서를 주었습니다.");
						htmlid = "";
					} else {
						htmlid = "killton3"; // 아데나 부족시
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 777848) { // 메린(진돗개)
				if (s.equalsIgnoreCase("0")) { // 메린의 계약서
					if (pc.getInventory().checkItem(40308, 500000)) {
						pc.getInventory().consumeItem(40308, 500000);
						pc.getInventory().storeItem(87051, 1);
						SC_SystemMessage.send(pc, "메린이 당신에게 메린의 계약서를 주었습니다.");
						htmlid = "";
					} else {
						htmlid = "merin3"; // 아데나 부족시
					}
				}
				// 마빈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70000) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().checkItem(700013, 1) && pc.getInventory().checkItem(700016, 1)
							&& pc.getInventory().checkItem(700015, 100)) {
						pc.getInventory().consumeItem(700013, 1);
						pc.getInventory().consumeItem(700016, 1);
						pc.getInventory().consumeItem(700015, 100);
						pc.getInventory().storeItem(700017, 5);
						마빈경험치지급2(pc);
						SC_SkillSound.broadcast(pc, pc.getId(), 3944);
						SC_SkillSound.send(pc, pc.getId(), 3944);
						htmlid = "marbinquest4";
					} else {
						htmlid = "marbinquest9";
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getLevel() >= 70) {// 70이상 본섭리뉴얼
						pc.getInventory().storeItem(700012, 1);
						htmlid = "marbinquest2";
					} else {
						htmlid = "marbinquest8";
					}
				} else if (s.equalsIgnoreCase("d")) {
					if (pc.getInventory().checkItem(700014, 1) && pc.getInventory().checkItem(1000004, 1)
							&& pc.getInventory().checkItem(700015, 100)) {
						pc.getInventory().consumeItem(700014, 1);
						pc.getInventory().consumeItem(1000004, 1);
						pc.getInventory().consumeItem(700015, 100);
						pc.getInventory().storeItem(700017, 15);
						마빈경험치지급6(pc);
						SC_SkillSound.broadcast(pc, pc.getId(), 3944);
						SC_SkillSound.send(pc, pc.getId(), 3944);
						htmlid = "marbinquest6";
					} else {
						htmlid = "marbinquest7";
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().checkItem(700014, 1) && pc.getInventory().checkItem(700016, 1)
							&& pc.getInventory().checkItem(700015, 100)) {
						pc.getInventory().consumeItem(700014, 1);
						pc.getInventory().consumeItem(700016, 1);
						pc.getInventory().consumeItem(700015, 100);
						pc.getInventory().storeItem(700017, 15);
						마빈경험치지급6(pc);
						SC_SkillSound.broadcast(pc, pc.getId(), 3944);
						SC_SkillSound.send(pc, pc.getId(), 3944);
						htmlid = "marbinquest6";
					} else {
						htmlid = "marbinquest7";
					}
				}
				// 피어스
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70908) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();

				if (s.equals("a")) { // 어둠의 힘을 원합니다.
					int[] list = new int[2];
					for (L1ItemInstance item : pc.getInventory().getItems()) {
						switch (item.getItemId()) {
						case 13:
						case 81:
						case 162:
						case 177:
						case 194:
							if (item.getEnchantLevel() == 8 && pc.getInventory().checkItem(40308, 5000000)) {
								list[0] = 1;
							} else if (item.getEnchantLevel() == 9 && pc.getInventory().checkItem(40308, 10000000)) {
								list[1] = 2;
							}
							break;
						default:
							break;
						}
					}
					int count = list[0] + list[1];
					switch (count) {
					case 1:
						htmlid = "piers03";
						break;
					case 2:
						htmlid = "piers02";
						break;
					case 3:
						htmlid = "piers01";
						break;
					default:
						htmlid = "piers04";
						break;
					}
				} else if (s.equals("0")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[0], pc.PiersEnchant[0], pc.PiersItemId[18]);
				} else if (s.equals("1")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[1], pc.PiersEnchant[1], pc.PiersItemId[18]);
				} else if (s.equals("2")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[2], pc.PiersEnchant[2], pc.PiersItemId[18]);
				} else if (s.equals("3")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[3], pc.PiersEnchant[3], pc.PiersItemId[18]);
				} else if (s.equals("4")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[4], pc.PiersEnchant[4], pc.PiersItemId[18]);
				} else if (s.equals("5")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[5], pc.PiersEnchant[5], pc.PiersItemId[18]);
				} else if (s.equals("6")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[6], pc.PiersEnchant[6], pc.PiersItemId[18]);
				} else if (s.equals("7")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[7], pc.PiersEnchant[7], pc.PiersItemId[18]);
				} else if (s.equals("8")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[8], pc.PiersEnchant[8], pc.PiersItemId[18]);
				} else if (s.equals("9")) {
					PiersItem(pc, objid, npcName, pc.PiersItemId[9], pc.PiersEnchant[9], pc.PiersItemId[18]);
				} else if (s.equals("A")) { // +7 파괴의 크로우
					PiersCheckItem(pc, objid, 8, 5000000, 30018, 7);
				} else if (s.equals("B")) { // +7 파괴의 이도류
					PiersCheckItem(pc, objid, 8, 5000000, 30019, 7);
				} else if (s.equals("C")) { // +8 파괴의 크로우
					PiersCheckItem(pc, objid, 9, 10000000, 30020, 8);
				} else if (s.equals("D")) { // +8 파괴의 이도류
					PiersCheckItem(pc, objid, 9, 10000000, 30021, 8);
				}
				// 시장 경비병/시장 중심지 이동
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50025
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50032
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50048
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50058
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5067
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5070) {
				if (s.equals("market-giran") || s.equals("teleport Mobjtele2")) {
					int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_GIRAN);
					pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, false);
				} else if (s.equals("EnterSeller")) {
		            L1PcInstance targetShop = L1World.getInstance().getPlayer(s2);
		            if (targetShop != null && targetShop.isPrivateShop() && pc.getMapId() == targetShop.getMapId()) {
		               pc.setFindMerchantId(targetShop.getId());
		               pc.start_teleport(targetShop.getX() - 1, targetShop.getY() + 1, targetShop.getMapId(), 1, 169, true, false);
		            }else{
		               L1NpcShopInstance nn = L1World.getInstance().getShopNpc(s2);
		               if (nn != null && nn.getMapId() == 800 && nn.getState() == 1) {
		                  pc.setFindMerchantId(nn.getId());
		                  pc.start_teleport(nn.getX() + CommonUtil.random(3)-1, nn.getY() + CommonUtil.random(3)-1, nn.getMapId(), 0, 169, false, false);
		               }
		            }
				} else if (s.equals("teleport Mobjtele1")) {
					pc.start_teleport(32800, 32927, 800, pc.getHeading(), 169, true, false);
				}
				// 적대적인 얼음 여왕 근위병
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5079) {
				if (s.equalsIgnoreCase("enter")) {
					QueenSystem.getInstance().startQueen(pc);
				}
				// 우호적인 얼음 여왕 근위병
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5078) {
				if (s.equalsIgnoreCase("enter")) {
					DemonSystem.getInstance().startDemon(pc);
				}
				// 상아탑 첩보원
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5086) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().checkItem(30055, 1)) { // 화염의 막대
						htmlid = "icqwand4";
					} else {
						createNewItem(pc, npcName, 30055, 120, 0);
						htmlid = "icqwand2";
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().checkItem(30056, 1)) { // 신비한 회복 물약
						htmlid = "icqwand4";
					} else {
						createNewItem(pc, npcName, 30056, 100, 0);
						htmlid = "icqwand3";
					}
				}
				// 스빈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5087) {
				if (s.equalsIgnoreCase("a")) {
					// createNewItem(pc, npcName, 49031, 1, 0); // 얼음 결정체
					// createNewItem(pc, npcName, 21081, 1, 0); // 얼음 귀걸이
					int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_OREN);
					// L1Teleport.teleport(pc, loc[0], loc[1], (short) loc[2],
					// pc.getHeading(), true);
					pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, false);
					htmlid = "";
				}
				// 베테르랑
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5111) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				int[] weapon = new int[] { 1126, 1127, 1128, 1129, 1130, 1131, 1132, 1133 };
				int[] armor = new int[] { 22328, 22329, 22330, 22331, 22332, 22333, 22334, 22335 };
				int result = 0;
				if (s.equals("a")) { // 1. 베테르랑 단검
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isCrown() || pc.isKnight() || pc.isWizard() || pc.isElf() || pc.isDarkelf()) {
							createNewItem(pc, npcName, 1126, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("b")) { // 2. 베테르랑 한손검
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isCrown() || pc.isKnight() || pc.isElf() || pc.isDragonknight()) {
							createNewItem(pc, npcName, 1127, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("c")) { // 3. 베테르랑 양손검
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isCrown() || pc.isKnight() || pc.isDragonknight()) {
							createNewItem(pc, npcName, 1128, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("d")) { // 4. 베테르랑 보우건
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isElf() || pc.isDarkelf() || pc.isBlackwizard()) {
							createNewItem(pc, npcName, 1129, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("e")) { // 5. 베테르랑 지팡이
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isWizard() || pc.isBlackwizard()) {
							createNewItem(pc, npcName, 1130, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("f")) { // 6. 베테르랑 크로우
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isDarkelf()) {
							createNewItem(pc, npcName, 1131, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("g")) { // 7. 베테르랑 체인소드
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isDragonknight()) {
							createNewItem(pc, npcName, 1132, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("h")) { // 8. 베테르랑 키링크
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isBlackwizard()) {
							createNewItem(pc, npcName, 1133, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("i")) { // 1. 베테르랑 판금 갑옷
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isKnight()) {
							createNewItem(pc, npcName, 22328, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("j")) { // 2. 베테르랑 가죽 갑옷
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isCrown() || pc.isElf() || pc.isDarkelf() || pc.isDragonknight() || pc.isBlackwizard()) {
							createNewItem(pc, npcName, 22329, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("k")) { // 3. 베테르랑 로브
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isWizard() || pc.isBlackwizard()) {
							createNewItem(pc, npcName, 22330, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("l")) { // 4. 베테르랑 방패
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isKnight() | pc.isDragonknight()) {
							createNewItem(pc, npcName, 22331, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("m")) { // 5. 베테르랑 티셔츠
					if (pc.getInventory().checkItem(30065, 1)) {
						createNewItem(pc, npcName, 22332, 1, 0);
						pc.getInventory().consumeItem(30065, 1);
						htmlid = "veteranE2";
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("n")) { // 6. 베테르랑 장화
					if (pc.getInventory().checkItem(30065, 1)) {
						createNewItem(pc, npcName, 22333, 1, 0);
						pc.getInventory().consumeItem(30065, 1);
						htmlid = "veteranE2";
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("o")) { // 7. 베테르랑 해골 투구
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isCrown() || pc.isKnight() || pc.isDragonknight()) {
							createNewItem(pc, npcName, 22334, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("p")) { // 8. 베테르랑 마법 망토
					if (pc.getInventory().checkItem(30065, 1)) {
						if (pc.isElf() || pc.isDarkelf() || pc.isBlackwizard()) {
							createNewItem(pc, npcName, 22335, 1, 0);
							pc.getInventory().consumeItem(30065, 1);
							htmlid = "veteranE2";
						} else {
							htmlid = "veteranE6";
						}
					} else {
						htmlid = "veteranE1";
					}
				} else if (s.equals("1")) { // 1. 체력 물약(포도주스) 무기</font>를 아이템으로
					for (int i = 0; i < weapon.length; i++) {
						if (pc.getInventory().checkItem(weapon[i], 1)) {
							pc.getInventory().consumeItem(weapon[i], 1);
							createNewItem(pc, npcName, 60029, 50, 0);
							result = 1;
							break;
						}
					}
					if (result == 1) {
						htmlid = "veteranE3";
					} else {
						htmlid = "veteranE4";
					}
				} else if (s.equals("2")) { // 2. 용사의 무기 마법 주문서
					for (int i = 0; i < weapon.length; i++) {
						if (pc.getInventory().checkItem(weapon[i], 1)) {
							pc.getInventory().consumeItem(weapon[i], 1);
							createNewItem(pc, npcName, 30068, 1, 0);
							result = 1;
							break;
						}
					}
					if (result == 1) {
						htmlid = "veteranE3";
					} else {
						htmlid = "veteranE4";
					}
				} else if (s.equals("3")) { // 3. 용사의 갑옷 마법 주문서
					for (int i = 0; i < weapon.length; i++) {
						if (pc.getInventory().checkItem(weapon[i], 1)) {
							pc.getInventory().consumeItem(weapon[i], 1);
							createNewItem(pc, npcName, 30069, 1, 0);
							result = 1;
							break;
						}
					}
					if (result == 1) {
						htmlid = "veteranE3";
					} else {
						htmlid = "veteranE4";
					}
				} else if (s.equals("4")) { // 1. 체력 물약(포도주스) 방어구</font>를 아이템으로
					for (int i = 0; i < armor.length; i++) {
						if (pc.getInventory().checkItem(armor[i], 1)) {
							pc.getInventory().consumeItem(armor[i], 1);
							createNewItem(pc, npcName, 60029, 50, 0);
							result = 1;
							break;
						}
					}
					if (result == 1) {
						htmlid = "veteranE3";
					} else {
						htmlid = "veteranE4";
					}
				} else if (s.equals("5")) { // 2. 용사의 무기 마법 주문서
					for (int i = 0; i < armor.length; i++) {
						if (pc.getInventory().checkItem(armor[i], 1)) {
							pc.getInventory().consumeItem(armor[i], 1);
							createNewItem(pc, npcName, 30068, 1, 0);
							result = 1;
							break;
						}
					}
					if (result == 1) {
						htmlid = "veteranE3";
					} else {
						htmlid = "veteranE4";
					}
				} else if (s.equals("6")) { // 3. 용사의 갑옷 마법 주문서
					for (int i = 0; i < armor.length; i++) {
						if (pc.getInventory().checkItem(armor[i], 1)) {
							pc.getInventory().consumeItem(armor[i], 1);
							createNewItem(pc, npcName, 30069, 1, 0);
							result = 1;
							break;
						}
					}
					if (result == 1) {
						htmlid = "veteranE3";
					} else {
						htmlid = "veteranE4";
					}
				}
				// 캐릭터 지원단
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5112) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				if (s.equals("a")) { // 1.레벨 상승(최대 75레벨)
					if (pc.getLevel() < 51) {
						pc.setExp(ExpTable.getExpByLevel(51));
					} else if (pc.getLevel() >= 51 && pc.getLevel() < 75) {
						pc.setExp(ExpTable.getExpByLevel(pc.getLevel() + 1));
					} else if (pc.getLevel() >= 75) {
						htmlid = "lind_sm1";
					}
				} else if (s.equals("b")) { // 2.지원품 지급
					if (pc.getLevel() < 75) {
						htmlid = "lind_sm9";
					} else if (pc.getInventory().checkItem(30070, 1)) { // 지급 확인서가
																		// 있을경우
						htmlid = "lind_sm4";
					} else { // 지급 [ 장비 지급 확인서, 장비 지급함, 회상의 촛불 ]
						createNewItem(pc, npcName, 30071, 1, 0); // 아덴왕국 장비 지급함
						createNewItem(pc, npcName, 30070, 1, 0); // 아덴왕국 장비 지급 확인서
						createNewItem(pc, npcName, 200000, 1, 0); // 회상의 촛불
						htmlid = "lind_sm2";
					}
				} else if (s.equals("c")) { // 3.회상의 촛불 지원
					if (pc.getInventory().checkItem(200000, 1)) { // 회상의 촛불
						htmlid = "lind_sm5";
					} else {
						createNewItem(pc, npcName, 200000, 1, 0);
						htmlid = "lind_sm6";
					}
				} else if (s.equals("d")) { // 1.드래곤의 혈흔(안타라스) 받기
					SC_SkillSound.send(pc, pc.getId(), 7783);
					pc.setSkillEffect(L1SkillId.ANTA_BUFF, 7200 * 1000);
					SC_OwnCharAttrDef.send(pc);
					SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 82, 7200 / 60);
					htmlid = "lind_sm8";
					SC_NPCTalkReturn.send(pc, objid, "lind_sm8");
				} else if (s.equals("e")) { // 2.드래곤의 혈흔(파푸리온) 받기.
					SC_SkillSound.send(pc, pc.getId(), 7783);
					pc.setSkillEffect(L1SkillId.FAFU_BUFF, 7200 * 1000);
					SC_OwnCharAttrDef.send(pc);
					SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 85, 7200 / 60);
					htmlid = "lind_sm8";
				}
				// 반지개방
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5162) {
				if (s.equals("A")) { // 76부터
					if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT76)) {
						SC_ServerMessage.send(pc, 3255);// 해당 슬롯은 이미 확장되었습니다.
					} else {
						if (pc.getInventory().checkItem(3000400, 1) && pc.getInventory().checkItem(40308, 5000000) && pc.getLevel() >= 76) {
							pc.getInventory().consumeItem(3000400, 1);
							pc.getInventory().consumeItem(40308, 5000000);
							pc.getQuest().set_end(L1Quest.QUEST_SLOT76);
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
							SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, 1);
							SC_SkillSound.send(pc, pc.getId(), 12003);
						} else {
							if (pc.getInventory().checkItem(40308, 10000000) && pc.getLevel() >= 76) {
								pc.getInventory().consumeItem(40308, 10000000);
								pc.getQuest().set_end(L1Quest.QUEST_SLOT76);
								SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
								SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, 1);
								SC_SkillSound.send(pc, pc.getId(), 12003);
							}
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot6");
						}
					}
				} else if (s.equals("B")) { // 81부터
					if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT81)) {
						SC_ServerMessage.send(pc, 3255);// 해당 슬롯은 이미 확장되었습니다.
					} else {
						if (pc.getInventory().checkItem(3000401, 1) && pc.getInventory().checkItem(40308, 15000000) && pc.getLevel() >= 81) {
							pc.getInventory().consumeItem(3000401, 1);
							pc.getInventory().consumeItem(40308, 15000000);
							pc.getQuest().set_end(L1Quest.QUEST_SLOT81);
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
							SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, 2);
							SC_SkillSound.send(pc, pc.getId(), 12003);
						} else {
							if (pc.getInventory().checkItem(40308, 30000000) && pc.getLevel() >= 81) {
								pc.getInventory().consumeItem(40308, 30000000);
								pc.getQuest().set_end(L1Quest.QUEST_SLOT81);
								SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
								SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, 2);
								SC_SkillSound.send(pc, pc.getId(), 12003);
							}
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot6");
						}
					}
				} else if (s.equals("D") || s.equals("C")) {
					if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT59)) {
						SC_ServerMessage.send(pc, 3255);// 해당 슬롯은 이미 확장되었습니다.
					} else {
						if (pc.getInventory().checkItem(3000399, 1) && pc.getInventory().checkItem(40308, 1000000) && pc.getLevel() >= 59) {
							pc.getInventory().consumeItem(3000399, 1);
							pc.getInventory().consumeItem(40308, 1000000);
							SC_ReturnedStat.sendSlot(pc, 67, 1, 16);
							pc.getQuest().set_end(L1Quest.QUEST_SLOT59);
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
							SC_SkillSound.send(pc, pc.getId(), 12004);
						} else {
							if (pc.getInventory().checkItem(40308, 2000000) && pc.getLevel() >= 59) {
								pc.getInventory().consumeItem(40308, 2000000);
								SC_ReturnedStat.sendSlot(pc, 67, 1, 16);
								pc.getQuest().set_end(L1Quest.QUEST_SLOT59);
								SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
								SC_SkillSound.send(pc, pc.getId(), 12004);
							}
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot11");
						}
					}
				} else if (s.equalsIgnoreCase("E")) { // 견갑
					if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT_SHOULD)) {
						SC_ServerMessage.send(pc, 3255);// 해당 슬롯은 이미 확장되었습니다.
					} else {
						if (pc.getInventory().checkItem(3000402, 1) && pc.getInventory().checkItem(40308, 15000000) && pc.getLevel() >= 83) {
							pc.getInventory().consumeItem(3000402, 1);
							pc.getInventory().consumeItem(40308, 15000000);
							pc.getQuest().set_end(L1Quest.QUEST_SLOT_SHOULD);
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
							SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, SC_ReturnedStat.OPEN_SLOT_SHOULD);
							SC_SkillSound.send(pc, pc.getId(), 12004);
						} else {
							if (pc.getInventory().checkItem(40308, 30000000) && pc.getLevel() >= 83) {
								pc.getInventory().consumeItem(40308, 30000000);
								pc.getQuest().set_end(L1Quest.QUEST_SLOT_SHOULD);
								SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
								SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, SC_ReturnedStat.OPEN_SLOT_SHOULD);
								SC_SkillSound.send(pc, pc.getId(), 12004);
							}
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot11");
						}
					}
				} else if (s.equalsIgnoreCase("F")) { // 휘장
					if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT_BADGE)) {
						SC_ServerMessage.send(pc, 3255);// 해당 슬롯은 이미 확장되었습니다.
					} else {
						if (pc.getInventory().checkItem(3000403, 1) && pc.getInventory().checkItem(40308, 1000000) && pc.getLevel() >= 70) {
							pc.getInventory().consumeItem(3000403, 1);
							pc.getInventory().consumeItem(40308, 1000000);
							pc.getQuest().set_end(L1Quest.QUEST_SLOT_BADGE);
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
							SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, SC_ReturnedStat.OPEN_SLOT_BADGE);
							SC_SkillSound.send(pc, pc.getId(), 12004);
						} else {
							if (pc.getInventory().checkItem(40308, 2000000) && pc.getLevel() >= 70) {
								pc.getInventory().consumeItem(40308, 2000000);
								pc.getQuest().set_end(L1Quest.QUEST_SLOT_BADGE);
								SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
								SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, SC_ReturnedStat.OPEN_SLOT_BADGE);
								SC_SkillSound.send(pc, pc.getId(), 12004);
							}
							SC_NPCTalkReturn.send(pc, pc.getId(), "slot11");
						}
					}
				}
//				}else if(s.equalsIgnoreCase("F")){	// 휘장
//					if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT_BADGE)) {
//						pc.sendPackets(new SC_ServerMessage(3255));// 해당 슬롯은 이미 확장되었습니다.
//					} else {
//						if(!pc.getInventory().checkItem(40308, 2000000)){
//							pc.sendPackets(new SC_SystemMessage("아데나가 부족합니다."), true);
//							pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "slot10"));
//						}else if(pc.getLevel() < 70){
//							pc.sendPackets(new SC_SystemMessage("레벨이 부족합니다."), true);
//							pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "slot10"));
//						}else{
//							pc.getInventory().consumeItem(40308, 2000000);
//							pc.sendPackets(new SC_ReturnedStat(SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, SC_ReturnedStat.OPEN_SLOT_BADGE));
//							pc.getQuest().set_end(L1Quest.QUEST_SLOT_BADGE);
//							pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "slot9"));
//						}
//					}
//				}
				// 안톤
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70614) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();

				int enchant = 0;
				int itemId = 0;
				int oldArmor = 0;
				if (s.equals("A") || s.equals("B") || s.equals("C") || s.equals("D") // 판금
						|| s.equals("E") || s.equals("F") || s.equals("G") || s.equals("H") // 비늘
						|| s.equals("I") || s.equals("J") || s.equals("K") || s.equals("L") // 가죽
						|| s.equals("M") || s.equals("N") || s.equals("O") || s.equals("P")) { // 로브
					if (s.equals("A") || s.equals("B") || s.equals("C") || s.equals("D")) {
						if (s.equals("A")) {
							enchant = 7;
						} else if (s.equals("B")) {
							enchant = 8;
						} else if (s.equals("C")) {
							enchant = 9;
						} else if (s.equals("D")) {
							enchant = 10;
						}
						oldArmor = 20095;
						itemId = 222300;
					} else if (s.equals("E") || s.equals("F") || s.equals("G") || s.equals("H")) {
						if (s.equals("E")) {
							enchant = 7;
						} else if (s.equals("F")) {
							enchant = 8;
						} else if (s.equals("G")) {
							enchant = 9;
						} else if (s.equals("H")) {
							enchant = 10;
						}
						oldArmor = 20094;
						itemId = 222301;
					} else if (s.equals("I") || s.equals("J") || s.equals("K") || s.equals("L")) {
						if (s.equals("I")) {
							enchant = 7;
						} else if (s.equals("J")) {
							enchant = 8;
						} else if (s.equals("K")) {
							enchant = 9;
						} else if (s.equals("L")) {
							enchant = 10;
						}
						oldArmor = 20092;
						itemId = 222302;
					} else if (s.equals("M") || s.equals("N") || s.equals("O") || s.equals("P")) {
						if (s.equals("M")) {
							enchant = 7;
						} else if (s.equals("N")) {
							enchant = 8;
						} else if (s.equals("O")) {
							enchant = 9;
						} else if (s.equals("P")) {
							enchant = 10;
						}
						oldArmor = 20093;
						itemId = 222303;
					}
					if (pc.getInventory().checkEnchantItem(20110, enchant, 1) && pc.getInventory().checkItem(41246, 100000)
							&& pc.getInventory().checkItem(oldArmor, 1)) {
						pc.getInventory().consumeEnchantItem(20110, enchant, 1);
						pc.getInventory().consumeItem(41246, 100000); // 용해제
						pc.getInventory().consumeItem(oldArmor, 1); // 고대의
						createNewItem(pc, npcName, itemId, 1, enchant - 7);
						htmlid = "";
					} else {
						SC_NPCTalkReturn.send(pc, pc.getId(), "anton9");
					}
				} else if (s.equals("1") || s.equals("2") || s.equals("3") || s.equals("4")) {
					if (s.equals("1")) {
						oldArmor = 20095;
						itemId = 222300;
					} else if (s.equals("2")) {
						oldArmor = 20094;
						itemId = 222301;
					} else if (s.equals("3")) {
						oldArmor = 20092;
						itemId = 222302;
					} else if (s.equals("4")) {
						oldArmor = 20093;
						itemId = 222303;
					}
					if (pc.getInventory().checkItem(49015, 1) && pc.getInventory().checkItem(oldArmor, 1)) {
						pc.getInventory().consumeItem(49015, 1); // 블랙미스릴용액
						pc.getInventory().consumeItem(oldArmor, 1); // 고대의 시리즈
						createNewItem(pc, npcName, itemId, 1, enchant);
						htmlid = "";
					} else {
						SC_NPCTalkReturn.send(pc, pc.getId(), "anton9");
					}
				}

				// 카너스
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5125) {
				htmlid = 카너스(pc, s);
				// 다크엘프 생존자
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5131) {
				if (s.equals("exitghost")) {
					pc.endGhost();
				}
				// 수호병사 OUT
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5128) {
				if (s.equals("a")) {
					// L1Teleport.teleport(pc, 32614, 33195, (short) 4,
					// pc.getHeading(), true);
					pc.start_teleport(32614, 33195, 4, pc.getHeading(), 169, true, false);
					htmlid = "";
				}

				// 수호병사 IN
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5130) {
				if (s.equals("a")) {
					pc.start_teleport(32617, 33215, 4, pc.getHeading(), 169, true, false);
					htmlid = "";
				}
				// 감시자의 눈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5133) {
				if (s.equals("exitghost")) {
					pc.endGhost();
				}
				// 비자야
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 470000350) {

				if (s.equalsIgnoreCase("1")) {
					if (Queen1.getqeensgo()) {
						SC_SystemMessage.send(pc, "현재 혈맹 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
					}
					if (pc.getClanid() == 0) {
						SC_SystemMessage.send(pc, "혈맹 가입자만 진행 가능합니다.");
						return;
					}
					if (pc.isInParty()) {

						if (pc.getParty().isLeader(pc)) {

							if (pc.getInventory().checkItem(7005254  ,1)) {
								Queen1.setqeensgo(true);
								QueenSystem2.getInstance().startQueen(pc);
								pc.getInventory().consumeItem(7005254, 1);
							} else {
								SC_SystemMessage.send(pc, "보스 레이드 입장권(1)이 부족합니다.");
							}
						} else {
							SC_SystemMessage.send(pc, "파티장만 신청 가능합니다.");
						}
					} else {
						SC_SystemMessage.send(pc, "파티중이 아닙니다. 2인 파티이상 출입 가능합니다.");
					} 
				}
				// 비자야
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 460000350) {
				if (BossRaid.getBossGo()) {
					SC_SystemMessage.send(pc, "현재 보스 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
					return; // 진행중이면 더 이상 아래 코드를 실행하지 않도록 리턴 추가
				}

				if (pc.isInParty()) {
					if (pc.getParty().isLeader(pc)) {
						if (pc.getInventory().hasItemDirect(7005254)) {
							pc.getInventory().consumeItem(7005254, 1);
							BossRaid.setBossGo(true);
							BossRaidSystem.getInstance().startBossRaid(pc);

						} else {
							SC_SystemMessage.send(pc, "보스 레이드 입장권(1)이 부족합니다.");
						}
					} else {
						SC_SystemMessage.send(pc, "파티장만 신청 가능합니다.");
					}
				} else {
					SC_SystemMessage.send(pc, "파티중이 아닙니다. 2인 파티이상 출입 가능합니다.");
				} 
				// 비자야
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5161) {
				htmlid = 비자야(pc, obj, s);
				/** 마을 강화 버프사 통합 **/
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900088
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 7310100
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 7132
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 5000
					&& ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 5012) {
				htmldata = 빈줌제작(s, pc);
				htmlid = 젠도르(s, pc, objid);
			} else {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();

				ActionListener listener = null;
				/** 액션 리스너에서 최대 레벨이 걸릴경우 **/
				if((npcid == 7320204 || npcid == 7320054 || npcid == 7310085) && pc.getLevel() >= Config.New_Cha1){
					pc.sendPackets(String.format("%d레벨 이상은 입장할 수 없습니다.", Config.New_Cha1));
					return;
				}
				
				if(npcid == 70751 && s.equalsIgnoreCase("a")){
					listener = ActionListenerLoader.getInstance().findListener(npcid, String.format("a%d", EClassesType.fromGfx(pc.getClassId()).toInt()));
				}else{
					listener = ActionListenerLoader.getInstance().findListener(npcid, s);
				}
				
				if(listener == null){
					htmlid = CS_NpcAcion2.getInstance().NpcAction(pc, obj, s, htmlid);				
				}else{
					htmlid = listener.to_action(pc, obj);
				}
			}
			if (htmlid != null && htmlid.equalsIgnoreCase("colos2")) {
				htmldata = makeUbInfoStrings(((L1NpcInstance) obj).getNpcTemplate().get_npcId());
			}
			if (createitem != null) {
				boolean isCreate = true;
				for (int j = 0; j < materials.length; j++) {
					if (!pc.getInventory().checkItemNotEquipped(materials[j], counts[j])) {
						L1Item temp = ItemTable.getInstance().getTemplate(materials[j]);
						SC_ServerMessage.send(pc, 337, temp.getName());
						isCreate = false;
					}
				}

				if (isCreate) {
					int create_count = 0;
					int create_weight = 0;
					L1Item temp = null;
					for (int k = 0; k < createitem.length; k++) {
						temp = ItemTable.getInstance().getTemplate(createitem[k]);
						if (temp.isStackable()) {
							if (!pc.getInventory().checkItem(createitem[k])) {
								create_count += 1;
							}
						} else {
							create_count += createcount[k];
						}
						create_weight += temp.getWeight() * createcount[k] / 1000;
					}
					if (pc.getInventory().getSize() + create_count > 180) {
						SC_ServerMessage.send(pc, 263);
						return;
					}
					if (pc.getMaxWeight() < pc.getInventory().getWeight() + create_weight) {
						SC_ServerMessage.send(pc, 82);
						return;
					}

					for (int j = 0; j < materials.length; j++) {
						pc.getInventory().consumeItem(materials[j], counts[j]);
					}
					L1ItemInstance item = null;
					for (int k = 0; k < createitem.length; k++) {
						item = pc.getInventory().storeItem(createitem[k], createcount[k]);
						if (item != null) {
							String itemName = ItemTable.getInstance().getTemplate(createitem[k]).getName();
							String createrName = "";
							if (obj instanceof L1NpcInstance) {
								createrName = ((L1NpcInstance) obj).getNpcTemplate().get_name();
							}
							if (createcount[k] > 1) {
								SC_ServerMessage.send(pc, 143, createrName, itemName + " (" + createcount[k] + ")");
							} else {
								SC_ServerMessage.send(pc, 143, createrName, itemName);
							}
						}
					}
					if (success_htmlid != null) {
						SC_NPCTalkReturn.send(pc, objid, success_htmlid, htmldata);
					}
				} else {
					if (failure_htmlid != null) {
						SC_NPCTalkReturn.send(pc, objid, failure_htmlid, htmldata);
					}
				}
			}

			if (htmlid != null) {
				SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
			}
		} catch (Exception e) {	
		}
	}

	private static String 지브릴(L1PcInstance pc, String s) {
		String htmlid = "";
		if (s.equals("A")) {
			if (pc.getInventory().checkItem(41348)) {
				pc.getInventory().consumeItem(41348, 1);
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 3);
				htmlid = "zybril12";
			} else {
				htmlid = "zybril11";
			}
		} else if (s.equals("B")) {
			if (pc.getInventory().checkItem(40048, 10) && pc.getInventory().checkItem(40049, 10)
					&& pc.getInventory().checkItem(40050, 10) && pc.getInventory().checkItem(40051, 10)) {
				pc.getInventory().consumeItem(40048, 10);
				pc.getInventory().consumeItem(40049, 10);
				pc.getInventory().consumeItem(40050, 10);
				pc.getInventory().consumeItem(40051, 10);
				final int[] item_ids = { 41353 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					SC_SystemMessage.send(pc, "에바의 단검을 얻었습니다.");
				}
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 4);
				htmlid = "zybril13";
			} else {
				htmlid = "";
			}
		} else if (s.equals("C")) {
			if (pc.getInventory().checkItem(40514, 10) && pc.getInventory().checkItem(41353, 1)) {
				pc.getInventory().consumeItem(40514, 10);
				pc.getInventory().consumeItem(41353, 1);
				final int[] item_ids = { 41354 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					SC_SystemMessage.send(pc, "신성한 에바의 물을 얻었습니다.");
				}
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 5);
				htmlid = "zybril9";
			} else {
				htmlid = "zybril13";
			}
		} else if (s.equals("D")) {
			if (pc.getInventory().checkItem(41349)) {
				pc.getInventory().consumeItem(41349, 1);
				final int[] item_ids = { 41351 };
				final int[] item_amounts = { 1 };
				for (int i = 0; i < item_ids.length; i++) {
					pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					SC_SystemMessage.send(pc, "달빛의 정기를 얻었습니다.");
				}
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 6);
				htmlid = "zybril10";
			} else {
				htmlid = "zybril14";
			}
		}
		return htmlid;
	}

	private static String 로빈후드(L1PcInstance pc, String s) {
		String htmlid = "";
		if (s.equals("A")) { /* robinhood1~7 */
			if (pc.getInventory().checkItem(40068)) { /* 엘븐 와퍼 체크 */
				pc.getInventory().consumeItem(40068, 1); /* 엘븐 와퍼 소비 */
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 1); /* 1단계 완료 */
				htmlid = "robinhood4";
			} else {
				htmlid = "robinhood19";
			}
		} else if (s.equals("B")) { /* robinhood8 */
			final int[] item_ids = { 41346, 41348 };
			final int[] item_amounts = { 1, 1, };
			for (int i = 0; i < item_ids.length; i++) {
				pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
				// L1ItemInstance memo = pc.getInventory().storeItem(41346,
				// 1);
				// L1ItemInstance memo2 = pc.getInventory().storeItem(41348,
				// 1);
				SC_SystemMessage.send(pc, "로빈후드의 메모지와 소개장을 얻었습니다.");
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 2);
				htmlid = "robinhood13";
			}
		} else if (s.equals("C")) { /* robinhood9 */
			if (pc.getInventory().checkItem(41346) && pc.getInventory().checkItem(41351) && /*pc.getInventory().checkItem(41352, 4) &&*/ 
				pc.getInventory().checkItem(40618, 30) && pc.getInventory().checkItem(40643, 30) && pc.getInventory().checkItem(40645, 30) && 
				pc.getInventory().checkItem(40651, 30) && pc.getInventory().checkItem(40676, 30)) {
				pc.getInventory().consumeItem(41346, 1); /* 메모장, 정기, 유뿔, 불, 물, 바람, 대지 어둠숨결 */
				pc.getInventory().consumeItem(41351, 1);
				//pc.getInventory().consumeItem(41352, 4); /*뿔은 제외*/
				pc.getInventory().consumeItem(40651, 30);
				pc.getInventory().consumeItem(40643, 30);
				pc.getInventory().consumeItem(40645, 30);
				pc.getInventory().consumeItem(40618, 30);
				pc.getInventory().consumeItem(40676, 30);
				final int[] item_ids = { 41350, 41347 };
				final int[] item_amounts = { 1, 1, };
				for (int i = 0; i < item_ids.length; i++) {
					pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					SC_SystemMessage.send(pc, "로빈후드의 반지와 메모지를 얻었습니다.");
				}
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 7); /* 7단계 완료 */
				htmlid = "robinhood10"; /* 나머지 재료를 찾아오게.. */
			} else {
				htmlid = "robinhood15"; /* 달빛정기, 유뿔 가져왔는가 */
			}
		} else if (s.equals("E")) { /* robinhood11 */
			if (pc.getInventory().checkItem(41350) && pc.getInventory().checkItem(41347) && pc.getInventory().checkItem(40491, 30) && 
				pc.getInventory().checkItem(40495, 40) && pc.getInventory().checkItem(100) && pc.getInventory().checkItem(40509, 12) && 
				pc.getInventory().checkItem(40052) && pc.getInventory().checkItem(40053) && pc.getInventory().checkItem(40054) && 
				pc.getInventory().checkItem(40055)) {
				pc.getInventory().consumeItem(41350, 1);
				/* 반지, 메모지, 그리폰깃털, 미스릴실, 오리뿔, 오판, 최고급보석1개씩 */
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
				for (int i = 0; i < item_ids.length; i++) {
					pc.getInventory().storeItem(item_ids[i], item_amounts[i]);
					SC_SystemMessage.send(pc, "달의 장궁을 얻었습니다.");
				}
				pc.getQuest().set_step(L1Quest.QUEST_MOONBOW, 0); /* 퀘스트 리셋 */
				htmlid = "robinhood12"; /* 완성이야 */
			} else {
				htmlid = "robinhood17"; /* 재료가 부족한걸 */
			}
		}
		return htmlid;
	}

	private static String 마르바(L1PcInstance pc, L1Object obj, String s) {
		String htmlid = "";
		L1NpcInstance npc = (L1NpcInstance) obj;
		if (pc.getInventory().checkItem(40665)) {
			htmlid = "marba17";
			if (s.equalsIgnoreCase("B")) {
				htmlid = "marba7";
				if (pc.getInventory().checkItem(214) && pc.getInventory().checkItem(20389)
						&& pc.getInventory().checkItem(20393) && pc.getInventory().checkItem(20401)
						&& pc.getInventory().checkItem(20406) && pc.getInventory().checkItem(20409)) {
					htmlid = "marba15";
				}
			}
		} else if (s.equalsIgnoreCase("A")) {
			if (pc.getInventory().checkItem(40637)) {
				htmlid = "marba20";
			} else {
				L1ItemInstance item = pc.getInventory().storeItem(40637, 1);
				String itemName = item.getItem().getName();
				String npcName = npc.getNpcTemplate().get_name();
				SC_ServerMessage.send(pc, 143, npcName, itemName);
				htmlid = "marba6";
			}
		}
		return htmlid;
	}

	private static String 카너스(L1PcInstance pc, String s) {
		String htmlid = "";
		if (s.equals("a")) { // 기르타스 전방으로 이동
			pc.start_teleport(32855, 32862, 537, pc.getHeading(), 169, true, false);
		} else if (s.equals("b")) { // 전초기지로 이동
			pc.start_teleport(32804, 32864, 537, pc.getHeading(), 169, true, false);
		} else if (s.equals("d")) { // 전투 지역을 확인(5000아데나)
			if (pc.getInventory().checkItem(40308, 5000)) {
				pc.getInventory().consumeItem(40308, 5000);
				SC_SystemMessage.send(pc, "아데나는 감사하지만, 지금은 사용 할 수 없습니다.");
				htmlid = "";
			} else {
				htmlid = "canus3";
			}
		}
		return htmlid;
	}

	private static String 비자야(L1PcInstance pc, Object obj, String s) {
		L1NpcInstance npc = (L1NpcInstance) obj;
		String npcName = npc.getNpcTemplate().get_name();
		String htmlid = "";
		int itemId = 0, enchant = 0, adena = 0;
		int[] sealitemId = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 30119, 30120 };
		if (s.equals("A")) { // +7 공명의 키링크 [ +8 사파이어 키링크 ]
			itemId = 503;
			enchant = 8;
			adena = 5000000;
		} else if (s.equals("B")) { // +8 공명의 키링크 [ +9 사파이어 키링크 ]
			itemId = 503;
			enchant = 9;
			adena = 10000000;
		} else if (s.equals("C")) { // +7 공명의 키링크 [ +8 흑요석 키링크 ]
			itemId = 504;
			enchant = 8;
			adena = 5000000;
		} else if (s.equals("D")) { // +8 공명의 키링크 [ +9 흑요석 키링크 ]
			itemId = 504;
			enchant = 9;
			adena = 10000000;
		}
		if (pc.getInventory().checkEnchant(itemId, enchant) && pc.getInventory().checkItem(40308, adena)) {
			pc.getInventory().DeleteEnchant(itemId, enchant);
			pc.getInventory().consumeItem(40308, adena);
			createNewItem(pc, npcName, sealitemId[enchant], 1, 0);
			htmlid = "vjaya05";
		} else {
			htmlid = "vjaya04";
		}
		return htmlid;
	}

	private static String 게렝(L1PcInstance pc, String s) {
		String htmlid = "";
		if (s.equalsIgnoreCase("a")) {
			if (pc.getInventory().checkItem(6003, 1) && pc.getInventory().checkItem(6013, 100) && pc.getLevel() < 70) {
				pc.getInventory().consumeItem(6003, 1);
				pc.getInventory().consumeItem(6013, 100);
				int needExp = ExpTable.getNeedExpNextLevel(52);
				double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
				int exp = 0;
				if (pc.getLevel() <= 60) {
					exp = (int) (needExp * 0.04D);
				} else if (pc.getLevel() <= 65) {
					exp = (int) (needExp * 0.03D);
				} else if (pc.getLevel() <= 70) {
					exp = (int) (needExp * 0.02D);
				} else {
					exp = (int) (needExp * 16D * exppenalty);
				}
				pc.addExp(exp);
				SC_SkillSound.broadcast(pc, pc.getId(), 3944);
				SC_SkillSound.send(pc, pc.getId(), 3944);
			} else {
				htmlid = "gereng02";
			}
		}
		return htmlid;
	}

	// 피어스
	private static void PiersItem(L1PcInstance pc, int objid, String npcName, int checkItem, int checkEnchant, int adena) {
		if (pc.getInventory().checkEnchant(checkItem, checkEnchant) && pc.getInventory().checkItem(40308, adena)) {
			pc.getInventory().DeleteEnchant(checkItem, checkEnchant);
			pc.getInventory().consumeItem(40308, adena);
			createNewItem(pc, npcName, pc.PiersItemId[16], 1, pc.PiersItemId[17]);
			SC_NPCTalkReturn.send(pc, objid, "");
			for (int i = 0; i < 20; i++) {
				pc.PiersItemId[i] = 0;
				pc.PiersEnchant[i] = 0;
			}
		} else {
			SC_NPCTalkReturn.send(pc, objid, "piers04");
		}
	}

	// 피어스 아이템 체크
	private static void PiersCheckItem(L1PcInstance pc, int objid, int enchant, int adena, int newItem, int newItemEnchant) {
		int listcount = 0;
		String[] list = new String[10];
		for (int i = 0; i < 10; i++) {
			list[i] = " ";
		}
		for (L1ItemInstance item : pc.getInventory().getItems()) {
			switch (item.getItemId()) {
			case 13:
			case 81:
			case 162:
			case 177:
			case 194:
				if (item.getEnchantLevel() == enchant && pc.getInventory().checkItem(40308, adena)) {
					list[listcount] = "+" + item.getEnchantLevel() + " " + item.getName();
					pc.PiersItemId[listcount] = item.getItemId();
					pc.PiersEnchant[listcount] = enchant;
					listcount++;
				}
				break;
			default:
				break;
			}
		}
		if (listcount == 0 || !pc.getInventory().checkItem(40308, adena)) {
			SC_NPCTalkReturn.send(pc, objid, "piers04");
		} else {
			pc.PiersItemId[16] = newItem;
			pc.PiersItemId[17] = newItemEnchant;
			pc.PiersItemId[18] = adena;
			SC_NPCTalkReturn.send(pc, objid, "piers00", list);
		}
	}

	private static boolean createNewItem(L1PcInstance pc, String npcName, int item_id, int count, int enchant) {
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			item.setCount(count);
			item.setEnchantLevel(enchant);
			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
				pc.getInventory().storeItem(item);
			} else {
				L1World.getInstance().getInventory(pc.getX(), pc.getY(), pc.getMapId()).storeItem(item);
			}
			SC_ServerMessage.send(pc, 143, npcName, item.getLogName());
			return true;
		} else {
			return false;
		}
	}

	   private static void petbuy(CIOClient client, int npcid, int paytype, int paycount) {
	        L1PcInstance pc = client.getActiveChar();
	        if (pc == null) return;
	        L1PcInventory inv = pc.getInventory();

	        // ── 카리스마 계산 ──
	        int charisma = pc.getAbility().getTotalCha();
	        int petcost = 0;
	        Object[] petlist = pc.getPetList().values().toArray();
	        for (Object pet : petlist) {
	            if (pet instanceof L1PetInstance) {
	                petcost += ((L1NpcInstance) pet).getPetcost();
	            }
	        }
	        if (pc.isCrown()) { charisma += 6; }        // CROWN
	        else if (pc.isElf()) { charisma += 12; }    // ELF
	        else if (pc.isWizard()) { charisma += 6; }  // WIZ
	        else if (pc.isDarkelf()) { charisma += 6; } // DE
	        else if (pc.isDragonknight()) { charisma += 6; } // 용기사
	        else if (pc.isBlackwizard()) { charisma += 6; }  // 환술사
	        charisma -= petcost;

	        if (charisma / 6 <= 0) {
	            SC_ServerMessage.send(pc, 489); // 애완동물이 너무 많습니다.
	            return;
	        }
	        if (!pc.getInventory().checkItem(paytype, paycount)) {
	            return;
	        }
	        pc.getInventory().consumeItem(paytype, paycount);

	        // 스폰하고 "방금 스폰된 객체"를 직접 받는다 — 주변 몹 스캔 불필요
	        L1NpcInstance spawned = L1SpawnUtil.spawn(pc, npcid, 0, 0, 1);
	        if (!(spawned instanceof L1MonsterInstance)) {
	            return;  
	        }
	        L1MonsterInstance targetpet = (L1MonsterInstance) spawned;

	        L1PetType petType = PetTypeTable.getInstance().get(targetpet.getNpcTemplate().get_npcId());
	        if (petType == null || targetpet.isDead()) {
	            return;
	        }
	        if (charisma >= 6 && inv.getSize() < 180) {
	            L1ItemInstance petamu = inv.storeItem(40314, 1); // 펫의 아뮬렛
	            if (petamu != null) {
	                new L1PetInstance(targetpet, pc, petamu.getId());
	                SC_ItemName.send(pc, petamu);
	            }
	        }
	    }
	   
	private static String karmaLevelToHtmlId(int level) {
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

	private static String enterUb(L1PcInstance pc, int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		if (!ub.isActive() || !ub.canPcEnter(pc)) {
			return "colos2";
		}
		if (ub.isNowUb()) {
			return "colos1";
		}
		if (ub.getMembersCount() >= ub.getMaxPlayer()) {
			return "colos4";
		}

		ub.addMember(pc);
		L1Location loc = ub.getLocation().randomLocation(10, false);
		pc.start_teleport(loc.getX(), loc.getY(), ub.getMapId(), pc.getHeading(), 169, true, false);
		return "";
	}

	private static String enterHauntedHouse(L1PcInstance pc) {
		if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_PLAYING) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}
		if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_CLEANUP) {
			SC_ServerMessage.send(pc, 1183);
			return "";
		}
		if (L1HauntedHouse.getInstance().getMembersCount() >= 10) {
			SC_ServerMessage.send(pc, 1184);
			return "";
		}

		if (pc.getLevel() < 30) {
			SC_ServerMessage.send(pc, 1273, "30", "99");
			return "";
		}

		if (!L1HauntedHouse.getInstance().isMember(pc)) {
			if (!pc.getInventory().checkItem(L1ItemId.ADENA, 1000)) {
				SC_ServerMessage.send(pc, 189);
				return "";
			}

			pc.getInventory().removeItem(L1ItemId.ADENA, 1000);

			L1HauntedHouse.getInstance().addMember(pc);
		}

		if (L1DeathMatch.getInstance().isMember(pc)) {
			L1DeathMatch.getInstance().removeMember(pc);
		}

		if (L1Racing.getInstance().contains(0, pc)) {
			L1Racing.getInstance().remove(0, pc);
		}

		return "";
	}

	private static String enterDeathMatch(L1PcInstance pc) {
		if (L1DeathMatch.getInstance().getDeathMatchStatus() == L1DeathMatch.STATUS_PLAYING) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}
		if (L1DeathMatch.getInstance().getMembersCount() >= 20) {
			SC_SystemMessage.send(pc, "이미 데스매치가 포화 상태라네.");
			return "";
		}
		if (pc.getLevel() < 52) {
			SC_ServerMessage.send(pc, 1273, "52", "99");
			return "";
		}

		L1DeathMatch.getInstance().addMember(pc);
		if (L1HauntedHouse.getInstance().isMember(pc)) {
			L1HauntedHouse.getInstance().removeMember(pc);
		}
		return "";
	}

	private static String enterPetMatch(L1PcInstance pc, int objid2) {
		Object[] petlist = pc.getPetList().values().toArray();
		if (petlist.length > 0) {
			SC_ServerMessage.send(pc, 1187); // 펫의 아뮤렛트가 사용중입니다.
			return "";
		}
		if (!L1PetMatch.getInstance().enterPetMatch(pc, objid2)) {
			SC_ServerMessage.send(pc, 1182);
		}
		return "";
	}

	private static final String[] summonstr_list = new String[] { "7", "263", "519", "8", "264", "520", "9", "265", "521", "10", "266", "522",
			"11", "267", "523", "12", "268", "524", "13", "269", "525", "14", "270", "526", "15", "271", "527",
			"16", "17", "18", "274" };
	
	private static final int[] summonid_list = new int[] { 810820, 810821, 810822, // 28
			810823, 810824, 810825, // 32
			810826, 810827, 810828, // 36
			810829, 810830, 810831, // 40
			810832, 810833, 810834, // 44
			810835, 810836, 810837, // 48
			810839, 810838, 810840, // 52
			810841, 810842, 810843, // 56
			810844, 810845, 810846, // 60
			810847, // 64
			810848, // 68
			810850, 810849 // 72 //-- 이부분 임.

	}; // 소환몹 정하는 구문....
	
	private static final int[] summonlvl_list = new int[] { 28, 28, 28, 32, 32, 32, 36, 36, 36, 40, 40, 40, 44, 44, 44, 48, 48, 48, 52, 52, 52,
			56, 56, 56, 60, 60, 60, 64, 68, 72, 72 };// 술자 레벨제한
	private static final int[] summoncha_list = new int[] { 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
			14, 36, 36, 50 };// 카리
	
	private static void summonMonster(L1PcInstance pc, String s) {
		int summonid = 0;
		int levelrange = 0;
		int summoncost = 0;
		
		// 소비구문
		for (int loop = 0; loop < summonstr_list.length; loop++) {
			if (s.equalsIgnoreCase(summonstr_list[loop])) {
				summonid = summonid_list[loop];
				levelrange = summonlvl_list[loop];
				summoncost = summoncha_list[loop];
				break;
			}
		}
		if (pc.getLevel() < levelrange) {
			SC_ServerMessage.send(pc, 743);
			return;
		}

		int petcost = 0;
		Object[] petlist = pc.getPetList().values().toArray();
		for (Object pet : petlist) {
			petcost += ((L1NpcInstance) pet).getPetcost();
		}
		if ((summonid == 810850 || summonid == 810849 || summonid == 810848) && petcost != 0) {
			SC_CloseList.send(pc, pc.getId());
			return;
		}
		int charisma = pc.getAbility().getTotalCha() + 6 - petcost;
		int summoncount = 0;
		if (levelrange <= 52) {
			summoncount = charisma / summoncost;
		} else if (levelrange == 56) {
			summoncount = charisma / (summoncost + 2);
		} else if (levelrange == 60) {
			summoncount = charisma / (summoncost + 4);
		} else if (levelrange == 64) {
			summoncount = charisma / (summoncost + 6);
		} else {
			summoncount = charisma / summoncost;
		}

		if (levelrange <= 52 && summoncount > 5) {
			summoncount = 5;
		} else if (levelrange == 56 && summoncount > 4) {
			summoncount = 4;
		} else if (levelrange == 60 && summoncount > 3) {
			summoncount = 3;
		} else if (levelrange == 64 && summoncount > 2) {
			summoncount = 2;
		}

		L1Npc npcTemp = NpcTable.getInstance().getTemplate(summonid);
		L1SummonInstance summon = null;
		for (int cnt = 0; cnt < summoncount; cnt++) {
			summon = new L1SummonInstance(npcTemp, pc);
			if (summonid == 810850 || summonid == 810849 || summonid == 810848) {
				summon.setPetcost(pc.getAbility().getTotalCha() + 7);
			} else {
				if (levelrange <= 52) {
					summon.setPetcost(summoncost);
				} else if (levelrange == 56) {
					summon.setPetcost(summoncost + 2);
				} else if (levelrange == 60) {
					summon.setPetcost(summoncost + 4);
				} else if (levelrange == 64) {
					summon.setPetcost(summoncost + 6);
				} else {
					summoncount = charisma / summoncost;
				}
			}
		}
		SC_CloseList.send(pc, pc.getId());
	}

	private static void poly(CIOClient clientthread, int polyId) {
		L1PcInstance pc = clientthread.getActiveChar();

		if (pc.getInventory().checkItem(L1ItemId.ADENA, 100)) {
			pc.getInventory().consumeItem(L1ItemId.ADENA, 100);

			L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_NPC, false);
		} else {
			SC_ServerMessage.send(pc, 337, "$4");
		}
	}

	private static void polyByKeplisha(CIOClient clientthread, int polyId) {
		L1PcInstance pc = clientthread.getActiveChar();

		if (pc.getInventory().checkItem(L1ItemId.ADENA, 100)) {
			pc.getInventory().consumeItem(L1ItemId.ADENA, 100);

			L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_KEPLISHA, false);
		} else {
			SC_ServerMessage.send(pc, 337, "$4");
		}
	}

	private static String sellHouse(L1PcInstance pc, int objectId, int npcId) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan == null) {
			return "";
		}
		int houseId = clan.getHouseId();
		if (houseId == 0) {
			return "";
		}
		L1House house = HouseTable.getInstance().getHouseTable(houseId);
		int keeperId = house.getKeeperId();
		if (npcId != keeperId) {
			return "";
		}
		if (!pc.isCrown()) {
			SC_ServerMessage.send(pc, 518);
			return "";
		}
		if (pc.getId() != clan.getLeaderId()) {
			SC_ServerMessage.send(pc, 518);
			return "";
		}
		if (house.isOnSale()) {
			return "agonsale";
		}

		SC_SellHouse.send(pc, objectId, String.valueOf(houseId));
		return null;
	}

	private static void openCloseDoor(L1PcInstance pc, L1NpcInstance npc, String s) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				int keeperId = house.getKeeperId();
				if (npc.getNpcTemplate().get_npcId() == keeperId) {
					L1DoorInstance door1 = null;
					L1DoorInstance door2 = null;
					L1DoorInstance door3 = null;
					L1DoorInstance door4 = null;
					List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
					for (L1DoorInstance door : doors) {
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
					doors = null;
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

	private static void openCloseGate(L1PcInstance pc, int keeperId, boolean isOpen) {
		boolean isNowWar = false;
		int pcCastleId = 0;
		if (pc.getClanid() != 0) {
			L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
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
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.KENT_CASTLE_ID);
		} else if (keeperId == 70600) { // OT
			if (isExistDefenseClan(L1CastleLocation.OT_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.OT_CASTLE_ID) {
					return;
				}
			}
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.OT_CASTLE_ID);
		} else if (keeperId == 70778 || keeperId == 70987 || keeperId == 70687) {
			if (isExistDefenseClan(L1CastleLocation.WW_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.WW_CASTLE_ID) {
					return;
				}
			}
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.WW_CASTLE_ID);
		} else if (keeperId == 70817 || keeperId == 70800 || keeperId == 70988 || keeperId == 70990 || keeperId == 70989
				|| keeperId == 70991) {
			if (isExistDefenseClan(L1CastleLocation.GIRAN_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.GIRAN_CASTLE_ID) {
					return;
				}
			}
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.GIRAN_CASTLE_ID);

		} else if (keeperId == 70863 || keeperId == 70992 || keeperId == 70862) {
			if (isExistDefenseClan(L1CastleLocation.HEINE_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.HEINE_CASTLE_ID) {
					return;
				}
			}
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.HEINE_CASTLE_ID);
		} else if (keeperId == 70995 || keeperId == 70994 || keeperId == 70993) {
			if (isExistDefenseClan(L1CastleLocation.DOWA_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.DOWA_CASTLE_ID) {
					return;
				}
			}
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.DOWA_CASTLE_ID);
		} else if (keeperId == 70996) {
			if (isExistDefenseClan(L1CastleLocation.ADEN_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.ADEN_CASTLE_ID) {
					return;
				}
			}
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(L1CastleLocation.ADEN_CASTLE_ID);
		}
		List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
		
		for (L1DoorInstance door : doors) {
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
		doors = null;
	}

	private static boolean isExistDefenseClan(int castleId) {
		boolean isExistDefenseClan = false;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (castleId == clan.getCastleId()) {
				isExistDefenseClan = true;
				break;
			}
		}
		return isExistDefenseClan;
	}

	private static void expelOtherClan(L1PcInstance clanPc, int keeperId) {

	    if (clanPc == null) {
	        return;
	    }

	    int houseId = 0;

	    for (L1House house : HouseTable.getInstance().getHouseTableList()) {
	        if (house == null) {
	            continue;
	        }
	        if (house.getKeeperId() == keeperId) {
	            houseId = house.getHouseId();
	            break;
	        }
	    }

	    if (houseId == 0) {
	        return;
	    }
	    final int[] loc =
	            L1HouseLocation.getHouseTeleportLoc(
	                    houseId,
	                    0);

	    if (loc == null || loc.length < 3) {
	        return;
	    }

	    final int clanId =
	            clanPc.getClanid();

	    for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {

	        if (pc == null) {
	            continue;
	        }

	        if (pc.getClanid() == clanId) {
	            continue;
	        }

	        if (!L1HouseLocation.isInHouseLoc(
	                houseId,
	                pc.getX(),
	                pc.getY(),
	                pc.getMapId())) {
	            continue;
	        }

	        pc.start_teleport(
	                loc[0],
	                loc[1],
	                loc[2],
	                pc.getHeading(),
	                169,
	                true,
	                false);
	    }
	}

	private static void repairGate(L1PcInstance pc) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan != null) {
			int castleId = clan.getCastleId();
			if (castleId != 0) {
				if (!MJCastleWarBusiness.getInstance().isNowWar(castleId)) {
					List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
					for (L1DoorInstance door : doors) {
						if (L1CastleLocation.checkInWarArea(castleId, door)) {
							door.repairGate();
						}
					}
					doors = null;
					SC_ServerMessage.send(pc, 990);
				} else {
					SC_ServerMessage.send(pc, 991);
				}
			}
		}
	}

	private static void payFee(L1PcInstance pc, L1NpcInstance npc) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan == null || clan.getHouseId() <= 0) return;
		L1House house = HouseTable.getInstance().getHouseTable(clan.getHouseId());
		if (house == null || npc.getNpcTemplate().get_npcId() != house.getKeeperId()) return;
		Calendar deadline = house.getTaxDeadline();
		if (deadline == null) return;
		Calendar checkCal = Calendar.getInstance(TimeZone.getTimeZone(Config.TIME_ZONE));
		checkCal.add(Calendar.DATE, 3);
		checkCal.set(Calendar.MINUTE, 0);
		checkCal.set(Calendar.SECOND, 0);
		if (deadline.after(checkCal)) {
			SC_SystemMessage.send(pc, "더 이상 납부할 세금이 없습니다.");
			return;
		}
		l1j.server.server.model.L1AuctionTransaction.payTax(pc, house);
	}

	private static String[] makeHouseTaxStrings(L1PcInstance pc, L1NpcInstance npc) {
		String name = npc.getNpcTemplate().get_name();
		String[] result;
		result = new String[] { name, "2000", "1", "1", "00" };
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
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

	private static String[] makeWarTimeStrings(int castleId) {		
		Calendar warTime = MJCastleWarBusiness.getInstance().get(castleId).nextCal();
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

	private static String[] makeUbInfoStrings(int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		return ub.makeUbInfoStrings();
	}

	private static String talkToDimensionDoor(L1PcInstance pc, L1NpcInstance npc, String s) {
		String htmlid = "";
		int protectionId = 0;
		int sealId = 0;
		int locX = 0;
		int locY = 0;
		short mapId = 0;
		if (npc.getNpcTemplate().get_npcId() == 80059) {
			protectionId = 40909;
			sealId = 40913;
			locX = 32773;
			locY = 32835;
			mapId = 607;
		} else if (npc.getNpcTemplate().get_npcId() == 80060) {
			protectionId = 40912;
			sealId = 40916;
			locX = 32757;
			locY = 32842;
			mapId = 606;
		} else if (npc.getNpcTemplate().get_npcId() == 80061) {
			protectionId = 40910;
			sealId = 40914;
			locX = 32830;
			locY = 32822;
			mapId = 604;
		} else if (npc.getNpcTemplate().get_npcId() == 80062) {
			protectionId = 40911;
			sealId = 40915;
			locX = 32835;
			locY = 32822;
			mapId = 605;
		}

		if (s.equalsIgnoreCase("a")) {
			// L1Teleport.teleport(pc, locX, locY, mapId, 5, true);
			pc.start_teleport(locX, locY, mapId, pc.getHeading(), 169, true, false);
			htmlid = "";
		} else if (s.equalsIgnoreCase("b")) {
			L1ItemInstance item = pc.getInventory().storeItem(protectionId, 1);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			htmlid = "";
		} else if (s.equalsIgnoreCase("c")) {
			htmlid = "wpass07";
		} else if (s.equalsIgnoreCase("d")) {
			if (pc.getInventory().checkItem(sealId)) {
				L1ItemInstance item = pc.getInventory().findItemId(sealId);
				pc.getInventory().consumeItem(sealId, item.getCount());
			}
		} else if (s.equalsIgnoreCase("e")) {
			htmlid = "";
		} else if (s.equalsIgnoreCase("f")) {
			if (pc.getInventory().checkItem(protectionId)) {
				pc.getInventory().consumeItem(protectionId, 1);
			}
			if (pc.getInventory().checkItem(sealId)) {
				L1ItemInstance item = pc.getInventory().findItemId(sealId);
				pc.getInventory().consumeItem(sealId, item.getCount());
			}
			htmlid = "";
		}
		return htmlid;
	}

	private static boolean isNpcSellOnly(L1NpcInstance npc) {
		int npcId = npc.getNpcTemplate().get_npcId();
		String npcName = npc.getNpcTemplate().get_name();
		if (npcId == 70027 || "에덴상단".equals(npcName)) {
			return true;
		}
		return false;
	}

	private static void getBloodCrystalByKarma(L1PcInstance pc, L1NpcInstance npc, String s) {
		L1ItemInstance item = null;

		if (s.equalsIgnoreCase("1")) {
			pc.addKarma((int) (500 * Config.RATE_KARMA));
			SC_Karma.send(pc);
			item = pc.getInventory().storeItem(40718, 1);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			SC_ServerMessage.send(pc, 1081);
		} else if (s.equalsIgnoreCase("2")) {
			pc.addKarma((int) (5000 * Config.RATE_KARMA));
			SC_Karma.send(pc);
			item = pc.getInventory().storeItem(40718, 10);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			SC_ServerMessage.send(pc, 1081);
		} else if (s.equalsIgnoreCase("3")) {
			pc.addKarma((int) (50000 * Config.RATE_KARMA));
			SC_Karma.send(pc);
			item = pc.getInventory().storeItem(40718, 100);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			SC_ServerMessage.send(pc, 1081);
		}
	}

	private static void getSoulCrystalByKarma(L1PcInstance pc, L1NpcInstance npc, String s) {
		L1ItemInstance item = null;

		if (s.equalsIgnoreCase("1")) {
			pc.addKarma((int) (-500 * Config.RATE_KARMA));
			SC_Karma.send(pc);
			item = pc.getInventory().storeItem(40678, 1);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			SC_ServerMessage.send(pc, 1080);
		} else if (s.equalsIgnoreCase("2")) {
			pc.addKarma((int) (-5000 * Config.RATE_KARMA));
			SC_Karma.send(pc);
			item = pc.getInventory().storeItem(40678, 10);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			SC_ServerMessage.send(pc, 1080);
		} else if (s.equalsIgnoreCase("3")) {
			pc.addKarma((int) (-50000 * Config.RATE_KARMA));
			SC_Karma.send(pc);
			item = pc.getInventory().storeItem(40678, 100);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			SC_ServerMessage.send(pc, 1080);
		}
	}

	private static String enterPe(L1PcInstance pc, int npcId) {
		if (L1Racing.getInstance().getGameStatus() == L1HauntedHouse.STATUS_PLAYING) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}

		if (L1Racing.getInstance().getGameStatus() == L1HauntedHouse.STATUS_CLEANUP) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}
		if (L1Racing.getInstance().getMembersCount() >= 8) {
			SC_ServerMessage.send(pc, 1184);
			return "";
		}

		if (pc.getLevel() < 30) {
			SC_ServerMessage.send(pc, 1273, "30", "99");
			return "";
		}

		if (!L1Racing.getInstance().isMember(pc)) {
			if (!pc.getInventory().checkItem(L1ItemId.ADENA, 1000)) {
				SC_ServerMessage.send(pc, 189);
				return "";
			}

			pc.getInventory().removeItem(L1ItemId.ADENA, 1000);

			L1Racing.getInstance().add(0, pc);
		}

		if (L1DeathMatch.getInstance().isMember(pc)) {
			L1DeathMatch.getInstance().removeMember(pc);
		}

		if (L1HauntedHouse.getInstance().isMember(pc)) {
			L1Racing.getInstance().remove(0, pc);
		}

		return "";
	}

	private static void StatInitialize(L1PcInstance pc) {
		
		L1SkillUse.cast(pc, L1SkillId.CANCELLATION, pc.getId(), pc.getX(), pc.getY(), null, 0,
				L1SkillUse.TYPE_LOGIN);


		if (pc.getWeapon() != null) {
			pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
		}

		SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
		SC_OwnCharStatus2.send(pc);

		for (L1ItemInstance armor : pc.getInventory().getItems()) {
			for (int type = 0; type <= 12; type++) {
				if (armor != null) {
					pc.getInventory().setEquipped(armor, false, false, false, false);
				}
			}
		}
		pc.setReturnStat(pc.getExp());
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus2.send(pc);
		SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START);
		try {
			pc.save();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void comaCheck(L1PcInstance pc, int type, int objid) {
		ArrayList<Integer> list = new ArrayList<Integer>();

		if (pc.getInventory().checkItem(3000022, 1)) {
			list.add(3000022);
		}
		if (pc.getInventory().checkItem(3000023, 1)) {
			list.add(3000023);
		}
		if (pc.getInventory().checkItem(3000024, 1)) {
			list.add(3000024);
		}
		if (pc.getInventory().checkItem(3000025, 1)) {
			list.add(3000025);
		}
		if (pc.getInventory().checkItem(3000026, 1)) {
			list.add(3000026);
		}

		if (list.size() >= type) {
			for (int i = 0; i < type; i++) {
				pc.getInventory().consumeItem(list.get(i), 1);
			}
			if (type == 3) {
				pc.removeSkillEffect(L1SkillId.COMA_B);
				if (!pc.hasSkillEffect(L1SkillId.COMA_A)) {
					pc.getAbility().addAddedCon(1);
					pc.getAbility().addAddedDex(5);
					pc.getAbility().addAddedStr(5);
					pc.addHitRate(3);
					pc.getAC().addAc(-3);
					SC_OwnCharStatus.send(pc);
				}
				SC_SkillSound.send(pc, pc.getId(), 7382);
				SC_SkillSound.broadcast(pc, pc.getId(), 7382);
				pc.setSkillEffect(L1SkillId.COMA_A, 3600 * 1000);
			} else if (type == 5) {
				pc.removeSkillEffect(L1SkillId.COMA_A);
				if (!pc.hasSkillEffect(L1SkillId.COMA_B)) {
					// pc.addSp(1);
					pc.getAbility().addSp(1);
					pc.getAbility().addAddedCon(3);
					pc.getAbility().addAddedDex(5);
					pc.getAbility().addAddedStr(5);
					pc.addHitRate(5);
					pc.getAC().addAc(-8);
					SC_OwnCharStatus.send(pc);
					SC_SPMR.send(pc);
				}
				SC_SkillSound.send(pc, pc.getId(), 7383);
				SC_SkillSound.broadcast(pc, pc.getId(), 7383);
				pc.setSkillEffect(L1SkillId.COMA_B, 7200 * 1000);
			}
			SC_NPCTalkReturn.send(pc, objid, "");
		} else {
			SC_NPCTalkReturn.send(pc, objid, "coma3");
		}
		list.clear();
	}

	private static boolean isComaBuff(L1PcInstance pc) {
		if (pc.getInventory().checkItem(3000022, pc.getDeathMatchPiece())
				&& pc.getInventory().checkItem(3000023, pc.getGhostHousePiece())
				&& pc.getInventory().checkItem(3000024, pc.getPetRacePiece())
				&& pc.getInventory().checkItem(3000025, pc.getPetMatchPiece())
				&& pc.getInventory().checkItem(3000026, pc.getUltimateBattlePiece())) {
			return true;
		}
		return false;
	}

	private static void giveComaBuff(L1PcInstance pc, int objid) {
		int amount = pc.getUltimateBattlePiece() + pc.getDeathMatchPiece() + pc.getGhostHousePiece()
				+ pc.getPetRacePiece() + pc.getPetMatchPiece();
		if (amount < 3 || amount == 4) {
			SC_NPCTalkReturn.send(pc, objid, "coma3_3");
		} else if (amount == 3) {
			if (isComaBuff(pc)) {
				consumePiece(pc);
				pc.removeSkillEffect(L1SkillId.COMA_B);
				if (!pc.hasSkillEffect(L1SkillId.COMA_A)) {
					pc.getAbility().addAddedCon(1);
					pc.getAbility().addAddedDex(5);
					pc.getAbility().addAddedStr(5);
					pc.addHitRate(3);
					pc.getAC().addAc(-3);
					SC_OwnCharStatus.send(pc);
				}
				SC_SkillSound.send(pc, pc.getId(), 7382);
				SC_SkillSound.broadcast(pc, pc.getId(), 7382);
				pc.setSkillEffect(L1SkillId.COMA_A, 3600 * 1000);
				SC_NPCTalkReturn.send(pc, objid, "");
			} else {
				SC_NPCTalkReturn.send(pc, objid, "coma3_2");
			}
		} else if (amount == 5) {
			if (isComaBuff(pc)) {
				consumePiece(pc);
				pc.removeSkillEffect(L1SkillId.COMA_A);
				if (!pc.hasSkillEffect(L1SkillId.COMA_B)) {
					// pc.addSp(1);
					pc.getAbility().addSp(1);
					pc.getAbility().addAddedCon(3);
					pc.getAbility().addAddedDex(5);
					pc.getAbility().addAddedStr(5);
					pc.addHitRate(5);
					pc.getAC().addAc(-8);
					SC_OwnCharStatus.send(pc);
					SC_SPMR.send(pc);
				}
				SC_SkillSound.send(pc, pc.getId(), 7383);
				SC_SkillSound.broadcast(pc, pc.getId(), 7383);
				pc.setSkillEffect(L1SkillId.COMA_B, 7200 * 1000);
				SC_NPCTalkReturn.send(pc, objid, "");
			} else {
				SC_NPCTalkReturn.send(pc, objid, "coma3_2");
			}
		} else if (amount > 5) {
			SC_NPCTalkReturn.send(pc, objid, "coma3_1");
		}
		resetPiece(pc);
	}

	private static void consumePiece(L1PcInstance pc) {
		pc.getInventory().consumeItem(3000022, pc.getDeathMatchPiece());
		pc.getInventory().consumeItem(3000023, pc.getGhostHousePiece());
		pc.getInventory().consumeItem(3000024, pc.getPetRacePiece());
		pc.getInventory().consumeItem(3000025, pc.getPetMatchPiece());
		pc.getInventory().consumeItem(3000026, pc.getUltimateBattlePiece());
	}

	private static void resetPiece(L1PcInstance pc) {
		pc.setDeathMatchPiece(0);
		pc.setGhostHousePiece(0);
		pc.setPetRacePiece(0);
		pc.setPetMatchPiece(0);
		pc.setUltimateBattlePiece(0);
	}

	private static void selectComa(L1PcInstance pc, int objid) {
		String[] htmldata = new String[] { String.valueOf(pc.getDeathMatchPiece()),
				String.valueOf(pc.getGhostHousePiece()), String.valueOf(pc.getPetRacePiece()),
				String.valueOf(pc.getPetMatchPiece()), String.valueOf(pc.getUltimateBattlePiece()) };
		SC_NPCTalkReturn.send(pc, objid, "coma5", htmldata);
	}

	/** 마빈2%경험치 */
	public static void 마빈경험치지급2(L1PcInstance pc) {
		int needExp = ExpTable.getNeedExpNextLevel(52);
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int exp = 0;
		if (pc.getLevel() <= 60) {
			exp = (int) (needExp * 0.04D);
		} else if (pc.getLevel() <= 65) {
			exp = (int) (needExp * 0.03D);
		} else if (pc.getLevel() <= 70) {
			exp = (int) (needExp * 0.02D);
		} else if (pc.getLevel() <= 75) {
			exp = (int) (needExp * 0.01D);
		} else {
			exp = (int) (needExp * 16D * exppenalty);
		}
		pc.addExp(exp);
	}

	/** 마빈6%경험치 */
	public static void 마빈경험치지급6(L1PcInstance pc) {
		int needExp = ExpTable.getNeedExpNextLevel(52);
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int exp = 0;
		if (pc.getLevel() <= 60) {
			exp = (int) (needExp * 0.12D);
		} else if (pc.getLevel() <= 65) {
			exp = (int) (needExp * 0.09D);
		} else if (pc.getLevel() <= 70) {
			exp = (int) (needExp * 0.06D);
		} else if (pc.getLevel() <= 75) {
			exp = (int) (needExp * 0.03D);
		} else {
			exp = (int) (needExp * 48D * exppenalty);
		}
		pc.addExp(exp);
	}

	private static String[] 빈줌제작(String s, L1PcInstance pc) {
		String[] result = new String[6];
		int x = 0;
		int x1 = 0;
		int x2 = 0;
		int x3 = 0;
		int x4 = 0;
		int x5 = 0;
		if (s.equalsIgnoreCase("1")) {
			x = 1;
			pc._x = 1;
		} else if (s.equalsIgnoreCase("2")) {
			x = 5;
			pc._x = 5;
		} else if (s.equalsIgnoreCase("3")) {
			x = 10;
			pc._x = 10;
		} else if (s.equalsIgnoreCase("4")) {
			x = 100;
			pc._x = 100;
		} else if (s.equalsIgnoreCase("5")) {
			x = 500;
			pc._x = 500;
		} else {
			return result;
		}
		x1 = 50 * x;
		x2 = 100 * x;
		x3 = 100 * x;
		x4 = 200 * x;
		x5 = 200 * x;
		result[0] = String.valueOf(x1);
		result[1] = String.valueOf(x2);
		result[2] = String.valueOf(x3);
		result[3] = String.valueOf(x4);
		result[4] = String.valueOf(x5);
		result[5] = String.valueOf(x);
		return result;
	}

	private static String 젠도르(String s, L1PcInstance pc, int objid) {
		String htmlid = null;
		if ((s.equalsIgnoreCase("1")) || (s.equalsIgnoreCase("2")) || (s.equalsIgnoreCase("3"))
				|| (s.equalsIgnoreCase("4")) || (s.equalsIgnoreCase("5"))) {
			int i = 0;
			if (s.equalsIgnoreCase("1"))
				i = 1;
			else if (s.equalsIgnoreCase("2"))
				i = 5;
			else if (s.equalsIgnoreCase("3"))
				i = 10;
			else if (s.equalsIgnoreCase("4"))
				i = 100;
			else if (s.equalsIgnoreCase("5"))
				i = 500;
			String[] htmldata = new String[] { String.valueOf(50 * i), String.valueOf(100 * i), String.valueOf(100 * i),
					String.valueOf(200 * i), String.valueOf(200 * i), String.valueOf(i) };
			SC_NPCTalkReturn.send(pc, objid, "bs_m4", htmldata);
			return htmlid;
		}
		if (pc._x == 0) {
			if (s.equals("a")) { // 마법을 받는다.
				
				final int ADENA_ID = 40308;
				final int BUFF_COST = 1000;
				
			    L1ItemInstance weapon = pc.getWeapon();

			    // 방어 조건 체크
			    if (weapon == null) {
			        SC_ChatPacket.sendDirect(pc, pc, "무기를 착용하고 받을 수 있습니다.");
			        return htmlid;
			    }

			    if (!pc.getInventory().checkItem(ADENA_ID, BUFF_COST)) {
			        SC_SystemMessage.send(pc, "아데나가 부족합니다.");
			        return htmlid;
			    }

			    // 비용 지불 및 로직 처리
			    pc.getInventory().consumeItem(ADENA_ID, BUFF_COST);

			    // 무기 타입이 20(활/원거리)인지 여부에 따라 스킬 리스트 분기
			    boolean isBow = (weapon.getItem().getType1() == 20);
			    
			    int[] skills = isBow ? new int[]{26, 37, 42, 149} : new int[]{26, 37, 42, 48};

			    // 스킬 일괄 부여
			    for (int skillId : skills) {
			        L1SkillUse.cast(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_GMBUFF);		    			        
			    }
			    
			    // 성혈 버프 성혈만 
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null && clan.getCastleId() != 0) {
					L1SkillUse.cast(pc, L1SkillId.성혈버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_GMBUFF);
				}
			    return ""; // 성공 시 htmlid 초기화
			} else if (s.equals("z")) { // 흑사의 기운을 받는다
				/*if (pc.getInventory().checkItem(65648, 1)) {
					pc.getInventory().consumeItem(65648, 1);
					if (pc.getLevel() >= 5) {
						if (pc.hasSkillEffect(L1SkillId.God_buff))
							pc.removeSkillEffect(L1SkillId.God_buff);
						new L1SkillUse().handleCommands(pc, L1SkillId.God_buff, pc.getId(), pc.getX(), pc.getY(), null,
								0, L1SkillUse.TYPE_SPELLSC);
						htmlid = "bs_done";
					}
					htmlid = "";
				} else {*/
//					htmlid = "bs_01z";
					SC_SystemMessage.send(pc, "흑사의 코인을 더블클릭 하세요.");
			}
		} else {
if (s.equalsIgnoreCase("A")) {
			
				if ((pc.getInventory().checkItem(40090, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 50))) {
					pc.getInventory().consumeItem(40090, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 50);
					pc.getInventory().storeItem(40860, pc._x);
					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("B")) {
				
			
				
				if ((pc.getInventory().checkItem(40090, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 50))) {
					pc.getInventory().consumeItem(40090, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 50);
					pc.getInventory().storeItem(40861, pc._x);
					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("C")) {
				
			
				if ((pc.getInventory().checkItem(40090, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 50))) {
					pc.getInventory().consumeItem(40090, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 50);
					pc.getInventory().storeItem(40862, pc._x);
					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("D")) {
				if ((pc.getInventory().checkItem(40090, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 50))) {
					pc.getInventory().consumeItem(40090, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 50);
					pc.getInventory().storeItem(40866, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("E")) {
				if ((pc.getInventory().checkItem(40090, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 50))) {
					pc.getInventory().consumeItem(40090, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 50);
					pc.getInventory().storeItem(40859, pc._x);
					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}

			}

			if (s.equalsIgnoreCase("F")) {
				if ((pc.getInventory().checkItem(40091, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40091, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40872, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("G")) {
				if ((pc.getInventory().checkItem(40091, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40091, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40871, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("H")) {
				if ((pc.getInventory().checkItem(40091, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40091, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40870, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("I")) {
				if ((pc.getInventory().checkItem(40091, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40091, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40867, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("J")) {
				if ((pc.getInventory().checkItem(40091, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40091, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40873, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}

			}

			if (s.equalsIgnoreCase("K")) {
				if ((pc.getInventory().checkItem(40092, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40092, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40875, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("L")) {
				if ((pc.getInventory().checkItem(40092, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40092, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40879, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("M")) {
				if ((pc.getInventory().checkItem(40092, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40092, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40877, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("N")) {
				if ((pc.getInventory().checkItem(40092, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40092, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40880, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("O")) {
				if ((pc.getInventory().checkItem(40092, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 100))) {
					pc.getInventory().consumeItem(40092, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 100);
					pc.getInventory().storeItem(40876, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}

			}

			if (s.equalsIgnoreCase("P")) {
				if ((pc.getInventory().checkItem(40093, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40093, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40890, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("Q")) {
				if ((pc.getInventory().checkItem(40093, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40093, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40883, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("R")) {
				if ((pc.getInventory().checkItem(40093, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40093, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40884, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("S")) {
				if ((pc.getInventory().checkItem(40093, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))
						&& (pc.getInventory().checkItem(40318, pc._x))) {
					pc.getInventory().consumeItem(40318, pc._x);
					pc.getInventory().consumeItem(40093, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40889, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("T")) {
				if ((pc.getInventory().checkItem(40093, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))
						&& (pc.getInventory().checkItem(40318, pc._x))) {
					pc.getInventory().consumeItem(40093, pc._x);
					pc.getInventory().consumeItem(40318, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40887, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}

			if (s.equalsIgnoreCase("U")) {
				if ((pc.getInventory().checkItem(40094, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40094, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40893, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("V")) {
				if ((pc.getInventory().checkItem(40094, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40094, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40895, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("W")) {
				if ((pc.getInventory().checkItem(40094, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40094, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40897, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("X")) {
				if ((pc.getInventory().checkItem(40094, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40094, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40896, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			if (s.equalsIgnoreCase("Y")) {
				if ((pc.getInventory().checkItem(40094, pc._x)) && (pc.getInventory().checkItem(40308, pc._x * 200))) {
					pc.getInventory().consumeItem(40094, pc._x);
					pc.getInventory().consumeItem(40308, pc._x * 200);
					pc.getInventory().storeItem(40892, pc._x);

					htmlid = "bs_m1";
				} else {
					htmlid = "bs_m2";
				}
			}
			pc._x = 0;
		}
		return htmlid;
	}

}
